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
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;

import io.chandler.gap.GapInterface;
import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;

public class PlanarStudyRepeated {

    public static void main(String[] args) throws IOException {
        // --------------------------------------------------------
        // Configuration variables
        // --------------------------------------------------------
        boolean allowSubgroups = true;
        boolean requirePlanar = true;
        // In this experiment we want to generate files and then filter in two stages.
        boolean generate = true;
        int repetitions = 1; // Change to 2 (or higher) for additional rounds (e.g., quadruple generation for 2).
        
        boolean directed = false;

        int order = -1;
        MemorySettings mem = MemorySettings.COMPACT;
        
        // We use two cycle descriptions for the candidate pairs.
        String[] conj = new String[] {
            "128p 2-cycles", "128p 2-cycles"
            // You can change these strings to use different cycle types.
        };
        // For phase 1, we use two different files (indices 0 and 1).
        int[] phase1Indices = new int[]{1,0};

        String generator = Generators.j1;
        String groupName = "j1";
        File root = new File("PlanarStudyMulti/" + groupName);
        root.mkdirs();

        // --------------------------------------------------------
        // Generation branch: generate input files if needed.
        // --------------------------------------------------------
        if (generate &&
                      !generator.equals(Generators.m24) &&
                      !generator.equals(Generators.hs) &&
                      !generator.equals(Generators.mcl)) {
            boolean multithread = true;
            PrintStream[] filesOut = new PrintStream[conj.length];
            for (int i = 0; i < conj.length; i++) {
                filesOut[i] = new PrintStream(root.getAbsolutePath() + "/" + conj[i] + ".txt");
            }
            
            GroupExplorer g = new GroupExplorer(generator, mem, new HashSet<>(), new HashSet<>(), new HashSet<>(), multithread);
            Generators.exploreGroup(g, (state, description) -> {
                //if (description.endsWith("3-cycles") || description.endsWith("2-cycles")) System.out.println(description);
                for (int i = 0; i < conj.length; i++) {
                    if (description.equals(conj[i])) {
                        String cycles = GroupExplorer.stateToNotation(state);
                        filesOut[i].println(cycles);
                    }
                }
            });
            order = g.order();
            
            // Close the generation files.
            for (int i = 0; i < conj.length; i++) {
                filesOut[i].close();
            }
        } else {
            // If not generated then obtain the order from GAP.
            GapInterface gap = new GapInterface();
            String orderS = gap.runGapSizeCommand(generator, 2).get(1).trim();
            System.out.println("Order: " + orderS);
            order = Integer.parseInt(orderS);
        }

        // --------------------------------------------------------
        // Phase 1: Pair Filtering
        // --------------------------------------------------------
        System.out.println("Starting Phase 1: Pair Filtering");
        // Read lines from the two designated files.
        List<String> lines1 = new ArrayList<>();
        File file1 = new File(root.getAbsolutePath() + "/" + conj[phase1Indices[0]] + ".txt");
        try (Scanner scanner1 = new Scanner(file1)) {
            while (scanner1.hasNextLine()) {
                String l = scanner1.nextLine();
                if (!l.trim().isEmpty()) {
                    lines1.add(l);
                }
            }
        }
        Collections.shuffle(lines1, new Random(123));

        List<String> lines2 = new ArrayList<>();
        File file2 = new File(root.getAbsolutePath() + "/" + conj[phase1Indices[1]] + ".txt");
        try (Scanner scanner2 = new Scanner(file2)) {
            while (scanner2.hasNextLine()) {
                String l = scanner2.nextLine();
                if (!l.trim().isEmpty()) {
                    lines2.add(l);
                }
            }
        }
        Collections.shuffle(lines2, new Random(321));

        // instantiate GAP to check group order.
        GapInterface gap = new GapInterface();
        
        // Create a list to save unique candidate pairs.
        List<int[][][]> candidatePairs = new ArrayList<>();
        List<Graph<Integer, DefaultEdge>> pairGraphs = new ArrayList<>();
        PrintStream phase1Out = new PrintStream(root.getAbsolutePath() + "/" + conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-filtered.txt");
        int[] found = new int[repetitions + 1];

        int p1_1_count = 0;
        int p1_2_count = 0;

        // Nested loops over the two files with early termination support.
        phase1Loop: for (String l1 : lines1) {
            p1_1_count++;
            int[][][] parsed1 = GroupExplorer.parseOperationsArr(l1);
            int[][] firstCandidate = parsed1[0]; // use the first generator set from file1.
            for (String l2 : lines2) {
                p1_2_count++;
                // Check for a key press to allow early termination of Phase 1 filtering.
                try {
                    if (System.in.available() > 0) {
                        System.out.println("Key press detected. Early termination of Phase 1 filtering.");
                        while (System.in.available() > 0) {
                            System.in.read();
                        }
                        System.out.println("p1_1_count: " + p1_1_count);
                        System.out.println("p1_2_count: " + p1_2_count);
                        break phase1Loop;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int[][][] parsed2 = GroupExplorer.parseOperationsArr(l2);
                int[][] secondCandidate = parsed2[0]; // use the first generator set from file2.
                // Combine the two generators into a pair.
                int[][][] combinedPair = new int[][][]{firstCandidate, secondCandidate};
                
                // Check for duplicate polygons (e.g. [1,2,3] vs [2,1,3]).
                if (hasDuplicatePolygon(combinedPair)) {
                    continue;
                }
                
                // Check planarity if required.
                if (requirePlanar && !checkPlanarity(combinedPair)) {
                    continue;
                }

                String size = null;
                if (!allowSubgroups) {
                    size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedPair), 2).get(1).trim();
                    if (!size.equals(String.valueOf(order))) {
                        continue;
                    }
                }
                
                // Check for isomorphic duplicates.
                Graph<Integer, DefaultEdge> candGraph = buildGraphFromCombinedGen(combinedPair, directed);
                boolean duplicate = false;
                for (Graph<Integer, DefaultEdge> g : pairGraphs) {
                    VF2GraphIsomorphismInspector<Integer, DefaultEdge> inspector =
                            new VF2GraphIsomorphismInspector<>(candGraph, g);
                    if (inspector.isomorphismExists()) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate)
                    continue;
                
                if (size == null) size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedPair), 2).get(1).trim();
                System.out.println("Found new "+(!requirePlanar ? "non-" : "")+"planar graph " + found[0] + " with order " + size);
                
                
                candidatePairs.add(combinedPair);
                pairGraphs.add(candGraph);
                if (size.equals(String.valueOf(order))) {
                    phase1Out.println(GroupExplorer.generatorsToString(combinedPair));
                    found[0]++;
                }
            }
        }
        phase1Out.close();
        System.out.println("Phase 1 completed. Unique candidate pairs: " + candidatePairs.size());
        
        
        // --------------------------------------------------------
        // Phase 2: Repetitions-based candidate generation.
        // --------------------------------------------------------
        // 'repetitions' determines how many times Phase 2 is applied.
        // If repetitions == 1, then we generate triple candidates (as before).
        // If repetitions > 1, then we iteratively combine the candidates with new generators (from lines2)
        // without checking group order until the final round.
        
        List<int[][][]> currentCandidates = new ArrayList<>(candidatePairs);
        // For output naming, build a base file name.
        String baseFileName = conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-" + conj[phase1Indices[1]];
        
        for (int r = 1; r <= repetitions; r++) {
            System.out.println("Starting Phase 2, round " + r + "");
            List<int[][][]> newCandidates = new ArrayList<>();
            List<Graph<Integer, DefaultEdge>> newCandidateGraphs = new ArrayList<>();
            String roundFileName = baseFileName + "_R" + r + "-filtered.txt";
            PrintStream phase2RoundOut = new PrintStream(root.getAbsolutePath() + "/" + roundFileName);
            int roundCount = 0;
            // For each candidate from the previous round, combine with each line from file2.
            for (int i = 0; i < currentCandidates.size(); i++) {
                int[][][] candidate = currentCandidates.get(i);
                // Inner loop: iterate over individual lines from file2.
                outerRoundLoop: for (String l : lines2) {
                    // Key press listener for early termination of the inner loop.
                    try {
                        if (System.in.available() > 0) {
                            System.out.println("Key press detected. Early termination of Phase 2 inner loop.");
                            while (System.in.available() > 0) {
                                System.in.read();
                            }
                            break outerRoundLoop;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // Parse the line from file2 and use its generator (index 0).
                    int[][][] parsedGen = GroupExplorer.parseOperationsArr(l);
                    int[][] newGenerator = parsedGen[0];
                    // Build the new candidate by appending newGenerator to the current candidate.
                    int currentLen = candidate.length;
                    int[][][] newCandidate = new int[currentLen + 1][][];
                    for (int j = 0; j < currentLen; j++) {
                        newCandidate[j] = candidate[j];
                    }
                    newCandidate[currentLen] = newGenerator;
                    // Check for duplicate polygons in the new candidate.
                    if (hasDuplicatePolygon(newCandidate)) {
                        continue;
                    }
                    // Check planarity if required.
                    if (requirePlanar && !checkPlanarity(newCandidate)) {
                        continue;
                    }
                    // Check for non-isomorphic duplicates.
                    Graph<Integer, DefaultEdge> candGraph = buildGraphFromCombinedGen(newCandidate, directed);
                    boolean duplicate = false;
                    for (Graph<Integer, DefaultEdge> g : newCandidateGraphs) {
                        VF2GraphIsomorphismInspector<Integer, DefaultEdge> inspector =
                                new VF2GraphIsomorphismInspector<>(candGraph, g);
                        if (inspector.isomorphismExists()) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (duplicate) continue;
                    
                    String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(newCandidate), 2).get(1).trim();
                    System.out.println("Found new "+(!requirePlanar ? "non-" : "")+"planar graph " + found[r] + " with order " + size);
                    
                    newCandidates.add(newCandidate);
                    newCandidateGraphs.add(candGraph);
                    if (size.equals(String.valueOf(order))) {
                        phase2RoundOut.println(GroupExplorer.generatorsToString(newCandidate));
                        found[r]++;
                    }
                    roundCount++;
                }
                System.out.println("Completed inner loop for candidate " + i);
            }
            phase2RoundOut.close();
            System.out.println("Round " + r + " completed. Unique new candidates: " + roundCount);
            currentCandidates = newCandidates;
        }
        System.out.println("Phase 2 completed after " + repetitions + " round(s). Final candidate count: " + currentCandidates.size() + " - order " + order + " found: " + Arrays.toString(found));
    }
    
    /**
     * Checks whether any duplicate polygon appears in the combined generator array.
     * Two polygons are considered duplicates if their canonical (sorted) representation is equal.
     */
    private static boolean hasDuplicatePolygon(int[][][] combinedGen) {
        Set<String> set = new HashSet<>();
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                String canon = canonicalPolygon(polygon);
                if (!set.add(canon)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Computes a canonical representation of a polygon (an int array) by sorting.
     * This is used in duplicate checks to ignore ordering differences (e.g. [1,2,3] vs [2,1,3]).
     */
    private static String canonicalPolygon(int[] polygon) {
        int[] copy = Arrays.copyOf(polygon, polygon.length);
        Arrays.sort(copy);
        return Arrays.toString(copy);
    }
    
    /**
     * Checks if the graph constructed from combinedGen is planar.
     *
     * @param combinedGen A 3D array representing the generators.
     * @return true if the graph is planar, false otherwise.
     */
    public static boolean checkPlanarity(int[][][] combinedGen) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                for (int i = 0; i < polygon.length; i++) {
                    graph.addEdge(polygon[i], polygon[(i + 1) % polygon.length]);
                }
            }
        }
        BoyerMyrvoldPlanarityInspector<Integer, DefaultEdge> inspector = new BoyerMyrvoldPlanarityInspector<>(graph);
        return inspector.isPlanar();
    }
    
    /**
     * Builds a JGraphT graph from a combined generator array.
     *
     * @param combinedGen A 3D array representing the generators.
     * @return A constructed Graph.
     */
    public static Graph<Integer, DefaultEdge> buildGraphFromCombinedGen(int[][][] combinedGen, boolean directed) {
        Graph<Integer, DefaultEdge> graph = 
            directed ? new SimpleDirectedGraph<>(DefaultEdge.class) : new SimpleGraph<>(DefaultEdge.class);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                for (int vertex : polygon) {
                    if (!graph.containsVertex(vertex)) {
                        graph.addVertex(vertex);
                    }
                }
                for (int i = 0; i < polygon.length; i++) {
                    int v1 = polygon[i], v2 = polygon[(i + 1) % polygon.length];
                    if (!graph.containsEdge(v1, v2)) {
                        graph.addEdge(v1, v2);
                    }
                }
            }
        }
        return graph;
    }
} 