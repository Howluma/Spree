# SpreeCore v1.8.23 — Legendary tooltip progression and Blood Butcher balance

**Server build:** Paper 1.21.11  
**Files:** `SpreeCore-1.8.23.jar` and `SpreeCore-Resource-Pack-1.8.23.zip` (distributed as conversation attachments)

## Player-facing changes

- The item previously displayed as **Blood Butcherer** is now **Blood Butcher**. Its internal persistent-data ID remains `butcherer`, so old axes and admin item identifiers remain compatible.
- All seven legendary item names are bolder and brighter, the original custom legendary tooltip border is preserved, and visual enchant glint is suppressed without removing enchantments.
- Lore removes the redundant **Relic** and **Test Copy** lines. Actual persistent item markers still exist in NBT/PDC.
- A player below an ability's unlock threshold sees **only the required killstreak**. Once unlocked, they see its ability description and action. Item lore is refreshed on join and during the online legendary tick.
- Text refers to **Offhand** and **Sneak + Offhand** rather than fixed keybindings.
- Locked tiers are 3, 5 and 7 for Blood Butcher. The existing non-Blood Butcher legendary actives now unlock at killstreak 3; their previous passives remain available at 0. No unimplemented 5/7-tier effects were invented.
- The Crimson Cleave item tooltip describes **4 hearts**, without exposing the "true damage" implementation detail or the internal lifesteal healing cap.

## Blood Rush mechanics

Blood Rush now applies a **transient -10-health-point max-health modifier** for 10 seconds instead of directly damaging the player by 5 hearts. Strength III remains. Clamped health is restored when the effect ends without erasing damage taken during the effect. The modifier is cleaned up on logout, death and plugin disable; old expiry callbacks cannot end newer casts. The default Blood Rush cooldown is increased from 35 to **50 seconds**.

New default config keys:
- `legendary.active-unlock-killstreak: 3`
- `legendary.weapons.butcherer.blood-rush-cooldown-seconds: 50`

Existing config files can remain in place; omitted keys use these defaults.

## Tests and compatibility

- Compiled modified helper sources using **Java 21**.
- Unit checks: ability visibility at killstreaks 0, 1, 2, 3, 4, 5, 6, 7, 10, 30; all seven legendary names/glint/tooltip styles.
- Mock integration tests: transient max health, proper end-of-effect health restoration, cooldown, logout, disable, and unlock gating.
- ASM check: **107 valid class files, zero invalid**.
- JAR and pack ZIP integrity/JSON checks: passed; the original **43 model/texture/metadata asset files** are preserved byte-for-byte.
- **Not tested on a live Paper multiplayer server.**

### Known limitation

Vanilla Minecraft stores hover lore on the item, not separately for each viewer. This release updates the **holder's inventory item** according to that player's killstreak. When inspecting shared containers, different viewers may temporarily see the last lore written to that physical item. True per-viewer hover content would require additional packet-layer handling.

### Source provenance

The distributable JAR is an **incremental patch of SpreeCore v1.8.22**. The main-branch Gradle source tree is from an earlier version and **is not yet a complete reproducible source for this JAR**. This release document tracks the binary changes without implying that the older main branch was fully reconciled.

SHA-256 of `SpreeCore-1.8.23.jar`: `b8c3ab34021a2f0ac9a7bea50b21bd89bb2c11ae47c16504df72bcf882596e73`  
SHA-256 of `SpreeCore-Resource-Pack-1.8.23.zip`: `29aeb32c078c82e8447ee54e0baa50feff146731af1794dfd19ad7075549f9d0`.
