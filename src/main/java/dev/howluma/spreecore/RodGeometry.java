package dev.howluma.spreecore;

/**
 * Pure swept-point/axis-aligned-box intersection.
 * Returns the fraction (0 to 1) of the bobber's path at its first
 * intersection with the target's ORIGINAL hitbox plus a small margin.
 * Positive infinity means the path does not intersect.
 */
public final class RodGeometry {
    private RodGeometry() {}

    public static double boundedMargin(double requested) {
        if (!Double.isFinite(requested)) return 0;
        return Math.max(0, Math.min(.50, requested));
    }

    public static double boundedSweep(double requested) {
        if (!Double.isFinite(requested)) return 4.0;
        return Math.max(.5, Math.min(5.0, requested));
    }

    public static double firstContact(
            double ax, double ay, double az,
            double bx, double by, double bz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double margin) {
        margin = boundedMargin(margin);
        double[] p = {ax, ay, az};
        double[] d = {bx-ax, by-ay, bz-az};
        double[] min = {minX-margin, minY-margin, minZ-margin};
        double[] max = {maxX+margin, maxY+margin, maxZ+margin};
        double enter = 0, exit = 1;
        for (int axis=0; axis<3; axis++) {
            if (Math.abs(d[axis]) < 1e-10) {
                if (p[axis] < min[axis] || p[axis] > max[axis])
                    return Double.POSITIVE_INFINITY;
                continue;
            }
            double a=(min[axis]-p[axis])/d[axis];
            double b=(max[axis]-p[axis])/d[axis];
            if(a>b) {double temp=a; a=b; b=temp;}
            enter=Math.max(enter,a);
            exit=Math.min(exit,b);
            if(enter>exit) return Double.POSITIVE_INFINITY;
        }
        return enter >= 0 && enter <= 1 ? enter : Double.POSITIVE_INFINITY;
    }
}
