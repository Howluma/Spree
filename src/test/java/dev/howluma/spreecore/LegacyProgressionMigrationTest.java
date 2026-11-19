package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for the 1.8.2 legacy progression migration. */
class LegacyProgressionMigrationTest {
    @Test void strengthInfusionMigratesIntoHasteWithoutChangingOtherRewards() {
        assertEquals("haste", PlayerDataStore.upgradeChoice(
                ProgressionCatalog.Kind.INFUSION, "strength"));
        assertEquals("speed", PlayerDataStore.upgradeChoice(
                ProgressionCatalog.Kind.INFUSION, "speed"));
        assertEquals("mace_mastery", PlayerDataStore.upgradeChoice(
                ProgressionCatalog.Kind.TALENT, "mace_mastery"));
        assertNull(PlayerDataStore.upgradeChoice(
                ProgressionCatalog.Kind.TALENT, "industrious"));
        // Do not rewrite unrelated IDs, even if they share a legacy word.
        assertEquals("strength", PlayerDataStore.upgradeChoice(
                ProgressionCatalog.Kind.PRIVILEGE, "strength"));
    }

    @Test void talentPoolNoLongerContainsIndustriousAndHasteIsAnInfusion() {
        assertEquals(21,ProgressionCatalog.TALENTS.size());
        assertNull(ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,"industrious"));
        assertEquals("Permanent Haste I",ProgressionCatalog.find(
                ProgressionCatalog.Kind.INFUSION, "haste").description());
        assertNull(ProgressionCatalog.find(ProgressionCatalog.Kind.INFUSION,"strength"));
        assertEquals(6,ProgressionCatalog.INFUSIONS.size());
        assertEquals(6,ProgressionCatalog.milestones(
                ProgressionCatalog.Kind.INFUSION,30).size());
    }

    @Test void retiredOfferKeepsOtherTwoChoicesAndReceivesSingleValidReplacement() {
        var old=List.of("executioner","industrious","mace_mastery");
        var migrated=PlayerDataStore.migrateRetiredTalentOffers(
                old, Set.of("second_wind","bloodlust"));
        assertEquals(3,migrated.size());
        assertEquals(3,migrated.stream().distinct().count());
        assertEquals("executioner",migrated.get(0));
        assertEquals("mace_mastery",migrated.get(1));
        assertFalse(migrated.contains("industrious"));
        assertFalse(migrated.contains("second_wind"));
        assertFalse(migrated.contains("bloodlust"));
        for(String id:migrated)
            assertNotNull(ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,id));
    }

    @Test void validExistingOffersAreNotRerolledForFree() {
        List<String> existing=List.of("executioner","second_wind","mace_mastery");
        assertEquals(existing,PlayerDataStore.migrateRetiredTalentOffers(
                existing,Set.of("mace_mastery")));
    }

    @Test void onlyRetiredOptionReceivesRepairNotArbitraryInvalidOffer() {
        assertEquals(List.of("executioner","mace_mastery"),
                PlayerDataStore.migrateRetiredTalentOffers(
                        List.of("executioner","not_a_real_talent","mace_mastery"),
                        Set.of("second_wind")));
    }
}
