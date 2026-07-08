package org.gw.chatfilterplus.configs;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigUtils {

    private static final Pattern RETENTION_PATTERN = Pattern.compile("(\\d+)([smhdwy])");

    private ConfigUtils() {
    }

    public static FileConfiguration loadWithUpdate(ChatFilterPlus plugin, File file, String resourceName) {
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        } else {
            new ConfigUpdater(plugin).update(file, resourceName);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public static List<String> cleanStringList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) {
            if (s != null) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static Set<String> cleanStringSet(List<String> list) {
        if (list == null || list.isEmpty()) return Set.of();
        Set<String> result = new LinkedHashSet<>(Math.max(4, list.size()));
        for (String s : list) {
            if (s != null) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result.isEmpty() ? Set.of() : Collections.unmodifiableSet(result);
    }

    public static List<String> cleanWordList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) {
            if (s != null) {
                String trimmed = s.trim().toLowerCase();
                if (trimmed.length() >= 2 && !result.contains(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static long parseDuration(String period) {
        if (period == null || period.trim().isEmpty()) return 0L;
        long total = 0;
        Matcher matcher = RETENTION_PATTERN.matcher(period.toLowerCase());
        while (matcher.find()) {
            try {
                long value = Long.parseLong(matcher.group(1));
                switch (matcher.group(2)) {
                    case "s" -> total += value * 1000L;
                    case "m" -> total += value * 60 * 1000L;
                    case "h" -> total += value * 3600 * 1000L;
                    case "d" -> total += value * 86400 * 1000L;
                    case "w" -> total += value * 604800 * 1000L;
                    case "y" -> total += value * 31536000 * 1000L;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return total;
    }

    public static long parseRetentionPeriod(String period) {
        return parseRetentionPeriod(period, 5 * 60 * 1000L);
    }

    public static long parseRetentionPeriod(String period, long defaultMillis) {
        long parsed = parseDuration(period);
        return parsed == 0 ? defaultMillis : parsed;
    }
}
