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

public class PlanarStudy {
    static {
        loadJBliss();
    }

    public static void main(String[] args) throws IOException {
        // --------------------------------------------------------
        // Preview mode.
        // --------------------------------------------------------
        boolean previewOnly = false;
        // --------------------------------------------------------
        // Configuration variables
        // --------------------------------------------------------
        int MAX_DUPLICATE_POLYGONS = 0; // Useful for allowing overlapping 2-cycles
        boolean allowSubgroups = true; // Allow searching subgroup graph candidates - this should always be true
        boolean requirePlanar = true; // Require the graphs to be planar / polyhedral
        boolean discardOverGenus1 = true; // If not requiring planar, this will discard graphs with genus > 1
        int enforceLoopMultiples = 1; // For planar grid stuff, set to 1 for normal operation
        boolean generate = true; // Generate the cycle lists?  If you've already generated them set to false to save time
        int repetitions = 4; // Change to 2 (or higher) for additional rounds (e.g., quadruple generation for 2).
        
        boolean directed = true; // Set to false to filter out isomorphic undirected duplicates.  This can speed things up if there are tons of results

        MemorySettings mem = MemorySettings.COMPACT;

        // We use two cycle descriptions for the candidate pairs.

        // Either use a complete description like "6p 3-cycles" or a partial description like "5-cycles" for 5-cycles only
        String[] conj = new String[] {
            "3-cycles",  "3-cycles"
        };
        // For phase 1, we use two different files (indices 0 and 1).
        int[] phase1Indices = new int[]{0,1};
        int[] phase2Indices = new int[]{1};

        String generator = Generators.m11_12pt;
        String groupName = "m11_12pt";

        // Print configuration
        System.out.println("Group: " + groupName);
        System.out.println("Generator: " + generator);
        System.out.println("  Conjugacy classes: " + Arrays.toString(conj));
        System.out.println("    Phase 1 indices: " + Arrays.toString(phase1Indices));
        System.out.println("    Phase 2 indices: " + Arrays.toString(phase2Indices));
        System.out.println("Max duplicate polygons: " + MAX_DUPLICATE_POLYGONS);
        System.out.println("Planar: " + requirePlanar);
        System.out.println("Toroidal: " + discardOverGenus1);
        System.out.println("Loop multiples: " + enforceLoopMultiples);
        System.out.println("Directed: " + directed);
        System.out.println("Generate: " + generate);

        File root = new File("PlanarStudy/" + groupName);
        root.mkdirs();

        long order = -1;

        // --------------------------------------------------------
        // Generation branch: generate input files if needed.
        // --------------------------------------------------------
        if (generate &&
                      !groupName.startsWith("u4_3_") &&
                      !groupName.startsWith("o8p2") &&
                      !groupName.startsWith("o8m2") &&
                      !groupName.startsWith("hs") &&
                      !groupName.startsWith("mcl") &&
                      !groupName.startsWith("co3") &&
                      !generator.equals(Generators.m24)) {
            boolean multithread = true;
            PrintStream[] filesOut = new PrintStream[conj.length];
            for (int i = 0; i < conj.length; i++) {
                filesOut[i] = new PrintStream(root.getAbsolutePath() + "/" + conj[i] + ".txt");
            }
            
            GroupExplorer g = new GroupExplorer(generator, mem, new HashSet<>(), new HashSet<>(), new HashSet<>(), multithread);
            Generators.exploreGroup(g, (state, description) -> {
                for (int i = 0; i < conj.length; i++) {
                    boolean match = conjMatches(conj[i], description);
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
            order = Long.parseLong(orderS);
        }


        // --------------------------------------------------------
        // Phase 1: Pair Filtering
        // --------------------------------------------------------
        System.out.println("Starting Phase 1: Pair Filtering");

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
        PrintStream phase1Out = new PrintStream(root.getAbsolutePath() + "/" + (MAX_DUPLICATE_POLYGONS > 0 ? "d" + MAX_DUPLICATE_POLYGONS + "-" : "") + (enforceLoopMultiples > 1 ? "l" + enforceLoopMultiples + "-" : "") + (requirePlanar ? "" : discardOverGenus1 ? "torus-" : "np-") + conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-filtered.txt");
        int[] found = new int[repetitions + 1];

        int p1_1_count = 0;

        String allConjClasses = gap.getConjugacyClasses(generator);
        int nPoints = 0;
        for (int[][] x : GroupExplorer.parseOperations(generator)) {
            for (int[] y : x) {
                for (int z : y) {
                    nPoints = Math.max(nPoints, z);
                }
            }
        }

        // Get an exemplary cycle from each matching conjugacy class
        List<String> lines1 = new ArrayList<>();

        
        List<int[][]> conjClasses = GroupExplorer.parseOperations(allConjClasses);
        for (int[][] x : conjClasses) {
            String y = GroupExplorer.describeCycles(nPoints, x);

            if (conjMatches(conj[0], y)) {
                lines1.add(GroupExplorer.cyclesToNotation(x));
                System.out.println("  Using phase 1 conjugacy class "+lines1.size()+": " + y);
            }
        }

        // Nested loops over the two lists with early termination support.
        phase1Loop: for (String l1 : lines1) {
            int p1_2_count = 0;
            p1_1_count++;
            System.out.println("  Searching conjugacy class " + p1_1_count + " / " + lines1.size());
            int[][][] parsed1 = GroupExplorer.parseOperationsArr(l1);
            GroupExplorer ge = new GroupExplorer(generator, mem, new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
            ge.applyOperation(parsed1[0], false);
            int[] l1State = ge.copyCurrentState();
            int[][] firstCandidate = parsed1[0]; // use the first generator set from file1.
            for (String l2 : lines2) {
                int[][][] parsed2 = GroupExplorer.parseOperationsArr(l2);
                int[][] secondCandidate = parsed2[0]; // use the first generator set from file2.
                ge.resetElements(false);
                ge.applyOperation(parsed2[0], false);
                int[] l2State = ge.copyCurrentState();

                p1_2_count++;
                // Check if
                if (Arrays.equals(l1State, l2State)) continue;
                // Check for a key press to allow early termination of Phase 1 filtering.
                try {
                    if (System.in.available() > 0) {
                        System.out.println("Key press detected. Early termination of Phase 1 filtering.");
                        while (System.in.available() > 0) {
                            System.in.read();
                        }
                        System.out.println("p1_1_count: " + p1_1_count + " / " + lines1.size());
                        System.out.println("p1_2_count: " + p1_2_count + " / " + lines2.size());
                        continue phase1Loop;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Combine the two generators into a pair.
                int[][][] combinedPair = new int[][][]{firstCandidate, secondCandidate};
                
                // Check for duplicate polygons (e.g. [1,2,3] vs [2,1,3]).
                if (hasDuplicatePolygon(combinedPair, MAX_DUPLICATE_POLYGONS)) {
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

                String canonicalLabeling = null;

                // Check for isomorphic duplicates.
                fi.tkk.ics.jbliss.Graph<Integer> jblissGraph = buildJblissGraphFromCombinedGen(combinedPair, directed);
                canonicalLabeling = getCanonicalGraph(jblissGraph);
                if (canonicalGraphs.contains(canonicalLabeling)) {
                    continue;
                }

                // Enforce all simple cycles have length multiple of N (if enabled)
                if (!allEdgeCyclesAreMultiples(candGraph, enforceLoopMultiples)) {
                    continue;
                }
                
                //if (Math.random() < 0.01) Collections.shuffle(pairGraphs);
                if (size == null) size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedPair), 2).get(1).trim();
                
                candidatePairs.add(combinedPair);
                pairGraphs.add(candGraph);
                canonicalGraphs.add(canonicalLabeling);
                if (size.equals(String.valueOf(order))) {
                    phase1Out.println(GroupExplorer.generatorsToString(combinedPair));
                    found[0]++;
                }

                System.out.println("    Found new "+(!requirePlanar ? "non-" : "")+"planar graph with order " + size + " - " + found[0] + " results and " + candidatePairs.size() + " candidates");
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
        String baseFileName =(MAX_DUPLICATE_POLYGONS > 0 ? "d" + MAX_DUPLICATE_POLYGONS + "-" : "") + (enforceLoopMultiples > 1 ? "l" + enforceLoopMultiples + "-" : "") +(requirePlanar ? "" : discardOverGenus1 ? "torus-" : "np-") + conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-" + conj[phase2Indices[0]];
        
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
                    if (Arrays.deepEquals(candidate[0], parsedGen[0])) continue;
                    if (Arrays.deepEquals(candidate[1], parsedGen[0])) continue;
                    int[][] newGenerator = parsedGen[0];
                    // Build the new candidate by appending newGenerator to the current candidate.
                    int currentLen = candidate.length;
                    int[][][] newCandidate = new int[currentLen + 1][][];
                    for (int j = 0; j < currentLen; j++) {
                        newCandidate[j] = candidate[j];
                    }
                    newCandidate[currentLen] = newGenerator;
                    // Check for duplicate polygons in the new candidate.
                    if (hasDuplicatePolygon(newCandidate, MAX_DUPLICATE_POLYGONS)) {
                        continue;
                    }
                    // Check planarity if required.
                    if (requirePlanar && !checkPlanarity(newCandidate)) {
                        continue;
                    }
                    Graph<Integer, DefaultEdge> candGraph = buildGraphFromCombinedGen(newCandidate, directed);

                    // Check for isomorphic duplicates.
                    String canonicalLabeling = null;
                    fi.tkk.ics.jbliss.Graph<Integer> jblissGraph = buildJblissGraphFromCombinedGen(newCandidate, directed);
                    canonicalLabeling = getCanonicalGraph(jblissGraph);
                    if (canonicalGraphs.contains(canonicalLabeling)) {
                        continue;
                    }
                    
                    // Enforce all simple cycles have length multiple of N (if enabled)
                    if (!allEdgeCyclesAreMultiples(candGraph, enforceLoopMultiples)) {
                        continue;
                    }

                    // Check for genus 1 if required.
                    if (discardOverGenus1 && !(checkPlanarity(newCandidate) || checkGenus1(newCandidate))) {
                        continue;
                    }
                    
                    String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(newCandidate), 2).get(1).trim();
                    
                    newCandidates.add(newCandidate);
                    newCandidateGraphs.add(candGraph);
                    canonicalGraphs.add(canonicalLabeling);
                    if (size.equals(String.valueOf(order))) {
                        phase2RoundOut.println(GroupExplorer.generatorsToString(newCandidate));
                        found[r]++;
                    }
                    roundCount++;   

                    System.out.println("    Found new "+(!requirePlanar ? "non-" : "")+"planar graph with order " + size + " - " + found[r] + " results and " + newCandidates.size() + " candidates");

                }
                System.out.println("  Completed inner loop for candidate " + i + " of " + currentCandidates.size());
            }
            phase2RoundOut.close();
            System.out.println("Round " + r + " completed. Unique new candidates: " + roundCount);
            currentCandidates = newCandidates;
        }
        System.out.println("Phase 2 completed after " + repetitions + " round(s). Final candidate count: " + currentCandidates.size() + " - order " + order + " found: " + Arrays.toString(found));
    }

    private static void loadJBliss() {
        // Check if we're on linux amd64

        if (System.getProperty("os.name").toLowerCase().contains("linux") && System.getProperty("os.arch").toLowerCase().contains("amd64")) {
            File jbliss = new File("lib/libjbliss.so");
            if (!jbliss.exists()) {
                System.out.println("JBliss library not found.");
                System.exit(1);
            }
            System.load(jbliss.getAbsolutePath());
        } else {
            System.out.println("JBliss library not found. Only Linux amd64 is pre-compiled.");
            System.exit(1);
        }
        
    }

    private static boolean conjMatches(String conj, String description) {
        boolean match = false;
        if (conj.contains(" ")) {
            match = description.equals(conj);
        } else {
            if (conj.contains("+")) {
                match = description.contains(conj.replace("+", ""));
            } else {
                match = !description.contains(",") && description.endsWith(" " + conj);
            }
        }
        return match;
    }
    
    /**
     * Checks whether any duplicate polygon appears in the combined generator array.
     * Two polygons are considered duplicates if their canonical (sorted) representation is equal.
     */
    private static boolean hasDuplicatePolygon(int[][][] combinedGen, int maxAllowed) {
        int count = 0;
        Set<String> set = new HashSet<>();
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                String canon = canonicalPolygon(polygon);
                if (!set.add(canon)) {
                    count++;
                    if (count > maxAllowed) {
                        return true;
                    }
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
        //HashSet<Long> edges = new HashSet<>();
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
                    //long edgeCache = ((long) v1 << 31) | v2;
                    //if (!edges.contains(edgeCache)) {
                       // edges.add(edgeCache);
                        graph.add_edge(v1, v2);
                        if (!directed || polygon.length == 2) {
                            graph.add_edge(v2, v1);
                        }
                    //}
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