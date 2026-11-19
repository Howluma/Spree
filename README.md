# SpreeCore v1.5.4 — Spree SMP

One native Java 21 plugin for Paper **1.21.11**, replacing the original killstreak, legendary weapon, forge, and event Skripts. The source code and CI builds are maintained at https://github.com/Howluma/Spree.

## Upgrade and installation

1. Back up the world and `plugins/SpreeCore/` (especially `players.yml` and `config.yml`). Test on a **separate server** first.
2. Stop Paper, replace the existing SpreeCore JAR with `SpreeCore-1.5.4.jar`, and keep the ten original `.sk` scripts disabled to avoid double-handling events.
3. Start the server with Java 21. SpreeCore upgrades the existing configuration on startup without resetting player profiles. Old passive/potion/item-lock settings are intentionally deleted from the active YAML and editor.
4. Use `/spree settings` or `/spreesettings` as an operator, or with `spree.admin`, to inspect/edit all active settings. `/spreecore:spree settings` works when a separate plugin owns `/spree`.
5. Test the new hearts and mace gate in-game before deploying to a live SMP. Compilation and automated tests do not substitute for real-server testing.

Build yourself: `gradle clean test build` with Java 21 and Gradle 8.14+. The JAR appears at `build/libs/SpreeCore-1.5.4.jar`. GitHub Actions publishes the build artifact after each push.

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

## Official resource-pack fallback (v1.5.3)

Real SpreeCore items keep the default vanilla item_model (Diamond Sword,
Diamond Axe, Crossbow or Heart of the Sea), and set only a custom_model_data
float selector. The official texture pack's minecraft/items definitions
render those exact items with custom models. Players without the pack see
ordinary vanilla items; SpreeCore never points their clients to a model
that does not exist on the client.

Any explicit item_model override on a recognized SpreeCore item is removed
when it is generated or migrated, preserving its actual mechanics. Legendary
inventory items are migrated on join and by /spreeadmin migrateitems; owned
Spree consumables/tokens are normalized on join.

IMPORTANT: Old /give preview items that directly set item_model="howluma:..."
are visual tests, not functional SpreeCore items, and will show missing
models without the pack. Use the NEW safe CMD-only preview commands in the
official resource pack, or issue real items with SpreeCore.

## Player Spree withdrawal (v1.5.2)

Use `/spree withdraw` to convert **all** your positive killstreak levels into
the normal right-click +1 Spree consumables. An optional amount withdraws
only that many levels: `/spree withdraw 5` at streak 30 creates 5 items
and lowers your streak to 25. `/spree withdraw all` is equivalent to
`/spree withdraw`. Redeeming each consumable grants exactly +1 again.

- Zero and Fractured players cannot withdraw. A withdrawal cannot exceed
  the player's available positive streak or accept negative/zero quantities.
- The transfer uses inventory storage only, merging matching Spree items.
  It does **not** spill items onto the ground; insufficient storage space
  cancels the entire transfer without subtracting any streak.
- Health, mace eligibility, milestone buffs and indicators refresh after
  deduction. Best lifetime streak and statistics are not reset.
- Every player has the `spree.withdraw` permission by default. Server
  operators can toggle `killstreak.withdrawal.enabled` in
  `/spree settings` > **Spree Withdrawal**.
- If another plugin intercepts `/spree`, use
  `/spreecore:spree withdraw` instead.

## Kill rewards and TNT credit (v1.5.1)

The original Skript limited kill rewards to **+2**, even when the victim had
a 30 killstreak. That cap is removed: **a qualifying player kill now awards
half of the victim's PRE-DEATH streak, rounded down, with a minimum of +1**.
This rule is independent of the damage source.

| Victim's streak before death | Killer gains | Victim after death |
| --- | --- | --- |
| 0 / Fractured | +1, subject to anti-farm restrictions | 0 / Fractured |
| 3 | +1 | 1 |
| 7 | +3 | 3 |
| 10 | +5 | 5 |
| 20 | +10 | 10 |
| 30 | +15 | 15 |

For a TNT kill credited to a **Fractured** player against a player at streak 30,
the killer is set to **15**, not 2; the victim goes from 30 to 15. The anti-farm
cooldown and ban on farming Fractured victims remain, as do event-specific
bonuses and the configured maximum-streak cap. Environmental-death Spree
Tokens use the same half-streak value and remain separate from the
right-click **+1 Spree item**.

When an explosion is attributed to an online player, the normal player-kill
branch awards streak and does **not** drop an environmental-death token.
Ownerless or ambiguous explosions stay uncredited, and a natural death can
still drop a token. If TNT behavior differs during live testing, note whether
you received a "for eliminating" message or picked up a dropped Spree Token;
those distinguish credited player kills from environmental deaths.

## Current streak milestone buffs (v1.5.0)

A player's killstreak provides +0.5 heart for each positive level. Level 1 unlocks
mace attacks. The following buffs last as long as the player's streak meets the
configured threshold (they are refreshed automatically):

| Killstreak | New buff |
| --- | --- |
| 5 | Weaving (level I) |
| 10 | Speed I |
| 15 | Strength II |
| 20 | Glowing, colored by the five-kill tier |

For glowing, level 20-24 defaults to red, 25-29 to purple, and 30+ to
dark red. Vanilla scoreboard team colors support only named colors; RGB
hex customization is approximated to the nearest named color. Existing team
membership controlled by another plugin takes precedence to preserve
friendly-fire, collisions, and other team rules.

`/spree` now shows a compact **Current Streak Buffs** book with just the
buffs that player already has; no future unlocks are listed.

Fractured remains `-1` internally for compatibility but is shown as
**Killstreak: Fractured** in the action bar. The v1.4.3 default label migrates
automatically; custom server labels are preserved.

The four milestone levels are editable through `/spree settings` >
**Milestone Buffs**. A lost streak removes only SpreeCore-owned buffs;
independent potions and stronger legendary abilities are not intentionally removed.

## Invisibility and tab-list privacy (v1.5.4)

While a player has the **Invisibility potion effect**, SpreeCore keeps their
previously displayed killstreak number and color unchanged **in the tab list
only**. Their actual streak still changes immediately through kills, Spree
withdrawals/consumables, deaths, anti-farming, etc. Hearts, milestone buffs,
glowing, action bar, /killstreak, and nametag systems remain functional.
This means an invisible player who gains 5 -> 10 streak may still appear as
5 in tab, then switch to 10 when invisibility ends.

SpreeCore listens to invisibility effect changes (including natural expiration,
milk, and commands) and refreshes the player's tab entry on the next tick,
without waiting for the periodic indicator refresh. If a player joins while
already invisible, their initial tab display remains unchanged until visible.

Admins can turn this off in `/spree settings` > **Tab & Nametag Indicators**
using `display.streak-indicators.freeze-tab-while-invisible`, default true.
Existing server configs receive the new default without resetting other values.

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
