package io.chandler.gap.alg.drawing;

public class Points3D {

    public static Point3D subtract(Point3D a, Point3D b) {
        return Point3D.of(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static Point3D add(Point3D a, Point3D b) {
        return Point3D.of(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static Point3D scalarMultiply(Point3D a, double scalar) {
        return Point3D.of(a.getX() * scalar, a.getY() * scalar, a.getZ() * scalar);
    }

    public static double length(Point3D a) {
        return Math.sqrt(a.getX() * a.getX() + a.getY() * a.getY() + a.getZ() * a.getZ());
    }
} 