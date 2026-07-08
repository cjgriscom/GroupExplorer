package io.chandler.gap.cache;

import java.util.Arrays;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Stores visited group states using prefix hashes for membership and
 * parent/generator derivations with periodic keyframe snapshots of full permutations.
 *
 * <p>Hash width is configured by {@code numLongs} (each limb is 64 bits).
 */
public class KeyframeStateCache implements CompressStateCache {

    public static int KEYFRAME_INTERVAL = 8;
    public static boolean KEYFRAMES_ENABLED = true;

    /** Prefix hash key spanning one or more 64-bit mixed-radix limbs. */
    public static final class PrefixHash {
        public final long[] parts;

        public PrefixHash(long[] parts) {
            this.parts = parts;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PrefixHash)) return false;
            return Arrays.equals(parts, ((PrefixHash) obj).parts);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(parts);
        }
    }

    private final int prefixLen;
    private final int numLongs;
    private final int nElements;
    private final List<int[][]> parsedOperations;

    private final ObjectOpenHashSet<PrefixHash> hashes = new ObjectOpenHashSet<>();

    private int[] parentId = new int[16];
    private byte[] genIndex = new byte[16];
    private short[][] keyframePerm = new short[16][];
    private int size = 0;

    public KeyframeStateCache(int prefixLen, int compressBits, int nElements, List<int[][]> parsedOperations) {
        if (compressBits <= 0 || compressBits % 64 != 0) {
            throw new IllegalArgumentException("compressBits must be a positive multiple of 64: " + compressBits);
        }
        this.prefixLen = prefixLen;
        this.numLongs = compressBits / 64;
        this.nElements = nElements;
        this.parsedOperations = parsedOperations;
    }

    public int prefixLen() {
        return prefixLen;
    }

    public int numLongs() {
        return numLongs;
    }

    public int compressBits() {
        return numLongs * 64;
    }

    @Override
    public int nElements() {
        return nElements;
    }

    @Override
    public boolean keyframesEnabled() {
        return KEYFRAMES_ENABLED;
    }

    @Override
    public List<int[][]> parsedOperations() {
        return parsedOperations;
    }

    public int size() {
        return size;
    }

    public boolean containsHash(PrefixHash hash) {
        return hashes.contains(hash);
    }

    public PrefixHash hash(int[] perm) {
        return new PrefixHash(StateHash.encodeLimbs(perm, prefixLen, nElements, numLongs));
    }

    public PrefixHash hash(short[] perm) {
        return new PrefixHash(StateHash.encodeLimbs(perm, prefixLen, nElements, numLongs));
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
        PrefixHash h = hash(permShort);
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
        PrefixHash h = hash(perm);
        if (!hashes.add(h)) {
            return -1;
        }
        int id = size++;
        ensureCapacity(id + 1);
        parentId[id] = parentStateId;
        genIndex[id] = gen;
        if (KEYFRAMES_ENABLED && depth % KEYFRAME_INTERVAL == 0) {
            keyframePerm[id] = cvt(perm);
        } else {
            keyframePerm[id] = null;
        }
        return id;
    }

    public int[] reconstruct(int stateId) {
        if (stateId < 0 || stateId >= size) {
            throw new IllegalArgumentException("Invalid state id: " + stateId);
        }
        if (!KEYFRAMES_ENABLED) {
            int[] path = tracePath(stateId);
            int[] state = new int[nElements];
            for (int i = 0; i < state.length; i++) {
                state[i] = i + 1;
            }
            for (int g : path) {
                state = io.chandler.gap.GroupExplorer.applyOperation(state, parsedOperations.get(g));
            }
            return state;
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

    /**
     * Returns the generator indices applied from the root to {@code stateId}, in
     * forward (root -> state) order. Reconstructs the BFS path without a separate
     * backtracking map by walking the stored parent/generator chain.
     */
    public int[] tracePath(int stateId) {
        if (stateId < 0 || stateId >= size) {
            throw new IllegalArgumentException("Invalid state id: " + stateId);
        }
        IntArrayList gens = new IntArrayList();
        int id = stateId;
        while (parentId[id] != -1) {
            gens.add(genIndex[id] & 0xff);
            id = parentId[id];
        }
        int n = gens.size();
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = gens.getInt(n - 1 - i);
        }
        return result;
    }

    public int parentOf(int stateId) {
        return parentId[stateId];
    }

    public int genOf(int stateId) {
        return genIndex[stateId] & 0xff;
    }

    public void clear() {
        hashes.clear();
        size = 0;
        Arrays.fill(keyframePerm, 0, keyframePerm.length, null);
    }

    /** Applies one generator, matching {@link io.chandler.gap.GroupExplorer} BFS semantics. */
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
