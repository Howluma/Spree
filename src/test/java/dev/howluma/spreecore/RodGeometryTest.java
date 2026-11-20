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

    @Test void modestlyExpandedDefaultsCatchNearMissesNotDistantTargets() {
        // Player bobber at 0.58 Z is outside the old 0.20 margin
        // (body edge 0.30 + 0.20) but inside the new 0.30 margin.
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                -2,1,.58,2,1,.58,-.3,0,-.3,.3,1.8,.3,.20)));
        assertTrue(Double.isFinite(RodGeometry.firstContact(
                -2,1,.58,2,1,.58,-.3,0,-.3,.3,1.8,.3,.30)));
        // Dropped item radius approx 0.125 plus new 0.35 margin.
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                -1,0,.45,1,0,.45,-.125,-.125,-.125,.125,.125,.125,.25)));
        assertTrue(Double.isFinite(RodGeometry.firstContact(
                -1,0,.45,1,0,.45,-.125,-.125,-.125,.125,.125,.125,.35)));
    }

    @Test void primedTntCanBeHookedWhenBobberBarelyMisses() {
        // Vanilla primed TNT is nearly one block wide.
        // No global hitbox changes: only this local fishing catch is widened.
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                -2,.5,.85,2,.5,.85,-.49,0,-.49,.49,.98,.49,0)));
        assertTrue(Double.isFinite(RodGeometry.firstContact(
                -2,.5,.85,2,.5,.85,-.49,0,-.49,.49,.98,.49,.40)));
        assertTrue(Double.isInfinite(RodGeometry.firstContact(
                -2,.5,1.1,2,.5,1.1,-.49,0,-.49,.49,.98,.49,.40)));
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
