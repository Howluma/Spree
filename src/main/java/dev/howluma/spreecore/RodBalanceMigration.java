package dev.howluma.spreecore;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Update only the three stock v1.8.7 collision settings when a server
 * upgrades. Preserve other customized margins and ignition distances.
 */
final class RodBalanceMigration {
    private RodBalanceMigration() {}

    private record Setting(String path, double previous, double current) {}
    private static final Setting[] UPGRADES={
            new Setting("rod-assist.player-extra-margin",.20,.30),
            new Setting("rod-assist.item-extra-margin",.25,.35),
            new Setting("talents.flame-rod.contact-tolerance",.35,.50)
    };

    static boolean migrate(FileConfiguration config) {
        boolean changed=false;
        for(Setting setting:UPGRADES) {
            if(config.isSet(setting.path())
                    && Math.abs(config.getDouble(setting.path())-setting.previous())<1e-9) {
                config.set(setting.path(),setting.current());
                changed=true;
            }
        }
        return changed;
    }
}
