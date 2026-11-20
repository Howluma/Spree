package dev.howluma.spreecore;

import java.util.function.DoubleUnaryOperator;

/** Small, separately testable damage and attribution rules. */
public final class CombatMath {
    private CombatMath() {}

    public static double scaleDamage(double damage, double multiplier) {
        if (!Double.isFinite(damage) || !Double.isFinite(multiplier) || damage < 0 || multiplier < 0)
            throw new IllegalArgumentException("Damage and multiplier must be finite and non-negative");
        return damage * multiplier;
    }

    /** Add percentage points per victim streak level. Fractured counts as zero. */
    public static double streakDamageMultiplier(double base, int victimStreak, double bonusPerStreak) {
        if (!Double.isFinite(base) || !Double.isFinite(bonusPerStreak)
                || base < 0 || bonusPerStreak < 0)
            throw new IllegalArgumentException("Multipliers must be finite and non-negative");
        return base + (double) Math.max(0, victimStreak) * bonusPerStreak;
    }

    /** Convert the minecart cap from displayed hearts to health points. */
    public static double minecartCapHealthPoints(int victimStreak, double baseHearts, double heartsPerStreak) {
        if (!Double.isFinite(baseHearts) || !Double.isFinite(heartsPerStreak)
                || baseHearts < 0 || heartsPerStreak < 0)
            throw new IllegalArgumentException("Heart caps must be finite and non-negative");
        return 2.0 * (baseHearts + (double) Math.max(0, victimStreak) * heartsPerStreak);
    }

    /** Search raw damage to enforce a FINAL after-mitigation damage limit.
     * Damage calculations are monotonic for vanilla armor and resistance.
     */
    public static double capRawDamage(double rawDamage, double maxFinalDamage,
                                       DoubleUnaryOperator finalDamageAtRaw) {
        if (!Double.isFinite(rawDamage) || !Double.isFinite(maxFinalDamage)
                || rawDamage < 0 || maxFinalDamage < 0 || finalDamageAtRaw == null)
            throw new IllegalArgumentException("Damage caps must be finite and non-negative");
        if (rawDamage == 0) return 0;
        if (finalDamageAtRaw.applyAsDouble(rawDamage) <= maxFinalDamage) return rawDamage;
        double low = 0.0, high = rawDamage;
        for (int i = 0; i < 28; i++) {
            double mid = low + (high - low) * 0.5;
            double result = finalDamageAtRaw.applyAsDouble(mid);
            if (!Double.isFinite(result))
                throw new IllegalArgumentException("Final damage must be finite");
            if (result > maxFinalDamage) high = mid;
            else low = mid;
        }
        return low;
    }

    /** Environmental finishing blows that can legitimately inherit a recent PvP hit.
     * Explicit /kill, suicides, projectiles and direct mob/player attacks are excluded.
     */
    public static boolean environmentalCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if(cause==null)return false;
        return switch(cause) {
            case FALL, VOID, FIRE, FIRE_TICK, LAVA, DROWNING, SUFFOCATION,
                    CONTACT, CAMPFIRE, HOT_FLOOR, FREEZE, CRAMMING, BLOCK_EXPLOSION,
                    LIGHTNING, MAGIC, POISON, WITHER, STARVATION, FLY_INTO_WALL,
                    FALLING_BLOCK, WORLD_BORDER, DRYOUT, MELTING -> true;
            default -> false;
        };
    }

    public static boolean creditedEnvironmentalDeath(
            org.bukkit.event.entity.EntityDamageEvent.DamageCause cause,
            long nowMillis, long hitMillis, long creditWindowSeconds) {
        return environmentalCause(cause) && recent(nowMillis,hitMillis,creditWindowSeconds);
    }

    public static boolean recent(long nowMillis, long recordedMillis, long windowSeconds) {
        return recordedMillis > 0 && nowMillis >= recordedMillis
                && nowMillis - recordedMillis <= Math.max(0, windowSeconds) * 1000L;
    }
}
