package io.chandler.gap;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.VertexColorSearch.ColorMapping;
import io.chandler.gap.cache.BigStateCache;
import io.chandler.gap.cache.LongLongStateCache;
import io.chandler.gap.cache.ParityStateCache;
import io.chandler.gap.cache.State;
import io.chandler.gap.util.TimeEstimator;

public class PHGenerators {

    public static final String pentPHSymmetryF1 = "[" +
            "(1,4,7,10,13)" +
            "(2,5,8,11,14)" +
            "(3,6,9,12,15)" +
            "(17,22,39,43,55)" +
            "(38,45,57,16,24)" +
            "(33,36,54,58,19)" +
            "(37,44,56,18,23)" +
            "(34,52,59,20,31)" +
            "(35,53,60,21,32)" +
            "(40,51,48,26,28)" +
            "(25,30,42,50,47)" +
            "(29,41,49,46,27)" +
        "]";

    public static final String triPHSymmetryF1 = "[" +
            "(1,2,3)(5,13,22)" +
            "(4,15,24)(10,19,39)" +
            "(7,16,33)(6,14,23)" +
            "(8,17,31)(12,21,38)" +
            "(11,20,37)(9,18,32)" +
            "(28,43,58)(57,25,36)" +
            "(26,34,55)(45,60,30)" +
            "(40,54,47)(59,29,44)" +
            "(35,56,27)(41,52,48)" +
            "(53,46,42)(50,49,51)" +
        "]";

    static int[][] getCyclesTetrahedral12Axis() {
        int[][][] useGlobalSymmetry_search = getDodecahedralSymm();

        VertexColorSearch vcs = new VertexColorSearch(useGlobalSymmetry_search, 60, PentagonalHexecontahedron::getFacesFromVertex, PentagonalHexecontahedron::getMatchingVertexFromFaces);

        for (ColorMapping c : vcs.searchForGenerators()) {
            int[] axes = c.axesSubgroup.vertex1Positions;
            if (axes.length == 12) {
                int[] vertices = c.axesSubgroup.vertex1Positions;

                int[][] cyclesUnified = new int[axes.length][];
                for (int i = 0; i < vertices.length; i++) {
                    cyclesUnified[i] = PentagonalHexecontahedron.getFacesFromVertex(vertices[i]);
                }

                return cyclesUnified;
            }
        }

        throw new RuntimeException("No cycles found");
    }

    // This is the second most common cycle in M60, it would likely
    //   not occur in any interesting groups
    private final static String REJECTED_CYCLE_DESC = "1 2 1 58 ";

    public static void main(String[] args) throws Exception {
        File out = new File("PH_VCS_3D_180_RESULTS.txt");
        if (out.exists()) {
            out.delete();
        }
        doExhaustive3DSearch(new PrintStream(out));
    }

    private static void doExhaustive3DSearch(PrintStream resultsPrintStream) {
        // Look for all 20 axis or 15 axis generators with 4x symmetry
        // Print it out if it's not Alt60
        // Heuristic for Alt60 is if it contains a 58-cycle + 2-cycle

        VertexColorSearch2 vcs60 = VertexColorSearch2.pentagonalHexecontahedron_3D_180_60vertices();
        System.out.println("Unfiltered 60: " + vcs60.generateAllSelections().size());
        vcs60.filterOutIdenticalGenerators();
        List<List<Integer>> allSelections60 = vcs60.generateAllSelections();
        System.out.println("Filtered 60: " + allSelections60.size());


        VertexColorSearch2 vcs80 = VertexColorSearch2.pentagonalHexecontahedron_3D_180_80vertices();
        System.out.println("Unfiltered 80: " + vcs80.generateAllSelections().size());
        vcs80.filterOutIdenticalGenerators();
        List<List<Integer>> allSelections80 = vcs80.generateAllSelections();
        System.out.println("Filtered 80: " + allSelections80.size());

        int twoPower15 = 1 << 15;
        int twoPower20 = 1 << 20;
        
        System.out.println("60: " + (allSelections60.size() * twoPower15)+ " 80: " + (allSelections80.size() * twoPower20));
        long totalCombinations = allSelections60.size() * twoPower15 + allSelections80.size() * twoPower20;
        System.out.println("Total: " + totalCombinations);
        resultsPrintStream.println("Total: " + totalCombinations);
        resultsPrintStream.flush();

        Integer MAX_TRANSITIVITY = null;

        TimeEstimator te = new TimeEstimator(totalCombinations);

        List<List<Integer>> selectionPool = allSelections60;
        selectionPool.addAll(allSelections80);

        Random r = new Random(1345980);
        Collections.shuffle(selectionPool, r);

        AtomicInteger results = new AtomicInteger();

        AtomicInteger iterations = new AtomicInteger();
        for (List<Integer> selection : selectionPool) {
            int[][] cycles = new int[selection.size()][];
            for (int i = 0; i < selection.size(); i++) {
                int[] faces = PentagonalHexecontahedron.getFacesFromVertex(selection.get(i));
                cycles[i] = faces;
            }

            boolean[][] fixedCycleIndices = new boolean[1][cycles.length];
            fixedCycleIndices[0][0] = true;

            List<int[][][]> invCyclesList = CycleInverter.generateInvertedCycles(fixedCycleIndices, new int[][][] {cycles});
            //System.out.println(cycles.length + " " + invCyclesList.size());
            invCyclesList.parallelStream().forEach(cyclesInverted -> {
                int iteration = iterations.incrementAndGet();
                if (
                    (iteration < 1000 && iteration % 100 == 0) ||
                    (iteration < 10000 && iteration % 1000 == 0) ||
                        iteration % 10000 == 0) te.checkProgressEstimate(iteration, results.get());
                Generator gen = Generator.combine(new Generator(vcs60.symmArr), new Generator(cyclesInverted));
                boolean good = checkGenerator(false, MAX_TRANSITIVITY, gen);
                if (good) {
                    results.incrementAndGet();
                    resultsPrintStream.println(GroupExplorer.generatorsToString(gen.generator()));
                    resultsPrintStream.flush();
                    Generator justCycles = new Generator(cyclesInverted);
                    PentagonalHexecontahedron.printVertexGeneratorNotations(justCycles.generator());
                }
            });

        }


    }

    public static void partialDualTetrahedral(String[] args) throws Exception {
        
        String otherTetrahedral12Axis = "[" +
            "(4,8,7)(5,1,3)(6,39,38)" +
            "(57,59,58)(55,12,11)(56,54,53)" +
            "(41,30,29)(42,49,51)(40,35,34)" +
            "(20,16,18)(21,26,25)(19,23,22)" +
            "]";
        
        int[][] cycles1 = getCyclesTetrahedral12Axis();

        int[][] cycles2 = GroupExplorer.parseOperationsArr(otherTetrahedral12Axis)[0];


        Generator gUnified =
            Generator.combine(
                    new Generator(new int[][][] {cycles1}),
                    new Generator(new int[][][] {cycles2}));

        checkGenerator(true, 18, gUnified);

        
    }


    public static void fullColorSearch(String[] args) throws Exception {
        //PentagonalHexecontahedron.printVertexGeneratorNotations(new Generator(GroupExplorer.parseOperationsArr("(23,15,24)(14,12,13)(11,10,7)(22,8,9)(20,17,18)(2,16,3)(5,1,4)(6,19,21)")).generator());
        
        
        getDodecahedralSymm(); // Order 60 (12p 5-cycles + 20p 3-cycles)
        get3D_180Symm();       // Order 3 (30p 2-cycles)
        getTetrahedralSymm();  // Order 12 (20p 3-cycles)

        
        int[][][] useGlobalSymmetry_search = getDodecahedralSymm();
        int[][][] useGlobalSymmetry_generation = getDodecahedralSymm();

        System.out.println(GroupExplorer.generatorsToString(useGlobalSymmetry_generation));

        System.exit(0);

        VertexColorSearch vcs = new VertexColorSearch(useGlobalSymmetry_search, 60, PentagonalHexecontahedron::getFacesFromVertex, PentagonalHexecontahedron::getMatchingVertexFromFaces);

        for (ColorMapping c : vcs.searchForGenerators()) {
            int[] axes = c.axesSubgroup.vertex1Positions;
            if (axes.length >= 12) {
                int colors = (int) Arrays.stream(c.getVertexToColorMap()).distinct().count();
                
                // Pick the two large axis mappings
                System.out.println(c.axesSubgroup.order + " " + Arrays.toString(c.axesSubgroup.vertex1Positions));

                int[] vertices = c.axesSubgroup.vertex1Positions;

                int[][] cyclesUnified = new int[axes.length][];
                for (int i = 0; i < vertices.length; i++) {
                    cyclesUnified[i] = PentagonalHexecontahedron.getFacesFromVertex(vertices[i]);
                }


                // Select partitions of vertices

                List<int[]> combinations = Permu.generateCombinations(vertices.length, vertices.length / 2);


                Generator gUnified = Generator.combine(
                    new Generator(new int[][][] {cyclesUnified}),
                    new Generator(useGlobalSymmetry_generation)
                );

                System.out.println(" --- 1 unified group and " + combinations.size() + " evenly split groups --- ");
                System.out.println("0: Generating unified group with " + colors + " colors");
                checkGenerator(false, 18, gUnified);

                int combinationIndex = 0;
                for (int[] combination : combinations) {
                    combinationIndex++;
                    HashSet<Integer> verticesB1 = new HashSet<>();
                    for (int v : vertices) verticesB1.add(v);
                    int[][] cyclesSplit = new int[axes.length][];
                    for (int i = 0; i < combination.length; i++) {
                        int vertex = vertices[combination[i]];
                        verticesB1.remove(vertex);
                        cyclesSplit[i] = PentagonalHexecontahedron.getFacesFromVertex(vertex);
                    }
                    int i = 0;
                    for (int vertex : verticesB1) {
                        cyclesSplit[combination.length + i] = CycleInverter.invertArray(PentagonalHexecontahedron.getFacesFromVertex(vertex));
                        i++;
                    }

                    //System.out.println(cyclesSplit.length);
                    
                    Generator gSplit = Generator.combine(
                        new Generator(new int[][][]{cyclesSplit}),
                        new Generator(useGlobalSymmetry_generation)
                    );

                    System.out.println((combinationIndex) + " / " + combinations.size() + ": Generating split group with " + colors + " colors");
                    checkGenerator(false, 21, gSplit);
                }
            }
        }
    }

    private static boolean checkGenerator(boolean debug, Integer transitivity, Generator g) {
        if (debug) System.out.println("Checking transitivity " + transitivity);

        // TODO check actual numbers
        Set<State> stateCache = 
            transitivity == null ? new HashSet<>() :
            transitivity > 23 ? new ParityStateCache(new BigStateCache(transitivity,60) ):
                                new ParityStateCache(new LongLongStateCache(transitivity,60));
        ArrayList<String> results = new ArrayList<>();
        HashMap<Integer, List<String>> smallGroupGenerators = new HashMap<>();

        checkGenerator(debug, g, results, smallGroupGenerators, stateCache);

        if (results.size() > 0 || smallGroupGenerators.size() > 0) {

            System.out.println("Found generator at transitivity " + transitivity);
            for (Map.Entry<Integer, List<String>> e : smallGroupGenerators.entrySet()) {
                System.out.println("Order " + e.getKey() + ":");
                for (String genString : e.getValue()) {
                    System.out.println(genString);
                }
            }
            for (String genString : results) {
                int[][] cycles = GroupExplorer.parseOperations(genString).get(0);
                PentagonalHexecontahedron.printVertexGeneratorNotations((new int[][][] {cycles}));
                System.out.println(genString);
            }

            return true;
        }
    
        return false;
    }

    private static List<State> findCycleInStateList(int[] ref, Set<State> states) {
        List<State> cycleStates = new ArrayList<>();
        for (State s : states) {
            int[][] cyclesA = GroupExplorer.stateToCycles(s.state());
            if (cyclesA.length == 0) continue;
            if (cyclesA[0].length != ref.length) continue;
            boolean found = false;
            for (int[] cycle : cyclesA) {
                int[] cycleX = cycle.clone();
                for (int i = 0; i < cycle.length; i++) {
                    int[] cycleY = CycleInverter.invertArray(cycleX);
                    if (Arrays.equals(cycleX, ref) || Arrays.equals(cycleY, ref)) {
                        found = true;
                        break;
                    }
                    ArrayRotator.rotateLeft(cycleX);
                }
            }
            if (found) {
                cycleStates.add(s);
            }
        }
        return cycleStates;
    }


    private static Map<Integer, LinkedHashMap<Integer, int[]>> vertexToIcosidodecSymmetryCache = new HashMap<>();
    private static LinkedHashMap<Integer, int[]> findIcosidodecSymmetryOfPHVertex(int vertex) {
        
        if (vertexToIcosidodecSymmetryCache.containsKey(vertex)) {
            return vertexToIcosidodecSymmetryCache.get(vertex);
        }
        
        int[][][] gen = getDodecahedralSymm();

        HashSet<State> cache = new HashSet<>();
        GroupExplorer ge = new GroupExplorer(GroupExplorer.generatorsToString(gen), MemorySettings.DEFAULT, cache);
        ge.resetElements(true);

        int[] vertexFaces = PentagonalHexecontahedron.getFacesFromVertex(vertex);
        LinkedHashMap<Integer, int[]> vertexMapping = new LinkedHashMap<>();

        // Find all copies of the specified vertex with tetrahedral symmetry

        ge.exploreStates(false, (states, depth) -> {
            for (int[] s : states) {
                int[] analogousFaces = new int[3];
                for (int fi = 0; fi < 3; fi++) {
                    int face = vertexFaces[fi];
                    analogousFaces[fi] = s[face - 1];
                }
                int cacheIndex = PentagonalHexecontahedron.getMatchingVertexFromFaces(analogousFaces);
                vertexMapping.put(cacheIndex, analogousFaces);
            }
        });
        
        vertexToIcosidodecSymmetryCache.put(vertex, vertexMapping);
        return vertexMapping;
    }


/*
    public static void findCube_6p() throws Exception {
        
        int[][] symm0 = GroupExplorer.parseOperationsArr(cubicPISymmetries_2)[0];
        int[][] symm1 = GroupExplorer.parseOperationsArr(cubicPISymmetries_2)[1];

        System.out.println("Starting cube 6p search");
        System.out.println("Press Q + Enter at any time to interrupt");

        Thread.sleep(1000);

        ArrayList<Generator> validVertexCombinations = get6pVertexCombinations();
        Collections.shuffle(validVertexCombinations);
        
        System.out.println("Found " + validVertexCombinations.size() + " possible generators");
        
        int[] iteration = new int[] {0};
        long combinations = validVertexCombinations.size();
        long startTime = System.currentTimeMillis();

        // Synchronized
        List<String> results = Collections.synchronizedList(new ArrayList<String>());
        Map<Integer, List<String>> smallGroupGenerators = Collections.synchronizedMap(new TreeMap<>());

        for (Generator vertices : validVertexCombinations) {

            if (checkQuit() == -1) {
                System.out.println("QUITTING");
                break;
            }

            if (iteration[0] % 50 == 0) {
                checkProgressEstimate(iteration[0], combinations, startTime, validVertexCombinations.size(), results.size());
            }

            Generator g = new Generator(new int[][][] {
                symm0,
                symm1,
                vertices.generator()[0],
            });

            //vertices2.parallelStream().forEach(g -> {
                checkGenerator(g, results, smallGroupGenerators);
            
            //});


            iteration[0]++;
        }

        System.out.println("Exited at iteration " + iteration[0] + " / " + combinations);

        System.out.println("Filtered down to " + results.size() + " valid generators");
        // Write to file 

        System.out.println("Writing results to file");

        Files.deleteIfExists(Paths.get("generators_results.txt"));

        PrintStream out2 = new PrintStream("generators_results.txt");
        for (Map.Entry<Integer, List<String>> e : smallGroupGenerators.entrySet()) {
            out2.println("Order " + e.getKey() + ":");
            for (String genString : e.getValue()) {
                out2.println(genString);
            }
        }

        out2.println("Order ?: ");
        for (String genString : results) {
            out2.println(genString);
        }
        out2.close();
    }
*/

    private static ArrayList<Generator> getNpVertexCombinations(int n) {

        HashSet<Generator> generatorCache = new HashSet<>();

        boolean[][] fixedCycleIndices = new boolean[1][n];
        fixedCycleIndices[0][0] = true;
        for (int i = 1; i < n; i++) {
            fixedCycleIndices[0][i] = false;
        }

        HashSet<Integer> uniqueFaces = new HashSet<>();
        PermuCallback.generateCombinations(60, n, (b) -> {
    
            int[][] cyclesA = new int[n][];
            for (int i = 0; i < n; i++) {
                cyclesA[i] = PentagonalHexecontahedron.getFacesFromVertex(b[i] + 1);
            }

            uniqueFaces.clear();
            for (int[] cycle : cyclesA) {
                for (int face : cycle) {
                    uniqueFaces.add(face);
                }
            }
            
            if (uniqueFaces.size() == n*3) {

                int[][][] genSrc = new int[][][] {
                    cyclesA,
                };
                List<int[][][]> cycled = CycleInverter.generateInvertedCycles(fixedCycleIndices, genSrc);

                for (int[][][] c : cycled) {
                    Generator g = new Generator(c);
                    generatorCache.add(g);
                }
                
            }

        });

        ArrayList<Generator> validVertexCombinations = new ArrayList<>(generatorCache);
        
        return validVertexCombinations;
    }

    private static void printGeneratorResults(Generator g, Set<State> stateCache) {
        String genString = GroupExplorer.generatorsToString(g.generator());
        GroupExplorer candidate = new GroupExplorer(
            genString,
            MemorySettings.COMPACT, stateCache);

        Generators.exploreGroup(candidate, null);
            
    }

    private static void checkGenerator(boolean debug, Generator g, List<String> lgGroupResults, Map<Integer, List<String>> smallGroupGenerators, Set<State> cache) {
        GroupExplorer candidate = new GroupExplorer(
            g, 60,
            MemorySettings.FASTEST, cache);
            

        ArrayList<String> depthPeek = new ArrayList<>();
        int limit = 7_000_000;
        int[] stateCount = new int[2];
        int iters = -2;

        //System.out.println(genString);
        
        candidate.initIterativeExploration();
        //HashMap<String, Integer> cycleDescriptions = new HashMap<>();
        while (iters == -2) {
            int[] depthA = new int[] {0};
            try {
                iters = candidate.iterateExploration(debug, limit, (states, depth) -> {
                    stateCount[0] = stateCount[1];
                    stateCount[1] += states.size();
                    depthA[0] = depth;
                    for (int[] s : states) {
                        String desc = GroupExplorer.describeStateForCache(candidate.nElements, s);
                        if (desc.equals(REJECTED_CYCLE_DESC)) {
                            throw new RuntimeException();
                        }
                        //cycleDescriptions.merge(desc, 1, Integer::sum);
                    }
                });
            } catch (ParityStateCache.StateRejectedException e) {
                // Heuristic 1:
                //    If the 7-transitive cache is too small we've generated a non-M24 group
                
                // Fail because this can't happen for valid M24 generators
                iters = -2;
                //System.out.println("Parity cache fails");
                break;
            } catch (RuntimeException e) {
                iters = -2;
                //System.out.println("Reject " + e.getMessage());
                break;
            }
            int depth = depthA[0];
            double ratio = depth < 2 ? 10000 : stateCount[1]/(double)stateCount[0];
            if (depth > 5 ) depthPeek.add(ratio+" " + stateCount[1] + ",");

            //System.out.println(stateCountB[0] + " " + stateCount[1]);

            
            // Heuristic 3: If there are more than 1000 cycle descriptions, we're probably not going to find anything
           /*  if (cycleDescriptions.size() > 500) {
                iters = -2;
                //System.out.println("Too many cycle descriptions");
                break;
            }*/

        }

        if (iters == -2) {
            /*if (depthPeek.size() > 4) {
                System.out.println("Reject " + iters + " Last iter states: " + depthPeek.get(depthPeek.size() - 1) + " Depth: " + depthPeek.size());
                System.out.println(depthPeek.toString());
            }*/
        } else if (iters == -1) {
            System.out.println("Iters: " + iters + " Last iter states: " + depthPeek.get(depthPeek.size() - 1) + " Depth: " + depthPeek.size());
            System.out.println( GroupExplorer.generatorsToString(g.generator()));
            System.out.println(depthPeek.toString());
            lgGroupResults.add(GroupExplorer.generatorsToString(g.generator()));

            /*
            HashMap<String, Integer> cycleDescriptions = new HashMap<>();
            for (State state : cache) {
                String cycleDescription = GroupExplorer.describeState(candidate.nElements, state.state());
                cycleDescriptions.merge(cycleDescription, 1, Integer::sum);
                if (cycleDescriptions.size() % 100_000 == 0) {
                    System.out.println("Processed " + cycleDescriptions.size() + " / " + cache.size() + " states");
                }
            }

            printCycleDescriptions(cycleDescriptions);
            */
        } else if (candidate.order() >= 1) {
            // Add genString to smallGroupGenerators
            List<String> gens = smallGroupGenerators.computeIfAbsent(candidate.order(), k -> Collections.synchronizedList(new ArrayList<String>()));
            gens.add(GroupExplorer.generatorsToString(g.generator()));
            System.out.println("Found order " + (candidate.order()) + ": " + GroupExplorer.generatorsToString(g.generator()));
        }
        

    }


    public static int[][][] getDodecahedralSymm() {
        int[][] op0 = GroupExplorer.parseOperationsArr(triPHSymmetryF1)[0];
        int[][] op1 = GroupExplorer.parseOperationsArr(pentPHSymmetryF1)[0];

        int[][][] gen = new int[][][] {
            op0,
            op1,
        };

        return gen;
    }

    public static int[][][] get5FoldSymm() {
        return new int[][][] {
            GroupExplorer.parseOperationsArr(pentPHSymmetryF1)[0],
        };
    }

    //20p 3-cycles
    //12p 5-cycles
    //30p 2-cycles
    private static List<int[]> getMatchingCycles(String type) {
        List<int[]> cycles = new ArrayList<>();
        int[][][] gen = getDodecahedralSymm();

        HashSet<State> cache = new HashSet<>();
        GroupExplorer ge = new GroupExplorer(GroupExplorer.generatorsToString(gen), MemorySettings.DEFAULT, cache);
        ge.exploreStates(false, null);

        for (State s : cache) {
            if (type.equals(GroupExplorer.describeState(60, s.state()))) {
                cycles.add(s.state());
            }
        }
        return cycles;
    }

    public static int[][][] get3D_180Symm() {
        return getSymm("30p 2-cycles", 4, "180 degree");
    }

    public static int[][][] getTetrahedralSymm() {
        return getSymm("20p 3-cycles", 12, "tetrahedral");
    }

    private static int[][][] getSymm(String cyclesSrcType, int order, String name) {
        List<int[]> cycles = getMatchingCycles(cyclesSrcType);
        // Select one and loop through the rest
        int[] cycle0 = cycles.get(0);
        for (int[] cycle1 : cycles) {
            int[][][] gen = new int[][][] {
                GroupExplorer.stateToCycles(cycle0),
                GroupExplorer.stateToCycles(cycle1),
            };

            GroupExplorer ge = new GroupExplorer(GroupExplorer.generatorsToString(gen), MemorySettings.DEFAULT, new HashSet<>());
            ge.exploreStates(false, null);
            
            if (ge.order() == order) {
                //System.out.println("Found " + name + " symmetry: order " + ge.order());
                return gen;
            }
        }
        throw new RuntimeException("No " + name + " symmetry found");
    }

}
