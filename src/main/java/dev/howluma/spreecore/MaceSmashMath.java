package dev.howluma.spreecore;

/** Pure rule: only actual unblocked damage from a mace slam spends a cooldown. */
public final class MaceSmashMath {
    private MaceSmashMath() {}
    public static boolean connects(boolean maceSmashDamageType, boolean cancelled,
                                   double finalDamage, boolean blockedByShield) {
        return maceSmashDamageType && !cancelled && !blockedByShield
                && Double.isFinite(finalDamage) && finalDamage > 0;
    }
}
