package org.gw.chatfilterplus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexColors {

    private static final Pattern LEGACY_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final boolean MINI_MESSAGE_AVAILABLE = true;

    private static final Map<String, String> LEGACY_TO_MINI = new LinkedHashMap<>() {{
        put("&0", "<black>"); put("&1", "<dark_blue>"); put("&2", "<dark_green>");
        put("&3", "<dark_aqua>"); put("&4", "<dark_red>"); put("&5", "<dark_purple>");
        put("&6", "<gold>"); put("&7", "<gray>"); put("&8", "<dark_gray>");
        put("&9", "<blue>"); put("&a", "<green>"); put("&b", "<aqua>");
        put("&c", "<red>"); put("&d", "<light_purple>"); put("&e", "<yellow>");
        put("&f", "<white>"); put("&k", "<obfuscated>"); put("&l", "<bold>");
        put("&m", "<strikethrough>"); put("&n", "<underlined>"); put("&o", "<italic>");
        put("&r", "<reset>");
    }};

    public static String translate(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        text = convertLegacyHexToMiniMessage(text);
        text = convertLegacyColorsToMiniMessage(text);

        if (MINI_MESSAGE_AVAILABLE) {
            try {
                return LegacyComponentSerializer.legacySection()
                        .serialize(MiniMessage.miniMessage().deserialize(text));
            } catch (Exception e) {
                return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
            }
        }
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String convertLegacyHexToMiniMessage(String text) {
        Matcher matcher = LEGACY_HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String convertLegacyColorsToMiniMessage(String text) {
        for (Map.Entry<String, String> entry : LEGACY_TO_MINI.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (message != null && !message.trim().isEmpty()) {
            sender.sendMessage(translate(message));
        }
    }

    public static Component translateToComponent(String message) {
        if (message == null || message.trim().isEmpty()) return Component.empty();

        String converted = convertLegacyHexToMiniMessage(message);
        converted = convertLegacyColorsToMiniMessage(converted);

        if (MINI_MESSAGE_AVAILABLE) {
            try {
                return MiniMessage.miniMessage().deserialize(converted);
            } catch (Exception ignored) {}
        }
        return LegacyComponentSerializer.legacySection().deserialize(
                net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', message)
        );
    }
}