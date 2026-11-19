package dev.howluma.spreecore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Claim-driven upgrades with saved milestones, offers, and Sigil rerolls. */
public final class ProgressionManager {
    public record Result(boolean ok, String message) {}
    private final SpreeCorePlugin plugin;
    ProgressionManager(SpreeCorePlugin plugin) { this.plugin = plugin; }

    private PlayerDataStore.Profile profile(Player player) { return plugin.data().get(player); }
    private Map<Integer,String> chosen(PlayerDataStore.Profile p, ProgressionCatalog.Kind kind) {
        return switch (kind) {
            case PRIVILEGE -> p.privilegeChoices;
            case INFUSION -> p.infusionChoices;
            case TALENT -> p.talentChoices;
            case PERK -> p.perkRolls;
        };
    }
    public String choice(Player player, ProgressionCatalog.Kind kind, int milestone) {
        return chosen(profile(player), kind).get(milestone);
    }
    public boolean active(Player player, ProgressionCatalog.Kind kind, String id) {
        PlayerDataStore.Profile p = profile(player);
        if (p.streak <= 0) return false;
        for (var choice : chosen(p, kind).entrySet())
            if (choice.getKey() <= SpreeMath.benefitLevel(p.streak) && choice.getValue().equals(id)) return true;
        return false;
    }
    public boolean hasPrivilege(Player p, String id) {
        return active(p, ProgressionCatalog.Kind.PRIVILEGE, id);
    }
    public boolean hasTalent(Player p, String id) {
        return active(p, ProgressionCatalog.Kind.TALENT, id);
    }
    public boolean hasInfusion(Player p, String id) {
        return active(p, ProgressionCatalog.Kind.INFUSION, id);
    }
    /**
     * Earning an upgrade milestone is permanent; actual gameplay benefits still
     * depend solely on the player's CURRENT killstreak.
     */
    static boolean historicallyEarned(ProgressionCatalog.Kind kind, int level, int streak, int best) {
        return kind != null && level <= SpreeMath.BENEFIT_CAP
                && ProgressionCatalog.milestone(kind, level) && Math.max(streak, best) >= level;
    }
    public boolean milestoneUnlocked(Player player, ProgressionCatalog.Kind kind, int level) {
        PlayerDataStore.Profile p = profile(player);
        return historicallyEarned(kind, level, p.streak, p.best)
                || (level <= SpreeMath.BENEFIT_CAP && ProgressionCatalog.milestone(kind, level)
                    && chosen(p, kind).containsKey(level));
    }
    public int available(Player p, ProgressionCatalog.Kind kind) {
        int count = 0;
        for (int level : ProgressionCatalog.milestones(kind, plugin.streaks().progressionCap()))
            if (milestoneUnlocked(p, kind, level) &&
                    !chosen(profile(p), kind).containsKey(level)) count++;
        return count;
    }
    public void notifyMilestones(Player player, int previous, int current) {
        if (current <= previous) return;
        List<String> messages = new ArrayList<>();
        for (ProgressionCatalog.Kind kind : List.of(ProgressionCatalog.Kind.PRIVILEGE,
                ProgressionCatalog.Kind.TALENT, ProgressionCatalog.Kind.INFUSION)) {
            int count = 0;
            for (int level = Math.max(1, previous + 1); level <= Math.min(SpreeMath.BENEFIT_CAP, current); level++)
                if (ProgressionCatalog.milestone(kind, level)
                        && !chosen(profile(player), kind).containsKey(level)) count++;
            if (count > 0) messages.add("&b" + count + " " + kind.title);
        }
        if (!messages.isEmpty()) {
            Text.send(player, plugin.prefix() + "&aUpgrade ready: " + String.join("&7, ", messages)
                    + " &7- open &9/spree &7> &bProgression &7to choose.");
        }
    }

    private Set<String> pickedExcept(PlayerDataStore.Profile p, ProgressionCatalog.Kind kind, int excludedLevel) {
        Set<String> used = new HashSet<>();
        for (var e : chosen(p, kind).entrySet())
            if (e.getKey() != excludedLevel) used.add(e.getValue());
        return used;
    }
    public List<ProgressionCatalog.Option> availableOptions(Player player,
            ProgressionCatalog.Kind kind, int level, boolean replacing) {
        return optionsFor(kind, chosen(profile(player), kind), level, replacing);
    }
    /** Pure option filtering shared by the GUI and regression tests. */
    static List<ProgressionCatalog.Option> optionsFor(ProgressionCatalog.Kind kind,
            Map<Integer,String> assigned, int level, boolean replacing) {
        if (kind == ProgressionCatalog.Kind.PERK) return List.of();
        Set<String> taken = new HashSet<>();
        for (var e : assigned.entrySet())
            if (e.getKey() != level) taken.add(e.getValue());
        String own = assigned.get(level);
        // With all six Privileges/Infusions obtained, the player must still be
        // allowed to reconfigure their order. Choosing a used option swaps slots.
        return ProgressionCatalog.options(kind).stream()
                .filter(o -> replacing && (kind == ProgressionCatalog.Kind.PRIVILEGE
                        || kind == ProgressionCatalog.Kind.INFUSION)
                        ? !o.id().equals(own)
                        : !taken.contains(o.id()) && (!replacing || !o.id().equals(own)))
                .toList();
    }

    /** Pure planning step: no Sigils are spent or choices changed on rejection. */
    static record Reselection(String displacedId, int swapLevel) {}
    static Reselection planReselection(Map<Integer,String> choices, int level, String replacement) {
        String current = choices.get(level);
        if (current == null || replacement == null || current.equals(replacement)) return null;
        int found = -1;
        for (var entry : choices.entrySet()) {
            if (entry.getKey() != level && replacement.equals(entry.getValue())) {
                found = entry.getKey();
                break;
            }
        }
        return new Reselection(current, found);
    }
    static void applyReselection(Map<Integer,String> choices, int level,
            String replacement, Reselection plan) {
        // Assignment is atomic on the main thread, and neither reward is lost.
        if (plan.swapLevel() >= 0) choices.put(plan.swapLevel(), plan.displacedId());
        choices.put(level, replacement);
    }
    public int assignedMilestone(Player player, ProgressionCatalog.Kind kind, String id) {
        for (var entry : chosen(profile(player),kind).entrySet())
            if (id.equals(entry.getValue())) return entry.getKey();
        return -1;
    }

    private List<String> buildTalentOffers(PlayerDataStore.Profile p, int level, String old) {
        Set<String> excluded = pickedExcept(p, ProgressionCatalog.Kind.TALENT, level);
        if (old != null) excluded.add(old);
        List<String> pool = new ArrayList<>();
        for (var option : ProgressionCatalog.TALENTS)
            if (!excluded.contains(option.id())) pool.add(option.id());
        Collections.shuffle(pool, ThreadLocalRandom.current());
        return List.copyOf(pool.subList(0, Math.min(3, pool.size())));
    }

    /** Persistent offers: closing a GUI cannot produce a fresh free roll. */
    public List<String> talentOffers(Player player, int level) {
        PlayerDataStore.Profile p = profile(player);
        if (!milestoneUnlocked(player, ProgressionCatalog.Kind.TALENT, level)
                || p.talentChoices.containsKey(level)) return List.of();
        List<String> old = p.talentOffers.get(level);
        // Never reroll a saved offer merely because another milestone was
        // claimed. In particular, a paid Fate Sigil reroll must not be repeatable
        // for free by changing other Talent selections while this menu is closed.
        if (old == null) {
            List<String> offers = buildTalentOffers(p, level, null);
            p.talentOffers.put(level, offers);
            plugin.data().dirty();
            return offers;
        }
        // If another Talent was claimed in the meantime, its duplicate offer
        // becomes unavailable instead of being replaced by a free new offer.
        Set<String> chosen = pickedExcept(p, ProgressionCatalog.Kind.TALENT, level);
        return old.stream().filter(id -> !chosen.contains(id)).toList();
    }

    public Result claim(Player player, ProgressionCatalog.Kind kind, int level, String optionId, boolean sigil) {
        if (kind == ProgressionCatalog.Kind.PERK || !milestoneUnlocked(player, kind, level))
            return new Result(false, "This milestone is not available.");
        PlayerDataStore.Profile p = profile(player);
        Map<Integer,String> choices = chosen(p, kind);
        String existing = choices.get(level);
        if (!sigil && existing != null) return new Result(false, "Already chosen. Use a Fate Sigil to reselect.");
        if (sigil && (existing == null || !plugin.fate().has(player)))
            return new Result(false, "You need a Fate Sigil and an existing unlocked choice.");
        var option = ProgressionCatalog.find(kind, optionId);
        if (option == null) return new Result(false, "Unknown choice.");
        Reselection change = sigil && (kind == ProgressionCatalog.Kind.PRIVILEGE
                || kind == ProgressionCatalog.Kind.INFUSION)
                ? planReselection(choices, level, optionId) : null;
        if (change == null && (pickedExcept(p, kind, level).contains(optionId)
                || (sigil && optionId.equals(existing))))
            return new Result(false, "Choose a different available upgrade.");
        if (kind == ProgressionCatalog.Kind.TALENT && (!talentOffers(player, level).contains(optionId) || sigil))
            return new Result(false, "Choose one of the three saved Talent offers.");
        // Validate everything before consuming; a failed or unchanged choice costs nothing.
        if (sigil && !plugin.fate().consume(player)) return new Result(false, "Fate Sigil missing.");
        if (change != null) applyReselection(choices, level, optionId, change);
        else choices.put(level, optionId);
        if (kind == ProgressionCatalog.Kind.TALENT) p.talentOffers.remove(level);
        plugin.data().dirty();
        plugin.streaks().update(player);
        if (kind == ProgressionCatalog.Kind.TALENT) plugin.talentFeedback().unlocked(player,optionId);
        if (kind == ProgressionCatalog.Kind.PRIVILEGE) plugin.privilegeRules().enforce(player);
        if (change != null && change.swapLevel() >= 0) {
            ProgressionCatalog.Option swapped = ProgressionCatalog.find(kind, change.displacedId());
            return new Result(true, "Fate Sigil used: " + option.name() + " is now at level " + level
                    + ", and " + (swapped == null ? "your previous choice" : swapped.name())
                    + " moved to level " + change.swapLevel() + "!");
        }
        return new Result(true, (sigil ? "Reselected " : "Unlocked ") + option.name() + "!");
    }


    /** Admin-only GUI editing, bypassing normal randomized offers but retaining milestone rules. */
    public static boolean validAdminChoice(ProgressionCatalog.Kind kind, int level, int maximum, String optionId) {
        if (kind == null || level > maximum || !ProgressionCatalog.milestone(kind, level)) return false;
        return switch (kind) {
            case TALENT -> ProgressionCatalog.find(kind, optionId) != null;
            case PERK -> PerkType.fromId(optionId) != null;
            default -> false;
        };
    }

    public Result adminSet(UUID target, ProgressionCatalog.Kind kind, int level, String optionId) {
        if (!validAdminChoice(kind, level, plugin.streaks().progressionCap(), optionId))
            return new Result(false, "Invalid Talent/Perk or milestone.");
        PlayerDataStore.Profile profile = plugin.data().get(target);
        String chosenName;
        if (kind == ProgressionCatalog.Kind.TALENT) {
            for (var entry : profile.talentChoices.entrySet()) {
                if (entry.getKey() != level && optionId.equals(entry.getValue()))
                    return new Result(false, "That Talent is already assigned at level " + entry.getKey() + ".");
            }
            var talent = ProgressionCatalog.find(kind, optionId);
            chosenName = talent.name();
            profile.talentChoices.put(level, optionId);
            profile.talentOffers.remove(level); // Invalidate an obsolete pre-assignment offer.
        } else {
            PerkType perk = PerkType.fromId(optionId);
            if (!plugin.getConfig().getBoolean("perks.enabled", true)
                    || !plugin.getConfig().getBoolean("perks." + perk.id + ".enabled", true))
                return new Result(false, "This Perk is disabled in plugin settings.");
            int maxCopies = Math.max(0, Math.min(8, plugin.getConfig().getInt(
                    "perks." + perk.id + ".max-copies", perk.defaultMaxCopies)));
            String old = profile.perkRolls.get(level);
            if (!perk.id.equals(old)
                    && PerkMath.copies(profile.perkRolls, perk, Integer.MAX_VALUE) >= maxCopies)
                return new Result(false, "That Perk has reached its maximum " + maxCopies + " copies.");
            chosenName = perk.display;
            profile.perkRolls.put(level, perk.id);
        }
        plugin.data().dirty();
        refreshAdminTarget(target);
        if(kind==ProgressionCatalog.Kind.TALENT && level<=profile.streak) {
            Player online=Bukkit.getPlayer(target);
            if(online!=null&&!online.isDead())plugin.talentFeedback().unlocked(online,optionId);
        }
        plugin.data().save();
        boolean active = level <= profile.streak;
        return new Result(true, "Set " + chosenName + " at level " + level
                + (active ? " (active now)." : " (saved; active when that streak is reached)."));
    }

    public Result adminClearTalent(UUID target, int level) {
        if (!ProgressionCatalog.milestone(ProgressionCatalog.Kind.TALENT, level)
                || level > plugin.streaks().progressionCap())
            return new Result(false, "Invalid Talent milestone.");
        PlayerDataStore.Profile profile = plugin.data().get(target);
        if (profile.talentChoices.remove(level) == null)
            return new Result(false, "No Talent assigned at that milestone.");
        profile.talentOffers.remove(level);
        plugin.data().dirty();
        refreshAdminTarget(target);
        plugin.data().save();
        return new Result(true, "Cleared Talent at level " + level + ".");
    }

    public Result adminAutoRollPerk(UUID target, int level) {
        if (!ProgressionCatalog.milestone(ProgressionCatalog.Kind.PERK, level)
                || level > plugin.streaks().progressionCap())
            return new Result(false, "Invalid Perk milestone.");
        PlayerDataStore.Profile profile = plugin.data().get(target);
        String original = profile.perkRolls.get(level);
        if (original == null) return new Result(false, "No Perk assigned at that level.");
        if (Math.max(profile.streak, profile.best) < level)
            return new Result(false, "Automatic rolls require a previously reached milestone.");
        profile.perkRolls.remove(level);
        plugin.perks().ensureRolls(profile);
        String chosen = profile.perkRolls.get(level);
        if (chosen == null) {
            profile.perkRolls.put(level, original);
            return new Result(false, "No eligible Perk is available for an automatic roll.");
        }
        plugin.data().dirty();
        refreshAdminTarget(target);
        plugin.data().save();
        return new Result(true, "Rolled " + PerkType.fromId(chosen).display + " for level " + level
                + (chosen.equals(original) ? " (same result; try selecting one manually)." : "."));
    }

    private void refreshAdminTarget(UUID target) {
        Player online = Bukkit.getPlayer(target);
        if (online != null && !online.isDead()) plugin.streaks().update(online);
    }

    /** Spend exactly one Sigil and offer new Talent choices for an unlocked Talent. */
    public Result rerollTalent(Player player, int level) {
        if (!milestoneUnlocked(player, ProgressionCatalog.Kind.TALENT, level))
            return new Result(false, "Talent milestone is locked.");
        PlayerDataStore.Profile p = profile(player);
        String original = p.talentChoices.get(level);
        if (original == null) return new Result(false, "Choose the original Talent before rerolling.");
        List<String> newOffers = buildTalentOffers(p, level, original);
        if (newOffers.size() < 3) return new Result(false, "No three valid alternative Talents available.");
        if (!plugin.fate().consume(player)) return new Result(false, "You need one Fate Sigil.");
        p.talentChoices.remove(level);
        p.talentOffers.put(level, newOffers);
        plugin.data().dirty();
        plugin.streaks().update(player);
        return new Result(true, "Fate Sigil spent. Select one of your three new Talent offers.");
    }

    /** Rebuild all historical Perk rolls once; do not reroll on death or withdrawal. */
    public Result rerollPerks(Player player) {
        if (!plugin.fate().has(player)) return new Result(false, "You need one Fate Sigil.");
        PlayerDataStore.Profile p = profile(player);
        if (p.perkRolls.isEmpty()) return new Result(false, "You haven't unlocked any Perks.");
        if (!plugin.fate().consume(player)) return new Result(false, "Fate Sigil missing.");
        p.perkRolls.clear();
        plugin.perks().ensureRolls(p);
        plugin.data().dirty();
        plugin.streaks().update(player);
        return new Result(true, "All earned Perk rolls have been rerolled!");
    }
}
