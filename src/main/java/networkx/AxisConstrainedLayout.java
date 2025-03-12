package networkx;

import java.util.*;

import io.chandler.gap.GroupExplorer;

import java.lang.Math;

/**
 * AxisConstrainedLayout places two sets of triangles along two 3D axes that both pass
 * through the origin. Triangles in group 1 lie on axis1, triangles in group 2 lie on axis2.
 * We then adjust:
 *  - The angles of axis1, axis2
 *  - The offset (position along the axis), scale, and rotation in-plane of each triangle
 * so that the sum of distances between connected vertices is minimized.
 */
public class AxisConstrainedLayout {

    private static final double EPS = 1e-6;
    private static final double THRESHOLD = 1e-5;
    private static final double STEP_SIZE = 0.05;

    /**
     * LayoutResult holds the final vertex positions as well as the computed axis for group 1 and group 2.
     */
    public static class LayoutResult {
        public Map<Integer, double[]> positions;  // each vertex -> {x,y,z}
        public double[] axis1;  // final axis1 direction
        public double[] axis2;  // final axis2 direction
        public double fit;

        public LayoutResult(Map<Integer, double[]> positions, double[] axis1, double[] axis2, double fit) {
            this.positions = positions;
            this.axis1 = axis1;
            this.axis2 = axis2;
            this.fit = fit;
        }
    }

    /**
     * A small helper class to hold triangle parameters.
     * For each triangle we store offsetAlongAxis, scale, rotation.
     */
    public static class TriangleParams {
        public double offset;    // distance from origin along the axis
        public double scale;     // scale factor of the triangle
        public double rotation;  // rotation in the plane perpendicular to the axis

        public TriangleParams(double offset, double scale, double rotation) {
            this.offset = offset;
            this.scale = scale;
            this.rotation = rotation;
        }
    }

    /**
     * For simplicity, we assume each triangle is "numbered" and has exactly 3 vertices.
     * Suppose group1 has nTriangles1, with vertices labeled stubs or according to a known mapping.
     * Suppose group2 has nTriangles2.
     * The 'mergedVertices' graph provides edge constraints among all vertices.
     *
     * We perform an iterative approach:
     *  1) Initialize axis1, axis2, and each triangle's {offset, scale, rotation} with random or default values.
     *  2) In each iteration:
     *     - Recompute the positions of each vertex
     *     - Compute the connectivity cost (sum of squared distances for each edge)
     *     - Try small random perturbations for axis directions (via their 3 components) 
     *       and each triangle's parameters.
     *     - Accept only tweaks that decrease the cost.
     *  3) Exit if the cost improvement is below a threshold or if the maximum number of iterations is reached.
     */
    public static LayoutResult computeLayout(
            Graph mergedVertices,
            int nTriangles1,
            int nTriangles2,
            int iterations,
            Random random,
            double scale // global scale factor
    ) {

        // --------------------------------------------------------------------
        // 1) Set up data structures: two 3D axes and per-triangle parameters.
        // --------------------------------------------------------------------
        double[] axis1 = randomDirection(random);
        // Fix axis1 and restrict axis2 to one degree of freedom (axis2 lies in the plane perpendicular to axis1)
        double[] e0 = findPerpVector(axis1);
        normalize(e0);
        double[] e1 = cross(axis1, e0);
        normalize(e1);
        double[] axis2;
        double[] axis2AngleHolder = new double[]{ random.nextDouble() * 2 * Math.PI };
        axis2 = computeAxis2FromAngle(axis2AngleHolder[0], e0, e1);

        TriangleParams[] triParams1 = new TriangleParams[nTriangles1];
        for (int i = 0; i < nTriangles1; i++) {
            triParams1[i] = new TriangleParams(
                    random.nextDouble() * 2 - 1,              // offset
                    1.0,                                     // initial scale = 1.0
                    random.nextDouble() * 2 * Math.PI        // rotation in [0,2π)
            );
        }
        TriangleParams[] triParams2 = new TriangleParams[nTriangles2];
        for (int i = 0; i < nTriangles2; i++) {
            triParams2[i] = new TriangleParams(
                    random.nextDouble() * 2 - 1,
                    1.0,
                    random.nextDouble() * 2 * Math.PI
            );
        }

        // Map from triangle index to the three vertex IDs.
        // Group 1: triangle 0 => vertices (1,2,3), triangle 1 => vertices (4,5,6), etc.
        Map<Integer, int[]> triangleToVerticesMap1 = buildTriangleToVertexMap(1, nTriangles1);
        // Group 2 vertices start after group 1, i.e. at 3*nTriangles1+1.
        int startIdx2 = 3 * nTriangles1 + 1;
        Map<Integer, int[]> triangleToVerticesMap2 = buildTriangleToVertexMap(startIdx2, nTriangles2);

        // We will update vertex positions in each iteration.
        Map<Integer, double[]> vertexPositions = new HashMap<>();

        // --------------------------------------------------------------------
        // 2) Main iterative loop.
        // --------------------------------------------------------------------
        double costAfter = 0;
        for (int iter = 0; iter < iterations; iter++) {

            // (a) Compute vertex positions from the current parameters.
            for (int t = 0; t < nTriangles1; t++) {
                int[] vIDs = triangleToVerticesMap1.get(t);
                TriangleParams tp = triParams1[t];
                for (int i = 0; i < 3; i++) {
                    double[] pos = computeVertexPositionOnAxis(axis1, tp, i, scale);
                    vertexPositions.put(vIDs[i], pos);
                }
            }
            double[] currentAxis2 = computeAxis2FromAngle(axis2AngleHolder[0], e0, e1);
            for (int t = 0; t < nTriangles2; t++) {
                int[] vIDs = triangleToVerticesMap2.get(t);
                TriangleParams tp = triParams2[t];
                for (int i = 0; i < 3; i++) {
                    double[] pos = computeVertexPositionOnAxis(currentAxis2, tp, i, scale);
                    vertexPositions.put(vIDs[i], pos);
                }
            }

            // (b) Compute connectivity cost.
            double costBefore = computeConnectivityCost(mergedVertices, vertexPositions);

            // (c) Perform local parameter search.
            localParameterSearchRestricted(random, axis1, e0, e1, axis2AngleHolder, triParams1, triParams2, mergedVertices,
                    triangleToVerticesMap1, triangleToVerticesMap2, scale);

            // Recompute vertex positions after the local changes.
            for (int t = 0; t < nTriangles1; t++) {
                int[] vIDs = triangleToVerticesMap1.get(t);
                for (int i = 0; i < 3; i++) {
                    vertexPositions.put(vIDs[i], computeVertexPositionOnAxis(axis1, triParams1[t], i, scale));
                }
            }
            for (int t = 0; t < nTriangles2; t++) {
                int[] vIDs = triangleToVerticesMap2.get(t);
                for (int i = 0; i < 3; i++) {
                    vertexPositions.put(vIDs[i], computeVertexPositionOnAxis(currentAxis2, triParams2[t], i, scale));
                }
            }
            costAfter = computeConnectivityCost(mergedVertices, vertexPositions);

            // (d) If the cost did not improve substantially, break early.
            if (Math.abs(costBefore - costAfter) < THRESHOLD) {
                break;
            }
        }

        // --------------------------------------------------------------------
        // 3) Finalize: recompute positions and normalize the final axis directions.
        // --------------------------------------------------------------------
        for (int t = 0; t < nTriangles1; t++) {
            int[] vIDs = triangleToVerticesMap1.get(t);
            for (int i = 0; i < 3; i++) {
                vertexPositions.put(vIDs[i], computeVertexPositionOnAxis(axis1, triParams1[t], i, scale));
            }
        }
        double[] finalAxis2 = computeAxis2FromAngle(axis2AngleHolder[0], e0, e1);
        for (int t = 0; t < nTriangles2; t++) {
            int[] vIDs = triangleToVerticesMap2.get(t);
            for (int i = 0; i < 3; i++) {
                vertexPositions.put(vIDs[i], computeVertexPositionOnAxis(finalAxis2, triParams2[t], i, scale));
            }
        }

        normalize(axis1);
        normalize(axis2);

        return new LayoutResult(vertexPositions, axis1, axis2, costAfter);
    }

    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------

    /**
     * Randomly generates a direction vector in 3D and normalizes it.
     */
    private static double[] randomDirection(Random rand) {
        double x = rand.nextDouble() * 2 - 1;
        double y = rand.nextDouble() * 2 - 1;
        double z = rand.nextDouble() * 2 - 1;
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < EPS)
            len = 1.0;
        return new double[]{x / len, y / len, z / len};
    }

    /**
     * Normalizes a 3D vector in-place.
     */
    private static void normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < EPS)
            return;
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }

    /**
     * Builds a simple mapping: triangle index t -> {3 vertex IDs}.
     * For example, if startVertex=1 then triangle 0=[1,2,3], triangle 1=[4,5,6], etc.
     */
    private static Map<Integer, int[]> buildTriangleToVertexMap(int startVertex, int nTriangles) {
        Map<Integer, int[]> map = new HashMap<>();
        int current = startVertex;
        for (int t = 0; t < nTriangles; t++) {
            int[] verts = new int[]{current, current + 1, current + 2};
            map.put(t, verts);
            current += 3;
        }
        return map;
    }

    /**
     * Computes the 3D position of a vertex for a given triangle.
     *
     * <p>
     * 1. The triangle center is along the axis at (offset * axis).  
     * 2. A local equilateral triangle is defined in 2D.
     * 3. We subtract the triangle's centroid so that (0,0) becomes the center.  
     * 4. The triangle is scaled and rotated, then embedded in the plane perpendicular to the axis.
     * </p>
     */
    private static double[] computeVertexPositionOnAxis(double[] axis, TriangleParams tp, int i, double globalScale) {
        // Center of the triangle along the axis.
        double[] center = new double[]{axis[0] * tp.offset, axis[1] * tp.offset, axis[2] * tp.offset};

        // Define local coordinates for a centered equilateral triangle.
        // Original vertices for an equilateral triangle: (0,0), (1,0), (0.5, sqrt(3)/2)
        // Its centroid is at (0.5, sqrt(3)/6). We subtract that from each point.
        // Thus, the new vertices are: (-0.5, -sqrt(3)/6), (0.5, -sqrt(3)/6), (0.0, sqrt(3)/3)
        double sqrt3 = Math.sqrt(3.0);
        double[][] local2D = new double[][]{
            {-0.5, -sqrt3 / 6.0},
            {0.5, -sqrt3 / 6.0},
            {0.0, sqrt3 / 3.0}
        };

        // Get local coordinate for vertex i.
        double lx = local2D[i][0];
        double ly = local2D[i][1];

        // Scale the 2D coordinates (triangle's scale + globalScale).
        double s = tp.scale * globalScale;
        lx *= s;
        ly *= s;

        // Rotate the 2D coordinates.
        double cosR = Math.cos(tp.rotation);
        double sinR = Math.sin(tp.rotation);
        double rx = lx * cosR - ly * sinR;
        double ry = lx * sinR + ly * cosR;

        // Embed in 3D: find two orthogonal vectors perpendicular to 'axis'
        double[] perp1 = findPerpVector(axis);
        double[] perp2 = cross(axis, perp1);
        normalize(perp1);
        normalize(perp2);

        double px = center[0] + rx * perp1[0] + ry * perp2[0];
        double py = center[1] + rx * perp1[1] + ry * perp2[1];
        double pz = center[2] + rx * perp1[2] + ry * perp2[2];
        return new double[]{px, py, pz};
    }

    /**
     * Finds an arbitrary vector perpendicular to the given vector 'v'.
     */
    private static double[] findPerpVector(double[] v) {
        double[] z = new double[]{0, 0, 1};
        double[] x = new double[]{1, 0, 0};
        double dotZ = Math.abs(v[0] * z[0] + v[1] * z[1] + v[2] * z[2]);
        if (dotZ < 0.9) {
            return cross(v, z);
        } else {
            return cross(v, x);
        }
    }

    /**
     * Computes the cross product of two 3D vectors.
     */
    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    /**
     * Computes the connectivity cost as the sum of squared distances of all edges.
     */
    private static double computeConnectivityCost(Graph g, Map<Integer, double[]> positions) {
        double cost = 0.0;
        for (Graph.Edge e : g.getEdges()) {
            double[] p1 = positions.get(e.u);
            double[] p2 = positions.get(e.v);
            if (p1 == null || p2 == null) continue;
            double dx = p1[0] - p2[0];
            double dy = p1[1] - p2[1];
            double dz = p1[2] - p2[2];
            cost += (dx * dx + dy * dy + dz * dz);
        }
        return cost;
    }

    /**
     * Rebuilds vertex positions from the current axes and triangle parameters,
     * then computes and returns the connectivity cost.
     */
    private static double attemptRecomputeAndCost(
            Graph g,
            Map<Integer, int[]> triangleToVerticesMap1,
            Map<Integer, int[]> triangleToVerticesMap2,
            double scale,
            double[] axis1,
            double[] axis2,
            TriangleParams[] triParams1,
            TriangleParams[] triParams2
    ) {
        Map<Integer, double[]> positions = new HashMap<>();
        // Group 1 triangles.
        for (int t = 0; t < triParams1.length; t++) {
            int[] vIDs = triangleToVerticesMap1.get(t);
            for (int i = 0; i < 3; i++) {
                double[] pos = computeVertexPositionOnAxis(axis1, triParams1[t], i, scale);
                positions.put(vIDs[i], pos);
            }
        }
        // Group 2 triangles.
        for (int t = 0; t < triParams2.length; t++) {
            int[] vIDs = triangleToVerticesMap2.get(t);
            for (int i = 0; i < 3; i++) {
                double[] pos = computeVertexPositionOnAxis(axis2, triParams2[t], i, scale);
                positions.put(vIDs[i], pos);
            }
        }
        return computeConnectivityCost(g, positions);
    }

    /**
     * Makes small random adjustments to the parameters of a given triangle.
     */
    private static void smallRandomTweakTriangle(
            TriangleParams tp,
            Random random,
            Graph g,
            Map<Integer, int[]> triangleToVerticesMap1,
            Map<Integer, int[]> triangleToVerticesMap2,
            double scale,
            double[] axis1,
            double[] axis2,
            TriangleParams[] triParams1,
            TriangleParams[] triParams2
    ) {
        // Compute baseline cost using current parameters.
        double baselineCost = attemptRecomputeAndCost(g, triangleToVerticesMap1, triangleToVerticesMap2, scale, axis1, axis2, triParams1, triParams2);

        // Tweak offset.
        double origOffset = tp.offset;
        double tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
        tp.offset += tweak;
        // Ensure offset remains positive
        //if (tp.offset < EPS) {
         //   tp.offset = EPS;
        //}
        double costNew = attemptRecomputeAndCost(g, triangleToVerticesMap1, triangleToVerticesMap2, scale, axis1, axis2, triParams1, triParams2);
        if (costNew < baselineCost) {
            baselineCost = costNew;
        } else {
            tp.offset = origOffset; // revert
        }

        // Tweak scale unless this is the fixed triangle (anchor) to avoid catastrophic scaling.
        if (!(triParams1.length > 0 && tp == triParams1[0])) {
            double origScale = tp.scale;
            tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
            tp.scale += tweak;
            if (tp.scale < EPS)
                tp.scale = EPS;
            costNew = attemptRecomputeAndCost(g, triangleToVerticesMap1, triangleToVerticesMap2, scale, axis1, axis2, triParams1, triParams2);
            if (costNew < baselineCost) {
                baselineCost = costNew;
            } else {
                tp.scale = origScale;
            }
        }

        // Tweak rotation.
        double origRotation = tp.rotation;
        tweak = (random.nextDouble() - 0.5) * STEP_SIZE * 2;
        tp.rotation += tweak;
        costNew = attemptRecomputeAndCost(g, triangleToVerticesMap1, triangleToVerticesMap2, scale, axis1, axis2, triParams1, triParams2);
        if (!(costNew < baselineCost)) {
            tp.rotation = origRotation;
        }
    }

    public static Map<Integer, double[]> computeLayout(String generatorS, int iterations, long seed, double scale) {
        Map<Integer, double[]> positions = computeLayoutN(generatorS, iterations, seed, scale, iterations);
        return positions;
    }

    public static Map<Integer, double[]> computeLayoutN(String generatorS, int iterations, long seed, double scale, int tries) {

        int[][][] generatorOrigNumbers = GroupExplorer.parseOperationsArr(generatorS);
        int[][][] generator = GroupExplorer.parseOperationsArr(GroupExplorer.renumberGeneratorNotation(generatorS));
        // Create a mapping of the original numbers to the new numbers
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
        int nTriangles1 = generator[0].length;
        int nTriangles2 = generator[1].length;

        TreeSet<Integer> equivalentVertices = new TreeSet<>();

        // Loop through second set of triangles and build the graph.
        Graph g = new Graph();
        int triangle2Offset = nTriangles1 * 3;
        for (int t = 0; t < nTriangles2; t++) {
            int[] triangle = generator[1][t];
            for (int j = 0; j < 3; j++) {
                int idx = triangle[j];
                if (idx <= triangle2Offset) {
                    int trinagle2Idx = triangle2Offset + t * 3 + 1 + j;
                    g.addEdge(trinagle2Idx, idx);
                    equivalentVertices.add(trinagle2Idx);
                    System.out.println("Adding edge " + trinagle2Idx + " -> " + idx);
                }
            }
        }

        System.out.println("Seed: " + seed + " iters: " + iterations);

        double bestFit = Double.MAX_VALUE;
        LayoutResult result = null;

        Random random = new Random(seed);
        for (int i = 0; i < tries; i++) {
            LayoutResult result0 = computeLayout(g, nTriangles1, nTriangles2, iterations, random, 1);
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
            // get the radius of the vector
            double radius = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
            maxRadius = Math.max(maxRadius, radius);
            resultList.add(new double[]{pos[0], pos[1], pos[2]});
        }
        Map<Integer, double[]> positions = new HashMap<>();

        scale /= maxRadius * 2;

        ArrayList<double[]> dupePositions = new ArrayList<>();
        // Re-index
        int nextKey = 1;
        for (int i = 1; i <= resultList.size(); i++) {
            double[] pos = resultList.get(i - 1);
            if (!equivalentVertices.contains(i)) {
                System.out.println("Adding vertex " + nextKey + " at " + Arrays.toString(pos));
                positions.put(newToOriginal.get(nextKey), new double[]{pos[0] * scale, pos[1] * scale, pos[2] * scale});
                nextKey++;
            } else {
                System.out.println("Skipping vertex " + i);
                dupePositions.add(pos);
            }
        }

        for (int i = 0; i < dupePositions.size(); i++) {
            double[] pos = dupePositions.get(i);
            positions.put(nextKey, new double[]{pos[0] * scale, pos[1] * scale, pos[2] * scale});
            nextKey++;
        }

        System.out.println("Fit: " + result.fit);
        System.out.println("Axis 1: " + Arrays.toString(result.axis1));
        System.out.println("Axis 2: " + Arrays.toString(result.axis2));

        return positions;
    }

    public static void main(String[] args) {
        // Run the AxisConstrainedLayout algorithm
        int iterations = 1000;
        long seed = 5111;

        int nTriangles1 = 2;
        int nTriangles2 = 2;

        // Vertices are numbered 1,2,3,    4,5,6
        //                       7,8,9,    10,11,12

        // Merge 2<->7, 4<->9, 5<->10

        Graph mergedVertices = new Graph();
        mergedVertices.addEdge(2, 7);
        mergedVertices.addEdge(4, 9);
        mergedVertices.addEdge(5, 10);

        LayoutResult result = computeLayout(mergedVertices, nTriangles1, nTriangles2, iterations, new Random(seed), 1);

        // Print the results
        System.out.println("Final Positions:");
        for (Map.Entry<Integer, double[]> entry : result.positions.entrySet()) {
            System.out.println("Vertex " + entry.getKey() + ": " + Arrays.toString(entry.getValue()));
        }

        System.out.println("Axis 1: " + Arrays.toString(result.axis1));
        System.out.println("Axis 2: " + Arrays.toString(result.axis2));

        // Now test it with generator notation.
        String generatorS = "[(1,2,3)(4,5,6),(2,7,4)(5,8,9)]";
        Map<Integer, double[]> result2 = computeLayout(generatorS, iterations, seed, 1);
        for (Map.Entry<Integer, double[]> entry : result2.entrySet()) {
            System.out.println("Vertex " + entry.getKey() + ": " + Arrays.toString(entry.getValue()));
        }

    }

    private static double[] computeAxis2FromAngle(double angle, double[] e0, double[] e1) {
        return new double[]{
            Math.cos(angle) * e0[0] + Math.sin(angle) * e1[0],
            Math.cos(angle) * e0[1] + Math.sin(angle) * e1[1],
            Math.cos(angle) * e0[2] + Math.sin(angle) * e1[2]
        };
    }

    private static void smallRandomTweakAxis2(Random random,
                                              double[] axis2AngleHolder,
                                              double[] e0,
                                              double[] e1,
                                              Graph g,
                                              Map<Integer, int[]> triangleToVerticesMap1,
                                              Map<Integer, int[]> triangleToVerticesMap2,
                                              double scale,
                                              double[] fixedAxis1,
                                              TriangleParams[] triParams1,
                                              TriangleParams[] triParams2) {
        double originalAngle = axis2AngleHolder[0];
        double[] currentAxis2 = computeAxis2FromAngle(originalAngle, e0, e1);
        double baselineCost = attemptRecomputeAndCost(g, triangleToVerticesMap1, triangleToVerticesMap2,
                                                      scale, fixedAxis1, currentAxis2,
                                                      triParams1, triParams2);
        double tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
        axis2AngleHolder[0] = originalAngle + tweak;
        double[] newAxis2 = computeAxis2FromAngle(axis2AngleHolder[0], e0, e1);
        double newCost = attemptRecomputeAndCost(g, triangleToVerticesMap1, triangleToVerticesMap2,
                                                 scale, fixedAxis1, newAxis2,
                                                 triParams1, triParams2);
        if (newCost >= baselineCost) {
            axis2AngleHolder[0] = originalAngle;
        }
    }

    private static void localParameterSearchRestricted(Random random,
                                                         double[] fixedAxis1,
                                                         double[] e0,
                                                         double[] e1,
                                                         double[] axis2AngleHolder,
                                                         TriangleParams[] triParams1,
                                                         TriangleParams[] triParams2,
                                                         Graph g,
                                                         Map<Integer, int[]> triangleToVerticesMap1,
                                                         Map<Integer, int[]> triangleToVerticesMap2,
                                                         double scale) {
        // Do not tweak fixedAxis1
        smallRandomTweakAxis2(random, axis2AngleHolder, e0, e1, g, triangleToVerticesMap1, triangleToVerticesMap2, scale,
                               fixedAxis1, triParams1, triParams2);
        for (TriangleParams tp : triParams1) {
            smallRandomTweakTriangle(tp, random, g, triangleToVerticesMap1, triangleToVerticesMap2, scale,
                                      fixedAxis1, computeAxis2FromAngle(axis2AngleHolder[0], e0, e1), triParams1, triParams2);
        }
        for (TriangleParams tp : triParams2) {
            smallRandomTweakTriangle(tp, random, g, triangleToVerticesMap1, triangleToVerticesMap2, scale,
                                      fixedAxis1, computeAxis2FromAngle(axis2AngleHolder[0], e0, e1), triParams1, triParams2);
        }
    }
} 