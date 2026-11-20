package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KillDeathFeedbackTest {
    @Test void creditedPvpKillTriggersEvenIfFinalCauseIsEnvironmental() {
        // Same credited-killer parameter StreakManager already resolved.
        assertTrue(KillDeathFeedback.celebrate(true,true,false));
        assertTrue(KillDeathFeedback.celebrate(true,false,false));
        assertFalse(KillDeathFeedback.celebrate(false,true,false));
        assertFalse(KillDeathFeedback.celebrate(false,false,false));
    }

    @Test void optionalOnlyRewardedSettingStillHonorsAntiFarm() {
        assertTrue(KillDeathFeedback.celebrate(true,true,true));
        assertFalse(KillDeathFeedback.celebrate(true,false,true));
        assertFalse(KillDeathFeedback.celebrate(false,true,true));
    }

    @Test void soundVolumesAreSafeAndBounded() {
        assertEquals(0f,KillDeathFeedback.volume(-4),0.00001);
        assertEquals(.8f,KillDeathFeedback.volume(Double.NaN),0.00001);
        assertEquals(2f,KillDeathFeedback.volume(20),0.00001);
        assertEquals(.75f,KillDeathFeedback.volume(.75),0.00001);
    }

    @Test void particleRingCannotCauseUnboundedPerKillWork() {
        assertEquals(8,KillDeathFeedback.ringPoints(0));
        assertEquals(16,KillDeathFeedback.ringPoints(16));
        assertEquals(32,KillDeathFeedback.ringPoints(10_000));
    }
}
