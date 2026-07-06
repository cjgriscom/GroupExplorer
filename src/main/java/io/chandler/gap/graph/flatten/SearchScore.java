package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact scoring used by the LNS search loop. Unlike {@link GridScorer}, hard-rule
 * violations do not make the score undefined; they are priced with large costs so
 * the solver can traverse mildly-illegal intermediate states and still compare them.
 *
 * Components:
 *  - edge cost: {@link GridGeometry#edgePenalty(int, int)} for edges with Chebyshev
 *    length in [1, MAX_CHEB]; over-length edges cost LENGTH_PENALTY[MAX_CHEB] plus
 *    a steep per-unit surcharge.
 *  - junction cost: legal same-color midpoint-coincident crossings, grouped by
 *    (color, doubled midpoint).
 *  - violation cost: illegal crossings/overlaps and vertex pass-throughs.
 */
public final class SearchScore {

    public enum PairKind {
        /** Proper crossing that is not (same color + equal midpoints). */
        CROSS_ILLEGAL,
        /** Collinear positive-length overlap of two distinct edges. */
        OVERLAP_ILLEGAL
    }

    public static final class PairViolation {
        public final ColoredEdge a, b;
        public final PairKind kind;
        PairViolation(ColoredEdge a, ColoredEdge b, PairKind kind) {
            this.a = a; this.b = b; this.kind = kind;
        }
        @Override public String toString() { return kind + " " + a + " x " + b; }
    }

    public static final class PassThrough {
        public final ColoredEdge edge;
        public final int vertex;
        PassThrough(ColoredEdge edge, int vertex) { this.edge = edge; this.vertex = vertex; }
        @Override public String toString() { return "vertex " + vertex + " interior on " + edge; }
    }

    public static final class Junction {
        public final int color;
        public final long mx2, my2;  // doubled midpoint coordinates
        public final List<ColoredEdge> edges = new ArrayList<>();
        Junction(int color, long mx2, long my2) { this.color = color; this.mx2 = mx2; this.my2 = my2; }
    }

    public final long total;
    public final long edgeCost;
    public final long junctionCost;
    public final long violationCost;
    public final List<PairViolation> pairViolations;
    public final List<PassThrough> passThroughs;
    public final List<ColoredEdge> oversizeEdges;
    public final List<Junction> junctions;

    private SearchScore(long edgeCost, long junctionCost, long violationCost,
                        List<PairViolation> pv, List<PassThrough> pt,
                        List<ColoredEdge> oversize, List<Junction> junctions) {
        this.edgeCost = edgeCost;
        this.junctionCost = junctionCost;
        this.violationCost = violationCost;
        this.total = edgeCost + junctionCost + violationCost;
        this.pairViolations = pv;
        this.passThroughs = pt;
        this.oversizeEdges = oversize;
        this.junctions = junctions;
    }

    public int violationCount() { return pairViolations.size() + passThroughs.size(); }

    public boolean isLegal() { return violationCount() == 0 && oversizeEdges.isEmpty(); }

    public String brief() {
        return "total=" + total + " (edge=" + edgeCost + " junc=" + junctionCost
                + " viol=" + violationCost + "; " + pairViolations.size() + " pair viol, "
                + passThroughs.size() + " pass-through, " + oversizeEdges.size() + " oversize, "
                + junctions.size() + " junctions)";
    }

    public static SearchScore of(ColoredGraph g, GridState s, FlattenParams p) {
        List<ColoredEdge> edges = g.edges();
        int n = edges.size();

        // Vertex positions and occupancy
        Map<Long, Integer> cellToVertex = new HashMap<>();
        for (int v : g.vertices()) {
            int[] pos = s.get(v);
            cellToVertex.put(cellKey(pos[0], pos[1]), v);
        }

        int[][] a = new int[n][];   // endpoint u position
        int[][] b = new int[n][];   // endpoint v position
        boolean[] oversize = new boolean[n];
        long edgeCost = 0;
        List<ColoredEdge> oversizeList = new ArrayList<>();
        List<PassThrough> passThroughs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ColoredEdge e = edges.get(i);
            a[i] = s.get(e.u);
            b[i] = s.get(e.v);
            int dx = b[i][0] - a[i][0], dy = b[i][1] - a[i][1];
            int cheb = Math.max(Math.abs(dx), Math.abs(dy));
            if (cheb > GridGeometry.MAX_CHEB) {
                oversize[i] = true;
                oversizeList.add(e);
                edgeCost += GridGeometry.LENGTH_PENALTY[GridGeometry.MAX_CHEB]
                        + p.oversizeChebUnitCost * (cheb - GridGeometry.MAX_CHEB);
            } else {
                edgeCost += GridGeometry.edgePenalty(dx, dy);
                // Pass-through check: interior lattice points must be vertex-free
                for (int[] off : GridGeometry.interiorLatticePoints(dx, dy)) {
                    Integer occ = cellToVertex.get(cellKey(a[i][0] + off[0], a[i][1] + off[1]));
                    if (occ != null) passThroughs.add(new PassThrough(e, occ));
                }
            }
        }

        // Pairwise geometry
        List<PairViolation> pairViolations = new ArrayList<>();
        Map<String, Junction> junctionMap = new HashMap<>();

        for (int i = 0; i < n; i++) {
            if (oversize[i]) continue;
            ColoredEdge ei = edges.get(i);
            int ilx = Math.min(a[i][0], b[i][0]), ihx = Math.max(a[i][0], b[i][0]);
            int ily = Math.min(a[i][1], b[i][1]), ihy = Math.max(a[i][1], b[i][1]);
            for (int j = i + 1; j < n; j++) {
                if (oversize[j]) continue;
                ColoredEdge ej = edges.get(j);
                // Duplicate vertex pair in two colors: full coincidence is allowed by rule
                if (ei.u == ej.u && ei.v == ej.v) continue;
                // Bounding box reject
                if (Math.min(a[j][0], b[j][0]) > ihx || Math.max(a[j][0], b[j][0]) < ilx
                        || Math.min(a[j][1], b[j][1]) > ihy || Math.max(a[j][1], b[j][1]) < ily) continue;

                int rel = GridGeometry.segRelation(a[i][0], a[i][1], b[i][0], b[i][1],
                                                   a[j][0], a[j][1], b[j][0], b[j][1]);
                if (rel == GridGeometry.DISJOINT || rel == GridGeometry.SHARED_ENDPOINT_ONLY) continue;

                if (rel == GridGeometry.OVERLAP) {
                    pairViolations.add(new PairViolation(ei, ej, PairKind.OVERLAP_ILLEGAL));
                } else { // CROSS
                    if (ei.sharesVertex(ej)) {
                        // Segments sharing an endpoint can only "cross" if one endpoint
                        // touches the other's interior — always illegal.
                        pairViolations.add(new PairViolation(ei, ej, PairKind.CROSS_ILLEGAL));
                        continue;
                    }
                    long[] mi = GridGeometry.midpoint2(a[i][0], a[i][1], b[i][0], b[i][1]);
                    long[] mj = GridGeometry.midpoint2(a[j][0], a[j][1], b[j][0], b[j][1]);
                    if (ei.color == ej.color && mi[0] == mj[0] && mi[1] == mj[1]) {
                        String key = ei.color + ":" + mi[0] + ":" + mi[1];
                        Junction junc = junctionMap.computeIfAbsent(key,
                                k -> new Junction(ei.color, mi[0], mi[1]));
                        if (!junc.edges.contains(ei)) junc.edges.add(ei);
                        if (!junc.edges.contains(ej)) junc.edges.add(ej);
                    } else {
                        pairViolations.add(new PairViolation(ei, ej, PairKind.CROSS_ILLEGAL));
                    }
                }
            }
        }

        long junctionCost = 0;
        for (Junction j : junctionMap.values()) {
            junctionCost += GridGeometry.JUNCTION_BASE_PENALTY
                    + GridGeometry.JUNCTION_EXTRA_EDGE_PENALTY * (j.edges.size() - 2);
        }

        long violationCost = (long) (pairViolations.size() + passThroughs.size()) * p.violationCost;

        return new SearchScore(edgeCost, junctionCost, violationCost,
                pairViolations, passThroughs, oversizeList,
                new ArrayList<>(junctionMap.values()));
    }

    static long cellKey(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
}
