package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure movement-geometry regression tests, not a substitute for Paper teleports. */
class WebEscapeMathTest {
    @Test void detectsWebAtHeadEvenWhenFeetAreInAir() {
        assertTrue(WebEscapeMath.occupied(.7,64.0,.7,1.3,65.8,1.3,
                (x,y,z)-> x==1 && y==65 && z==1));
        assertFalse(WebEscapeMath.occupied(.7,64.0,.7,1.3,64.9,1.3,
                (x,y,z)-> x==1 && y==65 && z==1));
    }

    @Test void detectsWebWhenOnlyPlayersHitboxEdgeIntersects() {
        assertTrue(WebEscapeMath.occupied(.75,64.0,.25,1.32,65.8,.85,
                (x,y,z)->x==1 && y==64 && z==0));
        assertFalse(WebEscapeMath.occupied(.1,64.0,.25,.7,65.8,.85,
                (x,y,z)->x==1 && y==64 && z==0));
    }

    @Test void exactBlockBoundaryDoesNotCountAdjacentWebAsTouching() {
        assertFalse(WebEscapeMath.occupied(1,64,1,2,65.8,2,
                (x,y,z)->x==2));
        assertTrue(WebEscapeMath.occupied(1,64,1,2.001,65.8,2,
                (x,y,z)->x==2));
    }

    @Test void checksBothFeetAndUpperBodyInMultipleBlocks() {
        assertTrue(WebEscapeMath.occupied(.8,64.0,.8,1.35,65.9,1.35,
                (x,y,z)->y==64 && x==0 && z==1));
        assertTrue(WebEscapeMath.occupied(.8,64.0,.8,1.35,65.9,1.35,
                (x,y,z)->y==65 && x==1 && z==0));
    }

    @Test void exitSearchPrefersNearestGroundLevelAndStaysBounded() {
        List<int[]> cells=WebEscapeMath.candidateOffsets(5);
        assertEquals(360,cells.size()); // 8 * (1+2+3+4+5) * 3 levels
        for(int i=0;i<8;i++) {
            assertEquals(0,cells.get(i)[1]); // same Y before jumping a block
            assertEquals(1,Math.max(Math.abs(cells.get(i)[0]),
                    Math.abs(cells.get(i)[2])));
        }
        for(int[] offset:cells)
            assertTrue(Math.max(Math.abs(offset[0]),Math.abs(offset[2]))<=5);
        assertEquals(0,WebEscapeMath.candidateOffsets(0).size());
    }

    @Test void emptyVolumeIsAlwaysSafeFromWebIntersection() {
        assertFalse(WebEscapeMath.occupied(1,2,3,1,4,5,(x,y,z)->true));
    }
}
