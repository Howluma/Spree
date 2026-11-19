package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreakIndicatorsTest {
    @Test void tabNumbersAreBoldDarkBlue() {
        assertEquals("&fHowluma &1⚔ &9&l12", StreakIndicators.tabLabel("Howluma", 12, "⚔", "✕"));
        assertEquals(" &1⚔ &9&l12", StreakIndicators.nametagSuffix(12, "⚔", "✕"));
    }
    @Test void fracturedIsAStatusNotAMinusOne() {
        String tab = StreakIndicators.tabLabel("Howluma", -1, "⚔", "✕");
        assertEquals("&fHowluma &c⚔ &c&l✕", tab);
        assertFalse(tab.contains("-1"));
    }
}
