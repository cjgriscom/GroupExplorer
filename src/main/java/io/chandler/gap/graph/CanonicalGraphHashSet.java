package io.chandler.gap.graph;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Memory- and serialization-compact set of dreadnaut {@code z} certificates.
 * <p>
 * Backed by fastutil's open hash set (no per-entry Node objects). On the wire,
 * entries are stored as a count plus {@link CanonicalGraphHash#PACKED_BYTES}-byte
 * records instead of Java Strings.
 * <p>
 * Public mutators/queries are synchronized so the set is safe as a shared iso
 * cache under parallel Phase 1/2 workers (fastutil's open hash set is not).
 */
public final class CanonicalGraphHashSet implements Serializable, Iterable<CanonicalGraphHash> {
    private static final long serialVersionUID = 1L;

    private transient ObjectOpenHashSet<CanonicalGraphHash> set;

    public CanonicalGraphHashSet() {
        this(16);
    }

    public CanonicalGraphHashSet(int expectedSize) {
        this.set = new ObjectOpenHashSet<>(Math.max(16, expectedSize));
    }

    public CanonicalGraphHashSet(Collection<CanonicalGraphHash> values) {
        this(values.size());
        addAll(values);
    }

    public synchronized CanonicalGraphHashSet copy() {
        CanonicalGraphHashSet out = new CanonicalGraphHashSet(size());
        out.set.addAll(this.set);
        return out;
    }

    public synchronized int size() {
        return set.size();
    }

    public synchronized boolean isEmpty() {
        return set.isEmpty();
    }

    public synchronized void clear() {
        set.clear();
    }

    public synchronized boolean add(CanonicalGraphHash hash) {
        return set.add(hash);
    }

    /** Parse a dreadnaut {@code z} string and insert it. */
    public boolean add(String dreadnautZ) {
        return add(CanonicalGraphHash.parse(dreadnautZ));
    }

    public synchronized boolean contains(CanonicalGraphHash hash) {
        return set.contains(hash);
    }

    public boolean contains(String dreadnautZ) {
        return contains(CanonicalGraphHash.parse(dreadnautZ));
    }

    public synchronized boolean addAll(Collection<CanonicalGraphHash> values) {
        boolean changed = false;
        for (CanonicalGraphHash h : values) {
            changed |= set.add(h);
        }
        return changed;
    }

    public boolean addAll(CanonicalGraphHashSet other) {
        // Snapshot under other's lock, then insert under ours — avoids deadlock and
        // concurrent mutation of either backing set.
        CanonicalGraphHash[] snapshot;
        synchronized (other) {
            snapshot = other.set.toArray(new CanonicalGraphHash[0]);
        }
        synchronized (this) {
            boolean changed = false;
            for (CanonicalGraphHash h : snapshot) {
                changed |= set.add(h);
            }
            return changed;
        }
    }

    /**
     * Iterator over a live view; not safe for concurrent mutation.
     * Prefer {@link #copy()} when a stable snapshot is needed.
     */
    @Override
    public synchronized Iterator<CanonicalGraphHash> iterator() {
        return set.iterator();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        CanonicalGraphHash[] snapshot;
        synchronized (this) {
            snapshot = set.toArray(new CanonicalGraphHash[0]);
        }
        out.writeInt(snapshot.length);
        for (CanonicalGraphHash h : snapshot) {
            h.writePacked(out);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int n = in.readInt();
        if (n < 0) {
            throw new IOException("Negative canonical hash count: " + n);
        }
        set = new ObjectOpenHashSet<>(Math.max(16, n));
        for (int i = 0; i < n; i++) {
            set.add(CanonicalGraphHash.readPacked(in));
        }
    }
}
