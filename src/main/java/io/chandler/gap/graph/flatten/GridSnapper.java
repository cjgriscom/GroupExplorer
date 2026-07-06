package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a continuous 2D projection (median edge length ~1) into a feasible
 * initial {@link GridState}: every vertex on a distinct integer cell.
 *
 * Strategy: search a small range of scale factors; for each, snap vertices to
 * their nearest cells resolving collisions by spiral search (most-crowded
 * vertices assigned first), and keep the scale whose snapped state has the
 * best {@link SearchScore}. The result intentionally ignores crossing rules —
 * the LNS phase repairs those.
 */
public final class GridSnapper {

    private GridSnapper() {}

    public static GridState snap(ColoredGraph g, Map<Integer, double[]> pos2d, FlattenParams params) {
        // Violations are cheap at this stage: a compact start with fixable crossings
        // beats a clean but sparse one that LNS can never pull together.
        FlattenParams snapPricing = new FlattenParams();
        snapPricing.violationCost = params.snapViolationCost;
        snapPricing.oversizeChebUnitCost = params.oversizeChebUnitCost;

        // Base scale from target occupancy: bounding box area ~= N / occupancy
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int v : g.vertices()) {
            double[] p = pos2d.get(v);
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        double area1 = Math.max(1, (maxX - minX) * (maxY - minY));
        double targetArea = g.vertexCount() / params.targetOccupancy;
        double sStar = Math.sqrt(targetArea / area1);

        double[] scales = {sStar * 0.7, sStar * 0.85, sStar, sStar * 1.15, sStar * 1.3};
        GridState best = null;
        long bestScore = Long.MAX_VALUE;
        for (double s : scales) {
            GridState st = snapAtScale(g, pos2d, s);
            normalizeOrigin(st);
            Compactor.compact(g, st, snapPricing);
            long score = snapScore(g, st, snapPricing);
            if (score < bestScore) {
                bestScore = score;
                best = st;
            }
        }
        return best;
    }

    /** Shift so min coordinate is 0 on each axis. */
    static void normalizeOrigin(GridState st) {
        int minX = st.minX(), minY = st.minY();
        if (minX == 0 && minY == 0) return;
        for (int v : st.placedVertices()) {
            int[] p = st.get(v);
            st.put(v, p[0] - minX, p[1] - minY);
        }
    }

    /** Search score plus bounding-box area penalty (drives compaction). */
    static long snapScore(ColoredGraph g, GridState st, FlattenParams pricing) {
        long base = SearchScore.of(g, st, pricing).total;
        int w = st.maxX() - st.minX() + 1;
        int h = st.maxY() - st.minY() + 1;
        return base + (long) w * h;
    }

    static GridState snapAtScale(ColoredGraph g, Map<Integer, double[]> pos2d, double scale) {
        int[] vs = g.vertices();

        // Assign crowded vertices first so they get their ideal cells and the
        // spiral displacement is pushed to the sparse fringe.
        Map<Integer, Double> density = new HashMap<>();
        for (int v : vs) {
            double[] p = pos2d.get(v);
            double d = 0;
            for (int w : vs) {
                if (w == v) continue;
                double[] q = pos2d.get(w);
                double dist2 = sq(p[0] - q[0]) + sq(p[1] - q[1]);
                d += 1.0 / (0.25 + dist2);
            }
            density.put(v, d);
        }
        List<Integer> order = new ArrayList<>();
        for (int v : vs) order.add(v);
        order.sort(Comparator.comparingDouble(v -> -density.get(v)));

        GridState st = new GridState();
        Set<Long> occupied = new HashSet<>();
        for (int v : order) {
            double[] p = pos2d.get(v);
            double ix = p[0] * scale, iy = p[1] * scale;
            int[] cell = nearestFreeCell(ix, iy, occupied);
            st.put(v, cell[0], cell[1]);
            occupied.add(SearchScore.cellKey(cell[0], cell[1]));
        }
        return st;
    }

    /** Nearest unoccupied integer cell to (ix, iy), by actual Euclidean distance. */
    static int[] nearestFreeCell(double ix, double iy, Set<Long> occupied) {
        int cx = (int) Math.round(ix), cy = (int) Math.round(iy);
        for (int r = 0; r < 64; r++) {
            int[] bestCell = null;
            double bestD = Double.MAX_VALUE;
            // ring of Chebyshev radius r
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cx + dx, y = cy + dy;
                    if (occupied.contains(SearchScore.cellKey(x, y))) continue;
                    double d = sq(x - ix) + sq(y - iy);
                    if (d < bestD) { bestD = d; bestCell = new int[]{x, y}; }
                }
            }
            if (bestCell != null) return bestCell;
        }
        throw new IllegalStateException("no free cell within radius 64 of (" + ix + "," + iy + ")");
    }

    private static double sq(double x) { return x * x; }
}
