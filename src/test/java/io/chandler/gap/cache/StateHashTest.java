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
        assertEquals(13, StateHash.derivePrefixLength(13));
        assertEquals(9, StateHash.derivePrefixLength(106));
    }

    @Test
    void l3_3_prefixHashIsInjective() {
        int prefixLen = StateHash.derivePrefixLength(13);
        assertTrue(StateHash.verifyInjective(L3_3, prefixLen));
    }

    @Test
    void compressModeMatchesFastestOrder() {
        GroupExplorer fastest = new GroupExplorer(L3_3, MemorySettings.FASTEST);
        fastest.exploreStates(false, (states, depth) -> {});

        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS);
        compress.exploreStates(false, (states, depth) -> {});

        assertEquals(fastest.order(), compress.order());
        assertEquals(5616, compress.order());
    }

    @Test
    void compressReconstructMatchesExploration() {
        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS);
        compress.exploreStates(false, (states, depth) -> {});

        KeyframeStateCache cache = compress.compressCache();
        int prefixLen = cache.prefixLen();
        for (int id = 0; id < cache.size(); id++) {
            int[] reconstructed = cache.reconstruct(id);
            long hash = StateHash.encode(reconstructed, prefixLen, compress.nElements);
            assertTrue(cache.containsHash(hash));
        }
    }

    @Test
    void compressPeekStatesMatchDirectApplication() {
        GroupExplorer compress = new GroupExplorer(L3_3, MemorySettings.COMPRESS);
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

}
