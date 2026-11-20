package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.Objects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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
    private ProgressionManager progression;
    private FateSigil fate;
    private ProgressionMenu progressMenus;
    private PrivilegeRules privilegeRules;
    private SpecialistItems specialistItems;
    private TalentEffects talents;
    private QuickHands quickHands;
    private FlameRodTalent flameRod;
    private RodAssistManager rodAssist;
    private AdminBalanceMenu balanceMenu;
    private AdminUpgradeMenu adminUpgrades;
    private TalentFeedback talentFeedback;
    private KillDeathFeedback killDeathFeedback;
    private BukkitTask hudTask, saveTask, flameRodTask, rodAssistTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        migrateLegacySettings();
        settings = new ConfigEditor(this);
        models = new ModelData(this);
        data = new PlayerDataStore(this);
        perks = new PerkManager(this);
        capacity = new CapacityPerk(this);
        fate = new FateSigil(this);
        progression = new ProgressionManager(this);
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
        privilegeRules = new PrivilegeRules(this);
        specialistItems = new SpecialistItems(this);
        talents = new TalentEffects(this);
        talentFeedback = new TalentFeedback(this);
        killDeathFeedback = new KillDeathFeedback(this);
        quickHands = new QuickHands(this);
        flameRod = new FlameRodTalent(this);
        rodAssist = new RodAssistManager(this);
        progressMenus = new ProgressionMenu(this);
        balanceMenu = new AdminBalanceMenu(this);
        adminUpgrades = new AdminUpgradeMenu(this);
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
        Bukkit.getPluginManager().registerEvents(fate, this);
        Bukkit.getPluginManager().registerEvents(progressMenus, this);
        Bukkit.getPluginManager().registerEvents(privilegeRules, this);
        Bukkit.getPluginManager().registerEvents(specialistItems, this);
        Bukkit.getPluginManager().registerEvents(talents, this);
        Bukkit.getPluginManager().registerEvents(talentFeedback, this);
        Bukkit.getPluginManager().registerEvents(quickHands, this);
        Bukkit.getPluginManager().registerEvents(flameRod, this);
        Bukkit.getPluginManager().registerEvents(rodAssist, this);
        Bukkit.getPluginManager().registerEvents(balanceMenu, this);
        Bukkit.getPluginManager().registerEvents(adminUpgrades, this);
        for (String command : new String[]{"spree", "spreehud", "killstreak", "stats", "leaderboard",
                "spreeinfo", "spreeadmin", "spreeset", "spreesetall", "fatesigil", "spreesettings", "spreeevent", "legendary"}) {
            Objects.requireNonNull(getCommand(command), "Missing command " + command).setExecutor(commands);
            Objects.requireNonNull(getCommand(command)).setTabCompleter(commands);
        }
        forges.registerRecipes();
        fate.registerRecipe();
        talents.registerRecipes();
        Bukkit.getScheduler().runTaskTimer(this, this::heartbeat, 20L, 20L);
        flameRodTask = Bukkit.getScheduler().runTaskTimer(this, flameRod::tick, 1L, 2L);
        // Track the bobber's travel every tick for accurate nearby catches.
        rodAssistTask = Bukkit.getScheduler().runTaskTimer(this, rodAssist::tick, 1L, 1L);
        scheduleConfigurableTasks();
        warnIfLegacyKillstreakScriptPresent();
        getLogger().info("SpreeCore enabled. No Skript dependency required.");
    }
    /**
     * A still-active copy of the previous killstreak.sk Skript is a common
     * cause of double score changes that no listener guard can prevent.
     * This is a warning only; never disable someone else's Skript files.
     */
    private void warnIfLegacyKillstreakScriptPresent() {
        if(!Bukkit.getPluginManager().isPluginEnabled("Skript"))return;
        Path scripts=getDataFolder().getParentFile().toPath()
                .resolve("Skript").resolve("scripts");
        if(!Files.isDirectory(scripts))return;
        try(Stream<Path> paths=Files.walk(scripts,3)) {
            boolean found=paths.anyMatch(path -> Files.isRegularFile(path)
                    &&path.getFileName().toString().equalsIgnoreCase("killstreak.sk"));
            if(found) getLogger().warning("[DeathGuard] A legacy killstreak.sk script is present "
                    +"and Skript is enabled. Verify that this script is NOT loaded alongside "
                    +"SpreeCore's killstreak handler. It can apply an independent second "
                    +"death penalty. This warning does not modify Skript.");
        } catch(IOException ex) {
            getLogger().fine("Cannot inspect legacy Skript scripts directory: "+ex.getMessage());
        }
    }

    private int heartbeatCount;
    private void heartbeat() {
        heartbeatCount++;
        events.tick();
        hunt.tick();
        privilegeRules.tick();
        specialistItems.tick();
        talents.tick();
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
        if (talents != null) talents.shutdown();
        if (talentFeedback != null) talentFeedback.shutdown();
        if (quickHands != null) quickHands.shutdown();
        if (flameRodTask != null) flameRodTask.cancel();
        if (rodAssistTask != null) rodAssistTask.cancel();
        if (rodAssist != null) rodAssist.shutdown();
        if (flameRod != null) flameRod.shutdown();
        if (specialistItems != null) specialistItems.shutdown();
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
    public ProgressionManager progression() { return progression; }
    public FateSigil fate() { return fate; }
    public ProgressionMenu progressMenus() { return progressMenus; }
    public PrivilegeRules privilegeRules() { return privilegeRules; }
    public SpecialistItems specialistItems() { return specialistItems; }
    public TalentEffects talents() { return talents; }
    public TalentFeedback talentFeedback() { return talentFeedback; }
    public QuickHands quickHands() { return quickHands; }
    public FlameRodTalent flameRod() { return flameRod; }
    public RodAssistManager rodAssist() { return rodAssist; }
    public AdminBalanceMenu balanceMenu() { return balanceMenu; }
    public AdminUpgradeMenu adminUpgrades() { return adminUpgrades; }
    public KillDeathFeedback killDeathFeedback() { return killDeathFeedback; }
    public String prefix() { return getConfig().getString("general.prefix", "&9&lSPREE &8| &f"); }
    /** Apply an in-memory GUI edit without reloading YAML or re-registering unrelated recipes. */
    public void applySettingChange(String path) {
        if (path.equals("general.actionbar-interval-ticks") || path.equals("general.save-interval-seconds"))
            scheduleConfigurableTasks();
        if (path.startsWith("killstreak.") || path.startsWith("events.juggernaut.")
                || path.startsWith("privileges.") || path.startsWith("talents."))
            streaks.refreshOnline();
        if (path.startsWith("display.streak-indicators.") || path.equals("hunt.glowing-level"))
            indicators.refreshOnline();
        if (path.startsWith("perks.")) {
            perks.initializeExisting();
            for (var player : Bukkit.getOnlinePlayers()) perks.refresh(player);
        }
        if(path.startsWith("privileges.")||path.equals("talents.expanded-reserves.extra-items"))
            for(var player:Bukkit.getOnlinePlayers())privilegeRules.enforce(player);
        if (path.startsWith("resource-pack."))
            for (var player : Bukkit.getOnlinePlayers()) legendaries.migrate(player);
        if (path.startsWith("fate-sigil.") || path.equals("resource-pack.enabled")
                || path.equals("resource-pack.models.fate-sigil"))
            fate.registerRecipe();
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
                "killstreak.milestones", "killstreak.mace-unlock-level"}) {
            if (getConfig().isSet(removed)) {
                getConfig().set(removed, null);
                changed = true;
            }
        }
        // Older versions wrote a hard 30 cap into existing servers' config.yml.
        // A new JAR alone must not leave returning players locked at 30.
        int formerScoreCap = getConfig().getInt("general.max-killstreak", 30);
        if (formerScoreCap <= SpreeMath.BENEFIT_CAP) {
            getConfig().set("general.max-killstreak", 1_000_000_000);
            getLogger().info("Migrated legacy 30-level killstreak cap: scoring now continues above 30; rewards still stop at 30.");
            changed = true;
        }
        // 0.80 was the former built-in 20%-faster eating default.
        // Preserve intentional custom multipliers other than the old default.
        if (Math.abs(getConfig().getDouble("talents.quick-hands.consume-time-multiplier",0.9)-0.8)<1e-8) {
            getConfig().set("talents.quick-hands.consume-time-multiplier",0.9);
            changed = true;
        }
        // One-time migration of the former stock 5% TNT-per-victim-streak value.
        // Record completion so an admin can deliberately set 5% later without
        // it being changed again on the next restart or /spreeadmin reload.
        final String tntMigration = "migrations.tnt-10-percent-victim-scaling-1-8-13";
        if (!getConfig().getBoolean(tntMigration, false)) {
            String tntSetting = "combat.tnt.bonus-damage-per-victim-streak";
            if (getConfig().isSet(tntSetting)
                    && Math.abs(getConfig().getDouble(tntSetting) - 0.05) < 1e-8) {
                getConfig().set(tntSetting, 0.10);
                getLogger().info("Migrated TNT damage growth from 5% to 10% per victim killstreak.");
            }
            getConfig().set(tntMigration, true);
            changed = true;
        }
        // Old servers retain their saved config.yml. Update previous stock
        // bobber collision margins without overwriting custom balance values.
        if (RodBalanceMigration.migrate(getConfig())) {
            getLogger().info("Updated stock fishing rod catches to +0.30 player/+0.35 item blocks and Flame Rod TNT ignition tolerance to 0.50 blocks.");
            changed = true;
        }
        if (getConfig().isSet("talents.barbed-hook")) {
            getConfig().set("talents.barbed-hook", null); // Retired; no damage-on-hook logic remains.
            changed = true;
        }
        if (FateSigil.migrateOldRecipe(getConfig())) {
            getLogger().info("Updated the old default Fate Sigil recipe to Ominous Trial Keys, Netherite Ingots and a Heart of the Sea.");
            changed = true;
        }
        if (changed) saveConfig();
    }

    public void reloadSpreeConfiguration() {
        reloadConfig();
        migrateLegacySettings();
        scheduleConfigurableTasks();
        forges.registerRecipes();
        fate.registerRecipe();
        talents.registerRecipes();
        for (var player : Bukkit.getOnlinePlayers()) legendaries.migrate(player);
        perks.initializeExisting();
        streaks.refreshOnline();
        indicators.refreshOnline();
        legendaries.syncBloodsBane();
    }
}
