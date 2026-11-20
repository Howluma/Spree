package dev.howluma.spreecore;

/**
 * Plain eligibility and duration rules for applying Flame Rod fire to a
 * player when the burning bobber actually attaches, not when it is cast.
 */
final class FlameRodRules {
    private FlameRodRules() {}

    static boolean canIgnitePlayer(boolean activeCast, boolean allowedPvp,
            boolean samePlayer, boolean allyWithFriendlyFireOff,
            boolean spectator, boolean alreadyIgnitedThisCast) {
        return activeCast && allowedPvp && !samePlayer
                && !allyWithFriendlyFireOff && !spectator
                && !alreadyIgnitedThisCast;
    }

    static int fireTicks(double seconds) {
        if (!Double.isFinite(seconds)) return 0;
        // No more than 15 seconds from a single fishing bobber hit.
        double safe=Math.max(0,Math.min(15,seconds));
        return (int)Math.ceil(safe*20);
    }
}
