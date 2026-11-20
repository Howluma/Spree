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
        /** Retired Industrious selections get a free replacement at the original level. */
        public final Set<Integer> retiredTalentMilestones = new TreeSet<>();
        /** Persist cooldowns so relogging cannot bypass defensive Talents. */
        public final Map<String,Long> talentCooldowns = new HashMap<>();
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
        boolean migrated = false;
        int retiredTalentCount = 0, strengthConversionCount = 0, hookConversions = 0;
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
            for (int level : section.getIntegerList("retired-talent-milestones"))
                if (ProgressionCatalog.milestone(ProgressionCatalog.Kind.TALENT,level)
                        && level<=SpreeMath.BENEFIT_CAP)
                    p.retiredTalentMilestones.add(level);
            readChoices(section, "privilege-choices", p.privilegeChoices, ProgressionCatalog.Kind.PRIVILEGE);
            ConfigurationSection pastTalents=section.getConfigurationSection("talent-choices");
            if(pastTalents!=null) for(String milestone : pastTalents.getKeys(false)) {
                if(!"industrious".equals(pastTalents.getString(milestone))) continue;
                try {
                    int level=Integer.parseInt(milestone);
                    if(ProgressionCatalog.milestone(ProgressionCatalog.Kind.TALENT,level)
                            && level<=SpreeMath.BENEFIT_CAP) {
                        p.retiredTalentMilestones.add(level);
                        retiredTalentCount++;
                        migrated=true;
                    }
                } catch(NumberFormatException ignored) {}
            }
            if (pastTalents != null) for (String raw : pastTalents.getKeys(false)) {
                if (!"barbed_hook".equals(pastTalents.getString(raw))) continue;
                try {
                    int level = Integer.parseInt(raw);
                    if (ProgressionCatalog.milestone(ProgressionCatalog.Kind.TALENT,level)
                            && level <= SpreeMath.BENEFIT_CAP) {
                        hookConversions++;
                        migrated = true;
                    }
                } catch (NumberFormatException ignored) {}
            }
            readChoices(section, "talent-choices", p.talentChoices, ProgressionCatalog.Kind.TALENT);
            ConfigurationSection pastInfusions=section.getConfigurationSection("infusion-choices");
            if(pastInfusions!=null) for(String milestone : pastInfusions.getKeys(false)) {
                if(!"strength".equals(pastInfusions.getString(milestone))) continue;
                try {
                    int level=Integer.parseInt(milestone);
                    if(ProgressionCatalog.milestone(ProgressionCatalog.Kind.INFUSION,level)
                            && level<=SpreeMath.BENEFIT_CAP) {
                        strengthConversionCount++;
                        migrated=true;
                    }
                } catch(NumberFormatException ignored) {}
            }
            readChoices(section, "infusion-choices", p.infusionChoices, ProgressionCatalog.Kind.INFUSION);
            // If a replacement was already selected, remove its stale refund notice.
            p.retiredTalentMilestones.removeIf(p.talentChoices::containsKey);
            ConfigurationSection offers = section.getConfigurationSection("talent-offers");
            if (offers != null) for (String offerKey : offers.getKeys(false)) {
                try {
                    int milestone = Integer.parseInt(offerKey);
                    if (!ProgressionCatalog.milestone(ProgressionCatalog.Kind.TALENT, milestone)) continue;
                    List<String> oldOffers=offers.getStringList(offerKey);
                    List<String> available=migrateRetiredTalentOffers(oldOffers,p.talentChoices.values());
                    if (oldOffers.contains("industrious") || oldOffers.contains("barbed_hook")) migrated=true;
                    if (!available.isEmpty() && !p.talentChoices.containsKey(milestone))
                        p.talentOffers.put(milestone,available);
                } catch (NumberFormatException ignored) {}
            }
            ConfigurationSection cooldowns = section.getConfigurationSection("talent-cooldowns");
            if (cooldowns != null) for (String name : cooldowns.getKeys(false)) {
                long until = cooldowns.getLong(name);
                if (until > System.currentTimeMillis()) p.talentCooldowns.put(name, until);
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
        dirty = migrated;
        if (migrated) plugin.getLogger().info("SpreeCore progression migration: converted "
                + strengthConversionCount + " Strength Infusions to Haste, refunded "
                + retiredTalentCount + " Industrious Talents, and upgraded "
                + hookConversions + " Barbed Hook Talents to Flame Rod.");
        plugin.getLogger().info("Loaded " + players.size() + " player profiles.");
    }

    /** Convert legacy IDs without changing level, stats, or other player choices. */
    static String upgradeChoice(ProgressionCatalog.Kind kind, String id) {
        if(kind==ProgressionCatalog.Kind.INFUSION && "strength".equals(id)) return "haste";
        if(kind==ProgressionCatalog.Kind.TALENT && "industrious".equals(id)) return null;
        if(kind==ProgressionCatalog.Kind.TALENT && "barbed_hook".equals(id)) return "flame_rod";
        return id;
    }

    /** Repair retired offers and silently rename the Barbed Hook option.
     * Existing valid offers are stable; no extra free roll is granted.
     */
    static List<String> migrateRetiredTalentOffers(List<String> old, Collection<String> selected) {
        boolean retired=old.contains("industrious");
        boolean renamed=old.contains("barbed_hook");
        List<String> valid=new ArrayList<>();
        for(String legacyId:old) {
            String id=upgradeChoice(ProgressionCatalog.Kind.TALENT,legacyId);
            if(id!=null && ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,id)!=null
                    && !valid.contains(id) && valid.size()<3
                    && !(renamed && selected.contains(id)))
                valid.add(id);
        }
        if(!retired && !renamed) return List.copyOf(valid);
        List<String> fresh=new ArrayList<>();
        for(var option:ProgressionCatalog.TALENTS)
            if(!valid.contains(option.id()) && !selected.contains(option.id()))
                fresh.add(option.id());
        Collections.shuffle(fresh);
        for(String id:fresh) {
            if(valid.size()>=3)break;
            valid.add(id);
        }
        return List.copyOf(valid);
    }

    private static void readChoices(ConfigurationSection section, String key, Map<Integer,String> target,
                                    ProgressionCatalog.Kind kind) {
        ConfigurationSection saved = section.getConfigurationSection(key);
        if (saved == null) return;
        for (String raw : saved.getKeys(false)) {
            try {
                int level = Integer.parseInt(raw);
                String chosen = upgradeChoice(kind, saved.getString(raw));
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
            if (!p.retiredTalentMilestones.isEmpty())
                yml.set(path + "retired-talent-milestones", new ArrayList<>(p.retiredTalentMilestones));
            long now = System.currentTimeMillis();
            for (var entry : p.talentCooldowns.entrySet())
                if (entry.getValue() > now)
                    yml.set(path + "talent-cooldowns." + entry.getKey(), entry.getValue());
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
