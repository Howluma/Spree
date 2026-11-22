package dev.howluma.spreecore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * At most one Spree death transaction per actual player life.
 *
 * TNT minecart blast chains and other plugins can cause multiple damage or
 * occasionally duplicate death callbacks. Never apply a second reduction from
 * the post-death score. Unlock ONLY on a real respawn (or login already alive),
 * so this is not a crude time-based cooldown that blocks legitimate rapid kills.
 *
 * This guard is main-thread owned, like Bukkit PlayerDeathEvent.
 */
final class DeathLifeGuard {
    record Snapshot(int streakBeforeDeath, int tick, long timestampMillis) {}
    private static final class Entry {
        final Snapshot snapshot;
        boolean warned;
        Entry(Snapshot snapshot) { this.snapshot=snapshot; }
    }
    private final Map<UUID,Entry> processed=new HashMap<>();

    /**
     * Captures the pre-death score once and returns null for another callback
     * before that player has actually respawned.
     */
    Snapshot begin(UUID playerId, int currentStreak, int tick, long nowMillis) {
        if(processed.containsKey(playerId))return null;
        Snapshot snapshot=new Snapshot(currentStreak,tick,nowMillis);
        processed.put(playerId,new Entry(snapshot));
        return snapshot;
    }

    Snapshot snapshot(UUID playerId) {
        Entry entry=processed.get(playerId);
        return entry==null?null:entry.snapshot;
    }

    /** Only emit a diagnostic once per offending player life. */
    boolean firstDuplicate(UUID playerId) {
        Entry entry=processed.get(playerId);
        if(entry==null || entry.warned)return false;
        entry.warned=true;
        return true;
    }

    /** A player may now die again as part of a distinct life. */
    void respawn(UUID playerId) { processed.remove(playerId); }

    /** Never clear a still-dead user when they log out or reconnect. */
    void join(UUID playerId, boolean dead) {
        if(!dead)respawn(playerId);
    }

    void shutdown() { processed.clear(); }
}
