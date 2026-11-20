package dev.howluma.spreecore;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecialistItemsTest {
    @Test void diamondWeaponsAndToolsHaveReversibleNetheriteCounterparts() {
        Material[][] pairs = {
                {Material.DIAMOND_SWORD, Material.NETHERITE_SWORD},
                {Material.DIAMOND_AXE, Material.NETHERITE_AXE},
                {Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE},
                {Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL},
                {Material.DIAMOND_HOE, Material.NETHERITE_HOE},
                {Material.DIAMOND_SPEAR, Material.NETHERITE_SPEAR}
        };
        for (Material[] pair : pairs) {
            assertEquals(pair[1], SpecialistItems.promoted(pair[0]));
            assertEquals(pair[0], SpecialistItems.original(pair[1]));
        }
        assertNull(SpecialistItems.promoted(Material.DIAMOND_CHESTPLATE),
                "Armor must not be upgraded by a tools-and-weapons privilege");
        assertNull(SpecialistItems.original(Material.DIAMOND_SWORD),
                "An ordinary diamond sword must never be demoted");
    }

    @Test void potionInfusionsHaveActualPotionIcons() {
        assertEquals(6, ProgressionCatalog.INFUSIONS.size());
        for (var infusion : ProgressionCatalog.INFUSIONS)
            assertEquals(Material.POTION, infusion.icon(), infusion.name());
        assertEquals(Material.POTION, ProgressionCatalog.find(
                ProgressionCatalog.Kind.PRIVILEGE, "turtle_master").icon());
    }
}
