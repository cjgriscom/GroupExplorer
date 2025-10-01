package io.chandler.gap.solvers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import io.chandler.gap.CycleInverter;
import io.chandler.gap.Dodecahedron;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.GroupExplorer.PeekData;
import io.chandler.gap.cache.State;
import io.chandler.gap.render.Icosahedron;
import javafx.util.Pair;

public class MiscSolvers {

	public static void main(String[] args) {
		rockingCubeSimm();
	}
	
    public static void crammedCubeSimm() {
        // Red
        // Y W
        // Blue
        { // Distant state: (2,5)(3,7)(6,9)(10,12)
          //  R' L' R' L R' L R L R' L R' L R L' R L R' L'

        String turns = "[" +
            "(4,2,5)(12,10,1)(3,11,7)," +   // L
            "(2,6,7)(1,8,4)(3,5,9)," +      // R
            "(4,5,2)(12,1,10)(3,7,11)," +   // L'
            "(2,7,6)(1,4,8)(3,9,5)]";       // R'

            GroupExplorer groudp = new GroupExplorer(turns, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
                if (depth == 18) {
                    System.out.println(GroupExplorer.stateToNotation(states.get(0)));
                }
            });
        }
        String m12_dodot = "[" +
        "(4,2,5)(12,10,1)(3,11,7)(16,17,18)," +       // L
            "(2,6,7)(1,8,4)(3,5,9)(13,14,15)," +      // R
            "(4,5,2)(12,1,10)(3,7,11)(18,17,16)," +   // L'
            "(2,7,6)(1,4,8)(3,9,5)(15,14,13)]";       // R'

            GroupExplorer groudp = new GroupExplorer(m12_dodot, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
           // System.exit(0);

        String[] namesLookup = new String[] {
            "L'", "R'", "L", "R"
        };
        String[] inverseNamesLookup = new String[] {
            "L", "R", "L'", "R'"
        };

        int[][][] g = GroupExplorer.parseOperationsArr(m12_dodot);

        // Now g contains the full puzzle generator

        GroupExplorer group = new GroupExplorer(
            GroupExplorer.generatorsToString(g),
            MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        // I want to iteratively explore states until I find a match to this state:
        // Zeroes should be ignored in comparison
        // 1,2,9, 10  are the fixed pcs
       // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12, 13,14,15, 16,17,18};
        int[] stateMatch =   {1,5,7,4,2,9,3,8,6,12,11,10, 13,14,15, 16,17,18};

        group.setTrackPath(true);
        group.initIterativeExploration();

        /*
        group.applyOperation(0);
        System.out.println(Arrays.toString(group.copyCurrentState()));
        System.exit(0);*/

        ArrayList<int[]> matchingStates = new ArrayList<>();

        System.out.println(Arrays.toString(stateMatch));
        System.out.println(Arrays.toString(group.copyCurrentState()));

        HashMap<State, Pair<State, Integer>> backtrack = new HashMap<>();

        while (matchingStates.size() < 1) {

            group.iterateExploration(false, 300_000_000, true, (states, depth) -> {
                for (Object x : states) {
                    PeekData data = (PeekData) x;
                    backtrack.put(data.newState, new Pair<>(data.oldState, data.operation));
                    int[] state = data.newState.state();
                    boolean matches = true;
                    for (int i = 0; i < state.length; i++) {
                        if (stateMatch[i] != 0 && state[i] != stateMatch[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {

                        System.out.println(Arrays.toString(state));
                        matchingStates.add(state);
                        // Figure out path
                        State current = State.of(state, group.nElements, group.mem);
                        String op = "";
                        String inverseOp = "";
                        while (backtrack.containsKey(current)) {
                            Pair<State, Integer> d = backtrack.get(current);
                            op = namesLookup[d.getValue()] + " " + op;
                            inverseOp = inverseOp + " " + inverseNamesLookup[d.getValue()];
                            current = d.getKey();
                        }
                        System.out.println("Fwd: " + op);
                        System.out.println("Inv: " + inverseOp);
                        System.out.println(Arrays.toString(state));
                    }
                }
            });
            System.out.println(group.getIteration() + " " + group.order());
        }

    }

    public static void m12CubeSimm() {
        // Red
        // Y W
        // Blue
        // 1 GR  2 YR  3 YG  4 YO
        // 5 YB  6 BO  7 WB  8 WO
        // 9 WG 10 WR 11 GO 12 BR
        {
            // 11, 3, 4

        String turns = "[" +
            "(7,12,10)(5,6,4)(2,3,1)(11,9,8)(15,14,13)," +   // A'
            "(12,5,2)(1,10,9)(7,8,6)(18,17,16),"+       // B'
            "(10,12,7)(4,6,5)(1,3,2)(8,9,11)(13,14,15)," +   // A
            "(2,5,12)(9,10,1)(6,8,7)(16,17,18)]";      // B

            GroupExplorer groudp = new GroupExplorer(turns, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
                if (depth == 24 || depth == 23) {
                    for (int[] x : states) {
                        System.out.println(GroupExplorer.stateToNotation(x));
                    }
                }
            });

            System.out.println("Order: " + groudp.order());
        }


        
        String gen = "[" +
        "(7,12,10)(5,6,4)(2,3,1)(11,9,8)(15,14,13)," +   // A'
        "(12,5,2)(1,10,9)(7,8,6)(18,17,16),"+       // B'
        "(10,12,7)(4,6,5)(1,3,2)(8,9,11)(13,14,15)," +   // A
        "(2,5,12)(9,10,1)(6,8,7)(16,17,18)]";      // B

            GroupExplorer groudp = new GroupExplorer(gen, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
           // System.exit(0);

        String[] namesLookup = new String[] {
            "A'", "B'", "A", "B"
        };
        String[] inverseNamesLookup = new String[] {
            "A", "B", "A'", "B'"
        };

        int[][][] g = GroupExplorer.parseOperationsArr(gen);

        // Now g contains the full puzzle generator

        GroupExplorer group = new GroupExplorer(
            GroupExplorer.generatorsToString(g),
            MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        // I want to iteratively explore states until I find a match to this state:
        // Zeroes should be ignored in comparison
       // int[] stateMatch = { 1,2,3,4,5,6,7,8,9,10,11,12, 13,14,15, 16,17,18};
        int[] stateMatch =   { 1,2,3,4,5,6,7,8,9,10,11,12, 13,14,15, 17,18,16};
        // 1 GR  2 YR  3 YG  4 YO
        // 5 YB  6 BO  7 WB  8 WO
        // 9 WG 10 WR 11 GO 12 BR
        group.setTrackPath(true);
        group.initIterativeExploration();

        /*
        group.applyOperation(0);
        System.out.println(Arrays.toString(group.copyCurrentState()));
        System.exit(0);*/

        ArrayList<int[]> matchingStates = new ArrayList<>();

        System.out.println(Arrays.toString(stateMatch));
        System.out.println(Arrays.toString(group.copyCurrentState()));

        HashMap<State, Pair<State, Integer>> backtrack = new HashMap<>();

        while (matchingStates.size() < 1) {
            if (group.getIteration() > 24) {
                System.out.println("Match not found");
                System.exit(0);
            }

            group.iterateExploration(false, 300_000_000, true, (states, depth) -> {
                for (Object x : states) {
                    PeekData data = (PeekData) x;
                    backtrack.put(data.newState, new Pair<>(data.oldState, data.operation));
                    int[] state = data.newState.state();
                    boolean matches = true;
                    for (int i = 0; i < state.length; i++) {
                        if (stateMatch[i] != 0 && state[i] != stateMatch[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {

                        System.out.println(Arrays.toString(state));
                        matchingStates.add(state);
                        // Figure out path
                        State current = State.of(state, group.nElements, group.mem);
                        String op = "";
                        String inverseOp = "";
                        while (backtrack.containsKey(current)) {
                            Pair<State, Integer> d = backtrack.get(current);
                            op = namesLookup[d.getValue()] + " " + op;
                            inverseOp = inverseOp + " " + inverseNamesLookup[d.getValue()];
                            current = d.getKey();
                        }
                        System.out.println("Fwd: " + op);
                        System.out.println("Inv: " + inverseOp);
                        System.out.println(Arrays.toString(state));
                    }
                }
            });
            System.out.println(group.getIteration() + " " + group.order());
        }

    }


    public static void rockingCubeSimm() {
        // Red
        // Y W
        // Blue
		/*
[(1,8,4)(2,14,12)(5,9,7)(6,13,10),(1,3,12)(2,8,11)(4,7,13)(6,14,9)]
[(1,8,4)(2,14,12)(5,9,7)(6,13,10),(1,11,13)(2,7,10)(3,4,6)(8,14,9)]
[(1,8,4)(2,14,12)(5,9,7)(6,13,10),(1,14,5)(3,10,9)(6,7,11)(8,12,13)]
         */
        {//[(1,8,4)(2,14,12)(5,9,7)(6,13,10),(1,11,13)(2,7,10)(3,4,6)(8,14,9)]

        String turns = "[" +
            "(1,8,4)(2,14,12)(5,9,7)(6,13,10)(17,16,15)," +   // A'
            "(1,11,13)(2,7,10)(3,4,6)(8,14,9)(20,19,18),"+       // B'
            "(4,8,1)(12,14,2)(7,9,5)(10,13,6)(15,16,17)," +   // A
            "(13,11,1)(10,7,2)(6,4,3)(9,14,8)(18,19,20)]";      // B

            GroupExplorer groudp = new GroupExplorer(turns, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
                if (depth == 14) {
                    for (int[] x : states) {
                        System.out.println(GroupExplorer.stateToNotation(x));
                    }
                }
            });

            System.out.println("Order: " + groudp.order());
        }


        
        String gen = "[" +
		"(1,8,4)(2,14,12)(5,9,7)(6,13,10)(17,16,15)," +   // A'
		"(1,11,13)(2,7,10)(3,4,6)(8,14,9)(20,19,18),"+       // B'
		"(4,8,1)(12,14,2)(7,9,5)(10,13,6)(15,16,17)," +   // A
		"(13,11,1)(10,7,2)(6,4,3)(9,14,8)(18,19,20)]";      // B

            GroupExplorer groudp = new GroupExplorer(gen, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
           // System.exit(0);

        String[] namesLookup = new String[] {
            "Y'", "X'", "Y", "X"
        };
        String[] inverseNamesLookup = new String[] {
            "Y", "X", "Y'", "X'"
        };

        int[][][] g = GroupExplorer.parseOperationsArr(gen);

        // Now g contains the full puzzle generator

        GroupExplorer group = new GroupExplorer(
            GroupExplorer.generatorsToString(g),
            MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        // I want to iteratively explore states until I find a match to this state:
        // Zeroes should be ignored in comparison
       // int[] stateMatch = { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14, 15,16,17, 18,19,20};
        int[] stateMatch =   { 6,14,11,13, 5, 1, 9,10, 7, 8, 3,12, 4, 2, 15,16,17, 18,19,20};
        group.setTrackPath(true);
        group.initIterativeExploration();

        /*
        group.applyOperation(0);
        System.out.println(Arrays.toString(group.copyCurrentState()));
        System.exit(0);*/

        ArrayList<int[]> matchingStates = new ArrayList<>();

        System.out.println(Arrays.toString(stateMatch));
        System.out.println(Arrays.toString(group.copyCurrentState()));

        HashMap<State, Pair<State, Integer>> backtrack = new HashMap<>();

        while (matchingStates.size() < 1) {
            if (group.getIteration() > 24) {
                System.out.println("Match not found");
                System.exit(0);
            }

            group.iterateExploration(false, 300_000_000, true, (states, depth) -> {
                for (Object x : states) {
                    PeekData data = (PeekData) x;
                    backtrack.put(data.newState, new Pair<>(data.oldState, data.operation));
                    int[] state = data.newState.state();
                    boolean matches = true;
                    for (int i = 0; i < state.length; i++) {
                        if (stateMatch[i] != 0 && state[i] != stateMatch[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {

                        System.out.println(Arrays.toString(state));
                        matchingStates.add(state);
                        // Figure out path
                        State current = State.of(state, group.nElements, group.mem);
                        String op = "";
                        String inverseOp = "";
                        while (backtrack.containsKey(current)) {
                            Pair<State, Integer> d = backtrack.get(current);
                            op = namesLookup[d.getValue()] + " " + op;
                            inverseOp = inverseOp + " " + inverseNamesLookup[d.getValue()];
                            current = d.getKey();
                        }
                        System.out.println("Fwd: " + op);
                        System.out.println("Inv: " + inverseOp);
                        System.out.println(Arrays.toString(state));
                    }
                }
            });
            System.out.println(group.getIteration() + " " + group.order());
        }

    }

    public static void dodotM12Simm() {

        Icosahedron revLookup = new Icosahedron();
        // With Cream (2) up and blue (4) down
        String m12_dodot = "[" +
            //"(8,9,11)(12,1,5)(7,6,4)," +
            "(1,8,5)(10,9,2)(12,4,3)," +       // R
           // "(9,1,2)(7,8,11)(10,3,6)," +   
            "(4,5,7)(2,12,3)(6,11,10)," +      // L
          //  "(11,9,8)(5,1,12)(4,6,7)," +
            "(5,8,1)(2,9,10)(3,4,12)," +       // R'
         //   "(2,1,9)(11,8,7)(6,3,10)," +
            "(7,5,4)(3,12,2)(10,11,6)]";       // L'

        String[] namesLookup = new String[] {
            "R'", "L'", "R", "L"
        };
        String[] inverseNamesLookup = new String[] {
            "R", "L", "R'", "L'"
        };

        int[][][] g = GroupExplorer.parseOperationsArr(m12_dodot);

        // For each dodecahedron vertex, find the edges
        for (int i = 0; i < 20; i++) {
            int[] vertexEdges = Dodecahedron.vertexEdges[i];
            System.out.println(Arrays.toString(vertexEdges));
        }

        for (int ci = 0; ci < g.length; ci++) {
            int[][] cycles = g[ci];
            int[][] edgeCycles = new int[cycles.length][];
            for (int i = 0; i < cycles.length; i++) {
                int[] cycle = cycles[i];
                int v = revLookup.getPosOrNegFaceFromGenerator(cycle);
                int[] edge = Dodecahedron.vertexEdges[Math.abs(v) - 1];
                if (v < 0) edge = CycleInverter.invertArray(edge);
                edgeCycles[i] = edge.clone();
                for (int e = 0; e < 3; e++) {
                    edgeCycles[i][e] = edgeCycles[i][e] + 20;
                }
            }
            // COncatenate
            int[][] combined = new int[edgeCycles.length + cycles.length][];
            for (int i = 0; i < edgeCycles.length; i++) {
                combined[i] = edgeCycles[i];
            }
            for (int i = 0; i < cycles.length; i++) {
                combined[i + edgeCycles.length] = cycles[i];
            }
            
            g[ci] = combined;
        }
        System.out.println(GroupExplorer.generatorsToString(g));


        // Now g contains the full puzzle generator

        GroupExplorer group = new GroupExplorer(
            GroupExplorer.generatorsToString(g),
            MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        // I want to iteratively explore states until I find a match to this state:
        // Zeroes should be ignored in comparison
        // 1,2,9, 10  are the fixed pcs
        int[] stateMatch =   {0,4,0,3,0,0, 0,12,0,2, 0,0,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50};

        // Single rotation
        //int[] stateMatch = new int[]{1,2,3,4,5,6,7,8,9,10,11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 26, 22, 23, 24, 21, 25, 27, 28, 29, 31, 42, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 30, 43, 45, 46, 44, 47, 48, 49, 50};

        group.setTrackPath(true);
        group.initIterativeExploration();

        /*
        group.applyOperation(0);
        System.out.println(Arrays.toString(group.copyCurrentState()));
        System.exit(0);*/

        ArrayList<int[]> matchingStates = new ArrayList<>();

        System.out.println(Arrays.toString(stateMatch));
        System.out.println(Arrays.toString(group.copyCurrentState()));

        HashMap<State, Pair<State, Integer>> backtrack = new HashMap<>();

        while (matchingStates.size() < 7) {

            group.iterateExploration(false, 300_000_000, true, (states, depth) -> {
                for (Object x : states) {
                    PeekData data = (PeekData) x;
                    backtrack.put(data.newState, new Pair<>(data.oldState, data.operation));
                    int[] state = data.newState.state();
                    boolean matches = true;
                    for (int i = 0; i < state.length; i++) {
                        if (stateMatch[i] != 0 && state[i] != stateMatch[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {

                        System.out.println(Arrays.toString(state));
                        matchingStates.add(state);
                        // Figure out path
                        State current = State.of(state, group.nElements, group.mem);
                        String op = "";
                        String inverseOp = "";
                        while (backtrack.containsKey(current)) {
                            Pair<State, Integer> d = backtrack.get(current);
                            op = namesLookup[d.getValue()] + " " + op;
                            inverseOp = inverseOp + " " + inverseNamesLookup[d.getValue()];
                            current = d.getKey();
                        }
                        System.out.println("Fwd: " + op);
                        System.out.println("Inv: " + inverseOp);
                        System.out.println(Arrays.toString(state));
                    }
                }
            });
            System.out.println(group.getIteration() + " " + group.order());
        }

    }
    
}
