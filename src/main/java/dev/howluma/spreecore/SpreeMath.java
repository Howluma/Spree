package dev.howluma.spreecore;

/** Pure, deterministic rules so critical streak calculations can be unit tested. */
public final class SpreeMath {
    private SpreeMath() {}
    public static int clamp(int streak, int limit) { return Math.max(-1, Math.min(limit, streak)); }
    public static int killGain(int victimStreak) {
        if (victimStreak >= 10) return 2;
        return Math.max(1, Math.min(2, Math.floorDiv(victimStreak, 2)));
    }
    public static int deathStreak(int oldStreak) {
        return oldStreak < 0 ? -1 : oldStreak / 2;
    }
    public static int naturalDeathStreak(int oldStreak) {
        return oldStreak <= 0 ? -1 : oldStreak / 2;
    }
    public static int healthHearts(int streak, int baseHearts, int maxHearts) {
        return Math.min(maxHearts, baseHearts + (streak >= 3 ? (streak - 1) / 2 : 0));
    }
    public static int applyKill(int current, int gain, int cap) {
        return Math.min(cap, Math.max(0, current) + Math.max(0, gain));
    }
}
