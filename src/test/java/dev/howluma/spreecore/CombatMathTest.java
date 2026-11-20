package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatMathTest {
    @Test void maceToPlayersDealsThreeQuartersDamage() {
        assertEquals(7.5, CombatMath.scaleDamage(10.0, 0.75), 0.00001);
    }
    @Test void igniterOwnTntDamageIsHalved() {
        assertEquals(10.0, CombatMath.scaleDamage(20.0, 0.50), 0.00001);
    }
    @Test void invalidDamageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CombatMath.scaleDamage(10, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> CombatMath.scaleDamage(-1, 0.5));
    }
    @Test void recentlyDamagedVictimDyingToEnvironmentCreditsAttacker() {
        var Fall=org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL;
        assertTrue(CombatMath.creditedEnvironmentalDeath(Fall,20_000,10_000,12));
        assertTrue(CombatMath.creditedEnvironmentalDeath(
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.LAVA,20_000,10_000,12));
        assertTrue(CombatMath.creditedEnvironmentalDeath(
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID,20_000,10_000,12));
        assertTrue(CombatMath.creditedEnvironmentalDeath(
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.DROWNING,20_000,10_000,12));
        assertTrue(CombatMath.creditedEnvironmentalDeath(
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUFFOCATION,20_000,10_000,12));
        assertFalse(CombatMath.creditedEnvironmentalDeath(Fall,24_000,10_000,12));
    }

    @Test void commandsAndDirectMobAttacksNeverInheritPvpCredit() {
        for (var cause : new org.bukkit.event.entity.EntityDamageEvent.DamageCause[]{
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.KILL,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUICIDE,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE}) {
            assertFalse(CombatMath.creditedEnvironmentalDeath(cause,11_000,10_000,12),
                    "Must not reassign "+cause);
        }
    }

    @Test void killCreditExpiresOutsideWindow() {
        assertTrue(CombatMath.recent(12_000, 10_000, 12));
        assertFalse(CombatMath.recent(25_000, 10_000, 12));
        assertFalse(CombatMath.recent(9_000, 10_000, 12));
    }
}
