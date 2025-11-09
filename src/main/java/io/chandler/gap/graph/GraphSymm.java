package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GroupExplorer;

/**
 * Utility for computing the order of the automorphism group of a graph
 * by invoking the external {@code dreadnaut} program (nauty / Traces).
 *
 * This mirrors the encoding used in {@link DreadnautInterface}.
 */
public final class GraphSymm {

    /** Default dreadnaut path used by helpers that don't take an explicit path. */
    public static final String DEFAULT_DREADNAUT_PATH =
            "dreadnaut";

    /** Whether to prefer Traces (At) over nauty (Ad) by default. */
    public static final boolean DEFAULT_USE_TRACES = true;

	public static void main(String[] args) {
		String gen = "[(1,10)(2,6)(3,11)(4,12)(5,9)(7,8),(1,11)(2,12)(3,5)(4,10)(6,8)(7,9),(1,7)(2,4)(3,10)(5,6)(8,12)(9,11)]";
		// build graph
		Graph<Integer, DefaultEdge> graph = PlanarStudy.buildGraphFromCombinedGen(GroupExplorer.parseOperationsArr(gen), false);
		// compute automorphism group order
		BigInteger order = automorphismGroupOrder(graph, false, DEFAULT_DREADNAUT_PATH, DEFAULT_USE_TRACES);
		System.out.println("Automorphism group order: " + order);
	}

    private GraphSymm() {
        // utility class
    }

    /**
     * Convenience overload: build a graph from the combined generator
     * description used elsewhere in the project, then compute |Aut(G)|.
     *
     * @param combinedGen   your int[][][] generator description
     * @param directed      whether to treat the graph as directed
     * @return |Aut(G)| as a BigInteger
     */
    public static BigInteger automorphismGroupOrder(int[][][] combinedGen,
                                                    boolean directed) {
        return automorphismGroupOrder(combinedGen, directed,
                DEFAULT_DREADNAUT_PATH, DEFAULT_USE_TRACES);
    }

    /**
     * Convenience overload: build a graph from the combined generator
     * description used elsewhere in the project, then compute |Aut(G)|.
     *
     * @param combinedGen   your int[][][] generator description
     * @param directed      whether to treat the graph as directed
     * @param dreadnautPath path to the dreadnaut executable
     * @param useTraces     true to use Traces, false to use nauty
     */
    public static BigInteger automorphismGroupOrder(int[][][] combinedGen,
                                                    boolean directed,
                                                    String dreadnautPath,
                                                    boolean useTraces) {
        boolean actualDirected = directed && !allTwoCycles(combinedGen);
        Graph<Integer, DefaultEdge> graph =
                PlanarStudy.buildGraphFromCombinedGen(combinedGen, actualDirected);
        return automorphismGroupOrder(graph, actualDirected, dreadnautPath, useTraces);
    }

    /**
     * Compute the order of the automorphism group |Aut(G)|
     * for a JGraphT graph via dreadnaut.
     *
     * @param graph         JGraphT graph (vertices are Integers)
     * @param directed      whether to treat the graph as directed
     * @param dreadnautPath path to the dreadnaut executable
     * @param useTraces     true to use Traces, false to use nauty
     * @return |Aut(G)| as a BigInteger
     */
    public static BigInteger automorphismGroupOrder(Graph<Integer, DefaultEdge> graph,
                                                    boolean directed,
                                                    String dreadnautPath,
                                                    boolean useTraces) {
        try {
            String script = buildScript(graph, directed, useTraces);

            ProcessBuilder pb = new ProcessBuilder(dreadnautPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                writer.write(script);
                writer.flush();
                writer.close(); // signal EOF to dreadnaut

                String line;
                BigInteger groupSize = null;

                // dreadnaut prints a line like:
                // "1 orbit; grpsize=9170703360; 4 gens; 9 nodes (6 peak); maxlev=2"
                while ((line = reader.readLine()) != null) {
                    int idx = line.indexOf("grpsize=");
                    if (idx >= 0) {
                        int start = idx + "grpsize=".length();
                        int end = start;
                        while (end < line.length() && Character.isDigit(line.charAt(end))) {
                            end++;
                        }
                        String num = line.substring(start, end);
                        groupSize = new BigInteger(num);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("dreadnaut exited with code " + exitCode);
                }
                if (groupSize == null) {
                    throw new IOException("Could not find grpsize=... in dreadnaut output");
                }

                return groupSize;
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to run dreadnaut: " + e.getMessage(), e);
        }
    }

    // --- internal helpers ---------------------------------------------------

    private static String buildScript(Graph<Integer, DefaultEdge> graph,
                                      boolean directed,
                                      boolean useTraces) {
        StringBuilder script = new StringBuilder();
        script.append("l=0\n");                       // label vertices 0..n-1
        script.append("-m\n");                        // more informative output
        script.append(useTraces && !directed ? "At" : "Ad").append("\n");
        if (directed) {
            script.append("d\n");                     // directed mode
        }
        script.append(buildGraphScript(graph, directed));
        script.append("x\n");                         // run nauty/Traces -> prints grpsize=...
        script.append("q\n");                         // quit
        return script.toString();
    }

    private static String buildGraphScript(Graph<Integer, DefaultEdge> graph, boolean directed) {
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

    private static boolean allTwoCycles(int[][][] combinedGen) {
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                if (polygon.length != 2) {
                    return false;
                }
            }
        }
        return true;
    }
}