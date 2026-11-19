package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigEditorTest {
    @Test void restrictsChancesToRealProbabilities() {
        assertNull(ConfigEditor.validate("legendary.weapons.tundra.passive-chance", 0.0));
        assertNull(ConfigEditor.validate("legendary.weapons.tundra.passive-chance", 1.0));
        assertNotNull(ConfigEditor.validate("legendary.weapons.tundra.passive-chance", -0.1));
        assertNotNull(ConfigEditor.validate("legendary.weapons.tundra.passive-chance", 1.01));
    }
    @Test void protectsModelSelectorsAndServerLimits() {
        assertNull(ConfigEditor.validate("resource-pack.models.tundra", 721001));
        assertNotNull(ConfigEditor.validate("resource-pack.models.tundra", 20000000));
        assertNotNull(ConfigEditor.validate("general.max-killstreak", -1));
        assertNull(ConfigEditor.validate("general.max-killstreak", 30));
        assertNotNull(ConfigEditor.validate("general.max-killstreak", 1001));
    }
    @Test void validatesCooldownsAndHealth() {
        assertNull(ConfigEditor.validate("legendary.weapons.hailstorm.cooldown-seconds", 30));
        assertNotNull(ConfigEditor.validate("legendary.weapons.hailstorm.cooldown-seconds", -4));
        assertNotNull(ConfigEditor.validate("killstreak.max-health-hearts", 201));
        assertNotNull(ConfigEditor.validate("events.timed.chaos.cooldown-multiplier", Double.NaN));
    }
    @Test void stackablePerksEnforceSmallBonusesAndReasonableCaps() {
        assertNull(ConfigEditor.validate("perks.power.per-copy", 0.02));
        assertNull(ConfigEditor.validate("perks.reach.per-copy", 0.05));
        assertNotNull(ConfigEditor.validate("perks.power.per-copy", 0.12));
        assertNotNull(ConfigEditor.validate("perks.reach.per-copy", -0.01));
        assertNotNull(ConfigEditor.validate("perks.power.max-copies", 0));
        assertNotNull(ConfigEditor.validate("perks.power.max-copies", 9));
        assertNull(ConfigEditor.validate("perks.agility.max-copies", 3));
    }
    @Test void tierColorEditorRejectsInvalidOrMultiColorValues() {
        assertNull(ConfigEditor.validate("display.streak-indicators.tier-colors.5", "&a"));
        assertNull(ConfigEditor.validate("display.streak-indicators.tier-colors.10", "<#123ABC>"));
        assertNotNull(ConfigEditor.validate("display.streak-indicators.tier-colors.5", "&a&c"));
        assertNotNull(ConfigEditor.validate("display.streak-indicators.tier-colors.5", "&x"));
    }
}
