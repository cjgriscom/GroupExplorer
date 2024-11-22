package io.chandler.gap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.M12StateCache;
import io.chandler.gap.cache.State;

public class FullSelectionSearch {
	public static void main(String[] args) {
		runDodecahedralSearch();
	}

	public static int[] getDodecahedronFaceAboutVertex(int i) {
		return IcosahedralGenerators.dodecahedronFaceAboutVertex_Shallow[Math.abs(i)-1];
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

        HashSet<State> symm = new HashSet<>();
        GroupExplorer symmEx = new GroupExplorer(GroupExplorer.generatorsToString(symmG.generator()), MemorySettings.FASTEST, symm);
        IcosahedralGenerators.exploreGroup(symmEx, null);
        //System.exit(0);


        int limit = 95040+2;
        boolean considerReverse = true;

        System.out.println("Searching for 3x2 selections");
        exhaustiveMultiAxisSearch(3, 2, limit, symm, considerReverse);
        System.out.println("Searching for 4x2 selections");
        exhaustiveMultiAxisSearch(4, 2, limit, symm, considerReverse);
        System.out.println("Searching for 5x2 selections");
        exhaustiveMultiAxisSearch(5, 2, limit, symm, considerReverse);
        System.out.println("Searching for 6x2 selections");
        exhaustiveMultiAxisSearch(6, 2, limit, symm, considerReverse);
        System.out.println("Searching for 7x2 selections");
        exhaustiveMultiAxisSearch(7, 2, limit, symm, considerReverse);
        System.out.println("Searching for 8x2 selections");
        exhaustiveMultiAxisSearch(8, 2, limit, symm, considerReverse);
        System.out.println("Searching for 9x2 selections");
        exhaustiveMultiAxisSearch(9, 2, limit, symm, considerReverse);
        System.out.println("Searching for 10x2 selections");
        exhaustiveMultiAxisSearch(10, 2, limit, symm, considerReverse);

        System.out.println("Searching for 2x3 selections");
        exhaustiveMultiAxisSearch(2, 3, limit, symm, considerReverse);
        System.out.println("Searching for 3x3 selections");
        exhaustiveMultiAxisSearch(3, 3, limit, symm, considerReverse);
        System.out.println("Searching for 4x3 selections");
        exhaustiveMultiAxisSearch(4, 3, limit, symm, considerReverse);
        System.out.println("Searching for 5x3 selections");
        exhaustiveMultiAxisSearch(5, 3, limit, symm, considerReverse);
        System.out.println("Searching for 6x3 selections");
        exhaustiveMultiAxisSearch(6, 3, limit, symm, considerReverse);

        System.out.println("Searching for 2x4 selections");
        exhaustiveMultiAxisSearch(2, 4, limit, symm, considerReverse);
        System.out.println("Searching for 3x4 selections");
        exhaustiveMultiAxisSearch(3, 4, limit, symm, considerReverse);
        System.out.println("Searching for 4x4 selections");
        exhaustiveMultiAxisSearch(4, 4, limit, symm, considerReverse);
        System.out.println("Searching for 5x4 selections");
        exhaustiveMultiAxisSearch(5, 4, limit, symm, considerReverse);

        System.out.println("Searching for 3,3,4 selections");
        exhaustiveMultiAxisSearch(Arrays.asList(3, 3, 4), limit, symm, considerReverse);

	}

    /**
     * Helper method to support legacy behavior with nSelections and nAxesPerSelection.
     * It constructs a list with nAxesPerSelection repeated nSelections times.
     */
    public static void exhaustiveMultiAxisSearch(int nSelections, int nAxesPerSelection, int maxGroupSize, Set<State> symm, boolean considerReverse) {
        List<Integer> axesPerSelection = new ArrayList<>();
        for (int i = 0; i < nSelections; i++) {
            axesPerSelection.add(nAxesPerSelection);
        }
        exhaustiveMultiAxisSearch(axesPerSelection, maxGroupSize, symm, considerReverse);
    }
    
    /**
     * New method that accepts a list specifying the number of axes per selection.
     * Example: List {4, 3, 3} selects 10 axes in groups of 4, 3, and 3.
     */
    public static void exhaustiveMultiAxisSearch(List<Integer> axesPerSelection, int maxGroupSize, Set<State> symm, boolean considerReverse) {        
        Map<Integer, List<int[][][]>> results = new TreeMap<>();
        List<Integer> selections = new ArrayList<>();
        HashSet<Generator> cache = new HashSet<>();

        int n = 0;
        for (int i = 0; i < axesPerSelection.size(); i++) {
            n += axesPerSelection.get(i);
        }

        // Select initial axis always 1 (positive direction)
        int axis0 = 1;
        selections.add(axis0);

        exhaustiveMultiAxisSearchRecursive(cache, considerReverse, n, axesPerSelection, maxGroupSize, selections, results, symm);

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
                String gapResult = gap.runGapCommands(GroupExplorer.generatorsToString(result), 3).get(2).trim();
                System.out.println("   " + GroupExplorer.generatorsToString(result) + " - elements=" + nElements + " - " + gapResult);
            }
        }
    }


    /**
     * Recursive helper method for exhaustiveMultiAxisSearch.
     */
    private static void exhaustiveMultiAxisSearchRecursive(Set<Generator> cache, boolean considerReverse, int n, List<Integer> axesPerSelection, int maxGroupSize, List<Integer> selections, Map<Integer, List<int[][][]>> results, Set<State> symm) {
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
                    int[] face = getDodecahedronFaceAboutVertex(axis);
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

            GroupExplorer group = new GroupExplorer(GroupExplorer.generatorsToString(generator), MemorySettings.FASTEST, new M12StateCache());
            try {
                int iterations = group.exploreStates(false, maxGroupSize, null);
                if (iterations <= 0) {
                    return;
                }
            } catch (Exception e) {
                //System.out.println("Error exploring states: " + e.getMessage());
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
                int order = group.order();
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
            exhaustiveMultiAxisSearchRecursive(cache, considerReverse, n, axesPerSelection, maxGroupSize, selections, results, symm);
            selections.remove(selections.size() - 1);
        }
    }

    private static Collection<Integer> getRemainingAxes(boolean considerReverse, List<Integer> axes, List<Integer> axesPerSelection) {
        HashSet<Integer> remainingAxes = new HashSet<>();
        for (int i = 1; i <= 20; i++) {
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
            for (int face : getDodecahedronFaceAboutVertex(axis)) {
                usedFaces.add(face);
            }
        }

        //System.out.println("Selections: " + axes + " - i0=" + i0);
        Iterator<Integer> it = remainingAxes.iterator();
        while (it.hasNext()) {
            int i = it.next();
            int[] faces = getDodecahedronFaceAboutVertex(i);
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
