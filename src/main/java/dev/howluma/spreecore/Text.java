package dev.howluma.spreecore;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Locale;

public final class Text {
    private Text() {}
    // Works with standard & codes and Skript-style <#RRGGBB> gradients.
    public static String c(String input) {
        if (input == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            if (i + 8 < input.length() && input.charAt(i) == '<'
                    && input.charAt(i + 1) == '#' && input.charAt(i + 8) == '>') {
                String hex = input.substring(i + 2, i + 8);
                if (hex.matches("[0-9a-fA-F]{6}")) {
                    out.append('§').append('x');
                    for (char ch : hex.toCharArray()) out.append('§').append(Character.toLowerCase(ch));
                    i += 8;
                    continue;
                }
            }
            out.append(input.charAt(i));
        }
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }
    public static void action(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(c(message)));
    }
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(c(message));
    }
    public static String formatStreak(int streak, String recoveryLabel) {
        return streak < 0 ? c(recoveryLabel) : Integer.toString(streak);
    }
    public static String time(long millis) {
        long secs = Math.max(0, (millis + 999) / 1000);
        return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }
}
