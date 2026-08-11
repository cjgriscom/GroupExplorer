package io.chandler.gap.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.chandler.gap.GroupExplorer;

/**
 * Packs a generator line into CSR edge lists and adjacency suitable for the CUDA backend.
 * <p>
 * Vertex index order is <b>sorted distinct labels</b> (stable across JVM / native).
 * When {@link NautyNative} is loaded, packing runs inside {@code libnauty_jni.so}.
 */
public final class CongestionGraphPack {

    static {
        // Resolve natives from libnauty_jni.so (loaded by NautyNative).
        NautyNative.isAvailable();
    }

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
        int[][][] gen = GroupExplorer.parseOperationsArr(line);
        if (NautyNative.isAvailable()) {
            return packNative(gen);
        }
        return packJava(gen);
    }

    static CongestionGraphPack packJava(int[][][] combinedGen) {
        Set<Integer> labelSet = new LinkedHashSet<>();
        List<int[]> undirected = new ArrayList<>();
        for (int[][] cycleSet : combinedGen) {
            for (int[] polygon : cycleSet) {
                for (int v : polygon) labelSet.add(v);
                for (int i = 0; i < polygon.length; i++) {
                    int a = polygon[i];
                    int b = polygon[(i + 1) % polygon.length];
                    if (a == b) continue;
                    int u = Math.min(a, b);
                    int v = Math.max(a, b);
                    undirected.add(new int[]{u, v});
                }
            }
        }
        List<Integer> nodeIds = new ArrayList<>(labelSet);
        Collections.sort(nodeIds);
        int n = nodeIds.size();
        int[] remap = null;
        int minLab = n == 0 ? 0 : nodeIds.get(0);
        int maxLab = n == 0 ? -1 : nodeIds.get(n - 1);
        if (n > 0) {
            remap = new int[maxLab - minLab + 1];
            Arrays.fill(remap, -1);
            for (int i = 0; i < n; i++) remap[nodeIds.get(i) - minLab] = i;
        }

        // Dedupe edges, sorted by (u,v) in remapped indices.
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> edges = new ArrayList<>();
        for (int[] e : undirected) {
            int u = remap[e[0] - minLab];
            int v = remap[e[1] - minLab];
            if (u > v) {
                int t = u;
                u = v;
                v = t;
            }
            long key = (((long) u) << 32) | (v & 0xffffffffL);
            if (seen.add(key)) edges.add(new int[]{u, v});
        }
        edges.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));

        int edgeCount = edges.size();
        int[] edgeU = new int[edgeCount];
        int[] edgeV = new int[edgeCount];
        byte[] adjacency = new byte[n * n];
        for (int i = 0; i < edgeCount; i++) {
            int u = edges.get(i)[0];
            int v = edges.get(i)[1];
            edgeU[i] = u;
            edgeV[i] = v;
            adjacency[u * n + v] = 1;
            adjacency[v * n + u] = 1;
        }
        return new CongestionGraphPack(n, edgeCount, edgeU, edgeV, adjacency, nodeIds);
    }

    static CongestionGraphPack packNative(int[][][] combinedGen) {
        FlatGen flat = flatten(combinedGen);
        Object[] parts = nativePackOne(flat.points, flat.cycleLens);
        int[] nodeIdArr = (int[]) parts[0];
        int[] edgeU = (int[]) parts[1];
        int[] edgeV = (int[]) parts[2];
        byte[] adjacency = (byte[]) parts[3];
        List<Integer> nodeIds = new ArrayList<>(nodeIdArr.length);
        for (int id : nodeIdArr) nodeIds.add(id);
        return new CongestionGraphPack(nodeIdArr.length, edgeU.length, edgeU, edgeV, adjacency, nodeIds);
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

        /** Assemble from native packBatch result arrays. */
        BatchPack(int nVertices, int[] graphEdgeOffsets, int[] graphEdgeCounts,
                  int[] edgeU, int[] edgeV, byte[] adjacency, float[] initialPos,
                  List<CongestionGraphPack> graphs) {
            this.nVertices = nVertices;
            this.graphEdgeOffsets = graphEdgeOffsets;
            this.graphEdgeCounts = graphEdgeCounts;
            this.edgeU = edgeU;
            this.edgeV = edgeV;
            this.adjacency = adjacency;
            this.initialPos = initialPos;
            this.graphs = graphs;
            this.numGraphs = graphs.size();
        }
    }

    public static BatchPack packBatch(List<String> lines, long[] seeds) {
        if (lines.isEmpty()) {
            return new BatchPack(Collections.emptyList(), seeds);
        }
        if (NautyNative.isAvailable()) {
            return packBatchNative(lines, seeds);
        }
        List<CongestionGraphPack> graphs = new ArrayList<>(lines.size());
        for (String line : lines) {
            graphs.add(packJava(GroupExplorer.parseOperationsArr(line)));
        }
        return new BatchPack(graphs, seeds);
    }

    private static BatchPack packBatchNative(List<String> lines, long[] seeds) {
        int numGraphs = lines.size();
        int totalPoints = 0;
        int totalCycles = 0;
        int[] cyclesPerGraph = new int[numGraphs];
        List<FlatGen> flats = new ArrayList<>(numGraphs);
        for (int g = 0; g < numGraphs; g++) {
            FlatGen flat = flatten(GroupExplorer.parseOperationsArr(lines.get(g)));
            flats.add(flat);
            cyclesPerGraph[g] = flat.cycleLens.length;
            totalPoints += flat.points.length;
            totalCycles += flat.cycleLens.length;
        }
        int[] points = new int[totalPoints];
        int[] cycleLens = new int[totalCycles];
        int po = 0, co = 0;
        for (FlatGen flat : flats) {
            System.arraycopy(flat.points, 0, points, po, flat.points.length);
            System.arraycopy(flat.cycleLens, 0, cycleLens, co, flat.cycleLens.length);
            po += flat.points.length;
            co += flat.cycleLens.length;
        }

        Object[] parts = nativePackBatch(points, cycleLens, cyclesPerGraph, seeds);
        int nVertices = ((int[]) parts[0])[0];
        int[] graphEdgeOffsets = (int[]) parts[1];
        int[] graphEdgeCounts = (int[]) parts[2];
        int[] edgeU = (int[]) parts[3];
        int[] edgeV = (int[]) parts[4];
        byte[] adjacency = (byte[]) parts[5];
        float[] initialPos = (float[]) parts[6];
        int[][] nodeIdsPerGraph = (int[][]) parts[7];

        List<CongestionGraphPack> graphs = new ArrayList<>(numGraphs);
        for (int g = 0; g < numGraphs; g++) {
            int ec = graphEdgeCounts[g];
            int e0 = graphEdgeOffsets[g];
            int[] eu = Arrays.copyOfRange(edgeU, e0, e0 + ec);
            int[] ev = Arrays.copyOfRange(edgeV, e0, e0 + ec);
            // Avoid cloning n×n adjacency per graph — batch.adjacency is authoritative for GPU.
            List<Integer> nodeIds = new ArrayList<>(nodeIdsPerGraph[g].length);
            for (int id : nodeIdsPerGraph[g]) nodeIds.add(id);
            graphs.add(new CongestionGraphPack(nVertices, ec, eu, ev, EMPTY_ADJ, nodeIds));
        }
        return new BatchPack(nVertices, graphEdgeOffsets, graphEdgeCounts,
                edgeU, edgeV, adjacency, initialPos, graphs);
    }

    private static final byte[] EMPTY_ADJ = new byte[0];

    private static final class FlatGen {
        final int[] points;
        final int[] cycleLens;
        FlatGen(int[] points, int[] cycleLens) {
            this.points = points;
            this.cycleLens = cycleLens;
        }
    }

    private static FlatGen flatten(int[][][] combinedGen) {
        int nCycles = 0;
        int nPoints = 0;
        for (int[][] cs : combinedGen) {
            for (int[] cyc : cs) {
                nCycles++;
                nPoints += cyc.length;
            }
        }
        int[] points = new int[nPoints];
        int[] cycleLens = new int[nCycles];
        int pi = 0, ci = 0;
        for (int[][] cs : combinedGen) {
            for (int[] cyc : cs) {
                cycleLens[ci++] = cyc.length;
                for (int p : cyc) points[pi++] = p;
            }
        }
        return new FlatGen(points, cycleLens);
    }

    private static native Object[] nativePackOne(int[] points, int[] cycleLens);

    private static native Object[] nativePackBatch(int[] points, int[] cycleLens,
                                                   int[] cyclesPerGraph, long[] seeds);
}
