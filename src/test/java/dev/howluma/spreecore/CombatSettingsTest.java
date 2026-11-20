package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatSettingsTest {
    @Test void newBalanceSettingsStayBetweenZeroAndFullDamage() {
        String path = "combat.tnt.igniter-self-damage-multiplier";
        assertNull(ConfigEditor.validate(path, 0.5));
        assertNull(ConfigEditor.validate(path, 0.0));
        assertNull(ConfigEditor.validate(path, 1.0));
        assertNotNull(ConfigEditor.validate(path, -0.01));
        assertNotNull(ConfigEditor.validate(path, 1.01));
        assertNull(ConfigEditor.validate("combat.mace.player-damage-multiplier", 0.75));
        assertNull(ConfigEditor.validate("killstreak.health-per-level-hearts", 0.5));
        assertNotNull(ConfigEditor.validate("killstreak.health-per-level-hearts", -0.1));
        assertNull(ConfigEditor.validate("killstreak.mace-unlock-level", 1));
        assertNotNull(ConfigEditor.validate("killstreak.mace-unlock-level", 0));
    }
    @Test void victimStreakSettingsHaveSafeEditableRanges() {
        assertNull(ConfigEditor.validate("combat.mace.bonus-damage-per-victim-streak", 0.05));
        assertNull(ConfigEditor.validate("combat.tnt.bonus-damage-per-victim-streak", 0.05));
        assertNotNull(ConfigEditor.validate("combat.tnt.bonus-damage-per-victim-streak", -0.05));
        assertNotNull(ConfigEditor.validate("combat.mace.bonus-damage-per-victim-streak", 1.1));
        assertNull(ConfigEditor.validate("combat.tnt-minecart.base-cap-hearts", 7.0));
        assertNotNull(ConfigEditor.validate("combat.tnt-minecart.base-cap-hearts", -1.0));
        assertNull(ConfigEditor.validate("combat.tnt-minecart.cap-increase-per-victim-streak-hearts", 0.5));
        assertNotNull(ConfigEditor.validate("combat.tnt-minecart.cap-increase-per-victim-streak-hearts", -0.5));
    }

}
