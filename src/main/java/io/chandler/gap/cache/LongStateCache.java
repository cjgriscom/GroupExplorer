package io.chandler.gap.cache;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;

public class LongStateCache extends AbstractSet<State> {
    private final HashSet<Long> map;

    final long elementsToStore;
    final int nElements;

    public LongStateCache(long elementsToStore, int nElements) {
        this.map = new HashSet<>();
        this.elementsToStore = elementsToStore;
        this.nElements = nElements;
    }

    long cvt(int[] state) {
        long value = 0;
        int x = nElements + 1;
        for (int i = 0; i < elementsToStore && i < state.length; i++) {
            value = value * x + (state[i]);
            //x--;
        }
        return value;
    }

    @Override
    public boolean add(State state) {
        int[] s = state.state();

        return map.add(cvt(s));
    }

    @Override
    public boolean contains(Object o) {
        return map.contains(cvt(((State)o).state()));
    }

    @Override
    public boolean remove(Object o) {
        return map.remove(cvt(((State)o).state()));
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public void clear() {
        map.clear();
    }

	@Override
    public Iterator<State> iterator() {
        // Since the full state is not stored we can't retrieve the original states
        throw new UnsupportedOperationException("Not implemented");
    }
}
