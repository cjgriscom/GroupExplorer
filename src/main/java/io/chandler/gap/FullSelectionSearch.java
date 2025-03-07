package io.chandler.gap;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.LongStateCache;
import io.chandler.gap.cache.LongLongStateCache;
import io.chandler.gap.cache.ParityStateCache;
import io.chandler.gap.cache.State;
import io.chandler.gap.render.Cuboctahedron;
import io.chandler.gap.render.Icosahedron;
import io.chandler.gap.render.Icosidodecahedron;
import io.chandler.gap.render.SnubCube;
import io.chandler.gap.render.SnubDodecahedron;

public class FullSelectionSearch {
	public static void main(String[] args) throws Exception{
        //runRhombicDodecahedralSearch();
		//runDodecahedralSearch();
        //runRhombicTriacontahedralSearch();
		runPentagonalIcositrahedralSearch();
        //runPentagonalHexecontahedralSearch();
	}



    static FullSelectionSearch getCubeEdgeSearch(boolean considerReverse, boolean reduceMirror) {
        int elementsToStore = 6; // Limits the transitivity in results
        int maxGroupSize = 95040+2;

        Generator symmG = Generator.combine(new Generator(
            new int[][] {
                {1,2,3,4},{7,9,11,5},{8,10,12,6}
            }), new Generator(
            new int[][] {
                {2,7,8,9},{1,6,10,3},{4,5,12,11}
            }));

        if (reduceMirror) {
            symmG = Generator.combine(symmG, new Generator(
                new int[][]{ {12,8}, {5,7}, {2,4}, {9,11} }));
        }


        Cuboctahedron cuboctahedron = new Cuboctahedron();

        FullSelectionSearch search = new FullSelectionSearch(
            symmG,
            8,
            1,
            (i) -> cuboctahedron.getFaceVertices(i-1),
            cuboctahedron::getPosOrNegFaceFromGenerator,
            (generator) -> {
                // Renumber is important for cache behavior
                GroupExplorer group = new GroupExplorer(
                    GroupExplorer.generatorsToString((generator)),
                    MemorySettings.FASTEST, new ParityStateCache(new LongStateCache(elementsToStore,12)));
                try {
                    int iterations = group.exploreStates(false, maxGroupSize, null);
                    if (iterations <= 0) {
                        return iterations;
                    }
                    
                } catch (Exception e) {
                    //System.out.println("Error exploring states: " + e.getMessage());
                    return -2;
                }
                return group.order();
            });

        return search;
    }

	public static void runRhombicDodecahedralSearch() {

        boolean considerReverse = true;
        boolean reduceMirror = true;
        FullSelectionSearch search = getCubeEdgeSearch(considerReverse, reduceMirror);
        
        System.out.println("Searching for 1,1,1 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(1,1,1), considerReverse, true);

        System.out.println("Searching for 2,1 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(2,1), considerReverse, true);

        System.out.println("Searching for 3,2 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3,2), considerReverse, true);

        System.out.println("Searching for 4,3 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(4,3), considerReverse, true);

        System.out.println("Searching for 4,2 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(4,2), considerReverse, true);


        System.out.println("Searching for 2x2 selections");
        search.exhaustiveMultiAxisSearch(2, 2, considerReverse, true);
        System.out.println("Searching for 3x2 selections");
        search.exhaustiveMultiAxisSearch(3, 2, considerReverse, true);



        System.out.println("Searching for 2x3 selections");
        search.exhaustiveMultiAxisSearch(2, 3, considerReverse, true);
        System.out.println("Searching for 3x3 selections");
        search.exhaustiveMultiAxisSearch(3, 3, considerReverse, true);

        System.out.println("Searching for 2x4 selections");
        search.exhaustiveMultiAxisSearch(2, 4, considerReverse, true);

	}




    static FullSelectionSearch getIcosahedralSearch(boolean considerReverse, boolean reduceMirror) {
        int elementsToStore = 6; // Limits the transitivity in results
        int maxGroupSize = 95040+2;

        Generator symmG = Generator.combine(new Generator(
            new int[][] {
                IcosahedralGenerators.dodecahedronFaceAboutVertex[0],
                IcosahedralGenerators.dodecahedronFaceAboutVertex[1],
                IcosahedralGenerators.dodecahedronFaceAboutVertex[2],
                IcosahedralGenerators.dodecahedronFaceAboutVertex[3]
            }), new Generator(
            new int[][] {
                IcosahedralGenerators.dodecahedronFaceAboutVertex[4],
                IcosahedralGenerators.dodecahedronFaceAboutVertex[5],
                IcosahedralGenerators.dodecahedronFaceAboutVertex[6],
                IcosahedralGenerators.dodecahedronFaceAboutVertex[7]
            }));

        if (reduceMirror) {
            symmG = Generator.combine(symmG, new Generator(
                IcosahedralGenerators.icosahedronMirrorSymm));
        }


        Icosahedron icosa = new Icosahedron();

        FullSelectionSearch search = new FullSelectionSearch(
            symmG,
            20,
            1,
            (i) -> Dodecahedron.vertexFaces[i-1],
            icosa::getPosOrNegFaceFromGenerator,
            (generator) -> {
                // Renumber is important for cache behavior
                GroupExplorer group = new GroupExplorer(
                    GroupExplorer.generatorsToString((generator)),
                    MemorySettings.FASTEST, new ParityStateCache(new LongStateCache(elementsToStore,12)));
                try {
                    int iterations = group.exploreStates(false, maxGroupSize, null);
                    if (iterations <= 0) {
                        return iterations;
                    }
                    
                } catch (Exception e) {
                    //System.out.println("Error exploring states: " + e.getMessage());
                    return -2;
                }
                return group.order();
            });

        return search;
    }

	public static void runDodecahedralSearch() {

        boolean considerReverse = true;
        boolean reduceMirror = true;
        FullSelectionSearch search = getIcosahedralSearch(considerReverse, reduceMirror);

        System.out.println("Searching for misc selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3, 4), considerReverse, true);
	
        System.exit(0);

        System.out.println("Searching for 3x2 selections");
        search.exhaustiveMultiAxisSearch(3, 2, considerReverse, true);
        System.out.println("Searching for 4x2 selections");
        search.exhaustiveMultiAxisSearch(4, 2, considerReverse, true);
        System.out.println("Searching for 5x2 selections");
        search.exhaustiveMultiAxisSearch(5, 2, considerReverse, true);
        System.out.println("Searching for 6x2 selections");
        search.exhaustiveMultiAxisSearch(6, 2, considerReverse, true);
        System.out.println("Searching for 7x2 selections");
        search.exhaustiveMultiAxisSearch(7, 2, considerReverse, true);
        System.out.println("Searching for 8x2 selections");
        search.exhaustiveMultiAxisSearch(8, 2, considerReverse, true);
        System.out.println("Searching for 9x2 selections");
        search.exhaustiveMultiAxisSearch(9, 2, considerReverse, true);
        System.out.println("Searching for 10x2 selections");
        search.exhaustiveMultiAxisSearch(10, 2, considerReverse, true);

        System.out.println("Searching for 2x3 selections");
        search.exhaustiveMultiAxisSearch(2, 3, considerReverse, true);
        System.out.println("Searching for 3x3 selections");
        search.exhaustiveMultiAxisSearch(3, 3, considerReverse, true);
        System.out.println("Searching for 4x3 selections");
        search.exhaustiveMultiAxisSearch(4, 3, considerReverse, true);
        System.out.println("Searching for 5x3 selections");
        search.exhaustiveMultiAxisSearch(5, 3, considerReverse, true);
        System.out.println("Searching for 6x3 selections");
        search.exhaustiveMultiAxisSearch(6, 3, considerReverse, true);

        System.out.println("Searching for 2x4 selections");
        search.exhaustiveMultiAxisSearch(2, 4, considerReverse, true);
        System.out.println("Searching for 3x4 selections");
        search.exhaustiveMultiAxisSearch(3, 4, considerReverse, true);
        System.out.println("Searching for 4x4 selections");
        search.exhaustiveMultiAxisSearch(4, 4, considerReverse, true);
        System.out.println("Searching for 5x4 selections");
        search.exhaustiveMultiAxisSearch(5, 4, considerReverse, true);

        System.out.println("Searching for 3,3,4 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3, 3, 4), considerReverse, true);
        System.out.println("Searching for 3,3,3,4 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3, 3, 3, 4), considerReverse, true);
        System.out.println("Searching for 3,3,3,3,4 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3, 3, 3, 3, 4), considerReverse, true);
        System.out.println("Searching for 3,4,4 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3, 4, 4), considerReverse, true);
        System.out.println("Searching for 3,3,4,4 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3, 3, 4, 4), considerReverse, true);
	}



    static FullSelectionSearch getDodecahedronEdgeSearch(boolean considerReverse, boolean reduceMirror) {
        
        int elementsToStore = 8; // Limits the transitivity in results
        int maxGroupSize = 443520+2; // TODO M22

        Generator symmG = Generator.combine(new Generator(
            new int[][] {
                {1,2,3,4,5},{6,9,20,19,27},
                {7,13,15,25,28},{26,29,8,16,18},
                {30,10,12,14,24},{11,17,21,23,22}
            }), new Generator(
            new int[][] {
                {1,6,7,8,9},{2,5,29,10,13},
                {22,12,20,4,28},{11,16,3,27,30},
                {26,23,17,15,19},{14,18,25,24,21}
            }));

        if (reduceMirror) {
            symmG = Generator.combine(symmG, new Generator(
                new int[][]{ 
                    {24,25},{21,18},{17,15},{12,16},
                    {8,9},{1,7},{5,29},{27,28},
                    {30,4},{19,23},{11,20},{2,10},
                    {22,3}
                }));
        }


        Icosidodecahedron icosidodecahedron = new Icosidodecahedron();

        FullSelectionSearch search = new FullSelectionSearch(
            symmG,
            20,
            1,
            (i) -> icosidodecahedron.getFaceVertices(i-1),
            icosidodecahedron::getPosOrNegFaceFromGenerator,
            (generator) -> {
                GroupExplorer group = new GroupExplorer(
                    GroupExplorer.generatorsToString(GroupExplorer.renumberGenerators_fast(generator)),
                    MemorySettings.FASTEST, new ParityStateCache(new LongStateCache(elementsToStore, 25)));

                group.initIterativeExploration();
                int iters = -2;
                
                while (iters <= 0) {
                    try {
                        Set<String> descs = Collections.synchronizedSet(new HashSet<>());
                        iters = group.iterateExploration(false, maxGroupSize, (states, depth) -> {
                            if (states.size() > 10000) {
                                try {
                                    states.parallelStream().forEach(s -> {
                                        String desc = GroupExplorer.describeStateForCache(group.nElements, s);
                                        synchronized(descs) {
                                            descs.add(desc);
                                            if (descs.size() > 35) {
                                                throw new RuntimeException("Too many descriptions");
                                            }
                                        }
                                    });
                                } catch (Exception e) {
                                    throw new RuntimeException("Too many descriptions");
                                }
                            } else {
                                for (int[] s : states) {
                                    
                                    String desc = GroupExplorer.describeStateForCache(group.nElements, s);
                                    descs.add(desc);
                                    if (descs.size() > 35) { // TODO
                                    
                                        //System.out.println(desc);
                                        //System.out.println("Reject due to cycle desc " + desc + " - " + descs.size());
                                        throw new RuntimeException();
                                    }
                                }
                            }
                        });
                        
                        if (iters == -1) {
                            //System.out.println("Lapsed");
                            //System.out.println(GroupExplorer.generatorsToString(generator));
                            return -1;
                        }
                    } catch (Exception e) {
                        //System.out.println("Exception " + e.getMessage());
                        //System.out.println("Error exploring states: " + e.getMessage());
                        return -2;
                    }
                }
                //System.out.println("Order: " + group.order());
                
                return group.order();
            });

        return search;
    }

	public static void runRhombicTriacontahedralSearch() {

        boolean considerReverse = true;
        boolean reduceMirror = true;
        FullSelectionSearch search = getDodecahedronEdgeSearch(considerReverse, reduceMirror);
        
        System.out.println("Searching for 2x6 selections");
        search.exhaustiveMultiAxisSearch(2, 8, considerReverse, true);


	}




	public static void runPentagonalHexecontahedralSearch() throws IOException {
        Generator symmG = new Generator(GroupExplorer.parseOperationsArr(PHGenerators.triPHSymmetryF1));
        symmG = Generator.combine(symmG, new Generator(GroupExplorer.parseOperationsArr(PHGenerators.pentPHSymmetryF1)));

        int elementsToStore = 21; // Limits the transitivity in results
        boolean considerReverse = false;
        int maxGroupSize = 102660+2; // TODO PSL(2,59)

        boolean includeIcosahedral = true;

        SnubDodecahedron snubDodecahedron = new SnubDodecahedron();
        
        FullSelectionSearch search = new FullSelectionSearch(
            symmG,
            includeIcosahedral ? 80 : 60,
            1,
            (i) -> PentagonalHexecontahedron.phverticesToPhfaces[i-1],
            snubDodecahedron::getPosOrNegFaceFromGenerator,
            (generator) -> {

                if (true) return 1; // TODO getting axis selections 1st

                GroupExplorer group = new GroupExplorer(
                    GroupExplorer.generatorsToString(GroupExplorer.renumberGenerators_fast(generator)),
                    MemorySettings.FASTEST, new ParityStateCache(new LongLongStateCache(elementsToStore, 61)));

                group.initIterativeExploration();
                int iters = -2;
                
                while (iters <= 0) {
                    try {
                        Set<String> descs = Collections.synchronizedSet(new HashSet<>());
                        iters = group.iterateExploration(false, maxGroupSize, (states, depth) -> {
                            if (states.size() > 10000) {
                                try {
                                    states.parallelStream().forEach(s -> {
                                        String desc = GroupExplorer.describeStateForCache(group.nElements, s);
                                        synchronized(descs) {
                                            descs.add(desc);
                                            if (descs.size() > 11) {
                                                throw new RuntimeException("Too many descriptions");
                                            }
                                        }
                                    });
                                } catch (Exception e) {
                                    throw new RuntimeException("Too many descriptions");
                                }
                            } else {
                                for (int[] s : states) {
                                    
                                    String desc = GroupExplorer.describeStateForCache(group.nElements, s);
                                    descs.add(desc);
                                    if (descs.size() > 11) { // TODO
                                    
                                        //System.out.println(desc);
                                        //System.out.println("Reject due to cycle desc " + desc + " - " + descs.size());
                                        throw new RuntimeException();
                                    }
                                }
                            }
                        });
                        
                        if (iters == -1) {
                            //System.out.println("Lapsed");
                            //System.out.println(GroupExplorer.generatorsToString(generator));
                            return -1;
                        }
                    } catch (Exception e) {
                        //System.out.println("Exception " + e.getMessage());
                        //System.out.println("Error exploring states: " + e.getMessage());
                        return -2;
                    }
                }
                //System.out.println("Order: " + group.order());
                
                return group.order();
            });
        

        System.out.println("Searching for 3x20 selections");
        search.exhaustiveMultiAxisSearch(3, 20, considerReverse, false);

    }

	public static void runPentagonalIcositrahedralSearch() throws IOException {
        Generator symmG = new Generator(GroupExplorer.parseOperationsArr(CubicGenerators.cubicPISymmetries_2));

        int elementsToStore = 8; // Limits the transitivity in results
        boolean considerReverse = true;

        int maxGroupSize = 1344+2; // TODO M12
        //int maxGroupSize = 443520+2; // TODO M22

        //int maxGroupSize = 10200960 + 2; // M23

        boolean includeOctahedral = false;
        boolean forceOctahedral = false;

        SnubCube snubCube = new SnubCube();
        
        FullSelectionSearch search = new FullSelectionSearch(
            symmG,
            includeOctahedral ? 32 : 24,
            forceOctahedral ? 25 : 1,
            (i) -> PentagonalIcositrahedron.piverticesToPifaces[i-1],
            snubCube::getPosOrNegFaceFromGenerator,
            (generator) -> {
                GroupExplorer group = new GroupExplorer(
                    GroupExplorer.generatorsToString(GroupExplorer.renumberGenerators_fast(generator)),
                    MemorySettings.FASTEST, new ParityStateCache(new LongStateCache(elementsToStore, 25)));

                group.initIterativeExploration();
                int iters = -2;
                
                while (iters <= 0) {
                    try {
                        Set<String> descs = Collections.synchronizedSet(new HashSet<>());
                        iters = group.iterateExploration(false, maxGroupSize, (states, depth) -> {
                            if (states.size() > 10000) {
                                try {
                                    states.parallelStream().forEach(s -> {
                                        String desc = GroupExplorer.describeStateForCache(group.nElements, s);
                                        synchronized(descs) {
                                            descs.add(desc);
                                            if (descs.size() > 35) {
                                                throw new RuntimeException("Too many descriptions");
                                            }
                                        }
                                    });
                                } catch (Exception e) {
                                    throw new RuntimeException("Too many descriptions");
                                }
                            } else {
                                for (int[] s : states) {
                                    
                                    String desc = GroupExplorer.describeState(group.nElements, s);
                                    descs.add(desc);
                                    if (descs.size() > 35) { // TODO
                                    
                                        //System.out.println(desc);
                                        //System.out.println("Reject due to cycle desc " + desc + " - " + descs.size());
                                        throw new RuntimeException();
                                    }
                                }
                            }
                        });
                        
                        if (iters == -1) {
                            //System.out.println("Lapsed");
                            //System.out.println(GroupExplorer.generatorsToString(generator));
                            return -2;
                        }

                    } catch (Exception e) {
                        //System.out.println("Exception " + e.getMessage());
                        //System.out.println("Error exploring states: " + e.getMessage());
                        return -2;
                    }

                }
                //System.out.println("Order: " + group.order());
                
                return group.order();
            });
        

        System.out.println("Searching for 2x4 selections");
        search.exhaustiveMultiAxisSearch(2, 4, considerReverse, false);

        //System.out.println("Searching for 2x8 selections");
        //search.exhaustiveMultiAxisSearch(2, 8, considerReverse);
        //System.out.println("Searching for 3x8 selections");
        //search.exhaustiveMultiAxisSearch(3, 8, considerReverse);
        //System.out.println("Searching for 4x8 selections");
        //search.exhaustiveMultiAxisSearch(4, 8, considerReverse);

	}

    Generator symmG;
    final int nAxes;
    final int initialAxis;
    int[][] axisSymm;
    Function<int[][][], Integer> groupChecker;

    Function<Integer, int[]> getFaceAboutVertex;
    Function<int[], Integer> getVertexFromFacesReversable;

    long cacheHits = 0, totalCacheChecks = 0;

    public FullSelectionSearch(Generator symmG, int nAxes, int initialAxis, Function<Integer, int[]> getFaceAboutVertex, Function<int[], Integer> getVertexFromFaces, Function<int[][][], Integer> groupChecker) {
        this.symmG = symmG;
        this.nAxes = nAxes;
        this.initialAxis = initialAxis;
        this.getFaceAboutVertex = getFaceAboutVertex;
        this.getVertexFromFacesReversable = getVertexFromFaces;
        this.groupChecker = groupChecker;

        HashSet<State> symm = new HashSet<>();
        GroupExplorer symmEx = new GroupExplorer(GroupExplorer.generatorsToString(symmG.generator()), MemorySettings.FASTEST, symm);
        Generators.exploreGroup(symmEx, null);
        ArrayList<State> symmList = new ArrayList<>();
        for (State state : symm) {
            symmList.add(state);
        }
        this.axisSymm = new int[nAxes][];
        for (int i = 0; i < nAxes; i++) {
            int[] face = getFaceAboutVertex.apply(i+1);
            axisSymm[i] = new int[symm.size()];
            for (int j = 0; j < symm.size(); j++) {
                int[] faceCopy = face.clone();
                for (int k = 0; k < faceCopy.length; k++) {
                    faceCopy[k] = symmList.get(j).state()[face[k] - 1];
                }
                axisSymm[i][j] = getVertexFromFacesReversable.apply(faceCopy);
            }
        }
    }

    /**
     * Checks if sub is a subset of main - if so it returns the first found
     * indices of main that align with the subset.  If not, it returns null.
     * @param main
     * @param sub
     * @return
     */
    public int[] asSubset(Generator main, Generator sub) {
        boolean debug = false;

        // Build cache from main that matches the layout of sub

        // Sub lengths are, for example, {4, 3, 3}
        // Main lengths are, for example, {4, 4, 4, 3, 3, 3}

        FSSCache cache = new FSSCache();
        int[][] mainAxesSelections = buildAxisSelectionsForCache(main.generator());
        int[][] subAxesSelections = buildAxisSelectionsForCache(sub.generator());

        // Sort both arrays by their length in reverse order
        Arrays.sort(mainAxesSelections, (a, b) -> b.length - a.length);
        Arrays.sort(subAxesSelections, (a, b) -> b.length - a.length);

        if (debug) {
            System.out.println("Main: " + Arrays.deepToString(mainAxesSelections));
            System.out.println("Sub: " + Arrays.deepToString(subAxesSelections));
        }
        
        // If the structure of main is {4, 4, 4, 3, 3, 3}, this will contain { 4=3, 4=3 }
        TreeMap<Integer, Integer> layoutMain = new TreeMap<>(Comparator.reverseOrder());
        for (int i = 0; i < mainAxesSelections.length; i++) {
            int key = mainAxesSelections[i].length;
            layoutMain.putIfAbsent(key, 0);
            layoutMain.put(key, layoutMain.get(key) + 1);
        }
        // If the structure of sub is {4, 3, 3}, this will contain { 4=1, 3=2 }
        TreeMap<Integer, Integer> layoutSub = new TreeMap<>(Comparator.reverseOrder());
        for (int i = 0; i < subAxesSelections.length; i++) {
            int key = subAxesSelections[i].length;
            layoutSub.putIfAbsent(key, 0);
            layoutSub.put(key, layoutSub.get(key) + 1);
        }

        if (debug) {
            System.out.println("Layout main: " + layoutMain);
            System.out.println("Layout sub: " + layoutSub);
        }

        // Cache every permutation of main that matches the layout of sub
        // We'll have to loop over each unique length, pick int pr(subL, mainL)
        // So in the example, for the first length 4, it will be nPr(3, 1)
        //   then for the next length 3 it will be nPr(3, 2)

        // Loop through main

        ArrayList<int[]> permutations = new ArrayList<>();
        permutations.add(new int[0]); // Initial permutation is empty
        
        int mainOffset = 0;
        for (int mainK : layoutMain.keySet()) {
            int mainL = layoutMain.get(mainK);
            Integer subL = layoutSub.get(mainK);
            if (subL == null) {
                if (debug) {
                    System.out.println("No subL for mainL " + mainL);
                }
                return null;
            }
            if (mainL < subL) {
                if (debug) {
                    System.out.println("mainL < subL for mainL " + mainL + " and subL " + subL);
                }
                return null;
            }
            List<int[]> nPr = Permu.generatePermutations(mainL, subL);

            ArrayList<int[]> newPermutations = new ArrayList<>();
            // For each existing permutation, add the new indices
            for (int[] perm : permutations) {
                for (int[] additional : nPr) {
                    int[] newPerm = new int[perm.length + subL];
                    System.arraycopy(perm, 0, newPerm, 0, perm.length);
                    for (int i = 0; i < subL; i++) {
                        // The indices returned by nPr don't match the layout of sub
                        //   so we need to add the mainOffset to each index
                        newPerm[perm.length + i] = additional[i]-1 + mainOffset;
                    }
                    newPermutations.add(newPerm);
                }
            }
            permutations.addAll(newPermutations);

            // Update the indexing offsets
            mainOffset += mainL;
        }

        // Now loop through the permutations and add them to the cache
        for (int[] perm : permutations) {
            int[][] axisSelectionsForCache = new int[perm.length][];
            for (int i = 0; i < perm.length; i++) {
                axisSelectionsForCache[i] = mainAxesSelections[perm[i]];
            }
            cache.cacheWithMetadata(axisSelectionsForCache, perm);
        }

        // Now check if the cache contains the subAxesSelections
        int[][] match = cache.getMatch(subAxesSelections);
        if (match != null) {
            // Convert back to generator
            int[][][] generator = new int[match.length][][];
            for (int i = 0; i < match.length; i++) {
                generator[i] = new int[match[i].length][];
                for (int j = 0; j < match[i].length; j++) {
                    generator[i][j] = this.getFaceAboutVertex.apply(Math.abs(match[i][j])).clone();
                    if (match[i][j] < 0) {
                        generator[i][j] = reverseArray(generator[i][j]);
                    }
                }
            }

            return (int[]) cache.extractMetadata(match);
        }
        return null;
    }

    /**
     * Helper method to support legacy behavior with nSelections and nAxesPerSelection.
     * It constructs a list with nAxesPerSelection repeated nSelections times.
     */
    public void exhaustiveMultiAxisSearch(int nSelections, int nAxesPerSelection, boolean considerReverse, boolean useGap) {
        List<Integer> axesPerSelection = new ArrayList<>();
        for (int i = 0; i < nSelections; i++) {
            axesPerSelection.add(nAxesPerSelection);
        }
        exhaustiveMultiAxisSearch(axesPerSelection, considerReverse, useGap);
    }
    
    /**
     * Accepts a list specifying the number of axes per selection.
     * Example: List {4, 3, 3} selects 10 axes in groups of 4, 3, and 3.
     */
    public void exhaustiveMultiAxisSearch(List<Integer> axesPerSelection, boolean considerReverse, boolean useGap) {        
        Map<Integer, List<int[][][]>> results = new TreeMap<>();
        List<Integer> selections = new ArrayList<>();
        FSSCache cache = new FSSCache();

        cacheHits = 0;
        totalCacheChecks = 0;

        int n = 0;
        for (int i = 0; i < axesPerSelection.size(); i++) {
            n += axesPerSelection.get(i);
        }

        // Select initial axis always 1 (positive direction)
        int axis0 = initialAxis;
        selections.add(axis0);

        exhaustiveMultiAxisSearchRecursive(cache, considerReverse, n, axesPerSelection, selections, results);

        printResults(System.out, true, useGap, results);
    }

    private void printResults(PrintStream out, boolean printDetails, boolean printGap, Map<Integer, List<int[][][]>> results) {
        
        GapInterface gap;
        if (printGap) {
            try {
                gap = new GapInterface();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        } else {
            gap = null;
        }
        
        
        for (Entry<Integer, List<int[][][]>> entry : results.entrySet()) {
            out.println(entry.getKey() + ": " + entry.getValue().size());
            if (!printDetails) continue;
            for (int[][][] result : entry.getValue()) {
                HashSet<Integer> elements = new HashSet<>();
                int nElements = 0;
                for (int[][] cycle : result) {
                    for (int[] element : cycle) {
                        for (int e : element) {
                            elements.add(e);
                        }
                    }
                }
                nElements = elements.size();
                out.print("   " + GroupExplorer.generatorsToString(result) + " - elements=" + nElements);
                if (printGap) try {
                    String gapResult = gap.runGapCommands(GroupExplorer.generatorsToString(result), 3).get(2).trim();
                    out.print(" - " + gapResult);
                } catch (Exception e) {
                    out.print(" - Error");
                    try {
                        gap.reset();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
                out.println();
            }
        }
    }

    private int[][] buildAxisSelectionsForCache(int[][][] generator) {
        int[][] axesSelectionsForCache = new int[generator.length][];
        
        for (int i = 0; i < generator.length; i++) {
            int nAxesInGroup = generator[i].length;
            axesSelectionsForCache[i] = new int[nAxesInGroup];
            for (int j = 0; j < nAxesInGroup; j++) {
                int selection = this.getVertexFromFacesReversable.apply(generator[i][j]);
                axesSelectionsForCache[i][j] = selection;
            }
        }
        return axesSelectionsForCache;
    }

    /**
     * Recursive helper method for exhaustiveMultiAxisSearch.
     */
    private void exhaustiveMultiAxisSearchRecursive(FSSCache cache, boolean considerReverse, int n, List<Integer> axesPerSelection, List<Integer> selections, Map<Integer, List<int[][][]>> results) {
        // If on boundary
        boolean onBoundary = false;
        int[][] addBoundarySelectionToCache = null;
        int completeGroups = 0;
        int temp = 0;
        for (int nAxesInGroup : axesPerSelection) {
            temp += nAxesInGroup;
            completeGroups++;
            if (temp == selections.size()) {
                onBoundary = true;
                break;
            }
        }
        
        if (onBoundary) {
            // Cull
            int[][][] generator = new int[completeGroups][][];
            int[][] axesSelectionsForCache = new int[completeGroups][];
            int[][] boundarySelection = new int[1][];
            int iCumulative = 0;
            for (int i = 0; i < generator.length; i++) {
                int nAxesInGroup = axesPerSelection.get(i);
                generator[i] = new int[nAxesInGroup][];
                axesSelectionsForCache[i] = new int[nAxesInGroup];
                // Boundary selection is the last axis group in the set
                boundarySelection[0] = new int[nAxesInGroup];
                for (int j = 0; j < nAxesInGroup; j++) {
                    int selection = selections.get(iCumulative);
                    int axis = Math.abs(selection);
                    int[] face = getFaceAboutVertex.apply(axis);
                    if (selection < 0) {
                        face = reverseArray(face);
                    }
                    generator[i][j] = face;
                    axesSelectionsForCache[i][j] = selection;
                    iCumulative++;
                    boundarySelection[0][j] = selection;
                }
            }

            if (completeGroups == 1) totalCacheChecks++;
            if (cache.checkContains(axesSelectionsForCache)) {
                if (completeGroups == 1) cacheHits++;
                return;
            }

            // Cull further by checking if cache contains new boundary selection
            // This is why we wait until later to add current selection to cache
            if (completeGroups != 1 && cache.checkContains(boundarySelection)) {
                // Stop this branch; it's already been explored by a previous branch
                return;
            }
            
            int order = groupChecker.apply(generator);
            if (order < -1) {
                return;
            } else {
                // Add boundary selection to cache after this branch is explored
                addBoundarySelectionToCache = axesSelectionsForCache;
            }
            
            // Add result if this is the full generator
            if (selections.size() == n) {
                List<int[][][]> resultsForOrder = results.get(order);
                if (resultsForOrder == null) {
                    resultsForOrder = new ArrayList<>();
                    results.put(order, resultsForOrder);
                }
                resultsForOrder.add(generator.clone());
                // Cache here before returning
                cache.cache(axesSelectionsForCache);
                return;
            }

        }

        try {
            if (System.in.available() > 0) {
                int in = System.in.read();
                if (in == ' ') {
                    System.err.println("Results: " + results.size() + ", Selections: " + selections);
                } else if (in == 'd') {
                    System.err.println("Cache size: " + cache.cache.size() + ", Cache hits: " + cacheHits + ", Total cache checks: " + totalCacheChecks + " (" + (100.0 * cacheHits / totalCacheChecks) + "%)");
                }
                else if (in == 'g') printResults(System.err, true, true, results);
                else if (in == 'p') printResults(System.err, true, false, results);
                else if (in == '\t') printResults(System.err, false, false, results);
            }
        } catch (Exception e) {}
        //System.out.println("Remaining axes: " + getRemainingAxes(selections, axesPerSelection));
        Collection<Integer> remainingAxes = getRemainingAxes(considerReverse, selections, axesPerSelection);

        for (int signedAxis : remainingAxes) {
            selections.add(signedAxis);
            exhaustiveMultiAxisSearchRecursive(cache, considerReverse, n, axesPerSelection, selections, results);
            selections.remove(selections.size() - 1);
        }

        if (addBoundarySelectionToCache != null) {
            cache.cache(addBoundarySelectionToCache);
        }
    }

    private Collection<Integer> getRemainingAxes(boolean considerReverse, List<Integer> axes, List<Integer> axesPerSelection) {
        HashSet<Integer> remainingAxes = new HashSet<>();
        for (int i = 1; i <= this.nAxes; i++) {
            remainingAxes.add(i);
        }
        for (int axis : axes) {
            remainingAxes.remove(Math.abs(axis));
        }

        HashSet<Integer> usedFaces = new HashSet<>();
        int i0 = 0; // Find the boundary of current selection set
        for (int nAxesInGroup : axesPerSelection) {
            if (i0 + nAxesInGroup <= axes.size()) i0 += nAxesInGroup;
            else break;
        }
        boolean firstInGroup = i0 == axes.size();
        int maxAxisInCurrentGroup = 0;
        for (int i = i0; i < axes.size(); i++) {
            int axis = Math.abs(axes.get(i));
            maxAxisInCurrentGroup = Math.max(maxAxisInCurrentGroup, axis);
            for (int face : getFaceAboutVertex.apply(axis)) {
                usedFaces.add(face);
            }
        }

        //if (i0 == 12) System.out.println("Selections: " + axes + " - i0=" + i0);
        Iterator<Integer> it = remainingAxes.iterator();
        while (it.hasNext()) {
            int i = it.next();
            int[] faces = getFaceAboutVertex.apply(i);
            boolean anyFaceUsed = false;
            for (int f : faces) anyFaceUsed |= usedFaces.contains(f);
            if (i <= maxAxisInCurrentGroup || anyFaceUsed) {
                it.remove();
            }
        }

        // Add negative axes, avoiding redundant cases
        if (considerReverse && !firstInGroup) {
            for (int axis : new HashSet<>(remainingAxes)) {
                remainingAxes.add(-axis);
            }
        }
        return remainingAxes;
    }


    /**
     * Utility method to reverse an integer array.
     */
    private static int[] reverseArray(int[] array) {
        int[] reversed = new int[array.length];
        for(int i=0; i < array.length; i++) {
            reversed[i] = array[array.length - 1 - i];
        }
        return reversed;
    }

    static class FSSCacheObject {
        byte[] data;
        Integer cachedHashCode;
        Object metadata;
        public FSSCacheObject(int[][] axes) {
            this(axes, null);
        }
        public FSSCacheObject(int[][] axes, Object metadata) {
            this.metadata = metadata;
            int length = axes.length;
            for (int[] axis : axes) {
                length += axis.length;
            }
            this.data = new byte[length];
            int offset = 0;
            for (int[] axis : axes) {
                data[offset++] = (byte) axis.length;
                for (int face : axis) {
                    data[offset++] = (byte) face;
                }
            }
            this.cachedHashCode = null;
        }
        
        public int hashCode() {
            if (cachedHashCode == null) cachedHashCode = Arrays.hashCode(data);
            return cachedHashCode;
        }
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || !(obj instanceof FSSCacheObject)) return false;
            return Arrays.equals(data, ((FSSCacheObject) obj).data);
        }
    }
    class FSSCache {
        Set<FSSCacheObject> cache = new HashSet<>();
    
    
        int[][] normalizeAxes(int[][] axes) {
            int[][] normalized = new int[axes.length][];
            // Sort each inner array, ignoring sign, then
            //   correct signs such that first element is positive
            for (int i = 0; i < axes.length; i++) {
                int[] axis = axes[i];
                normalized[i] = new int[axis.length];
                Integer[] sortedAxis = new Integer[axis.length];
                for (int j = 0; j < axis.length; j++) sortedAxis[j] = axis[j];
                Arrays.sort(sortedAxis, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer a, Integer b) {
                        return Integer.compare(Math.abs(a), Math.abs(b));
                    }
                });
                boolean positive = sortedAxis[0] > 0;
                for (int j = 0; j < sortedAxis.length; j++) {
                    if (positive) normalized[i][j] = sortedAxis[j];
                    else normalized[i][j] = -sortedAxis[j];
                }
            }
            // Sort each outer array by first element
            Arrays.sort(normalized, new Comparator<int[]>() {
                @Override
                public int compare(int[] a, int[] b) {
                    return Integer.compare(a[0], b[0]);
                }
            });
            return normalized;
        }

        public void cache(int[][] axesSelectionsForCache) {
            cache.add(new FSSCacheObject(normalizeAxes(axesSelectionsForCache)));
        }

        public void cacheWithMetadata(int[][] axesSelectionsForCache, Object metadata) {
            cache.add(new FSSCacheObject(normalizeAxes(axesSelectionsForCache), metadata));
        }
        
        public boolean checkContains(int[][] axesSelectionsForCache) {
            return getMatch(axesSelectionsForCache) != null;
        }
        public int[][] getMatch(int[][] axesSelectionsForCache) {
            
            int[][] normalizedAxes = normalizeAxes(axesSelectionsForCache);
            if (cache.contains(new FSSCacheObject(normalizedAxes))) {
                return normalizedAxes;
            }

            int symmSize = axisSymm[0].length;
            for (int i = 0; i < symmSize; i++) {
                int[][] replacementAxes = new int[normalizedAxes.length][];
                for (int j = 0; j < normalizedAxes.length; j++) {
                    replacementAxes[j] = new int[normalizedAxes[j].length];
                    for (int k = 0; k < normalizedAxes[j].length; k++) {
                        boolean positive = normalizedAxes[j][k] > 0;
                        replacementAxes[j][k] = axisSymm[Math.abs(normalizedAxes[j][k]) - 1][i];
                        if (!positive) replacementAxes[j][k] *= -1;
                    }
                }

                int[][] tmpCheck = normalizeAxes(replacementAxes);
                if (cache.contains(new FSSCacheObject(tmpCheck))) {
                    return tmpCheck;
                }
            }
            
            return null;
        }

        public Object extractMetadata(int[][] axesSelectionsForCache) {
            FSSCacheObject tmp = new FSSCacheObject(axesSelectionsForCache);
            Iterator<FSSCacheObject> it = cache.iterator();
            while (it.hasNext()) {
                FSSCacheObject obj = it.next();
                if (Arrays.equals(obj.data, tmp.data)) {
                    return obj.metadata;
                }
            }
            return null;
        }
    }
    
}
