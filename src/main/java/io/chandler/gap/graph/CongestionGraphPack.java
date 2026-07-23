package io.chandler.gap.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import networkx.SpringLayout;
import networkx.SpringLayout.SpringLayoutState;

/**
 * Packs a generator line into CSR edge lists and adjacency suitable for the CUDA backend.
 * Vertex index order matches {@link SpringLayout#getNodeIds} for Java parity.
 */
public final class CongestionGraphPack {

    public final int n;
    public final int edgeCount;
    public final int[] edgeU;
    public final int[] edgeV;
    public final byte[] adjacency;
    public final List<Integer> nodeIds;

    private CongestionGraphPack(int n, int edgeCount, int[] edgeU, int[] edgeV, byte[] adjacency,
            List<Integer> nodeIds) {
        this.n = n;
        this.edgeCount = edgeCount;
        this.edgeU = edgeU;
        this.edgeV = edgeV;
        this.adjacency = adjacency;
        this.nodeIds = nodeIds;
    }

    public static CongestionGraphPack pack(String line) {
        Graph<Integer, DefaultEdge> graph = CongestionEvaluator.buildGraphFromLine(line);
        networkx.Graph nxGraph = CongestionEvaluator.buildNetworkxGraph(graph);
        SpringLayoutState state = SpringLayout.createState(nxGraph, 3, 0L, 1);
        List<Integer> nodeIds = SpringLayout.getNodeIds(state);
        int n = nodeIds.size();

        Map<Integer, Integer> idToIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            idToIndex.put(nodeIds.get(i), i);
        }

        int edgeCount = graph.edgeSet().size();
        int[] edgeU = new int[edgeCount];
        int[] edgeV = new int[edgeCount];
        byte[] adjacency = new byte[n * n];
        int e = 0;
        for (DefaultEdge edge : graph.edgeSet()) {
            int ui = idToIndex.get(graph.getEdgeSource(edge));
            int vi = idToIndex.get(graph.getEdgeTarget(edge));
            edgeU[e] = ui;
            edgeV[e] = vi;
            adjacency[ui * n + vi] = 1;
            adjacency[vi * n + ui] = 1;
            e++;
        }
        return new CongestionGraphPack(n, edgeCount, edgeU, edgeV, adjacency, nodeIds);
    }

    /** Initial 3D positions for one seed: Random(seed).nextDouble() per node per dim. */
    public float[] initialPositions(long seed) {
        float[] pos = new float[n * 3];
        Random random = new Random(seed);
        for (int i = 0; i < n; i++) {
            pos[i * 3 + 0] = (float) random.nextDouble();
            pos[i * 3 + 1] = (float) random.nextDouble();
            pos[i * 3 + 2] = (float) random.nextDouble();
        }
        return pos;
    }

    public static float[] flattenRotations(double[][][] rotations) {
        float[] flat = new float[rotations.length * 9];
        for (int r = 0; r < rotations.length; r++) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    flat[r * 9 + row * 3 + col] = (float) rotations[r][row][col];
                }
            }
        }
        return flat;
    }

    public static class BatchPack {
        public final int numGraphs;
        public final int nVertices;
        public final int[] graphEdgeOffsets;
        public final int[] graphEdgeCounts;
        public final int[] edgeU;
        public final int[] edgeV;
        public final byte[] adjacency;
        public final float[] initialPos;
        public final List<CongestionGraphPack> graphs;

        BatchPack(List<CongestionGraphPack> graphs, long[] seeds) {
            this.graphs = graphs;
            numGraphs = graphs.size();
            if (numGraphs == 0) {
                nVertices = 0;
                graphEdgeOffsets = new int[]{0};
                graphEdgeCounts = new int[0];
                edgeU = new int[0];
                edgeV = new int[0];
                adjacency = new byte[0];
                initialPos = new float[0];
                return;
            }
            nVertices = graphs.get(0).n;
            graphEdgeOffsets = new int[numGraphs + 1];
            graphEdgeCounts = new int[numGraphs];
            int totalEdges = 0;
            for (int g = 0; g < numGraphs; g++) {
                CongestionGraphPack gp = graphs.get(g);
                if (gp.n != nVertices) {
                    throw new IllegalArgumentException(
                            "mixed vertex counts in batch: " + nVertices + " vs " + gp.n);
                }
                graphEdgeOffsets[g] = totalEdges;
                graphEdgeCounts[g] = gp.edgeCount;
                totalEdges += gp.edgeCount;
            }
            graphEdgeOffsets[numGraphs] = totalEdges;
            edgeU = new int[totalEdges];
            edgeV = new int[totalEdges];
            adjacency = new byte[numGraphs * nVertices * nVertices];
            int eo = 0;
            for (int g = 0; g < numGraphs; g++) {
                CongestionGraphPack gp = graphs.get(g);
                System.arraycopy(gp.edgeU, 0, edgeU, eo, gp.edgeCount);
                System.arraycopy(gp.edgeV, 0, edgeV, eo, gp.edgeCount);
                System.arraycopy(gp.adjacency, 0, adjacency, g * nVertices * nVertices, gp.adjacency.length);
                eo += gp.edgeCount;
            }
            initialPos = new float[numGraphs * seeds.length * nVertices * 3];
            for (int g = 0; g < numGraphs; g++) {
                for (int s = 0; s < seeds.length; s++) {
                    float[] pos = graphs.get(g).initialPositions(seeds[s]);
                    int base = ((g * seeds.length + s) * nVertices) * 3;
                    System.arraycopy(pos, 0, initialPos, base, pos.length);
                }
            }
        }
    }

    public static BatchPack packBatch(List<String> lines, long[] seeds) {
        List<CongestionGraphPack> graphs = new ArrayList<>(lines.size());
        for (String line : lines) {
            graphs.add(pack(line));
        }
        return new BatchPack(graphs, seeds);
    }
}
