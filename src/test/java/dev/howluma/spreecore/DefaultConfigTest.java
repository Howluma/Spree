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
    @Test void healthMaceAndFourMilestoneBuffsAreTheOnlyPermanentProgression() {
        var c = config();
        assertEquals(10, c.getInt("killstreak.base-health-hearts"));
        assertEquals(0.5, c.getDouble("killstreak.health-per-level-hearts"));
        assertEquals(1, c.getInt("killstreak.mace-unlock-level"));
        assertTrue(c.getBoolean("killstreak.withdrawal.enabled"));
        assertEquals(5, c.getInt("killstreak.milestones.weaving-level"));
        assertEquals(10, c.getInt("killstreak.milestones.speed-level"));
        assertEquals(15, c.getInt("killstreak.milestones.strength-level"));
        assertEquals(20, c.getInt("killstreak.milestones.glowing-level"));
        assertFalse(c.contains("killstreak.passives"));
        assertFalse(c.contains("killstreak.potion-bonuses"));
        assertFalse(c.contains("killstreak.restrictions"));
        assertFalse(c.contains("killstreak.max-health-hearts"));
        assertFalse(c.contains("events.timed.surge"));
    }
    @Test void allFourteenMinorPerksHaveSafeDefaults() {
        var c = config();
        assertTrue(c.getBoolean("perks.enabled"));
        for (PerkType type : PerkType.values()) {
            String prefix = "perks." + type.id + ".";
            assertTrue(c.getBoolean(prefix + "enabled"), type.display);
            assertEquals(type.defaultMaxCopies, c.getInt(prefix + "max-copies"), type.display);
            assertEquals(type.defaultPerCopy, c.getDouble(prefix + "per-copy"), type.display);
        }
        assertEquals(0.05, c.getDouble("perks.reach.per-copy"));
        assertEquals(3, c.getInt("perks.reach.max-copies"));
        assertEquals(0.05, c.getDouble("perks.scale.per-copy"));
        assertEquals(3, c.getInt("perks.scale.max-copies"));
        assertEquals(4.0, c.getDouble("perks.capacity.per-copy"));
        assertEquals(4, c.getInt("perks.capacity.max-copies"));
        assertEquals(0.05, c.getDouble("perks.alchemy.per-copy"));
        assertEquals(4, c.getInt("perks.alchemy.max-copies"));
    }
    @Test void darkBlueIndicatorDefaults() {
        var c = config();
        assertTrue(c.getBoolean("display.streak-indicators.tab-enabled"));
        assertTrue(c.getBoolean("display.streak-indicators.nametag-enabled"));
        assertEquals("⚔", c.getString("display.streak-indicators.symbol"));
        assertEquals("&c&lFractured", c.getString("general.fractured-label"));
        String[] expected = {"&7","&a","&e","&6","&c","&5","&4"};
        for (int i=0; i<expected.length; i++)
            assertEquals(expected[i], c.getString("display.streak-indicators.tier-colors."+(i*5)));
    }
    @Test void huntRewardsAndNoRevengeByDefault() {
        var c = config();
        assertEquals(1, c.getInt("killstreak.kill-rewards.base-reward"));
        assertEquals(5, c.getInt("killstreak.kill-rewards.levels-per-tier"));
        assertEquals(1, c.getInt("killstreak.kill-rewards.extra-per-tier"));
        assertFalse(c.getBoolean("killstreak.revenge.enabled"));
        assertTrue(c.getBoolean("killstreak.anti-farm.enabled"));
        assertTrue(c.getBoolean("killstreak.anti-farm.block-rewards-for-fractured-victim"));
    }
    @Test void wantedPenaltiesStartAtIntendedMilestones() {
        var c = config();
        assertEquals(10, c.getInt("hunt.glowing-level"));
        assertEquals(15, c.getInt("hunt.coordinates-level"));
        assertEquals(15, c.getInt("hunt.coordinate-interval-minutes"));
        assertEquals(20, c.getInt("hunt.no-teams-level"));
        assertTrue(c.getString("hunt.announcements.5").contains("{player} is on fire!"));
        assertTrue(c.getString("hunt.announcements.10").contains("{player} is unstoppable!"));
        assertFalse(c.contains("killstreak.milestones.glowing-level"));
    }
    @Test void combatDefaultsPreserved() {
        var c = config();
        assertEquals(0.75, c.getDouble("combat.mace.player-damage-multiplier"));
        assertEquals(0.5, c.getDouble("combat.tnt.igniter-self-damage-multiplier"));
    }
}
