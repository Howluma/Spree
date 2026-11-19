package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Symmetric, calm progression UX. Unlocks are redeemed from /spree, never
 * through a pop-up on kill. Menus are independent of the operator control GUI.
 */
public final class ProgressionMenu implements Listener {
    private enum Screen {
        HOME, SLOTS, CHOOSE, PERKS, SIGIL_HOME, SIGIL_SLOTS, SIGIL_CHOOSE,
        SIGIL_CONFIRM, GUIDE, GUIDE_KIND, WANTED, STATS
    }
    private static final class Holder implements InventoryHolder {
        final Screen screen;
        final ProgressionCatalog.Kind kind;
        final int milestone;
        final Map<Integer,String> choices = new HashMap<>();
        final Map<Integer,Integer> levels = new HashMap<>();
        Inventory inventory;
        Holder(Screen screen, ProgressionCatalog.Kind kind, int milestone) {
            this.screen = screen; this.kind = kind; this.milestone = milestone;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final SpreeCorePlugin plugin;
    ProgressionMenu(SpreeCorePlugin plugin) { this.plugin = plugin; }

    private Holder holder(Screen screen, ProgressionCatalog.Kind kind, int milestone) {
        return new Holder(screen, kind, milestone);
    }
    private Inventory inventory(Holder holder, int rows, String title) {
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.c(title));
        holder.inventory = inv;
        for (int i = 0; i < inv.getSize(); i++) {
            Material glass = i % 9 == 0 || i % 9 == 8
                    ? Material.BLUE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
            inv.setItem(i, icon(glass, "&8"));
        }
        return inv;
    }
    private ItemStack icon(Material type, String label, String... lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.c(label));
        meta.setLore(Arrays.stream(lore).map(Text::c).toList());
        if (type == Material.ECHO_SHARD) plugin.models().sigil(meta);
        if (type == Material.HEART_OF_THE_SEA) plugin.models().token(meta);
        if (type == Material.POTION && meta instanceof PotionMeta potion) {
            String name = label.toLowerCase(Locale.ROOT);
            int rgb = name.contains("speed") ? 0x7CAFC6 : name.contains("strength") ? 0x932423 :
                    name.contains("weaving") ? 0xB9B9B9 : name.contains("regeneration") || name.contains("medic") ? 0xCD5CAB :
                    name.contains("luck") ? 0x339900 : name.contains("hero") ? 0x44BB66 : 0x4466CC;
            potion.setColor(Color.fromRGB(rgb));
        }
        item.setItemMeta(meta);
        return item;
    }
    private void controls(Inventory inv, String back, boolean close) {
        inv.setItem(inv.getSize() - 9, icon(Material.ARROW, "&9&lBack", "&7" + back));
        inv.setItem(inv.getSize() - 5, icon(Material.NETHER_STAR, "&9&lProgression Home", "&7Your upgrades and unlocks"));
        if (close) inv.setItem(inv.getSize() - 1, icon(Material.BARRIER, "&cClose"));
    }
    private void status(Inventory inv, int index, String text) {
        inv.setItem(index, icon(Material.BOOK, "&f" + text));
    }

    public void open(Player player) {
        Holder h = holder(Screen.HOME, null, 0);
        Inventory inv = inventory(h, 3, "&9&lSPREE &8| &fProgression");
        inv.setItem(4, icon(Material.HEART_OF_THE_SEA, "&9&lYour Build",
                "&7Killstreak: &f" + plugin.streaks().display(plugin.streaks().streak(player)),
                "&7Choose rewards here, without combat pop-ups"));
        ProgressionCatalog.Kind[] kinds = ProgressionCatalog.Kind.values();
        int[] cells = {10, 12, 14, 16};
        for (int i=0; i<kinds.length; i++) {
            var kind = kinds[i];
            int count = plugin.progression().available(player, kind);
            if (kind == ProgressionCatalog.Kind.PERK) count = 0;
            inv.setItem(cells[i], icon(kind.icon, "&9&l" + kind.title,
                    kind == ProgressionCatalog.Kind.PERK ? "&7Rolled automatically" :
                            "&7Unclaimed: &f" + count,
                    "&eClick to inspect " + kind.title.toLowerCase(Locale.ROOT)));
        }
        inv.setItem(22, icon(Material.ECHO_SHARD, "&b&lFate Sigil",
                "&7Reroll saved upgrades with a crafted Sigil",
                "&7Recipe: 4 Ominous Trial Keys, 4 Netherite Ingots,",
                "&7Real Spree item in the center",
                "&eClick to see reroll options"));
        inv.setItem(18, icon(Material.ARROW, "&9Back to Dashboard"));
        inv.setItem(26, icon(Material.BARRIER, "&cClose"));
        player.openInventory(inv);
    }

    private int[] milestonesSlots() { return new int[]{11, 13, 15, 29, 31, 33}; }
    private String coloredStatus(boolean unlocked, String current, boolean reroll) {
        if (!unlocked) return "&8LOCKED";
        if (current != null) return reroll ? "&eCHANGE THIS CHOICE" : "&aCLAIMED";
        return reroll ? "&8No saved choice" : "&bREADY TO CLAIM";
    }

    public void openSlots(Player player, ProgressionCatalog.Kind kind, boolean reroll) {
        if (kind == ProgressionCatalog.Kind.PERK && !reroll) { openPerks(player); return; }
        if (kind == ProgressionCatalog.Kind.PERK) { openConfirm(player, kind, 0); return; }
        Holder h = holder(reroll ? Screen.SIGIL_SLOTS : Screen.SLOTS, kind, 0);
        Inventory inv = inventory(h, 6, "&9&l" + kind.title + (reroll ? " &8| &fReselect" : " &8| &fMilestones"));
        List<Integer> levels = ProgressionCatalog.milestones(kind, plugin.streaks().progressionCap());
        int[] cells = milestonesSlots();
        for (int i=0; i<Math.min(6, levels.size()); i++) {
            int level = levels.get(i);
            boolean unlocked = plugin.progression().milestoneUnlocked(player, kind, level);
            String current = plugin.progression().choice(player, kind, level);
            if (!unlocked) continue;
            var option = ProgressionCatalog.find(kind, current);
            Material material = option != null ? option.icon()
                    : unlocked ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(cells[i], icon(material, "&9&lLevel " + level,
                    coloredStatus(unlocked, current, reroll),
                    option != null ? "&f" + option.name() : "&7Not chosen yet",
                    option != null ? "&7" + option.description() : "&8One choice per milestone",
                    unlocked ? (current != null && !reroll ? "&aAlready claimed - hover to inspect" :
                            "&eClick to " + (reroll ? "rechoose" : "choose")) : "&8Reach level " + level + " first"));
            h.levels.put(cells[i], level);
        }
        inv.setItem(22, icon(kind.icon, "&9&l" + kind.title,
                "&7One unlock at each listed milestone",
                reroll ? "&7Requires 1 Fate Sigil" : "&7Choices save between deaths and logins"));
        controls(inv, reroll ? "Fate Sigil" : "Progression", true);
        player.openInventory(inv);
    }

    private void openPerks(Player player) {
        Holder h = holder(Screen.PERKS, ProgressionCatalog.Kind.PERK, 0);
        Inventory inv = inventory(h, 6, "&9&lSPREE &8| &fYour Perks");
        int[] slots = {10,12,14,16,19,21,23,25,28,30,32,34};
        int index = 0;
        for (int level : ProgressionCatalog.milestones(ProgressionCatalog.Kind.PERK, plugin.streaks().progressionCap())) {
            if (index >= slots.length) break;
            String id = plugin.data().get(player).perkRolls.get(level);
            if (id == null || level > plugin.streaks().streak(player)) continue;
            PerkType perk = PerkType.fromId(id);
            if (perk == null) continue;
            List<String> lore = new ArrayList<>();
            lore.add("&7Unlocked at killstreak &b" + level);
            lore.addAll(Arrays.asList(plugin.perks().detailLore(perk, plugin.data().get(player))));
            lore.add("&aPerk earned and active");
            lore.add("&8Duplicates stack; perks stop at level 30");
            inv.setItem(slots[index++], icon(perk.icon(), "&9&l" + perk.display + " &8(Lv " + level + ")",
                    lore.toArray(String[]::new)));
        }
        inv.setItem(40, icon(Material.ECHO_SHARD, "&b&lReroll All Perks",
                "&7Use 1 Fate Sigil to reroll",
                "&7all previously earned Perk milestones",
                "&eClick to open the Fate Sigil menu"));
        controls(inv, "Progression", true);
        player.openInventory(inv);
    }

    private void openChoices(Player player, ProgressionCatalog.Kind kind, int level, boolean sigil) {
        Holder h = holder(sigil ? Screen.SIGIL_CHOOSE : Screen.CHOOSE, kind, level);
        Inventory inv = inventory(h, kind == ProgressionCatalog.Kind.TALENT ? 3 : 6, "&9&lLevel " + level + " &8| &fChoose " + kind.title);
        List<String> optionIds = new ArrayList<>();
        if (kind == ProgressionCatalog.Kind.TALENT) optionIds.addAll(plugin.progression().talentOffers(player, level));
        else for (var option : plugin.progression().availableOptions(player, kind, level, sigil))
            optionIds.add(option.id());
        int[] three = {11, 13, 15};
        int[] six = {11, 13, 15, 29, 31, 33};
        int[] cells = kind == ProgressionCatalog.Kind.TALENT ? three : six;
        int index = 0;
        for (String id : optionIds) {
            if (index >= cells.length) break;
            var option = ProgressionCatalog.find(kind, id);
            if (option == null) continue;
            int slot = cells[index++];
            inv.setItem(slot, icon(option.icon(), "&b&l" + option.name(),
                    "&7" + option.description(),
                    sigil ? "&cConsumes 1 Fate Sigil" : "&aClick to claim this upgrade"));
            h.choices.put(slot, option.id());
        }
        if (optionIds.isEmpty()) inv.setItem(13, icon(Material.BARRIER, "&cNo alternative choices",
                "&7You already own every available option"));
        if (inv.getSize() == 54) controls(inv, "Milestones", true);
        else {
            inv.setItem(18, icon(Material.ARROW, "&9Back to Milestones"));
            inv.setItem(26, icon(Material.BARRIER, "&cClose"));
        }
        player.openInventory(inv);
    }

    public void openSigil(Player player) {
        Holder h = holder(Screen.SIGIL_HOME, null, 0);
        Inventory inv = inventory(h, 3, "&9&lFate Sigil &8| &fReforging");
        inv.setItem(4, icon(Material.ECHO_SHARD, "&9&lUniversal Rerolls",
                "&7Select a category. A Sigil is consumed only",
                "&7when you commit a new result."));
        int[] slots={10,12,14,16};
        var kinds=ProgressionCatalog.Kind.values();
        for (int i=0;i<kinds.length;i++) {
            var kind = kinds[i];
            inv.setItem(slots[i], icon(kind.icon, "&b&l" + (kind==ProgressionCatalog.Kind.PERK
                            ? "Reroll All Perks" : "Reselect " + kind.title),
                    kind == ProgressionCatalog.Kind.TALENT ? "&7Receive 3 new Talent offers" :
                            kind == ProgressionCatalog.Kind.PERK ? "&7Rebuild all earned Perks" :
                            "&7Choose a different unlocked " + kind.title.substring(0,kind.title.length()-1),
                    "&eClick to continue"));
        }
        inv.setItem(22, icon(Material.EMERALD, "&fSigils in inventory: &b"
                + (plugin.fate().has(player) ? "Available" : "None"),
                "&7Recipe: 4 Ominous Trial Keys, 4 Netherite Ingots,",
                "&7Real Spree item in the center"));
        inv.setItem(18, icon(Material.ARROW, "&9Back to Progression"));
        inv.setItem(26, icon(Material.BARRIER, "&cClose"));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, ProgressionCatalog.Kind kind, int level) {
        Holder h = holder(Screen.SIGIL_CONFIRM, kind, level);
        Inventory inv = inventory(h, 3, "&9&lFate Sigil &8| &fConfirm");
        inv.setItem(11, icon(Material.ARROW, "&7Cancel", "&7Keep your current upgrades"));
        inv.setItem(13, icon(kind.icon, "&b&lConfirm Reroll",
                kind==ProgressionCatalog.Kind.PERK ? "&7All earned Perks will be rerolled" :
                        "&7Level " + level + " Talent will be replaced",
                "&7You will choose from 3 new Talent offers",
                "&cConsumes 1 Fate Sigil"));
        inv.setItem(15, icon(Material.LIME_CONCRETE, "&a&lConfirm",
                "&7Consumes exactly 1 Fate Sigil",
                "&eClick to proceed"));
        player.openInventory(inv);
    }

    public void openGuide(Player player) {
        Holder h = holder(Screen.GUIDE, null, 0);
        Inventory inv = inventory(h, 3, "&9&lSPREE &8| &fGuide");
        var kinds=ProgressionCatalog.Kind.values();
        int[] cells={10,12,14,16};
        for (int i=0;i<kinds.length;i++) {
            var kind=kinds[i];
            inv.setItem(cells[i], icon(kind.icon, "&9&l" + kind.title,
                    switch(kind) {
                        case PRIVILEGE -> "&7Restricted items and use permissions";
                        case PERK -> "&7Small automatically rolled bonuses";
                        case TALENT -> "&7Choose 1 of 3 powerful passive abilities";
                        case INFUSION -> "&7Permanent effects you pick yourself";
                    }, "&eClick for abilities and details"));
        }
        inv.setItem(22, icon(Material.COMPASS, "&c&lWanted & Hunt",
                "&7Higher streaks make you a more valuable target",
                "&eClick to learn each milestone penalty"));
        inv.setItem(18, icon(Material.ARROW, "&9Back to Dashboard"));
        inv.setItem(26, icon(Material.BARRIER, "&cClose"));
        player.openInventory(inv);
    }

    public void openGuideKind(Player player, ProgressionCatalog.Kind kind) {
        Holder h=holder(Screen.GUIDE_KIND,kind,0);
        Inventory inv=inventory(h,6,"&9&lGuide &8| &f"+kind.title);
        if (kind == ProgressionCatalog.Kind.PERK) {
            List<PerkType> all = new ArrayList<>();
            for (var entry : plugin.data().get(player).perkRolls.entrySet()) {
                if (entry.getKey() > plugin.streaks().streak(player)) continue;
                PerkType perk = PerkType.fromId(entry.getValue());
                if (perk != null && !all.contains(perk)) all.add(perk);
            }
            int[] guideCells={10,12,14,16,19,21,23,25,28,30,32,34,37,39,41,43};
            for (int i=0; i<Math.min(guideCells.length,all.size()); i++) {
                int slot=guideCells[i];
                var perk=all.get(i);
                List<String> lore = new ArrayList<>(
                        Arrays.asList(plugin.perks().detailLore(perk, plugin.data().get(player))));
                lore.add("&7Automatically rolled at Perk milestones");
                inv.setItem(slot, icon(perk.icon(), "&9&l" + perk.display,
                        lore.toArray(String[]::new)));
            }
        } else {
            List<ProgressionCatalog.Option> all=ProgressionCatalog.options(kind);
            if (kind == ProgressionCatalog.Kind.TALENT) {
                Set<String> known = new HashSet<>();
                for (var entry : plugin.data().get(player).talentChoices.entrySet())
                    if (entry.getKey() <= plugin.streaks().streak(player)) known.add(entry.getValue());
                all = all.stream().filter(option -> known.contains(option.id())).toList();
            }
            int[] guideCells={11,13,15,20,22,24,29,31,33,38,40,42};
            for (int i=0; i<Math.min(guideCells.length,all.size()); i++) {
                int slot=guideCells[i];
                var option=all.get(i);
                inv.setItem(slot, icon(option.icon(),"&9&l"+option.name(),
                        "&7"+option.description(), "&7"+kind.title+" only"));
            }
        }
        controls(inv,"Guide",true);
        player.openInventory(inv);
    }

    public void openWanted(Player player) {
        Holder h=holder(Screen.WANTED,null,0);
        Inventory inv=inventory(h,3,"&9&lSPREE &8| &fWanted Levels");
        inv.setItem(10,icon(Material.FLINT_AND_STEEL,"&a&l5 - On Fire",
                "&7Global chat announcement", "&7Eliminating this player: +2 Spree"));
        inv.setItem(12,icon(Material.GLOWSTONE_DUST,"&e&l10 - Unstoppable",
                "&7Permanent tier-colored glowing", "&7Eliminating this player: +3 Spree"));
        inv.setItem(14,icon(Material.COMPASS,"&6&l15 - Hunted",
                "&7Global coordinates every 15 minutes", "&7Eliminating this player: +4 Spree"));
        inv.setItem(16,icon(Material.SHIELD,"&c&l20 - Lone Wolf",
                "&7Cannot join vanilla scoreboard teams", "&7Eliminating this player: +5 Spree"));
        inv.setItem(20,icon(Material.NETHERITE_SWORD,"&5&l25 - Most Wanted",
                "&7Global chat announcement", "&7Eliminating this player: +6 Spree"));
        inv.setItem(22,icon(Material.DRAGON_EGG,"&4&l30 - Legendary",
                "&7Global chat announcement", "&7Eliminating this player: +7 Spree"));
        inv.setItem(24,icon(Material.HEART_OF_THE_SEA,"&9&lBounties",
                "&7Victim streak 0-4: +1; every next five",
                "&7levels increase the reward by +1",
                "&7Natural-death tokens pay the same tier"));
        inv.setItem(18,icon(Material.ARROW,"&9Back to Guide"));
        inv.setItem(26,icon(Material.BARRIER,"&cClose"));
        player.openInventory(inv);
    }

    public void openStats(Player player) {
        var p=plugin.data().get(player);
        Holder h=holder(Screen.STATS,null,0);
        Inventory inv=inventory(h,3,"&9&lSPREE &8| &fStatistics");
        inv.setItem(11,icon(Material.NETHER_STAR,"&9&lCurrent Killstreak",
                "&f"+plugin.streaks().display(p.streak), "&7Highest: &f"+p.best));
        inv.setItem(13,icon(Material.DIAMOND_SWORD,"&b&lCombat",
                "&7Kills: &f"+p.kills, "&7Deaths: &f"+p.deaths,
                "&7Killstreaks ended: &f"+p.ended));
        inv.setItem(15,icon(Material.ENCHANTED_BOOK,"&9&lYour Progression",
                "&7Privileges: &f"+p.privilegeChoices.size(),
                "&7Talents: &f"+p.talentChoices.size(),
                "&7Infusions: &f"+p.infusionChoices.size(),
                "&7Perk rolls earned: &f"+p.perkRolls.size()));
        inv.setItem(18,icon(Material.ARROW,"&9Back to Dashboard"));
        inv.setItem(26,icon(Material.BARRIER,"&cClose"));
        player.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot=event.getRawSlot();
        if (slot < 0 || slot >= h.inventory.getSize()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder()!=h) return;
            selected(player,h,slot);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    private void say(Player player, ProgressionManager.Result result) {
        Text.send(player, plugin.prefix()+(result.ok()?"&a":"&c")+result.message());
    }
    private void selected(Player player, Holder h, int slot) {
        if (slot==h.inventory.getSize()-1) { player.closeInventory(); return; }
        if (slot==18 && h.inventory.getSize()==27) {
            switch(h.screen) {
                case HOME, GUIDE, STATS -> plugin.menus().openMain(player);
                case SIGIL_HOME -> open(player);
                case CHOOSE -> openSlots(player,h.kind,false);
                case SIGIL_CHOOSE -> openSlots(player,h.kind,true);
                case SIGIL_CONFIRM -> {
                    if (h.kind == ProgressionCatalog.Kind.PERK) openSigil(player);
                    else openSlots(player,h.kind,true);
                }
                case WANTED -> openGuide(player);
                default -> open(player);
            }
            return;
        }
        if (h.inventory.getSize()==54) {
            if(slot==45 || slot==49) {
                switch(h.screen) {
                    case GUIDE_KIND -> openGuide(player);
                    case SIGIL_SLOTS -> openSigil(player);
                    case CHOOSE -> openSlots(player,h.kind,false);
                    case SIGIL_CHOOSE -> openSlots(player,h.kind,true);
                    default -> open(player);
                }
                return;
            }
            if(slot==53) {player.closeInventory();return;}
        }
        switch(h.screen) {
            case HOME -> {
                if (slot==22) { openSigil(player); return; }
                ProgressionCatalog.Kind kind=homeKind(slot);
                if(kind!=null)openSlots(player,kind,false);
            }
            case GUIDE -> {
                if(slot==22) {openWanted(player);return;}
                ProgressionCatalog.Kind kind=homeKind(slot);
                if(kind!=null)openGuideKind(player,kind);
            }
            case SLOTS, SIGIL_SLOTS -> {
                Integer level=h.levels.get(slot);
                if(level==null) return;
                boolean reroll=h.screen==Screen.SIGIL_SLOTS;
                if(!plugin.progression().milestoneUnlocked(player,h.kind,level)) {
                    Text.action(player,"&cReach killstreak "+level+" first."); return;
                }
                String existing=plugin.progression().choice(player,h.kind,level);
                if(reroll) {
                    if(existing==null) {Text.action(player,"&cClaim this upgrade first.");return;}
                    if(h.kind==ProgressionCatalog.Kind.TALENT)openConfirm(player,h.kind,level);
                    else openChoices(player,h.kind,level,true);
                } else {
                    if(existing!=null) {Text.action(player,"&aUpgrade claimed. &7Hover over the slot to read its effect.");return;}
                    openChoices(player,h.kind,level,false);
                }
            }
            case CHOOSE, SIGIL_CHOOSE -> {
                String id=h.choices.get(slot);
                if(id==null)return;
                ProgressionManager.Result result=plugin.progression().claim(player,h.kind,h.milestone,id,
                        h.screen==Screen.SIGIL_CHOOSE);
                say(player,result);
                if(result.ok())openSlots(player,h.kind,false);
            }
            case PERKS -> {
                if(slot==40) openSigil(player);
            }
            case SIGIL_HOME -> {
                ProgressionCatalog.Kind kind=homeKind(slot);
                if(kind!=null)openSlots(player,kind,true);
            }
            case SIGIL_CONFIRM -> {
                if (slot == 11) {
                    if (h.kind == ProgressionCatalog.Kind.PERK) openSigil(player);
                    else openSlots(player, h.kind, true);
                    return;
                }
                if(slot!=15)return;
                var result=h.kind==ProgressionCatalog.Kind.PERK
                        ? plugin.progression().rerollPerks(player)
                        : plugin.progression().rerollTalent(player,h.milestone);
                say(player,result);
                if(result.ok()) {
                    if(h.kind==ProgressionCatalog.Kind.TALENT)openChoices(player,h.kind,h.milestone,false);
                    else openPerks(player);
                }
            }
            default -> {}
        }
    }
    private ProgressionCatalog.Kind homeKind(int slot) {
        return switch(slot) {
            case 10 -> ProgressionCatalog.Kind.PRIVILEGE;
            case 12 -> ProgressionCatalog.Kind.PERK;
            case 14 -> ProgressionCatalog.Kind.TALENT;
            case 16 -> ProgressionCatalog.Kind.INFUSION;
            default -> null;
        };
    }
}
