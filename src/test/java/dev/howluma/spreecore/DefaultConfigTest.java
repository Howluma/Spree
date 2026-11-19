package dev.howluma.spreecore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class DefaultConfigTest {
    @Test void unfinishedItemRulesAreNotEnforcedByDefault() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertFalse(defaults.getBoolean("killstreak.restrictions.enabled", true));
    }
    @Test void tabAndNameTagsAreOptOutSettings() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(defaults.getBoolean("display.streak-indicators.tab-enabled"));
        assertTrue(defaults.getBoolean("display.streak-indicators.nametag-enabled"));
        assertEquals("◆", defaults.getString("display.streak-indicators.symbol"));
        assertEquals("✕", defaults.getString("display.streak-indicators.fractured-symbol"));
        assertTrue(defaults.getString("general.fractured-label").contains("Fractured Killstreak"));
    }
    @Test void combatDefaultsMatchRequestedBalance() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertEquals(0.75, defaults.getDouble("combat.mace.player-damage-multiplier"));
        assertEquals(0.5, defaults.getDouble("combat.tnt.igniter-self-damage-multiplier"));
    }
}
