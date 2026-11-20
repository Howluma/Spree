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
    @Test void duplicateDeathTransactionsAreGuardedAndDiagnosable() {
        var c=config();
        assertTrue(c.getBoolean("killstreak.death-processing.warn-duplicate-events"));
        assertFalse(c.getBoolean("killstreak.death-processing.log-transitions"));
    }

    @Test void healthAndSelectableProgressionReplaceFixedMilestones() {
        var c = config();
        assertEquals(1_000_000_000, c.getInt("general.max-killstreak"));
        assertEquals(30, SpreeMath.BENEFIT_CAP);
        assertEquals(10, c.getInt("killstreak.base-health-hearts"));
        assertEquals(0.5, c.getDouble("killstreak.health-per-level-hearts"));
        assertFalse(c.contains("killstreak.mace-unlock-level"));
        assertTrue(c.getBoolean("killstreak.withdrawal.enabled"));
        assertFalse(c.contains("killstreak.milestones"));
        assertEquals(1, c.getInt("privileges.max-totems"));
        assertEquals(4, c.getInt("privileges.max-enchanted-apples"));
        assertEquals(64, c.getInt("privileges.max-wind-charges"));
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
    @Test void talentBalanceIsConfigurable() {
        var c=config();
        assertEquals(3,c.getInt("talents.combo-breaker.hits-required"));
        assertEquals(.10,c.getDouble("talents.executioner.bonus-damage-fraction"));
        assertEquals(45,c.getInt("talents.riposte.cooldown-seconds"));
        assertEquals(5.0,c.getDouble("talents.field-medic.heal-hearts"));
        assertEquals(64,c.getInt("talents.expanded-reserves.extra-items"));
    }
    @Test void fateSigilCanBeEditedWithoutLosingItsName() {
        var c=config();
        assertEquals("&9&lFate Sigil",c.getString("fate-sigil.display-name"));
        assertTrue(c.getBoolean("fate-sigil.recipe.enabled"));
        assertEquals(1,c.getInt("fate-sigil.recipe.result-amount"));
        assertEquals("SPREE_ITEM",c.getString("fate-sigil.recipe.slot-5"));
        String[] expected={"OMINOUS_TRIAL_KEY","NETHERITE_INGOT","OMINOUS_TRIAL_KEY",
                "NETHERITE_INGOT","SPREE_ITEM","NETHERITE_INGOT",
                "OMINOUS_TRIAL_KEY","NETHERITE_INGOT","OMINOUS_TRIAL_KEY"};
        for (int i=0;i<expected.length;i++)
            assertEquals(expected[i],c.getString("fate-sigil.recipe.slot-"+(i+1)));
        assertEquals(721051,c.getInt("resource-pack.models.fate-sigil"));
    }
    @Test void rodHitboxAssistIsBuiltIntoSpreeCoreWithSmallDefaults() {
        var c=config();
        assertTrue(c.getBoolean("rod-assist.enabled"));
        assertEquals(.30,c.getDouble("rod-assist.player-extra-margin"),1e-9);
        assertEquals(.35,c.getDouble("rod-assist.item-extra-margin"),1e-9);
        assertEquals(.40,c.getDouble("rod-assist.primed-tnt-extra-margin"),1e-9);
        assertEquals(4.0,c.getDouble("rod-assist.max-sweep-blocks-per-tick"),1e-9);
        assertTrue(c.getBoolean("rod-assist.respect-pvp-settings"));
        assertTrue(c.getBoolean("rod-assist.respect-friendly-fire"));
    }

    @Test void personalKillAndDeathEffectsAreEnabledAndConfigurable() {
        var c=config();
        assertTrue(c.getBoolean("combat-feedback.enabled"));
        assertTrue(c.getBoolean("combat-feedback.kill.enabled"));
        assertTrue(c.getBoolean("combat-feedback.kill.sounds"));
        assertTrue(c.getBoolean("combat-feedback.kill.particles"));
        assertEquals(.8,c.getDouble("combat-feedback.kill.volume"),1e-9);
        assertEquals(16,c.getInt("combat-feedback.kill.ring-points"));
        assertFalse(c.getBoolean("combat-feedback.kill.only-rewarded"));
        assertTrue(c.getBoolean("combat-feedback.death.enabled"));
        assertTrue(c.getBoolean("combat-feedback.death.sounds"));
        assertEquals(.75,c.getDouble("combat-feedback.death.volume"),1e-9);
    }

    @Test void quickHandsRodTalentAndEnvironmentalCreditDefaults() {
        var config=config();
        assertEquals(.90,config.getDouble("talents.quick-hands.consume-time-multiplier"),1e-9);
        assertTrue(config.getBoolean("talents.flame-rod.enabled"));
        assertTrue(config.getBoolean("talents.flame-rod.bobber-particles"));
        assertTrue(config.getBoolean("talents.flame-rod.cast-sound"));
        assertEquals(.50,config.getDouble("talents.flame-rod.contact-tolerance"),1e-9);
        assertTrue(config.getBoolean("talents.flame-rod.ignite-players"));
        assertEquals(4.0,config.getDouble("talents.flame-rod.player-fire-seconds"),1e-9);
        assertTrue(config.getBoolean("talents.flame-rod.player-fire-particles"));
        assertTrue(config.getBoolean("talents.flame-rod.player-fire-sound"));
        assertEquals(1,config.getInt("talents.flame-rod.max-ignitions-per-cast"));
        assertEquals(80,config.getInt("talents.flame-rod.tnt-fuse-ticks"));
        assertFalse(config.contains("talents.barbed-hook"));
        assertTrue(config.getBoolean("combat.pvp-environment-credit.enabled"));
        assertEquals(12,config.getInt("combat.pvp-environment-credit.window-seconds"));
    }

    @Test void maceSmashCooldownDefaults() {
        var c = config();
        assertEquals(60, c.getInt("combat.mace.slam-cooldown-seconds"));
        assertEquals(30, c.getInt("combat.mace.mastery-slam-cooldown-seconds"));
        assertTrue(c.getBoolean("combat.mace.cooldown-overlay"));
        assertTrue(c.getBoolean("combat.mace.impact-particles"));
        assertTrue(c.getBoolean("combat.mace.impact-sound"));
    }
    @Test void combatDefaultsPreserved() {
        var c = config();
        assertEquals(0.75, c.getDouble("combat.mace.player-damage-multiplier"));
        assertEquals(0.5, c.getDouble("combat.tnt.igniter-self-damage-multiplier"));
    }
    @Test void minecartDefaultsUsePostArmorHeartsAndMaceBonusIsAdditive() {
        var config = config();
        assertEquals(0.75, config.getDouble("combat.mace.player-damage-multiplier"), 1e-9);
        assertEquals(0.05, config.getDouble("combat.mace.bonus-damage-per-victim-streak"), 1e-9);
        assertEquals(0.05, config.getDouble("combat.tnt.bonus-damage-per-victim-streak"), 1e-9);
        assertEquals(0.5, config.getDouble("combat.tnt.igniter-self-damage-multiplier"), 1e-9);
        assertTrue(config.getBoolean("combat.tnt-minecart.enabled"));
        assertEquals(7.0, config.getDouble("combat.tnt-minecart.base-cap-hearts"), 1e-9);
        assertEquals(0.5, config.getDouble("combat.tnt-minecart.cap-increase-per-victim-streak-hearts"), 1e-9);
    }

}
