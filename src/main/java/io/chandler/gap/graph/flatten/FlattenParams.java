package io.chandler.gap.graph.flatten;

/**
 * Tunable parameters for the grid flattening pipeline.
 */
public class FlattenParams {
    // Seed / projection
    public int springIterations = 5000;
    public int projectionSamples = 400;
    public int projectionTopK = 3;
    public double targetOccupancy = 0.55;

    // Junction hypotheses
    public int maxJunctionHypotheses = 150;

    // LNS / windows
    public int maxFreePerWindow = 16;
    public int windowBoxSize = 8;
    /** Chebyshev cap used during search; edges longer than MAX_CHEB but <= this stay model-feasible with huge penalty. */
    public int maxChebExtended = 14;
    public double windowTimeSeconds = 3.0;
    public int lazyRoundsPerWindow = 6;
    public int cpWorkers = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    public int stallWindowsBeforePerturb = 30;
    public long totalTimeBudgetMillis = 20 * 60 * 1000L;

    // Search scoring (soft handling of hard-rule violations during LNS)
    public long violationCost = 100_000L;
    public long oversizeChebUnitCost = 20_000L;
    /** Reduced violation price when choosing the initial snap scale (compact starts beat clean-but-sparse ones). */
    public long snapViolationCost = 2_000L;
    /** Per-unit L1 pull toward the layout center inside window solves (pre-SCALE units). */
    public long gravityWeight = 2L;

    public long randomSeed = 12345L;

    public FlattenParams() {}
}
