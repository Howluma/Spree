package dev.howluma.spreecore;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Quick Hands applies only to a player's ordinary consumables (food and drink
 * potions), not unrelated items, chests, drops, or third-party consumable data.
 * The original duration is persisted as PDC and restored whenever it leaves
 * the player's carried inventory or the Talent deactivates.
 */
public final class QuickHands implements Listener {
    private final SpreeCorePlugin plugin;
    private final NamespacedKey originalSeconds;
    private final Map<UUID,Set<Inventory>> pending=new HashMap<>();

    QuickHands(SpreeCorePlugin plugin) {
        this.plugin=plugin;
        originalSeconds=new NamespacedKey(plugin,"quick_hands_original_seconds");
    }
    private boolean marked(ItemStack item) {
        return item!=null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                    .has(originalSeconds,PersistentDataType.FLOAT);
    }
    private boolean eligible(ItemStack item) {
        if(item==null||item.getType().isAir()||!item.hasData(DataComponentTypes.CONSUMABLE))return false;
        Material type=item.getType();
        boolean drink=type==Material.POTION||type==Material.HONEY_BOTTLE;
        if(!type.isEdible()&&!drink)return false;
        // Respect other plugins' explicit consume-duration overrides.
        return marked(item)||!item.isDataOverridden(DataComponentTypes.CONSUMABLE);
    }
    private ItemStack modify(ItemStack original, boolean active) {
        if(original==null||original.getType().isAir())return null;
        boolean ours=marked(original);
        if(!ours&&(!active||!eligible(original)))return null;
        Consumable component=original.getData(DataComponentTypes.CONSUMABLE);
        if(component==null)return null;
        ItemMeta meta=original.getItemMeta();
        if(meta==null)return null;
        Float stored=meta.getPersistentDataContainer().get(originalSeconds,PersistentDataType.FLOAT);
        float originalTime=stored==null?component.consumeSeconds():stored;
        if(active&&ours &&
            Math.abs(component.consumeSeconds()-Math.max(.05f,originalTime*
                (float)plugin.getConfig().getDouble("talents.quick-hands.consume-time-multiplier",.8)))<.001f)return null;
        ItemStack changed=original.clone();
        ItemMeta update=changed.getItemMeta();
        if(active) update.getPersistentDataContainer().set(originalSeconds,PersistentDataType.FLOAT,originalTime);
        else update.getPersistentDataContainer().remove(originalSeconds);
        changed.setItemMeta(update);
        changed.setData(DataComponentTypes.CONSUMABLE,component.toBuilder()
                .consumeSeconds(active?Math.max(.05f,originalTime*(float)plugin.getConfig().getDouble("talents.quick-hands.consume-time-multiplier",.8)):originalTime).build());
        return changed;
    }
    private void restoreContainer(Inventory inv) {
        if(inv==null||inv instanceof PlayerInventory)return;
        ItemStack[] items=inv.getStorageContents();
        boolean changed=false;
        for(int i=0;i<items.length;i++){
            ItemStack revised=modify(items[i],false);
            if(revised==null)continue;
            items[i]=revised;changed=true;
        }
        if(changed)inv.setStorageContents(items);
    }
    public void refresh(Player player) {
        if(!player.isOnline()||player.isDead())return;
        boolean active=plugin.progression().hasTalent(player,"quick_hands");
        PlayerInventory inv=player.getInventory();
        ItemStack[] items=inv.getStorageContents();
        boolean changed=false;
        for(int i=0;i<items.length;i++){
            ItemStack revised=modify(items[i],active);
            if(revised==null)continue;
            items[i]=revised;changed=true;
        }
        if(changed)inv.setStorageContents(items);
        ItemStack off=modify(inv.getItemInOffHand(),active);
        if(off!=null)inv.setItemInOffHand(off);
    }
    private void cleanCarried(Player player) {
        PlayerInventory inv=player.getInventory();
        ItemStack[] items=inv.getStorageContents();
        boolean changed=false;
        for(int i=0;i<items.length;i++){
            ItemStack revised=modify(items[i],false);
            if(revised==null)continue;
            items[i]=revised;changed=true;
        }
        if(changed)inv.setStorageContents(items);
        ItemStack off=modify(inv.getItemInOffHand(),false);
        if(off!=null)inv.setItemInOffHand(off);
    }
    private void schedule(Player player, Inventory outside) {
        UUID id=player.getUniqueId();
        Set<Inventory> existing=pending.get(id);
        if(existing!=null){if(outside!=null)existing.add(outside);return;}
        Set<Inventory> targets=Collections.newSetFromMap(new IdentityHashMap<>());
        if(outside!=null)targets.add(outside);
        pending.put(id,targets);
        Bukkit.getScheduler().runTask(plugin,()->{
            Set<Inventory> queued=pending.remove(id);
            if(queued!=null)for(Inventory inv:queued)restoreContainer(inv);
            if(player.isOnline()&&!player.isDead())refresh(player);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        if(e.getWhoClicked() instanceof Player p)schedule(p,e.getView().getTopInventory());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        if(e.getWhoClicked() instanceof Player p)schedule(p,e.getView().getTopInventory());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void close(InventoryCloseEvent e) {
        if(e.getPlayer() instanceof Player p)schedule(p,e.getInventory());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drop(PlayerDropItemEvent e) {
        ItemStack revised=modify(e.getItemDrop().getItemStack(),false);
        if(revised!=null)e.getItemDrop().setItemStack(revised);
        schedule(e.getPlayer(),null);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent e) {
        if(e.getKeepInventory())return;
        for(int i=0;i<e.getDrops().size();i++) {
            ItemStack revised=modify(e.getDrops().get(i),false);
            if(revised!=null)e.getDrops().set(i,revised);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void consumed(PlayerItemConsumeEvent e) {
        if(plugin.progression().hasTalent(e.getPlayer(),"quick_hands")
                &&marked(e.getItem()))
            plugin.talentFeedback().passive(e.getPlayer(),"quick_hands");
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        cleanCarried(e.getPlayer());
    }
    public void shutdown() {
        pending.clear();
        for(Player p:Bukkit.getOnlinePlayers())cleanCarried(p);
    }
}
