package org.gw.chatfilterplus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HexColors {

    private static final String[] PALETTE = {
            "000000", "0000aa", "00aa00", "00aaaa",
            "aa0000", "aa00aa", "ffaa00", "aaaaaa",
            "555555", "5555ff", "55ff55", "55ffff",
            "ff5555", "ff55ff", "ffff55", "ffffff"
    };

    private static final String LEGACY = "0123456789abcdefklmnorABCDEFKLMNOR";
    private static final String HEX = "0123456789abcdefABCDEF";

    private static final MiniMessage MINI = createMini();
    private static final LegacyComponentSerializer SECTION = createSection();
    private static final LegacyComponentSerializer SECTION_PLAIN = LegacyComponentSerializer.legacySection();

    private static final Map<String, String> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 2000;
                }
            });

    private HexColors() {
    }

    private static MiniMessage createMini() {
        try {
            if (Bukkit.getBukkitVersion().startsWith("1.16")) return null;
            return MiniMessage.miniMessage();
        } catch (Throwable t) {
            return null;
        }
    }

    private static LegacyComponentSerializer createSection() {
        try {
            return LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
        } catch (Throwable t) {
            return LegacyComponentSerializer.legacySection();
        }
    }

    public static String translate(String text) {
        if (text == null || text.isEmpty()) return "";
        String cached = CACHE.get(text);
        if (cached != null) return cached;
        String result = convert(text, false);
        CACHE.put(text, result);
        return result;
    }

    public static Component translateToComponent(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        if (MINI != null) {
            try {
                return MINI.deserialize(convert(message, true));
            } catch (Throwable ignored) {
            }
        }
        return SECTION.deserialize(convert(message, false));
    }

    public static String translateForConsole(String text) {
        if (text == null || text.isEmpty()) return "";
        return SECTION_PLAIN.serialize(translateToComponent(text));
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.trim().isEmpty()) return;
        try {
            sender.sendMessage(translateToComponent(message));
        } catch (Throwable t) {
            sender.sendMessage(translate(message));
        }
    }

    public static String stripMiniMessageTags(String text) {
        if (text == null || text.isEmpty() || MINI == null) return text;
        try {
            return MINI.stripTags(text);
        } catch (Throwable t) {
            return text;
        }
    }

    private static String convert(String input, boolean mini) {
        StringBuilder out = new StringBuilder(input.length() + 24);
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);

            if (c == '<' && mini) {
                int close = input.indexOf('>', i);
                if (close != -1 && close - i <= 64) {
                    out.append(input, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }

            if ((c == '&' || c == '§') && i + 1 < n) {
                char next = input.charAt(i + 1);

                if (next == '#' && i + 7 < n && isHex6(input, i + 2)) {
                    appendHex(out, input.substring(i + 2, i + 8), mini);
                    i += 8;
                    continue;
                }

                if ((next == 'x' || next == 'X') && i + 13 < n) {
                    String hex = readXHex(input, i + 2, c);
                    if (hex != null) {
                        appendHex(out, hex, mini);
                        i += 14;
                        continue;
                    }
                }

                if (LEGACY.indexOf(next) >= 0) {
                    appendLegacy(out, next, mini);
                    i += 2;
                    continue;
                }
            }

            if (c == '#' && i + 6 < n && isHex6(input, i + 1)
                    && (i == 0 || HEX.indexOf(input.charAt(i - 1)) < 0)) {
                appendHex(out, input.substring(i + 1, i + 7), mini);
                i += 7;
                continue;
            }

            if (c == '<' && !mini) {
                int close = input.indexOf('>', i);
                if (close != -1 && close - i <= 40) {
                    i = close + 1;
                    continue;
                }
            }

            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String readXHex(String input, int start, char marker) {
        char[] hex = new char[6];
        for (int j = 0; j < 6; j++) {
            int pos = start + j * 2;
            if (input.charAt(pos) != marker || HEX.indexOf(input.charAt(pos + 1)) < 0) return null;
            hex[j] = input.charAt(pos + 1);
        }
        return new String(hex);
    }

    private static void appendHex(StringBuilder out, String hex, boolean mini) {
        if (mini) {
            out.append("<#").append(hex).append('>');
            return;
        }
        out.append('§').append('x');
        for (int i = 0; i < 6; i++) out.append('§').append(Character.toLowerCase(hex.charAt(i)));
    }

    private static void appendLegacy(StringBuilder out, char code, boolean mini) {
        char c = Character.toLowerCase(code);
        int idx = "0123456789abcdef".indexOf(c);
        if (idx >= 0) {
            appendHex(out, PALETTE[idx], mini);
            return;
        }
        if (!mini) {
            out.append('§').append(c);
            return;
        }
        switch (c) {
            case 'k' -> out.append("<obfuscated>");
            case 'l' -> out.append("<bold>");
            case 'm' -> out.append("<strikethrough>");
            case 'n' -> out.append("<underlined>");
            case 'o' -> out.append("<italic>");
            case 'r' -> out.append("<reset>");
            default -> out.append('&').append(code);
        }
    }

    private static boolean isHex6(String s, int off) {
        for (int i = 0; i < 6; i++) {
            if (HEX.indexOf(s.charAt(off + i)) < 0) return false;
        }
        return true;
    }
}
