package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Vanilla-compatible per-player extra capacity for naturally stackable items.
 * Every Spree-managed MAX_STACK_SIZE item component is removed on transfer
 * outside the player's inventory (with any overflow returned or dropped).
 */
public final class CapacityPerk implements Listener {
    private final SpreeCorePlugin plugin;
    private final NamespacedKey managedKey;
    private final Map<UUID, Set<Inventory>> pending = new HashMap<>();

    CapacityPerk(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        this.managedKey = new NamespacedKey(plugin, "capacity_managed");
    }

    private int bonus(Player player) {
        return CapacityMath.bonus(plugin.perks().bonus(player, PerkType.CAPACITY));
    }

    private static boolean isItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private boolean managed(ItemStack item) {
        return isItem(item) && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                    .has(managedKey, PersistentDataType.BYTE);
    }

    private ItemStack adjusted(ItemStack item, int bonus) {
        if (!isItem(item) || item.getType().getMaxStackSize() <= 1) return null;
        boolean ours = managed(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null || (meta.hasMaxStackSize() && !ours)) return null;
        int desired = CapacityMath.limit(item.getType().getMaxStackSize(), bonus);
        if (!ours && bonus == 0) return null;
        if (ours && bonus > 0 && meta.getMaxStackSize() == desired) return null;
        ItemStack result = item.clone();
        ItemMeta updated = result.getItemMeta();
        if (bonus == 0) {
            updated.setMaxStackSize(null);
            updated.getPersistentDataContainer().remove(managedKey);
        } else {
            updated.setMaxStackSize(desired);
            updated.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
        }
        result.setItemMeta(updated);
        return result;
    }

    private ItemStack original(ItemStack item) {
        ItemStack reverted = adjusted(item, 0);
        return reverted == null ? item.clone() : reverted;
    }

    private void giveOrDrop(Player player, ItemStack spare) {
        if (!isItem(spare)) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(spare);
        for (ItemStack left : leftovers.values())
            for (int n : CapacityMath.split(left.getAmount(), left.getMaxStackSize())) {
                ItemStack one = left.clone(); one.setAmount(n);
                player.getWorld().dropItemNaturally(player.getLocation(), one);
            }
    }

    /** Upgrade/remove a player's carried item components and safely split excess. */
    public void refresh(Player player) {
        if (!player.isOnline() || player.isDead()) return;
        PlayerInventory inventory = player.getInventory();
        int extra = bonus(player);
        int newMax = CapacityMath.limit(64, extra);
        if (inventory.getMaxStackSize() != newMax) inventory.setMaxStackSize(newMax);
        ItemStack[] contents = inventory.getStorageContents();
        List<ItemStack> overflow = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack updated = adjusted(contents[i], extra);
            if (updated == null) continue;
            int max = updated.getMaxStackSize();
            if (updated.getAmount() > max) {
                int remain = updated.getAmount() - max;
                updated.setAmount(max);
                ItemStack spare = updated.clone(); spare.setAmount(remain);
                overflow.add(spare);
            }
            contents[i] = updated;
            changed = true;
        }
        if (changed) inventory.setStorageContents(contents);
        for (ItemStack spare : overflow) giveOrDrop(player, spare);
    }

    private void cleanContainer(Inventory inventory, Player player) {
        if (inventory == null || inventory instanceof PlayerInventory) return;
        ItemStack[] contents = inventory.getStorageContents();
        List<ItemStack> overflow = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!managed(stack)) continue;
            ItemStack standard = original(stack);
            int max = standard.getMaxStackSize();
            if (standard.getAmount() > max) {
                int remaining = standard.getAmount() - max;
                standard.setAmount(max);
                ItemStack extra = standard.clone(); extra.setAmount(remaining);
                overflow.add(extra);
            }
            contents[i] = standard;
            changed = true;
        }
        if (changed) inventory.setStorageContents(contents);
        for (ItemStack item : overflow) {
            Map<Integer,ItemStack> left = inventory.addItem(item);
            for (ItemStack remaining : left.values()) giveOrDrop(player, remaining);
        }
    }

    private void schedule(Player player, Inventory outside) {
        UUID id = player.getUniqueId();
        Set<Inventory> targets = pending.get(id);
        if (targets != null) {
            if (outside != null) targets.add(outside);
            return;
        }
        targets = Collections.newSetFromMap(new IdentityHashMap<>());
        if (outside != null) targets.add(outside);
        pending.put(id, targets);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Set<Inventory> queued = pending.remove(id);
            if (!player.isOnline() || player.isDead()) return;
            if (queued != null) for (Inventory external : queued)
                cleanContainer(external, player);
            refresh(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void clicked(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player)
            schedule(player, event.getView().getTopInventory());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dragged(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player)
            schedule(player, event.getView().getTopInventory());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void closed(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player)
            schedule(player, event.getInventory());
    }

    /** Vanilla pickup cannot combine items with and without MAX_STACK_SIZE. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickedUp(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        Item entity = event.getItem();
        ItemStack ground = entity.getItemStack();
        int added = bonus(player);
        ItemStack boosted = adjusted(ground, added);
        if (boosted == null) return;
        int originalAmount = ground.getAmount();
        player.getInventory().setMaxStackSize(CapacityMath.limit(64, added));
        Map<Integer,ItemStack> leftovers = player.getInventory().addItem(boosted);
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        event.setCancelled(true);
        if (remaining >= originalAmount) return;
        if (remaining == 0) entity.remove();
        else {
            ItemStack rest = ground.clone(); rest.setAmount(remaining);
            entity.setItemStack(rest);
        }
        schedule(player, null);
    }

    private void normalizeDrop(Item entity) {
        ItemStack stack = entity.getItemStack();
        if (!managed(stack)) return;
        ItemStack original = original(stack);
        int[] parts = CapacityMath.split(original.getAmount(), original.getMaxStackSize());
        if (parts.length == 0) { entity.remove(); return; }
        original.setAmount(parts[0]);
        entity.setItemStack(original);
        for (int i = 1; i < parts.length; i++) {
            ItemStack next = original.clone(); next.setAmount(parts[i]);
            entity.getWorld().dropItemNaturally(entity.getLocation(), next);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dropped(PlayerDropItemEvent event) {
        normalizeDrop(event.getItemDrop());
        schedule(event.getPlayer(), null);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void died(PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;
        List<ItemStack> normalized = new ArrayList<>();
        for (ItemStack stack : event.getDrops()) {
            if (!managed(stack)) { normalized.add(stack); continue; }
            ItemStack vanilla = original(stack);
            for (int n : CapacityMath.split(vanilla.getAmount(), vanilla.getMaxStackSize())) {
                ItemStack part = vanilla.clone(); part.setAmount(n);
                normalized.add(part);
            }
        }
        event.getDrops().clear();
        event.getDrops().addAll(normalized);
    }
    @EventHandler public void joined(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer()), 5L);
    }
    @EventHandler public void respawned(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer()), 3L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
    public void shutdown() { pending.clear(); }
}