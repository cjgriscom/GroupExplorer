package io.chandler.gap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.SamplePuzzleDepthDistribution.PuzzleDef;
import io.chandler.gap.cache.KeyframeStateCache;
import io.chandler.gap.cache.WeylE8StateCache;
import io.chandler.gap.weyl.WeylE8Antipodes;
import io.chandler.gap.weyl.WeylE8Quotient;

class WeylE8StateCacheTest {

    private static String weylE8Generator;

    @BeforeAll
    static void loadGenerator() throws IOException {
        Path puzzlesPath = Paths.get("/home/cjgriscom/Programming/GridExplorer/src/samplePuzzles.ts");
        List<PuzzleDef> puzzles = SamplePuzzleDepthDistribution.parseSamplePuzzles(puzzlesPath);
        weylE8Generator = puzzles.stream()
            .filter(p -> "weyl_e8".equals(p.id))
            .findFirst()
            .orElseThrow()
            .generator;
    }

    @Test
    void antipodesAreConsecutivePairs() {
        for (int label = 1; label <= WeylE8Antipodes.NUM_POINTS; label++) {
            assertEquals(label, WeylE8Antipodes.partner(WeylE8Antipodes.partner(label)));
            assertTrue(WeylE8Antipodes.partner(label) == label + 1 || WeylE8Antipodes.partner(label) == label - 1);
        }
    }

    @Test
    void centralInvolutionMapsToQuotientIdentity() {
        int[] perm = new int[WeylE8Antipodes.NUM_POINTS];
        for (int i = 0; i < perm.length; i++) {
            perm[i] = WeylE8Antipodes.partner(i + 1);
        }
        byte[] quotient = new byte[WeylE8Antipodes.NUM_PAIRS];
        WeylE8Quotient.encode(perm, quotient);
        for (int k = 0; k < quotient.length; k++) {
            assertEquals(k + 1, quotient[k] & 0xff);
        }
    }

    @Test
    void weylE8CompressUsesCustomCache() {
        GroupExplorer compress = new GroupExplorer(weylE8Generator, MemorySettings.compressWeylE8(2));
        assertTrue(compress.compressStateCache() instanceof WeylE8StateCache);
        assertEquals(WeylE8Antipodes.NUM_POINTS, compress.compressStateCache().nElements());
    }

    @Test
    void shallowBfsMatchesFastestMode() {
        GroupExplorer fastest = new GroupExplorer(weylE8Generator, MemorySettings.FASTEST);
        GroupExplorer compress = new GroupExplorer(
            weylE8Generator,
            MemorySettings.compressWeylE8(2),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            false);

        fastest.initIterativeExploration();
        compress.initIterativeExploration();

        for (int layer = 0; layer < 3; layer++) {
            fastest.iterateExploration(false, -1, null);
            compress.iterateExploration(false, -1, null);
            assertEquals(
                fastest.visitedStateCount(),
                compress.visitedStateCount(),
                "visited count at layer " + (layer + 1));
        }

        assertTrue(compress.compressStateCache().size() > 1);
    }

    @Test
    void tracePathReplaysState() {
        GroupExplorer compress = new GroupExplorer(
            weylE8Generator,
            MemorySettings.compressWeylE8(2),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            false);
        compress.initIterativeExploration();
        compress.iterateExploration(false, 10_000, null);

        WeylE8StateCache cache = (WeylE8StateCache) compress.compressStateCache();
        int[] identity = new int[WeylE8Antipodes.NUM_POINTS];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i + 1;
        }

        for (int id = 0; id < Math.min(cache.size(), 64); id++) {
            int[] path = cache.tracePath(id);
            int[] replay = identity.clone();
            for (int g : path) {
                replay = GroupExplorer.applyOperation(replay, compress.parsedOperations.get(g));
            }
            assertArrayEquals(cache.reconstruct(id), replay, "state id " + id);
        }
    }

    @Test
    void stripFrontierPermsMatchesFastestToDepth30() {
        KeyframeStateCache.STRIP_FRONTIER_PERMS = true;
        try {
            GroupExplorer fastest = new GroupExplorer(weylE8Generator, MemorySettings.FASTEST);
            fastest.initIterativeExploration();
            for (int d = 0; d < 30; d++) {
                fastest.iterateExploration(false, -1, null);
            }

            GroupExplorer compress = new GroupExplorer(
                weylE8Generator,
                MemorySettings.compressWeylE8(2),
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>(),
                false);
            compress.initIterativeExploration();
            for (int d = 0; d < 30; d++) {
                compress.iterateExploration(false, -1, null);
            }

            assertEquals(fastest.visitedStateCount(), compress.visitedStateCount());
        } finally {
            KeyframeStateCache.STRIP_FRONTIER_PERMS = false;
        }
    }
}
