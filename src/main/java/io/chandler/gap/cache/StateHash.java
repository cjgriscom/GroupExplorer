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
     * Maximum prefix length storable in a signed 64-bit key without overflow,
     * derived as {@code floor(log_{nElements}(2^64))}, capped at {@code nElements}.
     */
    public static int derivePrefixLength(int nElements) {
        if (nElements <= 1) {
            return nElements;
        }
        // log_n(2^64) = 64 / log2(n)
        int fromBits = (int) (64.0 / (Math.log(nElements) / Math.log(2)));
        return Math.min(nElements, fromBits);
    }

    public static long encode(int[] state, int prefixLen, int nElements) {
        long value = 0;
        long base = nElements + 1L;
        int len = Math.min(prefixLen, state.length);
        for (int i = 0; i < len; i++) {
            value = value * base + state[i];
        }
        return value;
    }

    public static long encode(short[] state, int prefixLen, int nElements) {
        long value = 0;
        long base = nElements + 1L;
        int len = Math.min(prefixLen, state.length);
        for (int i = 0; i < len; i++) {
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

    public static boolean verifyInjective(String generators) {
        GroupExplorer group = new GroupExplorer(generators, MemorySettings.FASTEST);
        return verifyInjective(group, derivePrefixLength(group.nElements));
    }
}
