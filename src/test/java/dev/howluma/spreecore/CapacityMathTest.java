package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapacityMathTest {
    @Test void addsFourPerCopyToNormalStacks() {
        for (int i=0;i<=4;i++) {
            assertEquals(64+4*i,CapacityMath.limit(64,CapacityMath.bonus(i*4)));
            assertEquals(16+4*i,CapacityMath.limit(16,CapacityMath.bonus(i*4)));
        }
    }
    @Test void neverRaisesUnstackableOrPastNinetyNine() {
        assertEquals(1,CapacityMath.limit(1,16));
        assertEquals(99,CapacityMath.limit(64,100));
        assertEquals(32,CapacityMath.bonus(900));
        assertEquals(0,CapacityMath.bonus(Double.NaN));
    }
    @Test void splittingNeverLosesItems() {
        assertArrayEquals(new int[]{64,16},CapacityMath.split(80,64));
        assertArrayEquals(new int[]{16,4},CapacityMath.split(20,16));
        assertArrayEquals(new int[]{99,1},CapacityMath.split(100,99));
        assertArrayEquals(new int[0],CapacityMath.split(0,64));
    }
}