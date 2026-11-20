package dev.howluma.spreecore;

/**
 * True-damage adjustment after a real Bukkit damage event.
 * Vanilla damage first gives other plugins the chance to cancel damage and
 * applies absorption. Then this adds only the deficit caused by protection/
 * armor effects; never double-damages a victim or overrides a cancelled hit.
 */
final class BarbedHookMath {
    private BarbedHookMath() {}

    static double requestedPoints(double hearts) {
        if(!Double.isFinite(hearts) || hearts <= 0) return 0;
        return Math.min(40.0, hearts * 2.0);
    }

    static double appliedPoints(double beforeHealth, double afterHealth,
            double beforeAbsorption, double afterAbsorption) {
        return Math.max(0,beforeHealth-afterHealth)
                +Math.max(0,beforeAbsorption-afterAbsorption);
    }

    static double additionalPoints(double requested, double beforeHealth, double afterHealth,
            double beforeAbsorption, double afterAbsorption) {
        double actual=appliedPoints(beforeHealth,afterHealth,beforeAbsorption,afterAbsorption);
        // Cancelled/invulnerable event: do NOT bypass the protection by setHealth.
        if(actual <= 0.000001) return 0;
        return Math.max(0,requested-actual);
    }

    static boolean eligible(boolean pvpEnabled, boolean samePlayer,
            boolean blockedByFriendlyFire, boolean cancelled, boolean activeTalent) {
        return pvpEnabled && !samePlayer && !blockedByFriendlyFire
                && !cancelled && activeTalent;
    }
}
