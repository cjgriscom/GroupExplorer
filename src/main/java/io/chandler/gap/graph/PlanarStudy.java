package io.chandler.gap.graph;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
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
import io.chandler.gap.PbinFile;
import io.chandler.gap.graph.filter.*;
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
    private static final String PHASE2_RECOVERY_FILENAME = "phase2-recovery.ser";

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
        
        int enforceLoopMultiples = 0; // For square/hex grid loop filtering, set to 0/1 for normal operation
        long minGeometryAutOrder = 1; // Minimum |Aut(geometry)|; 1 disables this filter
        long geometryAutOrderModulus = 1; // If >1, require |Aut(geometry)| ≡ geometryAutOrderRemainder (mod modulus)
        // On final result aggregation, also accept |G_result| = |G| / INCLUDE_QUOTIENT.
        // 0 or 1 = only full group order (normal behavior).
        int INCLUDE_QUOTIENT = 2;

        boolean directed = true; // Set to false to filter out isomorphic undirected duplicates.  This can speed things up if there are tons of results
        // Reject generator sets whose components have different cycle types (sorted cycle-length
        // multisets). Heuristic for "same conjugacy class" when conj[] uses partial descriptions
        // like "2-cycles" that span several classes (e.g. 8p vs 6p 2-cycles).
        boolean requireSameCycleType = false;
        boolean generate = true; // Generate the cycle lists?  If you've already generated them set to false to save time
        int repetitions = 1; // Change to 2 (or higher) for additional rounds (e.g., quadruple generation for 2).
        
        // We use two cycle descriptions for the candidate pairs.

        // Either use a complete description like "6p 3-cycles" or a partial description like "5-cycles" for 5-cycles only
        String[] conj = new String[] {
            "2-cycles",  "2-cycles"
        };
        // For phase 1, we use two different files (indices 0 and 1).
        int[] phase1Indices = new int[]{0,1};
        int[] phase2Indices = new int[]{1};

        String generator = Generators.suz2; 
        String groupName = "suz2";

        // Resume behavior
        boolean SORT_PH1_CANDIDATES = true; // sort Phase 1 pairs by canonical key before Phase 2 for stable indices
        int resumePhase2FromCandidate = 0; // 0 = normal full run, n = start from final results candidate n
        String resumePhase2ResultsFile = ""; // empty = no seed, or final results filename like "d30-np-2-cycles-2-cycles-2-cycles_R1-filtered.txt"
        // Load Phase 2 recovery checkpoint (written when interactive command recovery_on is active).
        // Skips Phase 1 and restores iso cache, round/candidate index, and candidate lists from phase2-recovery.ser.
        boolean loadRecovery = true;
        
        // Fixed study workers for Phase 1/2 (+ sort/seed). Caps ThreadLocal GAP/dreadnaut sessions.
        int studyThreads = Runtime.getRuntime().availableProcessors() + 2;
        int resultFilterQueueSize = 50000; // Max pending results in AbstractResultFilter before add() blocks
        AbstractResultFilter resultFilter;
        /*resultFilter = TrialityResultFilter.builder(resultFilterQueueSize)
            .referenceGroup(generator)
            .threads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2))
            .build();*/
        /* resultFilter = new NoOpResultFilter(resultFilterQueueSize); */
        
        boolean useGpuCongestionFilter = true; // set true to use CongestionResultFilterGPU
        // When a result-filter worker fails, print loudly and pause study workers (interactive resume).
        boolean pauseOnFilterFailure = true;
        if (useGpuCongestionFilter) {
            resultFilter = CongestionResultFilterGPU.builder(resultFilterQueueSize)
                .seeds(41, 129)
                .checkpoints(100,200,500,1000,1500)
                .thresholds(8.5,6.9,5.5,4.75,4.25)
                .nRotations(6)
                .batchSize(256)
                .batchWaitMs(30_000)
                .guardBand(0.1)
                .cpuOverflowThreads(studyThreads)
                .cpuOverflowQueueSize(150) // block study workers when CPU overflow backlog hits this
                .overflowBelowRemaining(512) // divert when GPU queue has <= this many free slots
                .build();
        } else {
            resultFilter = CongestionResultFilter.builder(resultFilterQueueSize)
                .seeds(41, 129)
                .checkpoints(100,200,500,1000,1500)
                .thresholds(8.5,6.9,5.5,4.75,4.25)
                .nRotations(6)
                .threads(Runtime.getRuntime().availableProcessors())
                .build();
        }
        ACTIVE_RESULT_FILTER = resultFilter;
        resultFilter.setPauseOnFailure(pauseOnFilterFailure);
        resultFilter.setFailurePauseHook(() -> {
            if (PAUSED.compareAndSet(false, true)) {
                System.err.println("Study PAUSED after result-filter failure (type 'resume' to continue)");
                System.err.flush();
            }
        });
        

        String filterName = "filtered";
        if (resultFilter != null) {
            filterName = resultFilter.shortFilterName();
        }

        // lastLoop only: dynamically choose GAP-then-dreadnaut vs dreadnaut-then-GAP.
        // Occasional dual-path probes pick the cheaper order for the rest of that candidate
        // (and optionally re-probe mid-candidate as the iso cache warms).
        boolean DYNAMIC_LASTLOOP_ORDER = true;
        int LASTLOOP_PROBE_SAMPLES = 24;           // disjoint-passing samples per probe window
        int LASTLOOP_PROBE_EVERY_CANDIDATES = 1;   // 1 = probe each candidate; raise to probe less often
        int LASTLOOP_REPROBE_EVERY_DISJOINT = 20000; // 0 = no mid-candidate re-probe

        MemorySettings mem = MemorySettings.COMPRESS_LONG;

        // Print configuration
        System.out.println("Group: " + groupName);
        System.out.println("Generator: " + generator);
        System.out.println("  Conjugacy classes: " + Arrays.toString(conj));
        System.out.println("    Phase 1 indices: " + Arrays.toString(phase1Indices));
        System.out.println("    Phase 2 indices: " + Arrays.toString(phase2Indices));
        System.out.println("Max duplicate polygons: " + MAX_DUPLICATE_POLYGONS);
        System.out.println("Planar: " + requirePlanar);
        if (!requirePlanar) System.out.println("Max genus: " + discardOverGenusN);
        System.out.println("Directed: " + directed);
        System.out.println("Require same cycle type: " + requireSameCycleType);
        System.out.println("Loop multiples: " + enforceLoopMultiples);
        System.out.println("Candidate graph: " +
            (NautyNative.hasTls() && enforceLoopMultiples <= 1 ? "native handle" : "JGraphT") +
            (NautyNative.hasTls() ? " (libnauty TLS)" : " (native unavailable)"));
        System.out.println("Min geometry Aut(G) order: " + minGeometryAutOrder);
        System.out.println("Include quotient: " + INCLUDE_QUOTIENT +
            (INCLUDE_QUOTIENT > 1 ? " (also accept |G|/" + INCLUDE_QUOTIENT + ")" : " (full order only)"));
        System.out.println("Result filter: " + resultFilter.getClass().getSimpleName() +
            " (queue size " + resultFilterQueueSize +
            ", threads " + resultFilter.threadCount() +
            ", pause_on_failure " + resultFilter.isPauseOnFailure() + ")");
        System.out.println("Study workers: " + studyThreads);
        System.out.println("Generate: " + generate);
        System.out.println("Sort phase 1 candidates: " + SORT_PH1_CANDIDATES);
        if (resumePhase2FromCandidate > 0) System.out.println("Resume Phase 2 from candidate: " + resumePhase2FromCandidate);
        if (resumePhase2FromCandidate > 0) System.out.println("Resume Phase 2 results file: " +
            (resumePhase2ResultsFile == null || resumePhase2ResultsFile.isEmpty() ? "(none)" : resumePhase2ResultsFile));
        System.out.println("Load Phase 2 recovery: " + loadRecovery);
        System.out.println("Dreadnaut watchdog timeout: " +
            (DREADNAUT_WATCHDOG_TIMEOUT_SECONDS <= 0 || System.getProperty("disableDreadnautWatchdog") != null ? "disabled" : DREADNAUT_WATCHDOG_TIMEOUT_SECONDS + "s"));
        System.out.println("Dynamic lastLoop checks: " + DYNAMIC_LASTLOOP_ORDER +
            (DYNAMIC_LASTLOOP_ORDER
                ? " (probe " + LASTLOOP_PROBE_SAMPLES + " every " + LASTLOOP_PROBE_EVERY_CANDIDATES +
                  " cand" + (LASTLOOP_REPROBE_EVERY_DISJOINT > 0
                    ? ", re-probe every " + LASTLOOP_REPROBE_EVERY_DISJOINT + " disjoint"
                    : "") + ")"
                : ""));
        
        File root = new File("PlanarStudy/" + groupName);
        root.mkdirs();

        String groupOrder = null;

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
                      !groupName.startsWith("sp_6_3") &&
                      !groupName.startsWith("l5_3") &&
                      !groupName.startsWith("tg") &&
                      !groupName.startsWith("he") &&
                      !groupName.startsWith("suz") &&
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
            groupOrder = Integer.toString(g.order());
            System.out.println("Order: " + groupOrder);
            
            // Close the generation files.
            for (int i = 0; i < conj.length; i++) {
                filesOut[i].close();
            }

            if (previewOnly) System.exit(0);
        } else {
            // If not generated then obtain the order from GAP.
            GapInterface gap = new GapInterface();
            groupOrder = gap.runGapSizeCommand(generator, 2).get(1).trim();
            System.out.println("Order: " + groupOrder);
        }

        // Cache accepted |G| strings for final aggregation (supports huge GAP orders).
        final String groupOrderFinal = groupOrder;
        final Set<String> acceptedFullOrder = Set.of(groupOrderFinal);
        final Set<String> acceptedFinalOrders = buildAcceptedFinalOrders(groupOrderFinal, INCLUDE_QUOTIENT);
        if (INCLUDE_QUOTIENT > 1) {
            System.out.println("Accepted final orders: " + acceptedFinalOrders);
        }


        // --------------------------------------------------------
        // Phase 1: Pair Filtering (skipped when loading Phase 2 recovery)
        // --------------------------------------------------------
        DreadnautWatchdog dreadnautWatchdog = new DreadnautWatchdog(DREADNAUT_WATCHDOG_TIMEOUT_SECONDS);
        dreadnautWatchdog.start();
        ExecutorService studyPool = Executors.newFixedThreadPool(studyThreads, r -> {
            Thread t = new Thread(r, "planar-study-worker");
            t.setDaemon(true);
            return t;
        });
        try (CycleSource lines3 = CycleSource.open(root, conj[phase2Indices[0]])) {

        // instantiate GAP to check group order (each thread keeps a live reference group G).
        final String referenceGenerator = generator;
        ThreadLocal<GapInterface> gapL = ThreadLocal.withInitial(() -> {
            try {
                GapInterface gap = new GapInterface();
                gap.loadReferenceGroup(referenceGenerator);
                return gap;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create GapInterface", e);
            }
        });
        ThreadLocal<DreadnautInterface> dreadnautL = ThreadLocal.withInitial(() -> new DreadnautInterface(DREADNAUT_PATH, USE_TRACES));

        List<int[][][]> candidatePairs = new ArrayList<>();
        CanonicalGraphHashSet canonicalGraphs = new CanonicalGraphHashSet();
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
        String andquotTag = INCLUDE_QUOTIENT > 1 ? "andquot" + INCLUDE_QUOTIENT + "-" : "";
        int[] found = new int[repetitions + 1];

        int nPoints = 0;
        for (int[][] x : GroupExplorer.parseOperations(generator)) {
            for (int[] y : x) {
                for (int z : y) {
                    nPoints = Math.max(nPoints, z);
                }
            }
        }
        final int nPointsFinal = nPoints;

        if (loadRecovery) {
            System.out.println("Skipping Phase 1: loading candidate list from " + PHASE2_RECOVERY_FILENAME);
        } else {
        final boolean phase1LastLoop = repetitions == 0;
        try (CycleSource lines2 = CycleSource.open(root, conj[phase1Indices[1]])) {
        System.out.println("Starting Phase 1: Pair Filtering");

        // Create a list to save unique candidate pairs.
        String phase1FilePath =
            root.getAbsolutePath() + "/" +
            (MAX_DUPLICATE_POLYGONS > 0 ? "d" + MAX_DUPLICATE_POLYGONS + "-" : "") +
            (enforceLoopMultiples > 1 ? "l" + enforceLoopMultiples + "-" : "") +
            (requirePlanar ? "" : discardOverGenusN == 1 ? "torus-" : (discardOverGenusN == 0 ? "np-" : "np" + discardOverGenusN + "-")) +
            geomAutTag +
            andquotTag +
            conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-"+filterName+".txt";
        PrintStream phase1Out = new PrintStream(phase1FilePath);
        QuarantineLog phase1Quarantine = new QuarantineLog(phase1FilePath);
        resultFilter.setOutput(phase1Out);
        resultFilter.resetStats(0);

        AtomicInteger p1_1_count = new AtomicInteger(0);

        String allConjClasses = gapL.get().getConjugacyClasses(generator);

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

        // Nested loops over the two lists with interactive skip/progress commands.
        AtomicBoolean skipRemaining = new AtomicBoolean(false);
        for (String l1 : lines1) {
            if (skipRemaining.get()) break;
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

            AtomicBoolean skipCurrent = new AtomicBoolean(false);

            lines2.forEachParallel(studyPool, studyThreads, l2 -> {
                waitWhilePaused();
                if (skipCurrent.get() || skipRemaining.get()) return;
                GroupExplorer ge = geL.get();
                int[][][] parsed2 = GroupExplorer.parseOperationsArr(l2);
                int[][] secondCandidate = parsed2[0]; // use the first generator set from file2.
                ge.resetElements(false);
                ge.applyOperation(parsed2[0], false);
                int[] l2State = ge.copyCurrentState();

                p1_2_count.incrementAndGet();
                // Check if
                if (Arrays.equals(l1State, l2State)) return;
                pollInteractiveCommand(skipCurrent, skipRemaining, () -> {
                    System.out.println("Progress (Phase 1):");
                    System.out.println("  conjugacy class: " + p1_1_count.get() + " / " + lines1.size());
                    System.out.println("  inner pairs: " + p1_2_count.get() + " / " + lines2.size());
                    System.out.println("  " + resultFilter.acceptedCount() + " results, " +
                        resultFilter.queuedCount() + " queued, " +
                        resultFilter.rejectedCount() + " rej., " +
                        candidatePairs.size() + " cand.");
                });
                if (skipCurrent.get() || skipRemaining.get()) return;
                // Combine the two generators into a pair.
                int[][][] combinedPair = new int[][][]{firstCandidate, secondCandidate};

                // Same conjugacy-class heuristic: all components share cycle type.
                if (requireSameCycleType && !generatorsHaveSameCycleType(combinedPair)) {
                    return;
                }
                
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
                    size = gapL.get().sizeOfSubgroup(GroupExplorer.generatorsToString(combinedPair));
                    if (!acceptedFullOrder.contains(size)) {
                        return;
                    }
                }

                // Check for genus 1 if required.
                if (discardOverGenusN > 0 && !(checkPlanarity(combinedPair) || checkGenusN(discardOverGenusN, combinedPair))) {
                    return;
                }

                boolean needsJavaGraph = enforceLoopMultiples > 1;
                try (CandidateGraph candGraph = CandidateGraph.open(combinedPair, directed, needsJavaGraph)) {

                // Check for isomorphic duplicates.
                CanonicalGraphHash canonicalLabeling = getCanonicalLabelingOrQuarantine(
                    dreadnautL.get(), candGraph, combinedPair, phase1Quarantine, null);
                if (canonicalLabeling == null) {
                    return;
                }
                synchronized (canonicalGraphs) {
                    if (canonicalGraphs.contains(canonicalLabeling)) {
                        return;
                    }
                }

                // Enforce all simple cycles have length multiple of N (if enabled)
                if (!candGraph.allEdgeCyclesAreMultiples(enforceLoopMultiples)) {
                    return;
                }
                
                if (size == null) {
                    size = gapL.get().sizeOfSubgroup(GroupExplorer.generatorsToString(combinedPair));
                }

                // Only apply the geometry automorphism filters to accepted final result orders.
                boolean passesGeometryFilter = true;
                if (acceptedFinalOrders.contains(size) &&
                    (minGeometryAutOrder > 1L || geometryAutOrderModulus > 1L)) {
                    try {
                        BigInteger geomOrder = candGraph.automorphismGroupOrder(
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

                boolean submitResult = false;
                // Must share the canonicalGraphs monitor with contains(); phase1Lock alone is not enough.
                synchronized (canonicalGraphs) {
                    if (!canonicalGraphs.add(canonicalLabeling)) return;
                }
                synchronized (phase1Lock) {
                    if (acceptedFinalOrders.contains(size) && passesGeometryFilterFinal) {
                        submitResult = true;
                    } else if (!phase1LastLoop) {
                        candidatePairs.add(combinedPair);
                    }
                }
                if (submitResult) {
                    resultFilter.addUnchecked(
                        GroupExplorer.generatorsToString(combinedPair),
                        phase1LastLoop,
                        phase1LastLoop ? null : () -> {
                            synchronized (phase1Lock) {
                                candidatePairs.add(combinedPair);
                            }
                        });
                }
                System.out.println("    Found new"+(requirePlanar ?" planar ":" ")+"graph with order " + size + " - " +
                    resultFilter.acceptedCount() + " results, " +
                    resultFilter.queuedCount() + " queued, " +
                    resultFilter.rejectedCount() + " rej., " +
                    candidatePairs.size() + " cand.");
                } // CandidateGraph
            }, () -> skipCurrent.get() || skipRemaining.get());
            if (skipCurrent.get() && !skipRemaining.get()) {
                System.out.println("  Skipped rest of conjugacy class " + p1_1_count.get());
            }
            if (skipRemaining.get()) {
                System.out.println("  Skipping remaining Phase 1 conjugacy classes.");
                break;
            }
        }
        resultFilter.drain();
        found[0] = resultFilter.acceptedCount();
        phase1Out.close();
        phase1Quarantine.close();
        System.out.println("Phase 1 completed. Unique candidate pairs: " + candidatePairs.size() +
            " - " + resultFilter.acceptedCount() + " results, " +
            resultFilter.rejectedCount() + " rej., " +
            candidatePairs.size() + " cand.");
        if (SORT_PH1_CANDIDATES) {
            sortCandidatesByCanonicalKey(candidatePairs, studyPool, studyThreads, dreadnautL, directed);
            System.out.println("Sorted candidate pairs by canonical key for stable Phase 2 indices: " +
                candidatePairs.size());
        }
        } // end lines2
        } // end !loadRecovery Phase 1
        
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
            andquotTag +
            conj[phase1Indices[0]] + "-" + conj[phase1Indices[1]] + "-" + conj[phase2Indices[0]];

        File recoveryFile = new File(root, PHASE2_RECOVERY_FILENAME);
        Phase2RecoveryState loadedRecovery = null;
        int startRound = 1;
        if (loadRecovery) {
            loadedRecovery = readPhase2Recovery(recoveryFile);
            if (!groupName.equals(loadedRecovery.groupName)) {
                throw new IOException("Recovery groupName mismatch: file has '" + loadedRecovery.groupName +
                    "', current is '" + groupName + "'");
            }
            if (!baseFileName.equals(loadedRecovery.baseFileName)) {
                throw new IOException("Recovery baseFileName mismatch: file has '" + loadedRecovery.baseFileName +
                    "', current is '" + baseFileName + "'");
            }
            synchronized (canonicalGraphs) {
                canonicalGraphs.clear();
                canonicalGraphs.addAll(loadedRecovery.canonicalGraphs);
            }
            currentCandidates = new ArrayList<>(loadedRecovery.currentCandidates);
            startRound = loadedRecovery.round;
            RECOVERY_ON.set(true);
            System.out.println("Loaded Phase 2 recovery from " + recoveryFile.getAbsolutePath() +
                ": round " + loadedRecovery.round +
                ", candidate " + loadedRecovery.candidateIndex +
                ", iso cache " + loadedRecovery.canonicalGraphs.size() +
                ", currentCandidates " + currentCandidates.size() +
                ", newCandidates so far " + loadedRecovery.newCandidates.size() +
                ", accepted " + loadedRecovery.acceptedCount);
        } else if (currentCandidates.isEmpty()) {
            System.out.println("No Phase 1 candidates; Phase 2 has nothing to do.");
        }
        for (int r = startRound; r <= repetitions; r++) {
            boolean lastLoop = r == repetitions; 
            final int rFinal = r;
            System.out.println("Starting Phase 2, round " + r + "");
            AtomicBoolean skipRemainingP2 = new AtomicBoolean(false);
            boolean recoverThisRound = loadedRecovery != null && loadedRecovery.round == r;
            List<int[][][]> newCandidates = recoverThisRound
                ? new ArrayList<>(loadedRecovery.newCandidates)
                : new ArrayList<>();
            String roundFileName = baseFileName + "_R" + r + "-"+filterName+".txt";
            String roundFilePath = root.getAbsolutePath() + "/" + roundFileName;
            File configuredResumeFile = (resumePhase2ResultsFile == null || resumePhase2ResultsFile.isEmpty())
                ? null
                : resolveResumeFile(root, resumePhase2ResultsFile);
            boolean resumeThisRound = !recoverThisRound && lastLoop &&
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
                    studyPool,
                    studyThreads,
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
            int startCandidate = recoverThisRound
                ? loadedRecovery.candidateIndex
                : (resumeThisRound ? resumePhase2FromCandidate : 0);
            int roundCount = recoverThisRound ? loadedRecovery.roundCount : 0;
            if (startCandidate >= currentCandidates.size()) {
                System.out.println("Resume start index " + startCandidate +
                    " >= candidate count " + currentCandidates.size() + "; skipping round " + r);
                roundQuarantine.close();
                if (recoverThisRound) loadedRecovery = null;
                continue;
            }
            boolean appendRoundOut = recoverThisRound || resumeThisRound;
            PrintStream phase2RoundOut = appendRoundOut
                ? new PrintStream(new FileOutputStream(roundFilePath, true))
                : new PrintStream(roundFilePath);
            resultFilter.setOutput(phase2RoundOut);
            int acceptedSeed = recoverThisRound
                ? loadedRecovery.acceptedCount
                : (resumeThisRound ? found[rFinal] : 0);
            resultFilter.resetStats(acceptedSeed);
            if (recoverThisRound) {
                System.out.println("Recovering " + roundFileName + ", starting at candidate " + startCandidate +
                    " of " + currentCandidates.size() +
                    " (iso cache " + canonicalGraphs.size() +
                    ", newCandidates " + newCandidates.size() +
                    ", accepted " + acceptedSeed + ")");
                loadedRecovery = null;
            } else if (resumeThisRound) {
                System.out.println("Appending to " + roundFileName + ", starting at candidate " + startCandidate +
                    " of " + currentCandidates.size());
            }
            final boolean dynamicLastLoopOrder = DYNAMIC_LASTLOOP_ORDER;
            final int lastLoopProbeSamples = LASTLOOP_PROBE_SAMPLES;
            final int lastLoopProbeEveryCandidates = Math.max(1, LASTLOOP_PROBE_EVERY_CANDIDATES);
            final int lastLoopReprobeEveryDisjoint = LASTLOOP_REPROBE_EVERY_DISJOINT;
            final LastLoopOrderSelector lastLoopOrderSelector = new LastLoopOrderSelector(
                lastLoopProbeSamples, lastLoopReprobeEveryDisjoint);
            // For each candidate from the previous round, combine with each line from file3.
            for (int i = startCandidate; i < currentCandidates.size(); i++) {
                if (skipRemainingP2.get()) break;
                final int iDisp = i;
                final int sizeDisp = currentCandidates.size();
                int[][][] candidate = currentCandidates.get(i);
                AtomicBoolean skipCurrentP2 = new AtomicBoolean(false);
                AtomicInteger roundCountAtomic = new AtomicInteger(0);
                AtomicInteger p2InnerCount = new AtomicInteger(0);
                AtomicInteger disjointRejected = new AtomicInteger(0);
                AtomicInteger disjointPassed = new AtomicInteger(0);
                boolean probeThisCandidate = lastLoop && dynamicLastLoopOrder
                    && ((i - startCandidate) % lastLoopProbeEveryCandidates == 0);
                lastLoopOrderSelector.beginCandidate(iDisp, probeThisCandidate);
                if (RECOVERY_ON.get()) {
                    resultFilter.drain();
                    writePhase2Recovery(recoveryFile, new Phase2RecoveryState(
                        groupName,
                        baseFileName,
                        r,
                        i,
                        roundCount,
                        resultFilter.acceptedCount(),
                        snapshotCanonicalGraphs(canonicalGraphs),
                        new ArrayList<>(currentCandidates),
                        new ArrayList<>(newCandidates)
                    ));
                }
                if (DEBUG.get() && lastLoop && dynamicLastLoopOrder) {
                    System.out.println("  Candidate " + i + " lastLoop order: " +
                        lastLoopOrderSelector.mode() +
                        (probeThisCandidate ? " (will probe)" : " (reuse)"));
                }

                // Inner loop: iterate over individual lines from file3 in parallel.
                lines3.forEachParallel(studyPool, studyThreads, l -> {
                    waitWhilePaused();
                    if (skipCurrentP2.get() || skipRemainingP2.get()) return;
                    p2InnerCount.incrementAndGet();
                    pollInteractiveCommand(skipCurrentP2, skipRemainingP2, () -> {
                        System.out.println("Progress (Phase 2, round " + rFinal + "):");
                        System.out.println("  candidate: " + iDisp + " / " + sizeDisp);
                        System.out.println("  inner generators: " + p2InnerCount.get() + " / " + lines3.size());
                        System.out.println("  disjoint rejected: " + disjointRejected.get() + " / " + p2InnerCount.get());
                        if (DEBUG.get() && lastLoop && dynamicLastLoopOrder) {
                            System.out.println("  lastLoop order: " + lastLoopOrderSelector.mode() +
                                " (disjoint-pass " + disjointPassed.get() + ")");
                        }
                        System.out.println("  " + resultFilter.acceptedCount() + " results, " +
                            resultFilter.queuedCount() + " queued, " +
                            resultFilter.rejectedCount() + " rej., " +
                            newCandidates.size() + " cand.");
                        if (RECOVERY_ON.get()) {
                            System.out.println("  recovery: on -> " + recoveryFile.getName());
                        }
                    });
                    if (skipCurrentP2.get() || skipRemainingP2.get()) return;
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
                    // Same conjugacy-class heuristic: all components share cycle type.
                    if (requireSameCycleType && !generatorsHaveSameCycleType(newCandidate)) {
                        return;
                    }
                    // Check for duplicate polygons in the new candidate.
                    if (hasDuplicatePolygon(newCandidate, MAX_DUPLICATE_POLYGONS)) {
                        return;
                    }
                    // Check planarity if required.
                    if (requirePlanar && !checkPlanarity(newCandidate)) {
                        return;
                    }

                    // Build the graph once; reuse for connectivity, GAP gate, and canonical labeling.
                    boolean needsJavaGraph = enforceLoopMultiples > 1;
                    try (CandidateGraph candGraph = CandidateGraph.open(newCandidate, directed, needsJavaGraph)) {

                    // Final results only: incomplete/disconnected support cannot generate |G| or |G|/q.
                    // Missing ambient points are treated as floating isolated vertices.
                    if (lastLoop && candGraph.isDisjointOrIncompleteSupport(nPointsFinal)) {
                        disjointRejected.incrementAndGet();
                        return;
                    }

                    String size = null;
                    CanonicalGraphHash canonicalLabeling = null;
                    Set<String> acceptedOrdersGate = lastLoop ? acceptedFinalOrders : acceptedFullOrder;

                    if (lastLoop && dynamicLastLoopOrder) {
                        int dp = disjointPassed.incrementAndGet();
                        boolean probe = lastLoopOrderSelector.offerProbeSlot(dp);
                        String genStr = GroupExplorer.generatorsToString(newCandidate);

                        if (probe) {
                            // Dual-path timing: run both ops once, attribute costs to each order.
                            long t0 = System.nanoTime();
                            size = gapL.get().sizeOfSubgroup(genStr);
                            long gapNs = System.nanoTime() - t0;
                            boolean sizeOk = acceptedOrdersGate.contains(size);

                            t0 = System.nanoTime();
                            canonicalLabeling = getCanonicalLabelingOrQuarantine(
                                dreadnautL.get(), candGraph, newCandidate, roundQuarantine, errors);
                            long dreadNs = System.nanoTime() - t0;
                            if (canonicalLabeling == null) return;

                            boolean isoHit;
                            synchronized (canonicalGraphs) {
                                isoHit = canonicalGraphs.contains(canonicalLabeling);
                            }
                            lastLoopOrderSelector.recordProbe(gapNs, dreadNs, sizeOk, isoHit);

                            if (!sizeOk) return;
                            if (isoHit) return;
                        } else if (lastLoopOrderSelector.mode() == LastLoopOrderSelector.Mode.DREAD_THEN_GAP) {
                            canonicalLabeling = getCanonicalLabelingOrQuarantine(
                                dreadnautL.get(), candGraph, newCandidate, roundQuarantine, errors);
                            if (canonicalLabeling == null) return;
                            synchronized (canonicalGraphs) {
                                if (canonicalGraphs.contains(canonicalLabeling)) return;
                            }
                            size = gapL.get().sizeOfSubgroup(genStr);
                            if (!acceptedOrdersGate.contains(size)) return;
                        } else {
                            // GAP_THEN_DREAD (default / current)
                            size = gapL.get().sizeOfSubgroup(genStr);
                            if (!acceptedOrdersGate.contains(size)) return;
                            canonicalLabeling = getCanonicalLabelingOrQuarantine(
                                dreadnautL.get(), candGraph, newCandidate, roundQuarantine, errors);
                            if (canonicalLabeling == null) return;
                            synchronized (canonicalGraphs) {
                                if (canonicalGraphs.contains(canonicalLabeling)) return;
                            }
                        }
                    } else {
                        if (!allowSubgroups || lastLoop) {
                            size = gapL.get().sizeOfSubgroup(GroupExplorer.generatorsToString(newCandidate));
                            // Intermediate rounds: full order only. Final round: also |G|/INCLUDE_QUOTIENT.
                            if (!acceptedOrdersGate.contains(size)) {
                                return;
                            }
                        }

                        // Check for isomorphic duplicates.
                        canonicalLabeling = getCanonicalLabelingOrQuarantine(
                            dreadnautL.get(), candGraph, newCandidate, roundQuarantine, errors);
                        if (canonicalLabeling == null) {
                            return;
                        }
                        synchronized (canonicalGraphs) {
                            if (canonicalGraphs.contains(canonicalLabeling)) {
                                return;
                            }
                        }
                    }
                    
                    // Enforce all simple cycles have length multiple of N (if enabled)
                    if (!candGraph.allEdgeCyclesAreMultiples(enforceLoopMultiples)) {
                        return;
                    }

                    // Check for genus 1 if required.
                    if (discardOverGenusN > 0 && !(checkPlanarity(newCandidate) || checkGenusN(discardOverGenusN, newCandidate))) {
                        return;
                    }
                    
                    if (size == null) {
                        size = gapL.get().sizeOfSubgroup(GroupExplorer.generatorsToString(newCandidate));
                    }

                    // Only apply the geometry automorphism filters to accepted final result orders.
                    boolean passesGeometryFilter = true;
                    if (acceptedFinalOrders.contains(size) &&
                        (minGeometryAutOrder > 1L || geometryAutOrderModulus > 1L)) {
                        try {
                            BigInteger geomOrder = candGraph.automorphismGroupOrder(
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

                    boolean submitResult = false;
                    // Must share the canonicalGraphs monitor with contains(); phase1Lock alone is not enough.
                    synchronized (canonicalGraphs) {
                        if (!canonicalGraphs.add(canonicalLabeling)) return;
                    }
                    synchronized (phase1Lock) {
                        if (acceptedFinalOrders.contains(size) && passesGeometryFilterFinal) {
                            submitResult = true;
                        } else if (!lastLoop) {
                            newCandidates.add(newCandidate);
                        }
                        roundCountAtomic.incrementAndGet();
                    }
                    if (submitResult) {
                        resultFilter.addUnchecked(
                            GroupExplorer.generatorsToString(newCandidate),
                            lastLoop,
                            lastLoop ? null : () -> {
                                synchronized (phase1Lock) {
                                    newCandidates.add(newCandidate);
                                }
                            });
                        System.out.println("    ("+(iDisp)+"/"+sizeDisp+") Found new"+(requirePlanar ?" planar ":" ")+"graph with order " + size + " - " +
                            resultFilter.acceptedCount() + " results, " +
                            resultFilter.queuedCount() + " queued, " +
                            resultFilter.rejectedCount() + " rej., " +
                            newCandidates.size() + " cand.");
                    }

                    } // CandidateGraph
                }, () -> skipCurrentP2.get() || skipRemainingP2.get());
                roundCount += roundCountAtomic.get();
                System.out.println("  Completed inner loop for candidate " + i + " of " + currentCandidates.size() +
                    (DEBUG.get() && lastLoop && dynamicLastLoopOrder
                        ? " [order=" + lastLoopOrderSelector.mode() + ", probes=" + lastLoopOrderSelector.probeWindowsCompleted() + "]"
                        : ""));
                if (skipCurrentP2.get() && !skipRemainingP2.get()) {
                    System.out.println("  Skipped rest of candidate " + i);
                }
                if (skipRemainingP2.get()) {
                    System.out.println("  Skipping remaining Phase 2 candidates this round.");
                    break;
                }
            }
            resultFilter.drain();
            found[rFinal] = resultFilter.acceptedCount();
            phase2RoundOut.close();
            roundQuarantine.close();
            System.out.println("Round " + r + " completed. Unique new candidates: " + roundCount +
                " - " + resultFilter.acceptedCount() + " results, " +
                resultFilter.rejectedCount() + " rej., " +
                newCandidates.size() + " cand.");
            currentCandidates = newCandidates;
        }
        System.out.println("Phase 2 completed after " + repetitions + " round(s). Final candidate count: " + currentCandidates.size() + " - order " + groupOrderFinal + " found: " + Arrays.toString(found));
        System.out.println("Errors: " + errors);
        } finally {
            studyPool.shutdownNow();
            try {
                studyPool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resultFilter.close();
            ACTIVE_RESULT_FILTER = null;
            dreadnautWatchdog.stop();
        }
    }

    /**
     * Chooses GAP-then-dreadnaut vs dreadnaut-then-GAP on lastLoop by occasionally
     * dual-timing both orders on disjoint-passing samples.
     */
    private static final class LastLoopOrderSelector {
        enum Mode { GAP_THEN_DREAD, DREAD_THEN_GAP }

        private final int probeSamples;
        private final int reprobeEveryDisjoint;
        private final Object lock = new Object();

        private volatile Mode mode = Mode.GAP_THEN_DREAD;
        private int candidateIndex = -1;
        private int probeLeft;
        private int probeSamplesRecorded;
        private int probeSamplesExpected;
        private long costGapThenDreadNs;
        private long costDreadThenGapNs;
        private int probeWindowsCompleted;
        private boolean decisionLogged;

        LastLoopOrderSelector(int probeSamples, int reprobeEveryDisjoint) {
            this.probeSamples = Math.max(1, probeSamples);
            this.reprobeEveryDisjoint = Math.max(0, reprobeEveryDisjoint);
        }

        void beginCandidate(int candidateIdx, boolean probeThisCandidate) {
            synchronized (lock) {
                this.candidateIndex = candidateIdx;
                this.probeLeft = 0;
                this.probeSamplesRecorded = 0;
                this.probeSamplesExpected = 0;
                this.costGapThenDreadNs = 0;
                this.costDreadThenGapNs = 0;
                this.decisionLogged = false;
                if (probeThisCandidate) {
                    // First disjoint-passing sample opens the initial probe window.
                    openProbeWindow();
                }
            }
        }

        Mode mode() {
            return mode;
        }

        int probeWindowsCompleted() {
            synchronized (lock) {
                return probeWindowsCompleted;
            }
        }

        /**
         * @param disjointIndex 1-based count of disjoint-passing items for this candidate
         * @return true if this item should run a dual-path probe sample
         */
        boolean offerProbeSlot(int disjointIndex) {
            synchronized (lock) {
                if (reprobeEveryDisjoint > 0
                        && disjointIndex > 1
                        && (disjointIndex - 1) % reprobeEveryDisjoint == 0
                        && probeLeft == 0
                        && probeSamplesRecorded == probeSamplesExpected) {
                    // Mid-candidate re-probe as the iso cache warms.
                    openProbeWindow();
                }
                if (probeLeft <= 0) return false;
                probeLeft--;
                return true;
            }
        }

        private void openProbeWindow() {
            probeLeft = probeSamples;
            probeSamplesExpected = probeSamples;
            probeSamplesRecorded = 0;
            costGapThenDreadNs = 0;
            costDreadThenGapNs = 0;
            decisionLogged = false;
        }

        void recordProbe(long gapNs, long dreadNs, boolean sizeOk, boolean isoHit) {
            Mode chosen;
            int cand;
            int samples;
            long aNs;
            long bNs;
            synchronized (lock) {
                // A: always GAP; dreadnaut only if size accepted
                costGapThenDreadNs += gapNs + (sizeOk ? dreadNs : 0);
                // B: always dreadnaut; GAP only if not already-seen iso
                costDreadThenGapNs += dreadNs + (isoHit ? 0 : gapNs);
                probeSamplesRecorded++;
                // Slots may be reserved before their workers finish. Decide only after
                // every reserved sample has recorded, never based on completion order.
                if (probeSamplesRecorded < probeSamplesExpected || decisionLogged) {
                    return;
                }
                mode = costDreadThenGapNs < costGapThenDreadNs
                    ? Mode.DREAD_THEN_GAP
                    : Mode.GAP_THEN_DREAD;
                decisionLogged = true;
                probeWindowsCompleted++;
                chosen = mode;
                cand = candidateIndex;
                samples = probeSamplesRecorded;
                aNs = costGapThenDreadNs;
                bNs = costDreadThenGapNs;
            }
            if (DEBUG.get()) {
                System.out.printf(java.util.Locale.ROOT,
                    "  lastLoop probe cand=%d samples=%d: GAP-then-dread=%.1fms dread-then-GAP=%.1fms -> %s%n",
                    cand, samples, aNs / 1e6, bNs / 1e6, chosen);
            }
        }
    }

    /**
     * Strip an optional trailing filter comment from a results line.
     * Comments are delimited by {@code " # "} (see {@link AbstractResultFilter}).
     */
    static String stripResultComment(String line) {
        if (line == null) return null;
        int idx = line.indexOf(" # ");
        return idx < 0 ? line : line.substring(0, idx);
    }

    /**
     * Build the set of GAP order strings accepted for final result aggregation.
     * Always includes {@code groupOrder}; when {@code includeQuotient > 1} also
     * includes {@code groupOrder / includeQuotient} (BigInteger division).
     */
    private static Set<String> buildAcceptedFinalOrders(String groupOrder, int includeQuotient) {
        Set<String> accepted = new HashSet<>();
        accepted.add(groupOrder);
        if (includeQuotient > 1) {
            BigInteger g = new BigInteger(groupOrder);
            accepted.add(g.divide(BigInteger.valueOf(includeQuotient)).toString());
        }
        return Collections.unmodifiableSet(accepted);
    }

    private static final Object STDIN_LOCK = new Object();
    private static final AtomicBoolean PAUSED = new AtomicBoolean(false);
    /** Toggled by interactive {@code toggle_debug}; gates probe chatter. */
    private static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    /** Toggled by interactive {@code recovery_on}/{@code recovery_off}; writes Phase 2 checkpoints. */
    private static final AtomicBoolean RECOVERY_ON = new AtomicBoolean(false);
    /** Active result filter for interactive pause_on_failure / pause_off_failure. */
    private static volatile AbstractResultFilter ACTIVE_RESULT_FILTER;

    // Phase2RecoveryState lives in its own class (compact CanonicalGraphHashSet iso cache).

    private static CanonicalGraphHashSet snapshotCanonicalGraphs(CanonicalGraphHashSet canonicalGraphs) {
        synchronized (canonicalGraphs) {
            return canonicalGraphs.copy();
        }
    }

    private static void writePhase2Recovery(File recoveryFile, Phase2RecoveryState state) {
        File tmp = new File(recoveryFile.getAbsolutePath() + ".tmp");
        try {
            try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                oos.writeObject(state);
                oos.flush();
            }
            Files.move(tmp.toPath(), recoveryFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("  Wrote Phase 2 recovery (round " + state.round +
                ", candidate " + state.candidateIndex +
                ", iso cache " + state.canonicalGraphs.size() +
                ", accepted " + state.acceptedCount + ") -> " + recoveryFile.getName());
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp.toPath(), recoveryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  Wrote Phase 2 recovery (round " + state.round +
                    ", candidate " + state.candidateIndex +
                    ", iso cache " + state.canonicalGraphs.size() +
                    ", accepted " + state.acceptedCount + ") -> " + recoveryFile.getName());
            } catch (IOException e2) {
                System.err.println("Failed to write Phase 2 recovery: " + e2.getMessage());
                if (tmp.exists() && !tmp.delete()) {
                    System.err.println("  Also failed to delete temp recovery file: " + tmp.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to write Phase 2 recovery: " + e.getMessage());
            if (tmp.exists() && !tmp.delete()) {
                System.err.println("  Also failed to delete temp recovery file: " + tmp.getAbsolutePath());
            }
        }
    }

    private static Phase2RecoveryState readPhase2Recovery(File recoveryFile) throws IOException {
        if (!recoveryFile.isFile()) {
            throw new IOException("Recovery file not found: " + recoveryFile.getAbsolutePath());
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(recoveryFile))) {
            Object obj = ois.readObject();
            if (obj instanceof Phase2RecoveryState) {
                return (Phase2RecoveryState) obj;
            }
            throw new IOException("Unexpected recovery object type: " +
                (obj == null ? "null" : obj.getClass().getName()) +
                ". If this is an old String-cache recovery file, run ConvertPhase2Recovery first.");
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize Phase 2 recovery", e);
        } catch (java.io.InvalidClassException e) {
            throw new IOException(
                "Recovery file is an older format. Run ConvertPhase2Recovery on " +
                    recoveryFile.getAbsolutePath() + " first.",
                e);
        }
    }

    /**
     * Block the calling thread in 1s sleeps while {@link #PAUSED} is set.
     * Polls stdin between sleeps so {@code resume} can still be handled.
     */
    private static void waitWhilePaused() {
        while (PAUSED.get()) {
            // Must poll stdin here — otherwise all workers sleep forever and never see "resume".
            pollInteractiveCommand(null, null, null);
            if (!PAUSED.get()) return;
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void printInteractiveHelp() {
        AbstractResultFilter filter = ACTIVE_RESULT_FILTER;
        System.out.println("Commands:");
        System.out.println("  skip_current    skip rest of current conjugacy class / candidate");
        System.out.println("  skip_remaining  skip all remaining work in this phase");
        System.out.println("  progress        print current progress");
        System.out.println("  pause           pause worker threads (1s sleep loop)");
        System.out.println("  resume          resume worker threads");
        System.out.println("  toggle_debug    toggle probe debug output (now " + (DEBUG.get() ? "on" : "off") + ")");
        System.out.println("  recovery_on     write Phase 2 recovery file at each new candidate");
        System.out.println("  recovery_off    stop writing Phase 2 recovery files");
        System.out.println("  pause_on_failure   pause study when result-filter fails (now " +
            (filter != null && filter.isPauseOnFailure() ? "on" : "off") + ")");
        System.out.println("  pause_off_failure  do not pause on result-filter failure");
        System.out.println("  (anything else) show this help");
    }

    /**
     * If a full line is waiting on stdin, read and dispatch it.
     * Safe to call from many worker threads; only one reads/handles at a time.
     * When {@code skipCurrent}/{@code skipRemaining}/{@code printProgress} are null
     * (pause wait loop), only pause/resume/help are handled.
     */
    private static void pollInteractiveCommand(
            AtomicBoolean skipCurrent,
            AtomicBoolean skipRemaining,
            Runnable printProgress) {
        synchronized (STDIN_LOCK) {
            try {
                if (System.in.available() <= 0) return;
                StringBuilder sb = new StringBuilder();
                while (System.in.available() > 0) {
                    int c = System.in.read();
                    if (c < 0 || c == '\n') break;
                    if (c != '\r') sb.append((char) c);
                }
                String cmd = sb.toString().trim();
                if (cmd.isEmpty()) return;
                switch (cmd.toLowerCase()) {
                    case "skip_current":
                        if (skipCurrent == null) {
                            System.out.println("Command: skip_current (ignored while pausing)");
                            break;
                        }
                        System.out.println("Command: skip_current");
                        skipCurrent.set(true);
                        break;
                    case "skip_remaining":
                        if (skipCurrent == null || skipRemaining == null) {
                            System.out.println("Command: skip_remaining (ignored while pausing)");
                            break;
                        }
                        System.out.println("Command: skip_remaining");
                        skipCurrent.set(true);
                        skipRemaining.set(true);
                        break;
                    case "progress":
                        if (printProgress == null) {
                            System.out.println("Paused (no progress callback in wait loop)");
                            break;
                        }
                        printProgress.run();
                        break;
                    case "pause":
                        System.out.println("Command: pause");
                        PAUSED.set(true);
                        break;
                    case "resume":
                        System.out.println("Command: resume");
                        PAUSED.set(false);
                        break;
                    case "toggle_debug":
                        boolean on = !DEBUG.get();
                        DEBUG.set(on);
                        System.out.println("Command: toggle_debug -> " + (on ? "on" : "off"));
                        break;
                    case "recovery_on":
                        RECOVERY_ON.set(true);
                        System.out.println("Command: recovery_on (Phase 2 checkpoints at each new candidate)");
                        break;
                    case "recovery_off":
                        RECOVERY_ON.set(false);
                        System.out.println("Command: recovery_off");
                        break;
                    case "pause_on_failure": {
                        AbstractResultFilter filter = ACTIVE_RESULT_FILTER;
                        if (filter == null) {
                            System.out.println("Command: pause_on_failure (no active result filter)");
                            break;
                        }
                        filter.setPauseOnFailure(true);
                        System.out.println("Command: pause_on_failure -> on");
                        break;
                    }
                    case "pause_off_failure": {
                        AbstractResultFilter filter = ACTIVE_RESULT_FILTER;
                        if (filter == null) {
                            System.out.println("Command: pause_off_failure (no active result filter)");
                            break;
                        }
                        filter.setPauseOnFailure(false);
                        System.out.println("Command: pause_off_failure -> off");
                        break;
                    }
                    default:
                        System.out.println("Unknown command: " + cmd);
                        printInteractiveHelp();
                        break;
                }
            } catch (IOException e) {
                System.err.println("Failed to read interactive command: " + e.getMessage());
            }
        }
        // After handling a command (including pause), park if needed — but only when
        // called from worker loops with a progress callback, not from waitWhilePaused itself.
        if (printProgress != null) {
            waitWhilePaused();
        }
    }

    /**
     * Source of conjugacy-class cycle notations in a deterministic shuffled order.
     * Resolves {@code <name>.txt.pbin} preferentially over {@code <name>.txt}.
     * Text sources shuffle individual lines; PBIN sources shuffle whole blocks so
     * decode stays sequential within each compressed chunk.
     */
    private abstract static class CycleSource implements AutoCloseable {
        static final long SHUFFLE_SEED = 321L;

        static CycleSource open(File dir, String conjName) throws IOException {
            File pbin = new File(dir, conjName + ".txt.pbin");
            if (pbin.isFile()) {
                System.out.println("Cycle source: " + pbin.getName() + " (pbin)");
                return new PbinCycleSource(PbinFile.open(pbin.getAbsolutePath()));
            }
            File txt = new File(dir, conjName + ".txt");
            if (txt.isFile()) {
                System.out.println("Cycle source: " + txt.getName() + " (txt)");
                return new TextCycleSource(loadTextLines(txt));
            }
            throw new IOException("No cycle file for '" + conjName +
                "' (.pbin or .txt) under " + dir.getAbsolutePath());
        }

        private static List<String> loadTextLines(File file) throws IOException {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = stripResultComment(line);
                    if (!line.trim().isEmpty()) {
                        lines.add(line);
                    }
                }
            }
            return lines;
        }

        /** Number of cycle notations in this source. */
        abstract int size();

        /**
         * Parallel visit in shuffled order on a fixed worker pool. When {@code cancelled}
         * becomes true, remaining work is skipped (including unread PBIN blocks).
         */
        abstract void forEachParallel(ExecutorService pool, int workers,
                                      java.util.function.Consumer<String> action,
                                      java.util.function.BooleanSupplier cancelled);

        @Override
        public abstract void close() throws IOException;
    }

    private static final class TextCycleSource extends CycleSource {
        private final List<String> lines;
        private final int[] order;

        TextCycleSource(List<String> lines) {
            this.lines = lines;
            List<Integer> idxs = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                idxs.add(i);
            }
            Collections.shuffle(idxs, new Random(SHUFFLE_SEED));
            this.order = new int[lines.size()];
            for (int i = 0; i < lines.size(); i++) {
                this.order[i] = idxs.get(i);
            }
        }

        @Override
        int size() {
            return order.length;
        }

        @Override
        void forEachParallel(ExecutorService pool, int workers,
                             java.util.function.Consumer<String> action,
                             java.util.function.BooleanSupplier cancelled) {
            runFixedParallel(pool, workers, order.length, i -> {
                waitWhilePaused();
                if (cancelled.getAsBoolean()) return;
                action.accept(lines.get(order[i]));
            }, cancelled);
        }

        @Override
        public void close() {
            // nothing to close
        }
    }

    /**
     * PBIN-backed source: shuffles compressed blocks (not individual generators)
     * so each block is decompressed once and its entries are visited contiguously.
     * Parallelism is over blocks; decode work outside the file lock.
     */
    private static final class PbinCycleSource extends CycleSource {
        private final PbinFile pbin;
        private final int[] blockOrder;

        PbinCycleSource(PbinFile pbin) {
            this.pbin = pbin;
            int blockSize = pbin.getBlockSize();
            int numBlocks = (pbin.size() + blockSize - 1) / blockSize;
            List<Integer> blocks = new ArrayList<>(numBlocks);
            for (int b = 0; b < numBlocks; b++) {
                blocks.add(b);
            }
            Collections.shuffle(blocks, new Random(SHUFFLE_SEED));
            this.blockOrder = new int[numBlocks];
            for (int i = 0; i < numBlocks; i++) {
                this.blockOrder[i] = blocks.get(i);
            }
            System.out.println("  Block-shuffled " + numBlocks + " blocks of size " + blockSize
                + " (" + pbin.size() + " generators)");
        }

        @Override
        int size() {
            return pbin.size();
        }

        @Override
        void forEachParallel(ExecutorService pool, int workers,
                             java.util.function.Consumer<String> action,
                             java.util.function.BooleanSupplier cancelled) {
            int blockSize = pbin.getBlockSize();
            int n = pbin.size();
            runFixedParallel(pool, workers, blockOrder.length, i -> {
                waitWhilePaused();
                if (cancelled.getAsBoolean()) return;
                int b = blockOrder[i];
                String[] entries = loadBlockEntries(b, blockSize, n);
                for (String entry : entries) {
                    waitWhilePaused();
                    if (cancelled.getAsBoolean()) return;
                    action.accept(entry);
                }
            }, cancelled);
        }

        private String[] loadBlockEntries(int block, int blockSize, int n) {
            try {
                byte[] raw = pbin.readRawBlock(block);
                byte[] payload = pbin.decompressBlock(raw);
                int start = block * blockSize;
                int count = Math.min(blockSize, n - start);
                String[] out = new String[count];
                for (int off = 0; off < count; off++) {
                    out[off] = pbin.decodeEntry(payload, off);
                }
                return out;
            } catch (IOException e) {
                throw new RuntimeException("Failed to read pbin block " + block, e);
            }
        }

        @Override
        public void close() throws IOException {
            pbin.close();
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
                waitWhilePaused();
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

    private static CanonicalGraphHash getCanonicalLabelingOrQuarantine(
        DreadnautInterface dreadnaut,
        int[][][] candidate,
        boolean directed,
        QuarantineLog quarantine,
        AtomicInteger errors
    ) {
        boolean needsJavaGraph = false; // hash-only path can stay native
        try (CandidateGraph graph = CandidateGraph.open(candidate, directed, needsJavaGraph)) {
            return getCanonicalLabelingOrQuarantine(dreadnaut, graph, candidate, quarantine, errors);
        }
    }

    private static CanonicalGraphHash getCanonicalLabelingOrQuarantine(
        DreadnautInterface dreadnaut,
        CandidateGraph graph,
        int[][][] candidate,
        QuarantineLog quarantine,
        AtomicInteger errors
    ) {
        try {
            return graph.canonicalHash(dreadnaut);
        } catch (RuntimeException e) {
            if (isAbnormalDreadnautExit(e)) {
                if (quarantine != null) {
                    quarantine.logFailure(GroupExplorer.generatorsToString(candidate), e.getMessage());
                }
            } else {
                System.err.println("Failed to compute canonical labeling: " + e.getMessage());
            }
            if (errors != null) {
                errors.incrementAndGet();
            }
            return null;
        }
    }

    /**
     * Cycle-type signature of one generator: sorted cycle lengths (fixed points omitted
     * since they are not present in the cycle array). Same signature ⇒ same conjugacy
     * class in S_n / A_n; elsewhere a useful heuristic.
     */
    private static int[] cycleTypeSignature(int[][] generator) {
        int[] lengths = new int[generator.length];
        for (int i = 0; i < generator.length; i++) {
            lengths[i] = generator[i].length;
        }
        Arrays.sort(lengths);
        return lengths;
    }

    /** True if every component of {@code combinedGen} has the same cycle-type signature. */
    private static boolean generatorsHaveSameCycleType(int[][][] combinedGen) {
        if (combinedGen.length <= 1) return true;
        int[] first = cycleTypeSignature(combinedGen[0]);
        for (int i = 1; i < combinedGen.length; i++) {
            if (!Arrays.equals(first, cycleTypeSignature(combinedGen[i]))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Final-result support check: reject if the generator graph omits any ambient
     * point ({@code vertexCount < nPoints}, i.e. floating fixed points) or if its
     * undirected view has more than one weak component.
     */
    static boolean isDisjointOrIncompleteSupport(
            Graph<Integer, DefaultEdge> graph, int nPoints) {
        int n = graph.vertexSet().size();
        if (n < nPoints) return true;
        if (n <= 1) return false;
        Set<Integer> seen = new HashSet<>(Math.max(16, n * 2));
        Queue<Integer> q = new ArrayDeque<>();
        Integer start = graph.vertexSet().iterator().next();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty()) {
            Integer u = q.remove();
            for (Integer v : Graphs.neighborListOf(graph, u)) {
                if (seen.add(v)) {
                    q.add(v);
                }
            }
        }
        return seen.size() < n;
    }

    private static void sortCandidatesByCanonicalKey(
        List<int[][][]> candidatePairs,
        ExecutorService pool,
        int workers,
        ThreadLocal<DreadnautInterface> dreadnautL,
        boolean directed
    ) {
        int n = candidatePairs.size();
        if (n <= 1) {
            return;
        }
        CanonicalGraphHash[] keys = new CanonicalGraphHash[n];
        String[] fallbackKeys = new String[n];
        runFixedParallel(pool, workers, n, i -> {
            int[][][] pair = candidatePairs.get(i);
            CanonicalGraphHash key = getCanonicalLabelingOrQuarantine(
                dreadnautL.get(), pair, directed, null, null);
            keys[i] = key;
            if (key == null) {
                fallbackKeys[i] = GroupExplorer.generatorsToString(pair);
            }
        }, () -> false);
        Integer[] order = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (a, b) -> {
            CanonicalGraphHash ka = keys[a];
            CanonicalGraphHash kb = keys[b];
            if (ka != null && kb != null) return ka.compareTo(kb);
            if (ka != null) return -1;
            if (kb != null) return 1;
            return fallbackKeys[a].compareTo(fallbackKeys[b]);
        });
        List<int[][][]> sorted = new ArrayList<>(n);
        for (int i : order) {
            sorted.add(candidatePairs.get(i));
        }
        candidatePairs.clear();
        candidatePairs.addAll(sorted);
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
        CanonicalGraphHashSet canonicalGraphs,
        ExecutorService pool,
        int workers,
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
                line = stripResultComment(line);
                if (line.trim().isEmpty()) {
                    continue;
                }
                batch.add(line);
                if (batch.size() >= batchSize) {
                    processSeedBatch(batch, pool, workers, canonicalGraphs, dreadnautL, directed, errors, uniqueLabels, quarantine);
                    int lines = lineCount.addAndGet(batch.size());
                    if (lines / progressInterval > (lines - batch.size()) / progressInterval) {
                        System.out.println("  Seeding progress: " + lines + " lines, " +
                            uniqueLabels.get() + " unique canonical labels");
                    }
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                processSeedBatch(batch, pool, workers, canonicalGraphs, dreadnautL, directed, errors, uniqueLabels, quarantine);
                lineCount.addAndGet(batch.size());
            }
        }
        System.out.println("  Seeding complete: " + lineCount.get() + " lines, " +
            uniqueLabels.get() + " unique canonical labels");
        return lineCount.get();
    }

    private static void processSeedBatch(
        List<String> batch,
        ExecutorService pool,
        int workers,
        CanonicalGraphHashSet canonicalGraphs,
        ThreadLocal<DreadnautInterface> dreadnautL,
        boolean directed,
        AtomicInteger errors,
        AtomicInteger uniqueLabels,
        QuarantineLog quarantine
    ) {
        runFixedParallel(pool, workers, batch.size(), i -> {
            waitWhilePaused();
            String line = batch.get(i);
            int[][][] candidate = GroupExplorer.parseOperationsArr(line);
            CanonicalGraphHash canonicalLabeling = getCanonicalLabelingOrQuarantine(
                dreadnautL.get(), candidate, directed, quarantine, errors);
            if (canonicalLabeling == null) {
                return;
            }
            synchronized (canonicalGraphs) {
                if (canonicalGraphs.add(canonicalLabeling)) {
                    uniqueLabels.incrementAndGet();
                }
            }
        }, () -> false);
    }

    /**
     * Run {@code n} indexed tasks on a fixed pool via worker-pull (at most
     * {@code workers} concurrent tasks; no million-Future queue).
     */
    private static void runFixedParallel(
        ExecutorService pool,
        int workers,
        int n,
        java.util.function.IntConsumer indexedAction,
        java.util.function.BooleanSupplier cancelled
    ) {
        if (n <= 0) {
            return;
        }
        int nWorkers = Math.min(Math.max(1, workers), n);
        AtomicInteger next = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>(nWorkers);
        for (int w = 0; w < nWorkers; w++) {
            futures.add(pool.submit(() -> {
                for (;;) {
                    if (cancelled.getAsBoolean()) return;
                    int i = next.getAndIncrement();
                    if (i >= n) return;
                    indexedAction.accept(i);
                }
            }));
        }
        awaitFutures(futures);
    }

    private static void awaitFutures(List<Future<?>> futures) {
        RuntimeException error = null;
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                for (Future<?> cancel : futures) {
                    cancel.cancel(true);
                }
                throw new RuntimeException("Interrupted while waiting for study workers", e);
            } catch (ExecutionException e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                if (error == null) {
                    error = c instanceof RuntimeException
                        ? (RuntimeException) c
                        : new RuntimeException(c);
                } else {
                    error.addSuppressed(c);
                }
            }
        }
        if (error != null) {
            throw error;
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
    /**
     * True if more than {@code maxAllowed} polygons share the same vertex multiset
     * (order ignored: {@code [1,2,3]} ≡ {@code [2,1,3]}).
     */
    private static boolean hasDuplicatePolygon(int[][][] combinedGen, int maxAllowed) {
        int estimate = 0;
        for (int[][] cycle : combinedGen) {
            estimate += cycle.length;
        }
        Set<SortedPolygonKey> seen = new HashSet<>(Math.max(16, estimate));
        int duplicates = 0;
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                if (!seen.add(SortedPolygonKey.of(polygon))) {
                    if (++duplicates > maxAllowed) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Sorted vertex multiset key for {@link #hasDuplicatePolygon}. */
    private static final class SortedPolygonKey {
        private final int[] sorted;
        private final int hash;

        private SortedPolygonKey(int[] sorted) {
            this.sorted = sorted;
            this.hash = Arrays.hashCode(sorted);
        }

        static SortedPolygonKey of(int[] polygon) {
            int[] copy = Arrays.copyOf(polygon, polygon.length);
            Arrays.sort(copy);
            return new SortedPolygonKey(copy);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SortedPolygonKey)) {
                return false;
            }
            SortedPolygonKey other = (SortedPolygonKey) o;
            return Arrays.equals(sorted, other.sorted);
        }
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

    static boolean allEdgeCyclesAreMultiples(Graph<Integer, DefaultEdge> graph, int k) {
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
