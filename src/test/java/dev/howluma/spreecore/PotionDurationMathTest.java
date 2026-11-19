package dev.howluma.spreecore;

import org.bukkit.event.entity.EntityPotionEffectEvent.Cause;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PotionDurationMathTest {
    @Test void percentagesAreAdditiveAndWorkWithShortPotions() {
        assertEquals(60, PotionDurationMath.extraTicks(1200, .05)); // +3 sec to one-minute potion
        assertEquals(120, PotionDurationMath.extraTicks(1200, .10));
        assertEquals(240, PotionDurationMath.extraTicks(1200, .20));
        assertEquals(180, PotionDurationMath.extraTicks(3600, .05));
        assertEquals(0, PotionDurationMath.extraTicks(3600, 0));
        assertEquals(0, PotionDurationMath.extraTicks(0, .05));
    }
    @Test void onlyConsumablesAndPotionDeliveryCount() {
        for (var cause : new Cause[]{Cause.POTION_DRINK, Cause.POTION_SPLASH,
                Cause.AREA_EFFECT_CLOUD, Cause.ARROW, Cause.FOOD})
            assertTrue(PotionDurationMath.isPotionSource(cause), cause.toString());
        for (var cause : new Cause[]{Cause.PLUGIN, Cause.COMMAND, Cause.BEACON,
                Cause.CONDUIT, Cause.EXPIRATION, Cause.TOTEM, Cause.MILK})
            assertFalse(PotionDurationMath.isPotionSource(cause), cause.toString());
    }
    @Test void corruptedValuesCannotProduceNegativeOrOverflowedDurations() {
        assertEquals(0, PotionDurationMath.extraTicks(1200, Double.NaN));
        assertEquals(0, PotionDurationMath.extraTicks(1200, Double.POSITIVE_INFINITY));
        assertEquals(0, PotionDurationMath.extraTicks(1200, -1));
        assertEquals(0, PotionDurationMath.extraTicks(Integer.MAX_VALUE-1, .05));
    }
}
