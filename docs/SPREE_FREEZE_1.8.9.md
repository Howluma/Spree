# SpreeCore 1.8.9 — Timed Spree Freeze

## What it does

Admins can temporarily **pause all automatic killstreak point changes** server-wide without stopping PvP. While the timer runs:

- Players **do not gain** killstreak from player kills, Hunt/Juggernaut/Blood Moon/Race bonuses, Spree +1 consumables, or Spree Token pickups.
- Players **do not lose** killstreak from PvP deaths, natural/environmental deaths, or the Fractured (-1) penalty.
- Uncredited environmental deaths **do not create new Spree Tokens** while the freeze is active, preventing delayed payout from the freeze itself.
- Existing Spree Tokens stay on the ground if picked up during the freeze (the pickup event is canceled); they are not claimed or consumed.
- Right-clicking a Spree consumable during the freeze **does not consume the item**.
- `/spree withdraw` is blocked so points can't be converted to items and spent.
- Player Kills/Deaths statistics, genuine kill credit, normal PvP combat, ordinary inventory drops, kill/death cosmetic effects, and existing currently active Talents/Privileges remain functional.
- **No streak-ended statistic** is recorded just because the player dies while frozen. Personal-best and previously earned rewards do not change.
- Anti-farming timers are **not used up** by kills made during the freeze, so a valid kill after unfreezing is still eligible under the usual anti-farming rules.
- Explicit staff score edits (admin player GUI, `/spreeset`, `/spreesetall`, `/spreeadmin set/add/remove/fracture/reset`) remain available to override the freeze deliberately.

## Admin controls

### In-game GUI
`/spreeadmin` → **Timed Spree Freeze**. The same menu can be opened through `/spreeadmin` → **Events** → **Timed Spree Freeze**.

The menu displays live status/remaining time and the following one-click presets: **5, 10, 15, 30, 60, or 120 minutes**. Starting another preset replaces the old countdown. **End Spree Freeze** resumes scoring immediately. Only players with `spree.admin` can use this menu.

### Command (console or in-game)
```mcfunction
/spreeadmin freeze 15m
/spreeadmin freeze 30
/spreeadmin freeze 2h
/spreeadmin freeze 90s
/spreeadmin freeze 1d
/spreeadmin freeze status
/spreeadmin freeze off
```

A bare number is interpreted as **minutes**; `s`, `m`, `h`, `d` mean seconds/minutes/hours/days. Maximum duration: **7 days**. Invalid/zero/negative values are rejected without changing the current freeze.

The status is visible in players' killstreak HUDs, admin GUI and command responses. The server broadcasts a start, manual stop or automatic expiry announcement.

## Persistence and expiry

The exact Unix epoch millisecond expiration is stored as `streak-freeze-ends` in the existing `plugins/SpreeCore/events.yml`. A restart does not refresh, extend or cancel the timer. Once the wall-clock end is reached, automatic killstreak changes resume; the one-second event heartbeat broadcasts the end and saves the cleared state.

The freeze is independent from the server's normal timed events and compass modes. It can coexist with Blood Moon, Chaos, etc., though their **streak rewards** do not trigger while frozen.

## Staging checklist (Paper 1.21.11)

1. Back up `players.yml`, `config.yml`, **`events.yml`**, and the previous plugin jar. Install SpreeCore 1.8.9.
2. Set two test players to killstreaks 10 and 15. Open the Admin Center freeze menu and start a 5-minute timer. Verify the HUD/status shows a countdown, and all players see the start announcement.
3. Kill the first player with the second, including a death caused by falls after PvP damage. Verify **neither score changes**, no normal/natural Spree Token spawns, but Kills/Deaths still increment.
4. Die to lava/void/environment from level 0 while frozen. Verify no Fractured (-1) status and no Token.
5. Right-click a +1 Spree item while frozen. Verify **no score gain and no item consumption**.
6. Pick up a pre-existing Spree Token during freeze. Verify the pickup is blocked and the token remains on the ground. Verify fractured recovery cannot be claimed during freeze.
7. Try `/spree withdraw 1`. It must refuse without changing inventory/score. Test bonus streak award events and ensure no points are added.
8. Verify a valid kill right after the freeze expires still awards streak (anti-farm not spent by frozen kills).
9. Restart the server with the freeze running. Verify the timer resumes with the proper **remaining time**, not a fresh 5 minutes.
10. Try `/spreeadmin freeze 90s`, `/spreeadmin freeze status` and `/spreeadmin freeze off` from console and in game. Confirm one-click GUI stop works.
11. While frozen, try `/spreeadmin set <player> 20` or `/spreeset <player> 20`. Admin overrides should work, but subsequent automatic changes still remain paused.
12. Verify kill/death sounds and particles, Flame Rod, TNT ignition, regular fishing-rod assist and previous Fate Sigil rules remain intact.

Automated JVM tests verify duration syntax and caps, expiry and restart-safe absolute expiration, GUI-friendly formatting and the central automatic-vs-admin change policy. Live server tests are still required for actual Paper events and item inventory transactions.
