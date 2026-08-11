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
        char engine = (useTraces && !directed) ? 'T' : 'S';
        return "[" + engine
                + Integer.toHexString(h.w0()) + " "
                + Integer.toHexString(h.w1()) + " "
                + Integer.toHexString(h.w2()) + "]";
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
}
