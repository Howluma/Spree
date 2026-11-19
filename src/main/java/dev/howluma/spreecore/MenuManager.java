package dev.howluma.spreecore;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

/** All clickable interfaces in one holder-based GUI (no fragile inventory-title matching). */
public final class MenuManager implements Listener {
    private final SpreeCorePlugin plugin;
    private final Map<UUID, Long> resetConfirm=new HashMap<>();
    private enum Screen { MAIN, ADMIN, PLAYERS, PLAYER, EVENTS, LEGENDARY }
    private static final class Holder implements InventoryHolder {
        final Screen screen;
        final UUID target;
        final int page;
        final Map<Integer,UUID> players=new HashMap<>();
        Inventory inv;
        Holder(Screen screen,UUID target,int page) {this.screen=screen;this.target=target;this.page=page;}
        @Override public Inventory getInventory() {return inv;}
    }
    MenuManager(SpreeCorePlugin plugin) {this.plugin=plugin;}
    private Inventory create(Holder holder,int rows,String title) {
        holder.inv=Bukkit.createInventory(holder,rows*9,Text.c(title));return holder.inv;
    }
    private ItemStack icon(Material material,String name,String... lore) {
        ItemStack stack=new ItemStack(material);
        ItemMeta meta=stack.getItemMeta();
        meta.setDisplayName(Text.c(name));
        meta.setLore(Arrays.stream(lore).map(Text::c).toList());
        stack.setItemMeta(meta);
        return stack;
    }
    public void openMain(Player p) {
        Holder h=new Holder(Screen.MAIN,null,0);
        Inventory inv=create(h,3,"&9&lSPREE MENU");
        PlayerDataStore.Profile s=plugin.data().get(p);
        inv.setItem(10,icon(Material.BLAZE_POWDER,"&9&lYour Killstreak", "&fCurrent: &9"+plugin.streaks().display(s.streak),"&fBest: &9"+s.best));
        inv.setItem(12,icon(Material.NETHERITE_SWORD,"&b&lItem Unlocks","&7See what your streak unlocks"));
        inv.setItem(14,icon(Material.PAPER,"&b&lYour Stats", "&7Kills: &f"+s.kills,"&7Deaths: &f"+s.deaths,"&7Streaks ended: &f"+s.ended));
        inv.setItem(16,icon(Material.PLAYER_HEAD,"&6&lLeaderboard","&7View the top players"));
        inv.setItem(20,icon(Material.COMPASS,"&e&lCurrent Event","&7Compass: &f"+plugin.events().mode(),"&7Timed: &f"+plugin.events().timed()));
        inv.setItem(22,icon(Material.DIAMOND_SWORD,"&b&lLegendary Weapons","&7Browse weapons and forge status"));
        inv.setItem(26,icon(Material.BARRIER,"&cClose"));
        p.openInventory(inv);
    }
    public void openAdmin(Player p) {
        Holder h=new Holder(Screen.ADMIN,null,0);Inventory inv=create(h,3,"&8&lSPREE ADMIN");
        inv.setItem(10,icon(Material.PLAYER_HEAD,"&9&lManage Players","&7Click to select an online player"));
        inv.setItem(12,icon(Material.LIGHTNING_ROD,"&e&lEvents","&7Toggle modes and timed events"));
        inv.setItem(14,icon(Material.DIAMOND_SWORD,"&b&lLegendary Weapons","&7Test weapons and inspect forging"));
        inv.setItem(16,icon(Material.EMERALD_BLOCK,"&a&lRecalculate All","&7Apply passives and restrictions"));
        inv.setItem(22,icon(Material.TNT,"&4&lReset All Killstreaks","&cClick twice within 5 seconds to confirm"));
        inv.setItem(26,icon(Material.BARRIER,"&cClose"));
        p.openInventory(inv);
    }
    public void openPlayers(Player p,int page) {
        List<Player> online=new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER));
        int maxPage=Math.max(0,(online.size()-1)/45);
        int selected=Math.max(0,Math.min(page,maxPage));
        Holder h=new Holder(Screen.PLAYERS,null,selected);Inventory inv=create(h,6,"&8&lMANAGE PLAYERS &7Page "+(selected+1));
        for(int i=0;i<45 && selected*45+i<online.size();i++) {
            Player target=online.get(selected*45+i);
            var stats=plugin.data().get(target);
            inv.setItem(i,icon(Material.PLAYER_HEAD,"&f"+target.getName(),
                    "&7Killstreak: &9"+plugin.streaks().display(stats.streak),"&7Kills: &f"+stats.kills,"&eClick to manage"));
            h.players.put(i,target.getUniqueId());
        }
        inv.setItem(45,icon(Material.ARROW,"&cPrevious page"));
        inv.setItem(49,icon(Material.BARRIER,"&cBack"));
        inv.setItem(53,icon(Material.ARROW,"&aNext page"));
        p.openInventory(inv);
    }
    public void openPlayer(Player admin,UUID id) {
        Player target=Bukkit.getPlayer(id);
        if(target==null) {Text.send(admin,"&cPlayer is offline. Use /spreeadmin set <name> <value>.");openPlayers(admin,0);return;}
        var profile=plugin.data().get(target);
        Holder h=new Holder(Screen.PLAYER,id,0);Inventory inv=create(h,3,"&8&lPLAYER: &f"+target.getName());
        inv.setItem(4,icon(Material.PLAYER_HEAD,"&f"+target.getName(),"&7Streak: &9"+plugin.streaks().display(profile.streak),
                "&7Best: &9"+profile.best,"&7Kills: &f"+profile.kills,"&7Deaths: &f"+profile.deaths));
        inv.setItem(10,icon(Material.EMERALD,"&aAdd 1"));
        inv.setItem(11,icon(Material.EMERALD_BLOCK,"&aAdd 5"));
        inv.setItem(12,icon(Material.REDSTONE,"&cRemove 1"));
        inv.setItem(13,icon(Material.REDSTONE_BLOCK,"&cRemove 5"));
        inv.setItem(14,icon(Material.BARRIER,"&4Reset to 0"));
        inv.setItem(15,icon(Material.IRON_INGOT,"&7Set to 0"));
        inv.setItem(16,icon(Material.GOLD_INGOT,"&eSet to 15"));
        inv.setItem(17,icon(Material.NETHERITE_INGOT,"&9Set to 30"));
        inv.setItem(26,icon(Material.ARROW,"&cBack to players"));
        admin.openInventory(inv);
    }
    public void openEvents(Player p,boolean admin) {
        Holder h=new Holder(Screen.EVENTS,null,0);Inventory inv=create(h,3,"&8&lSPREE EVENTS");
        inv.setItem(10,icon(Material.COMPASS,"&6&lThe Hunt (compass)","&7Status: &f"+(plugin.events().mode()==EventManager.Mode.HUNT?"ON":"OFF")));
        inv.setItem(11,icon(Material.NETHERITE_CHESTPLATE,"&4&lJuggernaut","&7Status: &f"+(plugin.events().mode()==EventManager.Mode.JUGGERNAUT?"ON":"OFF")));
        inv.setItem(12,icon(Material.ENDER_EYE,"&9&lLocator Bar","&7Status: &f"+(plugin.events().locatorEnabled()?"ON":"OFF")));
        inv.setItem(13,icon(Material.DIAMOND_SWORD,"&4&lBlood's Bane","&7Status: &f"+(plugin.events().bloodsBaneEnabled()?"ON":"OFF")));
        inv.setItem(14,icon(Material.RED_STAINED_GLASS_PANE,"&4&lBlood Moon","&7Timed: +1 streak per eligible kill"));
        inv.setItem(15,icon(Material.BLUE_STAINED_GLASS_PANE,"&b&lSpree Surge","&7Timed: boost 10+ streak players"));
        inv.setItem(16,icon(Material.PURPLE_STAINED_GLASS_PANE,"&5&lChaos","&7Timed: shorten high-streak cooldowns"));
        inv.setItem(17,icon(Material.ORANGE_STAINED_GLASS_PANE,"&6&lKill Race","&7Timed: first to 3 kills wins 3 streak"));
        inv.setItem(20,icon(Material.CLOCK,"&eActive timed event","&7"+plugin.events().timed(),"&7Remaining: "+plugin.events().timedSecondsRemaining()+"s"));
        inv.setItem(22,icon(Material.BARRIER,"&cStop timed event"));
        inv.setItem(24,icon(Material.BARRIER,"&cStop compass event"));
        inv.setItem(26,icon(Material.ARROW,"&cBack"));
        p.openInventory(inv);
    }
    public void openLegendary(Player p) {
        Holder h=new Holder(Screen.LEGENDARY,null,0);Inventory inv=create(h,3,"&9&lLEGENDARY WEAPONS");
        LegendaryType[] types=LegendaryType.values();
        for(int index=0;index<types.length;index++) {
            LegendaryType t=types[index];
            inv.setItem(10+index,icon(t.material,"&b&l"+t.display,
                    "&7Forge: &f"+plugin.forges().status(t),
                    p.hasPermission("spree.legendary.give")?"&eLeft click: receive test item":"&7Inspect via /legendary list",
                    p.hasPermission("spree.admin") && t.forgeable()?"&cShift click: reset forge":" "));
        }
        inv.setItem(26,icon(Material.ARROW,"&cBack"));
        p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        Inventory top=event.getView().getTopInventory();
        if(!(top.getHolder() instanceof Holder holder))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p))return;
        int slot=event.getRawSlot();
        if(slot<0 || slot>=top.getSize())return;
        boolean shift=event.isShiftClick();
        // InventoryClickEvent is inside an inventory transaction. Open/close on the
        // following tick instead of interfering with Minecraft's current click.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder()==holder)
                handleClick(p, holder, slot, shift);
        });
    }
    private void handleClick(Player p,Holder holder,int slot,boolean shift) {
        switch(holder.screen) {
            case MAIN -> {
                switch(slot) {
                    case 12 -> p.performCommand("spreeinfo");
                    case 14 -> p.performCommand("stats");
                    case 16 -> p.performCommand("leaderboard");
                    case 20 -> openEvents(p,false);
                    case 22 -> openLegendary(p);
                    case 26 -> p.closeInventory();
                    default -> {}
                }
            }
            case ADMIN -> {
                if(!p.hasPermission("spree.admin"))return;
                switch(slot) {
                    case 10 -> openPlayers(p,0);
                    case 12 -> openEvents(p,true);
                    case 14 -> openLegendary(p);
                    case 16 -> {plugin.streaks().refreshOnline();plugin.streaks().safetyInventoryCheck();Text.send(p,"&aUpdated online players.");}
                    case 22 -> {
                        long now=System.currentTimeMillis();
                        if(resetConfirm.getOrDefault(p.getUniqueId(),0L)>now) {
                            resetConfirm.remove(p.getUniqueId());
                            for(var profile:plugin.data().all())plugin.streaks().setStreak(profile.id,0,true);
                            Bukkit.broadcastMessage(Text.c("&9&lSPREE &7All killstreaks have been reset."));
                            p.closeInventory();
                        } else {resetConfirm.put(p.getUniqueId(),now+5000);Text.send(p,"&cClick again within five seconds to reset everyone.");}
                    }
                    case 26 -> p.closeInventory();
                    default -> {}
                }
            }
            case PLAYERS -> {
                if(!p.hasPermission("spree.admin"))return;
                if(slot==45)openPlayers(p,holder.page-1);
                else if(slot==53)openPlayers(p,holder.page+1);
                else if(slot==49)openAdmin(p);
                else if(holder.players.containsKey(slot))openPlayer(p,holder.players.get(slot));
            }
            case PLAYER -> {
                if(!p.hasPermission("spree.admin") || holder.target==null)return;
                if(slot==26) {openPlayers(p,0);return;}
                int current=plugin.data().get(holder.target).streak;
                Integer value=switch(slot) {
                    case 10 -> current+1;case 11 -> current+5;
                    case 12 -> current-1;case 13 -> current-5;
                    case 14,15 -> 0;case 16 -> 15;case 17 -> 30;
                    default -> null;
                };
                if(value!=null){plugin.streaks().setStreak(holder.target,value,true);openPlayer(p,holder.target);}
            }
            case EVENTS -> {
                if(slot==26) {if(p.hasPermission("spree.admin"))openAdmin(p);else openMain(p);return;}
                if(!p.hasPermission("spree.admin")) {Text.send(p,"&cOnly admins can change events.");return;}
                switch(slot) {
                    case 10 -> plugin.events().setMode(plugin.events().mode()==EventManager.Mode.HUNT?EventManager.Mode.NONE:EventManager.Mode.HUNT);
                    case 11 -> plugin.events().setMode(plugin.events().mode()==EventManager.Mode.JUGGERNAUT?EventManager.Mode.NONE:EventManager.Mode.JUGGERNAUT);
                    case 12 -> plugin.events().setLocator(!plugin.events().locatorEnabled());
                    case 13 -> plugin.events().setBloodsBane(!plugin.events().bloodsBaneEnabled());
                    case 14 -> plugin.events().setTimed(EventManager.Timed.BLOODMOON);
                    case 15 -> plugin.events().setTimed(EventManager.Timed.SURGE);
                    case 16 -> plugin.events().setTimed(EventManager.Timed.CHAOS);
                    case 17 -> plugin.events().setTimed(EventManager.Timed.RACE);
                    case 22 -> plugin.events().setTimed(EventManager.Timed.NONE);
                    case 24 -> plugin.events().setMode(EventManager.Mode.NONE);
                    default -> {}
                }
                openEvents(p,true);
            }
            case LEGENDARY -> {
                if(slot==26) {if(p.hasPermission("spree.admin"))openAdmin(p);else openMain(p);return;}
                int index=slot-10;
                if(index<0||index>=LegendaryType.values().length)return;
                LegendaryType t=LegendaryType.values()[index];
                if(shift && p.hasPermission("spree.admin") && t.forgeable()) {
                    plugin.forges().reset(t);
                    Text.send(p,"&aReset forge for "+t.display);
                } else if(p.hasPermission("spree.legendary.give")) {
                    p.getInventory().addItem(plugin.legendaries().create(t,true));
                    Text.send(p,"&aReceived a test copy of "+t.display);
                } else Text.send(p,"&7"+t.display+" - "+plugin.forges().status(t));
                openLegendary(p);
            }
        }
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);
    }
}
