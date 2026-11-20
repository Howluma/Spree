package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RodGeometryTest {
    @Test void playerMissBecomesHitWithSmallExtraMargin() {
        // A normal player is 0.6 blocks wide. Bobber passes 0.18
        // blocks outside the hitbox, so the 0.20 adjustment catches it.
        double unassisted=RodGeometry.firstContact(
                -2,1,0.48, 2,1,0.48, -.3,0,-.3, .3,1.8,.3,0);
        double assisted=RodGeometry.firstContact(
                -2,1,0.48, 2,1,0.48, -.3,0,-.3, .3,1.8,.3,.20);
        assertTrue(Double.isInfinite(unassisted));
        assertTrue(assisted>=0 && assisted<=1);
    }

    @Test void itemCatchHasSlightlyMoreForgivingMargin() {
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                -1,0,0.4,1,0,0.4,-.125,-.125,-.125,.125,.125,.125,0)));
        assertTrue(Double.isFinite(RodGeometry.firstContact(
                -1,0,0.4,1,0,0.4,-.125,-.125,-.125,.125,.125,.125,.28)));
    }

    @Test void noLongDistanceMagneticRods() {
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                0,1,0,5,1,0,2.4,0,1.5,3,2,2,.2)));
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                0,1,0,5,1,0,2.4,0,1.5,3,2,2,0)));
    }

    @Test void detectsFirstIntersectionNotArbitraryNearestCenter() {
        double fraction=RodGeometry.firstContact(
                0,1,0,10,1,0,3,.5,-.4,3.6,1.5,.4,0);
        assertEquals(.3,fraction,1e-8);
    }

    @Test void zeroMovementCanHitOnlyWhenAlreadyInsideBox() {
        assertEquals(0,RodGeometry.firstContact(
                0,0,0,0,0,0,-1,-1,-1,1,1,1,0));
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                4,0,0,4,0,0,-1,-1,-1,1,1,1,0)));
    }

    @Test void extremeSettingsAreClamped() {
        assertEquals(.5,RodGeometry.boundedMargin(99));
        assertEquals(0,RodGeometry.boundedMargin(-5));
        assertEquals(0,RodGeometry.boundedMargin(Double.NaN));
        assertEquals(5,RodGeometry.boundedSweep(100));
        assertEquals(.5,RodGeometry.boundedSweep(-99));
    }
}
