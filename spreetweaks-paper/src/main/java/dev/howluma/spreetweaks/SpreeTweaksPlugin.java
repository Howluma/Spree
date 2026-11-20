package dev.howluma.spreetweaks;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fishing-bobber hit detection, slightly more forgiving on players/items.
 *
 * This does not alter players' real hitboxes or change the rod's damage,
 * pull distance, durability, fire, enchantments, or vanilla fishing.
 *
 * Works with the SpreeCore Flame Rod Talent: if a Flame Rod bobber catches
 * an entity, native Paper fishing-rod retrieval handles the interaction.
 */
public final class SpreeTweaksPlugin extends JavaPlugin
        implements Listener, CommandExecutor, TabCompleter {

    private static final class Cast {
        final FishHook hook;
        final UUID owner;
        Vector lastPosition;

        Cast(FishHook hook, Player owner) {
            this.hook = hook;
            this.owner = owner.getUniqueId();
            this.lastPosition = hook.getLocation().toVector();
        }
    }

    private final Map<UUID, Cast> casts = new HashMap<>();
    private BukkitTask task;

    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("spreetweaks");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        task = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("SpreeTweaks 1.0 enabled: slightly expanded "
                + "fishing-bobber catching for players and dropped items.");
    }

    @Override public void onDisable() {
        if(task != null)task.cancel();
        casts.clear();
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void fishing(PlayerFishEvent event) {
        UUID id = event.getHook().getUniqueId();
        if(event.getState()==PlayerFishEvent.State.FISHING) {
            casts.put(id, new Cast(event.getHook(), event.getPlayer()));
        } else if(event.getState()==PlayerFishEvent.State.IN_GROUND
                ||event.getState()==PlayerFishEvent.State.REEL_IN
                ||event.getState()==PlayerFishEvent.State.CAUGHT_ENTITY
                ||event.getState()==PlayerFishEvent.State.CAUGHT_FISH
                ||event.getState()==PlayerFishEvent.State.FAILED_ATTEMPT) {
            casts.remove(id);
        }
    }

    @EventHandler public void logout(PlayerQuitEvent event) {
        UUID leaving = event.getPlayer().getUniqueId();
        casts.values().removeIf(c -> c.owner.equals(leaving));
    }

    private boolean isAllowedTarget(Player caster, Entity target) {
        if(target == caster || !target.isValid() || target.isDead())return false;
        if(target instanceof Item)return true;
        if(!(target instanceof Player player))return false;
        if(player.getGameMode()==GameMode.SPECTATOR || player.isDead())return false;

        if(getConfig().getBoolean("rod-assist.respect-pvp-settings",true)
                && !caster.getWorld().getPVP())return false;

        if(getConfig().getBoolean("rod-assist.respect-friendly-fire",true)) {
            Team team=caster.getScoreboard().getEntryTeam(caster.getName());
            if(team!=null && team.hasEntry(player.getName()) && !team.allowFriendlyFire())
                return false;
        }
        return true;
    }

    private boolean pathIsUnblocked(World world, Vector start,
            Vector end, double contactFraction) {
        if(contactFraction <= .00001)return true;
        Vector path=end.clone().subtract(start).multiply(contactFraction);
        double distance=path.length();
        if(distance<=.00001)return true;
        return world.rayTraceBlocks(
                new Location(world,start.getX(),start.getY(),start.getZ()),
                path.normalize(),distance,FluidCollisionMode.NEVER,true)==null;
    }

    private void inspect(Cast cast) {
        FishHook hook=cast.hook;
        Player caster=Bukkit.getPlayer(cast.owner);
        if(caster==null || !caster.isOnline() || !hook.isValid())return;
        Location location=hook.getLocation();
        if(location.getWorld()==null || location.getWorld()!=caster.getWorld())return;
        Vector now=location.toVector();
        Vector before=cast.lastPosition;
        cast.lastPosition=now.clone();

        // A hook has already caught its target or is bobbing normally.
        if(hook.getHookedEntity()!=null || hook.getState()!=FishHook.HookState.UNHOOKED)return;

        double playerMargin=RodGeometry.boundedMargin(
                getConfig().getDouble("rod-assist.player-extra-margin",.20));
        double itemMargin=RodGeometry.boundedMargin(
                getConfig().getDouble("rod-assist.item-extra-margin",.25));
        if(playerMargin<=0 && itemMargin<=0)return;

        double maxSweep=RodGeometry.boundedSweep(getConfig()
                .getDouble("rod-assist.max-sweep-blocks-per-tick",4.0));
        if(before.distanceSquared(now)>maxSweep*maxSweep) {
            // Avoid a cross-map sweep after a teleport, lag spike or plugin
            // moving the bobber. Check only its current location.
            before=now.clone();
        }

        // Search the swept segment, not an enormous sphere around the player.
        double padding=1.0;
        BoundingBox bounds=new BoundingBox(
                Math.min(before.getX(),now.getX())-padding,
                Math.min(before.getY(),now.getY())-padding,
                Math.min(before.getZ(),now.getZ())-padding,
                Math.max(before.getX(),now.getX())+padding,
                Math.max(before.getY(),now.getY())+padding,
                Math.max(before.getZ(),now.getZ())+padding);

        Collection<Entity> candidates=caster.getWorld().getNearbyEntities(
                bounds, target -> (target instanceof Player || target instanceof Item)
                        && isAllowedTarget(caster,target));

        Entity best=null;
        double first=Double.POSITIVE_INFINITY;
        for(Entity target:candidates) {
            double margin=target instanceof Player ? playerMargin : itemMargin;
            if(margin<=0)continue;
            BoundingBox box=target.getBoundingBox();
            double hit=RodGeometry.firstContact(
                    before.getX(),before.getY(),before.getZ(),
                    now.getX(),now.getY(),now.getZ(),
                    box.getMinX(),box.getMinY(),box.getMinZ(),
                    box.getMaxX(),box.getMaxY(),box.getMaxZ(),margin);
            if(hit>=first || !Double.isFinite(hit))continue;
            if(!pathIsUnblocked(caster.getWorld(),before,now,hit))continue;
            first=hit;
            best=target;
        }

        if(best!=null && hook.isValid()
                && hook.getHookedEntity()==null
                && hook.getState()==FishHook.HookState.UNHOOKED) {
            // Official Paper API: allows the normal native fishing reeling
            // logic to pull the player/item and send the usual caught event.
            hook.setHookedEntity(best);
        }
    }

    private void tick() {
        if(!getConfig().getBoolean("rod-assist.enabled",true)) {
            casts.clear();
            return;
        }
        Iterator<Map.Entry<UUID,Cast>> it=casts.entrySet().iterator();
        while(it.hasNext()) {
            Cast cast=it.next().getValue();
            FishHook hook=cast.hook;
            Player caster=Bukkit.getPlayer(cast.owner);
            if(caster==null || !caster.isOnline() || !hook.isValid()
                    || hook.getHookedEntity()!=null
                    || hook.getState()==FishHook.HookState.HOOKED_ENTITY) {
                it.remove();
                continue;
            }
            inspect(cast);
            if(hook.getHookedEntity()!=null)it.remove();
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command,
            String label, String[] args) {
        if(args.length==1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§9SpreeTweaks §7| §aSettings reloaded.");
            return true;
        }
        if(args.length==0 || (args.length==1 && args[0].equalsIgnoreCase("status"))) {
            sender.sendMessage("§9SpreeTweaks §7| fishing-rod catching: "
                    +(getConfig().getBoolean("rod-assist.enabled",true) ? "§aON" : "§cOFF"));
            sender.sendMessage("§7Extra hitbox margin: players §b"
                    +RodGeometry.boundedMargin(getConfig()
                        .getDouble("rod-assist.player-extra-margin",.20))
                    +" §7blocks; dropped items §b"
                    +RodGeometry.boundedMargin(getConfig()
                        .getDouble("rod-assist.item-extra-margin",.25))
                    +" §7blocks.");
            return true;
        }
        sender.sendMessage("§cUsage: /spreetweaks [status|reload]");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender,
            Command command, String alias, String[] args) {
        if(args.length!=1)return List.of();
        String prefix=args[0].toLowerCase(java.util.Locale.ROOT);
        return List.of("status","reload").stream().filter(x -> x.startsWith(prefix)).toList();
    }
}
