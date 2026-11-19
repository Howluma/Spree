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
    // Async chat listeners only inspect these; modifications and GUI work run on the server thread.
    private final Map<UUID, PendingEdit> editing = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, BrowserState> playerBrowser = new HashMap<>();
    private final Map<UUID, PendingStatEdit> statEditing = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> searchEditing = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, ConfirmPlayerReset> playerResetConfirm = new HashMap<>();
    private record BrowserState(int page, boolean onlineOnly, String search) {}
    private enum PlayerField { STREAK, BEST, KILLS, DEATHS, ENDED }
    private record PendingStatEdit(UUID target, PlayerField field) {}
    private record ConfirmPlayerReset(UUID target, long until) {}
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
        inv.setItem(45, icon(Material.ARROW, "&9Previous Page", "&7Page " + (page+1) + " of " + (maximum+1)));
        inv.setItem(48, icon(Material.BOOK, "&fHelp", "&7Left click: increase number / toggle", "&7Right click: decrease number",
                "&7Shift + Left: enter exact value in chat", "&7Shift + Right: restore default", "&7Text settings: click to edit in chat"));
        inv.setItem(49, icon(Material.ARROW, back));
        inv.setItem(50, icon(Material.CLOCK, "&9Page " + (page+1) + " / " + (maximum+1)));
        inv.setItem(53, icon(Material.ARROW, "&9Next Page"));
    }

    public void openMain(Player p) {
        Holder h = holder(Screen.MAIN);
        Inventory inv = create(h, 4, "&9&lSPREE &8| &fDashboard");
        var profile = plugin.data().get(p);
        // 4-column symmetric dashboard, identical for all player ranks.
        inv.setItem(10, icon(Material.NETHER_STAR, "&9&lProgression",
                "&7Level: &f" + plugin.streaks().display(profile.streak),
                "&7Make your Privilege, Talent, Infusion choices",
                "&7Perks roll automatically",
                "&bClick to manage upgrades"));
        inv.setItem(12, icon(Material.WRITABLE_BOOK, "&f&lStatistics",
                "&7Kills: &f" + profile.kills + " &8| &7Deaths: &f" + profile.deaths,
                "&7Personal best: &f" + profile.best, "&bClick to view"));
        inv.setItem(14, icon(Material.PLAYER_HEAD, "&6&lLeaderboard",
                "&7Compare killstreaks with other players", "&bClick to view"));
        inv.setItem(16, icon(Material.KNOWLEDGE_BOOK, "&9&lHow Everything Works",
                "&7Short guides split into categories",
                "&7Explore talents, perks, infusions, privileges",
                "&bClick to learn"));
        int infusionCount = (int) profile.infusionChoices.entrySet().stream()
                .filter(e -> e.getKey() <= profile.streak).count();
        inv.setItem(19, icon(Material.POTION, "&9&lActive Bonuses",
                "&7Max health: &f+" + ConfigEditor.format(
                        Math.max(0, profile.streak)*plugin.getConfig().getDouble("killstreak.health-per-level-hearts",.5)) + " hearts",
                "&7Permanent Infusions: &f" + infusionCount,
                "&7Glowing: " + (profile.streak >= plugin.getConfig().getInt("hunt.glowing-level",10)?"&aYes":"&8No"),
                "&bClick to see your build"));
        inv.setItem(21, icon(Material.ENCHANTED_BOOK, "&9&lYour Perks",
                "&7" + profile.perkRolls.size() + " rolled milestones",
                "&7Small bonuses, duplicates stack", "&bClick to view all"));
        inv.setItem(23, icon(profile.hideHud?Material.GRAY_DYE:Material.LIME_DYE,
                "&fKillstreak HUD: " + (profile.hideHud?"&cOFF":"&aON"),
                "&bClick to toggle"));
        inv.setItem(25, icon(Material.COMPASS, "&c&lWanted Status",
                "&7Why higher streaks make you a target",
                "&7Bounties, glowing, coordinates and teams",
                "&bClick for milestones"));
        if(p.hasPermission("spree.admin")) {
            inv.setItem(29,icon(Material.COMMAND_BLOCK,"&e&lAdmin Control",
                    "&7Players, events and legendary weapons","&eClick to manage"));
            inv.setItem(33,icon(Material.COMPARATOR,"&a&lPlugin Settings",
                    "&7All categories, arranged and editable","&eClick to configure"));
        }
        inv.setItem(31, icon(Material.BARRIER, "&cClose"));
        p.openInventory(inv);
    }
    /** Show only active buffs on the book, never the full list of future unlocks. */
    static String[] currentBuffLore(org.bukkit.configuration.file.FileConfiguration config, int streak) {
        if (streak <= 0) return new String[]{"&8No active Spree buffs"};
        List<String> current = new ArrayList<>();
        double healthBonus = streak * config.getDouble("killstreak.health-per-level-hearts", 0.5);
        if (healthBonus > 0)
            current.add("&9❤ &7Health &f+" + ConfigEditor.format(healthBonus) + " hearts");
        // Actual Infusions and Privileges are player-selected, not automatic.
        if (MilestoneEffects.unlocked(streak, config.getInt("hunt.glowing-level",10)))
            current.add("&7Hunt Glowing &aActive");
        current.add("&7Your chosen upgrades: &9/spree");
        return current.toArray(String[]::new);
    }

    public void openAdmin(Player p) {
        if (!p.hasPermission("spree.admin")) return;
        Holder h = holder(Screen.ADMIN);
        Inventory inv = create(h, 4, "&9&lSPREE &8| &fAdmin Center");
        // Match the public dashboard: four equal columns and a centered exit.
        inv.setItem(10, icon(Material.PLAYER_HEAD, "&9&lPlayers",
                "&7Individual killstreaks and statistics", "&eClick to manage"));
        inv.setItem(12, icon(Material.LIGHTNING_ROD, "&e&lEvents",
                "&7Hunt, Juggernaut, timed modes", "&eClick to manage"));
        inv.setItem(14, icon(Material.DIAMOND_SWORD, "&9&lLegendary Weapons",
                "&7Preview, test, reset forges", "&eClick to manage"));
        inv.setItem(16, icon(Material.TNT, "&9&lCombat Balance",
                "&7Mace, TNT, explosion credit", "&eClick to configure"));
        inv.setItem(19, icon(Material.COMPARATOR, "&a&lAll Plugin Settings",
                "&7Grouped categories, editable values", "&eClick to configure"));
        inv.setItem(21, icon(Material.EMERALD_BLOCK, "&a&lRefresh Players",
                "&7Recalculate health and effects", "&eClick to apply"));
        inv.setItem(23, icon(Material.BARRIER, "&c&lReset All Killstreaks",
                "&7Double-click confirmation required", "&4Danger: resets every current streak"));
        inv.setItem(25, icon(Material.KNOWLEDGE_BOOK, "&9&lProgression Guide",
                "&7Review Privileges, Talents and Infusions", "&eClick to browse"));
        inv.setItem(29, icon(Material.ARROW, "&9Back to Dashboard"));
        inv.setItem(31, icon(Material.BARRIER, "&cClose"));
        inv.setItem(33, icon(Material.CRAFTING_TABLE, "&b&lBalance Workshop",
                "&7Direct item carry limits and mace cooldowns",
                "&7Edit Fate Sigil ingredients in a 3x3 grid",
                "&eClick for gameplay customization"));
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
            inv.setItem(i,icon(c.icon(),"&9&l"+c.title(),"&7"+c.settings().size()+" editable settings",
                    "&8"+c.id(),"&aClick to open"));
        }
        foot(inv,selected,max,"&9Back to Admin");
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
            String tone=setting.toggle()?(enabled?"&a":"&c"):"&9";
            inv.setItem(i,icon(material,tone+"&l"+setting.label(),
                    "&7Current: &f"+setting.current(plugin.getConfig()),
                    "&7Default: &8"+setting.defaultText(),
                    "&8"+setting.path()," ",
                    setting.toggle()?"&eLeft: Toggle":setting.number()?"&aLeft: +1    &cRight: -1":"&eClick: Edit text in chat",
                    setting.number()?"&eShift + Left: Exact number":"&7Shift + Left: Edit in chat",
                    "&cShift + Right: Reset to default"));
        }
        foot(inv,selected,max,"&9Back to Categories");
        p.openInventory(inv);
    }
    public void openPlayers(Player p, int page) {
        BrowserState state = playerBrowser.getOrDefault(p.getUniqueId(), new BrowserState(0, true, ""));
        openPlayers(p, new BrowserState(page, state.onlineOnly(), state.search()));
    }
    private void openPlayers(Player p, BrowserState requested) {
        if (!p.hasPermission("spree.admin")) return;
        List<PlayerDataStore.Profile> profiles = new ArrayList<>(plugin.data().all());
        String query = requested.search().toLowerCase(Locale.ROOT);
        profiles.removeIf(profile -> (requested.onlineOnly() && Bukkit.getPlayer(profile.id) == null)
                || (!query.isBlank() && !profile.name.toLowerCase(Locale.ROOT).contains(query)
                        && !profile.id.toString().contains(query)));
        profiles.sort(Comparator.comparing((PlayerDataStore.Profile profile) -> Bukkit.getPlayer(profile.id) == null)
                .thenComparing(profile -> profile.name, String.CASE_INSENSITIVE_ORDER));
        int max = Math.max(0, (profiles.size() - 1) / 45);
        int page = Math.max(0, Math.min(requested.page(), max));
        BrowserState state = new BrowserState(page, requested.onlineOnly(), requested.search());
        playerBrowser.put(p.getUniqueId(), state);
        Holder h = new Holder(Screen.PLAYERS, null, page, null);
        Inventory inv = create(h, 6, "&9&lSPREE &8| &fPlayer Manager");
        for (int slot = 0; slot < 45 && page * 45 + slot < profiles.size(); slot++) {
            var profile = profiles.get(page * 45 + slot);
            Player online = Bukkit.getPlayer(profile.id);
            h.players.put(slot, profile.id);
            ItemStack head = icon(Material.PLAYER_HEAD, "&f&l" + profile.name,
                    online == null ? "&8● Offline" : "&a● Online",
                    "&7Killstreak: " + plugin.indicators().streakColor(profile.streak) + plugin.streaks().display(profile.streak),
                    "&7Best: &f" + profile.best + "   &7K/D: &f" + profile.kills + "/" + profile.deaths,
                    "&eClick to manage");
            if (online != null && head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(online);
                head.setItemMeta(skull);
            }
            inv.setItem(slot, head);
        }
        inv.setItem(45, icon(Material.ARROW, "&9Previous Page", "&7Page " + (page + 1) + "/" + (max + 1)));
        inv.setItem(46, icon(state.onlineOnly() ? Material.LIME_DYE : Material.CYAN_DYE,
                "&fView: " + (state.onlineOnly() ? "&aOnline" : "&9All Saved Players"),
                "&7Show " + (state.onlineOnly() ? "offline players too" : "online players only"),
                "&eClick to switch"));
        inv.setItem(47, icon(Material.OAK_SIGN, "&9Search Players",
                "&7Search by name or UUID", "&7Current: &f" + (state.search().isBlank() ? "No filter" : state.search()),
                "&eClick to enter a search"));
        inv.setItem(48, icon(Material.MILK_BUCKET, "&7Clear Search", "&7Reset the name/UUID filter"));
        inv.setItem(49, icon(Material.ARROW, "&9Back to Admin"));
        inv.setItem(50, icon(Material.BOOK, "&f" + profiles.size() + " Players Found",
                "&7Page " + (page + 1) + " of " + (max + 1),
                "&7Online: &a" + Bukkit.getOnlinePlayers().size(),
                "&7Stored profiles: &f" + plugin.data().all().size()));
        inv.setItem(53, icon(Material.ARROW, "&9Next Page"));
        p.openInventory(inv);
    }
    public void openPlayer(Player p, UUID id) {
        if (!p.hasPermission("spree.admin")) return;
        var profile = plugin.data().get(id);
        Player target = Bukkit.getPlayer(id);
        Holder h = new Holder(Screen.PLAYER, id, 0, null);
        Inventory inv = create(h, 6, "&9&lSPREE &8| &fManage " + profile.name);
        inv.setItem(4, icon(Material.PLAYER_HEAD, "&9&l" + profile.name,
                target != null ? "&aOnline" : "&8Offline - edits are saved",
                "&7Current: " + plugin.indicators().streakColor(profile.streak) + plugin.streaks().display(profile.streak),
                "&7Personal best: &f" + profile.best,
                "&7Kills: &f" + profile.kills + " &8| &7Deaths: &f" + profile.deaths,
                "&7Streaks ended: &f" + profile.ended));
        inv.setItem(10, icon(Material.REDSTONE_BLOCK, "&c-5 Killstreak", "&7Decrease by five", "&7Minimum: 0"));
        inv.setItem(11, icon(Material.REDSTONE, "&c-1 Killstreak", "&7Decrease by one", "&7Minimum: 0"));
        inv.setItem(13, icon(Material.EMERALD, "&a+1 Killstreak", "&7Increase by one"));
        inv.setItem(14, icon(Material.EMERALD_BLOCK, "&a+5 Killstreak", "&7Increase by five"));
        inv.setItem(12, icon(Material.NETHER_STAR, "&9&lCurrent: " + plugin.indicators().streakColor(profile.streak) + plugin.streaks().display(profile.streak),
                "&7Max: &f" + plugin.streaks().cap(), "&7Click Exact Value to set a number"));
        inv.setItem(20, icon(Material.AMETHYST_SHARD, "&c&lSet Fractured Killstreak",
                "&7Marks the player as Fractured", "&7Internally preserves the legacy penalty", "&eClick to apply"));
        inv.setItem(22, icon(Material.NAME_TAG, "&9&lSet Exact Killstreak",
                "&7Enter 0 to " + plugin.streaks().cap(), "&7Use Fractured to apply its special state", "&eClick to enter in chat"));
        ConfirmPlayerReset confirm = playerResetConfirm.get(p.getUniqueId());
        boolean awaiting = confirm != null && confirm.target().equals(id) && confirm.until() > System.currentTimeMillis();
        inv.setItem(24, icon(awaiting ? Material.ORANGE_DYE : Material.BARRIER,
                awaiting ? "&eClick Again to Reset" : "&cReset Killstreak to Zero",
                "&7Only resets the current killstreak", awaiting ? "&cClick to confirm" : "&7Requires confirmation"));
        inv.setItem(28, icon(Material.GOLDEN_APPLE, "&eEdit Personal Best", "&7Current: &f" + profile.best,
                "&7Must be at least the current streak", "&eClick to enter an exact number"));
        inv.setItem(30, icon(Material.IRON_SWORD, "&fEdit Total Kills", "&7Current: &f" + profile.kills,
                "&eClick to enter an exact number"));
        inv.setItem(32, icon(Material.SKELETON_SKULL, "&fEdit Total Deaths", "&7Current: &f" + profile.deaths,
                "&eClick to enter an exact number"));
        inv.setItem(34, icon(Material.BOOK, "&fEdit Streaks Ended", "&7Current: &f" + profile.ended,
                "&eClick to enter an exact number"));
        inv.setItem(40, icon(Material.CLOCK, "&aRefresh This Player",
                target == null ? "&7Changes take effect on next login" : "&7Reapply health and nameplates"));
        inv.setItem(42, icon(Material.HEART_OF_THE_SEA, "&9&lGive Spree Item",
                target == null ? "&8Player must be online" : "&7Right-click item for +1 killstreak",
                "&eClick: give 1 &8| &eShift-click: give 5"));
        inv.setItem(49, icon(Material.ARROW, "&9Back to Players"));
        inv.setItem(53, icon(Material.BARRIER, "&cClose"));
        p.openInventory(inv);
    }
    public void openEvents(Player p,boolean admin) {
        if (!p.hasPermission("spree.admin")) return;
        Holder h=holder(Screen.EVENTS);Inventory inv=create(h,3,"&9&lSPREE &8| &fEvents");
        inv.setItem(10,icon(Material.COMPASS,"&6The Hunt", "&7Status: &f"+(plugin.events().mode()==EventManager.Mode.HUNT?"ON":"OFF")));
        inv.setItem(11,icon(Material.NETHERITE_CHESTPLATE,"&cJuggernaut", "&7Status: &f"+(plugin.events().mode()==EventManager.Mode.JUGGERNAUT?"ON":"OFF")));
        inv.setItem(12,icon(Material.ENDER_EYE,"&9Locator Bar", "&7Status: &f"+(plugin.events().locatorEnabled()?"ON":"OFF")));
        inv.setItem(13,icon(Material.DIAMOND_SWORD,"&cBlood's Bane", "&7Status: &f"+(plugin.events().bloodsBaneEnabled()?"ON":"OFF")));
        inv.setItem(14,icon(Material.RED_STAINED_GLASS_PANE,"&cBlood Moon", "&7Bonus killstreak rewards"));
        inv.setItem(15,icon(Material.GRAY_STAINED_GLASS_PANE,"&8Spree Surge (Paused)", "&7Waiting for progression redesign"));
        inv.setItem(16,icon(Material.PURPLE_STAINED_GLASS_PANE,"&5Chaos", "&7Faster legendary cooldowns"));
        inv.setItem(17,icon(Material.ORANGE_STAINED_GLASS_PANE,"&6Kill Race", "&7First to the target wins"));
        inv.setItem(20,icon(Material.CLOCK,"&eTimed Event: &f"+plugin.events().timed(),"&7Remaining: "+plugin.events().timedSecondsRemaining()+"s"));
        inv.setItem(22,icon(Material.BARRIER,"&cStop Timed Event"));
        inv.setItem(24,icon(Material.BARRIER,"&cStop Compass Event"));
        inv.setItem(26,icon(Material.ARROW,"&cBack"));
        p.openInventory(inv);
    }
    public void openLegendary(Player p) {
        if (!p.hasPermission("spree.admin")) return;
        Holder h=holder(Screen.LEGENDARY);Inventory inv=create(h,3,"&9&lSPREE &8| &fLegendaries");
        LegendaryType[] types=LegendaryType.values();
        for(int i=0;i<types.length;i++) {
            LegendaryType t=types[i];
            inv.setItem(10+i,icon(t.material,"&9&l"+t.display,"&7Forge: &f"+plugin.forges().status(t),
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
                    case 10 -> plugin.progressMenus().open(p);
                    case 12 -> plugin.progressMenus().openStats(p);
                    case 14 -> p.performCommand("leaderboard");
                    case 16 -> plugin.progressMenus().openGuide(p);
                    case 19 -> plugin.progressMenus().open(p);
                    case 21 -> plugin.progressMenus().openSlots(p, ProgressionCatalog.Kind.PERK, false);
                    case 23 -> { p.performCommand("spreehud"); openMain(p); }
                    case 25 -> plugin.progressMenus().openWanted(p);
                    case 29 -> { if(p.hasPermission("spree.admin")) openAdmin(p); }
                    case 33 -> { if(p.hasPermission("spree.admin")) openSettings(p); }
                    case 31 -> p.closeInventory();
                    default -> {}
                }
            }
            case ADMIN -> {
                if(!p.hasPermission("spree.admin"))return;
                switch(slot) {
                    case 10 -> openPlayers(p,0);
                    case 12 -> openEvents(p,true);
                    case 14 -> openLegendary(p);
                    case 16 -> openConfigCategory(p,"combat",0);
                    case 19 -> openSettings(p);
                    case 21 -> {plugin.streaks().refreshOnline();Text.send(p,"&aRecalculated health for all online players.");}
                    case 23 -> {
                        long now=System.currentTimeMillis();
                        if(resetConfirm.getOrDefault(p.getUniqueId(),0L)>now) {
                            resetConfirm.remove(p.getUniqueId());
                            for(var profile:List.copyOf(plugin.data().all()))plugin.streaks().setStreak(profile.id,0,true);
                            Bukkit.broadcastMessage(Text.c("&9&lSPREE &7All killstreaks have been reset."));
                            p.closeInventory();
                        } else {resetConfirm.put(p.getUniqueId(),now+5000);Text.send(p,"&cClick again within 5 seconds to confirm.");}
                    }
                    case 25 -> plugin.progressMenus().openGuide(p);
                    case 29 -> openMain(p);
                    case 31 -> p.closeInventory();
                    case 33 -> plugin.balanceMenu().open(p);
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
                if (!p.hasPermission("spree.admin")) return;
                BrowserState state = playerBrowser.getOrDefault(p.getUniqueId(), new BrowserState(0, true, ""));
                if (slot == 45) openPlayers(p, h.page - 1);
                else if (slot == 53) openPlayers(p, h.page + 1);
                else if (slot == 49) openAdmin(p);
                else if (slot == 46) openPlayers(p, new BrowserState(0, !state.onlineOnly(), state.search()));
                else if (slot == 48) openPlayers(p, new BrowserState(0, state.onlineOnly(), ""));
                else if (slot == 47) {
                    searchEditing.add(p.getUniqueId());
                    p.closeInventory();
                    Text.send(p, plugin.prefix() + "&9Enter a player name or UUID in chat.");
                    Text.send(p, "&7Use &fall &7to clear search or &ccancel &7to return.");
                }
                else if (h.players.containsKey(slot)) openPlayer(p, h.players.get(slot));
            }
            case PLAYER -> {
                if (!p.hasPermission("spree.admin") || h.target == null) return;
                if (slot == 49) { openPlayers(p, playerBrowser.getOrDefault(p.getUniqueId(), new BrowserState(0, true, ""))); return; }
                if (slot == 53) { p.closeInventory(); return; }
                var profile = plugin.data().get(h.target);
                Integer changed = switch(slot) {
                    case 10 -> profile.streak < 0 ? -1 : Math.max(0, profile.streak - 5);
                    case 11 -> profile.streak < 0 ? -1 : Math.max(0, profile.streak - 1);
                    case 13 -> profile.streak + 1;
                    case 14 -> profile.streak + 5;
                    default -> null;
                };
                if (changed != null) {
                    if (changed != profile.streak) plugin.streaks().setStreak(h.target, changed, changed >= 0);
                    playerResetConfirm.remove(p.getUniqueId());
                    openPlayer(p, h.target);
                    return;
                }
                switch(slot) {
                    case 20 -> {
                        plugin.streaks().setStreak(h.target, -1, false);
                        playerResetConfirm.remove(p.getUniqueId());
                        openPlayer(p, h.target);
                    }
                    case 22 -> promptStat(p, h.target, PlayerField.STREAK);
                    case 24 -> {
                        ConfirmPlayerReset confirm = playerResetConfirm.get(p.getUniqueId());
                        if (confirm != null && confirm.target().equals(h.target)
                                && confirm.until() > System.currentTimeMillis()) {
                            plugin.streaks().setStreak(h.target, 0, true);
                            playerResetConfirm.remove(p.getUniqueId());
                            Text.send(p, plugin.prefix() + "&aReset " + profile.name + "'s killstreak to zero.");
                        } else {
                            playerResetConfirm.put(p.getUniqueId(),
                                    new ConfirmPlayerReset(h.target, System.currentTimeMillis() + 6000L));
                        }
                        openPlayer(p, h.target);
                    }
                    case 28 -> promptStat(p, h.target, PlayerField.BEST);
                    case 30 -> promptStat(p, h.target, PlayerField.KILLS);
                    case 32 -> promptStat(p, h.target, PlayerField.DEATHS);
                    case 34 -> promptStat(p, h.target, PlayerField.ENDED);
                    case 42 -> {
                        Player online = Bukkit.getPlayer(h.target);
                        if (online == null) Text.send(p, plugin.prefix() + "&cPlayer must be online.");
                        else {
                            int amount = click.isShiftClick() ? 5 : 1;
                            plugin.streaks().giveSpreeItem(online, amount);
                            Text.send(p, plugin.prefix() + "&aGave " + amount + " Spree item(s) to " + online.getName() + ".");
                        }
                        openPlayer(p, h.target);
                    }
                    case 40 -> {
                        Player online = Bukkit.getPlayer(h.target);
                        if (online != null) {
                            plugin.streaks().update(online);
                            plugin.indicators().refreshPlayer(online);
                            Text.send(p, plugin.prefix() + "&aUpdated " + profile.name + "'s live effects.");
                        } else Text.send(p, plugin.prefix() + "&7Offline: effects will update when the player logs in.");
                        openPlayer(p, h.target);
                    }
                    default -> {}
                }
            }
            case EVENTS -> {
                if (!p.hasPermission("spree.admin")) return;
                if(slot==26){openAdmin(p);return;}
                switch(slot) {
                    case 10 -> plugin.events().setMode(plugin.events().mode()==EventManager.Mode.HUNT?EventManager.Mode.NONE:EventManager.Mode.HUNT);
                    case 11 -> plugin.events().setMode(plugin.events().mode()==EventManager.Mode.JUGGERNAUT?EventManager.Mode.NONE:EventManager.Mode.JUGGERNAUT);
                    case 12 -> plugin.events().setLocator(!plugin.events().locatorEnabled());
                    case 13 -> plugin.events().setBloodsBane(!plugin.events().bloodsBaneEnabled());
                    case 14 -> plugin.events().setTimed(EventManager.Timed.BLOODMOON);
                    case 15 -> Text.send(p, "&7Spree Surge is paused until the new progression system is ready.");
                    case 16 -> plugin.events().setTimed(EventManager.Timed.CHAOS);
                    case 17 -> plugin.events().setTimed(EventManager.Timed.RACE);
                    case 22 -> plugin.events().setTimed(EventManager.Timed.NONE);
                    case 24 -> plugin.events().setMode(EventManager.Mode.NONE);
                    default -> {}
                }
                openEvents(p,true);
            }
            case LEGENDARY -> {
                if (!p.hasPermission("spree.admin")) return;
                if(slot==26){openAdmin(p);return;}
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
            Text.send(p,plugin.prefix()+"&9Editing &f"+setting.label()+"&9.");
            Text.send(p,"&7Current: &f"+setting.current(plugin.getConfig())+" &8| &7Default: &f"+setting.defaultText());
            Text.send(p,"&eEnter the new value in chat, or use &f/spree value <value>&e.");
            if (path.startsWith("combat.") && path.endsWith("multiplier"))
                Text.send(p,"&7Examples: &975% &7or &90.75 &8(maximum 100%)");
            Text.send(p,"&7Type &ccancel &7to return without changes.");
            return;
        }
        if(click!=ClickType.LEFT && click!=ClickType.RIGHT)return;
        double step=(path.startsWith("combat.") && path.endsWith("multiplier")) ? 0.05
                : path.startsWith("perks.") && path.endsWith(".per-copy")
                        && !path.equals("perks.capacity.per-copy") ? 0.01
                : (setting.defaultValue() instanceof Double || setting.defaultValue() instanceof Float) ? 0.1 : 1;
        Number current=(Number)plugin.getConfig().get(path,setting.defaultValue());
        double changed=current.doubleValue()+(click==ClickType.RIGHT?-step:step);
        String raw=setting.defaultValue() instanceof Integer||setting.defaultValue() instanceof Long
                ?String.valueOf((long)changed):String.format(Locale.ROOT,"%.4f",changed);
        report(p,plugin.settings().set(path,raw));
        openConfigCategory(p,h.category,h.page);
    }
    private void report(Player p,ConfigEditor.Result r) {Text.send(p,plugin.prefix()+(r.success()?"&a":"&c")+r.message());}
    private void promptStat(Player admin, UUID target, PlayerField field) {
        statEditing.put(admin.getUniqueId(), new PendingStatEdit(target, field));
        admin.closeInventory();
        Text.send(admin, plugin.prefix() + "&9Editing " + field.name().toLowerCase(Locale.ROOT)
                + " &7for &f" + plugin.data().get(target).name + "&7.");
        Text.send(admin, "&eEnter an exact number in chat (or /spree value <number>).");
        Text.send(admin, "&7Type &ccancel &7to return without changes.");
    }
    private boolean submitStat(Player admin, PendingStatEdit pending, String input) {
        int value;
        try { value = Integer.parseInt(input.trim()); }
        catch (NumberFormatException ex) {
            Text.send(admin, "&cEnter a whole number (or cancel). Try again.");
            return true;
        }
        var profile = plugin.data().get(pending.target());
        int low = 0;
        int high = pending.field() == PlayerField.STREAK ? plugin.streaks().cap() : 100000000;
        if (value < low || value > high) {
            Text.send(admin, "&cAllowed range: " + low + " to " + high + ". Try again.");
            return true;
        }
        if (pending.field() == PlayerField.BEST && value < Math.max(0, profile.streak)) {
            Text.send(admin, "&cPersonal best cannot be lower than the current streak. Try again.");
            return true;
        }
        statEditing.remove(admin.getUniqueId());
        switch (pending.field()) {
            case STREAK -> plugin.streaks().setStreak(pending.target(), value, value >= 0);
            case BEST -> profile.best = value;
            case KILLS -> profile.kills = value;
            case DEATHS -> profile.deaths = value;
            case ENDED -> profile.ended = value;
        }
        plugin.data().dirty();
        Text.send(admin, plugin.prefix() + "&aUpdated " + profile.name + "'s "
                + pending.field().name().toLowerCase(Locale.ROOT) + " to " + value + ".");
        openPlayer(admin, pending.target());
        return true;
    }
    public boolean submitInput(Player p, String input) {
        UUID id = p.getUniqueId();
        if (!editing.containsKey(id) && !statEditing.containsKey(id) && !searchEditing.contains(id)) return false;
        if (!p.hasPermission("spree.admin")) {
            editing.remove(id); statEditing.remove(id); searchEditing.remove(id);
            Text.send(p, "&cPermission required.");
            return true;
        }
        if (searchEditing.remove(id)) {
            BrowserState state = playerBrowser.getOrDefault(id, new BrowserState(0, true, ""));
            if (!input.equalsIgnoreCase("cancel")) {
                String query = input.trim().equalsIgnoreCase("all") ? "" : input.trim();
                if (query.length() > 48) { Text.send(p, "&cSearch limited to 48 characters."); query = ""; }
                openPlayers(p, new BrowserState(0, state.onlineOnly(), query));
            } else openPlayers(p, state);
            return true;
        }
        PendingStatEdit stat = statEditing.get(id);
        if (stat != null) {
            if (input.equalsIgnoreCase("cancel")) {
                statEditing.remove(id);openPlayer(p, stat.target());return true;
            }
            return submitStat(p, stat, input);
        }
        PendingEdit pending = editing.remove(id);
        if (pending == null) return false;
        if (input.equalsIgnoreCase("cancel")) {
            Text.send(p, "&7Edit cancelled.");
            openConfigCategory(p, pending.category, pending.page);
            return true;
        }
        report(p, plugin.settings().set(pending.path, input));
        openConfigCategory(p, pending.category, pending.page);
        return true;
    }
    @SuppressWarnings("deprecation")
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void chat(AsyncPlayerChatEvent event) {
        UUID playerId=event.getPlayer().getUniqueId();
        if(!editing.containsKey(playerId) && !statEditing.containsKey(playerId)
                && !searchEditing.contains(playerId))return;
        event.setCancelled(true);
        String message=event.getMessage();
        Bukkit.getScheduler().runTask(plugin,()->{
            Player player=Bukkit.getPlayer(playerId);
            if(player!=null && player.isOnline())submitInput(player,message);
            else { editing.remove(playerId); statEditing.remove(playerId); searchEditing.remove(playerId); }
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        editing.remove(id);resetConfirm.remove(id);
        statEditing.remove(id); searchEditing.remove(id); playerBrowser.remove(id);
        playerResetConfirm.remove(id);
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);
    }
}
