package io.chandler.gap.cache;

import java.util.Arrays;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Stores visited group states using long prefix hashes for membership and
 * parent/generator derivations with periodic keyframe snapshots of full permutations.
 */
public class KeyframeStateCache {

    public static final int KEYFRAME_INTERVAL = 22;

    private final int prefixLen;
    private final int nElements;
    private final List<int[][]> parsedOperations;

    private final LongOpenHashSet hashes = new LongOpenHashSet();

    private int[] parentId = new int[16];
    private byte[] genIndex = new byte[16];
    private short[][] keyframePerm = new short[16][];
    private int size = 0;

    public KeyframeStateCache(int prefixLen, int nElements, List<int[][]> parsedOperations) {
        this.prefixLen = prefixLen;
        this.nElements = nElements;
        this.parsedOperations = parsedOperations;
    }

    public int prefixLen() {
        return prefixLen;
    }

    public int size() {
        return size;
    }

    public boolean containsHash(long hash) {
        return hashes.contains(hash);
    }

    public long hash(int[] perm) {
        return StateHash.encode(perm, prefixLen, nElements);
    }

    public long hash(short[] perm) {
        return StateHash.encode(perm, prefixLen, nElements);
    }

    public short[] cvt(int[] perm) {
        short[] result = new short[perm.length];
        for (int i = 0; i < perm.length; i++) {
            result[i] = (short) (perm[i] & 0xffff);
        }
        return result;
    }

    /** Register the identity / root state at BFS depth 0. */
    public int registerRoot(int[] perm) {
        short[] permShort = cvt(perm);
        if (size != 0) {
            throw new IllegalStateException("Root already registered");
        }
        long h = hash(permShort);
        hashes.add(h);
        ensureCapacity(1);
        parentId[0] = -1;
        genIndex[0] = -1;
        keyframePerm[0] = permShort.clone();
        size = 1;
        return 0;
    }

    /**
     * Register a newly discovered state. Returns the assigned state id, or -1 if duplicate.
     */
    public int tryAdd(int parentStateId, byte gen, int[] perm, int depth) {
        short[] permShort = cvt(perm);
        long h = hash(perm);
        if (!hashes.add(h)) {
            return -1;
        }
        int id = size++;
        ensureCapacity(id + 1);
        parentId[id] = parentStateId;
        genIndex[id] = gen;
        if (depth % KEYFRAME_INTERVAL == 0) {
            keyframePerm[id] = permShort.clone();
        } else {
            keyframePerm[id] = null;
        }
        return id;
    }

    public int[] reconstruct(int stateId) {
        if (stateId < 0 || stateId >= size) {
            throw new IllegalArgumentException("Invalid state id: " + stateId);
        }
        int[] gens = new int[KEYFRAME_INTERVAL];
        int genCount = 0;
        int id = stateId;
        while (keyframePerm[id] == null) {
            gens[genCount++] = genIndex[id] & 0xff;
            id = parentId[id];
        }
        short[] result = keyframePerm[id].clone();
        for (int i = genCount - 1; i >= 0; i--) {
            result = applyGeneratorCopy(result, gens[i]);
        }
        int[] resultInt = new int[result.length];
        for (int i = 0; i < result.length; i++) {
            resultInt[i] = result[i] & 0xffff;
        }
        return resultInt;
    }

    public void clear() {
        hashes.clear();
        size = 0;
        Arrays.fill(keyframePerm, 0, keyframePerm.length, null);
    }

    /** Applies one generator, matching {@link GroupExplorer} BFS semantics. */
    public short[] applyGeneratorCopy(short[] state, int genIndex) {
        int[][] operation = parsedOperations.get(genIndex);
        short[] newState = Arrays.copyOf(state, state.length);
        for (int[] cycle : operation) {
            if (cycle.length > 1) {
                int first = cycle[0];
                for (int i = 0; i < cycle.length - 1; i++) {
                    int current = cycle[i];
                    int next = cycle[i + 1];
                    newState[current - 1] = state[next - 1];
                }
                newState[cycle[cycle.length - 1] - 1] = state[first - 1];
            }
        }
        return newState;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= parentId.length) {
            return;
        }
        int newCap = Math.max(minCapacity, parentId.length * 2);
        parentId = Arrays.copyOf(parentId, newCap);
        genIndex = Arrays.copyOf(genIndex, newCap);
        keyframePerm = Arrays.copyOf(keyframePerm, newCap);
    }
}
