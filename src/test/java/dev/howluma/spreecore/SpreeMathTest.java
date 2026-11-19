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
    @Test void killerReceivesHalfVictimStreakNotLegacyCap() {
        assertEquals(1, SpreeMath.killGain(-1));
        assertEquals(1, SpreeMath.killGain(0));
        assertEquals(1, SpreeMath.killGain(1));
        assertEquals(1, SpreeMath.killGain(3));
        assertEquals(2, SpreeMath.killGain(4));
        assertEquals(3, SpreeMath.killGain(7));
        assertEquals(4, SpreeMath.killGain(9));
        assertEquals(5, SpreeMath.killGain(10));
        assertEquals(7, SpreeMath.killGain(15));
        assertEquals(10, SpreeMath.killGain(20));
        assertEquals(14, SpreeMath.killGain(29));
        assertEquals(15, SpreeMath.killGain(30));
    }
    @Test void thirtyStreakTntKillFromFracturedAwardsFifteen() {
        // Combat attribution delivers the pre-death victim streak to killGain.
        int victimOldStreak = 30;
        int attackerOldStreak = -1;
        int gained = SpreeMath.killGain(victimOldStreak);
        assertEquals(15, gained);
        assertEquals(15, SpreeMath.applyKill(attackerOldStreak, gained, 30));
        assertEquals(15, SpreeMath.deathStreak(victimOldStreak));
        // Positive streaks still respect the configured 30 cap.
        assertEquals(30, SpreeMath.applyKill(20, gained, 30));
    }
    @Test void minimumRewardRecoversFractured() {
        assertEquals(1, SpreeMath.applyKill(-1, SpreeMath.killGain(0), 30));
        assertEquals(1, SpreeMath.applyKill(-1, SpreeMath.killGain(-1), 30));
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
