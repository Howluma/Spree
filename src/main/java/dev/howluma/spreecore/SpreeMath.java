package dev.howluma.spreecore;

/** Pure, deterministic rules so critical streak calculations can be unit tested. */
public final class SpreeMath {
    private SpreeMath() {}
    public static int clamp(int streak, int limit) { return Math.max(-1, Math.min(limit, streak)); }
    /**
     * Credit a killer with half of the victim's pre-death killstreak
     * (rounded down), with a minimum of one for low/Fractured victims.
     * This rule is shared by melee/projectile/explosive credited kills
     * and death tokens; there is no legacy +2 reward cap.
     */
    public static int killGain(int victimStreak) {
        return Math.max(1, Math.max(0, victimStreak) / 2);
    }
    public static int deathStreak(int oldStreak) {
        return oldStreak < 0 ? -1 : oldStreak / 2;
    }
    public static int naturalDeathStreak(int oldStreak) {
        return oldStreak <= 0 ? -1 : oldStreak / 2;
    }
    /** One killstreak level grants perLevelHearts, including fractional hearts.
     * Fractured (-1) is exactly the same health as zero, never a penalty.
     * The upper bound keeps the Minecraft max-health attribute in a valid range.
     */
    public static double maxHealthPoints(int streak, double baseHearts, double perLevelHearts) {
        double hearts = baseHearts + Math.max(0, streak) * perLevelHearts;
        return Math.min(1024.0, Math.max(2.0, hearts * 2.0));
    }
    /** The vanilla mace works only after the first positive killstreak level. */
    public static boolean canUseMace(int streak, int requiredLevel) {
        return streak >= Math.max(1, requiredLevel);
    }
    public static int applyKill(int current, int gain, int cap) {
        return Math.min(cap, Math.max(0, current) + Math.max(0, gain));
    }
}
