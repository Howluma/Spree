package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Private symmetrical GUI: direct numerical entry, live mace balancing,
 * and an in-game 3x3 editor for the craftable Fate Sigil.
 */
public final class AdminBalanceMenu implements Listener {
    private enum Screen { HOME, LIMITS, MACE, RECIPE }
    private record Editing(String path, Screen back) {}
    private static final int[] RECIPE_SLOTS={10,11,12,19,20,21,28,29,30};
    private static final int[] LIMIT_SLOTS={10,12,14,19,21,23};
    private static final String[] LIMIT_PATHS={
        "privileges.max-totems",
        "privileges.max-enchanted-apples",
        "privileges.max-wind-charges",
        "privileges.max-experience-bottles",
        "privileges.enchanted-apple-cooldown-seconds",
        "privileges.enchanted-apple-max-effect-seconds"
    };
    private static final Material[] LIMIT_ICONS={
        Material.TOTEM_OF_UNDYING,Material.ENCHANTED_GOLDEN_APPLE,Material.WIND_CHARGE,
        Material.EXPERIENCE_BOTTLE,Material.CLOCK,Material.GOLDEN_APPLE
    };
    private static final String[] LIMIT_LABELS={
        "Totem Carry Limit","Enchanted Apple Carry Limit","Wind Charge Carry Limit",
        "XP Bottle Carry Limit","Enchanted Apple Cooldown","Enchanted Apple Effect Cap"
    };
    private static final String[] MACE_PATHS={
        "combat.mace.slam-cooldown-seconds",
        "combat.mace.mastery-slam-cooldown-seconds",
        "combat.mace.player-damage-multiplier",
        "combat.mace.enabled"
    };
    private static final String[] MACE_LABELS={
        "Normal Slam Cooldown","Mace Mastery Slam Cooldown","Mace PvP Damage Multiplier",
        "Enable Mace Combat Adjustment"
    };
    private static final Material[] MACE_ICONS={
        Material.MACE,Material.ENCHANTED_BOOK,Material.NETHERITE_AXE,Material.LIME_DYE
    };

    private static class Holder implements InventoryHolder {
        final Screen screen;
        Inventory inv;
        Holder(Screen screen) {this.screen=screen;}
        @Override public Inventory getInventory(){return inv;}
    }
    private final SpreeCorePlugin plugin;
    private final Map<UUID,Editing> pending=new ConcurrentHashMap<>();
    AdminBalanceMenu(SpreeCorePlugin plugin){this.plugin=plugin;}

    private ItemStack icon(Material material,String name,String... lore){
        ItemStack item=new ItemStack(material);
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(Text.c(name));
        meta.setLore(Arrays.stream(lore).map(Text::c).toList());
        item.setItemMeta(meta);
        return item;
    }
    private Inventory create(Holder holder,int rows,String title){
        Inventory inv=Bukkit.createInventory(holder,rows*9,Text.c(title));
        holder.inv=inv;
        for(int slot=0;slot<inv.getSize();slot++){
            int x=slot%9;
            inv.setItem(slot,icon(x==0||x==8?Material.BLUE_STAINED_GLASS_PANE:
                    Material.BLACK_STAINED_GLASS_PANE,"&8"));
        }
        return inv;
    }
    private String value(String path){
        var item=plugin.settings().find(path);
        return item==null?"N/A":item.current(plugin.getConfig());
    }
    private void footer(Inventory inv,int back,int exit){
        inv.setItem(back,icon(Material.ARROW,"&9&lBack","&7Return to balance workshop"));
        inv.setItem(exit,icon(Material.BARRIER,"&cClose"));
    }
    public void open(Player player){
        if(!player.hasPermission("spree.admin"))return;
        Holder h=new Holder(Screen.HOME);
        Inventory inv=create(h,3,"&9&lSPREE &8| &fBalance Workshop");
        inv.setItem(4,icon(Material.COMPARATOR,"&9&lGameplay Workshop",
                "&7Exact inputs, not repeated +/- clicking",
                "&7Changes save immediately to config.yml"));
        inv.setItem(10,icon(Material.TOTEM_OF_UNDYING,"&9&lInventory Item Limits",
                "&7Totems, enchanted apples, wind charges,",
                "&7XP bottles, apple cooldowns and durations",
                "&eClick to edit precise amounts"));
        inv.setItem(12,icon(Material.MACE,"&9&lMace Slams",
                "&7Set normal and Mastery cooldowns",
                "&7Only connected slam damage spends the timer",
                "&eClick to customize"));
        inv.setItem(14,icon(Material.ECHO_SHARD,"&9&lFate Sigil Crafting",
                "&7Edit all nine crafting-grid ingredients",
                "&7Change recipe output and enable/disable",
                "&eClick to open visual recipe editor"));
        inv.setItem(16,icon(Material.COMPARATOR,"&a&lAll Config Categories",
                "&7Every editable Perk, hunt, combat,",
                "&7and progression setting in one place",
                "&eClick to browse"));
        footer(inv,18,26);
        player.openInventory(inv);
    }
    private void limits(Player player){
        Holder h=new Holder(Screen.LIMITS);
        Inventory inv=create(h,4,"&9&lSPREE &8| &fItem Limits");
        inv.setItem(4,icon(Material.HOPPER,"&9&lFunctional Item Limits",
                "&7Limits are per player, across all inventory slots",
                "&7Left click each amount and type an exact number",
                "&7The Expanded Reserves Talent grants an extra",
                "&7stack of wind charges and XP bottles"));
        for(int i=0;i<LIMIT_SLOTS.length;i++){
            String path=LIMIT_PATHS[i];
            inv.setItem(LIMIT_SLOTS[i],icon(LIMIT_ICONS[i],"&9&l"+LIMIT_LABELS[i],
                    "&7Current: &f"+value(path),
                    i<4?"&7Maximum number carried":"&7In seconds",
                    "&eLeft: Enter exact amount in chat",
                    "&cRight: Restore server default"));
        }
        inv.setItem(31,icon(Material.BOOK,"&7Changes apply to all online players",
                "&7No server restart or JAR rebuild needed."));
        footer(inv,27,35);
        player.openInventory(inv);
    }
    private void mace(Player player){
        Holder h=new Holder(Screen.MACE);
        Inventory inv=create(h,3,"&9&lSPREE &8| &fMace Slam Settings");
        inv.setItem(4,icon(Material.MACE,"&9&lSuccessful Smash Only",
                "&7Missed mace hits, normal swings, blocked hits,",
                "&7zero-damage and canceled hits DO NOT",
                "&7start the smash cooldown"));
        int[] slots={10,12,14,16};
        for(int i=0;i<slots.length;i++){
            String path=MACE_PATHS[i];
            inv.setItem(slots[i],icon(MACE_ICONS[i],"&9&l"+MACE_LABELS[i],
                    "&7Current: &f"+value(path),
                    i==0||i==1?"&7Cooldown duration in seconds":
                            i==2?"&7Use a fraction (0.75 = 75%)":"&7Combat multiplier enabled",
                    "&eLeft: "+(i==3?"Toggle":"Set exact value in chat"),
                    "&cRight: Restore default"));
        }
        footer(inv,18,26);
        player.openInventory(inv);
    }
    private void recipe(Player player){
        Holder h=new Holder(Screen.RECIPE);
        Inventory inv=create(h,5,"&9&lSPREE &8| &fFate Sigil Recipe");
        inv.setItem(4,icon(Material.CRAFTING_TABLE,"&9&lEditable 3x3 Grid",
                "&7Pick up any item onto your cursor below,",
                "&7then left click a recipe cell to set it",
                "&7Left with empty cursor: type item name",
                "&7Right click: empty the ingredient slot",
                "&7Shift+right: reset cell to default"));
        for(int i=0;i<9;i++){
            Material material=plugin.fate().ingredient(i+1);
            ItemStack button=icon(material.isAir()?Material.GRAY_STAINED_GLASS_PANE:material,
                    "&9&lIngredient "+(i+1),
                    "&7Current: &f"+material.name(),
                    "&eLeft: set from cursor or type",
                    "&cRight: Clear to AIR",
                    "&bShift+right: Default ingredient");
            inv.setItem(RECIPE_SLOTS[i],button);
        }
        inv.setItem(22,icon(Material.ARROW,"&7Crafts Into"));
        ItemStack result=plugin.fate().item();
        ItemMeta meta=result.getItemMeta();
        meta.setLore(List.of(Text.c("&7A Fate Sigil (one reroll per use)"),
                Text.c("&7Preview of the configured result"),
                Text.c("&7Normal Echo Shards stay unchanged")));
        result.setItemMeta(meta);
        inv.setItem(24,result);
        inv.setItem(37,icon(Material.NAME_TAG,"&9&lDisplay Name",
                "&7Current: &f"+plugin.getConfig().getString("fate-sigil.display-name"),
                "&eClick to edit text and colors"));
        inv.setItem(39,icon(Material.HOPPER,"&9&lCraft Output Count",
                "&7Current: &f"+value("fate-sigil.recipe.result-amount"),
                "&eLeft: enter count (1-16)","&cRight: restore 1"));
        boolean on=plugin.getConfig().getBoolean("fate-sigil.recipe.enabled",true);
        inv.setItem(41,icon(on?Material.LIME_DYE:Material.GRAY_DYE,"&9&lRecipe: "+(on?"&aEnabled":"&cDisabled"),
                "&eLeft: toggle crafting availability"));
        inv.setItem(43,icon(Material.COMPARATOR,"&a&lAdvanced Recipe Settings",
                "&7All 9 material names, count, name and toggle",
                "&eOpen the full config editor"));
        footer(inv,36,44);
        player.openInventory(inv);
    }
    private void show(Player player, Screen screen){
        switch(screen){
            case HOME -> open(player);
            case LIMITS -> limits(player);
            case MACE -> mace(player);
            case RECIPE -> recipe(player);
        }
    }
    private void ask(Player player,String path,Screen screen){
        pending.put(player.getUniqueId(),new Editing(path,screen));
        player.closeInventory();
        Text.send(player,plugin.prefix()+"&9Editing &f"+path+"&9.");
        Text.send(player,"&7Current: &f"+value(path));
        if(path.startsWith("fate-sigil.recipe.slot-"))
            Text.send(player,"&7Type an item ID (DIAMOND, NETHERITE_SCRAP, etc.), or AIR to clear.");
        else Text.send(player,"&7Type an exact amount or text value (no +/- clicks).");
        Text.send(player,"&7Type &ccancel &7to return without changes.");
    }
    private void apply(Player player,String path,String value,Screen screen){
        var result=plugin.settings().set(path,value);
        Text.send(player,plugin.prefix()+(result.success()?"&a":"&c")+result.message());
        if(result.success())show(player,screen);
        else ask(player,path,screen);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;
        if(!(e.getWhoClicked() instanceof Player player))return;
        if(!player.hasPermission("spree.admin")){e.setCancelled(true);return;}
        int slot=e.getRawSlot();
        if(slot>=h.inv.getSize()){
            // Allow ordinary left/right inventory clicks in the recipe editor
            // so admins can pick up an ingredient on their cursor.
            boolean allow=h.screen==Screen.RECIPE &&
                    (e.getClick()==ClickType.LEFT||e.getClick()==ClickType.RIGHT);
            if(!allow)e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        ItemStack cursor=e.getCursor()==null?null:e.getCursor().clone();
        ClickType click=e.getClick();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!player.isOnline()||player.getOpenInventory().getTopInventory().getHolder()!=h)return;
            if(h.screen==Screen.HOME){
                switch(slot){
                    case 10 -> limits(player);
                    case 12 -> mace(player);
                    case 14 -> recipe(player);
                    case 16 -> plugin.menus().openSettings(player);
                    case 18 -> plugin.menus().openAdmin(player);
                    case 26 -> player.closeInventory();
                    default -> {}
                }
                return;
            }
            if(slot==h.inv.getSize()-9){open(player);return;}
            if(slot==h.inv.getSize()-1){player.closeInventory();return;}
            if(h.screen==Screen.LIMITS){
                for(int i=0;i<LIMIT_SLOTS.length;i++)
                    if(slot==LIMIT_SLOTS[i]){
                        if(click==ClickType.RIGHT)
                            apply(player,LIMIT_PATHS[i],
                                String.valueOf(plugin.settings().find(LIMIT_PATHS[i]).defaultValue()),Screen.LIMITS);
                        else ask(player,LIMIT_PATHS[i],Screen.LIMITS);
                        return;
                    }
            }
            if(h.screen==Screen.MACE){
                int[] slots={10,12,14,16};
                for(int i=0;i<slots.length;i++)if(slot==slots[i]){
                    String path=MACE_PATHS[i];
                    if(click==ClickType.RIGHT)
                        apply(player,path,String.valueOf(plugin.settings().find(path).defaultValue()),Screen.MACE);
                    else if(i==3)
                        apply(player,path,plugin.getConfig().getBoolean(path,true)?"false":"true",Screen.MACE);
                    else ask(player,path,Screen.MACE);
                    return;
                }
            }
            if(h.screen==Screen.RECIPE){
                for(int i=0;i<9;i++)if(slot==RECIPE_SLOTS[i]){
                    String path="fate-sigil.recipe.slot-"+(i+1);
                    if(click==ClickType.SHIFT_RIGHT){
                        apply(player,path,String.valueOf(plugin.settings().find(path).defaultValue()),Screen.RECIPE);
                    }else if(click==ClickType.RIGHT){
                        apply(player,path,"AIR",Screen.RECIPE);
                    }else if(cursor!=null&&!cursor.getType().isAir()&&cursor.getType().isItem()){
                        apply(player,path,cursor.getType().name(),Screen.RECIPE);
                    }else ask(player,path,Screen.RECIPE);
                    return;
                }
                switch(slot){
                    case 37 -> ask(player,"fate-sigil.display-name",Screen.RECIPE);
                    case 39 -> {
                        if(click==ClickType.RIGHT)apply(player,"fate-sigil.recipe.result-amount","1",Screen.RECIPE);
                        else ask(player,"fate-sigil.recipe.result-amount",Screen.RECIPE);
                    }
                    case 41 -> apply(player,"fate-sigil.recipe.enabled",
                        plugin.getConfig().getBoolean("fate-sigil.recipe.enabled",true)?"false":"true",Screen.RECIPE);
                    case 43 -> plugin.menus().openSettings(player);
                    default -> {}
                }
            }
        });
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if(e.getView().getTopInventory().getHolder() instanceof Holder)
            for(int slot:e.getRawSlots())if(slot<e.getView().getTopInventory().getSize()){
                e.setCancelled(true);return;
            }
    }
    @SuppressWarnings("deprecation")
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void chat(AsyncPlayerChatEvent e){
        UUID id=e.getPlayer().getUniqueId();
        if(!pending.containsKey(id))return;
        e.setCancelled(true);
        String answer=e.getMessage();
        Bukkit.getScheduler().runTask(plugin,()->{
            Editing edit=pending.remove(id);
            Player player=Bukkit.getPlayer(id);
            if(edit==null||player==null||!player.hasPermission("spree.admin"))return;
            if(answer.equalsIgnoreCase("cancel"))show(player,edit.back());
            else apply(player,edit.path(),answer,edit.back());
        });
    }
    @EventHandler public void quit(PlayerQuitEvent e){pending.remove(e.getPlayer().getUniqueId());}
}
