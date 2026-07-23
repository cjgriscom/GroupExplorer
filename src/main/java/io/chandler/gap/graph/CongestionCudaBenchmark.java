package io.chandler.gap.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.chandler.gap.PbinFile;

/**
 * Benchmark CPU vs GPU throughput on a PBIN sample and project full-dataset runtime.
 */
public final class CongestionCudaBenchmark {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: CongestionCudaBenchmark <input.pbin> [sampleCount] [batchSize] [totalGenerators] [gpuOnly] [guardBand]");
            System.exit(1);
        }
        String input = args[0];
        int sampleCount = args.length > 1 ? Integer.parseInt(args[1]) : 128;
        int gpuBatch = args.length > 2 ? Integer.parseInt(args[2]) : 32;
        long totalGenerators = args.length > 3 ? Long.parseLong(args[3]) : -1L;
        boolean gpuOnly = args.length > 4 && Boolean.parseBoolean(args[4]);
        double guardBand = args.length > 5 ? Double.parseDouble(args[5]) : 0.1;

        long[] seeds = {41L, 129L};
        int[] checkpoints = {100, 200, 500, 1000};
        Double[] thresholds = {7.5, 4.9, 4.5, 4.25};
        int nRotations = 6;
        CongestionEvaluator cpu = new CongestionEvaluator(seeds, checkpoints, thresholds, nRotations);

        List<String> lines = new ArrayList<>();
        long fileTotal;
        try (PbinFile pbin = PbinFile.open(input)) {
            fileTotal = pbin.size();
            if (totalGenerators < 0) {
                totalGenerators = fileTotal;
            }
            int n = Math.min(sampleCount, pbin.size());
            byte[] raw = null;
            int rawB = -1;
            for (int i = 0; i < n; i++) {
                int b = pbin.blockOf(i);
                int off = pbin.offsetInBlock(i);
                if (b != rawB) {
                    raw = pbin.readRawBlock(b);
                    rawB = b;
                }
                lines.add(pbin.decodeEntry(pbin.getDecompressedPayload(b, raw), off));
            }
        }

        System.out.println("Sample size: " + lines.size() + " | file generators: " + fileTotal);
        System.out.println("Checkpoints: " + java.util.Arrays.toString(checkpoints)
                + " | seeds: " + java.util.Arrays.toString(seeds)
                + " | rotations: " + nRotations);

        double cpuMsPerGen = -1.0;
        if (!gpuOnly) {
            long t0 = System.nanoTime();
            for (String line : lines) {
                cpu.evaluate(line);
            }
            long cpuNs = System.nanoTime() - t0;
            cpuMsPerGen = cpuNs / 1e6 / lines.size();
            System.out.printf("CPU: %.2f ms/gen (%.2f gen/s)%n", cpuMsPerGen, 1000.0 / cpuMsPerGen);
        }

        if (!CongestionCuda.isAvailable()) {
            System.out.println("GPU: unavailable");
            project(totalGenerators, cpuMsPerGen, -1);
            return;
        }
        System.out.println("GPU device: " + CongestionCuda.deviceName());

        CongestionEvaluatorGPU gpu = new CongestionEvaluatorGPU(seeds, checkpoints, thresholds, nRotations, guardBand);
        // warmup
        gpu.evaluateBatch(lines.subList(0, Math.min(4, lines.size())));

        long t1 = System.nanoTime();
        int fallbacks = 0;
        for (int start = 0; start < lines.size(); start += gpuBatch) {
            int end = Math.min(start + gpuBatch, lines.size());
            CongestionEvaluatorGPU.BatchOutcome outcome = gpu.evaluateBatch(lines.subList(start, end));
            fallbacks += outcome.guardBandFallbacks;
        }
        long gpuNs = System.nanoTime() - t1;
        double gpuMsPerGen = gpuNs / 1e6 / lines.size();
        System.out.printf("GPU batch=%d: %.2f ms/gen (%.2f gen/s) fallbacks=%d%n",
                gpuBatch, gpuMsPerGen, 1000.0 / gpuMsPerGen, fallbacks);
        if (cpuMsPerGen > 0) {
            System.out.printf("Speedup: %.1fx%n", cpuMsPerGen / gpuMsPerGen);
        }
        project(totalGenerators, cpuMsPerGen, gpuMsPerGen);
        CongestionCuda.shutdown();
    }

    private static void project(long total, double cpuMs, double gpuMs) {
        System.out.println("--- Projection for " + total + " generators ---");
        if (cpuMs > 0) {
            double cpuHours = total * cpuMs / 1000.0 / 3600.0;
            System.out.printf("CPU ETA: %.1f hours%n", cpuHours);
        }
        if (gpuMs > 0) {
            double gpuHours = total * gpuMs / 1000.0 / 3600.0;
            System.out.printf("GPU ETA: %.1f hours%n", gpuHours);
        }
    }
}
