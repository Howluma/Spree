package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Netherite Specialist is a reversible privilege, NOT a smithing recipe.
 * Only tagged, temporarily promoted diamond tools turn back into diamond
 * when they leave a player inventory. Genuine netherite is never changed.
 */
public final class SpecialistItems implements Listener {
    private static final Map<Material, Material> PROMOTE = new EnumMap<>(Material.class);
    private static final Map<Material, Material> DEMOTE = new EnumMap<>(Material.class);
    static {
        map(Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
        map(Material.DIAMOND_AXE, Material.NETHERITE_AXE);
        map(Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
        map(Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL);
        map(Material.DIAMOND_HOE, Material.NETHERITE_HOE);
        map(Material.DIAMOND_SPEAR, Material.NETHERITE_SPEAR);
    }
    private static void map(Material diamond, Material netherite) {
        PROMOTE.put(diamond, netherite);
        DEMOTE.put(netherite, diamond);
    }

    private final SpreeCorePlugin plugin;
    private final NamespacedKey marker;
    SpecialistItems(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "specialist_temporary_netherite");
    }

    static Material promoted(Material type) { return PROMOTE.get(type); }
    static Material original(Material type) { return DEMOTE.get(type); }

    public boolean isTemporary(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && DEMOTE.containsKey(stack.getType())
                && stack.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    public boolean matchesLegendary(ItemStack item, LegendaryType legendary) {
        return legendary != null && (legendary.material == item.getType()
                || (isTemporary(item) && legendary.material == original(item.getType())));
    }

    /** Return the input unchanged when no transformation is necessary. */
    private ItemStack normalize(ItemStack item, boolean privileged) {
        if (item == null || item.getType().isAir()) return item;
        Material target = privileged ? promoted(item.getType()) : original(item.getType());
        if (target == null || (!privileged && !isTemporary(item))) return item;

        ItemStack result = item.clone();
        result.setType(target);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return item;
        if (privileged) meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        else meta.getPersistentDataContainer().remove(marker);
        result.setItemMeta(meta);
        return result;
    }

    private void sync(Player player, boolean eligible) {
        if (player.isDead()) return;
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack updated = normalize(contents[slot], eligible);
            if (updated != contents[slot]) {
                contents[slot] = updated;
                changed = true;
            }
        }
        if (changed) inventory.setContents(contents);
    }

    public void sync(Player player) {
        sync(player, plugin.progression().hasPrivilege(player, "netherite_specialist"));
    }

    private void revertPlayer(Player player) {
        // No temporary netherite may be persisted on logout or plugin unload.
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack next = normalize(contents[i], false);
            if (next != contents[i]) { contents[i] = next; changed = true; }
        }
        if (changed) player.getInventory().setContents(contents);
        ItemStack cursor = player.getItemOnCursor();
        ItemStack plain = normalize(cursor, false);
        if (cursor != plain) player.setItemOnCursor(plain);
    }

    private void revertExternal(Inventory inventory) {
        if (inventory == null || inventory instanceof org.bukkit.inventory.PlayerInventory) return;
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack next = normalize(contents[i], false);
            if (next != contents[i]) { contents[i] = next; changed = true; }
        }
        if (changed) inventory.setContents(contents);
    }

    private void afterTransfer(Player player, Inventory external) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            revertExternal(external);
            ItemStack cursor = player.getItemOnCursor();
            ItemStack plain = normalize(cursor, false);
            if (cursor != plain) player.setItemOnCursor(plain);
            sync(player);
        });
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) sync(player);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) revertPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        ItemStack plain = normalize(stack, false);
        if (plain != stack) event.getItemDrop().setItemStack(plain);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        for (int i = 0; i < event.getDrops().size(); i++) {
            ItemStack stack = event.getDrops().get(i);
            ItemStack plain = normalize(stack, false);
            if (plain != stack) event.getDrops().set(i, plain);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player)
            afterTransfer(player, event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player)
            afterTransfer(player, event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            revertExternal(event.getInventory());
            sync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player)
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) sync(player); });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        // If another plugin injects a temporary tool into a container, never
        // let automation move the promoted version out into the world.
        if (!isTemporary(event.getItem())) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> revertExternal(event.getSource()));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) sync(event.getPlayer());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { revertPlayer(event.getPlayer()); }
}
