package dev.howluma.spreecore;

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
            if (choice.getKey() <= p.streak && choice.getValue().equals(id)) return true;
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
    public boolean milestoneUnlocked(Player p, ProgressionCatalog.Kind kind, int level) {
        return ProgressionCatalog.milestone(kind, level) && level <= profile(p).streak;
    }
    public int available(Player p, ProgressionCatalog.Kind kind) {
        int count = 0;
        for (int level : ProgressionCatalog.milestones(kind, plugin.streaks().cap()))
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
            for (int level = Math.max(1, previous + 1); level <= current; level++)
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
        if (kind == ProgressionCatalog.Kind.PERK) return List.of();
        PlayerDataStore.Profile p = profile(player);
        Set<String> taken = pickedExcept(p, kind, level);
        String own = chosen(p, kind).get(level);
        return ProgressionCatalog.options(kind).stream()
                .filter(o -> !taken.contains(o.id()) && (!replacing || !o.id().equals(own)))
                .toList();
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
        // If another pending milestone was chosen, remove duplicate locked-out offers.
        Set<String> chosen = pickedExcept(p, ProgressionCatalog.Kind.TALENT, level);
        if (old == null || old.stream().anyMatch(chosen::contains)) {
            List<String> offers = buildTalentOffers(p, level, null);
            p.talentOffers.put(level, offers);
            plugin.data().dirty();
            return offers;
        }
        return old;
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
        if (pickedExcept(p, kind, level).contains(optionId) || (sigil && optionId.equals(existing)))
            return new Result(false, "Choose a different available upgrade.");
        if (kind == ProgressionCatalog.Kind.TALENT && (!talentOffers(player, level).contains(optionId) || sigil))
            return new Result(false, "Choose one of the three saved Talent offers.");
        if (sigil && !plugin.fate().consume(player)) return new Result(false, "Fate Sigil missing.");
        choices.put(level, optionId);
        if (kind == ProgressionCatalog.Kind.TALENT) p.talentOffers.remove(level);
        plugin.data().dirty();
        plugin.streaks().update(player);
        if (kind == ProgressionCatalog.Kind.PRIVILEGE) plugin.privilegeRules().enforce(player);
        return new Result(true, (sigil ? "Reselected " : "Unlocked ") + option.name() + "!");
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
