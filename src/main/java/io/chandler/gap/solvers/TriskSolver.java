package io.chandler.gap.solvers;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.GroupExplorer.PeekData;
import io.chandler.gap.cache.State;
import javafx.util.Pair;

public class TriskSolver {

	final boolean multithread = false;
	
	String[] l3_3 = {
		"(3,4)(1,2)(13,5)(10,11)",
		"(7,8)(5,6)(13,9)(2,3)",
		"(11,12)(9,10)(13,1)(6,7)"
	};

	String l3_3_gen = "["+l3_3[0]+","+l3_3[1]+","+l3_3[2]+"]";

	String[] rgb = {"","",""};
	int s = 12;
	int n = s*3;
	int o = 14;
	HashMap<State, Integer> linMap = new HashMap<>();
	HashMap<State, Integer> map = new HashMap<>();

	State[] linIntToState;
	State[] intToState;

	public static void main(String[] args) {
		TriskSolver solver = new TriskSolver();
		solver.triskellion_l3_3();
	}

    public void triskellion_l3_3() {
		final boolean SKIP_DEPTH = true;
        for (int i = 0; i < 3; i++) {
            int j = s*i;
            rgb[(i+0)%3] += "("+(o+(j+0)%n)+","+(o+(j+1)%n)+")";
            rgb[(i+0)%3] += "("+(o+(j+2)%n)+","+(o+(j+3)%n)+")";
            rgb[(i+0)%3] += "("+(o+(j+6)%n)+","+(o+(j+7)%n)+")";
            rgb[(i+0)%3] += "("+(o+(j+8)%n)+","+(o+(j+9)%n)+")";
            rgb[(i+1)%3] += "("+(o+(j+3)%n)+","+(o+(j+4)%n)+")";
            rgb[(i+1)%3] += "("+(o+(j+5)%n)+","+(o+(j+6)%n)+")";
            rgb[(i+2)%3] += "("+(o+(j+9)%n)+","+(o+(j+10)%n)+")";
            rgb[(i+2)%3] += "("+(o+(j+11)%n)+","+(o+(j+s+10)%n)+")";
        }

        GroupExplorer cacheHelper = new GroupExplorer(l3_3_gen, MemorySettings.FASTEST);
        int[] x = new int[2];
        // Add solved state
        linMap.put(State.of(cacheHelper.copyCurrentState(), 13, MemorySettings.FASTEST), x[0]++);
        cacheHelper.exploreStates(false, (states, depth) -> {
            for (int[] state : states) {
                State statesm = State.of(state, 13, MemorySettings.FASTEST);
                linMap.put(statesm, x[0]++);
            }
        });
        this.linIntToState = new State[linMap.size()];
        for (State state : linMap.keySet()) {
            linIntToState[linMap.get(state)] = state;
        }

        cacheHelper = new GroupExplorer("["+rgb[0]+","+rgb[1]+","+rgb[2]+"]", MemorySettings.FASTEST);
        // Add solved state
        // Copy after 13th element
        int[] stateCopySolved = Arrays.copyOfRange(cacheHelper.copyCurrentState(), 13, cacheHelper.copyCurrentState().length);
        // Subtract 13 from each element
        for (int i = 0; i < stateCopySolved.length; i++) {
            stateCopySolved[i] -= 13;
        }
        map.put(State.of(stateCopySolved, n+o, MemorySettings.FASTEST), x[1]++);
        cacheHelper.exploreStates(false, (states, depth) -> {
            for (int[] state : states) {
                // Copy 
                int[] stateCopy = Arrays.copyOfRange(state, 13, state.length);
                for (int i = 0; i < stateCopy.length; i++) {
                    stateCopy[i] -= 13;
                }
                State statesm = State.of(stateCopy, n+o, MemorySettings.FASTEST);
                map.put(statesm, x[1]++);
                if (map.size() == 10) for (State ss : map.keySet()) {
                    System.out.println(Arrays.toString(ss.state()));
                }
            }
        });
        this.intToState = new State[map.size()];
        for (State state : map.keySet()) {
            intToState[map.get(state)] = state;
        }

        System.out.println("Cached " + linMap.size() + " states");
        System.out.println("Cached " + map.size() + " states");


        String trisk = "["+l3_3[0]+rgb[0]+","+l3_3[1]+rgb[1]+","+l3_3[2]+rgb[2]+"]";

        System.out.println(trisk);
        //int[] stateMatch = { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13};   0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        int[] stateMatch =   { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 13,    14,15,16,17,18,19,20,21,22,23,24,25,  26,27,28,29,30,31,32,33,34,35,36,37,  38,39,40,41,42,43,44,45,46,47,48,49};

        System.out.println(stateMatch.length);
        int matchNStates = 70;

        int order = 698_720_256; //5616;
        Integer maxDepth = 36;

        if (!SKIP_DEPTH && (maxDepth == null || System.getProperty("debug") != null)) {
            GroupExplorer groudp = new GroupExplorer(trisk, MemorySettings.FASTEST, new TriskStateCache(), new TriskStateCache(), new TriskStateCache(), multithread);
			groudp.setMaxPeekSize(100_000);
			int lastDepth[] = {0};
			int statesCount[] = {1};
			BiConsumer<List<int[]>, Integer> peekStateAndDepth = (states, depth) -> {
				if (depth > lastDepth[0]) {
					System.out.println("Depth " + lastDepth[0] + ": " + statesCount[0] + " states");
					lastDepth[0] = depth;
					statesCount[0] = 0;
				}
				statesCount[0] += states.size();
			};
            groudp.exploreStates(false, peekStateAndDepth);
			peekStateAndDepth.accept(new ArrayList<>(), groudp.getIteration());
			peekStateAndDepth.accept(new ArrayList<>(), groudp.getIteration()+1);
            System.out.println("Order: " + groudp.order());
			System.out.println("Depth: " + groudp.getIteration());
        }
        drawers_analysis(trisk, order, maxDepth, stateMatch, matchNStates, () -> new TriskStateCache());

    }


    public static void drawers_analysis(String generator, int order, Integer maxDepth, int[] stateMatch, int matchNStates, Supplier<TriskStateCache> cache) {

        System.out.println("Order: " + order);
        System.out.println("Max depth: " + maxDepth);

        String[] namesLookup = new String[] {
            "A", "B", "C"
        };

        // 2-cycles; no inverse moves needed


        GroupExplorer group = new GroupExplorer(generator,
            MemorySettings.FASTEST,
            cache.get(), cache.get(), cache.get(), false);

        group.setTrackPath(true);
        group.initIterativeExploration();

        ArrayList<int[]> matchingStates = new ArrayList<>();

        System.out.println(Arrays.toString(stateMatch));
        System.out.println(Arrays.toString(group.copyCurrentState()));

        HashMap<State, Pair<State, Integer>> backtrack = new HashMap<>();

        while (matchingStates.size() < matchNStates) {

            group.iterateExploration(false, order+1, true, (states, depth) -> {
                for (Object x : states) {
                    PeekData data = (PeekData) x;
                    backtrack.put(data.newState, new Pair<>(data.oldState, data.operation));
                    int[] state = data.newState.state();
                    boolean matches = true;
                    for (int i = 0; i < state.length; i++) {
                        if (stateMatch[i] != 0 && state[i] != stateMatch[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {

                        System.out.println(Arrays.toString(state));
                        matchingStates.add(state);
                        System.out.println(GroupExplorer.describeState(13, state));
                        System.out.println(GroupExplorer.stateToNotation(state));
                        // Figure out path
                        State current = State.of(state, group.nElements, group.mem);
                        String op = "";
                        String inverseOp = "";
                        while (backtrack.containsKey(current)) {
                            Pair<State, Integer> d = backtrack.get(current);
                            op = namesLookup[d.getValue()] + " " + op;
                            inverseOp = inverseOp + " " + namesLookup[d.getValue()];
                            current = d.getKey();
                        }
                        System.out.println("Fwd: " + op);
                        System.out.println("Inv: " + inverseOp);
                        System.out.println(Arrays.toString(state));
                    }
                }
            });
            System.out.println(group.getIteration() + " " + group.order());
            if (maxDepth != null && group.getIteration() > maxDepth) {
                System.out.println("Max depth reached");
                return;
            }
        }


    }


	class TriskStateCache extends AbstractSet<State> {

		int[] cache = new int[536870912 / 4]; // Full uint32 cache
		long max_value = (long) 536870912L * 8L;
		long size = 0;

		// Reusable probe buffers and wrappers to avoid per-call allocations
		private final int[] probeState1Arr = new int[13];
		private final State probeState1 = State.of(probeState1Arr, 13, MemorySettings.FASTEST);
		private final int tailLength = intToState[0].state().length; // constant for this problem
		private final int[] probeState2Arr = new int[tailLength];
		private final State probeState2 = State.of(probeState2Arr, n+o, MemorySettings.FASTEST);
		private final int mapSizeCached = map.size();

		@Override
		public boolean addAll(Collection<? extends State> arg0) {
			if (arg0 instanceof TriskStateCache) {
				TriskStateCache other = (TriskStateCache) arg0;
				// Merge the cache into this one
				for (int i = 0; i < cache.length; i++) {
					this.size -= Long.bitCount(cache[i] & 0xFFFFFFFFL);
					cache[i] |= other.cache[i];
					this.size += Long.bitCount(cache[i] & 0xFFFFFFFFL);
				}
				return true;
			}
			return super.addAll(arg0);
		}

		private int cvt(State state) {
			final int[] src = state.state();
			// Copy first 13 elements directly
			System.arraycopy(src, 0, probeState1Arr, 0, 13);
			// Copy tail with -13 transform
			for (int i = 0, s = 13; i < tailLength; i++, s++) {
				probeState2Arr[i] = src[s] - 13;
			}
			// Lookups using reusable probe states
			final Integer state1Id = linMap.get(probeState1);
			if (state1Id == null) {
				throw new IllegalArgumentException("State1Id is null: " + Arrays.toString(probeState1Arr));
			}
			final Integer state2Id = map.get(probeState2);
			if (state2Id == null) {
				throw new IllegalArgumentException("State2Id is null: " + Arrays.toString(probeState2Arr));
			}
			final long stored = (long) state1Id * (long) mapSizeCached + (long) state2Id;
			if (stored > max_value) {
				throw new IllegalArgumentException("Stored value is greater than max value: " + stored + " > " + max_value);
			}
			return (int) stored;
		}

		private boolean isBitSet(int index) {
			return (cache[index / 32] & (1 << (index % 32))) != 0;
		}

		private void setBit(int index) {
			cache[index / 32] |= (1 << (index % 32));
		}

		@Override
		public boolean add(State state) {
			int s = cvt(state);
			boolean added = !isBitSet(s);
			if (added) {
				setBit(s);
				size++;
			}
			return added;
		}

		@Override
		public boolean contains(Object o) {
			return isBitSet(cvt(((State)o)));
		}

		@Override
		public Iterator<State> iterator() {
			return new Iterator<State>() {
				int i = 0;
				int counted = 0;
				@Override
				public boolean hasNext() { return counted < size; }
				@Override
				public State next() { 
					if (!hasNext()) return null;
					int stored = i++;
					while (!isBitSet(stored)) {
						stored = i++;
					}
					counted++;
					int state1Id = stored / map.size();
					int state2Id = stored % map.size();
					int[] state1 = linIntToState[state1Id].state();
					int[] state2 = intToState[state2Id].state().clone();
					// add 13 to each element of state2
					for (int j = 0; j < state2.length; j++) {
						state2[j] += 13;
					}
					// Concat
					int[] state = new int[state1.length + state2.length];
					System.arraycopy(state1, 0, state, 0, state1.length);
					System.arraycopy(state2, 0, state, state1.length, state2.length);
					return State.of(state, n+o, MemorySettings.FASTEST);
				}
				@Override public void remove() { throw new UnsupportedOperationException("Not implemented"); }
			};
		}

		@Override
		public void clear() { Arrays.fill(cache, (byte)0); size = 0; }



		@Override public int size() { return (int) size; }

	}

}


