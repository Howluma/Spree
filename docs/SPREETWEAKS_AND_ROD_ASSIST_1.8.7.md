# SpreeTweaks datapack + built-in SpreeCore rod assist (Paper 1.21.11)

This release has exactly **two server components**:
1. **SpreeTweaks** — the renamed Minecraft datapack (replaces HowlumaTweaks).
2. **SpreeCore 1.8.7** — the existing Spree SMP Paper plugin, with improved fishing-bobber catching built in.

No second `SpreeTweaks.jar` is needed or should be installed.

## SpreeTweaks datapack

`SpreeTweaks-Datapack-1.0.zip` preserves the original HowlumaTweaks spear changes, vault/trial spawner changes, and existing loot/enchants. It removes all three original villager/wandering-trader trade rewrite functions plus their scheduling and reload wiring. The old datapack's `data/howluma` namespace is now `data/spreetweaks`.

**Important:** Stop your server and **remove the old HowlumaTweaks datapack ZIP** from `world/datapacks/` before installing SpreeTweaks. Otherwise the old pack could still be modifying merchants, and both packs could run overlapping changes.

Removing the old datapack stops further forced one-emerald trades; it cannot restore the original price of a villager that was already modified and saved with a custom trade.

## SpreeCore rod assist

`SpreeCore-1.8.7.jar` contains `RodAssistManager` and `RodGeometry`. On a successful cast, the manager tracks each flying `FishHook` every server tick.

- The bobber's **effective catch margin** is expanded slightly around **other players** (default +0.20 blocks each side) and **dropped item entities** (default +0.25 blocks each side).
- Catching targets is based on the **swept path** of the bobber between ticks, not nearest player within a large radius. It selects the first encountered eligible entity.
- Checks line of sight through solid blocks to prevent hooks crossing walls.
- Uses Paper `FishHook.setHookedEntity` to preserve vanilla reel-in and pulling behavior. It does **not** change the actual player/item hitbox, melee reach, rod damage, enchantments, or knockback.
- Honors PvP-disabled worlds, spectator status, scoreboard teams with friendly fire disabled, and invalid/dead entities.
- Applies to **all fishing rods**, including the Flame Rod Talent; TNT ignition remains the separate Flame Rod effect, and the rod assist adds no fire or TNT by itself.
- The sweep scan is bounded, uses only currently active bobbers, and cleans up on hook retrieval, disconnect or plugin shutdown.

### Admin configuration

Change these in `plugins/SpreeCore/config.yml` and reload with `/spreeadmin reload`, or use `/spree` > Admin Center > settings > **Fishing Rod Hitbox Assist**:

```yaml
rod-assist:
  enabled: true
  player-extra-margin: 0.20
  item-extra-margin: 0.25
  max-sweep-blocks-per-tick: 4.0
  respect-pvp-settings: true
  respect-friendly-fire: true
```

Margins are limited to 0–0.50 blocks; sweep length 0.5–5 blocks. To restore vanilla hooking without uninstalling SpreeCore, set `rod-assist.enabled: false`.

## Staging testing (Paper 1.21.11)

1. Back up `world/datapacks/`, `plugins/SpreeCore/config.yml` and `players.yml`. Remove old HowlumaTweaks pack. Install SpreeTweaks zip and SpreeCore 1.8.7 JAR.
2. Run `/datapack list` and ensure the new `SpreeTweaks` pack is enabled and the old `HowlumaTweaks` pack is absent.
3. Test new villager and wandering-trader prices: there must be no forced one-emerald trade rewrite. Existing customized villagers may retain old prices in world data.
4. With default rod assist, cast just beside the torso of a standing player. Compare with `rod-assist.enabled: false`: narrowly missed casts should more often attach to the target when enabled.
5. Cast just beside a dropped item and reel it in. It should hook with slightly more tolerance while still obeying normal pulling mechanics.
6. Cast toward a player/item behind a solid wall. The bobber must not attach through the wall.
7. Test in a PvP-disabled world and while on the same scoreboard team with friendly fire off. Fishing rod assist must not hook players improperly.
8. Test with Flame Rod Talent: fishing bobber flames/TNT behavior should remain unchanged while its rod can also hook players and items more easily.
9. Modify the two margins through the admin settings GUI and verify `/spreeadmin reload` picks up any manual edits.
10. Confirm the datapack's preserved spear and trial-spawner/vault changes still behave as expected.
11. Confirm no standalone SpreeTweaks Paper companion plugin is installed; the improvement comes from SpreeCore.

Automated tests cover swept-AABB intersection, narrow misses made into valid catches, no unbounded sweep settings, and config-range validation. Native fishing-hook behavior and datapack gameplay require live server verification.
