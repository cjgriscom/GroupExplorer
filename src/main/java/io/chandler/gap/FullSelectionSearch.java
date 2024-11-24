package io.chandler.gap;

import java.io.IOException;
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
import io.chandler.gap.cache.M12StateCache;
import io.chandler.gap.cache.LongStateCache;
import io.chandler.gap.cache.ParityStateCache;
import io.chandler.gap.cache.State;

public class FullSelectionSearch {
	public static void main(String[] args) throws Exception{
		//runDodecahedralSearch();
		runPentagonalIcositrahedralSearch();
	}

	public static void runDodecahedralSearch() {
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

        
        //System.exit(0);
        int maxGroupSize = 95040+2;
        boolean considerReverse = true;

        FullSelectionSearch search = new FullSelectionSearch(
            symmG,
            20,
            1,
            (i) -> Dodecahedron.vertexFaces[i-1],
            (generator) -> {
                GroupExplorer group = new GroupExplorer(
                    GroupExplorer.generatorsToString(generator),
                    MemorySettings.FASTEST, new M12StateCache());
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


        System.out.println("Searching for 3x2 selections");
        search.exhaustiveMultiAxisSearch(3, 2, considerReverse);
        System.out.println("Searching for 4x2 selections");
        search.exhaustiveMultiAxisSearch(4, 2, considerReverse);
        System.out.println("Searching for 5x2 selections");
        search.exhaustiveMultiAxisSearch(5, 2, considerReverse);
        System.out.println("Searching for 6x2 selections");
        search.exhaustiveMultiAxisSearch(6, 2, considerReverse);
        System.out.println("Searching for 7x2 selections");
        search.exhaustiveMultiAxisSearch(7, 2, considerReverse);
        System.out.println("Searching for 8x2 selections");
        search.exhaustiveMultiAxisSearch(8, 2, considerReverse);
        System.out.println("Searching for 9x2 selections");
        search.exhaustiveMultiAxisSearch(9, 2, considerReverse);
        System.out.println("Searching for 10x2 selections");
        search.exhaustiveMultiAxisSearch(10, 2, considerReverse);

        System.out.println("Searching for 2x3 selections");
        search.exhaustiveMultiAxisSearch(2, 3, considerReverse);
        System.out.println("Searching for 3x3 selections");
        search.exhaustiveMultiAxisSearch(3, 3, considerReverse);
        System.out.println("Searching for 4x3 selections");
        search.exhaustiveMultiAxisSearch(4, 3, considerReverse);
        System.out.println("Searching for 5x3 selections");
        search.exhaustiveMultiAxisSearch(5, 3, considerReverse);
        System.out.println("Searching for 6x3 selections");
        search.exhaustiveMultiAxisSearch(6, 3, considerReverse);

        System.out.println("Searching for 2x4 selections");
        search.exhaustiveMultiAxisSearch(2, 4, considerReverse);
        System.out.println("Searching for 3x4 selections");
        search.exhaustiveMultiAxisSearch(3, 4, considerReverse);
        System.out.println("Searching for 4x4 selections");
        search.exhaustiveMultiAxisSearch(4, 4, considerReverse);
        System.out.println("Searching for 5x4 selections");
        search.exhaustiveMultiAxisSearch(5, 4, considerReverse);

        System.out.println("Searching for 3,3,4 selections");
        search.exhaustiveMultiAxisSearch(Arrays.asList(3, 3, 4), considerReverse);

	}

	public static void runPentagonalIcositrahedralSearch() throws IOException {
        Generator symmG = new Generator(GroupExplorer.parseOperationsArr(CubicGenerators.cubicPISymmetries_2));

        boolean considerReverse = true;

        int maxGroupSize = 443520+2; // TODO M22

        //int maxGroupSize = 10200960 + 2; // M23

        boolean includeOctahedral = true;
        boolean forceOctahedral = false;

        
        FullSelectionSearch search = new FullSelectionSearch(
            symmG,
            includeOctahedral ? 32 : 24,
            forceOctahedral ? 25 : 1,
            (i) -> PentagonalIcositrahedron.piverticesToPifaces[i-1],
            (generator) -> {
                GroupExplorer group = new GroupExplorer(
                    GroupExplorer.generatorsToString(generator),
                    MemorySettings.FASTEST, new ParityStateCache(new LongStateCache(8, 24)));

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
        


        System.out.println("Searching for 2x6 selections");
        search.exhaustiveMultiAxisSearch(2, 6, considerReverse);

	}

    Generator symmG;
    final int nAxes;
    final int initialAxis;
    HashSet<State> symm = new HashSet<>();
    Function<int[][][], Integer> groupChecker;

    Function<Integer, int[]> getFaceAboutVertex;
    public FullSelectionSearch(Generator symmG, int nAxes, int initialAxis, Function<Integer, int[]> getFaceAboutVertex, Function<int[][][], Integer> groupChecker) {
        this.symmG = symmG;
        this.nAxes = nAxes;
        this.initialAxis = initialAxis;
        this.getFaceAboutVertex = getFaceAboutVertex;
        this.groupChecker = groupChecker;

        GroupExplorer symmEx = new GroupExplorer(GroupExplorer.generatorsToString(symmG.generator()), MemorySettings.FASTEST, symm);
        Generators.exploreGroup(symmEx, null);
    }

    /**
     * Helper method to support legacy behavior with nSelections and nAxesPerSelection.
     * It constructs a list with nAxesPerSelection repeated nSelections times.
     */
    public void exhaustiveMultiAxisSearch(int nSelections, int nAxesPerSelection, boolean considerReverse) {
        List<Integer> axesPerSelection = new ArrayList<>();
        for (int i = 0; i < nSelections; i++) {
            axesPerSelection.add(nAxesPerSelection);
        }
        exhaustiveMultiAxisSearch(axesPerSelection, considerReverse);
    }
    
    /**
     * New method that accepts a list specifying the number of axes per selection.
     * Example: List {4, 3, 3} selects 10 axes in groups of 4, 3, and 3.
     */
    public void exhaustiveMultiAxisSearch(List<Integer> axesPerSelection, boolean considerReverse) {        
        Map<Integer, List<int[][][]>> results = new TreeMap<>();
        List<Integer> selections = new ArrayList<>();
        HashSet<Generator> cache = new HashSet<>();

        int n = 0;
        for (int i = 0; i < axesPerSelection.size(); i++) {
            n += axesPerSelection.get(i);
        }

        // Select initial axis always 1 (positive direction)
        int axis0 = initialAxis;
        selections.add(axis0);

        exhaustiveMultiAxisSearchRecursive(cache, considerReverse, n, axesPerSelection, selections, results);

        GapInterface gap;
        try {
            gap = new GapInterface();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        
        for (Entry<Integer, List<int[][][]>> entry : results.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().size());
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
                try {
                    String gapResult = gap.runGapCommands(GroupExplorer.generatorsToString(result), 3).get(2).trim();
                    System.out.println("   " + GroupExplorer.generatorsToString(result) + " - elements=" + nElements + " - " + gapResult);
                } catch (Exception e) {
                    System.out.println("   " + GroupExplorer.generatorsToString(result) + " - elements=" + nElements + " - Error");
                    try {
                        gap.reset();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * Recursive helper method for exhaustiveMultiAxisSearch.
     */
    private void exhaustiveMultiAxisSearchRecursive(Set<Generator> cache, boolean considerReverse, int n, List<Integer> axesPerSelection, List<Integer> selections, Map<Integer, List<int[][][]>> results) {
        // If on boundary
        boolean onBoundary = false;
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
            int iCumulative = 0;
            for (int i = 0; i < generator.length; i++) {
                int nAxesInGroup = axesPerSelection.get(i);
                generator[i] = new int[nAxesInGroup][];
                for (int j = 0; j < nAxesInGroup; j++) {
                    int selection = selections.get(iCumulative);
                    int axis = Math.abs(selection);
                    int[] face = getFaceAboutVertex.apply(axis);
                    if (selection < 0) {
                        face = reverseArray(face);
                    }
                    generator[i][j] = face;
                    iCumulative++;
                }
            }

            int[][][] normalizedGenerator = normalizeGenerator(generator);
            if (cache.contains(new Generator(normalizedGenerator))) {
                return;
            }

            int order = groupChecker.apply(generator);
            if (order < -1) {
                return;
            }
            // Cache
            for (State state : symm) {
                int[] replacements = state.state();
                int[][][] replacementGenerator = new int[normalizedGenerator.length][][];
                for (int i = 0; i < normalizedGenerator.length; i++) {
                    replacementGenerator[i] = new int[normalizedGenerator[i].length][];
                    for (int j = 0; j < normalizedGenerator[i].length; j++) {
                        replacementGenerator[i][j] = new int[normalizedGenerator[i][j].length];
                        for (int k = 0; k < normalizedGenerator[i][j].length; k++) {
                            replacementGenerator[i][j][k] = replacements[normalizedGenerator[i][j][k] - 1];
                        }
                    }
                }
                cache.add(new Generator(normalizeGenerator(replacementGenerator)));
            }
            cache.add(new Generator(normalizedGenerator));

            
            // Add result if this is the full generator
            if (selections.size() == n) {
                List<int[][][]> resultsForOrder = results.get(order);
                if (resultsForOrder == null) {
                    resultsForOrder = new ArrayList<>();
                    results.put(order, resultsForOrder);
                }
                resultsForOrder.add(generator.clone());
                return;
            }

        }

        try {
            if (System.in.available() > 0 && System.in.read() == ' ') System.out.println("Results: " + results.size() + ", Selections: " + selections);
        } catch (Exception e) {}
        //System.out.println("Remaining axes: " + getRemainingAxes(selections, axesPerSelection));
        Collection<Integer> remainingAxes = getRemainingAxes(considerReverse, selections, axesPerSelection);

        for (int signedAxis : remainingAxes) {
            selections.add(signedAxis);
            exhaustiveMultiAxisSearchRecursive(cache, considerReverse, n, axesPerSelection, selections, results);
            selections.remove(selections.size() - 1);
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
            if (i <= maxAxisInCurrentGroup ||
                usedFaces.contains(faces[0]) || usedFaces.contains(faces[1]) || usedFaces.contains(faces[2])) {
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





    private static int[][][] normalizeGenerator(int[][][] generator) {
        // Clone the generator to avoid modifying the original array
        int[][][] normalized = generator.clone();

        // Sort each middle int[][] array
        for (int i = 0; i < normalized.length; i++) {
            Arrays.sort(normalized[i], new Comparator<int[]>() {
                @Override
                public int compare(int[] a, int[] b) {
                    for (int j = 0; j < Math.min(a.length, b.length); j++) {
                        if (a[j] != b[j]) {
                            return Integer.compare(a[j], b[j]);
                        }
                    }
                    return Integer.compare(a.length, b.length);
                }
            });
        }

        // Sort the outer int[][][] array based on the sorted middle arrays
        Arrays.sort(normalized, new Comparator<int[][]>() {
            @Override
            public int compare(int[][] a, int[][] b) {
                for (int i = 0; i < Math.min(a.length, b.length); i++) {
                    int[] arr1 = a[i];
                    int[] arr2 = b[i];
                    for (int j = 0; j < Math.min(arr1.length, arr2.length); j++) {
                        if (arr1[j] != arr2[j]) {
                            return Integer.compare(arr1[j], arr2[j]);
                        }
                    }
                    if (arr1.length != arr2.length) {
                        return Integer.compare(arr1.length, arr2.length);
                    }
                }
                return Integer.compare(a.length, b.length);
            }
        });

        return normalized;
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

}
