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
        assertEquals("✦", defaults.getString("display.streak-indicators.symbol"));
    }
}
