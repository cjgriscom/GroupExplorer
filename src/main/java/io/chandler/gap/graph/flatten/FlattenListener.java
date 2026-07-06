package io.chandler.gap.graph.flatten;

/**
 * Progress callback for the flattening pipeline. Implementations may be called
 * from a background thread; UI consumers should marshal to the FX thread.
 */
public interface FlattenListener {
    /** Free-form status line (phase changes, window stats, etc). */
    void onStatus(String message);

    /**
     * Called whenever the working state improves (throttled by the caller).
     *
     * @param snapshot  a defensive copy of the current best working state
     * @param softScore the search score (lower is better; includes violation costs)
     * @param legal     true if the snapshot satisfies every hard rule
     */
    void onImprovement(GridState snapshot, long softScore, boolean legal);

    /** Return true to abort the run at the next safe point. */
    boolean isCancelled();

    FlattenListener CONSOLE = new FlattenListener() {
        @Override public void onStatus(String message) { System.out.println("[flatten] " + message); }
        @Override public void onImprovement(GridState snapshot, long softScore, boolean legal) {
            System.out.println("[flatten] improved: soft=" + softScore + " legal=" + legal);
        }
        @Override public boolean isCancelled() { return false; }
    };
}
