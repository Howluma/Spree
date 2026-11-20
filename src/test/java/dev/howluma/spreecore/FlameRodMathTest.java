package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlameRodMathTest {
    @Test void bobberInsideOrJustOutsideTntCollisionVolumeCanIgnite() {
        assertTrue(FlameRodMath.touchesBlock(.5,.5,.5,0,0,0,.35));
        assertTrue(FlameRodMath.touchesBlock(1.10,.5,.5,0,0,0,.35));
        assertTrue(FlameRodMath.touchesBlock(-.25,.5,.5,0,0,0,.35));
        assertTrue(FlameRodMath.touchesBlock(1.0,1.0,1.0,0,0,0,0));
    }

    @Test void doesNotIgniteTntAtAVisibleButNotTouchingDistance() {
        assertFalse(FlameRodMath.touchesBlock(1.5,.5,.5,0,0,0,.35));
        assertFalse(FlameRodMath.touchesBlock(.5,1.6,.5,0,0,0,.35));
        assertFalse(FlameRodMath.touchesBlock(.5,.5,.5,3,0,0,.7));
        assertFalse(FlameRodMath.touchesBlock(Double.NaN,0,0,0,0,0,.35));
        assertFalse(FlameRodMath.touchesBlock(0,0,0,0,0,0,-1));
    }

    @Test void scansTravelledSegmentWithoutUnboundedWork() {
        assertEquals(1,FlameRodMath.samples(0,0,0));
        assertEquals(3,FlameRodMath.samples(1,0,0));
        assertEquals(6,FlameRodMath.samples(0,0,2));
        assertEquals(32,FlameRodMath.samples(100,0,0));
        assertEquals(32,FlameRodMath.samples(Double.POSITIVE_INFINITY,0,0) == 1 ? 1 : 32);
    }

    @Test void noFlameDamageTalentRemainsInCatalog() {
        var talent=ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,"flame_rod");
        assertNotNull(talent);
        assertTrue(talent.description().contains("ignite TNT"));
        assertNull(ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,"barbed_hook"));
    }
}
