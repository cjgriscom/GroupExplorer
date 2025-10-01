package io.chandler.gap;

import java.io.File;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.TreeMap;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.State;

public class M13Graphs {
	public static void main(String[] args) throws Exception {
		Scanner in = new Scanner(new File("M13"));
		// 6 HJAIKGCEFBDML IECGFL

		HashSet<State> m13states = new HashSet<>();

		TreeMap<String, ArrayList<int[]>> statesDescribed = new TreeMap<>();
		while (in.hasNextLine()) {
			String line = in.nextLine();
			String[] parts = line.split(" ");
			String permu = parts[1];
			
			int i = 0;
			int[] state = new int[13];
			for (char c : permu.toCharArray()) {
				state[i++] = c - 'A' + 1;
			}
			m13states.add(State.of(state, 13, MemorySettings.FASTEST));
			String desc = GroupExplorer.describeState(13, state);
			if (statesDescribed.containsKey(desc)) {
				statesDescribed.get(desc).add(state);
			} else {
				ArrayList<int[]> list = new ArrayList<>();
				list.add(state);
				statesDescribed.put(desc, list);
			}
		}

		System.out.println("Sorted by name:");
		TreeMap<Integer, String> statesSorted = new TreeMap<>();
		for (String desc : statesDescribed.keySet()) {
			ArrayList<int[]> states = statesDescribed.get(desc);
			statesSorted.put(states.size(), desc);
			System.out.println(desc + " " + states.size());
		}
		System.out.println();
		System.out.println("Sorted by number of states:");

		int total = 0;
		for (Integer size : statesSorted.keySet()) {
			total += size;
			System.out.println(size + " " + statesSorted.get(size));
		}
		System.out.println("Total: " + total);

		class M13StateCache extends AbstractSet<State> {
			private final HashSet<State> map = new HashSet<>();

			@Override
			public boolean add(State state) {
				if (!m13states.contains(state)) {
					return false;
				}
				return map.add(state);
			}

			@Override
			public Iterator<State> iterator() {
				return map.iterator();
			}

			@Override
			public int size() {
				return map.size();
			}
		}

		for (int[] cycles1 : statesDescribed.get("single 13-cycle")) {
			for (int[] cycles2 : statesDescribed.get("single 13-cycle")) {
				if (cycles1 == cycles2) continue;
				for (int[] cycles3 : statesDescribed.get("single 13-cycle")) {
					if (cycles1 == cycles3 || cycles2 == cycles3) continue;

					Generator g = new Generator(new int[][][]{
						CycleInverter.invertArray(GroupExplorer.stateToCycles(cycles1)),
						CycleInverter.invertArray(GroupExplorer.stateToCycles(cycles2)), 
						CycleInverter.invertArray(GroupExplorer.stateToCycles(cycles3))});

					GroupExplorer ge = new GroupExplorer(g, 13, MemorySettings.FASTEST, new M13StateCache(), new M13StateCache(), new M13StateCache(), false);

					ge.exploreStates(false, null);
					System.out.println(ge.order());
				}
			}
		}
	}
}
