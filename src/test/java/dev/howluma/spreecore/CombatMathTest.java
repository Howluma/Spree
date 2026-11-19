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
    @Test void killCreditExpiresOutsideWindow() {
        assertTrue(CombatMath.recent(12_000, 10_000, 12));
        assertFalse(CombatMath.recent(25_000, 10_000, 12));
        assertFalse(CombatMath.recent(9_000, 10_000, 12));
    }
}
