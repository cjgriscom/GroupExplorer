package io.chandler.gap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.State;

public class Undectrix {
	public static void main(String[] args) {
		// Check for indistinguishable color states
		
		String gen = "[(1,13,24)(37,47,42)(60,64,66),(1,60)(13,37)(24,54)(64,70)]";

		int[][][] g = GroupExplorer.parseOperationsArr(gen);

		int[][][] gRenumbered = GroupExplorer.renumberGenerators(g);

		HashMap<Integer, Integer> renumberedMap = new HashMap<>();
		for (int i = 0; i < gRenumbered.length; i++) {
			for (int j = 0; j < gRenumbered[i].length; j++) {
				for (int k = 0; k < gRenumbered[i][j].length; k++) {
					renumberedMap.put(gRenumbered[i][j][k], g[i][j][k]);
				}
			}
		}

		HashMap<Integer, String> colorMap = new HashMap<>();
		colorMap.put(24, "G");
		colorMap.put(42, "Pi");
		colorMap.put(60, "Pi");
		colorMap.put(54, "Y");
		colorMap.put(70, "O");
		colorMap.put(13, "O");
		colorMap.put(47, "B");
		colorMap.put(66, "B");
		colorMap.put(37, "R");
		colorMap.put(64, "R");
		colorMap.put(1, "Pu");

		HashSet<State> states = new HashSet<>();

		GroupExplorer ge = new GroupExplorer(GroupExplorer.generatorsToString(gRenumbered), MemorySettings.COMPACT, states, new HashSet<>(), new HashSet<>(), true);
		
		Generators.exploreGroup(ge, (state, description) -> {
		});

		String[] colors = new String[ge.nElements];
		for (int i = 0; i < ge.nElements; i++) {
			colors[i] = colorMap.get(renumberedMap.get(i+1));
		}

		System.out.println(Arrays.toString(colors));


		for (State state : states) {
			int[] arr = state.state();
			String[] mapped = new String[arr.length];
			for (int i = 0; i < arr.length; i++) {
				mapped[i] = colorMap.get(renumberedMap.get(arr[i]));
			}

			//System.out.println(Arrays.toString(mapped));
			if (Arrays.equals(mapped, colors)) {
				System.out.println("Match found");
				System.out.println(Arrays.toString(arr));
			}
			
		}
	}
}
