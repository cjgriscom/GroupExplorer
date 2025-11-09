package io.chandler.gap.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import networkx.Graph;

/**
 * A first attempt at exploring "biaxial" arrangements of equal polygons (here:
 * triangles) along two axes.
 *
 * <p>
 * Simplifications for this initial version:
 * <ul>
 *   <li>Both axes carry the same number M of triangles; each triangle has 3 vertices.</li>
 *   <li>Axes are symmetric with respect to the YZ-plane and meet at a fixed angle
 *       {@link #AXIS_ANGLE_DEG} at the origin.</li>
 *   <li>For a given pattern, triangle i on axis 1 is connected to triangle i on axis 2
 *       via one or more touching vertex pairs; we can enumerate either a single pair
 *       per triangle or all non-empty matchings between the 3×3 vertices.</li>
 *   <li>Given a connection pattern, we search for offsets / scales / in-plane rotations
 *       of the triangles (keeping the axes fixed) that minimize the sum of squared
 *       distances between connected vertices.</li>
 * </ul>
 *
 * <p>
 * The goal is not to be exhaustive or optimal, but to provide a concrete,
 * inspectable framework that:
 * <ul>
 *   <li>Enumerates discrete vertex-connection patterns.</li>
 *   <li>Attempts a 3D realization for each pattern via a basic local search.</li>
 *   <li>Optionally enforces a coverage condition along each axis.</li>
 * </ul>
 */
public class BiaxeStudy {

    // ----------------------------
    // Configuration
    // ----------------------------

    /** Number of triangles on each axis (M = N in this first attempt). */
    private static final int NUM_TRIANGLES_PER_AXIS = 2;

    /** Number of vertices per polygon – all equilateral triangles here. */
    private static final int VERTICES_PER_TRIANGLE = 3;

    /**
     * Angle between the two axes (in degrees). The axes are mirror images across
     * the YZ-plane and meet at this angle at the origin.
     */
    private static final double AXIS_ANGLE_DEG = 120.0;

    /** Whether to enforce the coverage condition when accepting layouts. */
    private static final boolean ENFORCE_COVERAGE = true;

    /** Step size for local-search tweaks. */
    private static final double STEP_SIZE = 0.10;

    /** Threshold for stopping the local search when improvements become small. */
    private static final double IMPROVEMENT_THRESHOLD = 1e-6;

    /** Maximum number of iterations for the local search per pattern. */
    private static final int MAX_ITERATIONS = 6000;

    /** Number of random restarts per connection pattern when solving for geometry. */
    private static final int TRIES_PER_PATTERN = 60;

    /** Global scale for the local 2D triangle shape. */
    private static final double GLOBAL_SCALE = 1.0;

    /** Path to the dreadnaut executable (same as in {@link PlanarStudy}). */
    private static final String DREADNAUT_PATH =
        "dreadnaut";

    /** Whether to use Traces mode in dreadnaut. */
    private static final boolean USE_TRACES = true;

    /** Shared Dreadnaut interface for canonical labellings. */
    private static final DreadnautInterface DREADNAUT =
        new DreadnautInterface(DREADNAUT_PATH, USE_TRACES);

    // ----------------------------
    // Small helper types
    // ----------------------------

    /**
     * Parameters describing one polygon (triangle) on an axis.
     * <ul>
     *   <li>offset: signed distance from origin along the axis.</li>
     *   <li>scale: multiplicative factor for the triangle size.</li>
     *   <li>rotation: in-plane rotation (radians) in the plane perpendicular to the axis.</li>
     * </ul>
     */
    private static class PolygonParams {
        double offset;
        double scale;
        double rotation;

        PolygonParams(double offset, double scale, double rotation) {
            this.offset = offset;
            this.scale = scale;
            this.rotation = rotation;
        }
    }

    /**
     * One touching vertex pair:
     * <ul>
     *   <li>vertex {@code v1} on triangle {@code tri1} on axis 1</li>
     *   <li>touches vertex {@code v2} on triangle {@code tri2} on axis 2</li>
     * </ul>
     */
    private static class VertexPair {
        final int tri1; // triangle index on axis 1
        final int v1;   // vertex index 0..2 on axis 1 triangle
        final int tri2; // triangle index on axis 2
        final int v2;   // vertex index 0..2 on axis 2 triangle

        VertexPair(int tri1, int v1, int tri2, int v2) {
            this.tri1 = tri1;
            this.v1 = v1;
            this.tri2 = tri2;
            this.v2 = v2;
        }

        @Override
        public String toString() {
            return "(A1 T" + tri1 + ":v" + v1 + " ~ A2 T" + tri2 + ":v" + v2 + ")";
        }
    }

    /**
     * A discrete connection pattern: for each triangle index i, we store exactly one
     * pair (v1, v2) indicating which vertex on axis 1's triangle i touches which
     * vertex on axis 2's triangle i.
     */
    private static class ConnectionPattern {
        final List<VertexPair> pairs = new ArrayList<>();

        @Override
        public String toString() {
            return pairs.toString();
        }
    }

    /**
     * Result of an attempted 3D realization of a connection pattern.
     */
    public static class LayoutResult {
        private final ConnectionPattern pattern;
        public final Map<Integer, double[]> positions;
        public final double[] axis1;
        public final double[] axis2;
        public final double cost;
        public final boolean coverageSatisfied;
        /**
         * Touching vertex pairs, expressed directly as vertex IDs (id1 on axis 1, id2 on axis 2).
         */
        public final List<int[]> touchingPairs;

        LayoutResult(ConnectionPattern pattern,
                     Map<Integer, double[]> positions,
                     double[] axis1,
                     double[] axis2,
                     double cost,
                     boolean coverageSatisfied,
                     List<int[]> touchingPairs) {
            this.pattern = pattern;
            this.positions = positions;
            this.axis1 = axis1;
            this.axis2 = axis2;
            this.cost = cost;
            this.coverageSatisfied = coverageSatisfied;
            this.touchingPairs = touchingPairs;
        }

        public String patternString() {
            return pattern.toString();
        }
    }

    // ----------------------------
    // Entry point
    // ----------------------------

    public static void main(String[] args) {
        List<LayoutResult> results = generateLayouts(50, 1234L);

        System.out.println("BiaxeStudy: M = N = " + NUM_TRIANGLES_PER_AXIS
                + " triangles, axis angle = " + AXIS_ANGLE_DEG + " degrees.");
        System.out.println("Computed " + results.size()
                + " layouts (coverage " + (ENFORCE_COVERAGE ? "enforced" : "ignored") + ").");

        System.out.println("Top realized patterns (by final cost):");
        int topK = Math.min(10, results.size());
        for (int i = 0; i < topK; i++) {
            LayoutResult r = results.get(i);
            System.out.printf("  #%d: cost=%.6f, coverage=%s, pattern=%s%n",
                    i + 1, r.cost, r.coverageSatisfied, r.patternString());
        }
    }

    /**
     * Generate a list of layouts for the current configuration (triangles, symmetric axes),
     * optionally enforcing coverage and limiting the number of patterns to solve.
     */
    public static List<LayoutResult> generateLayouts(int maxToSolve, long seed) {
        int m = NUM_TRIANGLES_PER_AXIS;
        int n = NUM_TRIANGLES_PER_AXIS;
        if (m != n) {
            throw new IllegalStateException("This first attempt assumes the same number of triangles on both axes (M = N).");
        }

        List<ConnectionPattern> patterns = enumeratePatterns(m);

        Random random = new Random(seed);
        List<LayoutResult> results = new ArrayList<>();
        Set<String> canonicalArrangements = new HashSet<>();

        int solvedCount = 0;
        for (ConnectionPattern pattern : patterns) {
            // Build an arrangement graph for this pattern (triangles + touching edges).
            org.jgrapht.Graph<Integer, DefaultEdge> arrangement =
                buildArrangementGraph(pattern, m);

            // Reject graphs that are not fully connected.
            ConnectivityInspector<Integer, DefaultEdge> inspector =
                new ConnectivityInspector<>(arrangement);
            if (!inspector.isConnected()) {
                continue;
            }

            // Use dreadnaut to weed out isomorphic vertex-arrangement graphs.
            String canonical = DREADNAUT.getCanonicalLabeling(arrangement, false);
            if (!canonicalArrangements.add(canonical)) {
                // Isomorphic to a previously seen arrangement; skip.
                continue;
            }

            // Search harder: multiple random restarts for this pattern.
            LayoutResult best = null;
            for (int attempt = 0; attempt < TRIES_PER_PATTERN; attempt++) {
                LayoutResult res = solvePattern(pattern, random);
                if (ENFORCE_COVERAGE && !res.coverageSatisfied) {
                    continue;
                }
                if (best == null || res.cost < best.cost) {
                    best = res;
                }
            }

            if (best != null && (!ENFORCE_COVERAGE || best.coverageSatisfied)) {
                results.add(best);
            }

            solvedCount++;
            if (solvedCount >= maxToSolve) break;
        }

        // Sort by cost (lower is better).
        results.sort((a, b) -> Double.compare(a.cost, b.cost));
        return results;
    }

    // ----------------------------
    // Pattern enumeration
    // ----------------------------

    /**
     * Enumerate generalized connection patterns.
     *
     * <p>Model:
     * <ul>
     *   <li>For each triangle index {@code tri1} on axis 1 we choose exactly one
     *       touching vertex pair (so every axis‑1 triangle has at least one contact).</li>
     *   <li>For that pair we choose a triangle index {@code tri2} on axis 2 and a
     *       vertex index on that triangle.</li>
     *   <li>We require that every axis‑2 triangle appears in at least one pair, so both
     *       stacks are fully covered combinatorially.</li>
     * </ul>
     */
    private static List<ConnectionPattern> enumeratePatterns(int numTriangles) {
        List<ConnectionPattern> out = new ArrayList<>();
        int[] axis2Usage = new int[numTriangles];
        backtrackGeneral(0, numTriangles, new ConnectionPattern(), axis2Usage, out);
        return out;
    }

    private static void backtrackGeneral(int tri1,
                                         int numTriangles,
                                         ConnectionPattern current,
                                         int[] axis2Usage,
                                         List<ConnectionPattern> out) {
        if (tri1 >= numTriangles) {
            // Ensure every axis‑2 triangle has at least one incident pair.
            for (int t2 = 0; t2 < numTriangles; t2++) {
                if (axis2Usage[t2] == 0) {
                    return;
                }
            }
            ConnectionPattern copy = new ConnectionPattern();
            copy.pairs.addAll(current.pairs);
            out.add(copy);
            return;
        }

        for (int v1 = 0; v1 < VERTICES_PER_TRIANGLE; v1++) {
            for (int tri2 = 0; tri2 < numTriangles; tri2++) {
                for (int v2 = 0; v2 < VERTICES_PER_TRIANGLE; v2++) {
                    current.pairs.add(new VertexPair(tri1, v1, tri2, v2));
                    axis2Usage[tri2]++;
                    backtrackGeneral(tri1 + 1, numTriangles, current, axis2Usage, out);
                    axis2Usage[tri2]--;
                    current.pairs.remove(current.pairs.size() - 1);
                }
            }
        }
    }

    // ----------------------------
    // Geometry / layout
    // ----------------------------

    /**
     * Solve for a 3D realization of the given pattern using a simple local search
     * over per-triangle offsets / scales / rotations, with fixed symmetric axes.
     */
    private static LayoutResult solvePattern(ConnectionPattern pattern, Random random) {
        int m = NUM_TRIANGLES_PER_AXIS;

        // Build axes: symmetric with respect to the YZ-plane.
        double[] axis1 = new double[3];
        double[] axis2 = new double[3];
        buildSymmetricAxes(axis1, axis2, Math.toRadians(AXIS_ANGLE_DEG));

        // Local 2D coordinates for an equilateral triangle of radius 1.
        double[][] local2D = buildRegularPolygon2D(VERTICES_PER_TRIANGLE);

        // Initialize per-triangle parameters.
        PolygonParams[] params1 = new PolygonParams[m];
        PolygonParams[] params2 = new PolygonParams[m];
        initializePolygonParams(m, params1, params2, random);

        // Build a graph whose vertices correspond to triangle vertices and whose
        // edges represent "should coincide" constraints from the pattern.
        Graph g = buildConstraintGraph(pattern, m);

        // Also record the touching vertex ID pairs for downstream visualization.
        List<int[]> touchingPairs = buildTouchingVertexPairs(pattern, m);

        Map<Integer, double[]> positions = new HashMap<>();
        double bestCost = Double.POSITIVE_INFINITY;

        // Simple coordinate-descent style local search.
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            computeAllPositions(positions, axis1, axis2, params1, params2, local2D);
            double cost = computeConstraintCost(g, positions);

            if (cost < bestCost - IMPROVEMENT_THRESHOLD) {
                bestCost = cost;
            } else if (iter > 10) {
                // No significant improvement recently; stop early.
                break;
            }

            // Try small random tweaks on each polygon.
            for (int t = 0; t < m; t++) {
                tweakPolygon(params1[t], axis1, axis2, params1, params2, local2D, g, positions, random);
                tweakPolygon(params2[t], axis1, axis2, params1, params2, local2D, g, positions, random);
            }
        }

        // Final positions and cost.
        computeAllPositions(positions, axis1, axis2, params1, params2, local2D);
        double finalCost = computeConstraintCost(g, positions);

        boolean coverageOk = satisfiesCoverage(axis1, axis2, positions, pattern);

        return new LayoutResult(pattern, positions, axis1, axis2, finalCost, coverageOk, touchingPairs);
    }

    /**
     * Build axes symmetrical across the YZ-plane with a prescribed angle between them.
     *
     * <p>We take both axes in the XZ-plane, mirrored across x = 0:
     * axis1 = (sin(theta/2), 0, cos(theta/2))
     * axis2 = (-sin(theta/2), 0, cos(theta/2))
     * so that the angle between them is exactly {@code theta}.
     */
    private static void buildSymmetricAxes(double[] axis1, double[] axis2, double theta) {
        double half = theta / 2.0;
        double sx = Math.sin(half);
        double cz = Math.cos(half);
        axis1[0] = sx;
        axis1[1] = 0.0;
        axis1[2] = cz;
        axis2[0] = -sx;
        axis2[1] = 0.0;
        axis2[2] = cz;
        normalize(axis1);
        normalize(axis2);
    }

    private static double[][] buildRegularPolygon2D(int vertexCount) {
        double[][] coords = new double[vertexCount][2];
        for (int i = 0; i < vertexCount; i++) {
            double angle = 2.0 * Math.PI * i / vertexCount;
            coords[i][0] = Math.cos(angle);
            coords[i][1] = Math.sin(angle);
        }
        return coords;
    }

    private static void initializePolygonParams(int m,
                                                PolygonParams[] params1,
                                                PolygonParams[] params2,
                                                Random random) {
        // Simple evenly spaced offsets along each axis, centered near 0.
        double spacing = 2.0;
        double center = (m - 1) * 0.5;
        for (int i = 0; i < m; i++) {
            double offset = (i - center) * spacing;
            params1[i] = new PolygonParams(offset, 1.0, random.nextDouble() * 2.0 * Math.PI);
            params2[i] = new PolygonParams(offset, 1.0, random.nextDouble() * 2.0 * Math.PI);
        }
    }

    /**
     * Construct a constraint graph for the given pattern. Vertex IDs are:
     * <ul>
     *   <li>Axis 1: triangle t, vertex v -> id = 1 + t*3 + v</li>
     *   <li>Axis 2: triangle t, vertex v -> id = base2 + t*3 + v, where base2 = 1 + 3*M</li>
     * </ul>
     */
    private static Graph buildConstraintGraph(ConnectionPattern pattern, int m) {
        Graph g = new Graph();
        int base2 = 1 + m * VERTICES_PER_TRIANGLE;
        for (VertexPair vp : pattern.pairs) {
            int id1 = 1 + vp.tri1 * VERTICES_PER_TRIANGLE + vp.v1;
            int id2 = base2 + vp.tri2 * VERTICES_PER_TRIANGLE + vp.v2;
            g.addEdge(id1, id2);
        }
        return g;
    }

    /**
     * Build a JGraphT arrangement graph for a pattern. Vertices are all triangle
     * vertices on both axes; edges include:
     * <ul>
     *   <li>Cycle edges for each triangle (within an axis).</li>
     *   <li>Touching edges between corresponding vertices on different axes.</li>
     * </ul>
     */
    private static org.jgrapht.Graph<Integer, DefaultEdge> buildArrangementGraph(ConnectionPattern pattern, int m) {
        org.jgrapht.Graph<Integer, DefaultEdge> g =
            new SimpleGraph<>(DefaultEdge.class);

        int totalVertices = 2 * m * VERTICES_PER_TRIANGLE;
        for (int v = 1; v <= totalVertices; v++) {
            g.addVertex(v);
        }

        int base2 = 1 + m * VERTICES_PER_TRIANGLE;

        // Add internal triangle edges for axis 1.
        for (int t = 0; t < m; t++) {
            int base = 1 + t * VERTICES_PER_TRIANGLE;
            for (int i = 0; i < VERTICES_PER_TRIANGLE; i++) {
                int v1 = base + i;
                int v2 = base + ((i + 1) % VERTICES_PER_TRIANGLE);
                g.addEdge(v1, v2);
            }
        }

        // Add internal triangle edges for axis 2.
        for (int t = 0; t < m; t++) {
            int base = base2 + t * VERTICES_PER_TRIANGLE;
            for (int i = 0; i < VERTICES_PER_TRIANGLE; i++) {
                int v1 = base + i;
                int v2 = base + ((i + 1) % VERTICES_PER_TRIANGLE);
                g.addEdge(v1, v2);
            }
        }

        // Add touching edges between axes.
        for (VertexPair vp : pattern.pairs) {
            int id1 = 1 + vp.tri1 * VERTICES_PER_TRIANGLE + vp.v1;
            int id2 = base2 + vp.tri2 * VERTICES_PER_TRIANGLE + vp.v2;
            if (!g.containsEdge(id1, id2)) {
                g.addEdge(id1, id2);
            }
        }

        return g;
    }

    /**
     * Build the list of touching vertex ID pairs corresponding to a pattern.
     */
    private static List<int[]> buildTouchingVertexPairs(ConnectionPattern pattern, int m) {
        List<int[]> pairs = new ArrayList<>();
        int base2 = 1 + m * VERTICES_PER_TRIANGLE;
        for (VertexPair vp : pattern.pairs) {
            int id1 = 1 + vp.tri1 * VERTICES_PER_TRIANGLE + vp.v1;
            int id2 = base2 + vp.tri2 * VERTICES_PER_TRIANGLE + vp.v2;
            pairs.add(new int[] { id1, id2 });
        }
        return pairs;
    }

    /**
     * Compute positions for all triangle vertices on both axes.
     */
    private static void computeAllPositions(Map<Integer, double[]> positions,
                                            double[] axis1,
                                            double[] axis2,
                                            PolygonParams[] params1,
                                            PolygonParams[] params2,
                                            double[][] local2D) {
        positions.clear();
        int m = params1.length;
        int base2 = 1 + m * VERTICES_PER_TRIANGLE;
        for (int t = 0; t < m; t++) {
            for (int v = 0; v < VERTICES_PER_TRIANGLE; v++) {
                int id1 = 1 + t * VERTICES_PER_TRIANGLE + v;
                int id2 = base2 + t * VERTICES_PER_TRIANGLE + v;
                positions.put(id1, computePolygonVertexPositionOnAxis(axis1, params1[t], v, GLOBAL_SCALE, local2D));
                positions.put(id2, computePolygonVertexPositionOnAxis(axis2, params2[t], v, GLOBAL_SCALE, local2D));
            }
        }
    }

    /**
     * Compute the 3D position of vertex vIndex of a polygon on the given axis.
     */
    private static double[] computePolygonVertexPositionOnAxis(double[] axis,
                                                               PolygonParams pp,
                                                               int vIndex,
                                                               double globalScale,
                                                               double[][] local2D) {
        double[] center = new double[] {
            axis[0] * pp.offset,
            axis[1] * pp.offset,
            axis[2] * pp.offset
        };

        double lx = local2D[vIndex][0];
        double ly = local2D[vIndex][1];

        // Apply scale and rotation in the local 2D plane.
        lx *= (pp.scale * globalScale);
        ly *= (pp.scale * globalScale);

        double cosR = Math.cos(pp.rotation);
        double sinR = Math.sin(pp.rotation);
        double rx = lx * cosR - ly * sinR;
        double ry = lx * sinR + ly * cosR;

        // Build an orthonormal basis (perp1, perp2) for the plane perpendicular to axis.
        double[] perp1 = findPerpVector(axis);
        normalize(perp1);
        double[] perp2 = cross(axis, perp1);
        normalize(perp2);

        return new double[] {
            center[0] + rx * perp1[0] + ry * perp2[0],
            center[1] + rx * perp1[1] + ry * perp2[1],
            center[2] + rx * perp1[2] + ry * perp2[2]
        };
    }

    private static double computeConstraintCost(Graph g, Map<Integer, double[]> positions) {
        double cost = 0.0;
        for (Graph.Edge e : g.getEdges()) {
            double[] p1 = positions.get(e.u);
            double[] p2 = positions.get(e.v);
            if (p1 == null || p2 == null) continue;
            double dx = p1[0] - p2[0];
            double dy = p1[1] - p2[1];
            double dz = p1[2] - p2[2];
            cost += dx * dx + dy * dy + dz * dz;
        }
        return cost;
    }

    /**
     * Try small random tweaks to the given polygon's parameters and accept only
     * changes that improve the constraint cost. This is a very simple coordinate
     * descent scheme and is not intended to be highly optimized.
     */
    private static void tweakPolygon(PolygonParams pp,
                                     double[] axis1,
                                     double[] axis2,
                                     PolygonParams[] params1,
                                     PolygonParams[] params2,
                                     double[][] local2D,
                                     Graph g,
                                     Map<Integer, double[]> positions,
                                     Random random) {
        // Baseline
        computeAllPositions(positions, axis1, axis2, params1, params2, local2D);
        double baseline = computeConstraintCost(g, positions);

        // Tweak offset
        double originalOffset = pp.offset;
        pp.offset += (random.nextDouble() - 0.5) * STEP_SIZE * 2.0;
        computeAllPositions(positions, axis1, axis2, params1, params2, local2D);
        double newCost = computeConstraintCost(g, positions);
        if (newCost < baseline) {
            baseline = newCost;
        } else {
            pp.offset = originalOffset;
        }

        // Tweak scale
        double originalScale = pp.scale;
        pp.scale += (random.nextDouble() - 0.5) * STEP_SIZE;
        if (pp.scale < 0.1) pp.scale = 0.1;
        computeAllPositions(positions, axis1, axis2, params1, params2, local2D);
        newCost = computeConstraintCost(g, positions);
        if (newCost < baseline) {
            baseline = newCost;
        } else {
            pp.scale = originalScale;
        }

        // Tweak rotation
        double originalRotation = pp.rotation;
        pp.rotation += (random.nextDouble() - 0.5) * STEP_SIZE * 4.0;
        computeAllPositions(positions, axis1, axis2, params1, params2, local2D);
        newCost = computeConstraintCost(g, positions);
        if (newCost >= baseline) {
            pp.rotation = originalRotation;
        }
    }

    // ----------------------------
    // Small vector helpers
    // ----------------------------

    private static void normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len == 0.0) return;
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    /**
     * Return a vector perpendicular to v. This mirrors the logic used in the
     * axis-constrained layout algorithms.
     */
    private static double[] findPerpVector(double[] v) {
        double[] z = new double[] {0, 0, 1};
        double[] x = new double[] {1, 0, 0};
        double dotZ = Math.abs(v[0] * z[0] + v[1] * z[1] + v[2] * z[2]);
        if (dotZ < 0.9) {
            return cross(v, z);
        } else {
            return cross(v, x);
        }
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /**
     * Check the coverage condition along both axes.
     */
    private static boolean satisfiesCoverage(double[] axis1,
                                             double[] axis2,
                                             Map<Integer, double[]> positions,
                                             ConnectionPattern pattern) {
        int m = NUM_TRIANGLES_PER_AXIS;
        int base2 = 1 + m * VERTICES_PER_TRIANGLE;

        // Track which vertices on each axis are touching.
        boolean[] touchedAxis1 = new boolean[m * VERTICES_PER_TRIANGLE];
        boolean[] touchedAxis2 = new boolean[m * VERTICES_PER_TRIANGLE];
        for (VertexPair vp : pattern.pairs) {
            int id1 = 1 + vp.tri1 * VERTICES_PER_TRIANGLE + vp.v1;
            int id2 = base2 + vp.tri2 * VERTICES_PER_TRIANGLE + vp.v2;
            touchedAxis1[id1 - 1] = true;               // axis 1 ids start at 1
            touchedAxis2[id2 - base2] = true;           // axis 2 ids start at base2
        }

        boolean c1 = coverageForAxis(axis1,
                                     1, base2,
                                     base2, base2 + m * VERTICES_PER_TRIANGLE,
                                     touchedAxis2,
                                     positions);
        boolean c2 = coverageForAxis(axis2,
                                     base2, base2 + m * VERTICES_PER_TRIANGLE,
                                     1, base2,
                                     touchedAxis1,
                                     positions);
        return c1 && c2;
    }

    /**
     * Enforce the "coverage" condition for a single axis:
     *
     * <ul>
     *   <li>Let [Jmin,Jmax] be the range of projections (onto this axis) of all vertices
     *       belonging to the "J-axis" polygons (id range [jStart, jEnd)).</li>
     *   <li>Any vertex whose projection lies in [Jmin,Jmax] and that belongs to the
     *       "K-axis" (id range [kStart,kEnd)) must be one of the touching vertices
     *       (as indicated by {@code touchedK}).</li>
     *   <li>All non-touching K-axis vertices must project strictly outside [Jmin,Jmax],
     *       and they must lie on the same side of the interval (all &lt; Jmin or all &gt; Jmax),
     *       i.e. they cannot straddle the J-range.</li>
     * </ul>
     */
    private static boolean coverageForAxis(double[] axis,
                                           int jStart, int jEnd,
                                           int kStart, int kEnd,
                                           boolean[] touchedK,
                                           Map<Integer, double[]> positions) {
        final double EPS = 1e-6;

        double jMin = Double.POSITIVE_INFINITY;
        double jMax = Double.NEGATIVE_INFINITY;

        // Compute [Jmin, Jmax] from J-axis vertices.
        for (int vid = jStart; vid < jEnd; vid++) {
            double[] p = positions.get(vid);
            if (p == null) continue;
            double t = dot(p, axis);
            if (t < jMin) jMin = t;
            if (t > jMax) jMax = t;
        }

        if (jMin == Double.POSITIVE_INFINITY) {
            // No vertices for this axis; treat as trivially satisfied.
            return true;
        }

        // Collect projections of non-touching K-axis vertices that lie outside [Jmin, Jmax].
        boolean anyBelow = false;
        boolean anyAbove = false;

        for (int vid = kStart; vid < kEnd; vid++) {
            double[] p = positions.get(vid);
            if (p == null) continue;
            double t = dot(p, axis);
            int kIndex = vid - kStart;
            if (kIndex < 0 || kIndex >= touchedK.length) {
                // Should not happen, but be defensive.
                continue;
            }

            boolean isTouched = touchedK[kIndex];

            // Inside the J-range: must be touching.
            if (t >= jMin - EPS && t <= jMax + EPS) {
                if (!isTouched) {
                    return false;
                }
            } else {
                // Outside the J-range: for non-touching vertices we later require
                // that all lie on the same side.
                if (!isTouched) {
                    if (t < jMin - EPS) {
                        anyBelow = true;
                    } else if (t > jMax + EPS) {
                        anyAbove = true;
                    }
                }
            }
        }

        // If we have non-touching K vertices both below and above the J-range,
        // then the K-axis stack straddles the J-range, which violates coverage.
        if (anyBelow && anyAbove) {
            return false;
        }

        return true;
    }

    // ----------------------------
    // JavaFX viewer
    // ----------------------------

    /**
     * A minimal JavaFX viewer for biaxial layouts. It computes a small batch of
     * {@link LayoutResult}s using {@link #generateLayouts(int, long)} and lets the
     * user paginate through them while viewing the axis-1 and axis-2 triangle stacks
     * and the touching vertex connections.
     *
     * <p>To run: use {@code io.chandler.gap.graph.BiaxeStudy$FXApp} as the JavaFX
     * application main class.
     */
    public static class FXApp extends Application {

        private static final double NODE_RADIUS = 6.0;

        private Pane pane;
        private Label indexLabel;
        private Label costLabel;
        private Label coverageLabel;

        private List<LayoutResult> layouts;
        private int currentIndex = 0;

        private double zoomScale = 1.0;

        // Trackball rotation state (accumulated 3x3 rotation matrix) and last mouse position
        private double[][] trackballRotation = new double[][] { {1,0,0}, {0,1,0}, {0,0,1} };
        private Double lastMouseX = null;
        private Double lastMouseY = null;

        @Override
        public void start(Stage stage) {
            // Compute some layouts up-front.
            layouts = generateLayouts(200, 1234L);
            if (layouts.isEmpty()) {
                layouts = new ArrayList<>();
            }

            pane = new Pane();
            pane.setStyle("-fx-background-color: white;");

            // Create a trackball control for full 3D rotation.
            pane.setOnMousePressed(e -> {
                if (!e.isSecondaryButtonDown()) {
                    lastMouseX = null;
                    lastMouseY = null;
                    return;
                } else {
                    lastMouseX = e.getSceneX();
                    lastMouseY = e.getSceneY();
                    e.consume();
                }
            });
            pane.setOnMouseDragged(e -> {
                if (lastMouseX == null || lastMouseY == null) {
                    return;
                }
                double deltaX = e.getSceneX() - lastMouseX;
                double deltaY = e.getSceneY() - lastMouseY;
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
                deltaX = -deltaX;

                // Apply yaw (around Y) for horizontal drag, and pitch (around X) for vertical drag
                double sensitivity = 0.01; // radians per pixel
                double[][] Ry = rotationFromAxisAngle(0, 1, 0, deltaX * sensitivity);
                double[][] Rx = rotationFromAxisAngle(1, 0, 0, deltaY * sensitivity);
                // New rotation applied in world axes order: first yaw then pitch
                trackballRotation = multiply3(Rx, multiply3(Ry, trackballRotation));

                renderCurrent();
            });

            attachZoomHandler();

            Button prevButton = new Button("<");
            Button nextButton = new Button(">");
            indexLabel = new Label();
            costLabel = new Label();
            coverageLabel = new Label();

            prevButton.setOnAction(e -> {
                if (layouts.isEmpty()) return;
                currentIndex = (currentIndex - 1 + layouts.size()) % layouts.size();
                renderCurrent();
            });
            nextButton.setOnAction(e -> {
                if (layouts.isEmpty()) return;
                currentIndex = (currentIndex + 1) % layouts.size();
                renderCurrent();
            });

            HBox controls = new HBox(10, prevButton, nextButton, indexLabel, costLabel, coverageLabel);
            controls.setPadding(new Insets(8));

            BorderPane root = new BorderPane();
            root.setTop(controls);
            root.setCenter(pane);

            Scene scene = new Scene(root, 800, 600);
            stage.setTitle("BiaxeStudy Viewer");
            stage.setScene(scene);
            stage.show();

            renderCurrent();
        }

        private void attachZoomHandler() {
            pane.addEventFilter(ScrollEvent.SCROLL, evt -> {
                double factor = evt.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
                zoomScale = clamp(zoomScale * factor, 0.4, 3.0);
                //pane.setScaleX(zoomScale);
                //pane.setScaleY(zoomScale);
                evt.consume();
            });
        }

        private void renderCurrent() {
            pane.getChildren().clear();
            if (layouts == null || layouts.isEmpty()) {
                indexLabel.setText("No layouts");
                costLabel.setText("");
                coverageLabel.setText("");
                return;
            }

            LayoutResult lr = layouts.get(currentIndex);
            indexLabel.setText("Layout " + (currentIndex + 1) + " / " + layouts.size());
            costLabel.setText(String.format("Cost: %.5f", lr.cost));
            coverageLabel.setText("Coverage: " + (lr.coverageSatisfied ? "OK" : "FAIL"));

            Map<Integer, double[]> positions = lr.positions;
            if (positions == null || positions.isEmpty()) return;

            // Make a fresh copy of positions and apply the trackball rotation matrix.
            Map<Integer, double[]> rotatedPositions = new HashMap<>();
            for (Map.Entry<Integer, double[]> entry : positions.entrySet()) {
                double[] pos = entry.getValue();
                double[] copy = new double[pos.length];
                System.arraycopy(pos, 0, copy, 0, pos.length);
                
                // Apply trackball rotation to 3D positions
                if (copy.length == 3) {
                    double x = pos[0], y = pos[1], z = pos[2];
                    double rx = trackballRotation[0][0]*x + trackballRotation[0][1]*y + trackballRotation[0][2]*z;
                    double ry = trackballRotation[1][0]*x + trackballRotation[1][1]*y + trackballRotation[1][2]*z;
                    double rz = trackballRotation[2][0]*x + trackballRotation[2][1]*y + trackballRotation[2][2]*z;
                    copy[0] = rx;
                    copy[1] = ry;
                    copy[2] = rz;
                }
                rotatedPositions.put(entry.getKey(), copy);
            }
            positions = rotatedPositions;

            // Compute bounding box in the (x,y)-plane.
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (double[] p : positions.values()) {
                double x = p[0];
                double y = p[1];
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
            if (minX == Double.POSITIVE_INFINITY) return;

            double width = pane.getWidth() > 0 ? pane.getWidth() : 800;
            double height = pane.getHeight() > 0 ? pane.getHeight() : 600;

            double rangeX = maxX - minX;
            double rangeY = maxY - minY;
            if (rangeX < 1e-9) rangeX = 1.0;
            if (rangeY < 1e-9) rangeY = 1.0;

            double scale = 0.8 * Math.min(width / rangeX, height / rangeY);

            // Helper to project 3D->2D (x,y) and center in pane.
            Map<Integer, double[]> projected = new HashMap<>();
            for (Map.Entry<Integer, double[]> entry : positions.entrySet()) {
                int id = entry.getKey();
                double[] p = entry.getValue();
                double x = (p[0] - minX - rangeX / 2.0) * scale + width / 2.0;
                double y = (p[1] - minY - rangeY / 2.0) * scale + height / 2.0;
                projected.put(id, new double[]{x, y});
            }

            int m = NUM_TRIANGLES_PER_AXIS;
            int base2 = 1 + m * VERTICES_PER_TRIANGLE;

            // Draw triangle edges: axis 1 in blue, axis 2 in red.
            Color axis1Color = Color.DODGERBLUE;
            Color axis2Color = Color.CRIMSON;

            for (int t = 0; t < m; t++) {
                int[] ids1 = new int[]{
                        1 + t * VERTICES_PER_TRIANGLE + 0,
                        1 + t * VERTICES_PER_TRIANGLE + 1,
                        1 + t * VERTICES_PER_TRIANGLE + 2
                };
                drawPolygonEdges(ids1, projected, axis1Color);

                int[] ids2 = new int[]{
                        base2 + t * VERTICES_PER_TRIANGLE + 0,
                        base2 + t * VERTICES_PER_TRIANGLE + 1,
                        base2 + t * VERTICES_PER_TRIANGLE + 2
                };
                drawPolygonEdges(ids2, projected, axis2Color);
            }

            // Draw touching pairs as thick green lines.
            if (lr.touchingPairs != null) {
                for (int[] pair : lr.touchingPairs) {
                    double[] p1 = projected.get(pair[0]);
                    double[] p2 = projected.get(pair[1]);
                    if (p1 == null || p2 == null) continue;
                    Line line = new Line(p1[0], p1[1], p2[0], p2[1]);
                    line.setStroke(Color.FORESTGREEN);
                    line.setStrokeWidth(3.0);
                    pane.getChildren().add(line);
                }
            }

            // Draw vertices as small circles (axis1 blue, axis2 red).
            for (Map.Entry<Integer, double[]> entry : projected.entrySet()) {
                int id = entry.getKey();
                double[] p = entry.getValue();
                Circle c = new Circle(p[0], p[1], NODE_RADIUS);
                if (id < base2) {
                    c.setFill(axis1Color);
                } else {
                    c.setFill(axis2Color);
                }
                c.setStroke(Color.BLACK);
                c.setStrokeWidth(0.8);
                pane.getChildren().add(c);
            }

            // Optionally label vertices with their ID.
            for (Map.Entry<Integer, double[]> entry : projected.entrySet()) {
                int id = entry.getKey();
                double[] p = entry.getValue();
                Text txt = new Text(p[0] + NODE_RADIUS, p[1] - NODE_RADIUS, String.valueOf(id));
                txt.setFill(Color.BLACK);
                pane.getChildren().add(txt);
            }
        }

        private void drawPolygonEdges(int[] ids, Map<Integer, double[]> projected, Color color) {
            for (int i = 0; i < ids.length; i++) {
                int id1 = ids[i];
                int id2 = ids[(i + 1) % ids.length];
                double[] p1 = projected.get(id1);
                double[] p2 = projected.get(id2);
                if (p1 == null || p2 == null) continue;
                Line edge = new Line(p1[0], p1[1], p2[0], p2[1]);
                edge.setStroke(color.deriveColor(0, 1, 1, 0.6));
                edge.setStrokeWidth(1.5);
                pane.getChildren().add(edge);
            }
        }

        private double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }

        private double[][] rotationFromAxisAngle(double ax, double ay, double az, double angle) {
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            double t = 1.0 - c;
            double[][] R = new double[3][3];
            R[0][0] = t*ax*ax + c;     R[0][1] = t*ax*ay - s*az; R[0][2] = t*ax*az + s*ay;
            R[1][0] = t*ay*ax + s*az;  R[1][1] = t*ay*ay + c;    R[1][2] = t*ay*az - s*ax;
            R[2][0] = t*az*ax - s*ay;  R[2][1] = t*az*ay + s*ax; R[2][2] = t*az*az + c;
            return R;
        }

        private double[][] multiply3(double[][] A, double[][] B) {
            double[][] C = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    C[i][j] = A[i][0]*B[0][j] + A[i][1]*B[1][j] + A[i][2]*B[2][j];
                }
            }
            return C;
        }

        public static void main(String[] args) {
            launch(args);
        }
    }
}


