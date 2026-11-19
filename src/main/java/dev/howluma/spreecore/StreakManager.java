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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Killstreaks, recovery, tokens, item gates, passives, and efficient HUD updates. */
public final class StreakManager implements Listener {
    private final SpreeCorePlugin plugin;
    private final NamespacedKey tokenOwner, tokenValue, tokenSerial;
    private final Map<UUID, Long> tokenWarnings = new HashMap<>();
    private final Set<UUID> managedGlow = new HashSet<>();
    private static final Pattern LEGACY_WORTH = Pattern.compile("Worth:\\s*\\+(\\d+)");
    private static final Pattern LEGACY_OWNER = Pattern.compile("Owner ID:\\s*([0-9a-fA-F-]{36})");

    StreakManager(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        tokenOwner = new NamespacedKey(plugin, "token_owner");
        tokenValue = new NamespacedKey(plugin, "token_value");
        tokenSerial = new NamespacedKey(plugin, "token_serial");
    }
    private FileConfiguration c() { return plugin.getConfig(); }
    public PlayerDataStore.Profile get(Player player) { return plugin.data().get(player); }
    public int streak(OfflinePlayer player) { return plugin.data().get(player).streak; }
    public int cap() { return c().getInt("general.max-killstreak", 30); }
    public String display(int streak) { return Text.formatStreak(streak, c().getString("general.recovery-label", "&cRECOVERY")); }

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
                enforceInventory(online);
            }
            tabName(online);
        }
        plugin.legendaries().queueBloodsBaneSync();
    }
    public void addStreak(Player player, int gain) {
        setStreak(player.getUniqueId(), SpreeMath.applyKill(streak(player), gain, cap()), false);
    }
    public void tabName(Player player) {
        String color = streak(player) >= 25 ? "&5" : streak(player) >= 15 ? "&6" : streak(player) >= 10 ? "&e" : streak(player) >= 5 ? "&a" : streak(player) < 0 ? "&c" : "&7";
        player.setPlayerListName(Text.c("&f" + player.getName() + " &8| " + color + display(streak(player))));
    }
    public void update(Player player) {
        int streak = streak(player);
        int hearts = SpreeMath.healthHearts(streak,
                c().getInt("killstreak.base-health-hearts", 10),
                c().getInt("killstreak.max-health-hearts", 20));
        if (plugin.events() != null && plugin.events().isJuggernaut(player))
            hearts += c().getInt("events.juggernaut.bonus-hearts", 10);
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            double desired = hearts * 2.0;
            double oldMax = attribute.getValue();
            if (Math.abs(attribute.getBaseValue() - desired) > 0.01) {
                attribute.setBaseValue(desired);
                double newMax = attribute.getValue();
                if (newMax > oldMax) player.setHealth(Math.min(newMax, player.getHealth() + newMax - oldMax));
                else if (player.getHealth() > newMax) player.setHealth(Math.max(1.0, newMax));
            }
        }
        // Only apply effects for their threshold; never scan all inventories to do this.
        passive(player, PotionEffectType.FIRE_RESISTANCE, streak >= c().getInt("killstreak.passives.fire-resistance-streak", 4), 0);
        passive(player, PotionEffectType.HERO_OF_THE_VILLAGE, streak >= c().getInt("killstreak.passives.hero-streak", 7), 0);
        passive(player, PotionEffectType.WEAVING, streak >= c().getInt("killstreak.passives.weaving-streak", 10), 0);
        boolean fast = streak >= c().getInt("killstreak.passives.speed-streak", 15);
        if (fast && !plugin.legendaries().hasAquafierSpeed(player)) passive(player, PotionEffectType.SPEED, true, 0);
        boolean glow = streak >= c().getInt("killstreak.passives.glowing-streak", 15)
                || (plugin.events() != null && plugin.events().isJuggernaut(player))
                || (plugin.forges() != null && plugin.forges().isForging(player));
        if (glow) {
            passive(player, PotionEffectType.GLOWING, true, 0);
            managedGlow.add(player.getUniqueId());
        } else if (managedGlow.remove(player.getUniqueId())) player.removePotionEffect(PotionEffectType.GLOWING);
        if (plugin.events() != null) plugin.events().applyTimedBuffs(player, streak);
    }
    private void passive(Player player, PotionEffectType type, boolean enabled, int amplifier) {
        if (!enabled) return;
        PotionEffect prior = player.getPotionEffect(type);
        if (prior == null || (prior.getAmplifier() <= amplifier && prior.getDuration() <= 60)) {
            player.addPotionEffect(new PotionEffect(type, 110, amplifier, true, false, true));
        }
    }
    public void refreshOnline() { for (Player player : Bukkit.getOnlinePlayers()) update(player); }
    public void renderHud() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerDataStore.Profile profile = get(player);
            if (profile.hideHud) continue;
            String color = profile.streak < 0 ? "&c" : profile.streak >= 10 ? "&9&l" : "&9";
            Text.action(player, "&9⚔ &fKillstreak: " + color + display(profile.streak));
        }
    }
    public void shutdown() { /* Temporary effects expire naturally. */ }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.data().get(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            update(player);
            enforceInventory(player);
            tabName(player);
            plugin.legendaries().migrate(player);
            if (streak(player) >= c().getInt("general.announce-joining-streak", 10))
                Bukkit.broadcastMessage(Text.c(plugin.prefix() + "&f" + player.getName() + " &7joined on a &9" + streak(player) + " &7killstreak!"));
        }, 2L);
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                update(player);
                enforceInventory(player); // Also handles keepInventory servers safely.
            }
        }, 2L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        tokenWarnings.remove(event.getPlayer().getUniqueId());
        managedGlow.remove(event.getPlayer().getUniqueId());
        plugin.data().dirty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void death(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (victim.equals(killer)) killer = null;
        PlayerDataStore.Profile victimProfile = get(victim);
        int old = victimProfile.streak;
        victimProfile.deaths++;
        if (old > 0) victimProfile.ended++;
        plugin.data().dirty();
        if (killer == null) {
            if (old >= 0) dropToken(victim, SpreeMath.killGain(old));
            setStreak(victim.getUniqueId(), SpreeMath.naturalDeathStreak(old), false);
            if (old == 0) Text.send(victim, plugin.prefix() + "&cYou are in Recovery. Reclaim your token or get a kill.");
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
        if (oldStreak < 0 && c().getBoolean("killstreak.anti-farm.block-rewards-for-negative-victim", true)) return false;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, c().getLong("killstreak.anti-farm.same-victim-cooldown-seconds", 300)) * 1000L;
        killer.recentVictims.entrySet().removeIf(entry -> now - entry.getValue() > cooldown);
        Long previous = killer.recentVictims.get(victim);
        if (previous != null && now - previous < cooldown) return false;
        killer.recentVictims.put(victim, now);
        plugin.data().dirty();
        return true;
    }
    private void dropToken(Player player, int value) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.c("&a&lSpree Token"));
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
                    Text.action(player, "&cYou can only reclaim your own token at zero or during Recovery.");
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
                    Text.send(player, plugin.prefix() + "&aRecovery completed! You're back at zero.");
                } else Text.send(player, plugin.prefix() + "&aYour own token was reclaimed (no bonus).");
            } else {
                addStreak(player, token.value());
                Text.send(player, plugin.prefix() + "&a+" + token.value() + " Killstreak &7from a Spree Token!");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            return;
        }
        if (restricted(player, stack.getType())) event.setCancelled(true);
        if (stack.getType() == Material.ENDER_PEARL &&
                countItems(player.getInventory(), Material.ENDER_PEARL) >= c().getInt("killstreak.restrictions.max-ender-pearls", 3))
            event.setCancelled(true);
    }
    public boolean restricted(Player player, Material material) {
        int s = streak(player);
        return switch (material) {
            case TOTEM_OF_UNDYING -> s < c().getInt("killstreak.restrictions.totem", 1);
            case NETHERITE_SWORD -> s < c().getInt("killstreak.restrictions.netherite-sword", 2);
            case NETHERITE_AXE -> s < c().getInt("killstreak.restrictions.netherite-axe", 5);
            case ENCHANTED_GOLDEN_APPLE -> s < c().getInt("killstreak.restrictions.enchanted-golden-apple", 8);
            case NETHERITE_HELMET -> s < c().getInt("killstreak.restrictions.netherite-helmet", 12);
            case ENDER_PEARL -> s < c().getInt("killstreak.restrictions.ender-pearl", 14);
            default -> false;
        };
    }
    private int countItems(PlayerInventory inventory, Material type) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) if (item != null && item.getType() == type) total += item.getAmount();
        return total;
    }
    public void safetyInventoryCheck() { for (Player p : Bukkit.getOnlinePlayers()) enforceInventory(p); }
    public void enforceInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        int pearlsRemaining = c().getInt("killstreak.restrictions.max-ender-pearls", 3);
        ItemStack[] all = inv.getContents();
        for (int i = 0; i < all.length; i++) {
            ItemStack item = all[i];
            if (item == null || item.getType() == Material.AIR) continue;
            boolean block = restricted(player, item.getType());
            if (item.getType() == Material.ENDER_PEARL && !block) {
                if (item.getAmount() > pearlsRemaining) {
                    int extra = item.getAmount() - Math.max(0, pearlsRemaining);
                    ItemStack dropped = item.clone(); dropped.setAmount(extra);
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                    if (pearlsRemaining == 0) all[i] = null;
                    else { ItemStack kept = item.clone(); kept.setAmount(pearlsRemaining); all[i] = kept; }
                    pearlsRemaining = 0;
                } else pearlsRemaining -= item.getAmount();
            } else if (block) {
                all[i] = null;
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        inv.setContents(all);
    }
    @EventHandler(ignoreCancelled = true) public void inventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) enforceInventory(p); });
    }
    @EventHandler(ignoreCancelled = true) public void inventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) enforceInventory(p); });
    }
    @EventHandler(ignoreCancelled = true) public void interact(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.ENDER_PEARL && restricted(event.getPlayer(), Material.ENDER_PEARL)) {
            event.setCancelled(true);
            Text.action(event.getPlayer(), "&cYou need " + c().getInt("killstreak.restrictions.ender-pearl", 14) + " Killstreak for pearls.");
        }
    }
    @EventHandler(ignoreCancelled = true) public void resurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player p && restricted(p, Material.TOTEM_OF_UNDYING)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void consume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        if (restricted(event.getPlayer(), stack.getType())) { event.setCancelled(true); return; }
        if (!c().getBoolean("killstreak.potion-bonuses.enabled", true) || stack.getType() != Material.POTION) return;
        Player player = event.getPlayer();
        int streak = streak(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) potionBoost(player, streak);
        }, 2L);
    }
    @EventHandler(ignoreCancelled = true) public void splash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) return;
        if (!c().getBoolean("killstreak.potion-bonuses.enabled", true)) return;
        int s = streak(thrower);
        List<Player> affected = event.getAffectedEntities().stream()
                .filter(Player.class::isInstance).map(Player.class::cast).toList();
        Bukkit.getScheduler().runTaskLater(plugin, () -> affected.forEach(p -> { if (p.isOnline()) potionBoost(p, s); }), 2L);
    }
    private void potionBoost(Player p, int sourceStreak) {
        int base = c().getInt("killstreak.potion-bonuses.tier-one-min-streak", 2);
        if (sourceStreak < base) return;
        for (PotionEffectType type : List.of(PotionEffectType.SPEED, PotionEffectType.STRENGTH)) {
            PotionEffect effect = p.getPotionEffect(type);
            if (effect == null || effect.getAmplifier() != 1) continue; // Only extend Speed II / Strength II.
            int seconds;
            if (sourceStreak >= c().getInt(type == PotionEffectType.SPEED
                    ? "killstreak.potion-bonuses.speed-tier-three-min-streak"
                    : "killstreak.potion-bonuses.strength-tier-three-min-streak", 12))
                seconds = c().getInt("killstreak.potion-bonuses.tier-three-seconds", 480);
            else if (sourceStreak >= c().getInt("killstreak.potion-bonuses.tier-two-min-streak", 6))
                seconds = c().getInt("killstreak.potion-bonuses.tier-two-seconds", 135);
            else seconds = c().getInt("killstreak.potion-bonuses.tier-one-seconds", 112);
            p.addPotionEffect(new PotionEffect(type, seconds * 20, 1, effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
    }
}
