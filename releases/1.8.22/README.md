# SpreeCore 1.8.22 — Legendary visuals & tooltips

The distributed `SpreeCore-1.8.22.jar` adds the following changes to the prior 1.8.21 binary.

- All seven real legendaries suppress their **visual enchantment glint**, without removing enchantments or affecting ordinary enchanted equipment.
- Copper item identities and existing weapon models are preserved.
- Existing owned legendary item lore, tooltip style and glint are refreshed on player reconnect.
- Blood Butcher actions: **Offhand** for Blood Rush, **Sneak + Offhand** for Crimson Cleave.
- Other currently implemented legendary actives use **Sneak + Offhand**. The old ability activation on Sneak + Use is bypassed; ordinary item use (including Hailstorm's crossbow firing) is preserved.
- Tooltips use active/passive sections and action names instead of fixed keyboard shortcuts.
- The companion resource pack is named `SpreeCore-Resource-Pack-1.8.22.zip` and contains the original authentic Blockbench models and Pantheon-derived tooltip frames.

## Deployment

For Paper 1.21.11, replace the previous JAR with `SpreeCore-1.8.22.jar`, deploy the matching resource pack and restart the server. Back up player/plugin data first; reconnect players to refresh existing items.

## Source provenance

**Important:** This is a changelog for the separately distributed 1.8.22 JAR, not a claim that the repository's older main-branch Gradle source now builds that exact JAR. The binary was patched from 1.8.21 by updating `LegendaryCopperBridge` and `BloodButcherTierBridge`. A full source reconciliation with main is still needed before the current Gradle project can reproduce the release.

Local checks: Java 21 helper compilation; mock glint/tooltip/action checks; JAR class-file verification and ZIP integrity. **Live Paper multiplayer testing has not been performed.**
