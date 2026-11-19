package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import com.destroystokyo.paper.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;

public final class PrivilegeRules implements Listener {
    private final SpreeCorePlugin plugin;
    private final Map<UUID,Long> warnings = new HashMap<>();
    PrivilegeRules(SpreeCorePlugin plugin) {this.plugin=plugin;}
    private boolean owns(Player p,String key) {return plugin.progression().hasPrivilege(p,key);}
    private int setting(String key,int def) {return plugin.getConfig().getInt("privileges."+key,def);}
    private void warn(Player p,String message) {
        long now=System.currentTimeMillis();
        if(now-warnings.getOrDefault(p.getUniqueId(),0L)>2500) {
            Text.action(p,"&c"+message+" &7(/spree > Progression)");
            warnings.put(p.getUniqueId(),now);
        }
    }
    private boolean netherite(Material m) {
        return m==Material.NETHERITE_SWORD||m==Material.NETHERITE_AXE
                ||m==Material.NETHERITE_PICKAXE||m==Material.NETHERITE_HOE
                ||m==Material.NETHERITE_SHOVEL;
    }
    private boolean turtle(ItemStack stack) {
        return stack!=null && stack.hasItemMeta()
                && stack.getItemMeta() instanceof PotionMeta meta
                && meta.getBasePotionType()==PotionType.TURTLE_MASTER;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();ItemStack item=e.getItem();if(item==null)return;
        if(item.getType()==Material.TNT_MINECART&&!owns(p,"demolitionist")) {
            e.setCancelled(true);warn(p,"TNT minecart Privilege required.");return;
        }
        if(netherite(item.getType())&&!owns(p,"netherite_specialist")
                && (e.getAction()==Action.RIGHT_CLICK_BLOCK||e.getAction()==Action.RIGHT_CLICK_AIR)) {
            e.setCancelled(true);warn(p,"Netherite Specialist Privilege required.");return;
        }
        if((item.getType()==Material.SPLASH_POTION||item.getType()==Material.LINGERING_POTION)
                && turtle(item)&&!owns(p,"turtle_master")) {
            e.setCancelled(true);warn(p,"Turtle Master Privilege required.");
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void block(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(netherite(p.getInventory().getItemInMainHand().getType())
                &&!owns(p,"netherite_specialist")) {
            e.setCancelled(true);warn(p,"Netherite Specialist Privilege required.");
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void combat(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player p &&
                netherite(p.getInventory().getItemInMainHand().getType())
                &&!owns(p,"netherite_specialist")) {
            e.setCancelled(true);warn(p,"Netherite Specialist Privilege required.");
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void throwTurtle(ProjectileLaunchEvent e) {
        if(e.getEntity() instanceof ThrownPotion potion
                && potion.getShooter() instanceof Player p
                && turtle(potion.getItem())&&!owns(p,"turtle_master")) {
            e.setCancelled(true);warn(p,"Turtle Master Privilege required.");
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void resurrect(EntityResurrectEvent e) {
        if(e.getEntity() instanceof Player p&&!owns(p,"last_stand")) {
            e.setCancelled(true);warn(p,"Last Stand Privilege required for totems.");
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void consume(PlayerItemConsumeEvent e) {
        Player p=e.getPlayer();ItemStack item=e.getItem();
        if(turtle(item)&&!owns(p,"turtle_master")) {
            e.setCancelled(true);warn(p,"Turtle Master Privilege required.");return;
        }
        if(item.getType()!=Material.ENCHANTED_GOLDEN_APPLE)return;
        if(!owns(p,"forbidden_fruit")) {
            e.setCancelled(true);warn(p,"Forbidden Fruit Privilege required.");return;
        }
        if(p.hasCooldown(Material.ENCHANTED_GOLDEN_APPLE)
                ||System.currentTimeMillis()<plugin.data().get(p).talentCooldowns.getOrDefault("enchanted_apple",0L)) {
            e.setCancelled(true);warn(p,"Enchanted golden apple is cooling down.");return;
        }
        int seconds=Math.max(1,setting("enchanted-apple-cooldown-seconds",30));
        p.setCooldown(Material.ENCHANTED_GOLDEN_APPLE,seconds*20);
        plugin.data().get(p).talentCooldowns.put("enchanted_apple",
                System.currentTimeMillis()+seconds*1000L);
        plugin.data().dirty();
        int maxDuration=Math.max(1,setting("enchanted-apple-max-effect-seconds",60))*20;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!p.isOnline()||p.isDead())return;
            for(PotionEffectType type:List.of(PotionEffectType.REGENERATION,
                    PotionEffectType.RESISTANCE,PotionEffectType.FIRE_RESISTANCE,PotionEffectType.ABSORPTION)) {
                PotionEffect current=p.getPotionEffect(type);
                if(current!=null&&!current.isInfinite()&&current.getDuration()>maxDuration)
                    p.addPotionEffect(current.withDuration(maxDuration),true);
            }
        },2L);
    }
    private void cap(Player p,Material material,int max) {
        int left=Math.max(0,max);
        ItemStack[] contents=p.getInventory().getContents();
        List<ItemStack> overflow=new ArrayList<>();boolean changed=false;
        for(int slot=0;slot<contents.length;slot++) {
            ItemStack item=contents[slot];
            if(item==null||item.getType()!=material)continue;
            int keep=Math.min(left,item.getAmount());
            left-=keep;
            if(keep>=item.getAmount())continue;
            ItemStack spare=item.clone();spare.setAmount(item.getAmount()-keep);
            overflow.add(spare);
            if(keep<=0)contents[slot]=null;
            else {ItemStack smaller=item.clone();smaller.setAmount(keep);contents[slot]=smaller;}
            changed=true;
        }
        if(!changed)return;
        p.getInventory().setContents(contents);
        for(ItemStack item:overflow)for(int n:CapacityMath.split(item.getAmount(),item.getMaxStackSize())) {
            ItemStack part=item.clone();part.setAmount(n);
            p.getWorld().dropItemNaturally(p.getLocation(),part);
        }
        warn(p,material.name().replace('_',' ')+" carry limit; excess dropped.");
    }
    public void enforce(Player p) {
        if(!p.isOnline()||p.isDead())return;
        cap(p,Material.TOTEM_OF_UNDYING,owns(p,"last_stand")?setting("max-totems",1):0);
        cap(p,Material.ENCHANTED_GOLDEN_APPLE,owns(p,"forbidden_fruit")?setting("max-enchanted-apples",4):0);
        int extra=plugin.progression().hasTalent(p,"expanded_reserves")?64:0;
        cap(p,Material.WIND_CHARGE,setting("max-wind-charges",64)+extra);
        cap(p,Material.EXPERIENCE_BOTTLE,setting("max-experience-bottles",64)+extra);
    }
    private int itemLimit(Player p, Material type) {
        if(type==Material.TOTEM_OF_UNDYING)
            return owns(p,"last_stand")?setting("max-totems",1):0;
        if(type==Material.ENCHANTED_GOLDEN_APPLE)
            return owns(p,"forbidden_fruit")?setting("max-enchanted-apples",4):0;
        int extra=plugin.progression().hasTalent(p,"expanded_reserves")?64:0;
        if(type==Material.WIND_CHARGE)return setting("max-wind-charges",64)+extra;
        if(type==Material.EXPERIENCE_BOTTLE)return setting("max-experience-bottles",64)+extra;
        return -1;
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void attemptPickup(PlayerAttemptPickupItemEvent e) {
        Player p=e.getPlayer();ItemStack stack=e.getItem().getItemStack();
        int cap=itemLimit(p,stack.getType());
        if(cap<0)return;
        int held=0;
        for(ItemStack existing:p.getInventory().getContents())
            if(existing!=null&&existing.getType()==stack.getType())held+=existing.getAmount();
        int room=Math.max(0,cap-held);
        if(room>=stack.getAmount())return; // ordinary or Capacity-managed pickup.
        e.setCancelled(true);
        if(room<=0)return;
        ItemStack portion=stack.clone();
        portion.setAmount(room);
        Map<Integer,ItemStack> leftovers=p.getInventory().addItem(portion);
        int notTaken=leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int taken=room-notTaken;
        if(taken<=0)return;
        if(taken>=stack.getAmount())e.getItem().remove();
        else {
            ItemStack remaining=stack.clone();
            remaining.setAmount(stack.getAmount()-taken);
            e.getItem().setItemStack(remaining);
        }
    }
    public void tick(){for(Player p:Bukkit.getOnlinePlayers())enforce(p);}
    @EventHandler public void join(PlayerJoinEvent e){
        Bukkit.getScheduler().runTaskLater(plugin,()->enforce(e.getPlayer()),10L);
    }
}
