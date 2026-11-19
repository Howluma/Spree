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
    }
}
