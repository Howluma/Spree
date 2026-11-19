package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpreeMathTest {
    @Test void neverBelowMinusOne() {
        assertEquals(-1, SpreeMath.clamp(-500, 30));
        assertEquals(-1, SpreeMath.naturalDeathStreak(-1));
        assertEquals(-1, SpreeMath.naturalDeathStreak(0));
    }
    @Test void deathHalvesPositiveStreak() {
        assertEquals(0, SpreeMath.deathStreak(0));
        assertEquals(0, SpreeMath.deathStreak(1));
        assertEquals(1, SpreeMath.deathStreak(2));
        assertEquals(1, SpreeMath.deathStreak(3));
        assertEquals(2, SpreeMath.deathStreak(5));
        assertEquals(3, SpreeMath.deathStreak(7));
        assertEquals(15, SpreeMath.deathStreak(30));
    }
    @Test void killRewardAndRecovery() {
        assertEquals(1, SpreeMath.killGain(0));
        assertEquals(1, SpreeMath.killGain(3));
        assertEquals(2, SpreeMath.killGain(4));
        assertEquals(2, SpreeMath.killGain(30));
        assertEquals(1, SpreeMath.applyKill(-1, 1, 30));
    }
    @Test void halfHeartForEveryPositiveLevel() {
        assertEquals(20.0, SpreeMath.maxHealthPoints(-1, 10, 0.5));
        assertEquals(20.0, SpreeMath.maxHealthPoints(0, 10, 0.5));
        assertEquals(21.0, SpreeMath.maxHealthPoints(1, 10, 0.5));
        assertEquals(22.0, SpreeMath.maxHealthPoints(2, 10, 0.5));
        assertEquals(23.0, SpreeMath.maxHealthPoints(3, 10, 0.5));
        assertEquals(50.0, SpreeMath.maxHealthPoints(30, 10, 0.5));
    }
    @Test void maceRequiresLevelOneAndFracturedIsLocked() {
        assertFalse(SpreeMath.canUseMace(-1, 1));
        assertFalse(SpreeMath.canUseMace(0, 1));
        assertTrue(SpreeMath.canUseMace(1, 1));
        assertTrue(SpreeMath.canUseMace(30, 1));
        assertFalse(SpreeMath.canUseMace(1, 2));
        assertTrue(SpreeMath.canUseMace(2, 2));
        assertFalse(SpreeMath.canUseMace(0, 0)); // Misconfiguration cannot unlock at zero.
    }
}
