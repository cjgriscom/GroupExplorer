package io.chandler.gap;

import static io.chandler.gap.IcosahedralGenerators.printCycleDescriptions;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.VertexColorSearch.ColorMapping;
import io.chandler.gap.cache.InteractiveCachePair;
import io.chandler.gap.cache.LongStateCache;
import io.chandler.gap.cache.M24StateCache;
import io.chandler.gap.cache.ParityStateCache;
import io.chandler.gap.cache.State;

public class CubicGenerators {

    public static final String cubicPISymmetries3 = "[" +
        "(1,4,7,10)(2,5,8,11)(3,6,9,12)" + // Face 1 CW
        "(17,20,23,15)(13,18,21,24)(16,19,22,14)" + // Face 6 CCW
        "," +
        "(2,12,13,16)(3,10,14,17)(1,11,15,18)" + // Face 2 CW
        "(6,8,22,21)(4,9,23,19)(5,7,24,20)" + // Face 4 CCW
        "," +
        "(3,18,19,5)(1,16,20,6)(2,17,21,4)" + // Face 3 CW
        "(9,11,14,24)(7,12,15,22)(8,10,13,23)" + // Face 5 CCW
        "]";
    public static final String cubicPISymmetries_2 = "[" +
        "(1,4,7,10)(2,5,8,11)(3,6,9,12)" + // Face 1 CW
        "(17,20,23,15)(13,18,21,24)(16,19,22,14)" + // Face 6 CCW
        "," +
        "(2,12,13,16)(3,10,14,17)(1,11,15,18)" + // Face 2 CW
        "(6,8,22,21)(4,9,23,19)(5,7,24,20)" + // Face 4 CCW
        "]";
    public static final String cubicPISymmetries = "[" +
        "(1,4,7,10)(2,5,8,11)(3,6,9,12)" + // Face 1 CW
        "(17,20,23,15)(13,18,21,24)(16,19,22,14)" + // Face 6 CCW
        "]";

    public static final String cubicDiSymmetries = "[" +
        "(5,6,8,7)(3,24,10,17)(4,22,9,19)" + // Face 1 CW
        "(13,15,16,14)(1,23,12,18)(2,21,11,20)" + // Face 6 CCW
        "," +
        "(1,2,4,3)(5,20,16,24)(6,19,15,23)" +
        "(9,11,12,10)(13,21,8,17)(14,22,7,18)" +
        "]";

	public static int[][] getFace180Symm(int faceIndex) {
		String face1Symm = GroupExplorer.generatorsToString(new int[][][] {
			GroupExplorer.parseOperations(CubicGenerators.cubicPISymmetries3).get(faceIndex)});

		GroupExplorer ge_face1 = new GroupExplorer(face1Symm, MemorySettings.DEFAULT, new HashSet<>());
		ge_face1.resetElements(true);
		ge_face1.applyOperation(0);
		ge_face1.applyOperation(0); // 180 deg
		int[] turn180 = ge_face1.copyCurrentState();
		return GroupExplorer.stateToCycles(turn180);
	}


	// 180 8p doesn't work...
	// Cube 8p doesn't work
	

	public static void main(String[] args) throws Exception {
        //PentagonalIcositrahedron.printVertexGeneratorNotations(new Generator(GroupExplorer.parseOperationsArr("(23,15,24)(14,12,13)(11,10,7)(22,8,9)(20,17,18)(2,16,3)(5,1,4)(6,19,21)")).generator());
        
        //findCube_8p();
        //checkCube_8p();

        //vertexColorSearchPI();

        //doExhaustive3DSearchPI();

        //diTests();
        //printM24_dot_puzzle_Depth_Classes();
        doExhaustive3DSearchPI();
	}

    private static void printM24_dot_puzzle_Depth_Classes() {

        String m24_pidot = "[(1,10,2)(6,19,5)(9,11,7)(13,12,14)(18,20,17)(23,21,22),(11,14,12)(8,4,7)(23,15,24)(16,13,17)(1,5,3)(19,6,21),(15,17,13)(22,9,24)(19,18,20)(2,16,3)(11,7,10)(4,8,6),(20,23,21)(16,3,18)(14,24,15)(8,22,9)(5,1,4)(10,2,12),(2,10,1)(5,19,6)(7,11,9)(14,12,13)(17,20,18)(22,21,23),(12,14,11)(7,4,8)(24,15,23)(17,13,16)(3,5,1)(21,6,19),(13,17,15)(24,9,22)(20,18,19)(3,16,2)(10,7,11)(6,8,4),(21,23,20)(18,3,16)(15,24,14)(9,22,8)(4,1,5)(12,2,10)]";



        int nElements = 24;
        int cacheGB = 32;
        int batch = 10_000_000;
        try (
            Scanner scanner = new Scanner(System.in);
            InteractiveCachePair cachePair =
                new InteractiveCachePair(scanner, cacheGB, nElements, batch)) {

            GroupExplorer group = new GroupExplorer(m24_pidot, MemorySettings.COMPACT,
                    cachePair.cache, cachePair.cache_incomplete, cachePair.cache_tmp, true);

            // Explore conjugacy classes for each depth
            group.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
                HashMap<String,Integer> conjugacyClasses = new HashMap<>();
                for (int[] state : states) {
                    String conjugacyClass = GroupExplorer.describeState(24, state);
                    Integer count = conjugacyClasses.get(conjugacyClass);
                    conjugacyClasses.put(conjugacyClass, count == null ? 1 : count + 1);
                }
                for (Entry<String,Integer> conjugacyClass : conjugacyClasses.entrySet()) {
                    System.out.println("  " + conjugacyClass.getKey() + ": " + conjugacyClass.getValue());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<int[][][]> getEdgeAnd4FaceGeneratorsForEdge(int edgeIndex) {
        ArrayList<int[][][]> generators = new ArrayList<>();
        int[][] gen0 = DeltoidalIcositetrahedron.cubeEdgeToDifacesPairs[edgeIndex];

        // Now select 4 faces that don't conflict with contents of gen0
        HashSet<Integer> usedFaces = new HashSet<>();
        for (int[] faces : gen0) for (int face : faces) usedFaces.add(face);

        List<int[]> comb = Permu.generateCombinations(6, 4); // 6 choose 4
        nextPermu0: for (int[] cubicVertices : comb) {
            for (int vertex : cubicVertices) {
                for (int face : DeltoidalIcositetrahedron.cubeFaceToDifaces[vertex]) {
                    if (usedFaces.contains(face)) {
                        continue nextPermu0;
                    }
                }
            }
            boolean[][] fixedCycleIndices = new boolean[1][4];
            fixedCycleIndices[0][0] = true;
            int[][][] generatorSrc = new int[1][4][];
            for (int i = 0; i < 4; i++) {
                generatorSrc[0][i] = DeltoidalIcositetrahedron.cubeFaceToDifaces[cubicVertices[i]];
            }
            for (int[][][] inversions : CycleInverter.generateInvertedCycles(fixedCycleIndices, generatorSrc)) {
                generators.add(new int[][][] {
                    new int[][] {
                        gen0[0], gen0[1], inversions[0][0], inversions[0][1], inversions[0][2], inversions[0][3]
                    }
                });
            }
        }
        return generators;
    }
    private static List<int[][][]> getEdgeAnd4x4FoldGeneratorsForFaceOrEdge(int edgeIndex) {
        ArrayList<int[][][]> generators = new ArrayList<>();
        int[][] gen0;
        if (edgeIndex <= 5) gen0 = DeltoidalIcositetrahedron.cubeFaceToDifacesPairs[edgeIndex];
        else gen0 = DeltoidalIcositetrahedron.cubeEdgeToDifacesPairs[edgeIndex-6];

        List<int[]> comb = Permu.generateCombinations(6+12, 4); // 6 choose 4
        nextPermu0: for (int[] cubicVertices : comb) {
            // Now select 4 faces that don't conflict with contents of gen0
            HashSet<Integer> usedFaces = new HashSet<>();
            for (int[] faces : gen0) for (int face : faces) usedFaces.add(face);
    
            for (int vertex : cubicVertices) {
                int[] op;
                if (vertex <= 5) op = DeltoidalIcositetrahedron.cubeFaceToDifaces[vertex];
                else op = DeltoidalIcositetrahedron.cubeEdgeToDifaces[vertex-6];
                for (int face : op) {
                    if (usedFaces.contains(face)) {
                        continue nextPermu0;
                    }
                    usedFaces.add(face);
                }
            }
            boolean[][] fixedCycleIndices = new boolean[1][4];
            fixedCycleIndices[0][0] = true;
            int[][][] generatorSrc = new int[1][4][];
            for (int i = 0; i < 4; i++) {
                if (cubicVertices[i] <= 5) generatorSrc[0][i] = DeltoidalIcositetrahedron.cubeFaceToDifaces[cubicVertices[i]];
                else generatorSrc[0][i] = DeltoidalIcositetrahedron.cubeEdgeToDifaces[cubicVertices[i]-6];
            }
            for (int[][][] inversions : CycleInverter.generateInvertedCycles(fixedCycleIndices, generatorSrc)) {
                generators.add(new int[][][] {
                    new int[][] {
                        gen0[0], gen0[1], inversions[0][0], inversions[0][1], inversions[0][2], inversions[0][3]
                    }
                });
            }
        }
        return generators;
    }


    private static List<int[][][]> get8XVertexCombinations() {
        ArrayList<int[][][]> generators = new ArrayList<>();
        boolean[][] fixedCycleIndices = new boolean[1][8];
        fixedCycleIndices[0][0] = true;
        int[][][] generatorSrc = new int[1][8][];
        for (int i = 0; i < 8; i++) {
            generatorSrc[0][i] = DeltoidalIcositetrahedron.cubeVertexToDifaces[i];
        }
        for (int[][][] inversions : CycleInverter.generateInvertedCycles(fixedCycleIndices, generatorSrc)) {
            generators.add(new int[][][] {
                inversions[0].clone(),
            });
        }
        return generators;
    }

    private static void diTests() {

        Set<State> stateCache = new LongStateCache(9,24);
        ArrayList<String> results = new ArrayList<>();
        HashMap<Integer, List<String>> smallGroupGenerators = new HashMap<>();

        List<int[][][]> generators0 = getEdgeAnd4x4FoldGeneratorsForFaceOrEdge(12);
    
        for (int[][][] gen0 : generators0) {
                Generator g = new Generator(new int[][][] {
                        gen0[0],
                    });
                    g = Generator.combine(g, new Generator(GroupExplorer.parseOperationsArr(cubicDiSymmetries)));
                    System.out.println(GroupExplorer.generatorsToString(g.generator()));
                    stateCache.clear();
                    checkGenerator(true, g, results, smallGroupGenerators, stateCache);
            
        }
    
    
        
        System.out.println("Found " + results.size() + " / " + smallGroupGenerators.size() + " generators");

    }

    private static void doExhaustive3DSearchPI() {
        {
        VertexColorSearch2 vcs = VertexColorSearch2.pentagonalIcositrahedron_3D_180_32Vertices();
        System.out.println(vcs.generateAllSelections().size());
        vcs.filterOutIdenticalGenerators();
        System.out.println(vcs.generateAllSelections().size());
        vcs.forEachGeneratorWithInversions((gen, justCycles) -> {
            boolean good = checkGenerator(false, 8, gen);
            if (good) PentagonalIcositrahedron.printVertexGeneratorNotations(justCycles.generator());
        });
        }
        
        VertexColorSearch2 vcs = VertexColorSearch2.pentagonalIcositrahedron_3D_180();
        System.out.println(vcs.generateAllSelections().size());
        vcs.filterOutIdenticalGenerators();
        System.out.println(vcs.generateAllSelections().size());
        vcs.forEachGeneratorWithInversions((gen, justCycles) -> {
            boolean good = checkGenerator(false, 8, gen);
            if (good) PentagonalIcositrahedron.printVertexGeneratorNotations(justCycles.generator());
        });
    }

    private static void vertexColorSearchPI() {
        int[][][] piCubicSymm = GroupExplorer.parseOperationsArr(CubicGenerators.cubicPISymmetries_2);

        VertexColorSearch vcs = new VertexColorSearch(piCubicSymm, 24, PentagonalIcositrahedron::getFacesFromVertex, PentagonalIcositrahedron::getMatchingVertexFromFaces);

        for (ColorMapping c : vcs.searchForGenerators()) {
            int[] axes = c.axesSubgroup.vertex1Positions;
            if (axes.length >= 6) {
                int colors = (int) Arrays.stream(c.getVertexToColorMap()).distinct().count();
                
                // Pick the two large axis mappings
                System.out.println(c.axesSubgroup.order + " " + Arrays.toString(c.axesSubgroup.vertex1Positions));

                int[] vertices = c.axesSubgroup.vertex1Positions;

                int[][] cyclesUnified = new int[axes.length][];
                for (int i = 0; i < vertices.length; i++) {
                    cyclesUnified[i] = PentagonalIcositrahedron.getFacesFromVertex(vertices[i]);
                }
                
                // Select partitions of vertices

                List<int[]> combinations = Permu.generateCombinations(vertices.length, vertices.length / 2);

                Generator gUnified = Generator.combine(
                    new Generator(new int[][][] {cyclesUnified}),
                    new Generator(piCubicSymm)
                );
                System.out.println("Generating unified group with " + colors + " colors");
                checkGenerator(false, 13, gUnified);

                System.out.println("Generating split groups : " + combinations.size() + " combinations");

                for (int[] combination : combinations) {
                    HashSet<Integer> verticesB = new HashSet<>();
                    for (int v : vertices) verticesB.add(v);
                    int[][] cyclesSplit = new int[axes.length][];
                    for (int i = 0; i < combination.length; i++) {
                        int vertex = axes[combination[i]];
                        verticesB.remove(vertex);
                        cyclesSplit[i] = PentagonalIcositrahedron.getFacesFromVertex(vertex);
                    }
                    int i = 0;
                    for (int vertex : verticesB) {
                        cyclesSplit[combination.length + i] = CycleInverter.invertArray(PentagonalIcositrahedron.getFacesFromVertex(vertex));
                        i++;
                    }

                    //System.out.println(cyclesSplit.length);
                    
                    Generator gSplit = Generator.combine(
                        new Generator(new int[][][]{cyclesSplit}),
                        new Generator(piCubicSymm)
                    );

                    System.out.println("Generating split group with " + colors + " colors");
                    checkGenerator(false, 8, gSplit);
                }
            }
        }
    }

    private static void printGeneratorResults(Generator g, Set<State> stateCache) {
        String genString = GroupExplorer.generatorsToString(g.generator());
        System.out.println(genString);
        GroupExplorer candidate = new GroupExplorer(
            genString,
            MemorySettings.COMPACT, stateCache);

        IcosahedralGenerators.exploreGroup(candidate, null);
            
    }


    private static boolean checkGenerator(boolean debug, int transitivityMax, Generator g) {
        for (int transitivity = transitivityMax; transitivity <= transitivityMax; transitivity++) {

            if (debug) System.out.println("Checking transitivity " + transitivity);
            Set<State> stateCache = new LongStateCache(transitivity,24);
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
                    //PentagonalIcositrahedron.printVertexGeneratorNotations((new int[][][] {cycles}));
                    System.out.println(genString);
                }

                return true;
            }
        }
        return false;
    }

    public static void checkCube_8p() throws Exception {

        String genCandidate = "[(1,4,7,10)(2,5,8,11)(3,6,9,12)(17,20,23,15)(13,18,21,24)(16,19,22,14),(2,12,13,16)(3,10,14,17)(1,11,15,18)(6,8,22,21)(4,9,23,19)(5,7,24,20),(23,15,24)(14,12,13)(11,10,7)(22,8,9)(20,17,18)(2,16,3)(5,1,4)(6,19,21)]";
        

        int nElements = 24;
        int cacheGB = 32;
        int batch = 10_000_000;
        try (
            Scanner scanner = new Scanner(System.in);
            InteractiveCachePair cachePair =
                new InteractiveCachePair(scanner , cacheGB, nElements, batch)) {

            if (false) {
                int totalStates = cachePair.cache.size() + cachePair.cache_incomplete.size();

                System.out.println("Looping through " + totalStates + " states");

                HashMap<String, Integer> cycleDescriptions = new HashMap<>();
                for (State state : cachePair.cache) {
                    String cycleDescription = GroupExplorer.describeState(nElements, state.state());
                    cycleDescriptions.merge(cycleDescription, 1, Integer::sum);
                    if (cycleDescriptions.size() % 1_000_000 == 0) {
                        System.out.println("Processed " + cycleDescriptions.size() + " / " + totalStates + " states");
                    }
                }
                for (State state : cachePair.cache_incomplete) {
                    String cycleDescription = GroupExplorer.describeState(nElements, state.state());
                    cycleDescriptions.merge(cycleDescription, 1, Integer::sum);
                }

                printCycleDescriptions(cycleDescriptions);
            }
            
            cachePair.cache.debug(true);
            cachePair.cache_incomplete.debug(true);

            if (cachePair.cache.size() > 0) {
                System.out.println("Pre-initializing incomplete states to size " + cachePair.cache.size());
                cachePair.cache_incomplete.addAll(cachePair.cache);
            }
            GroupExplorer ge_ord = new GroupExplorer(genCandidate, MemorySettings.COMPACT,
                    cachePair.cache, cachePair.cache_incomplete, cachePair.cache_tmp, true);
			ge_ord.exploreStates(true, null);

            System.out.println("Order: " + ge_ord.order());
		}
		
    }
    public static void findHalfCube_8p() throws Exception {
        
        int[][] symm0 = GroupExplorer.parseOperationsArr(cubicPISymmetries_2)[0];
        int[][] symm1 = GroupExplorer.parseOperationsArr(cubicPISymmetries_2)[1];
        

        System.out.println("Starting cube 8p search");
        System.out.println("Press Q + Enter at any time to interrupt");

        Thread.sleep(1000);

        ArrayList<Generator> validVertexCombinations = new ArrayList<>();
        int[][] axes = new int[][] {
            {23, 15, 24},
            {13, 12, 14},
            {11, 10, 7},
            {9, 8, 22},
            {20, 17, 18},
            {3, 16, 2},
            {5, 1, 4},
            {21, 19, 6}
        };


        boolean[][] fixedCycleIndices = new boolean[][] {
            {true, false, false, false},
        };
        // Choose 4
        PermuCallback.generateCombinations(8, 4, (b) -> {

            int[][] axesPermute = new int[][] {
                axes[b[0]],
                axes[b[1]],
                axes[b[2]],
                axes[b[3]],
            };

            List<int[][][]> inverted = CycleInverter.generateInvertedCycles(fixedCycleIndices, new int[][][] {axesPermute});

            for (int[][][] g : inverted) {
                validVertexCombinations.add(new Generator(g));
            }

        });

        
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

            checkGenerator(false, g, results, smallGroupGenerators, new LongStateCache(8, 24));

                
            
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

    public static void findCube_8p() throws Exception {
        
        int[][] symm0 = GroupExplorer.parseOperationsArr(cubicPISymmetries_2)[0];
        int[][] symm1 = GroupExplorer.parseOperationsArr(cubicPISymmetries_2)[1];
        

        System.out.println("Starting cube 8p search");
        System.out.println("Press Q + Enter at any time to interrupt");

        Thread.sleep(1000);

        ArrayList<Generator> validVertexCombinations = new ArrayList<>();
        int[][] axes = new int[][] {
            {23, 15, 24},
            {13, 12, 14},
            {11, 10, 7},
            {9, 8, 22},
            {20, 17, 18},
            {3, 16, 2},
            {5, 1, 4},
            {21, 19, 6}
        };
        boolean[][] fixedCycleIndices = new boolean[][] {
            {true, false, false, false, false ,false, false ,false},
        };
        List<int[][][]> inverted = CycleInverter.generateInvertedCycles(fixedCycleIndices, new int[][][] {axes});

        for (int[][][] g : inverted) {
            validVertexCombinations.add(new Generator(g));
        }

        
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

            checkGenerator(false, g, results, smallGroupGenerators, new LongStateCache(8, 24));

                
            
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
                checkGenerator(false, g, results, smallGroupGenerators);
            
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

	// 180 6p works
    public static void find180_6p() throws Exception {
        int[][] symm0 = getFace180Symm(1);
        int[][] symm1 = getFace180Symm(2);

        System.out.println("Starting 180 search");
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

            if (iteration[0] % 10 == 0) {
                checkProgressEstimate(iteration[0], combinations, startTime, validVertexCombinations.size(), results.size());
            }

			Generator g = new Generator(new int[][][] {
				symm0,
				symm1,
				vertices.generator()[0],
			});

            //vertices2.parallelStream().forEach(g -> {
                checkGenerator(false, g, results, smallGroupGenerators);
            
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

    public static void fullPairSearch() throws Exception {
        System.out.println("Starting full pair search");
        System.out.println("Press Q + Enter at any time to interrupt");

        Thread.sleep(3000);

        ArrayList<Generator> validVertexCombinations = get6pVertexCombinations();
        Collections.shuffle(validVertexCombinations);
        
        System.out.println("Found " + validVertexCombinations.size() + " possible generators");
        
        int[] iteration = new int[] {0};
        long combinations = validVertexCombinations.size();
        long startTime = System.currentTimeMillis();

        // Synchronized
        List<String> results = Collections.synchronizedList(new ArrayList<String>());
        Map<Integer, List<String>> smallGroupGenerators =Collections.synchronizedMap(new TreeMap<>());

        for (Generator vertices : validVertexCombinations) {

            if (checkQuit() == -1) {
                System.out.println("QUITTING");
                break;
            }

            if (iteration[0] % 10 == 0) {
                checkProgressEstimate(iteration[0], combinations, startTime, validVertexCombinations.size(), results.size());
            }

            List<Generator> vertices2 = new ArrayList<>();

            tryagain: for (Generator v : validVertexCombinations) {
                Generator g = new Generator(new int[][][] {
                    v.generator()[0],
                    vertices.generator()[0]});

                // Check coverage of faces is greater than 21
                int[] faceCoverage = new int[24];
                for (int[][] cycles : g.generator()) {
                    for (int[] cycle : cycles) {
                        for (int f : cycle) faceCoverage[f - 1]++;
                    }
                }
                // Count how many faces have coverage of 1 or more
                int coverageCount = 0;
                for (int f : faceCoverage) {
                    if (f >= 1) coverageCount++;
                }
                if (coverageCount <= 23) {
                    //System.out.println("Skip");
                    continue tryagain;
                }

                // Make sure none of the cycles are the same
                for (int[] cycleA : g.generator()[0]) {
                    int[] sortedCycleA = Arrays.copyOf(cycleA, cycleA.length);
                    Arrays.sort(sortedCycleA); // Sort the elements of cycleA

                    for (int[] cycleB : g.generator()[1]) {
                        int[] sortedCycleB = Arrays.copyOf(cycleB, cycleB.length);
                        Arrays.sort(sortedCycleB); // Sort the elements of cycleB

                        if (Arrays.equals(sortedCycleA, sortedCycleB)) {
                            continue tryagain; // Skip if any cycle contains the same elements
                        }
                    }
                }

                vertices2.add(g);
            }

            System.out.println("Checking " + vertices2.size() + " matches");

            vertices2.parallelStream().forEach(g -> {
                checkGenerator(false, g, results, smallGroupGenerators);
            
            });


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

    private static ArrayList<Generator> get6pVertexCombinations() {

        HashSet<Generator> generatorCache = new HashSet<>();

        boolean[][] fixedCycleIndices = new boolean[][] {
            {true, false, false, false, false, false},
        };

        HashSet<Integer> uniqueFaces = new HashSet<>();
        PermuCallback.generateCombinations(32, 6, (b) -> {
    

            int[][] cyclesA = new int[][] {
                PentagonalIcositrahedron.getFacesFromVertex(b[0] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[1] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[2] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[3] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[4] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[5] + 1),
            };


            uniqueFaces.clear();
            for (int[] cycle : cyclesA) {
                for (int face : cycle) {
                    uniqueFaces.add(face);
                }
            }
            
            if (uniqueFaces.size() == 6*3) {

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

    private static ArrayList<Generator> get8pVertexCombinations() {

        HashSet<Generator> generatorCache = new HashSet<>();

        boolean[][] fixedCycleIndices = new boolean[][] {
            {true, false, false, false, false, false, false, false},
        };

        HashSet<Integer> uniqueFaces = new HashSet<>();
        PermuCallback.generateCombinations(32, 8, (b) -> {
    

            int[][] cyclesA = new int[][] {
                PentagonalIcositrahedron.getFacesFromVertex(b[0] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[1] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[2] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[3] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[4] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[5] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[6] + 1),
                PentagonalIcositrahedron.getFacesFromVertex(b[7] + 1),
            };


            uniqueFaces.clear();
            for (int[] cycle : cyclesA) {
                for (int face : cycle) {
                    uniqueFaces.add(face);
                }
            }
            
            if (uniqueFaces.size() == 6*4) {

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

    
    private static int checkQuit() {
        try {
            // Check for key press
            while (System.in.available() > 0) {
                int i = System.in.read();
                if (i == 'q' || i == 'Q') {
                    return -1;
                }
            }
        } catch (Exception e) {}
        return 0;
    }
    private static void checkProgressEstimate(int currentIteration, long totalCombinations, long startTime, int validCombinations, int results) {
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        long estimatedTotalTime = (long) ((double) elapsedTime / currentIteration * totalCombinations);
        long remainingTime = estimatedTotalTime - elapsedTime;
        
        String remainingTimeStr = String.format("%d hours, %d minutes, %d seconds",
            remainingTime / 3600000,
            (remainingTime % 3600000) / 60000,
            (remainingTime % 60000) / 1000);
        
        System.out.println(currentIteration + " / " + totalCombinations + " -> " + results +
            " | Estimated time remaining: " + remainingTimeStr);

    }

    private static void checkGenerator(boolean debug, Generator g, List<String> lgGroupResults, Map<Integer, List<String>> smallGroupGenerators) {
        checkGenerator(debug, g, lgGroupResults, smallGroupGenerators, new M24StateCache());
    }

    private static void checkGenerator(boolean debug, Generator g, List<String> lgGroupResults, Map<Integer, List<String>> smallGroupGenerators, Set<State> cache) {
        String genString = GroupExplorer.generatorsToString(g.generator());
        GroupExplorer candidate = new GroupExplorer(
            genString,
            MemorySettings.FASTEST, cache);
            

        ArrayList<String> depthPeek = new ArrayList<>();
        int startCheckingRatioIncreaseAtOrder = 313692;/// 739215;
        int limit = 30400000 / 4;
        int[] stateCount = new int[2];
        int iters = -2;

        //System.out.println(genString);
        
        double lastRatio = 0;
        candidate.initIterativeExploration();
        HashMap<String, Integer> cycleDescriptions = new HashMap<>();
        while (iters == -2) {
            int[] depthA = new int[] {0};
            try {
                iters = candidate.iterateExploration(debug, limit, (states, depth) -> {
                    stateCount[0] = stateCount[1];
                    stateCount[1] += states.size();
                    depthA[0] = depth;
                    for (int[] s : states) {
                        String desc = GroupExplorer.describeState(candidate.nElements, s);
                        cycleDescriptions.merge(desc, 1, Integer::sum);
                        // Heuristic 3: If there are more than 50 cycle descriptions, it's the alternating group
                        if (cycleDescriptions.size() > 50) {
                            throw new RuntimeException("Too many cycle descriptions");
                        }
                    }
                });
            } catch (ParityStateCache.StateRejectedException e) {
                // Heuristic 1:
                //    If the 7-transitive cache is too small we've generated a non-M24 group
                
                // Fail because this can't happen for valid M24 generators
                iters = -2;
                //System.out.println("M24 cache is different");
                break;
            } catch (RuntimeException e) {
                iters = -2;
                break;
            }
            int depth = depthA[0];
            double ratio = depth < 2 ? 10000 : stateCount[1]/(double)stateCount[0];
            if (depth > 5 ) depthPeek.add(ratio+" " + stateCount[1] + ",");

            //System.out.println(stateCountB[0] + " " + stateCount[1]);

            // Heuristic 2: If the ratio of states is increasing, it's not going to converge quickly
            boolean isDecreasing = ratio - lastRatio < 0.01;
            if (!isDecreasing && stateCount[1] > startCheckingRatioIncreaseAtOrder) {
                //iters = -2;
                //System.out.println("Ratio rate increase");
                //break;
            }
            
            
            
            lastRatio = ratio;
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
        } else if (candidate.order() > 1) {
            // Add genString to smallGroupGenerators
            List<String> gens = smallGroupGenerators.computeIfAbsent(candidate.order(), k -> Collections.synchronizedList(new ArrayList<String>()));
            gens.add(genString);
            System.out.println("Found order " + (candidate.order()) + ": " + GroupExplorer.generatorsToString(g.generator()));
        }
        

    }
}
