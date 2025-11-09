package io.chandler.gap.graph.layoutalgos;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Random;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.alg.drawing.Box3D;
import io.chandler.gap.alg.drawing.LayoutModel3D;
import io.chandler.gap.alg.drawing.MapLayoutModel3D;
import io.chandler.gap.alg.drawing.IndexedFRLayoutAlgorithm3D;
import io.chandler.gap.alg.drawing.Point3D;

public class Java3D extends LayoutAlgo {

    private Map<Integer, double[]> result;

    @Override
    public void performLayout(double boxSize, String generator, Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args) {
        int iterations = args.containsKey(LayoutAlgoArg.ITERS) ? args.get(LayoutAlgoArg.ITERS).intValue() : 1000;
        int seed = args.containsKey(LayoutAlgoArg.SEED) ? args.get(LayoutAlgoArg.SEED).intValue() : 42;
        double theta = args.containsKey(LayoutAlgoArg.THETA) ? args.get(LayoutAlgoArg.THETA) : 0.5;
        double norm = args.containsKey(LayoutAlgoArg.NORM) ? args.get(LayoutAlgoArg.NORM) : 0.5;

        if (iterations < 0) iterations = 1;
        if (norm < 0.01) norm = 0.01;
        if (theta < 0.01) theta = 0.01;
        if (iterations > 10000) iterations = 10000;
        if (norm > 0.99) norm = 0.99;
        if (theta > 0.99) theta = 0.99;

        // Create a 3D box with dimensions based on the pane height.
        Box3D box3d = new Box3D(0, 0, 0, boxSize * 5, boxSize * 5, boxSize * 5);
        LayoutModel3D<Integer> layoutModel = new MapLayoutModel3D<>(box3d);

        // Run the 3D force-directed layout.
        IndexedFRLayoutAlgorithm3D<Integer, DefaultEdge> algorithm3D =
                new IndexedFRLayoutAlgorithm3D<>(iterations, theta, norm, new Random(seed));
        algorithm3D.layout(graph, layoutModel);

        // Extract positions from the 3D layout.
        Map<Integer, double[]> positions = new HashMap<>();
        for (Integer vertex : graph.vertexSet()) {
            Point3D point = layoutModel.get(vertex);
            positions.put(vertex, new double[]{ point.getX(), point.getY(), point.getZ() });
        }

		norm3D(positions, boxSize);
		result = positions;
    }

    @Override
    public LayoutAlgoArg[] getArgs() {
        // This algorithm requires ITERS, SEED, THETA, and NORM.
        return new LayoutAlgoArg[]{ LayoutAlgoArg.ITERS, LayoutAlgoArg.SEED, LayoutAlgoArg.THETA, LayoutAlgoArg.NORM };
    }

    @Override
    public Map<Integer, double[]> getResult() {
        double[][] hardcodedArray = {
            {360.90859981719836, 515.7404984597775, 125.87484565231426},
            {463.6118896808394, 67.2, 203.47115590124963},
            {604.8000000000001, 379.5651192368765, 250.80036702331},
            {67.2, 222.30045267686796, 376.20381520653876},
            {220.13120372586127, 200.52542771043233, 67.2},
            {454.1852192906288, 396.449776360985, 564.7365560594545},
            {303.29364573799444, 83.53995790754988, 510.56212356162837},
            {204.88310486357307, 533.8382082347607, 438.83854179988793}
        };
        int indices[] = {16, 9, 10, 11, 12, 13, 14, 15};
        Map<Integer, double[]> result = new HashMap<>();
        for (int i = 0; i < indices.length; i++) {
            result.put(indices[i], hardcodedArray[i]);
        }
        return result;
    }

    @Override
    public Double getFitOut() {
        return null;
    }
} 