package io.chandler.gap.graph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

/**
 * JNI bridge to a TLS-enabled {@code libnauty} statically linked into
 * {@code libnauty_jni.so} (see {@code native/nauty_jni/build.sh}).
 * Produces the same dreadnaut {@code z} hash words as {@link DreadnautInterface}.
 */
public final class NautyNative {

    private static final boolean AVAILABLE;
    private static final boolean TLS;

    static {
        boolean loaded = tryLoad() && nativeAvailable();
        AVAILABLE = loaded;
        TLS = loaded && nativeHasTls();
    }

    private NautyNative() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** True when the bundled nauty was built with {@code --enable-tls}. */
    public static boolean hasTls() {
        return TLS;
    }

    public static String version() {
        return AVAILABLE ? nativeVersion() : "unavailable";
    }

    /**
     * Canonical graph hash matching dreadnaut {@code z} (engine letter omitted).
     *
     * @param n          vertex count (0..n-1)
     * @param edges      packed endpoints {@code [u0,v0,u1,v1,...]}, 0-based
     * @param directed   digraph vs undirected
     * @param useTraces  Traces ({@code At}) when undirected; else sparsenauty
     */
    public static CanonicalGraphHash canonicalHash(int n, int[] edges,
                                                   boolean directed,
                                                   boolean useTraces) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        int[] w = nativeCanonicalHash(n, edges, directed, useTraces);
        return new CanonicalGraphHash(w[0], w[1], w[2]);
    }

    public static CanonicalGraphHash canonicalHash(Graph<Integer, DefaultEdge> graph,
                                                   boolean directed,
                                                   boolean useTraces) {
        Packed g = pack(graph, directed);
        return canonicalHash(g.n, g.edges, directed, useTraces);
    }

    /** Same encoding / Traces default as {@link DreadnautInterface}. */
    public static String getCanonicalLabeling(Graph<Integer, DefaultEdge> graph,
                                              boolean directed,
                                              boolean useTraces) {
        CanonicalGraphHash h = canonicalHash(graph, directed, useTraces);
        return formatZ(h, directed, useTraces);
    }

    /**
     * Build the polygon graph from a combined generator (same geometry as
     * {@link PlanarStudy#buildGraphFromCombinedGen}) entirely in native code,
     * then return the dreadnaut {@code z} string.
     */
    public static String getCanonicalLabelingFromGen(int[][][] combinedGen,
                                                     boolean directed,
                                                     boolean useTraces) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        FlatGen flat = flatten(combinedGen);
        int[] w = nativeCanonicalHashFromGen(flat.points, flat.cycleLens,
                directed, useTraces);
        return formatZ(new CanonicalGraphHash(w[0], w[1], w[2]), directed, useTraces);
    }

    public static CanonicalGraphHash canonicalHashFromGen(int[][][] combinedGen,
                                                          boolean directed,
                                                          boolean useTraces) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        FlatGen flat = flatten(combinedGen);
        int[] w = nativeCanonicalHashFromGen(flat.points, flat.cycleLens,
                directed, useTraces);
        return new CanonicalGraphHash(w[0], w[1], w[2]);
    }

    /**
     * Opaque CSR graph built once from generators. Free with {@link #freeGraph(long)}.
     * Prefer {@link CandidateGraph} which owns the lifecycle.
     */
    public static long createFromGen(int[][][] combinedGen, boolean directed) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        FlatGen flat = flatten(combinedGen);
        long h = nativeCreateFromGen(flat.points, flat.cycleLens, directed);
        if (h == 0L) {
            throw new IllegalStateException("nativeCreateFromGen returned null handle");
        }
        return h;
    }

    public static void freeGraph(long handle) {
        if (handle != 0L && AVAILABLE) {
            nativeFree(handle);
        }
    }

    public static CanonicalGraphHash canonicalHash(long handle, boolean useTraces) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        int[] w = nativeCanonicalHashHandle(handle, useTraces);
        return new CanonicalGraphHash(w[0], w[1], w[2]);
    }

    public static String getCanonicalLabeling(long handle, boolean directed, boolean useTraces) {
        return formatZ(canonicalHash(handle, useTraces), directed, useTraces);
    }

    /** |Aut(G)| as decimal string (nauty/Traces grpsize1·10^grpsize2). */
    public static String groupSize(long handle, boolean forceUndirected, boolean useTraces) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        return nativeGrpsizeHandle(handle, forceUndirected, useTraces);
    }

    public static boolean isDisjointOrIncomplete(long handle, int nPoints) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        return nativeIsDisjointOrIncomplete(handle, nPoints);
    }

    public static int vertexCount(long handle) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libnauty_jni not loaded");
        }
        return nativeVertexCount(handle);
    }

    private static String formatZ(CanonicalGraphHash h, boolean directed, boolean useTraces) {
        char engine = (useTraces && !directed) ? 'T' : 'S';
        return "[" + engine
                + Integer.toHexString(h.w0()) + " "
                + Integer.toHexString(h.w1()) + " "
                + Integer.toHexString(h.w2()) + "]";
    }

    static final class FlatGen {
        final int[] points;
        final int[] cycleLens;
        FlatGen(int[] points, int[] cycleLens) {
            this.points = points;
            this.cycleLens = cycleLens;
        }
    }

    /** Concatenate all cycles; {@code cycleLens[i]} is the length of cycle {@code i}. */
    static FlatGen flatten(int[][][] combinedGen) {
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

    static final class Packed {
        final int n;
        final int[] edges;
        Packed(int n, int[] edges) { this.n = n; this.edges = edges; }
    }

    /** Remap vertex labels to 0..n-1 (sorted) and emit undirected/directed edges. */
    static Packed pack(Graph<Integer, DefaultEdge> graph, boolean directed) {
        List<Integer> verts = new ArrayList<>(graph.vertexSet());
        Collections.sort(verts);
        Map<Integer, Integer> v2i = new HashMap<>(verts.size() * 2);
        for (int i = 0; i < verts.size(); i++) {
            v2i.put(verts.get(i), i);
        }
        int n = verts.size();
        List<int[]> edgeList = new ArrayList<>();
        if (directed) {
            for (DefaultEdge e : new HashSet<>(graph.edgeSet())) {
                int u = v2i.get(graph.getEdgeSource(e));
                int v = v2i.get(graph.getEdgeTarget(e));
                if (u != v) edgeList.add(new int[]{u, v});
            }
        } else {
            // Emit each undirected edge once (u < v); native adds both directions.
            Set<Long> seen = new HashSet<>();
            for (DefaultEdge e : new HashSet<>(graph.edgeSet())) {
                int u = v2i.get(graph.getEdgeSource(e));
                int v = v2i.get(graph.getEdgeTarget(e));
                if (u == v) continue;
                int a = Math.min(u, v), b = Math.max(u, v);
                long key = (((long) a) << 32) | (b & 0xffffffffL);
                if (seen.add(key)) edgeList.add(new int[]{a, b});
            }
        }
        int[] edges = new int[edgeList.size() * 2];
        for (int i = 0; i < edgeList.size(); i++) {
            edges[2 * i] = edgeList.get(i)[0];
            edges[2 * i + 1] = edgeList.get(i)[1];
        }
        return new Packed(n, edges);
    }

    /** Build adjacency the same way {@link DreadnautInterface} scripts it (for tests). */
    static List<Set<Integer>> adjacency(Graph<Integer, DefaultEdge> graph, boolean directed) {
        List<Integer> verts = new ArrayList<>(graph.vertexSet());
        Collections.sort(verts);
        Map<Integer, Integer> v2i = new HashMap<>();
        for (int i = 0; i < verts.size(); i++) v2i.put(verts.get(i), i);
        int n = verts.size();
        List<Set<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new TreeSet<>());
        for (DefaultEdge e : new HashSet<>(graph.edgeSet())) {
            int u = v2i.get(graph.getEdgeSource(e));
            int v = v2i.get(graph.getEdgeTarget(e));
            adj.get(u).add(v);
            if (!directed) adj.get(v).add(u);
        }
        return adj;
    }

    private static boolean tryLoad() {
        try {
            System.loadLibrary("nauty_jni");
            return true;
        } catch (UnsatisfiedLinkError ignored) {
        }
        Path[] candidates = new Path[] {
                Paths.get("native/nauty_jni/libnauty_jni.so"),
                Paths.get("lib/libnauty_jni.so"),
        };
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) continue;
            try {
                System.load(p.toAbsolutePath().toString());
                return true;
            } catch (UnsatisfiedLinkError ignored) {
            }
        }
        return false;
    }

    private static native boolean nativeAvailable();
    private static native boolean nativeHasTls();
    private static native String nativeVersion();
    private static native int[] nativeCanonicalHash(int n, int[] edges,
                                                    boolean directed, boolean useTraces);
    private static native int[] nativeCanonicalHashFromGen(int[] points, int[] cycleLens,
                                                           boolean directed, boolean useTraces);
    private static native long nativeCreateFromGen(int[] points, int[] cycleLens, boolean directed);
    private static native void nativeFree(long handle);
    private static native int[] nativeCanonicalHashHandle(long handle, boolean useTraces);
    private static native String nativeGrpsizeHandle(long handle, boolean forceUndirected,
                                                     boolean useTraces);
    private static native boolean nativeIsDisjointOrIncomplete(long handle, int nPoints);
    private static native int nativeVertexCount(long handle);
}
