package io.chandler.gap;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

import io.chandler.gap.FullSelectionSearchResults.GeneratorEntry;
import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.render.ResultListParser;

public class FullAxisSubsetClassifier {
	public static void main(String[] args) throws Exception {
		//Scanner in = new Scanner(new File());


		FullSelectionSearchResults resultsSrc =
			FullSelectionSearchResults.parse(new FileReader("/home/cjgriscom/Programming/GroupTxt/"
			 +"Catalog of groups over geared shallow vertex turning dodecahedron.txt"));

		ArrayList<GeneratorEntry> allResults = new ArrayList<>();
		TreeMap<String, ArrayList<Integer>> allResultsMap = new TreeMap<>();
		Set<String> axisStructureSet = resultsSrc.getAxisStructureSet();
		System.out.println(axisStructureSet);
		for (String axisStructure : axisStructureSet) {
			for (GeneratorEntry entry : resultsSrc.getResultsByAxisStructure(axisStructure)) {
				int index = allResults.size();
				allResults.add(entry);
				allResultsMap.computeIfAbsent(axisStructure, k -> new ArrayList<>()).add(index);
			}
		}
		
		System.out.println("Total results: " + allResults.size());

		TreeMap<Integer, List<Integer>> subsetMap = new TreeMap<>();

		for (Entry<String, ArrayList<Integer>> entry : allResultsMap.entrySet()) {
			String axisStructure = entry.getKey();
			ArrayList<Integer> indices = entry.getValue();
			
			List<String> structureSubsets = new ArrayList<>();
			for (String structure : allResultsMap.keySet()) {
				int[] mainArray = Arrays.stream(axisStructure.replaceAll("[\\[\\]\\s]", "").split(","))
						.filter(s -> !s.isEmpty())
						.mapToInt(Integer::parseInt)
						.toArray();
				int[] candidateSubArray = Arrays.stream(structure.replaceAll("[\\[\\]\\s]", "").split(","))
						.filter(s -> !s.isEmpty())
						.mapToInt(Integer::parseInt)
						.toArray();
				
				if (isStructureSubset(mainArray, candidateSubArray)) {
					structureSubsets.add(structure);
				}
			}

			for (int mainIndex : indices) {
				GeneratorEntry mainEntry = allResults.get(mainIndex);
				Generator main = mainEntry.toGenerator();
				FullSelectionSearch search = FullSelectionSearch.getIcosahedralSearch(true, true);
				for (String structure : structureSubsets) {
					for (int subIndex : allResultsMap.get(structure)) {
						if (mainIndex == subIndex) {
							continue;
						}
						GeneratorEntry subEntry = allResults.get(subIndex);
						Generator sub = subEntry.toGenerator();
						int[] subIndices = search.asSubset(main, sub);
						if (subIndices != null) {
							subsetMap.computeIfAbsent(mainIndex, k -> new ArrayList<>()).add(subIndex);
						}
					}
				}
			}
		}

		// Now find all results that aren't subsets of any other result
		Set<Integer> allSubsets = new HashSet<>();
		for (List<Integer> subsets : subsetMap.values()) {
			allSubsets.addAll(subsets);
		}
		
		System.out.println("\nMaximal results (not subset of any other result):");
		int maximalResults = 0;
		for (int i = 0; i < allResults.size(); i++) {
			if (!allSubsets.contains(i)) {
				GeneratorEntry entry = allResults.get(i);
				maximalResults++;
				System.out.println(entry.axisStructure + ": " + entry.structureDescription + " " + entry.elements);
			}
		}
		System.out.println("Maximal results: " + maximalResults);
	}



	private static boolean isStructureSubset(int[] mainArray, int[] candidateSubArray) {
		// Count frequencies in both arrays
		TreeMap<Integer, Integer> mainFreq = new TreeMap<>();
		TreeMap<Integer, Integer> candidateFreq = new TreeMap<>();
		
		for (int num : mainArray) {
			mainFreq.merge(num, 1, Integer::sum);
		}
		for (int num : candidateSubArray) {
			candidateFreq.merge(num, 1, Integer::sum);
		}
		
		// Check if candidate is subset
		for (Entry<Integer, Integer> entry : candidateFreq.entrySet()) {
			int num = entry.getKey();
			int count = entry.getValue();
			if (!mainFreq.containsKey(num) || mainFreq.get(num) < count) {
				return false;
			}
		}
		
		return true;
	}

}
