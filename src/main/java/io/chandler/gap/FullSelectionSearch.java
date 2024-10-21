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
		return IcosahedralGenerators.dodecahedronFaceAboutVertex_Shallow[i-1];
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

        System.out.println("Searching for 2x3 selections");
        exhaustiveMultiAxisSearch(2, 3, 95040+2, symm);
        System.out.println("Searching for 3x3 selections");
        exhaustiveMultiAxisSearch(3, 3, 95040+2, symm);
        System.out.println("Searching for 4x3 selections");
        exhaustiveMultiAxisSearch(4, 3, 95040+2, symm);
        System.out.println("Searching for 5x3 selections");
        exhaustiveMultiAxisSearch(5, 3, 95040+2, symm);

        System.out.println("Searching for 2x4 selections");
        exhaustiveMultiAxisSearch(2, 4, 95040+2, symm);
        System.out.println("Searching for 3x4 selections");
        exhaustiveMultiAxisSearch(3, 4, 95040+2, symm);
        System.out.println("Searching for 4x4 selections");
        exhaustiveMultiAxisSearch(4, 4, 95040+2, symm);
        System.out.println("Searching for 5x4 selections");
        exhaustiveMultiAxisSearch(5, 4, 95040+2, symm);
	}
    public static void exhaustiveMultiAxisSearch(int nSelections, int nAxesPerSelection, int maxGroupSize, Set<State> symm) {
        Map<Integer, List<int[][][]>> results = new TreeMap<>();
        List<Integer> selections = new ArrayList<>();
        HashSet<Generator> cache = new HashSet<>();

        int n = nSelections * nAxesPerSelection;

        // Select initial axis always 1
        int axis0 = 1;
        selections.add(axis0);

        exhaustiveMultiAxisSearch(cache, n, nAxesPerSelection, maxGroupSize, selections, results, symm);

        for (Entry<Integer, List<int[][][]>> entry : results.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().size());
            for (int[][][] result : entry.getValue()) {
                System.out.println("   " + GroupExplorer.generatorsToString(result));
            }
        }
    }

    private static void exhaustiveMultiAxisSearch(Set<Generator> cache, int n, int nAxesPerSelection, int maxGroupSize, List<Integer> selections,  Map<Integer, List<int[][][]>> results, Set<State> symm) {
        if (selections.size() % nAxesPerSelection == 0) {
            // Cull

            int[][][] generator = new int[selections.size() / nAxesPerSelection][][];
            for (int i = 0; i < generator.length; i++) {
                generator[i] = new int[nAxesPerSelection][];
                for (int j = 0; j < nAxesPerSelection; j++) {
                    generator[i][j] = getDodecahedronFaceAboutVertex(selections.get(i*nAxesPerSelection + j));
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
                //ystem.out.println("Error exploring states: " + e.getMessage());
                return;
            }



            // Cache
            for (State state : symm) {
                int[] replacements = state.state();
                int[][][] replacementGenerator = new int[normalizedGenerator.length][normalizedGenerator[0].length][normalizedGenerator[0][0].length];
                for (int i = 0; i < normalizedGenerator.length; i++) {
                    for (int j = 0; j < normalizedGenerator[i].length; j++) {
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
        //System.out.println("Remaining axes: " + getRemainingAxes(selections, nAxesPerSelection));
        Collection<Integer> remainingAxes = getRemainingAxes(selections, nAxesPerSelection);

        for (int axis : remainingAxes) {
            selections.add(axis);
            exhaustiveMultiAxisSearch(cache, n, nAxesPerSelection, maxGroupSize, selections, results, symm);
            selections.remove(selections.size() - 1);
        }
    }

    private static Collection<Integer> getRemainingAxes(List<Integer> axes, int nAxesPerSelection) {
        HashSet<Integer> remainingAxes = new HashSet<>();
        for (int i = 1; i <= 20; i++) {
            remainingAxes.add(i);
        }
        remainingAxes.removeAll(axes);

        HashSet<Integer> usedFaces = new HashSet<>();
        for (int i = axes.size() / nAxesPerSelection * nAxesPerSelection; i < axes.size(); i++) {
            int axis = axes.get(i);
            for (int face : getDodecahedronFaceAboutVertex(axis)) {
                usedFaces.add(face);
            }
        }
        Iterator<Integer> it = remainingAxes.iterator();
        while (it.hasNext()) {
            int i = it.next();
            int[] faces = getDodecahedronFaceAboutVertex(i);
            if (usedFaces.contains(faces[0]) || usedFaces.contains(faces[1]) || usedFaces.contains(faces[2])) {
                it.remove();
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

}
