# SpreeCore 1.8.24 — Legendary item component and tooltip repair

**Build:** `SpreeCore-1.8.24.jar`  
**Matching pack:** `SpreeCore-Resource-Pack-1.8.24.zip`  
**Server target:** Paper 1.21.11

## Changes from 1.8.23

- Blood Butcher's Bleeding lore is simplified to **"Deals 1 heart every 2 seconds."** Gameplay is unchanged (5% proc; 1 heart pulses at 2 and 4 seconds; 10-second per-target reapplication cooldown).
- Reverse the prior `HIDE_ENCHANTS`, `HIDE_ATTRIBUTES`, and deprecated `HIDE_ADDITIONAL_TOOLTIP` item flags for all seven legendaries, and ensure whole-tooltip visibility. **Enchantments themselves are preserved**, but visual enchantment glint remains explicitly off.
- Copper-based legendaries now explicitly expose mainhand item attribute components matching netherite counterparts:
  - Swords: +7 attack damage / -2.4 attack speed on the player's base attributes = **8 attack damage, 1.6 attack speed**.
  - Axes: +9 attack damage / -3 attack speed = **10 attack damage, 1.0 attack speed**.
  - Only one explicit modifier per attribute; no duplication on refresh.
- Copper swords/axes now get explicit netherite-equivalent **2031 maximum durability** and **15 enchantability** components. Existing item wear, enchantments, persistent identity, and model data remain unchanged.
- Hailstorm remains a **crossbow**, with its normal statistics but visible enchantments again.
- Existing items from v1.8.22/23 are corrected at next join or ordinary periodic legendary inventory refresh, even when their lore and visible names already match the new format.
- Keeps original models, artwork, Pantheon-style tooltip frames, glint suppression, killstreak-locked descriptions, and existing Blood Butcher cooldowns/controls.

## Scope and limitation

The item material **stays copper** for the user-selected resource-pack models, so copper-specific vanilla mechanics such as mining tier and repair material are **not fully netherite**. Only the listed combat attributes and data components are explicitly matched. Hailstorm has no netherite counterpart.

The shipped JAR is an **incremental binary patch** of the 1.8.23 JAR with updated `LegendaryCopperBridge` and `BloodButcherTierBridge` classes, and `plugin.yml` version 1.8.24. The older repository main-branch Gradle source tree has **not yet been fully reconciled** with the binary release. Do not assume a plain main-branch Gradle build produces this JAR.

## Testing

- Java 21 compilation passed.
- Mock tests passed: all seven items; enchantments preserved and visible; no glint; old hidden flags removed; item name and tooltip style preserved; mainhand attribute values; no modifier duplication; 2031 max durability; enchantability 15; no changed item wear; locked-tier lore and Blood Butcher controls.
- Class-file ASM verification: **107 valid, 0 invalid**.
- JAR/pack ZIP integrity and pack JSON parsing passed.
- **43 original asset files** (models, sprites, metadata) preserved byte-for-byte from the previous matching pack.
- **Live multiplayer Paper verification not performed.**

## Distribution verification

- JAR SHA-256: `0963619110a60f694cc3d2b191da19a189dfbaf04f53615141e54630518d79e9`
- Resource pack SHA-256: `04824e410031b322dd1f217188952b57d8c7220c26497fb23336a3b4b579ea7b`

## Install

Back up the server and `plugins/SpreeCore`. Replace the old JAR with `SpreeCore-1.8.24.jar`, deploy the matching resource pack, restart Paper, then rejoin. No config reset is necessary. Inspect a legendary sword/axe hover to verify its actual vanilla enchantment and attack modifiers are shown. Existing items should refresh when held/online.
