package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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
 * Wrapper around the {@code dreadnaut} executable, with an optional in-process
 * backend via {@link NautyNative} (TLS-enabled bundled libnauty).
 * <p>
 * Select backend with {@code -Ddreadnaut.backend=process|native}.
 * Default is {@code native} when {@link NautyNative#hasTls()} (built by
 * {@code native/nauty_jni/build.sh}); otherwise {@code process}.
 */
public class DreadnautInterface {
    public enum Backend { PROCESS, NATIVE }

    private final String dreadnautPath;
    private final boolean useTraces;
    private final Backend backend;

    public DreadnautInterface(String dreadnautPath, boolean useTraces) {
        this(dreadnautPath, useTraces, defaultBackend());
    }

    public DreadnautInterface(String dreadnautPath, boolean useTraces, Backend backend) {
        this.dreadnautPath = dreadnautPath;
        this.useTraces = useTraces;
        this.backend = backend != null ? backend : defaultBackend();
        if (this.backend == Backend.NATIVE && !NautyNative.isAvailable()) {
            throw new IllegalStateException("dreadnaut.backend=native but libnauty_jni not loaded");
        }
    }

    public Backend getBackend() {
        return backend;
    }

    public boolean isUseTraces() {
        return useTraces;
    }

    public static Backend defaultBackend() {
        String prop = System.getProperty("dreadnaut.backend");
        if (prop != null) {
            switch (prop.trim().toLowerCase()) {
                case "process": return Backend.PROCESS;
                case "native": return Backend.NATIVE;
                default: break;
            }
        }
        // Only auto-select native when the bundled library is TLS-safe for
        // PlanarStudy's multi-threaded workers.
        return NautyNative.hasTls() ? Backend.NATIVE : Backend.PROCESS;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) {
            System.err.println("Usage: DreadnautInterface <dreadnaut-path> [directed=false] [useTraces=true]");
            System.exit(1);
        }

        String dreadnautPath = args[0];
        boolean directed = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
        boolean useTraces = args.length > 2 ? Boolean.parseBoolean(args[2]) : true;

        DreadnautInterface iface = new DreadnautInterface(dreadnautPath, useTraces);

        // Create a simple test graph: a triangle with an extra edge
        Graph<Integer, DefaultEdge> graph = PlanarStudy.buildGraphFromCombinedGen(new int[][][]{{{0,1,2},{1,2,3}}}, directed);

        String canonical = iface.getCanonicalLabeling(graph, directed);
        System.out.println("Canonical labeling: " + canonical);
    }

    /**
     * Returns the canonical labelling for a generator description. When the generators
     * only contain 2-cycles it is safe to treat the graph as undirected, matching the
     * logic previously embedded in {@link PlanarStudy}.
     * <p>
     * With {@link Backend#NATIVE}, the polygon graph is built inside JNI (no JGraphT).
     */
    public String getCanonicalLabeling(int[][][] combinedGen, boolean directed) {
        boolean actualDirected = directed && !allTwoCycles(combinedGen);
        if (backend == Backend.NATIVE) {
            return NautyNative.getCanonicalLabelingFromGen(combinedGen, actualDirected, useTraces);
        }
        Graph<Integer, DefaultEdge> graph =
            PlanarStudy.buildGraphFromCombinedGen(combinedGen, actualDirected);
        try {
            return canonicalizeProcess(graph, actualDirected);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("dreadnaut failed: " + e.getMessage(), e);
        }
    }

    public String getCanonicalLabeling(Graph<Integer, DefaultEdge> graph, boolean directed) {
        try {
            return canonicalize(graph, directed);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("dreadnaut failed: " + e.getMessage(), e);
        }
    }

    private String canonicalize(Graph<Integer, DefaultEdge> graph, boolean directed) throws IOException, InterruptedException {
        if (backend == Backend.NATIVE) {
            return NautyNative.getCanonicalLabeling(graph, directed, useTraces);
        }
        return canonicalizeProcess(graph, directed);
    }

    private String canonicalizeProcess(Graph<Integer, DefaultEdge> graph, boolean directed)
            throws IOException, InterruptedException {
        StringBuilder script = new StringBuilder();
        script.append("l=0\n");
        script.append("-m\n");
        // Match historical scripts: Traces (At) when requested+undirected, else dense nauty (Ad).
        script.append(useTraces && !directed ? "At" : "Ad").append("\n");
        if (directed) {
            script.append("d\n");
        }
        script.append(buildGraphScript(graph, directed));
        script.append("c -a\n");
        script.append("x\n");
        script.append("z\n");
        script.append("q\n");

        ProcessBuilder pb = new ProcessBuilder(dreadnautPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write(script.toString());
            writer.flush();

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("dreadnaut exited with code " + exitCode + ". Output: " + output);
            }

            return extractCanonicalOutput(output.toString());
        }
    }

    private String buildGraphScript(Graph<Integer, DefaultEdge> graph, boolean directed) {
        List<Integer> verts = new ArrayList<>(graph.vertexSet());
        Collections.sort(verts);

        Map<Integer, Integer> v2i = new HashMap<>();
        for (int i = 0; i < verts.size(); i++) {
            v2i.put(verts.get(i), i);
        }

        int n = verts.size();
        List<Set<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new TreeSet<>());
        }

        for (DefaultEdge e : new HashSet<>(graph.edgeSet())) {
            Integer u = graph.getEdgeSource(e);
            Integer v = graph.getEdgeTarget(e);
            int iu = v2i.get(u);
            int iv = v2i.get(v);
            adj.get(iu).add(iv);
            if (!directed) {
                adj.get(iv).add(iu);
            }
        }

        StringBuilder script = new StringBuilder();
        script.append("n=").append(n).append(" g\n");
        for (int i = 0; i < n; i++) {
            script.append(i).append(":");
            Set<Integer> neigh = adj.get(i);
            if (!neigh.isEmpty()) {
                script.append(" ");
                boolean first = true;
                for (int j : neigh) {
                    if (!first) {
                        script.append(" ");
                    }
                    script.append(j);
                    first = false;
                }
            }
            script.append(i == n - 1 ? ".\n" : ";\n");
        }
        return script.toString();
    }

    private String extractCanonicalOutput(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                return trimmed;
            }
        }
        throw new RuntimeException("No canonical labeling found in output: " + output);
    }

    private static boolean allTwoCycles(int[][][] combinedGen) {
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                if (polygon.length != 2) return false;
            }
        }
        return true;
    }
}