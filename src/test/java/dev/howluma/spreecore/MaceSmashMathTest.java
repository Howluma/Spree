package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaceSmashMathTest {
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
