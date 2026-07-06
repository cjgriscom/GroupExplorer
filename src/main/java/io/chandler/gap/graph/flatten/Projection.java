package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Samples random rotations of a 3D layout, projects each to the XY plane, and
 * scores the resulting continuous 2D drawing for "flattenability":
 *  - short, uniform edge lengths (relative to median),
 *  - few different-color edge crossings (these must be eliminated entirely later),
 *  - same-color crossings are cheap but not free (they must become junctions),
 *  - low point congestion (pairs of vertices much closer than the median edge).
 *
 * The output candidates are scaled so the median edge length is 1.0 grid unit,
 * ready for snapping.
 */
public final class Projection {

    public static final class Candidate {
        public final Map<Integer, double[]> pos2d;  // vertex -> {x, y}, median edge ~= 1
        public final double score;                  // lower is better
        public final double[] quaternion;
        Candidate(Map<Integer, double[]> pos2d, double score, double[] q) {
            this.pos2d = pos2d; this.score = score; this.quaternion = q;
        }
    }

    private Projection() {}

    /**
     * @param pos3d vertex -> {x,y,z} from the force layout
     * @return top-K candidates, best first
     */
    public static List<Candidate> searchProjections(ColoredGraph g, Map<Integer, double[]> pos3d,
                                                    FlattenParams params, FlattenListener listener) {
        Random rnd = new Random(params.randomSeed);
        List<Candidate> all = new ArrayList<>();
        for (int s = 0; s < params.projectionSamples; s++) {
            if (listener.isCancelled()) break;
            double[] q = (s == 0) ? new double[]{1, 0, 0, 0} : randomQuaternion(rnd);
            Map<Integer, double[]> p2 = rotateProject(pos3d, q);
            normalizeToMedianEdge(g, p2);
            double score = scoreProjection(g, p2);
            all.add(new Candidate(p2, score, q));
            if (s % 100 == 99) listener.onStatus("projection search " + (s + 1) + "/" + params.projectionSamples);
        }
        all.sort(Comparator.comparingDouble(c -> c.score));
        List<Candidate> top = new ArrayList<>(all.subList(0, Math.min(params.projectionTopK, all.size())));
        if (!top.isEmpty()) {
            listener.onStatus(String.format("projection search done: best=%.1f worst-kept=%.1f",
                    top.get(0).score, top.get(top.size() - 1).score));
        }
        return top;
    }

    static double[] randomQuaternion(Random rnd) {
        // Shoemake's uniform random rotation
        double u1 = rnd.nextDouble(), u2 = rnd.nextDouble(), u3 = rnd.nextDouble();
        double s1 = Math.sqrt(1 - u1), s2 = Math.sqrt(u1);
        return new double[]{
                s1 * Math.sin(2 * Math.PI * u2),
                s1 * Math.cos(2 * Math.PI * u2),
                s2 * Math.sin(2 * Math.PI * u3),
                s2 * Math.cos(2 * Math.PI * u3)
        };
    }

    static Map<Integer, double[]> rotateProject(Map<Integer, double[]> pos3d, double[] q) {
        double w = q[0], x = q[1], y = q[2], z = q[3];
        // Rotation matrix rows for X' and Y' only (we drop Z)
        double r00 = 1 - 2 * (y * y + z * z), r01 = 2 * (x * y - w * z), r02 = 2 * (x * z + w * y);
        double r10 = 2 * (x * y + w * z),     r11 = 1 - 2 * (x * x + z * z), r12 = 2 * (y * z - w * x);
        Map<Integer, double[]> out = new java.util.HashMap<>();
        for (Map.Entry<Integer, double[]> e : pos3d.entrySet()) {
            double[] p = e.getValue();
            double px = p[0], py = p[1], pz = p.length > 2 ? p[2] : 0;
            out.put(e.getKey(), new double[]{
                    r00 * px + r01 * py + r02 * pz,
                    r10 * px + r11 * py + r12 * pz
            });
        }
        return out;
    }

    static void normalizeToMedianEdge(ColoredGraph g, Map<Integer, double[]> pos) {
        List<ColoredEdge> edges = g.edges();
        double[] lens = new double[edges.size()];
        for (int i = 0; i < edges.size(); i++) {
            lens[i] = dist(pos.get(edges.get(i).u), pos.get(edges.get(i).v));
        }
        java.util.Arrays.sort(lens);
        double median = lens[lens.length / 2];
        if (median <= 1e-12) median = 1;
        for (double[] p : pos.values()) { p[0] /= median; p[1] /= median; }
    }

    /** Lower is better. */
    static double scoreProjection(ColoredGraph g, Map<Integer, double[]> pos) {
        List<ColoredEdge> edges = g.edges();
        double lengthCost = 0;
        for (ColoredEdge e : edges) {
            double len = dist(pos.get(e.u), pos.get(e.v));
            // unit edges free-ish; long edges quadratic
            double over = Math.max(0, len - 1.2);
            lengthCost += over * over * 4.0;
        }

        double crossCost = 0;
        int n = edges.size();
        for (int i = 0; i < n; i++) {
            ColoredEdge ei = edges.get(i);
            double[] a = pos.get(ei.u), b = pos.get(ei.v);
            for (int j = i + 1; j < n; j++) {
                ColoredEdge ej = edges.get(j);
                if (ei.sharesVertex(ej)) continue;
                if (ei.u == ej.u && ei.v == ej.v) continue;
                double[] c = pos.get(ej.u), d = pos.get(ej.v);
                if (!segmentsCross(a, b, c, d)) continue;
                if (ei.color == ej.color) {
                    // Potential junction: cheap if midpoints already close and both edges short
                    double mdist = Math.hypot((a[0] + b[0] - c[0] - d[0]) / 2, (a[1] + b[1] - c[1] - d[1]) / 2);
                    crossCost += 1.0 + 3.0 * mdist;
                } else {
                    crossCost += 12.0;
                }
            }
        }

        // Congestion: vertices closer than 0.5 units
        double congestion = 0;
        int[] vs = g.vertices();
        for (int i = 0; i < vs.length; i++) {
            double[] a = pos.get(vs[i]);
            for (int j = i + 1; j < vs.length; j++) {
                double[] b = pos.get(vs[j]);
                double dx = Math.abs(a[0] - b[0]);
                if (dx > 0.5) continue;
                double dy = Math.abs(a[1] - b[1]);
                if (dy > 0.5) continue;
                double d = Math.hypot(dx, dy);
                if (d < 0.5) congestion += (0.5 - d) * 8.0;
            }
        }

        return lengthCost + crossCost + congestion;
    }

    static boolean segmentsCross(double[] a, double[] b, double[] c, double[] d) {
        double d1 = orient(c, d, a), d2 = orient(c, d, b);
        double d3 = orient(a, b, c), d4 = orient(a, b, d);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
            && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double orient(double[] p, double[] q, double[] r) {
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]);
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
