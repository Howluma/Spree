package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * High-streak players become visible, valuable targets instead of receiving
 * unlimited extra progression. Scoreboard PvP teams are forbidden at level 20.
 */
public final class HuntManager implements Listener {
    private final SpreeCorePlugin plugin;
    private final Map<UUID, Long> nextCoordinates = new HashMap<>();
    private final Set<UUID> teamRestricted = new HashSet<>();

    HuntManager(SpreeCorePlugin plugin) { this.plugin = plugin; }

    private int level(String key, int fallback) {
        return Math.max(1, plugin.getConfig().getInt("hunt." + key, fallback));
    }

    void announceReached(Player player, int oldStreak, int newStreak) {
        if (newStreak <= oldStreak) return;
        for (int tier = 5; tier <= SpreeMath.benefitLevel(newStreak); tier += 5) {
            if (oldStreak >= tier) continue;
            String line = plugin.getConfig().getString("hunt.announcements." + tier);
            if (line != null && !line.isBlank())
                Bukkit.broadcastMessage(Text.c(line.replace("{player}", player.getName())
                        .replace("{streak}", Integer.toString(tier))));
        }
        // Ensure glowing and coordinate timers are available immediately.
        if (oldStreak < level("coordinates-level", 15)
                && newStreak >= level("coordinates-level", 15))
            nextCoordinates.put(player.getUniqueId(), System.currentTimeMillis() + intervalMillis());
    }

    private long intervalMillis() {
        int minutes = Math.max(1, Math.min(1440, plugin.getConfig().getInt("hunt.coordinate-interval-minutes", 15)));
        return minutes * 60_000L;
    }

    private void enforceNoTeams(Player player) {
        Set<Scoreboard> boards = Collections.newSetFromMap(new IdentityHashMap<>());
        if (Bukkit.getScoreboardManager() != null)
            boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) boards.add(viewer.getScoreboard());
        for (Scoreboard board : boards) {
            Team team = board.getEntryTeam(player.getName());
            if (team == null || team.getName().startsWith("spc_")) continue;
            team.removeEntry(player.getName());
        }
        if (teamRestricted.add(player.getUniqueId()))
            Text.send(player, plugin.prefix() + "&cAt " + level("no-teams-level", 20)
                    + "+ killstreak you cannot join scoreboard teams while wanted.");
    }

    /** Runs once per second; no inventory scans or heavy world searches. */
    public void tick() {
        long now = System.currentTimeMillis();
        long cadence = intervalMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            int streak = plugin.streaks().streak(player);
            if (streak >= level("no-teams-level", 20)) enforceNoTeams(player);
            else teamRestricted.remove(id);

            if (streak < level("coordinates-level", 15)) {
                nextCoordinates.remove(id);
                continue;
            }
            long next = nextCoordinates.computeIfAbsent(id, ignored -> now + cadence);
            if (now < next) continue;
            var at = player.getLocation();
            String world = player.getWorld().getName();
            Bukkit.broadcastMessage(Text.c("&9&lSPREE &8| &6WANTED &f" + player.getName()
                    + " &7located in &f" + world + " &7at &e"
                    + at.getBlockX() + "&7, &e" + at.getBlockY() + "&7, &e" + at.getBlockZ()));
            nextCoordinates.put(id, now + cadence);
        }
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        nextCoordinates.remove(id);
        teamRestricted.remove(id);
    }

    void shutdown() {
        nextCoordinates.clear();
        teamRestricted.clear();
    }
}
