package io.chandler.gap.cache;

import java.util.Arrays;
import java.util.List;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.cache.KeyframeStateCache.PrefixHash;
import io.chandler.gap.weyl.WeylE8Antipodes;
import io.chandler.gap.weyl.WeylE8Quotient;
import io.chandler.gap.weyl.WeylE8Quotient.Encoding;

/**
 * Compact visited-set for Weyl(E8): antipodal quotient hashing, flat primitive hash
 * table, packed parent/generator links, and no keyframe snapshots.
 */
public final class WeylE8StateCache implements CompressStateCache {

    private static final int GEN_MASK = 0xf;
    private static final long EMPTY_SLOT = 0L;

    private final int prefixLen;
    private final int numLongs;
    private final Encoding encoding;
    private final List<int[][]> parsedOperations;

    private FlatLongHashSet hashes;
    private long[] parentGen = new long[16];
    private int size = 0;

    public WeylE8StateCache(int numLongs, List<int[][]> parsedOperations) {
        this(numLongs, parsedOperations, Encoding.PAIR_ONLY);
    }

    public WeylE8StateCache(int numLongs, List<int[][]> parsedOperations, Encoding encoding) {
        if (numLongs < 1) {
            throw new IllegalArgumentException("numLongs must be >= 1");
        }
        this.numLongs = numLongs;
        this.encoding = encoding;
        int domain = WeylE8Quotient.hashDomainSize(encoding);
        this.prefixLen = StateHash.derivePrefixLength(domain, numLongs * 64);
        this.parsedOperations = parsedOperations;
        this.hashes = new FlatLongHashSet(numLongs, 1 << 20);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int nElements() {
        return WeylE8Antipodes.NUM_POINTS;
    }

    @Override
    public int prefixLen() {
        return prefixLen;
    }

    @Override
    public int numLongs() {
        return numLongs;
    }

    @Override
    public boolean keyframesEnabled() {
        return false;
    }

    @Override
    public List<int[][]> parsedOperations() {
        return parsedOperations;
    }

    @Override
    public PrefixHash hash(int[] perm) {
        return new PrefixHash(WeylE8Quotient.hashLimbs(perm, prefixLen, numLongs, encoding, null));
    }

    @Override
    public boolean containsHash(PrefixHash hash) {
        return hashes.contains(hash.parts);
    }

    @Override
    public int registerRoot(int[] perm) {
        if (size != 0) {
            throw new IllegalStateException("Root already registered");
        }
        PrefixHash h = hash(perm);
        if (!hashes.add(h.parts)) {
            throw new IllegalStateException("Duplicate root hash");
        }
        ensureCapacity(1);
        parentGen[0] = packParentGen(-1, 0);
        size = 1;
        return 0;
    }

    @Override
    public int tryAdd(int parentStateId, byte gen, int[] perm, int depth) {
        long[] limbs = WeylE8Quotient.hashLimbs(perm, prefixLen, numLongs, encoding, null);
        if (!hashes.add(limbs)) {
            return -1;
        }
        int id = size++;
        ensureCapacity(id + 1);
        parentGen[id] = packParentGen(parentStateId, gen);
        return id;
    }

    @Override
    public int[] reconstruct(int stateId) {
        int[] path = tracePath(stateId);
        int[] state = new int[WeylE8Antipodes.NUM_POINTS];
        for (int i = 0; i < state.length; i++) {
            state[i] = i + 1;
        }
        for (int g : path) {
            state = GroupExplorer.applyOperation(state, parsedOperations.get(g));
        }
        return state;
    }

    @Override
    public int[] tracePath(int stateId) {
        if (stateId < 0 || stateId >= size) {
            throw new IllegalArgumentException("Invalid state id: " + stateId);
        }
        int depth = 0;
        for (int id = stateId; parentOf(id) != -1; id = parentOf(id)) {
            depth++;
        }
        int[] result = new int[depth];
        int id = stateId;
        for (int i = depth - 1; i >= 0; i--) {
            result[i] = genOf(id);
            id = parentOf(id);
        }
        return result;
    }

    @Override
    public void clear() {
        hashes.clear();
        size = 0;
    }

    static long packParentGen(int parentId, int gen) {
        if (parentId < 0) {
            return -1L;
        }
        return ((long) parentId << 4) | (gen & GEN_MASK);
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= parentGen.length) {
            return;
        }
        int newCap = Math.max(minCapacity, parentGen.length * 2);
        parentGen = Arrays.copyOf(parentGen, newCap);
    }

    private int parentOf(int id) {
        long packed = parentGen[id];
        if (packed < 0) {
            return -1;
        }
        return (int) (packed >>> 4);
    }

    private int genOf(int id) {
        return (int) (parentGen[id] & GEN_MASK);
    }

    /** Open-addressing set storing {@code numLongs} consecutive longs per slot. */
    static final class FlatLongHashSet {
        private long[] table;
        private final int numLongs;
        private int capacity;
        private int mask;
        private int size;

        FlatLongHashSet(int numLongs, int initialCapacity) {
            this.numLongs = numLongs;
            resize(Integer.highestOneBit(Math.max(16, initialCapacity)));
        }

        int size() {
            return size;
        }

        void clear() {
            Arrays.fill(table, EMPTY_SLOT);
            size = 0;
        }

        boolean contains(long[] key) {
            return findSlot(key) >= 0;
        }

        boolean add(long[] key) {
            if (size * 4 >= capacity * 3) {
                rehash(capacity << 1);
            }
            int slot = findInsertSlot(key);
            if (slot >= 0) {
                return false;
            }
            slot = -slot - 1;
            copyKey(key, slot);
            size++;
            return true;
        }

        private void rehash(int newCapacity) {
            long[] old = table;
            int oldCapacity = capacity;
            resize(newCapacity);
            for (int slot = 0; slot < oldCapacity; slot++) {
                if (!isEmpty(old, slot)) {
                    long[] key = readKey(old, slot, new long[numLongs]);
                    int dest = -findInsertSlot(key) - 1;
                    copyKey(key, dest);
                    size++;
                }
            }
        }

        private void resize(int newCapacity) {
            capacity = newCapacity;
            mask = capacity - 1;
            table = new long[capacity * numLongs];
            size = 0;
        }

        private int findSlot(long[] key) {
            int idx = hashIndex(key) & mask;
            while (true) {
                if (isEmpty(table, idx)) {
                    return -1;
                }
                if (matches(table, idx, key)) {
                    return idx;
                }
                idx = (idx + 1) & mask;
            }
        }

        /** Returns slot index if present, otherwise {@code -(insertSlot + 1)}. */
        private int findInsertSlot(long[] key) {
            int idx = hashIndex(key) & mask;
            int firstDeleted = -1;
            while (true) {
                if (isEmpty(table, idx)) {
                    return firstDeleted >= 0 ? -(firstDeleted + 1) : -(idx + 1);
                }
                if (matches(table, idx, key)) {
                    return idx;
                }
                idx = (idx + 1) & mask;
            }
        }

        private static int hashIndex(long[] key) {
            long h = key[0];
            for (int i = 1; i < key.length; i++) {
                h = h * 31 + key[i];
            }
            return (int) (h ^ (h >>> 32));
        }

        private boolean isEmpty(long[] arr, int slot) {
            return arr[slot * numLongs] == EMPTY_SLOT;
        }

        private boolean matches(long[] arr, int slot, long[] key) {
            int base = slot * numLongs;
            for (int i = 0; i < numLongs; i++) {
                if (arr[base + i] != key[i]) {
                    return false;
                }
            }
            return true;
        }

        private void copyKey(long[] key, int slot) {
            int base = slot * numLongs;
            System.arraycopy(key, 0, table, base, numLongs);
        }

        private long[] readKey(long[] arr, int slot, long[] dest) {
            System.arraycopy(arr, slot * numLongs, dest, 0, numLongs);
            return dest;
        }
    }
}
