package io.chandler.gap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Permu {
    public static void main(String[] args) {
        int[][] operation = new int[][] {
            new int[] {1, 2, 3},{4,5,6}
        };
        int[][] operation2 = new int[][] {
            new int[] {7,1,2},
            new int[] {4,8,5}
        };
        int[][] operation3 = new int[][] {
            new int[] {2, 1, 3},
            new int[] {4, 8, 10},
        };
        int[][][] generator = new int[][][] {
            operation,
            operation2,
            operation3
        };
        List<int[][][]> isomorphs = new ArrayList<>();
        applyGeneratorPermutationsAndRotations(generator, isomorphs::add);
        for (int[][][] isomorph : isomorphs) {
            System.out.println(GroupExplorer.generatorsToString(GroupExplorer.renumberGenerators(isomorph)));
        }
    }

    /**
     * Generates all combinations of choosing K elements from a set of N elements.
     *
     * @param N the total number of elements
     * @param K the number of elements to choose
     * @return a list of integer arrays, each array representing a combination of indices
     */
    public static List<int[]> generateCombinations(int N, int K) {
        List<int[]> combinations = new ArrayList<>();
        int[] combination = new int[K]; // This array will hold the current combination
        // Start the recursive process
        generateCombinationsHelper(combinations, combination, 0, N, 0);
        return combinations;
    }

    /**
     * Helper method to generate combinations recursively.
     *
     * @param combinations the list to store combinations
     * @param combination the current combination being built
     * @param start the next index to consider for inclusion in the combination
     * @param N the total number of elements
     * @param index the current index in the combination array
     */
    private static void generateCombinationsHelper(List<int[]> combinations, int[] combination, int start, int N, int index) {
        if (index == combination.length) {
            combinations.add(combination.clone()); // Add a copy of the combination to the list
            return;
        }

        for (int i = start; i < N; i++) {
            combination[index] = i;
            generateCombinationsHelper(combinations, combination, i + 1, N, index + 1);
        }
    }

    

    public static void applyGeneratorPermutationsAndRotations(int[][][] a, Consumer<int[][][]> results) {
        List<int[][][]> result = new ArrayList<>();
        List<List<int[][]>> statePermutations = new ArrayList<>();
    
        // Apply permutations and repetitions to each state
        for (int[][] state : a) {
            statePermutations.add(applyStatePermutationsAndRepetitions(state));
        }
    
        // Generate all combinations of permuted states
        generateCombinations(statePermutations, 0, new int[a.length][][], result);
        
        // Now loop through the result and apply all rotations to each state
        for (int i = 0; i < result.size(); i++) {
            int[][][] gen = result.get(i);

            int nCycles = 0;
            for (int[][] group : gen) {
                nCycles += group.length;
            }
            int[][] genFlattened = new int[nCycles][];
            int flatIndex = 0;
            for (int[][] group : gen) {
                for (int[] cycle : group) {
                    genFlattened[flatIndex++] = cycle;

                }
            }

            ArrayRotator.rotateSubArrays(genFlattened, (int[][] rotated) -> {
                int[][][] genRotated = new int[gen.length][][];
                int flatIndex2 = 0;
                for (int genI = 0; genI < gen.length; genI++) {
                    genRotated[genI] = new int[gen[genI].length][];
                    for (int genJ = 0; genJ < gen[genI].length; genJ++) {
                        genRotated[genI][genJ] = rotated[flatIndex2];
                        flatIndex2++;
                    }
                }
                results.accept(genRotated);
            });
            
        }
    }

    private static void generateCombinations(List<List<int[][]>> statePermutations, int index, int[][][] current, List<int[][][]> result) {
        if (index == statePermutations.size()) {
            result.add(current.clone());
            return;
        }
    
        for (int[][] permutedState : statePermutations.get(index)) {
            current[index] = permutedState;
            generateCombinations(statePermutations, index + 1, current, result);
        }
    }

    public static List<int[][]> applyStatePermutationsAndRepetitions(int[][] a) {
        
        ArrayList<int[][]> result = new ArrayList<>();
        List<int[]> permutationsA = Permu.generatePermutations(a.length);
        
        int cycleOrderA = a[0].length;

        int aReps = cycleOrderA % 2 == 0 ? 1 : cycleOrderA / 2;

        int permsAndRepetitionsA = permutationsA.size() * aReps;
        for (int i = 0; i < permsAndRepetitionsA; i++) {
            int permutationIndex = i % permutationsA.size();
            int[] permutationA = permutationsA.get(permutationIndex);
            int repA = i / permutationsA.size();
            
            int[][] aRep = GroupExplorer.repeatOperation(a, repA);
            int[][] aModified = new int[a.length][cycleOrderA];
            for (int j = 0; j < a.length; j++) {
                int[] cycleSrc = aRep[permutationA[j] - 1];
                aModified[j] = cycleSrc;
            }
            result.add(aModified);
            result.add(GroupExplorer.reverseOperation(aModified));
        }
        return result;
    }

    public static List<int[][]> applyStateRepetitions(int[][] a) {
        ArrayList<int[][]> result = new ArrayList<>();
        
        int cycleOrderA = a[0].length;

        int aReps = cycleOrderA % 2 == 0 ? 1 : cycleOrderA / 2;

        for (int repA = 0; repA < aReps; repA++) {
            int[][] aRep = GroupExplorer.repeatOperation(a, repA);
            result.add(aRep);
            result.add(GroupExplorer.reverseOperation(aRep));
        }
        return result;
    }

    
    public static List<int[]> generatePermutations(int n) {
        List<int[]> permutations = new ArrayList<>();
        int[] initial = new int[n];
        for (int i = 0; i < n; i++) {
            initial[i] = i + 1;
        }
        generatePermutationsHelper(initial, 0, permutations);
        return permutations;
    }

    private static void generatePermutationsHelper(int[] arr, int start, List<int[]> permutations) {
        if (start == arr.length - 1) {
            permutations.add(arr.clone());
            return;
        }

        for (int i = start; i < arr.length; i++) {
            swap(arr, start, i);
            generatePermutationsHelper(arr, start + 1, permutations);
            swap(arr, start, i); // backtrack
        }
    }

    private static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    /**
     * Generates all k-permutations of n elements (nPr).
     * 
     * @param n the total number of elements to choose from
     * @param k the number of elements to arrange
     * @return a list of integer arrays, each representing a permutation
     */
    public static List<int[]> generatePermutations(int n, int k) {
        List<int[]> permutations = new ArrayList<>();
        int[] current = new int[k];
        boolean[] used = new boolean[n];
        generatePermutationsHelper(n, k, 0, current, used, permutations);
        return permutations;
    }

    private static void generatePermutationsHelper(int n, int k, int index, int[] current, boolean[] used, List<int[]> permutations) {
        if (index == k) {
            permutations.add(current.clone());
            return;
        }

        for (int i = 0; i < n; i++) {
            if (!used[i]) {
                used[i] = true;
                current[index] = i + 1;  // Using 1-based indexing to match existing code
                generatePermutationsHelper(n, k, index + 1, current, used, permutations);
                used[i] = false;
            }
        }
    }
}
