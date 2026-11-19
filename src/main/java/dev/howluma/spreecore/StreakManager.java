package dev.howluma.spreecore;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Killstreak health, Fractured state, Spree items, tokens, deaths and HUD. */
public final class StreakManager implements Listener {
    private final SpreeCorePlugin plugin;
    private final NamespacedKey tokenOwner, tokenValue, tokenSerial, spreeConsumable;
    private final Map<UUID, Integer> lastSpreeItemTick = new HashMap<>();
    private final Map<UUID, Long> tokenWarnings = new HashMap<>();
    private final Set<UUID> managedGlow = new HashSet<>();
    private static final Pattern LEGACY_WORTH = Pattern.compile("Worth:\\s*\\+(\\d+)");
    private static final Pattern LEGACY_OWNER = Pattern.compile("Owner ID:\\s*([0-9a-fA-F-]{36})");

    StreakManager(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        tokenOwner = new NamespacedKey(plugin, "token_owner");
        tokenValue = new NamespacedKey(plugin, "token_value");
        tokenSerial = new NamespacedKey(plugin, "token_serial");
        spreeConsumable = new NamespacedKey(plugin, "spree_consumable");
    }
    private FileConfiguration c() { return plugin.getConfig(); }
    public PlayerDataStore.Profile get(Player player) { return plugin.data().get(player); }
    public int streak(OfflinePlayer player) { return plugin.data().get(player).streak; }
    public int cap() { return c().getInt("general.max-killstreak", 30); }
    public String display(int streak) { return Text.formatStreak(streak, c().getString("general.fractured-label", "&c&lFractured Killstreak")); }

    /** Single entry point for every stat modification; handles best, HUD, health, relic leader. */
    public void setStreak(UUID id, int amount, boolean admin) {
        PlayerDataStore.Profile p = plugin.data().get(id);
        int newStreak = SpreeMath.clamp(amount, cap());
        if (admin) newStreak = Math.max(0, newStreak);
        int old = p.streak;
        p.streak = newStreak;
        if (newStreak > p.best) {
            p.best = newStreak;
            Player online = Bukkit.getPlayer(id);
            if (online != null && newStreak >= 5 && newStreak != old)
                Text.send(online, "&9&lNEW PERSONAL BEST &f" + newStreak + " Killstreak!");
        }
        plugin.data().dirty();
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            // Do not change a dead player's inventory/health during PlayerDeathEvent:
            // Bukkit already computed its drops and inventory changes can duplicate items.
            if (!online.isDead()) {
                update(online);
            }
            tabName(online);
        }
        plugin.legendaries().queueBloodsBaneSync();
    }
    public void addStreak(Player player, int gain) {
        setStreak(player.getUniqueId(), SpreeMath.applyKill(streak(player), gain, cap()), false);
    }
    public void tabName(Player player) { plugin.indicators().refreshPlayer(player); }
    public void update(Player player) {
        int streak = streak(player);
        double desired = SpreeMath.maxHealthPoints(streak,
                c().getDouble("killstreak.base-health-hearts", 10),
                c().getDouble("killstreak.health-per-level-hearts", 0.5));
        // Juggernaut's temporary event health is independent of killstreak rewards.
        if (plugin.events() != null && plugin.events().isJuggernaut(player))
            desired = Math.min(1024.0, desired + 2.0 * c().getDouble("events.juggernaut.bonus-hearts", 10));
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null && Math.abs(attribute.getBaseValue() - desired) > 0.01) {
            double oldMax = attribute.getValue();
            attribute.setBaseValue(desired);
            double newMax = attribute.getValue();
            if (newMax > oldMax) {
                player.setHealth(Math.min(newMax, player.getHealth() + (newMax - oldMax)));
            } else if (player.getHealth() > newMax) {
                player.setHealth(Math.max(1.0, newMax));
            }
        }
        // Only temporary event/forge visibility remains. All automatic
        // killstreak potion bonuses and passives have been removed.
        boolean eventGlow = (plugin.events() != null && plugin.events().isJuggernaut(player))
                || (plugin.forges() != null && plugin.forges().isForging(player));
        if (eventGlow) {
            PotionEffect prior = player.getPotionEffect(PotionEffectType.GLOWING);
            if (prior == null || prior.getDuration() <= 60) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 110, 0, true, false, true));
                managedGlow.add(player.getUniqueId());
            }
        } else if (managedGlow.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }
    public void refreshOnline() { for (Player player : Bukkit.getOnlinePlayers()) update(player); }
    public void renderHud() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerDataStore.Profile profile = get(player);
            if (profile.hideHud) continue;
            String color = plugin.indicators().streakColor(profile.streak);
            Text.action(player, StreakIndicators.ICON_COLOR + "⚔ &7Killstreak: " + color + display(profile.streak));
        }
    }
    public void shutdown() { /* Temporary effects expire naturally. */ }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.data().get(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            update(player);
            tabName(player);
            plugin.legendaries().migrate(player);
            if (streak(player) >= c().getInt("general.announce-joining-streak", 10))
                Bukkit.broadcastMessage(Text.c(plugin.prefix() + "&f" + player.getName() + " &7joined on a " + plugin.indicators().streakColor(streak(player)) + streak(player) + " &7killstreak!"));
        }, 2L);
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                update(player);
            }
        }, 2L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        tokenWarnings.remove(event.getPlayer().getUniqueId());
        lastSpreeItemTick.remove(event.getPlayer().getUniqueId());
        managedGlow.remove(event.getPlayer().getUniqueId());
        plugin.data().dirty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void death(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = plugin.combat().killer(victim);
        plugin.combat().clearVictim(victim.getUniqueId());
        if (victim.equals(killer)) killer = null;
        PlayerDataStore.Profile victimProfile = get(victim);
        int old = victimProfile.streak;
        victimProfile.deaths++;
        if (old > 0) victimProfile.ended++;
        plugin.data().dirty();
        if (killer == null) {
            if (old >= 0) dropToken(victim, SpreeMath.killGain(old));
            setStreak(victim.getUniqueId(), SpreeMath.naturalDeathStreak(old), false);
            if (old == 0) Text.send(victim, plugin.prefix() + "&cYou have a Fractured Killstreak. Reclaim your own token or get a kill.");
            else Text.send(victim, plugin.prefix() + "&7Natural death: your streak is now " + display(streak(victim)));
        } else {
            victimProfile.revengeAgainst = killer.getUniqueId();
            setStreak(victim.getUniqueId(), SpreeMath.deathStreak(old), false);
            PlayerDataStore.Profile attacker = get(killer);
            attacker.kills++;
            boolean reward = eligibleKill(attacker, victim.getUniqueId(), old);
            int gain = 0;
            if (reward) {
                gain = SpreeMath.killGain(old);
                if (plugin.events() != null) gain += plugin.events().timedKillBonus();
                if (c().getBoolean("killstreak.revenge.enabled", true) && victim.getUniqueId().equals(attacker.revengeAgainst)) {
                    gain += c().getInt("killstreak.revenge.bonus-streak", 2);
                    attacker.revengeAgainst = null;
                    Text.send(killer, "&4&lREVENGE COMPLETE &7- bonus streak!");
                }
                addStreak(killer, gain);
                Text.send(killer, plugin.prefix() + "&a+" + gain + " Killstreak &7for eliminating &f" + victim.getName());
            } else {
                Text.action(killer, "&cNo streak earned (anti-farm protection)");
            }
            if (plugin.events() != null) plugin.events().handleKill(killer, victim, reward);
            plugin.data().dirty();
            killer.playSound(killer.getLocation(), Sound.BLOCK_VAULT_BREAK, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isOnline() && !victim.isDead()) update(victim);
        }, 2L);
    }
    private boolean eligibleKill(PlayerDataStore.Profile killer, UUID victim, int oldStreak) {
        if (!c().getBoolean("killstreak.anti-farm.enabled", true)) return true;
        if (oldStreak < 0 && c().getBoolean("killstreak.anti-farm.block-rewards-for-fractured-victim", true)) return false;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, c().getLong("killstreak.anti-farm.same-victim-cooldown-seconds", 300)) * 1000L;
        killer.recentVictims.entrySet().removeIf(entry -> now - entry.getValue() > cooldown);
        Long previous = killer.recentVictims.get(victim);
        if (previous != null && now - previous < cooldown) return false;
        killer.recentVictims.put(victim, now);
        plugin.data().dirty();
        return true;
    }
    /** A distinct consumable: unlike environmental-death trap tokens, this grants exactly +1 on use. */
    public ItemStack createSpreeItem(int amount) {
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("Amount must be 1-64");
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.c("&9⚔ &9&lSpree"));
        meta.setLore(List.of(Text.c("&7Right-click to gain &9&l+1 Killstreak"),
                Text.c("&8Consumed on use")));
        meta.getPersistentDataContainer().set(spreeConsumable, PersistentDataType.BYTE, (byte) 1);
        plugin.models().token(meta);
        item.setItemMeta(meta);
        return item;
    }
    public void giveSpreeItem(Player player, int amount) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(createSpreeItem(amount));
        for (ItemStack item : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), item);
    }
    // Right-click-air events may arrive pre-cancelled when vanilla has no action.
    // Handle those while cancelling the normal Heart of the Sea interaction.
    @EventHandler(priority = EventPriority.HIGH)
    public void useSpreeItem(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        ItemStack item = hand == EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (item.getType() != Material.HEART_OF_THE_SEA || !item.hasItemMeta()
                || !item.getItemMeta().getPersistentDataContainer().has(spreeConsumable, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        int tick = player.getTicksLived();
        if (lastSpreeItemTick.getOrDefault(player.getUniqueId(), -1) == tick) return;
        lastSpreeItemTick.put(player.getUniqueId(), tick);
        if (streak(player) >= cap()) {
            Text.action(player, "&cYou are already at the maximum killstreak.");
            return; // Never waste the item at the cap.
        }
        ItemStack remaining = item.clone();
        if (remaining.getAmount() == 1) remaining = new ItemStack(Material.AIR);
        else remaining.setAmount(remaining.getAmount() - 1);
        if (hand == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(remaining);
        else player.getInventory().setItemInOffHand(remaining);
        addStreak(player, 1);
        Text.send(player, plugin.prefix() + "&9&l+1 Killstreak &7from Spree!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.25f);
    }

    private void dropToken(Player player, int value) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.c("&9&lSpree Token"));
        meta.setLore(List.of(Text.c("&8&oSpree Token"), Text.c("&aWorth: +" + value + " Spree"),
                Text.c("&7Dropped by &f" + player.getName()), Text.c("&7Pick up to claim the Spree")));
        meta.getPersistentDataContainer().set(tokenOwner, PersistentDataType.STRING, player.getUniqueId().toString());
        plugin.models().token(meta);
        meta.getPersistentDataContainer().set(tokenValue, PersistentDataType.INTEGER, value);
        meta.getPersistentDataContainer().set(tokenSerial, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        player.getWorld().dropItemNaturally(player.getLocation(), item);
    }
    private record Token(UUID owner, int value) {}
    private Token token(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        String owner = meta.getPersistentDataContainer().get(tokenOwner, PersistentDataType.STRING);
        Integer value = meta.getPersistentDataContainer().get(tokenValue, PersistentDataType.INTEGER);
        if (owner != null && value != null) {
            try { return value > 0 && value <= cap() ? new Token(UUID.fromString(owner), value) : null; }
            catch (IllegalArgumentException ignored) { return null; }
        }
        if (stack.getType() != Material.HEART_OF_THE_SEA && stack.getType() != Material.NETHERITE_SCRAP) return null;
        if (meta.getLore() == null) return null;
        String lore = String.join(" ", meta.getLore().stream().map(ChatColor::stripColor).toList());
        if (!lore.contains("Spree Token")) return null;
        Matcher m1 = LEGACY_WORTH.matcher(lore);
        Matcher m2 = LEGACY_OWNER.matcher(lore);
        if (!m1.find() || !m2.find()) return null;
        try { return new Token(UUID.fromString(m2.group(1)), Integer.parseInt(m1.group(1))); }
        catch (IllegalArgumentException ex) { return null; }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        Token token = token(stack);
        if (token != null) {
            int current = streak(player);
            if (player.getUniqueId().equals(token.owner()) && current > 0) {
                event.setCancelled(true);
                long now = System.currentTimeMillis();
                if (now - tokenWarnings.getOrDefault(player.getUniqueId(), 0L) > 2000L) {
                    Text.action(player, "&cReclaim your own token at zero or while Fractured.");
                    tokenWarnings.put(player.getUniqueId(), now);
                }
                return;
            }
            event.setCancelled(true);
            event.getItem().remove(); // Atomic, never placed into inventory and reused.
            if (player.getUniqueId().equals(token.owner())) {
                if (current < 0) {
                    setStreak(player.getUniqueId(), 0, false);
                    player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + 2));
                    Text.send(player, plugin.prefix() + "&aFractured Killstreak repaired! You're back at zero.");
                } else Text.send(player, plugin.prefix() + "&aYour own token was reclaimed (no bonus).");
            } else {
                addStreak(player, token.value());
                Text.send(player, plugin.prefix() + "&a+" + token.value() + " Killstreak &7from a Spree Token!");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            return;
        }
    }
}
