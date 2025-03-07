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

public class PSL27Cube {

    public static void main(String[] args) {
		String turns = "[" +
        	"(3,4,6)(8,7,5)," +       // L
            "(1,7,4)(2,8,3)," +      // R
            "(3,6,4)(8,5,7)," +   // L'
            "(1,4,7)(2,3,8)]";       // R'

            GroupExplorer groudp = new GroupExplorer(turns, MemorySettings.COMPACT);
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

        int[][][] g = GroupExplorer.parseOperationsArr(turns);

        GroupExplorer group = new GroupExplorer(
            GroupExplorer.generatorsToString(g),
            MemorySettings.COMPACT,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        // I want to iteratively explore states until I find a match to this state:
        // Zeroes should be ignored in comparison
        // 1,2,9, 10  are the fixed pcs
       // int[] stateMatch =   {1,2,3,4,5,6,7,8,9,10,11,12, 13,14,15, 16,17,18};
        int[] stateMatch =   {7,0,0,0,0,0,1,0};

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

        for (int iter = 0; iter < 10; iter++) {

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
                    if (matches && GroupExplorer.describeState(8, state).equals("quadruple 2-cycles")) {

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
