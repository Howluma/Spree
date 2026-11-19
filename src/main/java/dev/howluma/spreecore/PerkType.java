package dev.howluma.spreecore;

import java.util.Locale;
import org.bukkit.Material;

/** Each copy of a Perk is a small permanent, stackable stat increase. */
public enum PerkType {
    POWER("power", "Power", .02, 4),
    FORTITUDE("fortitude", "Fortitude", .02, 4),
    AGILITY("agility", "Agility", .03, 3),
    DEXTERITY("dexterity", "Dexterity", .02, 4),
    RESTORATION("restoration", "Restoration", .03, 4),
    STABILITY("stability", "Stability", .04, 4),
    ENDURANCE("endurance", "Endurance", .05, 4),
    EFFICIENCY("efficiency", "Efficiency", .04, 4),
    PRESERVATION("preservation", "Preservation", .05, 4),
    INSIGHT("insight", "Insight", .05, 4),
    REACH("reach", "Reach", .05, 3),
    SCALE("scale", "Scale", .05, 3),
    CAPACITY("capacity", "Capacity", 4, 4),
    ALCHEMY("alchemy", "Alchemy", .05, 4);

    public final String id;
    public final String display;
    public final double defaultPerCopy;
    public final int defaultMaxCopies;

    PerkType(String id, String display, double defaultPerCopy, int defaultMaxCopies) {
        this.id = id;
        this.display = display;
        this.defaultPerCopy = defaultPerCopy;
        this.defaultMaxCopies = defaultMaxCopies;
    }

    /** Distinct icons for the operator picker and earned Perk guide. */
    public Material icon() {
        return switch (this) {
            case POWER -> Material.BLAZE_ROD;
            case FORTITUDE -> Material.IRON_CHESTPLATE;
            case AGILITY -> Material.RABBIT_FOOT;
            case DEXTERITY -> Material.SHEARS;
            case RESTORATION -> Material.GLISTERING_MELON_SLICE;
            case STABILITY -> Material.OBSIDIAN;
            case ENDURANCE -> Material.COOKED_BEEF;
            case EFFICIENCY -> Material.DIAMOND_PICKAXE;
            case PRESERVATION -> Material.ANVIL;
            case INSIGHT -> Material.EXPERIENCE_BOTTLE;
            case REACH -> Material.SPYGLASS;
            case SCALE -> Material.SLIME_BALL;
            case CAPACITY -> Material.BUNDLE;
            case ALCHEMY -> Material.POTION;
        };
    }

    /** User-facing descriptions of what the code actually changes. */
    public String effectDescription() {
        return switch (this) {
            case POWER -> "Stronger melee and projectile hits";
            case FORTITUDE -> "Reduce incoming damage from most sources";
            case AGILITY -> "Move and sprint faster";
            case DEXTERITY -> "Attack with weapons faster";
            case RESTORATION -> "Restore more health when healed";
            case STABILITY -> "Resist knockback from enemy attacks";
            case ENDURANCE -> "Consume less hunger from exhaustion";
            case EFFICIENCY -> "Mine and break blocks faster";
            case PRESERVATION -> "Take less durability damage to items";
            case INSIGHT -> "Earn additional experience points";
            case REACH -> "Reach entities from farther away";
            case SCALE -> "Become smaller with a smaller hitbox";
            case CAPACITY -> "Carry more items per stackable slot";
            case ALCHEMY -> "Extend beneficial potion effect durations";
        };
    }

    public String effectNote() {
        return switch (this) {
            case POWER -> "Excludes TNT and crystal explosions";
            case ALCHEMY -> "Excludes permanent Infusions and beacons";
            case REACH -> "Entity reach only, not block reach";
            case CAPACITY -> "Applies to naturally stackable items";
            default -> "";
        };
    }

    public static PerkType fromId(String id) {
        if (id == null) return null;
        try { return valueOf(id.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
