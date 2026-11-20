package dev.howluma.spreecore;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.Material;
import org.bukkit.damage.DamageType;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.Keyed;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Automatically triggered Talents. No added activation items, keybinds or
 * periodic proc spam. Cooldowns start only on successful triggers.
 */
public final class TalentEffects implements Listener {
    private record Combo(UUID victim, int hits, long lastHit) {}
    private record Sense(UUID target, long expires) {}
    private record SmashAttempt(UUID target, int tick) {}
    private final SpreeCorePlugin plugin;
     private final Map<UUID,Combo> combos = new HashMap<>();
    private final Map<UUID,Integer> bloodlust = new HashMap<>();
    private final Map<UUID,Long> jumpBlocked = new HashMap<>();
    private final Map<UUID,Sense> sensed = new HashMap<>();
    private final Map<UUID,SmashAttempt> acceptedSmashes = new HashMap<>();
    private final Map<UUID,Integer> lastSmashFx = new HashMap<>();
    private final Map<UUID,Location> lastWebSafe = new HashMap<>();
    private final Set<UUID> pendingEscapes = new HashSet<>();
    private final Map<UUID,Long> lastEscapeFailure = new HashMap<>();
    private final Map<UUID,Integer> insuranceOnRespawn = new HashMap<>();
    private final NamespacedKey jumpKey,stepKey,appleKey,rodKey;

    TalentEffects(SpreeCorePlugin plugin) {
        this.plugin=plugin;
        jumpKey=new NamespacedKey(plugin,"talent_acrobat_jump");
        stepKey=new NamespacedKey(plugin,"talent_trail_step");
        appleKey=new NamespacedKey(plugin,"blacksmith_golden_apple");
        rodKey=new NamespacedKey(plugin,"blacksmith_breeze_rod");
    }
    private boolean has(Player p,String talent){return plugin.progression().hasTalent(p,talent);}
    private int ti(String name,int fallback){return plugin.getConfig().getInt("talents."+name,fallback);}
    private double td(String name,double fallback){return plugin.getConfig().getDouble("talents."+name,fallback);}

    private boolean available(Player p,String key){
        return System.currentTimeMillis()>=plugin.data().get(p).talentCooldowns.getOrDefault(key,0L);
    }
    /** Start a timer only AFTER the ability really activated. */
    private void start(Player p,String key,int seconds){
        int bounded=Math.max(0,Math.min(3600,seconds));
        plugin.data().get(p).talentCooldowns.put(key,
                System.currentTimeMillis()+(long)bounded*1000L);
        plugin.data().dirty();
        plugin.talentFeedback().activated(p,key,bounded);
    }
    /** Emergency Talents have their own cooldown plus a shared 10s anti-stack. */
    private boolean defensiveAvailable(Player p,String key){
        return available(p,key)&&available(p,"defensive_shared");
    }
    private void startDefense(Player p,String key,int seconds){
        plugin.data().get(p).talentCooldowns.put("defensive_shared",
                System.currentTimeMillis()+
                1000L*Math.max(0,ti("defense.shared-cooldown-seconds",10)));
        start(p,key,seconds);
    }
    private boolean apply(Player p,PotionEffectType type,int amplifier,int seconds){
        int duration=Math.max(1,seconds)*20;
        PotionEffect old=p.getPotionEffect(type);
        if(old!=null&&(old.getAmplifier()>amplifier
                ||(old.getAmplifier()==amplifier&&old.getDuration()>=duration)))return false;
        return p.addPotionEffect(new PotionEffect(type,duration,amplifier,true,false,true),true);
    }
    /**
     * A player's feet block is not always the cobweb they are touching:
     * the web may overlap their head or only the edge of their hitbox.
     * Check all touched blocks using the actual entity bounding box.
     */
    private BoundingBox at(Player player, Location location) {
        Location current=player.getLocation();
        return player.getBoundingBox().clone().shift(
                location.getX()-current.getX(),
                location.getY()-current.getY(),
                location.getZ()-current.getZ());
    }

    private boolean touchesWeb(Player player, Location location) {
        if(location==null || !player.getWorld().equals(location.getWorld()))return false;
        BoundingBox box=at(player,location);
        return WebEscapeMath.occupied(box.getMinX(),box.getMinY(),box.getMinZ(),
                box.getMaxX(),box.getMaxY(),box.getMaxZ(),
                (x,y,z)->location.getWorld().getBlockAt(x,y,z).getType()==Material.COBWEB);
    }

    /** A safe landing cannot contain web, liquid, or solid collision blocks. */
    private boolean clearBody(Player player, Location location, boolean allowWeb) {
        if(location==null || !player.getWorld().equals(location.getWorld()))return false;
        World world=location.getWorld();
        BoundingBox box=at(player,location);
        return !WebEscapeMath.occupied(box.getMinX(),box.getMinY(),box.getMinZ(),
                box.getMaxX(),box.getMaxY(),box.getMaxZ(),(x,y,z)->{
                    if(!world.isChunkLoaded(x>>4,z>>4))return true;
                    Block block=world.getBlockAt(x,y,z);
                    Material m=block.getType();
                    return !block.isPassable() || block.isLiquid()
                            || (!allowWeb && m==Material.COBWEB)
                            || m==Material.FIRE || m==Material.SOUL_FIRE
                            || m==Material.POWDER_SNOW || m==Material.SWEET_BERRY_BUSH;
                });
    }

    private boolean safeLanding(Player player, Location location) {
        World world=location.getWorld();
        int x=location.getBlockX(),y=(int)Math.floor(location.getY()-.10),
                z=location.getBlockZ();
        return world.isChunkLoaded(x>>4,z>>4) && world.getBlockAt(x,y,z).getType().isSolid()
                && clearBody(player,location,false);
    }

    /** Check a straight unobstructed route. Cobwebs are traversable, walls are not. */
    private boolean routeClear(Player player, Location from, Location to) {
        if(!from.getWorld().equals(to.getWorld()))return false;
        double distance=from.distance(to);
        int steps=Math.max(1,(int)Math.ceil(distance*5));
        for(int i=1;i<=steps;i++) {
            double alpha=(double)i/steps;
            Location step=from.clone().add(
                    (to.getX()-from.getX())*alpha,
                    (to.getY()-from.getY())*alpha,
                    (to.getZ()-from.getZ())*alpha);
            if(!clearBody(player,step,true))return false;
        }
        return true;
    }

    /** Prefer the position the player occupied before entering the web. */
    private Location escapeDestination(Player player) {
        Location current=player.getLocation();
        Location previous=lastWebSafe.get(player.getUniqueId());
        if(previous!=null && previous.getWorld().equals(current.getWorld())
                && previous.distanceSquared(current)<=36
                && safeLanding(player,previous) && routeClear(player,current,previous)) {
            Location back=previous.clone();
            back.setYaw(current.getYaw());
            back.setPitch(current.getPitch());
            return back;
        }
        int bx=current.getBlockX(),by=current.getBlockY(),bz=current.getBlockZ();
        for(int[] offset:WebEscapeMath.candidateOffsets(5)) {
            Location candidate=new Location(current.getWorld(),
                    bx+offset[0]+.5,by+offset[1],bz+offset[2]+.5,
                    current.getYaw(),current.getPitch());
            if(safeLanding(player,candidate) && routeClear(player,current,candidate))
                return candidate;
        }
        return null; // Never teleport into a wall or outside protected areas.
    }

    /**
     * Schedule once for the next tick so the movement event has finished.
     * No cooldown is spent until a real escape/Slowness cleanse succeeds.
     */
    private void queueEscape(Player player) {
        UUID id=player.getUniqueId();
        if(!pendingEscapes.add(id))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            pendingEscapes.remove(id);
            if(!player.isOnline()||player.isDead()
                    ||!has(player,"escape_artist")||!available(player,"escape_artist"))return;
            boolean web=touchesWeb(player,player.getLocation());
            PotionEffect slow=player.getPotionEffect(PotionEffectType.SLOWNESS);
            if(!web && slow==null)return;
            if(web) {
                Location exit=escapeDestination(player);
                if(exit==null) {
                    long now=System.currentTimeMillis();
                    if(now-lastEscapeFailure.getOrDefault(id,0L)>3000L) {
                        Text.action(player,"&cEscape Artist: no safe exit nearby");
                        lastEscapeFailure.put(id,now);
                    }
                    return; // Do not waste a Talent cooldown when stuck.
                }
                lastEscapeFailure.remove(id);
                Location former=player.getLocation().clone();
                if(!player.teleport(exit,PlayerTeleportEvent.TeleportCause.PLUGIN))return;
                lastWebSafe.put(id,player.getLocation().clone());
                player.setVelocity(new org.bukkit.util.Vector(0,0,0));
                if(plugin.getConfig().getBoolean("talents.feedback.particles",true))
                    former.getWorld().spawnParticle(Particle.CLOUD,former.add(0,.85,0),
                            24,.34,.55,.34,.035);
                if(plugin.getConfig().getBoolean("talents.feedback.sounds",true))
                    player.getWorld().playSound(player.getLocation(),
                            Sound.ENTITY_ENDERMAN_TELEPORT,.7f,1.3f);
            }
            if(slow!=null)player.removePotionEffect(PotionEffectType.SLOWNESS);
            apply(player,PotionEffectType.SPEED,2,3);
            start(player,"escape_artist",ti("escape-artist.cooldown-seconds",50));
            if(slow!=null && slow.getDuration()>60)
                Bukkit.getScheduler().runTaskLater(plugin,()->{
                    if(player.isOnline()&&!player.isDead()
                            &&!player.hasPotionEffect(PotionEffectType.SLOWNESS))
                        player.addPotionEffect(slow.withDuration(slow.getDuration()-60),true);
                },60L);
        });
    }

    /** Enters webs without damage, including webs crossing only the head. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void moveIntoWeb(PlayerMoveEvent event) {
        if(event instanceof PlayerTeleportEvent || !event.hasChangedPosition())return;
        Player player=event.getPlayer();
        if(!has(player,"escape_artist") || !available(player,"escape_artist"))return;
        Location next=event.getTo();
        if(touchesWeb(player,next)) {
            Location before=event.getFrom();
            if(!touchesWeb(player,before) && safeLanding(player,before))
                lastWebSafe.put(player.getUniqueId(),before.clone());
            queueEscape(player);
        }else if(player.isOnGround() && safeLanding(player,next)) {
            lastWebSafe.put(player.getUniqueId(),next.clone());
        }
    }

    private static boolean axe(Material type){return type.name().endsWith("_AXE");}
    private static Player credited(EntityDamageByEntityEvent event){
        if(event.getDamager() instanceof Player p)return p;
        if(event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p)return p;
        return null;
    }
    private static double maxHealth(Player p){
        AttributeInstance health=p.getAttribute(Attribute.MAX_HEALTH);
        return health==null?20:health.getValue();
    }
    private void heal(Player p,double hearts){
        if(!p.isDead())p.setHealth(Math.min(maxHealth(p),p.getHealth()+2*hearts));
    }

    /** Use transient keyed modifiers; other plugins' attributes stay intact. */
    private void modifier(Player p,Attribute attribute,NamespacedKey key,
                          AttributeModifier.Operation operation,double amount){
        AttributeInstance instance=p.getAttribute(attribute);
        if(instance==null)return;
        AttributeModifier previous=null;
        for(var modifier:instance.getModifiers())
            if(modifier.getKey().equals(key)){previous=modifier;break;}
        if(previous!=null&&previous.getOperation()==operation
                &&Math.abs(previous.getAmount()-amount)<1e-9)return;
        if(previous!=null)instance.removeModifier(previous);
        if(amount!=0)instance.addTransientModifier(new AttributeModifier(key,amount,operation));
    }
    public void refresh(Player p){
        if(p.isDead())return;
        modifier(p,Attribute.JUMP_STRENGTH,jumpKey,AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                has(p,"acrobat")?td("acrobat.jump-bonus-fraction",.10):0);
        modifier(p,Attribute.STEP_HEIGHT,stepKey,AttributeModifier.Operation.ADD_NUMBER,
                has(p,"trailblazer")?td("trailblazer.step-height-addition",.4):0);
        plugin.quickHands().refresh(p);
        if(plugin.progression().hasPrivilege(p,"mace_wielder")
                &&p.getCooldown(Material.MACE)==0)
            restoreMaceOverlay(p);
    }
    public void clear(Player p){
        for(var pair:Map.of(jumpKey,Attribute.JUMP_STRENGTH,stepKey,Attribute.STEP_HEIGHT).entrySet())
            modifier(p,pair.getValue(),pair.getKey(),
                    pair.getKey().equals(jumpKey)?AttributeModifier.Operation.MULTIPLY_SCALAR_1
                            :AttributeModifier.Operation.ADD_NUMBER,0);
        comboClear(p.getUniqueId());
         jumpBlocked.remove(p.getUniqueId());
        sensed.remove(p.getUniqueId());
        insuranceOnRespawn.remove(p.getUniqueId());
        lastWebSafe.remove(p.getUniqueId());
        pendingEscapes.remove(p.getUniqueId());
        lastEscapeFailure.remove(p.getUniqueId());
        acceptedSmashes.remove(p.getUniqueId());
        lastSmashFx.remove(p.getUniqueId());
    }
    private void comboClear(UUID id){combos.remove(id);bloodlust.remove(id);}
    public void clearCombatCombo(Player player) { comboClear(player.getUniqueId()); }
    public void shutdown(){for(Player p:Bukkit.getOnlinePlayers())clear(p);}

    @EventHandler public void quit(PlayerQuitEvent e){clear(e.getPlayer());}

    /**
     * DENY only a real candidate smash while the player is on cooldown.
     * Never START the cooldown here: an attempted attack can miss, hit a
     * shield, deal zero damage, or be cancelled by another plugin.
     */
    @EventHandler(priority=EventPriority.HIGHEST)
    public void smash(EntityAttemptSmashAttackEvent e) {
        if (!(e.getEntity() instanceof Player attacker)
                || e.getWeapon().getType() != Material.MACE
                || !plugin.progression().hasPrivilege(attacker,"mace_wielder")
                || e.getResult() == Event.Result.DENY
                || (e.getResult() == Event.Result.DEFAULT && !e.getOriginalResult()))
            return;
        long until = plugin.data().get(attacker).talentCooldowns.getOrDefault("mace_smash",0L);
        if (System.currentTimeMillis() < until) {
            e.setResult(Event.Result.DENY);
            long seconds = (until - System.currentTimeMillis() + 999) / 1000;
            Text.action(attacker, "&cMace slam recharging: &f" + Math.max(1,seconds) + "s");
        }
    }

    /** Remember accepted special-smash attempts, not ordinary mace swings. */
    @EventHandler(priority=EventPriority.MONITOR)
    public void observedSmashAttempt(EntityAttemptSmashAttackEvent event) {
        if(!(event.getEntity() instanceof Player player)
                ||event.getWeapon().getType()!=Material.MACE)return;
        UUID id=player.getUniqueId();
        Event.Result result=event.getResult();
        if(MaceSmashMath.successfulAttempt(event.getOriginalResult(),
                result==Event.Result.ALLOW,result==Event.Result.DENY)
                &&plugin.progression().hasPrivilege(player,"mace_wielder"))
            acceptedSmashes.put(id,new SmashAttempt(
                    event.getTarget().getUniqueId(),Bukkit.getCurrentTick()));
        else acceptedSmashes.remove(id);
    }

    /** Visible in-world impact for everyone near the victim, not only the wielder. */
    private void maceImpact(Player attacker, org.bukkit.entity.Entity target) {
        Location center=target.getLocation().add(0,.9,0);
        World world=center.getWorld();
        if(plugin.getConfig().getBoolean("combat.mace.impact-particles",true)) {
            world.spawnParticle(Particle.GUST_EMITTER_SMALL,center,1);
            world.spawnParticle(Particle.CLOUD,center,26,.42,.32,.42,.06);
            Particle.DustOptions blue=new Particle.DustOptions(Color.fromRGB(0x397FEA),1.45f);
            world.spawnParticle(Particle.DUST,center,17,.65,.35,.65,.025,blue);
            // A visible ground-level expanding shockwave, one burst per smash.
            for(int step=0;step<16;step++) {
                double angle=2*Math.PI*step/16;
                Location ring=center.clone().add(
                        Math.cos(angle)*1.65,-.67,Math.sin(angle)*1.65);
                world.spawnParticle(Particle.DUST,ring,1,0,0,0,0,blue);
            }
        }
        if(plugin.getConfig().getBoolean("combat.mace.impact-sound",true))
            world.playSound(center,Sound.ITEM_MACE_SMASH_GROUND,.75f,1.13f);
    }

    /** Recreate the client-side mace icon overlay after relogging with a timer. */
    private void restoreMaceOverlay(Player player) {
        long remaining=plugin.data().get(player).talentCooldowns
                .getOrDefault("mace_smash",0L)-System.currentTimeMillis();
        int ticks=MaceSmashMath.overlayTicks(remaining);
        if(ticks>0 && plugin.progression().hasPrivilege(player,"mace_wielder")
                &&plugin.getConfig().getBoolean("combat.mace.cooldown-overlay",true)
                &&player.getCooldown(Material.MACE)<ticks)
            player.setCooldown(Material.MACE,ticks);
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player=event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(player.isOnline()&&!player.isDead())restoreMaceOverlay(player);
        },3L);
    }

    /** Only a confirmed, unblocked mace-smash damage event starts the timer. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void confirmedSmash(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)
                || attacker.getInventory().getItemInMainHand().getType()!=Material.MACE
                || !plugin.progression().hasPrivilege(attacker,"mace_wielder"))
            return;
        SmashAttempt observed=acceptedSmashes.get(attacker.getUniqueId());
        int tick=Bukkit.getCurrentTick();
        boolean actualAttempt=observed!=null && observed.target().equals(e.getEntity().getUniqueId())
                && tick>=observed.tick() && tick-observed.tick()<=2;
        // Some other plugins adjust DamageSource. An accepted Paper smash
        // attempt is a second authoritative signal; normal hits have neither.
        boolean special=DamageType.MACE_SMASH.equals(e.getDamageSource().getDamageType())
                ||actualAttempt;
        boolean shield=e.getEntity() instanceof Player defender
                &&actualShieldBlock(e,defender,attacker);
        if (!MaceSmashMath.connects(special,e.isCancelled(),e.getFinalDamage(),shield))return;
        Map<String,Long> timers=plugin.data().get(attacker).talentCooldowns;
        long timestamp=System.currentTimeMillis();
        // Never create duplicate shockwaves or renew a cooldown for the same
        // smash (Wind Burst and secondary entities may produce extra hits).
        if(timers.getOrDefault("mace_smash",0L)>timestamp
                ||lastSmashFx.getOrDefault(attacker.getUniqueId(),Integer.MIN_VALUE)==tick)return;
        int base=plugin.getConfig().getInt("combat.mace.slam-cooldown-seconds",60);
        int mastery=plugin.getConfig().getInt("combat.mace.mastery-slam-cooldown-seconds",30);
        int seconds=Math.max(0,Math.min(3600,has(attacker,"mace_mastery")?mastery:base));
        lastSmashFx.put(attacker.getUniqueId(),tick);
        acceptedSmashes.remove(attacker.getUniqueId());
        if(seconds>0){
            timers.put("mace_smash",timestamp+seconds*1000L);
            plugin.data().dirty();
            // Vanilla's shaded hotbar icon. This does not prevent regular mace
            // attacks: the attempt event above blocks ONLY further smashes.
            if(plugin.getConfig().getBoolean("combat.mace.cooldown-overlay",true))
                attacker.setCooldown(Material.MACE,Math.max(
                        attacker.getCooldown(Material.MACE),seconds*20));
        }
        maceImpact(attacker,e.getEntity());
        plugin.talentFeedback().activated(attacker,"mace_smash",seconds);
    }

    /** Damage multipliers only: these run BEFORE Paper computes the final hit. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void combat(EntityDamageByEntityEvent e){
        if(e.getDamage()<=0 || !(e.getEntity() instanceof Player victim))return;
        Player attacker=credited(e);
        if(attacker==null || attacker.equals(victim))return;
        if(has(attacker,"executioner") && victim.getHealth()<
                maxHealth(victim)*td("executioner.health-threshold-fraction",.5)){
            e.setDamage(e.getDamage()*(1+td("executioner.bonus-damage-fraction",.10)));
            if(e.getFinalDamage()>0)plugin.talentFeedback().passive(attacker,"executioner");
        }
        if(e.getDamager() instanceof Player
                && e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && has(attacker,"combo_breaker")){
            Combo previous=combos.get(attacker.getUniqueId());
            int threshold=Math.max(1,ti("combo-breaker.hits-required",3));
            if(previous!=null && previous.victim().equals(victim.getUniqueId())
                    && System.currentTimeMillis()-previous.lastHit()
                        <=ti("combo-breaker.window-seconds",5)*1000L
                    && previous.hits()>=threshold) {
                e.setDamage(e.getDamage()*(1+td("combo-breaker.bonus-damage-fraction",.10)));
                if(e.getFinalDamage()>0 && previous.hits()==threshold)
                    plugin.talentFeedback().passive(attacker,"combo_breaker");
            }
        }
    }

    @SuppressWarnings("deprecation")
    private boolean actualShieldBlock(EntityDamageByEntityEvent e,Player defender,Player attacker){
        if(!defender.isBlocking()
                ||e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK)return false;
        // Paper retains a damage modifier for the actual damage that the shield
        // absorbed. Merely raising a shield does NOT qualify for Riposte.
        try {
            if(e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING))
                return e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)<-0.01;
        } catch(UnsupportedOperationException | IllegalArgumentException ignored) {
            // New shield item components may not expose the deprecated modifier.
        }
        // Only a zero-damage frontal block qualifies as a fallback. Never
        // trigger just because the defender was raising a shield.
        if(e.getFinalDamage()>0.001 || e.getDamage()<=0)return false;
        org.bukkit.util.Vector toward=attacker.getLocation().toVector()
                .subtract(defender.getLocation().toVector()).setY(0);
        org.bukkit.util.Vector facing=defender.getLocation().getDirection().setY(0);
        return toward.lengthSquared()>.001 && facing.lengthSquared()>.001
                && facing.normalize().dot(toward.normalize())>.25;
    }

    /**
     * Only a real, successful shield disable may activate Sunder.
     * Paper exposes the actual vanilla cooldown here; modifying it before
     * application is much safer than scheduling a guessed shield cooldown.
     */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void shieldDisable(PlayerShieldDisableEvent e) {
        if(!(e.getDamager() instanceof Player attacker)
                || !axe(attacker.getInventory().getItemInMainHand().getType())
                || !has(attacker,"sunder") || !available(attacker,"sunder"))return;
        int extra=Math.max(0,ti("sunder.extra-shield-disable-ticks",30));
        if(extra<=0)return;
        e.setCooldown(Math.min(72000,e.getCooldown()+extra));
        start(attacker,"sunder",ti("sunder.cooldown-seconds",45));
    }

    /** A shield block may reduce damage to zero, so handle it separately. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void blockedShield(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player defender)
                ||!(e.getDamager() instanceof Player attacker)
                ||defender.equals(attacker)
                ||!has(defender,"riposte")
                ||!actualShieldBlock(e,defender,attacker)
                ||!available(defender,"riposte"))return;
        int length=Math.max(1,ti("riposte.duration-seconds",2));
        boolean slow=apply(attacker,PotionEffectType.SLOWNESS,1,length);
        jumpBlocked.put(attacker.getUniqueId(),System.currentTimeMillis()+length*1000L);
        start(defender,"riposte",ti("riposte.cooldown-seconds",45));
        // Riposte's stun is real even if a stronger slowness effect already exists.
    }

    /** Count ONLY un-cancelled, non-blocked hits; do not burn cooldowns on misses. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void landedHit(EntityDamageByEntityEvent e){
        if(e.getFinalDamage()<=0 || !(e.getEntity() instanceof Player victim))return;
        Player attacker=credited(e);
        if(attacker==null || attacker.equals(victim))return;
        // Being struck interrupts the defender's offensive combo.
        combos.remove(victim.getUniqueId());
        boolean melee=e.getDamager() instanceof Player
                &&e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        if(melee) {
            if(has(attacker,"combo_breaker")){
                long now=System.currentTimeMillis();
                Combo previous=combos.get(attacker.getUniqueId());
                int hits=previous!=null && previous.victim().equals(victim.getUniqueId())
                        && now-previous.lastHit()
                            <=ti("combo-breaker.window-seconds",5)*1000L
                        ?previous.hits():0;
                combos.put(attacker.getUniqueId(),new Combo(victim.getUniqueId(),hits+1,now));
            }
            if(has(attacker,"concussive_blow") && available(attacker,"concussive")
                    &&apply(victim,PotionEffectType.MINING_FATIGUE,1,
                            ti("concussive-blow.duration-seconds",5)))
                start(attacker,"concussive",ti("concussive-blow.cooldown-seconds",45));

            if(has(attacker,"bloodlust") && attacker.getCooledAttackStrength(0)>=.9){
                int needed=Math.max(1,ti("bloodlust.hits-required",5));
                int count=Math.min(needed,bloodlust.getOrDefault(attacker.getUniqueId(),0)+1);
                if(count>=needed && available(attacker,"bloodlust_heal")
                        &&attacker.getHealth()<maxHealth(attacker)-0.01) {
                    heal(attacker,td("bloodlust.heal-hearts",1));
                    start(attacker,"bloodlust_heal",ti("bloodlust.cooldown-seconds",15));
                    count=0;
                }
                bloodlust.put(attacker.getUniqueId(),count);
            }
        }
        if(has(attacker,"battle_sense")){
            Sense previous=sensed.get(attacker.getUniqueId());
            long now=System.currentTimeMillis();
            if(previous==null || now>=previous.expires()
                    ||!previous.target().equals(victim.getUniqueId()))
                plugin.talentFeedback().passive(attacker,"battle_sense");
            sensed.put(attacker.getUniqueId(),new Sense(victim.getUniqueId(),
                    now+ti("battle-sense.duration-seconds",8)*1000L));
        }
        if(has(attacker,"scavenger") && available(attacker,"scavenger")
                &&ThreadLocalRandom.current().nextDouble()<td("scavenger.chance",.15)){
            int cap=plugin.getConfig().getInt("privileges.max-wind-charges",64)
                    +(has(attacker,"expanded_reserves")?ti("expanded-reserves.extra-items",64):0);
            int held=0,free=0;
            for(ItemStack item:attacker.getInventory().getStorageContents()) {
                if(item==null || item.getType().isAir())free+=64;
                else if(item.getType()==Material.WIND_CHARGE) {
                    held+=item.getAmount();
                    free+=Math.max(0,item.getMaxStackSize()-item.getAmount());
                }
            }
            ItemStack offhand=attacker.getInventory().getItemInOffHand();
            if(offhand.getType()==Material.WIND_CHARGE)held+=offhand.getAmount();
            // This chance does not spend its cooldown if the inventory is full,
            // or if the configured carrying limit has already been reached.
            int amount=Math.min(ti("scavenger.wind-charges",4),Math.min(
                    Math.max(0,cap-held),free));
            if(amount>0){
                Map<Integer,ItemStack> overflow=attacker.getInventory()
                        .addItem(new ItemStack(Material.WIND_CHARGE,amount));
                int delivered=amount-overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                if(delivered>0)start(attacker,"scavenger",
                        ti("scavenger.cooldown-seconds",90));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||e.getFinalDamage()<=0)return;
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL&&has(p,"acrobat")){
            double factor=Math.max(0,Math.min(1,td("acrobat.fall-damage-multiplier",.60)));
            if(factor<1){
                e.setDamage(e.getDamage()*factor);
                plugin.talentFeedback().passive(p,"acrobat");
            }
        }
        // Give the life-saving option priority when both could activate.
        if(has(p,"second_wind")
                &&p.getHealth()-e.getFinalDamage()
                        <=maxHealth(p)*td("second-wind.health-threshold-fraction",.30)
                &&defensiveAvailable(p,"second_wind")
                &&apply(p,PotionEffectType.ABSORPTION,0,ti("second-wind.duration-seconds",8))){
            startDefense(p,"second_wind",ti("second-wind.cooldown-seconds",90));
        }else if(has(p,"iron_will")
                &&e.getFinalDamage()>=td("iron-will.trigger-hearts",3)*2
                &&defensiveAvailable(p,"iron_will")
                &&apply(p,PotionEffectType.RESISTANCE,0,ti("iron-will.duration-seconds",5))){
            startDefense(p,"iron_will",ti("iron-will.cooldown-seconds",75));
        }
        // Escape Artist also triggers on movement into webs, without needing
        // to take damage. Hits can still trigger the Slowness-cleansing version.
        if(has(p,"escape_artist") && available(p,"escape_artist")
                && (p.hasPotionEffect(PotionEffectType.SLOWNESS)
                        || touchesWeb(p,p.getLocation())))
            queueEscape(p);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void noJump(PlayerJumpEvent e){
        if(System.currentTimeMillis()<jumpBlocked.getOrDefault(e.getPlayer().getUniqueId(),0L))
            e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void goldenApple(PlayerItemConsumeEvent e){
        Player p=e.getPlayer();
        if(e.getItem().getType()!=Material.GOLDEN_APPLE||!has(p,"golden_aegis")
                ||!available(p,"golden_aegis"))return;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!p.isOnline()||p.isDead()||!has(p,"golden_aegis")
                    ||!available(p,"golden_aegis"))return;
            double before=p.getAbsorptionAmount();
            double added=Math.max(0,ti("golden-aegis.extra-absorption-hearts",2)*2);
            // A normal Golden Apple grants two absorption hearts; Aegis adds
            // another two without repeatedly stacking above its intended cap.
            double after=Math.max(before,Math.min(4+added,before+added));
            if(after>before+0.01){
                p.setAbsorptionAmount(after);
                start(p,"golden_aegis",ti("golden-aegis.cooldown-seconds",45));
            }
        },2L);
    }

    /** Called only after StreakManager confirms a kill is anti-farm eligible. */
    public void onEligibleKill(Player killer){
        if(has(killer,"field_medic")&&available(killer,"field_medic")
                &&killer.getHealth()<maxHealth(killer)-.01){
            heal(killer,td("field-medic.heal-hearts",5));
            start(killer,"field_medic",ti("field-medic.cooldown-seconds",60));
        }
    }

    /** Does not protect Fractured, and never increases a streak above pre-death. */
    public int protectedDeath(Player victim,int oldStreak,int normalLoss){
        if(oldStreak<=0||normalLoss>=oldStreak||!has(victim,"streak_insurance")
                ||!available(victim,"streak_insurance"))return normalLoss;
        int protectedStreak=Math.min(oldStreak,normalLoss
                +Math.max(0,ti("streak-insurance.protected-levels",2)));
        if(protectedStreak<=normalLoss)return normalLoss;
        insuranceOnRespawn.put(victim.getUniqueId(),protectedStreak-normalLoss);
        start(victim,"streak_insurance",ti("streak-insurance.cooldown-seconds",600));
        return protectedStreak;
    }
    @EventHandler public void respawn(PlayerRespawnEvent e){
        Integer saved=insuranceOnRespawn.remove(e.getPlayer().getUniqueId());
        if(saved==null)return;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            Player p=e.getPlayer();
            if(!p.isOnline()||p.isDead())return;
            Text.send(p,plugin.prefix()+"&aStreak Insurance saved &f"+saved
                    +" &akillstreak levels! &7Check &b/spree cooldowns &7for its recharge.");
            plugin.talentFeedback().activated(p,"streak_insurance",
                    ti("streak-insurance.cooldown-seconds",600));
        },2L);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void wind(ProjectileHitEvent e){
        if(!(e.getEntity() instanceof WindCharge charge)
                ||!(charge.getShooter() instanceof Player p)
                ||!has(p,"windrunner"))return;
        if(!charge.getWorld().equals(p.getWorld())
                ||charge.getLocation().distanceSquared(p.getLocation())>16)return;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(p.isOnline()&&!p.isDead()&&has(p,"windrunner")){
                p.setVelocity(p.getVelocity().multiply(td("windrunner.velocity-multiplier",1.25)));
                plugin.talentFeedback().passive(p,"windrunner");
            }
        },1L);
    }

    /** Called from the main heartbeat after the HUD, for 8s of target health. */
    public void tick(){
        plugin.talentFeedback().tick();
        long now=System.currentTimeMillis();
        for(Player p:Bukkit.getOnlinePlayers()){
            // Catch webs placed around a stationary player as well as
            // first entering one (handled immediately by PlayerMoveEvent).
            if(!p.isDead() && has(p,"escape_artist")
                    && available(p,"escape_artist")
                    && touchesWeb(p,p.getLocation()))
                queueEscape(p);
            Sense sense=sensed.get(p.getUniqueId());
            if(sense==null||now>=sense.expires()||!has(p,"battle_sense")
                    ||plugin.talentFeedback().suppressBattleSense(p))continue;
            Player target=Bukkit.getPlayer(sense.target());
            if(target!=null&&target.isOnline()&&!target.isDead())
                Text.action(p,"&9&lBATTLE SENSE &7"+target.getName()+" &c"
                        +Math.round(target.getHealth()/2.0*10)/10.0+" hearts");
        }
    }

    public void registerRecipes(){
        Bukkit.removeRecipe(appleKey);
        Bukkit.removeRecipe(rodKey);
        ShapedRecipe apple=new ShapedRecipe(appleKey,new ItemStack(Material.GOLDEN_APPLE));
        apple.shape("GAG");
        apple.setIngredient('G',Material.GOLD_INGOT);
        apple.setIngredient('A',Material.APPLE);
        Bukkit.addRecipe(apple);
        ShapedRecipe rod=new ShapedRecipe(rodKey,new ItemStack(Material.BREEZE_ROD));
        rod.shape("III","ISI","III");
        rod.setIngredient('I',Material.IRON_NUGGET);
        rod.setIngredient('S',Material.STICK);
        Bukkit.addRecipe(rod);
    }
    private boolean blacksmithRecipe(org.bukkit.inventory.Recipe recipe){
        if(!(recipe instanceof Keyed keyed))return false;
        return keyed.getKey().equals(appleKey)||keyed.getKey().equals(rodKey);
    }
    @EventHandler public void prepared(PrepareItemCraftEvent e){
        if(!blacksmithRecipe(e.getRecipe()))return;
        if(!(e.getView().getPlayer() instanceof Player p)||!has(p,"blacksmith"))
            e.getInventory().setResult(new ItemStack(Material.AIR));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void crafted(CraftItemEvent e){
        if(!blacksmithRecipe(e.getRecipe()))return;
        if(!(e.getWhoClicked() instanceof Player p)||!has(p,"blacksmith")){
            e.setCancelled(true);
            if(e.getWhoClicked() instanceof Player p)
                Text.action(p,"&cBlacksmith Talent required.");
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void craftedFeedback(CraftItemEvent e) {
        if(blacksmithRecipe(e.getRecipe())&&e.getWhoClicked() instanceof Player p
                &&has(p,"blacksmith") &&e.getCurrentItem()!=null
                &&!e.getCurrentItem().getType().isAir())
            plugin.talentFeedback().passive(p,"blacksmith");
    }
}
