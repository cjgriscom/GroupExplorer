package io.chandler.gap.solvers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import io.chandler.gap.CycleInverter;
import io.chandler.gap.Dodecahedron;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.GroupExplorer.PeekData;
import io.chandler.gap.cache.KeyframeStateCache;
import io.chandler.gap.cache.State;
import io.chandler.gap.cache.State.StateCompressed;
import io.chandler.gap.render.Icosahedron;
import javafx.util.Pair;

public class MiscSolvers {

	public static void main(String[] args) {
        drawers_hs_2();
	}


    public static void trapentrixSimm() {
        // Red
        // Y W
        // Blue
         // Distant state: (2,5)(3,7)(6,9)(10,12)
          //  R' L' R' L R' L R L R' L R' L R L' R L R' L'

        String turns = "[" +
            "(1,2,3)(4,5,6)(7,8,9)(10,11,12)(21,20,19)(25,26,27)(29,30,31)," +   // LD
            "(2,13,14)(3,6,15)(7,16,10)(17,18,8)(24,23,22)(27,26,28)(31,32,33)," +   // RU
            "(3,2,1)(6,5,4)(9,8,7)(12,11,10)(19,20,21)(27,26,25)(31,30,29)," +   // LU
            "(14,13,2)(15,6,3)(10,16,7)(8,18,17)(22,23,24)(28,26,27)(33,32,31)]";    // RD
        // Fix 13,5,12,21,20,19,24,23,22 Swap 17,18

        String[] namesLookup = new String[] {
            "LD", "RU", "LU", "RD"
        };
        String[] inverseNamesLookup = new String[] {
            "LU", "RD", "LD", "RU"
        };

        int[][][] g = GroupExplorer.parseOperationsArr(turns);

        // Now g contains the full puzzle generator

        GroupExplorer group = new GroupExplorer(
            GroupExplorer.generatorsToString(g),
            MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        // I want to iteratively explore states until I find a match to this state:
        // Zeroes should be ignored in comparison

        // Fix 13,5,12,21,20,19,24,23,22 Swap 17,18
       // int[] stateMatch = { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24};
        int[] stateMatch =   { 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0,13,14,15,16,17,18,19,20,21,22,23,24,0,0,0,0  ,   0,0,0,0,0};

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

        while (matchingStates.size() < 100) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}

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
                        System.out.println(GroupExplorer.stateToNotation(state));
                        System.out.println(GroupExplorer.describeState(18, state));
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

    // Simple cube rotation explorer
    public static void simpleCubeRotationExplorer() {
        String turns = "[" +
            "(4,3,2,1)," +   // X
            "(6,3,5,1)," +   // Y
            "(1,2,3,4)," +   // X'
            "(1,5,3,6)]";   // Y'
            
        String[] namesLookup = new String[] {
            "X", "Y", "X'", "Y'"
        };


        GroupExplorer groudp = new GroupExplorer(turns, MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), false);
        groudp.setTrackPath(true);
        groudp.initIterativeExploration();

        HashMap<State, Pair<State, Integer>> backtrack = new HashMap<>();

        int counter[] = new int[2];

        System.out.println((++counter[0]) + ":");

        for (int i = 0; i < 10; i++) {
            groudp.iterateExploration(false, 1000000, true, (states, depth) -> {
                for (Object x : states) {
                    PeekData data = (PeekData) x;
                    backtrack.put(data.newState, new Pair<>(data.oldState, data.operation));
                    
                    // Print out path
                    State current = data.newState;
                    String op = "";
                    while (backtrack.containsKey(current)) {
                        Pair<State, Integer> d = backtrack.get(current);
                        op = namesLookup[d.getValue()] + " " + op;
                        current = d.getKey();
                    }
                    System.out.println((++counter[0]) + ": " + op);
                }
            });
        }

        System.out.println("Order: " + groudp.order());
    }

    // Simplified version with just l3_3 stickers
    public static void triskellion_l3_3() {
        
        String l3_3 = "[(3,4)(1,2)(13,5)(10,11),(7,8)(5,6)(13,9)(2,3),(11,12)(9,10)(13,1)(6,7)]";
        //int[] stateMatch = {1,2,3,4,5,6,7,8,9,10,11,12,13};
        int[] stateMatch =   {0,0,0,0,0,0,0,0,0,0,0,0,0};

        int matchNStates = 5;

        int order = 5616;
        Integer maxDepth = null;

        if (maxDepth == null || System.getProperty("debug") != null) {
            GroupExplorer groudp = new GroupExplorer(l3_3, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
            System.out.println("Order: " + groudp.order());
        }
        drawers_analysis(l3_3, order, maxDepth, stateMatch, matchNStates);

    }

    public static void drawers_hs_2() {
        // 
        String hs_2 = "[(1,91)(3,95)(4,24)(5,74)(7,94)(8,34)(9,73)(11,37)(13,20)(14,63)(19,71)(21,51)(22,86)(23,85)(27,88)(28,61)(29,52)(30,89)(31,100)(32,92)(33,58)(35,93)(36,53)(39,62)(41,46)(42,65)(44,72)(47,83)(48,64)(54,87)(55,81)(68,76)(69,97)(70,75)(79,84),"+
                       "(1,37)(2,54)(3,98)(4,96)(5,90)(6,13)(7,22)(8,73)(9,24)(10,70)(11,80)(12,65)(14,83)(15,17)(16,46)(18,69)(19,95)(20,32)(23,71)(25,33)(26,88)(27,84)(28,75)(30,59)(31,40)(34,43)(35,62)(36,56)(38,85)(41,91)(42,57)(45,97)(47,66)(48,68)(49,64)(50,78)(51,94)(52,86)(53,92)(55,72)(58,76)(60,67)(61,100)(63,87)(74,79)(81,93)(89,99),"+
                       "(1,88)(2,56)(3,7)(4,53)(5,65)(8,66)(9,33)(10,16)(11,35)(12,90)(14,97)(15,51)(17,94)(18,55)(19,100)(20,30)(21,82)(22,98)(24,25)(26,37)(27,86)(28,49)(29,39)(31,74)(32,59)(34,93)(36,54)(38,48)(40,79)(41,60)(43,81)(44,77)(45,83)(46,70)(47,73)(50,87)(52,84)(58,99)(61,95)(62,80)(63,78)(64,75)(67,91)(68,85)(69,72)(76,89)(92,96)]";

        // Invert A - 


        // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106};
        //int[] stateMatch =     {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,0,0,0,0,0,0};
        int[] stateMatch =     {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0/*, 0,0,0,0,0,0*/};
        stateMatch[6-1] = 77;
        stateMatch[77-1] = 6;
        stateMatch[13-1] = 44;
        stateMatch[44-1] = 13;
        stateMatch[20-1] = 72;
        stateMatch[72-1] = 20;
        stateMatch[54-1] = 87;
        stateMatch[87-1] = 54;

        int matchNStates = 3;

        Integer order = 88704000;
        Integer maxDepth = 58;

        if (maxDepth == null || order == null || System.getProperty("debug") != null) {
            GroupExplorer groudp = new GroupExplorer(hs_2, MemorySettings.COMPRESS);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
            System.out.println("Order: " + groudp.order());
        }

        drawers_analysis(hs_2, order, maxDepth, stateMatch, matchNStates, MemorySettings.COMPRESS);
    }

    public static void drawers_j2_2() {
        // Solution3 12x9
        // Any pair of drawers can be inverted - 4x times the order of the original j2:2
        String j2_2 = "[(101,102)(1,47)(3,7)(4,31)(5,9)(6,73)(8,54)(11,15)(13,100)(14,51)(16,55)(18,29)(19,34)(20,98)(22,95)(23,41)(24,88)(25,85)(26,56)(27,92)(28,78)(33,50)(35,67)(36,42)(37,63)(38,62)(39,99)(40,70)(43,83)(44,97)(45,96)(46,94)(48,53)(49,69)(52,75)(57,72)(58,66)(59,61)(60,86)(65,77)(71,90)(76,82)(81,91)(84,89)," +
                       "(103,104)(1,45)(2,8)(3,16)(4,30)(5,68)(6,34)(7,82)(9,33)(10,11)(12,96)(13,15)(14,41)(17,18)(19,54)(20,69)(21,76)(22,31)(23,90)(25,64)(26,75)(28,77)(29,52)(32,39)(35,78)(36,99)(37,40)(42,53)(43,95)(44,86)(46,91)(47,57)(49,89)(50,62)(58,98)(59,66)(60,61)(63,85)(67,87)(71,93)(80,81)(84,97)(88,100)(92,94),"+
                       "(105,106)(1,88)(2,52)(3,90)(4,62)(5,64)(6,35)(7,66)(8,29)(12,36)(13,39)(14,47)(15,32)(16,23)(17,49)(18,89)(19,97)(21,91)(22,95)(24,51)(25,68)(27,65)(28,44)(30,50)(31,43)(34,78)(37,87)(38,83)(40,67)(41,57)(45,100)(46,76)(48,79)(54,84)(55,72)(56,74)(58,71)(59,82)(60,92)(61,94)(70,73)(77,86)(93,98)(96,99)]";

        // A C A C A C - Invert A and C
        // A B A B A B A B A B A B A B - Invert A and B

        // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106};
        int[] stateMatch =     {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,0,0,0,0,0,0};

        int matchNStates = 3;

        Integer order = 4838400;
        Integer maxDepth = 84;

        if (maxDepth == null || order == null || System.getProperty("debug") != null) {
            GroupExplorer groudp = new GroupExplorer(j2_2, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
            System.out.println("Order: " + groudp.order());
        }

        drawers_analysis(j2_2, order, maxDepth, stateMatch, matchNStates);
    }

    public static void drawers_j2_2_symm() {
        String j2_2 = "[(101,102)(1,13)(2,3)(4,5)(6,7)(8,9)(10,11)(12,24)(14,27)(15,28)(16,29)(17,30)(18,31)(19,32)(20,33)(21,34)(22,23)(25,37)(26,38)(35,47)(36,48)(49,50)(45,46)(41,42)(39,40)(43,57)(44,58)(51,52)(55,56)(59,60)(61,62)(53,65)(54,66)(63,75)(64,76)(67,80)(68,81)(69,82)(70,83)(71,84)(72,85)(73,86)(74,87)(78,79)(77,89)(88,100)(90,91)(92,93)(94,95)(96,97)(98,99),"
                     + "(103,104)(1,14)(2,13)(3,16)(4,15)(5,18)(6,17)(7,20)(8,19)(9,22)(10,21)(23,24)(29,43)(30,42)(33,45)(34,44)(38,39)(40,41)(46,61)(47,60)(48,63)(49,62)(50,64)(52,53)(54,55)(56,67)(57,68)(69,70)(59,72)(58,71)(73,74)(75,76)(65,66)(89,90)(79,80)(81,82)(83,84)(85,86)(87,88)(99,100)(97,98)(95,96)(93,94)(91,92),"
                     + "(103,104)(1,2)(3,4)(5,6)(7,8)(9,10)(11,12)(13,14)(15,16)(17,18)(19,20)(21,22)(25,26)(27,28)(31,32)(35,36)(29,42)(30,43)(33,44)(34,45)(46,47)(48,49)(60,61)(62,63)(37,51)(38,53)(39,52)(40,55)(41,54)(56,68)(57,67)(58,72)(59,71)(77,78)(79,92)(80,91)(81,94)(82,93)(83,96)(84,95)(85,98)(86,97)(87,100)(88,99)]";

        // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106};
        int[] stateMatch =     {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,0,0,0,0,0,0};

        int matchNStates = 3;

        Integer order = 2419200;
        Integer maxDepth = 52;

        if (maxDepth == null || order == null || System.getProperty("debug") != null) {
            GroupExplorer groudp = new GroupExplorer(j2_2, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
            System.out.println("Order: " + groudp.order());
        }

        drawers_analysis(j2_2, order, maxDepth, stateMatch, matchNStates);
    }


    public static void drawers_sp6_2() {
        // All drawers move independently - 8x times the order of the original sp(6,2)
        String sp6_2 = "[(3,4)(7,15)(8,9)(11,12)(19,23)(20,21)(29,30),(1,8)(2,13)(3,23)(4,14)(5,25)(6,10)(11,20)(12,16)(15,27)(17,21)(18,19)(22,26)(31,32),(2,5)(3,21)(4,17)(7,12)(8,22)(9,23)(10,27)(11,28)(15,16)(18,25)(19,24)(20,26)(33,34)]";

        // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32};
        int[] stateMatch =     {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,0,0,0,0,0,0};

        int matchNStates = 7;

        Integer order = 11612160;
        Integer maxDepth = 47;

        if (maxDepth == null || order == null || System.getProperty("debug") != null) {
            GroupExplorer groudp = new GroupExplorer(sp6_2, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
            System.out.println("Order: " + groudp.order());
        }

        drawers_analysis(sp6_2, order, maxDepth, stateMatch, matchNStates);
    }

    public static void drawers_m22_2() {
        // M22_2 (solution 15) with drawer stops added
        // d3 - hexagon tiling
        // This puzzle has 4x the order of the original M22:2 - drawer b can be moved independently, and a/c can be inverted as a pair
        String m22_2 = "[(5,12)(6,11)(7,15)(9,17)(10,18)(13,14)(16,19)(23,24),(1,10)(2,3)(4,6)(5,19)(7,14)(8,13)(9,16)(15,21)(25,26),(2,16)(4,14)(5,22)(6,20)(7,18)(9,10)(15,17)(27,28)]";

        // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32};
        int[] stateMatch =     {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,0,0,0,0,0,0};

        int matchNStates = 3;

        int order = 3548160;
        Integer maxDepth = 33;

        if (maxDepth == null || System.getProperty("debug") != null) {
            GroupExplorer groudp = new GroupExplorer(m22_2, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
            System.out.println("Order: " + groudp.order());
        }

        drawers_analysis(m22_2, order, maxDepth, stateMatch, matchNStates);
    }

    public static void drawers_analysis(String generator, int order, Integer maxDepth, int[] stateMatch, int matchNStates) {
        drawers_analysis(generator, order, maxDepth, stateMatch, matchNStates, MemorySettings.COMPACT);
    }

    public static void drawers_analysis(String generator, int order, Integer maxDepth, int[] stateMatch, int matchNStates, MemorySettings mem) {

        System.out.println("Order: " + order);
        System.out.println("Max depth: " + maxDepth);

        String[] namesLookup = new String[] {
            "A", "B", "C"
        };

        // 2-cycles; no inverse moves needed


        GroupExplorer group = new GroupExplorer(generator,
            mem,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);

        group.setTrackPath(true);
        group.initIterativeExploration();

        KeyframeStateCache compressCache = group.compressCache();
        final boolean compress = compressCache != null;

        ArrayList<int[]> matchingStates = new ArrayList<>();

        System.out.println(Arrays.toString(stateMatch));
        System.out.println(Arrays.toString(group.copyCurrentState()));

        // In COMPRESS mode the parent/generator chain lives in the cache, so the
        // per-state backtracking map (which dominated heap usage) is not needed.
        HashMap<State, Pair<State, Integer>> backtrack = compress ? null : new HashMap<>();

        while (matchingStates.size() < matchNStates) {

            group.iterateExploration(false, order+1, true, (states, depth) -> {
                for (Object x : states) {
                    PeekData data = (PeekData) x;
                    if (!compress) {
                        backtrack.put(data.newState, new Pair<>(data.oldState, data.operation));
                    }
                    int[] state = data.newState.state();
                    boolean matches = true;
                    for (int i = 0; i < state.length; i++) {
                        if (stateMatch[i] != 0 && state[i] != stateMatch[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches || depth >= maxDepth-2) {

                        System.out.println(Arrays.toString(state));
                        matchingStates.add(state);
                        System.out.println(GroupExplorer.describeState(13, state));
                        System.out.println(GroupExplorer.stateToNotation(state));

                        // Figure out path
                        String op = "";
                        String inverseOp = "";
                        if (compress) {
                            // Walk the cache's stored parent/generator chain (root -> state).
                            int[] path = compressCache.tracePath(((StateCompressed) data.newState).getStateId());
                            StringBuilder fwd = new StringBuilder();
                            StringBuilder inv = new StringBuilder();
                            for (int g : path) {
                                fwd.append(namesLookup[g]).append(" ");
                                inv.insert(0, namesLookup[g] + " ");
                            }
                            op = fwd.toString();
                            inverseOp = inv.toString();
                        } else {
                            State current = State.of(state, group.nElements, group.mem);
                            while (backtrack.containsKey(current)) {
                                Pair<State, Integer> d = backtrack.get(current);
                                op = namesLookup[d.getValue()] + " " + op;
                                inverseOp = inverseOp + " " + namesLookup[d.getValue()];
                                current = d.getKey();
                            }
                        }
                        System.out.println("Fwd: " + op);
                        System.out.println("Inv: " + inverseOp);
                        System.out.println(Arrays.toString(state));
                    }
                }
            });
            System.out.println(group.getIteration() + " " + group.order());
            if (maxDepth != null && group.getIteration() > maxDepth) {
                System.out.println("Max depth reached");
                return;
            }
        }


    }
	
    public static void crammedCubeSimm() {
        // Red
        // Y W
        // Blue
        { // Distant state: (2,5)(3,7)(6,9)(10,12)
          //  R' L' R' L R' L R L R' L R' L R L' R L R' L'

        String turns = "[" +
            "(3,7,6)(2,10,9)(8,11,4)," +   // L
            "(7,12,11)(6,5,4)(3,2,1)," +      // R
            "(3,6,7)(2,9,10)(8,4,11)," +   // L'
            "(7,11,12)(6,4,5)(3,1,2)]";       // R'

            GroupExplorer groudp = new GroupExplorer(turns, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
                if (depth == 18) {
                    System.out.println(GroupExplorer.stateToNotation(states.get(0)));
                }
            });
        }
        String m12_dodot = "[" +
        "(3,7,6)(2,10,9)(8,11,4)(16,17,18)," +       // L
            "(7,12,11)(6,5,4)(3,2,1)(13,14,15)," +      // R
            "(3,6,7)(2,9,10)(8,4,11)(18,17,16)," +   // L'
            "(7,11,12)(6,4,5)(3,1,2)(15,14,13)]";       // R'

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
       // int[] stateMatch = {1,2,3,4,5,6,7,8,9,10,11,12, 13,14,15, 16,17,18};
        int[] stateMatch =   {0,0,0,0,0,0,7,0,0,10,0,0, 0,0,0,0,0,0};

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

        while (matchingStates.size() < 10) {

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
                        System.out.println(GroupExplorer.stateToNotation(state));
                        System.out.println(GroupExplorer.describeState(18, state));
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
        /*{
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
        }*/


        String gen = "[" +
        "(7,12,10)(5,6,4)(2,3,1)(11,9,8)(15,14,13)," +   // A'
        "(12,5,2)(1,10,9)(7,8,6)(18,17,16),"+       // B'
        "(10,12,7)(4,6,5)(1,3,2)(8,9,11)(13,14,15)," +   // A
        "(2,5,12)(9,10,1)(6,8,7)(16,17,18)]";      // B
        
        String genSimpl = "[" +
        "(7,12,10)(5,6,4)(2,3,1)(11,9,8)," +   // A'
        "(12,5,2)(1,10,9)(7,8,6),"+       // B'
        "(10,12,7)(4,6,5)(1,3,2)(8,9,11)," +   // A
        "(2,5,12)(9,10,1)(6,8,7)]";      // B

            GroupExplorer groudp = new GroupExplorer(genSimpl, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
                if (depth==19) {
                    for (int[] x : states) {
                        if (!GroupExplorer.describeState(12, x).endsWith("2-cycles")) continue;
                        String colors = GroupExplorer.stateToNotation(x).replace("11", "GO").replace("12", "BR").replace("10", "WR").replace("9", "WG").replace("8", "WB").replace("7", "WO").replace("6", "BO").replace("5", "YB").replace("4", "YO").replace("3", "YG").replace("2", "YR").replace("1", "GR");
                        System.out.println(GroupExplorer.describeState(12, x)+"\t\t\t"+colors +"\t\t\t"+Arrays.toString(x));
                    }
                }
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
        int[] stateMatch =   { 0,0,0,4,5,6,0,0,9,0,0,0, 13,14,15, 16,17,18};
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

        while (matchingStates.size() < 7) {
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

    public static void whiskerCubeSimm() {
        {
        String turns = "[" +
            "(1,10,7)(2,5,4)(3,12,9)(6,13,8)(17,16,15)," +   // A'
            "(1,4,6)(3,14,7)(5,11,13)(8,12,10)(20,19,18),"+       // B'
            "(1,7,10)(2,4,5)(3,9,12)(6,8,13)(15,16,17)," +   // A
            "(1,6,4)(3,7,14)(5,13,11)(8,10,12)(18,19,20)]";      // B

            GroupExplorer groudp = new GroupExplorer(turns, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
                if (depth == 15) {
                    for (int[] x : states) {
                        System.out.println(GroupExplorer.stateToNotation(x));
                    }
                }
            });

            System.out.println("Order: " + groudp.order());
        }


        
        String gen = "[" +
        "(1,10,7)(2,5,4)(3,12,9)(6,13,8)(17,16,15)," +   // A'
        "(1,4,6)(3,14,7)(5,11,13)(8,12,10)(20,19,18),"+       // B'
        "(1,7,10)(2,4,5)(3,9,12)(6,8,13)(15,16,17)," +   // A
        "(1,6,4)(3,7,14)(5,13,11)(8,10,12)(18,19,20)]";      // B

            GroupExplorer groudp = new GroupExplorer(gen, MemorySettings.COMPACT);
            groudp.exploreStates(false, (states, depth) -> {
                System.out.println("Depth " + depth + ": " + states.size() + " states");
            });
           // System.exit(0);

        String[] namesLookup = new String[] {
            "X'", "Y'", "X", "Y"
        };
        String[] inverseNamesLookup = new String[] {
            "X", "Y", "X'", "Y'"
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
        int[] stateMatch =   { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14, 16,17,15, 19,20,18};
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
        //   11  8
        //
        //     9
        // 10 *2* 1
        //   3   12
        //  6 *4*  5
        //     7

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
        //int[] stateMatch =  { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12, 
        //int[] stateMatch =  { 0,10, 3,12, 0, 4, 0, 8, 0, 6, 0, 2,   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
        int[] stateMatch =  { 0, 0, 3,12, 0, 4, 0, 8, 0, 0, 0, 2,   13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50};

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
