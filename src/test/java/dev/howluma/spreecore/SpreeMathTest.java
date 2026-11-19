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
    @Test void pvpRewardsScaleEveryFiveVictimLevels() {
        assertEquals(1, SpreeMath.killGain(-1));
        assertEquals(1, SpreeMath.killGain(0));
        assertEquals(1, SpreeMath.killGain(1));
        assertEquals(1, SpreeMath.killGain(4));
        assertEquals(2, SpreeMath.killGain(5));
        assertEquals(2, SpreeMath.killGain(9));
        assertEquals(3, SpreeMath.killGain(10));
        assertEquals(3, SpreeMath.killGain(14));
        assertEquals(4, SpreeMath.killGain(15));
        assertEquals(5, SpreeMath.killGain(20));
        assertEquals(6, SpreeMath.killGain(25));
        assertEquals(6, SpreeMath.killGain(29));
        assertEquals(7, SpreeMath.killGain(30));
        assertEquals(7, SpreeMath.killGain(34));
        assertEquals(8, SpreeMath.killGain(35));
    }
    @Test void environmentalTokensUseTheSameRewardTiersAsPvPKills() {
        assertEquals(1, SpreeMath.killGain(0, 1, 5, 1));
        assertEquals(2, SpreeMath.killGain(5, 1, 5, 1));
        assertEquals(3, SpreeMath.killGain(9, 1, 4, 1));
        assertEquals(4, SpreeMath.killGain(10, 2, 10, 2));
        assertEquals(1, SpreeMath.killGain(-10, -1, 0, -1));
        assertEquals(7, SpreeMath.killGain(30));
        assertEquals(5, SpreeMath.killGain(20));
        assertEquals(1, SpreeMath.killGain(0));
        // Victim's penalty still halves their streak, independently of token value.
        assertEquals(15, SpreeMath.naturalDeathStreak(30));
    }
    @Test void thirtyStreakTntKillFromFracturedAwardsSeven() {
        // Credited TNT uses the same PRE-DEATH victim streak as melee kills.
        int victimOldStreak = 30;
        int attackerOldStreak = -1;
        int gained = SpreeMath.killGain(victimOldStreak);
        assertEquals(7, gained);
        assertEquals(7, SpreeMath.applyKill(attackerOldStreak, gained, 30));
        assertEquals(15, SpreeMath.deathStreak(victimOldStreak));
        // Positive streaks still respect the configured 30 cap.
        assertEquals(30, SpreeMath.applyKill(25, gained, 30));
    }
    @Test void minimumRewardRecoversFractured() {
        assertEquals(1, SpreeMath.applyKill(-1, SpreeMath.killGain(0), 30));
        assertEquals(1, SpreeMath.applyKill(-1, SpreeMath.killGain(-1), 30));
    }
    @Test void withdrawalNeverCreatesExtraKillstreak() {
        assertEquals(0, SpreeMath.withdrawRemaining(30, 30));
        assertEquals(25, SpreeMath.withdrawRemaining(30, 5));
        assertEquals(0, SpreeMath.withdrawRemaining(1, 1));
        assertThrows(IllegalArgumentException.class, () -> SpreeMath.withdrawRemaining(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> SpreeMath.withdrawRemaining(0, 1));
        assertThrows(IllegalArgumentException.class, () -> SpreeMath.withdrawRemaining(30, -1));
        assertThrows(IllegalArgumentException.class, () -> SpreeMath.withdrawRemaining(30, 31));
        assertThrows(IllegalArgumentException.class, () -> SpreeMath.withdrawRemaining(30, 0));
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
