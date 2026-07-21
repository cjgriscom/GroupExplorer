package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

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
    private static final String DREADNAUT_PATH = 
        "dreadnaut";
    // Switch between nauty (Ad) and Traces (At) when calling dreadnaut.
    // Traces cannot handle directed graphs. If enabled but a batch contains any directed graphs,
    // fall back to nauty.
    private static final boolean USE_TRACES = true;
    /** Kill dreadnaut processes running longer than this (seconds); 0 disables the watchdog. */
    private static final int DREADNAUT_WATCHDOG_TIMEOUT_SECONDS = 5*60;
    private static final int DREADNAUT_WATCHDOG_INTERVAL_SECONDS = 10;
    private static final String DREADNAUT_COMM = new File(DREADNAUT_PATH).getName();

    public static void main(String[] args) throws IOException {
        // --------------------------------------------------------
        // Preview mode.
        // --------------------------------------------------------
        boolean previewOnly = false;
        // --------------------------------------------------------
        // Configuration variables
        // --------------------------------------------------------
        int MAX_DUPLICATE_POLYGONS = 300; // Useful for allowing overlapping 2-cycles
        boolean allowSubgroups = true; // Allow searching subgroup graph candidates - this should always be true
        boolean requirePlanar = false; // Require the graphs to be planar / polyhedral
        int discardOverGenusN = 0; // If not requiring planar, this will discard graphs with genus > N.  If 0, ignore genus.
        int enforceLoopMultiples = 0; // For planar grid stuff, set to 1 for normal operation
        long minGeometryAutOrder = 1; // Minimum |Aut(geometry)|; 1 disables this filter
        long geometryAutOrderModulus = 1; // If >1, require |Aut(geometry)| ≡ geometryAutOrderRemainder (mod modulus)
        boolean generate = true; // Generate the cycle lists?  If you've already generated them set to false to save time
        int repetitions = 1; // Change to 2 (or higher) for additional rounds (e.g., quadruple generation for 2).
        boolean SORT_CANDIDATES = true; // sort Phase 1 pairs before Phase 2 for stable indices
        int resumePhase2FromCandidate = 0; // 0 = normal full run, n = start from final results candidate n
        String resumePhase2ResultsFile = ""; // empty = no seed, or final results filename like "d30-np-2-cycles-2-cycles-2-cycles_R1-filtered.txt"
        
        boolean directed = true; // Set to false to filter out isomorphic undirected duplicates.  This can speed things up if there are tons of results

        MemorySettings mem = MemorySettings.COMPRESS_LONG;

        // We use two cycle descriptions for the candidate pairs.

        // Either use a complete description like "6p 3-cycles" or a partial description like "5-cycles" for 5-cycles only
        String[] conj = new String[] {
            "2-cycles",  "2-cycles"
        };
        // For phase 1, we use two different files (indices 0 and 1).
        int[] phase1Indices = new int[]{0,1};
        int[] phase2Indices = new int[]{1};

        String generator = Generators.he2; 
        String groupName = "he2";

        // Print configuration
        System.out.println("Group: " + groupName);
        System.out.println("Generator: " + generator);
        System.out.println("  Conjugacy classes: " + Arrays.toString(conj));
        System.out.println("    Phase 1 indices: " + Arrays.toString(phase1Indices));
        System.out.println("    Phase 2 indices: " + Arrays.toString(phase2Indices));
        System.out.println("Max duplicate polygons: " + MAX_DUPLICATE_POLYGONS);
        System.out.println("Planar: " + requirePlanar);
        if (!requirePlanar) System.out.println("Max genus: " + discardOverGenusN);
        System.out.println("Loop multiples: " + enforceLoopMultiples);
        System.out.println("Min geometry Aut(G) order: " + minGeometryAutOrder);
        System.out.println("Directed: " + directed);
        System.out.println("Generate: " + generate);
        System.out.println("Sort candidates: " + SORT_CANDIDATES);
        System.out.println("Resume Phase 2 from candidate: " + resumePhase2FromCandidate);
        System.out.println("Resume Phase 2 results file: " +
            (resumePhase2ResultsFile == null || resumePhase2ResultsFile.isEmpty() ? "(none)" : resumePhase2ResultsFile));
        System.out.println("Dreadnaut watchdog timeout: " +
            (DREADNAUT_WATCHDOG_TIMEOUT_SECONDS <= 0 || System.getProperty("disableDreadnautWatchdog") != null ? "disabled" : DREADNAUT_WATCHDOG_TIMEOUT_SECONDS + "s"));

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
                      !groupName.startsWith("we8") &&
                      !groupName.startsWith("hs") &&
                      !groupName.startsWith("mcl") &&
                      !groupName.startsWith("co3") &&
                      !groupName.startsWith("td42") &&
                      !groupName.startsWith("tf42") &&
                      !groupName.startsWith("l7_2") &&
                      !groupName.startsWith("sp_8_2") &&
                      !groupName.startsWith("l5_3") &&
                      !groupName.startsWith("tg") &&
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
        DreadnautWatchdog dreadnautWatchdog = new DreadnautWatchdog(DREADNAUT_WATCHDOG_TIMEOUT_SECONDS);
        dreadnautWatchdog.start();
        try {
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
        ThreadLocal<GapInterface> gapL = ThreadLocal.withInitial(() -> { try { return new GapInterface(); } catch (IOException e) { throw new RuntimeException("Failed to create GapInterface", e); } });
        ThreadLocal<DreadnautInterface> dreadnautL = ThreadLocal.withInitial(() -> new DreadnautInterface(DREADNAUT_PATH, USE_TRACES));

        // Create a list to save unique candidate pairs.
        List<int[][][]> candidatePairs = new ArrayList<>();
        List<Graph<Integer, DefaultEdge>> pairGraphs = new ArrayList<>();
        Set<String> canonicalGraphs = Collections.synchronizedSet(new HashSet<>());
        Object phase1Lock = new Object();
        String geomAutTagCore = "";
        if (minGeometryAutOrder > 1L) {
            geomAutTagCore += "g" + minGeometryAutOrder;
        }
        if (geometryAutOrderModulus > 1L) {
            if (!geomAutTagCore.isEmpty()) {
                geomAutTagCore += "_";
            }
            geomAutTagCore += "gm" + geometryAutOrderModulus;
        }
        String geomAutTag = geomAutTagCore.isEmpty() ? "" : geomAutTagCore + "-";
        String phase1FilePath =
            root.getAbsolutePath() + "/" +
            (MAX_DUPLICATE_POLYGONS > 0 ? "d" + MAX_DUPLICATE_POLYGONS + "-" : "") +
            (enforceLoopMultiples > 1 ? "l" + enforceLoopMultiples + "-" : "") +
            (requirePlanar ? "" : discardOverGenusN == 1 ? "torus-" : (discardOverGenusN == 0 ? "np-" : "np" + discardOverGenusN + "-")) +
            geomAutTag +
            conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-filtered.txt";
        PrintStream phase1Out = new PrintStream(phase1FilePath);
        QuarantineLog phase1Quarantine = new QuarantineLog(phase1FilePath);
        int[] found = new int[repetitions + 1];

        AtomicInteger p1_1_count = new AtomicInteger(0);

        String allConjClasses = gapL.get().getConjugacyClasses(generator);
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
        for (String l1 : lines1) {
            AtomicInteger p1_2_count = new AtomicInteger(0);
            p1_1_count.incrementAndGet();
            System.out.println("  Searching conjugacy class " + p1_1_count + " / " + lines1.size());
            int[][][] parsed1 = GroupExplorer.parseOperationsArr(l1);

            ThreadLocal<GroupExplorer> geL = ThreadLocal.withInitial(() -> {
                GroupExplorer geT = new GroupExplorer(generator, mem, new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
                geT.applyOperation(parsed1[0], false);
                return geT;
            });
            int[] l1State = geL.get().copyCurrentState();
            int[][] firstCandidate = parsed1[0]; // use the first generator set from file1.

            AtomicBoolean earlyTermination = new AtomicBoolean(false);

            long orderFinal = order;

            lines2.parallelStream().forEach(l2 -> {
                if (earlyTermination.get()) return;
                GroupExplorer ge = geL.get();
                int[][][] parsed2 = GroupExplorer.parseOperationsArr(l2);
                int[][] secondCandidate = parsed2[0]; // use the first generator set from file2.
                ge.resetElements(false);
                ge.applyOperation(parsed2[0], false);
                int[] l2State = ge.copyCurrentState();

                p1_2_count.incrementAndGet();
                // Check if
                if (Arrays.equals(l1State, l2State)) return;
                // Check for a key press to allow early termination of Phase 1 filtering.
                try {
                    if (System.in.available() > 0) {
                        System.out.println("Key press detected. Early termination of Phase 1 filtering.");
                        while (System.in.available() > 0) {
                            System.in.read();
                        }
                        earlyTermination.set(true);
                        System.out.println("p1_1_count: " + p1_1_count.get() + " / " + lines1.size());
                        System.out.println("p1_2_count: " + p1_2_count.get() + " / " + lines2.size());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Combine the two generators into a pair.
                int[][][] combinedPair = new int[][][]{firstCandidate, secondCandidate};
                
                // Check for duplicate polygons (e.g. [1,2,3] vs [2,1,3]).
                if (hasDuplicatePolygon(combinedPair, MAX_DUPLICATE_POLYGONS)) {
                    return;
                }
                
                // Check planarity if required.
                if (requirePlanar && !checkPlanarity(combinedPair)) {
                    return;
                }

                String size = null;
                if (!allowSubgroups) {
                    size = gapL.get().runGapSizeCommand(GroupExplorer.generatorsToString(combinedPair), 2).get(1).trim();
                    if (!size.equals(String.valueOf(orderFinal))) {
                        return;
                    }
                }

                // Check for genus 1 if required.
                if (discardOverGenusN > 0 && !(checkPlanarity(combinedPair) || checkGenusN(discardOverGenusN, combinedPair))) {
                    return;
                }

                Graph<Integer, DefaultEdge> candGraph = buildGraphFromCombinedGen(combinedPair, directed);

                // Check for isomorphic duplicates.
                String canonicalLabeling = getCanonicalLabelingOrQuarantine(
                    dreadnautL.get(), combinedPair, directed, phase1Quarantine, null);
                if (canonicalLabeling == null) {
                    return;
                }
                synchronized (canonicalGraphs) {
                    if (canonicalGraphs.contains(canonicalLabeling)) {
                        return;
                    }
                }

                // Enforce all simple cycles have length multiple of N (if enabled)
                if (!allEdgeCyclesAreMultiples(candGraph, enforceLoopMultiples)) {
                    return;
                }
                
                //if (Math.random() < 0.01) Collections.shuffle(pairGraphs);
                if (size == null) {
                    size = gapL.get().runGapSizeCommand(GroupExplorer.generatorsToString(combinedPair), 2).get(1).trim();
                }

                // Only apply the geometry automorphism filters to final results (full group order).
                boolean passesGeometryFilter = true;
                if (size.equals(String.valueOf(orderFinal)) &&
                    (minGeometryAutOrder > 1L || geometryAutOrderModulus > 1L)) {
                    try {
                        BigInteger geomOrder = GraphSymm.automorphismGroupOrder(
                            candGraph,
                            false, // geometry is treated as undirected
                            DREADNAUT_PATH,
                            USE_TRACES
                        );
                        if (minGeometryAutOrder > 1L &&
                            geomOrder.compareTo(BigInteger.valueOf(minGeometryAutOrder)) < 0) {
                            passesGeometryFilter = false;
                        }
                        if (passesGeometryFilter && geometryAutOrderModulus > 1L) {
                            BigInteger mod = BigInteger.valueOf(geometryAutOrderModulus);
                            if (!geomOrder.mod(mod).equals(BigInteger.ZERO)) {
                                passesGeometryFilter = false;
                            }
                        }
                    } catch (RuntimeException e) {
                        maybeQuarantineDreadnautFailure(phase1Quarantine, combinedPair, e);
                        System.err.println("Failed to compute geometry automorphism order: " + e.getMessage());
                        passesGeometryFilter = false;
                    }
                }
                final boolean passesGeometryFilterFinal = passesGeometryFilter;

                synchronized (phase1Lock) {
                    // Prevent duplicates while lock is released
                    if (!canonicalGraphs.add(canonicalLabeling)) return;
                    candidatePairs.add(combinedPair);
                    pairGraphs.add(candGraph);
                    if (size.equals(String.valueOf(orderFinal)) && passesGeometryFilterFinal) {
                        phase1Out.println(GroupExplorer.generatorsToString(combinedPair));
                        found[0]++;
                    }

                    System.out.println("    Found new "+(!requirePlanar ? "non-" : "")+"planar graph with order " + size + " - " + found[0] + " results and " + candidatePairs.size() + " candidates");
                }
            });
        }
        phase1Out.close();
        phase1Quarantine.close();
        System.out.println("Phase 1 completed. Unique candidate pairs: " + candidatePairs.size());
        if (SORT_CANDIDATES) {
            candidatePairs.sort(Comparator.comparing(pair -> GroupExplorer.generatorsToString(pair)));
            System.out.println("Sorted candidate pairs for stable Phase 2 indices: " + candidatePairs.size());
        }
        
        // --------------------------------------------------------
        // Phase 2: Repetitions-based candidate generation.
        // --------------------------------------------------------
        // 'repetitions' determines how many times Phase 2 is applied.
        // If repetitions == 1, then we generate triple candidates (as before).
        // If repetitions > 1, then we iteratively combine the candidates with new generators (from lines2)
        // without checking group order until the final round.
        AtomicInteger errors = new AtomicInteger(0);
        List<int[][][]> currentCandidates = new ArrayList<>(candidatePairs);
        // For output naming, build a base file name.
        String baseFileName =
            (MAX_DUPLICATE_POLYGONS > 0 ? "d" + MAX_DUPLICATE_POLYGONS + "-" : "") +
            (enforceLoopMultiples > 1 ? "l" + enforceLoopMultiples + "-" : "") +
            (requirePlanar ? "" : discardOverGenusN == 1 ? "torus-" : (discardOverGenusN == 0 ? "np-" : "np" + discardOverGenusN + "-")) +
            geomAutTag +
            conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-" + conj[phase2Indices[0]];
        
        for (int r = 1; r <= repetitions; r++) {
            boolean lastLoop = r == repetitions; 
            final int rFinal = r;
            final long orderFinal = order;
            System.out.println("Starting Phase 2, round " + r + "");
            List<int[][][]> newCandidates = new ArrayList<>();
            List<Graph<Integer, DefaultEdge>> newCandidateGraphs = new ArrayList<>();
            String roundFileName = baseFileName + "_R" + r + "-filtered.txt";
            String roundFilePath = root.getAbsolutePath() + "/" + roundFileName;
            File configuredResumeFile = (resumePhase2ResultsFile == null || resumePhase2ResultsFile.isEmpty())
                ? null
                : resolveResumeFile(root, resumePhase2ResultsFile);
            boolean resumeThisRound = lastLoop &&
                configuredResumeFile != null &&
                roundFileName.equals(configuredResumeFile.getName());
            QuarantineLog roundQuarantine = new QuarantineLog(roundFilePath);
            if (resumeThisRound) {
                if (!configuredResumeFile.isFile()) {
                    throw new IOException("Resume results file not found: " + configuredResumeFile.getAbsolutePath());
                }
                QuarantineLog seedQuarantine = new QuarantineLog(configuredResumeFile.getAbsolutePath());
                int seededLines = seedCanonicalGraphsFromResultsFile(
                    configuredResumeFile,
                    canonicalGraphs,
                    dreadnautL,
                    directed,
                    errors,
                    seedQuarantine
                );
                seedQuarantine.close();
                found[rFinal] = seededLines;
                System.out.println("Seeded canonical labels from " + configuredResumeFile.getName() +
                    " (" + seededLines + " result lines)");
            }
            int startCandidate = resumeThisRound ? resumePhase2FromCandidate : 0;
            if (startCandidate >= currentCandidates.size()) {
                System.out.println("Resume start index " + startCandidate +
                    " >= candidate count " + currentCandidates.size() + "; skipping round " + r);
                roundQuarantine.close();
                continue;
            }
            PrintStream phase2RoundOut = resumeThisRound
                ? new PrintStream(new FileOutputStream(roundFilePath, true))
                : new PrintStream(roundFilePath);
            if (resumeThisRound) {
                System.out.println("Appending to " + roundFileName + ", starting at candidate " + startCandidate +
                    " of " + currentCandidates.size());
            }
            int roundCount = 0;
            // For each candidate from the previous round, combine with each line from file3.
            for (int i = startCandidate; i < currentCandidates.size(); i++) {
                final int iDisp = i;
                final int sizeDisp = currentCandidates.size();
                int[][][] candidate = currentCandidates.get(i);
                AtomicBoolean earlyTerminationP2 = new AtomicBoolean(false);
                AtomicInteger roundCountAtomic = new AtomicInteger(0);

                // Inner loop: iterate over individual lines from file3 in parallel.
                lines3.parallelStream().forEach(l -> {
                    if (earlyTerminationP2.get()) return;
                    // Key press listener for early termination of the inner loop.
                    try {
                        if (System.in.available() > 0) {
                            while (System.in.available() > 0) {
                                char rr = (char) System.in.read();
                                if (!(""+rr).trim().isEmpty()) {
                                    System.out.println("Key press detected. Early termination of Phase 2 inner loop.");
                                    earlyTerminationP2.set(true);
                                }
                            }
                            earlyTerminationP2.set(true);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // Parse the line from file2 and use its generator (index 0).
                    int[][][] parsedGen = GroupExplorer.parseOperationsArr(l);
                    if (Arrays.deepEquals(candidate[0], parsedGen[0])) return;
                    if (Arrays.deepEquals(candidate[1], parsedGen[0])) return;
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
                        return;
                    }
                    // Check planarity if required.
                    if (requirePlanar && !checkPlanarity(newCandidate)) {
                        return;
                    }

                    String size = null;
                    if (!allowSubgroups) {
                        size = gapL.get().runGapSizeCommand(GroupExplorer.generatorsToString(newCandidate), 2).get(1).trim();
                        if (!size.equals(String.valueOf(orderFinal))) {
                            return;
                        }
                    }

                    Graph<Integer, DefaultEdge> candGraph = buildGraphFromCombinedGen(newCandidate, directed);

                    // Check for isomorphic duplicates.
                    String canonicalLabeling = getCanonicalLabelingOrQuarantine(
                        dreadnautL.get(), newCandidate, directed, roundQuarantine, errors);
                    if (canonicalLabeling == null) {
                        return;
                    }
                    synchronized (canonicalGraphs) {
                        if (canonicalGraphs.contains(canonicalLabeling)) {
                            return;
                        }
                    }
                    
                    // Enforce all simple cycles have length multiple of N (if enabled)
                    if (!allEdgeCyclesAreMultiples(candGraph, enforceLoopMultiples)) {
                        return;
                    }

                    // Check for genus 1 if required.
                    if (discardOverGenusN > 0 && !(checkPlanarity(newCandidate) || checkGenusN(discardOverGenusN, newCandidate))) {
                        return;
                    }
                    
                    if (size == null) {
                        size = gapL.get().runGapSizeCommand(GroupExplorer.generatorsToString(newCandidate), 2).get(1).trim();
                    }

                    // Only apply the geometry automorphism filters to final results (full group order).
                    boolean passesGeometryFilter = true;
                    if (size.equals(String.valueOf(orderFinal)) &&
                        (minGeometryAutOrder > 1L || geometryAutOrderModulus > 1L)) {
                        try {
                            BigInteger geomOrder = GraphSymm.automorphismGroupOrder(
                                candGraph,
                                false, // geometry is treated as undirected
                                DREADNAUT_PATH,
                                USE_TRACES
                            );
                            if (minGeometryAutOrder > 1L &&
                                geomOrder.compareTo(BigInteger.valueOf(minGeometryAutOrder)) < 0) {
                                passesGeometryFilter = false;
                            }
                            if (passesGeometryFilter && geometryAutOrderModulus > 1L) {
                                BigInteger mod = BigInteger.valueOf(geometryAutOrderModulus);
                                if (!geomOrder.mod(mod).equals(BigInteger.ZERO)) {
                                    passesGeometryFilter = false;
                                }
                            }
                        } catch (RuntimeException e) {
                            maybeQuarantineDreadnautFailure(roundQuarantine, newCandidate, e);
                            System.err.println("Failed to compute geometry automorphism order: " + e.getMessage());
                            passesGeometryFilter = false;
                        }
                    }
                    final boolean passesGeometryFilterFinal = passesGeometryFilter;

                    synchronized (phase1Lock) {
                        // Prevent duplicates while lock is released
                        if (!canonicalGraphs.add(canonicalLabeling)) return;
                        if (!lastLoop) newCandidates.add(newCandidate); // COMMENT OUT if too many candidates
                        if (!lastLoop) newCandidateGraphs.add(candGraph);
                        if (size.equals(String.valueOf(orderFinal)) && passesGeometryFilterFinal) {
                            phase2RoundOut.println(GroupExplorer.generatorsToString(newCandidate));
                            found[rFinal]++;
                            System.out.println("    ("+(iDisp)+"/"+sizeDisp+") Found new "+(!requirePlanar ? "non-" : "")+"planar graph with order " + size + " - " + found[rFinal] + " results and " + newCandidates.size() + " candidates");

                        }
                        roundCountAtomic.incrementAndGet();

                    }
                });
                roundCount += roundCountAtomic.get();
                System.out.println("  Completed inner loop for candidate " + i + " of " + currentCandidates.size());
            }
            phase2RoundOut.close();
            roundQuarantine.close();
            System.out.println("Round " + r + " completed. Unique new candidates: " + roundCount);
            currentCandidates = newCandidates;
        }
        System.out.println("Phase 2 completed after " + repetitions + " round(s). Final candidate count: " + currentCandidates.size() + " - order " + order + " found: " + Arrays.toString(found));
        System.out.println("Errors: " + errors);
        } finally {
            dreadnautWatchdog.stop();
        }
    }

    /**
     * Background thread that periodically finds dreadnaut processes running longer
     * than {@link #DREADNAUT_WATCHDOG_TIMEOUT_SECONDS} and sends {@code kill PID}.
     */
    private static final class DreadnautWatchdog {
        private final int timeoutSeconds;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread thread;

        DreadnautWatchdog(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        void start() {
            if (timeoutSeconds <= 0) {
                return;
            }
            running.set(true);
            thread = new Thread(this::runLoop, "dreadnaut-watchdog");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running.set(false);
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void runLoop() {
            while (running.get()) {
                try {
                    killStaleProcesses();
                    Thread.sleep(DREADNAUT_WATCHDOG_INTERVAL_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    if (!running.get()) {
                        break;
                    }
                }
            }
        }

        private void killStaleProcesses() {
            for (String pid : listDreadnautPids()) {
                int elapsed = getProcessElapsedSeconds(pid);
                if (elapsed >= 0 && elapsed > timeoutSeconds) {
                    killProcess(pid, elapsed);
                }
            }
        }

        private List<String> listDreadnautPids() {
            List<String> pids = new ArrayList<>();
            try {
                Process process = new ProcessBuilder("pgrep", "-x", DREADNAUT_COMM).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            pids.add(line);
                        }
                    }
                }
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                System.err.println("Dreadnaut watchdog: failed to list processes: " + e.getMessage());
            }
            return pids;
        }

        private int getProcessElapsedSeconds(String pid) {
            try {
                Process process = new ProcessBuilder("ps", "-o", "etimes=", "-p", pid).start();
                String elapsedLine;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    elapsedLine = reader.readLine();
                }
                process.waitFor();
                if (elapsedLine == null) {
                    return -1;
                }
                return Integer.parseInt(elapsedLine.trim());
            } catch (IOException | InterruptedException | NumberFormatException e) {
                System.err.println("Dreadnaut watchdog: failed to read elapsed time for pid " + pid + ": " + e.getMessage());
                return -1;
            }
        }

        private void killProcess(String pid, int elapsedSeconds) {
            try {
                System.err.println("Dreadnaut watchdog: killing pid " + pid +
                    " (running " + elapsedSeconds + "s, limit " + timeoutSeconds + "s)");
                Process process = new ProcessBuilder("kill", pid).start();
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                System.err.println("Dreadnaut watchdog: failed to kill pid " + pid + ": " + e.getMessage());
            }
        }
    }

    /**
     * Lazily opened gzipped log for graphs that cause dreadnaut to exit abnormally.
     * The file is {@code outputFilePath + ".quarantine.log.gz"} and is only created
     * when the first failure is recorded.
     */
    private static final class QuarantineLog {
        private final String quarantinePath;
        private PrintStream out;
        private final Object lock = new Object();

        QuarantineLog(String outputFilePath) {
            this.quarantinePath = outputFilePath + ".quarantine.log.gz";
        }

        void logFailure(String graph, String reason) {
            synchronized (lock) {
                if (out == null) {
                    try {
                        out = new PrintStream(new GZIPOutputStream(new FileOutputStream(quarantinePath)));
                    } catch (IOException e) {
                        System.err.println("Failed to open quarantine log " + quarantinePath + ": " + e.getMessage());
                        return;
                    }
                }
                out.println("# " + reason);
                out.println(graph);
            }
        }

        void close() {
            synchronized (lock) {
                if (out != null) {
                    out.close();
                    out = null;
                }
            }
        }
    }

    private static boolean isAbnormalDreadnautExit(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof IOException) {
                String msg = cur.getMessage();
                if (msg != null && msg.contains("dreadnaut exited with code")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void maybeQuarantineDreadnautFailure(
        QuarantineLog quarantine,
        int[][][] candidate,
        RuntimeException e
    ) {
        if (quarantine != null && isAbnormalDreadnautExit(e)) {
            quarantine.logFailure(GroupExplorer.generatorsToString(candidate), e.getMessage());
        }
    }

    private static String getCanonicalLabelingOrQuarantine(
        DreadnautInterface dreadnaut,
        int[][][] candidate,
        boolean directed,
        QuarantineLog quarantine,
        AtomicInteger errors
    ) {
        try {
            return dreadnaut.getCanonicalLabeling(candidate, directed);
        } catch (RuntimeException e) {
            if (isAbnormalDreadnautExit(e)) {
                quarantine.logFailure(GroupExplorer.generatorsToString(candidate), e.getMessage());
            } else {
                System.err.println("Failed to compute canonical labeling: " + e.getMessage());
            }
            if (errors != null) {
                errors.incrementAndGet();
            }
            return null;
        }
    }

    private static File resolveResumeFile(File root, String resumePhase2ResultsFile) {
        File resumeFile = new File(resumePhase2ResultsFile);
        if (!resumeFile.isAbsolute()) {
            resumeFile = new File(root, resumePhase2ResultsFile);
        }
        return resumeFile;
    }

    private static int seedCanonicalGraphsFromResultsFile(
        File resumeFile,
        Set<String> canonicalGraphs,
        ThreadLocal<DreadnautInterface> dreadnautL,
        boolean directed,
        AtomicInteger errors,
        QuarantineLog quarantine
    ) throws IOException {
        final int batchSize = 1000;
        final int progressInterval = 10000;
        AtomicInteger lineCount = new AtomicInteger(0);
        AtomicInteger uniqueLabels = new AtomicInteger(0);
        List<String> batch = new ArrayList<>(batchSize);
        try (BufferedReader reader = new BufferedReader(new FileReader(resumeFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                batch.add(line);
                if (batch.size() >= batchSize) {
                    processSeedBatch(batch, canonicalGraphs, dreadnautL, directed, errors, uniqueLabels, quarantine);
                    int lines = lineCount.addAndGet(batch.size());
                    if (lines / progressInterval > (lines - batch.size()) / progressInterval) {
                        System.out.println("  Seeding progress: " + lines + " lines, " +
                            uniqueLabels.get() + " unique canonical labels");
                    }
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                processSeedBatch(batch, canonicalGraphs, dreadnautL, directed, errors, uniqueLabels, quarantine);
                lineCount.addAndGet(batch.size());
            }
        }
        System.out.println("  Seeding complete: " + lineCount.get() + " lines, " +
            uniqueLabels.get() + " unique canonical labels");
        return lineCount.get();
    }

    private static void processSeedBatch(
        List<String> batch,
        Set<String> canonicalGraphs,
        ThreadLocal<DreadnautInterface> dreadnautL,
        boolean directed,
        AtomicInteger errors,
        AtomicInteger uniqueLabels,
        QuarantineLog quarantine
    ) {
        batch.parallelStream().forEach(line -> {
            int[][][] candidate = GroupExplorer.parseOperationsArr(line);
            String canonicalLabeling = getCanonicalLabelingOrQuarantine(
                dreadnautL.get(), candidate, directed, quarantine, errors);
            if (canonicalLabeling == null) {
                return;
            }
            synchronized (canonicalGraphs) {
                if (canonicalGraphs.add(canonicalLabeling)) {
                    uniqueLabels.incrementAndGet();
                }
            }
        });
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
    
    public static boolean checkGenusN(int N, int[][][] combinedGen) {
        if (N == 0) return true;
        String genus = MultiGenus.computeGenusFromGenerators(
                Arrays.<int[][][]>asList(combinedGen),
                new MultiGenus.ParameterizedMultiGenusOption(MultiGenus.MultiGenusOption.LIMIT_TO_GENUS_N, N)).get(0) + "";
        
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

    public static fi.tkk.ics.jbliss.AbstractGraph<Integer> buildJblissGraphFromCombinedGen(int[][][] combinedGen, boolean directed) {
        // check if all 2-cycles
        boolean all2Cycles = true;
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                if (polygon.length != 2) {
                    all2Cycles = false;
                    break;
                }
            }
        }
        if (all2Cycles) directed = false;
        fi.tkk.ics.jbliss.AbstractGraph<Integer> graph =
            directed ? new fi.tkk.ics.jbliss.Digraph<Integer>() : new fi.tkk.ics.jbliss.Graph<Integer>();
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                for (int i = 0; i < polygon.length; i++) {
                    int v1 = polygon[i], v2 = polygon[(i + 1) % polygon.length];
                    graph.add_edge(v1, v2);
                    if (!directed && polygon.length == 2) {
                        graph.add_edge(v2, v1);
                    }
                }
            }
        }
        return graph;
    }

    public static String getCanonicalGraph(fi.tkk.ics.jbliss.AbstractGraph<Integer> graph) {
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
