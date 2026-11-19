package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reusable, stackable minor stat Perks. Every roll is bound to a specific
 * milestone in players.yml, so withdrawing/recovering streak cannot reroll it.
 * Modifiers use keyed/transient attributes and never overwrite other plugins.
 */
public final class PerkManager implements Listener {
    public record Award(int milestone, PerkType type) {}

    private final SpreeCorePlugin plugin;
    private final Map<PerkType, NamespacedKey> modifierKeys = new EnumMap<>(PerkType.class);

    PerkManager(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        for (PerkType type : PerkType.values())
            modifierKeys.put(type, new NamespacedKey(plugin, "perk_" + type.id));
    }

    private String key(PerkType type, String suffix) {
        return "perks." + type.id + "." + suffix;
    }
    public boolean enabled() { return plugin.getConfig().getBoolean("perks.enabled", true); }
    private boolean allowed(PerkType type) {
        return plugin.getConfig().getBoolean(key(type, "enabled"), true);
    }
    private int cap(PerkType type) {
        return Math.max(0, Math.min(8,
                plugin.getConfig().getInt(key(type, "max-copies"), type.defaultMaxCopies)));
    }
    private double perCopy(PerkType type) {
        double configured = plugin.getConfig().getDouble(key(type, "per-copy"), type.defaultPerCopy);
        double limit = type == PerkType.CAPACITY ? 16 : .10;
        return Double.isFinite(configured) ? Math.max(0, Math.min(limit, configured)) : type.defaultPerCopy;
    }

    /** Allocate all previously reached levels on upgrade and when a milestone is crossed. */
    public List<Award> ensureRolls(PlayerDataStore.Profile profile) {
        int achieved = SpreeMath.benefitLevel(Math.max(profile.best, profile.streak));
        List<Award> newAwards = new ArrayList<>();
        List<PerkType> options = new ArrayList<>();
        Map<PerkType, Integer> caps = new EnumMap<>(PerkType.class);
        for (PerkType type : PerkType.values()) {
            if (allowed(type)) options.add(type);
            caps.put(type, cap(type));
        }
        for (int milestone = 2; milestone <= achieved; milestone++) {
            if (!PerkMath.isPerkMilestone(milestone) || profile.perkRolls.containsKey(milestone)) continue;
            PerkType selected = PerkMath.draw(profile.perkRolls, options, caps, ThreadLocalRandom.current());
            if (selected == null) break; // Can be filled later when an admin enables more options.
            profile.perkRolls.put(milestone, selected.id);
            newAwards.add(new Award(milestone, selected));
            plugin.data().dirty();
        }
        return newAwards;
    }

    /** Default migration: no reset or reroll of any saved player data. */
    public void initializeExisting() {
        for (PlayerDataStore.Profile p : plugin.data().all()) ensureRolls(p);
    }

    public int stacks(PlayerDataStore.Profile profile, PerkType type) {
        if (!enabled() || !allowed(type) || profile.streak <= 0) return 0;
        return Math.min(cap(type), PerkMath.copies(profile.perkRolls, type, SpreeMath.benefitLevel(profile.streak)));
    }
    public double bonus(Player player, PerkType type) {
        double total = stacks(plugin.data().get(player), type) * perCopy(type);
        // Server-side hard limits prevent accidental extreme config values.
        // Entity reach maxes at +0.20 blocks; player scale cannot shrink
        // by more than 15%, even if an admin raises a copy limit.
        double limit = type == PerkType.REACH ? .20 : type == PerkType.SCALE ? .15
                : type == PerkType.CAPACITY ? 32 : .30;
        return Math.min(limit, total);
    }

    /** Current bonuses rendered as a separate, compact active-only dashboard item. */
    public String[] activeLore(PlayerDataStore.Profile profile) {
        List<String> lines = new ArrayList<>();
        for (PerkType type : PerkType.values()) {
            int copies = stacks(profile, type);
            if (copies <= 0) continue;
            double amount = Math.min(type == PerkType.REACH ? .20 : type == PerkType.SCALE ? .15
                    : type == PerkType.CAPACITY ? 32 : .30, copies * perCopy(type));
            String unit = type == PerkType.REACH ? format(amount) + " blocks"
                    : type == PerkType.CAPACITY ? format(amount) + " items" : format(amount * 100) + "%";
            String subject = switch (type) {
                case POWER -> " damage dealt";
                case FORTITUDE -> " damage resistance";
                case AGILITY -> " movement speed";
                case DEXTERITY -> " attack speed";
                case RESTORATION -> " healing received";
                case STABILITY -> " knockback resistance";
                case ENDURANCE -> " lower hunger exhaustion";
                case EFFICIENCY -> " block-breaking speed";
                case PRESERVATION -> " durability saved";
                case INSIGHT -> " XP earned";
                case REACH -> " entity reach";
                case SCALE -> " player size";
                case CAPACITY -> " per inventory stack";
                case ALCHEMY -> " beneficial potion duration";
            };
            String sign = type == PerkType.SCALE ? "-" : "+";
            lines.add("&9" + type.display + " &8x" + copies + " &7(" + sign + unit + subject + ")");
        }
        return lines.isEmpty() ? new String[]{"&8No active Perks"}
                : lines.toArray(String[]::new);
    }

    private static String format(double n) {
        return BigDecimal.valueOf(Math.round(n * 100.0) / 100.0).stripTrailingZeros().toPlainString();
    }

    /** Modify the vanilla attribute by a keyed multiplier/offset; leave every other mod intact. */
    private void sync(Player player, PerkType type, Attribute attribute, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        // Negative MULTIPLY_SCALAR_1 shrinks the vanilla SCALE attribute and
        // its hitbox, without replacing the base size or another plugin's modifiers.
        double desired = type == PerkType.SCALE ? -bonus(player, type) : bonus(player, type);
        NamespacedKey key = modifierKeys.get(type);
        AttributeModifier previous = null;
        for (AttributeModifier modifier : instance.getModifiers())
            if (modifier.getKey().equals(key)) { previous = modifier; break; }
        if (previous != null && Math.abs(previous.getAmount() - desired) < 1e-9
                && previous.getOperation() == operation) return;
        if (previous != null) instance.removeModifier(previous);
        if (desired != 0) instance.addTransientModifier(new AttributeModifier(key, desired, operation));
    }

    public void refresh(Player player) {
        if (player.isDead()) return;
        ensureRolls(plugin.data().get(player));
        sync(player, PerkType.AGILITY, Attribute.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        sync(player, PerkType.DEXTERITY, Attribute.ATTACK_SPEED, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        sync(player, PerkType.STABILITY, Attribute.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_NUMBER);
        sync(player, PerkType.EFFICIENCY, Attribute.BLOCK_BREAK_SPEED, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        sync(player, PerkType.REACH, Attribute.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_NUMBER);
        sync(player, PerkType.SCALE, Attribute.SCALE, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        plugin.capacity().refresh(player);
    }

    /** Remove only modifiers owned by SpreeCore. */
    public void clear(Player player) {
        for (var pair : Map.of(
                PerkType.AGILITY, Attribute.MOVEMENT_SPEED,
                PerkType.DEXTERITY, Attribute.ATTACK_SPEED,
                PerkType.STABILITY, Attribute.KNOCKBACK_RESISTANCE,
                PerkType.EFFICIENCY, Attribute.BLOCK_BREAK_SPEED,
                PerkType.REACH, Attribute.ENTITY_INTERACTION_RANGE,
                PerkType.SCALE, Attribute.SCALE).entrySet()) {
            AttributeInstance instance = player.getAttribute(pair.getValue());
            if (instance == null) continue;
            NamespacedKey key = modifierKeys.get(pair.getKey());
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.getKey().equals(key)) { instance.removeModifier(modifier); break; }
            }
        }
    }

    public void shutdown() { for (Player player : Bukkit.getOnlinePlayers()) clear(player); }

    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer()); }

    /** Generic damage dealt by direct player hits and player-fired projectiles.
     * TNT/crystals are excluded so explosive balancing remains unchanged.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dealt(EntityDamageByEntityEvent event) {
        if (event.getDamage() <= 0) return;
        if (event.getDamager() instanceof TNTPrimed || event.getDamager() instanceof EnderCrystal) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) attacker = player;
        }
        if (attacker == null || attacker.equals(event.getEntity())) return;
        double amount = bonus(attacker, PerkType.POWER);
        if (amount > 0) event.setDamage(event.getDamage() * (1.0 + amount));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void taken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getDamage() <= 0) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        double amount = bonus(player, PerkType.FORTITUDE);
        if (amount > 0) event.setDamage(event.getDamage() * (1.0 - amount));
    }

    /**
     * Alchemy adds a small amount of time to newly acquired beneficial potion
     * effects. It ignores commands, plugin buffs, beacons and other continuously
     * renewed sources (including Spree's permanent milestone Infusions).
     * Waiting one tick allows vanilla to actually apply the new effect first.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void potionChanged(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!PotionDurationMath.isPotionSource(event.getCause())) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        PotionEffect effect = event.getNewEffect();
        if (effect == null || effect.isInfinite() || effect.getDuration() < 20
                || effect.getType().isInstant()
                || effect.getType().getCategory() != PotionEffectTypeCategory.BENEFICIAL)
            return;
        int extraTicks = PotionDurationMath.extraTicks(effect.getDuration(),
                bonus(player, PerkType.ALCHEMY));
        if (extraTicks <= 0) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            PotionEffect current = player.getPotionEffect(effect.getType());
            if (current == null || current.isInfinite()
                    || current.getAmplifier() != effect.getAmplifier()) return;
            // If another potion, command or plugin replaced this effect before
            // the task ran, don't overwrite or extend the unrelated effect.
            int elapsed = effect.getDuration() - current.getDuration();
            if (elapsed < 0 || elapsed > 4) return;
            int upgraded = Math.min(Integer.MAX_VALUE - 1,
                    current.getDuration() + extraTicks);
            if (upgraded > current.getDuration())
                player.addPotionEffect(current.withDuration(upgraded), true);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void healed(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getAmount() <= 0) return;
        double amount = bonus(player, PerkType.RESTORATION);
        if (amount > 0) event.setAmount(event.getAmount() * (1.0 + amount));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void exhausted(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getExhaustion() <= 0) return;
        double amount = bonus(player, PerkType.ENDURANCE);
        if (amount > 0) event.setExhaustion((float)(event.getExhaustion() * (1.0 - amount)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void durability(PlayerItemDamageEvent event) {
        int damage = event.getDamage();
        if (damage <= 0) return;
        Player player = event.getPlayer();
        double amount = bonus(player, PerkType.PRESERVATION);
        if (amount <= 0) return;
        PlayerDataStore.Profile profile = plugin.data().get(player);
        double credit = profile.durabilityCredit + damage * amount;
        int saved = Math.min(damage, (int) Math.floor(credit + 1e-9));
        profile.durabilityCredit = PerkMath.fractionalPart(credit - saved);
        plugin.data().dirty();
        if (saved >= damage) event.setCancelled(true);
        else if (saved > 0) event.setDamage(damage - saved);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void experience(PlayerExpChangeEvent event) {
        int exp = event.getAmount();
        if (exp <= 0) return;
        PlayerDataStore.Profile profile = plugin.data().get(event.getPlayer());
        double amount = bonus(event.getPlayer(), PerkType.INSIGHT);
        if (amount <= 0) return;
        double credit = profile.experienceCredit + exp * amount;
        int extra = (int) Math.floor(credit + 1e-9);
        profile.experienceCredit = PerkMath.fractionalPart(credit);
        plugin.data().dirty();
        if (extra > 0) event.setAmount(Math.addExact(exp, Math.min(extra, Integer.MAX_VALUE - exp)));
    }
}
