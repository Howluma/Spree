package dev.howluma.spreecore;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Personal, cinematic kill/death audio and a compact blue elimination burst.
 *
 * Call this directly from StreakManager's single PlayerDeathEvent handler,
 * using the SAME resolved killer as the killstreak/anti-farm calculation.
 * Do not attach a second death listener that could incorrectly attribute an
 * environmental kill or fire duplicate effects.
 */
public final class KillDeathFeedback {
    private static final Particle.DustOptions ICE_BLUE =
            new Particle.DustOptions(Color.fromRGB(0x51BBFF), 1.35f);
    private static final Particle.DustOptions MIDNIGHT_BLUE =
            new Particle.DustOptions(Color.fromRGB(0x204C9B), 1.15f);
    private final SpreeCorePlugin plugin;

    KillDeathFeedback(SpreeCorePlugin plugin) { this.plugin = plugin; }

    static boolean celebrate(boolean creditedPlayerKill, boolean antiFarmRewardAllowed,
            boolean requireStreakReward) {
        return creditedPlayerKill && (!requireStreakReward || antiFarmRewardAllowed);
    }

    static float volume(double configured) {
        if(!Double.isFinite(configured)) return .8f;
        return (float)Math.max(0,Math.min(2,configured));
    }

    static int ringPoints(int configured) {
        return Math.max(8,Math.min(32,configured));
    }

    /**
     * Unique, locally audible confirmation for the credited killer.
     * Particles appear at the victim's death location, visible to nearby
     * players rather than being attached to or following the killer.
     */
    public void onCreditedKill(Player killer, Player victim, boolean rewarded) {
        FileConfiguration cfg = plugin.getConfig();
        if(!cfg.getBoolean("combat-feedback.enabled",true)
                ||!cfg.getBoolean("combat-feedback.kill.enabled",true)) return;
        if(!celebrate(killer!=null && victim!=null && killer!=victim, rewarded,
                cfg.getBoolean("combat-feedback.kill.only-rewarded",false))) return;

        if(cfg.getBoolean("combat-feedback.kill.sounds",true)) {
            float volume=volume(cfg.getDouble("combat-feedback.kill.volume",.8));
            // Bright rising chime with a darker resonant accent: audible
            // only to the player who earned kill credit, not globally.
            killer.playSound(killer.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP,volume,.93f);
            killer.playSound(killer.getLocation(),
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME,volume*.85f,1.53f);
        }
        if(!cfg.getBoolean("combat-feedback.kill.particles",true))return;
        Location origin=victim.getLocation().clone().add(0,1,0);
        World world=origin.getWorld();
        if(world==null)return;
        int points=ringPoints(cfg.getInt("combat-feedback.kill.ring-points",16));
        // Two concentric blue shock rings, different heights/pitches, for
        // a short striking burst. Bounded at 64 dust emissions max.
        for(int i=0;i<points;i++){
            double angle=2*Math.PI*i/points;
            double x=Math.cos(angle),z=Math.sin(angle);
            world.spawnParticle(Particle.DUST,
                    origin.clone().add(x*.68,-.25,z*.68),
                    1,0,0,0,0,ICE_BLUE);
            world.spawnParticle(Particle.DUST,
                    origin.clone().add(x*1.08,.12,z*1.08),
                    1,0,0,0,0,MIDNIGHT_BLUE);
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                origin, 12,.38,.46,.38,.025);
        world.spawnParticle(Particle.END_ROD,
                origin.clone().add(0,.4,0),8,.30,.36,.30,.035);
    }

    /** A personal, descending death sting; it is never broadcast to nearby players. */
    public void onDeath(Player victim) {
        FileConfiguration cfg = plugin.getConfig();
        if(victim==null || !cfg.getBoolean("combat-feedback.enabled",true)
                ||!cfg.getBoolean("combat-feedback.death.enabled",true)
                ||!cfg.getBoolean("combat-feedback.death.sounds",true)) return;
        float volume=volume(cfg.getDouble("combat-feedback.death.volume",.75));
        victim.playSound(victim.getLocation(),
                Sound.BLOCK_BEACON_DEACTIVATE,volume,.63f);
        victim.playSound(victim.getLocation(),
                Sound.ENTITY_WARDEN_HEARTBEAT,volume*.52f,.85f);
    }
}
