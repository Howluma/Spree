package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Slightly enlarged effective catch area for vanilla and Flame Rod bobbers.
 *
 * It never changes the real hitbox or damage of a player/item. Once an
 * eligible bobber intersects a slightly enlarged AABB, the official Paper
 * FishHook.setHookedEntity API performs normal fishing-rod retrieval.
 * The check is a bounded swept path and will not hook through solid blocks.
 */
public final class RodAssistManager implements Listener {
    private static final class Cast {
        final FishHook hook;
        final UUID owner;
        Vector previous;

        Cast(FishHook hook, Player owner) {
            this.hook=hook;
            this.owner=owner.getUniqueId();
            this.previous=hook.getLocation().toVector();
        }
    }

    private final SpreeCorePlugin plugin;
    private final Map<UUID,Cast> active=new HashMap<>();

    RodAssistManager(SpreeCorePlugin plugin) { this.plugin=plugin; }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void fish(PlayerFishEvent event) {
        UUID hookId=event.getHook().getUniqueId();
        if(event.getState()==PlayerFishEvent.State.FISHING) {
            if(plugin.getConfig().getBoolean("rod-assist.enabled",true))
                active.put(hookId,new Cast(event.getHook(),event.getPlayer()));
        } else if(event.getState()==PlayerFishEvent.State.IN_GROUND
                ||event.getState()==PlayerFishEvent.State.REEL_IN
                ||event.getState()==PlayerFishEvent.State.CAUGHT_ENTITY
                ||event.getState()==PlayerFishEvent.State.CAUGHT_FISH
                ||event.getState()==PlayerFishEvent.State.FAILED_ATTEMPT)
            active.remove(hookId);
    }

    @EventHandler public void leave(PlayerQuitEvent event) {
        UUID leaving=event.getPlayer().getUniqueId();
        active.values().removeIf(c->c.owner.equals(leaving));
    }

    private boolean validTarget(Player caster,Entity target) {
        if(target==caster || !target.isValid() || target.isDead())return false;
        if(target instanceof Item || target instanceof TNTPrimed)return true;
        if(!(target instanceof Player other))return false;
        if(other.isDead() || other.getGameMode()==GameMode.SPECTATOR)return false;
        if(plugin.getConfig().getBoolean("rod-assist.respect-pvp-settings",true)
                && !caster.getWorld().getPVP())return false;
        if(plugin.getConfig().getBoolean("rod-assist.respect-friendly-fire",true)) {
            Team team=caster.getScoreboard().getEntryTeam(caster.getName());
            if(team!=null && team.hasEntry(other.getName()) && !team.allowFriendlyFire())
                return false;
        }
        return true;
    }

    private static boolean clearPath(World world,Vector start,
            Vector end,double contactFraction) {
        if(contactFraction<=.00001)return true;
        Vector direction=end.clone().subtract(start).multiply(contactFraction);
        double distance=direction.length();
        if(distance<=.00001)return true;
        return world.rayTraceBlocks(new Location(world,
                    start.getX(),start.getY(),start.getZ()),
                direction.normalize(),distance,FluidCollisionMode.NEVER,true)==null;
    }

    /** Runs synchronously on the server thread, one bounded check per live cast. */
    public void tick() {
        if(!plugin.getConfig().getBoolean("rod-assist.enabled",true)) {
            active.clear();
            return;
        }
        for(Iterator<Cast> iter=active.values().iterator();iter.hasNext();) {
            Cast cast=iter.next();
            FishHook hook=cast.hook;
            Player caster=Bukkit.getPlayer(cast.owner);
            if(caster==null || !caster.isOnline() || !hook.isValid()
                    || hook.getHookedEntity()!=null
                    || hook.getState()==FishHook.HookState.HOOKED_ENTITY) {
                iter.remove();
                continue;
            }
            if(hook.getState()!=FishHook.HookState.UNHOOKED) {
                // Bobbing in water or embedded in terrain isn't a flying hook.
                cast.previous=hook.getLocation().toVector();
                continue;
            }

            Location loc=hook.getLocation();
            World world=loc.getWorld();
            if(world==null || world!=caster.getWorld()) {
                iter.remove();
                continue;
            }

            Vector now=loc.toVector();
            Vector previous=cast.previous;
            cast.previous=now.clone();
            double playerMargin=RodGeometry.boundedMargin(plugin.getConfig()
                    .getDouble("rod-assist.player-extra-margin",.30));
            double itemMargin=RodGeometry.boundedMargin(plugin.getConfig()
                    .getDouble("rod-assist.item-extra-margin",.35));
            double primedTntMargin=RodGeometry.boundedMargin(plugin.getConfig()
                    .getDouble("rod-assist.primed-tnt-extra-margin",.40));
            if(playerMargin<=0 && itemMargin<=0 && primedTntMargin<=0)continue;

            double maxSweep=RodGeometry.boundedSweep(plugin.getConfig()
                    .getDouble("rod-assist.max-sweep-blocks-per-tick",4));
            if(previous.distanceSquared(now)>maxSweep*maxSweep)
                previous=now.clone(); // No teleport or lag-spike magnetism.

            double pad=Math.max(Math.max(playerMargin,itemMargin),primedTntMargin)+.70;
            BoundingBox area=new BoundingBox(
                    Math.min(previous.getX(),now.getX())-pad,
                    Math.min(previous.getY(),now.getY())-pad,
                    Math.min(previous.getZ(),now.getZ())-pad,
                    Math.max(previous.getX(),now.getX())+pad,
                    Math.max(previous.getY(),now.getY())+pad,
                    Math.max(previous.getZ(),now.getZ())+pad);
            Collection<Entity> candidates=world.getNearbyEntities(area,
                    target -> (target instanceof Player || target instanceof Item
                            || target instanceof TNTPrimed)
                            && validTarget(caster,target));
            Entity earliestTarget=null;
            double earliest=Double.POSITIVE_INFINITY;
            for(Entity target:candidates) {
                double margin=target instanceof Player ? playerMargin
                        : target instanceof TNTPrimed ? primedTntMargin : itemMargin;
                if(margin<=0)continue;
                BoundingBox box=target.getBoundingBox();
                double contact=RodGeometry.firstContact(
                        previous.getX(),previous.getY(),previous.getZ(),
                        now.getX(),now.getY(),now.getZ(),
                        box.getMinX(),box.getMinY(),box.getMinZ(),
                        box.getMaxX(),box.getMaxY(),box.getMaxZ(),margin);
                if(!Double.isFinite(contact)||contact>=earliest)continue;
                if(!clearPath(world,previous,now,contact))continue;
                earliest=contact;
                earliestTarget=target;
            }

            if(earliestTarget!=null && hook.isValid()
                    && hook.getHookedEntity()==null
                    && hook.getState()==FishHook.HookState.UNHOOKED) {
                // Use native fishing hook attachment instead of custom
                // pulling, knockback, damage or swapping entity hitboxes.
                hook.setHookedEntity(earliestTarget);
                iter.remove();
            }
        }
    }

    public void shutdown() { active.clear(); }
}
