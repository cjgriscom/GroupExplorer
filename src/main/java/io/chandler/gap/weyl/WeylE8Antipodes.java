package io.chandler.gap.weyl;

/**
 * Antipodal pairing for Weyl(E8) on 240 roots.
 *
 * <p>GAP confirms the central involution swaps consecutive labels
 * {@code (1,2), (3,4), …, (239,240)}.
 */
public final class WeylE8Antipodes {

    public static final int NUM_POINTS = 240;
    public static final int NUM_PAIRS = 120;

    private WeylE8Antipodes() {}

    /** Antipodal partner of {@code label} (1-based). */
    public static int partner(int label) {
        return (label & 1) == 1 ? label + 1 : label - 1;
    }

    /** Pair index in {@code 1..120} for {@code label} (1-based). */
    public static int pairIndex(int label) {
        return (label + 1) >> 1;
    }

    /** Primary (odd) label for {@code pair} in {@code 1..120}. */
    public static int pairPrimary(int pair) {
        return pair * 2 - 1;
    }

    /** Canonical odd label for the pair containing {@code label}. */
    public static int canonicalLabel(int label) {
        return (label & 1) == 1 ? label : label - 1;
    }
}
