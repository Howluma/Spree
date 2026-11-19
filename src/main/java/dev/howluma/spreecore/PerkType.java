package dev.howluma.spreecore;

import java.util.Locale;

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

    public static PerkType fromId(String id) {
        if (id == null) return null;
        try { return valueOf(id.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
