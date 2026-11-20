package dev.howluma.spreecore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PerkDescriptionAndSigilRecipeTest {
    @Test void everyPerkExplainsItsActualEffect() {
        Set<String> effects=new HashSet<>();
        for (PerkType perk : PerkType.values()) {
            assertNotNull(perk.effectDescription(), perk.id);
            assertTrue(perk.effectDescription().length() > 10, perk.id);
            assertTrue(effects.add(perk.effectDescription()), "Duplicate description: " + perk.id);
        }
        assertTrue(PerkType.POWER.effectNote().contains("TNT"));
        assertTrue(PerkType.ALCHEMY.effectNote().contains("Infusions"));
        assertTrue(PerkType.REACH.effectNote().contains("not block reach"));
        assertTrue(PerkType.CAPACITY.effectNote().contains("stackable"));
    }

    @Test void craftingPatternMatchesUploadedNineSlotImage() {
        List<String> expected=List.of(
                "OMINOUS_TRIAL_KEY","NETHERITE_INGOT","OMINOUS_TRIAL_KEY",
                "NETHERITE_INGOT","SPREE_ITEM","NETHERITE_INGOT",
                "OMINOUS_TRIAL_KEY","NETHERITE_INGOT","OMINOUS_TRIAL_KEY");
        assertEquals(expected,FateSigil.DEFAULT_RECIPE);
        assertEquals(4,expected.stream().filter("OMINOUS_TRIAL_KEY"::equals).count());
        assertEquals(4,expected.stream().filter("NETHERITE_INGOT"::equals).count());
        assertEquals(1,expected.stream().filter("SPREE_ITEM"::equals).count());
        assertEquals(0,expected.stream().filter("HEART_OF_THE_SEA"::equals).count());
    }

    @Test void exactLegacyRecipeMigratesOnlyOnce() {
        YamlConfiguration config=new YamlConfiguration();
        for(int i=0;i<9;i++)
            config.set("fate-sigil.recipe.slot-"+(i+1), FateSigil.LEGACY_RECIPE.get(i));
        assertTrue(FateSigil.migrateOldRecipe(config));
        for(int i=0;i<9;i++)
            assertEquals(FateSigil.DEFAULT_RECIPE.get(i),
                    config.getString("fate-sigil.recipe.slot-"+(i+1)));
        assertFalse(FateSigil.migrateOldRecipe(config));
    }

    @Test void version175RecipeMigratesToSpreeItemOnlyAtCenter() {
        YamlConfiguration config=new YamlConfiguration();
        for(int i=0;i<9;i++)
            config.set("fate-sigil.recipe.slot-"+(i+1), FateSigil.PREVIOUS_RECIPE.get(i));
        assertEquals("HEART_OF_THE_SEA",config.getString("fate-sigil.recipe.slot-5"));
        assertTrue(FateSigil.migrateOldRecipe(config));
        for(int i=0;i<9;i++)
            assertEquals(FateSigil.DEFAULT_RECIPE.get(i),
                    config.getString("fate-sigil.recipe.slot-"+(i+1)));
        assertEquals("SPREE_ITEM",config.getString("fate-sigil.recipe.slot-5"));
        assertFalse(FateSigil.migrateOldRecipe(config));
    }

    @Test void existingAdminCustomizedRecipeIsNeverOverwritten() {
        YamlConfiguration config=new YamlConfiguration();
        for(int i=0;i<9;i++)
            config.set("fate-sigil.recipe.slot-"+(i+1), FateSigil.LEGACY_RECIPE.get(i));
        config.set("fate-sigil.recipe.slot-1", "TOTEM_OF_UNDYING");
        assertFalse(FateSigil.migrateOldRecipe(config));
        assertEquals("TOTEM_OF_UNDYING",config.getString("fate-sigil.recipe.slot-1"));
        assertEquals("NETHERITE_SCRAP",config.getString("fate-sigil.recipe.slot-5"));
    }
}
