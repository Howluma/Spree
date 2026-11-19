package dev.howluma.spreecore;

import java.util.Arrays;

/** Safe, pure arithmetic for temporary stack limits and lossless splits. */
public final class CapacityMath {
    private CapacityMath() {}
    public static int bonus(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(32, (int)Math.floor(value + 1e-9))) : 0;
    }
    public static int limit(int base, int added) {
        return base <= 1 ? base : Math.max(base, Math.min(99, base + Math.max(0, added)));
    }
    public static int[] split(int amount, int max) {
        if (amount <= 0) return new int[0];
        max = Math.max(1, Math.min(99, max));
        int full = amount / max, remainder = amount % max;
        int[] parts = new int[full + (remainder == 0 ? 0 : 1)];
        Arrays.fill(parts, 0, full, max);
        if (remainder > 0) parts[full] = remainder;
        return parts;
    }
}