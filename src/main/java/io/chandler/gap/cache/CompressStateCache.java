package io.chandler.gap.cache;

import java.util.List;

import io.chandler.gap.cache.KeyframeStateCache.PrefixHash;

/** Visited-set backing store for COMPRESS-mode BFS. */
public interface CompressStateCache {

    int size();

    int nElements();

    int prefixLen();

    int numLongs();

    boolean keyframesEnabled();

    PrefixHash hash(int[] perm);

    boolean containsHash(PrefixHash hash);

    int registerRoot(int[] perm);

    int tryAdd(int parentStateId, byte gen, int[] perm, int depth);

    int[] reconstruct(int stateId);

    int[] tracePath(int stateId);

    void clear();

    List<int[][]> parsedOperations();
}
