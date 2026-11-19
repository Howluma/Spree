# SpreeCore: test-server checklist

Test on a **copy** of the server with the original ten Skript scripts disabled. Use Paper 1.21.11 and Java 21. Check the server console for exceptions after **every** test.

## Admin GUI and resource pack (1.1)

- [ ] `/spree` shows the extra Admin Dashboard and Edit All Settings buttons only to `spree.admin` holders.
- [ ] `/spree settings` lists every bundled config category and no ordinary players can open it.
- [ ] Browse each category, toggle a boolean, increase/decrease numeric settings and shift-left to enter an exact value through chat.
- [ ] `/spree value 0.12` works for pending chat edits, `cancel` cancels edits and shift-right resets defaults.
- [ ] Invalid input (negative streak cap, chance >1, nonnumeric number) is rejected and never saved.
- [ ] Changes persist after restart; config values take effect with no restart, including actionbar and autosave periods.
- [ ] Try rapid shift-clicks, number keys, dragged items, offhand swaps and bottom-inventory interactions; nothing can be removed from a GUI.
- [ ] Custom legendary models render correctly with the actual installed resource pack; without it every weapon remains the correct vanilla weapon.
- [ ] Spree Token is a custom texture with the pack but a normal Heart of the Sea without it.
- [ ] Old legendary items in online player inventories migrate to new custom_model_data; original enchantments and PDC IDs remain.
- [ ] The model adapter does not overwrite existing weapon/crossbow pack overrides or break crossbow loading animations.

## First launch / data

- [ ] Plugin starts without `Skript`, and creates `plugins/SpreeCore/config.yml`.
- [ ] Regular `/spree` shows personal stats, leaderboard, info and HUD only; no live events, legendary list or draft unlocks.
- [ ] Ordinary players cannot browse `/legendary list`, `/legendary status`, or `/legendary` GUI; `/legendary refresh` still refreshes their held item.
- [ ] Admin `/spreeadmin` → Players switches online/all profiles, searches by partial name/UUID, and navigates multiple pages.
- [ ] Admin may open offline profiles and edit streak, best, kills, deaths and streaks ended without waiting for login.
- [ ] Exact value chat prompts accept valid numbers, reject invalid ones, and `cancel` returns safely to the menu.
- [ ] Reset current streak needs two clicks on the *same player* within six seconds; no accidental mass resets.
- [ ] Tab renders `✦` plus the real streak number; nametag renders the compact suffix, and Recovery uses `↺`.
- [ ] With a separate teams/scoreboard plugin, existing teams and friendly-fire remain intact; tab indicator still shows even if team collision prevents a nametag suffix.
- [ ] `killstreak.restrictions.enabled` defaults off; no draft item restrictions or pearl cap are enforced before server approval.
- [ ] `/spree`, `/spreeadmin`, `/spreeevent`, `/legendary` menus open for authorized players and their buttons work.
- [ ] `/spreeadmin set <player> 10` changes the actionbar, tab display, overhead tag where compatible, and max health.
- [ ] `/spreeadmin recovery <player>` displays `RECOVERY`; a legitimate kill restores positive streak.
- [ ] A player at **0** dying to a mob goes to **-1** and drops one token; dying again at **-1** does not create more tokens.
- [ ] Own token restores **-1** to **0** without granting killstreak; another player's token adds its value only once.
- [ ] Repeated PvP kills of the same victim within 300 seconds do not grant streak rewards (default rule).
- [ ] Death reduces positive streak to half, and **keepInventory** doesn't duplicate drops or permit locked equipment.
- [ ] `plugins/SpreeCore/players.yml` survives restart and retains streaks, stats, and anti-farm data.

## Relics

- [ ] `/legendary give tundra`, `butcherer`, `stormbreaker`, `aquafier`, `darkheart`, `hailstorm` create distinct tagged items.
- [ ] Sneak + right-click activates each relic's intended ability, with cooldown feedback.
- [ ] Tundra freezes head rotation and movement for two seconds.
- [ ] Blood Butcherer costs hearts before Strength III and occasionally applies Wither III.
- [ ] Storm Breaker lightning does not kill targets from its direct active damage and respects distance.
- [ ] Aquafier absorbs connected water and is denied by the **actual** server's region-protection setup; test carefully.
- [ ] Darkheart dashes, hits each target only once per dash, and heals every four weapon hits.
- [ ] Hailstorm's every-third-shot frost and armed next-hit volley work with loaded crossbows, different targets, and an empty arrow stack.
- [ ] Blood's Bane goes only to the top **online** streak 15+, cannot be dropped/stored by the bound owner, and changes hands as ranks change.
- [ ] Old Skript relics can be migrated with `/spreeadmin migrateitems` before `accept-legacy-lore-items` is set to false.

## Forges, events, admins

- [ ] All four shaped recipes require their **exact base potion types**.
- [ ] One recipe starts a forge and consumes one set of ingredients; shift-click does not duplicate crafts.
- [ ] Boss bar, coordinates, 3-minute announcement, death failure and restart recovery behave as expected.
- [ ] A forge completed while its owner is offline grants exactly one item on later login.
- [ ] `/spreeevent hunt on` gives per-player target compasses; `/spreeevent juggernaut on` grants 10 hearts and rotates on death.
- [ ] Juggernaut killer receives the slay bonus **before** the new juggernaut is selected.
- [ ] `locator`, `bloodsbane`, `bloodmoon`, `surge`, `chaos`, and `race` modes can be enabled and disabled.
- [ ] `/spreeadmin reload` applies config changes. Adjust 5-minute events and forge timers downward temporarily for the tests.
- [ ] If migrating, back up original Skript variables and examine `/spreeadmin importskript` output before enabling `force`.

**Testing note:** GitHub Actions builds and runs JUnit tests against the Paper 1.21.11 API. Passing CI establishes compilation and arithmetic/config validation only. Run the above manual integration tests in-game before deploying on the live SMP.


### v1.3 regression checks

1. As an op, run `/spree settings`, `/spreesettings`, and `/spree admin`.
   Confirm all navigate correctly. As a non-op, verify editor permission is denied.
   When another plugin also defines `/spree`, use `/spreecore:spree settings`.
2. Inspect tab and overhead names: `◆ 12` should be dark cyan + **bold cyan**;
   `◆ ✕` should be red when Fractured. Confirm scoreboard team plugins still work.
3. Kill players with ordinary swords and then TNT ignited by a different player.
   Confirm the latter credits the igniter, halves the victim's streak, and drops **no**
   environmental death token. Test TNT chains and TNT unloaded/reloaded in chunks.
4. Test igniter taking TNT blast damage: compare with TNT damage to another player;
   only the igniter should take 50% of baseline when enabled. Set the GUI to 100%
   and verify damage becomes vanilla. Verify redstone TNT with unknown owner is not
   assigned to an unrelated player.
5. Test mace damage with the same target armor: with Mace 75%, the base damage
   should be scaled by 0.75; ordinary weapons should be unaffected. Adjust via
   `/spree settings` > Combat Balancing and confirm updates without a restart.
6. Give `/spreeadmin giveitem <player> 3`. Right-click three times, confirm a
   single +1 per item, exactly three items consumed. Try both hands, zero/Fractured,
   the cap (item retained), relogging, and ordinary renamed Hearts of the Sea
   (must NOT grant points). Admin menu 'Give Spree Item' should do the same.
7. Confirm old dropped Spree Tokens still auto-claim on pickup. Reclaiming your own
   at Fractured removes the penalty but does not award a bonus. Other players
   claiming tokens retain the previous trap reward. Check no item duplication.
8. Die on streaks 0, 1, 3, 5, 7, 30 in both PvP and natural deaths.
   Positive streaks must be floored half. Natural death at 0 => Fractured;
   PvP death at 0 => zero. Natural death while Fractured => Fractured.
9. Reload/restart an existing 1.2 server with `players.yml` and `config.yml`;
   verify profiles intact and old Recovery label automatically renamed.
