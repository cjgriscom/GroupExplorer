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
		// Globally symmetric geometry: Aut=2, QAut=4 on undirected simple quotient
		String gen = "[(1,84)(2,101)(3,27)(4,68)(5,58)(6,134)(7,8)(9,19)(10,23)(11,99)(12,136)(13,38)(14,60)(15,78)(16,126)(17,87)(18,43)(21,114)(22,70)(24,69)(25,59)(26,56)(28,48)(29,121)(30,50)(31,112)(32,65)(33,93)(34,83)(35,51)(36,42)(37,86)(39,115)(40,61)(41,64)(44,75)(45,95)(46,128)(49,122)(52,85)(53,116)(54,135)(55,82)(57,81)(62,72)(63,113)(71,120)(73,130)(74,91)(76,90)(79,127)(80,108)(88,109)(92,125)(94,117)(96,132)(98,111)(100,131)(102,104)(103,105)(106,110)(107,118)(119,124)(123,133),(1,117)(2,95)(3,69)(4,113)(5,33)(7,114)(9,32)(10,89)(11,103)(12,122)(13,125)(14,130)(15,116)(16,78)(17,22)(18,108)(19,59)(20,42)(21,49)(23,68)(24,106)(26,66)(28,36)(29,75)(30,60)(31,52)(34,79)(35,53)(37,105)(38,124)(39,94)(41,85)(43,131)(44,96)(45,118)(46,98)(47,119)(48,83)(50,84)(51,97)(54,93)(55,81)(56,70)(57,91)(58,121)(61,87)(63,72)(64,104)(65,135)(67,102)(71,92)(73,110)(74,128)(77,136)(80,82)(88,123)(90,107)(99,109)(101,133)(111,134),(1,79)(2,30)(3,69)(5,107)(6,40)(7,128)(8,25)(9,81)(10,50)(11,124)(13,125)(14,109)(15,63)(16,68)(17,80)(18,133)(19,85)(20,61)(21,134)(22,82)(23,78)(24,77)(26,66)(27,126)(28,36)(31,52)(32,55)(33,90)(34,117)(35,44)(38,103)(39,121)(41,59)(42,87)(43,71)(45,51)(46,98)(49,111)(53,96)(56,102)(58,94)(60,95)(62,76)(64,73)(65,135)(67,70)(72,116)(74,114)(84,89)(86,120)(88,123)(92,131)(97,118)(99,130)(100,129)(101,108)(104,110)(106,136)(112,132)(115,127)]";
		Graph<Integer, DefaultEdge> graph = PlanarStudy.buildGraphFromCombinedGen(GroupExplorer.parseOperationsArr(gen), false);
		BigInteger order = automorphismGroupOrder(graph, false, DEFAULT_DREADNAUT_PATH, DEFAULT_USE_TRACES);
		System.out.println("Automorphism group order: " + order);
		BigInteger qOrder = quotientAutomorphismGroupOrder(graph, DEFAULT_DREADNAUT_PATH, DEFAULT_USE_TRACES);
		System.out.println("Quotient automorphism group order: " + qOrder);
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
            String script = buildScript(graph, directed, useTraces, null);
            return runDreadnautGrpsize(script, dreadnautPath);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to run dreadnaut: " + e.getMessage(), e);
        }
    }

    /**
     * Global-symmetry score based on the 1-WL equitable quotient of G.
     * <p>
     * The input is always treated as an undirected simple graph (edge geometry
     * only — no direction, no edge colors/weights). Local indistinguishability
     * is collapsed into cells; the score is {@code |Aut(Q)|} of the simple
     * undirected quotient with cells colored by size. When there is a single
     * cell (1-WL–vertex-transitive), {@code |Aut(Q)|} is trivial, so the score
     * falls back to {@code |Aut(G)|}.
     */
    public static BigInteger quotientAutomorphismGroupOrder(int[][][] combinedGen,
                                                            boolean directed) {
        // directed is ignored: geometry is always undirected/simple
        return quotientAutomorphismGroupOrder(combinedGen,
                DEFAULT_DREADNAUT_PATH, DEFAULT_USE_TRACES);
    }

    public static BigInteger quotientAutomorphismGroupOrder(int[][][] combinedGen) {
        return quotientAutomorphismGroupOrder(combinedGen,
                DEFAULT_DREADNAUT_PATH, DEFAULT_USE_TRACES);
    }

    public static BigInteger quotientAutomorphismGroupOrder(int[][][] combinedGen,
                                                            String dreadnautPath,
                                                            boolean useTraces) {
        Graph<Integer, DefaultEdge> graph =
                PlanarStudy.buildGraphFromCombinedGen(combinedGen, false);
        return quotientAutomorphismGroupOrder(graph, dreadnautPath, useTraces);
    }

    public static BigInteger quotientAutomorphismGroupOrder(Graph<Integer, DefaultEdge> graph,
                                                            boolean directed,
                                                            String dreadnautPath,
                                                            boolean useTraces) {
        // directed is ignored: geometry is always undirected/simple
        return quotientAutomorphismGroupOrder(graph, dreadnautPath, useTraces);
    }

    public static BigInteger quotientAutomorphismGroupOrder(Graph<Integer, DefaultEdge> graph,
                                                            String dreadnautPath,
                                                            boolean useTraces) {
        try {
            QuotientEncoding enc = encodeEquitableQuotient(graph);
            if (enc.n == 0) {
                return BigInteger.ONE;
            }
            // Single 1-WL cell: quotient Aut is trivial; report full |Aut(G)|.
            if (enc.cellCount <= 1) {
                return automorphismGroupOrder(graph, false, dreadnautPath, useTraces);
            }
            String script = buildScriptFromAdj(enc.adj, false, useTraces, enc.partition);
            return runDreadnautGrpsize(script, dreadnautPath);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to run dreadnaut: " + e.getMessage(), e);
        }
    }

    // --- internal helpers ---------------------------------------------------

    /** Simple undirected quotient on 1-WL cells; vertices colored by cell size. */
    private static final class QuotientEncoding {
        final List<Set<Integer>> adj;
        final int n;
        final int cellCount;
        final String partition; // dreadnaut f=[...] by cell size, or null
        QuotientEncoding(List<Set<Integer>> adj, int cellCount, String partition) {
            this.adj = adj;
            this.n = adj.size();
            this.cellCount = cellCount;
            this.partition = partition;
        }
    }

    /**
     * 1-WL color refinement → equitable cells, then the simple undirected
     * quotient: one vertex per cell, an edge iff any original edge crosses
     * between those cells. No edge colors/weights. Cells are partitioned by
     * size so differently sized roles cannot be swapped.
     */
    static QuotientEncoding encodeEquitableQuotient(Graph<Integer, DefaultEdge> graph) {
        List<Integer> verts = new ArrayList<>(graph.vertexSet());
        Collections.sort(verts);
        if (verts.isEmpty()) {
            return new QuotientEncoding(Collections.emptyList(), 0, null);
        }

        Map<Integer, Integer> v2i = new HashMap<>();
        for (int i = 0; i < verts.size(); i++) {
            v2i.put(verts.get(i), i);
        }
        int n = verts.size();
        List<List<Integer>> neigh = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            neigh.add(new ArrayList<>());
        }
        // Undirected simple adjacency only
        for (DefaultEdge e : new HashSet<>(graph.edgeSet())) {
            int iu = v2i.get(graph.getEdgeSource(e));
            int iv = v2i.get(graph.getEdgeTarget(e));
            if (iu == iv) continue;
            neigh.get(iu).add(iv);
            neigh.get(iv).add(iu);
        }

        int[] color = colorRefine(neigh);

        // Group vertices into cells by refined color; stable order by min vertex index.
        Map<Integer, List<Integer>> byColor = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byColor.computeIfAbsent(color[i], k -> new ArrayList<>()).add(i);
        }
        List<List<Integer>> cells = new ArrayList<>(byColor.values());
        cells.sort(Comparator.comparingInt(c -> c.get(0)));
        int k = cells.size();

        int[] cellOf = new int[n];
        int[] cellSize = new int[k];
        for (int c = 0; c < k; c++) {
            cellSize[c] = cells.get(c).size();
            for (int v : cells.get(c)) {
                cellOf[v] = c;
            }
        }

        // Simple undirected quotient: edge between cells if any cross-edge exists
        List<Set<Integer>> adj = new ArrayList<>(k);
        Map<Integer, List<Integer>> sizeClasses = new TreeMap<>();
        for (int c = 0; c < k; c++) {
            adj.add(new TreeSet<>());
            sizeClasses.computeIfAbsent(cellSize[c], x -> new ArrayList<>()).add(c);
        }
        for (int i = 0; i < n; i++) {
            int ci = cellOf[i];
            for (int nb : neigh.get(i)) {
                int cj = cellOf[nb];
                if (ci != cj) {
                    adj.get(ci).add(cj);
                    adj.get(cj).add(ci);
                }
            }
        }

        return new QuotientEncoding(adj, k, buildPartitionString(sizeClasses));
    }

    /** Classic 1-WL / equitable color refinement. Returns color id per vertex index. */
    static int[] colorRefine(List<List<Integer>> neigh) {
        int n = neigh.size();
        int[] color = new int[n];
        for (int i = 0; i < n; i++) {
            color[i] = neigh.get(i).size(); // initial: degree
        }
        while (true) {
            Map<String, Integer> sigToId = new HashMap<>();
            int[] next = new int[n];
            int nextId = 0;
            for (int i = 0; i < n; i++) {
                int[] nbColors = new int[neigh.get(i).size()];
                int t = 0;
                for (int nb : neigh.get(i)) {
                    nbColors[t++] = color[nb];
                }
                Arrays.sort(nbColors);
                StringBuilder sb = new StringBuilder();
                sb.append(color[i]).append('|');
                for (int c : nbColors) {
                    sb.append(c).append(',');
                }
                String sig = sb.toString();
                Integer id = sigToId.get(sig);
                if (id == null) {
                    id = nextId++;
                    sigToId.put(sig, id);
                }
                next[i] = id;
            }
            if (Arrays.equals(color, next)) {
                return color;
            }
            color = next;
        }
    }

    private static String buildPartitionString(Map<Integer, List<Integer>> colorClasses) {
        if (colorClasses.size() <= 1) {
            return null; // all same size: no f= needed
        }
        StringBuilder sb = new StringBuilder("f=[");
        boolean firstClass = true;
        for (List<Integer> cls : colorClasses.values()) {
            if (!firstClass) sb.append('|');
            firstClass = false;
            for (int i = 0; i < cls.size(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(cls.get(i));
            }
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static BigInteger runDreadnautGrpsize(String script, String dreadnautPath)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(dreadnautPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write(script);
            writer.flush();
            writer.close();

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
    }

    private static String buildScript(Graph<Integer, DefaultEdge> graph,
                                      boolean directed,
                                      boolean useTraces,
                                      String partition) {
        return buildScriptFromAdj(adjacencyFromGraph(graph, directed), directed, useTraces, partition);
    }

    private static String buildScriptFromAdj(List<Set<Integer>> adj,
                                             boolean directed,
                                             boolean useTraces,
                                             String partition) {
        StringBuilder script = new StringBuilder();
        script.append("l=0\n");                       // label vertices 0..n-1
        script.append("-m\n");                        // more informative output
        script.append(useTraces && !directed ? "At" : "Ad").append("\n");
        if (directed) {
            script.append("d\n");                     // directed mode
        }
        script.append(buildGraphScriptFromAdj(adj));
        if (partition != null) {
            script.append(partition);
        }
        script.append("x\n");                         // run nauty/Traces -> prints grpsize=...
        script.append("q\n");                         // quit
        return script.toString();
    }

    private static List<Set<Integer>> adjacencyFromGraph(Graph<Integer, DefaultEdge> graph,
                                                         boolean directed) {
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
        return adj;
    }

    private static String buildGraphScriptFromAdj(List<Set<Integer>> adj) {
        int n = adj.size();
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