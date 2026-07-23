package io.chandler.gap.graph;

import java.io.IOException;

import io.chandler.gap.PbinFile;

/**
 * Stage-level CPU timing for CongestionBatch decode vs evaluate on a PBIN sample.
 */
public final class CongestionProfile {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: CongestionProfile <input.pbin> [sampleCount]");
            System.exit(1);
        }
        String input = args[0];
        int sampleCount = args.length > 1 ? Integer.parseInt(args[1]) : 64;
        long[] seeds = {41L, 129L};
        int[] checkpoints = {100, 200, 500, 1000};
        Double[] thresholds = {7.5, 4.9, 4.5, 4.25};
        int nRotations = 6;

        CongestionEvaluator evaluator = new CongestionEvaluator(seeds, checkpoints, thresholds, nRotations);

        try (PbinFile pbin = PbinFile.open(input)) {
            int total = Math.min(sampleCount, pbin.size());
            System.out.println("Profiling " + total + " generators from " + input);
            System.out.println("PBIN blocks cached after run: (reported below)");

            long decodeNs = 0;
            long evalNs = 0;
            byte[] rawBlock = null;
            int rawBlockId = -1;

            for (int pos = 0; pos < total; pos++) {
                int index = pos;
                int b = pbin.blockOf(index);
                int off = pbin.offsetInBlock(index);
                if (b != rawBlockId) {
                    long t0 = System.nanoTime();
                    rawBlock = pbin.readRawBlock(b);
                    rawBlockId = b;
                    decodeNs += System.nanoTime() - t0;
                }
                long t1 = System.nanoTime();
                byte[] payload = pbin.getDecompressedPayload(b, rawBlock);
                String line = pbin.decodeEntry(payload, off);
                decodeNs += System.nanoTime() - t1;

                long t2 = System.nanoTime();
                evaluator.evaluate(line);
                evalNs += System.nanoTime() - t2;
            }

            System.out.printf("Decode (read+decompress+entry): %.2f ms/gen%n", decodeNs / 1e6 / total);
            System.out.printf("Evaluate (CPU spring+congestion): %.2f ms/gen%n", evalNs / 1e6 / total);
            System.out.printf("Total: %.2f ms/gen (%.2f gen/s)%n",
                    (decodeNs + evalNs) / 1e6 / total, 1e9 / ((decodeNs + evalNs) / (double) total));
            System.out.println("Decompressed PBIN blocks cached: " + pbin.decompressedBlockCacheSize());
        }
    }
}
