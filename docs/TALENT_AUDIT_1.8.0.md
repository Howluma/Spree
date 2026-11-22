# SpreeCore 1.8.0 — Talent activation and cooldown audit

**Minecraft:** Paper 1.21.11 (Java 21). All Talents remain passive/automatic, with one saved selection from three offers at milestones 3, 8, 13, 18, 23, and 28. Progression rewards stop at level 30. No active-use item or keybind is required.

## Feedback and testing

On **successful activation**, each timed Talent gets a distinct owner sound, a small nearby particle burst and a brief action-bar message with the cooldown time. On **cooldown completion**, its owner receives a distinct *ready* pling, end-rod particles, action-bar message and optional one-line chat notice. Each ready notice fires **once per activation**, not every heartbeat. Cooldown timers survive logout/restart; active pending notices are rescheduled on login.

Use `/spree cooldowns` (alias `/spree talents`) to see *READY*, *time remaining*, or *PASSIVE* for each currently active Talent. Player Talents and Guide menus show that status on hover. Set `talents.feedback.*` options in `config.yml` / the admin settings GUI to enable/disable sounds, particles, action bars, ready notifications, ready chat and passive-cue throttling.

An admin can prepare a test with `/spreeset <player> 28` and `/spreeadmin` > Players > player > Set Talent, then exercise the relevant action. **The plugin compiles and passes automated regression tests. Real combat, client particles, item-component behavior and shield blocking still require a live Paper server to validate.**

## Audit matrix (22 Talents)

| Talent | Trigger / preserved benefit | Timing | Activation cue | Live QA action |
|---|---|---|---|---|
| Mace Mastery | Successful, unblocked mace smash reduces the cooldown from 60s to 30s | 30s smash cooldown | Heavy metallic strike, impact particles | Land a real falling mace slam; regular hits must not start cooldown |
| Executioner | Hit a target *already below* 50% max health for +10% damage | Passive | Subtle crit | Compare identical hits above/below half health |
| Combo Breaker | 3 landed melee hits within 5s, without being hit back; damage increases by 10% on next hit | Passive | First powered hit gets a crit cue | Verify shielded/cancelled hits do not advance; counter-hits break combo |
| Concussive Blow | Successful melee strike applies Mining Fatigue II | 5s debuff / 45s cooldown | Anvil strike and crit sparks | Misses and cancelled hits must not start cooldown |
| Sunder | *Actual* shield-disable event from axe hit; add 30 ticks to vanilla cooldown | +1.5s disable / 45s cooldown | Shield break and crit sparks | Attack a raised shield with an axe; simple unblocked hits should not proc |
| Riposte | Real frontal shield block slows attacker (Slowness II) and prevents jumps | 2s / 45s cooldown | Shield block and crit sparks | Block a melee hit, jump as attacker, then attack the defender from behind |
| Second Wind | Lethal/critical hit that takes player below 30% HP grants Absorption I | 8s / 90s cooldown | Healing chime and hearts | Trigger a damaging hit while near threshold; shared defense cooldown applies |
| Iron Will | Incoming hit of at least 3 hearts grants Resistance I (20%) | 5s / 75s cooldown | Anvil and enchant particles | Land one large hit, verify subsequent damage reduction; share defense lock |
| Bloodlust | Every five successful fully charged melee hits heals one heart, if wounded | 15s after heal | Healing chime and hearts | Test charged vs spam hits, while at max HP, and while cooling down |
| Field Medic | Anti-farm-eligible PvP kill restores 5 hearts, if wounded | 60s cooldown | Healing chime and hearts | Get legitimate kill while injured; repeated farm kill should not heal |
| Golden Aegis | Eating a regular golden apple adds 2 absorption hearts | 45s cooldown | Enchant particles | Eat apple after taking damage; no activation on cancelled consumption |
| Windrunner | Wind charge fired by self impacts within range; boost own propulsion 25% | Passive | Brief wind sound/cloud | Fire wind charge near own feet and compare launch height |
| Escape Artist | Hit while slowed/in a cobweb; clear Slowness and apply Speed III for 3s. Step into safe adjacent air when webbed, else apply a lift | 50s cooldown | Wind sound/cloud | Get hit in a web and while slowed; never teleport through solid blocks |
| Acrobat | Multiply fall damage by .6; increase jump strength by 10% | Passive | Subtle cloud on fall protection | Compare fall damage and jump reach |
| Trailblazer | +0.4 step-height attribute (normally lets player step up 1-block ledges) | Passive | Unlock sound only | Walk into a one-block ledge without jumping |
| Expanded Reserves | Increases capped wind-charge and XP-bottle inventory capacity by one stack | Passive | Unlock sound only | Attempt to carry 128 capped items with active Talent |
| Battle Sense | Shows attacked player's current health in actionbar for up to 8s after a real hit | Passive | Subtle chime when tracking target starts | Hit opponent, watch 8s of health display, verify HUD resumes |
| Scavenger | 15% chance on genuine hit to restore up to 4 wind charges, respecting capacity | 90s *after successful reward* | Item pickup sound and green burst | Test at 0, near limit, full inventory; no cooldown if none awarded |
| Quick Hands | 20% faster ordinary food and drink consumables via Paper data components | Passive | Soft chime when consumption succeeds | Time golden apple and drink vs base; check items revert when dropped |
| Streak Insurance | Saves up to 2 killstreak levels that would otherwise be lost on death | 10 minute cooldown | Beacon/totem effect with respawn message | Die above 0 streak, respawn and verify saved amount; cooldown persists |
| Blacksmith | Unlocks 2-gold golden apple recipe and 8-nugget/stick breeze rod recipe | Passive | Subtle craft cue | Craft both recipes; verify other players cannot claim result |
| Industrious | Permanent Haste I through milestone refresh | Passive | Unlock sound only | Mine after unlock; confirm Haste disappears if Talent inactive |

## Trigger fixes in 1.8.0

- **Sunder** now uses Paper's `PlayerShieldDisableEvent`; extend actual shield-disable cooldown before application rather than guessing via delayed `setCooldown`.
- **Riposte** checks actual blocked shield damage (or a narrowly restricted frontal zero-damage fallback for newer shield internals), rather than proccing whenever the shield happens to be raised.
- **Combo Breaker, Bloodlust and Concussive Blow** now require successful connected hits. Blocked/failed damage no longer incorrectly progresses hit counts or starts cooldowns.
- **Bloodlust** banks its five-hit charge until the player actually needs healing and the ability is ready, instead of throwing away a completed charge during cooldown.
- **Scavenger** starts cooldown only after granting at least one wind charge and respects inventory space.
- **Field Medic and Golden Aegis** only start cooldown when health/absorption actually increases.
- **Escape Artist** can make a safe one-block step out of a cobweb; it does not break blocks or pass through walls.
- **Streak Insurance** reports saved killstreak levels after respawn.
- **Quick Hands, Blacksmith, Acrobat, Windrunner, Battle Sense** now have muted, rate-limited passive usage cues. Trailblazer, Expanded Reserves, Industrious remain silent during their continuous passive operation but chime when first unlocked.
- The status command and GUI never expose locked future Talents.

## QA limitations

Automated checks cover the full Talent catalog, 12 cooldown-to-Talent mappings, signal timers and configuration defaults. The project does **not** yet run a real Paper server in CI, so no claim is made that all 22 event interactions have passed live integration tests. Validate real PvP, shield blocks, cobweb rescue, item consumption, and post-death effects on a staging Paper 1.21.11 server before publishing.
