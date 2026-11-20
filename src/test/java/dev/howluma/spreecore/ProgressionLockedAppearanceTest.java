package dev.howluma.spreecore;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Previously earned rewards must look inactive whenever their level is lost. */
class ProgressionLockedAppearanceTest {
    @Test void savedPrivilegeIsNotDisplayedAsClaimedWhenStreakFalls() {
        String status=ProgressionMenu.displayedMilestoneStatus(true,true,0,26,false);
        assertTrue(status.contains("INACTIVE"));
        assertTrue(status.contains("26"));
        assertFalse(status.contains("CLAIMED"));
        assertEquals(Material.GRAY_DYE,
                ProgressionMenu.milestoneIcon(Material.NETHERITE_SWORD,true,0,26));
    }

    @Test void savedInfusionRetainsLockedAppearanceInFateSigilMenu() {
        String status=ProgressionMenu.displayedMilestoneStatus(true,true,-1,30,true);
        assertTrue(status.contains("INACTIVE"));
        assertFalse(status.contains("CHANGE ACTIVE"));
        assertEquals(Material.GRAY_DYE,
                ProgressionMenu.milestoneIcon(Material.POTION,true,-1,30));
    }

    @Test void earnedUnclaimedMilestoneIsNotShownActiveUntilStreakRecovers() {
        assertTrue(ProgressionMenu.displayedMilestoneStatus(true,false,3,18,false)
                .contains("INACTIVE"));
        assertEquals(Material.GRAY_DYE,
                ProgressionMenu.milestoneIcon(null,false,3,18));
        assertTrue(ProgressionMenu.displayedMilestoneStatus(true,false,18,18,false)
                .contains("READY"));
        assertEquals(Material.LIME_DYE,
                ProgressionMenu.milestoneIcon(null,false,18,18));
    }

    @Test void reearnedPrivilegeBecomesActiveAgainWithoutAnotherUnlock() {
        assertEquals("&a&lACTIVE",
                ProgressionMenu.displayedMilestoneStatus(true,true,26,26,false));
        assertEquals(Material.MACE,
                ProgressionMenu.milestoneIcon(Material.MACE,true,26,26));
        assertTrue(ProgressionMenu.displayedMilestoneStatus(true,true,26,26,true)
                .contains("CHANGE ACTIVE"));
    }

    @Test void unknownFutureMilestonesRemainLocked() {
        assertEquals("&8&lLOCKED",
                ProgressionMenu.displayedMilestoneStatus(false,false,5,20,false));
    }
}
