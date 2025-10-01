package io.chandler.gap.graph.layoutalgos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.graph.GridSolver;

public class GridLayout extends LayoutAlgo {

    private Map<Integer, double[]> result;

    @Override
    public void performLayout(double boxSize, String generator, Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args) {
        try {
			Map<Integer, int[]> integerPositions = GridSolver.solve(graph);
			Map<Integer, double[]> positions = new HashMap<>();
			for (Map.Entry<Integer, int[]> e : integerPositions.entrySet()) {
				int[] p = e.getValue();
				positions.put(e.getKey(), new double[]{ p[0], p[1] });
			}
			norm2D(positions, boxSize);
			result = positions;
		} catch (Exception e) {
			System.out.println("Error solving grid: " + e.getMessage());
			e.printStackTrace();
			result = new HashMap<>();
		}
    }

    @Override
    public LayoutAlgoArg[] getArgs() {
        return new LayoutAlgoArg[]{};
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


