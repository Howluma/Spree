package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreakIndicatorsTest {
    @Test void tabIncludesCleanGlyphAndActualStreak() {
        String name = StreakIndicators.tabLabel("Howluma", 12, "✦", "↺");
        assertTrue(name.contains("Howluma"));
        assertTrue(name.contains("✦"));
        assertTrue(name.endsWith("12"));
    }
    @Test void nametagKeepsTheNumberNearTheName() {
        assertTrue(StreakIndicators.nametagSuffix(5, "✦", "↺").contains("✦"));
        assertTrue(StreakIndicators.nametagSuffix(5, "✦", "↺").endsWith("5"));
    }
    @Test void recoveryDoesNotMisleadPlayersWithAnUnapprovedNegativeStreak() {
        assertTrue(StreakIndicators.tabLabel("Player", -1, "✦", "↺").endsWith("↺"));
        assertTrue(StreakIndicators.nametagSuffix(-1, "✦", "↺").endsWith("↺"));
    }
}
