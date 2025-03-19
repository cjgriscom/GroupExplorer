package io.chandler.gap.graph.layoutalgos;

import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Random;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.alg.drawing.IndexedFRLayoutAlgorithm2D;
import org.jgrapht.alg.drawing.model.Box2D;
import org.jgrapht.alg.drawing.model.LayoutModel2D;
import org.jgrapht.alg.drawing.model.MapLayoutModel2D;
import org.jgrapht.alg.drawing.model.Point2D;

public class JavaSpring extends LayoutAlgo {

    private Map<Integer, double[]> result;

    @Override
    public void performLayout(double boxSize, String generator, Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args) {
        // Retrieve parameters with defaults.
        int iterations = args.containsKey(LayoutAlgoArg.ITERS) ? args.get(LayoutAlgoArg.ITERS).intValue() : 1000;
        int seed = args.containsKey(LayoutAlgoArg.SEED) ? args.get(LayoutAlgoArg.SEED).intValue() : 42;
        double theta = args.containsKey(LayoutAlgoArg.THETA) ? args.get(LayoutAlgoArg.THETA) : 0.5;  // represents 0.50
        double norm = args.containsKey(LayoutAlgoArg.NORM) ? args.get(LayoutAlgoArg.NORM) : 0.5;    // represents 0.50

        // Validate limits.
        if (iterations < 0) iterations = 1;
        if (norm < 0.01) norm = 0.01;
        if (theta < 0.01) theta = 0.01;
        if (iterations > 10000) iterations = 10000;
        if (norm > 0.99) norm = 0.99;
        if (theta > 0.99) theta = 0.99;

        // Create a layout model with a box sized relative to the drawing pane.
        Box2D box2d = new Box2D(boxSize * 2, boxSize * 2);
        LayoutModel2D<Integer> layoutModel = new MapLayoutModel2D<>(box2d);

        // Instantiate and run the force-directed layout algorithm.
        IndexedFRLayoutAlgorithm2D<Integer, DefaultEdge> algorithm =
                new IndexedFRLayoutAlgorithm2D<>(iterations, theta, norm, new Random(seed));
        algorithm.layout(graph, layoutModel);

        // Extract the 2D positions.
        Map<Integer, double[]> positions = new HashMap<>();
        for (Integer vertex : graph.vertexSet()) {
            Point2D point = layoutModel.get(vertex);
            positions.put(vertex, new double[]{ point.getX(), point.getY() });
        }

		norm2D(positions, boxSize);
        result = positions;
    }

    @Override
    public LayoutAlgoArg[] getArgs() {
        // This algorithm requires ITERS, SEED, THETA, and NORM.
        return new LayoutAlgoArg[]{ LayoutAlgoArg.ITERS, LayoutAlgoArg.SEED, LayoutAlgoArg.THETA, LayoutAlgoArg.NORM };
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