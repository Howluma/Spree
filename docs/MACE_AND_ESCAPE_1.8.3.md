# SpreeCore 1.8.3 — Mace cooldown visuals and Escape Artist

## Mace Wielder and Mace Mastery

The previous implementation stored a mace smash cooldown privately, with no vanilla darkened mace icon, and accidentally limited ready notifications to Mace Mastery owners. Impact effects appeared around the attacker rather than at the struck target.

- Successful, unblocked mace smashes now show a vanilla shaded mace hotbar cooldown. Default 60s; with Mace Mastery 30s. Duration can still be edited in the balance GUI.
- Regular grounded mace swings continue working while the smash is cooling down. Paper documents that item cooldowns do not by themselves prevent normal attacks.
- The mace retains its native vanilla smash behavior, sound and knockback, with added blue dust shockwave, gust and cloud impact particles centered on the target, plus a subtle extra impact sound.
- All players holding Mace Wielder privilege receive the MACE SLAM READY audio/action-bar cue, even without the Mace Mastery Talent. /spree cooldowns shows the base mace timer too.
- The overlay resumes with the correct remaining time after reconnection.
- The cooldown only starts on a positive-damage, unblocked, uncancelled true smash. Misses, normal mace attacks and shield blocks do not spend cooldown. Accepted Paper smash attempts provide a fallback for third-party damage source changes.
- New settings in combat.mace: cooldown-overlay, impact-particles, impact-sound. Enabled by default.

## Escape Artist

- Checks all cobwebs overlapping the actual player hitbox, including head- and edge-level intersections.
- Triggers proactively when entering a web, even without receiving damage; also checks once a second for webs placed around stationary players.
- Prefers teleporting the player back to their recently safe spot, otherwise searches up to five nearby blocks for a clear two-block-high landing with solid footing.
- Validates the whole player hitbox and path, prevents teleporting through solid barriers or hazards, and respects failed/cancelled plugin teleports.
- On success plays clouds and a teleport chime, cleanses Slowness, grants Speed III for 3 seconds and begins the existing 50-second cooldown.
- If no safe destination exists, the cooldown is not consumed and the player is told why. Moving into an accessible area will allow another attempt.

## Staging server test checklist (Paper 1.21.11)

1. Back up players.yml and config.yml; install version 1.8.3 on the test server.
2. Give one test player Mace Wielder but NOT Mace Mastery; smash a valid target after falling 3 blocks. Verify the blue shockwave and 60s darkened mace icon.
3. Confirm normal grounded mace melee still works during the cooldown, but another airborne smash is prevented.
4. Verify a missed hit and a shield-blocked attack do not start cooldown.
5. Wait for the ready audio/message and confirm /spree cooldowns shows the cooldown even without mastery.
6. Add Mace Mastery; verify new smashes have 30s cooldown and relogging during the cooldown restores the icon.
7. Give Escape Artist and walk directly into a cobweb without taking damage. Verify immediate safe displacement, particles, sound and Speed III.
8. Test webs touching head/edge, multi-web traps and cobwebs placed around a stationary player.
9. Test surrounded by solid walls with no safe exit. Confirm no clipping/teleport into a wall and NO cooldown wasted; open an escape route.
10. Check Slowness-on-hit cleansing and verify other Talents and all previous features remain unchanged.

Automated tests exercise cooldown ticks, accepted smash attempts, hitbox intersection and bounded exit searches. Actual Paper combat and teleports still require in-game validation.
