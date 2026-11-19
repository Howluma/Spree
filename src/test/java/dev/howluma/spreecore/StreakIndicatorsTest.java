package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreakIndicatorsTest {
    @Test void colorsChangeAtExactlyFiveKillIntervals() {
        assertEquals("&c&l", StreakIndicators.color(-1));
        int[] starts = {0,5,10,15,20,25,30};
        String[] colors = {"&7&l","&a&l","&e&l","&6&l","&c&l","&5&l","&4&l"};
        for (int i=0; i<starts.length; i++) {
            assertEquals(colors[i], StreakIndicators.color(starts[i]), "At " + starts[i]);
            if (i<starts.length-1) assertEquals(colors[i], StreakIndicators.color(starts[i]+4));
        }
        assertEquals("&4&l", StreakIndicators.color(50));
    }
    @Test void tabAndNametagShareTheSameColorAndBoldNumbers() {
        assertEquals("&fHowluma &1\u2694 &e&l12", StreakIndicators.tabLabel("Howluma",12,"\u2694","\u2715"));
        assertEquals(" &1\u2694 &e&l12", StreakIndicators.nametagSuffix(12,"\u2694","\u2715"));
        assertEquals("&fHowluma &1\u2694 &a&l5", StreakIndicators.tabLabel("Howluma",5,"\u2694","\u2715"));
        assertEquals(" &1\u2694 &4&l30", StreakIndicators.nametagSuffix(30,"\u2694","\u2715"));
    }
    @Test void fracturedIsAStatusNotAMinusOne() {
        String tab = StreakIndicators.tabLabel("Howluma", -1, "\u2694", "\u2715");
        assertEquals("&fHowluma &c\u2694 &c&l\u2715", tab);
        assertFalse(tab.contains("-1"));
    }
}
