package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import java.util.*;

/** Craftable, one-use, non-exploitable gateway to all four reroll types. */
public final class FateSigil implements Listener {
    private final SpreeCorePlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;
    private final Map<UUID, Integer> usedTick = new HashMap<>();

    FateSigil(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "fate_sigil");
        this.recipeKey = new NamespacedKey(plugin, "fate_sigil_recipe");
    }

    public ItemStack item() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD,
                Math.max(1,Math.min(16,plugin.getConfig().getInt("fate-sigil.recipe.result-amount",1))));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.c(plugin.getConfig().getString("fate-sigil.display-name","&9&lFate Sigil")));
        plugin.models().sigil(meta);
        meta.setLore(List.of(Text.c("&7Reroll an upgrade in any category"),
                Text.c("&7Privileges, Talents, Infusions, or all Perks"),
                Text.c("&aRight-click to choose what to reforge"),
                Text.c("&8Consumed when you confirm a reroll")));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSigil(ItemStack item) {
        return item != null && item.getType() == Material.ECHO_SHARD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public Material ingredient(int index) {
        if (index<1||index>9) throw new IllegalArgumentException("Cell must be 1..9");
        String fallback=switch(index){
            case 5 -> "HEART_OF_THE_SEA";
            case 1,3,7,9 -> "OMINOUS_TRIAL_KEY";
            default -> "NETHERITE_INGOT";
        };
        String raw=plugin.getConfig().getString("fate-sigil.recipe.slot-"+index,fallback);
        Material result=raw==null?null:Material.matchMaterial(raw);
        return result==null||result.isAir()||!result.isItem()?Material.AIR:result;
    }
    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        if(!plugin.getConfig().getBoolean("fate-sigil.recipe.enabled",true))return;
        char[] grid=new char[9];
        Map<Character,Material> ingredients=new LinkedHashMap<>();
        for(int i=0;i<9;i++){
            Material material=ingredient(i+1);
            if(material.isAir())grid[i]=' ';
            else {
                char letter=(char)('A'+i);
                grid[i]=letter;
                ingredients.put(letter,material);
            }
        }
        if(ingredients.isEmpty()){
            plugin.getLogger().warning("Empty Fate Sigil recipe; no crafting recipe registered.");
            return;
        }
        ShapedRecipe recipe=new ShapedRecipe(recipeKey,item());
        recipe.shape(new String(grid,0,3),new String(grid,3,3),new String(grid,6,3));
        ingredients.forEach(recipe::setIngredient);
        if(!Bukkit.addRecipe(recipe))plugin.getLogger().warning("Fate Sigil recipe registration failed.");
    }

    public boolean has(Player player) {
        for (ItemStack item : player.getInventory().getContents())
            if (isSigil(item)) return true;
        return false;
    }

    public boolean consume(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isSigil(item)) continue;
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else contents[i] = null;
            player.getInventory().setContents(contents);
            return true;
        }
        return false;
    }

    @EventHandler public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;
        Player player = event.getPlayer();
        ItemStack held = event.getHand() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (!isSigil(held)) return;
        event.setCancelled(true);
        int tick = player.getTicksLived();
        if (usedTick.getOrDefault(player.getUniqueId(), -1) == tick) return;
        usedTick.put(player.getUniqueId(), tick);
        plugin.progressMenus().openSigil(player);
    }
}
