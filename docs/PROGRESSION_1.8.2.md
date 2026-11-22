# SpreeCore 1.8.2 — Haste Infusion and Retired Industrious Talent

## New design
- **Haste I** is now a permanent *Infusion* available at infusion milestones (5, 10, 15, 20, 25 and 30), and works only while a player's current killstreak meets the assigned milestone.
- **Strength I** has been retired from the Infusion pool. Haste I replaces it one-for-one: there are still **six Infusion choices** for six Infusion milestones.
- **Industrious**, the old Haste-granting Talent, has been retired. There are now **21 available Talents** for the usual six Talent milestones (3, 8, 13, 18, 23 and 28). All other Talents, cooldown cues and balance are unchanged.

## Existing player-data migration
The server performs migration when `players.yml` is loaded:
1. Any saved `infusion-choices.<level>: strength` becomes `infusion-choices.<level>: haste`. The same milestone is retained. No Fate Sigil is consumed, and the rest of the player's choices/streak/best are unchanged.
2. Any `talent-choices.<level>: industrious` is removed because that Talent no longer exists. Its earned milestone is marked for a **free replacement**, saved under `retired-talent-milestones`. That player can revisit `/spree > Progression > Talents` to choose a new Talent from the normal **three offered choices** without a Fate Sigil, even when their current killstreak has fallen but their milestone was previously earned.
3. If a previously saved three-Talent offer contained `industrious`, its other valid choices are preserved. Only the retired option is replaced with a valid nonduplicate Talent, without an extra free reroll.
4. After claiming a replacement Talent, its refund marker is removed. Previously earned rewards remain inactive when the current killstreak is below the corresponding milestone, exactly as before.

The migration is idempotent: after the new choices are saved, restarting the server does not re-grant anything or change the repaired options again.

## How to check in-game (Paper 1.21.11)
1. Back up `plugins/SpreeCore/players.yml` and `config.yml`; stop the server and install 1.8.2.
2. For an account that formerly chose Strength I, verify Haste I now appears in its Infusion slot and applies Haste I (not Strength I) at the same current killstreak threshold.
3. Below the Infusion milestone, verify Haste is inactive; above it, verify the expected effect is present.
4. For an account that formerly chose Industrious, open `/spree > Progression > Talents`. Verify its old milestone can be reclaimed for free, with no loss of other earned Talents.
5. Inspect the three choices; verify Industrious is absent. Claim a new Talent, relog/restart, and ensure the replacement remains saved.
6. Verify unknown future Talents remain hidden and historical Fate Sigil swapping of six Infusions still functions.
7. Verify no automatic new benefits are earned past killstreak 30.

The original 1.8.0 Talent audit documented 22 Talents. As of 1.8.2 there are **21** because Industrious was retired, while Haste lives in the Infusion lane.
