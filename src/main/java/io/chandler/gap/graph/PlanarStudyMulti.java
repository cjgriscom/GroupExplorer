package io.chandler.gap.graph;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.List;
import java.util.Collections;
import java.util.Random;
import java.io.File;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;

import io.chandler.gap.GapInterface;
import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;

public class PlanarStudyMulti {

    public static void main(String[] args) throws IOException {
        // --------------------------
        // Configuration variables
        // --------------------------
        boolean requirePlanar = true;
        boolean generate = true;
        boolean filter = true;
        int order = -1;
        MemorySettings mem = MemorySettings.COMPACT;
        // Definitions for different conjugacy classes or cycle descriptions.
        String[] conj = new String[] {
            "6p 3-cycles", "6p 3-cycles"
            // You can add more strings here if you plan to use additional filterCombination indices.
        };
        // Define the indices (into the conj array) to be used for filtering.
        // For two files you might use: new int[]{0, 1}
        // For more than 2, simply add additional indices, e.g. new int[]{0, 1, 2}
        int[] filterCombinationIndices = new int[]{0, 0, 0};

        String generator = Generators.m22;
        String groupName = "m22";
        File root = new File("PlanarStudyMulti/" + groupName);
        root.mkdirs();

        // --------------------------
        // Generation branch (unchanged from original, except for path)
        // --------------------------
        if (generate && !generator.equals(Generators.m24)) {
            boolean multithread = true;
            PrintStream[] filesOut = new PrintStream[conj.length];
            for (int i = 0; i < conj.length; i++) {
                filesOut[i] = new PrintStream(root.getAbsolutePath() + "/" + conj[i] + ".txt");
            }

            GroupExplorer g = new GroupExplorer(generator, mem, new HashSet<>(), new HashSet<>(), new HashSet<>(), multithread);
            Generators.exploreGroup(g, (state, description) -> {
                for (int i = 0; i < conj.length; i++) {
                    if (description.equals(conj[i])) {
                        String cycles = GroupExplorer.stateToNotation(state);
                        filesOut[i].println(cycles);
                    }
                }
            });
            order = g.order();

            // Close files
            for (int i = 0; i < conj.length; i++) {
                filesOut[i].close();
            }
        } else {
            // Use GAP to get the order
            GapInterface gap = new GapInterface();
            String orderS = gap.runGapSizeCommand(generator, 2).get(1).trim();
            System.out.println("Order: " + orderS);
            order = Integer.parseInt(orderS);
        }

        // --------------------------
        // Filtering branch (modified for arbitrary many indices)
        // --------------------------
        if (filter) {
            GapInterface gap = new GapInterface();

            // Load each file specified by filterCombinationIndices into its own list
            List<List<String>> allLines = new ArrayList<>();
            for (int index : filterCombinationIndices) {
                List<String> lines = new ArrayList<>();
                File file = new File(root.getAbsolutePath() + "/" + conj[index] + ".txt");
                try (Scanner scanner = new Scanner(file)) {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if (!line.trim().isEmpty()) {
                            lines.add(line);
                        }
                    }
                }
                Collections.shuffle(lines, new Random());
                System.out.println(conj[index] + ": " + lines.size());
                allLines.add(lines);
            }
            
            // Build output file name by concatenating the names of the conj's used.
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < filterCombinationIndices.length; i++) {
                if (i > 0) {
                    sb.append("-");
                }
                sb.append(conj[filterCombinationIndices[i]]);
            }
            String fileOutPath = root.getAbsolutePath() + "/" + sb.toString() + "-filtered.txt";
            PrintStream fileOut = new PrintStream(fileOutPath);

            // Maintain a list of unique planar graphs.
            List<Graph<Integer, DefaultEdge>> uniquePlanarGraphs = new ArrayList<>();

            // Use arrays to hold counters (so they can be passed by reference)
            int[] countTotal = new int[]{0};
            int[] countFound = new int[]{0};

            // Recursively iterate over all combinations of lines from each file.
            recursiveFilter(allLines, 0, new ArrayList<>(), order, requirePlanar, gap, uniquePlanarGraphs, fileOut, countTotal, countFound);

            System.out.println("Found " + countFound[0] + " " + (requirePlanar ? "planar" : "non-planar") +
                    " graphs of " + countTotal[0]);
            System.out.println("Wrote: " + fileOutPath);
            fileOut.close();
        }
    }

    /**
     * Recursively iterates through the list-of-lists (allLines) to process every combination of lines.
     *
     * @param allLines          A list containing one list per filter-combination file.
     * @param depth             The current nesting depth.
     * @param currentLines      The current combination of lines.
     * @param order             The group order to test.
     * @param requirePlanar     If true, only planar graphs are processed.
     * @param gap               A GapInterface instance.
     * @param uniquePlanarGraphs List of graphs already encountered.
     * @param fileOut           Output stream to write valid combinations.
     * @param countTotal        Array holding the total count (when order equals).
     * @param countFound        Array holding the count of unique graphs found.
     */
    public static void recursiveFilter(List<List<String>> allLines, int depth, List<String> currentLines, int order, boolean requirePlanar,
                                         GapInterface gap, List<Graph<Integer, DefaultEdge>> uniquePlanarGraphs, PrintStream fileOut,
                                         int[] countTotal, int[] countFound) {
        if (depth == allLines.size()) {
            // Process the full combination of lines.
            List<int[][]> genList = new ArrayList<>();
            // For each selected line, parse the operations and use the first generator set (index 0).
            for (String line : currentLines) {
                int[][][] parsed = GroupExplorer.parseOperationsArr(line);
                genList.add(parsed[0]);
            }

            // Check for duplicate polygons across the selected generators.
            // For each polygon in each generator, compute a canonical representation (sorted order).
            // If any duplicate is found, skip this combination.
            Set<String> uniquePolygons = new HashSet<>();
            for (int[][] polygonSet : genList) {
                for (int[] polygon : polygonSet) {
                    String canon = canonicalPolygon(polygon);
                    if (!uniquePolygons.add(canon)) {
                        //System.out.println("Skipping combination due to duplicate polygon: " + canon);
                        return;
                    }
                }
            }

            // Convert list to an array for the renumbering method.
            int[][][] combinedArray = new int[genList.size()][][];
            for (int i = 0; i < genList.size(); i++) {
                combinedArray[i] = genList.get(i);
            }
            int[][][] combinedGen = GroupExplorer.renumberGenerators_fast(combinedArray, 300);
            String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedGen), 2).get(1).trim();
            if (size.equals(String.valueOf(order))) {
                countTotal[0]++;
                if (!requirePlanar || checkPlanarity(combinedGen)) {
                    Graph<Integer, DefaultEdge> candidateGraph = buildGraphFromCombinedGen(combinedGen);
                    boolean duplicate = false;
                    for (Graph<Integer, DefaultEdge> existingGraph : uniquePlanarGraphs) {
                        VF2GraphIsomorphismInspector<Integer, DefaultEdge> inspector =
                                new VF2GraphIsomorphismInspector<>(candidateGraph, existingGraph);
                        if (inspector.isomorphismExists()) {
                            duplicate = true;
                            break;
                        }
                    }

                    if (!duplicate) {
                        System.out.println("Found new " + (requirePlanar ? "planar" : "non-planar") 
                            + " graph " + countFound[0] + " of " + countTotal[0] 
                            + " - combination: " + currentLines);
                        fileOut.println(GroupExplorer.generatorsToString(combinedGen));
                        uniquePlanarGraphs.add(candidateGraph);
                        countFound[0]++;
                    }
                }
            }
            return;
        } else {
            List<String> currentList = allLines.get(depth);
            for (String line : currentList) {
                // Allow early termination if a key press is detected.
                try {
                    if (System.in.available() > 0) {
                        System.out.println("Key press detected. Early termination of filtering.");
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                currentLines.add(line);
                recursiveFilter(allLines, depth + 1, currentLines, order, requirePlanar, gap, uniquePlanarGraphs, fileOut, countTotal, countFound);
                currentLines.remove(currentLines.size() - 1);
            }
        }
    }

    /**
     * Checks if the graph constructed from combinedGen is planar.
     *
     * @param combinedGen A 3D array representing the generators of the graph.
     * @return true if the graph is planar, false otherwise.
     */
    public static boolean checkPlanarity(int[][][] combinedGen) {
        // Create a graph using JGraphT
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);

        // Add vertices and edges from combinedGen
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                // Add all vertices first
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                // Add edges to form a complete cycle
                for (int i = 0; i < polygon.length; i++) {
                    graph.addEdge(polygon[i], polygon[(i + 1) % polygon.length]);
                }
            }
        }

        // Check planarity using the BoyerMyrvold algorithm
        BoyerMyrvoldPlanarityInspector<Integer, DefaultEdge> inspector = new BoyerMyrvoldPlanarityInspector<>(graph);
        return inspector.isPlanar();
    }

    /**
     * Builds a JGraphT graph from a combined generator array.
     *
     * @param combinedGen A 3D array representing the generators.
     * @return A constructed Graph.
     */
    public static Graph<Integer, DefaultEdge> buildGraphFromCombinedGen(int[][][] combinedGen) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                // Add all vertices.
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                // Add cycle edges.
                for (int i = 0; i < polygon.length; i++) {
                    graph.addEdge(polygon[i], polygon[(i + 1) % polygon.length]);
                }
            }
        }
        return graph;
    }

    /**
     * Computes a canonical representation of a polygon (int array) by sorting.
     * This helps in detecting duplicate polygons regardless of the order of vertices.
     *
     * @param polygon an array representing the vertices of the polygon.
     * @return a canonical string representation of the polygon.
     */
    private static String canonicalPolygon(int[] polygon) {
        int[] copy = Arrays.copyOf(polygon, polygon.length);
        Arrays.sort(copy);
        return Arrays.toString(copy);
    }

    // The following helper methods remain from your original file.

    public static void generate3CyclesFile() throws IOException {
        PrintStream fileOut11 = new PrintStream("hs-9p-11-cycles.txt");
        PrintStream fileOut10 = new PrintStream("hs-10p-10-cycles.txt");
        PrintStream fileOut7 = new PrintStream("hs-14p-7-cycles.txt");
        PrintStream fileOut5 = new PrintStream("hs-19p-5-cycles.txt");
        PrintStream fileOut3 = new PrintStream("hs-30p-3-cycles.txt");
        PrintStream fileOut2_full = new PrintStream("hs-50p-2-cycles.txt");
        PrintStream fileOut2 = new PrintStream("hs-40p-2-cycles.txt");

        GroupExplorer g = new GroupExplorer(Generators.hs, MemorySettings.COMPACT, new HashSet<>(), new HashSet<>(), new HashSet<>(), false);
        Generators.exploreGroup(g, (state, description) -> {
            if (description.equals("30p 3-cycles")) {
                String cycles = GroupExplorer.stateToNotation(state);
                fileOut3.println(cycles);
            } else if (description.equals("19p 5-cycles")) {
                String cycles = GroupExplorer.stateToNotation(state);
                fileOut5.println(cycles);
            } else if (description.equals("14p 7-cycles")) {
                String cycles = GroupExplorer.stateToNotation(state);
                fileOut7.println(cycles);
            } else if (description.equals("10p 10-cycles")) {
                String cycles = GroupExplorer.stateToNotation(state);
                fileOut10.println(cycles);
            } else if (description.equals("9p 11-cycles")) {
                String cycles = GroupExplorer.stateToNotation(state);
                fileOut11.println(cycles);
            } else if (description.equals("40p 2-cycles")) {
                String cycles = GroupExplorer.stateToNotation(state);
                fileOut2.println(cycles);
            } else if (description.equals("50p 2-cycles")) {
                String cycles = GroupExplorer.stateToNotation(state);
                fileOut2_full.println(cycles);
            }
        });

        fileOut11.close();
        fileOut10.close();
        fileOut7.close();
        fileOut5.close();
        fileOut3.close();
        fileOut2.close();
        fileOut2_full.close();
    }

    public static void filter3CyclesFile() throws IOException {
        GapInterface gap = new GapInterface();

        Scanner fileIn = new Scanner(new File("hs-30p-3-cycles.txt"));
        PrintStream fileOut = new PrintStream("hs-30p-3-cycles-filtered.txt");
        String line0 = fileIn.nextLine();
        int[][][] gen0 = GroupExplorer.parseOperationsArr(line0);

        while (fileIn.hasNextLine()) {
            String line = fileIn.nextLine();
            int[][][] gen1 = GroupExplorer.parseOperationsArr(line);
            Generator combined = Generator.combine(new Generator(gen0), new Generator(gen1));
            int[][][] combinedGen = GroupExplorer.renumberGenerators_fast(combined.generator());

            String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedGen), 2).get(1).trim();
            System.out.println(size);
            if (size.equals("44352000")) {
                fileOut.println(GroupExplorer.generatorsToString(combinedGen));
            }

        }
        fileOut.close();
        fileIn.close();
    }
} 