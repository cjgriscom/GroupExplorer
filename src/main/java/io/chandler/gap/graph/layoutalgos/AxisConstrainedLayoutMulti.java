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
 * An N-group axis-constrained layout. 
 * 
 * Previously, this code handled exactly two groups of polygons along two 3D axes. 
 * Now we generalize to N groups, each with its own 3D axis. 
 * 
 * Each group i has:
 *   - nPolygons[i] polygons
 *   - verticesPerPolygon[i] vertices per polygon
 *   - a random axis direction axis[i]
 *   - an array of PolygonParams for each polygon in that group (holding offset, scale, rotation)
 * 
 * We place each polygon i's center on its group's axis, then apply a local 2D shape 
 * (triangle, line, square, etc.) scaled/rotated/perpendicular in 3D. 
 *
 * The iterative approach adds up all edges' lengths in the merged graph (connectivity cost) 
 * and attempts small random tweaks (in parameters and axes) that reduce the cost.
 */
public class AxisConstrainedLayoutMulti extends LayoutAlgo {

    private Map<Integer, double[]> result;
    private double fitOut = -1;
    
    @Override
    public LayoutAlgoArg[] getArgs() {
        return new LayoutAlgoArg[]{
            LayoutAlgoArg.ITERS,
            LayoutAlgoArg.SEED,
            LayoutAlgoArg.SHOW_FITTED_NODES,
            LayoutAlgoArg.TRIES,
            LayoutAlgoArg.INITIAL_ITERS
        };
    }
    @Override
    public void performLayout(double boxSize, String generator, org.jgrapht.Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args) {
        double[] fitOut = new double[]{-1};
        result = computeLayoutN(generator,
                    args.get(LayoutAlgoArg.ITERS).intValue(),
                    args.get(LayoutAlgoArg.SEED).longValue(),
                    boxSize,
                    args.get(LayoutAlgoArg.TRIES).intValue(),
                    args.get(LayoutAlgoArg.INITIAL_ITERS).intValue(),
                    !args.get(LayoutAlgoArg.SHOW_FITTED_NODES).equals(0.0),
                    fitOut);
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

    // =========================================
    // Constants / configuration
    // =========================================
    private static final double EPS = 1e-6;
    private static final double THRESHOLD = 1e-5;
    private static final double STEP_SIZE = 0.05;

    // =========================================
    // Data structures
    // =========================================

    /**
     * A small helper class to hold polygon parameters for each polygon:
     *   offset  (distance from origin along the axis)
     *   scale   (scale factor of local 2D shape)
     *   rotation (in-plane rotation for the shape)
     */
    public static class PolygonParams {
        public double offset;    // distance from origin along the axis
        public double scale;     // scale factor of the polygon
        public double rotation;  // rotation (radians) in the plane perpendicular to the axis

        public PolygonParams(double offset, double scale, double rotation) {
            this.offset = offset;
            this.scale = scale;
            this.rotation = rotation;
        }
    }

    /**
     * The result of a layout computation. 
     *  - positions: Map vertex -> {x,y,z}
     *  - axes: an array of length N, each axis[i] is a double[3] representing the direction 
     *          of group i's axis
     *  - fit: the final connectivity cost
     */
    public static class LayoutResult {
        public Map<Integer,double[]> positions;
        public double[][] axes;   // axes[i] is the direction for group i
        public double fit;

        public LayoutResult(Map<Integer,double[]> positions, double[][] axes, double fit) {
            this.positions = positions;
            this.axes = axes;
            this.fit = fit;
        }
    }

    // =========================================
    // Public entry point
    // =========================================

    public static Map<Integer, double[]> computeLayoutN(String generatorS, int iterations, long seed, double scale, int tries, int initialIters, boolean showFittedNodes, double[] fitOut) {
        int[][][] generatorOrigNumbers = GroupExplorer.parseOperationsArr(generatorS);
        int[][][] generator = GroupExplorer.parseOperationsArr(GroupExplorer.renumberGeneratorNotation(generatorS));
        // Create a mapping of the original numbers to the new numbers.
        Map<Integer, Integer> newToOriginal = new HashMap<>();
        for (int i = 0; i < generatorOrigNumbers.length; i++) {
            for (int j = 0; j < generatorOrigNumbers[i].length; j++) {
                for (int k = 0; k < generatorOrigNumbers[i][j].length; k++) {
                    newToOriginal.put(generator[i][j][k], generatorOrigNumbers[i][j][k]);
                }
            }
        }
        
        System.out.println(Arrays.deepToString(generatorOrigNumbers));
        System.out.println(Arrays.deepToString(generator));
        // Support an arbitrary number of groups.
        int nGroups = generator.length;

        // For each group, determine the number of polygons and vertices per polygon.
        int[] nPolygons = new int[nGroups];
        int[] verticesPerPolygon = new int[nGroups];
        for (int i = 0; i < nGroups; i++) {
            nPolygons[i] = generator[i].length;
            verticesPerPolygon[i] = generator[i][0].length;
        }

        // Compute global offsets for each group (first group's vertices start at 1).
        int[] globalOffsets = new int[nGroups];
        globalOffsets[0] = 1;
        for (int i = 1; i < nGroups; i++) {
            globalOffsets[i] = globalOffsets[i-1] + nPolygons[i-1] * verticesPerPolygon[i-1];
        }
        int totalVertices = globalOffsets[nGroups-1] + nPolygons[nGroups-1] * verticesPerPolygon[nGroups-1] - 1;

        // Build the connectivity graph.
        Graph g = new Graph();

        // Construct a new generator that's numbered sequentially
        int itmp = 1;
        int[][][] generatorSeq = new int[nGroups][][];
        for (int i = 0; i < nGroups; i++) {
            generatorSeq[i] = new int[nPolygons[i]][verticesPerPolygon[i]];
            for (int j = 0; j < nPolygons[i]; j++) {
                for (int k = 0; k < verticesPerPolygon[i]; k++) {
                    generatorSeq[i][j][k] = itmp++;
                }
            }
        }
        System.out.println(Arrays.deepToString(generatorSeq));

        TreeSet<Integer> equivalentVertices = new TreeSet<>();
        
        // Now loop through generator and generatorSeq, and add edges between equivalent vertices.

        // This tracks transient mappings so the graph is fully connected
        TreeMap<Integer, List<Integer>> alreadyAddedFromSrc = new TreeMap<>();
        // I suppose this could be done after the thingy is built

        // This hurts my head but I think it's correct.
        TreeMap<Integer, Integer> idxToPolyGlobalId = new TreeMap<>();
        for (int i = 0; i < nGroups; i++) {
            for (int j = 0; j < nPolygons[i]; j++) {
                for (int k = 0; k < verticesPerPolygon[i]; k++) {
                    int idx = generator[i][j][k];
                    int polyGlobalId = generatorSeq[i][j][k];
                    
                    if (alreadyAddedFromSrc.containsKey(idx)) { // Need an edge from
                        int idxMapped = idxToPolyGlobalId.get(idx);
                        g.addEdge(polyGlobalId, idxMapped);
                        System.out.println("Adding edge " + polyGlobalId + " -> " + idxMapped);
                        List<Integer> list = alreadyAddedFromSrc.get(idx);
                        for (Integer other : list) {
                            System.out.println("    " + polyGlobalId + " -> " + other);
                            g.addEdge(polyGlobalId, other);
                        }
                        list.add(polyGlobalId);
                        equivalentVertices.add(polyGlobalId);
                    } else {
                        idxToPolyGlobalId.put(idx, polyGlobalId);
                        alreadyAddedFromSrc.put(idx, new ArrayList<>());
                    }
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
            LayoutResult result0 = computeLayout(g, nGroups, nPolygons, verticesPerPolygon, initialIters, random, 1.0);
            if (result0.fit < bestFit) {
                bestFit = result0.fit;
                result = result0;
                savedRandom = tmp;
            }
        }
        // Recompute the layout with the best fit.
        if (initialIters != iterations) result = computeLayout(g, nGroups, nPolygons, verticesPerPolygon, iterations, savedRandom, 1.0);
        System.out.println("Final positions: ");
        for (Map.Entry<Integer, double[]> entry : result.positions.entrySet()) {
            System.out.println("Vertex " + entry.getKey() + ": " + Arrays.toString(entry.getValue()));
        }

        // Normalize positions and re-index.
        ArrayList<double[]> resultList = new ArrayList<>();
        double maxRadius = 0;
        for (int i = 1; i <= totalVertices; i++) {
            double[] pos = result.positions.get(i);
            double radius = Math.sqrt(pos[0]*pos[0] + pos[1]*pos[1] + pos[2]*pos[2]);
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
                System.out.println("Adding vertex " + nextKey + " (" + newToOriginal.get(nextKey) + ") at " + Arrays.toString(pos));
                positions.put(newToOriginal.get(nextKey), new double[]{pos[0]*scale, pos[1]*scale, pos[2]*scale});
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

        System.out.println("Fit: " + result.fit);
        fitOut[0] = result.fit;
        for (int i = 0; i < result.axes.length; i++) {
            System.out.println("Axis " + i + ": " + Arrays.toString(result.axes[i]));
        }

        return positions;
    }

    /**
     * Computes a layout for N groups of polygons. 
     * 
     * @param mergedGraph a graph containing edges among all vertices in all groups
     * @param nGroups number of groups
     * @param nPolygons an array of length nGroups, nPolygons[i] is how many polygons in group i
     * @param verticesPerPolygon an array of length nGroups, verticesPerPolygon[i] is how many vertices in each polygon in group i
     * @param iterations maximum number of main iterations
     * @param random random number generator
     * @param scale global scale factor
     * @return a LayoutResult containing the final positions, axes, and cost
     */
    public static LayoutResult computeLayout(
            Graph mergedGraph,
            int nGroups,
            int[] nPolygons,
            int[] verticesPerPolygon,
            int iterations,
            Random random,
            double scale
    ) {
        // --------------------------------
        // 1) Build data structures
        // --------------------------------

        // For each group i, define an axis in 3D
        double[][] axes = new double[nGroups][];
        for (int i = 0; i < nGroups; i++) {
            axes[i] = randomDirection(random);  // random normalized direction
        }

        // For each group i, define an array of polygon parameters
        PolygonParams[][] polyParams = new PolygonParams[nGroups][];
        for (int i = 0; i < nGroups; i++) {
            polyParams[i] = new PolygonParams[nPolygons[i]];
            for (int j = 0; j < nPolygons[i]; j++) {
                // offset in [-1,1], scale=1, rotation in [0,2π)
                polyParams[i][j] = new PolygonParams(
                        random.nextDouble()*2 - 1,  // offset
                        1.0,                        // scale
                        random.nextDouble()*2*Math.PI
                );
            }
        }

        // For each group i, build a mapping from group-specific polygon index 
        // to the vertex IDs in the global numbering.
        // We'll track the next available vertex ID as we progress through the groups.
        @SuppressWarnings("unchecked")
        Map<Integer,int[]>[] polygonToVerticesMap = new Map[nGroups];
        int currentVertexId = 1;
        for (int i = 0; i < nGroups; i++) {
            polygonToVerticesMap[i] = buildPolygonToVertexMap(currentVertexId, nPolygons[i], verticesPerPolygon[i]);
            currentVertexId += nPolygons[i] * verticesPerPolygon[i];
        }

        // For each group, define the local2D shape for each polygon
        double[][][] local2DGroups = new double[nGroups][][];
        for (int i = 0; i < nGroups; i++) {
            local2DGroups[i] = getDefaultLocal2DPolygon(verticesPerPolygon[i]);
        }

        // We'll maintain a map from vertex -> position in each iteration
        Map<Integer,double[]> vertexPositions = new HashMap<>();

        // --------------------------------
        // 2) Main iterative solver
        // --------------------------------
        double finalCost = 0.0;
        for (int iter = 0; iter < iterations; iter++) {
            // (a) Recompute all vertex positions for all groups
            for (int gIndex = 0; gIndex < nGroups; gIndex++) {
                for (int polyIndex = 0; polyIndex < nPolygons[gIndex]; polyIndex++) {
                    PolygonParams pp = polyParams[gIndex][polyIndex];
                    int[] vIDs = polygonToVerticesMap[gIndex].get(polyIndex);
                    for (int v = 0; v < verticesPerPolygon[gIndex]; v++) {
                        double[] pos = computePolygonVertexPositionOnAxis(
                                axes[gIndex], pp, v, scale, local2DGroups[gIndex]
                        );
                        vertexPositions.put(vIDs[v], pos);
                    }
                }
            }

            // (b) Compute connectivity cost
            double costBefore = computeConnectivityCost(mergedGraph, vertexPositions);

            // (c) Local search: tweak each axis and polygon
            localParameterSearchNGroups(
                random, 
                axes, 
                polyParams, 
                mergedGraph, 
                polygonToVerticesMap, 
                scale,
                verticesPerPolygon, 
                local2DGroups
            );

            // (d) Recompute positions after local search
            for (int gIndex = 0; gIndex < nGroups; gIndex++) {
                for (int polyIndex = 0; polyIndex < nPolygons[gIndex]; polyIndex++) {
                    int[] vIDs = polygonToVerticesMap[gIndex].get(polyIndex);
                    for (int v = 0; v < verticesPerPolygon[gIndex]; v++) {
                        vertexPositions.put(
                                vIDs[v], 
                                computePolygonVertexPositionOnAxis(axes[gIndex], polyParams[gIndex][polyIndex], v, scale, local2DGroups[gIndex])
                        );
                    }
                }
            }
            double costAfter = computeConnectivityCost(mergedGraph, vertexPositions);

            // (e) Stop if improvement is small
            if (Math.abs(costBefore - costAfter) < THRESHOLD) {
                finalCost = costAfter;
                break;
            }
            finalCost = costAfter;
        }

        // One last refresh of positions in case we haven't done so after the final iteration
        for (int gIndex = 0; gIndex < nGroups; gIndex++) {
            for (int polyIndex = 0; polyIndex < nPolygons[gIndex]; polyIndex++) {
                int[] vIDs = polygonToVerticesMap[gIndex].get(polyIndex);
                for (int v = 0; v < verticesPerPolygon[gIndex]; v++) {
                    vertexPositions.put(
                            vIDs[v], 
                            computePolygonVertexPositionOnAxis(axes[gIndex], polyParams[gIndex][polyIndex], v, scale, local2DGroups[gIndex])
                    );
                }
            }
        }

        // Normalize axes
        for (int i = 0; i < nGroups; i++) {
            normalize(axes[i]);
        }

        // Return result
        return new LayoutResult(vertexPositions, axes, finalCost);
    }

    // =========================================
    // Local search
    // =========================================

    /**
     * Attempts small random tweaks to each axis and each polygon's parameters
     * for all N groups.
     */
    private static void localParameterSearchNGroups(
            Random random,
            double[][] axes,
            PolygonParams[][] polyParams,
            Graph mergedGraph,
            Map<Integer,int[]>[] polygonToVerticesMap,
            double scale,
            int[] verticesPerPolygon,
            double[][][] local2DGroups
    ) {
        int nGroups = axes.length;
        // Tweak each axis
        for (int gIndex = 0; gIndex < nGroups; gIndex++) {
            smallRandomTweakAxis(random, axes, gIndex,
                mergedGraph, polyParams, polygonToVerticesMap,
                scale, verticesPerPolygon, local2DGroups
            );
        }
        // Tweak each polygon in each group
        for (int gIndex = 0; gIndex < nGroups; gIndex++) {
            for (int polyIndex = 0; polyIndex < polyParams[gIndex].length; polyIndex++) {
                smallRandomTweakPolygon(
                        polyParams[gIndex][polyIndex],
                        random,
                        mergedGraph,
                        axes,
                        polyParams,
                        polygonToVerticesMap,
                        scale,
                        verticesPerPolygon, 
                        local2DGroups
                );
            }
        }
    }

    /**
     * Makes a small random tweak to the direction of axis[gIndex].
     * We do so by adding a small random offset in each component, 
     * and accept it if it lowers the cost. Otherwise revert.
     */
    private static void smallRandomTweakAxis(
            Random random,
            double[][] axes,
            int gIndex,
            Graph mergedGraph,
            PolygonParams[][] polyParams,
            Map<Integer,int[]>[] polygonToVerticesMap,
            double scale,
            int[] verticesPerPolygon,
            double[][][] local2DGroups
    ) {
        double[] axis = axes[gIndex];
        double[] originalAxis = new double[]{axis[0], axis[1], axis[2]};

        double baselineCost = attemptRecomputeAndCost(
                mergedGraph, axes, polyParams, polygonToVerticesMap,
                scale, verticesPerPolygon, local2DGroups
        );

        // Tweak each component by up to +/- STEP_SIZE
        axis[0] += (random.nextDouble() - 0.5) * STEP_SIZE;
        axis[1] += (random.nextDouble() - 0.5) * STEP_SIZE;
        axis[2] += (random.nextDouble() - 0.5) * STEP_SIZE;
        if (length(axis) < EPS) {
            // avoid zero vector
            axis[0] = originalAxis[0];
            axis[1] = originalAxis[1];
            axis[2] = originalAxis[2];
        }
        double newCost = attemptRecomputeAndCost(
                mergedGraph, axes, polyParams, polygonToVerticesMap,
                scale, verticesPerPolygon, local2DGroups
        );
        if (newCost >= baselineCost) {
            // revert
            axis[0] = originalAxis[0];
            axis[1] = originalAxis[1];
            axis[2] = originalAxis[2];
        }
    }

    /**
     * Makes small random adjustments to the offset, scale, and rotation 
     * of a given polygon's parameters. 
     */
    private static void smallRandomTweakPolygon(
            PolygonParams pp,
            Random random,
            Graph mergedGraph,
            double[][] axes,
            PolygonParams[][] polyParams,
            Map<Integer,int[]>[] polygonToVerticesMap,
            double scale,
            int[] verticesPerPolygon,
            double[][][] local2DGroups
    ) {
        // baseline
        double baselineCost = attemptRecomputeAndCost(
                mergedGraph, axes, polyParams, polygonToVerticesMap,
                scale, verticesPerPolygon, local2DGroups
        );

        // Tweak offset
        double origOffset = pp.offset;
        double tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
        pp.offset += tweak;
        double newCost = attemptRecomputeAndCost(
                mergedGraph, axes, polyParams, polygonToVerticesMap,
                scale, verticesPerPolygon, local2DGroups
        );
        if (newCost < baselineCost) {
            baselineCost = newCost;
        } else {
            pp.offset = origOffset;
        }

        // Tweak scale unless this is a fixed (anchor) polygon.
        if (!(polyParams[0].length > 0 && pp == polyParams[0][0])) {
            double origScale = pp.scale;
            tweak = (random.nextDouble() - 0.5) * STEP_SIZE;
            pp.scale += tweak;
            if (pp.scale < EPS) pp.scale = EPS;
            newCost = attemptRecomputeAndCost(
                    mergedGraph, axes, polyParams, polygonToVerticesMap,
                    scale, verticesPerPolygon, local2DGroups
            );
            if (newCost < baselineCost) {
                baselineCost = newCost;
            } else {
                pp.scale = origScale;
            }
        }

        // Tweak rotation
        double origRotation = pp.rotation;
        tweak = (random.nextDouble() - 0.5) * STEP_SIZE * 2;
        pp.rotation += tweak;
        newCost = attemptRecomputeAndCost(
                mergedGraph, axes, polyParams, polygonToVerticesMap,
                scale, verticesPerPolygon, local2DGroups
        );
        if (!(newCost < baselineCost)) {
            pp.rotation = origRotation;
        }
    }

    // =========================================
    // Rebuild and cost
    // =========================================

    /**
     * Rebuilds all vertex positions from the current axes + polyParams, 
     * then computes connectivity cost. 
     */
    private static double attemptRecomputeAndCost(
            Graph mergedGraph,
            double[][] axes, 
            PolygonParams[][] polyParams,
            Map<Integer,int[]>[] polygonToVerticesMap,
            double scale,
            int[] verticesPerPolygon,
            double[][][] local2DGroups
    ) {
        Map<Integer,double[]> positions = new HashMap<>();
        int nGroups = axes.length;
        for (int gIndex = 0; gIndex < nGroups; gIndex++) {
            for (int polyIndex = 0; polyIndex < polyParams[gIndex].length; polyIndex++) {
                int[] vIDs = polygonToVerticesMap[gIndex].get(polyIndex);
                for (int v = 0; v < verticesPerPolygon[gIndex]; v++) {
                    double[] pos = computePolygonVertexPositionOnAxis(
                            axes[gIndex], 
                            polyParams[gIndex][polyIndex], 
                            v, 
                            scale, 
                            local2DGroups[gIndex]
                    );
                    positions.put(vIDs[v], pos);
                }
            }
        }
        return computeConnectivityCost(mergedGraph, positions);
    }

    // =========================================
    // Positioning
    // =========================================

    /**
     * Computes the 3D position of the v-th vertex in a polygon, 
     * given the group's axis, the polygon params, and a local2D shape. 
     */
    private static double[] computePolygonVertexPositionOnAxis(
            double[] axis, 
            PolygonParams pp, 
            int vIndex, 
            double globalScale, 
            double[][] local2D
    ) {
        // The polygon's center is offset * axis
        double[] center = new double[]{
            axis[0]*pp.offset,
            axis[1]*pp.offset,
            axis[2]*pp.offset
        };

        // local2D[vIndex] is (lx, ly) in 2D
        double lx = local2D[vIndex][0];
        double ly = local2D[vIndex][1];

        // apply scale
        lx *= (pp.scale * globalScale);
        ly *= (pp.scale * globalScale);

        // apply rotation
        double cosR = Math.cos(pp.rotation);
        double sinR = Math.sin(pp.rotation);
        double rx = lx*cosR - ly*sinR;
        double ry = lx*sinR + ly*cosR;

        // find two perpendicular vectors to 'axis'
        double[] perp1 = findPerpVector(axis);
        normalize(perp1);
        double[] perp2 = cross(axis, perp1);
        normalize(perp2);

        // combine center + rx*perp1 + ry*perp2
        return new double[]{
            center[0] + rx*perp1[0] + ry*perp2[0],
            center[1] + rx*perp1[1] + ry*perp2[1],
            center[2] + rx*perp1[2] + ry*perp2[2]
        };
    }

    // =========================================
    // Local shape creation
    // =========================================

    /**
     * buildPolygonToVertexMap: 
     * group i => polygon index p -> {list of vertex IDs}
     */
    private static Map<Integer,int[]> buildPolygonToVertexMap(int startVertex, int nPolygons, int verticesPerPolygon) {
        Map<Integer,int[]> map = new HashMap<>();
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
     * getDefaultLocal2DPolygon: returns local 2D coordinates for 
     * various polygon shapes. 
     * 
     * For 2 vertices, returns a line from -0.5 to 0.5. 
     * Otherwise, returns a regular polygon of radius 1, 
     * centered at the origin.
     */
    private static double[][] getDefaultLocal2DPolygon(int vertexCount) {
        double[][] shape = new double[vertexCount][2];
        if (vertexCount == 2) {
            shape[0][0] = -0.5; shape[0][1] = 0.0;
            shape[1][0] =  0.5; shape[1][1] = 0.0;
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
    // 3D Vector helpers
    // =========================================

    private static double length(double[] v) {
        return Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
    }

    private static void normalize(double[] v) {
        double len = length(v);
        if (len < EPS) return;
        v[0]/=len; v[1]/=len; v[2]/=len;
    }

    private static double[] randomDirection(Random rand) {
        // generate a random vector, then normalize
        double x = rand.nextDouble()*2 - 1;
        double y = rand.nextDouble()*2 - 1;
        double z = rand.nextDouble()*2 - 1;
        double len = Math.sqrt(x*x + y*y + z*z);
        if (len < EPS) {
            // fallback
            return new double[]{1,0,0};
        }
        return new double[]{ x/len, y/len, z/len };
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
            a[1]*b[2] - a[2]*b[1],
            a[2]*b[0] - a[0]*b[2],
            a[0]*b[1] - a[1]*b[0]
        };
    }

    /**
     * Finds an arbitrary vector perpendicular to v. 
     */
    private static double[] findPerpVector(double[] v) {
        // if v is not close to z-axis, cross with z
        double[] z = new double[]{0,0,1};
        double[] x = new double[]{1,0,0};
        double dotZ = Math.abs(v[0]*z[0] + v[1]*z[1] + v[2]*z[2]);
        if (dotZ < 0.9) {
            return cross(v,z);
        } else {
            return cross(v,x);
        }
    }

    // =========================================
    // Compute connectivity cost 
    // =========================================
    private static double computeConnectivityCost(Graph g, Map<Integer,double[]> positions) {
        double cost = 0;
        for (Graph.Edge e : g.getEdges()) {
            double[] p1 = positions.get(e.u);
            double[] p2 = positions.get(e.v);
            if (p1==null || p2==null) continue;
            double dx = p1[0]-p2[0];
            double dy = p1[1]-p2[1];
            double dz = p1[2]-p2[2];
            cost += dx*dx + dy*dy + dz*dz;
        }
        return cost;
    }

} 