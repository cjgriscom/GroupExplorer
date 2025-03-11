package io.chandler.gap.graph.genus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;

public class MultiGenus {
    private static final boolean DEBUG = false;

	public static void main(String[] args) {
		String icosianGenerator = "[(1,2,3)(4,5,6)(7,8,9)(10,11,12)(13,14,15)(16,17,18)(19,20,21)(22,23,24)(25,26,27)(28,29,30)(31,32,33)(34,35,36)(37,38,39)(40,41,42)(43,44,45)(46,47,48)(49,50,51)(52,53,54)(55,56,57)(58,59,60)(61,62,63)(64,65,66)(67,68,69)(70,71,72)(73,74,75)(76,77,78)(79,80,81)(82,83,84)(85,86,87)(88,89,90),(1,91,14)(4,78,79)(7,60,22)(10,49,44)(13,92,16)(19,90,29)(23,93,69)(25,55,36)(28,80,34)(31,21,2)(37,68,94)(12,18,95)(43,15,54)(96,71,77)(48,62,50)(27,39,97)(58,11,9)(61,98,87)(64,53,3)(99,63,67)(70,6,59)(42,66,84)(73,85,89)(76,20,33)(8,57,38)(82,86,47)(72,100,17)(45,24,51)(32,74,41)(26,75,88)]";
		List<Integer> genuses = computeGenusFromGenerators(Arrays.<int[][][]>asList(
            GroupExplorer.parseOperationsArr(Generators.m12_66pt),
            GroupExplorer.parseOperationsArr(icosianGenerator),
            GroupExplorer.parseOperationsArr(Generators.m11_55pt),
            GroupExplorer.parseOperationsArr(Generators.hs)));
		System.out.println("Genus: " + genuses);
	}

	public static List<Integer> computeGenusFromGenerators(List<int[][][]> generators) {
		List<int[][]> adjLists = new ArrayList<>();
		for (int[][][] generator : generators) {
            TreeMap<Integer, Set<Integer>> adjList = new TreeMap<>();
            int maxVertex = 0;
            // Build adjList from the triangles in the icosian generator
            for (int[][] cycles : generator) {
                for (int[] polygon : cycles) {
                    for (int i = 0; i < polygon.length; i++) {
                        int v1 = polygon[i];
                        int v2 = polygon[(i+1)%polygon.length];
                        adjList.putIfAbsent(v1-1, new HashSet<>());
                        adjList.putIfAbsent(v2-1, new HashSet<>());
                        adjList.get(v1-1).add(v2-1);
                        adjList.get(v2-1).add(v1-1);
                        maxVertex = Math.max(maxVertex, v1-1);
                        maxVertex = Math.max(maxVertex, v2-1);
                    }
                }
            }

            // make it bidirectional
            for (int key : adjList.keySet()) {
                Set<Integer> neighbors = adjList.get(key);
                for (int neighbor : neighbors) {
                    adjList.putIfAbsent(neighbor, new HashSet<>());
                    adjList.get(neighbor).add(key);
                }
            }

            int[][] adjListArray = new int[maxVertex+1][];
            for (int i = 0; i < maxVertex+1; i++) {
                if (!adjList.containsKey(i)) {
                    continue;
                }
                adjListArray[i] = adjList.get(i).stream().mapToInt(Integer::intValue).toArray();
            }

            if (DEBUG) {
                // Print adjListArray
                for (int i = 0; i < maxVertex+1; i++) {
                    System.out.println(i + ": " + Arrays.toString(adjListArray[i]));
                }
            }
            adjLists.add(adjListArray);
        }
		return computeGenus(adjLists);
    }

    public static List<Integer> computeGenus(List<int[][]> adjLists) {
        // Convert adjacency list to MultiCode format
        byte[] multiCode = toMultiCode(adjLists);

        // Setup input/output streams
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("src/main/c/multi_genus_128", "N"); // I added N to output just the genus
            Process process = processBuilder.start();

            // Write the multiCode to the process's input stream
            try (OutputStream processInput = process.getOutputStream()) {
                processInput.write(multiCode);
            }

            // Read the process's debug output
            try (InputStream processOutput = process.getErrorStream()) {
                processOutput.transferTo(stderr);
            }
            // Read the process's output
            try (InputStream processOutput = process.getInputStream()) {
                processOutput.transferTo(stdout);
            }

            int exitCode = process.waitFor();
            if (DEBUG) {
                StringBuilder hex = new StringBuilder();
                for (byte b : stdout.toByteArray()) {
                    hex.append(String.format("%02X ", b));
                }
                System.out.println("Standard Output: " + hex.toString().trim());
                System.out.println("Standard Error: " + stderr.toString());
                System.out.println("Process exited with code: " + exitCode);
            }

            // Scan the stdout array for genus values
            byte[] outputBytes = stdout.toByteArray();
            List<Integer> genusValues = new ArrayList<>();
            for (int i = 0; i < outputBytes.length; i++) {
                if (outputBytes[i] == (byte) 0xFF) {
                    if (i + 1 < outputBytes.length) {
                        genusValues.add((int) outputBytes[i + 1]);
                        i++; // Skip the next byte as it is already processed
                    } else {
                        // If the length doesn't match expected, return null
                        return null;
                    }
                }
            }

            // If the number of genus values doesn't match the number of graphs, return null
            if (genusValues.size() != adjLists.size()) {
                return null;
            }

            return genusValues;
        } catch (IOException | InterruptedException e) {
            if (DEBUG) {
                e.printStackTrace();
                System.err.println("Error during process execution: " + e.getMessage());
            }
        }
        return null;
    }

    private static byte[] toMultiCode(List<int[][]> adjLists) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (int[][] adjList : adjLists) {
            // get min vertex
            int minVertex = Integer.MAX_VALUE;
            for (int[] neighbors : adjList) {
                for (int neighbor : neighbors) {
                    minVertex = Math.min(minVertex, neighbor);
                }
            }
            
            int n = adjList.length;
            int numBytes = n > 255 ? 2 : 1;

            writeInt(baos, n, numBytes);

            for (int i = 0; i < n; i++) {
                for (int j : adjList[i]) {
                    if (j - minVertex > i) writeInt(baos, j - minVertex + 1, numBytes);
                }
                if (i < n - 1) writeInt(baos, 0, numBytes);
            }

        }

		return baos.toByteArray();
    }

	private static void writeInt(ByteArrayOutputStream baos, int value, int numBytes) {
		for (int i = 0; i < numBytes; i++) {
			baos.write(0xFF & value);
			value >>= 8;
		}
	}

    /**
     * Expected (correct) hex output: 
     *    04 02 03 04 00 03 04 00 04 00
     */
    public static int[][] testToMultiCode() {
        int[][] testAdjList = {
            {1, 2, 3, 4},
            {0, 2, 3, 4},
            {0, 1, 3, 4},
            {0, 1, 2, 4},
            {0, 1, 2, 3}
        };
		
        byte[] multiCode = toMultiCode(Arrays.<int[][]>asList(testAdjList));
        StringBuilder hex = new StringBuilder();
        for (byte b : multiCode) {
            hex.append(String.format("%02X ", b));
        }
        System.out.println("Test MultiCode hex output: " + hex.toString().trim());
        // Expected hex output: "04 02 03 04 00 03 04 00 04 00"

		return testAdjList;
    }
}
