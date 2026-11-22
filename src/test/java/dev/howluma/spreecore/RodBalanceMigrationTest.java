package dev.howluma.spreecore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RodBalanceMigrationTest {
    @Test void upgradesAllThreeOldStockMarginsOnExistingServer() {
        YamlConfiguration config=new YamlConfiguration();
        config.set("rod-assist.player-extra-margin",.20);
        config.set("rod-assist.item-extra-margin",.25);
        config.set("talents.flame-rod.contact-tolerance",.35);
        assertTrue(RodBalanceMigration.migrate(config));
        assertEquals(.30,config.getDouble("rod-assist.player-extra-margin"),1e-9);
        assertEquals(.35,config.getDouble("rod-assist.item-extra-margin"),1e-9);
        assertEquals(.50,config.getDouble("talents.flame-rod.contact-tolerance"),1e-9);
        assertFalse(RodBalanceMigration.migrate(config),"Migration must be idempotent");
    }

    @Test void leavesUserConfiguredMarginsAndTntRangeAlone() {
        YamlConfiguration config=new YamlConfiguration();
        config.set("rod-assist.player-extra-margin",.45);
        config.set("rod-assist.item-extra-margin",.10);
        config.set("talents.flame-rod.contact-tolerance",.60);
        assertFalse(RodBalanceMigration.migrate(config));
        assertEquals(.45,config.getDouble("rod-assist.player-extra-margin"),1e-9);
        assertEquals(.10,config.getDouble("rod-assist.item-extra-margin"),1e-9);
        assertEquals(.60,config.getDouble("talents.flame-rod.contact-tolerance"),1e-9);
    }

    @Test void upgradesOnlyExistingUnmodifiedSetting() {
        YamlConfiguration config=new YamlConfiguration();
        config.set("rod-assist.player-extra-margin",.20);
        config.set("rod-assist.item-extra-margin",.40); // admin customization
        assertTrue(RodBalanceMigration.migrate(config));
        assertEquals(.30,config.getDouble("rod-assist.player-extra-margin"),1e-9);
        assertEquals(.40,config.getDouble("rod-assist.item-extra-margin"),1e-9);
        assertFalse(config.isSet("talents.flame-rod.contact-tolerance"));
    }
}
