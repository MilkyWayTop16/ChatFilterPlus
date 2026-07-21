package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.PermissionCompat;
import org.gw.chatfilterplus.utils.PlaceholderUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class PunishmentManager {

    private static final long VIOLATION_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;

    private final Map<FilterType, File> punishmentLogFiles = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Map<String, ViolationCounter>> violations = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Set<String>> exemptPlayers = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Set<String>> exemptGroups = new EnumMap<>(FilterType.class);
    private final Map<FilterType, String> bypassPermissions = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Boolean> punishmentsEnabled = new EnumMap<>(FilterType.class);

    private final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    private static final class ViolationCounter {
        private final AtomicInteger count = new AtomicInteger();
        private volatile long lastViolationAt;

        int increment() {
            lastViolationAt = System.currentTimeMillis();
            return count.incrementAndGet();
        }

        long lastViolationAt() {
            return lastViolationAt;
        }
    }

    public PunishmentManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        File logsDir = new File(plugin.getDataFolder(), "logs");
        for (FilterType type : FilterType.values()) {
            punishmentLogFiles.put(type, new File(logsDir, type.punishmentLogFileName()));
            violations.put(type, new ConcurrentHashMap<>());
        }

        punishmentLogFiles.values().forEach(this::createLogFileIfNotExists);
        reloadExemptCache();
        startViolationCleanupTask();
    }

    private void reloadExemptCache() {
        for (FilterType type : FilterType.values()) {
            FileConfiguration config = type.config(configManager);
            punishmentsEnabled.put(type, config.getBoolean(type.punishmentPath("enabled"), false));

            Set<String> players = new HashSet<>();
            for (String name : config.getStringList(type.punishmentPath("exceptions.players"))) {
                if (name != null && !name.isBlank()) players.add(name.trim());
            }
            exemptPlayers.put(type, players.isEmpty() ? Set.of() : Set.copyOf(players));

            Set<String> groups = new HashSet<>();
            for (String group : config.getStringList(type.punishmentPath("exceptions.groups"))) {
                if (group != null && !group.isBlank()) groups.add(group.trim());
            }
            exemptGroups.put(type, groups.isEmpty() ? Set.of() : Set.copyOf(groups));

            String bypass = config.getString(type.punishmentPath("bypass-permission"), "");
            bypassPermissions.put(type, bypass == null ? "" : bypass);
        }
    }

    private void createLogFileIfNotExists(File file) {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.console("Не удалось создать файл логов наказаний: " + file.getName());
        }
    }

    private void startViolationCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanOldViolations, 20L * 60 * 60, 20L * 60 * 60);
    }

    private void cleanOldViolations() {
        long currentTime = System.currentTimeMillis();

        for (FilterType type : FilterType.values()) {
            violations.get(type).entrySet().removeIf(entry -> {
                if (Bukkit.getPlayerExact(entry.getKey()) != null) return false;
                return currentTime - entry.getValue().lastViolationAt() > VIOLATION_RETENTION_MILLIS;
            });
        }
    }

    public void handlePunishment(Player player, FilterType type, List<String> items) {
        if (!isPunishmentsEnabled(type) || isPlayerExempt(player, type)) return;

        String playerName = player.getName();
        int violationCount = violations.get(type)
                .computeIfAbsent(playerName, k -> new ViolationCounter())
                .increment();

        ConfigurationSection stagesSection = type.config(configManager)
                .getConfigurationSection(type.punishmentPath("stages"));
        if (stagesSection == null) return;

        String stage = String.valueOf(violationCount);
        List<String> actions = stagesSection.getStringList(stage + ".actions");

        if (!actions.isEmpty()) {
            List<String> commands = parsePunishmentActions(actions, player, items);
            if (!commands.isEmpty()) {
                executeCommands(player, commands);
                logPunishment(playerName, items, violationCount, stage, type);
            }
        }
    }

    private boolean isPunishmentsEnabled(FilterType type) {
        return Boolean.TRUE.equals(punishmentsEnabled.get(type));
    }

    private boolean isPlayerExempt(Player player, FilterType type) {
        if (exemptPlayers.getOrDefault(type, Set.of()).contains(player.getName())) return true;

        String bypassPermission = bypassPermissions.getOrDefault(type, "");
        if (!bypassPermission.isEmpty() && PermissionCompat.hasPermission(player, bypassPermission)) return true;
        if (type == FilterType.ANTI_SPAM && player.hasPermission(PermissionCompat.PUNISH_ANTISPAM)) return true;

        for (String group : exemptGroups.getOrDefault(type, Set.of())) {
            if (player.hasPermission("group." + group)) return true;
        }
        return false;
    }

    private void executeCommands(Player player, List<String> commands) {
        for (String cmd : commands) {
            String formatted = cmd.replace("{player}", player.getName());
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), formatted));
        }
    }

    private List<String> parsePunishmentActions(List<String> actions, Player player, List<String> items) {
        if (actions == null || actions.isEmpty()) return Collections.emptyList();

        String playerName = player.getName();
        String wordsJoined = PlaceholderUtil.sanitizeCommandValue(String.join(", ", items));
        String originalMessage = items.isEmpty()
                ? "[CAPS]"
                : PlaceholderUtil.sanitizeCommandValue(String.join(" ", items));
        String reason = items.isEmpty() ? "" : PlaceholderUtil.sanitizeCommandValue(items.get(0));

        List<String> result = new ArrayList<>(actions.size());
        for (String action : actions) {
            if (action == null) continue;
            String trimmed = action.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("[console-command]")) {
                trimmed = trimmed.substring("[console-command]".length()).trim();
            } else if (trimmed.startsWith("[")) {
                continue;
            }

            String cmd = trimmed
                    .replace("{player}", playerName)
                    .replace("{words}", wordsJoined)
                    .replace("{links}", wordsJoined)
                    .replace("{original-message}", originalMessage)
                    .replace("{reason}", reason);

            if (!cmd.isEmpty()) {
                result.add(cmd);
            }
        }
        return result;
    }

    private void logPunishment(String playerName, List<String> items, int violationCount, String stage, FilterType type) {
        FileConfiguration config = type.config(configManager);
        if (!config.getBoolean(type.punishmentPath("logs.enabled"), true)) return;

        String template = config.getString(type.punishmentPath("logs.message"), type.getDefaultPunishmentLogTemplate());
        String joined = String.join(", ", items);

        String message = template
                .replace("{player}", playerName)
                .replace("{time}", dateFormat.get().format(new Date()))
                .replace("{violations}", String.valueOf(violationCount))
                .replace("{stage}", stage)
                .replace("{words}", joined)
                .replace("{links}", joined)
                .replace("{original-message}", items.isEmpty() ? "[CAPS]" : joined);

        File logFile = punishmentLogFiles.get(type);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    logFile.toPath(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                writer.write(message);
                writer.newLine();
            } catch (IOException e) {
                plugin.console("Ошибка записи лога наказания: " + e.getMessage());
            }
        });
    }

    public void reload() {
        violations.values().forEach(Map::clear);
        reloadExemptCache();
    }
}
