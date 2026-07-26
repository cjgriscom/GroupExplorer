package io.chandler.gap;

import java.util.HashSet;

import io.chandler.gap.GroupExplorer.PeekData;
import io.chandler.gap.cache.CompressStateCache;
import io.chandler.gap.cache.State.StateCompressed;

public class DeepestPathFinder {

    private static final String GENERATORS =
        "[(2,3)(4,7)(5,6)(9,12)(11,13)(14,19)(15,17)(16,21)(22,23),(1,2)(6,10)(7,8)(9,11)(12,21)(13,16)(14,20)(15,18)(23,24),(1,8)(5,16)(6,21)(11,14)(13,19)(18,24),(4,5)(6,7)(8,10)(14,15)(17,19)(18,20)]";

    private static final String[] GEN_NAMES = {"A", "B", "C", "D"};

    public static void main(String[] args) {
        GroupExplorer group = new GroupExplorer(
            GENERATORS,
            GroupExplorer.MemorySettings.COMPRESS_LONG,
            new HashSet<>(), new HashSet<>(), new HashSet<>(), true);

        group.initIterativeExploration();
        CompressStateCache cache = group.compressStateCache();

        final StateCompressed[] deepestState = {null};

        while (true) {
            // Only enable peek tracking on the iteration that discovers depth-40 states.
            int deepDepth = 24;
            boolean capture = group.getIteration() == deepDepth-1;
            int ret = group.iterateExploration(true, -1, capture, (states, depth) -> {
                if (depth == deepDepth) {
                    for (Object x : states) {
                        deepestState[0] = (StateCompressed) ((PeekData) x).newState;
                    }
                }
            });
            if (ret != -2) {
                break;
            }
        }

        if (deepestState[0] == null) {
            System.out.println("No depth-40 state found");
            return;
        }

        int stateId = deepestState[0].getStateId();
        int[] state = cache.reconstruct(stateId);
        int[] path = cache.tracePath(stateId);

        System.out.println();
        System.out.println("Deepest depth: 40");
        System.out.println("State id: " + stateId);
        System.out.println("Cycle structure: " + GroupExplorer.describeState(group.nElements, state));
        System.out.println("State: " + GroupExplorer.stateToNotation(state));
        System.out.println();

        StringBuilder fwd = new StringBuilder();
        StringBuilder inv = new StringBuilder();
        for (int g : path) {
            fwd.append(GEN_NAMES[g]).append(' ');
            inv.insert(0, GEN_NAMES[g] + " ");
        }
        System.out.println("Path length: " + path.length);
        System.out.println("Forward:  " + fwd.toString().trim());
        System.out.println("Inverse:  " + inv.toString().trim());
        System.out.println("Generators: " + java.util.Arrays.toString(path));
    }
}
