package io.chandler.trapentrix;
import java.util.ArrayList;
import java.util.List;

public class CycleInverter {

    /**
     * Generates all combinations of inverted cycles for a given generator.
     *
     * @param generator the original generator as a three-dimensional integer array
     * @return a list of generators with all possible combinations of inverted cycles
     */
    public static List<int[][][]> generateInvertedCycles(int startIndex, int[][][] generator) {
        List<int[][][]> result = new ArrayList<>();
        generateInvertedCyclesHelper(generator, startIndex, 0, result, deepCopy(generator));
        return result;
    }

    /**
     * Helper method to recursively generate all combinations of inverted cycles.
     *
     * @param generator the original generator
     * @param stateIndex the current state index being processed
     * @param cycleIndex the current cycle index within the state being processed
     * @param result the list to store all combinations
     * @param current the current combination being built
     */
    private static void generateInvertedCyclesHelper(int[][][] generator, int stateIndex, int cycleIndex, List<int[][][]> result, int[][][] current) {
        if (stateIndex == generator.length) {
            result.add(deepCopy(current));
            return;
        }

        if (cycleIndex == generator[stateIndex].length) {
            generateInvertedCyclesHelper(generator, stateIndex + 1, 0, result, current);
            return;
        }

        // Generate with current cycle as is
        generateInvertedCyclesHelper(generator, stateIndex, cycleIndex + 1, result, current);

        // Generate with current cycle inverted
        current[stateIndex][cycleIndex] = invertArray(generator[stateIndex][cycleIndex]);
        generateInvertedCyclesHelper(generator, stateIndex, cycleIndex + 1, result, current);
        current[stateIndex][cycleIndex] = generator[stateIndex][cycleIndex]; // Reset after use
    }

    /**
     * Creates a deep copy of a three-dimensional integer array.
     *
     * @param array the array to copy
     * @return a deep copy of the array
     */
    private static int[][][] deepCopy(int[][][] array) {
        int[][][] copy = new int[array.length][][];
        for (int i = 0; i < array.length; i++) {
            copy[i] = new int[array[i].length][];
            for (int j = 0; j < array[i].length; j++) {
                copy[i][j] = array[i][j].clone();
            }
        }
        return copy;
    }

    /**
     * Inverts an array.
     *
     * @param array the array to invert
     * @return the inverted array
     */
    private static int[] invertArray(int[] array) {
        int[] inverted = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            inverted[i] = array[array.length - 1 - i];
        }
        return inverted;
    }

    public static void main(String[] args) {
        int[][][] generator = {
            {{1, 2}, {4, 0}},
            {{3, 4,5}}
        };

        List<int[][][]> combinations = generateInvertedCycles(0, generator);
        for (int[][][] combination : combinations) {
            System.out.println(java.util.Arrays.deepToString(combination));
        }
    }
}