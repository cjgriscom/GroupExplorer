package io.chandler.gap.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import io.chandler.gap.PbinFile;

/**
 * Compare GPU batch scores and survivor decisions against CPU reference.
 */
public final class CongestionCudaValidate {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: CongestionCudaValidate <input.pbin> [sampleCount] [guardBand]");
            System.exit(1);
        }
        String input = args[0];
        int sampleCount = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        double guardBand = args.length > 2 ? Double.parseDouble(args[2]) : 0.05;

        long[] seeds = {41L, 129L};
        int[] checkpoints = {100, 200, 500, 1000};
        Double[] thresholds = {7.5, 4.9, 4.5, 4.25};
        int nRotations = 6;

        if (!CongestionCuda.isAvailable()) {
            System.err.println("CUDA unavailable");
            System.exit(2);
        }
        System.out.println("Device: " + CongestionCuda.deviceName());

        CongestionEvaluator cpu = new CongestionEvaluator(seeds, checkpoints, thresholds, nRotations);
        CongestionEvaluatorGPU gpu = new CongestionEvaluatorGPU(seeds, checkpoints, thresholds, nRotations, guardBand);

        List<String> lines = new ArrayList<>();
        try (PbinFile pbin = PbinFile.open(input)) {
            int total = Math.min(sampleCount, pbin.size());
            byte[] raw = null;
            int rawB = -1;
            for (int i = 0; i < total; i++) {
                int b = pbin.blockOf(i);
                int off = pbin.offsetInBlock(i);
                if (b != rawB) {
                    raw = pbin.readRawBlock(b);
                    rawB = b;
                }
                lines.add(pbin.decodeEntry(pbin.getDecompressedPayload(b, raw), off));
            }
        }

        CongestionEvaluatorGPU.BatchOutcome outcome = gpu.evaluateBatch(lines);
        CongestionEvaluator.Result[] references = new CongestionEvaluator.Result[lines.size()];
        IntStream.range(0, lines.size()).parallel()
                .forEach(i -> references[i] = cpu.evaluate(lines.get(i)));
        int scoreMismatches = 0;
        int survivorMismatches = 0;
        int maxReport = 10;
        double maxScoreDrift = 0.0;

        for (int i = 0; i < lines.size(); i++) {
            CongestionEvaluator.Result ref = references[i];
            CongestionEvaluator.Result got = outcome.results[i];
            if (ref.survived != got.survived) {
                survivorMismatches++;
                if (survivorMismatches <= maxReport) {
                    System.out.printf("survivor mismatch gen %d: cpu=%s gpu=%s bestCpu=%.4f bestGpu=%.4f%n",
                            i, ref.survived, got.survived, ref.bestScore, got.bestScore);
                }
            }
            for (int c = 0; c < checkpoints.length; c++) {
                if (ref.cells[c] == null && got.cells[c] == null) {
                    continue;
                }
                if (ref.cells[c] == null || got.cells[c] == null) {
                    scoreMismatches++;
                    continue;
                }
                double refMin = minCell(ref.cells[c]);
                double gotMin = minCell(got.cells[c]);
                double drift = Math.abs(refMin - gotMin);
                maxScoreDrift = Math.max(maxScoreDrift, drift);
                if (drift > 0.5) {
                    scoreMismatches++;
                    if (scoreMismatches <= maxReport) {
                        System.out.printf("score drift gen %d cp %d: cpu=%.4f gpu=%.4f%n",
                                i, checkpoints[c], refMin, gotMin);
                    }
                }
            }
        }

        System.out.println("Samples: " + lines.size());
        System.out.println("Guard-band CPU fallbacks: " + outcome.guardBandFallbacks);
        System.out.println("Survivor mismatches (after guard band): " + survivorMismatches);
        System.out.println("Large score drifts (>0.5): " + scoreMismatches);
        System.out.printf("Maximum checkpoint-minimum drift: %.6f%n", maxScoreDrift);
        CongestionCuda.shutdown();
        System.exit(survivorMismatches > 0 ? 1 : 0);
    }

    private static double minCell(String cell) {
        String[] parts = cell.trim().split("\\s+");
        double min = Double.parseDouble(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            min = Math.min(min, Double.parseDouble(parts[i]));
        }
        return min;
    }
}
