# SpreeCore 1.8.26 — Legendary inventory and durability repair

**Paper target:** 1.21.11, Java 21  
**Distributables:** `SpreeCore-1.8.26.jar`, `SpreeCore-Resource-Pack-1.8.26.zip`  
**Source patch kit:** `SpreeCore-1.8.26-source.zip` (distributed alongside the release in ChatGPT)

## Fixes verified in mock integration tests

- Every genuine legendary sword, axe, and Hailstorm crossbow is **unbreakable**; existing lost durability is reset when its metadata refreshes. The *Unbreakable* tooltip line is hidden.
- Enchantments remain **functionally active**, but their names/levels and **visual glint** are hidden. Uses both Bukkit item flags and Paper's `tooltip_display` hidden-components and `enchantment_glint_override=false` data components.
- Hides standard **attack damage and attack speed tooltip lines**, while actually assigning **netherite-equivalent mainhand attributes** to copper-based weapons: swords 8 damage, 1.6 speed; axes 10 damage, 1.0 speed. Hailstorm stays a crossbow.
- Assigns `howluma:legendary` as the item tooltip style so the real frame sprites in the resource pack are used.
- Replaces the older reflective tooltip refresh with a direct player-inventory refresh bridge. For Blood Butcher, ability descriptions at killstreaks **3 / 5 / 7** appear once unlocked; only requirements appear before then. The display name stays **Blood Butcher**.
- Removes a redundant `item_name` component when present, without hiding the custom display name or lore.
- Retains existing models, enchantments, persistent item IDs, commands and gameplay abilities.

## Debug tooltips

The `minecraft:copper_axe` identifier and `N components` counter in F3+H advanced tooltips are **client debug UI**, not normal item lore. They cannot be hidden per item using the standard Paper tooltip APIs. Players can toggle F3+H off.

## Source and validation

This JAR is an **incremental, bytecode-level patch** of v1.8.25 with a new `LegendaryItemFixes` Java class and small hook changes in `LegendaryCopperBridge` and `BloodButcherTierBridge`. The separate source patch kit includes the helper source, transformer source, and mock test generator.

**Important:** The repository's older main-branch Gradle sources do not yet reproduce this released binary. This note is not a claim of complete main-branch synchronization.

Checks passed: Java 21 compilation; 108 class-file headers verified; JAR/ZIP integrity; 7-streak/2-streak lore visibility; dynamic title and frame; netherite modifiers for swords/axes; enchant and attribute tooltip hiding; glint suppression; unbreakable metadata, reset wear; no redundant inventory updates. **Live Paper gameplay testing has not yet been performed.**

## Deployment

Back up the server and `plugins/SpreeCore` folder, keep only the new JAR, deploy the matching resource pack, fully restart Paper and reconnect. Reload client assets with F3+T if needed. Verify the item at 7 killstreak and attack a mob repeatedly to ensure the item does not lose durability.
