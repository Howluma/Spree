package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.potion.PotionEffectType;

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
    private boolean freezeTabWhileInvisible() {
        return plugin.getConfig().getBoolean("display.streak-indicators.freeze-tab-while-invisible", true);
    }

    /** An invisible player's last published tab label stays unchanged, without freezing actual streak logic. */
    static boolean mayPublishTab(boolean hasInvisibilityEffect, boolean freezeWhileInvisible) {
        return !freezeWhileInvisible || !hasInvisibilityEffect;
    }

    private boolean glowMilestone(int streak) {
        return MilestoneEffects.unlocked(streak,
                plugin.getConfig().getInt("killstreak.milestones.glowing-level", 20));
    }

    /** Vanilla glowing outlines are colored via the scoreboard team's color. */
    static ChatColor glowColor(String decoratedColor) {
        if (decoratedColor == null) return ChatColor.WHITE;
        if (decoratedColor.length() >= 2 && decoratedColor.charAt(0) == '&') {
            ChatColor named = ChatColor.getByChar(decoratedColor.charAt(1));
            if (named != null && named.isColor()) return named;
        }
        // Legacy scoreboard team colors are limited to 16 named colors.
        // If the displayed tier uses hex RGB, choose the closest vanilla color.
        if (decoratedColor.startsWith("<#") && decoratedColor.length() >= 9) {
            try {
                int rgb = Integer.parseInt(decoratedColor.substring(2, 8), 16);
                ChatColor[] palette = {
                        ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
                        ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
                        ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
                        ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
                };
                int[] colors = {
                        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
                        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
                        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
                        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
                };
                ChatColor best = ChatColor.WHITE;
                long bestDistance = Long.MAX_VALUE;
                for (int i = 0; i < colors.length; i++) {
                    int other = colors[i];
                    long dr = ((rgb >> 16) & 255) - ((other >> 16) & 255);
                    long dg = ((rgb >> 8) & 255) - ((other >> 8) & 255);
                    long db = (rgb & 255) - (other & 255);
                    long distance = dr * dr + dg * dg + db * db;
                    if (distance < bestDistance) { bestDistance = distance; best = palette[i]; }
                }
                return best;
            } catch (NumberFormatException ignored) { /* Keep the standard color. */ }
        }
        return ChatColor.WHITE;
    }

    /** Apply the player's own tab name and update every relevant viewer scoreboard. */
    public void refreshPlayer(Player player) {
        if (!player.isOnline()) return;
        if (tabEnabled()) {
            previousTabNames.putIfAbsent(player.getUniqueId(), player.getPlayerListName());
            // Freeze ONLY the tab display, not nametags, glowing, health, rewards,
            // milestone buffs, HUD, or internal /killstreak values. A player
            // who joins already invisible keeps their existing tab label.
            if (mayPublishTab(player.hasPotionEffect(PotionEffectType.INVISIBILITY), freezeTabWhileInvisible())) {
                int streak = plugin.streaks().streak(player);
                String display = Text.c(tabLabel(player.getName(), streak, symbol(), recoverySymbol(), streakColor(streak)));
                if (!display.equals(player.getPlayerListName())) player.setPlayerListName(display);
            }
        } else restoreTab(player);
        // Glow colors require a team even with the nametag indicator disabled.
        // Never steal a team owned by another plugin (friendly-fire/collision).
        if (tagEnabled() || glowMilestone(plugin.streaks().streak(player))) {
            for (Scoreboard scoreboard : visibleScoreboards()) updateTag(scoreboard, player);
        } else clearTags(player.getName());
    }
    public void refreshOnline() {
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
        String suffix = tagEnabled()
                ? Text.c(nametagSuffix(streak, symbol(), recoverySymbol(), streakColor(streak))) : "";
        if (!suffix.equals(team.getSuffix())) team.setSuffix(suffix);
        ChatColor color = glowMilestone(streak) ? glowColor(streakColor(streak)) : ChatColor.WHITE;
        if (team.getColor() != color) team.setColor(color);
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
    /**
     * Refresh one tick after potion changes so we see the final effect state.
     * Expiration, milk, commands and other removal causes all unfreeze the tab
     * automatically. Refreshing on application is safe: the invisible guard
     * retains the previously published streak number.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void invisibilityChanged(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getModifiedType() != PotionEffectType.INVISIBILITY) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) refreshPlayer(player);
        });
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
