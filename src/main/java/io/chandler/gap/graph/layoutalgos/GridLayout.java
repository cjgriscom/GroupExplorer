package io.chandler.gap.graph.layoutalgos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.graph.GridSolver;

public class GridLayout extends LayoutAlgo {
    java.util.List<Map<Integer, int[]>> results;
    private Map<Integer, double[]> result = new HashMap<>();
    private String lastCacheKey;

    @Override
    public void performLayout(double boxSize, String generator, Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args) {
        int solution = args.containsKey(LayoutAlgoArg.SOLUTION) ? args.get(LayoutAlgoArg.SOLUTION).intValue() : 0;
        boolean allSolutions = args.containsKey(LayoutAlgoArg.ALL_SOLUTIONS) ? args.get(LayoutAlgoArg.ALL_SOLUTIONS).intValue() != 0 : false;
        int w = args.containsKey(LayoutAlgoArg.W) ? args.get(LayoutAlgoArg.W).intValue() : 20;
        int h = args.containsKey(LayoutAlgoArg.H) ? args.get(LayoutAlgoArg.H).intValue() : 20;
        
        // Cache key should NOT include solution - we want to reuse results when just changing solution index
        // But it SHOULD include allSolutions because it changes the search space
        String cacheKey = generator + "_" + w + "_" + h + "_" + allSolutions;

        if (generator != null && !cacheKey.equals(this.lastCacheKey)) {
            try {
                System.out.println("Computing grid solutions for " + w + "x" + h + " grid...");
                java.util.List<Map<Integer, int[]>> results = GridSolver.solveAll(w, h, GroupExplorer.parseOperationsArr(generator)[0][0], allSolutions, graph);
                System.out.println("Found " + results.size() + " solutions");
                this.results = results;
                if (results.size() > 0) {
                    result = new HashMap<>();
                }
            } catch (Exception e) {
                System.out.println("Error solving grid: " + e.getMessage());
                e.printStackTrace();
                this.results = new java.util.ArrayList<>();
                this.result = new HashMap<>();
            }
            this.lastCacheKey = cacheKey;
        }

        if (results != null && results.size() > 0) {
            // Clamp solution index to valid range
            solution = Math.max(0, Math.min(solution, results.size() - 1));
            
            Map<Integer, double[]> positions = new HashMap<>();
            for (Map.Entry<Integer, int[]> e : results.get(solution).entrySet()) {
                int[] p = e.getValue();
                positions.put(e.getKey(), new double[]{ p[0], p[1] });
            }
            norm2D(positions, boxSize);
            result = positions;
        }
    }

    @Override
    public LayoutAlgoArg[] getArgs() {
        return new LayoutAlgoArg[]{LayoutAlgoArg.W, LayoutAlgoArg.H, LayoutAlgoArg.SOLUTION, LayoutAlgoArg.ALL_SOLUTIONS};
    }

    @Override
    public Map<Integer, double[]> getResult() {
        return result == null ? new HashMap<>() : result;
    }

    @Override
    public Double getFitOut() {
        return null;
    }
}


