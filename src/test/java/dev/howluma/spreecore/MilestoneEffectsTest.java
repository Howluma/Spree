package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilestoneEffectsTest {
    @Test void unlocksExactlyAtMilestonesAndLocksFractured() {
        assertFalse(MilestoneEffects.unlocked(-1, 1));
        assertFalse(MilestoneEffects.unlocked(0, 5));
        assertFalse(MilestoneEffects.unlocked(4, 5));
        assertTrue(MilestoneEffects.unlocked(5, 5));
        assertFalse(MilestoneEffects.unlocked(9, 10));
        assertTrue(MilestoneEffects.unlocked(10, 10));
        assertFalse(MilestoneEffects.unlocked(14, 15));
        assertTrue(MilestoneEffects.unlocked(15, 15));
        assertFalse(MilestoneEffects.unlocked(19, 20));
        assertTrue(MilestoneEffects.unlocked(20, 20));
        assertTrue(MilestoneEffects.unlocked(30, 20));
        assertFalse(MilestoneEffects.unlocked(0, 0));
    }
}
