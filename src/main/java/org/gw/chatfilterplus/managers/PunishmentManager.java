package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.configs.ConfigUtils;
import org.gw.chatfilterplus.utils.PermissionCompat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class PunishmentManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;

    private final Map<FilterType, File> punishmentLogFiles = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Map<String, AtomicInteger>> violations = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Map<String, Map<String, Long>>> notificationCooldowns = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Set<String>> exemptPlayers = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Set<String>> exemptGroups = new EnumMap<>(FilterType.class);
    private final Map<FilterType, String> bypassPermissions = new EnumMap<>(FilterType.class);
    private final Map<FilterType, Boolean> punishmentsEnabled = new EnumMap<>(FilterType.class);

    private final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public PunishmentManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        File logsDir = new File(plugin.getDataFolder(), "logs");
        for (FilterType type : FilterType.values()) {
            punishmentLogFiles.put(type, new File(logsDir, type.punishmentLogFileName()));
            violations.put(type, new ConcurrentHashMap<>());
            notificationCooldowns.put(type, new ConcurrentHashMap<>());
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
            violations.get(type).entrySet().removeIf(entry -> Bukkit.getPlayerExact(entry.getKey()) == null);
            cleanCooldowns(notificationCooldowns.get(type), currentTime);
        }
    }

    private void cleanCooldowns(Map<String, Map<String, Long>> cooldownsMap, long currentTime) {
        cooldownsMap.entrySet().removeIf(entry -> {
            if (Bukkit.getPlayerExact(entry.getKey()) == null) return true;
            entry.getValue().entrySet().removeIf(cooldown -> currentTime > cooldown.getValue());
            return entry.getValue().isEmpty();
        });
    }

    public void handlePunishment(Player player, FilterType type, List<String> items) {
        if (!isPunishmentsEnabled(type) || isPlayerExempt(player, type)) return;

        String playerName = player.getName();
        int violationCount = violations.get(type)
                .computeIfAbsent(playerName, k -> new AtomicInteger(0))
                .incrementAndGet();

        ConfigurationSection stagesSection = type.config(configManager)
                .getConfigurationSection(type.punishmentPath("stages"));
        if (stagesSection == null) return;

        String stage = String.valueOf(violationCount);
        List<String> actions = stagesSection.getStringList(stage + ".actions");
        List<String> cooldowns = stagesSection.getStringList(stage + ".notification-cooldowns");

        if (!actions.isEmpty()) {
            List<String> commands = parsePunishmentActions(actions, player, items);
            if (!commands.isEmpty()) {
                executeCommands(player, commands);
                logPunishment(playerName, items, violationCount, stage, type);
            }
        }
        updateNotificationCooldowns(playerName, cooldowns, type);
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
        String wordsJoined = String.join(", ", items);
        String originalMessage = items.isEmpty() ? "[CAPS]" : String.join(" ", items);
        String reason = items.isEmpty() ? "" : items.get(0);

        List<String> result = new ArrayList<>(actions.size());
        for (String action : actions) {
            if (action == null) continue;
            String trimmed = action.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("[console-command]")) {
                trimmed = trimmed.substring("[console-command]".length()).trim();
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
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(message);
                writer.newLine();
            } catch (IOException e) {
                plugin.console("Ошибка записи лога наказания: " + e.getMessage());
            }
        });
    }

    private void updateNotificationCooldowns(String playerName, List<String> cooldowns, FilterType type) {
        if (cooldowns.isEmpty()) return;

        Map<String, Long> playerCooldowns = notificationCooldowns.get(type)
                .computeIfAbsent(playerName, k -> new ConcurrentHashMap<>());
        long currentTime = System.currentTimeMillis();

        for (String cooldown : cooldowns) {
            String[] parts = cooldown.split(":", 2);
            if (parts.length != 2) continue;

            long durationMillis = ConfigUtils.parseDuration(parts[1].trim());
            if (durationMillis > 0) {
                playerCooldowns.put(parts[0].trim(), currentTime + durationMillis);
            }
        }
    }

    public void reload() {
        violations.values().forEach(Map::clear);
        notificationCooldowns.values().forEach(Map::clear);
        reloadExemptCache();
    }
}
