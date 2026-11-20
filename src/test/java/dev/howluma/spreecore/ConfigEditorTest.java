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
        assertNotNull(ConfigEditor.validate("general.max-killstreak", 30));
        assertNull(ConfigEditor.validate("general.max-killstreak", 31));
        assertNull(ConfigEditor.validate("general.max-killstreak", 1001));
        assertNull(ConfigEditor.validate("general.max-killstreak", 1_000_000_000));
        assertNotNull(ConfigEditor.validate("general.max-killstreak", 1_000_000_001L));
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
    @Test void huntRewardSettingsCannotUseZeroIntervalsOrNegativeRewards() {
        assertNull(ConfigEditor.validate("killstreak.kill-rewards.levels-per-tier", 5));
        assertNotNull(ConfigEditor.validate("killstreak.kill-rewards.levels-per-tier", 0));
        assertNotNull(ConfigEditor.validate("killstreak.kill-rewards.levels-per-tier", -5));
        assertNotNull(ConfigEditor.validate("killstreak.kill-rewards.base-reward", 0));
        assertNull(ConfigEditor.validate("killstreak.kill-rewards.base-reward", 1));
        assertNotNull(ConfigEditor.validate("killstreak.kill-rewards.extra-per-tier", -1));
        assertNull(ConfigEditor.validate("killstreak.kill-rewards.extra-per-tier", 1));
    }
    @Test void recipeMaterialAndCountEditorsRejectInvalidInputs() {
        // Material.matchMaterial needs a running Paper registry; ingredient
        // validation is checked on a live staging server, not in pure JVM tests.
        assertNull(ConfigEditor.validate("fate-sigil.recipe.slot-5", "SPREE_ITEM"));
        assertNull(ConfigEditor.validate("fate-sigil.recipe.slot-5", "spree_item"));
        assertNull(ConfigEditor.validate("fate-sigil.recipe.result-amount",16));
        assertNotNull(ConfigEditor.validate("fate-sigil.recipe.result-amount",0));
        assertNotNull(ConfigEditor.validate("fate-sigil.recipe.result-amount",17));
        assertNull(ConfigEditor.validate("talents.scavenger.chance",.15));
        assertNotNull(ConfigEditor.validate("talents.scavenger.chance",1.25));
        assertNotNull(ConfigEditor.validate("talents.combo-breaker.hits-required",0));
        assertNull(ConfigEditor.validate("talents.combo-breaker.hits-required",3));
    }
    @Test void rodCollisionMarginsCanBeEditedSafelyInSpreeCoreSettings() {
        assertNull(ConfigEditor.validate("rod-assist.player-extra-margin",.30));
        assertNull(ConfigEditor.validate("rod-assist.item-extra-margin",.35));
        assertNull(ConfigEditor.validate("rod-assist.player-extra-margin",0));
        assertNull(ConfigEditor.validate("rod-assist.item-extra-margin",.50));
        assertNotNull(ConfigEditor.validate("rod-assist.player-extra-margin",.51));
        assertNotNull(ConfigEditor.validate("rod-assist.item-extra-margin",-.01));
        assertNotNull(ConfigEditor.validate("rod-assist.max-sweep-blocks-per-tick",0.25));
        assertNull(ConfigEditor.validate("rod-assist.max-sweep-blocks-per-tick",4.0));
        assertNotNull(ConfigEditor.validate("rod-assist.max-sweep-blocks-per-tick",5.01));
        assertNull(ConfigEditor.validate("talents.flame-rod.contact-tolerance",.50));
        assertNull(ConfigEditor.validate("talents.flame-rod.contact-tolerance",.70));
        assertNotNull(ConfigEditor.validate("talents.flame-rod.contact-tolerance",.71));
        assertNotNull(ConfigEditor.validate("talents.flame-rod.contact-tolerance",0));
    }

    @Test void tierColorEditorRejectsInvalidOrMultiColorValues() {
        assertNull(ConfigEditor.validate("display.streak-indicators.tier-colors.5", "&a"));
        assertNull(ConfigEditor.validate("display.streak-indicators.tier-colors.10", "<#123ABC>"));
        assertNotNull(ConfigEditor.validate("display.streak-indicators.tier-colors.5", "&a&c"));
        assertNotNull(ConfigEditor.validate("display.streak-indicators.tier-colors.5", "&x"));
    }
}
