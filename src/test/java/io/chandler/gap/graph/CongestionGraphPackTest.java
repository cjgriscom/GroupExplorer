package io.chandler.gap.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

class CongestionGraphPackTest {

    @Test
    void packsSimpleGraphAndSymmetricAdjacency() {
        CongestionGraphPack pack = CongestionGraphPack.pack(
                "[(1,2,3)(4,5),(1,4)]");

        assertEquals(5, pack.n);
        assertEquals(5, pack.edgeCount);
        for (int e = 0; e < pack.edgeCount; e++) {
            int u = pack.edgeU[e];
            int v = pack.edgeV[e];
            assertEquals(1, pack.adjacency[u * pack.n + v]);
            assertEquals(1, pack.adjacency[v * pack.n + u]);
        }
    }

    @Test
    void initialPositionsMatchJavaRandomSequence() {
        CongestionGraphPack pack = CongestionGraphPack.pack(
                "[(1,2,3)(4,5),(1,4)]");
        float[] actual = pack.initialPositions(41L);
        Random expected = new Random(41L);

        for (float value : actual) {
            assertEquals((float) expected.nextDouble(), value);
        }
    }

    @Test
    void batchRejectsMixedVertexCounts() {
        assertThrows(IllegalArgumentException.class, () ->
                CongestionGraphPack.packBatch(Arrays.asList(
                        "[(1,2,3)]",
                        "[(1,2,3,4)]"),
                        new long[]{41L}));
    }

    @Test
    void batchDimensionsMatchGraphSeedProduct() {
        CongestionGraphPack.BatchPack batch = CongestionGraphPack.packBatch(Arrays.asList(
                "[(1,2,3)(4,5),(1,4)]",
                "[(1,2,3)(4,5),(1,5)]"),
                new long[]{41L, 129L});

        assertEquals(2, batch.numGraphs);
        assertEquals(5, batch.nVertices);
        assertEquals(2 * 2 * 5 * 3, batch.initialPos.length);
        assertEquals(3, batch.graphEdgeOffsets.length);
        assertTrue(batch.graphEdgeOffsets[2] > 0);
    }

    @Test
    void javaAndNativePackAgreeWhenNativeAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(NautyNative.isAvailable());
        String line = "[(1,2,3)(4,5),(1,4)]";
        int[][][] gen = io.chandler.gap.GroupExplorer.parseOperationsArr(line);
        CongestionGraphPack j = CongestionGraphPack.packJava(gen);
        CongestionGraphPack n = CongestionGraphPack.packNative(gen);
        assertEquals(j.n, n.n);
        assertEquals(j.edgeCount, n.edgeCount);
        assertEquals(j.nodeIds, n.nodeIds);
        assertTrue(Arrays.equals(j.edgeU, n.edgeU));
        assertTrue(Arrays.equals(j.edgeV, n.edgeV));
        assertTrue(Arrays.equals(j.adjacency, n.adjacency));

        CongestionGraphPack.BatchPack batch = CongestionGraphPack.packBatch(
                Arrays.asList(line, "[(1,2,3)(4,5),(1,5)]"),
                new long[]{41L, 129L});
        float[] expect = j.initialPositions(41L);
        for (int i = 0; i < expect.length; i++) {
            assertEquals(expect[i], batch.initialPos[i]);
        }
    }
}
