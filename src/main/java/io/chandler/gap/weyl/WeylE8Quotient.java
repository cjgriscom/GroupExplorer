package io.chandler.gap.weyl;

import io.chandler.gap.cache.StateHash;

/** Maps full 240-point permutations to coordinates for Weyl(E8) compress hashing. */
public final class WeylE8Quotient {

    public enum Encoding {
        /** Pair index (1..120) only; collapses odd/even within target pair. */
        PAIR_ONLY,
        /** Full image label (1..240) for each primary slot; retains within-pair parity. */
        PRIMARY_IMAGE
    }

    private WeylE8Quotient() {}

    public static int hashDomainSize(Encoding encoding) {
        return encoding == Encoding.PAIR_ONLY ? WeylE8Antipodes.NUM_PAIRS : WeylE8Antipodes.NUM_POINTS;
    }

    /**
     * Writes hash coordinates into {@code out} (length 120).
     * {@link Encoding#PAIR_ONLY}: pair index (1..120) for each primary label.
     * {@link Encoding#PRIMARY_IMAGE}: full image label (1..240) for each primary label.
     */
    public static void encode(int[] perm, byte[] out, Encoding encoding) {
        for (int k = 0; k < WeylE8Antipodes.NUM_PAIRS; k++) {
            int image = perm[k * 2];
            if (encoding == Encoding.PAIR_ONLY) {
                out[k] = (byte) WeylE8Antipodes.pairIndex(WeylE8Antipodes.canonicalLabel(image));
            } else {
                out[k] = (byte) image;
            }
        }
    }

    public static void encode(int[] perm, byte[] out) {
        encode(perm, out, Encoding.PAIR_ONLY);
    }

    public static long[] hashLimbs(int[] perm, int prefixLen, int numLongs, Encoding encoding, long[] scratch) {
        byte[] quotient = new byte[WeylE8Antipodes.NUM_PAIRS];
        encode(perm, quotient, encoding);
        int domain = hashDomainSize(encoding);
        int[] asInt = new int[WeylE8Antipodes.NUM_PAIRS];
        for (int i = 0; i < asInt.length; i++) {
            asInt[i] = quotient[i] & 0xff;
        }
        long[] limbs = StateHash.encodeLimbs(asInt, prefixLen, domain, numLongs);
        if (scratch == null || scratch.length < limbs.length) {
            return limbs;
        }
        System.arraycopy(limbs, 0, scratch, 0, limbs.length);
        return scratch;
    }

    public static long[] hashLimbs(int[] perm, int prefixLen, int numLongs, long[] scratch) {
        return hashLimbs(perm, prefixLen, numLongs, Encoding.PAIR_ONLY, scratch);
    }
}
