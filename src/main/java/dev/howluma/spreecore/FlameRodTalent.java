package dev.howluma.spreecore;

import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.scoreboard.Team;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Flame Rod replaces Barbed Hook; it never damages hooked players directly.
 * - Owned fishing rods receive an ephemeral Flame I enchantment and glint.
 * - The bobber visually burns and trails flame particles.
 * - A bobber that contacts TNT primes that block and credits the rod owner.
 * - Protected/cancelled TNTPrimeEvents are honored before changing any blocks.
 * - The temporary enchantment is removed outside the qualifying inventory.
 */
public final class FlameRodTalent implements Listener {
    private static final class ActiveHook {
        final FishHook hook;
        final UUID owner;
        final TriState originalFire;
        Location previous;
        final Set<String> attempted=new HashSet<>();
        final Set<UUID> ignitedPlayers=new HashSet<>();
        int ignited;
        ActiveHook(FishHook hook, UUID owner) {
            this.hook=hook;
            this.owner=owner;
            this.originalFire=hook.getVisualFire();
            this.previous=hook.getLocation().clone();
        }
    }

    private final SpreeCorePlugin plugin;
    private final NamespacedKey temporaryFlame;
    private final Map<UUID,ActiveHook> hooks=new HashMap<>();
    private final Map<UUID,Set<Inventory>> pending=new HashMap<>();
    private int ticks;

    FlameRodTalent(SpreeCorePlugin plugin) {
        this.plugin=plugin;
        this.temporaryFlame=new NamespacedKey(plugin,"flame_rod_temporary_enchant");
    }

    private boolean empowered(Player player) {
        return player.isOnline() && !player.isDead()
                &&plugin.getConfig().getBoolean("talents.flame-rod.enabled",true)
                &&plugin.progression().hasTalent(player,"flame_rod");
    }

    private boolean marked(ItemStack stack) {
        return stack!=null && stack.getType()==Material.FISHING_ROD
                &&stack.hasItemMeta()
                &&stack.getItemMeta().getPersistentDataContainer()
                    .has(temporaryFlame,PersistentDataType.BYTE);
    }

    /**
     * Return null when no changes are required. Genuine third-party Flame rods
     * are never marked or stripped, and all other enchantments are preserved.
     */
    private ItemStack convert(ItemStack original, boolean active) {
        if(original==null||original.getType()!=Material.FISHING_ROD)return null;
        boolean ours=marked(original);
        if(active && (ours||original.containsEnchantment(Enchantment.FLAME)))return null;
        if(!active && !ours)return null;

        ItemStack changed=original.clone();
        if(active) changed.addUnsafeEnchantment(Enchantment.FLAME,1);
        else changed.removeEnchantment(Enchantment.FLAME);
        ItemMeta meta=changed.getItemMeta();
        if(active) meta.getPersistentDataContainer().set(
                temporaryFlame,PersistentDataType.BYTE,(byte)1);
        else meta.getPersistentDataContainer().remove(temporaryFlame);
        changed.setItemMeta(meta);
        return changed;
    }

    private void refreshPlayer(Player player,boolean active) {
        PlayerInventory inventory=player.getInventory();
        ItemStack[] contents=inventory.getStorageContents();
        boolean changed=false;
        for(int i=0;i<contents.length;i++){
            ItemStack updated=convert(contents[i],active);
            if(updated!=null){contents[i]=updated;changed=true;}
        }
        if(changed) inventory.setStorageContents(contents);
        ItemStack off=convert(inventory.getItemInOffHand(),active);
        if(off!=null)inventory.setItemInOffHand(off);
    }

    public void refresh(Player player) {
        if(!player.isOnline()||player.isDead())return;
        refreshPlayer(player,empowered(player));
    }

    private void restoreCarried(Player player) {refreshPlayer(player,false);}

    private void restoreOutside(Inventory inventory) {
        if(inventory==null||inventory instanceof PlayerInventory)return;
        ItemStack[] contents=inventory.getStorageContents();
        boolean changed=false;
        for(int i=0;i<contents.length;i++){
            ItemStack updated=convert(contents[i],false);
            if(updated!=null){contents[i]=updated;changed=true;}
        }
        if(changed)inventory.setStorageContents(contents);
    }

    private void schedule(Player player,Inventory external) {
        UUID id=player.getUniqueId();
        Set<Inventory> already=pending.get(id);
        if(already!=null) {
            if(external!=null)already.add(external);
            return;
        }
        Set<Inventory> targets=Collections.newSetFromMap(new IdentityHashMap<>());
        if(external!=null)targets.add(external);
        pending.put(id,targets);
        Bukkit.getScheduler().runTask(plugin,()->{
            Set<Inventory> removed=pending.remove(id);
            if(removed!=null)for(Inventory target:removed)restoreOutside(target);
            if(player.isOnline()&&!player.isDead())refresh(player);
        });
    }

    private void stop(ActiveHook active) {
        if(active.hook.isValid())
            active.hook.setVisualFire(active.originalFire);
    }

    private boolean holdingRod(Player player) {
        return player.getInventory().getItemInMainHand().getType()==Material.FISHING_ROD
                ||player.getInventory().getItemInOffHand().getType()==Material.FISHING_ROD;
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void cast(PlayerFishEvent event) {
        Player owner=event.getPlayer();
        FishHook hook=event.getHook();
        UUID id=hook.getUniqueId();

        if(event.getState()==PlayerFishEvent.State.FISHING) {
            if(!empowered(owner)||!holdingRod(owner))return;
            ActiveHook previous=hooks.remove(id);
            if(previous!=null)stop(previous);
            ActiveHook active=new ActiveHook(hook,owner.getUniqueId());
            hooks.put(id,active);
            hook.setVisualFire(TriState.TRUE);
            if(plugin.getConfig().getBoolean("talents.flame-rod.cast-sound",true))
                owner.playSound(owner.getLocation(),Sound.ITEM_FIRECHARGE_USE,.35f,1.6f);
            return;
        }

        ActiveHook active=hooks.get(id);
        if(active==null)return;
        if(event.getState()==PlayerFishEvent.State.IN_GROUND) {
            searchTnt(active,hook.getLocation());
            return;
        }
        if(event.getState()==PlayerFishEvent.State.CAUGHT_ENTITY
                &&event.getCaught() instanceof Player caught) {
            ignitePlayer(active,caught);
        }

        // Retracting/reeling a hook removes the visual flames and tracking.
        if(event.getState()==PlayerFishEvent.State.REEL_IN
                ||event.getState()==PlayerFishEvent.State.CAUGHT_ENTITY
                ||event.getState()==PlayerFishEvent.State.CAUGHT_FISH
                ||event.getState()==PlayerFishEvent.State.FAILED_ATTEMPT) {
            hooks.remove(id);
            stop(active);
        }
    }

    /**
     * Combust a hooked ENEMY player one time per cast, on hook contact or on
     * caught-entity events. All normal region plugins can veto this through
     * EntityCombustByEntityEvent. Vanilla fire does the follow-up damage.
     */
    private void ignitePlayer(ActiveHook active,Player target) {
        Player owner=Bukkit.getPlayer(active.owner);
        if(owner==null || !empowered(owner) || !target.isOnline() || target.isDead())return;
        Team team=owner.getScoreboard().getEntryTeam(owner.getName());
        boolean teammate=team!=null && team.hasEntry(target.getName())
                && !team.allowFriendlyFire();
        boolean permitted=FlameRodRules.canIgnitePlayer(true,
                owner.getWorld().getPVP() && owner.getWorld().equals(target.getWorld()),
                owner.getUniqueId().equals(target.getUniqueId()),teammate,
                target.getGameMode()==GameMode.SPECTATOR,
                active.ignitedPlayers.contains(target.getUniqueId()));
        if(!permitted || !plugin.getConfig().getBoolean("talents.flame-rod.ignite-players",true))
            return;
        double duration=Math.max(0,Math.min(15,plugin.getConfig()
                .getDouble("talents.flame-rod.player-fire-seconds",4.0)));
        if(FlameRodRules.fireTicks(duration)<=0)return;

        EntityCombustByEntityEvent combust=new EntityCombustByEntityEvent(
                active.hook,target,(float)duration);
        Bukkit.getPluginManager().callEvent(combust);
        if(combust.isCancelled())return;
        int ticks=FlameRodRules.fireTicks(combust.getDuration());
        if(ticks<=0)return;
        if(!active.ignitedPlayers.add(target.getUniqueId()))return;
        target.setFireTicks(Math.max(target.getFireTicks(),ticks));
        // Retain player kill credit if the target burns to death after the
        // initial fishing hit, within the normal PvP environment credit window.
        plugin.combat().recordFlameRodIgnition(owner,target);
        if(plugin.getConfig().getBoolean("talents.flame-rod.player-fire-particles",true)) {
            Location location=target.getLocation().add(0,1,0);
            location.getWorld().spawnParticle(Particle.FLAME,location,
                    20,.24,.40,.24,.045);
        }
        if(plugin.getConfig().getBoolean("talents.flame-rod.player-fire-sound",true))
            target.getWorld().playSound(target.getLocation(),
                    Sound.ITEM_FIRECHARGE_USE,.65f,1.35f);
        plugin.talentFeedback().passive(owner,"flame_rod");
    }

    private String positionKey(Block block) {
        Location location=block.getLocation();
        return location.getWorld().getUID()+":"+location.getBlockX()+":"
                +location.getBlockY()+":"+location.getBlockZ();
    }

    private boolean prime(Block block,ActiveHook active,Player owner) {
        if(block.getType()!=Material.TNT)return false;
        // This is the same cancellable protection hook as a vanilla burning
        // projectile priming TNT. Never change the block before permission.
        TNTPrimeEvent permission=new TNTPrimeEvent(
                block,TNTPrimeEvent.PrimeCause.PROJECTILE,active.hook,null);
        Bukkit.getPluginManager().callEvent(permission);
        if(permission.isCancelled()||block.getType()!=Material.TNT)return false;
        BlockData old=block.getBlockData();
        Location center=block.getLocation().add(.5,0,.5);
        block.setType(Material.AIR,false);
        try {
            TNTPrimed tnt=block.getWorld().spawn(center,TNTPrimed.class);
            tnt.setSource(owner); // Explosive kill credit survives via SpreeCore.
            tnt.setFuseTicks(Math.max(1,Math.min(72000,plugin.getConfig()
                    .getInt("talents.flame-rod.tnt-fuse-ticks",80))));
        }catch(RuntimeException ex) {
            if(block.getType()==Material.AIR)block.setBlockData(old,false);
            plugin.getLogger().warning("Flame Rod could not ignite TNT at "+center+": "+ex);
            return false;
        }

        World world=block.getWorld();
        world.spawnParticle(Particle.FLAME,center.clone().add(0,.45,0),
                19,.35,.30,.35,.025);
        world.playSound(center,Sound.ENTITY_TNT_PRIMED,.75f,1.15f);
        plugin.talentFeedback().passive(owner,"flame_rod");
        return true;
    }

    private boolean searchTnt(ActiveHook active,Location sample) {
        if(sample.getWorld()==null)return false;
        int bx=sample.getBlockX(),by=sample.getBlockY(),bz=sample.getBlockZ();
        int max=Math.max(1,Math.min(16,plugin.getConfig()
                .getInt("talents.flame-rod.max-ignitions-per-cast",1)));
        if(active.ignited>=max)return false;
        Player player=Bukkit.getPlayer(active.owner);
        if(player==null||!empowered(player))return false;

        double tolerance=Math.max(.05,Math.min(.7,plugin.getConfig()
                .getDouble("talents.flame-rod.contact-tolerance",.50)));
        World world=sample.getWorld();
        for(int x=bx-1;x<=bx+1;x++)
            for(int y=by-1;y<=by+1;y++)
                for(int z=bz-1;z<=bz+1;z++){
                    if(!world.isChunkLoaded(x>>4,z>>4))continue;
                    if(!FlameRodMath.touchesBlock(sample.getX(),sample.getY(),
                            sample.getZ(),x,y,z,tolerance))continue;
                    Block block=world.getBlockAt(x,y,z);
                    if(block.getType()!=Material.TNT)continue;
                    String key=positionKey(block);
                    if(!active.attempted.add(key))continue; // No prime-event spam.
                    if(prime(block,active,player)) {
                        active.ignited++;
                        return true; // One ignition per trail sample.
                    }
                }
        return false;
    }

    /** Called every two ticks. Checks collision between previous/new positions
     * to catch fast-moving bobbers without expanding the ignition radius.
     */
    private void updateHook(ActiveHook active) {
        FishHook hook=active.hook;
        if(!hook.isValid())return;
        Location current=hook.getLocation();
        Entity hooked=hook.getHookedEntity();
        if(hooked instanceof Player target)
            ignitePlayer(active,target);
        if(plugin.getConfig().getBoolean("talents.flame-rod.bobber-particles",true))
            current.getWorld().spawnParticle(Particle.FLAME,current,4,.065,.065,.065,.008);
        int max=Math.max(1,Math.min(16,plugin.getConfig()
                .getInt("talents.flame-rod.max-ignitions-per-cast",1)));
        if(active.ignited<max &&active.previous.getWorld()!=null
                &&active.previous.getWorld().equals(current.getWorld())){
            double dx=current.getX()-active.previous.getX();
            double dy=current.getY()-active.previous.getY();
            double dz=current.getZ()-active.previous.getZ();
            double squared=dx*dx+dy*dy+dz*dz;
            int samples=squared>144?1:FlameRodMath.samples(dx,dy,dz);
            for(int i=1;i<=samples;i++){
                double f=(double)i/samples;
                Location step=active.previous.clone().add(dx*f,dy*f,dz*f);
                if(searchTnt(active,step))break;
            }
        }else if(active.ignited<max)searchTnt(active,current);
        active.previous=current.clone();
    }

    public void tick() {
        ticks++;
        if(ticks%10==0) for(Player player:Bukkit.getOnlinePlayers())refresh(player);
        for(Iterator<ActiveHook> iter=hooks.values().iterator();iter.hasNext();) {
            ActiveHook active=iter.next();
            Player owner=Bukkit.getPlayer(active.owner);
            if(owner==null || !empowered(owner) || !active.hook.isValid()) {
                stop(active);
                iter.remove();
                continue;
            }
            updateHook(active);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        if(event.getWhoClicked() instanceof Player player)
            schedule(player,event.getView().getTopInventory());
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drag(InventoryDragEvent event) {
        if(event.getWhoClicked() instanceof Player player)
            schedule(player,event.getView().getTopInventory());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void close(InventoryCloseEvent event) {
        if(event.getPlayer() instanceof Player player)
            schedule(player,event.getInventory());
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drop(PlayerDropItemEvent event) {
        ItemStack original=event.getItemDrop().getItemStack();
        ItemStack ordinary=convert(original,false);
        if(ordinary!=null)event.getItemDrop().setItemStack(ordinary);
        schedule(event.getPlayer(),null);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {
        if(event.getKeepInventory())return;
        for(int i=0;i<event.getDrops().size();i++){
            ItemStack ordinary=convert(event.getDrops().get(i),false);
            if(ordinary!=null)event.getDrops().set(i,ordinary);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hopper(InventoryMoveItemEvent event) {
        if(!marked(event.getItem()))return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin,()->restoreOutside(event.getSource()));
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(event.getPlayer().isOnline())refresh(event.getPlayer());
        },1L);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        Player player=event.getPlayer();
        pending.remove(player.getUniqueId());
        restoreCarried(player);
        for(Iterator<ActiveHook> iter=hooks.values().iterator();iter.hasNext();) {
            ActiveHook active=iter.next();
            if(active.owner.equals(player.getUniqueId())) {
                stop(active);
                iter.remove();
            }
        }
    }

    public void shutdown() {
        pending.clear();
        for(Player player:Bukkit.getOnlinePlayers())restoreCarried(player);
        for(ActiveHook active:hooks.values())stop(active);
        hooks.clear();
    }
}
