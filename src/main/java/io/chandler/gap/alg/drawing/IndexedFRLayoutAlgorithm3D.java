/*
 * A 3D version of the Fruchterman-Reingold Force-Directed Placement Algorithm using Barnes-Hut approximation.
 * This algorithm uses an octree for approximating repulsive forces in 3D space.
 */
package io.chandler.gap.alg.drawing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.jgrapht.Graph;
import org.jgrapht.alg.util.ToleranceDoubleComparator;

public class IndexedFRLayoutAlgorithm3D<V, E> extends FRLayoutAlgorithm3D<V, E> {
    public static final double DEFAULT_THETA_FACTOR = 0.5;

    protected double theta;
    protected long savedComparisons;

    /**
     * Create a new 3D layout algorithm with default parameters.
     */
    public IndexedFRLayoutAlgorithm3D() {
        this(DEFAULT_ITERATIONS, DEFAULT_THETA_FACTOR, DEFAULT_NORMALIZATION_FACTOR);
    }

    /**
     * Create a new 3D layout algorithm with specified iterations and theta parameter.
     * @param iterations number of iterations
     * @param theta parameter for Barnes-Hut approximation (should be between 0 and 1)
     */
    public IndexedFRLayoutAlgorithm3D(int iterations, double theta) {
        this(iterations, theta, DEFAULT_NORMALIZATION_FACTOR);
    }

    /**
     * Create a new 3D layout algorithm with specified iterations, theta, and normalization factor.
     * @param iterations number of iterations
     * @param theta parameter for Barnes-Hut approximation (should be between 0 and 1)
     * @param normalizationFactor normalization factor for optimal distance
     */
    public IndexedFRLayoutAlgorithm3D(int iterations, double theta, double normalizationFactor) {
        this(iterations, theta, normalizationFactor, new Random());
    }

    /**
     * Create a new 3D layout algorithm with a provided random generator.
     * @param iterations number of iterations
     * @param theta parameter for Barnes-Hut approximation
     * @param normalizationFactor normalization factor for optimal distance
     * @param rng random number generator
     */
    public IndexedFRLayoutAlgorithm3D(int iterations, double theta, double normalizationFactor, Random rng) {
        this(iterations, theta, normalizationFactor, rng, ToleranceDoubleComparator.DEFAULT_EPSILON);
    }

    /**
     * Create a new 3D layout algorithm with full parameterization.
     * @param iterations number of iterations
     * @param theta parameter for Barnes-Hut approximation
     * @param normalizationFactor normalization factor for optimal distance
     * @param rng random number generator
     * @param tolerance tolerance used when comparing floating point values
     */
    public IndexedFRLayoutAlgorithm3D(int iterations, double theta, double normalizationFactor, Random rng, double tolerance) {
        super(iterations, normalizationFactor, rng, tolerance);
        this.theta = theta;
        if (theta < 0d || theta > 1d) {
            throw new IllegalArgumentException("Illegal theta value");
        }
        this.savedComparisons = 0;
    }

    @Override
    public void layout(Graph<V, E> graph, LayoutModel3D<V> model) {
        this.savedComparisons = 0;
        super.layout(graph, model);
    }

    @Override
    protected Map<V, Point3D> calculateRepulsiveForces(Graph<V, E> graph, LayoutModel3D<V> model) {
        // Index all points using an octree based on the drawable volume of the layout model
        FROctree octree = new FROctree(model.getDrawableVolume());
        for (V v : graph.vertexSet()) {
            octree.insert(model.get(v));
        }

        // Determine the origin of the volume for relative computations
        Point3D origin = Point3D.of(
            model.getDrawableVolume().getMinX(),
            model.getDrawableVolume().getMinY(),
            model.getDrawableVolume().getMinZ()
        );

        Map<V, Point3D> disp = new HashMap<>();
        for (V v : graph.vertexSet()) {
            Point3D vPos = Points3D.subtract(model.get(v), origin);
            Point3D vDisp = Point3D.of(0d, 0d, 0d);

            Deque<FROctree.Node> queue = new ArrayDeque<>();
            queue.add(octree.getRoot());

            while (!queue.isEmpty()) {
                FROctree.Node node = queue.removeFirst();
                Box3D box = node.getBox();
                double boxWidth = box.getWidth();

                Point3D uPos = null;
                if (node.isLeaf()) {
                    if (!node.hasPoints()) {
                        continue;
                    }
                    // Use the first point in the leaf node
                    uPos = Points3D.subtract(node.getPoints().iterator().next(), origin);
                } else {
                    double distanceToCentroid = Points3D.length(Points3D.subtract(vPos, node.getCentroid()));
                    if (comparator.compare(distanceToCentroid, 0d) == 0) {
                        savedComparisons += node.getNumberOfPoints() - 1;
                        continue;
                    } else if (comparator.compare(boxWidth / distanceToCentroid, theta) < 0) {
                        uPos = Points3D.subtract(node.getCentroid(), origin);
                        savedComparisons += node.getNumberOfPoints() - 1;
                    } else {
                        for (FROctree.Node child : node.getChildren()) {
                            queue.add(child);
                        }
                        continue;
                    }
                }

                // Avoid computing repulsive force if positions are the same
                if (comparator.compare(vPos.getX(), uPos.getX()) != 0 ||
                    comparator.compare(vPos.getY(), uPos.getY()) != 0 ||
                    comparator.compare(vPos.getZ(), uPos.getZ()) != 0) {
                    Point3D delta = Points3D.subtract(vPos, uPos);
                    double deltaLen = Points3D.length(delta);
                    // Compute displacement contribution based on the repulsive force
                    Point3D dispContribution = Points3D.scalarMultiply(delta, repulsiveForce(deltaLen) / deltaLen);
                    vDisp = Points3D.add(vDisp, dispContribution);
                }
            }
            disp.put(v, vDisp);
        }
        return disp;
    }

    /**
     * Get the total number of saved comparisons due to the Barnes-Hut octree approximation.
     * @return the total number of saved comparisons
     */
    public long getSavedComparisons() {
        return savedComparisons;
    }
} 