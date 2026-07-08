package io.chandler.gap.cache;

import java.util.HashSet;
import java.util.Set;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;

/**
 * Mixed-radix encoding of the first {@code prefixLen} images in a permutation state.
 * Used as a compact, collision-checkable key for group element deduplication.
 */
public final class StateHash {

    private StateHash() {}

    /**
     * Maximum prefix length storable in {@code compressBits} without overflow,
     * derived as {@code floor(compressBits / log2(nElements))}, capped at {@code nElements}.
     */
    public static int derivePrefixLength(int nElements, int compressBits) {
        if (nElements <= 1) {
            return nElements;
        }
        int fromBits = (int) ((double) compressBits / (Math.log(nElements) / Math.log(2)));
        return Math.min(nElements, fromBits);
    }

    public static long encode(int[] state, int prefixLen, int nElements) {
        return encodeRange(state, 0, Math.min(prefixLen, state.length), nElements);
    }

    public static long encode(short[] state, int prefixLen, int nElements) {
        return encodeRange(state, 0, Math.min(prefixLen, state.length), nElements);
    }

    /**
     * Encodes {@code prefixLen} images split across {@code numLongs} 64-bit limbs without overflow.
     * Each limb holds up to {@link #derivePrefixLength(int, int) derivePrefixLength(n, 64)} coordinates.
     */
    public static long[] encodeLimbs(int[] state, int prefixLen, int nElements, int numLongs) {
        return encodeLimbsImpl(state, prefixLen, nElements, numLongs);
    }

    public static long[] encodeLimbs(short[] state, int prefixLen, int nElements, int numLongs) {
        return encodeLimbsImpl(state, prefixLen, nElements, numLongs);
    }

    private static long[] encodeLimbsImpl(Object state, int prefixLen, int nElements, int numLongs) {
        if (numLongs < 1) {
            throw new IllegalArgumentException("numLongs must be >= 1");
        }
        int limbCapacity = derivePrefixLength(nElements, 64);
        long[] parts = new long[numLongs];
        int limb = 0;
        int offset = 0;
        while (offset < prefixLen && limb < numLongs) {
            int len = Math.min(limbCapacity, prefixLen - offset);
            if (state instanceof int[]) {
                parts[limb] = encodeRange((int[]) state, offset, len, nElements);
            } else {
                parts[limb] = encodeRange((short[]) state, offset, len, nElements);
            }
            offset += len;
            limb++;
        }
        return parts;
    }

    private static long encodeRange(int[] state, int offset, int len, int nElements) {
        long value = 0;
        long base = nElements + 1L;
        int end = Math.min(offset + len, state.length);
        for (int i = offset; i < end; i++) {
            value = value * base + state[i];
        }
        return value;
    }

    private static long encodeRange(short[] state, int offset, int len, int nElements) {
        long value = 0;
        long base = nElements + 1L;
        int end = Math.min(offset + len, state.length);
        for (int i = offset; i < end; i++) {
            value = value * base + state[i];
        }
        return value;
    }

    /**
     * Explores the full group and verifies that {@code prefixLen}-prefix encoding is injective.
     *
     * @return true if order equals unique hash count and there are zero collisions
     */
    public static boolean verifyInjective(GroupExplorer group, int prefixLen) {
        Set<Long> seen = new HashSet<>();
        int[] collisions = {0};

        int[] identity = new int[group.nElements];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i + 1;
        }
        seen.add(encode(identity, prefixLen, group.nElements));

        group.exploreStates(false, (states, depth) -> {
            for (int[] s : states) {
                long h = encode(s, prefixLen, group.nElements);
                if (!seen.add(h)) {
                    collisions[0]++;
                }
            }
        });
        int order = group.order();
        boolean ok = collisions[0] == 0 && seen.size() == order;
        System.out.println("StateHash verify: order=" + order
            + " uniqueHashes=" + seen.size()
            + " collisions=" + collisions[0]
            + " prefixLen=" + prefixLen
            + " ok=" + ok);
        return ok;
    }

    public static boolean verifyInjective(String generators, int prefixLen) {
        GroupExplorer group = new GroupExplorer(generators, MemorySettings.FASTEST);
        return verifyInjective(group, prefixLen);
    }
}
