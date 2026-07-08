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
        long newCap = Math.max(minCapacity, (long) parentGen.length * 2);
        if (newCap > Integer.MAX_VALUE - 8) {
            throw new IllegalStateException("parentGen capacity exceeded: " + minCapacity);
        }
        parentGen = Arrays.copyOf(parentGen, (int) newCap);
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
        /** Slots per chunk; keep {@code SLOTS_PER_CHUNK * numLongs <= Integer.MAX_VALUE}. */
        static final int CHUNK_SHIFT = 26;
        static final int SLOTS_PER_CHUNK = 1 << CHUNK_SHIFT;
        static final int CHUNK_MASK = SLOTS_PER_CHUNK - 1;

        private long[][] chunks;
        private int numChunks;
        private final int numLongs;
        private int capacity;
        private int mask;
        private int size;

        FlatLongHashSet(int numLongs, int initialCapacity) {
            this.numLongs = numLongs;
            if ((long) SLOTS_PER_CHUNK * numLongs > Integer.MAX_VALUE - 8) {
                throw new IllegalArgumentException("numLongs too large for chunked table");
            }
            int chunksNeeded = Math.max(1, roundUpChunks(initialCapacity));
            initChunks(chunksNeeded);
        }

        int size() {
            return size;
        }

        void clear() {
            for (int c = 0; c < numChunks; c++) {
                Arrays.fill(chunks[c], EMPTY_SLOT);
            }
            size = 0;
        }

        boolean contains(long[] key) {
            return findSlot(key) >= 0;
        }

        boolean add(long[] key) {
            if (size * 4 >= capacity * 3) {
                grow();
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

        private void grow() {
            if (numChunks >= maxChunks()) {
                throw new IllegalStateException(
                    "Hash table capacity exceeded at " + size + " entries (max slots "
                    + ((long) numChunks * SLOTS_PER_CHUNK) + ")");
            }
            rehash(numChunks << 1);
        }

        private int maxChunks() {
            return Integer.MAX_VALUE / (SLOTS_PER_CHUNK * numLongs);
        }

        private static int roundUpChunks(int minSlots) {
            int slots = Integer.highestOneBit(Math.max(SLOTS_PER_CHUNK, minSlots));
            if (slots < minSlots) {
                slots <<= 1;
            }
            return slots / SLOTS_PER_CHUNK;
        }

        private void initChunks(int newNumChunks) {
            numChunks = newNumChunks;
            capacity = numChunks * SLOTS_PER_CHUNK;
            mask = capacity - 1;
            chunks = new long[numChunks][];
            for (int c = 0; c < numChunks; c++) {
                chunks[c] = new long[SLOTS_PER_CHUNK * numLongs];
            }
            size = 0;
        }

        private void rehash(int newNumChunks) {
            long[][] old = chunks;
            int oldNumChunks = numChunks;
            initChunks(newNumChunks);
            for (int c = 0; c < oldNumChunks; c++) {
                long[] chunk = old[c];
                for (int s = 0; s < SLOTS_PER_CHUNK; s++) {
                    if (chunk[s * numLongs] != EMPTY_SLOT) {
                        long[] key = readKey(chunk, s, new long[numLongs]);
                        int dest = -findInsertSlot(key) - 1;
                        copyKey(key, dest);
                        size++;
                    }
                }
            }
        }

        private int findSlot(long[] key) {
            int idx = hashIndex(key) & mask;
            while (true) {
                if (isEmpty(idx)) {
                    return -1;
                }
                if (matches(idx, key)) {
                    return idx;
                }
                idx = (idx + 1) & mask;
            }
        }

        /** Returns slot index if present, otherwise {@code -(insertSlot + 1)}. */
        private int findInsertSlot(long[] key) {
            int idx = hashIndex(key) & mask;
            while (true) {
                if (isEmpty(idx)) {
                    return -(idx + 1);
                }
                if (matches(idx, key)) {
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

        private boolean isEmpty(int slot) {
            return slotLong(slot, 0) == EMPTY_SLOT;
        }

        private boolean matches(int slot, long[] key) {
            for (int i = 0; i < numLongs; i++) {
                if (slotLong(slot, i) != key[i]) {
                    return false;
                }
            }
            return true;
        }

        private long slotLong(int slot, int limb) {
            int chunk = slot >>> CHUNK_SHIFT;
            int index = (slot & CHUNK_MASK) * numLongs + limb;
            return chunks[chunk][index];
        }

        private void copyKey(long[] key, int slot) {
            int chunk = slot >>> CHUNK_SHIFT;
            int base = (slot & CHUNK_MASK) * numLongs;
            System.arraycopy(key, 0, chunks[chunk], base, numLongs);
        }

        private static long[] readKey(long[] chunk, int slot, long[] dest) {
            System.arraycopy(chunk, slot * dest.length, dest, 0, dest.length);
            return dest;
        }
    }
}
