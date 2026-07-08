package io.chandler.gap.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;

class StateHashTest {

    static final String L3_3 = "[(3,4)(1,2)(13,5)(10,11),(7,8)(5,6)(13,9)(2,3),(11,12)(9,10)(13,1)(6,7)]";

    @Test
    void derivePrefixLength_fromLogFormula() {
        assertEquals(13, StateHash.derivePrefixLength(13, 64));
        assertEquals(9, StateHash.derivePrefixLength(106, 64));
    }

    @Test
    void l3_3_prefixHashIsInjective() {
        int prefixLen = StateHash.derivePrefixLength(13, 64);
        assertTrue(StateHash.verifyInjective(L3_3, prefixLen));
    }

    @Test
    void compressModeMatchesFastestOrder() {
        GroupExplorer fastest = new GroupExplorer(L3_3, MemorySettings.FASTEST);
        fastest.exploreStates(false, (states, depth) -> {});

        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS_LONG);
        compress.exploreStates(false, (states, depth) -> {});

        assertEquals(fastest.order(), compress.order());
        assertEquals(5616, compress.order());
    }

    @Test
    void compressReconstructMatchesExploration() {
        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS_LONG);
        compress.exploreStates(false, (states, depth) -> {});

        KeyframeStateCache cache = compress.compressCache();
        for (int id = 0; id < cache.size(); id++) {
            int[] reconstructed = cache.reconstruct(id);
            assertTrue(cache.containsHash(cache.hash(reconstructed)));
        }
    }

    @Test
    void compressPeekStatesMatchDirectApplication() {
        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS_LONG);
        compress.setTrackPath(true);
        compress.initIterativeExploration();
        compress.iterateExploration(false, -1, true, (list, depth) -> {
            for (Object item : list) {
                GroupExplorer.PeekData data = (GroupExplorer.PeekData) item;
                int[] fromParent = data.oldState.state();
                int[] expected = GroupExplorer.applyOperation(
                    fromParent, compress.parsedOperations.get(data.operation));
                assertArrayEquals(expected, data.newState.state());
            }
        });
    }

    @Test
    void tracePathReproducesEveryState() {
        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS_LONG);
        compress.exploreStates(false, (states, depth) -> {});

        KeyframeStateCache cache = compress.compressCache();
        int n = compress.nElements;

        int[] identity = new int[n];
        for (int i = 0; i < n; i++) identity[i] = i + 1;

        for (int id = 0; id < cache.size(); id++) {
            int[] path = cache.tracePath(id);
            // Replay the generator sequence from the identity.
            int[] replay = identity.clone();
            for (int g : path) {
                replay = GroupExplorer.applyOperation(replay, compress.parsedOperations.get(g));
            }
            assertArrayEquals(cache.reconstruct(id), replay,
                "tracePath replay must equal the cached state for id " + id);
        }
    }

    @Test
    void compressDoesNotAccumulateStateMap() {
        // The visited set must live entirely in the hash cache; stateMap should stay
        // empty so memory does not scale with the number of explored states.
        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS_LONG);
        compress.exploreStates(false, (states, depth) -> {});

        assertEquals(5616, compress.order());
        assertEquals(0, compress.visitedSetSize(),
            "COMPRESS mode should not populate the legacy stateMap");
    }

}
