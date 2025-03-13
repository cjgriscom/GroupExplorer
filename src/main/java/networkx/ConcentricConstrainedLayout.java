package networkx;

import java.util.*;
import java.lang.Math;
import io.chandler.gap.GroupExplorer;

/**
 * ConcentricConstrainedLayout places two sets of polygons onto a 2D plane.
 * All group 1 polygons are rendered with their center at the origin (concentric),
 * while all group 2 polygons share a common (but adjustable) center, which is set
 * to (d, 0) where d is optimized so that the connectivity cost is minimized.
 *
 * In each group, each polygon has its own scale and in‐plane rotation (its offset
 * parameter is now ignored so that they remain concentric). The rest of the
 * iterative local search algorithm remains analogous to the AxisConstrainedLayout.
 */
public class ConcentricConstrainedLayout {

    private static final double EPS = 1e-6;
    private static final double THRESHOLD = 1e-5;
    private static final double STEP_SIZE = 0.05;

    /**
     * LayoutResult holds the final vertex positions (2D coordinates) plus the two centers:
     * center1 is for group 1 (always [0,0]) and center2 is computed from the optimized
     * distance parameter for group 2.
     */
    public static class LayoutResult {
        public Map<Integer, double[]> positions;  // each vertex -> {x,y}
        public double[] center1;  // for group 1 (always [0,0])
        public double[] center2;  // for group 2 (always {1,0})
        public double fit;

        public LayoutResult(Map<Integer, double[]> positions, double[] center1, double[] center2, double fit) {
            this.positions = positions;
            this.center1 = center1;
            this.center2 = center2;
            this.fit = fit;
        }
    }

    /**
     * A helper class for per‐polygon parameters.
     * In this concentric layout, the "offset" parameter is ignored (so that all polygons remain concentric).
     * Only scale and rotation are used.
     */
    public static class PolygonParams {
        public double scale;     // scale factor of the polygon
        public double rotation;  // rotation in the 2D plane

        public PolygonParams(double scale, double rotation) {
            this.scale = scale;
            this.rotation = rotation;
        }
    }

    // ------------------------------------------------------------------------
    // Mapping functions and local shape generation
    // ------------------------------------------------------------------------

    /**
     * Builds a mapping from polygon index to the IDs of its vertices.
     * (Unchanged from AxisConstrainedLayout but generalized to support an arbitrary number of vertices.)
     */
    private static Map<Integer, int[]> buildPolygonToVertexMap(int startVertex, int nPolygons, int verticesPerPolygon) {
        Map<Integer, int[]> map = new HashMap<>();
        int current = startVertex;
        for (int t = 0; t < nPolygons; t++) {
            int[] verts = new int[verticesPerPolygon];
            for (int i = 0; i < verticesPerPolygon; i++) {
                verts[i] = current++;
            }
            map.put(t, verts);
        }
        return map;
    }

    /**
     * Returns the default 2D local shape for a polygon.
     * For a line (2 vertices) uses [(-0.5,0), (0.5,0)]; otherwise uses the vertices of a regular polygon.
     */
    private static double[][] getDefaultLocal2DPolygon(int verticesCount) {
        double[][] local2D = new double[verticesCount][2];
        if (verticesCount == 2) {
            local2D[0] = new double[]{ -0.5, 0 };
            local2D[1] = new double[]{ 0.5, 0 };
        } else {
            for (int i = 0; i < verticesCount; i++) {
                double angle = 2 * Math.PI * i / verticesCount;
                local2D[i] = new double[]{ Math.cos(angle), Math.sin(angle) };
            }
        }
        return local2D;
    }

    // ------------------------------------------------------------------------
    // 2D Embedding functions (replacing the 3D ones)
    // ------------------------------------------------------------------------

    /**
     * Computes the vertex position for a polygon in group 2.
     * All group 2 polygons are translated by the common center, computed from centerDistanceHolder.
     */
    private static double[] computePolygonVertexPosition(PolygonParams pp, int i, double globalScale, double[][] local2D, double[] center) {
        double[] local = local2D[i];
        double s = pp.scale * globalScale;
        double cosR = Math.cos(pp.rotation);
        double sinR = Math.sin(pp.rotation);
        double rx = local[0] * cosR - local[1] * sinR;
        double ry = local[0] * sinR + local[1] * cosR;
        return new double[]{ center[0] + rx * s, center[1] + ry * s };
    }

    /**
     * Computes the connectivity cost in 2D (sum of squared Euclidean distances).
     */
    private static double computeConnectivityCost(Graph g, Map<Integer, double[]> positions) {
        double cost = 0.0;
        for (Graph.Edge e : g.getEdges()) {
            double[] p1 = positions.get(e.u);
            double[] p2 = positions.get(e.v);
            if (p1 == null || p2 == null) continue;
            double dx = p1[0] - p2[0];
            double dy = p1[1] - p2[1];
            cost += (dx * dx + dy * dy);
        }
        return cost;
    }

    // ------------------------------------------------------------------------
    // Main iterative layout function
    // ------------------------------------------------------------------------

    /**
     * Computes the layout with polygons embedded into a 2D plane.
     * Group 1 polygons are concentric about the origin.
     * Group 2 polygons are concentric about a common center, which is (d,0) and where d is optimized.
     */
    public static LayoutResult computeLayout(
            Graph mergedVertices,
            int nPolygons1, int verticesPerPolygon1,
            int nPolygons2, int verticesPerPolygon2,
            int iterations,
            Random random,
            double scale // global scale factor
    ) {
        // For group 1, the center is fixed at (0,0)
        double[] center1 = new double[]{0, 0};
        double[] center2 = new double[]{ 1, 0 };

        // Initialize per-polygon parameters. For this layout we ignore the offset (set to 0) so that all polygons are concentric.
        PolygonParams[] polyParams1 = new PolygonParams[nPolygons1];
        for (int i = 0; i < nPolygons1; i++) {
            polyParams1[i] = new PolygonParams(1.0, random.nextDouble() * 2 * Math.PI);
        }
        PolygonParams[] polyParams2 = new PolygonParams[nPolygons2];
        for (int i = 0; i < nPolygons2; i++) {
            polyParams2[i] = new PolygonParams(1.0, random.nextDouble() * 2 * Math.PI);
        }

        // Map from polygon index to the vertex IDs.
        Map<Integer, int[]> polygonToVerticesMap1 = buildPolygonToVertexMap(1, nPolygons1, verticesPerPolygon1);
        int startIdx2 = verticesPerPolygon1 * nPolygons1 + 1;
        Map<Integer, int[]> polygonToVerticesMap2 = buildPolygonToVertexMap(startIdx2, nPolygons2, verticesPerPolygon2);

        // Get local 2D shapes for each group.
        double[][] local2DGroup1 = getDefaultLocal2DPolygon(verticesPerPolygon1);
        double[][] local2DGroup2 = getDefaultLocal2DPolygon(verticesPerPolygon2);

        Map<Integer, double[]> vertexPositions = new HashMap<>();

        double costAfter = 0;
        for (int iter = 0; iter < iterations; iter++) {

            // (a) Compute vertex positions for group 1.
            for (int t = 0; t < nPolygons1; t++) {
                int[] vIDs = polygonToVerticesMap1.get(t);
                PolygonParams pp = polyParams1[t];
                for (int i = 0; i < verticesPerPolygon1; i++) {
                    double[] pos = computePolygonVertexPosition(pp, i, scale, local2DGroup1, center1);
                    vertexPositions.put(vIDs[i], pos);
                }
            }
            // (b) Compute vertex positions for group 2.
            for (int t = 0; t < nPolygons2; t++) {
                int[] vIDs = polygonToVerticesMap2.get(t);
                PolygonParams pp = polyParams2[t];
                for (int i = 0; i < verticesPerPolygon2; i++) {
                    double[] pos = computePolygonVertexPosition(pp, i, scale, local2DGroup2, center2);
                    vertexPositions.put(vIDs[i], pos);
                }
            }

            // (c) Compute connectivity cost.
            double costBefore = computeConnectivityCost(mergedVertices, vertexPositions);

            // (d) Perform local parameter search (tweaks the group 2 center distance and each polygon's scale/rotation).
            localParameterSearchRestricted(random, center2,
                    polyParams1, polyParams2, mergedVertices,
                    polygonToVerticesMap1, polygonToVerticesMap2, scale,
                    verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2);

            // (e) Recompute vertex positions after the local changes.
            for (int t = 0; t < nPolygons1; t++) {
                int[] vIDs = polygonToVerticesMap1.get(t);
                for (int i = 0; i < verticesPerPolygon1; i++) {
                    vertexPositions.put(vIDs[i], computePolygonVertexPosition(polyParams1[t], i, scale, local2DGroup1, center1));
                }
            }
            for (int t = 0; t < nPolygons2; t++) {
                int[] vIDs = polygonToVerticesMap2.get(t);
                for (int i = 0; i < verticesPerPolygon2; i++) {
                    vertexPositions.put(vIDs[i], computePolygonVertexPosition(polyParams2[t], i, scale, local2DGroup2, center2));
                }
            }
            costAfter = computeConnectivityCost(mergedVertices, vertexPositions);

            // (f) Exit if improvement is below the threshold.
            if (Math.abs(costBefore - costAfter) < THRESHOLD) {
                break;
            }
        }

        // (g) Finalize: recompute the positions.
        for (int t = 0; t < nPolygons1; t++) {
            int[] vIDs = polygonToVerticesMap1.get(t);
            for (int i = 0; i < verticesPerPolygon1; i++) {
                vertexPositions.put(vIDs[i], computePolygonVertexPosition(polyParams1[t], i, scale, local2DGroup1, center1));
            }
        }
        for (int t = 0; t < nPolygons2; t++) {
            int[] vIDs = polygonToVerticesMap2.get(t);
            for (int i = 0; i < verticesPerPolygon2; i++) {
                vertexPositions.put(vIDs[i], computePolygonVertexPosition(polyParams2[t], i, scale, local2DGroup2, center2));
            }
        }

        return new LayoutResult(vertexPositions, center1, center2, costAfter);
    }

    // ------------------------------------------------------------------------
    // Local search functions (adjusting center distance and each polygon's parameters)
    // ------------------------------------------------------------------------

    /**
     * Computes the connectivity cost for the current parameter choices.
     * This version uses the concentric (2D) embedding.
     */
    private static double attemptRecomputeAndCost(
            Graph g,
            Map<Integer, int[]> polygonToVerticesMap1,
            Map<Integer, int[]> polygonToVerticesMap2,
            double scale,
            PolygonParams[] polyParams1,
            PolygonParams[] polyParams2,
            int verticesPerPolygon1,
            double[][] local2DGroup1,
            int verticesPerPolygon2,
            double[][] local2DGroup2,
            double[] center2
    ) {
        Map<Integer, double[]> positions = new HashMap<>();
        // Group 1 polygons.
        for (int t = 0; t < polyParams1.length; t++) {
            int[] vIDs = polygonToVerticesMap1.get(t);
            for (int i = 0; i < verticesPerPolygon1; i++) {
                double[] pos = computePolygonVertexPosition(polyParams1[t], i, scale, local2DGroup1, new double[]{0, 0});
                positions.put(vIDs[i], pos);
            }
        }
        // Group 2 polygons.
        for (int t = 0; t < polyParams2.length; t++) {
            int[] vIDs = polygonToVerticesMap2.get(t);
            for (int i = 0; i < verticesPerPolygon2; i++) {
                double[] pos = computePolygonVertexPosition(polyParams2[t], i, scale, local2DGroup2, center2);
                positions.put(vIDs[i], pos);
            }
        }
        return computeConnectivityCost(g, positions);
    }

    /**
     * Performs a small random tweak on a polygon's parameters (only scale and rotation, since offset is ignored).
     */
    private static void smallRandomTweakPolygon(
            PolygonParams pp,
            Random random,
            Graph g,
            Map<Integer, int[]> polygonToVerticesMap1,
            Map<Integer, int[]> polygonToVerticesMap2,
            double scale,
            PolygonParams[] polyParams1,
            PolygonParams[] polyParams2,
            int verticesPerPolygon1,
            double[][] local2DGroup1,
            int verticesPerPolygon2,
            double[][] local2DGroup2,
            double[] center2
    ) {
        double baselineCost = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2,
                scale, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, center2);

        // Tweak scale.
        double origScale = pp.scale;
        double tweak = (random.nextDouble() - 0.5) * STEP_SIZE * pp.scale;
        pp.scale += tweak;
		// Constrain scale from 0.1 to 5
		if (pp.scale < 0.8) pp.scale = 0.8;
		if (pp.scale > 4) pp.scale = 4;
        double costNew = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2,
                scale, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, center2);
        if (costNew < baselineCost) {
            baselineCost = costNew;
        } else {
            pp.scale = origScale; // revert
        }

        // Tweak rotation.
        double origRotation = pp.rotation;
        tweak = (random.nextDouble() - 0.5) * STEP_SIZE * 2;
        pp.rotation += tweak;
        costNew = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2,
                scale, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, center2);
        if (!(costNew < baselineCost)) {
            pp.rotation = origRotation;
        }
    }

    /**
     * Performs local parameter search:
     * First, a tweak of the group 2 center (via its x–coordinate) is attempted,
     * then each polygon's scale and rotation are independently tweaked.
     */
    private static void localParameterSearchRestricted(
            Random random,
            double[] centerDistanceHolder,
            PolygonParams[] polyParams1,
            PolygonParams[] polyParams2,
            Graph g,
            Map<Integer, int[]> polygonToVerticesMap1,
            Map<Integer, int[]> polygonToVerticesMap2,
            double scale,
            int verticesPerPolygon1,
            double[][] local2DGroup1,
            int verticesPerPolygon2,
            double[][] local2DGroup2
    ) {
        for (PolygonParams pp : polyParams1) {
            smallRandomTweakPolygon(pp, random, g, polygonToVerticesMap1, polygonToVerticesMap2,
                    scale, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, centerDistanceHolder);
        }
        for (PolygonParams pp : polyParams2) {
            smallRandomTweakPolygon(pp, random, g, polygonToVerticesMap1, polygonToVerticesMap2,
                    scale, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, centerDistanceHolder);
        }
    }


    // ------------------------------------------------------------------------
    // Generator-notation wrappers and main()
    // ------------------------------------------------------------------------

    public static Map<Integer, double[]> computeLayout(String generatorS, int iterations, long seed, double scale, double[] fitOut) {
        Map<Integer, double[]> positions = computeLayoutN(generatorS, iterations, seed, scale, iterations, fitOut);
        return positions;
    }

    public static Map<Integer, double[]> computeLayoutN(String generatorS, int iterations, long seed, double scale, int tries, double[] fitOut) {

        int[][][] generatorOrigNumbers = GroupExplorer.parseOperationsArr(generatorS);
        int[][][] generator = GroupExplorer.parseOperationsArr(GroupExplorer.renumberGeneratorNotation(generatorS));
        // Create mapping of original numbers to new numbers.
        Map<Integer, Integer> newToOriginal = new HashMap<>();
        for (int i = 0; i < generatorOrigNumbers.length; i++) {
            for (int j = 0; j < generatorOrigNumbers[i].length; j++) {
                for (int k = 0; k < generatorOrigNumbers[i][j].length; k++) {
                    newToOriginal.put(generator[i][j][k], generatorOrigNumbers[i][j][k]);
                }
            }
        }

        System.out.println(Arrays.deepToString(generator));
        if (generator.length != 2) throw new IllegalArgumentException("Generator must have exactly two groups");
        int nPolygons1 = generator[0].length;
        int nPolygons2 = generator[1].length;
        int verticesPerPolygon1 = generator[0][0].length;
        int verticesPerPolygon2 = generator[1][0].length;

        TreeSet<Integer> equivalentVertices = new TreeSet<>();

        // Build connectivity graph from group 2's operations.
        Graph g = new Graph();
        int polygon2Offset = nPolygons1 * verticesPerPolygon1;
        for (int t = 0; t < nPolygons2; t++) {
            int[] poly = generator[1][t];
            for (int j = 0; j < verticesPerPolygon2; j++) {
                int idx = poly[j];
                if (idx <= polygon2Offset) {
                    int poly2Idx = polygon2Offset + t * verticesPerPolygon2 + 1 + j;
                    g.addEdge(poly2Idx, idx);
                    equivalentVertices.add(poly2Idx);
                    System.out.println("Adding edge " + poly2Idx + " -> " + idx);
                }
            }
        }

        System.out.println("Seed: " + seed + " iters: " + iterations);

        double bestFit = Double.MAX_VALUE;
        LayoutResult result = null;

        Random random = new Random(seed);
        for (int i = 0; i < tries; i++) {
            LayoutResult result0 = computeLayout(g, nPolygons1, verticesPerPolygon1, nPolygons2, verticesPerPolygon2, iterations, random, 1);
            if (result0.fit < bestFit) {
                bestFit = result0.fit;
                result = result0;
            }
        }
        System.out.println("Final positions: ");
        for (Map.Entry<Integer, double[]> entry : result.positions.entrySet()) {
            System.out.println("Vertex " + entry.getKey() + ": " + Arrays.toString(entry.getValue()));
        }

        ArrayList<double[]> resultList = new ArrayList<>();
        double maxRadius = 0;
        for (int i = 1; i <= result.positions.size(); i++) {
            double[] pos = result.positions.get(i);
            double radius = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1]);
            maxRadius = Math.max(maxRadius, radius);
            resultList.add(new double[]{ pos[0], pos[1] });
        }
        Map<Integer, double[]> positions = new HashMap<>();

        scale /= maxRadius * 2;

        ArrayList<double[]> dupePositions = new ArrayList<>();
        // Re-index.
        int nextKey = 1;
        for (int i = 1; i <= resultList.size(); i++) {
            double[] pos = resultList.get(i - 1);
            if (!equivalentVertices.contains(i)) {
                System.out.println("Adding vertex " + nextKey + " at " + Arrays.toString(pos));
                positions.put(newToOriginal.get(nextKey), new double[]{ pos[0] * scale + scale/2, pos[1] * scale + scale/2 });
                nextKey++;
            } else {
                System.out.println("Skipping vertex " + i);
                dupePositions.add(pos);
            }
        }

        for (int i = 0; i < dupePositions.size(); i++) {
            double[] pos = dupePositions.get(i);
            positions.put(nextKey, new double[]{pos[0] * scale + scale/2, pos[1] * scale + scale/2});
            nextKey++;
        }

        System.out.println("Fit: " + result.fit);
        fitOut[0] = result.fit;
        System.out.println("Center 1: " + Arrays.toString(result.center1));
        System.out.println("Center 2: " + Arrays.toString(result.center2));

        return positions;
    }

    public static void main(String[] args) {
        // Run the ConcentricConstrainedLayout algorithm with custom polygon types
        int iterations = 1000;
        long seed = 5111;

        // For example, group 1 is lines (2 vertices) and group 2 is squares (4 vertices)
        // (all lying in a 2D plane).
        int nPolygons1 = 2;
        int verticesPerPolygon1 = 2;
        int nPolygons2 = 2;
        int verticesPerPolygon2 = 4;

        // Define connectivity (e.g. merge vertices).
        // Vertices for group 1: line 0 → vertices (1,2), line 1 → vertices (3,4);
        // For group 2: square 0 → vertices (5,6,7,8), square 1 → vertices (9,10,11,12)
        Graph mergedVertices = new Graph();
        mergedVertices.addEdge(2, 5);   // e.g. merge vertex 2 (from group1) with vertex 5 (from group2)
        mergedVertices.addEdge(3, 7);   // merge vertex 3 with vertex 7
        mergedVertices.addEdge(4, 10);  // merge vertex 4 with vertex 10

        LayoutResult result = computeLayout(mergedVertices, nPolygons1, verticesPerPolygon1,
                nPolygons2, verticesPerPolygon2, iterations, new Random(seed), 1);

        // Print the final results.
        System.out.println("Final Positions:");
        for (Map.Entry<Integer, double[]> entry : result.positions.entrySet()) {
            System.out.println("Vertex " + entry.getKey() + ": " + Arrays.toString(entry.getValue()));
        }

        System.out.println("Center 1: " + Arrays.toString(result.center1));
        System.out.println("Center 2: " + Arrays.toString(result.center2));

        // Also test using generator notation.
        // For instance, generator string "[(1,2)(3,4),(2,5,6,7)(8,9,10,11)]" yields
        // group 1: lines and group 2: squares.
        String generatorS = "[(1,2)(3,4),(2,5,6,7)(8,9,10,11)]";
        Map<Integer, double[]> result2 = computeLayout(generatorS, iterations, seed, 1, new double[]{0});
        for (Map.Entry<Integer, double[]> entry : result2.entrySet()) {
            System.out.println("Vertex " + entry.getKey() + ": " + Arrays.toString(entry.getValue()));
        }
    }
} 