# SpreeCore v1.4 — test-server checklist

Use a **backup/test copy** of the SMP with Java 21 and Paper 1.21.11. Disable the old Skripts. After every stage, check the server console. A successful CI build proves the code compiles, not that every game interaction has been observed.

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

## Existing mechanics regression

- [ ] Dying from PvP or environment at streak 7 leaves 3; at 5 leaves 2; at 1 leaves 0. Natural death at zero enters Fractured.
- [ ] Spree consumables grant exactly +1 per right click, consume only one item per click, work in main/offhand, and aren't consumed if already at maximum.
- [ ] Environmental-death Spree Tokens retain their separate pickup/reclaim behavior; anti-farm limits still work.
- [ ] TNT igniter receives 50% self-damage while others take normal damage. Explosive kills give player credit whenever the ownership evidence is reliable.
- [ ] Legendary weapons, forging, Blood's Bane, event-only Juggernaut hearts and health recalculations still work.
- [ ] Tab and overhead (where no other plugin owns the team) display `⚔ 12` with navy symbol and bold blue number; Fractured shows the red marker. Custom symbol changes apply immediately and persist.
- [ ] `/spree settings` and `/spreesettings` work for operators/admins, not other players. Browse all config categories; retired passives, potion bonuses, item unlocks and old health cap must be absent.
- [ ] `/spree` for ordinary players does not show live events or the admin-only legendary list. Main menu uses deeper blue than prior cyan.
- [ ] Restart with a backup copy of existing 1.3 `players.yml` and `config.yml`. User stats and Fractured statuses survive, and the old gem icon migrates without overwriting a manually customized symbol.

**Note:** Paper can vary in how smash/knockback effects are emitted. Test Wind Burst and other damage/knockback-producing mace interactions on your exact Paper build. If another plugin overrides event cancellation at a later priority, adjust that plugin's integration rather than assuming a unit test proves the attack cannot happen.
