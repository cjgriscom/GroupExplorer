package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import com.google.ortools.sat.TableConstraint;

/**
 * One CP-SAT solve of a local window: a subset of "free" vertices may move
 * within a bounding box while everything else stays fixed.
 *
 * Model summary:
 *  - Position variables for free vertices, AllDifferent over encoded cells
 *    (fixed vertices inside the box contribute constant codes).
 *  - Every edge touching a free vertex ("active") gets displacement variables.
 *    Normal edges are restricted to Chebyshev length 1..5 with the exact
 *    {@link GridGeometry#edgePenalty(int, int)} cost via a 120-tuple table.
 *    Edges that cannot reach length &le; 5 inside this window are "relaxed":
 *    any displacement, costed steeply per unit over 5, excluded from geometry.
 *  - Pairwise segment constraints only for an explicitly requested pair set
 *    (lazy constraint generation happens outside this class). The encoding is
 *    exact for closed segments:
 *       legal(A,B) = strictSepByLine(A) or strictSepByLine(B)
 *                    or collinearDisjoint
 *                    or (sameColor and midpointsEqual and notCollinear)
 *    The last disjunct is the "junction" case and carries a cost.
 *  - Pass-through constraints (vertex strictly interior to an edge segment)
 *    for an explicitly requested (edge, vertex) set.
 *  - Objective: 64 * (edge costs + junction costs + oversize costs) + drift
 *    (L1 distance from current positions, as a stabilizing tie-breaker).
 */
final class WindowModel {

    private static volatile boolean nativeLoaded = false;
    static void ensureNative() {
        if (!nativeLoaded) {
            synchronized (WindowModel.class) {
                if (!nativeLoaded) {
                    Loader.loadNativeLibraries();
                    nativeLoaded = true;
                }
            }
        }
    }

    private static final long SCALE = 64;

    /** Shared (dx, dy, penalty) tuples for all allowed displacements. */
    private static long[][] displacementTuples;
    private static synchronized long[][] displacementTuples() {
        if (displacementTuples == null) {
            List<long[]> tuples = new ArrayList<>();
            for (int dx = -GridGeometry.MAX_CHEB; dx <= GridGeometry.MAX_CHEB; dx++) {
                for (int dy = -GridGeometry.MAX_CHEB; dy <= GridGeometry.MAX_CHEB; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    tuples.add(new long[]{dx, dy, GridGeometry.edgePenalty(dx, dy)});
                }
            }
            displacementTuples = tuples.toArray(new long[0][]);
        }
        return displacementTuples;
    }

    // ---- inputs ----
    private final ColoredGraph graph;
    private final GridState current;
    private final Set<Integer> freeVerts;
    private final int bx0, by0, bx1, by1;
    private final FlattenParams params;

    // ---- model state ----
    private final CpModel model = new CpModel();
    private final Map<Integer, IntVar[]> freeVars = new HashMap<>();
    private final LinearExprBuilder objective = LinearExpr.newBuilder();
    /** Per active edge index: materialized dx/dy IntVars; null entry = inactive or relaxed. */
    private final Map<Integer, IntVar[]> edgeDisp = new HashMap<>();
    private final Set<Integer> relaxedEdges = new java.util.HashSet<>();
    private int auxCounter = 0;

    WindowModel(ColoredGraph graph, GridState current, Set<Integer> freeVerts,
                int bx0, int by0, int bx1, int by1, FlattenParams params) {
        ensureNative();
        this.graph = graph;
        this.current = current;
        this.freeVerts = freeVerts;
        this.bx0 = bx0; this.by0 = by0; this.bx1 = bx1; this.by1 = by1;
        this.params = params;
    }

    /**
     * Builds and solves the model.
     *
     * @param pairConstraints  set of edge-index pairs (i &lt; j packed as (long) i << 32 | j)
     * @param passConstraints  set of (edgeIndex, vertexId) packed the same way
     * @return new positions for the free vertices, or null if no solution found
     */
    Map<Integer, int[]> solve(Set<Long> pairConstraints, Set<Long> passConstraints,
                              double timeSeconds, int workers) {
        buildPositions();
        buildEdges();
        List<ColoredEdge> edges = graph.edges();
        for (long pk : pairConstraints) {
            int i = (int) (pk >>> 32), j = (int) (pk & 0xffffffffL);
            addPairConstraint(i, j, edges.get(i), edges.get(j));
        }
        for (long pk : passConstraints) {
            int ei = (int) (pk >>> 32), w = (int) (pk & 0xffffffffL);
            addPassThroughConstraint(ei, edges.get(ei), w);
        }

        model.minimize(objective.build());

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(timeSeconds);
        solver.getParameters().setNumSearchWorkers(workers);
        CpSolverStatus status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) return null;

        Map<Integer, int[]> out = new HashMap<>();
        for (Map.Entry<Integer, IntVar[]> e : freeVars.entrySet()) {
            out.put(e.getKey(), new int[]{
                    (int) solver.value(e.getValue()[0]),
                    (int) solver.value(e.getValue()[1])});
        }
        return out;
    }

    boolean isRelaxed(int edgeIndex) { return relaxedEdges.contains(edgeIndex); }

    // ------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------

    private void buildPositions() {
        // Centroid of the whole current layout: gravity target
        long sx = 0, sy = 0;
        int[] vs = graph.vertices();
        for (int v : vs) { int[] p = current.get(v); sx += p[0]; sy += p[1]; }
        int gx = (int) Math.round(sx / (double) vs.length);
        int gy = (int) Math.round(sy / (double) vs.length);

        List<LinearArgument> allDiff = new ArrayList<>();
        for (int v : freeVerts) {
            int[] cur = current.get(v);
            IntVar x = model.newIntVar(bx0, bx1, "x" + v);
            IntVar y = model.newIntVar(by0, by1, "y" + v);
            freeVars.put(v, new IntVar[]{x, y});
            allDiff.add(cellCode(x, y));
            model.addHint(x, cur[0]);
            model.addHint(y, cur[1]);
            // Drift tie-breaker
            IntVar ddx = newAux(0, Math.max(bx1 - bx0, 1));
            IntVar ddy = newAux(0, Math.max(by1 - by0, 1));
            model.addAbsEquality(ddx, LinearExpr.newBuilder().add(x).add(-cur[0]).build());
            model.addAbsEquality(ddy, LinearExpr.newBuilder().add(y).add(-cur[1]).build());
            objective.addTerm(ddx, 1).addTerm(ddy, 1);
            // Gravity: L1 pull toward layout centroid (compaction pressure)
            if (params.gravityWeight > 0) {
                IntVar gdx = newAux(0, 4096), gdy = newAux(0, 4096);
                model.addAbsEquality(gdx, LinearExpr.newBuilder().add(x).add(-gx).build());
                model.addAbsEquality(gdy, LinearExpr.newBuilder().add(y).add(-gy).build());
                objective.addTerm(gdx, params.gravityWeight).addTerm(gdy, params.gravityWeight);
            }
        }
        // Fixed vertices inside the box block their cells
        for (int v : graph.vertices()) {
            if (freeVerts.contains(v)) continue;
            int[] p = current.get(v);
            if (p[0] >= bx0 && p[0] <= bx1 && p[1] >= by0 && p[1] <= by1) {
                allDiff.add(LinearExpr.constant(constCellCode(p[0], p[1])));
            }
        }
        model.addAllDifferent(allDiff.toArray(new LinearArgument[0]));
    }

    private static final long CODE_OFFSET = 2048, CODE_STRIDE = 4096;
    private LinearArgument cellCode(IntVar x, IntVar y) {
        return LinearExpr.newBuilder().addTerm(x, CODE_STRIDE).add(y).add(CODE_OFFSET * CODE_STRIDE + CODE_OFFSET).build();
    }
    private long constCellCode(int x, int y) {
        return (x + CODE_OFFSET) * CODE_STRIDE + (y + CODE_OFFSET);
    }

    private LinearArgument coordExpr(int vertex, int axis) {
        IntVar[] fv = freeVars.get(vertex);
        if (fv != null) return fv[axis];
        return LinearExpr.constant(current.get(vertex)[axis]);
    }

    private long coordLb(int vertex, int axis) {
        if (freeVars.containsKey(vertex)) return axis == 0 ? bx0 : by0;
        return current.get(vertex)[axis];
    }
    private long coordUb(int vertex, int axis) {
        if (freeVars.containsKey(vertex)) return axis == 0 ? bx1 : by1;
        return current.get(vertex)[axis];
    }

    // ------------------------------------------------------------------
    // Edges
    // ------------------------------------------------------------------

    private void buildEdges() {
        List<ColoredEdge> edges = graph.edges();
        for (int i = 0; i < edges.size(); i++) {
            ColoredEdge e = edges.get(i);
            boolean active = freeVerts.contains(e.u) || freeVerts.contains(e.v);
            if (!active) continue;

            long dxLb = coordLb(e.v, 0) - coordUb(e.u, 0), dxUb = coordUb(e.v, 0) - coordLb(e.u, 0);
            long dyLb = coordLb(e.v, 1) - coordUb(e.u, 1), dyUb = coordUb(e.v, 1) - coordLb(e.u, 1);
            boolean reachable = intervalsAdmitCheb5(dxLb, dxUb) && intervalsAdmitCheb5(dyLb, dyUb);
            int[] pu = current.get(e.u), pv = current.get(e.v);
            int curCheb = Math.max(Math.abs(pv[0] - pu[0]), Math.abs(pv[1] - pu[1]));
            boolean relaxed = !reachable || curCheb > GridGeometry.MAX_CHEB;

            if (relaxed) {
                relaxedEdges.add(i);
                IntVar dx = newAux(dxLb, dxUb);
                IntVar dy = newAux(dyLb, dyUb);
                model.addEquality(dx, diff(e.v, e.u, 0));
                model.addEquality(dy, diff(e.v, e.u, 1));
                long absMax = Math.max(Math.max(Math.abs(dxLb), Math.abs(dxUb)),
                                       Math.max(Math.abs(dyLb), Math.abs(dyUb)));
                IntVar adx = newAux(0, absMax), ady = newAux(0, absMax);
                model.addAbsEquality(adx, dx);
                model.addAbsEquality(ady, dy);
                IntVar cheb = newAux(1, absMax);   // >= 1 also forbids (0,0)
                model.addMaxEquality(cheb, new IntVar[]{adx, ady});
                IntVar over = newAux(0, Math.max(absMax - GridGeometry.MAX_CHEB, 0));
                model.addGreaterOrEqual(
                        LinearExpr.newBuilder().add(over).addTerm(cheb, -1).add(GridGeometry.MAX_CHEB).build(), 0);
                objective.addTerm(over, SCALE * params.oversizeChebUnitCost)
                         .addTerm(cheb, SCALE); // gentle pull inward even below the cap
                edgeDisp.put(i, null);
            } else {
                IntVar dx = newAux(Math.max(dxLb, -GridGeometry.MAX_CHEB), Math.min(dxUb, GridGeometry.MAX_CHEB));
                IntVar dy = newAux(Math.max(dyLb, -GridGeometry.MAX_CHEB), Math.min(dyUb, GridGeometry.MAX_CHEB));
                model.addEquality(dx, diff(e.v, e.u, 0));
                model.addEquality(dy, diff(e.v, e.u, 1));
                IntVar cost = newAux(0, GridGeometry.LENGTH_PENALTY[GridGeometry.MAX_CHEB]
                        + GridGeometry.ANGLE_PENALTY + GridGeometry.DIAG_UNIT_PENALTY);
                TableConstraint table = model.addAllowedAssignments(new IntVar[]{dx, dy, cost});
                table.addTuples(displacementTuples());
                objective.addTerm(cost, SCALE);
                edgeDisp.put(i, new IntVar[]{dx, dy});
            }
        }
    }

    private static boolean intervalsAdmitCheb5(long lb, long ub) {
        // Interval [lb, ub] intersects [-5, 5]
        return lb <= GridGeometry.MAX_CHEB && ub >= -GridGeometry.MAX_CHEB;
    }

    private LinearExpr diff(int va, int vb, int axis) {
        LinearExprBuilder b = LinearExpr.newBuilder();
        IntVar[] fa = freeVars.get(va);
        if (fa != null) b.addTerm(fa[axis], 1); else b.add(current.get(va)[axis]);
        IntVar[] fb = freeVars.get(vb);
        if (fb != null) b.addTerm(fb[axis], -1); else b.add(-current.get(vb)[axis]);
        return b.build();
    }

    // ------------------------------------------------------------------
    // Affine value helper: a coordinate difference or product, materialized
    // as (constant) or (IntVar) with tracked bounds.
    // ------------------------------------------------------------------

    private final class Val {
        final IntVar var;   // null => constant
        final long k;       // constant value when var == null
        final long lb, ub;
        Val(long constant) { this.var = null; this.k = constant; this.lb = constant; this.ub = constant; }
        Val(IntVar v, long lb, long ub) { this.var = v; this.k = 0; this.lb = lb; this.ub = ub; }
        boolean isConst() { return var == null; }
        LinearArgument arg() { return var != null ? var : LinearExpr.constant(k); }
    }

    private IntVar newAux(long lb, long ub) {
        if (lb > ub) lb = ub; // degenerate guard
        return model.newIntVar(lb, ub, "a" + (auxCounter++));
    }

    private final Map<Long, Val> diffCache = new HashMap<>();
    private final Map<Long, Val> mulCache = new HashMap<>();

    /** coordinate(va) - coordinate(vb) as a Val (cached). */
    private Val diffVal(int va, int vb, int axis) {
        IntVar[] fa = freeVars.get(va), fb = freeVars.get(vb);
        if (fa == null && fb == null) {
            return new Val(current.get(va)[axis] - current.get(vb)[axis]);
        }
        long key = (((long) va << 21) | vb) << 1 | axis;
        Val cached = diffCache.get(key);
        if (cached != null) return cached;
        long lb = coordLb(va, axis) - coordUb(vb, axis);
        long ub = coordUb(va, axis) - coordLb(vb, axis);
        IntVar v = newAux(lb, ub);
        model.addEquality(v, diff(va, vb, axis));
        Val val = new Val(v, lb, ub);
        diffCache.put(key, val);
        return val;
    }

    private Val mul(Val a, Val b) {
        if (a.isConst() && b.isConst()) return new Val(a.k * b.k);
        if (a.isConst()) return scale(b, a.k);
        if (b.isConst()) return scale(a, b.k);
        long ck = cacheKey(a.var, b.var);
        Val cached = mulCache.get(ck);
        if (cached != null) return cached;
        long[] c = {a.lb * b.lb, a.lb * b.ub, a.ub * b.lb, a.ub * b.ub};
        long lo = Math.min(Math.min(c[0], c[1]), Math.min(c[2], c[3]));
        long hi = Math.max(Math.max(c[0], c[1]), Math.max(c[2], c[3]));
        IntVar p = newAux(lo, hi);
        model.addMultiplicationEquality(p, new IntVar[]{a.var, b.var});
        Val val = new Val(p, lo, hi);
        mulCache.put(ck, val);
        return val;
    }

    private final Map<IntVar, Integer> varIds = new HashMap<>();
    private long cacheKey(IntVar a, IntVar b) {
        int ia = varIds.computeIfAbsent(a, k -> varIds.size());
        int ib = varIds.computeIfAbsent(b, k -> varIds.size());
        return ia < ib ? ((long) ia << 32) | ib : ((long) ib << 32) | ia;
    }

    private Val scale(Val a, long k) {
        if (k == 0) return new Val(0);
        if (a.isConst()) return new Val(a.k * k);
        long lo = Math.min(a.lb * k, a.ub * k), hi = Math.max(a.lb * k, a.ub * k);
        IntVar v = newAux(lo, hi);
        model.addEquality(v, LinearExpr.newBuilder().addTerm(a.var, k).build());
        return new Val(v, lo, hi);
    }

    private Val sub(Val a, Val b) {
        if (a.isConst() && b.isConst()) return new Val(a.k - b.k);
        long lo = a.lb - b.ub, hi = a.ub - b.lb;
        IntVar v = newAux(lo, hi);
        LinearExprBuilder eb = LinearExpr.newBuilder();
        if (a.isConst()) eb.add(a.k); else eb.addTerm(a.var, 1);
        if (b.isConst()) eb.add(-b.k); else eb.addTerm(b.var, -1);
        model.addEquality(v, eb.build());
        return new Val(v, lo, hi);
    }

    private Val add(Val a, Val b) {
        if (a.isConst() && b.isConst()) return new Val(a.k + b.k);
        long lo = a.lb + b.lb, hi = a.ub + b.ub;
        IntVar v = newAux(lo, hi);
        LinearExprBuilder eb = LinearExpr.newBuilder();
        if (a.isConst()) eb.add(a.k); else eb.addTerm(a.var, 1);
        if (b.isConst()) eb.add(b.k); else eb.addTerm(b.var, 1);
        model.addEquality(v, eb.build());
        return new Val(v, lo, hi);
    }

    /** cross((x1,y1),(x2,y2)) = x1*y2 - y1*x2 */
    private Val cross(Val x1, Val y1, Val x2, Val y2) {
        return sub(mul(x1, y2), mul(y1, x2));
    }

    /** dot((x1,y1),(x2,y2)) = x1*x2 + y1*y2 */
    private Val dot(Val x1, Val y1, Val x2, Val y2) {
        return add(mul(x1, x2), mul(y1, y2));
    }

    /** literal true => val >= bound (half-reified). */
    private void impliesGe(Literal lit, Val v, long bound) {
        if (v.isConst()) {
            if (v.k < bound) model.addBoolOr(new Literal[]{lit.not()});
            return;
        }
        model.addGreaterOrEqual(v.var, bound).onlyEnforceIf(lit);
    }

    private void impliesLe(Literal lit, Val v, long bound) {
        if (v.isConst()) {
            if (v.k > bound) model.addBoolOr(new Literal[]{lit.not()});
            return;
        }
        model.addLessOrEqual(v.var, bound).onlyEnforceIf(lit);
    }

    private void impliesEq(Literal lit, Val v, long value) {
        if (v.isConst()) {
            if (v.k != value) model.addBoolOr(new Literal[]{lit.not()});
            return;
        }
        model.addEquality(v.var, value).onlyEnforceIf(lit);
    }

    /** Returns a literal that (when true) forces val != 0. */
    private Literal nonZeroLit(Val v) {
        BoolVar pos = model.newBoolVar("nzp" + auxCounter);
        BoolVar neg = model.newBoolVar("nzn" + auxCounter);
        BoolVar nz = model.newBoolVar("nz" + (auxCounter++));
        impliesGe(pos, v, 1);
        impliesLe(neg, v, -1);
        model.addBoolOr(new Literal[]{pos, neg, nz.not()});
        return nz;
    }

    // ------------------------------------------------------------------
    // Pairwise segment constraint
    // ------------------------------------------------------------------

    private void addPairConstraint(int i, int j, ColoredEdge a, ColoredEdge b) {
        if (relaxedEdges.contains(i) || relaxedEdges.contains(j)) return;
        boolean iActive = edgeDisp.containsKey(i), jActive = edgeDisp.containsKey(j);
        if (!iActive && !jActive) return;
        if (a.u == b.u && a.v == b.v) return; // duplicate pair (two colors): coincide by construction

        int shared = sharedVertex(a, b);
        if (shared >= 0) {
            addAdjacentPairConstraint(a, b, shared);
        } else {
            addNonAdjacentPairConstraint(a, b);
        }
    }

    private static int sharedVertex(ColoredEdge a, ColoredEdge b) {
        if (a.u == b.u || a.u == b.v) return a.u;
        if (a.v == b.u || a.v == b.v) return a.v;
        return -1;
    }

    /** Escape literal that satisfies any soft disjunction at a steep price. */
    private BoolVar violationLit() {
        BoolVar viol = model.newBoolVar("viol" + (auxCounter++));
        objective.addTerm(viol, SCALE * params.violationCost);
        return viol;
    }

    /**
     * Edges share vertex w; other endpoints p, q. Legal iff they meet only at w:
     * cross(p-w, q-w) != 0  OR  dot(p-w, q-w) <= -1 (collinear pointing apart).
     */
    private void addAdjacentPairConstraint(ColoredEdge a, ColoredEdge b, int w) {
        int p = (a.u == w) ? a.v : a.u;
        int q = (b.u == w) ? b.v : b.u;
        Val px = diffVal(p, w, 0), py = diffVal(p, w, 1);
        Val qx = diffVal(q, w, 0), qy = diffVal(q, w, 1);
        Val cr = cross(px, py, qx, qy);
        Val dt = dot(px, py, qx, qy);
        BoolVar crPos = model.newBoolVar("apP" + auxCounter);
        BoolVar crNeg = model.newBoolVar("apN" + auxCounter);
        BoolVar apart = model.newBoolVar("apD" + (auxCounter++));
        impliesGe(crPos, cr, 1);
        impliesLe(crNeg, cr, -1);
        impliesLe(apart, dt, -1);
        model.addBoolOr(new Literal[]{crPos, crNeg, apart, violationLit()});
    }

    /**
     * Four distinct endpoints. Legal iff
     *   strict separation by line A, or by line B, or collinear-disjoint,
     *   or (same color AND midpoints equal AND not collinear) [junction, costed].
     */
    private void addNonAdjacentPairConstraint(ColoredEdge a, ColoredEdge b) {
        int p1 = a.u, p2 = a.v, q1 = b.u, q2 = b.v;

        Val dax = diffVal(p2, p1, 0), day = diffVal(p2, p1, 1);
        Val dbx = diffVal(q2, q1, 0), dby = diffVal(q2, q1, 1);
        Val q1px = diffVal(q1, p1, 0), q1py = diffVal(q1, p1, 1);
        Val q2px = diffVal(q2, p1, 0), q2py = diffVal(q2, p1, 1);
        Val p1qx = diffVal(p1, q1, 0), p1qy = diffVal(p1, q1, 1);
        Val p2qx = diffVal(p2, q1, 0), p2qy = diffVal(p2, q1, 1);

        Val c1 = cross(dax, day, q1px, q1py);
        Val c2 = cross(dax, day, q2px, q2py);
        Val c3 = cross(dbx, dby, p1qx, p1qy);
        Val c4 = cross(dbx, dby, p2qx, p2qy);

        List<Literal> disjuncts = new ArrayList<>();

        BoolVar sepAp = model.newBoolVar("sAp" + auxCounter);
        impliesGe(sepAp, c1, 1); impliesGe(sepAp, c2, 1);
        BoolVar sepAn = model.newBoolVar("sAn" + auxCounter);
        impliesLe(sepAn, c1, -1); impliesLe(sepAn, c2, -1);
        BoolVar sepBp = model.newBoolVar("sBp" + auxCounter);
        impliesGe(sepBp, c3, 1); impliesGe(sepBp, c4, 1);
        BoolVar sepBn = model.newBoolVar("sBn" + (auxCounter++));
        impliesLe(sepBn, c3, -1); impliesLe(sepBn, c4, -1);
        disjuncts.add(sepAp); disjuncts.add(sepAn);
        disjuncts.add(sepBp); disjuncts.add(sepBn);

        // Collinear and disjoint: all on one line, spans do not overlap.
        BoolVar col = model.newBoolVar("col" + auxCounter++);
        impliesEq(col, c1, 0);
        impliesEq(col, c2, 0);
        Val d1 = dot(q1px, q1py, diffVal(q1, p2, 0), diffVal(q1, p2, 1));
        Val d2 = dot(q2px, q2py, diffVal(q2, p2, 0), diffVal(q2, p2, 1));
        Val d3 = dot(p1qx, p1qy, diffVal(p1, q2, 0), diffVal(p1, q2, 1));
        impliesGe(col, d1, 1);
        impliesGe(col, d2, 1);
        impliesGe(col, d3, 1);
        disjuncts.add(col);

        if (a.color == b.color) {
            BoolVar junction = model.newBoolVar("junc" + auxCounter++);
            // midpoints equal (doubled): p1 + p2 == q1 + q2 componentwise
            for (int axis = 0; axis < 2; axis++) {
                LinearExprBuilder eb = LinearExpr.newBuilder();
                addCoord(eb, p1, axis, 1); addCoord(eb, p2, axis, 1);
                addCoord(eb, q1, axis, -1); addCoord(eb, q2, axis, -1);
                model.addEquality(eb.build(), 0).onlyEnforceIf(junction);
            }
            // not collinear: cross of directions != 0
            Val dirCross = cross(dax, day, dbx, dby);
            Literal nz = nonZeroLit(dirCross);
            model.addImplication(junction, nz);
            objective.addTerm(junction, SCALE * GridGeometry.JUNCTION_BASE_PENALTY);
            disjuncts.add(junction);
        }

        disjuncts.add(violationLit());
        model.addBoolOr(disjuncts.toArray(new Literal[0]));
    }

    private void addCoord(LinearExprBuilder eb, int vertex, int axis, long coeff) {
        IntVar[] fv = freeVars.get(vertex);
        if (fv != null) eb.addTerm(fv[axis], coeff);
        else eb.add(coeff * current.get(vertex)[axis]);
    }

    // ------------------------------------------------------------------
    // Pass-through constraint: vertex w must not lie strictly inside edge e.
    // Legal iff cross(v-u, w-u) != 0 OR dot(w-u, w-v) >= 0.
    // ------------------------------------------------------------------

    private void addPassThroughConstraint(int edgeIndex, ColoredEdge e, int w) {
        if (relaxedEdges.contains(edgeIndex)) return;
        if (w == e.u || w == e.v) return;
        boolean anyVar = freeVars.containsKey(e.u) || freeVars.containsKey(e.v) || freeVars.containsKey(w);
        if (!anyVar) return;
        Val ex = diffVal(e.v, e.u, 0), ey = diffVal(e.v, e.u, 1);
        Val wux = diffVal(w, e.u, 0), wuy = diffVal(w, e.u, 1);
        Val wvx = diffVal(w, e.v, 0), wvy = diffVal(w, e.v, 1);
        Val cr = cross(ex, ey, wux, wuy);
        Val dt = dot(wux, wuy, wvx, wvy);
        BoolVar crPos = model.newBoolVar("ptP" + auxCounter);
        BoolVar crNeg = model.newBoolVar("ptN" + auxCounter);
        BoolVar outside = model.newBoolVar("ptO" + (auxCounter++));
        impliesGe(crPos, cr, 1);
        impliesLe(crNeg, cr, -1);
        impliesGe(outside, dt, 0);
        model.addBoolOr(new Literal[]{crPos, crNeg, outside, violationLit()});
    }
}
