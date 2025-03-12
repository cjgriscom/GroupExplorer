package io.chandler.gap.alg.drawing;

import java.util.*;
import java.util.Map.*;

/**
 * A layout model which uses a hashtable to store the vertices' locations.
 * 
 * @author Dimitrios Michail
 *
 * @param <V> the vertex type
 */
public class MapLayoutModel3D<V>
    implements LayoutModel3D<V>
{
    protected Box3D drawableArea;
    protected Map<V, Point3D> points;
    protected Set<V> fixed;

    /**
     * Create a new model.
     * 
     * @param drawableArea the drawable area
     */
    public MapLayoutModel3D(Box3D drawableArea)
    {
        this.drawableArea = drawableArea;
        this.points = new LinkedHashMap<>();
        this.fixed = new HashSet<>();
    }

    @Override
    public Box3D getDrawableVolume()
    {
        return drawableArea;
    }

    @Override
    public void setDrawableVolume(Box3D drawableVolume)
    {
        this.drawableArea = drawableVolume;
    }

    @Override
    public Iterator<Entry<V, Point3D>> iterator()
    {
        return points.entrySet().iterator();
    }

    @Override
    public Point3D get(V vertex)
    {
        return points.get(vertex);
    }

    @Override
    public Point3D put(V vertex, Point3D point)
    {
        boolean isFixed = fixed.contains(vertex);
        if (!isFixed) {
            return points.put(vertex, point);
        }
        return points.putIfAbsent(vertex, point);
    }

    @Override
    public void setFixed(V vertex, boolean fixed)
    {
        if (fixed) {
            this.fixed.add(vertex);
        } else {
            this.fixed.remove(vertex);
        }
    }

    @Override
    public boolean isFixed(V vertex)
    {
        return fixed.contains(vertex);
    }

}
