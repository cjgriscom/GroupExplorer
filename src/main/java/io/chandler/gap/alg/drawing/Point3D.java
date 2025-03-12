package io.chandler.gap.alg.drawing;

public class Point3D {
    private final double x;
    private final double y;
    private final double z;

    private Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Point3D of(double x, double y, double z) {
        return new Point3D(x, y, z);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Override
    public String toString() {
        return "Point3D{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
} 