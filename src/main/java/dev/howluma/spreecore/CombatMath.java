package dev.howluma.spreecore;

/** Small, separately testable damage and attribution rules. */
public final class CombatMath {
    private CombatMath() {}

    public static double scaleDamage(double damage, double multiplier) {
        if (!Double.isFinite(damage) || !Double.isFinite(multiplier) || damage < 0 || multiplier < 0)
            throw new IllegalArgumentException("Damage and multiplier must be finite and non-negative");
        return damage * multiplier;
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
