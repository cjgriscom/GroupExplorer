package io.chandler.gap.graph.layoutalgos;

import java.util.Map;
import java.util.EnumMap;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import networkx.SpringLayout;

public class JavaNetworkx extends LayoutAlgo {

    private Map<Integer, double[]> result;

    @Override
    public void performLayout(double boxSize, String generator, Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args) {
        // Get the number of iterations and seed from the arguments (with defaults)
        int iters = args.containsKey(LayoutAlgoArg.ITERS) ? args.get(LayoutAlgoArg.ITERS).intValue() : 1000;
        int seed = args.containsKey(LayoutAlgoArg.SEED) ? args.get(LayoutAlgoArg.SEED).intValue() : 42;

        // Build the edge data string in the "u,v;u,v;…" format.
        StringBuilder edgeData = new StringBuilder();
        for (DefaultEdge edge : graph.edgeSet()) {
            int u = graph.getEdgeSource(edge);
            int v = graph.getEdgeTarget(edge);
            edgeData.append(u).append(",").append(v).append(";");
        }
        if (edgeData.length() > 0) {
            edgeData.setLength(edgeData.length() - 1);
        }

        // Call the external Networkx layout computation.
        result = computeLayout(edgeData.toString(), iters, 3, seed);

        norm3D(result, boxSize);
    }


    public static Map<Integer, double[]> computeLayout(String edgeData, int iterations, int dim, long seed) {
        // Parse edge data (format: "u,v;u,v;...")
        networkx.Graph g = new networkx.Graph();
        String[] edgeStrs = edgeData.split(";");
        for (String edgeStr : edgeStrs) {
            edgeStr = edgeStr.trim();
            if (!edgeStr.isEmpty()) {
                String[] parts = edgeStr.split(",");
                if (parts.length == 2) {
                    try {
                        int u = Integer.parseInt(parts[0].trim());
                        int v = Integer.parseInt(parts[1].trim());
                        g.addEdge(u, v);
                    } catch (NumberFormatException ex) {
                        // Ignore invalid edges
                    }
                }
            }
        }
        
        Map<Integer, double[]> pos = SpringLayout.springLayout(g, iterations, dim, seed);
        return pos;
    }

    @Override
    public LayoutAlgoArg[] getArgs() {
        return new LayoutAlgoArg[]{ LayoutAlgoArg.ITERS, LayoutAlgoArg.SEED };
    }

    @Override
    public Map<Integer, double[]> getResult() {
        return result;
    }

    @Override
    public Double getFitOut() {
        return null;
    }
}
