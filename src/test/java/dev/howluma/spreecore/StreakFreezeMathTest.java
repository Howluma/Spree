package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for timed global Spree freeze and safe duration handling. */
class StreakFreezeMathTest {
    @Test void acceptsSimpleMinutesAndExplicitTimeUnits() {
        assertEquals(30*60,StreakFreezeMath.parseSeconds("30"));
        assertEquals(5*60,StreakFreezeMath.parseSeconds("5m"));
        assertEquals(2*60*60,StreakFreezeMath.parseSeconds("2h"));
        assertEquals(90,StreakFreezeMath.parseSeconds("90s"));
        assertEquals(24*60*60,StreakFreezeMath.parseSeconds("1d"));
        assertEquals(7*24*60*60,StreakFreezeMath.parseSeconds("7d"));
        assertEquals(15*60,StreakFreezeMath.parseSeconds(" 15M "));
    }

    @Test void preventsInvalidOrExcessivelyLongFreezes() {
        for(String bad:new String[]{"0","0m","-5m","abc","1.5h","8d",
                "0s","60x","1w","999999999h","","10000000000000000"}) {
            assertEquals(-1,StreakFreezeMath.parseSeconds(bad),bad);
        }
        assertEquals(-1,StreakFreezeMath.parseSeconds(null));
        assertThrows(IllegalArgumentException.class,()->StreakFreezeMath.endAt(1000,0));
        assertThrows(IllegalArgumentException.class,()->StreakFreezeMath.endAt(1000,604801));
    }

    @Test void expirationUsesAbsoluteWallTimeAndSurvivesRestarts() {
        long started=1_700_000_000_000L;
        long until=StreakFreezeMath.endAt(started,90);
        assertEquals(started+90_000,until);
        assertTrue(StreakFreezeMath.active(until,started));
        assertTrue(StreakFreezeMath.active(until,until-1));
        assertFalse(StreakFreezeMath.active(until,until));
        assertFalse(StreakFreezeMath.active(until,until+1_000));
        assertEquals(90,StreakFreezeMath.secondsRemaining(until,started));
        assertEquals(2,StreakFreezeMath.secondsRemaining(until,until-1_001));
        assertEquals(1,StreakFreezeMath.secondsRemaining(until,until-1));
        assertEquals(0,StreakFreezeMath.secondsRemaining(until,until));
    }

    @Test void formatStatusIsReadableForGuiAndCommands() {
        assertEquals("0s",StreakFreezeMath.format(0));
        assertEquals("15m",StreakFreezeMath.format(900));
        assertEquals("1h 30m",StreakFreezeMath.format(5_400));
        assertEquals("1h 1m 1s",StreakFreezeMath.format(3_661));
        assertEquals("1d 2h 5s",StreakFreezeMath.format(86400+7200+5));
    }

    @Test void frozenScoresCannotChangeAutomaticallyButStaffCanOverride() {
        assertTrue(StreakFreezeMath.blocksAutomaticChange(true,false),
                "Natural death and kills cannot change streak while frozen");
        assertFalse(StreakFreezeMath.blocksAutomaticChange(true,true),
                "Explicit admin set or fracture may override the freeze");
        assertFalse(StreakFreezeMath.blocksAutomaticChange(false,false));
        assertFalse(StreakFreezeMath.blocksAutomaticChange(false,true));
    }
}
