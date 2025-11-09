package io.chandler.gap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

public class CoxeterDynkinMutation {
	public static void main(String[] args) {
		int points = 7;
		int[][] graph = new int[][] {
			{0, 1},
			{1, 2},
			{2, 3},
			{3, 4},
			{4, 5},
			{2, points-1},
		};
		HashMap<Integer, HashSet<Integer>> graphMap = new HashMap<>();
		for (int[] edge : graph) {
			graphMap.computeIfAbsent(edge[0], k -> new HashSet<>()).add(edge[1]);
			graphMap.computeIfAbsent(edge[1], k -> new HashSet<>()).add(edge[0]);
		}

		HashSet<String> visited = new HashSet<>();

		HashSet<String> queue = new HashSet<>();

		// Add unit vectors in the form "1,0,0,0,0,0,0,0,"
		for (int i = 0; i < points; i++) {
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < points; j++) {
				sb.append(j == i ? 1 : 0);
				if (j != points - 1) sb.append(",");
			}
			queue.add(sb.toString());
		}

		while (!queue.isEmpty()) {
			String current = queue.iterator().next();
			queue.remove(current);
			// For each point, try the mutation operation and add to queue if it is not visited
			String[] parseMe = current.split(",");
			int[] currentArray = new int[points];
			for (int i = 0; i < points; i++) {
				currentArray[i] = Integer.parseInt(parseMe[i]);
			}
			for (int i = 0; i < points; i++) {
				int[] newArray = currentArray.clone();
				newArray[i] = -newArray[i];
				for (int nei : graphMap.get(i)) { // Clone and add neighbors
					newArray[i] += currentArray[nei];
				}
				String newString = Arrays.stream(newArray).mapToObj(String::valueOf).collect(Collectors.joining(","));
				if (!visited.contains(newString)) {
					System.out.println(newString);
					visited.add(newString);
					queue.add(newString);
				}
			}
			//System.out.println(queue.size());


		}

		System.out.println(visited.size());

	}
}
