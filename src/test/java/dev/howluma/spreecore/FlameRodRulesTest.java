package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlameRodRulesTest {
    @Test void activeHookIgnitesEnemyOncePerCast() {
        assertTrue(FlameRodRules.canIgnitePlayer(true,true,false,false,false,false));
        assertFalse(FlameRodRules.canIgnitePlayer(true,true,false,false,false,true));
    }

    @Test void neverIgnitesAlliesSelfOrPlayersInProtectedCombatModes() {
        assertFalse(FlameRodRules.canIgnitePlayer(false,true,false,false,false,false));
        assertFalse(FlameRodRules.canIgnitePlayer(true,false,false,false,false,false));
        assertFalse(FlameRodRules.canIgnitePlayer(true,true,true,false,false,false));
        assertFalse(FlameRodRules.canIgnitePlayer(true,true,false,true,false,false));
        assertFalse(FlameRodRules.canIgnitePlayer(true,true,false,false,true,false));
    }

    @Test void fireDurationIsFourSecondsByDefaultAndCappedForSafety() {
        assertEquals(80,FlameRodRules.fireTicks(4.0));
        assertEquals(20,FlameRodRules.fireTicks(1.0));
        assertEquals(1,FlameRodRules.fireTicks(.001));
        assertEquals(300,FlameRodRules.fireTicks(1000));
        assertEquals(0,FlameRodRules.fireTicks(0));
        assertEquals(0,FlameRodRules.fireTicks(-1));
        assertEquals(0,FlameRodRules.fireTicks(Double.NaN));
    }
}
