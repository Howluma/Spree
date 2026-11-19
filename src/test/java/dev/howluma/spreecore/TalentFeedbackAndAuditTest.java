package dev.howluma.spreecore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused regression guards for every Talent and the cooldown UX.
 * Event-specific gameplay still requires a live Paper test server.
 */
class TalentFeedbackAndAuditTest {
    @Test void allDesignedTalentsExistWithoutDuplicateIds() {
        assertEquals(22,ProgressionCatalog.TALENTS.size());
        Set<String> unique=new HashSet<>();
        for(var talent:ProgressionCatalog.TALENTS) {
            assertTrue(unique.add(talent.id()),"Duplicate Talent: "+talent.id());
            assertFalse(talent.description().isBlank(),talent.id());
        }
        assertTrue(unique.containsAll(Set.of("mace_mastery","executioner","combo_breaker",
                "concussive_blow","sunder","riposte","second_wind","iron_will","bloodlust",
                "field_medic","golden_aegis","windrunner","escape_artist","acrobat",
                "trailblazer","expanded_reserves","battle_sense","scavenger",
                "quick_hands","streak_insurance","blacksmith","industrious")));
    }

    @Test void allTimedTalentsHaveExactlyOneCorrectTimerName() {
        Map<String,String> expected=Map.ofEntries(
                Map.entry("mace_mastery","mace_smash"),
                Map.entry("concussive_blow","concussive"),
                Map.entry("sunder","sunder"),
                Map.entry("riposte","riposte"),
                Map.entry("second_wind","second_wind"),
                Map.entry("iron_will","iron_will"),
                Map.entry("bloodlust","bloodlust_heal"),
                Map.entry("field_medic","field_medic"),
                Map.entry("golden_aegis","golden_aegis"),
                Map.entry("escape_artist","escape_artist"),
                Map.entry("scavenger","scavenger"),
                Map.entry("streak_insurance","streak_insurance")
        );
        for(var talent:ProgressionCatalog.TALENTS)
            assertEquals(expected.get(talent.id()),TalentFeedback.cooldownKey(talent.id()),
                    "Missing or wrong timer: "+talent.id());
    }

    @Test void countdownRoundsUpAndNeverGoesNegative() {
        long now=1_000_000L;
        assertEquals(0,TalentFeedback.secondsLeft(now-1,now));
        assertEquals(0,TalentFeedback.secondsLeft(now,now));
        assertEquals(1,TalentFeedback.secondsLeft(now+1,now));
        assertEquals(1,TalentFeedback.secondsLeft(now+999,now));
        assertEquals(2,TalentFeedback.secondsLeft(now+1001,now));
        assertEquals("15s",TalentFeedback.format(15));
        assertEquals("1m 5s",TalentFeedback.format(65));
    }

    @Test void feedbackDefaultsOnAndRespectPreviousTalents() {
        var stream=getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        var cfg=YamlConfiguration.loadConfiguration(new InputStreamReader(
                stream,StandardCharsets.UTF_8));
        for(String key:new String[]{"enabled","sounds","particles","actionbar",
                "ready-notifications","ready-chat"})
            assertTrue(cfg.getBoolean("talents.feedback."+key),key);
        assertTrue(cfg.getLong("talents.feedback.passive-interval-ms")>=1500);
        assertEquals(45,cfg.getInt("talents.sunder.cooldown-seconds"));
        assertEquals(30,cfg.getInt("talents.sunder.extra-shield-disable-ticks"));
        assertEquals(5,cfg.getInt("talents.bloodlust.hits-required"));
        assertEquals(15,cfg.getInt("talents.bloodlust.cooldown-seconds"));
        assertEquals(.15,cfg.getDouble("talents.scavenger.chance"));
        assertEquals(90,cfg.getInt("talents.scavenger.cooldown-seconds"));
        assertEquals(600,cfg.getInt("talents.streak-insurance.cooldown-seconds"));
        assertEquals(10,cfg.getInt("talents.defense.shared-cooldown-seconds"));
    }
}
