package dev.howluma.spreecore;

/** Pure rule: only actual unblocked damage from a mace slam spends a cooldown. */
public final class MaceSmashMath {
    private MaceSmashMath() {}
    /** Vanilla's shaded mace hotbar icon needs whole ticks (20 per second).
     * Round UP on reconnect so the overlay never ends before the saved timer.
     */
    public static int overlayTicks(long remainingMillis) {
        if (remainingMillis <= 0) return 0;
        long ticks = remainingMillis / 50L + (remainingMillis % 50L == 0 ? 0 : 1);
        return (int) Math.min(72_000L, ticks);
    }

    /** Check accepted smash candidates after all plugins have voted on the event. */
    public static boolean successfulAttempt(boolean originalResult, boolean allow, boolean deny) {
        return !deny && (allow || originalResult);
    }

    public static boolean connects(boolean maceSmashDamageType, boolean cancelled,
                                   double finalDamage, boolean blockedByShield) {
        return maceSmashDamageType && !cancelled && !blockedByShield
                && Double.isFinite(finalDamage) && finalDamage > 0;
    }
}
