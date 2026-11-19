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
    private final MilestoneEffects milestones;
    private static final Pattern LEGACY_WORTH = Pattern.compile("Worth:\\s*\\+(\\d+)");
    private static final Pattern LEGACY_OWNER = Pattern.compile("Owner ID:\\s*([0-9a-fA-F-]{36})");

    StreakManager(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        milestones = new MilestoneEffects(plugin);
        tokenOwner = new NamespacedKey(plugin, "token_owner");
        tokenValue = new NamespacedKey(plugin, "token_value");
        tokenSerial = new NamespacedKey(plugin, "token_serial");
        spreeConsumable = new NamespacedKey(plugin, "spree_consumable");
    }
    private FileConfiguration c() { return plugin.getConfig(); }
    public PlayerDataStore.Profile get(Player player) { return plugin.data().get(player); }
    public int streak(OfflinePlayer player) { return plugin.data().get(player).streak; }
    /** Technical maximum score, separate from the level-30 progression ceiling. */
    public int cap() { return Math.max(31, Math.min(1_000_000_000,
            c().getInt("general.max-killstreak", 1_000_000_000))); }
    public int progressionCap() { return SpreeMath.BENEFIT_CAP; }
    public String display(int streak) { return Text.formatStreak(streak, c().getString("general.fractured-label", "&c&lFractured")); }

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
        List<PerkManager.Award> newPerks = plugin.perks().ensureRolls(p);
        plugin.data().dirty();
        Player online = Bukkit.getPlayer(id);
        if (online != null && newStreak > old) {
            List<String> gainedPerks = new ArrayList<>();
            for (int level = Math.max(1, Math.min(progressionCap(), old) + 1);
                    level <= Math.min(progressionCap(), newStreak); level++) {
                if (!PerkMath.isPerkMilestone(level)) continue;
                String perk = p.perkRolls.get(level);
                PerkType type = PerkType.fromId(perk);
                if (type != null) gainedPerks.add("&f" + type.display + " &8(level " + level + ")");
            }
            if (!gainedPerks.isEmpty())
                Text.send(online, plugin.prefix() + "&9Perk gained: &f" + String.join("&7, ", gainedPerks));
            if (!admin) plugin.hunt().announceReached(online, old, newStreak);
            // Even administrative level gains should tell the affected player
            // which milestone rewards became claimable (without a GUI popup).
            plugin.progression().notifyMilestones(online, old, newStreak);
        }
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
        // Milestone benefits are independent of temporary event/forge visibility.
        boolean eventGlow = (plugin.events() != null && plugin.events().isJuggernaut(player))
                || (plugin.forges() != null && plugin.forges().isForging(player));
        milestones.refresh(player, streak, eventGlow);
        plugin.perks().refresh(player);
        plugin.talents().refresh(player);
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
    public void shutdown() { for (Player player : Bukkit.getOnlinePlayers()) milestones.clear(player); }

    /** Normalize legacy item_model overrides for real PDC-tagged Spree
     * consumables and tokens already in an inventory. Ignore unrelated items. */
    public int migrateTokenModels(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int changed = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != Material.HEART_OF_THE_SEA || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            boolean owned = meta.getPersistentDataContainer().has(spreeConsumable, PersistentDataType.BYTE)
                    || meta.getPersistentDataContainer().has(tokenOwner, PersistentDataType.STRING);
            if (!owned || !plugin.models().token(meta)) continue;
            item.setItemMeta(meta);
            contents[slot] = item;
            changed++;
        }
        if (changed > 0) player.getInventory().setContents(contents);
        return changed;
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.data().get(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            update(player);
            tabName(player);
            if (!get(player).retiredTalentMilestones.isEmpty())
                Text.send(player, plugin.prefix()
                        + "&bIndustrious was retired. &7Your earned Talent slot(s) "
                        + get(player).retiredTalentMilestones
                        + " can be reselected &afor free &7in &9/spree &7> Talents.");
            plugin.legendaries().migrate(player);
            migrateTokenModels(player);
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
        milestones.clear(event.getPlayer());
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
            if (old >= 0) dropToken(victim, SpreeMath.killGain(old,
                    c().getInt("killstreak.kill-rewards.base-reward", 1),
                    c().getInt("killstreak.kill-rewards.levels-per-tier", 5),
                    c().getInt("killstreak.kill-rewards.extra-per-tier", 1)));
            setStreak(victim.getUniqueId(),
                    plugin.talents().protectedDeath(victim,old,SpreeMath.naturalDeathStreak(old)),false);
            if (old == 0) Text.send(victim, plugin.prefix() + "&cYou have a Fractured Killstreak. Reclaim your own token or get a kill.");
            else Text.send(victim, plugin.prefix() + "&7Natural death: your streak is now " + display(streak(victim)));
        } else {
            victimProfile.revengeAgainst = killer.getUniqueId();
            setStreak(victim.getUniqueId(),
                    plugin.talents().protectedDeath(victim,old,SpreeMath.deathStreak(old)),false);
            PlayerDataStore.Profile attacker = get(killer);
            attacker.kills++;
            boolean reward = eligibleKill(attacker, victim.getUniqueId(), old);
            int gain = 0;
            if (reward) {
                gain = SpreeMath.killGain(old,
                        c().getInt("killstreak.kill-rewards.base-reward", 1),
                        c().getInt("killstreak.kill-rewards.levels-per-tier", 5),
                        c().getInt("killstreak.kill-rewards.extra-per-tier", 1));
                if (plugin.events() != null) gain += plugin.events().timedKillBonus();
                if (c().getBoolean("killstreak.revenge.enabled", false) && victim.getUniqueId().equals(attacker.revengeAgainst)) {
                    gain += c().getInt("killstreak.revenge.bonus-streak", 2);
                    attacker.revengeAgainst = null;
                    Text.send(killer, "&4&lREVENGE COMPLETE &7- bonus streak!");
                }
                addStreak(killer, gain);
                plugin.talents().onEligibleKill(killer);
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
    /** True only for the SpreeCore +1 killstreak consumable, never vanilla Hearts of the Sea. */
    public boolean isSpreeItem(ItemStack item) {
        return item != null && item.getType() == Material.HEART_OF_THE_SEA && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                    .has(spreeConsumable, PersistentDataType.BYTE);
    }

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
    public record WithdrawalResult(boolean success, String message, int amount, int remaining) {}

    /** Gives exactly the requested amount of the pre-existing +1 consumable,
     * deducting the streak only after successful inventory insertion.
     * Full inventory means no items and no deduction; no ground drops.
     */
    public WithdrawalResult withdrawSpree(Player player, int amount) {
        int streak = streak(player);
        if (streak <= 0) return new WithdrawalResult(false,
                "You need a positive killstreak to withdraw Spree.", 0, streak);
        if (amount <= 0 || amount > streak) return new WithdrawalResult(false,
                "Withdraw between 1 and " + streak + " Spree.", 0, streak);

        PlayerInventory inventory = player.getInventory();
        ItemStack example = createSpreeItem(1);
        int stackMax = Math.max(1, Math.min(inventory.getMaxStackSize(), example.getMaxStackSize()));
        int capacity = 0;
        for (ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) capacity += stackMax;
            else if (existing.isSimilar(example))
                capacity += Math.max(0, Math.min(stackMax, existing.getMaxStackSize()) - existing.getAmount());
        }
        if (capacity < amount) return new WithdrawalResult(false,
                "Not enough inventory space. You can fit " + capacity + " more Spree items.", 0, streak);

        List<ItemStack> items = new ArrayList<>();
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, stackMax);
            items.add(createSpreeItem(n));
            left -= n;
        }
        ItemStack[] backup = inventory.getStorageContents();
        for (int i = 0; i < backup.length; i++) if (backup[i] != null) backup[i] = backup[i].clone();
        Map<Integer, ItemStack> leftover = inventory.addItem(items.toArray(ItemStack[]::new));
        if (!leftover.isEmpty()) {
            inventory.setStorageContents(backup);
            return new WithdrawalResult(false, "Inventory changed; nothing was withdrawn.", 0, streak);
        }

        int remaining = SpreeMath.withdrawRemaining(streak, amount);
        setStreak(player.getUniqueId(), remaining, false);
        return new WithdrawalResult(true, "Spree withdrawn.", amount, remaining);
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
        if (!isSpreeItem(item)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        int tick = player.getTicksLived();
        if (lastSpreeItemTick.getOrDefault(player.getUniqueId(), -1) == tick) return;
        lastSpreeItemTick.put(player.getUniqueId(), tick);
        if (streak(player) >= cap()) {
            Text.action(player, "&cMaximum supported killstreak reached.");
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
