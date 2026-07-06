package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Full pipeline: 2D projection candidates -> grid snap -> sliding-window CP-SAT
 * Large Neighborhood Search with lazy geometric constraint generation.
 *
 * The search state may pass through illegal configurations (priced by
 * {@link SearchScore}); the global best legal state is tracked separately.
 */
public class GridFlattenSolver {

    private final ColoredGraph graph;
    private final FlattenParams params;
    private final FlattenListener listener;
    private final Random rnd;

    // Global best across everything (may be illegal early on)
    private GridState bestState;
    private long bestScore = Long.MAX_VALUE;
    // Best fully-legal state seen
    private GridState bestLegalState;
    private long bestLegalScore = Long.MAX_VALUE;

    private long deadline;

    public GridFlattenSolver(ColoredGraph graph, FlattenParams params, FlattenListener listener) {
        this.graph = graph;
        this.params = params;
        this.listener = listener;
        this.rnd = new Random(params.randomSeed);
    }

    public GridState bestState() { return bestState; }
    public GridState bestLegalState() { return bestLegalState; }

    /**
     * @param pos3d vertex -> {x,y,z} force-directed seed layout
     * @return best legal state found, or best overall if nothing legal was reached
     */
    public GridState solve(Map<Integer, double[]> pos3d) {
        deadline = System.currentTimeMillis() + params.totalTimeBudgetMillis;

        listener.onStatus("searching projections (" + params.projectionSamples + " samples)...");
        List<Projection.Candidate> candidates = Projection.searchProjections(graph, pos3d, params, listener);
        if (candidates.isEmpty()) throw new IllegalStateException("no projection candidates");

        // Snap every candidate and keep the one with the best snapped score;
        // the whole LNS budget goes to that single state.
        GridState state = null;
        long snapBest = Long.MAX_VALUE;
        for (int ci = 0; ci < candidates.size(); ci++) {
            Projection.Candidate cand = candidates.get(ci);
            GridState st = GridSnapper.snap(graph, cand.pos2d, params);
            SearchScore ss = SearchScore.of(graph, st, params);
            listener.onStatus(String.format("candidate %d/%d (projection %.1f) snapped: %s",
                    ci + 1, candidates.size(), cand.score, ss.brief()));
            record(st, ss);
            if (ss.total < snapBest) {
                snapBest = ss.total;
                state = st;
            }
        }

        if (!timeUp()) {
            runLns(state, deadline);
        }

        if (bestLegalState != null) {
            listener.onStatus("DONE: best legal score " + bestLegalScore);
            return bestLegalState;
        }
        listener.onStatus("DONE: no fully legal state found; best soft score " + bestScore);
        return bestState;
    }

    private boolean timeUp() {
        return System.currentTimeMillis() >= deadline || listener.isCancelled();
    }

    private void record(GridState state, SearchScore ss) {
        if (ss.total < bestScore) {
            bestScore = ss.total;
            bestState = state.copy();
            listener.onImprovement(bestState.copy(), ss.total, ss.isLegal());
        }
        if (ss.isLegal() && ss.total < bestLegalScore) {
            bestLegalScore = ss.total;
            bestLegalState = state.copy();
        }
    }

    // ------------------------------------------------------------------
    // LNS driver
    // ------------------------------------------------------------------

    /** Sum of L1 distances to the centroid: secondary compactness objective. */
    private long compactness(GridState state) {
        int[] vs = graph.vertices();
        long sx = 0, sy = 0;
        for (int v : vs) { int[] p = state.get(v); sx += p[0]; sy += p[1]; }
        long cx = Math.round(sx / (double) vs.length), cy = Math.round(sy / (double) vs.length);
        long sum = 0;
        for (int v : vs) {
            int[] p = state.get(v);
            sum += Math.abs(p[0] - cx) + Math.abs(p[1] - cy);
        }
        return sum;
    }

    private void runLns(GridState state, long candDeadline) {
        Compactor.compact(graph, state, params);
        long cur = SearchScore.of(graph, state, params).total;
        long curCompact = compactness(state);
        int stall = 0;
        int windowCount = 0;

        while (System.currentTimeMillis() < candDeadline && !listener.isCancelled()) {
            Window w = null;
            for (int attempt = 0; attempt < 10 && w == null; attempt++) {
                w = pickWindow(state);
            }
            if (w == null) break;
            windowCount++;

            boolean accepted = solveWindow(state, cur, curCompact, w);
            if (windowCount % 25 == 0) {
                accepted |= Compactor.compact(graph, state, params);
            }
            if (accepted) {
                cur = SearchScore.of(graph, state, params).total;
                curCompact = compactness(state);
                stall = 0;
                SearchScore ss = SearchScore.of(graph, state, params);
                record(state, ss);
                if (windowCount % 5 == 0 || ss.isLegal()) {
                    listener.onStatus("window " + windowCount + ": " + ss.brief());
                }
            } else {
                stall++;
                if (stall >= params.stallWindowsBeforePerturb) {
                    // Restart from the global best, then shake it
                    if (bestState != null) {
                        for (int v : graph.vertices()) {
                            int[] p = bestState.get(v);
                            state.put(v, p[0], p[1]);
                        }
                    }
                    listener.onStatus("stalled after " + windowCount + " windows; perturbing best (score " + bestScore + ")");
                    perturb(state);
                    Compactor.compact(graph, state, params);
                    cur = SearchScore.of(graph, state, params).total;
                    curCompact = compactness(state);
                    stall = 0;
                }
            }
        }
        Compactor.compact(graph, state, params);
        record(state, SearchScore.of(graph, state, params));
        listener.onStatus("LNS pass finished: " + windowCount + " windows, score "
                + SearchScore.of(graph, state, params).total);
    }

    /** A window: the set of movable vertices plus its bounding box. */
    private static final class Window {
        final Set<Integer> free;
        final int x0, y0, x1, y1;
        double timeSeconds = -1; // -1 = use default
        Window(Set<Integer> free, int x0, int y0, int x1, int y1) {
            this.free = free; this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        }
    }

    private int violationCursor = 0;

    /**
     * Picks the next window. Priority order:
     *  - 80% while violations remain: a window built around one specific
     *    violation, guaranteeing every involved endpoint is free;
     *  - otherwise: a box centered on an expensive edge or a random spot.
     */
    private Window pickWindow(GridState state) {
        SearchScore ss = SearchScore.of(graph, state, params);

        boolean hasViolations = !ss.pairViolations.isEmpty() || !ss.passThroughs.isEmpty()
                || !ss.oversizeEdges.isEmpty();
        if (hasViolations && rnd.nextDouble() < 0.8) {
            // Escalate: every few violation windows, use a big one
            boolean big = (violationCursor % 6) == 5;
            return violationWindow(state, ss, big);
        }

        List<ColoredEdge> hot = new ArrayList<>();
        for (ColoredEdge e : graph.edges()) {
            int[] a = state.get(e.u), b = state.get(e.v);
            int dx = b[0] - a[0], dy = b[1] - a[1];
            int cheb = Math.max(Math.abs(dx), Math.abs(dy));
            if (cheb >= 1 && cheb <= GridGeometry.MAX_CHEB
                    && GridGeometry.edgePenalty(dx, dy) >= GridGeometry.LENGTH_PENALTY[2]) {
                hot.add(e);
            }
        }

        int cx, cy;
        if (!hot.isEmpty() && rnd.nextDouble() < 0.7) {
            ColoredEdge e = hot.get(rnd.nextInt(hot.size()));
            int[] a = state.get(e.u), b = state.get(e.v);
            cx = (a[0] + b[0]) / 2;
            cy = (a[1] + b[1]) / 2;
        } else {
            int minX = state.minX(), maxX = state.maxX(), minY = state.minY(), maxY = state.maxY();
            cx = minX + rnd.nextInt(Math.max(1, maxX - minX + 1));
            cy = minY + rnd.nextInt(Math.max(1, maxY - minY + 1));
        }

        int half = params.windowBoxSize / 2;
        int jx = rnd.nextInt(3) - 1, jy = rnd.nextInt(3) - 1; // jitter so repeated picks differ
        return boxWindow(state, ss, cx - half + jx, cy - half + jy, cx + half + jx, cy + half + jy, null);
    }

    /** Window dedicated to one violation: all involved endpoints are guaranteed free. */
    private Window violationWindow(GridState state, SearchScore ss, boolean big) {
        List<Set<Integer>> targets = new ArrayList<>();
        for (SearchScore.PairViolation pv : ss.pairViolations) {
            Set<Integer> t = new HashSet<>();
            t.add(pv.a.u); t.add(pv.a.v); t.add(pv.b.u); t.add(pv.b.v);
            targets.add(t);
        }
        for (SearchScore.PassThrough pt : ss.passThroughs) {
            Set<Integer> t = new HashSet<>();
            t.add(pt.edge.u); t.add(pt.edge.v); t.add(pt.vertex);
            targets.add(t);
        }
        for (ColoredEdge e : ss.oversizeEdges) {
            Set<Integer> t = new HashSet<>();
            t.add(e.u); t.add(e.v);
            targets.add(t);
        }
        Set<Integer> must = new HashSet<>(targets.get(violationCursor++ % targets.size()));

        // Free the graph neighbors of the involved vertices too: moving a
        // crossing endpoint usually drags its matched partners along.
        for (int v : new ArrayList<>(must)) {
            for (ColoredEdge e : graph.edgesOf(v)) {
                must.add(e.u == v ? e.v : e.u);
            }
        }

        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int v : must) {
            int[] p = state.get(v);
            x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]);
            y0 = Math.min(y0, p[1]); y1 = Math.max(y1, p[1]);
        }
        int margin = big ? 5 : 3;
        Window w = boxWindow(state, ss, x0 - margin, y0 - margin, x1 + margin, y1 + margin, must,
                big ? params.maxFreePerWindow * 2 : params.maxFreePerWindow);
        if (w != null && big) w.timeSeconds = params.windowTimeSeconds * 4;
        return w;
    }

    private Window boxWindow(GridState state, SearchScore ss,
                             int x0, int y0, int x1, int y1, Set<Integer> mustFree) {
        return boxWindow(state, ss, x0, y0, x1, y1, mustFree, params.maxFreePerWindow);
    }

    /**
     * Builds a window from a selection box. Vertices in {@code mustFree} are
     * always included; remaining slots go to the highest-cost vertices inside
     * the box.
     */
    private Window boxWindow(GridState state, SearchScore ss,
                             int x0, int y0, int x1, int y1, Set<Integer> mustFree, int maxFree) {
        List<Integer> inside = new ArrayList<>();
        for (int v : graph.vertices()) {
            if (mustFree != null && mustFree.contains(v)) continue;
            int[] p = state.get(v);
            if (p[0] >= x0 && p[0] <= x1 && p[1] >= y0 && p[1] <= y1) inside.add(v);
        }
        Set<Integer> free = new HashSet<>();
        if (mustFree != null) free.addAll(mustFree);
        int slots = maxFree - free.size();
        if (inside.size() > slots) {
            Map<Integer, Long> vcost = vertexCosts(state, ss);
            inside.sort(Comparator.comparingLong(v -> -vcost.getOrDefault(v, 0L)));
            Collections.shuffle(inside.subList(Math.max(0, slots / 2), inside.size()), rnd);
            inside = inside.subList(0, Math.max(0, slots));
        }
        free.addAll(inside);
        if (free.isEmpty()) return null;

        // Movement box must cover every free vertex plus slack
        int bx0 = x0, by0 = y0, bx1 = x1, by1 = y1;
        for (int v : free) {
            int[] p = state.get(v);
            bx0 = Math.min(bx0, p[0]); bx1 = Math.max(bx1, p[0]);
            by0 = Math.min(by0, p[1]); by1 = Math.max(by1, p[1]);
        }
        int slack = 2;
        bx0 -= slack; by0 -= slack; bx1 += slack; by1 += slack;
        // AllDifferent needs at least |free| empty cells in the movement box
        while (countFreeCells(state, bx0, by0, bx1, by1, free) < free.size() && slack < 12) {
            slack++;
            bx0 = x0 - slack; by0 = y0 - slack; bx1 = x1 + slack; by1 = y1 + slack;
            for (int v : free) {
                int[] p = state.get(v);
                bx0 = Math.min(bx0, p[0] - slack); bx1 = Math.max(bx1, p[0] + slack);
                by0 = Math.min(by0, p[1] - slack); by1 = Math.max(by1, p[1] + slack);
            }
        }
        return new Window(free, bx0, by0, bx1, by1);
    }

    private int countFreeCells(GridState state, int bx0, int by0, int bx1, int by1, Set<Integer> free) {
        Set<Long> occupied = new HashSet<>();
        for (int v : graph.vertices()) {
            if (free.contains(v)) continue;
            int[] p = state.get(v);
            if (p[0] >= bx0 && p[0] <= bx1 && p[1] >= by0 && p[1] <= by1) {
                occupied.add(SearchScore.cellKey(p[0], p[1]));
            }
        }
        int total = (bx1 - bx0 + 1) * (by1 - by0 + 1);
        return total - occupied.size();
    }

    private Map<Integer, Long> vertexCosts(GridState state, SearchScore ss) {
        Map<Integer, Long> cost = new HashMap<>();
        for (ColoredEdge e : graph.edges()) {
            int[] a = state.get(e.u), b = state.get(e.v);
            int dx = b[0] - a[0], dy = b[1] - a[1];
            int cheb = Math.max(Math.abs(dx), Math.abs(dy));
            long c = (cheb > GridGeometry.MAX_CHEB)
                    ? params.oversizeChebUnitCost * (cheb - GridGeometry.MAX_CHEB)
                    : GridGeometry.edgePenalty(dx, dy);
            cost.merge(e.u, c, Long::sum);
            cost.merge(e.v, c, Long::sum);
        }
        for (SearchScore.PairViolation pv : ss.pairViolations) {
            for (ColoredEdge e : new ColoredEdge[]{pv.a, pv.b}) {
                cost.merge(e.u, params.violationCost / 2, Long::sum);
                cost.merge(e.v, params.violationCost / 2, Long::sum);
            }
        }
        for (SearchScore.PassThrough pt : ss.passThroughs) {
            cost.merge(pt.vertex, params.violationCost / 2, Long::sum);
            cost.merge(pt.edge.u, params.violationCost / 2, Long::sum);
            cost.merge(pt.edge.v, params.violationCost / 2, Long::sum);
        }
        return cost;
    }

    // ------------------------------------------------------------------
    // Single window solve with lazy constraint rounds
    // ------------------------------------------------------------------

    /**
     * Solves the window and applies the result to {@code state} if it improves
     * (score, compactness) lexicographically.
     *
     * @return true if state was modified
     */
    private boolean solveWindow(GridState state, long before, long beforeCompact, Window w) {
        long t0 = System.currentTimeMillis();

        Set<Long> pairs = seedPairConstraints(state, w);
        Set<Long> passes = seedPassConstraints(state, w);

        GridState working = null;
        int rounds = 0;
        for (int round = 0; round < params.lazyRoundsPerWindow; round++) {
            rounds = round + 1;
            WindowModel model = new WindowModel(graph, state, w.free, w.x0, w.y0, w.x1, w.y1, params);
            double tsec = w.timeSeconds > 0 ? w.timeSeconds : params.windowTimeSeconds;
            Map<Integer, int[]> sol = model.solve(pairs, passes, tsec, params.cpWorkers);
            if (sol == null) {
                if (debugTiming) {
                    listener.onStatus("DEBUG solveWindow: no solution (round " + round + ", " + w.free.size()
                            + " free, " + pairs.size() + " pairs, " + passes.size() + " passes)");
                }
                return false;
            }

            GridState candidate = state.copy();
            for (Map.Entry<Integer, int[]> e : sol.entrySet()) {
                candidate.put(e.getKey(), e.getValue()[0], e.getValue()[1]);
            }
            SearchScore css = SearchScore.of(graph, candidate, params);

            // Lazily add any violations the model didn't know about
            int added = addViolatedConstraints(css, pairs, passes);
            if (added == 0 || css.total < before) {
                working = candidate;
                break;
            }
        }
        if (working == null) return false;

        long after = SearchScore.of(graph, working, params).total;
        long afterCompact = compactness(working);
        if (debugTiming) {
            listener.onStatus("DEBUG window: " + (System.currentTimeMillis() - t0) + "ms, " + rounds
                    + " rounds, " + pairs.size() + " pairs, " + passes.size() + " passes, "
                    + before + " -> " + after + " (compact " + beforeCompact + " -> " + afterCompact + ")");
        }
        boolean better = after < before || (after == before && afterCompact < beforeCompact);
        if (better) {
            for (int v : w.free) {
                int[] p = working.get(v);
                state.put(v, p[0], p[1]);
            }
        }
        return better;
    }

    private final boolean debugTiming = Boolean.getBoolean("flatten.debug");

    /**
     * Pairs to constrain up front: currently-violating pairs plus pairs of edges
     * that are close to each other right now and touch the window. Everything
     * else is caught by the lazy verification rounds.
     */
    private Set<Long> seedPairConstraints(GridState state, Window w) {
        Set<Long> pairs = new LinkedHashSet<>();
        List<ColoredEdge> edges = graph.edges();
        int n = edges.size();

        int[][] bb = new int[n][];
        boolean[] active = new boolean[n];
        for (int i = 0; i < n; i++) {
            ColoredEdge e = edges.get(i);
            int[] a = state.get(e.u), b = state.get(e.v);
            bb[i] = new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                              Math.max(a[0], b[0]), Math.max(a[1], b[1])};
            active[i] = w.free.contains(e.u) || w.free.contains(e.v);
        }

        // Currently-violating pairs anywhere (they carry the big costs)
        SearchScore ss = SearchScore.of(graph, state, params);
        Map<ColoredEdge, Integer> index = edgeIndex();
        for (SearchScore.PairViolation pv : ss.pairViolations) {
            int i = index.get(pv.a), j = index.get(pv.b);
            if (active[i] || active[j]) {
                pairs.add(i < j ? ((long) i << 32) | j : ((long) j << 32) | i);
            }
        }
        // Current junction pairs touching the window (so the model prices them)
        for (SearchScore.Junction junc : ss.junctions) {
            List<ColoredEdge> je = junc.edges;
            for (int x = 0; x < je.size(); x++) {
                for (int y = x + 1; y < je.size(); y++) {
                    int i = index.get(je.get(x)), j = index.get(je.get(y));
                    if (active[i] || active[j]) {
                        pairs.add(i < j ? ((long) i << 32) | j : ((long) j << 32) | i);
                    }
                }
            }
        }

        // Pairs currently within touching distance of an active edge (gap <= 1).
        // Capped: too many pairs makes CP-SAT infeasible or times out.
        List<long[]> near = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!active[i] && !active[j]) continue;
                if (bb[i][0] > bb[j][2] + 1 || bb[j][0] > bb[i][2] + 1
                        || bb[i][1] > bb[j][3] + 1 || bb[j][1] > bb[i][3] + 1) continue;
                near.add(new long[]{((long) i << 32) | j, active[i] && active[j] ? 0 : 1});
            }
        }
        near.sort(Comparator.comparingLong(a -> a[1]));
        int cap = Math.min(near.size(), Math.max(20, 80 - pairs.size()));
        for (int k = 0; k < cap; k++) pairs.add(near.get(k)[0]);
        return pairs;
    }

    /** Pass-through seeds: only currently-violating ones; lazy rounds add the rest. */
    private Set<Long> seedPassConstraints(GridState state, Window w) {
        Set<Long> passes = new LinkedHashSet<>();
        SearchScore ss = SearchScore.of(graph, state, params);
        Map<ColoredEdge, Integer> index = edgeIndex();
        for (SearchScore.PassThrough pt : ss.passThroughs) {
            passes.add(((long) index.get(pt.edge) << 32) | pt.vertex);
        }
        return passes;
    }

    private int addViolatedConstraints(SearchScore css, Set<Long> pairs, Set<Long> passes) {
        Map<ColoredEdge, Integer> index = edgeIndex();
        int added = 0;
        for (SearchScore.PairViolation pv : css.pairViolations) {
            int i = index.get(pv.a), j = index.get(pv.b);
            long key = i < j ? ((long) i << 32) | j : ((long) j << 32) | i;
            if (pairs.add(key)) added++;
        }
        for (SearchScore.PassThrough pt : css.passThroughs) {
            long key = ((long) index.get(pt.edge) << 32) | pt.vertex;
            if (passes.add(key)) added++;
        }
        return added;
    }

    private Map<ColoredEdge, Integer> edgeIndexCache;
    private Map<ColoredEdge, Integer> edgeIndex() {
        if (edgeIndexCache == null) {
            edgeIndexCache = new HashMap<>();
            List<ColoredEdge> edges = graph.edges();
            for (int i = 0; i < edges.size(); i++) edgeIndexCache.put(edges.get(i), i);
        }
        return edgeIndexCache;
    }

    // ------------------------------------------------------------------
    // Perturbation: teleport a random cluster of vertices to fresh cells
    // near their neighbors' centroid, accepting the (usually worse) state.
    // ------------------------------------------------------------------

    private void perturb(GridState state) {
        int[] vs = graph.vertices();
        int seedV = vs[rnd.nextInt(vs.length)];
        int[] c = state.get(seedV);

        // Cluster = seed + graph neighbors within radius 3
        Set<Integer> cluster = new HashSet<>();
        cluster.add(seedV);
        for (ColoredEdge e : graph.edgesOf(seedV)) {
            cluster.add(e.u == seedV ? e.v : e.u);
        }
        Set<Long> occupied = new HashSet<>();
        for (int v : vs) {
            if (!cluster.contains(v)) {
                int[] p = state.get(v);
                occupied.add(SearchScore.cellKey(p[0], p[1]));
            }
        }
        for (int v : cluster) {
            int tx = c[0] + rnd.nextInt(9) - 4;
            int ty = c[1] + rnd.nextInt(9) - 4;
            int[] cell = GridSnapper.nearestFreeCell(tx, ty, occupied);
            state.put(v, cell[0], cell[1]);
            occupied.add(SearchScore.cellKey(cell[0], cell[1]));
        }
    }
}
