package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FateSigilReselectionTest {
    private static SortedMap<Integer,String> allSix(ProgressionCatalog.Kind kind) {
        List<Integer> levels=ProgressionCatalog.milestones(kind,SpreeMath.BENEFIT_CAP);
        List<ProgressionCatalog.Option> options=ProgressionCatalog.options(kind);
        assertEquals(6,levels.size());
        assertEquals(6,options.size());
        SortedMap<Integer,String> choices=new TreeMap<>();
        for(int i=0;i<6;i++) choices.put(levels.get(i),options.get(i).id());
        return choices;
    }

    @Test void fullPrivilegesStillHaveFiveRerollAlternatives() {
        var choices=allSix(ProgressionCatalog.Kind.PRIVILEGE);
        int level=1;
        List<ProgressionCatalog.Option> alternatives=ProgressionManager.optionsFor(
                ProgressionCatalog.Kind.PRIVILEGE,choices,level,true);
        assertEquals(5,alternatives.size());
        assertTrue(alternatives.stream().anyMatch(o->o.id().equals(choices.get(6))));
        assertFalse(alternatives.stream().anyMatch(o->o.id().equals(choices.get(1))));
        // A first-time claim must never create a second copy of another unlock.
        assertTrue(ProgressionManager.optionsFor(
                ProgressionCatalog.Kind.PRIVILEGE,choices,level,false).isEmpty());
    }

    @Test void allSixInfusionsCanSwapWithoutLosingOrDuplicatingAnything() {
        var choices=allSix(ProgressionCatalog.Kind.INFUSION);
        Map<Integer,String> before=new TreeMap<>(choices);
        var change=ProgressionManager.planReselection(choices,5,choices.get(30));
        assertNotNull(change);
        assertEquals(30,change.swapLevel());
        ProgressionManager.applyReselection(choices,5,before.get(30),change);
        assertEquals(before.get(30),choices.get(5));
        assertEquals(before.get(5),choices.get(30));
        assertEquals(6,choices.size());
        assertEquals(new HashSet<>(before.values()),new HashSet<>(choices.values()));
        assertEquals(6,new HashSet<>(choices.values()).size());
        assertEquals(5,ProgressionManager.optionsFor(
                ProgressionCatalog.Kind.INFUSION,choices,5,true).size());
    }

    @Test void previouslyEarnedMilestonesStaySelectableAfterStreakLoss() {
        assertTrue(ProgressionManager.historicallyEarned(
                ProgressionCatalog.Kind.INFUSION,30,0,30));
        assertTrue(ProgressionManager.historicallyEarned(
                ProgressionCatalog.Kind.PRIVILEGE,26,-1,30));
        assertTrue(ProgressionManager.historicallyEarned(
                ProgressionCatalog.Kind.TALENT,28,2,30));
        assertFalse(ProgressionManager.historicallyEarned(
                ProgressionCatalog.Kind.TALENT,28,2,27));
        assertFalse(ProgressionManager.historicallyEarned(
                ProgressionCatalog.Kind.INFUSION,30,10,25));
        assertFalse(ProgressionManager.historicallyEarned(
                ProgressionCatalog.Kind.INFUSION,35,40,40));
        assertFalse(ProgressionManager.historicallyEarned(
                ProgressionCatalog.Kind.PERK,31,40,40));
    }

    @Test void reselectUnusedOptionReplacesRatherThanMovingAnotherMilestone() {
        Map<Integer,String> choices=new TreeMap<>();
        choices.put(5,"speed");
        choices.put(10,"strength");
        var plan=ProgressionManager.planReselection(choices,5,"regeneration");
        assertNotNull(plan);
        assertEquals(-1,plan.swapLevel());
        ProgressionManager.applyReselection(choices,5,"regeneration",plan);
        assertEquals("regeneration",choices.get(5));
        assertEquals("strength",choices.get(10));
        assertEquals(2,choices.size());
    }

    @Test void unchangedOrUnclaimedReselectionsAreRejectedBeforeAnyMutation() {
        Map<Integer,String> choices=new TreeMap<>();
        choices.put(1,"mace_wielder");
        Map<Integer,String> before=new TreeMap<>(choices);
        assertNull(ProgressionManager.planReselection(choices,1,"mace_wielder"));
        assertNull(ProgressionManager.planReselection(choices,6,"netherite_specialist"));
        assertNull(ProgressionManager.planReselection(choices,1,null));
        assertEquals(before,choices);
    }

    @Test void normalTalentOffersStillExcludeChoicesOwnedAtOtherMilestones() {
        Map<Integer,String> choices=new TreeMap<>();
        choices.put(3,"mace_mastery");
        choices.put(8,"executioner");
        List<ProgressionCatalog.Option> remaining=ProgressionManager.optionsFor(
                ProgressionCatalog.Kind.TALENT,choices,13,false);
        assertFalse(remaining.stream().anyMatch(o->o.id().equals("mace_mastery")));
        assertFalse(remaining.stream().anyMatch(o->o.id().equals("executioner")));
        assertEquals(ProgressionCatalog.TALENTS.size()-2,remaining.size());
    }
}
