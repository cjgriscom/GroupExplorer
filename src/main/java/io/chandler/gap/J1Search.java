package io.chandler.gap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import io.chandler.gap.GroupExplorer.MemorySettings;

public class J1Search {
	public static void main(String[] args) {
		ArrayList<int[]> sevenCycles = new ArrayList<>();
		ArrayList<int[]> nineteenCycles = new ArrayList<>();
		GroupExplorer g = new GroupExplorer(Generators.j1, MemorySettings.FASTEST, new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
		Generators.exploreGroup(g, (state, description) -> {
			if (description.equals("38p 7-cycles")) {
				sevenCycles.add(state);
			} else if (description.equals("14p 19-cycles")) {
				nineteenCycles.add(state);
			}
		});

		int[][] g0 = GroupExplorer.stateToCycles(nineteenCycles.get(3));
		int[][] g1 = GroupExplorer.stateToCycles(sevenCycles.get(0));
		int[][][] combined = new int[][][]{g0, g1};
		String newGenString = GroupExplorer.generatorsToString(combined);
		newGenString = GroupExplorer.renumberGeneratorNotation(newGenString);
		System.out.println(newGenString);
		TreeMap<String, Integer> uniqueSectionsCounter = new TreeMap<>();
		GroupExplorer g2 = new GroupExplorer(newGenString, MemorySettings.FASTEST, new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
		Generators.exploreGroup(g2, (state, description) -> {
			if (description.equals("38p 7-cycles")) {
				int[][] cycles = GroupExplorer.stateToCycles(state);

				int[][] cyclesClone = new int[cycles.length][];
				for (int i = 0; i < cycles.length; i++) {
					cyclesClone[i] = Arrays.copyOf(cycles[i], cycles[i].length);
					for (int j = 0; j < cyclesClone[i].length; j++) {
						cyclesClone[i][j] = (cyclesClone[i][j] - 1) % 19 + 1;
					}
				}

				System.out.println(GroupExplorer.cyclesToNotation(cyclesClone));

				int[] uniqueSections = new int[14];
				for (int[] cycle : cycles) {
					for (int element : cycle) {
						uniqueSections[(element-1) / 19]++;
					}
				}
				Arrays.sort(uniqueSections);
				String uniqueSectionsString = Arrays.toString(uniqueSections);
				uniqueSectionsCounter.put(uniqueSectionsString, uniqueSectionsCounter.getOrDefault(uniqueSectionsString, 0) + 1);

			}
		});

		for (Map.Entry<String, Integer> entry : uniqueSectionsCounter.entrySet()) {
			System.out.println(entry.getKey() + " " + entry.getValue());
		}
	}
}
