# SpreeCore v1.5.3 — test-server checklist

Use a **backup/test copy** of the SMP with Java 21 and Paper 1.21.11. Disable the old Skripts. After every stage, check the server console. A successful CI build proves the code compiles, not that every game interaction has been observed.

## Official pack and vanilla-client compatibility (v1.5.3)

- [ ] With pack: issued Spree item, Tundra Tanto, Blood Butcher, Blood's Bane show correct original art.
- [ ] Without pack: same items render as Heart of the Sea, Diamond Sword, Diamond Axe or Diamond Sword, never missing-texture purple/black.
- [ ] Legacy recognized items with an explicit item_model get normalized on login or /spreeadmin migrateitems without losing stats, enchantments, lore or PDC.
- [ ] Existing PDC-tagged Spree consumables and tokens from prior versions migrate when joining, retaining function.
- [ ] Unrelated items and other plugin's models are not affected by the Spree inventory migration.
- [ ] Archived item_model /give test items may still require pack: replace them with CMD-only safe preview commands from official pack.

## Spree withdrawal regression (v1.5.2)

- [ ] At streak 30, `/spree withdraw` creates exactly 30 +1 Spree items, sets streak to 0, and drops no tokens.
- [ ] At streak 30, `/spree withdraw 5` gives exactly 5 items and leaves streak 25; right-clicking each restores +1.
- [ ] `/spree withdraw all` and `/spree withdraw 1` work normally.
- [ ] At Fractured or 0, invalid amounts, over-balance values, extra arguments, and malformed quantities are rejected without changes.
- [ ] A full inventory, or room for fewer items than requested, leaves streak untouched and creates no items.
- [ ] Existing matching Spree items merge; ordinary Heart of the Sea and environmental-death tokens do not count.
- [ ] Withdrawing refreshes max health, milestone effects and tab display immediately; lifetime best is unchanged.
- [ ] Ordinary players have permission by default, but the admin GUI toggle disables withdrawal immediately and persists across restart.
- [ ] Existing player data migrates unchanged and no inventory/streak duplication occurs on rapid repeated command use.

## TNT / Fractured streak reward regression (v1.5.1)

- [ ] A Fractured player (-1) ignites TNT and gets a credited lethal hit on a victim with 30: killer becomes 15, victim becomes 15, killer receives +15 "for eliminating" message, no Spree Token drops.
- [ ] A player with 30 streak dying to an unowned environmental TNT blast loses half (15); an environmental Spree Token worth +15 drops, but no one is given direct killer credit.
- [ ] Melee, arrows, and attributed TNT all award the same victim-based reward: 7 -> +3, 10 -> +5, 20 -> +10, 30 -> +15 (no +2 cap).
- [ ] Killing a victim at zero or Fractured awards at most the minimum +1, with existing anti-farm protections; farming Fractured victims remains blocked when configured.
- [ ] Existing anti-farm repeat-victim cooldown still blocks repeated rewards regardless of TNT.
- [ ] Large rewards respect the configured max killstreak (30 by default). The right-click Spree item remains exactly +1.
- [ ] Test TNT directly ignited by flint & steel, TNT chains, and explosions from unknown/redstone sources; do not assign an arbitrary player if ownership cannot be proven.

## Milestone perk and current-buffs book regression (v1.5.0)

- [ ] At level 0 and Fractured, the book shows only "No active Spree buffs" and no permanent effects.
- [ ] Level 1 displays only the actual health bonus and mace permission.
- [ ] Level 5 has Weaving I; level 10 adds Speed I; level 15 adds Strength II (amplifier 1); level 20 adds Glowing.
- [ ] Effects refresh indefinitely while eligible, with no flashing, and vanish when the streak falls below their threshold.
- [ ] 20-24 glowing outlines are red, 25-29 purple, 30+ dark red. Hex-configured tier colors use the nearest vanilla team color.
- [ ] Existing faction/party scoreboard teams are not stolen or recolored; document the glow limitation for affected players.
- [ ] Temporarily gaining Strength III from Blood Butcher or Speed III from Aquafier works; the milestone buff resumes after expiration.
- [ ] Death and respawn, relog, Fractured state, and plugin disable leave no lingering Spree-owned effects.
- [ ] The book has no unreached tiers and updates immediately after a streak change or milestone-setting edit.
- [ ] Existing config with the v1.4.3 default Fractured Killstreak label changes to Fractured; custom labels are preserved.
- [ ] The HUD says "Killstreak: Fractured" (not "Killstreak: Fractured Killstreak").

## New temporary progression

- [ ] Fractured (`-1` internally) has **20 health points / 10 hearts**, exactly equal to streak 0. Its name never shows `-1` in the tab list or overhead.
- [ ] Streak 1 = **21 health points / 10.5 hearts**, streak 2 = 22 points / 11 hearts, streak 3 = 23 points / 11.5 hearts, streak 30 = 50 points / 25 hearts.
- [ ] Reaching level 1 grants precisely +0.5 heart; every higher level adds another half, including levels above 20. No old 20-heart cap remains.
- [ ] Test a player at full and partial health when gaining/losing a streak and during a respawn/rejoin. Health must never exceed its actual maximum or drop to zero due to recalculation.
- [ ] Disable all unrelated potion sources. Streak increases must no longer grant Fire Resistance, Hero of the Village, Weaving, Speed, Glowing or potion upgrades. Temporarily applied old effects should expire normally.
- [ ] Old `killstreak.restrictions.enabled: true` and other legacy progression keys should be pruned on upgrade; item pickup, use and inventory movement are never blocked by those retired rules.
- [ ] Mace at Fractured and 0: try grounded attack, fall/smash hit, Wind Burst mace against players **and mobs**. Direct damage must be blocked; vanilla special smash must not trigger as a successful attack. The attacker should see a brief unlock message, not spam.
- [ ] At 1 and above: repeat those mace attacks. Damage works, while the 75% PvP multiplier remains applied. Mobs retain normal mace damage. Other weapons work regardless of streak.
- [ ] Set `killstreak.mace-unlock-level` to 2 in `/spree settings`; level 1 must lose access, level 2 must regain it. Restore to 1. Change `killstreak.health-per-level-hearts`, verify live update, then reset to 0.5.
- [ ] Confirm `Spree Surge` is marked **Paused** in the admin GUI and cannot start through `/spreeevent surge on`.

## Killstreak tier-color and tab icon regression (v1.4.3)

- [ ] The sword symbol uses the menu-heading blue (&9) at all streaks in action bar, tab, and nametag (including zero and Fractured).
- [ ] Only the tab shows a gray `|` separating the player's name and the Spree indicator.
- [ ] Tab, overhead tag and action bar show gray bold numbers at levels 0 and 4; green at 5-9; yellow at 10-14; gold at 15-19; red at 20-24; purple at 25-29; dark red at 30.
- [ ] The sword symbol stays menu-blue (&9), including for Fractured; the Fractured marker remains red and never shows -1.
- [ ] /killstreak, /stats, /leaderboard, dashboard and admin player manager match the tab/HUD tier.
- [ ] Admin can change color 5-9 through /spree settings, see a preview, persist it after restart and reset; invalid color strings are rejected.
- [ ] Upgrading with an existing config.yml supplies default tier colors while preserving customized settings.

## Existing mechanics regression

- [ ] Dying from PvP or environment at streak 7 leaves 3; at 5 leaves 2; at 1 leaves 0. Natural death at zero enters Fractured.
- [ ] Spree consumables grant exactly +1 per right click, consume only one item per click, work in main/offhand, and aren't consumed if already at maximum.
- [ ] Environmental-death Spree Tokens retain their separate pickup/reclaim behavior; anti-farm limits still work.
- [ ] TNT igniter receives 50% self-damage while others take normal damage. Explosive kills give player credit whenever the ownership evidence is reliable.
- [ ] Legendary weapons, forging, Blood's Bane, event-only Juggernaut hearts and health recalculations still work.
- [ ] Tab and overhead (where no other plugin owns the team) display `⚔ 12` with menu-blue symbol and a bold five-kill-tier number; Fractured shows the red marker. Custom symbol changes apply immediately and persist.
- [ ] `/spree settings` and `/spreesettings` work for operators/admins, not other players. Browse all config categories; retired passives, potion bonuses, item unlocks and old health cap must be absent.
- [ ] `/spree` for ordinary players does not show live events or the admin-only legendary list. Main menu uses deeper blue than prior cyan.
- [ ] Restart with a backup copy of existing 1.3 `players.yml` and `config.yml`. User stats and Fractured statuses survive, and the old gem icon migrates without overwriting a manually customized symbol.

**Note:** Paper can vary in how smash/knockback effects are emitted. Test Wind Burst and other damage/knockback-producing mace interactions on your exact Paper build. If another plugin overrides event cancellation at a later priority, adjust that plugin's integration rather than assuming a unit test proves the attack cannot happen.
