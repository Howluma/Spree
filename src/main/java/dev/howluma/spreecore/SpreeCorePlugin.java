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
    private CombatManager combat;
    private PerkManager perks;
    private CapacityPerk capacity;
    private HuntManager hunt;
    private BukkitTask hudTask, saveTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        migrateLegacySettings();
        settings = new ConfigEditor(this);
        models = new ModelData(this);
        data = new PlayerDataStore(this);
        perks = new PerkManager(this);
        capacity = new CapacityPerk(this);
        perks.initializeExisting();
        legendaries = new LegendaryManager(this);
        streaks = new StreakManager(this);
        forges = new ForgeManager(this);
        events = new EventManager(this);
        menus = new MenuManager(this);
        commands = new CoreCommands(this);
        indicators = new StreakIndicators(this);
        combat = new CombatManager(this);
        hunt = new HuntManager(this);
        Bukkit.getPluginManager().registerEvents(streaks, this);
        Bukkit.getPluginManager().registerEvents(legendaries, this);
        Bukkit.getPluginManager().registerEvents(forges, this);
        Bukkit.getPluginManager().registerEvents(events, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(indicators, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        // Register after core combat so final melee damage multipliers apply in order.
        Bukkit.getPluginManager().registerEvents(perks, this);
        Bukkit.getPluginManager().registerEvents(capacity, this);
        Bukkit.getPluginManager().registerEvents(hunt, this);
        for (String command : new String[]{"spree", "spreehud", "killstreak", "stats", "leaderboard",
                "spreeinfo", "spreeadmin", "spreesettings", "spreeevent", "legendary"}) {
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
        hunt.tick();
        forges.tick();
        legendaries.tick();
        // Potions/health only need refreshing every 4 seconds, not scanning inventories each tick.
        if (heartbeatCount % 4 == 0) streaks.refreshOnline();
        if (heartbeatCount % 10 == 0) { legendaries.syncBloodsBane(); indicators.refreshOnline(); }
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
        if (perks != null) perks.shutdown();
        if (capacity != null) capacity.shutdown();
        if (hunt != null) hunt.shutdown();
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
    public CombatManager combat() { return combat; }
    public PerkManager perks() { return perks; }
    public CapacityPerk capacity() { return capacity; }
    public HuntManager hunt() { return hunt; }
    public String prefix() { return getConfig().getString("general.prefix", "&9&lSPREE &8| &f"); }
    /** Apply an in-memory GUI edit without reloading YAML or re-registering unrelated recipes. */
    public void applySettingChange(String path) {
        if (path.equals("general.actionbar-interval-ticks") || path.equals("general.save-interval-seconds"))
            scheduleConfigurableTasks();
        if (path.startsWith("killstreak.") || path.startsWith("events.juggernaut."))
            streaks.refreshOnline();
        if (path.startsWith("display.streak-indicators.") || path.equals("hunt.glowing-level"))
            indicators.refreshOnline();
        if (path.startsWith("perks.")) {
            perks.initializeExisting();
            for (var player : Bukkit.getOnlinePlayers()) perks.refresh(player);
        }
        if (path.startsWith("resource-pack."))
            for (var player : Bukkit.getOnlinePlayers()) legendaries.migrate(player);
        if (path.equals("legendary.crafting-enabled") || path.startsWith("legendary.forging."))
            forges.registerRecipes();
        if (path.startsWith("legendary.weapons.bloodsbane.")) legendaries.syncBloodsBane();
    }
    /** Preserve admin choices while replacing the v1.2 Recovery terminology and art defaults. */
    private void migrateLegacySettings() {
        boolean changed = false;
        String label = getConfig().getString("general.recovery-label");
        if (label != null) {
            getConfig().set("general.fractured-label",
                    label.contains("RECOVERY") ? "&c&lFractured" : label);
            getConfig().set("general.recovery-label", null);
            changed = true;
        }
        // Shorten the default label on existing 1.4.3 installations without
        // overwriting deliberately customized server labels.
        String currentLabel = getConfig().getString("general.fractured-label", "&c&lFractured");
        if ("&c&lFractured Killstreak".equals(currentLabel)) {
            getConfig().set("general.fractured-label", "&c&lFractured");
            changed = true;
        }
        String symbol = getConfig().getString("display.streak-indicators.symbol", "⚔");
        if ("✦".equals(symbol) || "◆".equals(symbol)) {
            getConfig().set("display.streak-indicators.symbol", "⚔");
            changed = true;
        }
        String fractured = getConfig().getString("display.streak-indicators.recovery-symbol");
        if (fractured != null) {
            getConfig().set("display.streak-indicators.fractured-symbol",
                    "↺".equals(fractured) ? "✕" : fractured);
            getConfig().set("display.streak-indicators.recovery-symbol", null);
            changed = true;
        }
        String oldFarmRule = "killstreak.anti-farm.block-rewards-for-negative-victim";
        if (getConfig().isSet(oldFarmRule)) {
            getConfig().set("killstreak.anti-farm.block-rewards-for-fractured-victim",
                    getConfig().getBoolean(oldFarmRule, true));
            getConfig().set(oldFarmRule, null);
            changed = true;
        }
        // The old progression is retired. Remove saved legacy settings too,
        // otherwise it would appear that the old perks are still configurable.
        for (String removed : new String[]{"killstreak.max-health-hearts", "killstreak.potion-bonuses",
                "killstreak.passives", "killstreak.restrictions", "events.timed.surge",
                "killstreak.milestones.glowing-level"}) {
            if (getConfig().isSet(removed)) {
                getConfig().set(removed, null);
                changed = true;
            }
        }
        if (changed) saveConfig();
    }

    public void reloadSpreeConfiguration() {
        reloadConfig();
        migrateLegacySettings();
        scheduleConfigurableTasks();
        forges.registerRecipes();
        for (var player : Bukkit.getOnlinePlayers()) legendaries.migrate(player);
        perks.initializeExisting();
        streaks.refreshOnline();
        indicators.refreshOnline();
        legendaries.syncBloodsBane();
    }
}
