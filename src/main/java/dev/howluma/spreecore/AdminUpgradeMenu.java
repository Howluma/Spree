package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.*;

/** Dedicated, permission-gated test interface. Never exposes future upgrades to regular players. */
public final class AdminUpgradeMenu implements Listener {
    private static final int PAGE_SIZE = 28;
    private enum Screen { MILESTONES, CHOICES }

    private static final class Holder implements InventoryHolder {
        final Screen screen;
        final UUID target;
        final ProgressionCatalog.Kind kind;
        final int pageOrLevel;
        final Map<Integer,Integer> milestones = new HashMap<>();
        final Map<Integer,String> choices = new HashMap<>();
        Inventory inventory;
        Holder(Screen screen, UUID target, ProgressionCatalog.Kind kind, int pageOrLevel) {
            this.screen=screen; this.target=target; this.kind=kind; this.pageOrLevel=pageOrLevel;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final SpreeCorePlugin plugin;
    AdminUpgradeMenu(SpreeCorePlugin plugin) { this.plugin=plugin; }

    private boolean allowed(Player admin, ProgressionCatalog.Kind kind) {
        return admin.hasPermission("spree.admin")
                && (kind==ProgressionCatalog.Kind.TALENT || kind==ProgressionCatalog.Kind.PERK);
    }

    private ItemStack icon(Material material, String label, String... lore) {
        ItemStack item=new ItemStack(material);
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(Text.c(label));
        meta.setLore(Arrays.stream(lore).map(Text::c).toList());
        if(material==Material.POTION && meta instanceof PotionMeta potion)
            potion.setColor(Color.fromRGB(0x5A9BD5));
        item.setItemMeta(meta);
        return item;
    }
    private Inventory create(Holder holder, String name) {
        Inventory inv=Bukkit.createInventory(holder,54,Text.c(name));
        holder.inventory=inv;
        for(int i=0;i<54;i++) {
            Material background=i%9==0||i%9==8
                    ?Material.BLUE_STAINED_GLASS_PANE:Material.BLACK_STAINED_GLASS_PANE;
            inv.setItem(i,icon(background,"&8"));
        }
        return inv;
    }

    /** Center the final partially occupied row to avoid lopsided option grids. */
    static int[] centeredSlots(int count) {
        int n=Math.max(0,Math.min(PAGE_SIZE,count));
        if(n==6) return new int[]{11,13,15,29,31,33};
        int[] result=new int[n];
        for(int i=0;i<n;) {
            int inRow=Math.min(7,n-i);
            int col=1+(7-inRow)/2;
            int row=i/7;
            for(int j=0;j<inRow;j++) result[i+j]=9*(row+1)+col+j;
            i+=inRow;
        }
        return result;
    }

    public void openMilestones(Player admin, UUID target, ProgressionCatalog.Kind kind, int requestedPage) {
        if(!allowed(admin,kind)) return;
        List<Integer> levels=ProgressionCatalog.milestones(kind,plugin.streaks().progressionCap());
        int totalPages=Math.max(1,(levels.size()+PAGE_SIZE-1)/PAGE_SIZE);
        int page=Math.max(0,Math.min(requestedPage,totalPages-1));
        var profile=plugin.data().get(target);
        Holder h=new Holder(Screen.MILESTONES,target,kind,page);
        Inventory inv=create(h,"&9&lSet "+(kind==ProgressionCatalog.Kind.TALENT?"Talent":"Perk")+" &8| &fLevels");
        inv.setItem(4,icon(kind.icon,"&b&l"+profile.name+" &8| &f"+kind.title,
                "&7Choose a milestone to assign a specific ability.",
                "&7Assignments are saved, including for offline players.",
                "&7To activate: raise the player's streak to that level.",
                "&7Current killstreak: &f"+plugin.streaks().display(profile.streak)));
        int start=page*PAGE_SIZE;
        int visible=Math.min(PAGE_SIZE,levels.size()-start);
        int[] slots=centeredSlots(visible);
        Map<Integer,String> saved=kind==ProgressionCatalog.Kind.TALENT
                ?profile.talentChoices:profile.perkRolls;
        for(int i=0;i<visible;i++) {
            int level=levels.get(start+i);
            String current=saved.get(level);
            var talent=kind==ProgressionCatalog.Kind.TALENT
                    ?ProgressionCatalog.find(kind,current):null;
            PerkType perk=kind==ProgressionCatalog.Kind.PERK?PerkType.fromId(current):null;
            String chosen=talent!=null?talent.name():perk!=null?perk.display:"Unassigned";
            Material picture=talent!=null?talent.icon():perk!=null?perk.icon()
                    :(kind==ProgressionCatalog.Kind.TALENT?Material.AMETHYST_SHARD:Material.ENCHANTED_BOOK);
            int slot=slots[i];
            inv.setItem(slot,icon(picture,"&9&lLevel "+level,
                    "&7Chosen: &f"+chosen,
                    level<=profile.streak?"&aAbility active at this streak":"&8Inactive until level "+level,
                    "&eClick to set a specific "+(kind==ProgressionCatalog.Kind.TALENT?"Talent":"Perk")));
            h.milestones.put(slot,level);
        }
        if(levels.isEmpty())inv.setItem(22,icon(Material.BARRIER,"&cNo milestones configured"));
        inv.setItem(45,icon(Material.ARROW,page>0?"&9Previous Page":"&9Back to Player"));
        inv.setItem(49,icon(Material.PLAYER_HEAD,"&9&lBack to "+profile.name));
        inv.setItem(53,icon(page+1<totalPages?Material.ARROW:Material.BARRIER,
                page+1<totalPages?"&9Next Page":"&cClose",
                "&7Page "+(page+1)+"/"+totalPages));
        admin.openInventory(inv);
    }

    public void openChoices(Player admin, UUID target, ProgressionCatalog.Kind kind, int level) {
        if(!allowed(admin,kind)
                || !ProgressionCatalog.milestone(kind,level)
                || level>plugin.streaks().progressionCap()) return;
        var profile=plugin.data().get(target);
        Holder h=new Holder(Screen.CHOICES,target,kind,level);
        Inventory inv=create(h,"&9&lSet "+(kind==ProgressionCatalog.Kind.TALENT?"Talent":"Perk")+" &8| &f"+level);
        String selected=kind==ProgressionCatalog.Kind.TALENT
                ?profile.talentChoices.get(level):profile.perkRolls.get(level);
        inv.setItem(4,icon(kind.icon,"&b&l"+profile.name+" &8| &fLevel "+level,
                "&7Click any listed ability to assign it immediately.",
                "&7This bypasses random Talent offers and Fate Sigils.",
                "&7The choice is saved and replaces the prior one.",
                level<=profile.streak?"&aActive immediately":"&8Not active until killstreak "+level));
        if(kind==ProgressionCatalog.Kind.TALENT) {
            List<ProgressionCatalog.Option> options=ProgressionCatalog.options(kind);
            int[] slots=centeredSlots(options.size());
            for(int i=0;i<options.size()&&i<slots.length;i++) {
                var option=options.get(i);
                int slot=slots[i];
                inv.setItem(slot,icon(option.icon(),
                        "&b&l"+option.name(),
                        "&7"+option.description(),
                        option.id().equals(selected)?"&aCurrently assigned":"&eClick to assign (no Sigil cost)"));
                h.choices.put(slot,option.id());
            }
            inv.setItem(49,icon(Material.BARRIER,"&cClear This Talent",
                    "&7Remove the assigned Talent from level "+level,
                    "&7Restores normal player selection when available",
                    "&eClick to clear without changing killstreak"));
        } else {
            PerkType[] options=PerkType.values();
            int[] slots=centeredSlots(options.length);
            for(int i=0;i<options.length&&i<slots.length;i++) {
                PerkType perk=options[i];
                int slot=slots[i];
                int copies=PerkMath.copies(profile.perkRolls,perk,Integer.MAX_VALUE);
                int max=Math.max(0,Math.min(8,plugin.getConfig().getInt(
                        "perks."+perk.id+".max-copies",perk.defaultMaxCopies)));
                boolean enabled=plugin.getConfig().getBoolean("perks.enabled",true)
                        &&plugin.getConfig().getBoolean("perks."+perk.id+".enabled",true);
                inv.setItem(slot,icon(perk.icon(),"&b&l"+perk.display,
                        "&7Bonus per copy: &f"+ ConfigEditor.format(
                                perk==PerkType.CAPACITY||perk==PerkType.REACH
                                        ?perk.defaultPerCopy:perk.defaultPerCopy*100)
                                +(perk==PerkType.CAPACITY?" items":perk==PerkType.REACH?" blocks":"%"),
                        "&7Total assigned: &f"+copies+" &7/ &f"+max,
                        !enabled?"&cDisabled by server config":
                                perk.id.equals(selected)?"&aCurrently assigned":"&eClick to assign"));
                h.choices.put(slot,perk.id);
            }
            inv.setItem(49,icon(Material.ENCHANTED_BOOK,"&9Roll Automatic Perk",
                    "&7Restore a normal random roll at level "+level,
                    "&7Perks may reroll into the same result",
                    "&eClick to reroll without a Fate Sigil"));
        }
        inv.setItem(45,icon(Material.ARROW,"&9Back to Milestones"));
        inv.setItem(53,icon(Material.BARRIER,"&cClose"));
        admin.openInventory(inv);
    }

    private void reply(Player admin, UUID target, ProgressionManager.Result result) {
        Text.send(admin,plugin.prefix()+(result.ok()?"&a":"&c")+result.message());
        if(result.ok())plugin.getLogger().info(admin.getName()+" changed a test upgrade for "
                +plugin.data().get(target).name+": "+result.message());
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder h))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player admin)||!allowed(admin,h.kind))return;
        int slot=event.getRawSlot();
        if(slot<0||slot>=54)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!admin.isOnline()||admin.getOpenInventory().getTopInventory().getHolder()!=h)return;
            if(h.screen==Screen.MILESTONES) {
                if(slot==49)plugin.menus().openPlayer(admin,h.target);
                else if(slot==45) {
                    if(h.pageOrLevel>0)openMilestones(admin,h.target,h.kind,h.pageOrLevel-1);
                    else plugin.menus().openPlayer(admin,h.target);
                } else if(slot==53) {
                    int total=ProgressionCatalog.milestones(h.kind,plugin.streaks().progressionCap()).size();
                    if((h.pageOrLevel+1)*PAGE_SIZE<total)
                        openMilestones(admin,h.target,h.kind,h.pageOrLevel+1);
                    else admin.closeInventory();
                } else if(h.milestones.containsKey(slot))
                    openChoices(admin,h.target,h.kind,h.milestones.get(slot));
            } else {
                if(slot==45)openMilestones(admin,h.target,h.kind,0);
                else if(slot==53)admin.closeInventory();
                else if(slot==49) {
                    ProgressionManager.Result result=h.kind==ProgressionCatalog.Kind.TALENT
                            ?plugin.progression().adminClearTalent(h.target,h.pageOrLevel)
                            :plugin.progression().adminAutoRollPerk(h.target,h.pageOrLevel);
                    reply(admin,h.target,result);
                    if(result.ok())openChoices(admin,h.target,h.kind,h.pageOrLevel);
                } else if(h.choices.containsKey(slot)) {
                    ProgressionManager.Result result=plugin.progression().adminSet(
                            h.target,h.kind,h.pageOrLevel,h.choices.get(slot));
                    reply(admin,h.target,result);
                    if(result.ok())openChoices(admin,h.target,h.kind,h.pageOrLevel);
                }
            }
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);
    }
}
