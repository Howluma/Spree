package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminUpgradeMenuTest {
    @Test void adminCanAssignAnyTalentOrPerkButOnlyAtTheirMilestones() {
        assertTrue(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.TALENT, 3, 30, "mace_mastery"));
        assertTrue(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.TALENT, 28, 30, "blacksmith"));
        assertTrue(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.PERK, 2, 30, "power"));
        assertTrue(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.PERK, 29, 30, "alchemy"));

        assertFalse(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.TALENT, 2, 30, "mace_mastery"));
        assertFalse(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.PERK, 3, 30, "power"));
        assertFalse(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.TALENT, 33, 30, "mace_mastery"));
        assertFalse(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.PERK, -1, 30, "power"));
        assertFalse(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.TALENT, 3, 30, "not_a_talent"));
        assertFalse(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.PERK, 2, 30, "not_a_perk"));
        assertFalse(ProgressionManager.validAdminChoice(ProgressionCatalog.Kind.PRIVILEGE, 1, 30, "mace_wielder"));
    }

    @Test void testMenuSlotsAreCenteredAndUnique() {
        assertArrayEquals(new int[]{11,13,15,29,31,33}, AdminUpgradeMenu.centeredSlots(6));
        for (int count = 0; count <= 28; count++) {
            int[] cells=AdminUpgradeMenu.centeredSlots(count);
            assertEquals(count,cells.length);
            Set<Integer> distinct=new HashSet<>();
            for (int slot : cells) {
                assertTrue(slot>=10 && slot<=43 && slot%9>=1 && slot%9<=7);
                assertTrue(distinct.add(slot), "Duplicate GUI slot at count="+count);
            }
        }
    }

    @Test void perkPickerDoesNotReuseIcons() {
        assertEquals(PerkType.values().length,
                Arrays.stream(PerkType.values()).map(PerkType::icon).distinct().count());
    }
}
