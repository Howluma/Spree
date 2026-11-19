# SpreeCore: test-server checklist

Test on a **copy** of the server with the original ten Skript scripts disabled. Use Paper 1.21.11 and Java 21. Check the server console for exceptions after **every** test.

## First launch / data

- [ ] Plugin starts without `Skript`, and creates `plugins/SpreeCore/config.yml`.
- [ ] `/spree`, `/spreeadmin`, `/spreeevent`, `/legendary` menus open and their buttons work.
- [ ] `/spreeadmin set <player> 10` changes the actionbar, tab display, max health, permissions and unlocks.
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

**Testing note:** This source distribution was syntax-checked and its standalone streak arithmetic tested, but the complete Paper dependency-backed build and live in-game integration tests were unavailable in the generation environment. The included GitHub Actions workflow runs `gradle clean test build` to validate compilation and JUnit tests before creating a JAR.
