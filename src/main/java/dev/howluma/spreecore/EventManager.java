package dev.howluma.spreecore;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Mutually exclusive compass modes plus independently timed server-wide events. */
public final class EventManager implements Listener {
    public enum Mode { NONE, HUNT, JUGGERNAUT }
    public enum Timed { NONE, BLOODMOON, SURGE, CHAOS, RACE }
    private final SpreeCorePlugin plugin;
    private final File file;
    private final NamespacedKey compassKey;
    private final Map<UUID,UUID> huntTargets=new HashMap<>();
    private final Map<UUID,Location> previousCompassTargets=new HashMap<>();
    private final Map<UUID,Integer> raceKills=new HashMap<>();
    private Mode mode=Mode.NONE;
    private Timed timed=Timed.NONE;
    private long timedEnds;
    private boolean locator,bloodsBane;
    private UUID juggernaut;

    EventManager(SpreeCorePlugin plugin) {
        this.plugin=plugin;
        file=new File(plugin.getDataFolder(),"events.yml");
        compassKey=new NamespacedKey(plugin,"event_compass");
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        try {mode=Mode.valueOf(y.getString("compass-mode","NONE"));} catch(IllegalArgumentException ignored) {}
        try {timed=Timed.valueOf(y.getString("timed-event","NONE"));} catch(IllegalArgumentException ignored) {}
        timedEnds=y.getLong("timed-ends");
        if (timed == Timed.SURGE) { timed = Timed.NONE; timedEnds = 0; } // Old buff-only event retired.
        locator=y.getBoolean("locator");
        bloodsBane=y.getBoolean("bloodsbane");
        try {juggernaut=UUID.fromString(y.getString("juggernaut",""));} catch(IllegalArgumentException ignored) {}
        for(String key:y.getStringList("hunt-targets")) {
            String[] parts=key.split(":",2);
            if(parts.length==2) try {huntTargets.put(UUID.fromString(parts[0]),UUID.fromString(parts[1]));}
                catch(IllegalArgumentException ignored) {}
        }
    }
    public Mode mode() {return mode;}
    public Timed timed() {return timed;}
    public boolean bloodsBaneEnabled() {return bloodsBane;}
    public boolean locatorEnabled() {return locator;}
    public boolean isJuggernaut(Player p) {return mode==Mode.JUGGERNAUT && p.getUniqueId().equals(juggernaut);}
    public UUID juggernaut() {return juggernaut;}
    public int timedSecondsRemaining() {return timed==Timed.NONE?0:(int)Math.max(0,(timedEnds-System.currentTimeMillis()+999)/1000);}
    private void persist() {
        YamlConfiguration y=new YamlConfiguration();
        y.set("compass-mode",mode.name());
        y.set("timed-event",timed.name());
        y.set("timed-ends",timedEnds);
        y.set("locator",locator);
        y.set("bloodsbane",bloodsBane);
        y.set("juggernaut",juggernaut==null?null:juggernaut.toString());
        y.set("hunt-targets",huntTargets.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).toList());
        try {y.save(file);}catch(IOException ex){plugin.getLogger().severe("Could not save event state: "+ex.getMessage());}
    }
    public boolean setMode(Mode next) {
        if (mode==next) return false;
        Mode old=mode;
        stopCompasses();
        mode=next;
        if(next==Mode.HUNT) {
            for(Player player:Bukkit.getOnlinePlayers()) {
                ensureCompass(player);
                reroll(player,null);
            }
            Bukkit.broadcastMessage(Text.c("&6&lTHE HUNT &7has begun! Your compass points toward a target."));
        } else if(next==Mode.JUGGERNAUT) {
            pickJuggernaut(null);
            Bukkit.broadcastMessage(Text.c("&4&lJUGGERNAUT &7event has begun!"));
        } else if(old!=Mode.NONE) Bukkit.broadcastMessage(Text.c("&7The &9"+old+" &7event has ended."));
        plugin.streaks().refreshOnline(); // Undo the previous Juggernaut's extra hearts immediately.
        persist();
        return true;
    }
    private void stopCompasses() {
        for(Player p:Bukkit.getOnlinePlayers()) {
            stripCompasses(p);
            Location old=previousCompassTargets.get(p.getUniqueId());
            if(old!=null && old.getWorld()!=null && p.getWorld().equals(old.getWorld())) p.setCompassTarget(old);
        }
        previousCompassTargets.clear();
        huntTargets.clear();
        juggernaut=null;
    }
    private ItemStack compass() {
        ItemStack item=new ItemStack(Material.COMPASS);
        ItemMeta meta=item.getItemMeta();
        String kind=mode==Mode.HUNT?"hunt":"juggernaut";
        meta.setDisplayName(Text.c(mode==Mode.HUNT?"&6&lHunt Compass":"&4&lJuggernaut Compass"));
        meta.setLore(mode==Mode.HUNT
                ?List.of(Text.c("&7Tracks your assigned target"),Text.c("&a+2 streak for target, +3 for hunter"),Text.c("&8Relic: Hunt Compass"))
                :List.of(Text.c("&7Tracks the current Juggernaut"),Text.c("&a+4 streak for slaying them"),Text.c("&8Relic: Juggernaut Compass")));
        meta.getPersistentDataContainer().set(compassKey,PersistentDataType.STRING,kind);
        item.setItemMeta(meta);
        return item;
    }
    private boolean eventCompass(ItemStack item) {
        if(item==null || item.getType()!=Material.COMPASS || !item.hasItemMeta())return false;
        if(item.getItemMeta().getPersistentDataContainer().has(compassKey))return true;
        List<String> lore=item.getItemMeta().getLore();
        return lore!=null && lore.stream().anyMatch(line -> {
            String raw=ChatColor.stripColor(line);
            return raw.contains("Relic: Hunt Compass") || raw.contains("Relic: Juggernaut Compass");
        });
    }
    private void stripCompasses(Player p) {
        ItemStack[] items=p.getInventory().getContents();
        boolean changed=false;
        for(int i=0;i<items.length;i++) if(eventCompass(items[i])) {items[i]=null;changed=true;}
        if(changed)p.getInventory().setContents(items);
    }
    private boolean hasCompass(Player p) {
        for(ItemStack item:p.getInventory().getContents())if(eventCompass(item))return true;
        return false;
    }
    private void ensureCompass(Player p) {
        if(mode==Mode.NONE || (mode==Mode.JUGGERNAUT && isJuggernaut(p))) {stripCompasses(p);return;}
        if(!hasCompass(p)) {
            Map<Integer,ItemStack> excess=p.getInventory().addItem(compass());
            if(!excess.isEmpty()) Text.action(p,"&cMake room in your inventory for the event compass.");
        }
    }
    private void reroll(Player hunter,UUID avoid) {
        List<Player> choices=new ArrayList<>(Bukkit.getOnlinePlayers());
        choices.removeIf(p->p.equals(hunter) || p.getUniqueId().equals(avoid));
        if(choices.isEmpty()) {huntTargets.remove(hunter.getUniqueId());return;}
        Player target=choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        huntTargets.put(hunter.getUniqueId(),target.getUniqueId());
        Text.action(hunter,"&6&lHUNT &7New target: &f"+target.getName());
    }
    private void pickJuggernaut(UUID avoid) {
        UUID old=juggernaut;
        List<Player> choices=new ArrayList<>(Bukkit.getOnlinePlayers());
        choices.removeIf(p->p.getUniqueId().equals(avoid));
        if(choices.isEmpty())choices=new ArrayList<>(Bukkit.getOnlinePlayers());
        if(choices.isEmpty()) {juggernaut=null;return;}
        Player selected=choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        juggernaut=selected.getUniqueId();
        Player previous=old==null?null:Bukkit.getPlayer(old);
        if(previous!=null && !previous.isDead())plugin.streaks().update(previous);
        plugin.streaks().update(selected);
        for(Player p:Bukkit.getOnlinePlayers()) ensureCompass(p);
        Bukkit.broadcastMessage(Text.c("&4&l"+selected.getName()+" &cis the JUGGERNAUT, gaining +"+
                plugin.getConfig().getInt("events.juggernaut.bonus-hearts",10)+" hearts!"));
        selected.getWorld().playSound(selected.getLocation(),Sound.ENTITY_WITHER_SPAWN,1f,1f);
        persist();
    }
    public void setLocator(boolean value) {
        locator=value;
        // 1.21.11 game rules are per-world; update every loaded dimension.
        for (World world : Bukkit.getWorlds()) world.setGameRule(GameRules.LOCATOR_BAR, value);
        Bukkit.broadcastMessage(Text.c("&9&lSPREE &7Locator bar "+(value?"&aON":"&cOFF")));
        persist();
    }
    public void setBloodsBane(boolean value) {
        bloodsBane=value;
        Bukkit.broadcastMessage(Text.c("&4&lBLOOD'S BANE &7auto-award "+(value?"&aON":"&cOFF")));
        persist();
        plugin.legendaries().syncBloodsBane();
    }
    public void setTimed(Timed next) {
        if (next == Timed.SURGE) { // Retired until killstreak effects are redesigned.
            plugin.getLogger().warning("Spree Surge is paused; no timed event was started.");
            return;
        }
        timed=next;
        raceKills.clear();
        timedEnds=next==Timed.NONE?0:System.currentTimeMillis()+Math.max(10,
                plugin.getConfig().getInt("events.timed.default-seconds",300))*1000L;
        Bukkit.broadcastMessage(Text.c(next==Timed.NONE?"&9&lSPREE EVENT &7Timed event ended."
                :"&9&lSPREE EVENT &f"+next.name()+" &7started for "+timedSecondsRemaining()+" seconds!"));
        persist();
    }
    public int timedKillBonus() {
        return timed==Timed.BLOODMOON?plugin.getConfig().getInt("events.timed.bloodmoon.extra-streak-per-kill",1):0;
    }
    public double cooldownMultiplier(Player player) {
        if(timed==Timed.CHAOS && plugin.streaks().streak(player)>=plugin.getConfig().getInt("events.timed.chaos.minimum-streak",10))
            return Math.max(.1,Math.min(1,plugin.getConfig().getDouble("events.timed.chaos.cooldown-multiplier",.7)));
        return 1.0;
    }
    public void handleKill(Player killer,Player victim,boolean eligible) {
        if(!eligible)return;
        if(mode==Mode.HUNT) {
            int bonus=0;
            if(killer.getUniqueId().equals(huntTargets.get(victim.getUniqueId())))
                bonus=plugin.getConfig().getInt("events.hunt.hunter-kill-bonus",3);
            else if(victim.getUniqueId().equals(huntTargets.get(killer.getUniqueId())))
                bonus=plugin.getConfig().getInt("events.hunt.target-kill-bonus",2);
            if(bonus>0) {
                plugin.streaks().addStreak(killer,bonus);
                Text.send(killer,"&6&lHUNT &7Bonus: &a+"+bonus+" streak");
            }
        }
        if(mode==Mode.JUGGERNAUT) {
            int bonus=0;
            if(isJuggernaut(killer))bonus+=plugin.getConfig().getInt("events.juggernaut.kill-bonus",2);
            if(isJuggernaut(victim))bonus+=plugin.getConfig().getInt("events.juggernaut.slay-bonus",4);
            if(bonus>0) {
                plugin.streaks().addStreak(killer,bonus);
                Text.send(killer,"&4&lJUGGERNAUT &7Bonus: &a+"+bonus+" streak");
            }
        }
        if(timed==Timed.RACE) {
            int kills=raceKills.merge(killer.getUniqueId(),1,Integer::sum);
            if(kills>=plugin.getConfig().getInt("events.timed.race.kills-to-win",3)) {
                int reward=plugin.getConfig().getInt("events.timed.race.winner-bonus",3);
                plugin.streaks().addStreak(killer,reward);
                Bukkit.broadcastMessage(Text.c("&6&lTHE HUNT &f"+killer.getName()+" &7won the kill race! &a+"+reward+" streak"));
                setTimed(Timed.NONE);
            }
        }
    }
    public void tick() {
        if(timed!=Timed.NONE && System.currentTimeMillis()>=timedEnds)setTimed(Timed.NONE);
        if(mode==Mode.HUNT) {
            for(Player p:Bukkit.getOnlinePlayers()) {
                UUID id=huntTargets.get(p.getUniqueId());
                Player target=id==null?null:Bukkit.getPlayer(id);
                if(target==null || target.equals(p)) {
                    reroll(p,null);
                    UUID next=huntTargets.get(p.getUniqueId());
                    target=next==null?null:Bukkit.getPlayer(next);
                }
                if(target!=null && target.getWorld().equals(p.getWorld())) {
                    previousCompassTargets.putIfAbsent(p.getUniqueId(),p.getCompassTarget());
                    p.setCompassTarget(target.getLocation());
                }
                ensureCompass(p);
            }
        } else if(mode==Mode.JUGGERNAUT) {
            Player jug=juggernaut==null?null:Bukkit.getPlayer(juggernaut);
            if(jug==null) {pickJuggernaut(juggernaut);jug=juggernaut==null?null:Bukkit.getPlayer(juggernaut);}
            if(jug!=null) for(Player p:Bukkit.getOnlinePlayers()) {
                if(p.equals(jug)) continue;
                if(p.getWorld().equals(jug.getWorld())) {
                    previousCompassTargets.putIfAbsent(p.getUniqueId(),p.getCompassTarget());
                    p.setCompassTarget(jug.getLocation());
                }
                ensureCompass(p);
            }
        }
    }
    // Run AFTER StreakManager's HIGH handler so the slain Juggernaut still awards +4.
    @EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::eventCompass);
        Player victim=event.getEntity();
        if(mode==Mode.HUNT) {
            huntTargets.remove(victim.getUniqueId());
            for(Player player:Bukkit.getOnlinePlayers()) {
                if(victim.getUniqueId().equals(huntTargets.get(player.getUniqueId()))) reroll(player,victim.getUniqueId());
            }
        } else if(mode==Mode.JUGGERNAUT && isJuggernaut(victim)) {
            Bukkit.broadcastMessage(Text.c("&4&lJUGGERNAUT &7has fallen!"));
            pickJuggernaut(victim.getUniqueId());
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player p=event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!p.isOnline())return;
            if(mode!=Mode.NONE) {
                ensureCompass(p);
                if(mode==Mode.HUNT)reroll(p,null);
                else if(mode==Mode.JUGGERNAUT)plugin.streaks().update(p);
            } else stripCompasses(p);
        },2L);
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Player p=event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline()){ensureCompass(p);if(mode==Mode.HUNT)reroll(p,null);}},2L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        huntTargets.remove(event.getPlayer().getUniqueId());
        previousCompassTargets.remove(event.getPlayer().getUniqueId());
        persist();
    }
    @EventHandler(ignoreCancelled=true) public void drop(PlayerDropItemEvent event) {
        if(eventCompass(event.getItemDrop().getItemStack()))event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void pickup(EntityPickupItemEvent event) {
        if(!(event.getEntity() instanceof Player p) || !eventCompass(event.getItem().getItemStack()))return;
        if(mode==Mode.NONE || hasCompass(p) || isJuggernaut(p))event.setCancelled(true);
    }
    public void shutdown() {persist();for(Player p:Bukkit.getOnlinePlayers()) {
        Location old=previousCompassTargets.get(p.getUniqueId());
        if(old!=null && p.getWorld().equals(old.getWorld()))p.setCompassTarget(old);
    }}
}
