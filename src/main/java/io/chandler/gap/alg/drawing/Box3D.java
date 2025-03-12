package io.chandler.gap.alg.drawing;

public class Box3D {
    private double minX;
    private double minY;
    private double minZ;
    private double width, height, depth;
    
    public Box3D(double minX, double minY, double minZ, double width, double height, double depth) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }
    
    public double getMinX() {
        return minX;
    }
    
    public double getMinY() {
        return minY;
    }
    
    public double getMinZ() {
        return minZ;
    }
    
    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double getDepth() {
        return depth;
    }
    
} 