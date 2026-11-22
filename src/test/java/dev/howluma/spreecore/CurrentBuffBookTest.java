package dev.howluma.spreecore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class CurrentBuffBookTest {
    private YamlConfiguration config(){
        var stream=getClass().getResourceAsStream("/config.yml");assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
    }
    @Test void onlyHealthAndHuntAreAutomatic() {
        var c=config();
        assertArrayEquals(new String[]{"&8No active Spree buffs"},MenuManager.currentBuffLore(c,-1));
        assertArrayEquals(new String[]{"&8No active Spree buffs"},MenuManager.currentBuffLore(c,0));
        var one=Arrays.asList(MenuManager.currentBuffLore(c,1));
        assertTrue(one.stream().anyMatch(s->s.contains("+0.5 hearts")));
        assertFalse(one.stream().anyMatch(s->s.contains("Mace")));
        assertFalse(one.stream().anyMatch(s->s.contains("Weaving")));
        assertFalse(Arrays.asList(MenuManager.currentBuffLore(c,9)).stream().anyMatch(s->s.contains("Glowing")));
        assertTrue(Arrays.asList(MenuManager.currentBuffLore(c,10)).stream().anyMatch(s->s.contains("Hunt Glowing")));
        assertArrayEquals(MenuManager.currentBuffLore(c, 30), MenuManager.currentBuffLore(c, 31));
        assertArrayEquals(MenuManager.currentBuffLore(c, 30), MenuManager.currentBuffLore(c, 500));
    }
}