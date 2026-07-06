package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Scores same-color edge pairs as candidate crossing junctions, based on the
 * continuous 2D projection. A good junction hypothesis has:
 *  - midpoints already nearly coincident,
 *  - both edges short (junction edges in real solutions are sqrt2 or length-2),
 *  - directions clearly crossing (near-perpendicular preferred).
 *
 * These are advisory only: they bias the snap phase and provide CP-SAT hints,
 * but the LNS is free to form or dissolve junctions.
 */
public final class JunctionHypotheses {

    public static final class Hypothesis {
        public final ColoredEdge e1, e2;
        public final double score;   // lower = more likely junction
        public final double[] center; // continuous midpoint average
        Hypothesis(ColoredEdge e1, ColoredEdge e2, double score, double[] center) {
            this.e1 = e1; this.e2 = e2; this.score = score; this.center = center;
        }
        @Override public String toString() {
            return String.format("junction? %s x %s score=%.2f", e1, e2, score);
        }
    }

    private JunctionHypotheses() {}

    public static List<Hypothesis> rank(ColoredGraph g, Map<Integer, double[]> pos2d, FlattenParams params) {
        List<ColoredEdge> edges = g.edges();
        List<Hypothesis> out = new ArrayList<>();
        int n = edges.size();
        for (int i = 0; i < n; i++) {
            ColoredEdge ei = edges.get(i);
            if (dist(pos2d.get(ei.u), pos2d.get(ei.v)) > 3.0) continue;
            for (int j = i + 1; j < n; j++) {
                ColoredEdge ej = edges.get(j);
                if (ei.color != ej.color || ei.sharesVertex(ej)) continue;

                double[] a = pos2d.get(ei.u), b = pos2d.get(ei.v);
                double[] c = pos2d.get(ej.u), d = pos2d.get(ej.v);

                double mx1 = (a[0] + b[0]) / 2, my1 = (a[1] + b[1]) / 2;
                double mx2 = (c[0] + d[0]) / 2, my2 = (c[1] + d[1]) / 2;
                double mdist = Math.hypot(mx1 - mx2, my1 - my2);
                if (mdist > 1.0) continue;

                double len1 = dist(a, b), len2 = dist(c, d);
                if (len2 > 3.0) continue;

                // Angle between edge directions; junction crossings want ~90 degrees
                double ang = angleBetween(b[0] - a[0], b[1] - a[1], d[0] - c[0], d[1] - c[1]);
                double perp = Math.abs(Math.PI / 2 - ang); // 0 when perpendicular

                double score = 4.0 * mdist
                        + 1.0 * (Math.max(0, len1 - 1.5) + Math.max(0, len2 - 1.5))
                        + 1.5 * perp;
                out.add(new Hypothesis(ei, ej, score,
                        new double[]{(mx1 + mx2) / 2, (my1 + my2) / 2}));
            }
        }
        out.sort(Comparator.comparingDouble(h -> h.score));
        if (out.size() > params.maxJunctionHypotheses) {
            out = new ArrayList<>(out.subList(0, params.maxJunctionHypotheses));
        }
        return out;
    }

    private static double angleBetween(double ux, double uy, double vx, double vy) {
        double dot = Math.abs(ux * vx + uy * vy);
        double mag = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        if (mag < 1e-12) return 0;
        return Math.acos(Math.min(1, dot / mag));
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
