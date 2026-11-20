# Spree SMP - Legendary Ability Rework

Design reference for the reworked legendary weapons. This file documents **design decisions**, not implemented Java mechanics. Do not treat an entry as shipped until its implementation and gameplay testing are complete.

## Shared four-tier legendary framework

Each legendary is held in the **main hand**, with abilities tied to the **wielder's current killstreak**:

| Killstreak | Ability | Activation |
| --- | --- | --- |
| 0 | Passive I | Automatic while wielding |
| 3 | Active I | Offhand-swap key (default F) |
| 5 | Passive II | Automatic while wielding |
| 7 | Active II | Sneak + offhand-swap key (default Shift+F) |

- Both active controls trigger abilities **instead of physically swapping hands** while a legendary is in the main hand. 
- Two active slots have **separate cooldowns**.
- Locked abilities become unavailable if killstreak falls below their thresholds.
- Passives operate while the weapon is wielded, not simply carried in inventory.
- The GUI and item lore should clearly identify current unlocks, ability effects and cooldowns.
- All numerical balance values should be adjustable when implemented.

## Tundra Tanto - agreed design

**Identity:** Frost assassin / movement suppression and precision. The weapon does **not** use Minecraft's vanilla Slowness potion effect. Its movement suppression should affect grounded movement, movement in the air, swimming, jumping, knockback and external boosts (including wind charges).

### Level 0: Passive I - Cold Steel / Frostbite
- Each successful melee hit with the Tantō adds one Frostbite stack to the target.
- Frostbite stacks persist for **5 seconds**.
- At **3 stacks**, the target is subject to **20% movement reduction in all directions and movement modes** for **2 seconds**; the stacks reset.
- Do not repeatedly compound velocity against itself every tick, and do not substitute the Slowness potion effect.

### Level 3: Active I - Cold Snap (F)
- Arms the next successful melee hit within **6 seconds**.
- That hit causes a **hard freeze for 0.8 seconds**, anchoring the target to its position with no vertical or horizontal movement, momentum or knockback.
- Target can still look around.
- After the hard freeze: **20% all-movement reduction for 2 seconds**.
- Cooldown: **28 seconds**.

### Level 5: Passive II - Shatter
- A melee strike on a target currently under Frostbite's movement reduction **or** a hard freeze deals **15% bonus damage**.
- Ice-breaking sound and crystalline particles.
- **6-second cooldown per target**.

### Level 7: Active II - Absolute Zero (Shift+F)
- Emits a **5-block radius frost shockwave** around the wielder.
- Affected opponents are hard-frozen for **1.2 seconds**.
- Afterward they receive **20% all-movement reduction for 3 seconds**.
- Frozen targets are vulnerable to Shatter.
- Cooldown: **75 seconds**.
- The shockwave is the current working ability form; visual execution may be revisited.

### Shared control-effect rules
- **Movement suppression:** 80% movement remains in walking, sprinting, jumping, air movement, swimming and external velocity. No vanilla Slowness.
- **Hard freeze:** 0% positional movement, including horizontal/vertical velocity and external impulses. The affected player can look around.
- Multiple hard freezes do not add durations together; the **longer remaining freeze wins**.
- Consider safe teleports/respawns, anti-cheat compatibility, protection against rubber-banding and clean state restoration when coding.
- Frostbite: subtle snow particles. Hard freeze: ice-crystal burst.

## Other legendary designs

Pending individual review. Candidates in the current SpreeCore roster: Blood Butcherer (Blood Butcher), Storm Breaker, Aquafier, Darkheart, Hailstorm and Blood's Bane.
