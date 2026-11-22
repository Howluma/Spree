package dev.howluma.spreecore;

/** Pure, deterministic rules so critical streak calculations can be unit tested. */
public final class SpreeMath {
    private SpreeMath() {}
    /** Only the first 30 levels grant benefits; visible streaks may go higher. */
    public static final int BENEFIT_CAP = 30;
    public static int benefitLevel(int streak) {
        return Math.max(0, Math.min(BENEFIT_CAP, streak));
    }
    public static int clamp(int streak, int limit) { return Math.max(-1, Math.min(limit, streak)); }
    /**
     * PvP kill reward from the victim's PRE-DEATH streak.
     * By default: 0-4 => +1, 5-9 => +2, ... 25-29 => +6, 30 => +7.
     * One reward rule applies to direct, projectile and credited explosion
     * kills AND to environmental-death token amounts. The death penalty itself
     * still halves the victim's existing streak.
     */
    public static int killGain(int victimStreak) {
        return killGain(victimStreak, 1, 5, 1);
    }
    /** Configurable base reward + one increment per threshold interval. */
    public static int killGain(int victimStreak, int base, int levelsPerTier, int extraPerTier) {
        int initial = Math.max(1, base);
        int interval = Math.max(1, levelsPerTier);
        int additional = Math.max(0, extraPerTier);
        long total = (long) initial + ((long) benefitLevel(victimStreak) / interval) * additional;
        return (int) Math.min(Integer.MAX_VALUE, total);
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
        double hearts = baseHearts + benefitLevel(streak) * perLevelHearts;
        return Math.min(1024.0, Math.max(2.0, hearts * 2.0));
    }
    /** The vanilla mace works only after the first positive killstreak level. */
    public static boolean canUseMace(int streak, int requiredLevel) {
        return streak >= Math.max(1, requiredLevel);
    }
    /** All withdrawals are a one-for-one exchange of positive streak levels. */
    public static int withdrawRemaining(int streak, int amount) {
        if (streak <= 0 || amount <= 0 || amount > streak)
            throw new IllegalArgumentException("Invalid Spree withdrawal.");
        return streak - amount;
    }
    public static int applyKill(int current, int gain, int cap) {
        return (int) Math.min(Math.max(0, cap), Math.max(0L, (long) current) + Math.max(0L, (long) gain));
    }
}
