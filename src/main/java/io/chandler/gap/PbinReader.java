package io.chandler.gap;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Convenience helpers for the PBIN format.
 * For large files use {@link PbinFile} directly.
 *
 * <pre>
 * Usage:
 *   PbinReader &lt;file.pbin&gt;              # dump all generators
 *   PbinReader --bench &lt;file.pbin&gt;      # factorialDecode microbench + e2e
 * </pre>
 */
public class PbinReader {

    /** Materializes every generator into memory. Avoid for very large files. */
    public static List<String> readPbinFile(String filePath) throws IOException {
        try (PbinFile pf = PbinFile.open(filePath)) {
            List<String> lines = new ArrayList<>(pf.size());
            for (int i = 0; i < pf.size(); i++)
                lines.add(pf.get(i));
            return lines;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: PbinReader [--bench] <file.pbin>");
            System.exit(1);
        }
        if ("--bench".equals(args[0])) {
            if (args.length < 2) {
                System.err.println("Usage: PbinReader --bench <file.pbin>");
                System.exit(1);
            }
            runBench(args[1]);
            return;
        }
        try (PbinFile pf = PbinFile.open(args[0])) {
            for (int i = 0; i < pf.size(); i++)
                System.out.println(pf.get(i));
        }
    }

    // ── Benchmark ───────────────────────────────────────────

    private static final class Packed {
        final byte[] bytes;
        final int k;
        Packed(byte[] bytes, int k) { this.bytes = bytes; this.k = k; }
    }

    private static void runBench(String filePath) throws Exception {
        System.out.println("=== PBIN factorialDecode benchmark ===");
        System.out.println("file: " + filePath);
        System.out.println("native lib: " + (PbinFactorialNative.isAvailable() ? "loaded" : "NOT LOADED"));

        List<Packed> samples;
        int N;
        try (PbinFile pf = PbinFile.open(filePath)) {
            N = pf.getN();
            samples = extractPacked(pf);
            System.out.printf("N=%d  generators=%d  factorial packs=%d%n",
                    N, pf.size(), samples.size());
        }

        long totalBytes = 0;
        int minK = Integer.MAX_VALUE, maxK = 0, minB = Integer.MAX_VALUE, maxB = 0;
        for (Packed p : samples) {
            totalBytes += p.bytes.length;
            minK = Math.min(minK, p.k);
            maxK = Math.max(maxK, p.k);
            minB = Math.min(minB, p.bytes.length);
            maxB = Math.max(maxB, p.bytes.length);
        }
        System.out.printf("k: [%d, %d]  bigint bytes: [%d, %d]  mean=%.1f%n",
                minK, maxK, minB, maxB, totalBytes / (double) samples.size());

        // Correctness: first few samples must agree across backends.
        int check = Math.min(8, samples.size());
        System.out.println("\n-- correctness (first " + check + " packs) --");
        for (int i = 0; i < check; i++) {
            Packed p = samples.get(i);
            int[] javaPts = PbinFile.factorialDecodeJava(new BigInteger(1, p.bytes), N, p.k);
            int[] limbPts = PbinFile.factorialDecodeLimb(p.bytes, N, p.k);
            if (!Arrays.equals(javaPts, limbPts)) {
                throw new AssertionError("limb mismatch at sample " + i);
            }
            if (PbinFactorialNative.isAvailable()) {
                int[] nativePts = PbinFactorialNative.factorialDecode(p.bytes, N, p.k);
                if (!Arrays.equals(javaPts, nativePts)) {
                    throw new AssertionError("native mismatch at sample " + i);
                }
            }
        }
        System.out.println("OK: java / limb" + (PbinFactorialNative.isAvailable() ? " / native" : "") + " agree");

        // Microbench: decode every packed bigint once per iteration.
        final int warmup = 2;
        final int iters = 5;
        System.out.printf("%n-- microbench: decode all %d packs, warmup=%d iters=%d --%n",
                samples.size(), warmup, iters);

        benchMicro("java BigInteger", samples, N, warmup, iters, (bytes, n, k) ->
                PbinFile.factorialDecodeJava(new BigInteger(1, bytes), n, k));

        benchMicro("java limb", samples, N, warmup, iters, (bytes, n, k) ->
                PbinFile.factorialDecodeLimb(bytes, n, k));

        if (PbinFactorialNative.isAvailable()) {
            benchMicro("native JNI", samples, N, warmup, iters,
                    PbinFactorialNative::factorialDecode);

            // Reuse a max-sized out buffer to skip jintArray allocation.
            int maxKLocal = maxK;
            int[] outBuf = new int[maxKLocal];
            benchMicro("native JNI (into)", samples, N, warmup, iters, (bytes, n, k) -> {
                PbinFactorialNative.factorialDecodeInto(bytes, n, k, outBuf);
                return outBuf;
            });
        }

        // End-to-end: full PbinFile scan (decompress + decode + string build).
        System.out.printf("%n-- end-to-end: scan all generators via PbinFile.get --%n");
        for (PbinFile.FactorialDecodeBackend backend : e2eBackends()) {
            PbinFile.setFactorialDecodeBackend(backend);
            // warmup
            e2eScan(filePath);
            long best = Long.MAX_VALUE;
            long checksum = 0;
            for (int i = 0; i < iters; i++) {
                long t0 = System.nanoTime();
                checksum = e2eScan(filePath);
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                best = Math.min(best, ms);
            }
            System.out.printf("  %-14s  best=%5d ms  checksum=%d%n",
                    backend.name().toLowerCase(), best, checksum);
        }
    }

    private interface Decoder {
        int[] decode(byte[] bytes, int N, int k);
    }

    private static void benchMicro(String name, List<Packed> samples, int N,
                                   int warmup, int iters, Decoder dec) {
        for (int w = 0; w < warmup; w++) {
            for (Packed p : samples) dec.decode(p.bytes, N, p.k);
        }
        long bestNs = Long.MAX_VALUE;
        long sumPts = 0;
        for (int it = 0; it < iters; it++) {
            long t0 = System.nanoTime();
            long local = 0;
            for (Packed p : samples) {
                int[] pts = dec.decode(p.bytes, N, p.k);
                // Touch result so JIT cannot DCE the decode.
                local += pts[0] + pts[pts.length - 1];
            }
            long dt = System.nanoTime() - t0;
            bestNs = Math.min(bestNs, dt);
            sumPts = local;
        }
        double ms = bestNs / 1_000_000.0;
        double per = bestNs / (double) samples.size() / 1000.0;
        System.out.printf("  %-18s  best=%8.1f ms  (%.1f µs/pack)  touch=%d%n",
                name, ms, per, sumPts);
    }

    private static List<PbinFile.FactorialDecodeBackend> e2eBackends() {
        List<PbinFile.FactorialDecodeBackend> list = new ArrayList<>();
        list.add(PbinFile.FactorialDecodeBackend.JAVA);
        list.add(PbinFile.FactorialDecodeBackend.LIMB);
        if (PbinFactorialNative.isAvailable()) {
            list.add(PbinFile.FactorialDecodeBackend.NATIVE);
        }
        return list;
    }

    private static long e2eScan(String filePath) throws IOException {
        long checksum = 0;
        try (PbinFile pf = PbinFile.open(filePath)) {
            for (int i = 0; i < pf.size(); i++) {
                String s = pf.get(i);
                checksum += s.length() * 131L + s.charAt(0) + s.charAt(s.length() - 1);
            }
        }
        return checksum;
    }

    /** Pull every factorial-packed bigint out of the file's decompressed blocks. */
    private static List<Packed> extractPacked(PbinFile pf) throws IOException {
        List<Packed> out = new ArrayList<>();
        int blockSize = pf.getBlockSize();
        int numBlocks = (pf.size() + blockSize - 1) / blockSize;
        for (int b = 0; b < numBlocks; b++) {
            byte[] raw = pf.readRawBlock(b);
            byte[] payload = pf.decompressBlock(raw);
            int[] pos = {0};
            int count = readVarint(payload, pos);
            int[] sizes = new int[count];
            for (int i = 0; i < count; i++) sizes[i] = readVarint(payload, pos);
            for (int i = 0; i < count; i++) {
                int end = pos[0] + sizes[i];
                extractFromGenerator(payload, pos, end, out);
                pos[0] = end;
            }
        }
        return out;
    }

    private static void extractFromGenerator(byte[] raw, int[] pos, int end,
                                             List<Packed> out) {
        int ncs = readVarint(raw, pos);
        for (int s = 0; s < ncs; s++) {
            int ncyc = readVarint(raw, pos);
            int total = 0;
            for (int j = 0; j < ncyc; j++) total += readVarint(raw, pos);
            int biLen = readVarint(raw, pos);
            if (total > 0 && biLen > 0) {
                if (pos[0] + biLen > end)
                    throw new RuntimeException("Truncated bigint");
                byte[] bi = Arrays.copyOfRange(raw, pos[0], pos[0] + biLen);
                pos[0] += biLen;
                out.add(new Packed(bi, total));
            } else {
                pos[0] += biLen;
            }
        }
    }

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
}
