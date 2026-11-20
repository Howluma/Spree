package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Entity;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One half-heart of true fishing-rod hook damage to enemy players only.
 *
 * The first hit goes through Bukkit's normal damage event with a player-owned
 * source and a damage type that bypasses armor. Protection plugins can cancel
 * it, and armor/absorption/etc. are handled normally. When a successful hit
 * is reduced by potion/enchantment protection, a narrowly scoped health/absorption
 * adjustment tops the applied damage up to exactly one health point.
 *
 * No direct setHealth is performed when the Bukkit damage event was cancelled;
 * a fully resisted but otherwise approved event still receives true damage.
 */
public final class BarbedHookTalent implements Listener {
    private final SpreeCorePlugin plugin;
    private final Map<UUID,Long> processedHooks = new HashMap<>();
    private final Map<UUID,UUID> pendingDamage = new HashMap<>();
    private final Set<UUID> acceptedDamage = new HashSet<>();

    BarbedHookTalent(SpreeCorePlugin plugin) { this.plugin=plugin; }

    private boolean friendlyFireDisabled(Player shooter, Player target) {
        Team team=shooter.getScoreboard().getEntryTeam(shooter.getName());
        return team!=null && team.hasEntry(target.getName()) && !team.allowFriendlyFire();
    }

    /**
     * Observe the real native PvP damage event before compensating armor/
     * resistance reduction. If a protection plugin cancels the event,
     * there is NO custom damage and NO kill credit.
     */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void approvedDamage(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof Player target))return;
        UUID expectedHook=pendingDamage.get(target.getUniqueId());
        if(expectedHook==null || event.getDamage()<=0)return;
        Entity direct=event.getDamageSource().getDirectEntity();
        if(direct!=null && expectedHook.equals(direct.getUniqueId()))
            acceptedDamage.add(target.getUniqueId());
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void caught(PlayerFishEvent event) {
        if(event.getState()!=PlayerFishEvent.State.CAUGHT_ENTITY
                ||!(event.getCaught() instanceof Player target))return;
        Player shooter=event.getPlayer();
        boolean canAttack=BarbedHookMath.eligible(
                shooter.getWorld().getPVP(),shooter.equals(target),
                friendlyFireDisabled(shooter,target),event.isCancelled(),
                plugin.progression().hasTalent(shooter,"barbed_hook"));
        if(!canAttack || shooter.isDead() || target.isDead() || !target.isOnline()
                || !plugin.getConfig().getBoolean("talents.barbed-hook.enabled",true))return;
        if(shooter.getInventory().getItemInMainHand().getType()!=Material.FISHING_ROD
                && shooter.getInventory().getItemInOffHand().getType()!=Material.FISHING_ROD)
            return;
        UUID hookId=event.getHook().getUniqueId();
        if(processedHooks.containsKey(hookId))return;
        long now=System.currentTimeMillis();
        // Each real hook can hurt at most once; lazily expire old hook identities.
        processedHooks.entrySet().removeIf(e->now-e.getValue()>30_000);
        processedHooks.put(hookId,now);

        double points=BarbedHookMath.requestedPoints(plugin.getConfig().getDouble(
                "talents.barbed-hook.damage-hearts",.5));
        if(points<=0)return;
        double healthBefore=target.getHealth();
        double absorptionBefore=target.getAbsorptionAmount();

        DamageSource source=DamageSource.builder(DamageType.GENERIC)
                .withCausingEntity(shooter).withDirectEntity(event.getHook()).build();
        // Always dispatch ordinary damage first. A WorldGuard/other PvP cancel
        // is honored, and the native damage source carries the killer.
        UUID victimId=target.getUniqueId();
        acceptedDamage.remove(victimId);
        pendingDamage.put(victimId,hookId);
        try {
            target.damage(points,source);
        } finally {
            pendingDamage.remove(victimId);
        }
        if(!acceptedDamage.remove(victimId))return; // Cancelled PvP event.
        double healthAfter=target.getHealth();
        double absorptionAfter=target.getAbsorptionAmount();

        // Register combat credit BEFORE compensating health; the compensation
        // may kill a 0.5-heart victim directly.
        plugin.combat().recordRodHit(shooter,target);
        double missing=BarbedHookMath.additionalPoints(points,
                healthBefore,healthAfter,absorptionBefore,absorptionAfter,true);
        if(missing>0 && !target.isDead()) {
            // Vanilla absorption absorbs first, including for true damage.
            double extraAbsorption=Math.min(missing,target.getAbsorptionAmount());
            if(extraAbsorption>0) {
                target.setAbsorptionAmount(target.getAbsorptionAmount()-extraAbsorption);
                missing-=extraAbsorption;
            }
            if(missing>0 && !target.isDead())
                plugin.combat().creditedRodAdjustment(shooter,target,missing);
        }

        // The hook bobber itself is the recognizable visual reference.
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                target.getLocation().add(0,1.0,0),8,.25,.45,.25,.06);
        plugin.talentFeedback().passive(shooter,"barbed_hook");
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        // Hook IDs are short-lived; clear expired references on quit.
        long now=System.currentTimeMillis();
        processedHooks.entrySet().removeIf(e->now-e.getValue()>30_000);
    }
    public void shutdown() {
        processedHooks.clear();
        pendingDamage.clear();
        acceptedDamage.clear();
    }
}
