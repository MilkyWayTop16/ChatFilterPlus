package org.gw.chatfilterplus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.gw.chatfilterplus.ChatFilterPlus;

public final class HexColors {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final boolean SUPPORTS_ANSI = System.console() != null && System.getenv().get("TERM") != null;
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_WHITE = "\u001B[37m";

    private HexColors() {
    }

    public static String translate(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        message = message.replace("\\n", "\n");
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = "#" + matcher.group(1);
            matcher.appendReplacement(sb, ChatColor.of(hex).toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static String colorize(String message, boolean consoleLogsEnabled, java.util.logging.Logger logger) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String translated = translate(message);

        if (!SUPPORTS_ANSI) {
            return ChatColor.stripColor(translated);
        }

        Pattern hexPattern = Pattern.compile("§x§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])");
        Matcher matcher = hexPattern.matcher(translated);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4) + matcher.group(5) + matcher.group(6);
            int red = Integer.parseInt(hexCode.substring(0, 2), 16);
            int green = Integer.parseInt(hexCode.substring(2, 4), 16);
            int blue = Integer.parseInt(hexCode.substring(4, 6), 16);
            String ansiColor = String.format("\u001B[38;2;%d;%d;%dm", red, green, blue);
            matcher.appendReplacement(sb, ansiColor);
        }
        matcher.appendTail(sb);

        String result = sb.toString()
                .replace(ChatColor.YELLOW.toString(), ANSI_YELLOW)
                .replace(ChatColor.GREEN.toString(), ANSI_GREEN)
                .replace(ChatColor.RED.toString(), ANSI_RED)
                .replace(ChatColor.WHITE.toString(), ANSI_WHITE)
                .replace(ChatColor.RESET.toString(), ANSI_RESET);

        return result + ANSI_RESET;
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        String translated = translate(message);
        sender.sendMessage(translated);
    }

    public static void sendMessage(CommandSender sender, List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            ChatFilterPlus plugin = ChatFilterPlus.getPlugin(ChatFilterPlus.class);
            if (plugin.getConfigManager().isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Попытка отправить null или пустой список сообщений");
            }
            return;
        }
        for (String message : messages) {
            sendMessage(sender, message);
        }
    }

    public static List<String> translate(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            ChatFilterPlus plugin = ChatFilterPlus.getPlugin(ChatFilterPlus.class);
            if (plugin.getConfigManager().isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Попытка перевести null или пустой список сообщений");
            }
            return new ArrayList<>();
        }
        List<String> translated = new ArrayList<>();
        for (String message : messages) {
            String result = translate(message);
            if (result != null) {
                translated.add(result);
            }
        }
        return translated;
    }

    public static Component translateToComponent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Component.empty();
        }
        message = message.replace("\\n", "\n");
        Component result = Component.empty();
        Matcher matcher = HEX_PATTERN.matcher(message);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = message.substring(lastEnd, matcher.start());
                Component beforeComponent = LegacyComponentSerializer.legacySection()
                        .deserialize(before.replace("&", "§"));
                result = result.append(beforeComponent);
            }

            String hex = matcher.group(1);
            int textStart = matcher.end();
            int nextStart = matcher.find() ? matcher.start() : message.length();
            String text = message.substring(textStart, nextStart);

            Component textComponent = Component.text(text.replace("&", "§"), TextColor.fromHexString("#" + hex));
            result = result.append(textComponent);

            lastEnd = nextStart;
            if (nextStart < message.length()) {
                matcher.reset(message).region(nextStart, message.length());
            }
        }

        if (lastEnd < message.length()) {
            String remaining = message.substring(lastEnd);
            Component remainingComponent = LegacyComponentSerializer.legacySection()
                    .deserialize(remaining.replace("&", "§"));
            result = result.append(remainingComponent);
        }

        ChatFilterPlus plugin = ChatFilterPlus.getPlugin(ChatFilterPlus.class);

        return result;
    }
}