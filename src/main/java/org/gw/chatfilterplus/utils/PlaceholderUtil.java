package org.gw.chatfilterplus.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlaceholderUtil {

    private static final boolean placeholderAPIAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private static final int COMMAND_VALUE_MAX_LENGTH = 128;

    public static String parse(Player player, String text) {
        if (text == null || text.isEmpty()) return text;

        if (placeholderAPIAvailable && player != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public static String sanitizeCommandValue(String value) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(Math.min(value.length(), COMMAND_VALUE_MAX_LENGTH));
        for (int i = 0; i < value.length() && sb.length() < COMMAND_VALUE_MAX_LENGTH; i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '%' || c == '{' || c == '}') continue;
            sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isPlayerControlledPlaceholder(String key) {
        if (key == null) return false;
        return switch (key) {
            case "words", "links", "original-message", "reason" -> true;
            default -> false;
        };
    }
}
