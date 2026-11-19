package dev.howluma.spreecore;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Recipe registration, single-winner craft locks, robust persisted forge timers. */
public final class ForgeManager implements Listener {
    private static final class Forge {
        boolean crafted;
        UUID owner;
        long endsAt, lastCoordinates;
        boolean pending;
    }
    private final SpreeCorePlugin plugin;
    private final Map<LegendaryType,Forge> states=new EnumMap<>(LegendaryType.class);
    private final Map<LegendaryType,BossBar> bars=new EnumMap<>(LegendaryType.class);
    private final File file;

    ForgeManager(SpreeCorePlugin plugin) {
        this.plugin=plugin;
        file=new File(plugin.getDataFolder(),"forges.yml");
        YamlConfiguration cfg=YamlConfiguration.loadConfiguration(file);
        for (LegendaryType t:LegendaryType.values()) if (t.forgeable()) {
            Forge state=new Forge();
            String path="forges."+t.id+".";
            state.crafted=cfg.getBoolean(path+"crafted");
            state.endsAt=cfg.getLong(path+"ends-at");
            state.lastCoordinates=cfg.getLong(path+"coordinates-at");
            state.pending=cfg.getBoolean(path+"pending-claim");
            try { String id=cfg.getString(path+"forger");if(id!=null)state.owner=UUID.fromString(id); }
            catch(IllegalArgumentException ignored) {}
            states.put(t,state);
        }
    }
    private int duration() { return Math.max(10,plugin.getConfig().getInt("legendary.forging.duration-seconds",900)); }
    public boolean isForging(Player player) {
        return states.values().stream().anyMatch(f->f.endsAt>0 && player.getUniqueId().equals(f.owner));
    }
    public String status(LegendaryType t) {
        Forge f=states.get(t);
        if (f==null) return "Not craftable";
        if (f.endsAt>0) return "Forging ("+Text.time(f.endsAt-System.currentTimeMillis())+")";
        if (f.pending) return "Awaiting offline claim";
        return f.crafted ? "Already forged" : "Available";
    }
    private NamespacedKey recipeKey(LegendaryType t) { return new NamespacedKey(plugin,"forge_"+t.id); }
    public void registerRecipes() {
        for (LegendaryType t:states.keySet()) Bukkit.removeRecipe(recipeKey(t));
        if (!plugin.getConfig().getBoolean("legendary.crafting-enabled",true)) return;
        for (LegendaryType t:states.keySet()) {
            ShapedRecipe recipe=new ShapedRecipe(recipeKey(t),plugin.legendaries().create(t,false));
            switch(t) {
                case TUNDRA -> {
                    recipe.shape("PIP","ISI","PIP");
                    recipe.setIngredient('P',Material.POTION);
                    recipe.setIngredient('I',Material.BLUE_ICE);
                    recipe.setIngredient('S',Material.DIAMOND_SWORD);
                }
                case BUTCHERER -> {
                    recipe.shape("PVP","VAV","HVH");
                    recipe.setIngredient('P',Material.POTION);
                    recipe.setIngredient('H',Material.POTION);
                    recipe.setIngredient('V',Material.WEEPING_VINES);
                    recipe.setIngredient('A',Material.DIAMOND_AXE);
                }
                case STORMBREAKER -> {
                    recipe.shape("GGL","GRL","LRL");
                    recipe.setIngredient('G',Material.GOLD_BLOCK);
                    recipe.setIngredient('L',Material.POTION);
                    recipe.setIngredient('R',Material.LIGHTNING_ROD);
                }
                case AQUAFIER -> {
                    recipe.shape("PIP","WIW","PBP");
                    recipe.setIngredient('P',Material.POTION);
                    recipe.setIngredient('I',Material.IRON_BLOCK);
                    recipe.setIngredient('W',Material.WATER_BUCKET);
                    recipe.setIngredient('B',Material.BREEZE_ROD);
                }
                default -> { continue; }
            }
            Bukkit.addRecipe(recipe);
        }
    }
    private LegendaryType recipeType(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) return null;
        if (!keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) return null;
        for (LegendaryType t:states.keySet()) if (keyed.getKey().equals(recipeKey(t))) return t;
        return null;
    }
    private boolean potion(ItemStack item,PotionType type) {
        return item!=null && item.getType()==Material.POTION && item.hasItemMeta()
                && item.getItemMeta() instanceof PotionMeta m && m.getBasePotionType()==type;
    }
    /** Vanilla materials for shaped recipes, plus explicit base-potion verification. */
    private boolean hasRequiredPotions(LegendaryType t,CraftingInventory inventory) {
        ItemStack[] slots=inventory.getMatrix();
        if (slots.length!=9) return false;
        return switch(t) {
            case TUNDRA -> potion(slots[0],PotionType.SLOWNESS) && potion(slots[2],PotionType.SLOWNESS)
                    && potion(slots[6],PotionType.SLOWNESS) && potion(slots[8],PotionType.SLOWNESS);
            case BUTCHERER -> potion(slots[0],PotionType.STRENGTH) && potion(slots[2],PotionType.STRENGTH)
                    && potion(slots[6],PotionType.HARMING) && potion(slots[8],PotionType.HARMING);
            case STORMBREAKER -> potion(slots[2],PotionType.LEAPING) && potion(slots[5],PotionType.LEAPING)
                    && potion(slots[6],PotionType.LEAPING) && potion(slots[8],PotionType.LEAPING);
            case AQUAFIER -> potion(slots[0],PotionType.WATER_BREATHING) && potion(slots[2],PotionType.WATER_BREATHING)
                    && potion(slots[6],PotionType.WATER_BREATHING) && potion(slots[8],PotionType.WATER_BREATHING);
            default -> false;
        };
    }
    @EventHandler public void prepare(PrepareItemCraftEvent event) {
        LegendaryType t=recipeType(event.getRecipe());
        if (t==null) return;
        Forge f=states.get(t);
        if (f.crafted || !hasRequiredPotions(t,event.getInventory())) event.getInventory().setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent event) {
        LegendaryType t=recipeType(event.getRecipe());
        if(t==null) return;
        event.setCancelled(true); // No instant forged weapon and no shift-crafting duplicates.
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Forge state=states.get(t);
        if (state.crafted) { Text.send(player,"&c"+t.display+" has already been forged.");return; }
        CraftingInventory inventory=(CraftingInventory)event.getInventory();
        if (!hasRequiredPotions(t,inventory)) {Text.send(player,"&cUse the correct potion types.");return;}
        ItemStack[] matrix=inventory.getMatrix();
        for (int index=0;index<matrix.length;index++) {
            ItemStack material=matrix[index];
            if (material==null || material.getType()==Material.AIR) continue;
            material=material.clone();
            material.setAmount(material.getAmount()-1);
            matrix[index]=material.getAmount()==0?null:material;
        }
        inventory.setMatrix(matrix);
        player.updateInventory();
        state.crafted=true;
        state.owner=player.getUniqueId();
        state.endsAt=System.currentTimeMillis()+duration()*1000L;
        state.lastCoordinates=System.currentTimeMillis();
        state.pending=false;
        save();
        Bukkit.broadcastMessage(Text.c("&b&lFORGING &f"+player.getName()+" &7is forging &b"+t.display+"&7!"));
        Bukkit.broadcastMessage(Text.c("&7Last known location: &f"+coordinate(player)+" &7- survive &b"+duration()/60+"m&7!"));
        player.getWorld().playSound(player.getLocation(),Sound.BLOCK_END_PORTAL_SPAWN,1,1);
        plugin.streaks().update(player);
    }
    private String coordinate(Player player) {
        Location l=player.getLocation();
        return l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ()+" ("+l.getWorld().getName()+")";
    }
    public void tick() {
        long time=System.currentTimeMillis();
        for (var entry:states.entrySet()) {
            LegendaryType t=entry.getKey(); Forge state=entry.getValue();
            if (state.endsAt<=0) continue;
            if (time>=state.endsAt) { finish(t,state);continue; }
            Player p=state.owner==null?null:Bukkit.getPlayer(state.owner);
            BossBar bar=bars.computeIfAbsent(t,key->Bukkit.createBossBar(Text.c("&b"+t.display+" forging"),
                    t==LegendaryType.BUTCHERER?BarColor.RED:t==LegendaryType.STORMBREAKER?BarColor.YELLOW:BarColor.BLUE,
                    BarStyle.SOLID));
            double progress=Math.max(0,Math.min(1,(double)(state.endsAt-time)/(duration()*1000L)));
            bar.setProgress(progress);
            bar.setTitle(Text.c("&b"+t.display+" &7- "+Text.time(state.endsAt-time)));
            if (p==null) bar.removeAll();
            else {
                if (!bar.getPlayers().contains(p)) {bar.removeAll();bar.addPlayer(p);}
                if (time-state.lastCoordinates>=plugin.getConfig().getLong("legendary.forging.coordinate-broadcast-seconds",180)*1000L) {
                    state.lastCoordinates=time;
                    Bukkit.broadcastMessage(Text.c("&b&lFORGE LOCATION &f"+p.getName()+" &7("+t.display+") &f"+coordinate(p)));
                    p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BELL_USE,1,1);
                    save();
                }
            }
        }
    }
    private void finish(LegendaryType t,Forge f) {
        removeBar(t);
        f.endsAt=0;
        Player p=f.owner==null?null:Bukkit.getPlayer(f.owner);
        if (p!=null) {
            give(p,t);
            Bukkit.broadcastMessage(Text.c("&b&lFORGED &f"+p.getName()+" &7survived and claimed &b"+t.display+"!"));
            f.owner=null;
            f.pending=false;
            plugin.streaks().update(p);
        } else {
            f.pending=true;
            Bukkit.broadcastMessage(Text.c("&7The &b"+t.display+" &7forge completed. Its offline owner will claim on join."));
        }
        save();
    }
    private void give(Player p,LegendaryType t) {
        Map<Integer,ItemStack> extra=p.getInventory().addItem(plugin.legendaries().create(t,false));
        for (ItemStack stack:extra.values()) p.getWorld().dropItemNaturally(p.getLocation(),stack);
        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_END_PORTAL_SPAWN,1,1);
    }
    @EventHandler public void death(PlayerDeathEvent event) {
        Player victim=event.getEntity();
        for(var entry:states.entrySet()) {
            Forge f=entry.getValue();
            if (f.endsAt<=0 || !victim.getUniqueId().equals(f.owner)) continue;
            if (f.endsAt<=System.currentTimeMillis()) {finish(entry.getKey(),f);continue;}
            f.endsAt=0;f.crafted=false;f.owner=null;f.pending=false;
            removeBar(entry.getKey());
            Bukkit.broadcastMessage(Text.c("&c&lFORGE FAILED &f"+victim.getName()+" &7died before forging &c"+entry.getKey().display+"&7. Recipe unlocked."));
            save();
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player p=event.getPlayer();
        for(var entry:states.entrySet()) {
            Forge f=entry.getValue();
            if (!f.pending || !p.getUniqueId().equals(f.owner)) continue;
            LegendaryType t=entry.getKey();
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                // Do not consume a pending claim if the owner disconnects immediately.
                if (!p.isOnline() || !f.pending || !p.getUniqueId().equals(f.owner)) return;
                give(p,t);
                f.pending=false;
                f.owner=null;
                save();
                Bukkit.broadcastMessage(Text.c("&b&lFORGED &f"+p.getName()+" &7claimed &b"+t.display+"!"));
            },2L);
        }
    }
    public boolean end(LegendaryType type) {
        Forge f=states.get(type);
        if (f==null || f.endsAt<=0) return false;
        f.endsAt=System.currentTimeMillis();tick();return true;
    }
    public boolean reset(LegendaryType type) {
        Forge f=states.get(type);
        if (f==null) return false;
        f.crafted=false;f.endsAt=0;f.owner=null;f.pending=false;f.lastCoordinates=0;
        removeBar(type);save();return true;
    }
    private void removeBar(LegendaryType t) {
        BossBar bar=bars.remove(t);
        if (bar!=null) {bar.removeAll();bar.setVisible(false);}
    }
    private void save() {
        YamlConfiguration cfg=new YamlConfiguration();
        for(var entry:states.entrySet()) {
            String path="forges."+entry.getKey().id+".";
            Forge f=entry.getValue();
            cfg.set(path+"crafted",f.crafted);
            cfg.set(path+"forger",f.owner==null?null:f.owner.toString());
            cfg.set(path+"ends-at",f.endsAt);
            cfg.set(path+"coordinates-at",f.lastCoordinates);
            cfg.set(path+"pending-claim",f.pending);
        }
        try { cfg.save(file); }
        catch(IOException ex) { plugin.getLogger().severe("Couldn't save forge state: "+ex.getMessage()); }
    }
    public void shutdown() {
        save();
        for (LegendaryType t:List.copyOf(bars.keySet())) removeBar(t);
    }
}
