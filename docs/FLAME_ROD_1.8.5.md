# SpreeCore v1.8.5 — Flame Rod replaces Barbed Hook

## Behavior

**Flame Rod** replaces the Barbed Hook Talent completely (22 Talents total, six Talent milestones unchanged):

- Qualifying players' fishing rods temporarily gain the **Flame I** enchantment and its visible enchanted-item glint. This is visual; fishing rods do not acquire any bow-only vanilla shooting behavior.
- On casting, the **bobber visibly burns** using Paper's visual-fire flag and receives a short continuous flame-particle trail. Fire disappears when reeled in, despawned, or the Talent deactivates.
- When the moving bobber **touches a TNT block**, the block is primed with vanilla TNT behavior and a default **80-tick fuse**, with the player registered as the TNT source for explosion kill attribution.
- Before priming, SpreeCore fires Paper's cancellable **TNTPrimeEvent (PROJECTILE)**. If another plugin cancels the event, TNT stays intact. No fire blocks are placed, nothing nearby ignites automatically, and players/mobs take **no damage just from being hooked**.
- By default each cast can ignite **one TNT block**; configurable through `talents.flame-rod.max-ignitions-per-cast`. Chain-reaction TNT follows normal Minecraft rules.
- Enchantment is **temporary and reversible**. The plugin tags only Flame it created, preserves other enchantments and custom item data, strips the added Flame when rods leave an eligible player's inventory (including drops, container transfers, deaths, logout, plugin shutdown), and does not alter third-party Flame-enchanted rods.
- A dropped or transferred fishing rod never becomes a permanently enchanted Flame Rod just because it was carried by a player with the Talent.

## Existing server/player migration

When `players.yml` is loaded:
- Previously selected `talent-choices.<level>: barbed_hook` becomes `flame_rod` at the **same earned milestone**.
- Saved three-Talent offers including `barbed_hook` become `flame_rod`, with no extra Fate Sigil cost or random reroll. Other offers and earned rewards are retained.
- `talents.barbed-hook` settings are retired; new `talents.flame-rod` settings default to enabled.
- Existing Fate Sigils, perks, privileges, infusions, and streaks are unchanged.

## Options

```yaml
talents:
  flame-rod:
    enabled: true
    bobber-particles: true
    cast-sound: true
    contact-tolerance: 0.35
    max-ignitions-per-cast: 1
    tnt-fuse-ticks: 80
```

## Live Paper 1.21.11 test checklist

1. Back up `plugins/SpreeCore/players.yml` and `config.yml`. Replace the JAR and install the **complete** updated SpreeCore pack (not layered with older versions).
2. Assign Flame Rod in the admin **Set Talent** GUI. Verify the Fishing Rod shows **Flame I** and glints, while other enchantments/custom names are preserved.
3. Cast into open air. Verify the bobber visibly burns and emits flame particles along its motion. Reel in and verify the flames stop.
4. Cast a bobber at the side or top of a TNT block. Confirm it primes with approximately a four-second fuse and a standard explosion.
5. Put another TNT block within one block of the first; verify the standard explosion chain, not additional free hook ignition (default max one per cast).
6. Verify hitting another player with a bobber does **not** cause the former Barbed Hook half-heart damage.
7. In a claimed/protected area, have a protection plugin cancel TNTPrimeEvent. The block must remain TNT and cannot ignite; test with the actual protection plugins installed on the server.
8. Try dropping a temporarily enchanted rod, putting it in a chest, trading it, logging out, and deactivating the Talent (lowering current streak below milestone): verify **the plugin-added Flame I is removed**.
9. Try a fishing rod that was enchanted with Flame I by another plugin: verify its original enchantment is not stripped.
10. Prime TNT with Flame Rod and damage an opponent: verify the rod owner can receive explosion kill credit under the existing anti-farm and combat rules.
11. Test a returning player previously saved with Barbed Hook: confirm their original Talent milestone is now Flame Rod and they weren't given a free duplicate Talent.
12. Confirm the other SpreeCore items, all resource-pack models, Fate Sigil recipe, Haste Infusion, mace effects and web escapes still work.

GitHub Actions tests cover TNT proximity and path sampling, default configuration, Talent catalog consistency and migration. Client rendering and protection plugin integrations still require testing on an actual Paper server.
