package dev.howluma.spreecore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Every legendary has one canonical PDC ID and shares abilities/cooldowns/storage logic. */
public final class LegendaryManager implements Listener {
    private final SpreeCorePlugin plugin;
    private final NamespacedKey itemId, testId, frostId, volleyId, visualId;
    private final Map<UUID, EnumMap<LegendaryType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, EnumMap<LegendaryType, Long>> armed = new HashMap<>();
    private final Map<UUID, Integer> darkheartHits = new HashMap<>(), hailstormShots = new HashMap<>();
    private final Map<UUID, Combo> baneCombos = new HashMap<>(), aquaCombos = new HashMap<>();
    private final Map<UUID, Long> aquafierActive = new HashMap<>(), frozen = new HashMap<>();
    private final Set<UUID> dashing = new HashSet<>();
    private UUID bloodsBaneHolder;
    private boolean syncQueued;
    private record Combo(UUID target, int count, long at) {}

    LegendaryManager(SpreeCorePlugin plugin) {
        this.plugin = plugin;
        itemId = new NamespacedKey(plugin, "relic");
        testId = new NamespacedKey(plugin, "test_copy");
        frostId = new NamespacedKey(plugin, "frost_arrow");
        volleyId = new NamespacedKey(plugin, "hailstorm_volley");
        visualId = new NamespacedKey(plugin, "visual_arrow");
    }
    private String path(LegendaryType t, String property) { return "legendary.weapons." + t.id + "." + property; }
    private int i(LegendaryType t, String property, int fallback) { return plugin.getConfig().getInt(path(t, property), fallback); }
    private double d(LegendaryType t, String property, double fallback) { return plugin.getConfig().getDouble(path(t, property), fallback); }
    private long now() { return System.currentTimeMillis(); }
    public boolean hasAquafierSpeed(Player player) { return aquafierActive.getOrDefault(player.getUniqueId(), 0L) > now(); }
    public boolean isBloodsBaneHolder(Player p) { return bloodsBaneHolder != null && bloodsBaneHolder.equals(p.getUniqueId()); }
    public UUID bloodsBaneHolder() { return bloodsBaneHolder; }

    public ItemStack create(LegendaryType type, boolean test) {
        ItemStack stack = new ItemStack(type.material);
        if (type == LegendaryType.HAILSTORM) {
            stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            stack.addUnsafeEnchantment(Enchantment.POWER, 3);
            stack.addUnsafeEnchantment(Enchantment.INFINITY, 1);
            stack.addUnsafeEnchantment(Enchantment.PIERCING, 4);
        } else {
            stack.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
            stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            if (type != LegendaryType.BLOODSBANE) stack.addUnsafeEnchantment(Enchantment.MENDING, 1);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(Text.c(type.coloredName));
        meta.setLore(buildLore(type, test));
        meta.getPersistentDataContainer().set(itemId, PersistentDataType.STRING, type.id);
        plugin.models().legendary(meta, type);
        if (test) meta.getPersistentDataContainer().set(testId, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }
    private List<String> buildLore(LegendaryType t, boolean test) {
        List<String> lore = new ArrayList<>();
        lore.add("&8&oLegendary Weapon"); lore.add(" ");
        lore.add("&b&lActive &7(Sneak + Right Click)");
        switch (t) {
            case TUNDRA -> { lore.add("&7Next hit in &b" + i(t,"arm-seconds",8) + "s &7freezes enemy for &b" + i(t,"freeze-seconds",2) + "s");
                lore.add("&7Passive: &b" + (int)(d(t,"passive-chance",.07)*100) + "% &7Slowness I for 2s"); }
            case BUTCHERER -> { lore.add("&7Sacrifice &c" + i(t,"sacrifice-hearts",5) + " hearts &7for &cStrength " + i(t,"strength-level",3) + " &7(" + i(t,"strength-seconds",10) + "s)");
                lore.add("&7Passive: &c" + (int)(d(t,"wither-chance",.05)*100) + "% &7Wither III for 2s"); }
            case STORMBREAKER -> { lore.add("&7Lightning strikes nearby players for &e" + d(t,"active-damage-hearts",3) + " hearts");
                lore.add("&7Passive: &e" + (int)(d(t,"passive-chance",.10)*100) + "% &7lightning bonus"); }
            case AQUAFIER -> { lore.add("&7Absorb water: Speed III and &b+" + (int)((d(t,"active-damage-multiplier",1.2)-1)*100) + "% &7damage for 15s");
                lore.add("&7Passive: four-hit drowning combo (&b3.5 hearts&7)"); }
            case DARKHEART -> { lore.add("&7Dash through players and deal &83.5 hearts");
                lore.add("&7Passive: every 4 hits heals &c2 hearts"); }
            case HAILSTORM -> { lore.add("&7Next hit by an arrow calls a &b10-arrow volley");
                lore.add("&7Passive: every 3rd arrow slows targets"); }
            case BLOODSBANE -> { lore.add("&7Next hit launches victim for &c4.5 hearts &7over time");
                lore.add("&7Passive: &c+4% &7damage per combo hit (max +40%)"); }
        }
        lore.add(" ");
        lore.add("&7Cooldown: &f" + i(t,"cooldown-seconds",30) + "s");
        lore.add("&8" + t.legacyLore);
        if (test) lore.add("&8Test Copy");
        return lore.stream().map(Text::c).toList();
    }
    /** New items use PDC. Old Skript items can be recognized by the exact relic lore. */
    public LegendaryType type(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(itemId, PersistentDataType.STRING);
        if (id != null) {
            LegendaryType candidate = LegendaryType.byId(id);
            return candidate != null && candidate.material == item.getType() ? candidate : null;
        }
        if (!plugin.getConfig().getBoolean("legendary.accept-legacy-lore-items", true)) return null;
        List<String> lore = meta.getLore();
        if (lore == null) return null;
        for (String line : lore) {
            String plain = ChatColor.stripColor(line);
            for (LegendaryType t : LegendaryType.values()) {
                if (t.material == item.getType() && plain != null && plain.trim().equalsIgnoreCase(t.legacyLore)) return t;
            }
        }
        return null;
    }
    public boolean testCopy(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getItemMeta().getPersistentDataContainer().has(testId, PersistentDataType.BYTE)) return true;
        List<String> lore = item.getItemMeta().getLore();
        return lore != null && lore.stream().anyMatch(s -> ChatColor.stripColor(s).contains("Test Copy"));
    }
    /** Upgrade old inventory relics in place, including existing PDC-tagged relics without models. */
    public int migrate(Player player) {
        int migrated = 0;
        ItemStack[] contents = player.getInventory().getContents();
        boolean inventoryChanged = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            LegendaryType type = type(item);
            if (type == null) continue;
            ItemMeta meta = item.getItemMeta();
            boolean wasTagged = meta.getPersistentDataContainer().has(itemId);
            if (!wasTagged) {
                meta.getPersistentDataContainer().set(itemId, PersistentDataType.STRING, type.id);
                if (testCopy(item)) meta.getPersistentDataContainer().set(testId, PersistentDataType.BYTE, (byte)1);
                migrated++;
            }
            boolean visualChanged = plugin.models().legendary(meta, type);
            if (!wasTagged || visualChanged) {
                item.setItemMeta(meta);
                contents[slot] = item;
                inventoryChanged = true;
            }
        }
        if (inventoryChanged) player.getInventory().setContents(contents);
        return migrated;
    }
    public void refreshHeld(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        LegendaryType t = type(held);
        if (t == null) return;
        ItemMeta meta = held.getItemMeta();
        meta.setDisplayName(Text.c(t.coloredName));
        meta.setLore(buildLore(t, testCopy(held)));
        plugin.models().legendary(meta, t);
        held.setItemMeta(meta);
        Text.action(player, "&aUpdated lore for " + t.display);
    }
    private boolean canUse(Player player, ItemStack item, LegendaryType type) {
        return type != LegendaryType.BLOODSBANE || testCopy(item)
                || (plugin.events().bloodsBaneEnabled() && isBloodsBaneHolder(player));
    }
    private boolean ready(Player player, LegendaryType t) {
        long left = cooldowns.getOrDefault(player.getUniqueId(), new EnumMap<>(LegendaryType.class)).getOrDefault(t, 0L) - now();
        if (left > 0) {
            Text.action(player, "&c" + t.display + " recharging: &7" + Text.time(left));
            return false;
        }
        return true;
    }
    private void cooldown(Player p, LegendaryType t) {
        double mult = plugin.events().cooldownMultiplier(p);
        int ms = Math.max(200, (int)Math.round(i(t, "cooldown-seconds", 30) * 1000 * mult));
        cooldowns.computeIfAbsent(p.getUniqueId(), u -> new EnumMap<>(LegendaryType.class)).put(t, now() + ms);
    }
    private boolean arm(Player player, LegendaryType t) {
        if (!ready(player, t)) return false;
        armed.computeIfAbsent(player.getUniqueId(), u -> new EnumMap<>(LegendaryType.class))
                .put(t, now() + 1000L * i(t, "arm-seconds", 8));
        if (t != LegendaryType.HAILSTORM) cooldown(player, t); // Hailstorm begins cooldown when shot.
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1f);
        Text.action(player, "&b&l" + t.display + " &7- next hit empowered!");
        return true;
    }
    private boolean consumeArm(Player p, LegendaryType t) {
        EnumMap<LegendaryType,Long> map = armed.get(p.getUniqueId());
        if (map == null) return false;
        Long expiry = map.remove(t);
        return expiry != null && expiry > now();
    }
    private boolean isArmed(Player p, LegendaryType t) {
        EnumMap<LegendaryType,Long> map = armed.get(p.getUniqueId());
        return map != null && map.getOrDefault(t, 0L) > now();
    }
    private void effect(LivingEntity e, PotionEffectType type, int seconds, int level) {
        e.addPotionEffect(new PotionEffect(type, seconds * 20, Math.max(0,level-1), true, true, true));
    }
    private void nonlethal(LivingEntity entity, double hearts) {
        if (!entity.isDead() && entity.isValid()) entity.setHealth(Math.max(1.0, entity.getHealth() - hearts * 2));
    }
    private boolean chance(double p) { return ThreadLocalRandom.current().nextDouble() < Math.max(0, Math.min(1, p)); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void active(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!p.isSneaking() || !plugin.getConfig().getBoolean("legendary.enabled", true)) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        LegendaryType t = type(held);
        if (t == null || !canUse(p, held, t)) return;
        if (t == LegendaryType.HAILSTORM && isArmed(p,t)) return; // second click shoots loaded crossbow.
        event.setCancelled(true);
        switch (t) {
            case TUNDRA, BLOODSBANE, HAILSTORM -> arm(p,t);
            case BUTCHERER -> activateButcherer(p,t);
            case STORMBREAKER -> activateStormbreaker(p,t);
            case AQUAFIER -> activateAquafier(p,t);
            case DARKHEART -> activateDarkheart(p,t);
        }
    }
    private void activateButcherer(Player p, LegendaryType t) {
        if (!ready(p,t)) return;
        double cost = d(t,"sacrifice-hearts",5) * 2;
        if (p.getHealth() <= cost) { Text.action(p, "&cNot enough hearts to sacrifice!"); return; }
        p.setHealth(p.getHealth() - cost);
        effect(p, PotionEffectType.STRENGTH, i(t,"strength-seconds",10), i(t,"strength-level",3));
        cooldown(p,t);
        p.playSound(p.getLocation(),Sound.ENTITY_EVOKER_PREPARE_SUMMON,1f,0.7f);
        Text.action(p, "&4&lBLOOD RAGE &cStrength III!");
    }
    private void activateStormbreaker(Player p, LegendaryType t) {
        if (!ready(p,t)) return;
        cooldown(p,t);
        p.getWorld().strikeLightningEffect(p.getLocation());
        double radius = d(t,"active-radius",6);
        double damage = d(t,"active-damage-hearts",3);
        p.playSound(p.getLocation(),Sound.ITEM_TRIDENT_THUNDER,1f,1f);
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
            if (!(e instanceof Player target) || e.equals(p) || target.getLocation().distanceSquared(p.getLocation()) > radius * radius) continue;
            target.getWorld().strikeLightningEffect(target.getLocation());
            nonlethal(target,damage);
            target.setVelocity(new Vector(0,d(t,"launch-velocity",.8),0));
        }
        Text.action(p,"&e&lSTORM BREAKER &7- lightning unleashed!");
    }
    private void activateAquafier(Player p, LegendaryType t) {
        if (!ready(p,t)) return;
        Block nearest = nearestWater(p.getLocation(), i(t,"absorb-search-radius",8));
        if (nearest == null) { Text.action(p,"&bNo nearby water to absorb."); return; }
        int removed = sponge(p, nearest, i(t,"absorb-blocks-max",65));
        if (removed == 0) { Text.action(p,"&cWater is protected here; absorption blocked."); return; }
        int seconds = i(t,"active-seconds",15);
        effect(p,PotionEffectType.SPEED,seconds,i(t,"speed-level",3));
        aquafierActive.put(p.getUniqueId(),now() + seconds * 1000L);
        cooldown(p,t);
        p.playSound(p.getLocation(),Sound.ENTITY_GENERIC_SPLASH,1f,1f);
        Text.action(p,"&b&lAQUAFIER &7- " + removed + " water blocks absorbed!");
    }
    private Block nearestWater(Location center, int radius) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy=center.getBlockY(), cz=center.getBlockZ();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        int r = Math.max(1,Math.min(12,radius));
        for (int dx=-r;dx<=r;dx++) for(int dy=-r;dy<=r;dy++) for(int dz=-r;dz<=r;dz++) {
            double dist = dx*dx+dy*dy+dz*dz;
            if (dist > r*r || dist >= bestD) continue;
            int y=cy+dy;
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
            Block block = world.getBlockAt(cx+dx,y,cz+dz);
            if (block.getType() == Material.WATER) { best = block; bestD=dist; }
        }
        return best;
    }
    private int sponge(Player player, Block origin, int max) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(origin);
        int taken=0;
        while (!queue.isEmpty() && taken < Math.max(1,Math.min(200,max))) {
            Block b=queue.remove();
            String key=b.getX()+","+b.getY()+","+b.getZ();
            if (!visited.add(key)) continue;
            if (Math.abs(b.getX()-origin.getX())>4 || Math.abs(b.getY()-origin.getY())>4 || Math.abs(b.getZ()-origin.getZ())>4) continue;
            if (b.getType()!=Material.WATER) continue;
            org.bukkit.event.block.BlockBreakEvent check=new org.bukkit.event.block.BlockBreakEvent(b,player);
            Bukkit.getPluginManager().callEvent(check);
            if (check.isCancelled()) continue; // Claim plugins can veto; test your protection setup.
            b.setType(Material.AIR, false);
            taken++;
            for (BlockFace face : List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN))
                queue.add(b.getRelative(face));
        }
        return taken;
    }
    private void activateDarkheart(Player p, LegendaryType t) {
        if (!ready(p,t) || !dashing.add(p.getUniqueId())) return;
        cooldown(p,t);
        Vector direction=p.getLocation().getDirection().setY(0).normalize().multiply(d(t,"dash-speed",.9));
        if (direction.lengthSquared()<0.01) direction = new Vector(0,0,1);
        Vector push=direction.clone().normalize().multiply(1.1).setY(.35);
        Vector dash=direction.clone().setY(.08);
        Set<UUID> hit=new HashSet<>();
        int ticks=Math.max(1,i(t,"dash-ticks",6));
        double radius=d(t,"dash-radius",1.8);
        p.playSound(p.getLocation(),Sound.ITEM_TRIDENT_RIPTIDE_1,1f,1f);
        for (int tick=0;tick<ticks;tick++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.isDead()) return;
                p.setVelocity(dash);
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
                    if (!(e instanceof Player victim) || victim.equals(p) || !hit.add(victim.getUniqueId())) continue;
                    if (victim.getLocation().distanceSquared(p.getLocation()) > radius*radius) continue;
                    nonlethal(victim,d(t,"dash-damage-hearts",3.5));
                    victim.setVelocity(push);
                    p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,1f,1f);
                    Text.action(victim,"&8DARKHEART &7- you were struck!");
                }
            },tick);
        }
        Bukkit.getScheduler().runTaskLater(plugin,()->dashing.remove(p.getUniqueId()),ticks+1L);
    }

    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true)
    public void shot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter) || !(event.getProjectile() instanceof Arrow arrow)) return;
        if (type(event.getBow()) != LegendaryType.HAILSTORM) return;
        hailstormShots.merge(shooter.getUniqueId(),1,Integer::sum);
        int count=hailstormShots.get(shooter.getUniqueId());
        if (count % Math.max(1,i(LegendaryType.HAILSTORM,"frost-every-shots",3)) == 0) {
            arrow.getPersistentDataContainer().set(frostId,PersistentDataType.BYTE,(byte)1);
            Text.action(shooter,"&3&lHAILSTORM &b- frost arrow!");
        }
        if (consumeArm(shooter,LegendaryType.HAILSTORM)) {
            arrow.getPersistentDataContainer().set(volleyId,PersistentDataType.BYTE,(byte)1);
            cooldown(shooter,LegendaryType.HAILSTORM);
            Text.action(shooter,"&3&lHAILSTORM &b- empowered arrow fired!");
        }
        if (plugin.getConfig().getBoolean("legendary.weapons.hailstorm.replenished-arrows",true)) {
            Bukkit.getScheduler().runTaskLater(plugin,()->replenish(shooter),2L);
        }
    }
    private void replenish(Player p) {
        if (p.isOnline() && type(p.getInventory().getItemInMainHand())==LegendaryType.HAILSTORM
                && !p.getInventory().contains(Material.ARROW))
            p.getInventory().addItem(new ItemStack(Material.ARROW));
    }
    @EventHandler(ignoreCancelled=true) public void held(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,()->replenish(event.getPlayer()),2L);
    }

    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && arrow.getPersistentDataContainer().has(visualId)) {
            event.setCancelled(true); return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        Player attacker = null;
        boolean projectile = event.getDamager() instanceof Projectile;
        if (event.getDamager() instanceof Player p) attacker=p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker=p;
        if (victim instanceof Player player && attacker != null && !victim.equals(attacker)) {
            baneCombos.remove(player.getUniqueId());
            aquaCombos.remove(player.getUniqueId());
        }
        if (attacker==null || attacker.equals(victim) || !plugin.getConfig().getBoolean("legendary.enabled",true)) return;
        if (projectile) {
            if (!(event.getDamager() instanceof Arrow arrow)) return;
            if (arrow.getPersistentDataContainer().has(frostId)) {
                effect(victim,PotionEffectType.SLOWNESS,i(LegendaryType.HAILSTORM,"frost-seconds",3),i(LegendaryType.HAILSTORM,"frost-level",3));
                victim.getWorld().playSound(victim.getLocation(),Sound.BLOCK_GLASS_BREAK,0.9f,1.3f);
            }
            if (arrow.getPersistentDataContainer().has(volleyId)) startVolley(attacker,victim);
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        LegendaryType t=type(weapon);
        if (t==null || !canUse(attacker,weapon,t)) return;
        switch (t) {
            case TUNDRA -> hitTundra(attacker,victim);
            case BUTCHERER -> {
                if (chance(d(t,"wither-chance",.05)))
                    effect(victim,PotionEffectType.WITHER,i(t,"wither-seconds",2),i(t,"wither-level",3));
            }
            case STORMBREAKER -> {
                if (chance(d(t,"passive-chance",.10))) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    event.setDamage(event.getDamage()+d(t,"passive-extra-damage-hearts",1.5)*2);
                }
            }
            case AQUAFIER -> {
                if (hasAquafierSpeed(attacker)) event.setDamage(event.getDamage()*d(t,"active-damage-multiplier",1.2));
                if (victim instanceof Player player) hitAquafier(attacker,player);
            }
            case DARKHEART -> hitDarkheart(attacker);
            case HAILSTORM -> {}
            case BLOODSBANE -> hitBloodsBane(attacker,victim,event);
        }
    }
    private void hitTundra(Player attacker, LivingEntity victim) {
        LegendaryType t=LegendaryType.TUNDRA;
        if (chance(d(t,"passive-chance",.07))) effect(victim,PotionEffectType.SLOWNESS,i(t,"slow-seconds",2),1);
        if (!consumeArm(attacker,t)) return;
        int duration=i(t,"freeze-seconds",2);
        effect(victim,PotionEffectType.SLOWNESS,duration,255);
        effect(victim,PotionEffectType.JUMP_BOOST,duration,250);
        effect(victim,PotionEffectType.RESISTANCE,duration,1);
        if (victim instanceof Player p) frozen.put(p.getUniqueId(),now()+duration*1000L);
        victim.getWorld().playSound(victim.getLocation(),Sound.BLOCK_GLASS_BREAK,1,0.7f);
        Text.action(attacker,"&b&lFROZEN &7- target locked!");
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void frozenMove(PlayerMoveEvent event) {
        Long expires=frozen.get(event.getPlayer().getUniqueId());
        if (expires==null) return;
        if (expires<=now()) { frozen.remove(event.getPlayer().getUniqueId()); return; }
        event.setTo(event.getFrom()); // Locks both movement and head turning.
    }
    private void hitDarkheart(Player attacker) {
        LegendaryType t=LegendaryType.DARKHEART;
        int count=darkheartHits.merge(attacker.getUniqueId(),1,Integer::sum);
        if (count<Math.max(1,i(t,"lifedrain-hits",4))) return;
        darkheartHits.put(attacker.getUniqueId(),0);
        double max=attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(max,attacker.getHealth()+d(t,"lifedrain-hearts",2)*2));
        attacker.playSound(attacker.getLocation(),Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,.8f,1f);
        Text.action(attacker,"&8DARKHEART &c- lifedrain!");
    }
    private void hitAquafier(Player attacker, Player target) {
        LegendaryType t=LegendaryType.AQUAFIER;
        long timestamp=now();
        Combo old=aquaCombos.get(attacker.getUniqueId());
        int hits=old!=null && old.target().equals(target.getUniqueId())
                && timestamp-old.at()<i(t,"combo-window-seconds",2)*1000L ? old.count()+1 : 1;
        int required=Math.max(1,i(t,"combo-hit-count",4));
        if (hits>=required) {
            aquaCombos.remove(attacker.getUniqueId());
            target.setRemainingAir(0);
            nonlethal(target,d(t,"drowning-damage-hearts",3.5));
            target.playSound(target.getLocation(),Sound.ENTITY_PLAYER_HURT_DROWN,1,1);
            Text.action(attacker,"&b&lDROWNED &7- four-hit combo!");
            Text.action(target,"&b&lDROWNING &7- air depleted!");
        } else {
            aquaCombos.put(attacker.getUniqueId(),new Combo(target.getUniqueId(),hits,timestamp));
            target.setRemainingAir(hits==1?225:hits==2?150:75);
            Text.action(attacker,"&b&lAQUAFIER COMBO &7"+hits+"/"+required);
        }
    }
    private void hitBloodsBane(Player attacker, LivingEntity target,EntityDamageByEntityEvent event) {
        LegendaryType t=LegendaryType.BLOODSBANE;
        if (target instanceof Player) {
            long timestamp=now();
            Combo old=baneCombos.get(attacker.getUniqueId());
            int hits=(old!=null && timestamp-old.at()<i(t,"combo-reset-seconds",7)*1000L)?old.count():0;
            double increase=d(t,"combo-per-hit",.04)*Math.min(hits,i(t,"combo-max-stacks",10));
            event.setDamage(event.getDamage()*(1+increase));
            baneCombos.put(attacker.getUniqueId(),new Combo(target.getUniqueId(),Math.min(hits+1,i(t,"combo-max-stacks",10)),timestamp));
            Text.action(attacker,"&4&lCOMBO &7x"+Math.min(hits+1,i(t,"combo-max-stacks",10)));
        }
        if (!consumeArm(attacker,t)) return;
        effect(target,PotionEffectType.LEVITATION,1,5);
        target.getWorld().playSound(target.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1,1);
        int hits=Math.max(1,i(t,"launch-hits",6));
        int interval=Math.max(1,i(t,"launch-ticks-between-hits",10));
        for (int j=1;j<=hits;j++) {
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                if (!target.isDead() && target.isValid()) {
                    nonlethal(target,d(t,"launch-damage-per-hit-hearts",.75));
                    target.getWorld().playSound(target.getLocation(),Sound.ENTITY_PLAYER_HURT,.7f,1f);
                }
            },(long)j*interval);
        }
        Text.action(attacker,"&4&lBLOOD'S BANE &7- target launched!");
    }
    private void startVolley(Player shooter,LivingEntity victim) {
        LegendaryType t=LegendaryType.HAILSTORM;
        int arrows=Math.max(1,Math.min(32,i(t,"volley-arrows",10)));
        int interval=Math.max(1,i(t,"volley-interval-ticks",10));
        Text.action(shooter,"&3&lHAILSTORM &b- incoming volley!");
        for(int n=0;n<arrows;n++) {
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                if (victim.isDead() || !victim.isValid()) return;
                Location top=victim.getLocation().add(0,4,0);
                if (!top.getBlock().isEmpty()) top=victim.getLocation().add(0,2,0);
                Arrow visual=victim.getWorld().spawnArrow(top,new Vector(0,-1,0),1.6f,0);
                visual.setDamage(0);
                visual.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                visual.getPersistentDataContainer().set(visualId,PersistentDataType.BYTE,(byte)1);
                visual.setShooter(shooter);
                Bukkit.getScheduler().runTaskLater(plugin,()->{ if (visual.isValid()) visual.remove(); },5L);
                nonlethal(victim,d(t,"volley-damage-hearts",.5));
                victim.getWorld().playSound(victim.getLocation(),Sound.ENTITY_ARROW_HIT_PLAYER,.6f,1f);
            },(long)n*interval);
        }
    }
    public void tick() {
        long timestamp=now();
        aquafierActive.entrySet().removeIf(e->e.getValue()<=timestamp);
        frozen.entrySet().removeIf(e->e.getValue()<=timestamp);
        // Only examine the held item, and only once per second, for Hailstorm ammo.
        if (plugin.getConfig().getBoolean("legendary.weapons.hailstorm.replenished-arrows",true))
            for (Player player:Bukkit.getOnlinePlayers()) replenish(player);
    }
    @EventHandler public void death(PlayerDeathEvent event) {
        UUID id=event.getEntity().getUniqueId();
        frozen.remove(id);dashing.remove(id);darkheartHits.remove(id);hailstormShots.remove(id);
        aquaCombos.remove(id);baneCombos.remove(id);aquafierActive.remove(id);armed.remove(id);
        Set<LegendaryType> had=new LinkedHashSet<>();
        for (ItemStack item : event.getDrops()) {
            LegendaryType type=type(item);
            if (type!=null) had.add(type);
        }
        // Bound Bane must never drop, even when death announcements are disabled.
        event.getDrops().removeIf(item -> type(item)==LegendaryType.BLOODSBANE && !testCopy(item));
        if (plugin.getConfig().getBoolean("legendary.forging.announce-death-of-holder",true)) {
            for (LegendaryType type:had) Bukkit.broadcastMessage(Text.c("&8&lRELIC &7"+event.getEntity().getName()+" has fallen while carrying &f"+type.display+"&7!"));
        }
    }
    @EventHandler(ignoreCancelled=true) public void drop(PlayerDropItemEvent event) {
        ItemStack stack=event.getItemDrop().getItemStack();
        if (type(stack)==LegendaryType.BLOODSBANE && !testCopy(stack)) {
            event.setCancelled(true);
            Text.action(event.getPlayer(),"&cBlood's Bane is bound to the #1 player.");
        }
    }
    @EventHandler(ignoreCancelled=true) public void pickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack=event.getItem().getItemStack();
        if (type(stack)==LegendaryType.BLOODSBANE && !testCopy(stack) && !isBloodsBaneHolder(player)) event.setCancelled(true);
    }
    private boolean blockedStorage(ItemStack item) {
        LegendaryType t=type(item);
        return t!=null && ((t.forgeable() && plugin.getConfig().getBoolean("legendary.prevent-storage",true))
                || (t==LegendaryType.BLOODSBANE && !testCopy(item)));
    }
    @EventHandler(ignoreCancelled=true) public void containerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int topSize=event.getView().getTopInventory().getSize();
        // Player inventory is the bottom inventory; guard all transfers to the top.
        if (event.getRawSlot()<topSize && event.getRawSlot()>=0 && blockedStorage(event.getCursor())) event.setCancelled(true);
        if (event.isShiftClick() && event.getRawSlot()>=topSize && blockedStorage(event.getCurrentItem())) event.setCancelled(true);
        if (event.getRawSlot()<topSize && event.getHotbarButton()>=0 && blockedStorage(p.getInventory().getItem(event.getHotbarButton()))) event.setCancelled(true);
        if (event.getRawSlot()<topSize && event.getClick()==ClickType.SWAP_OFFHAND && blockedStorage(p.getInventory().getItemInOffHand())) event.setCancelled(true);
        if (event.isCancelled()) Text.action(p,"&cThis legendary weapon cannot be stored.");
    }
    @EventHandler(ignoreCancelled=true) public void containerDrag(InventoryDragEvent event) {
        if (!blockedStorage(event.getOldCursor())) return;
        int topSize=event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot->slot<topSize)) event.setCancelled(true);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,this::syncBloodsBane,3L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        UUID id=event.getPlayer().getUniqueId();
        armed.remove(id);
        darkheartHits.remove(id);
        hailstormShots.remove(id);
        baneCombos.remove(id);
        aquaCombos.remove(id);
        aquafierActive.remove(id);
        frozen.remove(id);
        dashing.remove(id);
        Bukkit.getScheduler().runTask(plugin,this::syncBloodsBane);
    }
    /** Collapse multiple kill/death stat updates into one inventory sync on the next tick. */
    public void queueBloodsBaneSync() {
        if (syncQueued) return;
        syncQueued=true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncQueued=false;
            syncBloodsBane();
        });
    }
    public void syncBloodsBane() {
        if (plugin.events()==null) return;
        UUID chosen=null;
        int best=plugin.getConfig().getInt("legendary.weapons.bloodsbane.minimum-leader-streak",15)-1;
        if (plugin.events().bloodsBaneEnabled()) {
            for(Player p:Bukkit.getOnlinePlayers()) {
                int score=plugin.streaks().streak(p);
                if (score>best || score==best && p.getUniqueId().equals(bloodsBaneHolder)) {
                    best=score;chosen=p.getUniqueId();
                }
            }
        }
        if (!Objects.equals(chosen,bloodsBaneHolder)) {
            bloodsBaneHolder=chosen;
            if (chosen!=null) {
                Player p=Bukkit.getPlayer(chosen);
                if (p!=null) Bukkit.broadcastMessage(Text.c("&4&lBLOOD'S BANE &f"+p.getName()+" &7claims the #1 streak!"));
            }
        }
        for(Player p:Bukkit.getOnlinePlayers()) {
            boolean rightful=p.getUniqueId().equals(chosen);
            boolean has=false;
            ItemStack[] items=p.getInventory().getContents();
            boolean changed=false;
            for(int index=0;index<items.length;index++) {
                if (type(items[index])!=LegendaryType.BLOODSBANE || testCopy(items[index])) continue;
                if (!rightful || has) { items[index]=null;changed=true; }
                else has=true;
            }
            if (changed) p.getInventory().setContents(items);
            if (rightful && !has) {
                HashMap<Integer,ItemStack> excess=p.getInventory().addItem(create(LegendaryType.BLOODSBANE,false));
                if (!excess.isEmpty()) Text.action(p,"&cMake inventory room to claim Blood's Bane.");
            }
        }
    }
}
