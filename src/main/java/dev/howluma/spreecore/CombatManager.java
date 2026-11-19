package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat tuning and evidence-based explosive kill credit. The detonator's UUID is
 * persisted on primed TNT, so credit survives TNT chunk unload/reload. Explosion
 * chains inherit ownership only when there is a single unambiguous nearby source.
 * Redstone/unknown-source blasts are intentionally never assigned to a random player.
 */
public final class CombatManager implements Listener {
    private record Blast(UUID owner, UUID world, double x, double y, double z, long when) {
        double distanceSquared(Location l) {
            double dx = x - l.getX(), dy = y - l.getY(), dz = z - l.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }
    private record Hit(UUID attacker, long when, boolean explosive) {}

    private final SpreeCorePlugin plugin;
    private final NamespacedKey detonatorKey;
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Deque<Blast> recentBlasts = new ArrayDeque<>();
    private static final long BLAST_BRIDGE_MS = 500;

    CombatManager(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        detonatorKey = new NamespacedKey(plugin, "tnt_detonator");
    }
    private FileConfiguration config() { return plugin.getConfig(); }

    /** Resolve Bukkit native kill credit first, with a bounded fallback for explosions and resulting falls. */
    public Player killer(Player victim) {
        Player nativeKiller = victim.getKiller();
        Hit hit = lastHit.get(victim.getUniqueId());
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        boolean explosiveAftermath = lastDamage != null && switch (lastDamage.getCause()) {
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION, FALL, VOID, FIRE, FIRE_TICK -> true;
            default -> false;
        };
        if (config().getBoolean("combat.explosive-kill-credit.enabled", true)
                && hit != null && hit.explosive() && explosiveAftermath
                && CombatMath.recent(System.currentTimeMillis(), hit.when(),
                    config().getLong("combat.explosive-kill-credit.window-seconds", 12))) {
            Player attacker = Bukkit.getPlayer(hit.attacker());
            if (attacker != null && attacker.isOnline() && !attacker.getUniqueId().equals(victim.getUniqueId()))
                return attacker;
        }
        return nativeKiller == victim ? null : nativeKiller;
    }
    public void clearVictim(UUID id) { lastHit.remove(id); }

    private UUID directPlayer(Entity source) {
        if (source instanceof Player p) return p.getUniqueId();
        if (source instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player.getUniqueId();
        }
        return null;
    }
    private UUID storedOwner(Entity entity) {
        String text = entity.getPersistentDataContainer().get(detonatorKey, PersistentDataType.STRING);
        if (text == null) return null;
        try { return UUID.fromString(text); } catch (IllegalArgumentException ignored) { return null; }
    }
    private void rememberOwner(Entity entity, UUID player) {
        if (player != null) entity.getPersistentDataContainer().set(detonatorKey, PersistentDataType.STRING, player.toString());
    }
    private UUID tntOwner(TNTPrimed tnt) {
        UUID persistent = storedOwner(tnt);
        if (persistent != null) return persistent;
        UUID source = directPlayer(tnt.getSource());
        if (source != null) rememberOwner(tnt, source);
        return source;
    }
    private void rememberBlast(Location loc, UUID owner) {
        if (owner == null || loc.getWorld() == null) return;
        long now = System.currentTimeMillis();
        expireBlasts(now);
        recentBlasts.addLast(new Blast(owner, loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ(), now));
        while (recentBlasts.size() > 80) recentBlasts.removeFirst();
    }
    private void expireBlasts(long now) {
        while (!recentBlasts.isEmpty() && now - recentBlasts.peekFirst().when() > BLAST_BRIDGE_MS)
            recentBlasts.removeFirst();
    }
    private UUID nearbyBlastOwner(Location location, double maxRadius) {
        if (location.getWorld() == null) return null;
        expireBlasts(System.currentTimeMillis());
        UUID candidate = null;
        double nearest = maxRadius * maxRadius;
        boolean ambiguous = false;
        for (Blast b : recentBlasts) {
            if (!b.world().equals(location.getWorld().getUID())) continue;
            double distance = b.distanceSquared(location);
            if (distance > maxRadius * maxRadius) continue;
            if (candidate != null && !candidate.equals(b.owner())) ambiguous = true;
            if (distance <= nearest) { candidate = b.owner(); nearest = distance; }
        }
        return ambiguous ? null : candidate;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        UUID owner = tntOwner(tnt);
        if (owner == null) owner = nearbyBlastOwner(tnt.getLocation(), 11.0);
        rememberOwner(tnt, owner);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnition(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();
        if (type == Material.TNT) {
            Material held = event.getItem() == null ? Material.AIR : event.getItem().getType();
            if (held == Material.FLINT_AND_STEEL || held == Material.FIRE_CHARGE)
                rememberBlast(block.getLocation().add(0.5, 0.5, 0.5), event.getPlayer().getUniqueId());
        } else if (type == Material.RESPAWN_ANCHOR || type.name().endsWith("_BED")) {
            // Recorded only briefly; an actual explosion must happen immediately to count.
            rememberBlast(block.getLocation().add(0.5, 0.5, 0.5), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) rememberBlast(event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        Entity e = event.getEntity();
        if (e instanceof TNTPrimed tnt) rememberBlast(e.getLocation(), tntOwner(tnt));
        else if (e instanceof EnderCrystal) rememberBlast(e.getLocation(), storedOwner(e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        UUID attacker = directPlayer(event.getDamager());
        if (attacker != null) rememberOwner(crystal, attacker);
    }

    private UUID attacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof TNTPrimed tnt) return tntOwner(tnt);
        if (damager instanceof EnderCrystal crystal) return storedOwner(crystal);
        UUID direct = directPlayer(damager);
        if (direct != null) return direct;
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof TNTPrimed tnt) return tntOwner(tnt);
        return directPlayer(causing);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBalanceDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        if (config().getBoolean("combat.mace.enabled", true)
                && event.getDamager() instanceof Player attacker
                && attacker.getInventory().getItemInMainHand().getType() == Material.MACE
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.setDamage(CombatMath.scaleDamage(event.getDamage(),
                    config().getDouble("combat.mace.player-damage-multiplier", 0.75)));
        }
        if (config().getBoolean("combat.tnt.enabled", true)
                && event.getDamager() instanceof TNTPrimed tnt
                && target.getUniqueId().equals(tntOwner(tnt))) {
            event.setDamage(CombatMath.scaleDamage(event.getDamage(),
                    config().getDouble("combat.tnt.igniter-self-damage-multiplier", 0.50)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageCredited(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) return;
        UUID player = attacker(event);
        if (player == null || player.equals(victim.getUniqueId())) return;
        boolean explosive = event.getDamager() instanceof TNTPrimed || event.getDamager() instanceof EnderCrystal
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
        lastHit.put(victim.getUniqueId(), new Hit(player, System.currentTimeMillis(), explosive));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosionDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof Player victim)
                || event.getFinalDamage() <= 0 || event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        UUID owner = nearbyBlastOwner(victim.getLocation(), 12.0);
        if (owner != null && !owner.equals(victim.getUniqueId()))
            lastHit.put(victim.getUniqueId(), new Hit(owner, System.currentTimeMillis(), true));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { clearVictim(event.getPlayer().getUniqueId()); }
}
