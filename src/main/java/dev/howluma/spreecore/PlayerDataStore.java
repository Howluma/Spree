package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** All persistent player state in one UUID-keyed cache, not hundreds of Skript variables. */
public final class PlayerDataStore {
    public static final class Profile {
        public final UUID id;
        public String name = "Unknown";
        public int streak, best, kills, deaths, ended;
        public boolean hideHud;
        public UUID revengeAgainst;
        public final Map<UUID, Long> recentVictims = new HashMap<>();
        /** Historical milestones are never rerolled, even after withdrawal or death. */
        public final Map<Integer, String> perkRolls = new TreeMap<>();
        public final Map<Integer, String> privilegeChoices = new TreeMap<>();
        public final Map<Integer, String> talentChoices = new TreeMap<>();
        public final Map<Integer, String> infusionChoices = new TreeMap<>();
        public final Map<Integer, List<String>> talentOffers = new TreeMap<>();
        /** Fractional credits make small XP/durability bonuses reliable over time. */
        public double durabilityCredit, experienceCredit;
        Profile(UUID id) { this.id = id; }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Profile> players = new HashMap<>();
    private boolean dirty;

    public PlayerDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }
    public Profile get(UUID id) { return players.computeIfAbsent(id, key -> { dirty = true; return new Profile(key); }); }
    public Profile get(OfflinePlayer player) {
        Profile p = get(player.getUniqueId());
        if (player.getName() != null && !player.getName().equals(p.name)) {
            p.name = player.getName(); dirty = true;
        }
        return p;
    }
    public Collection<Profile> all() { return Collections.unmodifiableCollection(players.values()); }
    public void dirty() { dirty = true; }
    public void load() {
        players.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            Profile p = new Profile(id);
            p.name = section.getString("name", "Unknown");
            p.streak = section.getInt("streak");
            p.best = section.getInt("best");
            p.kills = section.getInt("kills");
            p.deaths = section.getInt("deaths");
            p.ended = section.getInt("ended");
            p.hideHud = section.getBoolean("hide-hud");
            p.durabilityCredit = PerkMath.fractionalPart(section.getDouble("perk-durability-credit", 0));
            p.experienceCredit = PerkMath.fractionalPart(section.getDouble("perk-experience-credit", 0));
            ConfigurationSection rolls = section.getConfigurationSection("perk-rolls");
            if (rolls != null) for (String milestone : rolls.getKeys(false)) {
                try {
                    int level = Integer.parseInt(milestone);
                    String perk = rolls.getString(milestone);
                    if (PerkMath.isPerkMilestone(level) && PerkType.fromId(perk) != null)
                        p.perkRolls.put(level, perk);
                } catch (NumberFormatException ignored) {}
            }
            readChoices(section, "privilege-choices", p.privilegeChoices, ProgressionCatalog.Kind.PRIVILEGE);
            readChoices(section, "talent-choices", p.talentChoices, ProgressionCatalog.Kind.TALENT);
            readChoices(section, "infusion-choices", p.infusionChoices, ProgressionCatalog.Kind.INFUSION);
            ConfigurationSection offers = section.getConfigurationSection("talent-offers");
            if (offers != null) for (String key : offers.getKeys(false)) {
                try {
                    int milestone = Integer.parseInt(key);
                    if (!ProgressionCatalog.milestone(ProgressionCatalog.Kind.TALENT, milestone)) continue;
                    List<String> available = offers.getStringList(key).stream()
                            .filter(s -> ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT, s) != null)
                            .distinct().limit(3).toList();
                    if (!available.isEmpty()) p.talentOffers.put(milestone, available);
                } catch (NumberFormatException ignored) {}
            }
            String revenge = section.getString("revenge-against");
            if (revenge != null) {
                try { p.revengeAgainst = UUID.fromString(revenge); } catch (IllegalArgumentException ignored) {}
            }
            ConfigurationSection victims = section.getConfigurationSection("recent-victims");
            if (victims != null) for (String victim : victims.getKeys(false)) {
                try { p.recentVictims.put(UUID.fromString(victim), victims.getLong(victim)); }
                catch (IllegalArgumentException ignored) {}
            }
            players.put(id, p);
        }
        dirty = false;
        plugin.getLogger().info("Loaded " + players.size() + " player profiles.");
    }

    private static void readChoices(ConfigurationSection section, String key, Map<Integer,String> target,
                                    ProgressionCatalog.Kind kind) {
        ConfigurationSection saved = section.getConfigurationSection(key);
        if (saved == null) return;
        for (String raw : saved.getKeys(false)) {
            try {
                int level = Integer.parseInt(raw);
                String chosen = saved.getString(raw);
                if (ProgressionCatalog.milestone(kind, level)
                        && ProgressionCatalog.find(kind, chosen) != null)
                    target.put(level, chosen);
            } catch (NumberFormatException ignored) {}
        }
    }

    /** No write each kill. Use a temporary file + atomic rename to resist partial writes. */
    public void save() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Profile p : players.values()) {
            String path = "players." + p.id + ".";
            yml.set(path + "name", p.name);
            yml.set(path + "streak", p.streak);
            yml.set(path + "best", p.best);
            yml.set(path + "kills", p.kills);
            yml.set(path + "deaths", p.deaths);
            yml.set(path + "ended", p.ended);
            yml.set(path + "hide-hud", p.hideHud);
            yml.set(path + "perk-durability-credit", p.durabilityCredit);
            yml.set(path + "perk-experience-credit", p.experienceCredit);
            for (var roll : p.perkRolls.entrySet())
                yml.set(path + "perk-rolls." + roll.getKey(), roll.getValue());
            for (var entry : p.privilegeChoices.entrySet())
                yml.set(path + "privilege-choices." + entry.getKey(), entry.getValue());
            for (var entry : p.talentChoices.entrySet())
                yml.set(path + "talent-choices." + entry.getKey(), entry.getValue());
            for (var entry : p.infusionChoices.entrySet())
                yml.set(path + "infusion-choices." + entry.getKey(), entry.getValue());
            for (var entry : p.talentOffers.entrySet())
                yml.set(path + "talent-offers." + entry.getKey(), entry.getValue());
            if (p.revengeAgainst != null) yml.set(path + "revenge-against", p.revengeAgainst.toString());
            for (var recent : p.recentVictims.entrySet())
                yml.set(path + "recent-victims." + recent.getKey(), recent.getValue());
        }
        try {
            Files.createDirectories(file.toPath().getParent());
            File temp = new File(file.getParentFile(), "players.yml.tmp");
            yml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed saving player data: " + ex.getMessage());
        }
    }
    public List<Profile> leaders(int amount) {
        return players.values().stream()
                .sorted(Comparator.comparingInt((Profile p) -> p.streak).reversed()
                        .thenComparing(p -> p.name, String.CASE_INSENSITIVE_ORDER))
                .limit(amount).toList();
    }
}
