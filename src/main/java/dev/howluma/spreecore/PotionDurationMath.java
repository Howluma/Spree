package dev.howluma.spreecore;

import org.bukkit.event.entity.EntityPotionEffectEvent;

/** Pure calculations and source policy for the minor Alchemy potion Perk. */
public final class PotionDurationMath {
    private PotionDurationMath() {}

    public static boolean isPotionSource(EntityPotionEffectEvent.Cause cause) {
        return switch (cause) {
            case POTION_DRINK, POTION_SPLASH, AREA_EFFECT_CLOUD, ARROW, FOOD -> true;
            default -> false; // Never extend renewable buffs or other plugins.
        };
    }

    /** Additional ticks beyond the natural effect duration, capped to avoid overflow. */
    public static int extraTicks(int baseDurationTicks, double bonusFraction) {
        if (baseDurationTicks <= 0 || !Double.isFinite(bonusFraction) || bonusFraction <= 0) return 0;
        double safeFraction = Math.min(.30, bonusFraction);
        return (int)Math.min(Integer.MAX_VALUE - 1L - baseDurationTicks,
                Math.max(0, Math.round(baseDurationTicks * safeFraction)));
    }
}
