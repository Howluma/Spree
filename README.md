# SpreeCore v1.4.3 — Spree SMP

One native Java 21 plugin for Paper **1.21.11**, replacing the original killstreak, legendary weapon, forge, and event Skripts. The source code and CI builds are maintained at https://github.com/Howluma/Spree.

## Upgrade and installation

1. Back up the world and `plugins/SpreeCore/` (especially `players.yml` and `config.yml`). Test on a **separate server** first.
2. Stop Paper, replace the existing SpreeCore JAR with `SpreeCore-1.4.3.jar`, and keep the ten original `.sk` scripts disabled to avoid double-handling events.
3. Start the server with Java 21. SpreeCore upgrades the existing configuration on startup without resetting player profiles. Old passive/potion/item-lock settings are intentionally deleted from the active YAML and editor.
4. Use `/spree settings` or `/spreesettings` as an operator, or with `spree.admin`, to inspect/edit all active settings. `/spreecore:spree settings` works when a separate plugin owns `/spree`.
5. Test the new hearts and mace gate in-game before deploying to a live SMP. Compilation and automated tests do not substitute for real-server testing.

Build yourself: `gradle clean test build` with Java 21 and Gradle 8.14+. The JAR appears at `build/libs/SpreeCore-1.4.3.jar`. GitHub Actions publishes the build artifact after each push.

## Interim killstreak progression (v1.4)

There are exactly **two default progression effects** until the progression redesign is decided:

| Killstreak | Health | Mace attacks |
| --- | --- | --- |
| Fractured (`-1` internally) | 10 hearts | Blocked |
| 0 | 10 hearts | Blocked |
| 1 | 10.5 hearts | Allowed |
| 2 | 11 hearts | Allowed |
| 3 | 11.5 hearts | Allowed |
| 10 | 15 hearts | Allowed |
| 30 (default cap) | 25 hearts | Allowed |

`killstreak.base-health-hearts: 10`, `killstreak.health-per-level-hearts: 0.5`, and `killstreak.mace-unlock-level: 1` are visible and adjustable in the admin GUI. Every **positive** killstreak level grants half a heart; Fractured is treated as zero for health, not as a penalty. There is no previous 20-heart cap cutting off the progression. The health calculation uses double-precision health points, allowing half-heart changes.

At zero or Fractured, direct mace attacks against **players and mobs** are cancelled, and Paper mace smash attempts are denied. Mace attacks are available after reaching the configured unlock level (1 by default). Once unlocked, mace damage to **players** retains the existing 75% multiplier, adjustable under Combat Balancing. TNT igniter self-damage still defaults to 50%, also adjustable.

**Removed for now:** streak-dependent automatic Fire Resistance, Hero of the Village, Weaving, Speed, Glowing, potion upgrades, and all tentative item restrictions. Their old GUI categories and YAML settings are removed. This does **not** remove independent legendary weapon abilities, forge requirements, event-only Juggernaut health/glowing, or one-off event streak points. The old Spree Surge event, which relied on tier-based buffs, is paused pending the progression redesign.

**Unchanged streak rules:** PvP and environmental deaths halve an existing positive streak, rounded down. Environmental death at 0 creates Fractured (`-1` in `players.yml` for migration compatibility); Fractured never goes below `-1`. The anti-farming cooldown, revenge bonus, item use, and death-token logic stay intact. `/spreeadmin giveitem <player> [1–64]` gives a Heart of the Sea Spree consumable; right click consumes one and adds exactly one killstreak. Original environmental-death tokens auto-claim on pickup using separate logic.

## Dark-blue theme and tiered killstreak colors

SpreeCore preserves the navy UI and the blue sword symbol (`&9`, matching menu headings), but **numbers in the tab list, overhead nametags, HUD, `/killstreak`, `/stats`, `/leaderboard`, and player manager change color every five levels**, as in the original Skript:

| Streak | Number color |
| --- | --- |
| 0-4 | Gray (`&7`) |
| 5-9 | Green (`&a`) |
| 10-14 | Yellow (`&e`) |
| 15-19 | Gold (`&6`) |
| 20-24 | Red (`&c`) |
| 25-29 | Purple (`&5`) |
| 30+ | Dark red (`&4`) |

Numbers remain bold in tab and overhead names; Fractured keeps a separate red symbol, never displaying `-1`. Admins can change individual tier colors in `/spree settings` > Tab & Nametag Indicators. The setting accepts single vanilla chat color codes (`&0`-`&f`) or hex (`<#RRGGBB>`), including updates with existing user configs. The sword icon uses the same brighter blue `&9` as the action bar and menu headings, including at zero and Fractured. The tab list alone uses a dark-gray `|` separator between player name and streak (e.g. `Howluma | ⚔ 12`). The overhead nametag and action bar do not include the separator. Bold numbers still change color every five kills. The overall GUI remains dark blue.

Overhead tags use scoreboard suffixes only where an existing team does not own the player, preserving friendly-fire/collision rules of other plugins.

## Commands

| Command | Description |
| --- | --- |
| `/spree` | Regular dashboard; admin buttons only if permitted |
| `/spree settings`, `/spreesettings`, `/spreeconfig` | Config editor (`spree.admin`) |
| `/spreeadmin` | Admin player browser, events, balancing, weapons |
| `/spreeadmin set/add/remove/reset/fracture <player> [amount]` | Player killstreak controls |
| `/spreeadmin giveitem <player> [amount]` | Give Spree consumables |
| `/spreeadmin update [player]` | Recalculate current health |
| `/spreeadmin reload` | Reload changes made to config.yml manually |
| `/spreeadmin importskript [force]` | Import original Skript variable CSV (after backup) |
| `/spreehud`, `/killstreak`, `/stats`, `/leaderboard`, `/spreeinfo` | Player stats and status |
| `/spreeevent`, `/legendary` | Admin event management and legendary tools |

In the settings GUI: left/right clicks adjust numbers, shift-left enters an exact value via chat, shift-right restores the default. The editor validates range and type, saves instantly, and reapplies health when progression settings change. All 1.4 active scalar config values are exposed; the retired ones are not.

## Systems deliberately preserved

- All seven legendary weapons and their abilities, model data and forged item IDs; temporary event mechanics remain separate from the passive killstreak progression.
- Resource-pack model selectors: players with the matching pack see custom weapons and Spree items; others see the native vanilla item textures.
- Player data keyed by UUID, anti-farming history, best streaks, kills/deaths, recent victims, and temporary Fractured status.
- TNT self-damage and explosive killer attribution; keep the previously established fallback rules for chain explosions and unknown igniters.
- Hunt, Juggernaut, Blood Moon, Chaos and Kill Race events. **Spree Surge is paused** because its previous buffs belonged to the removed streak progression.
- Forge timers, persistence, recipes, and progression-independent weapon effects.

## Compatibility and verification

This build targets Paper 1.21.11 only. The unit tests cover fractional hearts, Fractured health, mace thresholds, config keys, and indicator formatting. The JAR should compile successfully through GitHub Actions; check the corresponding run before using it. There is still an **in-game test requirement** for mace smash/wind-burst interactions, attribute modifiers from other plugins, spectator/team scoreboards, and health synchronization on death/rejoin. If a plugin also sets a player's maximum health, check the interaction in your test server.
