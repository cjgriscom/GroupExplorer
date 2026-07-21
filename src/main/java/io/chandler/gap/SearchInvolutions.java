package io.chandler.gap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class SearchInvolutions {
	public static void main(String[] args) throws FileNotFoundException {
		Scanner in = new Scanner(new BufferedReader(new FileReader(new File("/home/cjgriscom/Programming/GroupExplorer/PlanarStudy/sp_8_2_120/2-cycles.txt"))));
		
		List<int[]> searchPairs = new ArrayList<>();
		String pairs = "[(12,79)(20,32)(29,76)(40,81)(44,100)(53,36)(66,2)(97,33)(112,101)(116,96)(118,90)]";
		int[][] pairsArr = GroupExplorer.parseOperations(pairs).get(0);
		for (int[] pair : pairsArr) {
			searchPairs.add(pair);
		}

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
