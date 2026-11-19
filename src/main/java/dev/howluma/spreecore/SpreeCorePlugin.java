package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public final class SpreeCorePlugin extends JavaPlugin {
    private PlayerDataStore data;
    private StreakManager streaks;
    private LegendaryManager legendaries;
    private ForgeManager forges;
    private EventManager events;
    private MenuManager menus;
    private CoreCommands commands;

    @Override public void onEnable() {
        saveDefaultConfig();
        data = new PlayerDataStore(this);
        legendaries = new LegendaryManager(this);
        streaks = new StreakManager(this);
        forges = new ForgeManager(this);
        events = new EventManager(this);
        menus = new MenuManager(this);
        commands = new CoreCommands(this);
        Bukkit.getPluginManager().registerEvents(streaks, this);
        Bukkit.getPluginManager().registerEvents(legendaries, this);
        Bukkit.getPluginManager().registerEvents(forges, this);
        Bukkit.getPluginManager().registerEvents(events, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        for (String command : new String[]{"spree", "spreehud", "killstreak", "stats", "leaderboard",
                "spreeinfo", "spreeadmin", "spreeevent", "legendary"}) {
            Objects.requireNonNull(getCommand(command), "Missing command " + command).setExecutor(commands);
            Objects.requireNonNull(getCommand(command)).setTabCompleter(commands);
        }
        forges.registerRecipes();
        long hudInterval = Math.max(10, getConfig().getLong("general.actionbar-interval-ticks", 40));
        Bukkit.getScheduler().runTaskTimer(this, this::heartbeat, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, streaks::renderHud, hudInterval, hudInterval);
        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(),
                20L * Math.max(30, getConfig().getLong("general.save-interval-seconds", 120)),
                20L * Math.max(30, getConfig().getLong("general.save-interval-seconds", 120)));
        getLogger().info("SpreeCore enabled. No Skript dependency required.");
    }
    private int heartbeatCount;
    private void heartbeat() {
        heartbeatCount++;
        events.tick();
        forges.tick();
        legendaries.tick();
        // Potions/health only need refreshing every 4 seconds, not scanning inventories each tick.
        if (heartbeatCount % 4 == 0) streaks.refreshOnline();
        if (heartbeatCount % 10 == 0) legendaries.syncBloodsBane();
        if (heartbeatCount % 30 == 0) streaks.safetyInventoryCheck();
    }
    @Override public void onDisable() {
        if (forges != null) forges.shutdown();
        if (events != null) events.shutdown();
        if (streaks != null) streaks.shutdown();
        if (data != null) data.save();
    }
    public PlayerDataStore data() { return data; }
    public StreakManager streaks() { return streaks; }
    public LegendaryManager legendaries() { return legendaries; }
    public ForgeManager forges() { return forges; }
    public EventManager events() { return events; }
    public MenuManager menus() { return menus; }
    public String prefix() { return getConfig().getString("general.prefix", "&9&lSPREE &8| &f"); }
    public void reloadSpreeConfiguration() {
        reloadConfig();
        forges.registerRecipes();
        streaks.refreshOnline();
        legendaries.syncBloodsBane();
    }
}
