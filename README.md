> **Latest distributed binary: SpreeCore 1.8.26 (November 22, 2026).** [Release notes](releases/1.8.26/README.md) document legendary unbreakability, actual netherite-equivalent attributes, hidden glint/enchantment/stat tooltips, custom tooltip frames, and killstreak-aware abilities. The older main-branch Gradle source is **not yet a fully reproducible build of this binary**.

# SpreeCore v1.7.1 — Spree SMP

One native Java 21 plugin for Paper **1.21.11**, replacing the original killstreak, legendary weapon, forge, and event Skripts. The source code and CI builds are maintained at https://github.com/Howluma/Spree.

## Upgrade and installation

1. Back up the world and `plugins/SpreeCore/` (especially `players.yml` and `config.yml`). Test on a **separate server** first.
2. Stop Paper, replace the existing SpreeCore JAR with `SpreeCore-1.7.1.jar`, and keep the ten original `.sk` scripts disabled to avoid double-handling events.
3. Start the server with Java 21. SpreeCore upgrades the existing configuration on startup without resetting player profiles. Old passive/potion/item-lock settings are intentionally deleted from the active YAML and editor.
4. Use `/spree settings` or `/spreesettings` as an operator, or with `spree.admin`, to inspect/edit all active settings. `/spreecore:spree settings` works when a separate plugin owns `/spree`.
5. Test the new hearts and mace gate in-game before deploying to a live SMP. Compilation and automated tests do not substitute for real-server testing.

Build yourself: `gradle clean test build` with Java 21 and Gradle 8.14+. The JAR appears at `build/libs/SpreeCore-1.7.1.jar`. GitHub Actions publishes the build artifact after each push.

## Admin Balance Workshop, live recipes and conditional mace cooldown (v1.7.1)

**Fate Sigil** keeps its existing name, icon identity and four-way reroll
functionality. Its crafting grid is now **fully editable from the game**:

Open **/spree > Admin Center > Balance Workshop > Fate Sigil Crafting**.
The 3x3 grid is displayed literally. Pick an item onto your cursor from
your own inventory, then left-click any cell to use that ingredient;
left-click with an empty cursor to type a material ID in chat, right-click
to clear a cell (AIR), or shift-right-click to restore that slot's
default. Adjust crafted output count (1–16), display name and recipe
enabled status from the same page. All edits save and **re-register the
recipe immediately**, without a server restart. The default recipe is
corner amethyst shards, edge diamonds and center netherite scrap.

**Balance Workshop > Item Limits** provides six direct-entry controls
for totems, enchanted apples, wind charges, XP bottles, enchanted apple
cooldown and its maximum effect duration. Left-click to type an exact
number, right-click to reset the default. These are active restrictions,
not visual-only numbers.

**Balance Workshop > Mace Slams** configures ordinary smash cooldown
(default 60 seconds), Mace Mastery cooldown (default 30 seconds),
player-damage multiplier and combat toggle. A cooldown **starts only on
a real mace-smash damage event with positive final damage**; ordinary
melee swings, shield blocks, missed smashes, cancelled hits, and
zero-damage events do not spend it. Blocked smashes on cooldown are
denied. Mace Mastery still requires its Talent and the Mace Wielder
Privilege. The cooldown persists across relogs, and changing the
configuration affects future cooldown triggers.

**/spree > Settings** also exposes dozens of independent Talent
balancing fields: conditional damage thresholds, combo requirements,
healing, durations, movement multipliers, defensive shared cooldown,
Scavenger odds and rewards, consumable speed, and other parameters.
These are grouped by Talent rather than mixed into the main dashboard.

**Fate Sigil resource pack**: place the updated \`Spree-SMP.zip\` above
other packs affecting Echo Shards. SpreeCore sets a float CMD selector
\`721051\` and preserves vanilla Echo Shard appearance for players
without the pack. The 128x128 texture is at
\`assets/howluma/textures/item/fate_sigil.png\`, and the exact original
artwork files for other Spree items remain unchanged. If you edit the
CMD selector in-game, also update the matching pack JSON.

## Choice-based progression (v1.7.0)

The four milestone categories repeat through the default 30 killstreak cap:

| Killstreak | Category | Reward |
| --- | --- | --- |
| 1, 6, 11, 16, 21, 26 | Privilege | Choose an available item-use unlock |
| 2, 4, 7, 9, 12, 14, 17, 19, 22, 24, 27, 29 | Perk | Auto-roll a small stacking bonus |
| 3, 8, 13, 18, 23, 28 | Talent | Choose one of three randomly offered Talents |
| 5, 10, 15, 20, 25, 30 | Infusion | Choose an unclaimed permanent potion effect |

**The game never automatically opens a GUI after a kill.** Players
receive chat notices for new selectable rewards and freshly rolled Perks;
they can visit **/spree > Progression** to redeem choices when ready.
The four category screens distinguish locked, ready, owned and inactive
milestones, with six symmetrically placed unlocks per category. A separate
**/spree > How Everything Works** guide explains every Talent, Perk,
Privilege, Infusion and Wanted penalty. Both the normal-player dashboard
and the operator control center use compact, symmetric blue/black layouts,
instead of walls of text or an overloaded front page.

**All claimed options are saved at their original milestone.** Losing
streak disables options above the current level, while regaining it
restores those exact choices. Perks are automatic and continue to use
saved rolls and maximum-copy caps. Talent selections come from **three
saved random offers**, so reopening the GUI cannot provide a free reroll.
Players can eventually acquire all six Privileges and all six Infusions;
this is intentional.

Privileges: Demolitionist (TNT minecarts), Mace Wielder, Netherite
Specialist (netherite tools), Turtle Master potions, Forbidden Fruit
(enchanted golden apples), and Last Stand (totems). Mace attacks now
require a claimed active Privilege rather than just level 1. The
Forbidden Fruit restriction is **maximum 4 carried**, **30s consumption
cooldown**, and **at most 60s on granted potion effects**. Last Stand
permits **1 total totem**. Default individual possession limits for
Wind Charges and XP Bottles are 64 each; the Expanded Reserves Talent
adds one more stack of each (up to 128).

Infusions: **Speed I, Strength I, Weaving I, Regeneration I, Luck V,
Hero of the Village X**, refreshed while chosen and active. This
replaces the old fixed Weaving/Speed/Strength unlocks.

The 22 Talents are passive or automatically triggered and cover combat,
survival, mobility, economy and resource management. Examples include:
Mace Mastery (mace smash cooldown **60s -> 30s**), Blacksmith
(2-gold-ingot golden apples and 8-iron-nugget breeze rods), Industrious
(permanent Haste I), Combo Breaker, Field Medic, and Quick Hands.
No added keybinds or manually activated Talent items.

Positive killstreak levels still give **+0.5 max hearts** each:
10 hearts at 0/Fractured, 25 hearts at 30. Natural and PvP deaths still
halve the victim's streak, rounded down. The anti-farm and Fractured
rules remain intact.

### Fate Sigil: universal reroll item

Craft an **Echo Shard Fate Sigil** with four amethyst shards in the
corners, four diamonds on the edges, and one netherite scrap in the
center. Right-click to open its reroll interface, or browse from
**/spree > Progression > Fate Sigil**.

You can rechoose **one currently unlocked Privilege**, rechoose **one
currently unlocked Infusion**, reroll **three new saved Talent options**
for one previously chosen Talent milestone, or **reroll all earned
Perk rolls** together. A Sigil is used only when the player confirms a
reroll/replacement; browsing costs nothing. A confirmed Talent reroll
immediately consumes one Sigil and stores the new three offers, which
persist through closing the interface and logouts.

**Existing-player migration:** Previous fixed potion milestones and
automatic mace access are retired. The stored killstreak, statistics and
all historical Perk rolls are retained, but new Privilege/Talent/Infusion
milestones start unclaimed until a player makes a choice. Back up
players.yml and test migration on a staging server first.

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
- Health, selected Privilege/Talent/Infusion eligibility, Perks and Wanted
  indicators refresh after deduction. Best lifetime streak and statistics remain.
- Every player has the `spree.withdraw` permission by default. Server
  operators can toggle `killstreak.withdrawal.enabled` in
  `/spree settings` > **Spree Withdrawal**.
- If another plugin intercepts `/spree`, use
  `/spreecore:spree withdraw` instead.

## Wanted status and bounty tokens (v1.7.0)

Environmental deaths still halve the player's current killstreak, but
their dropped Spree token now carries **the same tiered amount an eligible
player killer would have received**: level 5 yields +2, 10 +3, 15 +4,
20 +5, 25 +6 and 30 +7. This retains the prior token ownership/recovery
rules and anti-farm behavior while no longer rewarding environmental
death more than PvP.

An escalating status system broadcasts announcements at each multiple
of five killstreak levels (5/10/15/20/25/30). At 10, the player receives
continuous Glowing with a color matching the current five-level killstreak
tier where SpreeCore owns their display scoreboard team. At 15, their
world and coordinates are broadcast every 15 minutes. At 20, they are
removed from vanilla scoreboard gameplay teams every second; SpreeCore's
**own singleton display-only scoreboard team** remains for colored glowing.
External parties/clans maintained by independent plugins may require
separate integration. All these thresholds and announcement texts are
editable in /spree settings.

## Escalating hunt rewards and TNT credit (v1.6.4)

**Qualifying PvP kills earn +1 Spree plus +1 for every five levels of the
victim's PRE-DEATH killstreak.** The victim's streak is captured before
being halved on death. This replaces v1.5.1's *half-the-victim-streak*
reward formula for PvP kills (e.g. a level-30 victim was previously worth
+15 but is now worth +7). The tier values are configurable in
`/spree settings` > **Hunt Kill Rewards** using three options:
`base-reward` (1), `levels-per-tier` (5), and `extra-per-tier` (1).

| Victim's streak before death | Killer gains | Victim after death |
| --- | --- | --- |
| 0-4 | +1 | Half, rounded down |
| 5-9 | +2 | Half, rounded down |
| 10-14 | +3 | Half, rounded down |
| 15-19 | +4 | Half, rounded down |
| 20-24 | +5 | Half, rounded down |
| 25-29 | +6 | Half, rounded down |
| 30 | +7 | 15 |

Example: a player at Fractured (-1) who TNT-kills an eligible level-30
player earns +7, returning to a 7 killstreak. This applies to direct
melee/ranged kills and properly credited explosive kills. Anti-farm
cooldowns remain mandatory for qualifying rewards, and farming Fractured
victims is still blocked by default. The configured maximum killstreak
cap still applies.

**Revenge bonuses are disabled by default** and never included when that
option is off. Existing server choices in config.yml are preserved.
Separately configured timed/Juggernaut/Hunt event bonuses still apply
when those events are running.

**Environmental-death Spree Tokens now use the same tiered value as a
PvP kill** (e.g. level 30 drops a +7 token), but dying naturally still
halves the victim's killstreak. Right-click **Spree** items still give +1.

When an explosion is attributed to an online player, the normal player-kill
branch awards streak and does **not** drop an environmental-death token.
Ownerless or ambiguous explosions stay uncredited, and a natural death can
still drop a token. If TNT behavior differs during live testing, note whether
you received a "for eliminating" message or picked up a dropped Spree Token;
those distinguish credited player kills from environmental deaths.

## Wanted penalties and chosen permanent effects (v1.7.0)

Every five killstreak levels triggers a global chat message. At **5**:
"{player} is on fire!". At **10**: "{player} is unstoppable!".
At **10**, players additionally receive persistent glowing in the
current five-level streak tier color (where SpreeCore owns scoreboard
presentation). At **15**, their world and XYZ coordinates are publicly
announced **every 15 minutes**. At **20**, they cannot remain in vanilla
scoreboard gameplay teams; SpreeCore's own singleton team remains to
color their glowing outline. Third-party clans or party systems may
need separate hooks. At **25** and **30**, custom server announcements
complete the wanted tiers. All tiers are explained in the Wanted GUI.

Permanent Speed/Strength/Weaving/Regen/Luck/Hero effects are **chosen
Infusions**, not fixed rewards. SpreeCore refreshes them only while the
relevant selected milestone is active, and respects stronger external
potion effects. Losing levels removes owned effects without erasing the
permanent recorded selections.

## Alchemy Perk and Talent design (v1.6.3)

The **Alchemy** minor Perk gives **+5% duration per copy** to beneficial,
finite potion effects obtained from drinking potions, splash potions,
lingering area clouds, tipped arrows or food (such as golden apples).
It stacks additively, up to 4 copies for +20% by default. For example,
a 3-minute beneficial potion lasts 3:09, 3:18, 3:27, or 3:36 depending
on how many copies the player has unlocked. Only one extension applies
per acquired effect; repeated reapplications start from the new effect,
not the already-increased duration.

Harmful, neutral, instant and infinite effects are excluded. Spree's
renewable permanent Infusions are also excluded, as are /effect commands,
beacons, conduits and other plugin-owned effects. The duration is
extended on acquisition; losing Alchemy does not retroactively shorten
an already consumed potion, but new potions use the current Perk bonus.

Alchemy appears in `/spree perks` and can be tuned or disabled in
`/spree settings`. Current existing Perk assignments are preserved;
Alchemy simply joins the possible future random rolls.

**All 22 designed Talents are now in the v1.7.0 code**, with automatic
triggers rather than new keybinds. This remains a staging build: advanced
combat, inventory and scoreboard interactions require in-game verification.

## Capacity Perk (v1.6.2)

**Capacity** adds +4 items to the maximum size of an inventory stack per
copy, up to four copies by default: 64 -> 68 -> 72 -> 76 -> 80.
An item with a natural limit of 16 increases to 20/24/28/32 instead.
Only naturally stackable items are eligible; tools, weapons and armor
remain unstackable. Other plugins' custom max-stack settings are preserved.

Vanilla Minecraft 1.21.11 only respects extra stack capacity when the
item has an explicit MAX_STACK_SIZE component; simply changing a player's
Inventory#getMaxStackSize is not sufficient. SpreeCore temporarily adds
that component to eligible items in the player's inventory, marking
its own modifications and restoring them on drops, death and transfers
to other inventories. Large stacks are split into vanilla-sized parts;
if there is no free room, excess items are dropped, never deleted.

The Perk is saved at its milestone and disables/restores on level changes
without rerolling. Its default +16 maximum is safely under the vanilla
99-item absolute limit. Use `/spree perks` and `/spree settings`.

**Test inventory transfers on your staging server before enabling on the
main server.** Cursor interactions, inventory-sorting plugins, custom
GUIs, item pickup mechanics and automated transfers can interfere with
per-player stack-limit rules. No resource pack or client mod is needed.

## Scale Perk (v1.6.1)

**Scale** joins the existing random, stackable Perk pool, without removing
any old Perks or rerolling saved assignments. Each copy reduces the player's
physical size by 5%, including their hitbox, through a transient, keyed
Minecraft `SCALE` attribute modifier. Three copies produce 85% normal
size by default. The bonus adds linearly (-5%, -10%, -15%); even if an admin
raises its config limits, total size reduction from Scale is capped at 15%
to protect PvP balance. Other size modifiers from commands or plugins
continue to coexist.

Only currently active Scale rolls apply: dying or withdrawing below their
unlock levels restores the proper size, and recovering the level restores
the same roll. The perk's modifier is removed on quit and plugin shutdown.
The `/spree` Perks book and `/spree perks` show a clear negative size
percentage. Configure `perks.scale.enabled`, `perks.scale.per-copy` and
`perks.scale.max-copies` via `/spree settings`.

**This is both a visual and hitbox change**, not a cosmetic-only scale.
Check combat balance and any reach/anti-cheat rules on your test server.

## Random, stackable minor Perks (v1.6.0)

Perks now roll automatically at **2, 4, 7, 9, 12, 14, 17, 19, 22, 24,
27 and 29** on the default level-30 cap. A roll can repeat a Perk, up to its
per-type copy cap, and provides a small passive bonus. It is never a random
"proc". Every Perk milestone is saved permanently to players.yml under
perk-rolls. Dropping streak or withdrawing disables Perks from higher
milestones; regaining levels restores the same Perks with no rerolls.

| Perk | Each copy | Default copies cap |
| --- | --- | --- |
| Power | +2% outgoing melee/projectile damage | 4 |
| Fortitude | -2% incoming damage (excluding void/admin kills) | 4 |
| Agility | +3% movement speed | 3 |
| Dexterity | +2% attack speed | 4 |
| Restoration | +3% healing received | 4 |
| Stability | +4% knockback resistance | 4 |
| Endurance | -5% hunger exhaustion | 4 |
| Efficiency | +4% block-breaking speed | 4 |
| Preservation | -5% item durability used | 4 |
| Insight | +5% naturally gained XP | 4 |
| Reach | **+0.05 block entity attack reach** | **3 (+0.15 blocks)** |
| Scale | **-5% player size and hitbox** | **3 (-15%)** |
| Capacity | **+4 items per inventory stack** | **4 (+16 items)** |
| Alchemy | **+5% positive potion duration** | **4 (+20%)** |

All percentages are **per copy, added together**, not multiplicative.
Power does not boost TNT or end-crystal explosions. The 0.15-block maximum
reach is deliberately small; some anti-cheat plugins require matching reach
settings. Reach does not increase block interaction distance.

Small whole-number effects are precise over time: Preservation/Insight
accumulate fractional savings/XP in the player's saved profile, so 5%
is not lost to per-event integer rounding.

Existing players with old killstreak histories are assigned their historic
milestones once on upgrade. Perks are inactive when streak is below their
respective unlocked level. Admins can edit each Perk's enabled flag,
bonus-per-copy, and max copies through /spree settings > Stackable Perks.
Perks are visible as their own compact active-only icon on the /spree
dashboard and via **/spree perks**, separate from the current-buffs book.
The existing +0.5 hearts per level, all Infusions (Weaving/Speed/Strength/
Glowing), mace restrictions, TNT rules, and all legendaries are unchanged.
Talents and the complete Privileges system remain future design work.

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
| `/spree` | Symmetric player dashboard: Progression, Guide, Wanted, Perks, Statistics and optional operator controls |
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

This build targets Paper 1.21.11 and Java 21. Automated tests
cover killstreak, Perk and progression math, config defaults and GUI-related
formatting. A successful CI build does not prove live-server compatibility.
Before production, test all Talent interactions, the Fate Sigil,
item-transfer components, anti-farm protections, chat notices,
coordinates and scoreboard-team restrictions in a staging server.
