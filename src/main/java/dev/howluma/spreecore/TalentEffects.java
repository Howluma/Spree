package dev.howluma.spreecore;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import org.bukkit.Bukkit;
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
    private final SpreeCorePlugin plugin;
     private final Map<UUID,Combo> combos = new HashMap<>();
    private final Map<UUID,Integer> bloodlust = new HashMap<>();
    private final Map<UUID,Long> jumpBlocked = new HashMap<>();
    private final Map<UUID,Sense> sensed = new HashMap<>();
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

    /** Only a confirmed, unblocked mace-smash damage event starts the timer. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void confirmedSmash(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)
                || attacker.getInventory().getItemInMainHand().getType()!=Material.MACE
                || !plugin.progression().hasPrivilege(attacker,"mace_wielder"))
            return;
        boolean shield = e.getEntity() instanceof Player p && p.isBlocking();
        if (!MaceSmashMath.connects(DamageType.MACE_SMASH.equals(e.getDamageSource().getDamageType()),
                e.isCancelled(), e.getFinalDamage(), shield)) return;
        Map<String,Long> timers = plugin.data().get(attacker).talentCooldowns;
        long now=System.currentTimeMillis();
        // A single smash may cause multiple damage events; never renew the
        // cooldown for secondary impacts from the same smash.
        if (timers.getOrDefault("mace_smash",0L)>now) return;
        int base=plugin.getConfig().getInt("combat.mace.slam-cooldown-seconds",60);
        int mastery=plugin.getConfig().getInt("combat.mace.mastery-slam-cooldown-seconds",30);
        int seconds=Math.max(0,Math.min(3600,has(attacker,"mace_mastery")?mastery:base));
        if(seconds==0) return;
        timers.put("mace_smash",now+seconds*1000L);
        plugin.data().dirty();
        plugin.talentFeedback().activated(attacker,"mace_smash",seconds);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void combat(EntityDamageByEntityEvent e){
        if(e.getDamage()<=0)return;
        if(!(e.getEntity() instanceof Player victim))return;
        Player attacker=credited(e);
        if(attacker==null||attacker.equals(victim))return;
        // Any PvP hit interrupts the victim's own offensive combo.
        combos.remove(victim.getUniqueId());
        if(has(attacker,"executioner") && victim.getHealth()<
                maxHealth(victim)*td("executioner.health-threshold-fraction",.5))
            e.setDamage(e.getDamage()*(1+td("executioner.bonus-damage-fraction",.10)));

        if(e.getDamager() instanceof Player) {
            if(has(attacker,"combo_breaker")){
                long now=System.currentTimeMillis();
                Combo previous=combos.get(attacker.getUniqueId());
                int hits=previous!=null&&previous.victim().equals(victim.getUniqueId())
                        &&now-previous.lastHit()<=ti("combo-breaker.window-seconds",5)*1000L
                        ?previous.hits():0;
                if(hits>=ti("combo-breaker.hits-required",3))
                    e.setDamage(e.getDamage()*(1+td("combo-breaker.bonus-damage-fraction",.10)));
                combos.put(attacker.getUniqueId(),new Combo(victim.getUniqueId(),hits+1,now));
            }
            if(has(attacker,"concussive_blow")&&ready(attacker,"concussive",ti("concussive-blow.cooldown-seconds",45)))
                apply(victim,PotionEffectType.MINING_FATIGUE,1,ti("concussive-blow.duration-seconds",5));

            if(victim.isBlocking()) {
                if(has(victim,"riposte") && ready(victim,"riposte",ti("riposte.cooldown-seconds",45))){
                    apply(attacker,PotionEffectType.SLOWNESS,1,ti("riposte.duration-seconds",2));
                    jumpBlocked.put(attacker.getUniqueId(),System.currentTimeMillis()+ti("riposte.duration-seconds",2)*1000L);
                }
                if(has(attacker,"sunder")&&axe(attacker.getInventory().getItemInMainHand().getType())
                        &&ready(attacker,"sunder",ti("sunder.cooldown-seconds",45)))
                    Bukkit.getScheduler().runTask(plugin,()->{
                        if(victim.isOnline())victim.setCooldown(Material.SHIELD,
                                Math.max(100+ti("sunder.extra-shield-disable-ticks",30),victim.getCooldown(Material.SHIELD)+ti("sunder.extra-shield-disable-ticks",30)));
                    });
            }
            if(has(attacker,"bloodlust") && attacker.getCooledAttackStrength(0)>=.9){
                int hits=bloodlust.merge(attacker.getUniqueId(),1,Integer::sum);
                if(hits>=ti("bloodlust.hits-required",5)){
                    if(ready(attacker,"bloodlust_heal",ti("bloodlust.cooldown-seconds",15)))heal(attacker,td("bloodlust.heal-hearts",1));
                    bloodlust.put(attacker.getUniqueId(),0);
                }
            }
        }
        if(has(attacker,"battle_sense"))
            sensed.put(attacker.getUniqueId(),new Sense(victim.getUniqueId(),System.currentTimeMillis()+ti("battle-sense.duration-seconds",8)*1000L));
        if(has(attacker,"scavenger") && ThreadLocalRandom.current().nextDouble()<td("scavenger.chance",.15)
                && ready(attacker,"scavenger",ti("scavenger.cooldown-seconds",90))){
            int cap=plugin.getConfig().getInt("privileges.max-wind-charges",64)
                    +(has(attacker,"expanded_reserves")?ti("expanded-reserves.extra-items",64):0);
            int held=0;
            for(ItemStack item:attacker.getInventory().getContents())
                if(item!=null&&item.getType()==Material.WIND_CHARGE)held+=item.getAmount();
            int amount=Math.min(ti("scavenger.wind-charges",4),Math.max(0,cap-held));
            if(amount>0)attacker.getInventory().addItem(new ItemStack(Material.WIND_CHARGE,amount));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||e.getFinalDamage()<=0)return;
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL&&has(p,"acrobat"))
            e.setDamage(e.getDamage()*td("acrobat.fall-damage-multiplier",.60));
        // Give the life-saving option priority when both could activate.
        if(has(p,"second_wind") && p.getHealth()-e.getFinalDamage()<=maxHealth(p)*td("second-wind.health-threshold-fraction",.30)
                && defensiveReady(p,"second_wind",ti("second-wind.cooldown-seconds",90)))
            apply(p,PotionEffectType.ABSORPTION,0,ti("second-wind.duration-seconds",8));
        else if(has(p,"iron_will") && e.getFinalDamage()>=td("iron-will.trigger-hearts",3)*2
                && defensiveReady(p,"iron_will",ti("iron-will.cooldown-seconds",75)))
            apply(p,PotionEffectType.RESISTANCE,0,ti("iron-will.duration-seconds",5));
        if(has(p,"escape_artist") && (p.hasPotionEffect(PotionEffectType.SLOWNESS)
                ||p.getLocation().getBlock().getType()==Material.COBWEB)
                && ready(p,"escape_artist",ti("escape-artist.cooldown-seconds",50))){
            PotionEffect originalSlow=p.getPotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            apply(p,PotionEffectType.SPEED,2,3);
            // A short lift helps the player escape an actual cobweb without
            // breaking blocks or changing the underlying world.
            if(p.getLocation().getBlock().getType()==Material.COBWEB)
                p.setVelocity(p.getVelocity().add(new org.bukkit.util.Vector(0,.42,0)));
            if(originalSlow!=null && originalSlow.getDuration()>60)
                Bukkit.getScheduler().runTaskLater(plugin,()->{
                    if(p.isOnline()&&!p.isDead()&&!p.hasPotionEffect(PotionEffectType.SLOWNESS))
                        p.addPotionEffect(originalSlow.withDuration(originalSlow.getDuration()-60),true);
                },60L);
        }
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
                ||!ready(p,"golden_aegis",ti("golden-aegis.cooldown-seconds",45)))return;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(p.isOnline()&&!p.isDead()&&has(p,"golden_aegis"))
                p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(),
                        Math.min(4+ti("golden-aegis.extra-absorption-hearts",2)*2,
                                p.getAbsorptionAmount()+ti("golden-aegis.extra-absorption-hearts",2)*2)));
        },2L);
    }

    /** Called only after StreakManager confirms a kill is anti-farm eligible. */
    public void onEligibleKill(Player killer){
        if(has(killer,"field_medic")&&ready(killer,"field_medic",ti("field-medic.cooldown-seconds",60)))heal(killer,td("field-medic.heal-hearts",5));
    }

    /** Does not protect Fractured, and never increases a streak above pre-death. */
    public int protectedDeath(Player victim,int oldStreak,int normalLoss){
        if(oldStreak<=0||normalLoss>=oldStreak||!has(victim,"streak_insurance")
                ||!ready(victim,"streak_insurance",ti("streak-insurance.cooldown-seconds",600)))return normalLoss;
        return Math.min(oldStreak,normalLoss+ti("streak-insurance.protected-levels",2));
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void wind(ProjectileHitEvent e){
        if(!(e.getEntity() instanceof WindCharge charge)
                ||!(charge.getShooter() instanceof Player p)
                ||!has(p,"windrunner"))return;
        if(!charge.getWorld().equals(p.getWorld())
                ||charge.getLocation().distanceSquared(p.getLocation())>16)return;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(p.isOnline()&&!p.isDead()&&has(p,"windrunner"))
                p.setVelocity(p.getVelocity().multiply(td("windrunner.velocity-multiplier",1.25)));
        },1L);
    }

    /** Called from the main heartbeat after the HUD, for 8s of target health. */
    public void tick(){
        long now=System.currentTimeMillis();
        for(Player p:Bukkit.getOnlinePlayers()){
            Sense sense=sensed.get(p.getUniqueId());
            if(sense==null||now>=sense.expires()||!has(p,"battle_sense"))continue;
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
}
