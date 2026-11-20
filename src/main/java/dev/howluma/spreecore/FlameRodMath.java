package dev.howluma.spreecore;

/**
 * Tight fishing-hook/TNT proximity math. A flame rod ignites a block only
 * when the moving bobber reaches its collision volume, not when TNT happens
 * to be several blocks away.
 */
final class FlameRodMath {
    private FlameRodMath() {}

    static boolean touchesBlock(double px, double py, double pz,
            int bx, int by, int bz, double tolerance) {
        if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz)
                || !Double.isFinite(tolerance) || tolerance < 0) return false;
        double dx=Math.max(0,Math.max(bx-px,px-(bx+1)));
        double dy=Math.max(0,Math.max(by-py,py-(by+1)));
        double dz=Math.max(0,Math.max(bz-pz,pz-(bz+1)));
        return dx*dx+dy*dy+dz*dz<=tolerance*tolerance;
    }

    static int samples(double dx, double dy, double dz) {
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (!Double.isFinite(distance) || distance<0.0001) return 1;
        return Math.max(1,Math.min(32,(int)Math.ceil(distance/.35)));
    }
}
