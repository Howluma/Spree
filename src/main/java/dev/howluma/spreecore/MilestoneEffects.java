package dev.howluma.spreecore;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Streak perks: weaving, Speed I, Strength II, and Glowing.
 * Renewable, short-lived effects act as permanent buffs while a milestone is
 * held, with no permanent effects left behind if the server crashes.
 * Stronger/independent effects from other plugins and legendary items win.
 */
final class MilestoneEffects {
    private static final int DURATION_TICKS = 260;
    private static final int REFRESH_AT_TICKS = 100;
    private final SpreeCorePlugin plugin;
    private final Map<UUID, Map<PotionEffectType, Integer>> owned = new HashMap<>();

    MilestoneEffects(SpreeCorePlugin plugin) { this.plugin = plugin; }

    /** Zero or Fractured can never gain a positive-streak perk. */
    static boolean unlocked(int streak, int level) {
        return streak >= Math.max(1, level);
    }

    void refresh(Player player, int streak, boolean eventGlow) {
        maintain(player, PotionEffectType.WEAVING, 0,
                unlocked(streak, plugin.getConfig().getInt("killstreak.milestones.weaving-level", 5)));
        maintain(player, PotionEffectType.SPEED, 0,
                unlocked(streak, plugin.getConfig().getInt("killstreak.milestones.speed-level", 10)));
        maintain(player, PotionEffectType.STRENGTH, 1,
                unlocked(streak, plugin.getConfig().getInt("killstreak.milestones.strength-level", 15)));
        maintain(player, PotionEffectType.GLOWING, 0,
                eventGlow || unlocked(streak, plugin.getConfig().getInt("killstreak.milestones.glowing-level", 20)));
    }

    private void maintain(Player player, PotionEffectType type, int amplifier, boolean enabled) {
        UUID id = player.getUniqueId();
        Map<PotionEffectType, Integer> perPlayer = owned.get(id);
        Integer previous = perPlayer == null ? null : perPlayer.get(type);
        PotionEffect current = player.getPotionEffect(type);

        if (!enabled) {
            if (previous != null && looksOwned(current, previous)) player.removePotionEffect(type);
            forget(id, type);
            return;
        }
        if (current != null) {
            if (current.getAmplifier() > amplifier) {
                // E.g. Blood Butcher Strength III or Aquafier Speed III.
                forget(id, type);
                return;
            }
            if (current.getAmplifier() == amplifier) {
                if (previous == null || !looksOwned(current, previous)) {
                    // Don't adopt a potion granted by another source.
                    forget(id, type);
                    return;
                }
                if (current.getDuration() > REFRESH_AT_TICKS) return;
            }
        }
        player.addPotionEffect(new PotionEffect(type, DURATION_TICKS, amplifier, true, false, false), true);
        owned.computeIfAbsent(id, ignored -> new HashMap<>()).put(type, amplifier);
    }

    private static boolean looksOwned(PotionEffect effect, int amplifier) {
        return effect != null && effect.getAmplifier() == amplifier
                && effect.isAmbient() && !effect.hasParticles() && !effect.hasIcon()
                && effect.getDuration() > 0 && effect.getDuration() <= DURATION_TICKS;
    }

    private void forget(UUID id, PotionEffectType type) {
        Map<PotionEffectType, Integer> perPlayer = owned.get(id);
        if (perPlayer == null) return;
        perPlayer.remove(type);
        if (perPlayer.isEmpty()) owned.remove(id);
    }

    void clear(Player player) {
        Map<PotionEffectType, Integer> perPlayer = owned.remove(player.getUniqueId());
        if (perPlayer == null) return;
        for (var entry : perPlayer.entrySet()) {
            if (looksOwned(player.getPotionEffect(entry.getKey()), entry.getValue()))
                player.removePotionEffect(entry.getKey());
        }
    }
}
