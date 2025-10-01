package io.chandler.gap.graph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.SimpleGraph;

import fi.tkk.ics.jbliss.DefaultReporter;
import io.chandler.gap.GapInterface;
import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.graph.genus.MultiGenus;


// PlanarStudy has a problem
// In a deep cut puzzle you can select the same line twice and still have a proper conscruction
// But the duplicate polygon check blocks it
public class PlanarStudyRepeated {
    private static final boolean JBLISS = true;
    static {
        if (JBLISS) {
            System.loadLibrary("jbliss");
        }
    }

    public static void main(String[] args) throws IOException {
        // --------------------------------------------------------
        // Preview mode.
        // --------------------------------------------------------
        boolean previewOnly = false;
        // --------------------------------------------------------
        // Configuration variables
        // --------------------------------------------------------
        boolean allowSubgroups = true;
        boolean requirePlanar = true;
        boolean discardOverGenus1 = true;
        int enforceLoopMultiples = 3;
        // In this experiment we want to generate files and then filter in two stages.
        boolean generate = true;
        int repetitions = 4; // Change to 2 (or higher) for additional rounds (e.g., quadruple generation for 2).
        
        boolean directed = true;

        int order = -1;
        MemorySettings mem = MemorySettings.COMPACT;

        // We use two cycle descriptions for the candidate pairs.

        // Either use a complete description like "6p 3-cycles" or a partial description like "5-cycles" for 5-cycles only
        String[] conj = new String[] {
            "2-cycles",  "2-cycles"
        };
        // For phase 1, we use two different files (indices 0 and 1).
        int[] phase1Indices = new int[]{0,1};
        int[] phase2Indices = new int[]{1};

        String generator = Generators.sp_6_2;
        String groupName = "sp_6_2";
        System.out.println(groupName);
        File root = new File("PlanarStudyMulti/" + groupName);
        root.mkdirs();

        // --------------------------------------------------------
        // Generation branch: generate input files if needed.
        // --------------------------------------------------------
        if (generate &&
                      !groupName.startsWith("u4_3_") &&
                      !generator.equals(Generators.m24) &&
                      !generator.equals(Generators.hs) &&
                      !generator.equals(Generators.hs_2) &&
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
                    boolean match = false;
                    if (conj[i].contains(" ")) {
                        match = description.equals(conj[i]);
                    } else {
                        if (conj[i].contains("+")) {
                            match = description.contains(conj[i].replace("+", ""));
                        } else {
                            match = !description.contains(",") && description.endsWith(conj[i]);
                        }
                    }
                    if (match) {
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

            if (previewOnly) System.exit(0);
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

        List<String> lines3 = new ArrayList<>();
        File file3 = new File(root.getAbsolutePath() + "/" + conj[phase2Indices[0]] + ".txt");
        try (Scanner scanner3 = new Scanner(file3)) {
            while (scanner3.hasNextLine()) {
                String l = scanner3.nextLine();
                if (!l.trim().isEmpty()) {
                    lines3.add(l);
                }
            }
        }
        Collections.shuffle(lines3, new Random(321));

        // instantiate GAP to check group order.
        GapInterface gap = new GapInterface();
        
        // Create a list to save unique candidate pairs.
        List<int[][][]> candidatePairs = new ArrayList<>();
        List<Graph<Integer, DefaultEdge>> pairGraphs = new ArrayList<>();
        HashSet<String> canonicalGraphs = new HashSet<>();
        PrintStream phase1Out = new PrintStream(root.getAbsolutePath() + "/" + (enforceLoopMultiples > 1 ? "l" + enforceLoopMultiples + "-" : "") + (requirePlanar ? "" : discardOverGenus1 ? "torus-" : "np-") + conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-filtered.txt");
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

                // Check for genus 1 if required.
                if (discardOverGenus1 && !(checkPlanarity(combinedPair) || checkGenus1(combinedPair))) {
                    continue;
                }

                Graph<Integer, DefaultEdge> candGraph = buildGraphFromCombinedGen(combinedPair, directed);

                // Enforce all simple cycles have length multiple of N (if enabled)
                if (!allEdgeCyclesAreMultiples(candGraph, enforceLoopMultiples)) {
                    continue;
                }

                boolean duplicate = false;
                String canonicalLabeling = null;

                // Check for isomorphic duplicates.
                if (JBLISS) {
                    fi.tkk.ics.jbliss.Graph<Integer> jblissGraph = buildJblissGraphFromCombinedGen(combinedPair, directed);
                    canonicalLabeling = getCanonicalGraph(jblissGraph);
                    if (canonicalGraphs.contains(canonicalLabeling)) {
                        duplicate = true;
                        break;
                    }
                } else {
                    for (Graph<Integer, DefaultEdge> g : pairGraphs) {
                        VF2GraphIsomorphismInspector<Integer, DefaultEdge> inspector =
                                new VF2GraphIsomorphismInspector<>(candGraph, g);
                        if (inspector.isomorphismExists()) {
                            duplicate = true;
                            break;
                        }
                    }
                }
                if (duplicate)
                    continue;
                
                if (Math.random() < 0.01) Collections.shuffle(pairGraphs);
                if (size == null) size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedPair), 2).get(1).trim();
                System.out.println("Found new "+(!requirePlanar ? "non-" : "")+"planar graph " + found[0] + " with order " + size);
                
                candidatePairs.add(combinedPair);
                pairGraphs.add(candGraph);
                if (JBLISS) canonicalGraphs.add(canonicalLabeling);
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
        String baseFileName =  (enforceLoopMultiples > 1 ? "l" + enforceLoopMultiples + "-" : "") +(requirePlanar ? "" : discardOverGenus1 ? "torus-" : "np-") + conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-" + conj[phase2Indices[0]];
        
        for (int r = 1; r <= repetitions; r++) {
            System.out.println("Starting Phase 2, round " + r + "");
            List<int[][][]> newCandidates = new ArrayList<>();
            List<Graph<Integer, DefaultEdge>> newCandidateGraphs = new ArrayList<>();
            String roundFileName = baseFileName + "_R" + r + "-filtered.txt";
            PrintStream phase2RoundOut = new PrintStream(root.getAbsolutePath() + "/" + roundFileName);
            int roundCount = 0;
            // For each candidate from the previous round, combine with each line from file3.
            for (int i = 0; i < currentCandidates.size(); i++) {
                int[][][] candidate = currentCandidates.get(i);
                // Inner loop: iterate over individual lines from file3.
                outerRoundLoop: for (String l : lines3) {
                    // Key press listener for early termination of the inner loop.
                    try {
                        if (System.in.available() > 0) {
                            while (System.in.available() > 0) {
                                char rr = (char) System.in.read();
                                if (rr == '+' ) {
                                    // Add 1 to repetitions.
                                    repetitions++;
                                } else if (!(""+rr).trim().isEmpty()) {
                                    System.out.println("Key press detected. Early termination of Phase 2 inner loop.");
                                    break outerRoundLoop;
                                }
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
                    Graph<Integer, DefaultEdge> candGraph = buildGraphFromCombinedGen(newCandidate, directed);

                    // Enforce all simple cycles have length multiple of N (if enabled)
                    if (!allEdgeCyclesAreMultiples(candGraph, enforceLoopMultiples)) {
                        continue;
                    }

                    // Check for isomorphic duplicates.
                    boolean duplicate = false;
                    String canonicalLabeling = null;
                    if (JBLISS) {
                        fi.tkk.ics.jbliss.Graph<Integer> jblissGraph = buildJblissGraphFromCombinedGen(newCandidate, directed);
                        canonicalLabeling = getCanonicalGraph(jblissGraph);
                        if (canonicalGraphs.contains(canonicalLabeling)) {
                            duplicate = true;
                            break;
                        }
                    } else {
                        for (Graph<Integer, DefaultEdge> g : newCandidateGraphs) {
                            VF2GraphIsomorphismInspector<Integer, DefaultEdge> inspector =
                                    new VF2GraphIsomorphismInspector<>(candGraph, g);
                            if (inspector.isomorphismExists()) {
                                duplicate = true;
                                break;
                            }
                        }
                    }
                    if (duplicate) continue;

                    // Check for genus 1 if required.
                    if (discardOverGenus1 && !(checkPlanarity(newCandidate) || checkGenus1(newCandidate))) {
                        continue;
                    }
                    
                    String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(newCandidate), 2).get(1).trim();
                    System.out.println("Found new "+(!requirePlanar ? "non-" : "")+"planar graph " + found[r] + " with order " + size);
                    
                    newCandidates.add(newCandidate);
                    newCandidateGraphs.add(candGraph);
                    if (JBLISS) canonicalGraphs.add(canonicalLabeling);
                    if (size.equals(String.valueOf(order))) {
                        phase2RoundOut.println(GroupExplorer.generatorsToString(newCandidate));
                        found[r]++;
                    }
                    roundCount++;
                }
                System.out.println("Completed inner loop for candidate " + i + " of " + currentCandidates.size());
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
    
    public static boolean checkGenus1(int[][][] combinedGen) {
        String genus = MultiGenus.computeGenusFromGenerators(
            Arrays.<int[][][]>asList(combinedGen),
            MultiGenus.MultiGenusOption.LIMIT_TO_GENUS_1).get(0) + "";
        
        if (genus.equals("-1")) {
            return false;
        }
        return true;
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

    public static fi.tkk.ics.jbliss.Graph<Integer> buildJblissGraphFromCombinedGen(int[][][] combinedGen, boolean directed) {
        fi.tkk.ics.jbliss.Graph<Integer> graph = new fi.tkk.ics.jbliss.Graph<Integer>();
        HashSet<Integer> vertices = new HashSet<>();
        HashSet<Long> edges = new HashSet<>();
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                for (int vertex : polygon) {
                    if (!vertices.contains(vertex)) {
                        vertices.add(vertex);
                        graph.add_vertex(vertex);
                    }
                }
                for (int i = 0; i < polygon.length; i++) {
                    int v1 = polygon[i], v2 = polygon[(i + 1) % polygon.length];
                    long edgeCache = ((long) v1 << 31) | v2;
                    if (!edges.contains(edgeCache)) {
                        edges.add(edgeCache);
                        graph.add_edge(v1, v2);
                        if (!directed || polygon.length == 2) {
                            graph.add_edge(v2, v1);
                        }
                    }
                }
            }
        }
        return graph;
    }

    public static String getCanonicalGraph(fi.tkk.ics.jbliss.Graph<Integer> graph) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        DefaultReporter reporter = new DefaultReporter();
        reporter.stream = new PrintStream(new OutputStream() {
            @Override public void write(int b) throws IOException {}
            @Override public void write(byte[] b) throws IOException {}
            @Override public void write(byte[] b, int off, int len) throws IOException {}
            @Override public void flush() throws IOException {}
            @Override public void close() throws IOException {}
        });

        graph.find_automorphisms(reporter, null);
        Map<Integer, Integer> canonicalLabeling = graph.canonical_labeling();
        
        graph.relabel(canonicalLabeling).write_dot(ps);
        ps.flush();
        return new String(baos.toByteArray());
    }

    private static boolean allEdgeCyclesAreMultiples(Graph<Integer, DefaultEdge> graph, int k) {
        if (k <= 1) return true;

        // Work in undirected sense for edge cycles
        Graph<Integer, DefaultEdge> undirected = new SimpleGraph<>(DefaultEdge.class);
        for (Integer v : graph.vertexSet()) undirected.addVertex(v);
        for (DefaultEdge e : graph.edgeSet()) {
            Integer u = graph.getEdgeSource(e);
            Integer v = graph.getEdgeTarget(e);
            if (!undirected.containsEdge(u, v) && !undirected.containsEdge(v, u)) {
                undirected.addEdge(u, v);
            }
        }

        if (k == 2) {
            return isBipartite(undirected); // even-cycle condition
        }

        // Enumerate simple cycles using Johnson over a directed expansion
        Graph<Integer, DefaultEdge> dir = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (Integer v : undirected.vertexSet()) dir.addVertex(v);
        for (DefaultEdge e : undirected.edgeSet()) {
            Integer u = undirected.getEdgeSource(e);
            Integer v = undirected.getEdgeTarget(e);
            if (!dir.containsEdge(u, v)) dir.addEdge(u, v);
            if (!dir.containsEdge(v, u)) dir.addEdge(v, u);
        }

        JohnsonSimpleCycles<Integer, DefaultEdge> jc = new JohnsonSimpleCycles<>(dir);
        List<List<Integer>> cycles = jc.findSimpleCycles();
        for (List<Integer> cyc : cycles) {
            if (cyc.size() > 2 && cyc.size() % k != 0) return false;
        }
        return true;
    }

    private static boolean isBipartite(Graph<Integer, DefaultEdge> graph) {
        Map<Integer, Integer> color = new HashMap<>();
        Queue<Integer> q = new ArrayDeque<>();
        for (Integer s : graph.vertexSet()) {
            if (color.containsKey(s)) continue;
            color.put(s, 0);
            q.add(s);
            while (!q.isEmpty()) {
                Integer u = q.remove();
                for (DefaultEdge e : graph.edgesOf(u)) {
                    Integer v = Graphs.getOppositeVertex(graph, e, u);
                    Integer cv = color.get(v);
                    if (cv == null) {
                        color.put(v, 1 - color.get(u));
                        q.add(v);
                    } else if (cv.intValue() == color.get(u)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
} 