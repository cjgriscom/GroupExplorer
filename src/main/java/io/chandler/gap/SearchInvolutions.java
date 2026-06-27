package io.chandler.gap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class SearchInvolutions {
	public static void main(String[] args) throws FileNotFoundException {
		Scanner in = new Scanner(new File("/home/cjgriscom/Programming/GroupExplorer/PlanarStudy/mcl_2/2-cycles.txt"));
		
		List<int[]> searchPairs = new ArrayList<>();
		searchPairs.add(new int[] {18, 180});
		searchPairs.add(new int[] {16, 15});
		searchPairs.add(new int[] {222, 94});

		// Match every line that contains all search pairs		

		int i = 0;
		while (in.hasNextLine()) {
			String line = in.nextLine();
			if (line.trim().isEmpty()) continue;
			int[][] cycles = GroupExplorer.parseOperationsArr(line)[0];
			i++;

			boolean[] matches = new boolean[searchPairs.size()];
			for (int[] cycle : cycles) {
				for (int j = 0; j < searchPairs.size(); j++) {
					int[] cycleReversed = new int[cycle.length];
					for (int k = 0; k < cycle.length; k++) {
						cycleReversed[k] = cycle[cycle.length - k - 1];
					}
					if (Arrays.equals(cycle, searchPairs.get(j)) || Arrays.equals(cycleReversed, searchPairs.get(j))) {
						matches[j] = true;
					}
				}
			}

			boolean allMatches = true;
			for (boolean match : matches) {
				if (!match) {
					allMatches = false;
					break;
				}
			}
			if (allMatches) {
				System.out.println(line);
			}
		}
	}
}
