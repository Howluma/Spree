package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PerkMathTest {
    @Test void perksLandEitherSideOfTalentWithinEveryCycle() {
        int[] expected = {2,4,7,9,12,14,17,19,22,24,27,29};
        List<Integer> actual = new ArrayList<>();
        for (int i = -1; i <= 30; i++) if (PerkMath.isPerkMilestone(i)) actual.add(i);
        assertEquals(Arrays.stream(expected).boxed().toList(), actual);
        assertFalse(PerkMath.isPerkMilestone(3));
        assertFalse(PerkMath.isPerkMilestone(5));
        assertTrue(PerkMath.isPerkMilestone(32));
    }

    @Test void duplicatesStackOnlyAtUnlockedLevels() {
        Map<Integer,String> history = Map.of(2,"power",4,"power",7,"agility",9,"power");
        assertEquals(0, PerkMath.copies(history, PerkType.POWER, 1));
        assertEquals(1, PerkMath.copies(history, PerkType.POWER, 2));
        assertEquals(2, PerkMath.copies(history, PerkType.POWER, 7));
        assertEquals(3, PerkMath.copies(history, PerkType.POWER, 9));
        assertEquals(0, PerkMath.copies(history, PerkType.AGILITY, 4));
        assertEquals(1, PerkMath.copies(history, PerkType.AGILITY, 7));
        assertEquals(0, PerkMath.copies(history, PerkType.POWER, -1));
        assertEquals(3, PerkMath.copies(history, PerkType.POWER, 100));
    }

    @Test void scaleCanRollMultipleTimesAndRespectsCap() {
        Map<Integer,String> history = new HashMap<>();
        history.put(2, "scale");
        history.put(4, "scale");
        assertEquals(1, PerkMath.copies(history, PerkType.SCALE, 2));
        assertEquals(2, PerkMath.copies(history, PerkType.SCALE, 4));
        assertEquals(PerkType.SCALE,
                PerkMath.draw(history, List.of(PerkType.SCALE), Map.of(PerkType.SCALE, 3), new Random(2)));
        history.put(7, "scale");
        assertEquals(3, PerkMath.copies(history, PerkType.SCALE, 7));
        assertNull(PerkMath.draw(history, List.of(PerkType.SCALE), Map.of(PerkType.SCALE, 3), new Random(2)));
        assertEquals(0, PerkMath.copies(history, PerkType.SCALE, -1));
        assertEquals(2, PerkMath.copies(history, PerkType.SCALE, 6));
        assertEquals(0.85, 1.0 - PerkType.SCALE.defaultPerCopy * PerkType.SCALE.defaultMaxCopies, 1e-10);
    }
    @Test void cappingATypeRemovesItFromFutureRandomOffers() {
        Map<Integer,String> history = Map.of(2,"power",4,"power",7,"power",9,"power");
        Map<PerkType,Integer> caps = Map.of(PerkType.POWER,4,PerkType.AGILITY,3);
        for (int i=0;i<100;i++)
            assertEquals(PerkType.AGILITY, PerkMath.draw(history,
                    List.of(PerkType.POWER,PerkType.AGILITY),caps,new Random(i)));
        Map<Integer,String> allCapped = new HashMap<>(history);
        allCapped.put(12, "agility");
        allCapped.put(14, "agility");
        allCapped.put(17, "agility");
        assertNull(PerkMath.draw(allCapped,List.of(PerkType.POWER,PerkType.AGILITY),caps,new Random(9)));
    }

    @Test void randomSelectionCanRollDuplicatePerks() {
        Map<Integer,String> history = Map.of(2,"power");
        Map<PerkType,Integer> caps = Map.of(PerkType.POWER,4);
        assertEquals(PerkType.POWER,
                PerkMath.draw(history,List.of(PerkType.POWER),caps,new Random(0)));
    }

    @Test void fractionalRemaindersAccumulateWithoutWholeNumberRoundingLoss() {
        assertEquals(0.15, PerkMath.fractionalPart(1.15), 1e-8);
        assertEquals(0.50, PerkMath.fractionalPart(2.50), 1e-8);
        assertEquals(0.0, PerkMath.fractionalPart(-1), 1e-8);
        assertEquals(0.0, PerkMath.fractionalPart(Double.NaN), 1e-8);
    }
}
