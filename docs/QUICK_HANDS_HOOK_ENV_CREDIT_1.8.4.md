# SpreeCore 1.8.4 — Quick Hands, Barbed Hook and credited environmental deaths

## Quick Hands eating and drinking
- Previously, Quick Hands multiplied vanilla consumption duration by 0.80.
- Now the multiplier defaults to **0.90**, or 10% shorter consumption time.
- Both food and drinkable potions use the same updated modifier.
- Existing server configs containing the previous exact 0.80 default migrate to 0.90 automatically. Other customized multipliers are preserved.
- The original duration is still restored whenever consumables leave the player's inventory or the Talent becomes inactive, so modified items cannot be traded as permanent fast-consumption items.

## Barbed Hook (new Talent)
- New option in the original Talent selection pool. The original six Talent milestone levels remain unchanged; total choices: 22.
- A valid `PlayerFishEvent.State.CAUGHT_ENTITY` that hooks an *enemy player* awards one fixed half-heart (1.0 health point) of true damage.
- The first part is dispatched as a proper player-owned Bukkit damage event using the fishing hook as its direct source. This preserves most PvP protection hooks, death attribution and normal absorption.
- Armor is bypassed using Minecraft's generic damage type. For an approved damage event, damage reductions caused by Protection/Resistance are compensated to reach a total of 1.0 health point or absorption point, without granting extra damage beyond the intended amount.
- A **cancelled damage event is never forced through**. No damage is applied when PvP is disabled, the player hooks themselves, friendly fire is disabled for their team, or the Talent is inactive.
- Each fishing hook UUID may only inflict the Talent once. Existing fishing mechanics (pulling the victim) are unchanged.
- A successful proc shows hit particles and hook sound feedback. The damage amount is configurable as `talents.barbed-hook.damage-hearts: 0.5`; disabling the Talent is possible with `talents.barbed-hook.enabled: false`.
- When the true-health compensation is lethal, the killer is credited synchronously, rather than spawning an accidental natural-death token.
- This ability is still subject to standard damage-event invulnerability behavior if Paper never delivers an eligible damage event.

## PvP credit followed by environmental death
- The prior code only attributed delayed *explosive* aftereffects, leaving ordinary melee/bow/rod hits followed by environmental deaths sometimes mislabeled as natural deaths.
- Recent legitimate PvP damage is now credited to the attacker if the target dies from **falling, void, fire/lava, drowning, block suffocation, freezing, cramming, hazards or other recognized environmental causes** within the default 12-second window.
- Such deaths use the existing player-kill branch and therefore **do not spawn a natural-death Spree Token**, even if the final damage cause was environmental.
- The actual credited player gets their normal Spree kill reward if the existing anti-farm rules allow it, and the victim follows PvP death streak consequences rather than environmental-death rules.
- Old anti-farm limits still apply. Unattributed deaths and hits outside the attribution window remain ordinary environmental deaths and still drop tokens normally.
- /kill, suicide, custom damages, direct mob attacks and unrelated projectiles do NOT inherit PvP credit from an older hit.
- Configure `combat.pvp-environment-credit.enabled` and `combat.pvp-environment-credit.window-seconds`. The legacy explosive credit feature is retained.

## Staging tests — Paper 1.21.11

1. Back up `plugins/SpreeCore/config.yml` and `players.yml`, then install v1.8.4.
2. Assign Quick Hands via the admin GUI, test steak and potions. Verify the duration is 90% of the usual time (not 80%).
3. Remove the Talent or drop the food; verify normal consumption duration is restored.
4. Assign Barbed Hook; hook a fully armored, healthy enemy **once**. Verify 0.5 heart total (or absorption equivalent), particles and a sound cue.
5. Repeat with Protection armor, Resistance, absorption, and a shield, checking damage remains fixed where the attack is allowed.
6. Hook repeatedly using the same bobber; verify the Talent does not damage multiple times per cast.
7. Hook a teammate with friendly fire off, in a no-PvP world, or where a region plugin cancels PvP; verify no direct health damage.
8. Hook a victim at 0.5 heart for a lethal hit; verify the attacker is credited, the anti-farm limit applies and no natural-death Spree Token spawns.
9. Hit another player with a sword/arrow, let them fall or burn to death **within 12 seconds**; verify kill credit and no natural-death token.
10. Repeat after 13+ seconds or without any player hit; verify ordinary environmental death and normal token drop.
11. Verify /kill and suicide are never turned into credited PvP kills by this new fallback.
12. Confirm all existing Talents, Mace Smash, Fate Sigils, and resource-pack model selectors remain intact.

The automated tests cover damage compensation, cancellation gating, six forms of PvP qualification, and environmental attribution windows. The in-game cases above still require live verification.
