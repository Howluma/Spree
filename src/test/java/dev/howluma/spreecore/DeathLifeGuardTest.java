package dev.howluma.spreecore;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Reproduces the 20 -> 10 -> 5 double-halving regression in a single life. */
class DeathLifeGuardTest {
    @Test void tntMinecartOrExplosionChainCallbacksCannotHalveTwice() {
        var guard=new DeathLifeGuard();
        UUID victim=UUID.randomUUID();
        var first=guard.begin(victim,20,9001,1_800_000_000_000L);
        assertNotNull(first);
        assertEquals(20,first.streakBeforeDeath());
        int afterOne=SpreeMath.deathStreak(first.streakBeforeDeath());
        assertEquals(10,afterOne);

        // A second death callback in the same game tick or a later tick
        // cannot consume the already-reduced score.
        assertNull(guard.begin(victim,afterOne,9001,1_800_000_000_001L));
        assertNull(guard.begin(victim,afterOne,9002,1_800_000_000_051L));
        assertEquals(20,guard.snapshot(victim).streakBeforeDeath());
        assertTrue(guard.firstDuplicate(victim));  // one log per incident
        assertFalse(guard.firstDuplicate(victim));
    }

    @Test void differentRealLifeAfterRespawnIsAllowedToDie() {
        var guard=new DeathLifeGuard();
        UUID victim=UUID.randomUUID();
        assertNotNull(guard.begin(victim,20,1,1000));
        guard.respawn(victim);
        var second=guard.begin(victim,10,2,1100);
        assertNotNull(second);
        assertEquals(10,second.streakBeforeDeath());
        assertEquals(5,SpreeMath.deathStreak(second.streakBeforeDeath()));
    }

    @Test void logoffBeforeRespawnDoesNotRearmDeathPenalty() {
        var guard=new DeathLifeGuard();
        UUID victim=UUID.randomUUID();
        guard.begin(victim,30,200,2000);
        guard.join(victim,true); // reconnect still viewing death screen
        assertNull(guard.begin(victim,15,201,2100));
        guard.respawn(victim);
        assertEquals(15,guard.begin(victim,15,202,2200).streakBeforeDeath());
    }

    @Test void reconnectWhenAliveRepairsStaleInMemoryDeathLatch() {
        var guard=new DeathLifeGuard();
        UUID victim=UUID.randomUUID();
        guard.begin(victim,14,4,2000);
        guard.join(victim,false);
        assertEquals(7,guard.begin(victim,7,5,3000).streakBeforeDeath());
    }

    @Test void duplicateFrozenDeathDoesNotCountTwiceOrGenerateTokens() {
        // The guard runs before the frozen branch, so both normal deaths
        // and temporary Spree Freeze deaths are once per life.
        var guard=new DeathLifeGuard();
        UUID victim=UUID.randomUUID();
        assertNotNull(guard.begin(victim,0,10,100));
        assertNull(guard.begin(victim,0,11,151));
        assertTrue(guard.firstDuplicate(victim));
        guard.respawn(victim);
        assertNotNull(guard.begin(victim,0,12,200));
    }

    @Test void clearsAllBookkeepingOnShutdown() {
        var guard=new DeathLifeGuard();
        UUID victim=UUID.randomUUID();
        guard.begin(victim,32,1,100);
        guard.shutdown();
        assertNull(guard.snapshot(victim));
        assertFalse(guard.firstDuplicate(victim));
        assertNotNull(guard.begin(victim,32,2,150));
    }
}
