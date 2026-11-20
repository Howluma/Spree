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
    @Test void victimKillstreakAddsFivePointsToMaceAndTenPointsToTnt() {
        assertEquals(0.75, CombatMath.streakDamageMultiplier(0.75, -1, 0.05), 1e-9);
        assertEquals(0.75, CombatMath.streakDamageMultiplier(0.75, 0, 0.05), 1e-9);
        assertEquals(1.00, CombatMath.streakDamageMultiplier(0.75, 5, 0.05), 1e-9);
        assertEquals(1.25, CombatMath.streakDamageMultiplier(0.75, 10, 0.05), 1e-9);
        assertEquals(1.00, CombatMath.streakDamageMultiplier(1.0, 0, 0.10), 1e-9);
        assertEquals(1.10, CombatMath.streakDamageMultiplier(1.0, 1, 0.10), 1e-9);
        assertEquals(1.50, CombatMath.streakDamageMultiplier(1.0, 5, 0.10), 1e-9);
        assertEquals(4.50, CombatMath.streakDamageMultiplier(1.0, 35, 0.10), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> CombatMath.streakDamageMultiplier(0.75, 5, -0.05));
    }

    @Test void minecartCapIsSevenHeartsPlusHalfAHeartPerVictimLevel() {
        assertEquals(14.0, CombatMath.minecartCapHealthPoints(-1, 7, 0.5), 1e-9);
        assertEquals(14.0, CombatMath.minecartCapHealthPoints(0, 7, 0.5), 1e-9);
        assertEquals(15.0, CombatMath.minecartCapHealthPoints(1, 7, 0.5), 1e-9);
        assertEquals(19.0, CombatMath.minecartCapHealthPoints(5, 7, 0.5), 1e-9);
        assertEquals(24.0, CombatMath.minecartCapHealthPoints(10, 7, 0.5), 1e-9);
    }

    @Test void minecartCapAppliesAfterArmorWithoutIncreasingSmallExplosions() {
        // With 75% armor mitigation, 56 raw points = 14 final = 7 hearts.
        double raw = CombatMath.capRawDamage(100.0, 14.0, candidate -> candidate * 0.25);
        assertEquals(56.0, raw, 0.0001);
        assertTrue(raw * 0.25 <= 14.000001);
        assertEquals(20.0, CombatMath.capRawDamage(20.0, 14.0,
                candidate -> candidate * 0.25), 1e-9);
        double nonlinear = CombatMath.capRawDamage(100.0, 14.0,
                candidate -> candidate * (0.20 + candidate * 0.004));
        assertTrue(nonlinear * (0.20 + nonlinear * 0.004) <= 14.000001);
        assertThrows(IllegalArgumentException.class,
                () -> CombatMath.capRawDamage(20.0, -14.0, candidate -> candidate));
    }

}
