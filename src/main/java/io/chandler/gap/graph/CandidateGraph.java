package io.chandler.gap.graph;

import java.math.BigInteger;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Candidate geometry for PlanarStudy hot loops: either a native CSR handle
 * (default when TLS libnauty is available and no Java-only graph checks are
 * required) or a JGraphT graph (needed for cycle-multiple filtering, or when
 * the native library is unavailable).
 */
public abstract class CandidateGraph implements AutoCloseable {

    protected final boolean directed;

    protected CandidateGraph(boolean directed) {
        this.directed = directed;
    }

    public boolean directed() {
        return directed;
    }

    /**
     * @param needsJavaGraphChecks true when Java-only consumers are needed
     *        (currently {@code enforceLoopMultiples > 1})
     */
    public static CandidateGraph open(int[][][] combinedGen,
                                      boolean directed,
                                      boolean needsJavaGraphChecks) {
        boolean actualDirected = directed && !allTwoCycles(combinedGen);
        if (!needsJavaGraphChecks && NautyNative.hasTls()) {
            return new Native(combinedGen, actualDirected);
        }
        return new Jgrapht(combinedGen, actualDirected);
    }

    public abstract CanonicalGraphHash canonicalHash(DreadnautInterface dreadnaut);

    public abstract boolean isDisjointOrIncompleteSupport(int nPoints);

    /**
     * Java-only: simple-cycle length multiples. Native backends return true
     * when {@code k <= 1}; otherwise {@link #open} should have selected JGraphT.
     */
    public abstract boolean allEdgeCyclesAreMultiples(int k);

    public abstract BigInteger automorphismGroupOrder(boolean directed,
                                                      String dreadnautPath,
                                                      boolean useTraces);

    @Override
    public abstract void close();

    static boolean allTwoCycles(int[][][] combinedGen) {
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                if (polygon.length != 2) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ native

    static final class Native extends CandidateGraph {
        private long handle;

        Native(int[][][] combinedGen, boolean directed) {
            super(directed);
            this.handle = NautyNative.createFromGen(combinedGen, directed);
        }

        @Override
        public CanonicalGraphHash canonicalHash(DreadnautInterface dreadnaut) {
            ensureOpen();
            return NautyNative.canonicalHash(handle, dreadnaut.isUseTraces());
        }

        @Override
        public boolean isDisjointOrIncompleteSupport(int nPoints) {
            ensureOpen();
            return NautyNative.isDisjointOrIncomplete(handle, nPoints);
        }

        @Override
        public boolean allEdgeCyclesAreMultiples(int k) {
            if (k <= 1) return true;
            throw new IllegalStateException(
                    "cycle-multiple filter requires JGraphT CandidateGraph");
        }

        @Override
        public BigInteger automorphismGroupOrder(boolean directed,
                                                 String dreadnautPath,
                                                 boolean useTraces) {
            ensureOpen();
            // Geometry Aut is usually requested undirected (directed=false).
            boolean forceUndirected = !directed;
            String s = NautyNative.groupSize(handle, forceUndirected, useTraces);
            return new BigInteger(s.trim());
        }

        @Override
        public void close() {
            if (handle != 0L) {
                NautyNative.freeGraph(handle);
                handle = 0L;
            }
        }

        private void ensureOpen() {
            if (handle == 0L) {
                throw new IllegalStateException("native CandidateGraph already closed");
            }
        }
    }

    // ------------------------------------------------------------------ JGraphT

    static final class Jgrapht extends CandidateGraph {
        private final Graph<Integer, DefaultEdge> graph;

        Jgrapht(int[][][] combinedGen, boolean directed) {
            super(directed);
            this.graph = PlanarStudy.buildGraphFromCombinedGen(combinedGen, directed);
        }

        Graph<Integer, DefaultEdge> graph() {
            return graph;
        }

        @Override
        public CanonicalGraphHash canonicalHash(DreadnautInterface dreadnaut) {
            return CanonicalGraphHash.parse(dreadnaut.getCanonicalLabeling(graph, directed));
        }

        @Override
        public boolean isDisjointOrIncompleteSupport(int nPoints) {
            return PlanarStudy.isDisjointOrIncompleteSupport(graph, nPoints);
        }

        @Override
        public boolean allEdgeCyclesAreMultiples(int k) {
            return PlanarStudy.allEdgeCyclesAreMultiples(graph, k);
        }

        @Override
        public BigInteger automorphismGroupOrder(boolean directed,
                                                 String dreadnautPath,
                                                 boolean useTraces) {
            return GraphSymm.automorphismGroupOrder(
                    graph, directed, dreadnautPath, useTraces);
        }

        @Override
        public void close() {
            // JGraphT graphs are GC-managed
        }
    }
}
