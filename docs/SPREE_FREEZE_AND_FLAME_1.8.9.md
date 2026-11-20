# SpreeCore 1.8.9 — timed Spree Freeze and Flame Rod enemy ignition

This release includes BOTH the user-requested admin-controlled timer and the Flame Rod fire fix.

## 1. Global timed Spree Freeze

Admin Center -> Timed Spree Freeze; also Events -> Timed Spree Freeze.

Click any of 5, 10, 15, 30, 60, or 120 minutes; click End to stop before expiry. Starting again replaces the existing timer.

Console or player commands (permission `spree.admin`):
```
/spreeadmin freeze 15m
/spreeadmin freeze 30
/spreeadmin freeze 90s
/spreeadmin freeze 2h
/spreeadmin freeze 1d
/spreeadmin freeze status
/spreeadmin freeze off
```

- Bare number = minutes; optional s/m/h/d suffix; max 7 days.
- Frozen players **never gain or lose killstreak levels** from normal combat deaths, environmental deaths, Fractured penalties, killed-player rewards, event streak bonuses, Spree pickups or withdrawals. Their *current* killstreak remains unchanged when dying, and existing hearts/rewards are reapplied after respawn.
- Freezing disables Fractured changes even on deaths at killstreak zero.
- Death statistics, real kills, PvP, other combat systems and personal kill/death audiovisual effects remain operational.
- A death during freeze does not create an environmental Spree Token and does not consume an anti-farming kill-reward slot.
- Pre-existing Spree Tokens cannot be picked up/claimed; the item remains on the ground. Using a +1 Spree consumable doesn't consume it.
- Staff can still deliberately set scores through `/spreeset`, `/spreesetall`, `/spreeadmin set/add/remove/fracture` or the player admin GUI.
- The remaining time appears in the killstreak HUD; start/end are broadcast.
- Freeze expiry is stored as an absolute timestamp in `plugins/SpreeCore/events.yml`. A server restart does not reset it; expired timers automatically clear on the next event tick. It coexists with Hunt/Blood Moon/Juggernaut modes, though automatic streak rewards are blocked.

## 2. Flame Rod: burning other players

Before 1.8.9, Flame Rod applied visual fire to the bobber and ignited TNT blocks but **did not burn hooked players**.

Now when the flame bobber attaches to an *enemy player*, that player is ignited for **four seconds (80 ticks)** by default. A flame particle burst and audible ignition cue play at the hooked player's location. Vanilla fire damage follows naturally; the rod itself does not deal an additional direct hit.

- Works with the normal Paper `PlayerFishEvent.State.CAUGHT_ENTITY` and, crucially, with `FishHook.getHookedEntity` observed on the active bobber — so the fire begins on actual contact, including the enhanced SpreeCore hooking assist, rather than requiring a second reel-in action.
- Once per player per cast. No infinite fire spam or reapplication on every heartbeat.
- Requires the player to currently have the Flame Rod Talent active.
- Ignores self-hooks, spectators, PvP-disabled worlds and scoreboard teammates with friendly fire disabled.
- Emits Paper's cancellable `EntityCombustByEntityEvent` before setting fire ticks, so region/protection plugins that listen for this event can deny player ignition.
- Successful ignition registers the legitimate rod owner with SpreeCore's recent-PvP credit system so a later fire death within that window can be credited.
- The original TNT ignition, temporary Flame enchant, rod visuals and other Talents are unchanged.

Settings (admin configuration menu or `plugins/SpreeCore/config.yml`):
```yaml
talents:
  flame-rod:
    ignite-players: true
    player-fire-seconds: 4.0
    player-fire-particles: true
    player-fire-sound: true
```

Fire duration can be customized between 0 and 15 seconds, with 0 disabling that part of the effect. To fully disable it use `ignite-players: false`.

## Staging tests (Paper 1.21.11)

1. Back up `players.yml`, `config.yml` and **`events.yml`**; install the 1.8.9 JAR.
2. Give two players different killstreaks; freeze for five minutes through the Admin Center. Kill one, including a fall/environment death. Verify both scores are unchanged, no token is dropped and kill/death stats still count.
3. During freeze, try +1 Spree item, an existing Spree Token, `/spree withdraw 1` and bonus event kills. Verify the score cannot change and consumable/token isn't lost.
4. Run `/spreeadmin freeze status`, restart the server while frozen and verify the timer resumes at its actual remaining time.
5. While frozen, edit a player's score through the staff GUI and `/spreeadmin fracture`. Admin changes must work; automatic ones remain frozen.
6. Run `/spreeadmin freeze off`. Confirm normal streak gain/loss resumes immediately.
7. Give a player Flame Rod, cast and hook an enemy. Verify **the hooked player is visibly on fire for four seconds** without needing a follow-up right click, with flame particles and ignition sound.
8. Cast with Flame Rod again at the same player: it can ignite on the new cast, but must never reapply fire continuously with the same bobber.
9. Hook yourself, an ally with team-friendly-fire disabled or a player in a PvP-disabled world. Verify no ignition.
10. In a protected region, cancel `EntityCombustByEntityEvent`; ignition must not be applied.
11. Repeat hooking using SpreeCore's enlarged rod collision. Both vanilla catches and assisted catches should burn opponents when Flame Rod is active.
12. Burn an enemy to death within the PvP attribution window and check credit. Confirm no extra half-heart fishing damage is dealt.
13. Test TNT-block ignition and hooking primed TNT to ensure no regressions.
14. Confirm Fate Sigils, Haste Infusion, Mace cooldown and SpreeTweaks datapack remain unchanged.

JUnit tests verify the freeze duration parsing and hard cap, automatic score blocking vs staff overrides, expiration and time formatting, plus Flame Rod burning eligibility and fire tick limits. Paper listener event cancellation, real clients and sound reach require live staging verification.
