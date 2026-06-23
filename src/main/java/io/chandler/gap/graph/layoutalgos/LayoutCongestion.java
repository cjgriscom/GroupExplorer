package io.chandler.gap.graph.layoutalgos;

import java.util.ArrayList;
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
        int n = graph.vertexSet().size();
        double optimal = 1.0 / Math.sqrt(n);

        List<int[]> edges = new ArrayList<>();
        Set<Integer> neighbors = new HashSet<>();
        for (DefaultEdge edge : graph.edgeSet()) {
            int u = graph.getEdgeSource(edge);
            int v = graph.getEdgeTarget(edge);
            edges.add(new int[]{u, v});
        }

        int crossings = countCrossings(edges, positions);
        double maxCrossings = edges.size() * (edges.size() - 1) / 2.0;
        double crossingScore = maxCrossings > 0 ? crossings / maxCrossings : 0.0;

        double crowding = 0.0;
        int crowdingPairs = 0;
        List<Integer> verts = new ArrayList<>(graph.vertexSet());
        for (int i = 0; i < verts.size(); i++) {
            int u = verts.get(i);
            double[] pu = positions.get(u);
            if (pu == null) continue;
            neighbors.clear();
            for (DefaultEdge e : graph.edgesOf(u)) {
                neighbors.add(graph.getEdgeSource(e) == u ? graph.getEdgeTarget(e) : graph.getEdgeSource(e));
            }
            for (int j = i + 1; j < verts.size(); j++) {
                int v = verts.get(j);
                if (neighbors.contains(v)) continue;
                double[] pv = positions.get(v);
                if (pv == null) continue;
                double dist = distance(pu, pv);
                if (dist < optimal) {
                    double ratio = optimal / Math.max(dist, 1e-9);
                    crowding += (ratio - 1.0) * (ratio - 1.0);
                    crowdingPairs++;
                }
            }
        }
        double crowdingScore = crowdingPairs > 0 ? crowding / crowdingPairs : 0.0;

        return crossingScore + crowdingScore;
    }

    private static int countCrossings(List<int[]> edges, Map<Integer, double[]> positions) {
        int crossings = 0;
        for (int i = 0; i < edges.size(); i++) {
            int[] e1 = edges.get(i);
            double[] a = positions.get(e1[0]);
            double[] b = positions.get(e1[1]);
            if (a == null || b == null) continue;
            for (int j = i + 1; j < edges.size(); j++) {
                int[] e2 = edges.get(j);
                if (sharesVertex(e1, e2)) continue;
                double[] c = positions.get(e2[0]);
                double[] d = positions.get(e2[1]);
                if (c == null || d == null) continue;
                if (segmentsIntersect(a, b, c, d)) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    private static boolean sharesVertex(int[] e1, int[] e2) {
        return e1[0] == e2[0] || e1[0] == e2[1] || e1[1] == e2[0] || e1[1] == e2[1];
    }

    private static double distance(double[] p, double[] q) {
        double sum = 0.0;
        int dim = Math.min(p.length, q.length);
        for (int i = 0; i < dim; i++) {
            double d = p[i] - q[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    private static boolean segmentsIntersect(double[] p1, double[] p2, double[] p3, double[] p4) {
        double o1 = orientation(p1, p2, p3);
        double o2 = orientation(p1, p2, p4);
        double o3 = orientation(p3, p4, p1);
        double o4 = orientation(p3, p4, p2);
        if (o1 * o2 < 0 && o3 * o4 < 0) return true;
        if (Math.abs(o1) < 1e-12 && onSegment(p1, p3, p2)) return true;
        if (Math.abs(o2) < 1e-12 && onSegment(p1, p4, p2)) return true;
        if (Math.abs(o3) < 1e-12 && onSegment(p3, p1, p4)) return true;
        if (Math.abs(o4) < 1e-12 && onSegment(p3, p2, p4)) return true;
        return false;
    }

    private static double orientation(double[] a, double[] b, double[] c) {
        return (b[1] - a[1]) * (c[0] - b[0]) - (b[0] - a[0]) * (c[1] - b[1]);
    }

    private static boolean onSegment(double[] a, double[] b, double[] c) {
        return Math.min(a[0], c[0]) <= b[0] && b[0] <= Math.max(a[0], c[0])
            && Math.min(a[1], c[1]) <= b[1] && b[1] <= Math.max(a[1], c[1]);
    }
}
