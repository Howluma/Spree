package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaceSmashMathTest {
    @Test void visibleMaceOverlayRoundsUpAndNeverOverflows() {
        assertEquals(0, MaceSmashMath.overlayTicks(-100));
        assertEquals(0, MaceSmashMath.overlayTicks(0));
        assertEquals(1, MaceSmashMath.overlayTicks(1));
        assertEquals(1, MaceSmashMath.overlayTicks(50));
        assertEquals(2, MaceSmashMath.overlayTicks(51));
        assertEquals(600, MaceSmashMath.overlayTicks(30_000));
        assertEquals(1200, MaceSmashMath.overlayTicks(60_000));
        assertEquals(72_000, MaceSmashMath.overlayTicks(3_600_000));
        assertEquals(72_000, MaceSmashMath.overlayTicks(Long.MAX_VALUE));
    }

    @Test void onlyAcceptedMaceSmashAttemptsCountNotOrdinaryHits() {
        assertTrue(MaceSmashMath.successfulAttempt(true, false, false));
        assertTrue(MaceSmashMath.successfulAttempt(false, true, false));
        assertFalse(MaceSmashMath.successfulAttempt(false, false, false));
        assertFalse(MaceSmashMath.successfulAttempt(true, true, true));
        assertFalse(MaceSmashMath.successfulAttempt(true, false, true));
    }

    @Test void onlyUnblockedDamageConsumesTheCooldown() {
        assertTrue(MaceSmashMath.connects(true,false,6,false));
        assertTrue(MaceSmashMath.connects(true,false,.2,false));
        assertFalse(MaceSmashMath.connects(false,false,6,false)); // normal mace swing
        assertFalse(MaceSmashMath.connects(true,true,6,false)); // cancelled
        assertFalse(MaceSmashMath.connects(true,false,0,false)); // no actual damage
        assertFalse(MaceSmashMath.connects(true,false,8,true)); // shield
        assertFalse(MaceSmashMath.connects(true,false,Double.NaN,false));
    }
}
