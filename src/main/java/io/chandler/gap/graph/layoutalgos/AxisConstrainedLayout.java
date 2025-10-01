package io.chandler.gap.graph.layoutalgos;

import java.util.*;

import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GroupExplorer;
import networkx.Graph;

import java.lang.Math;

/**
 * AxisConstrainedLayout places two sets of triangles along two 3D axes that both pass
 * through the origin. Triangles in group 1 lie on axis1, triangles in group 2 lie on axis2.
 * We then adjust:
 *  - The angles of axis1, axis2
 *  - The offset (position along the axis), scale, and rotation in-plane of each triangle
 * so that the sum of distances between connected vertices is minimized.
 */
public class AxisConstrainedLayout extends LayoutAlgo {

    private Map<Integer, double[]> result;
    private double fitOut = -1;
    
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
        double[] fitOut = new double[]{-1};
        double repulsionFactor = args.get(LayoutAlgoArg.REPULSION_FACTOR);
        result = computeLayoutN(generator,
                    args.get(LayoutAlgoArg.ITERS).intValue(),
                    args.get(LayoutAlgoArg.SEED).longValue(),
                    boxSize,
                    args.get(LayoutAlgoArg.TRIES).intValue(),
                    args.get(LayoutAlgoArg.INITIAL_ITERS).intValue(),
                    !args.get(LayoutAlgoArg.SHOW_FITTED_NODES).equals(0.0),
                    fitOut,
                    repulsionFactor);
        this.fitOut = fitOut[0];
    }

    @Override
    public Map<Integer, double[]> getResult() {
        return result;
    }

    @Override
    public Double getFitOut() {
        return fitOut;
    }

    private static final double EPS = 1e-6;
    private static final double THRESHOLD = 1e-7;
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
     * A small helper class to hold polygon parameters.
     * For each polygon we store offsetAlongAxis, scale, rotation.
     */
    public static class PolygonParams {
        public double offset;    // distance from origin along the axis
        public double scale;     // scale factor of the polygon
        public double rotation;  // rotation in the plane perpendicular to the axis

        public PolygonParams(double offset, double scale, double rotation) {
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
            int nPolygons1, int verticesPerPolygon1,
            int nPolygons2, int verticesPerPolygon2,
            int iterations,
            Random random,
            double scale, // global scale factor
            double repulsionFactor // <--- new parameter
    ) {

        // --------------------------------------------------------------------
        // 1) Set up data structures: two 3D axes and per-polygon parameters.
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

        // Initialize per-polygon parameters.
        PolygonParams[] polyParams1 = new PolygonParams[nPolygons1];
        for (int i = 0; i < nPolygons1; i++) {
            polyParams1[i] = new PolygonParams(
                    random.nextDouble() * 2 - 1,              // offset
                    1.0,                                      // initial scale = 1.0
                    random.nextDouble() * 2 * Math.PI         // rotation in [0,2π)
            );
        }
        PolygonParams[] polyParams2 = new PolygonParams[nPolygons2];
        for (int i = 0; i < nPolygons2; i++) {
            polyParams2[i] = new PolygonParams(
                    random.nextDouble() * 2 - 1,
                    1.0,
                    random.nextDouble() * 2 * Math.PI
            );
        }

        // Map from polygon (previously triangle) index to the vertex IDs.
        Map<Integer, int[]> polygonToVerticesMap1 = buildPolygonToVertexMap(1, nPolygons1, verticesPerPolygon1);
        int startIdx2 = verticesPerPolygon1 * nPolygons1 + 1;
        Map<Integer, int[]> polygonToVerticesMap2 = buildPolygonToVertexMap(startIdx2, nPolygons2, verticesPerPolygon2);

        // Get local shapes for each group.
        double[][] local2DGroup1 = getDefaultLocal2DPolygon(verticesPerPolygon1);
        double[][] local2DGroup2 = getDefaultLocal2DPolygon(verticesPerPolygon2);

        // We will update vertex positions in each iteration.
        Map<Integer, double[]> vertexPositions = new HashMap<>();

        // --------------------------------------------------------------------
        // 2) Main iterative loop.
        // --------------------------------------------------------------------
        double costAfter = 0;
        double axisFit = 0;
        for (int iter = 0; iter < iterations; iter++) {

            // (a) Compute vertex positions from the current parameters.
            for (int t = 0; t < nPolygons1; t++) {
                int[] vIDs = polygonToVerticesMap1.get(t);
                PolygonParams pp = polyParams1[t];
                for (int i = 0; i < verticesPerPolygon1; i++) {
                    double[] pos = computePolygonVertexPositionOnAxis(axis1, pp, i, scale, local2DGroup1);
                    vertexPositions.put(vIDs[i], pos);
                }
            }
            double[] currentAxis2 = computeAxis2FromAngle(axis2AngleHolder[0], e0, e1);
            for (int t = 0; t < nPolygons2; t++) {
                int[] vIDs = polygonToVerticesMap2.get(t);
                PolygonParams pp = polyParams2[t];
                for (int i = 0; i < verticesPerPolygon2; i++) {
                    double[] pos = computePolygonVertexPositionOnAxis(currentAxis2, pp, i, scale, local2DGroup2);
                    vertexPositions.put(vIDs[i], pos);
                }
            }

            // (b) Compute connectivity cost.
            double costBefore = computeConnectivityCost(mergedVertices, vertexPositions) + computeRepulsionCost(vertexPositions, repulsionFactor);

            // (c) Perform local parameter search.
            localParameterSearchRestricted(random, axis1, e0, e1, axis2AngleHolder,
                    polyParams1, polyParams2, mergedVertices,
                    polygonToVerticesMap1, polygonToVerticesMap2, scale,
                    verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);

            // Recompute vertex positions after the local changes.
            for (int t = 0; t < nPolygons1; t++) {
                int[] vIDs = polygonToVerticesMap1.get(t);
                for (int i = 0; i < verticesPerPolygon1; i++) {
                    vertexPositions.put(vIDs[i], computePolygonVertexPositionOnAxis(axis1, polyParams1[t], i, scale, local2DGroup1));
                }
            }
            for (int t = 0; t < nPolygons2; t++) {
                int[] vIDs = polygonToVerticesMap2.get(t);
                for (int i = 0; i < verticesPerPolygon2; i++) {
                    vertexPositions.put(vIDs[i], computePolygonVertexPositionOnAxis(currentAxis2, polyParams2[t], i, scale, local2DGroup2));
                }
            }
            axisFit = computeConnectivityCost(mergedVertices, vertexPositions);
            costAfter = axisFit + computeRepulsionCost(vertexPositions, repulsionFactor);

            // (d) Exit early if the cost did not improve substantially.
            if (Math.abs(costBefore - costAfter) < THRESHOLD) {
                break;
            }
        }

        // --------------------------------------------------------------------
        // 3) Finalize: recompute positions and normalize the final axis directions.
        // --------------------------------------------------------------------
        for (int t = 0; t < nPolygons1; t++) {
            int[] vIDs = polygonToVerticesMap1.get(t);
            for (int i = 0; i < verticesPerPolygon1; i++) {
                vertexPositions.put(vIDs[i], computePolygonVertexPositionOnAxis(axis1, polyParams1[t], i, scale, local2DGroup1));
            }
        }
        double[] finalAxis2 = computeAxis2FromAngle(axis2AngleHolder[0], e0, e1);
        for (int t = 0; t < nPolygons2; t++) {
            int[] vIDs = polygonToVerticesMap2.get(t);
            for (int i = 0; i < verticesPerPolygon2; i++) {
                vertexPositions.put(vIDs[i], computePolygonVertexPositionOnAxis(finalAxis2, polyParams2[t], i, scale, local2DGroup2));
            }
        }

        normalize(axis1);
        normalize(axis2);

        return new LayoutResult(vertexPositions, axis1, axis2, axisFit);
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
     * Computes the 3D position of a vertex for a given triangle.
     *
     * <p>
     * 1. The triangle center is along the axis at (offset * axis).  
     * 2. A local equilateral triangle is defined in 2D.
     * 3. We subtract the triangle's centroid so that (0,0) becomes the center.  
     * 4. The triangle is scaled and rotated, then embedded in the plane perpendicular to the axis.
     * </p>
     */
    private static double[] computePolygonVertexPositionOnAxis(double[] axis, PolygonParams pp, int i, double globalScale, double[][] local2D) {
        // Center of the polygon along the axis.
        double[] center = new double[]{axis[0] * pp.offset, axis[1] * pp.offset, axis[2] * pp.offset};

        // Get the local coordinate for vertex i from the provided local2D array.
        double lx = local2D[i][0];
        double ly = local2D[i][1];

        // Scale (by polygon's scale and global scale) and apply rotation.
        double s = pp.scale * globalScale;
        lx *= s;
        ly *= s;
        double cosR = Math.cos(pp.rotation);
        double sinR = Math.sin(pp.rotation);
        double rx = lx * cosR - ly * sinR;
        double ry = lx * sinR + ly * cosR;

        // Embed in 3D: find two orthogonal vectors perpendicular to 'axis'
        double[] perp1 = findPerpVector(axis);
        normalize(perp1);
        double[] perp2 = cross(axis, perp1);
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
     * Computes an inverse-square repulsion cost between vertices
     */
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
                // Inverse-square law for repulsion
                cost += repulsionFactor / dSquared;
                
            }
        }
        return cost;
    }

    /**
     * Rebuilds vertex positions from the current axes and polygon parameters,
     * then computes and returns the connectivity cost.
     */
    private static double attemptRecomputeAndCost(
            Graph g,
            Map<Integer, int[]> polygonToVerticesMap1,
            Map<Integer, int[]> polygonToVerticesMap2,
            double scale,
            double[] axis1,
            double[] axis2,
            PolygonParams[] polyParams1,
            PolygonParams[] polyParams2,
            int verticesPerPolygon1,
            double[][] local2DGroup1,
            int verticesPerPolygon2,
            double[][] local2DGroup2,
            double repulsionFactor) {
        Map<Integer, double[]> positions = new HashMap<>();
        // Group 1 polygons.
        for (int t = 0; t < polyParams1.length; t++) {
            int[] vIDs = polygonToVerticesMap1.get(t);
            for (int i = 0; i < verticesPerPolygon1; i++) {
                double[] pos = computePolygonVertexPositionOnAxis(axis1, polyParams1[t], i, scale, local2DGroup1);
                positions.put(vIDs[i], pos);
            }
        }
        // Group 2 polygons.
        for (int t = 0; t < polyParams2.length; t++) {
            int[] vIDs = polygonToVerticesMap2.get(t);
            for (int i = 0; i < verticesPerPolygon2; i++) {
                double[] pos = computePolygonVertexPositionOnAxis(axis2, polyParams2[t], i, scale, local2DGroup2);
                positions.put(vIDs[i], pos);
            }
        }
        double connectivityCost = computeConnectivityCost(g, positions);
        double repulsionCost = repulsionFactor > 0 ? computeRepulsionCost(positions, repulsionFactor) : 0;
        return connectivityCost + repulsionCost;
    }

    /**
     * Makes small random adjustments to the parameters of a given polygon.
     */
    private static void smallRandomTweakPolygon(
            PolygonParams pp,
            Random random,
            Graph g,
            Map<Integer, int[]> polygonToVerticesMap1,
            Map<Integer, int[]> polygonToVerticesMap2,
            double scale,
            double[] axis1,
            double[] axis2,
            PolygonParams[] polyParams1,
            PolygonParams[] polyParams2,
            int verticesPerPolygon1,
            double[][] local2DGroup1,
            int verticesPerPolygon2,
            double[][] local2DGroup2,
            double repulsionFactor) {
        double baselineCost = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2, scale,
                axis1, axis2, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
    
        // Tweak offset.
        double origOffset = pp.offset;
        double tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
        pp.offset += tweak;
        double costNew = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2, scale,
                axis1, axis2, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
        if (costNew < baselineCost) {
            baselineCost = costNew;
        } else {
            pp.offset = origOffset; // revert
        }
    
        // Tweak scale (if not the anchor polygon).
        if (!(polyParams1.length > 0 && pp == polyParams1[0])) {
            double origScale = pp.scale;
            tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
            pp.scale += tweak;
            if (pp.scale < EPS)
                pp.scale = EPS;
            costNew = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2, scale,
                    axis1, axis2, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
            if (costNew < baselineCost) {
                baselineCost = costNew;
            } else {
                pp.scale = origScale;
            }
        }
    
        // Tweak rotation.
        double origRotation = pp.rotation;
        tweak = (random.nextDouble() - 0.5) * STEP_SIZE * 2;
        pp.rotation += tweak;
        costNew = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2, scale,
                axis1, axis2, polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
        if (!(costNew < baselineCost)) {
            pp.rotation = origRotation;
        }
    }

    public static Map<Integer, double[]> computeLayoutN(String generatorS, int iterations, long seed, double scale, int tries, int initialIters, boolean showFittedNodes, double[] fitOut, double repulsionFactor) {
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
        int nPolygons1 = generator[0].length;
        int nPolygons2 = generator[1].length;
        // Derive the number of vertices per polygon from the generator.
        int verticesPerPolygon1 = generator[0][0].length;
        int verticesPerPolygon2 = generator[1][0].length;

        TreeSet<Integer> equivalentVertices = new TreeSet<>();

        // Loop through second group and build the graph.
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

        System.out.println("Seed: " + seed + " iters: " + iterations + " initialIters: " + initialIters + " tries: " + tries);

        double bestFit = Double.MAX_VALUE;
        LayoutResult result = null;
        Random savedRandom = null;

        Random random = new Random(seed);
        for (int i = 0; i < tries; i++) {
            Random tmp = cloneRandom(random);
            LayoutResult result0 = computeLayout(g, nPolygons1, verticesPerPolygon1, nPolygons2, verticesPerPolygon2, initialIters, random, 1, repulsionFactor);
            if (result0.fit < bestFit) {
                bestFit = result0.fit;
                result = result0;
                savedRandom = tmp;
            }
        }
        if (initialIters != iterations)
            result = computeLayout(g, nPolygons1, verticesPerPolygon1, nPolygons2, verticesPerPolygon2, iterations, savedRandom, 1, repulsionFactor);
        System.out.println("Final positions: ");
        for (Map.Entry<Integer, double[]> entry : result.positions.entrySet()) {
            System.out.println("Vertex " + entry.getKey() + ": " + Arrays.toString(entry.getValue()));
        }

        ArrayList<double[]> resultList = new ArrayList<>();
        double maxRadius = 0;
        for (int i = 1; i <= result.positions.size(); i++) {
            double[] pos = result.positions.get(i);
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

        if (showFittedNodes) {
            for (int i = 0; i < dupePositions.size(); i++) {
                double[] pos = dupePositions.get(i);
                positions.put(nextKey, new double[]{pos[0] * scale, pos[1] * scale, pos[2] * scale});
                nextKey++;
            }
        }

        fitOut[0] = result.fit;
        System.out.println("Fit: " + result.fit);
        System.out.println("Axis 1: " + Arrays.toString(result.axis1));
        System.out.println("Axis 2: " + Arrays.toString(result.axis2));

        return positions;
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
                                              Map<Integer, int[]> polygonToVerticesMap1,
                                              Map<Integer, int[]> polygonToVerticesMap2,
                                              double scale,
                                              double[] fixedAxis1,
                                              PolygonParams[] polyParams1,
                                              PolygonParams[] polyParams2,
                                              int verticesPerPolygon1,
                                              double[][] local2DGroup1,
                                              int verticesPerPolygon2,
                                              double[][] local2DGroup2,
                                              double repulsionFactor) {
        double originalAngle = axis2AngleHolder[0];
        double[] currentAxis2 = computeAxis2FromAngle(originalAngle, e0, e1);
        double baselineCost = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2,
                                                      scale, fixedAxis1, currentAxis2, polyParams1, polyParams2,
                                                      verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
        double tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
        axis2AngleHolder[0] = originalAngle + tweak;
        double[] newAxis2 = computeAxis2FromAngle(axis2AngleHolder[0], e0, e1);
        double newCost = attemptRecomputeAndCost(g, polygonToVerticesMap1, polygonToVerticesMap2,
                                                 scale, fixedAxis1, newAxis2, polyParams1, polyParams2,
                                                 verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
        if (newCost >= baselineCost) {
            axis2AngleHolder[0] = originalAngle;
        }
    }

    private static void localParameterSearchRestricted(Random random,
                                                         double[] fixedAxis1,
                                                         double[] e0,
                                                         double[] e1,
                                                         double[] axis2AngleHolder,
                                                         PolygonParams[] polyParams1,
                                                         PolygonParams[] polyParams2,
                                                         Graph g,
                                                         Map<Integer, int[]> polygonToVerticesMap1,
                                                         Map<Integer, int[]> polygonToVerticesMap2,
                                                         double scale,
                                                         int verticesPerPolygon1,
                                                         double[][] local2DGroup1,
                                                         int verticesPerPolygon2,
                                                         double[][] local2DGroup2,
                                                         double repulsionFactor) {
        smallRandomTweakAxis2(random, axis2AngleHolder, e0, e1, g,
                polygonToVerticesMap1, polygonToVerticesMap2, scale, fixedAxis1,
                polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1,
                verticesPerPolygon2, local2DGroup2, repulsionFactor);
        for (PolygonParams pp : polyParams1) {
            smallRandomTweakPolygon(pp, random, g, polygonToVerticesMap1, polygonToVerticesMap2,
                    scale, fixedAxis1, computeAxis2FromAngle(axis2AngleHolder[0], e0, e1),
                    polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
        }
        for (PolygonParams pp : polyParams2) {
            smallRandomTweakPolygon(pp, random, g, polygonToVerticesMap1, polygonToVerticesMap2,
                    scale, fixedAxis1, computeAxis2FromAngle(axis2AngleHolder[0], e0, e1),
                    polyParams1, polyParams2, verticesPerPolygon1, local2DGroup1, verticesPerPolygon2, local2DGroup2, repulsionFactor);
        }
    }

    // ------------------------------------------------------------------------
    // Updated polygon mapping: now each polygon may have an arbitrary number of vertices.
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

    // ------------------------------------------------------------------------
    // Returns a default local 2D representation for a polygon with the given number of vertices.
    // For a line (2 vertices) we use (-0.5,0) and (0.5,0); otherwise we build a regular polygon.
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
} 