package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * One lightweight, owner-focused feedback system for all passive Talents.
 * Activation is sent only after gameplay has genuinely triggered, and each
 * cooldown creates at most one ready alert. No per-tick particle loops.
 */
public final class TalentFeedback implements Listener {
    private final SpreeCorePlugin plugin;
    private final Map<UUID,Map<String,Long>> pendingReady = new HashMap<>();
    private final Map<UUID,Map<String,Long>> recentPassive = new HashMap<>();
    private final Map<UUID,Long> toastUntil = new HashMap<>();

    TalentFeedback(SpreeCorePlugin plugin) { this.plugin=plugin; }

    public static String cooldownKey(String talent) {
        return switch(talent) {
            case "mace_mastery" -> "mace_smash";
            case "concussive_blow" -> "concussive";
            case "bloodlust" -> "bloodlust_heal";
            case "riposte","sunder","second_wind","iron_will","field_medic",
                 "golden_aegis","scavenger","escape_artist","streak_insurance" -> talent;
            default -> null;
        };
    }

    static long secondsLeft(long until, long now) {
        return until <= now ? 0 : Math.max(1, (until-now+999)/1000);
    }

    private boolean enabled(String key) {
        return plugin.getConfig().getBoolean("talents.feedback.enabled",true)
                && plugin.getConfig().getBoolean("talents.feedback."+key,true);
    }

    public String cooldownLore(Player player,String talentId) {
        String key=cooldownKey(talentId);
        if(key==null)return "&bAlways active &8(passive)";
        long now=System.currentTimeMillis();
        long until=plugin.data().get(player).talentCooldowns.getOrDefault(key,0L);
        long remaining=secondsLeft(until,now);
        return remaining==0?"&aREADY &8| activates automatically":"&eCooldown: &f"+format(remaining);
    }

    public static String format(long seconds) {
        if(seconds<60)return seconds+"s";
        return (seconds/60)+"m "+(seconds%60)+"s";
    }

    private static String name(String key) {
        if(key.equals("mace_smash"))return "Mace Slam";
        if(key.equals("bloodlust_heal"))return "Bloodlust";
        if(key.equals("concussive"))return "Concussive Blow";
        for(ProgressionCatalog.Option o:ProgressionCatalog.TALENTS)
            if(o.id().equals(key))return o.name();
        return key.replace('_',' ');
    }

    private static String talentIdForKey(String key) {
        return switch(key) {
            case "mace_smash" -> "mace_mastery";
            case "bloodlust_heal" -> "bloodlust";
            case "concussive" -> "concussive_blow";
            default -> key;
        };
    }

    public String[] statusLines(Player player) {
        PlayerDataStore.Profile profile=plugin.data().get(player);
        List<String> out=new ArrayList<>();
        profile.talentChoices.entrySet().stream()
                .filter(e->e.getKey()<=SpreeMath.benefitLevel(profile.streak))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e->{
                    var option=ProgressionCatalog.find(ProgressionCatalog.Kind.TALENT,e.getValue());
                    if(option!=null)
                        out.add("&9Level "+e.getKey()+" &f"+option.name()+" &8- "+cooldownLore(player,option.id()));
                });
        return out.isEmpty()?new String[]{"&8No currently active Talents."}:out.toArray(String[]::new);
    }

    private static Sound sound(String id) {
        return switch(id) {
            case "concussive_blow" -> Sound.BLOCK_ANVIL_LAND;
            case "sunder" -> Sound.ITEM_SHIELD_BREAK;
            case "riposte" -> Sound.ITEM_SHIELD_BLOCK;
            case "second_wind","field_medic","bloodlust" -> Sound.ENTITY_PLAYER_LEVELUP;
            case "iron_will" -> Sound.BLOCK_ANVIL_USE;
            case "escape_artist","windrunner","acrobat" -> Sound.ENTITY_ENDER_DRAGON_FLAP;
            case "golden_aegis" -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            case "scavenger" -> Sound.ENTITY_ITEM_PICKUP;
            case "combo_breaker","executioner" -> Sound.ENTITY_PLAYER_ATTACK_CRIT;
            case "battle_sense" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "streak_insurance" -> Sound.BLOCK_BEACON_ACTIVATE;
            case "mace_mastery" -> Sound.ENTITY_IRON_GOLEM_ATTACK;
            default -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
        };
    }

    private static Particle particle(String id) {
        return switch(id) {
            case "concussive_blow","sunder","riposte","combo_breaker" -> Particle.CRIT;
            case "second_wind","field_medic","bloodlust" -> Particle.HEART;
            case "iron_will","golden_aegis" -> Particle.ENCHANT;
            case "escape_artist","windrunner","acrobat" -> Particle.CLOUD;
            case "scavenger","blacksmith","industrious" -> Particle.HAPPY_VILLAGER;
            case "streak_insurance" -> Particle.TOTEM_OF_UNDYING;
            case "executioner" -> Particle.DAMAGE_INDICATOR;
            default -> Particle.END_ROD;
        };
    }

    private void visual(Player player,String talent,boolean ready,boolean subtle) {
        if(!enabled("enabled"))return;
        if(enabled("sounds")) {
            Sound s=ready?Sound.BLOCK_NOTE_BLOCK_PLING:sound(talent);
            player.playSound(player.getLocation(),s,subtle?.22f:ready?.8f:.65f,
                    ready?1.65f:subtle?1.5f:1.05f);
        }
        if(enabled("particles")) {
            var where=player.getLocation().add(0,1.15,0);
            player.getWorld().spawnParticle(ready?Particle.END_ROD:particle(talent),where,
                    subtle?4:ready?8:13,.33,.48,.33,.018);
        }
    }

    private void alert(Player p,String message) {
        if(!enabled("actionbar"))return;
        Text.action(p,message);
        toastUntil.put(p.getUniqueId(),System.currentTimeMillis()+1650);
    }

    /** Called after a real ability effect has succeeded and its timer was saved. */
    public void activated(Player player,String key,long seconds) {
        String id=talentIdForKey(key);
        visual(player,id,false,false);
        if(seconds>0) {
            long until=plugin.data().get(player).talentCooldowns.getOrDefault(key,0L);
            if(until>System.currentTimeMillis())
                pendingReady.computeIfAbsent(player.getUniqueId(),ignored->new HashMap<>()).put(key,until);
        }
        alert(player,"&9&l"+name(key)+" &bACTIVATED"+(seconds>0?" &8| &7"+seconds+"s cooldown":""));
    }

    /** Feedback for abilities with no cooldown. Throttled per player + Talent. */
    public void passive(Player player,String id) {
        if(!enabled("enabled"))return;
        long now=System.currentTimeMillis();
        long spacing=Math.max(1000,plugin.getConfig().getLong(
                "talents.feedback.passive-interval-ms",2500));
        Map<String,Long> per=recentPassive.computeIfAbsent(player.getUniqueId(),k->new HashMap<>());
        if(now-per.getOrDefault(id,0L)<spacing)return;
        per.put(id,now);
        visual(player,id,false,true);
        if(!id.equals("acrobat")&&!id.equals("quick_hands")&&!id.equals("battle_sense"))
            alert(player,"&b"+name(id)+" &7activated");
    }

    public void unlocked(Player player,String talentId) {
        visual(player,talentId,false,false);
        alert(player,"&b&lTALENT UNLOCKED &8| &f"+name(talentId));
    }

    public boolean suppressBattleSense(Player player) {
        return System.currentTimeMillis()<toastUntil.getOrDefault(player.getUniqueId(),0L);
    }

    public void tick() {
        if(pendingReady.isEmpty())return;
        long now=System.currentTimeMillis();
        for(var iter=pendingReady.entrySet().iterator();iter.hasNext();) {
            var entry=iter.next();
            Player player=Bukkit.getPlayer(entry.getKey());
            if(player==null || !player.isOnline()) {iter.remove();continue;}
            Iterator<Map.Entry<String,Long>> timers=entry.getValue().entrySet().iterator();
            while(timers.hasNext()) {
                var timer=timers.next();
                long actual=plugin.data().get(player).talentCooldowns.getOrDefault(timer.getKey(),0L);
                if(actual!=timer.getValue()) {timers.remove();continue;}
                if(now<actual)continue;
                timers.remove();
                if(!plugin.progression().hasTalent(player,talentIdForKey(timer.getKey())))continue;
                if(!enabled("ready-notifications"))continue;
                String label=name(timer.getKey());
                visual(player,talentIdForKey(timer.getKey()),true,false);
                alert(player,"&a&lTALENT READY &8| &f"+label);
                if(enabled("ready-chat"))
                    Text.send(player,plugin.prefix()+"&a"+label+" is off cooldown and ready!");
            }
            if(entry.getValue().isEmpty())iter.remove();
        }
    }

    /** Existing cooldowns on login still get a ready alert after they expire. */
    public void reattach(Player player) {
        long now=System.currentTimeMillis();
        for(var e:plugin.data().get(player).talentCooldowns.entrySet())
            if(e.getValue()>now && plugin.progression().hasTalent(player,talentIdForKey(e.getKey()))
                    && cooldownKey(talentIdForKey(e.getKey()))!=null)
                pendingReady.computeIfAbsent(player.getUniqueId(),id->new HashMap<>())
                        .put(e.getKey(),e.getValue());
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(e.getPlayer().isOnline())reattach(e.getPlayer());
        },3L);
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId();
        pendingReady.remove(id);
        recentPassive.remove(id);
        toastUntil.remove(id);
    }
    public void shutdown() {
        pendingReady.clear();
        recentPassive.clear();
        toastUntil.clear();
    }
}
