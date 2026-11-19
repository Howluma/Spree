package dev.howluma.spreecore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/** Whitelisted, typed settings: only keys shipped in config.yml can be edited via GUI. */
public final class ConfigEditor {
    public record Setting(String path, String label, Object defaultValue) {
        public boolean toggle() { return defaultValue instanceof Boolean; }
        public boolean number() { return defaultValue instanceof Number; }
        private String shown(Object value) {
            if (path.startsWith("display.streak-indicators.tier-colors.")) {
                String code = String.valueOf(value);
                return "&7" + code.replace("&", "\uFF06") + "  " + code + "\u25A0";
            }
            if (path.startsWith("combat.") && path.endsWith("multiplier") && value instanceof Number number)
                return format(number.doubleValue() * 100) + "%";
            return format(value);
        }
        public String current(FileConfiguration config) { return shown(config.get(path, defaultValue)); }
        public String defaultText() { return shown(defaultValue); }
    }
    public record Category(String id, String title, Material icon, List<Setting> settings) {}
    public record Result(boolean success, String message) {}

    private final SpreeCorePlugin plugin;
    ConfigEditor(SpreeCorePlugin plugin) { this.plugin = plugin; }

    public List<Category> categories() {
        org.bukkit.configuration.Configuration defaults = plugin.getConfig().getDefaults();
        if (defaults == null) return List.of();
        Map<String, List<Setting>> groups = new LinkedHashMap<>();
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) continue;
            Object value = defaults.get(path);
            if (!(value instanceof Boolean || value instanceof Number || value instanceof String)) continue;
            groups.computeIfAbsent(group(path), ignored -> new ArrayList<>())
                    .add(new Setting(path, label(path), value));
        }
        List<Category> result = new ArrayList<>();
        groups.forEach((id, settings) -> result.add(new Category(id, title(id), icon(id), List.copyOf(settings))));
        return result;
    }
    public Category category(String id) {
        return categories().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
    public Setting find(String path) {
        for (Category c : categories()) for (Setting setting : c.settings()) {
            if (setting.path().equals(path)) return setting;
        }
        return null;
    }
    public Result set(String path, String input) {
        Setting setting = find(path);
        if (setting == null) return new Result(false, "Unknown or unsupported setting.");
        Object value;
        String trimmed = input.trim();
        if (path.startsWith("combat.") && path.endsWith("multiplier") && trimmed.endsWith("%")) {
            try { trimmed = Double.toString(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1).trim()) / 100.0); }
            catch (NumberFormatException ex) { return new Result(false, "Use a percentage like 75% or a decimal like 0.75."); }
        }
        try {
            if (setting.toggle()) {
                value = switch (trimmed.toLowerCase(Locale.ROOT)) {
                    case "true", "on", "yes", "1" -> true;
                    case "false", "off", "no", "0" -> false;
                    default -> throw new IllegalArgumentException("Use on/off or true/false.");
                };
            } else if (setting.defaultValue() instanceof Integer) {
                value = Integer.parseInt(trimmed);
            } else if (setting.defaultValue() instanceof Long) {
                value = Long.parseLong(trimmed);
            } else if (setting.defaultValue() instanceof Number) {
                double parsed = Double.parseDouble(trimmed);
                if (!Double.isFinite(parsed)) throw new IllegalArgumentException("The number must be finite.");
                value = parsed;
            } else {
                if (trimmed.isEmpty() || trimmed.length() > 160 || trimmed.contains("\n") || trimmed.contains("\r"))
                    throw new IllegalArgumentException("Text must contain 1-160 characters on one line.");
                value = input;
            }
        } catch (NumberFormatException ex) {
            return new Result(false, "Enter a valid " + (setting.defaultValue() instanceof Integer ? "whole number" : "number") + ".");
        } catch (IllegalArgumentException ex) {
            return new Result(false, ex.getMessage());
        }
        String problem = validate(path, value);
        if (problem != null) return new Result(false, problem);
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        plugin.applySettingChange(path);
        return new Result(true, setting.label() + " = " + format(value));
    }
    public Result reset(String path) {
        Setting s = find(path);
        return s == null ? new Result(false, "Setting no longer exists.") : set(path, String.valueOf(s.defaultValue()));
    }
    public static String validate(String path, Object value) {
        if (path.startsWith("fate-sigil.recipe.slot-")) {
            if (!(value instanceof String input)) return "Enter a material name.";
            if (input.equalsIgnoreCase("AIR")) return null;
            Material m=Material.matchMaterial(input.trim());
            return m!=null && m.isItem() && !m.isAir()
                    ? null : "Use a valid item name such as DIAMOND, or AIR for an empty slot.";
        }
        if (path.equals("fate-sigil.recipe.result-amount") && value instanceof Number amount)
            return between(amount.doubleValue(),1,16);
        if (path.startsWith("perks.") && value instanceof Number perkNumber) {
            if (path.endsWith(".max-copies")) return between(perkNumber.doubleValue(), 1, 8);
            if (path.endsWith(".per-copy"))
                return between(perkNumber.doubleValue(), 0, path.equals("perks.capacity.per-copy") ? 16 : .10);
        }
        if (path.startsWith("display.streak-indicators.tier-colors."))
            return value instanceof String str && str.matches("(?i)(?:&[0-9a-f]|<#[0-9a-f]{6}>)")
                    ? null : "Use a single vanilla color code or six-digit RGB hex.";
        if (!(value instanceof Number number)) return null;
        double amount = number.doubleValue();
        if (!Double.isFinite(amount)) return "Enter a finite number.";
        if (path.equals("general.max-killstreak")) return between(amount, 1, 1000);
        if (path.equals("killstreak.kill-rewards.levels-per-tier")
                || path.equals("killstreak.kill-rewards.base-reward"))
            return between(amount, 1, 1000);
        if (path.equals("killstreak.kill-rewards.extra-per-tier"))
            return between(amount, 0, 1000);
        if (path.equals("killstreak.mace-unlock-level") || path.startsWith("killstreak.milestones."))
            return between(amount, 1, 1000);
        if (path.equals("killstreak.health-per-level-hearts")) return between(amount, 0, 2);
        if (path.startsWith("resource-pack.models.")) return between(amount, 1, 16000000);
        if (path.endsWith("chance")) return between(amount, 0, 1);
        if (path.startsWith("combat.") && path.endsWith("multiplier")) return between(amount, 0, 1);
        if (path.endsWith("multiplier") || path.endsWith("damage-multiplier")) return between(amount, 0, 10);
        if (path.contains("health-hearts") || path.endsWith("bonus-hearts") || path.endsWith("sacrifice-hearts"))
            return between(amount, 1, 200);
        if (path.endsWith("level")) return between(amount, 0, 10);
        if (path.contains("radius")) return between(amount, 0, 128);
        if (path.endsWith("seconds") || path.endsWith("seconds-between-hits")) return between(amount, 0, 86400);
        if (path.endsWith("ticks")) return between(amount, 1, 72000);
        if (path.contains("streak") || path.endsWith("kill-bonus") || path.endsWith("winner-bonus"))
            return between(amount, 0, 1000);
        return between(amount, 0, 100000);
    }
    private static String between(double amount, double min, double max) {
        if (amount < min || amount > max) return "Allowed range: " + format(min) + " to " + format(max) + ".";
        return null;
    }
    public static String format(Object value) {
        if (value instanceof Double d && d == Math.rint(d)) return Long.toString(d.longValue());
        if (value instanceof Float f && f == Math.rint(f)) return Long.toString(f.longValue());
        if (value instanceof Number) return String.valueOf(value);
        if (value instanceof Boolean b) return b ? "ON" : "OFF";
        return String.valueOf(value);
    }
    private static String group(String path) {
        String[] p = path.split("\\.");
        if (p[0].equals("general") || p[0].equals("resource-pack")) return p[0];
        if (p[0].equals("fate-sigil")) return p.length>=3 ? "fate-sigil.recipe" : "fate-sigil";
        if (p[0].equals("killstreak")) return p.length >= 3 ? "killstreak." + p[1] : "killstreak";
        if (p[0].equals("legendary")) {
            if (p.length >= 4 && p[1].equals("weapons")) return "legendary." + p[2];
            if (p.length >= 3 && p[1].equals("forging")) return "legendary.forging";
            return "legendary";
        }
        if (p[0].equals("events")) return p.length >= 3 && p[1].equals("timed") && p.length >= 4
                ? "events." + p[2] : p.length >= 3 ? "events." + p[1] : "events";
        return p[0];
    }
    private static String title(String id) {
        return switch (id) {
            case "general" -> "General & Interface";
            case "combat" -> "Combat Balancing";
            case "perks" -> "Stackable Perks";
            case "hunt" -> "Wanted Status & Hunt";
            case "privileges" -> "Privilege Item Limits";
            case "fate-sigil" -> "Fate Sigil";
            case "fate-sigil.recipe" -> "Fate Sigil Crafting Recipe";
            case "resource-pack" -> "Resource Pack Models";
            case "killstreak" -> "Killstreak & Health";
            case "killstreak.anti-farm" -> "Anti-Farming";
            case "killstreak.revenge" -> "Legacy Revenge (Disabled by Default)";
            case "killstreak.kill-rewards" -> "Hunt Kill Rewards";
            case "killstreak.withdrawal" -> "Spree Withdrawal";
            case "killstreak.milestones" -> "Milestone Buffs";
            case "display" -> "Tab & Nametag Indicators";
            case "legendary" -> "Legendary System";
            case "legendary.forging" -> "Legendary Forging";
            default -> capitalize(id.substring(id.lastIndexOf('.') + 1));
        };
    }
    private static String label(String path) {
        if (path.startsWith("perks.")) {
            String[] parts = path.split("\\.");
            if (parts.length == 3)
                return capitalize(parts[1]) + " " + (parts[2].equals("per-copy") ? "Bonus Per Copy"
                        : capitalize(parts[2]));
        }
        if (path.startsWith("display.streak-indicators.tier-colors.")) {
            int first = Integer.parseInt(path.substring(path.lastIndexOf('.') + 1));
            return first >= 30 ? "Level 30+ Number Color" : "Levels " + first + "-" + (first + 4) + " Number Color";
        }
        return capitalize(path.substring(path.lastIndexOf('.') + 1));
    }
    private static String capitalize(String s) {
        String[] parts = s.replace('-', ' ').replace('_', ' ').split(" ");
        StringJoiner words = new StringJoiner(" ");
        for (String part : parts) if (!part.isEmpty()) words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        return words.toString();
    }
    private static Material icon(String id) {
        if (id.equals("general")) return Material.COMPARATOR;
        if (id.equals("combat")) return Material.TNT;
        if (id.equals("perks")) return Material.ENCHANTED_BOOK;
        if (id.startsWith("fate-sigil")) return Material.ECHO_SHARD;
        if (id.equals("privileges")) return Material.TOTEM_OF_UNDYING;
        if (id.equals("hunt")) return Material.COMPASS;
        if (id.equals("display")) return Material.NAME_TAG;
        if (id.equals("resource-pack")) return Material.PAINTING;
        if (id.contains("anti-farm")) return Material.SHIELD;
        if (id.contains("revenge")) return Material.IRON_SWORD;
        if (id.contains("withdrawal")) return Material.HEART_OF_THE_SEA;
        if (id.contains("killstreak")) return Material.NETHER_STAR;
        if (id.contains("forging")) return Material.ANVIL;
        if (id.startsWith("legendary.")) return Material.NETHERITE_SWORD;
        if (id.startsWith("legendary")) return Material.DIAMOND_SWORD;
        if (id.startsWith("events")) return Material.CLOCK;
        return Material.BOOK;
    }
}
