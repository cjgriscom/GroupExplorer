package io.chandler.gap.graph.flatten;

public final class GridGeometry {
    public static final int MAX_CHEB = 5;
    public static final long[] LENGTH_PENALTY = {Long.MAX_VALUE, 0, 4, 10, 20, 35};
    public static final long DIAG_UNIT_PENALTY = 1;
    public static final long ANGLE_PENALTY = 12;
    public static final long JUNCTION_BASE_PENALTY = 3;
    public static final long JUNCTION_EXTRA_EDGE_PENALTY = 3;

    public static final int DISJOINT = 0;
    public static final int SHARED_ENDPOINT_ONLY = 1;
    public static final int CROSS = 2;
    public static final int OVERLAP = 3;

    private GridGeometry() {
    }

    public static long edgePenalty(int dx, int dy) {
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);
        int cheb = Math.max(adx, ady);
        if (cheb == 0 || cheb > MAX_CHEB) {
            throw new IllegalArgumentException("Chebyshev distance out of range: " + cheb);
        }
        long penalty = LENGTH_PENALTY[cheb];
        if (adx == 1 && ady == 1) {
            penalty += DIAG_UNIT_PENALTY;
        }
        int min = Math.min(adx, ady);
        if (min != 0 && adx != ady) {
            penalty += ANGLE_PENALTY;
        }
        return penalty;
    }

    public static boolean isAllowedDisplacement(int dx, int dy) {
        int cheb = Math.max(Math.abs(dx), Math.abs(dy));
        return cheb >= 1 && cheb <= MAX_CHEB;
    }

    public static int[][] interiorLatticePoints(int dx, int dy) {
        int g = gcd(Math.abs(dx), Math.abs(dy));
        if (g <= 1) {
            return new int[0][];
        }
        int[][] points = new int[g - 1][2];
        for (int i = 1; i < g; i++) {
            points[i - 1][0] = i * dx / g;
            points[i - 1][1] = i * dy / g;
        }
        return points;
    }

    public static int segRelation(int ax, int ay, int bx, int by, int cx, int cy, int dx2, int dy2) {
        if (ax == bx && ay == by || cx == dx2 && cy == dy2) {
            return pointsEqual(ax, ay, cx, cy) ? SHARED_ENDPOINT_ONLY : DISJOINT;
        }

        long abx = bx - ax;
        long aby = by - ay;
        long cdx = dx2 - cx;
        long cdy = dy2 - cy;
        long acx = cx - ax;
        long acy = cy - ay;

        long crossAB_AC = abx * (cy - ay) - aby * (cx - ax);
        long crossAB_AD = abx * (dy2 - ay) - aby * (dx2 - ax);
        long crossCD_CA = cdx * (ay - cy) - cdy * (ax - cx);
        long crossCD_CB = cdx * (by - cy) - cdy * (bx - cx);

        if (crossAB_AC == 0 && crossAB_AD == 0 && crossCD_CA == 0 && crossCD_CB == 0) {
            return collinearRelation(ax, ay, bx, by, cx, cy, dx2, dy2);
        }

        if (pointOnSegmentInterior(cx, cy, ax, ay, bx, by)
                || pointOnSegmentInterior(dx2, dy2, ax, ay, bx, by)
                || pointOnSegmentInterior(ax, ay, cx, cy, dx2, dy2)
                || pointOnSegmentInterior(bx, by, cx, cy, dx2, dy2)) {
            return CROSS;
        }

        if (sharedEndpointCount(ax, ay, bx, by, cx, cy, dx2, dy2) == 1) {
            return SHARED_ENDPOINT_ONLY;
        }

        long denom = abx * cdy - aby * cdx;
        if (denom != 0) {
            long tNum = acx * cdy - acy * cdx;
            long uNum = acx * aby - acy * abx;
            if (paramInOpenUnitInterval(tNum, denom) && paramInOpenUnitInterval(uNum, denom)) {
                return CROSS;
            }
        }

        return DISJOINT;
    }

    public static long[] midpoint2(int ax, int ay, int bx, int by) {
        return new long[] { (long) ax + bx, (long) ay + by };
    }

    private static int gcd(int a, int b) {
        if (a == 0) {
            return b;
        }
        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }

    private static boolean paramInOpenUnitInterval(long num, long denom) {
        if (denom > 0) {
            return num > 0 && num < denom;
        }
        if (denom < 0) {
            return num < 0 && num > denom;
        }
        return false;
    }

    private static boolean pointsEqual(int x1, int y1, int x2, int y2) {
        return x1 == x2 && y1 == y2;
    }

    private static int sharedEndpointCount(int ax, int ay, int bx, int by, int cx, int cy, int dx2, int dy2) {
        int count = 0;
        if (pointsEqual(ax, ay, cx, cy)) {
            count++;
        }
        if (pointsEqual(ax, ay, dx2, dy2)) {
            count++;
        }
        if (pointsEqual(bx, by, cx, cy)) {
            count++;
        }
        if (pointsEqual(bx, by, dx2, dy2)) {
            count++;
        }
        return count;
    }

    private static boolean pointOnSegmentInterior(int px, int py, int ax, int ay, int bx, int by) {
        if (pointsEqual(px, py, ax, ay) || pointsEqual(px, py, bx, by)) {
            return false;
        }
        long cross = (long) (bx - ax) * (py - ay) - (long) (by - ay) * (px - ax);
        if (cross != 0) {
            return false;
        }
        long dot = (long) (px - ax) * (bx - ax) + (long) (py - ay) * (by - ay);
        if (dot <= 0) {
            return false;
        }
        long lenSq = (long) (bx - ax) * (bx - ax) + (long) (by - ay) * (by - ay);
        return dot < lenSq;
    }

    private static int collinearRelation(int ax, int ay, int bx, int by, int cx, int cy, int dx2, int dy2) {
        if (Math.abs(bx - ax) >= Math.abs(by - ay)) {
            return collinearRelation1D(ax, bx, cx, dx2);
        }
        return collinearRelation1D(ay, by, cy, dy2);
    }

    private static int collinearRelation1D(int a, int b, int c, int d) {
        int aMin = Math.min(a, b);
        int aMax = Math.max(a, b);
        int cMin = Math.min(c, d);
        int cMax = Math.max(c, d);

        int overlapStart = Math.max(aMin, cMin);
        int overlapEnd = Math.min(aMax, cMax);

        if (overlapStart > overlapEnd) {
            return DISJOINT;
        }
        if (overlapStart == overlapEnd) {
            return SHARED_ENDPOINT_ONLY;
        }
        return OVERLAP;
    }
}
