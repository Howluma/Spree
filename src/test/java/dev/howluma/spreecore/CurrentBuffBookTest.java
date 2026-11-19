package dev.howluma.spreecore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CurrentBuffBookTest {
    private YamlConfiguration config() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test void onlyCurrentPerksAppear() {
        var c = config();
        assertArrayEquals(new String[]{"&8No active Spree buffs"}, MenuManager.currentBuffLore(c, -1));
        assertArrayEquals(new String[]{"&8No active Spree buffs"}, MenuManager.currentBuffLore(c, 0));

        var one = Arrays.asList(MenuManager.currentBuffLore(c, 1));
        assertEquals(2, one.size());
        assertTrue(one.stream().anyMatch(x -> x.contains("+0.5 hearts")));
        assertTrue(one.stream().anyMatch(x -> x.contains("Mace")));
        assertFalse(one.stream().anyMatch(x -> x.contains("Weaving")));

        var five = Arrays.asList(MenuManager.currentBuffLore(c, 5));
        assertTrue(five.stream().anyMatch(x -> x.contains("Weaving")));
        assertFalse(five.stream().anyMatch(x -> x.contains("Speed I")));
        var ten = Arrays.asList(MenuManager.currentBuffLore(c, 10));
        assertTrue(ten.stream().anyMatch(x -> x.contains("Speed I")));
        assertFalse(ten.stream().anyMatch(x -> x.contains("Strength II")));
        var fifteen = Arrays.asList(MenuManager.currentBuffLore(c, 15));
        assertTrue(fifteen.stream().anyMatch(x -> x.contains("Strength II")));
        assertFalse(fifteen.stream().anyMatch(x -> x.contains("Glowing")));
        var twenty = Arrays.asList(MenuManager.currentBuffLore(c, 20));
        assertEquals(6, twenty.size());
        assertTrue(twenty.stream().anyMatch(x -> x.contains("Glowing")));
    }

    @Test void bookReflectsEditedThresholds() {
        var c = config();
        c.set("killstreak.milestones.weaving-level", 8);
        var five = Arrays.asList(MenuManager.currentBuffLore(c, 5));
        assertFalse(five.stream().anyMatch(x -> x.contains("Weaving")));
        assertTrue(Arrays.asList(MenuManager.currentBuffLore(c, 8)).stream()
                .anyMatch(x -> x.contains("Weaving")));
    }
}
