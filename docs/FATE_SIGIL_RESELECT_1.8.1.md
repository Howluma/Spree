# Fate Sigil — historical milestone reselection (SpreeCore 1.8.1)

## Intended behavior

A player earns a milestone when they reach it for the first time. That milestone remains **earned** after killstreak loss, although its **gameplay effects remain inactive until the player's current killstreak reaches the selected milestone again**.

For example, a player with a personal best of 30 and a current killstreak of 0 can still use the Fate Sigil menu to reconfigure their previously earned level-5 and level-30 Infusions. It does **not** grant them the level-30 effect while their current score is 0.

### Privileges and Infusions

Each lane has six distinct options and six milestones. When all six have already been assigned, a Fate Sigil must still provide meaningful choices. Every alternative to the target milestone's current upgrade is shown.

- Choosing an **unassigned** option replaces the one at the selected milestone, spending one Fate Sigil.
- Choosing an option **already assigned at another milestone** exchanges the two choices' milestone positions, spending one Fate Sigil.
- Both existing unlocks survive a swap; no option is duplicated or lost.
- Clicking the already selected option is disallowed; no Sigil is consumed.
- Clicking Back or Close never consumes a Sigil.
- The GUI displays the source milestone and says explicitly when selecting an option will **SWAP**.
- Players cannot open future milestones they have never earned.

### Talents and Perks

- A previously earned Talent can be rerolled using a Fate Sigil while the player is below its milestone. The three saved offers still use normal duplicate prevention.
- When a Talent is rerolled, its paid new offers remain saved, including across closing and reopening menus.
- Previously earned Perks can be seen (marked inactive after streak loss) without revealing any future unearned Perks.
- Perks' automatic reroll mechanism is unchanged; it still rebuilds historical saved rolls.

## Staging server checklist (Paper 1.21.11)

1. Back up `players.yml` and `config.yml`; install the 1.8.1 jar.
2. Give a test player one Sigil using `/fatesigil 1`, then use `/spreeset <player> 30`.
3. Via `/spree > Progression`, claim all six Privileges and six Infusions, preserving their unique selections.
4. Use `/spreeset <player> 0`. Open `/spree > Progression > Fate Sigil > Reselect Privileges`. Verify every previously earned milestone remains visible, and effects are inactive.
5. Choose the level-1 Privilege and select the upgrade currently at level 26. Verify the UI says **swap**, exactly one Sigil is spent, and the two saved slots trade options.
6. Repeat for Infusions (levels 5 and 30), including when the current streak is -1 (Fractured) and when it is between the levels.
7. Choose an unused option if not all six were claimed: verify it **replaces** rather than swaps.
8. Verify trying to select the same option, closing, and backing out do not spend Sigils.
9. Rejoin the server and confirm reordered choices persist. Raise current killstreak and verify effects activate only at their new milestone levels.
10. With best 30/current 0, reroll a previously earned Talent. Verify exactly one Sigil is spent and the newly offered three choices are still available after closing/reopening.
11. Create a fresh account with best 0/current 0 and verify future options stay hidden.
12. Confirm real +1 Spree item as the Fate Sigil recipe center ingredient is unchanged.

## Automated test coverage

`FateSigilReselectionTest` covers complete six-option pools, five available alternatives, swap preservation, unused replacement, unchanged choice rejection, historical-best eligibility, future milestone locks and unchanged normal Talent duplicate filtering.

The automated suite does not replace the in-game ingredient / click-navigation test.
