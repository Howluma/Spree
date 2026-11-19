package dev.howluma.spreecore;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Stable holder-based GUIs with an allowlisted, typed config editor. */
public final class MenuManager implements Listener {
    private final SpreeCorePlugin plugin;
    private final Map<UUID, Long> resetConfirm = new HashMap<>();
    private final Map<UUID, PendingEdit> editing = new HashMap<>();
    private enum Screen { MAIN, ADMIN, PLAYERS, PLAYER, EVENTS, LEGENDARY, SETTINGS, SETTINGS_CATEGORY }
    private record PendingEdit(String path, String category, int page) {}
    private static final class Holder implements InventoryHolder {
        final Screen screen;
        final UUID target;
        final int page;
        final String category;
        final Map<Integer,UUID> players = new HashMap<>();
        final Map<Integer,String> entries = new HashMap<>();
        Inventory inv;
        Holder(Screen screen, UUID target, int page, String category) {
            this.screen = screen; this.target = target; this.page = page; this.category = category;
        }
        @Override public Inventory getInventory() { return inv; }
    }
    MenuManager(SpreeCorePlugin plugin) { this.plugin = plugin; }
    private Holder holder(Screen screen) { return new Holder(screen, null, 0, null); }
    private Inventory create(Holder h, int rows, String title) {
        h.inv = Bukkit.createInventory(h, rows * 9, Text.c(title));
        for (int slot = 0; slot < h.inv.getSize(); slot++) {
            if (rows == 6 && slot < 45) continue;
            h.inv.setItem(slot, icon(slot % 9 == 0 || slot % 9 == 8
                    ? Material.BLUE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, "&8"));
        }
        return h.inv;
    }
    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.c(name));
        meta.setLore(Arrays.stream(lore).map(Text::c).toList());
        item.setItemMeta(meta);
        return item;
    }
    private void foot(Inventory inv, int page, int maximum, String back) {
        inv.setItem(45, icon(Material.ARROW, "&bPrevious Page", "&7Page " + (page+1) + " of " + (maximum+1)));
        inv.setItem(48, icon(Material.BOOK, "&fHelp", "&7Left click: increase number / toggle", "&7Right click: decrease number",
                "&7Shift + Left: enter exact value in chat", "&7Shift + Right: restore default", "&7Text settings: click to edit in chat"));
        inv.setItem(49, icon(Material.ARROW, back));
        inv.setItem(50, icon(Material.CLOCK, "&9Page " + (page+1) + " / " + (maximum+1)));
        inv.setItem(53, icon(Material.ARROW, "&bNext Page"));
    }

    public void openMain(Player p) {
        Holder h = holder(Screen.MAIN);
        Inventory inv = create(h, 3, "&9&lSPREE &8| &fYour Dashboard");
        var s = plugin.data().get(p);
        inv.setItem(10, icon(Material.NETHER_STAR, "&b&lYour Killstreak", "&7Current: &b" + plugin.streaks().display(s.streak), "&7Personal best: &f"+s.best));
        inv.setItem(12, icon(Material.NETHERITE_SWORD, "&fItem Unlocks", "&7Click to view progression"));
        inv.setItem(14, icon(Material.WRITABLE_BOOK, "&fYour Stats", "&7Kills: &f"+s.kills, "&7Deaths: &f"+s.deaths, "&7Streaks ended: &f"+s.ended));
        inv.setItem(16, icon(Material.PLAYER_HEAD, "&6Leaderboard", "&7Top killstreaks"));
        inv.setItem(20, icon(Material.COMPASS, "&eLive Events", "&7Mode: &f"+plugin.events().mode(), "&7Timed event: &f"+plugin.events().timed()));
        inv.setItem(22, icon(Material.DIAMOND_SWORD, "&bLegendary Weapons", "&7Browse weapons and forge status"));
        if (p.hasPermission("spree.admin")) {
            inv.setItem(18, icon(Material.COMMAND_BLOCK, "&eAdmin Dashboard", "&7Manage players, events and weapons"));
            inv.setItem(24, icon(Material.COMPARATOR, "&a&lEdit All Settings", "&7Click to open the configuration editor", "&7Toggles, numbers and text without YAML"));
        }
        inv.setItem(26, icon(Material.BARRIER, "&cClose"));
        p.openInventory(inv);
    }
    public void openAdmin(Player p) {
        if (!p.hasPermission("spree.admin")) return;
        Holder h = holder(Screen.ADMIN);
        Inventory inv = create(h, 3, "&9&lSPREE &8| &fAdmin Control");
        inv.setItem(10, icon(Material.PLAYER_HEAD, "&bPlayers", "&7Manage killstreaks individually"));
        inv.setItem(12, icon(Material.LIGHTNING_ROD, "&eEvents", "&7Start and stop server events"));
        inv.setItem(14, icon(Material.DIAMOND_SWORD, "&bLegendary Weapons", "&7Preview items and reset forges"));
        inv.setItem(16, icon(Material.EMERALD_BLOCK, "&aRecalculate Effects", "&7Apply live changes to all online players"));
        inv.setItem(20, icon(Material.COMPARATOR, "&a&lSettings Editor", "&7Every value in config.yml, organized", "&7Click to configure the plugin"));
        inv.setItem(22, icon(Material.TNT, "&cReset Every Killstreak", "&4Requires a second click within 5 seconds"));
        inv.setItem(24, icon(Material.ARROW, "&bBack to Dashboard"));
        inv.setItem(26, icon(Material.BARRIER, "&cClose"));
        p.openInventory(inv);
    }
    public void openSettings(Player p) { openSettings(p,0); }
    private void openSettings(Player p,int page) {
        if (!p.hasPermission("spree.admin")) { Text.send(p,"&cYou need spree.admin.");return; }
        List<ConfigEditor.Category> categories = plugin.settings().categories();
        int max = Math.max(0, (categories.size()-1)/45);
        int selected = Math.max(0, Math.min(page,max));
        Holder h = new Holder(Screen.SETTINGS,null,selected,null);
        Inventory inv = create(h,6,"&9&lSPREE &8| &fSettings &7"+(selected+1));
        for (int i=0; i<45 && selected*45+i<categories.size(); i++) {
            ConfigEditor.Category c = categories.get(selected*45+i);
            h.entries.put(i,c.id());
            inv.setItem(i,icon(c.icon(),"&b&l"+c.title(),"&7"+c.settings().size()+" editable settings",
                    "&8"+c.id(),"&aClick to open"));
        }
        foot(inv,selected,max,"&bBack to Admin");
        p.openInventory(inv);
    }
    private void openConfigCategory(Player p,String category,int page) {
        if (!p.hasPermission("spree.admin")) return;
        ConfigEditor.Category c=plugin.settings().category(category);
        if(c==null) {openSettings(p);return;}
        int max=Math.max(0,(c.settings().size()-1)/45);
        int selected=Math.max(0,Math.min(page,max));
        Holder h=new Holder(Screen.SETTINGS_CATEGORY,null,selected,category);
        Inventory inv=create(h,6,"&8Settings &7> &9"+c.title());
        for(int i=0;i<45 && selected*45+i<c.settings().size();i++) {
            ConfigEditor.Setting setting=c.settings().get(selected*45+i);
            h.entries.put(i,setting.path());
            Object current=plugin.getConfig().get(setting.path(),setting.defaultValue());
            boolean enabled=Boolean.TRUE.equals(current);
            Material material=setting.toggle()?(enabled?Material.LIME_DYE:Material.GRAY_DYE)
                    :setting.number()?Material.REPEATER:Material.NAME_TAG;
            String tone=setting.toggle()?(enabled?"&a":"&c"):"&b";
            inv.setItem(i,icon(material,tone+"&l"+setting.label(),
                    "&7Current: &f"+setting.current(plugin.getConfig()),
                    "&7Default: &8"+setting.defaultText(),
                    "&8"+setting.path()," ",
                    setting.toggle()?"&eLeft: Toggle":setting.number()?"&aLeft: +1    &cRight: -1":"&eClick: Edit text in chat",
                    setting.number()?"&eShift + Left: Exact number":"&7Shift + Left: Edit in chat",
                    "&cShift + Right: Reset to default"));
        }
        foot(inv,selected,max,"&bBack to Categories");
        p.openInventory(inv);
    }
    public void openPlayers(Player p,int page) {
        if (!p.hasPermission("spree.admin")) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER));
        int max=Math.max(0,(online.size()-1)/45);
        int selected=Math.max(0,Math.min(page,max));
        Holder h=new Holder(Screen.PLAYERS,null,selected,null);
        Inventory inv=create(h,6,"&8Players &7| &bPage "+(selected+1));
        for(int i=0;i<45 && selected*45+i<online.size();i++) {
            Player target=online.get(selected*45+i);
            var profile=plugin.data().get(target);
            h.players.put(i,target.getUniqueId());
            inv.setItem(i,icon(Material.PLAYER_HEAD,"&f"+target.getName(),
                    "&7Killstreak: &b"+plugin.streaks().display(profile.streak),"&7Kills: &f"+profile.kills,"&aClick to manage"));
        }
        foot(inv,selected,max,"&bBack to Admin");
        p.openInventory(inv);
    }
    public void openPlayer(Player p,UUID id) {
        if (!p.hasPermission("spree.admin")) return;
        Player target=Bukkit.getPlayer(id);
        if(target==null) {Text.send(p,"&cPlayer went offline. Use /spreeadmin set <name> <amount>.");openPlayers(p,0);return;}
        var profile=plugin.data().get(target);
        Holder h=new Holder(Screen.PLAYER,id,0,null);
        Inventory inv=create(h,3,"&9Manage &7| &f"+target.getName());
        inv.setItem(4,icon(Material.PLAYER_HEAD,"&f"+target.getName(),"&7Streak: &b"+plugin.streaks().display(profile.streak),
                "&7Best: &f"+profile.best,"&7Kills: &f"+profile.kills,"&7Deaths: &f"+profile.deaths));
        inv.setItem(10,icon(Material.EMERALD,"&aAdd 1"));
        inv.setItem(11,icon(Material.EMERALD_BLOCK,"&aAdd 5"));
        inv.setItem(12,icon(Material.REDSTONE,"&cRemove 1"));
        inv.setItem(13,icon(Material.REDSTONE_BLOCK,"&cRemove 5"));
        inv.setItem(14,icon(Material.BARRIER,"&4Reset to 0"));
        inv.setItem(15,icon(Material.IRON_INGOT,"&7Set to 0"));
        inv.setItem(16,icon(Material.GOLD_INGOT,"&eSet to 15"));
        inv.setItem(17,icon(Material.NETHERITE_INGOT,"&bSet to 30"));
        inv.setItem(26,icon(Material.ARROW,"&cBack to Players"));
        p.openInventory(inv);
    }
    public void openEvents(Player p,boolean admin) {
        Holder h=holder(Screen.EVENTS);Inventory inv=create(h,3,"&9&lSPREE &8| &fEvents");
        inv.setItem(10,icon(Material.COMPASS,"&6The Hunt", "&7Status: &f"+(plugin.events().mode()==EventManager.Mode.HUNT?"ON":"OFF")));
        inv.setItem(11,icon(Material.NETHERITE_CHESTPLATE,"&cJuggernaut", "&7Status: &f"+(plugin.events().mode()==EventManager.Mode.JUGGERNAUT?"ON":"OFF")));
        inv.setItem(12,icon(Material.ENDER_EYE,"&bLocator Bar", "&7Status: &f"+(plugin.events().locatorEnabled()?"ON":"OFF")));
        inv.setItem(13,icon(Material.DIAMOND_SWORD,"&cBlood's Bane", "&7Status: &f"+(plugin.events().bloodsBaneEnabled()?"ON":"OFF")));
        inv.setItem(14,icon(Material.RED_STAINED_GLASS_PANE,"&cBlood Moon", "&7Bonus killstreak rewards"));
        inv.setItem(15,icon(Material.BLUE_STAINED_GLASS_PANE,"&bSpree Surge", "&7High-streak buffs"));
        inv.setItem(16,icon(Material.PURPLE_STAINED_GLASS_PANE,"&5Chaos", "&7Faster legendary cooldowns"));
        inv.setItem(17,icon(Material.ORANGE_STAINED_GLASS_PANE,"&6Kill Race", "&7First to the target wins"));
        inv.setItem(20,icon(Material.CLOCK,"&eTimed Event: &f"+plugin.events().timed(),"&7Remaining: "+plugin.events().timedSecondsRemaining()+"s"));
        inv.setItem(22,icon(Material.BARRIER,"&cStop Timed Event"));
        inv.setItem(24,icon(Material.BARRIER,"&cStop Compass Event"));
        inv.setItem(26,icon(Material.ARROW,"&cBack"));
        p.openInventory(inv);
    }
    public void openLegendary(Player p) {
        Holder h=holder(Screen.LEGENDARY);Inventory inv=create(h,3,"&9&lSPREE &8| &fLegendaries");
        LegendaryType[] types=LegendaryType.values();
        for(int i=0;i<types.length;i++) {
            LegendaryType t=types[i];
            inv.setItem(10+i,icon(t.material,"&b&l"+t.display,"&7Forge: &f"+plugin.forges().status(t),
                    p.hasPermission("spree.legendary.give")?"&eLeft click: give test item":"&7Browse available weapons",
                    p.hasPermission("spree.admin") && t.forgeable()?"&cShift click: reset forge":" "));
        }
        inv.setItem(26,icon(Material.ARROW,"&cBack"));
        p.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        Inventory top=event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot=event.getRawSlot();
        if(slot<0 || slot>=top.getSize()) return;
        ClickType click=event.getClick();
        // Delay navigation until after the inventory transaction completes.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if(p.isOnline() && p.getOpenInventory().getTopInventory().getHolder()==h)
                handleClick(p,h,slot,click);
        });
    }
    private void handleClick(Player p,Holder h,int slot,ClickType click) {
        switch(h.screen) {
            case MAIN -> {
                switch(slot) {
                    case 12 -> p.performCommand("spreeinfo");
                    case 14 -> p.performCommand("stats");
                    case 16 -> p.performCommand("leaderboard");
                    case 18 -> {if(p.hasPermission("spree.admin"))openAdmin(p);}
                    case 20 -> openEvents(p,false);
                    case 22 -> openLegendary(p);
                    case 24 -> {if(p.hasPermission("spree.admin"))openSettings(p);}
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
                    case 16 -> {plugin.streaks().refreshOnline();plugin.streaks().safetyInventoryCheck();Text.send(p,"&aRecalculated all online players.");}
                    case 20 -> openSettings(p);
                    case 22 -> {
                        long now=System.currentTimeMillis();
                        if(resetConfirm.getOrDefault(p.getUniqueId(),0L)>now) {
                            resetConfirm.remove(p.getUniqueId());
                            for(var profile:List.copyOf(plugin.data().all()))plugin.streaks().setStreak(profile.id,0,true);
                            Bukkit.broadcastMessage(Text.c("&9&lSPREE &7All killstreaks have been reset."));
                            p.closeInventory();
                        } else {resetConfirm.put(p.getUniqueId(),now+5000);Text.send(p,"&cClick again within 5 seconds to confirm.");}
                    }
                    case 24 -> openMain(p);
                    case 26 -> p.closeInventory();
                    default -> {}
                }
            }
            case SETTINGS -> {
                if(!p.hasPermission("spree.admin"))return;
                if(slot==45)openSettings(p,h.page-1);
                else if(slot==53)openSettings(p,h.page+1);
                else if(slot==49)openAdmin(p);
                else if(h.entries.containsKey(slot))openConfigCategory(p,h.entries.get(slot),0);
            }
            case SETTINGS_CATEGORY -> {
                if(!p.hasPermission("spree.admin"))return;
                if(slot==45)openConfigCategory(p,h.category,h.page-1);
                else if(slot==53)openConfigCategory(p,h.category,h.page+1);
                else if(slot==49)openSettings(p);
                else if(h.entries.containsKey(slot))editClick(p,h,h.entries.get(slot),click);
            }
            case PLAYERS -> {
                if(!p.hasPermission("spree.admin"))return;
                if(slot==45)openPlayers(p,h.page-1);
                else if(slot==53)openPlayers(p,h.page+1);
                else if(slot==49)openAdmin(p);
                else if(h.players.containsKey(slot))openPlayer(p,h.players.get(slot));
            }
            case PLAYER -> {
                if(!p.hasPermission("spree.admin") || h.target==null)return;
                if(slot==26){openPlayers(p,0);return;}
                int current=plugin.data().get(h.target).streak;
                Integer value=switch(slot) {
                    case 10 -> current+1; case 11 -> current+5;
                    case 12 -> current-1; case 13 -> current-5;
                    case 14,15 -> 0; case 16 -> 15; case 17 -> 30;
                    default -> null;
                };
                if(value!=null){plugin.streaks().setStreak(h.target,value,true);openPlayer(p,h.target);}
            }
            case EVENTS -> {
                if(slot==26){if(p.hasPermission("spree.admin"))openAdmin(p);else openMain(p);return;}
                if(!p.hasPermission("spree.admin")){Text.send(p,"&cOnly admins may change events.");return;}
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
                if(slot==26){if(p.hasPermission("spree.admin"))openAdmin(p);else openMain(p);return;}
                int index=slot-10;
                if(index<0 || index>=LegendaryType.values().length)return;
                LegendaryType type=LegendaryType.values()[index];
                if(click.isShiftClick() && p.hasPermission("spree.admin") && type.forgeable()) {
                    plugin.forges().reset(type);Text.send(p,"&aForge reset for "+type.display);
                } else if(p.hasPermission("spree.legendary.give")) {
                    Map<Integer,ItemStack> spare=p.getInventory().addItem(plugin.legendaries().create(type,true));
                    for(ItemStack item:spare.values())p.getWorld().dropItemNaturally(p.getLocation(),item);
                    Text.send(p,"&aReceived a test copy of "+type.display);
                } else Text.send(p,"&7"+type.display+" - "+plugin.forges().status(type));
                openLegendary(p);
            }
        }
    }
    private void editClick(Player p, Holder h, String path, ClickType click) {
        ConfigEditor.Setting setting=plugin.settings().find(path);
        if(setting==null) {openSettings(p);return;}
        if(click==ClickType.SHIFT_RIGHT) {
            report(p,plugin.settings().reset(path));openConfigCategory(p,h.category,h.page);return;
        }
        if(setting.toggle()) {
            Object current=plugin.getConfig().get(path,setting.defaultValue());
            report(p,plugin.settings().set(path,Boolean.TRUE.equals(current)?"false":"true"));
            openConfigCategory(p,h.category,h.page);return;
        }
        if(!setting.number() || click==ClickType.SHIFT_LEFT || click==ClickType.MIDDLE) {
            editing.put(p.getUniqueId(),new PendingEdit(path,h.category,h.page));
            p.closeInventory();
            Text.send(p,plugin.prefix()+"&bEditing &f"+setting.label()+"&b.");
            Text.send(p,"&7Current: &f"+setting.current(plugin.getConfig())+" &8| &7Default: &f"+setting.defaultText());
            Text.send(p,"&eEnter the new value in chat, or use &f/spree value <value>&e.");
            Text.send(p,"&7Type &ccancel &7to return without changes.");
            return;
        }
        if(click!=ClickType.LEFT && click!=ClickType.RIGHT)return;
        double step=(setting.defaultValue() instanceof Double || setting.defaultValue() instanceof Float)?0.1:1;
        Number current=(Number)plugin.getConfig().get(path,setting.defaultValue());
        double changed=current.doubleValue()+(click==ClickType.RIGHT?-step:step);
        String raw=setting.defaultValue() instanceof Integer||setting.defaultValue() instanceof Long
                ?String.valueOf((long)changed):String.format(Locale.ROOT,"%.4f",changed);
        report(p,plugin.settings().set(path,raw));
        openConfigCategory(p,h.category,h.page);
    }
    private void report(Player p,ConfigEditor.Result r) {Text.send(p,plugin.prefix()+(r.success()?"&a":"&c")+r.message());}
    public boolean submitInput(Player p,String input) {
        PendingEdit pending=editing.get(p.getUniqueId());
        if(pending==null)return false;
        editing.remove(p.getUniqueId());
        if(!p.hasPermission("spree.admin")){Text.send(p,"&cNo permission to edit settings.");return true;}
        if(input.equalsIgnoreCase("cancel")){Text.send(p,"&7Edit cancelled.");openConfigCategory(p,pending.category,pending.page);return true;}
        report(p,plugin.settings().set(pending.path,input));
        openConfigCategory(p,pending.category,pending.page);
        return true;
    }
    @SuppressWarnings("deprecation")
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void chat(AsyncPlayerChatEvent event) {
        UUID playerId=event.getPlayer().getUniqueId();
        if(!editing.containsKey(playerId))return;
        event.setCancelled(true);
        String message=event.getMessage();
        Bukkit.getScheduler().runTask(plugin,()->{
            Player player=Bukkit.getPlayer(playerId);
            if(player!=null && player.isOnline())submitInput(player,message);
            else editing.remove(playerId);
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        editing.remove(id);resetConfirm.remove(id);
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);
    }
}
