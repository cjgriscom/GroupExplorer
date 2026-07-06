package io.chandler.gap.graph.flatten;

import java.util.HashSet;
import java.util.Set;

/**
 * Global compaction moves that windows cannot make: collapsing empty grid rows
 * and columns. Each candidate collapse is applied and kept only if the exact
 * {@link SearchScore} does not get worse (collapses can break junction midpoint
 * alignment or create new crossings, so validation is mandatory).
 */
public final class Compactor {

    private Compactor() {}

    /** Repeatedly removes empty rows/columns while the score does not worsen. */
    public static boolean compact(ColoredGraph g, GridState state, FlattenParams params) {
        boolean any = false;
        boolean progress = true;
        while (progress) {
            progress = false;
            long score = SearchScore.of(g, state, params).total;
            for (int axis = 0; axis <= 1; axis++) {
                Set<Integer> used = new HashSet<>();
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int v : g.vertices()) {
                    int c = state.get(v)[axis];
                    used.add(c);
                    lo = Math.min(lo, c);
                    hi = Math.max(hi, c);
                }
                for (int c = lo + 1; c < hi; c++) {
                    if (used.contains(c)) continue;
                    GridState trial = state.copy();
                    trial.shiftBeyond(axis, c, -1);
                    long trialScore = SearchScore.of(g, trial, params).total;
                    if (trialScore <= score) {
                        state.shiftBeyond(axis, c, -1);
                        score = trialScore;
                        progress = true;
                        any = true;
                        break; // column indices shifted; recompute
                    }
                }
                if (progress) break;
            }
        }
        return any;
    }
}
