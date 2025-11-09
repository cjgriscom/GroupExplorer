package io.chandler.gap.graph.layoutalgos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GroupExplorer;
import networkx.Graph;

/**
 * Multi-start SAT-style layout solver.
 *
 * Uses the same parameterization as AxisConstrainedLayoutMulti (axis + offset/scale/rotation
 * per polygon) but runs many random starts with moderate iterations each, keeping the best.
 * Designed to quickly determine whether a geometric solution exists (fit near 0).
 */
public class SATLayout extends LayoutAlgo {

    private Map<Integer, double[]> result;
    private double fitOut = -1;

    private static final double EPS = 1e-9;
    private static final double CONVERGENCE_THRESHOLD = 1e-8;
    private static final double STEP_SIZE = 0.05;
    private static final double SAT_THRESHOLD = 1e-6;

    @Override
    public LayoutAlgoArg[] getArgs() {
        return new LayoutAlgoArg[]{
            LayoutAlgoArg.ITERS,
            LayoutAlgoArg.SEED,
            LayoutAlgoArg.SHOW_FITTED_NODES,
            LayoutAlgoArg.TRIES,
            LayoutAlgoArg.INITIAL_ITERS,
            LayoutAlgoArg.REPULSION_FACTOR
        };
    }

    @Override
    public void performLayout(double boxSize, String generator, org.jgrapht.Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args) {
        double[] fitArr = new double[]{-1};
        result = computeLayoutN(generator,
                args.get(LayoutAlgoArg.ITERS).intValue(),
                args.get(LayoutAlgoArg.SEED).longValue(),
                boxSize,
                args.get(LayoutAlgoArg.TRIES).intValue(),
                args.get(LayoutAlgoArg.INITIAL_ITERS).intValue(),
                !args.get(LayoutAlgoArg.SHOW_FITTED_NODES).equals(0.0),
                fitArr,
                args.get(LayoutAlgoArg.REPULSION_FACTOR));
        this.fitOut = fitArr[0];
    }

    @Override
    public Map<Integer, double[]> getResult() { return result; }

    @Override
    public Double getFitOut() { return fitOut; }

    // =========================================
    // Data structures
    // =========================================

    private static class PolygonParams {
        double offset, scale, rotation;
        PolygonParams(double offset, double scale, double rotation) {
            this.offset = offset; this.scale = scale; this.rotation = rotation;
        }
    }

    private static class LayoutResult {
        Map<Integer, double[]> positions;
        double[][] axes;
        double fit;
        LayoutResult(Map<Integer, double[]> positions, double[][] axes, double fit) {
            this.positions = positions; this.axes = axes; this.fit = fit;
        }
    }

    /**
     * Computes only the best fit value for a generator without building full position maps.
     * Respects thread interruption for early termination.
     */
    public static double computeFitOnly(String generatorS, int iterations, long seed,
            int tries, int initialIters, double repulsionFactor) {
        double[] fitOut = new double[]{Double.MAX_VALUE};
        computeLayoutN(generatorS, iterations, seed, 1.0, tries, initialIters, false, fitOut, repulsionFactor);
        return fitOut[0];
    }

    // =========================================
    // Public entry point
    // =========================================

    public static Map<Integer, double[]> computeLayoutN(
            String generatorS, int iterations, long seed, double scale,
            int tries, int initialIters, boolean showFittedNodes, double[] fitOut,
            double repulsionFactor) {

        int[][][] generatorOrigNumbers = GroupExplorer.parseOperationsArr(generatorS);
        int[][][] generator = GroupExplorer.parseOperationsArr(GroupExplorer.renumberGeneratorNotation(generatorS));

        Map<Integer, Integer> newToOriginal = new HashMap<>();
        for (int i = 0; i < generatorOrigNumbers.length; i++)
            for (int j = 0; j < generatorOrigNumbers[i].length; j++)
                for (int k = 0; k < generatorOrigNumbers[i][j].length; k++)
                    newToOriginal.put(generator[i][j][k], generatorOrigNumbers[i][j][k]);

        int nGroups = generator.length;
        int[] nPolygons = new int[nGroups];
        int[] verticesPerPolygon = new int[nGroups];
        for (int i = 0; i < nGroups; i++) {
            nPolygons[i] = generator[i].length;
            verticesPerPolygon[i] = generator[i][0].length;
        }

        int[] globalOffsets = new int[nGroups];
        globalOffsets[0] = 1;
        for (int i = 1; i < nGroups; i++)
            globalOffsets[i] = globalOffsets[i - 1] + nPolygons[i - 1] * verticesPerPolygon[i - 1];
        int totalVertices = globalOffsets[nGroups - 1] + nPolygons[nGroups - 1] * verticesPerPolygon[nGroups - 1] - 1;

        // Build connectivity graph
        Graph g = new Graph();
        int itmp = 1;
        int[][][] generatorSeq = new int[nGroups][][];
        for (int i = 0; i < nGroups; i++) {
            generatorSeq[i] = new int[nPolygons[i]][verticesPerPolygon[i]];
            for (int j = 0; j < nPolygons[i]; j++)
                for (int k = 0; k < verticesPerPolygon[i]; k++)
                    generatorSeq[i][j][k] = itmp++;
        }

        TreeSet<Integer> equivalentVertices = new TreeSet<>();
        TreeMap<Integer, List<Integer>> alreadyAddedFromSrc = new TreeMap<>();
        TreeMap<Integer, Integer> idxToPolyGlobalId = new TreeMap<>();

        for (int i = 0; i < nGroups; i++) {
            for (int j = 0; j < nPolygons[i]; j++) {
                for (int k = 0; k < verticesPerPolygon[i]; k++) {
                    int idx = generator[i][j][k];
                    int polyGlobalId = generatorSeq[i][j][k];

                    if (alreadyAddedFromSrc.containsKey(idx)) {
                        int idxMapped = idxToPolyGlobalId.get(idx);
                        g.addEdge(polyGlobalId, idxMapped);
                        List<Integer> list = alreadyAddedFromSrc.get(idx);
                        for (Integer other : list)
                            g.addEdge(polyGlobalId, other);
                        list.add(polyGlobalId);
                        equivalentVertices.add(polyGlobalId);
                    } else {
                        idxToPolyGlobalId.put(idx, polyGlobalId);
                        alreadyAddedFromSrc.put(idx, new ArrayList<>());
                    }
                }
            }
        }

        // Multi-start: many tries with initialIters, keep best, then refine
        double bestFit = Double.MAX_VALUE;
        LayoutResult bestResult = null;
        Random bestRandom = null;
        Random random = new Random(seed);

        for (int t = 0; t < tries; t++) {
            if (Thread.currentThread().isInterrupted()) break;
            Random snapshot = cloneRandom(random);
            LayoutResult r = computeLayout(g, nGroups, nPolygons, verticesPerPolygon, initialIters, random, 1.0, repulsionFactor);
            if (r.fit < bestFit) {
                bestFit = r.fit;
                bestResult = r;
                bestRandom = snapshot;
                if (r.fit < SAT_THRESHOLD) break;
            }
        }

        // Refine best result with full iterations
        if (bestRandom != null && initialIters != iterations) {
            LayoutResult refined = computeLayout(g, nGroups, nPolygons, verticesPerPolygon, iterations, bestRandom, 1.0, repulsionFactor);
            if (refined.fit < bestFit) {
                bestResult = refined;
                bestFit = refined.fit;
            }
        }

        System.out.println("Fit: " + bestFit);
        fitOut[0] = bestFit;
        for (int i = 0; i < bestResult.axes.length; i++)
            System.out.println("Axis " + i + ": " + Arrays.toString(bestResult.axes[i]));

        // Normalize positions and re-index to original vertex IDs
        ArrayList<double[]> resultList = new ArrayList<>();
        double maxRadius = 0;
        for (int i = 1; i <= totalVertices; i++) {
            double[] pos = bestResult.positions.get(i);
            double radius = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
            maxRadius = Math.max(maxRadius, radius);
            resultList.add(new double[]{pos[0], pos[1], pos[2]});
        }
        Map<Integer, double[]> positions = new HashMap<>();
        scale /= maxRadius * 2;

        ArrayList<double[]> dupePositions = new ArrayList<>();
        int nextKey = 1;
        for (int i = 1; i <= resultList.size(); i++) {
            double[] pos = resultList.get(i - 1);
            if (!equivalentVertices.contains(i)) {
                positions.put(newToOriginal.get(nextKey), new double[]{pos[0] * scale, pos[1] * scale, pos[2] * scale});
                nextKey++;
            } else {
                dupePositions.add(pos);
            }
        }

        if (showFittedNodes) {
            for (double[] pos : dupePositions) {
                positions.put(nextKey, new double[]{pos[0] * scale, pos[1] * scale, pos[2] * scale});
                nextKey++;
            }
        }

        return positions;
    }

    // =========================================
    // Core solver (single start)
    // =========================================

    @SuppressWarnings("unchecked")
    private static LayoutResult computeLayout(
            Graph mergedGraph, int nGroups, int[] nPolygons, int[] verticesPerPolygon,
            int iterations, Random random, double scale, double repulsionFactor) {

        double[][] axes = new double[nGroups][];
        for (int i = 0; i < nGroups; i++)
            axes[i] = randomDirection(random);

        PolygonParams[][] polyParams = new PolygonParams[nGroups][];
        for (int i = 0; i < nGroups; i++) {
            polyParams[i] = new PolygonParams[nPolygons[i]];
            for (int j = 0; j < nPolygons[i]; j++)
                polyParams[i][j] = new PolygonParams(
                        random.nextDouble() * 2 - 1, 1.0, random.nextDouble() * 2 * Math.PI);
        }

        Map<Integer, int[]>[] polyToVerts = new Map[nGroups];
        int currentId = 1;
        for (int i = 0; i < nGroups; i++) {
            polyToVerts[i] = buildPolygonToVertexMap(currentId, nPolygons[i], verticesPerPolygon[i]);
            currentId += nPolygons[i] * verticesPerPolygon[i];
        }

        double[][][] local2D = new double[nGroups][][];
        for (int i = 0; i < nGroups; i++)
            local2D[i] = getDefaultLocal2DPolygon(verticesPerPolygon[i]);

        Map<Integer, double[]> vertexPositions = new HashMap<>();
        double finalCost = Double.MAX_VALUE;

        for (int iter = 0; iter < iterations; iter++) {
            if (Thread.currentThread().isInterrupted()) break;

            double costBefore = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);

            tweakAll(random, axes, polyParams, mergedGraph, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);

            double costAfter = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);
            finalCost = costAfter;

            if (costAfter < SAT_THRESHOLD) break;
            if (Math.abs(costBefore - costAfter) < CONVERGENCE_THRESHOLD) break;
        }

        // Final position refresh
        for (int g = 0; g < nGroups; g++)
            for (int p = 0; p < polyParams[g].length; p++) {
                int[] vIDs = polyToVerts[g].get(p);
                for (int v = 0; v < verticesPerPolygon[g]; v++)
                    vertexPositions.put(vIDs[v], vertexPosition(axes[g], polyParams[g][p], v, scale, local2D[g]));
            }

        for (int i = 0; i < nGroups; i++) normalize(axes[i]);

        return new LayoutResult(vertexPositions, axes, finalCost);
    }

    // =========================================
    // Local search
    // =========================================

    private static void tweakAll(
            Random random, double[][] axes, PolygonParams[][] polyParams,
            Graph mergedGraph, Map<Integer, int[]>[] polyToVerts,
            double scale, int[] verticesPerPolygon, double[][][] local2D,
            double repulsionFactor) {
        int nGroups = axes.length;
        for (int g = 0; g < nGroups; g++)
            tweakAxis(random, axes, g, mergedGraph, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);
        for (int g = 0; g < nGroups; g++)
            for (int p = 0; p < polyParams[g].length; p++)
                tweakPolygon(polyParams[g][p], polyParams[0][0], random, mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);
    }

    private static void tweakAxis(
            Random random, double[][] axes, int gIndex, Graph mergedGraph,
            PolygonParams[][] polyParams, Map<Integer, int[]>[] polyToVerts,
            double scale, int[] verticesPerPolygon, double[][][] local2D,
            double repulsionFactor) {
        double[] axis = axes[gIndex];
        double[] orig = {axis[0], axis[1], axis[2]};
        double baseline = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);

        axis[0] += (random.nextDouble() - 0.5) * STEP_SIZE;
        axis[1] += (random.nextDouble() - 0.5) * STEP_SIZE;
        axis[2] += (random.nextDouble() - 0.5) * STEP_SIZE;
        if (length(axis) < EPS) { axis[0] = orig[0]; axis[1] = orig[1]; axis[2] = orig[2]; return; }

        double newCost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);
        if (newCost >= baseline) { axis[0] = orig[0]; axis[1] = orig[1]; axis[2] = orig[2]; }
    }

    private static void tweakPolygon(
            PolygonParams pp, PolygonParams anchor, Random random, Graph mergedGraph,
            double[][] axes, PolygonParams[][] polyParams, Map<Integer, int[]>[] polyToVerts,
            double scale, int[] verticesPerPolygon, double[][][] local2D,
            double repulsionFactor) {
        double baseline = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);

        double origOffset = pp.offset;
        pp.offset += (random.nextDouble() - 0.5) * STEP_SIZE;
        double cost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);
        if (cost < baseline) baseline = cost; else pp.offset = origOffset;

        if (pp != anchor) {
            double origScale = pp.scale;
            pp.scale += (random.nextDouble() - 0.5) * STEP_SIZE;
            if (pp.scale < EPS) pp.scale = EPS;
            cost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);
            if (cost < baseline) baseline = cost; else pp.scale = origScale;
        }

        double origRotation = pp.rotation;
        pp.rotation += (random.nextDouble() - 0.5) * STEP_SIZE * 2;
        cost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D, repulsionFactor);
        if (cost >= baseline) pp.rotation = origRotation;
    }

    // =========================================
    // Cost computation
    // =========================================

    private static double recomputeAndCost(
            Graph mergedGraph, double[][] axes, PolygonParams[][] polyParams,
            Map<Integer, int[]>[] polyToVerts, double scale,
            int[] verticesPerPolygon, double[][][] local2D,
            double repulsionFactor) {
        Map<Integer, double[]> positions = new HashMap<>();
        int nGroups = axes.length;
        for (int g = 0; g < nGroups; g++)
            for (int p = 0; p < polyParams[g].length; p++) {
                int[] vIDs = polyToVerts[g].get(p);
                for (int v = 0; v < verticesPerPolygon[g]; v++)
                    positions.put(vIDs[v], vertexPosition(axes[g], polyParams[g][p], v, scale, local2D[g]));
            }
        double cost = 0;
        for (Graph.Edge e : mergedGraph.getEdges()) {
            double[] p1 = positions.get(e.u);
            double[] p2 = positions.get(e.v);
            if (p1 == null || p2 == null) continue;
            double dx = p1[0] - p2[0], dy = p1[1] - p2[1], dz = p1[2] - p2[2];
            cost += dx * dx + dy * dy + dz * dz;
        }
        cost += computeRepulsionCost(positions, repulsionFactor);
        return cost;
    }

    private static double computeRepulsionCost(Map<Integer, double[]> positions, double repulsionFactor) {
        if (repulsionFactor <= 0) return 0;
        double cost = 0.0;
        List<double[]> posList = new ArrayList<>(positions.values());
        for (int i = 0; i < posList.size(); i++) {
            double[] p1 = posList.get(i);
            for (int j = i + 1; j < posList.size(); j++) {
                double[] p2 = posList.get(j);
                double dx = p1[0] - p2[0];
                double dy = p1[1] - p2[1];
                double dz = p1[2] - p2[2];
                double dSquared = dx * dx + dy * dy + dz * dz;
                cost += repulsionFactor / dSquared;
            }
        }
        return cost;
    }

    // =========================================
    // 3D positioning
    // =========================================

    private static double[] vertexPosition(double[] axis, PolygonParams pp, int vIndex, double globalScale, double[][] local2D) {
        double cx = axis[0] * pp.offset;
        double cy = axis[1] * pp.offset;
        double cz = axis[2] * pp.offset;

        double lx = local2D[vIndex][0] * pp.scale * globalScale;
        double ly = local2D[vIndex][1] * pp.scale * globalScale;

        double cosR = Math.cos(pp.rotation), sinR = Math.sin(pp.rotation);
        double rx = lx * cosR - ly * sinR;
        double ry = lx * sinR + ly * cosR;

        double[] perp1 = findPerpVector(axis);
        normalize(perp1);
        double[] perp2 = cross(axis, perp1);
        normalize(perp2);

        return new double[]{
            cx + rx * perp1[0] + ry * perp2[0],
            cy + rx * perp1[1] + ry * perp2[1],
            cz + rx * perp1[2] + ry * perp2[2]
        };
    }

    // =========================================
    // Helpers
    // =========================================

    private static Map<Integer, int[]> buildPolygonToVertexMap(int startVertex, int nPolygons, int verticesPerPolygon) {
        Map<Integer, int[]> map = new HashMap<>();
        int current = startVertex;
        for (int t = 0; t < nPolygons; t++) {
            int[] verts = new int[verticesPerPolygon];
            for (int i = 0; i < verticesPerPolygon; i++) verts[i] = current++;
            map.put(t, verts);
        }
        return map;
    }

    private static double[][] getDefaultLocal2DPolygon(int vertexCount) {
        double[][] shape = new double[vertexCount][2];
        if (vertexCount == 2) {
            shape[0][0] = -0.5; shape[0][1] = 0.0;
            shape[1][0] = 0.5;  shape[1][1] = 0.0;
        } else {
            for (int i = 0; i < vertexCount; i++) {
                double angle = 2.0 * Math.PI * i / vertexCount;
                shape[i][0] = Math.cos(angle);
                shape[i][1] = Math.sin(angle);
            }
        }
        return shape;
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static void normalize(double[] v) {
        double len = length(v);
        if (len < EPS) return;
        v[0] /= len; v[1] /= len; v[2] /= len;
    }

    private static double[] randomDirection(Random rand) {
        double x = rand.nextDouble() * 2 - 1;
        double y = rand.nextDouble() * 2 - 1;
        double z = rand.nextDouble() * 2 - 1;
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < EPS) return new double[]{1, 0, 0};
        return new double[]{x / len, y / len, z / len};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    private static double[] findPerpVector(double[] v) {
        double[] z = {0, 0, 1}, x = {1, 0, 0};
        double dotZ = Math.abs(v[0] * z[0] + v[1] * z[1] + v[2] * z[2]);
        return dotZ < 0.9 ? cross(v, z) : cross(v, x);
    }
}
