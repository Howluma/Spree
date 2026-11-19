package dev.howluma.spreecore;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionCatalogTest {
    @Test void twelvePerkSixPrivilegeSixTalentSixInfusionMilestones() {
        assertEquals(List.of(1,6,11,16,21,26),
                ProgressionCatalog.milestones(ProgressionCatalog.Kind.PRIVILEGE,30));
        assertEquals(List.of(2,4,7,9,12,14,17,19,22,24,27,29),
                ProgressionCatalog.milestones(ProgressionCatalog.Kind.PERK,30));
        assertEquals(List.of(3,8,13,18,23,28),
                ProgressionCatalog.milestones(ProgressionCatalog.Kind.TALENT,30));
        assertEquals(List.of(5,10,15,20,25,30),
                ProgressionCatalog.milestones(ProgressionCatalog.Kind.INFUSION,30));
    }
    @Test void noDuplicateOptionIdsAcrossEachChoicePool() {
        for (var kind : new ProgressionCatalog.Kind[]{ProgressionCatalog.Kind.PRIVILEGE,
                ProgressionCatalog.Kind.INFUSION,ProgressionCatalog.Kind.TALENT}) {
            var options=ProgressionCatalog.options(kind);
            assertEquals(options.size(),options.stream().map(ProgressionCatalog.Option::id).distinct().count());
            for(var option:options) {
                assertNotNull(option.icon());
                assertNotNull(ProgressionCatalog.find(kind,option.id()));
            }
        }
    }
    @Test void fullPoolsContainTheApprovedChoices() {
        assertEquals(6,ProgressionCatalog.PRIVILEGES.size());
        assertEquals(6,ProgressionCatalog.INFUSIONS.size());
        assertEquals(22,ProgressionCatalog.TALENTS.size());
        assertNotNull(ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,"blacksmith"));
        assertNotNull(ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,"mace_mastery"));
        assertNotNull(ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,"industrious"));
        assertNotNull(ProgressionCatalog.find(ProgressionCatalog.Kind.PRIVILEGE,"mace_wielder"));
    }
}
