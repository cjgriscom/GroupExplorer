package io.chandler.gap;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Random-access reader for PBIN files. Only one compressed block is held in
 * memory at a time; individual generators are decoded on demand.
 */
public final class PbinFile implements Closeable {

    private final RandomAccessFile raf;
    private final long fileLen;
    private final int M;
    private final int N;
    private final int blockSize;
    private final int compression;
    private final boolean bare;
    /** Absolute file offsets of each compressed block (unsigned, may exceed 2^31). */
    private final long[] offsets;
    private final int numBlocks;

    private int cachedBlock = -1;
    private String[] cachedLines;

    /**
     * Small LRU of decompressed payloads. CongestionBatch processes shuffled PBIN
     * blocks contiguously, so this avoids duplicate inflation without retaining a
     * multi-gigabyte dataset in memory.
     */
    private static final int DECOMPRESSED_CACHE_BLOCKS = 16;
    private final Map<Integer, byte[]> decompressedBlockCache =
            new LinkedHashMap<Integer, byte[]>(DECOMPRESSED_CACHE_BLOCKS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
                    return size() > DECOMPRESSED_CACHE_BLOCKS;
                }
            };

    private PbinFile(RandomAccessFile raf, long fileLen, int M, int N,
                     int blockSize, int compression, boolean bare,
                     long[] offsets) {
        this.raf = raf;
        this.fileLen = fileLen;
        this.M = M;
        this.N = N;
        this.blockSize = blockSize;
        this.compression = compression;
        this.bare = bare;
        this.offsets = offsets;
        this.numBlocks = offsets.length;
    }

    public static PbinFile open(String filePath) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(filePath, "r");
        try {
            long fileLen = raf.length();
            if (fileLen < 7) throw new IOException("Not a PBIN file");

            byte[] header = new byte[7];
            raf.readFully(header);
            if (header[0] != 'P' || header[1] != 'B' || header[2] != 'I' || header[3] != 'N')
                throw new IOException("Not a PBIN file");
            int version = header[4] & 0xFF;
            // v1: uint32 offsets (< 4 GiB). v2: uint64 offsets (large files).
            if (version != 0x01 && version != 0x02)
                throw new IOException("Unsupported PBIN version " + version);
            int offsetWidth = (version == 0x01) ? 4 : 8;

            int flags       = header[5] & 0xFF;
            int compression = header[6] & 0xFF;
            boolean bare = (flags & 0x01) != 0;

            byte[] vbuf = new byte[15];
            raf.readFully(vbuf);
            int[] vp = {0};
            int blockSize = readVarint(vbuf, vp);
            int N         = readVarint(vbuf, vp);
            int M         = readVarint(vbuf, vp);

            long dirStart = 7 + vp[0];
            int numBlocks = (M + blockSize - 1) / blockSize;

            raf.seek(dirStart);
            byte[] dirBuf = new byte[numBlocks * offsetWidth];
            raf.readFully(dirBuf);
            long[] offsets = new long[numBlocks];
            for (int b = 0; b < numBlocks; b++) {
                offsets[b] = (offsetWidth == 4)
                    ? readU32LE(dirBuf, b * 4)
                    : readU64LE(dirBuf, b * 8);
            }

            return new PbinFile(raf, fileLen, M, N, blockSize, compression, bare, offsets);
        } catch (IOException | RuntimeException e) {
            raf.close();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    public int size() { return M; }

    public int getN() { return N; }

    public int getBlockSize() { return blockSize; }

    public String get(int index) throws IOException {
        if (index < 0 || index >= M)
            throw new IndexOutOfBoundsException("index " + index + ", size " + M);
        int b = index / blockSize;
        int off = index % blockSize;
        if (b != cachedBlock)
            loadBlock(b);
        return cachedLines[off];
    }

    // ── Parallel-friendly access ────────────────────────────
    //
    // The single-block cache used by get() forces every random-access read to
    // decompress an entire block and decode ALL of its generators (each via an
    // expensive BigInteger factorial decode) just to return one line. The
    // methods below split that work so a caller can do the cheap, inherently
    // serial part (the RandomAccessFile read) on one thread and farm out the
    // expensive decompress + single-entry decode to worker threads.

    public int blockOf(int index) {
        if (index < 0 || index >= M)
            throw new IndexOutOfBoundsException("index " + index + ", size " + M);
        return index / blockSize;
    }

    public int offsetInBlock(int index) {
        if (index < 0 || index >= M)
            throw new IndexOutOfBoundsException("index " + index + ", size " + M);
        return index % blockSize;
    }

    /**
     * Reads the raw (still-compressed) bytes for block {@code b}. This is the
     * only part that touches the shared {@link RandomAccessFile}, so it is NOT
     * thread-safe and must be called from a single thread. The returned array
     * is freshly allocated and never mutated afterwards, so it may be shared
     * read-only with worker threads.
     */
    public byte[] readRawBlock(int b) throws IOException {
        if (b < 0 || b >= numBlocks)
            throw new IndexOutOfBoundsException("block " + b + ", numBlocks " + numBlocks);
        long bStart = offsets[b];
        long bEnd   = (b + 1 < numBlocks) ? offsets[b + 1] : fileLen;
        if (bStart < 0 || bEnd <= bStart || bEnd > fileLen)
            throw new IOException("bad block offset at " + b + ": [" + bStart + ", " + bEnd + ")");
        long blen = bEnd - bStart;
        if (blen > Integer.MAX_VALUE)
            throw new IOException("block " + b + " too large (" + blen + " bytes)");
        raf.seek(bStart);
        byte[] blockData = new byte[(int) blen];
        raf.readFully(blockData);
        return blockData;
    }

    /**
     * Decompresses raw block bytes into the decoded payload. Pure / thread-safe:
     * reads only {@code rawBlock} plus immutable fields and allocates fresh
     * output, so it may be called concurrently from many threads.
     */
    public byte[] decompressBlock(byte[] rawBlock) throws IOException {
        return (compression == 1) ? zlibDecompress(rawBlock) : rawBlock;
    }

    /**
     * Returns the decompressed payload for block {@code blockIndex}, caching by block
     * so randomized generator order does not repeat zlib inflation for the same block.
     * {@code rawBlock} must be the compressed bytes for {@code blockIndex} (from
     * {@link #readRawBlock}); callers on a single producer thread read raw blocks serially.
     */
    public synchronized byte[] getDecompressedPayload(int blockIndex, byte[] rawBlock) throws IOException {
        byte[] cached = decompressedBlockCache.get(blockIndex);
        if (cached != null) {
            return cached;
        }
        byte[] payload = decompressBlock(rawBlock);
        decompressedBlockCache.put(blockIndex, payload);
        return payload;
    }

    /** Number of blocks whose decompressed payloads are currently cached. */
    public synchronized int decompressedBlockCacheSize() {
        return decompressedBlockCache.size();
    }

    /**
     * Decodes a single generator (entry {@code off}) from a decompressed
     * payload produced by {@link #decompressBlock}. Pure / thread-safe.
     * Produces byte-for-byte the same string as {@link #get} would for the
     * corresponding index.
     */
    public String decodeEntry(byte[] payload, int off) {
        int[] bp = {0};
        int count = readVarint(payload, bp);
        if (off < 0 || off >= count)
            throw new IndexOutOfBoundsException("entry " + off + ", count " + count);
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++)
            sizes[i] = readVarint(payload, bp);
        int start = bp[0];
        for (int i = 0; i < off; i++)
            start += sizes[i];
        return decodeGenerator(payload, start, sizes[off], N, bare);
    }

    private void loadBlock(int b) throws IOException {
        byte[] blockData = readRawBlock(b);

        byte[] payload = (compression == 1)
            ? zlibDecompress(blockData) : blockData;

        int[] bp = {0};
        int count = readVarint(payload, bp);
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++)
            sizes[i] = readVarint(payload, bp);

        cachedLines = new String[count];
        for (int i = 0; i < count; i++) {
            cachedLines[i] = decodeGenerator(payload, bp[0], sizes[i], N, bare);
            bp[0] += sizes[i];
        }
        cachedBlock = b;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    // ── varint ──────────────────────────────────────────────

    private static int readVarint(byte[] data, int[] pos) {
        int val = 0, shift = 0;
        while (true) {
            if (pos[0] >= data.length)
                throw new RuntimeException("Truncated varint");
            int b = data[pos[0]++] & 0xFF;
            val |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return val;
    }

    /** Reads an unsigned 32-bit little-endian value as a non-negative long. */
    private static long readU32LE(byte[] data, int pos) {
        return (data[pos] & 0xFFL)
             | ((data[pos + 1] & 0xFFL) << 8)
             | ((data[pos + 2] & 0xFFL) << 16)
             | ((data[pos + 3] & 0xFFL) << 24);
    }

    private static long readU64LE(byte[] data, int pos) {
        return readU32LE(data, pos) | (readU32LE(data, pos + 4) << 32);
    }

    // ── zlib ────────────────────────────────────────────────

    private static byte[] zlibDecompress(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] buf = new byte[8192];
        int cap = Math.max(compressed.length * 4, 8192);
        byte[] out = new byte[cap];
        int len = 0;
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count == 0 && inflater.needsInput()) break;
                if (len + count > out.length) {
                    byte[] grown = new byte[out.length * 2];
                    System.arraycopy(out, 0, grown, 0, len);
                    out = grown;
                }
                System.arraycopy(buf, 0, out, len, count);
                len += count;
            }
        } catch (DataFormatException e) {
            throw new IOException("zlib decompression failed", e);
        } finally {
            inflater.end();
        }
        byte[] result = new byte[len];
        System.arraycopy(out, 0, result, 0, len);
        return result;
    }

    // ── Fenwick tree ────────────────────────────────────────

    private static final class FenwickTree {
        private final int[] t;
        private final int n;
        private final int logn;

        FenwickTree(int n) {
            this.n = n;
            t = new int[n + 1];
            for (int i = 1; i <= n; i++) {
                t[i] += 1;
                int j = i + (i & -i);
                if (j <= n) t[j] += t[i];
            }
            int lg = 0;
            while ((1 << (lg + 1)) <= n) lg++;
            logn = lg;
        }

        int kth(int k) {
            int pos = 0, rem = k + 1;
            for (int i = logn; i >= 0; i--) {
                int nxt = pos + (1 << i);
                if (nxt <= n && t[nxt] < rem) {
                    rem -= t[nxt];
                    pos = nxt;
                }
            }
            return pos + 1;
        }

        void remove(int x) {
            for (int i = x; i <= n; i += i & -i) t[i] -= 1;
        }
    }

    /**
     * Implementation of the factorial-number-system point unpacker.
     * {@code java} = {@link BigInteger}; {@code limb} = pure-Java uint32 limbs;
     * {@code native} = GMP via JNI ({@link PbinFactorialNative}). Default prefers
     * native when the shared library is loaded.
     */
    public enum FactorialDecodeBackend {
        JAVA, LIMB, NATIVE
    }

    private static volatile FactorialDecodeBackend factorialBackend = defaultBackend();

    private static FactorialDecodeBackend defaultBackend() {
        String prop = System.getProperty("pbin.factorial");
        if (prop != null) {
            switch (prop.trim().toLowerCase()) {
                case "java": return FactorialDecodeBackend.JAVA;
                case "limb": return FactorialDecodeBackend.LIMB;
                case "native": return FactorialDecodeBackend.NATIVE;
                default: break;
            }
        }
        return PbinFactorialNative.isAvailable()
                ? FactorialDecodeBackend.NATIVE
                : FactorialDecodeBackend.JAVA;
    }

    public static FactorialDecodeBackend getFactorialDecodeBackend() {
        return factorialBackend;
    }

    public static void setFactorialDecodeBackend(FactorialDecodeBackend backend) {
        if (backend == null) throw new NullPointerException("backend");
        if (backend == FactorialDecodeBackend.NATIVE && !PbinFactorialNative.isAvailable()) {
            throw new IllegalStateException("libpbin_factorial not available");
        }
        factorialBackend = backend;
    }

    /** Baseline: java.math.BigInteger divideAndRemainder loop + Fenwick. */
    static int[] factorialDecodeJava(BigInteger acc, int N, int k) {
        int[] indices = new int[k];
        for (int i = k - 1; i >= 0; i--) {
            int remaining = N - i;
            BigInteger[] qr = acc.divideAndRemainder(BigInteger.valueOf(remaining));
            acc = qr[0];
            indices[i] = qr[1].intValue();
        }
        FenwickTree ft = new FenwickTree(N);
        int[] points = new int[k];
        for (int i = 0; i < k; i++) {
            points[i] = ft.kth(indices[i]);
            ft.remove(points[i]);
        }
        return points;
    }

    /**
     * Pure-Java port of pbin.cpp BigInt: little-endian uint32 limbs with
     * single-limb divmod. Useful fallback / comparison; GMP native is faster.
     */
    static int[] factorialDecodeLimb(byte[] bigEndianUnsigned, int N, int k) {
        int[] words = limbsFromBigEndian(bigEndianUnsigned);
        int nWords = words.length;
        int[] indices = new int[k];
        for (int i = k - 1; i >= 0; i--) {
            int remaining = N - i;
            long rem = 0;
            for (int wi = nWords - 1; wi >= 0; wi--) {
                rem = (rem << 32) | (words[wi] & 0xFFFFFFFFL);
                words[wi] = (int) (rem / remaining);
                rem %= remaining;
            }
            while (nWords > 0 && words[nWords - 1] == 0) nWords--;
            indices[i] = (int) rem;
        }
        FenwickTree ft = new FenwickTree(N);
        int[] points = new int[k];
        for (int i = 0; i < k; i++) {
            points[i] = ft.kth(indices[i]);
            ft.remove(points[i]);
        }
        return points;
    }

    private static int[] limbsFromBigEndian(byte[] data) {
        int start = 0;
        while (start < data.length && data[start] == 0) start++;
        int useful = data.length - start;
        if (useful == 0) return new int[0];
        int nWords = (useful + 3) / 4;
        int[] w = new int[nWords];
        for (int i = 0; i < useful; i++) {
            int fromEnd = useful - 1 - i;
            w[fromEnd / 4] |= (data[start + i] & 0xFF) << (8 * (fromEnd % 4));
        }
        return w;
    }

    static int[] factorialDecode(byte[] bigEndianUnsigned, int N, int k) {
        switch (factorialBackend) {
            case NATIVE:
                return PbinFactorialNative.factorialDecode(bigEndianUnsigned, N, k);
            case LIMB:
                return factorialDecodeLimb(bigEndianUnsigned, N, k);
            case JAVA:
            default:
                return factorialDecodeJava(new BigInteger(1, bigEndianUnsigned), N, k);
        }
    }

    private static String decodeGenerator(byte[] raw, int start, int len,
                                          int N, boolean bare) {
        int end = start + len;
        int[] pos = {start};
        int ncs = readVarint(raw, pos);

        StringBuilder sb = new StringBuilder();
        boolean useBare = bare && ncs == 1;
        if (!useBare) sb.append('[');

        for (int s = 0; s < ncs; s++) {
            if (s > 0) sb.append(',');
            int ncyc = readVarint(raw, pos);
            int[] sizes = new int[ncyc];
            int total = 0;
            for (int j = 0; j < ncyc; j++) {
                sizes[j] = readVarint(raw, pos);
                total += sizes[j];
            }
            int biLen = readVarint(raw, pos);
            int[] points;
            if (total > 0 && biLen > 0) {
                if (pos[0] + biLen > end)
                    throw new RuntimeException("Truncated bigint in generator");
                byte[] biBytes = new byte[biLen];
                System.arraycopy(raw, pos[0], biBytes, 0, biLen);
                pos[0] += biLen;
                points = factorialDecode(biBytes, N, total);
            } else {
                pos[0] += biLen;
                points = new int[0];
            }

            int off = 0;
            for (int j = 0; j < ncyc; j++) {
                sb.append('(');
                for (int k = 0; k < sizes[j]; k++) {
                    if (k > 0) sb.append(',');
                    sb.append(points[off + k]);
                }
                sb.append(')');
                off += sizes[j];
            }
        }
        if (!useBare) sb.append(']');
        return sb.toString();
    }
}
