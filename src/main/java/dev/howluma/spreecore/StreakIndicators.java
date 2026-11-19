package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * Compact tab-list and overhead killstreak indicators, with vanilla-compatible scoreboard teams.
 * Never moves a player out of a foreign scoreboard team: that would change PvP/friendly-fire rules.
 */
public final class StreakIndicators implements Listener {
    private static final String TEAM_PREFIX = "spc_";
    /** Match the action-bar icon color across all streak levels. */
    static final String ICON_COLOR = "&9";
    private final SpreeCorePlugin plugin;
    private final Set<Scoreboard> touched = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UUID, String> previousTabNames = new HashMap<>();

    StreakIndicators(SpreeCorePlugin plugin) { this.plugin = plugin; }

    /** Original Spree SMP colors, changing at every five kills through the default 30 cap. */
    static String color(int streak) {
        if (streak < 0) return "&c&l";
        if (streak >= 30) return "&4&l";
        if (streak >= 25) return "&5&l";
        if (streak >= 20) return "&c&l";
        if (streak >= 15) return "&6&l";
        if (streak >= 10) return "&e&l";
        if (streak >= 5) return "&a&l";
        return "&7&l";
    }
    /** Admin-configured colors always retain bold numbers and the distinct Fractured status. */
    public String streakColor(int streak) {
        if (streak < 0) return "&c&l";
        int tier = Math.min(30, (streak / 5) * 5);
        String custom = plugin.getConfig().getString("display.streak-indicators.tier-colors." + tier);
        if (custom == null || !custom.matches("(?i)(?:&[0-9a-f]|<#[0-9a-f]{6}>)")) return color(streak);
        return custom + "&l";
    }
    static String tabLabel(String name, int streak, String symbol, String fracturedSymbol) {
        return tabLabel(name, streak, symbol, fracturedSymbol, color(streak));
    }
    static String tabLabel(String name, int streak, String symbol, String fracturedSymbol, String numberColor) {
        return "&f" + name + " &8| " + ICON_COLOR + symbol + " " + numberColor
                + (streak < 0 ? fracturedSymbol : Integer.toString(streak));
    }
    static String nametagSuffix(int streak, String symbol, String fracturedSymbol) {
        return nametagSuffix(streak, symbol, fracturedSymbol, color(streak));
    }
    static String nametagSuffix(int streak, String symbol, String fracturedSymbol, String numberColor) {
        return " " + ICON_COLOR + symbol + " " + numberColor
                + (streak < 0 ? fracturedSymbol : Integer.toString(streak));
    }
    private String safeSymbol(String key, String fallback) {
        String raw = plugin.getConfig().getString(key, fallback);
        if (raw == null || raw.isBlank()) return fallback;
        // A visual indicator, not a MiniMessage/chat-format expression.
        String plain = ChatColor.stripColor(Text.c(raw.replace('&', ' ')));
        return plain.length() > 5 ? plain.substring(0, 5) : plain;
    }
    private String symbol() { return safeSymbol("display.streak-indicators.symbol", "⚔"); }
    private String recoverySymbol() { return safeSymbol("display.streak-indicators.fractured-symbol", "✕"); }
    private boolean tabEnabled() { return plugin.getConfig().getBoolean("display.streak-indicators.tab-enabled", true); }
    private boolean tagEnabled() { return plugin.getConfig().getBoolean("display.streak-indicators.nametag-enabled", true); }

    /** Apply the player's own tab name and update every relevant viewer scoreboard. */
    public void refreshPlayer(Player player) {
        if (!player.isOnline()) return;
        if (tabEnabled()) {
            previousTabNames.putIfAbsent(player.getUniqueId(), player.getPlayerListName());
            int streak = plugin.streaks().streak(player);
            String display = Text.c(tabLabel(player.getName(), streak, symbol(), recoverySymbol(), streakColor(streak)));
            if (!display.equals(player.getPlayerListName())) player.setPlayerListName(display);
        } else restoreTab(player);
        // Each viewer may have a different scoreboard. Deduplicate identical scoreboards.
        if (tagEnabled()) for (Scoreboard scoreboard : visibleScoreboards()) updateTag(scoreboard, player);
        else clearTags(player.getName());
    }
    public void refreshOnline() {
        if (!tagEnabled()) removeAllTags();
        for (Player player : Bukkit.getOnlinePlayers()) refreshPlayer(player);
    }
    private Collection<Scoreboard> visibleScoreboards() {
        Set<Scoreboard> scoreboards = Collections.newSetFromMap(new IdentityHashMap<>());
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) scoreboards.add(manager.getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) scoreboards.add(viewer.getScoreboard());
        return scoreboards;
    }
    private String teamId(UUID uuid) { return TEAM_PREFIX + uuid.toString().replace("-", "").substring(0, 12); }
    private void updateTag(Scoreboard board, Player target) {
        String entry = target.getName();
        Team assigned = board.getEntryTeam(entry);
        String teamName = teamId(target.getUniqueId());
        if (assigned != null && !assigned.getName().equals(teamName)) {
            // A faction/party/other plugin already owns this player/team. Respect its rules.
            return;
        }
        Team team = board.getTeam(teamName);
        if (team == null) {
            if (assigned != null) return; // Never adopt a team created by another plugin.
            team = board.registerNewTeam(teamName);
            touched.add(board);
        } else if (!touched.contains(board)) {
            // Recover our own exact singleton team after an unclean server restart,
            // but never adopt a foreign shared team or change its collision/PvP rules.
            if (team.getEntries().size() != 1 || !team.hasEntry(entry)) return;
            touched.add(board);
        }
        if (!team.hasEntry(entry)) team.addEntry(entry);
        int streak = plugin.streaks().streak(target);
        String suffix = Text.c(nametagSuffix(streak, symbol(), recoverySymbol(), streakColor(streak)));
        if (!suffix.equals(team.getSuffix())) team.setSuffix(suffix);
    }
    private void clearTags(String entry) {
        for (Scoreboard board : List.copyOf(touched)) {
            Team current = board.getEntryTeam(entry);
            if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
                current.removeEntry(entry);
                if (current.getEntries().isEmpty()) current.unregister();
            }
        }
    }
    private void removeAllTags() {
        for (Scoreboard board : List.copyOf(touched)) {
            for (Team team : List.copyOf(board.getTeams())) {
                if (team.getName().startsWith(TEAM_PREFIX)) team.unregister();
            }
        }
        touched.clear();
    }
    private void restoreTab(Player player) {
        String original = previousTabNames.remove(player.getUniqueId());
        if (original != null && player.isOnline()) player.setPlayerListName(original);
    }
    public void shutdown() {
        removeAllTags();
        for (Player player : Bukkit.getOnlinePlayers()) restoreTab(player);
        previousTabNames.clear();
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        // Newly joined players may be assigned a third-party scoreboard just after join.
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshOnline, 5L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        clearTags(event.getPlayer().getName());
        previousTabNames.remove(event.getPlayer().getUniqueId());
    }
}
