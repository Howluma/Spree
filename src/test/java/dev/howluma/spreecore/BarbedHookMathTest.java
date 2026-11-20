package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarbedHookMathTest {
    @Test void exactlyHalfHeartIsOneHealthPoint() {
        assertEquals(1.0,BarbedHookMath.requestedPoints(.5),1e-8);
        assertEquals(.5,BarbedHookMath.requestedPoints(.25),1e-8);
        assertEquals(0,BarbedHookMath.requestedPoints(0),1e-8);
        assertEquals(0,BarbedHookMath.requestedPoints(Double.NaN),1e-8);
    }

    @Test void restoresArmorOrResistanceDamageDeficitWithoutDoubleDamaging() {
        assertEquals(.7,BarbedHookMath.additionalPoints(1.0,20.0,19.7,0,0,true),1e-8);
        assertEquals(0.0,BarbedHookMath.additionalPoints(1.0,20.0,19.0,0,0,true),1e-8);
        assertEquals(0.0,BarbedHookMath.additionalPoints(1.0,20.0,18.0,0,0,true),1e-8);
        assertEquals(.8,BarbedHookMath.additionalPoints(1.0,20.0,20.0,5,4.8,true),1e-8);
        assertEquals(0.0,BarbedHookMath.additionalPoints(1.0,20.0,19.5,5,4.5,true),1e-8);
    }

    @Test void cancelledOrInvulnerableDamageIsNeverForcedThrough() {
        assertEquals(0,BarbedHookMath.additionalPoints(1.0,20,20,0,0,false),1e-8);
        assertEquals(0,BarbedHookMath.additionalPoints(1.0,20,20,5,5,false),1e-8);
    }

    @Test void approvedButCompletelyResistedHitStillDealsTrueDamage() {
        assertEquals(1.0,BarbedHookMath.additionalPoints(1.0,20,20,0,0,true),1e-8);
        assertEquals(0,BarbedHookMath.additionalPoints(1.0,20,20,0,0,false),1e-8);
    }

    @Test void pvpAndTeamRulesAreAlwaysApplied() {
        assertTrue(BarbedHookMath.eligible(true,false,false,false,true));
        assertFalse(BarbedHookMath.eligible(false,false,false,false,true));
        assertFalse(BarbedHookMath.eligible(true,true,false,false,true));
        assertFalse(BarbedHookMath.eligible(true,false,true,false,true));
        assertFalse(BarbedHookMath.eligible(true,false,false,true,true));
        assertFalse(BarbedHookMath.eligible(true,false,false,false,false));
    }
}
