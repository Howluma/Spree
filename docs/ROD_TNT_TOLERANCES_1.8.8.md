# SpreeCore 1.8.8 — increased fishing rod and TNT collision tolerance

## What changed

This patch makes fishing rods a little more forgiving without changing **actual entity hitboxes**, melee reach, knockback, damage or the vanilla TNT explosion radius.

| Bobber interaction | v1.8.7 default | v1.8.8 default |
|---|---:|---:|
| Hooking another player | +0.20 blocks outside each hitbox face | **+0.30** |
| Hooking dropped items | +0.25 blocks outside each hitbox face | **+0.35** |
| Hooking a *primed TNT entity* | no additional assistance | **+0.40** |
| **Flame Rod** bobber igniting a **TNT block** | +0.35 blocks outside the block | **+0.50** |

There are two distinct TNT mechanics: an unlit TNT **block** becomes primed when Flame Rod's burning bobber gets close to it, and an already-lit/primed TNT **entity** can be *hooked and reeled* with an ordinary rod. The latter is now an eligible target for SpreeCore's swept bobber hit detection, with an extra 0.40-block catch margin; this does not grant any additional fire or explosion damage.

## Existing installations and configuration

New installations receive all defaults from the updated config.yml.
Existing installations using the *exact previous stock defaults* automatically migrate **0.20 → 0.30**, **0.25 → 0.35**, and **0.35 → 0.50** at startup/reload. Admin-customized values different from the old defaults are preserved. The newly introduced primed TNT margin defaults to 0.40 on all installations.

Adjust in `plugins/SpreeCore/config.yml` or via the admin settings UI:

```yaml
rod-assist:
  enabled: true
  player-extra-margin: 0.30
  item-extra-margin: 0.35
  primed-tnt-extra-margin: 0.40
  max-sweep-blocks-per-tick: 4.0
  respect-pvp-settings: true
  respect-friendly-fire: true

talents:
  flame-rod:
    contact-tolerance: 0.50
```

The three catch margins can be customized between 0 and 0.50 blocks. The Flame Rod TNT ignition tolerance can be customized between 0.05 and 0.70 blocks. Fire damage, the four-second fuse and one-TNT-block-per-cast rules remain unchanged.

## Staging-server checklist (Paper 1.21.11)

1. Back up `plugins/SpreeCore/config.yml` and `players.yml`. Install `SpreeCore-1.8.8.jar` and restart the server.
2. With an ordinary fishing rod, cast *just beside* a player. Verify hits that missed with the old +0.20 block margin more often attach at the new +0.30. Test from different angles, while retaining no hooking through solid walls.
3. Test dropped items with a near-miss bobber at +0.35 blocks.
4. Ignite a **TNT block** with a Flame Rod bobber. Cast slightly past the block's edge: the new +0.50 radius should still ignite it, but a bobber passing a whole block away must not.
5. Prime TNT and use any normal rod to hook the **moving primed TNT entity**. Verify the new +0.40 extra margin improves catching. The standard vanilla reel-in should still pull TNT toward the caster.
6. Test a protected TNT area; plugins that cancel `TNTPrimeEvent` should still prevent block ignition.
7. Set `rod-assist.enabled: false` and verify that the added target catching stops; vanilla fishing still works normally. Setting `primed-tnt-extra-margin: 0` should remove only the **additional** assist for primed TNT, not disable native rod fishing.
8. With PvP disabled / scoreboard friendly fire off, verify the player hook assist does not bypass those settings.
9. Change each margin through the admin settings menu and reload. Reconnect to check the settings persist.
10. Verify that Flame Rod fire visual effects and other SpreeCore 1.8.7 features are unchanged.

JUnit regression tests cover new defaults, legacy config migrations, edit bounds, player/item/primed-TNT catch geometry and TNT ignition distances. Actual Paper entity hook interactions, client rendering, and third-party protection hooks require live server testing.

**No separate SpreeTweaks Paper plugin:** the increased rod hit detection is inside SpreeCore. The SpreeTweaks 1.1 datapack is unchanged and continues to handle only the original non-merchant datapack tweaks.
