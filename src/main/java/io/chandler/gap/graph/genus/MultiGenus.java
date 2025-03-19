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
    private static final boolean USE_320 = false;

    public static enum MultiGenusOption {
        LIMIT_TO_GENUS_1, // Useful to keep things quick
    }

	public static void main(String[] args) {
		String icosianGenerator = "[(1,2,3)(4,5,6)(7,8,9)(10,11,12)(13,14,15)(16,17,18)(19,20,21)(22,23,24)(25,26,27)(28,29,30)(31,32,33)(34,35,36)(37,38,39)(40,41,42)(43,44,45)(46,47,48)(49,50,51)(52,53,54)(55,56,57)(58,59,60)(61,62,63)(64,65,66)(67,68,69)(70,71,72)(73,74,75)(76,77,78)(79,80,81)(82,83,84)(85,86,87)(88,89,90),(1,91,14)(4,78,79)(7,60,22)(10,49,44)(13,92,16)(19,90,29)(23,93,69)(25,55,36)(28,80,34)(31,21,2)(37,68,94)(12,18,95)(43,15,54)(96,71,77)(48,62,50)(27,39,97)(58,11,9)(61,98,87)(64,53,3)(99,63,67)(70,6,59)(42,66,84)(73,85,89)(76,20,33)(8,57,38)(82,86,47)(72,100,17)(45,24,51)(32,74,41)(26,75,88)]";
		List<Integer> genuses = computeGenusFromGenerators(Arrays.<int[][][]>asList(
            GroupExplorer.parseOperationsArr(Generators.m12_66pt),
            GroupExplorer.parseOperationsArr(icosianGenerator),
            GroupExplorer.parseOperationsArr(Generators.m11_55pt),
            GroupExplorer.parseOperationsArr(Generators.hs),
           // GroupExplorer.parseOperationsArr(Generators.j1),
            GroupExplorer.parseOperationsArr("[(1,214)(2,44)(3,182)(4,80)(5,173)(6,117)(7,75)(8,71)(9,153)(10,11)(12,261)(13,157)(14,85)(15,209)(16,31)(17,94)(18,197)(19,109)(20,76)(21,136)(22,108)(23,194)(24,87)(25,221)(26,48)(27,92)(28,247)(29,59)(30,193)(32,205)(33,203)(34,106)(35,259)(37,83)(38,174)(39,150)(40,104)(41,121)(43,222)(45,49)(46,51)(50,238)(52,53)(54,258)(55,107)(56,265)(57,143)(60,116)(61,187)(62,126)(63,231)(64,99)(65,130)(66,208)(67,140)(68,195)(69,133)(70,219)(72,138)(73,77)(74,165)(78,234)(79,207)(81,251)(82,167)(84,263)(86,223)(88,239)(89,170)(90,199)(91,113)(93,129)(95,218)(96,201)(97,172)(98,123)(100,148)(101,200)(102,135)(103,236)(105,233)(110,177)(111,128)(112,161)(114,131)(115,224)(118,144)(119,186)(120,139)(122,250)(124,230)(127,149)(132,253)(134,202)(137,164)(141,145)(142,245)(147,155)(151,189)(152,228)(154,190)(156,158)(159,184)(160,168)(162,264)(163,255)(166,256)(169,198)(171,216)(175,246)(176,185)(178,244)(179,227)(180,225)(181,220)(183,242)(188,213)(191,217)(192,252)(196,229)(204,212)(206,257)(211,241)(215,235)(232,260)(243,249)(248,266)(254,262),(1,30,226)(2,86,111)(3,214,115)(4,44,7)(5,54,212)(6,253,168)(8,161,63)(9,241,11)(10,101,224)(12,46,179)(13,242,60)(14,229,166)(15,85,48)(16,65,104)(17,193,80)(18,171,62)(19,113,252)(20,207,93)(21,163,257)(22,87,67)(23,92,183)(24,251,42)(25,240,43)(26,53,165)(27,250,239)(28,152,81)(29,154,158)(31,121,122)(32,157,164)(33,199,141)(34,127,74)(35,145,131)(36,73,143)(37,181,61)(39,189,133)(40,184,237)(41,162,64)(45,47,52)(49,117,236)(50,178,129)(51,194,97)(55,138,155)(56,123,220)(57,191,245)(58,107,114)(59,102,137)(66,170,238)(68,255,234)(69,211,75)(70,246,265)(71,151,202)(72,95,233)(76,235,259)(77,177,180)(78,208,186)(79,96,198)(82,247,160)(83,216,210)(84,103,108)(88,125,222)(89,192,176)(90,205,218)(91,112,244)(94,174,153)(98,124,156)(99,116,221)(100,169,249)(105,243,225)(106,263,139)(109,136,188)(110,248,142)(118,190,264)(119,148,135)(126,132,256)(128,173,175)(130,261,228)(134,196,215)(140,227,149)(144,159,254)(146,182,223)(147,217,201)(150,219,197)(167,187,262)(172,203,209)(195,230,260)(200,231,258)(204,213,232)]")
            ),
            MultiGenusOption.LIMIT_TO_GENUS_1);
		System.out.println("Genus: " + genuses);
	}

	public static List<Integer> computeGenusFromGenerators(List<int[][][]> generators, MultiGenusOption... options) {
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
		return computeGenus(adjLists, options);
    }

    public static List<Integer> computeGenus(List<int[][]> adjLists, MultiGenusOption... options) {
        int maxVertex = 0;
        for (int[][] adjList : adjLists) {
            for (int[] neighbors : adjList) {
                for (int neighbor : neighbors) {
                    maxVertex = Math.max(maxVertex, neighbor);
                }
            }
        }

        // Count the number of edges
        int maxEdges = 0;
        for (int[][] adjList : adjLists) {
            HashSet<Integer> edges = new HashSet<>();
            for (int[] neighbors : adjList) {
                for (int i = 0; i < neighbors.length; i++) {
                    for (int j = i+1; j < neighbors.length; j++) {
                        edges.add(Math.min(neighbors[i], neighbors[j]) * 65536 + Math.max(neighbors[i], neighbors[j]));
                    }
                }
            }
            maxEdges = Math.max(edges.size(), maxEdges);
        }
        
        if (maxVertex >= 320 || maxEdges >= 2048) return null; // too large for the program
        boolean largerThan128 = maxVertex >= 128 || maxEdges >= 1024 || USE_320;
        String prog = largerThan128 ? "multi_genus_320" : "multi_genus_128";
        // Convert adjacency list to MultiCode format
        byte[] multiCode = toMultiCode(adjLists, largerThan128 ? 2 : 1);

        // Setup input/output streams
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int upperLimit = 9;
        for (MultiGenusOption option : options) {
            if (option == MultiGenusOption.LIMIT_TO_GENUS_1) {
                upperLimit = 1;
            }
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("src/main/c/" + prog, "N", "l"+upperLimit); // I added N to output just the genus
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

    private static byte[] toMultiCode(List<int[][]> adjLists, int numBytes) {
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
        // Big endian
        for (int i = 0; i < numBytes; i++) {
            baos.write((value >> (8 * (numBytes - i - 1))) & 0xFF);
        }
	}

    /**
     * Expected (correct) hex output: 
     *    05 02 03 04 05 00 03 04 05 00 04 05 00 05 00
     * 
     */
    public static int[][] testToMultiCode(int numBytes) {
        int[][] testAdjList = {
            {1, 2, 3, 4},
            {0, 2, 3, 4},
            {0, 1, 3, 4},
            {0, 1, 2, 4},
            {0, 1, 2, 3}
        };
		
        byte[] multiCode = toMultiCode(Arrays.<int[][]>asList(testAdjList), numBytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : multiCode) {
            hex.append(String.format("%02X ", b));
        }
        System.out.println("Test MultiCode hex output: " + hex.toString().trim());
        // Expected hex output: "05 02 03 04 05 00 03 04 05 00 04 05 00 05 00"

		return testAdjList;
    }
}
