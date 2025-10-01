package io.chandler.gap;

import static io.chandler.gap.Generators.exploreGroup;
import static io.chandler.gap.Generators.l2_7;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.GroupExplorer.PeekData;
import io.chandler.gap.cache.State;
import javafx.util.Pair;

public class ISCCube {

    public static void main(String[] args) {

		String turns = "[" +
        	"(1,2,3,4)(5,6,7,8)(9,10,11,12)(13,14,15,16)(17,18,19,20)(21,22,23,24)(31,32,33,34)(35,36,37,38)(39,40,41,42)(43,44,45,46)," +       // L
            "(1,12,15,23)(9,8,22,19)(5,4,18,16)(6,11,25,26)(10,3,27,28)(2,7,29,30)(47,38,48,46)(49,34,50,42)(51,36,52,53)(54,32,55,56)," +       // R
            "(4,3,2,1)(8,7,6,5)(12,11,10,9)(16,15,14,13)(20,19,18,17)(24,23,22,21)(34,33,32,31)(38,37,36,35)(42,41,40,39)(46,45,44,43)," +       // L'
            "(23,15,12,1)(19,22,8,9)(16,18,4,5)(26,25,11,6)(28,27,3,10)(30,29,7,2)(46,48,38,47)(42,50,34,49)(53,52,36,51)(56,55,32,54)," +       // R'
            "(1,3)(2,4)(6,8)(5,7)(9,11)(10,12)(13,15)(14,16)(17,19)(18,20)(21,23)(22,24)(31,33)(32,34)(35,37)(36,38)(39,41)(40,42)(45,43)(46,44)," + // L2
            "(1,15)(12,23)(8,19)(9,22)(4,16)(5,18)(11,26)(6,25)(3,28)(10,27)(2,29)(7,30)(38,46)(47,48)(34,42)(49,50)(36,53)(51,52)(32,56)(55,54)," +        // R2

        	"(31,32,54)(33,34,49)(35,36,51)(37,38,47)(41,42,50)(45,46,48)," +       // X
            "(54,32,31)(49,34,33)(51,36,35)(47,38,37)(50,42,41)(48,46,45)," +       // 

        	"(31,49,34)(32,33,54)(35,47,38)(36,37,51)(39,50,42)(43,48,46)," +       // Y
            "(34,49,31)(54,33,32)(38,47,35)(51,37,36)(42,50,39)(46,48,43)]";       // Y'

           // System.exit(0);


        // Print state for move sequence
        if (false) {
        GroupExplorer g = new GroupExplorer(turns, MemorySettings.COMPACT, new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        g.initIterativeExploration();
        g.applyOperation(2); g.applyOperation(5);
        g.applyOperation(1); g.applyOperation(5);
        g.applyOperation(2); g.applyOperation(5);
        g.applyOperation(1); g.applyOperation(5);
        System.out.println(GroupExplorer.stateToNotation(g.copyCurrentState()));
        System.exit(0);
        }

        String[] namesLookup = new String[] {
            "L'", "R'", "L", "R", "L2", "R2", "X", "X'", "Y", "Y'"
        };
        String[] inverseNamesLookup = new String[] {
            "L", "R", "L'", "R'", "L2", "R2", "X'", "X", "Y'", "Y"
        };

        int[][][] g = GroupExplorer.parseOperationsArr(turns);

        GroupExplorer group = new GroupExplorer(
            GroupExplorer.generatorsToString(g),
            MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        // I want to iteratively explore states until I find a match to this state:
        // Zeroes should be ignored in comparison
        // 1,2,9, 10  are the fixed pcs
       // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12, 13,14,15, 16,17,18};
      //int[] stateMatch =   { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56};
      //int[] stateMatch =   { 9, 2, 3, 0, 0, 6, 7, 0, 0,10,11, 0,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int[] stateMatch =   { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

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

        for (int iter = 0; iter < 20; iter++) {

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
                        String notn = GroupExplorer.stateToNotation(state);
                        if (notn.length() < 3 || notn.length() > 23) continue;
                        System.out.println(Arrays.toString(state));
                        System.out.println(notn);
                        System.out.println("Fwd: " + op);
                        System.out.println("Inv: " + inverseOp);
                        //System.out.println(Arrays.toString(state));
                    }
                }
            });
            System.out.println(group.iteration + " " + group.order());
        }

    }

    public static void mainx(String[] args) {
        //String l2_7 = "[(2,3,4,5,6,7,8),(1,2)(3,8)(4,5)(6,7)]"; 	
		String l2_7 = "[(3,6,4)(8,5,7),(1,4,7)(2,3,8),(3,4,6)(8,7,5),(1,7,4)(2,8,3)]";
       // System.out.println(GroupExplorer.renumberGeneratorNotation(l2_7)); 
        //if (true) return;
        GroupExplorer g = new GroupExplorer(l2_7, MemorySettings.COMPACT, new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        exploreGroup(g, (state, description) -> {
            int[][] cycles = GroupExplorer.stateToCycles(state);
            // Isolate inversions
            if (cycles[0].length != 2) return;
            /*if (cycles[0][0] != 1) return;
            if (cycles[0][1] != 2) return;*/

            boolean contains1 = false;
            boolean contains2 = false;
            //if (!GroupExplorer.describeState(8,state).equals("triple 3-cycles")) return;
            for (int[] cycle : cycles) {
                for (int i : cycle) {   
                    //if (i == 1) contains1 = true;
                    //if (i == 2) contains2 = true;
                }
            }
            if (contains1 || contains2) return;
            System.out.println(GroupExplorer.stateToNotation(state));
        });

    }

}
