package dev.howluma.spreecore;

import org.bukkit.Material;
import java.util.*;

/** The four repeatable milestone lanes; only Perks roll automatically. */
public final class ProgressionCatalog {
    private ProgressionCatalog() {}

    public enum Kind {
        PRIVILEGE("Privileges", Material.NETHER_STAR, 1),
        PERK("Perks", Material.ENCHANTED_BOOK, 2),
        TALENT("Talents", Material.NETHERITE_SWORD, 3),
        INFUSION("Infusions", Material.POTION, 5);
        public final String title;
        public final Material icon;
        public final int first;
        Kind(String title, Material icon, int first) {
            this.title = title; this.icon = icon; this.first = first;
        }
    }

    public record Option(String id, String name, Material icon, String description) {}

    public static final List<Option> PRIVILEGES = List.of(
        new Option("demolitionist", "Demolitionist", Material.TNT_MINECART, "Unlock TNT minecart placement and use"),
        new Option("mace_wielder", "Mace Wielder", Material.MACE, "Unlock use of the mace in combat"),
        new Option("netherite_specialist", "Netherite Specialist", Material.NETHERITE_PICKAXE, "Diamond tools and weapons become netherite while carried; unlock all netherite weapons and tools"),
        new Option("turtle_master", "Turtle Master", Material.POTION, "Unlock Turtle Master potions"),
        new Option("forbidden_fruit", "Forbidden Fruit", Material.ENCHANTED_GOLDEN_APPLE, "Unlock enchanted golden apples: 60s effects, 30s cooldown, max 4 carried"),
        new Option("last_stand", "Last Stand", Material.TOTEM_OF_UNDYING, "Unlock one total Totem of Undying"));

    public static final List<Option> INFUSIONS = List.of(
        new Option("speed", "Speed I", Material.POTION, "Permanent Speed I"),
        new Option("haste", "Haste I", Material.POTION, "Permanent Haste I"),
        new Option("weaving", "Weaving I", Material.POTION, "Permanent Weaving I"),
        new Option("regeneration", "Regeneration I", Material.POTION, "Permanent Regeneration I"),
        new Option("luck", "Luck V", Material.POTION, "Permanent Luck V"),
        new Option("hero", "Hero of the Village X", Material.POTION, "Permanent Hero of the Village X"));

    public static final List<Option> TALENTS = List.of(
        new Option("mace_mastery", "Mace Mastery", Material.HEAVY_CORE, "Smash cooldown reduced from 60s to 30s; see mace hotbar overlay"),
        new Option("executioner", "Executioner", Material.WITHER_SKELETON_SKULL, "+10% damage against enemies below half health"),
        new Option("combo_breaker", "Combo Breaker", Material.CLOCK, "After 3 unreturned hits, +10% damage until counter-hit or 5s gap"),
        new Option("concussive_blow", "Concussive Blow", Material.ANVIL, "Hit applies Mining Fatigue II for 5s; 45s cooldown"),
        new Option("sunder", "Sunder", Material.SHIELD, "Axe hits against blocking shields extend disable by 1.5s; 45s cooldown"),
        new Option("riposte", "Riposte", Material.IRON_CHESTPLATE, "Shield block slows attacker and prevents jumping for 2s; 45s cooldown"),
        new Option("second_wind", "Second Wind", Material.GHAST_TEAR, "Below 30% health: 2 absorption hearts for 8s; 90s cooldown"),
        new Option("iron_will", "Iron Will", Material.CHAINMAIL_CHESTPLATE, "Take 3+ hearts in one hit: +20% resistance for 5s; 75s cooldown"),
        new Option("bloodlust", "Bloodlust", Material.RED_DYE, "Every 5 fully charged melee hits heals 1 heart; 15s cooldown"),
        new Option("field_medic", "Field Medic", Material.POTION, "PvP kill heals 5 hearts; 60s cooldown"),
        new Option("golden_aegis", "Golden Aegis", Material.GOLD_BLOCK, "Golden apples grant 2 extra absorption hearts; 45s cooldown"),
        new Option("windrunner", "Windrunner", Material.FEATHER, "Self-launched wind charges propel you 25% farther"),
        new Option("escape_artist", "Escape Artist", Material.SHEARS, "Blink to a safe spot when entering cobwebs; on hit cleanse Slowness for 3s; 50s cooldown"),
        new Option("acrobat", "Acrobat", Material.FEATHER, "-40% fall damage; +10% jump strength"),
        new Option("trailblazer", "Trailblazer", Material.LEATHER_BOOTS, "Walk up one-block ledges without jumping"),
        new Option("expanded_reserves", "Expanded Reserves", Material.EXPERIENCE_BOTTLE, "Carry one additional stack of capped XP bottles and wind charges"),
        new Option("battle_sense", "Battle Sense", Material.SPYGLASS, "See an enemy's health for 8s after hitting them"),
        new Option("scavenger", "Scavenger", Material.BUNDLE, "15% hit chance to restore 4 wind charges; 90s cooldown"),
        new Option("quick_hands", "Quick Hands", Material.RABBIT_FOOT, "Consume food and drink potions 20% faster"),
        new Option("streak_insurance", "Streak Insurance", Material.RECOVERY_COMPASS, "Preserve up to 2 lost streak levels on death; 10m cooldown"),
        new Option("blacksmith", "Blacksmith", Material.ANVIL, "Craft golden apples with 2 gold and breeze rods from 8 iron nuggets + stick"));

    public static List<Option> options(Kind kind) {
        return switch (kind) {
            case PRIVILEGE -> PRIVILEGES;
            case INFUSION -> INFUSIONS;
            case TALENT -> TALENTS;
            case PERK -> List.of();
        };
    }

    public static Option find(Kind kind, String id) {
        return options(kind).stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
    }

    public static boolean milestone(Kind kind, int level) {
        if (level <= 0) return false;
        return switch (kind) {
            case PRIVILEGE -> level % 5 == 1;
            case PERK -> PerkMath.isPerkMilestone(level);
            case TALENT -> level % 5 == 3;
            case INFUSION -> level % 5 == 0;
        };
    }

    public static List<Integer> milestones(Kind kind, int cap) {
        List<Integer> result = new ArrayList<>();
        for (int level = 1; level <= cap; level++)
            if (milestone(kind, level)) result.add(level);
        return List.copyOf(result);
    }
}
