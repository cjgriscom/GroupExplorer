package io.chandler.gap.graph.layoutalgos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Qualitative congestion score for a graph drawing: higher means more crowded
 * (edge crossings and vertices packed too tightly relative to the spring ideal spacing).
 */
public final class LayoutCongestion {

    private LayoutCongestion() {}

    public static double compute(Graph<Integer, DefaultEdge> graph, Map<Integer, double[]> positions) {
        if (graph == null || positions == null || graph.vertexSet().isEmpty()) {
            return 0.0;
        }
        List<Integer> vertexIds = new ArrayList<>(graph.vertexSet());
        double[][] pos = new double[vertexIds.size()][];
        for (int i = 0; i < vertexIds.size(); i++) {
            pos[i] = positions.get(vertexIds.get(i));
        }
        return compute(graph, vertexIds, pos);
    }

    public static double compute(Graph<Integer, DefaultEdge> graph, List<Integer> vertexIds,
            double[][] positions) {
        if (graph == null || vertexIds == null || positions == null || graph.vertexSet().isEmpty()) {
            return 0.0;
        }
        int n = vertexIds.size();
        double optimal = 1.0 / Math.sqrt(n);
        double optimalSq = optimal * optimal;

        Map<Integer, Integer> idToIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            idToIndex.put(vertexIds.get(i), i);
        }

        int edgeCount = graph.edgeSet().size();
        int[] eu = new int[edgeCount];
        int[] ev = new int[edgeCount];
        int validEdges = 0;
        for (DefaultEdge edge : graph.edgeSet()) {
            Integer ui = idToIndex.get(graph.getEdgeSource(edge));
            Integer vi = idToIndex.get(graph.getEdgeTarget(edge));
            if (ui == null || vi == null) {
                continue;
            }
            double[] pu = positions[ui];
            double[] pv = positions[vi];
            if (pu == null || pv == null) {
                continue;
            }
            eu[validEdges] = ui;
            ev[validEdges] = vi;
            validEdges++;
        }

        int crossings = countCrossings(eu, ev, validEdges, positions);
        double maxCrossings = validEdges * (validEdges - 1) / 2.0;
        double crossingScore = maxCrossings > 0 ? crossings / maxCrossings : 0.0;

        double crowding = 0.0;
        int crowdingPairs = 0;
        Set<Integer> neighborIndices = new HashSet<>();
        for (int i = 0; i < n; i++) {
            double[] pu = positions[i];
            if (pu == null) {
                continue;
            }
            neighborIndices.clear();
            int u = vertexIds.get(i);
            for (DefaultEdge e : graph.edgesOf(u)) {
                Integer neighbor = graph.getEdgeSource(e) == u
                        ? graph.getEdgeTarget(e)
                        : graph.getEdgeSource(e);
                Integer neighborIndex = idToIndex.get(neighbor);
                if (neighborIndex != null) {
                    neighborIndices.add(neighborIndex);
                }
            }
            for (int j = i + 1; j < n; j++) {
                if (neighborIndices.contains(j)) {
                    continue;
                }
                double[] pv = positions[j];
                if (pv == null) {
                    continue;
                }
                double distSq = distanceSquared(pu, pv);
                if (distSq < optimalSq) {
                    double dist = Math.sqrt(distSq);
                    double ratio = optimal / Math.max(dist, 1e-9);
                    crowding += (ratio - 1.0) * (ratio - 1.0);
                    crowdingPairs++;
                }
            }
        }
        double crowdingScore = crowdingPairs > 0 ? crowding / crowdingPairs : 0.0;

        return crossingScore + crowdingScore;
    }

    private static int countCrossings(int[] eu, int[] ev, int edgeCount, double[][] positions) {
        double[] ax = new double[edgeCount];
        double[] ay = new double[edgeCount];
        double[] bx = new double[edgeCount];
        double[] by = new double[edgeCount];
        double[] minX = new double[edgeCount];
        double[] maxX = new double[edgeCount];
        double[] minY = new double[edgeCount];
        double[] maxY = new double[edgeCount];

        for (int i = 0; i < edgeCount; i++) {
            double[] a = positions[eu[i]];
            double[] b = positions[ev[i]];
            double ax0 = a[0];
            double ay0 = a[1];
            double bx0 = b[0];
            double by0 = b[1];
            ax[i] = ax0;
            ay[i] = ay0;
            bx[i] = bx0;
            by[i] = by0;
            minX[i] = Math.min(ax0, bx0);
            maxX[i] = Math.max(ax0, bx0);
            minY[i] = Math.min(ay0, by0);
            maxY[i] = Math.max(ay0, by0);
        }

        int crossings = 0;
        for (int i = 0; i < edgeCount; i++) {
            for (int j = i + 1; j < edgeCount; j++) {
                if (eu[i] == eu[j] || eu[i] == ev[j] || ev[i] == eu[j] || ev[i] == ev[j]) {
                    continue;
                }
                if (maxX[i] < minX[j] || maxX[j] < minX[i]
                        || maxY[i] < minY[j] || maxY[j] < minY[i]) {
                    continue;
                }
                if (segmentsIntersect(ax[i], ay[i], bx[i], by[i], ax[j], ay[j], bx[j], by[j])) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    private static double distanceSquared(double[] p, double[] q) {
        double sum = 0.0;
        int dim = Math.min(p.length, q.length);
        for (int i = 0; i < dim; i++) {
            double d = p[i] - q[i];
            sum += d * d;
        }
        return sum;
    }

    private static boolean segmentsIntersect(
            double ax, double ay, double bx, double by,
            double cx, double cy, double dx, double dy) {
        double o1 = orientation(ax, ay, bx, by, cx, cy);
        double o2 = orientation(ax, ay, bx, by, dx, dy);
        if (o1 * o2 < 0) {
            double o3 = orientation(cx, cy, dx, dy, ax, ay);
            double o4 = orientation(cx, cy, dx, dy, bx, by);
            if (o3 * o4 < 0) {
                return true;
            }
        }
        if (Math.abs(o1) < 1e-12 && onSegment(ax, ay, cx, cy, bx, by)) {
            return true;
        }
        if (Math.abs(o2) < 1e-12 && onSegment(ax, ay, dx, dy, bx, by)) {
            return true;
        }
        double o3 = orientation(cx, cy, dx, dy, ax, ay);
        if (Math.abs(o3) < 1e-12 && onSegment(cx, cy, ax, ay, dx, dy)) {
            return true;
        }
        double o4 = orientation(cx, cy, dx, dy, bx, by);
        if (Math.abs(o4) < 1e-12 && onSegment(cx, cy, bx, by, dx, dy)) {
            return true;
        }
        return false;
    }

    private static double orientation(double ax, double ay, double bx, double by, double cx, double cy) {
        return (by - ay) * (cx - bx) - (bx - ax) * (cy - by);
    }

    private static boolean onSegment(double ax, double ay, double bx, double by, double cx, double cy) {
        return Math.min(ax, bx) <= cx && cx <= Math.max(ax, bx)
                && Math.min(ay, by) <= cy && cy <= Math.max(ay, by);
    }
}
