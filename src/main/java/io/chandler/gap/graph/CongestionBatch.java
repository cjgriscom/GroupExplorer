package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.PbinFile;
import io.chandler.gap.graph.layoutalgos.LayoutCongestion;
import networkx.SpringLayout;
import networkx.SpringLayout.SpringLayoutState;

/**
 * Headless batch congestion evaluator. Streams generators from a PBIN (or text)
 * file in batches, runs incremental Java Networkx 3D spring layout at checkpoints,
 * scores congestion under multiple seeds and 3D rotations, optionally prunes by
 * threshold, writes CSV plot data incrementally, and writes surviving generators
 * sorted by best final congestion score after printing statistics.
 */
public class CongestionBatch {

    private static final double BOX_SIZE = 1000.0;
    private static final int LAYOUT_DIM = 3;
    private static final long ROTATION_RNG_SEED = 42L;
    /** Congestion scores and thresholds both use this unit scale (raw layout score × 1000). */
    private static final double SCORE_SCALE = 1000.0;

    private final String inputFile;
    private final int batchSize;
    private final int threads;
    private final long[] seeds;
    private final int[] checkpoints;
    private final Double[] thresholds;
    private final int nRotations;
    private final String plotFile;
    private final String outputFile;
    private final boolean randomize;
    private final long shuffleSeed;
    private final int maxCheckpoint;
    private final double[][][] rotationMatrices;

    private final Object csvLock = new Object();
    private boolean csvHeaderWritten = false;

    /** Best congestion (min over seeds/rotations) at last evaluated checkpoint, indexed by generator. */
    private final List<Double> bestScores = new ArrayList<>();
    /** Whether generator survived all threshold pruning. */
    private final List<Boolean> survivedFlags = new ArrayList<>();

    private long runStartTimeMs;
    private int survivorCount;

    public CongestionBatch(String inputFile, int batchSize, int threads, long[] seeds, int[] checkpoints,
            Double[] thresholds, int nRotations, String plotFile, String outputFile,
            boolean randomize, long shuffleSeed) {
        this.inputFile = inputFile;
        this.batchSize = batchSize;
        this.threads = threads;
        this.seeds = seeds;
        this.checkpoints = checkpoints;
        this.thresholds = thresholds;
        this.nRotations = nRotations;
        this.plotFile = plotFile;
        this.outputFile = outputFile;
        this.randomize = randomize;
        this.shuffleSeed = shuffleSeed;
        this.maxCheckpoint = checkpoints[checkpoints.length - 1];
        this.rotationMatrices = generateRotationMatrices(nRotations);
    }

    public void run() throws IOException {
        System.out.println("CongestionBatch starting...");
        System.out.println("  Input: " + inputFile);
        System.out.println("  Batch size: " + batchSize);
        System.out.println("  Threads: " + threads);
        System.out.println("  Seeds: " + Arrays.toString(seeds));
        System.out.println("  Checkpoints: " + Arrays.toString(checkpoints));
        if (thresholds != null) {
            System.out.println("  Thresholds: " + Arrays.toString(thresholds));
        }
        System.out.println("  Rotations: " + nRotations);
        System.out.println("  Score units: layout congestion × " + (int) SCORE_SCALE);
        System.out.println("  Randomize order: " + randomize
                + (randomize ? " (seed " + shuffleSeed + ")" : ""));
        System.out.println("  Plot file: " + (plotFile != null ? plotFile : "(none)"));
        System.out.println("  Output file: " + (outputFile != null ? outputFile : "(none)"));
        System.out.println();

        if (plotFile != null) {
            Path plotPath = Paths.get(plotFile);
            if (plotPath.getParent() != null) {
                Files.createDirectories(plotPath.getParent());
            }
        }
        if (outputFile != null) {
            Path outPath = Paths.get(outputFile);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
        }

        runStartTimeMs = System.currentTimeMillis();

        String lower = inputFile.toLowerCase();
        if (lower.endsWith(".pbin")) {
            runPbin();
        } else {
            runText();
        }

        finalizeAndWriteSurvivors();

        System.out.println("\nCongestionBatch finished.");
    }

    private void runPbin() throws IOException {
        try (PbinFile pbin = PbinFile.open(inputFile)) {
            int total = pbin.size();
            System.out.println("  Generators: " + total);
            ensureScoreCapacity(total);

            List<Integer> order = buildProcessingOrder(total);
            int prefetch = Math.max(batchSize, threads * 4);
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            // Producer-side 1-block raw cache. The serial RandomAccessFile read
            // is the only part that must stay on this thread; caching the last
            // block keeps the sequential path from re-reading the same block
            // once per generator. Raw blocks are immutable once read, so they
            // can be shared read-only with the worker that decodes them.
            int[] cachedRawBlock = { -1 };
            byte[][] cachedRawBytes = { null };

            try {
                slidingWindowProcess(executor, total, prefetch,
                        pos -> {
                            int index = order.get(pos);
                            int b = pbin.blockOf(index);
                            int off = pbin.offsetInBlock(index);
                            if (b != cachedRawBlock[0]) {
                                cachedRawBytes[0] = pbin.readRawBlock(b);
                                cachedRawBlock[0] = b;
                            }
                            byte[] raw = cachedRawBytes[0];
                            // Expensive work (zlib decompress + single-entry
                            // BigInteger decode) runs on the worker thread, in
                            // parallel, instead of on this producer thread.
                            return executor.submit(() -> {
                                byte[] payload = pbin.decompressBlock(raw);
                                String line = pbin.decodeEntry(payload, off);
                                return processGenerator(index, line);
                            });
                        });
            } finally {
                executor.shutdown();
                executor.awaitTermination(365, TimeUnit.DAYS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for batch executor", e);
        }
    }

    private List<Integer> buildProcessingOrder(int total) {
        List<Integer> order = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            order.add(i);
        }
        if (randomize) {
            Collections.shuffle(order, new Random(shuffleSeed));
        }
        return order;
    }

    private void runText() throws IOException {
        if (randomize) {
            runTextShuffled();
            return;
        }

        List<BatchEntry> allEntries = loadAllTextEntries();
        int total = allEntries.size();
        System.out.println("  Generators: " + total);
        ensureScoreCapacity(total);

        int prefetch = Math.max(batchSize, threads * 4);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            slidingWindowProcess(executor, total, prefetch,
                    pos -> {
                        BatchEntry entry = allEntries.get(pos);
                        return executor.submit(() -> processGenerator(entry.index, entry.line));
                    });
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(365, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for batch executor", e);
            }
        }
    }

    private void runTextShuffled() throws IOException {
        List<BatchEntry> allEntries = loadAllTextEntries();
        System.out.println("  Generators: " + allEntries.size());
        ensureScoreCapacity(allEntries.size());

        List<BatchEntry> orderedEntries = orderEntries(allEntries);
        int total = orderedEntries.size();

        int prefetch = Math.max(batchSize, threads * 4);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            slidingWindowProcess(executor, total, prefetch,
                    pos -> {
                        BatchEntry entry = orderedEntries.get(pos);
                        return executor.submit(() -> processGenerator(entry.index, entry.line));
                    });
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(365, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for batch executor", e);
            }
        }
    }

    private List<BatchEntry> loadAllTextEntries() throws IOException {
        List<BatchEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                entries.add(new BatchEntry(index, line));
                index++;
            }
        }
        return entries;
    }

    private List<BatchEntry> orderEntries(List<BatchEntry> entries) {
        if (!randomize) {
            return entries;
        }
        List<BatchEntry> copy = new ArrayList<>(entries);
        Collections.shuffle(copy, new Random(shuffleSeed));
        return copy;
    }

    @FunctionalInterface
    private interface TaskSubmitter {
        Future<GeneratorResult> submit(int position) throws IOException;
    }

    /**
     * Maintains a sliding window of in-flight futures so the thread pool
     * is always saturated. Tasks are submitted via the supplier as window
     * slots open up, and results are drained/flushed in batchSize chunks.
     */
    private void slidingWindowProcess(ExecutorService executor, int total,
            int prefetch, TaskSubmitter submitter) throws IOException {
        List<Future<GeneratorResult>> window = new ArrayList<>(prefetch);
        int nextSubmit = 0;
        int processed = 0;

        int initialFill = Math.min(total, prefetch);
        for (int i = 0; i < initialFill; i++) {
            window.add(submitter.submit(nextSubmit++));
        }

        int drainCursor = 0;
        while (drainCursor < window.size()) {
            int chunkEnd = Math.min(drainCursor + batchSize, window.size());
            List<GeneratorResult> chunk = new ArrayList<>(chunkEnd - drainCursor);
            for (int i = drainCursor; i < chunkEnd; i++) {
                try {
                    chunk.add(window.get(i).get());
                } catch (Exception e) {
                    throw new IOException("Batch processing failed", e);
                }
                window.set(i, null);
            }
            Collections.sort(chunk, (a, b) -> Integer.compare(a.index, b.index));
            processed += chunk.size();
            flushBatchResults(chunk, processed, total);
            drainCursor = chunkEnd;

            while (nextSubmit < total && (window.size() - drainCursor) < prefetch) {
                window.add(submitter.submit(nextSubmit++));
            }
        }
    }

    private void flushBatchResults(List<GeneratorResult> results, int processed, int total)
            throws IOException {
        if (plotFile != null) {
            appendCsvRows(results);
        }
        recordBatchScores(results);

        if (total > 0) {
            String eta = formatEstimatedRemaining(processed, total);
            System.out.println("Processed " + processed + " / " + total
                    + " | Survivors: " + survivorCount + " / " + processed
                    + " | Estimated time remaining: " + eta);
        } else {
            System.out.println("Processed " + processed
                    + " | Survivors: " + survivorCount + " / " + processed);
        }
    }

    private String formatEstimatedRemaining(int completed, int total) {
        if (completed >= total) {
            return "0 seconds";
        }
        if (completed <= 0) {
            return "calculating...";
        }
        long elapsedMs = System.currentTimeMillis() - runStartTimeMs;
        long remainingMs = (long) ((double) elapsedMs / completed * (total - completed));
        return formatDuration(remainingMs);
    }

    private static String formatDuration(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        long seconds = (ms % 60000) / 1000;
        return String.format("%d hours, %d minutes, %d seconds", hours, minutes, seconds);
    }

    private void ensureScoreCapacity(int size) {
        while (bestScores.size() < size) {
            bestScores.add(Double.NaN);
            survivedFlags.add(false);
        }
    }

    private void recordBatchScores(List<GeneratorResult> results) {
        for (GeneratorResult result : results) {
            ensureScoreCapacity(result.index + 1);
            bestScores.set(result.index, result.bestScore);
            survivedFlags.set(result.index, result.survived);
            if (result.survived) {
                survivorCount++;
            }
        }
    }

    private void finalizeAndWriteSurvivors() throws IOException {
        List<Integer> survivorIndices = new ArrayList<>();
        for (int i = 0; i < bestScores.size(); i++) {
            if (survivedFlags.get(i)) {
                survivorIndices.add(i);
            }
        }

        survivorIndices.sort((a, b) -> Double.compare(bestScores.get(a), bestScores.get(b)));

        printStatistics(survivorIndices);

        if (outputFile != null && !survivorIndices.isEmpty()) {
            writeSurvivorsSorted(survivorIndices);
            System.out.println("Wrote " + survivorIndices.size() + " survivors to " + outputFile);
        } else if (outputFile != null) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(outputFile, false))) {
                // create empty output file
            }
            System.out.println("No survivors; wrote empty " + outputFile);
        }
    }

    private void printStatistics(List<Integer> survivorIndices) {
        System.out.println();
        System.out.println("=== Congestion Statistics (survivors, sorted by best final score) ===");
        System.out.println("  Total generators processed: " + bestScores.size());
        System.out.println("  Survivors: " + survivorIndices.size());
        System.out.println("  Pruned: " + (bestScores.size() - survivorIndices.size()));

        if (survivorIndices.isEmpty()) {
            System.out.println("  No survivors to summarize.");
            return;
        }

        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int minIndex = -1;
        int maxIndex = -1;

        for (int index : survivorIndices) {
            double score = bestScores.get(index);
            sum += score;
            if (score < min) {
                min = score;
                minIndex = index;
            }
            if (score > max) {
                max = score;
                maxIndex = index;
            }
        }

        double avg = sum / survivorIndices.size();
        System.out.printf("  Avg best score: %.6f%n", avg);
        System.out.printf("  Min best score: %.6f (index %d)%n", min, minIndex);
        System.out.printf("  Max best score: %.6f (index %d)%n", max, maxIndex);

        int preview = Math.min(10, survivorIndices.size());
        System.out.println("  Lowest " + preview + " by score:");
        for (int i = 0; i < preview; i++) {
            int index = survivorIndices.get(i);
            System.out.printf("    [%d] %.6f%n", index, bestScores.get(index));
        }
    }

    private void writeSurvivorsSorted(List<Integer> survivorIndices) throws IOException {
        String lower = inputFile.toLowerCase();
        if (lower.endsWith(".pbin")) {
            try (PbinFile pbin = PbinFile.open(inputFile);
                    BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {
                for (int index : survivorIndices) {
                    writer.write(pbin.get(index));
                    writer.newLine();
                }
            }
        } else {
            Map<Integer, String> linesByIndex = loadTextLinesByIndex(new java.util.HashSet<>(survivorIndices));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {
                for (int index : survivorIndices) {
                    String line = linesByIndex.get(index);
                    if (line != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
        }
    }

    private Map<Integer, String> loadTextLinesByIndex(java.util.Set<Integer> indices) throws IOException {
        Map<Integer, String> lines = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (indices.contains(index)) {
                    lines.put(index, line);
                    if (lines.size() == indices.size()) {
                        break;
                    }
                }
                index++;
            }
        }
        return lines;
    }

    private GeneratorResult processGenerator(int index, String line) {
        Graph<Integer, DefaultEdge> graph = buildGraphFromLine(line);
        networkx.Graph nxGraph = buildNetworkxGraph(graph);
        int vertexCount = graph.vertexSet().size();
        double[][] positions3d = new double[vertexCount][LAYOUT_DIM];
        double[][] projected2d = new double[vertexCount][2];

        SpringLayoutState[] layoutStates = new SpringLayoutState[seeds.length];
        List<Integer> nodeIds = null;
        for (int s = 0; s < seeds.length; s++) {
            layoutStates[s] = SpringLayout.createState(nxGraph, LAYOUT_DIM, seeds[s], maxCheckpoint);
            if (s == 0) {
                nodeIds = SpringLayout.getNodeIds(layoutStates[s]);
            }
        }

        int scoresPerCheckpoint = seeds.length * nRotations;
        double[] scores = new double[scoresPerCheckpoint];
        String[] cells = new String[checkpoints.length];
        boolean pruned = false;
        double bestScore = Double.NaN;

        for (int c = 0; c < checkpoints.length; c++) {
            if (pruned) {
                break;
            }

            int checkpoint = checkpoints[c];
            int si = 0;

            for (int s = 0; s < seeds.length; s++) {
                SpringLayout.advance(layoutStates[s], checkpoint);
                SpringLayout.copyPositions(layoutStates[s], positions3d);
                norm3DArray(positions3d, BOX_SIZE);

                for (int r = 0; r < nRotations; r++) {
                    projectRotated(positions3d, projected2d, rotationMatrices[r]);
                    scores[si++] = LayoutCongestion.compute(graph, nodeIds, projected2d) * SCORE_SCALE;
                }
            }

            cells[c] = formatScores(scores, si);
            bestScore = scores[0];
            for (int i = 1; i < si; i++) {
                if (scores[i] < bestScore) bestScore = scores[i];
            }

            Double threshold = (thresholds != null && c < thresholds.length) ? thresholds[c] : null;
            if (threshold != null) {
                if (bestScore >= threshold) {
                    pruned = true;
                }
            }
        }

        return new GeneratorResult(index, cells, !pruned, bestScore);
    }

    private void appendCsvRows(List<GeneratorResult> results) throws IOException {
        synchronized (csvLock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(plotFile, csvHeaderWritten))) {
                if (!csvHeaderWritten) {
                    writer.write("generator_index");
                    for (int checkpoint : checkpoints) {
                        writer.write(",checkpoint_" + checkpoint);
                    }
                    writer.newLine();
                    csvHeaderWritten = true;
                }

                for (GeneratorResult result : results) {
                    writer.write(Integer.toString(result.index));
                    for (int c = 0; c < checkpoints.length; c++) {
                        writer.write(',');
                        if (result.cells[c] != null) {
                            writer.write(result.cells[c]);
                        }
                    }
                    writer.newLine();
                }
            }
        }
    }

    private static String formatScores(double[] scores, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%.6f", scores[i]));
        }
        return sb.toString();
    }

    private static Graph<Integer, DefaultEdge> buildGraphFromLine(String line) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        int[][][] combinedGen = GroupExplorer.parseOperationsArr(line);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                for (int i = 0; i < polygon.length; i++) {
                    int a = polygon[i];
                    int b = polygon[(i + 1) % polygon.length];
                    graph.addEdge(a, b);
                }
            }
        }
        return graph;
    }

    private static networkx.Graph buildNetworkxGraph(Graph<Integer, DefaultEdge> graph) {
        networkx.Graph nxGraph = new networkx.Graph();
        for (DefaultEdge edge : graph.edgeSet()) {
            nxGraph.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge));
        }
        return nxGraph;
    }

    private static void norm3DArray(double[][] positions, double boxSize) {
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        for (double[] pos : positions) {
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minY = Math.min(minY, pos[1]);
            maxY = Math.max(maxY, pos[1]);
            minZ = Math.min(minZ, pos[2]);
            maxZ = Math.max(maxZ, pos[2]);
        }
        double maxScale = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        for (double[] pos : positions) {
            if (maxX - minX > 0) {
                pos[0] = (pos[0] - minX) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
            } else {
                pos[0] = 0.5 * boxSize;
            }
            if (maxY - minY > 0) {
                pos[1] = (pos[1] - minY) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
            } else {
                pos[1] = 0.5 * boxSize;
            }
            if (maxZ - minZ > 0) {
                pos[2] = (pos[2] - minZ) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
            } else {
                pos[2] = 0.5 * boxSize;
            }
        }
    }

    private static void projectRotated(double[][] positions3d, double[][] projected2d, double[][] rotation) {
        for (int i = 0; i < positions3d.length; i++) {
            double x = positions3d[i][0];
            double y = positions3d[i][1];
            double z = positions3d[i][2];
            projected2d[i][0] = rotation[0][0] * x + rotation[0][1] * y + rotation[0][2] * z;
            projected2d[i][1] = rotation[1][0] * x + rotation[1][1] * y + rotation[1][2] * z;
        }
    }

    private static double[][][] generateRotationMatrices(int count) {
        double[][][] rotations = new double[count][3][3];
        Random rotRng = new Random(ROTATION_RNG_SEED);
        for (int i = 0; i < count; i++) {
            double ax = rotRng.nextGaussian();
            double ay = rotRng.nextGaussian();
            double az = rotRng.nextGaussian();
            double norm = Math.sqrt(ax * ax + ay * ay + az * az);
            if (norm < 1e-12) {
                ax = 1.0;
                ay = 0.0;
                az = 0.0;
                norm = 1.0;
            }
            ax /= norm;
            ay /= norm;
            az /= norm;
            double angle = rotRng.nextDouble() * 2.0 * Math.PI;
            rotations[i] = rotationFromAxisAngle(ax, ay, az, angle);
        }
        return rotations;
    }

    private static double[][] rotationFromAxisAngle(double ax, double ay, double az, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double t = 1.0 - c;
        double[][] r = new double[3][3];
        r[0][0] = t * ax * ax + c;
        r[0][1] = t * ax * ay - s * az;
        r[0][2] = t * ax * az + s * ay;
        r[1][0] = t * ay * ax + s * az;
        r[1][1] = t * ay * ay + c;
        r[1][2] = t * ay * az - s * ax;
        r[2][0] = t * az * ax - s * ay;
        r[2][1] = t * az * ay + s * ax;
        r[2][2] = t * az * az + c;
        return r;
    }

    private static long[] parseLongList(String value) {
        String[] parts = value.split(",");
        long[] result = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Long.parseLong(parts[i].trim());
        }
        return result;
    }

    private static int[] parseIntList(String value) {
        String[] parts = value.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    private static Double[] parseDoubleList(String value) {
        String[] parts = value.split(",");
        Double[] result = new Double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }

    private static void printUsage() {
        System.err.println("Usage: CongestionBatch [options] <input.pbin|input.txt>");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --batch-size N       Generators per reporting batch (default: 32)");
        System.err.println("  --threads N          Worker thread count (default: available processors)");
        System.err.println("  --seeds a,b,c        Layout seeds (default: 0)");
        System.err.println("  --checkpoints a,b,c  Iteration checkpoints (default: 500)");
        System.err.println("  --thresholds a,b,c   Prune thresholds per checkpoint (same units as CSV scores)");
        System.err.println("  --n-rotations N      Deterministic 3D rotations per checkpoint (default: 1)");
        System.err.println("  --plot-file PATH     CSV output path");
        System.err.println("  --output-file PATH   Text output for surviving generators");
        System.err.println("  --randomize          Shuffle processing order (all generators still run)");
        System.err.println("  --shuffle-seed N     RNG seed for --randomize (default: 42)");
    }

    public static void main(String[] args) {
        int batchSize = 32;
        int threads = Runtime.getRuntime().availableProcessors();
        long[] seeds = new long[]{0L};
        int[] checkpoints = new int[]{500};
        Double[] thresholds = null;
        int nRotations = 1;
        String plotFile = null;
        String outputFile = null;
        String inputFile = null;
        boolean randomize = false;
        long shuffleSeed = 42L;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--batch-size":
                    batchSize = Integer.parseInt(requireArg(args, ++i, arg));
                    break;
                case "--threads":
                    threads = Integer.parseInt(requireArg(args, ++i, arg));
                    break;
                case "--seeds":
                    seeds = parseLongList(requireArg(args, ++i, arg));
                    break;
                case "--checkpoints":
                    checkpoints = parseIntList(requireArg(args, ++i, arg));
                    break;
                case "--thresholds":
                    thresholds = parseDoubleList(requireArg(args, ++i, arg));
                    break;
                case "--n-rotations":
                    nRotations = Integer.parseInt(requireArg(args, ++i, arg));
                    break;
                case "--plot-file":
                    plotFile = requireArg(args, ++i, arg);
                    break;
                case "--output-file":
                    outputFile = requireArg(args, ++i, arg);
                    break;
                case "--randomize":
                    randomize = true;
                    break;
                case "--shuffle-seed":
                    shuffleSeed = Long.parseLong(requireArg(args, ++i, arg));
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
                default:
                    if (arg.startsWith("-")) {
                        System.err.println("Unknown option: " + arg);
                        printUsage();
                        System.exit(1);
                    } else if (inputFile == null) {
                        inputFile = arg;
                    } else {
                        System.err.println("Unexpected argument: " + arg);
                        printUsage();
                        System.exit(1);
                    }
                    break;
            }
        }

        if (inputFile == null) {
            printUsage();
            System.exit(1);
        }
        if (batchSize < 1) {
            System.err.println("--batch-size must be >= 1");
            System.exit(1);
        }
        if (threads < 1) {
            System.err.println("--threads must be >= 1");
            System.exit(1);
        }
        if (nRotations < 1) {
            System.err.println("--n-rotations must be >= 1");
            System.exit(1);
        }
        if (seeds.length < 1) {
            System.err.println("--seeds must contain at least one value");
            System.exit(1);
        }
        if (checkpoints.length < 1) {
            System.err.println("--checkpoints must contain at least one value");
            System.exit(1);
        }

        Arrays.sort(checkpoints);
        for (int i = 1; i < checkpoints.length; i++) {
            if (checkpoints[i] <= checkpoints[i - 1]) {
                System.err.println("--checkpoints must be strictly increasing");
                System.exit(1);
            }
        }

        CongestionBatch batch = new CongestionBatch(
                inputFile, batchSize, threads, seeds, checkpoints, thresholds, nRotations, plotFile, outputFile,
                randomize, shuffleSeed);
        try {
            batch.run();
        } catch (IOException e) {
            System.err.println("CongestionBatch failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String requireArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            System.err.println("Missing value for " + flag);
            printUsage();
            System.exit(1);
        }
        return args[index];
    }

    private static final class BatchEntry {
        final int index;
        final String line;

        BatchEntry(int index, String line) {
            this.index = index;
            this.line = line;
        }
    }

    private static final class GeneratorResult {
        final int index;
        final String[] cells;
        final boolean survived;
        final double bestScore;

        GeneratorResult(int index, String[] cells, boolean survived, double bestScore) {
            this.index = index;
            this.cells = cells;
            this.survived = survived;
            this.bestScore = bestScore;
        }
    }
}
