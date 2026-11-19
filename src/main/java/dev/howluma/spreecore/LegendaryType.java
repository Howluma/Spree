package dev.howluma.spreecore;

import org.bukkit.Material;
import java.util.*;

public enum LegendaryType {
    TUNDRA("tundra", "Tundra Tanto", "Relic: Tundra Tanto", Material.DIAMOND_SWORD,
            "<#e8fbff>T<#d0f5ff>u<#b8eeff>n<#a0e6ff>d<#88dcff>r<#70d0ff>a <#58c2ff>T<#44b2ff>a<#3aa0ff>n<#308cf0>t<#2678e0>ō"),
    BUTCHERER("butcherer", "Blood Butcherer", "Relic: Blood Butcherer", Material.DIAMOND_AXE,
            "<#ff0000>B<#ef0000>l<#de0000>o<#ce0000>o<#be0000>d <#9d0000>B<#8d0000>u<#7c0000>t<#6c0000>c<#5c0000>h<#4b0000>e<#3b0000>r<#2b0000>e<#1a0000>r"),
    STORMBREAKER("stormbreaker", "Storm Breaker", "Relic: Storm Breaker", Material.DIAMOND_AXE,
            "<#ffe94d>S<#efe35d>t<#dfdd6d>o<#ced77e>r<#bed18e>m <#aecb9e>B<#9ec5ae>r<#8ebebe>e<#7db8cf>a<#6db2df>k<#5dacef>e<#4da6ff>r"),
    AQUAFIER("aquafier", "Aquafier", "Relic: Aquafier", Material.DIAMOND_SWORD,
            "<#b2fff5>A<#9ae8f6>q<#82d1f7>u<#6abaf8>a<#52a3f9>f<#3b8cfa>i<#2376fc>e<#0b5fff>r"),
    DARKHEART("darkheart", "Darkheart", "Relic: Darkheart", Material.DIAMOND_SWORD,
            "<#111111>D<#272727>a<#3c3c3c>r<#525252>k<#686868>h<#7e7e7e>e<#939393>a<#a9a9a9>r<#bfbfbf>t"),
    HAILSTORM("hailstorm", "Hailstorm", "Relic: Hailstorm", Material.CROSSBOW,
            "<#b8f8ff>H<#a4f0fb>a<#90e8f7>i<#7cdff2>l<#68d6ee>s<#54cce9>t<#40c0e3>o<#2db3dc>r<#1aa5d5>m"),
    BLOODSBANE("bloodsbane", "Blood's Bane", "Relic: Bloods Bane", Material.DIAMOND_SWORD,
            "<#e5001c>B<#d7001e>l<#c90020>o<#bb0022>o<#ad0024>d<#9f0026>'<#910028>s <#83002a>B<#76002c>a<#68002e>n<#5a0030>e");

    public final String id, display, legacyLore, coloredName;
    public final Material material;
    LegendaryType(String id, String display, String legacyLore, Material material, String coloredName) {
        this.id = id; this.display = display; this.legacyLore = legacyLore;
        this.material = material; this.coloredName = coloredName;
    }
    public boolean forgeable() { return this == TUNDRA || this == BUTCHERER || this == STORMBREAKER || this == AQUAFIER; }
    public static LegendaryType byId(String input) {
        if (input == null) return null;
        String cleaned = input.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace("'", "").replace(" ", "");
        if (cleaned.equals("bloodbutcher") || cleaned.equals("bloodbutch")) return BUTCHERER;
        if (cleaned.equals("tundratanto")) return TUNDRA;
        if (cleaned.equals("stormbreaker")) return STORMBREAKER;
        if (cleaned.equals("bloodsbane") || cleaned.equals("bloodbane")) return BLOODSBANE;
        for (LegendaryType t : values()) if (cleaned.equals(t.id)) return t;
        return null;
    }
    public static List<String> ids() { return Arrays.stream(values()).map(t -> t.id).toList(); }
}
