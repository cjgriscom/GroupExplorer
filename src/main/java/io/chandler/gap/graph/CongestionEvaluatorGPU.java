package io.chandler.gap.graph;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * GPU batch evaluator with optional CPU guard-band fallback near prune thresholds.
 */
public final class CongestionEvaluatorGPU {

    private final long[] seeds;
    private final int[] checkpoints;
    private final Double[] thresholds;
    private final int nRotations;
    private final int maxCheckpoint;
    private final double guardBand;
    private final double[][][] rotationMatrices;
    private final float[] rotFlat;
    private final float[] thresholdFlat;
    private final CongestionEvaluator cpuEvaluator;

    public CongestionEvaluatorGPU(long[] seeds, int[] checkpoints, Double[] thresholds,
            int nRotations, double guardBand) {
        this.cpuEvaluator = new CongestionEvaluator(seeds, checkpoints, thresholds, nRotations);
        this.seeds = seeds.clone();
        this.checkpoints = checkpoints.clone();
        this.thresholds = thresholds == null ? null : thresholds.clone();
        this.nRotations = nRotations;
        this.maxCheckpoint = checkpoints[checkpoints.length - 1];
        this.guardBand = guardBand;
        this.rotationMatrices = CongestionEvaluator.generateRotationMatrices(nRotations);
        this.rotFlat = CongestionGraphPack.flattenRotations(rotationMatrices);
        this.thresholdFlat = new float[checkpoints.length];
        Arrays.fill(this.thresholdFlat, Float.NaN);
        if (thresholds != null) {
            for (int i = 0; i < Math.min(thresholds.length, checkpoints.length); i++) {
                if (thresholds[i] != null) {
                    this.thresholdFlat[i] = thresholds[i].floatValue();
                }
            }
        }
    }

    public static final class BatchOutcome {
        public final CongestionEvaluator.Result[] results;
        /** Per-graph: GPU score was within {@code guardBand} of a prune threshold. */
        public final boolean[] needsGuardBandFallback;
        public final int guardBandFallbacks;

        BatchOutcome(CongestionEvaluator.Result[] results, boolean[] needsGuardBandFallback) {
            this.results = results;
            this.needsGuardBandFallback = needsGuardBandFallback;
            int n = 0;
            if (needsGuardBandFallback != null) {
                for (boolean b : needsGuardBandFallback) {
                    if (b) n++;
                }
            }
            this.guardBandFallbacks = n;
        }
    }

    /** Pack generators for a later {@link #evaluatePacked} (overlaps with a prior CUDA batch). */
    public CongestionGraphPack.BatchPack packBatch(List<String> lines) {
        return CongestionGraphPack.packBatch(lines, seeds);
    }

    /**
     * GPU batch evaluate; runs guard-band CPU fallback inline (ForkJoin) before
     * returning. Prefer {@link #evaluateBatch(List, boolean)} with
     * {@code runGuardBandCpuInline=false} when a caller can defer to its own CPU pool.
     */
    public BatchOutcome evaluateBatch(List<String> lines) {
        return evaluateBatch(lines, true);
    }

    /**
     * @param runGuardBandCpuInline if true, re-evaluate guard-band hits on the
     *        common ForkJoin pool before return (blocks caller). If false, only
     *        marks {@link BatchOutcome#needsGuardBandFallback}; GPU results for
     *        those graphs are provisional and should not be used for accept/reject.
     */
    public BatchOutcome evaluateBatch(List<String> lines, boolean runGuardBandCpuInline) {
        return evaluatePacked(lines, packBatch(lines), runGuardBandCpuInline);
    }

    /**
     * Run CUDA on a pre-packed batch. Same guard-band semantics as
     * {@link #evaluateBatch(List, boolean)}.
     */
    public BatchOutcome evaluatePacked(List<String> lines, CongestionGraphPack.BatchPack batch,
                                       boolean runGuardBandCpuInline) {
        if (!CongestionCuda.isAvailable()) {
            throw new IllegalStateException("CUDA backend unavailable: " + CongestionCuda.deviceName());
        }
        if (lines.size() != batch.numGraphs) {
            throw new IllegalArgumentException(
                    "lines.size=" + lines.size() + " != pack.numGraphs=" + batch.numGraphs);
        }
        int numGraphs = lines.size();
        int scoreCount = numGraphs * checkpoints.length * seeds.length * nRotations;
        float[] scoresOut = new float[scoreCount];

        int rc = CongestionCuda.evaluateBatch(
                numGraphs, seeds.length, batch.nVertices,
                batch.graphEdgeOffsets, batch.graphEdgeCounts,
                batch.edgeU, batch.edgeV, batch.adjacency,
                batch.initialPos, checkpoints, thresholdFlat, rotFlat, nRotations, maxCheckpoint,
                scoresOut);
        if (rc != 0) {
            throw new IllegalStateException("CUDA evaluateBatch failed with code " + rc);
        }

        CongestionEvaluator.Result[] results = new CongestionEvaluator.Result[numGraphs];
        boolean[] fallback = new boolean[numGraphs];
        for (int g = 0; g < numGraphs; g++) {
            results[g] = resultFromGpuScores(g, scoresOut);
            if (needsGuardBandFallback(results[g])) {
                fallback[g] = true;
            }
        }
        if (runGuardBandCpuInline) {
            int fallbacks = 0;
            for (boolean needed : fallback) {
                if (needed) fallbacks++;
            }
            if (fallbacks > 0) {
                IntStream.range(0, numGraphs).parallel()
                        .filter(g -> fallback[g])
                        .forEach(g -> results[g] = cpuEvaluator.evaluate(lines.get(g)));
            }
        }
        return new BatchOutcome(results, fallback);
    }

    private CongestionEvaluator.Result resultFromGpuScores(int graphIndex, float[] scoresOut) {
        String[] cells = new String[checkpoints.length];
        boolean pruned = false;
        double bestScore = Double.NaN;

        for (int c = 0; c < checkpoints.length; c++) {
            if (pruned) {
                break;
            }
            int base = ((graphIndex * checkpoints.length + c) * seeds.length) * nRotations;
            double[] scores = new double[seeds.length * nRotations];
            for (int i = 0; i < scores.length; i++) {
                scores[i] = scoresOut[base + i];
            }
            cells[c] = CongestionEvaluator.formatScores(
                    Arrays.copyOf(scores, scores.length), scores.length);
            bestScore = scores[0];
            for (int i = 1; i < scores.length; i++) {
                if (scores[i] < bestScore) {
                    bestScore = scores[i];
                }
            }
            Double threshold = (thresholds != null && c < thresholds.length) ? thresholds[c] : null;
            if (threshold != null && bestScore >= threshold) {
                pruned = true;
            }
        }
        return new CongestionEvaluator.Result(cells, !pruned, bestScore);
    }

    private boolean needsGuardBandFallback(CongestionEvaluator.Result gpuResult) {
        if (guardBand <= 0.0 || thresholds == null) {
            return false;
        }
        for (int c = 0; c < checkpoints.length; c++) {
            if (gpuResult.cells[c] == null) {
                break;
            }
            Double threshold = c < thresholds.length ? thresholds[c] : null;
            if (threshold == null) {
                continue;
            }
            double minAtCp = minScoreFromCell(gpuResult.cells[c]);
            if (Math.abs(minAtCp - threshold) <= guardBand) {
                return true;
            }
        }
        return false;
    }

    private static double minScoreFromCell(String cell) {
        String[] parts = cell.trim().split("\\s+");
        double min = Double.parseDouble(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            min = Math.min(min, Double.parseDouble(parts[i]));
        }
        return min;
    }
}
