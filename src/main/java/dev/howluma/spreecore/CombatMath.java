package dev.howluma.spreecore;

/** Small, separately testable damage and attribution rules. */
public final class CombatMath {
    private CombatMath() {}

    public static double scaleDamage(double damage, double multiplier) {
        if (!Double.isFinite(damage) || !Double.isFinite(multiplier) || damage < 0 || multiplier < 0)
            throw new IllegalArgumentException("Damage and multiplier must be finite and non-negative");
        return damage * multiplier;
    }

    public static boolean recent(long nowMillis, long recordedMillis, long windowSeconds) {
        return recordedMillis > 0 && nowMillis >= recordedMillis
                && nowMillis - recordedMillis <= Math.max(0, windowSeconds) * 1000L;
    }
}
