# SpreeCore v1.8.6 — personal kill/death sound effects

This update adds short, recognizable combat cues without resource-pack audio replacements or extra dependencies.

## Kill confirmation

When **SpreeCore credits you for a player's death**:
- Only you hear a two-tone triumph sound: Minecraft's player-level-up sound plus a bright amethyst chime.
- A short, visible burst of navy and ice-blue dust rings, soul flames and End Rod sparks appears at the opponent's death location.
- Players near that location see the particles, but do not hear your personal victory sound.
- This is executed in the same PlayerDeathEvent flow as SpreeCore's existing kill credits. Thus a fall/lava/void/etc. finish attributed to recent PvP counts as a kill; an uncredited environmental death or suicide does not create a false kill effect.
- By default a successfully credited kill plays feedback even when existing anti-farm rules block the killstreak reward. To suppress celebratory effects on anti-farm-blocked kills, enable `combat-feedback.kill.only-rewarded`.

## Personal death cue

Every time your own player dies, only your client hears:
- A deep Beacon-deactivation sound
- A quieter Warden heartbeat for a descending, dark electronic sting

This works for direct PvP, environmental hazards, and deaths to mobs, without adding death particles or extra loss mechanics. Nearby players do **not** hear the new custom death cue.

## Configuration

```yaml
combat-feedback:
  enabled: true
  kill:
    enabled: true
    sounds: true
    particles: true
    volume: 0.8
    ring-points: 16
    only-rewarded: false
  death:
    enabled: true
    sounds: true
    volume: 0.75
```

`ring-points` is clamped to 8–32 points per ring; `volume` is clamped to 0–2 to prevent misconfiguration from crashing the server. This uses built-in vanilla Minecraft sounds and particles: the **complete** SpreeCore resource pack from v1.8.5 is unchanged and remains compatible.

## Quick staging checks on Paper 1.21.11

1. Back up `plugins/SpreeCore/config.yml` and `players.yml`. Install `SpreeCore-1.8.6.jar`.
2. Kill a second test player in direct PvP. The killer alone should hear the positive two-tone sound and everyone near the victim should see a blue ring and short sparks.
3. The victim alone should hear the dark death sting. Other nearby players should not hear that extra death audio.
4. Hit a player then let them fall into lava, void or onto the ground within the configured PvP credit window. Verify the killer receives the same elimination effect, while the victim hears the death sting. The natural-death token should not drop.
5. Die without another player hitting you. Verify only the death sting sounds; no kill celebration appears.
6. Test repeated killing of the same player within the anti-farm window. With `only-rewarded: false`, it still plays cosmetic effects; switch to `true` and verify blocked rewards no longer trigger the celebration.
7. Toggle `kill.particles`, `kill.sounds`, and `death.sounds` independently, and test volume changes.
8. Check gameplay invariants: old killstreak rewards, Fractured (-1), kill credit, no extra items, Mace Wielder, Fate Sigils, Flame Rod, and all existing custom models remain unchanged.

Automated tests verify feedback eligibility, anti-farm optional gating, sound-volume and effect-size safety, and default config. Client-side sound audibility requires live server testing.
