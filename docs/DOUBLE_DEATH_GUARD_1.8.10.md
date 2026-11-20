# SpreeCore 1.8.10 — Prevent double death penalties (including TNT minecarts)

## Root-cause assessment
The existing SpreeCore PlayerDeathEvent handler used the **current saved killstreak** every time the handler was called. There was no per-life transaction guard. If the death callback was invoked twice before a player respawned, a score of **20 → 10 → 5** would be possible. This could also double-count deaths, create two environmental Spree Tokens, or award the killer twice.

A TNT minecart explosion is a possible trigger for a reported incident, but it has not been proven to be the cause. Paper normally delivers one PlayerDeathEvent per actual player death. An old concurrently running Skript or another killstreak plugin can independently reduce or display a different score. This patch guards SpreeCore's internal handler and logs useful evidence without blaming a specific mechanism.

## Fix: one immutable death transaction per player life

- A `DeathLifeGuard` begins **before** resolving combat credit, playing sounds, modifying scores, counting deaths, or spawning items.
- It saves the **pre-death streak** for the current player life; all penalty calculations use this original score.
- A duplicate PlayerDeathEvent before respawn is ignored entirely; it cannot halve again, increment deaths/ended, award kill score, consume anti-farm eligibility, play another kill/death sound, or drop another natural-death token.
- Only a **real PlayerRespawnEvent** re-arms the guard for the next life. Reconnecting while still dead does not clear it; reconnecting alive repairs a stale guard. Multiple *real* deaths after separate respawns are still treated normally.
- The rule applies to normal PvP, fall/void/lava, explosions and TNT minecarts, and while the global Timed Spree Freeze is running. Existing Fate Sigils, death insurance, combat credit, TNT rules, and normal rewards remain unchanged.

## Diagnostics and compatibility with Skript

Add/edit in `plugins/SpreeCore/config.yml`:

```yaml
killstreak:
  death-processing:
    warn-duplicate-events: true
    log-transitions: false
```

- A second death callback from the same player life logs **one warning**, including username, original streak, current streak, original and duplicate server ticks, last damage cause and damaging entity type. Example: `[DeathGuard] Ignored duplicate player death ... cause=ENTITY_EXPLOSION source=TNT_MINECART`.
- Set `log-transitions: true` temporarily to log every accepted death, including cause, source, score before halving and credited attacker. Return it to `false` after diagnosing.
- If the plugin detects **Skript enabled** with an old `plugins/Skript/scripts/killstreak.sk` (including within a subfolder to depth 3), it logs a warning to check whether the Skript is running. It does **not** disable other plugins or scripts.
- If the score still changes twice but no `[DeathGuard] Ignored duplicate` warning appears, check for the legacy Skript and any other external score-changing plugins or admin commands. SpreeCore cannot cancel a completely separate plugin's scoreboard updates.

## Quick reproduction / staging checklist (Paper 1.21.11)

1. Back up `plugins/SpreeCore/players.yml` and `config.yml`. Install the 1.8.10 JAR.
2. Use `/spreeadmin set <player> 20`. Kill that player once in ordinary PvP. Their killstreak must be **10**, not 5; death counters increment only once, and the killer is rewarded only once.
3. Set the score to 20 again; kill them with TNT minecart explosions, optionally chaining multiple carts. A single actual death must result in **10**, regardless of the number of explosion hits in that life. Confirm no duplicate kill sound, second payout, or extra natural-death token.
4. Repeat an unattributed environmental death (no credited player): a 20 streak must become 10, producing **one** Spree Token with the usual payout.
5. Restart with `log-transitions: true` to see the accepted death's damage cause and source in console. If a duplicate event happens, also check for the one-time warning with two server tick values.
6. After clicking Respawn, a **second real death** should halve the new score again as normal (e.g., 10 → 5). This is legitimate and not considered a duplicate.
7. Test quickly logging out while on the death screen and reconnecting: the first death's penalty must not repeat before respawn.
8. Test the global `/spreeadmin freeze 5m`; one or multiple death callbacks must not decrease a score, mint tokens or double-count the death statistic. Disable via `/spreeadmin freeze off`.
9. Check other progression features, skill cooldowns, Death Insurance, TNT attribution, and kill/death feedback for regressions.
10. If the score still halves twice, check console for a `[DeathGuard]` warning and make sure an old `killstreak.sk` or separate killstreak implementation isn't still running.

## Automated coverage
`DeathLifeGuardTest` includes 20→10→5 double-halving reproduction, same/later tick duplicates, rearming on respawn, not rearming on death-screen reconnection, safe cleanup and frozen-death cases. `DefaultConfigTest` validates diagnostics defaults. The JUnit suite and Gradle compilation can run in GitHub Actions; event-level behavior still requires live staging verification.
