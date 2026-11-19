# SpreeCore 1.2 — Spree SMP native Paper plugin

A Java 21 / Paper **1.21.11** replacement for these ten Skript files.

**Build format:** Java source with GitHub Actions producing an installable `SpreeCore-1.2.0.jar`. A test server is still required for gameplay and visual verification.

**Previously added in 1.1:** `/spree` dashboard now links to a complete admin-only graphical settings editor. Every primitive configuration entry in `config.yml` is automatically grouped and editable, so adding new values later does not require rewriting the menu. Resource-pack model selectors show custom models only when the player installs the matching pack, while non-pack clients keep vanilla item appearances.

Original inputs:

`killstreak.sk`, `spreetraps.sk`, `spreeevents.sk`, `tundratanto.sk`, `bloodbutcherer.sk`, `stormbreaker (1).sk`, `aquafier.sk`, `darkheart.sk`, `hailstorm.sk`, and `bloodsbane.sk`.

## Install / build

1. **Back up your server** (world and `plugins/Skript` folder). In your **test** server first, disable/remove the ten `.sk` files (do not load both implementations at the same time).
2. Build with Java 21 and Gradle 8.14+: `gradle clean test build` (or run `build-windows.bat` on Windows if Gradle is installed).
   - The built jar is `build/libs/SpreeCore-1.1.0.jar`.
   - Alternatively, push this folder to GitHub and run the included GitHub Actions `Build SpreeCore` workflow to download the compiled jar.
3. Place **only the built JAR** in `plugins/` and start your Paper 1.21.11 server. **Skript is not required** for this plugin.
4. As an OP or someone granted `spree.admin`, run `/spree` and click **Edit All Settings** (or run `/spree settings`). Every bundled balance option is available in an organized 6-row container GUI. GUI edits save immediately, reapply online effects, and reschedule HUD/autosave tasks; use `/spreeadmin reload` after editing YAML manually.
5. If migrating existing Skript player data and the Skript default CSV is available, run `/spreeadmin importskript` once. If profiles already contain data, use `/spreeadmin importskript force` to override fields **after** reviewing the automatic backup. Other Skript database formats are not automatically imported.
6. The first time you install, set `legendary.accept-legacy-lore-items: true` to recognize Skript-generated weapons. Run `/spreeadmin migrateitems` with players online, then disable lore detection when every old item is converted. This helps prevent spoofed relics.

## Commands

| Command | Purpose |
| --- | --- |
| `/spree` | Player dashboard; admins also see Admin Control and Edit All Settings |
| `/spree settings` | Open complete config editor directly (admin) |
| `/spree value <value>` | Submit the value of an in-progress chat edit (admin) |
| `/spree cancel` | Cancel a chat edit (admin) |
| `/spreeadmin settings` | Open config editor from admin command (admin) |
| `/spreehud` | Toggle personal actionbar display |
| `/killstreak [player]` (`/ks`) | Show current streak |
| `/stats [player]` (`/mystats` alias) | Current and lifetime stats |
| `/leaderboard` (`/lb`, `/topstreak`) | Top 10 streaks |
| `/spreeinfo` | Health scaling and recovery; does not reveal event status or unfinished item progression |
| `/spreeadmin` (`/sa`) | Clickable admin GUI |
| `/spreeadmin set\|add\|remove <player> <amount>` | Modify streak for online or known offline player |
| `/spreeadmin reset <player>` | Reset a player's streak |
| `/spreeadmin recovery <player>` | Place player in the internal -1 recovery state |
| `/spreeadmin setall <amount>` | Set all known streaks |
| `/spreeadmin resetall` twice | Confirm global reset |
| `/spreeadmin update [player]` | Re-apply health and any enabled item rules |
| `/spreeadmin reload` | Read updated configuration |
| `/spreeadmin migrateitems` | Add permanent IDs to online players' old Skript relics |
| `/spreeadmin importskript [force]` | Import `plugins/Skript/variables.csv` into profiles |
| `/spreeevent` | Event admin GUI |
| `/spreeevent hunt\|juggernaut\|locator\|bloodsbane on\|off` | Compass modes / locator / auto-award |
| `/spreeevent bloodmoon\|surge\|chaos\|race on\|off` | Timed events |
| `/legendary` | Admin-only weapon browser GUI (ordinary players may use `/legendary refresh`) |
| `/legendary give <id> [player]` | Give weapon (Blood's Bane given as test copy) |
| `/legendary forge end <id>` | Complete running forge |
| `/legendary forge reset <id>` | Unlock crafting again |
| `/legendary status` | All forge states |
| `/legendary refresh` | Update held relic's lore from config |

## Preserved and improved mechanics

- **Killstreaks:** -1 recovery minimum; 30 default cap; natural death halves positive streak and drops a pickup token; PvP death halves positive streak, gives the killer a configured reward; player profiles persist on restart. Natural deaths at 0 enter **Recovery** and only create one token. Further deaths at -1 do not drop any token.
- **Anti-farm:** Default 5-minute same-victim cooldown and no rewards from players already at -1. Configurable in `killstreak.anti-farm`. Anti-farm does **not** stop PvP; only the reward. Repeat victims do not trigger event bonuses or win the kill race.
- **Revenge:** A legitimate kill of your previous killer gets +2 extra streak. This activates the revenge system that was referenced but never fully wired in the old scripts.
- **Item progression still undecided:** Tentative thresholds remain under `killstreak.restrictions` as editable drafts, but **`killstreak.restrictions.enabled: false`** by default means none of them restrict player items. The player dashboard does not advertise item unlocks. Admins may explicitly turn rules on only after finalizing the design.
- **Streak passives:** Fire Resistance from 4, Hero of the Village from 7, Weaving from 10, Speed I and Glowing from 15; max health grows as the original code specified (10 hearts plus one every two streaks from 3, capped at 20 hearts). Note: the original `/spreeinfo` text said 15-heart max, **but its running health code capped at 20**; the Java rewrite follows the running code.
- **Spree tokens:** Item PDC owner/value ID, atomic claiming, own token restores -1 to zero without farming, old Skript token lore recognized.
- **Events:** Hunt target compasses (+2 target, +3 hunter); Juggernaut (+10 max hearts, +2 on kill, +4 for killer who slays them); locator gamerule; Blood's Bane auto-awarded to the **highest online** streak of 15+; distinct timed Blood Moon (+1 kill), Surge (Strength I + Resistance I at 10+), Chaos (30% shorter relic cooldowns at 10+), and kill race (first 3 eligible kills wins 3 streak). Timed modes were displayed in the old admin GUI but not actually functional; these are newly implemented.
- **Tundra Tanto:** 40s arm, 8s window, 2s freeze/head lock, 7% Slowness I.
- **Blood Butcherer:** sacrifice 5 hearts for Strength III for 10s, 35s cooldown, 5% Wither III.
- **Storm Breaker:** 55s lightning AOE, 3 hearts direct damage (source tuning function value), 10% extra lightning proc.
- **Aquafier:** absorb up to 65 connected water blocks (respecting cancellation from block protection plugins), Speed III and +20% attack damage for 15s, 4-hit drown.
- **Darkheart:** 6-tick dash, 3.5-heart direct hits, every 4th hit heals 2 hearts.
- **Hailstorm:** every 3rd actual fired arrow is a frost arrow; arming next shot causes 10-arrow visual rain dealing 5 nonlethal hearts across 5 seconds.
- **Blood's Bane:** temporary top-streak owner, per-hit combo bonus to +40%, launch for 4.5 hearts across 3s, admin test copies.
- **Forges:** one attempt per item at a time; recipe consumes ingredients but doesn't give the weapon until the 15-minute survival timer completes. Boss bar, location broadcasts, kill-to-fail, and pending offline claim all supported. Forge state persists across restart.

## Recipes

The shaped recipes are native and require the exact *base potion* type. Read left to right, top to bottom:

```
Tundra Tanto:         Blood Butcherer:        Storm Breaker:          Aquafier:
Slowness BlueIce Slow Strength Vine Strength Gold Gold Leaping     WaterBr Iron WaterBr
BlueIce Sword  BlueIce Vine   Axe  Vine      Gold Rod  Leaping     Bucket Iron Bucket
Slowness BlueIce Slow Harming Vine Harming  Leaping Rod Leaping   WaterBr Breeze WaterBr
```

## Admin settings GUI

Admins can open `/spree` → **Edit All Settings**. The editor enumerates *every scalar setting in the plugin's bundled defaults*, not a hardcoded short list. Settings are split into General, Tab/Nametag Indicators, Killstreak/Health, Anti-farming, Revenge, Item Rules (WIP), Potion Bonuses, Passives, Legendary/Forge, each legendary weapon, Events, and Resource Pack Models.

- **Boolean:** left click toggles ON/OFF.
- **Number:** left click adds 1 (0.1 for decimal settings); right click subtracts 1; shift+left requests an exact value through chat.
- **Text:** click to enter a new value through chat. `/spree value <value>` works as a fallback if a chat plugin blocks input.
- **Reset:** shift+right restores the bundled default for any setting.
- Every write is type-checked and range-checked; changed values are saved to `config.yml` immediately. Players without `spree.admin` cannot open or edit settings.
- Sidebar actions, navigation and all GUI clicks are server-side and menu-holder-verified to avoid inventory item extraction.

## Resource pack model compatibility

The plugin tags generated legendary items and dropped Spree Tokens with **custom_model_data** while retaining the original vanilla `item_model`: weapons remain diamond swords, diamond axes and crossbows; Spree Tokens remain Heart of the Sea. **Players who do not use the pack still see normal vanilla items.**

Merge the contents of `resource-pack-bridge/` into your real Spree resource pack, following `resource-pack-bridge/README-merge-into-your-pack.md`. The bridge includes item-definition switch logic and safe placeholder models. Replace placeholders with your real Blockbench model JSON/PNG files; those textures are not bundled here. If your pack already overrides vanilla items JSON, manually merge the cases rather than overwriting other custom models or crossbow animations. Supported version is Minecraft 1.21.11's `assets/minecraft/items/*.json` definition format.

The selector IDs (721001–721007 for legendaries; 721050 for Spree Token) are in the GUI under **Resource Pack Models**. Changing the IDs also requires matching changes in the client pack.

Existing PDC-tagged weapons and recognizable legacy-lore relics in **online player inventories** gain the correct model selector on join, on reload and through `/spreeadmin migrateitems`. Items still stored in unloaded chunks or offline containers will be updated only after they are moved into an online player's inventory and migrated.

## Architecture and performance

Seven weapons use **one PDC item registry** and one shared listener, instead of seven independent scripts. Hailstorm's frost and volley flags attach to projectile entities, not the shooter's latest-arrow variable. Forge timers use a shared one-second task and persist to `forges.yml`. The global HUD is one task every 40 ticks; passive recalculation happens every four seconds; inventory enforcement is event-driven with a 30-second safety scan (not an inventory scan every two seconds). Player profiles are cached by UUID in `players.yml` with an atomic save approach and 120-second autosave, plus shutdown saves. GUI clicks use a typed holder instead of brittle inventory-title strings. Player UUIDs, serials, cooldowns, and weapon metadata are isolated by plugin namespaced keys.

## Testing and limitations

- **Stage first.** Generated Java code is intended for Paper 1.21.11 with Java 21. Functional testing in a real Minecraft server (Potion effect stacking, protection plugins, crossbow loading, boss bars, keepInventory, and concurrent event interactions) is still required before production deployment.
- Real custom Blockbench model textures are not included in the bridge; they must be copied from your actual Spree resource pack. The placeholder JSONs ensure the bridge does not produce missing textures while art assets are pending.
- In-game GUIs and custom model rendering need visual confirmation on a live Paper test server; the build checks cannot simulate the Minecraft client.
- Time-based cooldowns are memory-only and reset on restart; player streaks, anti-farm victim timestamps, forge state, and event state persist.
- Legacy recognition can be spoofed by user-edited lore; run `migrateitems` and disable it after migration.
- This plugin does not import Skript SQLite/SQL databases, only the default CSV. Existing world items not seen by `migrateitems` will need to be brought online first.
- The plugin deliberately does not create the old unfinished Mutation interface; the source did not define any working mutation behavior.
- `locatorBar` is a vanilla Minecraft gamerule. The event changes the rule in each loaded world via Paper's typed API.
- Direct-health legendary effects may not obey all region-protection plugins. Aquafier's water absorption emits a block-break check, but test your actual protection setup rather than assuming it will block every situation.
- The optional timed events were added where the original scripts had menu entries without functioning implementations; their exact balance is a new design choice, not a one-for-one port.


## Version 1.2: private menus, player manager and streak nameplates

- **Player `/spree` dashboard:** only personal streak, statistics, leaderboard, basic mechanic info and HUD toggle. **No event status, legendary list or unlock progression** is exposed. `/spreeinfo` no longer discloses event timing or unfinished item thresholds.
- **Admin `/spreeadmin` player manager:** show online players or all stored profiles; search by name/UUID; see online state, best, kills and deaths; manage *offline* profiles without requiring those players to rejoin. Set exact streak by chat, quick-adjust ±1/±5, set Recovery (-1), and reset to zero with confirmation. Exact editors for personal best, kills, deaths and ended streak counts. Changes persist to `players.yml`.
- **Tab and overhead indicators:** compact `✦ 12` after each player's name, with a configurable Recovery marker `↺`. Config paths are under `display.streak-indicators`; disable tab or nametag independently, or change the symbols through the admin settings GUI.
- **Non-invasive nametags:** native scoreboard team suffixes are only added when a player is *not already in another team's scoreboard entry*. This deliberately avoids removing factions/party teams and altering friendly-fire or collision semantics. A pre-existing shared scoreboard team may prevent overhead indicators for its members; their tab indicators remain visible. If you use another nametag/team plugin, coordinate the integrations rather than forcing a team override.
- **Tentative item rules inactive:** old draft thresholds remain editable for future decisions, but they are disabled by default. No server-wide inventory item confiscation while they're still being decided.

