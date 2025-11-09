package io.chandler.gap.graph;

import io.chandler.gap.GroupExplorer;
import networkx.Graph;

import java.io.*;
import java.util.*;

/**
 * Determines whether a generator is "geometrically solvable" using multi-start
 * stochastic hill-climbing.
 *
 * Reuses the parameterization from AxisConstrainedLayoutMulti: each operation
 * gets an axis direction, and each polygon gets (offset, scale, rotation).
 * The "connectivity cost" is the sum of squared distances between vertices that
 * should coincide. A cost near 0 means the geometry is consistent.
 *
 * Runs many random starts with moderate iterations each. If any converges to
 * cost < SAT_THRESHOLD, reports SAT. Otherwise reports likely UNSAT.
 */
public class SMTGeometricSolver {

    private static final double EPS = 1e-9;
    private static final double CONVERGENCE_THRESHOLD = 1e-8;
    private static final double STEP_SIZE = 0.05;

    private static final double SAT_THRESHOLD = 1e-6;
    private static final int DEFAULT_TRIES = 2000;
    private static final int INITIAL_ITERS = 500;
    private static final int REFINE_ITERS = 10000;

    public static void main(String[] args) throws Exception {
        test("[(1,2,3)(4,5,6),(2,7,8)]");
        test("[(1,8,2)(3,10,12)(4,15,14)(9,11,13),(1,9)(4,7)(6,13)(10,15),(2,10)(4,5)(6,12)(9,14)]");
    }

    private static void test(String raw) {
        String gen = GroupExplorer.renumberGeneratorNotation(raw);
        System.out.println("\n=== Generator: " + gen + " ===");
        long t0 = System.currentTimeMillis();
        double fit = checkSolvability(gen, DEFAULT_TRIES, INITIAL_ITERS, REFINE_ITERS, 42L);
        long elapsed = System.currentTimeMillis() - t0;
        String verdict = fit < SAT_THRESHOLD ? "SAT" : "LIKELY UNSAT";
        System.out.printf("Best fit: %.2e  ->  %s  (%d ms)%n", fit, verdict, elapsed);
    }

    /**
     * Returns the best connectivity cost found across all random starts.
     * A value < SAT_THRESHOLD (~1e-6) means the geometry is solvable.
     */
    public static double checkSolvability(String generatorS, int tries, int initialIters, int refineIters, long seed) {
        int[][][] generator = GroupExplorer.parseOperationsArr(
                GroupExplorer.renumberGeneratorNotation(generatorS));

        int nGroups = generator.length;
        int[] nPolygons = new int[nGroups];
        int[] verticesPerPolygon = new int[nGroups];
        for (int i = 0; i < nGroups; i++) {
            nPolygons[i] = generator[i].length;
            verticesPerPolygon[i] = generator[i][0].length;
        }

        Graph g = buildConnectivityGraph(generator, nGroups, nPolygons, verticesPerPolygon);

        double bestFit = Double.MAX_VALUE;
        Random bestRandom = null;
        Random random = new Random(seed);

        for (int t = 0; t < tries; t++) {
            Random snapshot = cloneRandom(random);
            double fit = computeLayout(g, nGroups, nPolygons, verticesPerPolygon, initialIters, random, 1.0);
            if (fit < bestFit) {
                bestFit = fit;
                bestRandom = snapshot;
                if (fit < SAT_THRESHOLD) break;
            }
        }

        // Refine the best result with more iterations
        if (bestRandom != null && bestFit < 1.0) {
            double refined = computeLayout(g, nGroups, nPolygons, verticesPerPolygon, refineIters, bestRandom, 1.0);
            if (refined < bestFit) bestFit = refined;
        }

        return bestFit;
    }

    // =========================================
    // Connectivity graph
    // =========================================

    private static Graph buildConnectivityGraph(int[][][] generator, int nGroups, int[] nPolygons, int[] verticesPerPolygon) {
        Graph g = new Graph();

        // Build sequential IDs for each slot in (group, polygon, vertex)
        int itmp = 1;
        int[][][] generatorSeq = new int[nGroups][][];
        for (int i = 0; i < nGroups; i++) {
            generatorSeq[i] = new int[nPolygons[i]][verticesPerPolygon[i]];
            for (int j = 0; j < nPolygons[i]; j++)
                for (int k = 0; k < verticesPerPolygon[i]; k++)
                    generatorSeq[i][j][k] = itmp++;
        }

        // Vertices that share the same original index must coincide in 3D.
        // Connect them with edges so that the connectivity cost penalizes mismatch.
        TreeMap<Integer, Integer> idxToFirstGlobal = new TreeMap<>();
        TreeMap<Integer, List<Integer>> idxToOtherGlobals = new TreeMap<>();

        for (int i = 0; i < nGroups; i++) {
            for (int j = 0; j < nPolygons[i]; j++) {
                for (int k = 0; k < verticesPerPolygon[i]; k++) {
                    int idx = generator[i][j][k];
                    int globalId = generatorSeq[i][j][k];

                    if (idxToFirstGlobal.containsKey(idx)) {
                        int first = idxToFirstGlobal.get(idx);
                        g.addEdge(globalId, first);
                        List<Integer> others = idxToOtherGlobals.get(idx);
                        for (int other : others)
                            g.addEdge(globalId, other);
                        others.add(globalId);
                    } else {
                        idxToFirstGlobal.put(idx, globalId);
                        idxToOtherGlobals.put(idx, new ArrayList<>());
                    }
                }
            }
        }

        return g;
    }

    // =========================================
    // Core solver
    // =========================================

    @SuppressWarnings("unchecked")
    private static double computeLayout(
            Graph mergedGraph, int nGroups, int[] nPolygons, int[] verticesPerPolygon,
            int iterations, Random random, double scale) {

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

        double finalCost = Double.MAX_VALUE;
        for (int iter = 0; iter < iterations; iter++) {
            double costBefore = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);

            tweakAll(random, axes, polyParams, mergedGraph, polyToVerts, scale, verticesPerPolygon, local2D);

            double costAfter = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);
            finalCost = costAfter;

            if (costAfter < SAT_THRESHOLD) break;
            if (Math.abs(costBefore - costAfter) < CONVERGENCE_THRESHOLD) break;
        }

        return finalCost;
    }

    // =========================================
    // Local search
    // =========================================

    private static void tweakAll(
            Random random, double[][] axes, PolygonParams[][] polyParams,
            Graph mergedGraph, Map<Integer, int[]>[] polyToVerts,
            double scale, int[] verticesPerPolygon, double[][][] local2D) {
        int nGroups = axes.length;
        for (int g = 0; g < nGroups; g++)
            tweakAxis(random, axes, g, mergedGraph, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);
        for (int g = 0; g < nGroups; g++)
            for (int p = 0; p < polyParams[g].length; p++)
                tweakPolygon(polyParams[g][p], polyParams[0][0], random, mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);
    }

    private static void tweakAxis(
            Random random, double[][] axes, int gIndex, Graph mergedGraph,
            PolygonParams[][] polyParams, Map<Integer, int[]>[] polyToVerts,
            double scale, int[] verticesPerPolygon, double[][][] local2D) {
        double[] axis = axes[gIndex];
        double[] orig = {axis[0], axis[1], axis[2]};
        double baseline = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);

        axis[0] += (random.nextDouble() - 0.5) * STEP_SIZE;
        axis[1] += (random.nextDouble() - 0.5) * STEP_SIZE;
        axis[2] += (random.nextDouble() - 0.5) * STEP_SIZE;
        if (length(axis) < EPS) { axis[0] = orig[0]; axis[1] = orig[1]; axis[2] = orig[2]; return; }

        double newCost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);
        if (newCost >= baseline) { axis[0] = orig[0]; axis[1] = orig[1]; axis[2] = orig[2]; }
    }

    private static void tweakPolygon(
            PolygonParams pp, PolygonParams anchor, Random random, Graph mergedGraph,
            double[][] axes, PolygonParams[][] polyParams, Map<Integer, int[]>[] polyToVerts,
            double scale, int[] verticesPerPolygon, double[][][] local2D) {
        double baseline = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);

        // Offset
        double origOffset = pp.offset;
        pp.offset += (random.nextDouble() - 0.5) * STEP_SIZE;
        double cost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);
        if (cost < baseline) baseline = cost; else pp.offset = origOffset;

        // Scale (skip for anchor polygon)
        if (pp != anchor) {
            double origScale = pp.scale;
            pp.scale += (random.nextDouble() - 0.5) * STEP_SIZE;
            if (pp.scale < EPS) pp.scale = EPS;
            cost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);
            if (cost < baseline) baseline = cost; else pp.scale = origScale;
        }

        // Rotation
        double origRotation = pp.rotation;
        pp.rotation += (random.nextDouble() - 0.5) * STEP_SIZE * 2;
        cost = recomputeAndCost(mergedGraph, axes, polyParams, polyToVerts, scale, verticesPerPolygon, local2D);
        if (cost >= baseline) pp.rotation = origRotation;
    }

    // =========================================
    // Cost computation
    // =========================================

    private static double recomputeAndCost(
            Graph mergedGraph, double[][] axes, PolygonParams[][] polyParams,
            Map<Integer, int[]>[] polyToVerts, double scale,
            int[] verticesPerPolygon, double[][][] local2D) {
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
        return cost;
    }

    // =========================================
    // 3D positioning from parameters
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
    // Data structures
    // =========================================

    private static class PolygonParams {
        double offset, scale, rotation;
        PolygonParams(double offset, double scale, double rotation) {
            this.offset = offset; this.scale = scale; this.rotation = rotation;
        }
    }

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

    // =========================================
    // 3D vector helpers
    // =========================================

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

    private static Random cloneRandom(Random random) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(random);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
            return (Random) ois.readObject();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
