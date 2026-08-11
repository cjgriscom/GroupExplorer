package io.chandler.gap;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Stress-compare parallel raw-block I/O strategies for PBIN.
 *
 * <pre>
 *   PbinReadBench &lt;file.pbin&gt; [threads=16] [rounds=4] [opsPerRound=0]
 * </pre>
 * {@code opsPerRound=0} means one read of every block (shuffled) per round.
 */
public final class PbinReadBench {

    enum Mode { CHANNEL, THREADLOCAL_RAF, LOCKED_RAF }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: PbinReadBench <file.pbin> [threads=16] [rounds=4] [opsPerRound=0]");
            System.exit(1);
        }
        String path = args[0];
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 16;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int opsPerRound = args.length > 3 ? Integer.parseInt(args[3]) : 0;

        Meta meta = Meta.open(path);
        int nBlocks = meta.numBlocks;
        if (opsPerRound <= 0) opsPerRound = nBlocks;

        System.out.println("file=" + path);
        System.out.println("size=" + meta.fileLen + " bytes  generators=" + meta.M
                + "  blockSize=" + meta.blockSize + "  blocks=" + nBlocks);
        System.out.println("threads=" + threads + "  rounds=" + rounds
                + "  ops/round=" + opsPerRound
                + "  (~" + String.format("%.1f", opsPerRound * (meta.fileLen / (double) nBlocks) / (1 << 20))
                + " MiB raw I/O per round if uniform)");
        System.out.println();

        // Warm filesystem cache with a full channel pass.
        warm(meta, Math.min(nBlocks, 256));

        for (Mode mode : Mode.values()) {
            Result r = runMode(mode, meta, threads, rounds, opsPerRound);
            System.out.printf("%-16s  wall=%7.1f ms  bytes=%10d  ops=%7d  "
                            + "MiB/s=%6.1f  us/op=%6.1f  errors=%d%n",
                    mode, r.wallMs, r.bytes, r.ops,
                    r.mibPerSec, r.usPerOp, r.errors);
        }

        // Also match PlanarStudy: read + decompress under each strategy.
        System.out.println();
        System.out.println("--- read + decompress (PlanarStudy loadBlockEntries shape) ---");
        for (Mode mode : new Mode[]{Mode.CHANNEL, Mode.THREADLOCAL_RAF}) {
            Result r = runModeDecompress(mode, meta, threads, rounds, opsPerRound);
            System.out.printf("%-16s  wall=%7.1f ms  bytes=%10d  ops=%7d  "
                            + "MiB/s(raw)=%6.1f  us/op=%6.1f  errors=%d%n",
                    mode + "+zlib", r.wallMs, r.bytes, r.ops,
                    r.mibPerSec, r.usPerOp, r.errors);
        }
    }

    private static void warm(Meta meta, int blocks) throws IOException {
        try (FileChannel ch = FileChannel.open(meta.path, StandardOpenOption.READ)) {
            for (int b = 0; b < blocks; b++) {
                readBlockChannel(ch, meta, b);
            }
        }
    }

    private static Result runMode(Mode mode, Meta meta, int threads, int rounds, int opsPerRound)
            throws Exception {
        int[] schedule = buildSchedule(meta.numBlocks, opsPerRound, 42L);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        LongAdder bytes = new LongAdder();
        LongAdder ops = new LongAdder();
        AtomicLong errors = new AtomicLong();

        ThreadLocal<RandomAccessFile> tlRaf = ThreadLocal.withInitial(() -> {
            try {
                return new RandomAccessFile(meta.path.toFile(), "r");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Object rafLock = new Object();
        RandomAccessFile sharedRaf = mode == Mode.LOCKED_RAF
                ? new RandomAccessFile(meta.path.toFile(), "r") : null;
        FileChannel channel = mode == Mode.CHANNEL
                ? FileChannel.open(meta.path, StandardOpenOption.READ) : null;

        // Prime ThreadLocals on pool threads.
        if (mode == Mode.THREADLOCAL_RAF) {
            CountDownLatch primed = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.execute(() -> {
                    tlRaf.get();
                    primed.countDown();
                });
            }
            primed.await();
        }

        long t0 = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            CountDownLatch done = new CountDownLatch(opsPerRound);
            for (int i = 0; i < opsPerRound; i++) {
                final int block = schedule[i];
                pool.execute(() -> {
                    try {
                        byte[] raw;
                        switch (mode) {
                            case CHANNEL:
                                raw = readBlockChannel(channel, meta, block);
                                break;
                            case THREADLOCAL_RAF:
                                raw = readBlockRaf(tlRaf.get(), meta, block);
                                break;
                            case LOCKED_RAF:
                                synchronized (rafLock) {
                                    raw = readBlockRaf(sharedRaf, meta, block);
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        bytes.add(raw.length);
                        ops.increment();
                    } catch (Throwable e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
        }
        long elapsed = System.nanoTime() - t0;
        pool.shutdownNow();
        if (channel != null) channel.close();
        if (sharedRaf != null) sharedRaf.close();
        return Result.of(elapsed, bytes.sum(), ops.sum(), errors.get());
    }

    private static Result runModeDecompress(Mode mode, Meta meta, int threads, int rounds, int opsPerRound)
            throws Exception {
        // PbinFile.readRawBlock is ThreadLocal RAF; CHANNEL here still means FileChannel I/O.
        try (PbinFile pbin = PbinFile.open(meta.path.toString());
             FileChannel channel = mode == Mode.CHANNEL
                     ? FileChannel.open(meta.path, StandardOpenOption.READ) : null) {
            int[] schedule = buildSchedule(meta.numBlocks, opsPerRound, 99L);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            LongAdder bytes = new LongAdder();
            LongAdder ops = new LongAdder();
            AtomicLong errors = new AtomicLong();

            ThreadLocal<RandomAccessFile> tlRaf = ThreadLocal.withInitial(() -> {
                try {
                    return new RandomAccessFile(meta.path.toFile(), "r");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            if (mode == Mode.THREADLOCAL_RAF) {
                CountDownLatch primed = new CountDownLatch(threads);
                for (int t = 0; t < threads; t++) {
                    pool.execute(() -> { tlRaf.get(); primed.countDown(); });
                }
                primed.await();
            }

            long t0 = System.nanoTime();
            for (int r = 0; r < rounds; r++) {
                CountDownLatch done = new CountDownLatch(opsPerRound);
                for (int i = 0; i < opsPerRound; i++) {
                    final int block = schedule[i];
                    pool.execute(() -> {
                        try {
                            byte[] raw;
                            switch (mode) {
                                case CHANNEL:
                                    raw = readBlockChannel(channel, meta, block);
                                    break;
                                case THREADLOCAL_RAF:
                                    // Production path: PbinFile's ThreadLocal reader
                                    raw = pbin.readRawBlock(block);
                                    break;
                                default:
                                    throw new IllegalStateException();
                            }
                            byte[] payload = pbin.decompressBlock(raw);
                            bytes.add(raw.length);
                            ops.increment();
                            if (payload.length < 0) errors.incrementAndGet(); // keep payload live
                        } catch (Throwable e) {
                            errors.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                done.await();
            }
            long elapsed = System.nanoTime() - t0;
            pool.shutdownNow();
            return Result.of(elapsed, bytes.sum(), ops.sum(), errors.get());
        }
    }

    private static int[] buildSchedule(int nBlocks, int ops, long seed) {
        List<Integer> all = new ArrayList<>(nBlocks);
        for (int i = 0; i < nBlocks; i++) all.add(i);
        Collections.shuffle(all, new Random(seed));
        int[] schedule = new int[ops];
        for (int i = 0; i < ops; i++) {
            schedule[i] = all.get(i % nBlocks);
            if (i > 0 && i % nBlocks == 0) {
                Collections.shuffle(all, new Random(seed + i));
            }
        }
        Random rnd = new Random(seed ^ 0x9E3779B97F4A7C15L);
        for (int i = schedule.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = schedule[i];
            schedule[i] = schedule[j];
            schedule[j] = tmp;
        }
        return schedule;
    }

    private static byte[] readBlockChannel(FileChannel ch, Meta meta, int b) throws IOException {
        long start = meta.offsets[b];
        long end = (b + 1 < meta.numBlocks) ? meta.offsets[b + 1] : meta.fileLen;
        int len = (int) (end - start);
        byte[] data = new byte[len];
        ByteBuffer buf = ByteBuffer.wrap(data);
        long pos = start;
        while (buf.hasRemaining()) {
            int n = ch.read(buf, pos);
            if (n < 0) throw new EOFException();
            pos += n;
        }
        return data;
    }

    private static byte[] readBlockRaf(RandomAccessFile raf, Meta meta, int b) throws IOException {
        long start = meta.offsets[b];
        long end = (b + 1 < meta.numBlocks) ? meta.offsets[b + 1] : meta.fileLen;
        int len = (int) (end - start);
        byte[] data = new byte[len];
        raf.seek(start);
        raf.readFully(data);
        return data;
    }

    private static final class Result {
        final double wallMs;
        final long bytes;
        final long ops;
        final long errors;
        final double mibPerSec;
        final double usPerOp;

        Result(double wallMs, long bytes, long ops, long errors) {
            this.wallMs = wallMs;
            this.bytes = bytes;
            this.ops = ops;
            this.errors = errors;
            this.mibPerSec = wallMs > 0 ? (bytes / (1024.0 * 1024.0)) / (wallMs / 1000.0) : 0;
            this.usPerOp = ops > 0 ? (wallMs * 1000.0) / ops : 0;
        }

        static Result of(long nanos, long bytes, long ops, long errors) {
            return new Result(nanos / 1e6, bytes, ops, errors);
        }
    }

    /** Header + block directory only (same layout as {@link PbinFile}). */
    private static final class Meta {
        final Path path;
        final long fileLen;
        final int M;
        final int blockSize;
        final long[] offsets;
        final int numBlocks;

        Meta(Path path, long fileLen, int M, int blockSize, long[] offsets) {
            this.path = path;
            this.fileLen = fileLen;
            this.M = M;
            this.blockSize = blockSize;
            this.offsets = offsets;
            this.numBlocks = offsets.length;
        }

        static Meta open(String filePath) throws IOException {
            Path path = Path.of(filePath);
            try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                long fileLen = ch.size();
                byte[] header = new byte[7];
                readFully(ch, header, 0);
                if (header[0] != 'P' || header[1] != 'B' || header[2] != 'I' || header[3] != 'N')
                    throw new IOException("Not a PBIN file");
                int version = header[4] & 0xFF;
                int offsetWidth = (version == 0x01) ? 4 : 8;
                byte[] vbuf = new byte[15];
                readFully(ch, vbuf, 7);
                int[] vp = {0};
                int blockSize = readVarint(vbuf, vp);
                readVarint(vbuf, vp); // N
                int M = readVarint(vbuf, vp);
                long dirStart = 7 + vp[0];
                int numBlocks = (M + blockSize - 1) / blockSize;
                byte[] dirBuf = new byte[numBlocks * offsetWidth];
                readFully(ch, dirBuf, dirStart);
                long[] offsets = new long[numBlocks];
                for (int b = 0; b < numBlocks; b++) {
                    offsets[b] = (offsetWidth == 4)
                            ? u32(dirBuf, b * 4) : u64(dirBuf, b * 8);
                }
                return new Meta(path, fileLen, M, blockSize, offsets);
            }
        }

        private static void readFully(FileChannel ch, byte[] dst, long pos) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(dst);
            long p = pos;
            while (buf.hasRemaining()) {
                int n = ch.read(buf, p);
                if (n < 0) throw new EOFException();
                p += n;
            }
        }

        private static int readVarint(byte[] data, int[] pos) {
            int val = 0, shift = 0;
            while (true) {
                int b = data[pos[0]++] & 0xFF;
                val |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            return val;
        }

        private static long u32(byte[] d, int i) {
            return (d[i] & 0xFFL) | ((d[i + 1] & 0xFFL) << 8)
                    | ((d[i + 2] & 0xFFL) << 16) | ((d[i + 3] & 0xFFL) << 24);
        }

        private static long u64(byte[] d, int i) {
            return u32(d, i) | (u32(d, i + 4) << 32);
        }
    }
}
