package dev.howluma.spreecore;

import java.util.*;

/** Pure calculations and selection logic for the five-level Perk cycle. */
public final class PerkMath {
    private PerkMath() {}

    /** Perks occupy the two slots either side of each Talent: 2,4,7,9... */
    public static boolean isPerkMilestone(int level) {
        return level > 0 && (level % 5 == 2 || level % 5 == 4);
    }

    public static int copies(Map<Integer, String> history, PerkType type, int currentLevel) {
        int count = 0;
        for (var e : history.entrySet())
            if (isPerkMilestone(e.getKey()) && e.getKey() <= currentLevel && e.getValue().equals(type.id))
                count++;
        return count;
    }

    /** Duplicate rolls are allowed. Fully capped types are not in the pool. */
    public static PerkType draw(Map<Integer, String> history, Collection<PerkType> allowed,
                                Map<PerkType, Integer> caps, Random random) {
        List<PerkType> possible = new ArrayList<>();
        for (PerkType type : allowed) {
            int cap = Math.max(0, caps.getOrDefault(type, type.defaultMaxCopies));
            if (copies(history, type, Integer.MAX_VALUE) < cap) possible.add(type);
        }
        return possible.isEmpty() ? null : possible.get(random.nextInt(possible.size()));
    }

    /** Fractional durability/XP credits accumulate without random rolls or rounding loss. */
    public static double fractionalPart(double credit) {
        if (!Double.isFinite(credit) || credit < 0) return 0;
        return Math.max(0, credit - Math.floor(credit + 1e-9));
    }
}
