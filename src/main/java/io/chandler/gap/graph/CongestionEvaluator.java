package io.chandler.gap.graph;

import java.util.List;
import java.util.Random;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.graph.layoutalgos.LayoutCongestion;
import networkx.SpringLayout;
import networkx.SpringLayout.SpringLayoutState;

/**
 * Shared congestion scoring used by {@link CongestionBatch} and
 * {@link io.chandler.gap.graph.filter.CongestionResultFilter}: incremental 3D spring layout at
 * checkpoints, min score over seeds/rotations, optional threshold pruning.
 */
public final class CongestionEvaluator {

    private static final double BOX_SIZE = 1000.0;
    private static final int LAYOUT_DIM = 3;
    private static final long ROTATION_RNG_SEED = 42L;
    /** Congestion scores and thresholds both use this unit scale (raw layout score × 1000). */
    public static final double SCORE_SCALE = 1000.0;

    private final long[] seeds;
    private final int[] checkpoints;
    private final Double[] thresholds;
    private final int nRotations;
    private final int maxCheckpoint;
    private final double[][][] rotationMatrices;

    public CongestionEvaluator(long[] seeds, int[] checkpoints, Double[] thresholds, int nRotations) {
        if (seeds == null || seeds.length < 1) {
            throw new IllegalArgumentException("seeds must contain at least one value");
        }
        if (checkpoints == null || checkpoints.length < 1) {
            throw new IllegalArgumentException("checkpoints must contain at least one value");
        }
        if (nRotations < 1) {
            throw new IllegalArgumentException("nRotations must be >= 1");
        }
        int[] cps = checkpoints.clone();
        // Require strictly increasing (caller may already sort).
        for (int i = 1; i < cps.length; i++) {
            if (cps[i] <= cps[i - 1]) {
                throw new IllegalArgumentException("checkpoints must be strictly increasing");
            }
        }
        this.seeds = seeds.clone();
        this.checkpoints = cps;
        this.thresholds = thresholds == null ? null : thresholds.clone();
        this.nRotations = nRotations;
        this.maxCheckpoint = cps[cps.length - 1];
        this.rotationMatrices = generateRotationMatrices(nRotations);
    }

    public long[] seeds() { return seeds.clone(); }
    public int[] checkpoints() { return checkpoints.clone(); }
    public Double[] thresholds() {
        return thresholds == null ? null : thresholds.clone();
    }
    public int nRotations() { return nRotations; }

    /**
     * Score a generator line. Survives unless pruned by a checkpoint threshold
     * ({@code bestScore >= threshold}).
     */
    public Result evaluate(String line) {
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

        return new Result(cells, !pruned, bestScore);
    }

    public static final class Result {
        /** Per-checkpoint space-separated scores (null for checkpoints skipped after prune). */
        public final String[] cells;
        public final boolean survived;
        /** Min over seeds/rotations at the last evaluated checkpoint. */
        public final double bestScore;

        Result(String[] cells, boolean survived, double bestScore) {
            this.cells = cells;
            this.survived = survived;
            this.bestScore = bestScore;
        }
    }

    static String formatScores(double[] scores, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%.6f", scores[i]));
        }
        return sb.toString();
    }

    static Graph<Integer, DefaultEdge> buildGraphFromLine(String line) {
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

    static networkx.Graph buildNetworkxGraph(Graph<Integer, DefaultEdge> graph) {
        networkx.Graph nxGraph = new networkx.Graph();
        for (DefaultEdge edge : graph.edgeSet()) {
            nxGraph.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge));
        }
        return nxGraph;
    }

    static void norm3DArray(double[][] positions, double boxSize) {
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

    static void projectRotated(double[][] positions3d, double[][] projected2d, double[][] rotation) {
        for (int i = 0; i < positions3d.length; i++) {
            double x = positions3d[i][0];
            double y = positions3d[i][1];
            double z = positions3d[i][2];
            projected2d[i][0] = rotation[0][0] * x + rotation[0][1] * y + rotation[0][2] * z;
            projected2d[i][1] = rotation[1][0] * x + rotation[1][1] * y + rotation[1][2] * z;
        }
    }

    static double[][][] generateRotationMatrices(int count) {
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

    static double[][] rotationFromAxisAngle(double ax, double ay, double az, double angle) {
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
}
