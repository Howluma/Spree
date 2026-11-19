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
    @Test void health() {
        assertEquals(10, SpreeMath.healthHearts(0, 10, 20));
        assertEquals(11, SpreeMath.healthHearts(3, 10, 20));
        assertEquals(20, SpreeMath.healthHearts(30, 10, 20));
    }
}
