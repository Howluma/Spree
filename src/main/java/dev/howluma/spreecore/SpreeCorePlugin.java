package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.Objects;

public final class SpreeCorePlugin extends JavaPlugin {
    private PlayerDataStore data;
    private StreakManager streaks;
    private LegendaryManager legendaries;
    private ForgeManager forges;
    private EventManager events;
    private MenuManager menus;
    private CoreCommands commands;
    private ConfigEditor settings;
    private ModelData models;
    private StreakIndicators indicators;
    private BukkitTask hudTask, saveTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        settings = new ConfigEditor(this);
        models = new ModelData(this);
        data = new PlayerDataStore(this);
        legendaries = new LegendaryManager(this);
        streaks = new StreakManager(this);
        forges = new ForgeManager(this);
        events = new EventManager(this);
        menus = new MenuManager(this);
        commands = new CoreCommands(this);
        indicators = new StreakIndicators(this);
        Bukkit.getPluginManager().registerEvents(streaks, this);
        Bukkit.getPluginManager().registerEvents(legendaries, this);
        Bukkit.getPluginManager().registerEvents(forges, this);
        Bukkit.getPluginManager().registerEvents(events, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(indicators, this);
        for (String command : new String[]{"spree", "spreehud", "killstreak", "stats", "leaderboard",
                "spreeinfo", "spreeadmin", "spreeevent", "legendary"}) {
            Objects.requireNonNull(getCommand(command), "Missing command " + command).setExecutor(commands);
            Objects.requireNonNull(getCommand(command)).setTabCompleter(commands);
        }
        forges.registerRecipes();
        Bukkit.getScheduler().runTaskTimer(this, this::heartbeat, 20L, 20L);
        scheduleConfigurableTasks();
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
        if (heartbeatCount % 10 == 0) { legendaries.syncBloodsBane(); indicators.refreshOnline(); }
        if (heartbeatCount % 30 == 0) streaks.safetyInventoryCheck();
    }
    private void scheduleConfigurableTasks() {
        if (hudTask != null) hudTask.cancel();
        if (saveTask != null) saveTask.cancel();
        long hudInterval = Math.max(10, getConfig().getLong("general.actionbar-interval-ticks", 40));
        long saveInterval = 20L * Math.max(30, getConfig().getLong("general.save-interval-seconds", 120));
        hudTask = Bukkit.getScheduler().runTaskTimer(this, streaks::renderHud, hudInterval, hudInterval);
        saveTask = Bukkit.getScheduler().runTaskTimer(this, data::save, saveInterval, saveInterval);
    }
    @Override public void onDisable() {
        if (hudTask != null) hudTask.cancel();
        if (saveTask != null) saveTask.cancel();
        if (forges != null) forges.shutdown();
        if (events != null) events.shutdown();
        if (indicators != null) indicators.shutdown();
        if (streaks != null) streaks.shutdown();
        if (data != null) data.save();
    }
    public PlayerDataStore data() { return data; }
    public StreakManager streaks() { return streaks; }
    public LegendaryManager legendaries() { return legendaries; }
    public ForgeManager forges() { return forges; }
    public EventManager events() { return events; }
    public MenuManager menus() { return menus; }
    public ConfigEditor settings() { return settings; }
    public ModelData models() { return models; }
    public StreakIndicators indicators() { return indicators; }
    public String prefix() { return getConfig().getString("general.prefix", "&9&lSPREE &8| &f"); }
    /** Apply an in-memory GUI edit without reloading YAML or re-registering unrelated recipes. */
    public void applySettingChange(String path) {
        if (path.equals("general.actionbar-interval-ticks") || path.equals("general.save-interval-seconds"))
            scheduleConfigurableTasks();
        if (path.startsWith("killstreak.") || path.startsWith("events.juggernaut."))
            streaks.refreshOnline();
        if (path.startsWith("display.streak-indicators.")) indicators.refreshOnline();
        if (path.startsWith("resource-pack."))
            for (var player : Bukkit.getOnlinePlayers()) legendaries.migrate(player);
        if (path.equals("legendary.crafting-enabled") || path.startsWith("legendary.forging."))
            forges.registerRecipes();
        if (path.startsWith("legendary.weapons.bloodsbane.")) legendaries.syncBloodsBane();
    }
    public void reloadSpreeConfiguration() {
        reloadConfig();
        scheduleConfigurableTasks();
        forges.registerRecipes();
        for (var player : Bukkit.getOnlinePlayers()) legendaries.migrate(player);
        streaks.refreshOnline();
        indicators.refreshOnline();
        legendaries.syncBloodsBane();
    }
}
