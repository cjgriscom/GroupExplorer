package io.chandler.gap.cache;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Stores visited group states using prefix hashes for membership and
 * parent/generator derivations with periodic keyframe snapshots of full permutations.
 *
 * <p>Supports 64-bit ({@code compressBits == 64}) and 128-bit ({@code compressBits == 128})
 * mixed-radix prefix keys via {@link PrefixHash}.
 */
public class KeyframeStateCache {

    public static final int KEYFRAME_INTERVAL = 4;

    /** Prefix hash key: one limb for 64-bit mode, two limbs for 128-bit mode. */
    public static final class PrefixHash {
        public final long part0;
        public final long part1;

        public PrefixHash(long part0, long part1) {
            this.part0 = part0;
            this.part1 = part1;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PrefixHash)) return false;
            PrefixHash other = (PrefixHash) obj;
            return part0 == other.part0 && part1 == other.part1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(part0, part1);
        }
    }

    private final int prefixLen;
    private final int compressBits;
    private final int nElements;
    private final List<int[][]> parsedOperations;

    private final ObjectOpenHashSet<PrefixHash> hashes = new ObjectOpenHashSet<>();

    private int[] parentId = new int[16];
    private byte[] genIndex = new byte[16];
    private short[][] keyframePerm = new short[16][];
    private int size = 0;

    public KeyframeStateCache(int prefixLen, int compressBits, int nElements, List<int[][]> parsedOperations) {
        if (compressBits != 64 && compressBits != 128) {
            throw new IllegalArgumentException("Unsupported compressBits: " + compressBits);
        }
        this.prefixLen = prefixLen;
        this.compressBits = compressBits;
        this.nElements = nElements;
        this.parsedOperations = parsedOperations;
    }

    public int prefixLen() {
        return prefixLen;
    }

    public int compressBits() {
        return compressBits;
    }

    public int size() {
        return size;
    }

    public boolean containsHash(PrefixHash hash) {
        return hashes.contains(hash);
    }

    public PrefixHash hash(int[] perm) {
        if (compressBits <= 64) {
            return new PrefixHash(StateHash.encode(perm, prefixLen, nElements), 0L);
        }
        long[] pair = StateHash.encodePair(perm, prefixLen, nElements);
        return new PrefixHash(pair[0], pair[1]);
    }

    public PrefixHash hash(short[] perm) {
        if (compressBits <= 64) {
            return new PrefixHash(StateHash.encode(perm, prefixLen, nElements), 0L);
        }
        long[] pair = StateHash.encodePair(perm, prefixLen, nElements);
        return new PrefixHash(pair[0], pair[1]);
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
        if (depth % KEYFRAME_INTERVAL == 0) {
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
