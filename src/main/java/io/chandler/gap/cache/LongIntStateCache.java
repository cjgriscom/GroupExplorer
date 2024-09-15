package io.chandler.gap.cache;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;

// Not sure if this works
public class LongIntStateCache extends AbstractSet<State> {
    private final HashSet<LongInt> map;

    final int elementsToStore;
    final int nElements;

    public static class LongInt {
        public long l;
        public int i;

        @Override
        public int hashCode() {
            long h = 31L * l + i;
            h = (h ^ (h >>> 32)) * 0x517cc1b727220a95L;
            h = (h ^ (h >>> 32)) * 0x517cc1b727220a95L;
            h = h ^ (h >>> 32);
            return (int) h;
        }
        

        public boolean equals(Object o) {
            if (o instanceof LongInt) {
                LongInt li = (LongInt) o;
                return l == li.l && i == li.i;
            }
            return false;
        }
    }

    public LongIntStateCache(int elementsToStore, int nElements) {
        this.map = new HashSet<>();
        this.elementsToStore = elementsToStore;
        this.nElements = nElements;
    }

    LongInt cvt(int[] state) {
        long value = 0;
        long overflow = 0;
        long x = nElements + 1L;
        for (int i = 0; i < elementsToStore && i < state.length; i++) {
            long prevValue = value;
            value *= x;
            if (value / x != prevValue) {
                overflow = overflow * x + prevValue;
                value %= x;
            }
            value += state[i];
            if (value < 0) {
                overflow++;
                value &= Long.MAX_VALUE;
            }
        }
        LongInt result = new LongInt();
        result.l = value;
        result.i = (int) (overflow & 0x7FFFFFFF);
        return result;
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
