package io.chandler.gap.alg.drawing;

import java.util.Iterator;
import java.util.Map;
import java.util.LinkedHashMap;

public interface LayoutModel3D<V> extends Iterable<Map.Entry<V, Point3D>> {

    // Returns the drawable volume of the model as a Box3D
    Box3D getDrawableVolume();

    // Sets the drawable volume of the model
    void setDrawableVolume(Box3D drawableVolume);

    // Returns the 3D point associated with the given vertex
    Point3D get(V vertex);

    // Sets the 3D point for the given vertex and returns the previous point or null if none
    Point3D put(V vertex, Point3D point);

    // Sets whether the given vertex is fixed
    void setFixed(V vertex, boolean fixed);

    // Checks if the given vertex is fixed
    boolean isFixed(V vertex);

    // Collects a map of all vertex locations
    default Map<V, Point3D> collect() {
        Map<V, Point3D> map = new LinkedHashMap<>();
        for (Map.Entry<V, Point3D> entry : this) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    // Returns an iterator over the vertex and its associated 3D point
    Iterator<Map.Entry<V, Point3D>> iterator();
} 