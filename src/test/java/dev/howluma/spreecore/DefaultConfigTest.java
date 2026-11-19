package dev.howluma.spreecore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class DefaultConfigTest {
    private YamlConfiguration config() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    @Test void onlyHealthAndMaceArePassiveProgression() {
        var c = config();
        assertEquals(10, c.getInt("killstreak.base-health-hearts"));
        assertEquals(0.5, c.getDouble("killstreak.health-per-level-hearts"));
        assertEquals(1, c.getInt("killstreak.mace-unlock-level"));
        assertFalse(c.contains("killstreak.passives"));
        assertFalse(c.contains("killstreak.potion-bonuses"));
        assertFalse(c.contains("killstreak.restrictions"));
        assertFalse(c.contains("killstreak.max-health-hearts"));
        assertFalse(c.contains("events.timed.surge"));
    }
    @Test void darkBlueIndicatorDefaults() {
        var c = config();
        assertTrue(c.getBoolean("display.streak-indicators.tab-enabled"));
        assertTrue(c.getBoolean("display.streak-indicators.nametag-enabled"));
        assertEquals("⚔", c.getString("display.streak-indicators.symbol"));
        assertTrue(c.getString("general.fractured-label").contains("Fractured Killstreak"));
        String[] expected = {"&7","&a","&e","&6","&c","&5","&4"};
        for (int i=0; i<expected.length; i++)
            assertEquals(expected[i], c.getString("display.streak-indicators.tier-colors."+(i*5)));
    }
    @Test void combatDefaultsPreserved() {
        var c = config();
        assertEquals(0.75, c.getDouble("combat.mace.player-damage-multiplier"));
        assertEquals(0.5, c.getDouble("combat.tnt.igniter-self-damage-multiplier"));
    }
}
