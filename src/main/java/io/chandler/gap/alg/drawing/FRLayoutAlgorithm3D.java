package io.chandler.gap.alg.drawing;

import org.jgrapht.Graph;
import org.jgrapht.alg.util.ToleranceDoubleComparator;

import java.util.*;
import java.util.function.*;

public class FRLayoutAlgorithm3D<V, E> {

    public static final int DEFAULT_ITERATIONS = 700;
    public static final double DEFAULT_NORMALIZATION_FACTOR = 1.0;

    protected Random rng;
    protected double optimalDistance;
    protected double normalizationFactor;
    protected int iterations;
    protected BiFunction<LayoutModel3D<V>, Integer, TemperatureModel> temperatureModelSupplier;
    protected final ToleranceDoubleComparator comparator;
    protected Function<V, Point3D> initializer;

    public FRLayoutAlgorithm3D() {
        this(DEFAULT_ITERATIONS, DEFAULT_NORMALIZATION_FACTOR, new Random(), ToleranceDoubleComparator.DEFAULT_EPSILON);
    }

    public FRLayoutAlgorithm3D(int iterations) {
        this(iterations, DEFAULT_NORMALIZATION_FACTOR, new Random(), ToleranceDoubleComparator.DEFAULT_EPSILON);
    }

    public FRLayoutAlgorithm3D(int iterations, double normalizationFactor, Random rng) {
        this(iterations, normalizationFactor, rng, ToleranceDoubleComparator.DEFAULT_EPSILON);
    }

    public FRLayoutAlgorithm3D(int iterations, double normalizationFactor, Random rng, double tolerance) {
        this(iterations, normalizationFactor, (model, totalIterations) -> {
            double dimension = model.getDrawableVolume().getWidth(); // assuming a cube
            return new InverseLinearTemperatureModel(-dimension / (10.0 * totalIterations), dimension / 10.0);
        }, rng, tolerance);
    }

    public FRLayoutAlgorithm3D(int iterations, double normalizationFactor,
                               BiFunction<LayoutModel3D<V>, Integer, TemperatureModel> temperatureModelSupplier,
                               Random rng, double tolerance) {
        this.iterations = iterations;
        this.normalizationFactor = normalizationFactor;
        this.rng = rng;
        this.temperatureModelSupplier = temperatureModelSupplier;
        this.comparator = new ToleranceDoubleComparator(tolerance);
    }

    public Function<V, Point3D> getInitializer() {
        return initializer;
    }

    public void setInitializer(Function<V, Point3D> initializer) {
        this.initializer = initializer;
    }

    protected void init(Graph<V, E> graph, LayoutModel3D<V> model) {
        if (initializer != null) {
            for (V v : graph.vertexSet()) {
                Point3D initPoint = initializer.apply(v);
                if (initPoint != null) {
                    model.put(v, initPoint);
                }
            }
        }
    }

    public void layout(Graph<V, E> graph, LayoutModel3D<V> model) {
        Box3D drawableVolume = model.getDrawableVolume();
        double minX = drawableVolume.getMinX();
        double minY = drawableVolume.getMinY();
        double minZ = drawableVolume.getMinZ();
        double width = drawableVolume.getWidth();

        if (initializer != null) {
            init(graph, model);
            for (V v : graph.vertexSet()) {
                if (model.get(v) == null) {
                    model.put(v, Point3D.of(minX, minY, minZ));
                }
            }
        } else {
            // assign random initial positions within the cube [min, min+width]
            for (V v : graph.vertexSet()) {
                double x = minX + rng.nextDouble() * width;
                double y = minY + rng.nextDouble() * width;
                double z = minZ + rng.nextDouble() * width;
                model.put(v, Point3D.of(x, y, z));
            }
        }

        int n = graph.vertexSet().size();
        if (n == 0) return;
        double volume = width * width * width;
        optimalDistance = normalizationFactor * Math.cbrt(volume / n);

        TemperatureModel temperatureModel = temperatureModelSupplier.apply(model, iterations);

        for (int i = 0; i < iterations; i++) {
            Map<V, Point3D> repulsiveDisp = calculateRepulsiveForces(graph, model);
            Map<V, Point3D> attractiveDisp = calculateAttractiveForces(graph, model);
            double temp = temperatureModel.temperature(i, iterations);

            for (V v : graph.vertexSet()) {
                Point3D repDisp = repulsiveDisp.getOrDefault(v, Point3D.of(0d, 0d, 0d));
                Point3D attrDisp = attractiveDisp.getOrDefault(v, Point3D.of(0d, 0d, 0d));
                Point3D vDisp = Points3D.add(repDisp, attrDisp);

                if (Points3D.length(vDisp) > 0) {
                    double vDispLen = Points3D.length(vDisp);
                    double factor = Math.min(vDispLen, temp) / vDispLen;
                    Point3D displacement = Points3D.scalarMultiply(vDisp, factor);
                    Point3D current = model.get(v);
                    Point3D newPos = Points3D.add(current, displacement);
                    //double clampedX = Math.min(minX + width, Math.max(minX, newPos.getX()));
                    //double clampedY = Math.min(minY + width, Math.max(minY, newPos.getY()));
                    //double clampedZ = Math.min(minZ + width, Math.max(minZ, newPos.getZ()));
                    model.put(v, Point3D.of(newPos.getX(), newPos.getY(), newPos.getZ()));
                }
            }
        }
    }

    protected double attractiveForce(double distance) {
        return (distance * distance) / optimalDistance;
    }

    protected double repulsiveForce(double distance) {
        return (optimalDistance * optimalDistance) / distance;
    }

    protected Map<V, Point3D> calculateRepulsiveForces(Graph<V, E> graph, LayoutModel3D<V> model) {
        Box3D drawableVolume = model.getDrawableVolume();
        double minX = drawableVolume.getMinX();
        double minY = drawableVolume.getMinY();
        double minZ = drawableVolume.getMinZ();
        Point3D origin = Point3D.of(minX, minY, minZ);
        Map<V, Point3D> disp = new HashMap<>();
        for (V v : graph.vertexSet()) {
            Point3D vPos = Points3D.subtract(model.get(v), origin);
            Point3D vDisp = Point3D.of(0d, 0d, 0d);
            for (V u : graph.vertexSet()) {
                if (v.equals(u))
                    continue;
                Point3D uPos = Points3D.subtract(model.get(u), origin);
                if (comparator.compare(vPos.getX(), uPos.getX()) != 0 ||
                    comparator.compare(vPos.getY(), uPos.getY()) != 0 ||
                    comparator.compare(vPos.getZ(), uPos.getZ()) != 0) {
                    Point3D delta = Points3D.subtract(vPos, uPos);
                    double deltaLen = Points3D.length(delta);
                    if (deltaLen > 0) {
                        Point3D dispContribution = Points3D.scalarMultiply(delta, repulsiveForce(deltaLen) / deltaLen);
                        vDisp = Points3D.add(vDisp, dispContribution);
                    }
                }
            }
            disp.put(v, vDisp);
        }
        return disp;
    }

    protected Map<V, Point3D> calculateAttractiveForces(Graph<V, E> graph, LayoutModel3D<V> model) {
        Box3D drawableVolume = model.getDrawableVolume();
        double minX = drawableVolume.getMinX();
        double minY = drawableVolume.getMinY();
        double minZ = drawableVolume.getMinZ();
        Point3D origin = Point3D.of(minX, minY, minZ);
        Map<V, Point3D> disp = new HashMap<>();
        for (E e : graph.edgeSet()) {
            V v = graph.getEdgeSource(e);
            V u = graph.getEdgeTarget(e);
            Point3D vPos = Points3D.subtract(model.get(v), origin);
            Point3D uPos = Points3D.subtract(model.get(u), origin);
            if (comparator.compare(vPos.getX(), uPos.getX()) != 0 ||
                comparator.compare(vPos.getY(), uPos.getY()) != 0 ||
                comparator.compare(vPos.getZ(), uPos.getZ()) != 0) {
                Point3D delta = Points3D.subtract(vPos, uPos);
                double deltaLen = Points3D.length(delta);
                if (deltaLen > 0) {
                    Point3D dispContribution = Points3D.scalarMultiply(delta, attractiveForce(deltaLen) / deltaLen);
                    Point3D curV = disp.getOrDefault(v, Point3D.of(0d, 0d, 0d));
                    Point3D curU = disp.getOrDefault(u, Point3D.of(0d, 0d, 0d));
                    // Subtract from v and add to u
                    disp.put(v, Points3D.add(curV, Points3D.scalarMultiply(dispContribution, -1)));
                    disp.put(u, Points3D.add(curU, dispContribution));
                }
            }
        }
        return disp;
    }

    public static interface TemperatureModel {
        double temperature(int iteration, int maxIterations);
    }

    protected static class InverseLinearTemperatureModel implements TemperatureModel {
        private double a;
        private double b;

        public InverseLinearTemperatureModel(double a, double b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public double temperature(int iteration, int maxIterations) {
            if (iteration >= maxIterations - 1) {
                return 0.0;
            }
            return a * iteration + b;
        }
    }
}