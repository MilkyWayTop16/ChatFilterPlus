package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;

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

    private final File badWordsPunishmentLogFile;
    private final File linksPunishmentLogFile;
    private final File capsPunishmentLogFile;
    private final File blockedWordsPunishmentLogFile;
    private final File antiSpamPunishmentLogFile;

    private final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    private final Map<String, AtomicInteger> playerBadWordsViolations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playerLinksViolations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playerCapsViolations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playerBlockedWordsViolations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playerAntiSpamViolations = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Long>> playerBadWordsNotificationCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> playerLinksNotificationCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> playerCapsNotificationCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> playerBlockedWordsNotificationCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> playerAntiSpamNotificationCooldowns = new ConcurrentHashMap<>();

    public PunishmentManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        this.badWordsPunishmentLogFile = new File(plugin.getDataFolder() + File.separator + "logs", "badwords-punishments-logs.txt");
        this.linksPunishmentLogFile = new File(plugin.getDataFolder() + File.separator + "logs", "links-punishments-logs.txt");
        this.capsPunishmentLogFile = new File(plugin.getDataFolder() + File.separator + "logs", "caps-punishments-logs.txt");
        this.blockedWordsPunishmentLogFile = new File(plugin.getDataFolder() + File.separator + "logs", "blockedwords-punishments-logs.txt");
        this.antiSpamPunishmentLogFile = new File(plugin.getDataFolder() + File.separator + "logs", "antispam-punishments-logs.txt");

        initializePunishmentLogFiles();
        startViolationCleanupTask();
    }

    private void initializePunishmentLogFiles() {
        createLogFileIfNotExists(badWordsPunishmentLogFile);
        createLogFileIfNotExists(linksPunishmentLogFile);
        createLogFileIfNotExists(capsPunishmentLogFile);
        createLogFileIfNotExists(blockedWordsPunishmentLogFile);
        createLogFileIfNotExists(antiSpamPunishmentLogFile);
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
        cleanAtomicMap(playerBadWordsViolations);
        cleanAtomicMap(playerLinksViolations);
        cleanAtomicMap(playerCapsViolations);
        cleanAtomicMap(playerBlockedWordsViolations);
        cleanAtomicMap(playerAntiSpamViolations);

        cleanCooldowns(playerBadWordsNotificationCooldowns, currentTime);
        cleanCooldowns(playerLinksNotificationCooldowns, currentTime);
        cleanCooldowns(playerCapsNotificationCooldowns, currentTime);
        cleanCooldowns(playerBlockedWordsNotificationCooldowns, currentTime);
        cleanCooldowns(playerAntiSpamNotificationCooldowns, currentTime);
    }

    private void cleanAtomicMap(Map<String, AtomicInteger> map) {
        map.entrySet().removeIf(entry -> Bukkit.getPlayerExact(entry.getKey()) == null);
    }

    private void cleanCooldowns(Map<String, Map<String, Long>> cooldownsMap, long currentTime) {
        cooldownsMap.entrySet().removeIf(entry -> {
            boolean isOffline = Bukkit.getPlayerExact(entry.getKey()) == null;
            if (isOffline) return true;
            entry.getValue().entrySet().removeIf(cooldown -> currentTime > cooldown.getValue());
            return entry.getValue().isEmpty();
        });
    }

    public void handlePunishment(Player player, FilterType type, List<String> items) {
        if (!isPunishmentsEnabled(type) || isPlayerExempt(player, type)) return;

        String playerName = player.getName();
        AtomicInteger counter = getViolationsMap(type).computeIfAbsent(playerName, k -> new AtomicInteger(0));
        int violations = counter.incrementAndGet();

        ConfigurationSection punishments = getPunishmentsSection(type);
        if (punishments == null) return;

        String stage = String.valueOf(violations);
        ConfigurationSection stagesSection = punishments.getConfigurationSection("stages");
        List<String> actions = (stagesSection != null) ? stagesSection.getStringList(stage + ".actions") : Collections.emptyList();
        List<String> notificationCooldowns = (stagesSection != null) ? stagesSection.getStringList(stage + ".notification-cooldowns") : Collections.emptyList();

        if (!actions.isEmpty()) {
            List<String> parsedCommands = parsePunishmentActions(actions, player, items);
            if (!parsedCommands.isEmpty()) {
                executeCommands(player, parsedCommands);
                logPunishment(playerName, items, violations, stage, parsedCommands, type);
            }
        }
        updateNotificationCooldowns(playerName, notificationCooldowns, type);
    }

    private Map<String, AtomicInteger> getViolationsMap(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> playerBadWordsViolations;
            case LINKS -> playerLinksViolations;
            case CAPS -> playerCapsViolations;
            case BLOCKED_WORDS -> playerBlockedWordsViolations;
            case ANTI_SPAM -> playerAntiSpamViolations;
        };
    }

    private ConfigurationSection getPunishmentsSection(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getConfigurationSection("punishments.bad-words");
            case LINKS -> configManager.getLinksConfig().getConfigurationSection("punishments.links");
            case CAPS -> configManager.getCapsConfig().getConfigurationSection("punishments.caps");
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getConfigurationSection("punishments.blocked-words");
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getConfigurationSection("punishments.anti-spam");
        };
    }

    private boolean isPunishmentsEnabled(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getBoolean("punishments.bad-words.enabled", false);
            case LINKS -> configManager.getLinksConfig().getBoolean("punishments.links.enabled", false);
            case CAPS -> configManager.getCapsConfig().getBoolean("punishments.caps.enabled", false);
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getBoolean("punishments.blocked-words.enabled", false);
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getBoolean("punishments.anti-spam.enabled", false);
        };
    }

    private boolean isPlayerExempt(Player player, FilterType type) {
        List<String> exceptionPlayers = switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getStringList("punishments.bad-words.exceptions.players");
            case LINKS -> configManager.getLinksConfig().getStringList("punishments.links.exceptions.players");
            case CAPS -> configManager.getCapsConfig().getStringList("punishments.caps.exceptions.players");
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getStringList("punishments.blocked-words.exceptions.players");
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getStringList("punishments.anti-spam.exceptions.players");
        };

        List<String> exceptionGroups = switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getStringList("punishments.bad-words.exceptions.groups");
            case LINKS -> configManager.getLinksConfig().getStringList("punishments.links.exceptions.groups");
            case CAPS -> configManager.getCapsConfig().getStringList("punishments.caps.exceptions.groups");
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getStringList("punishments.blocked-words.exceptions.groups");
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getStringList("punishments.anti-spam.exceptions.groups");
        };

        String bypassPermission = switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getString("punishments.bad-words.bypass-permission");
            case LINKS -> configManager.getLinksConfig().getString("punishments.links.bypass-permission");
            case CAPS -> configManager.getCapsConfig().getString("punishments.caps.bypass-permission");
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getString("punishments.blocked-words.bypass-permission");
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getString("punishments.anti-spam.bypass-permission");
        };

        if (exceptionPlayers.contains(player.getName()) || player.hasPermission(bypassPermission)) return true;

        for (String group : exceptionGroups) {
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

    private void logPunishment(String playerName, List<String> items, int violations, String stage, List<String> commands, FilterType type) {
        if (!isPunishmentLoggingEnabled(type)) return;

        String message = getLogTemplate(type)
                .replace("{player}", playerName)
                .replace("{time}", dateFormat.get().format(new Date()))
                .replace("{violations}", String.valueOf(violations))
                .replace("{stage}", stage)
                .replace("{words}", String.join(", ", items))
                .replace("{links}", String.join(", ", items))
                .replace("{original-message", items.isEmpty() ? "[CAPS]" : String.join(", ", items));

        File logFile = getPunishmentLogFile(type);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(message);
                writer.newLine();
            } catch (IOException e) {
                plugin.console("Ошибка записи лога наказания: " + e.getMessage());
            }
        });
    }

    private boolean isPunishmentLoggingEnabled(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getBoolean("punishments.bad-words.logs.enabled", true);
            case LINKS -> configManager.getLinksConfig().getBoolean("punishments.links.logs.enabled", true);
            case CAPS -> configManager.getCapsConfig().getBoolean("punishments.caps.logs.enabled", true);
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getBoolean("punishments.blocked-words.logs.enabled", true);
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getBoolean("punishments.anti-spam.logs.enabled", true);
        };
    }

    private String getLogTemplate(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getString("punishments.bad-words.logs.message");
            case LINKS -> configManager.getLinksConfig().getString("punishments.links.logs.message");
            case CAPS -> configManager.getCapsConfig().getString("punishments.caps.logs.message", "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) использовал капс");
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getString("punishments.blocked-words.logs.message", "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) использовал запрещённое слово(а): {words}");
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getString("punishments.anti-spam.logs.message", "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) был пойман на спаме");
        };
    }

    private File getPunishmentLogFile(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> badWordsPunishmentLogFile;
            case LINKS -> linksPunishmentLogFile;
            case CAPS -> capsPunishmentLogFile;
            case BLOCKED_WORDS -> blockedWordsPunishmentLogFile;
            case ANTI_SPAM -> antiSpamPunishmentLogFile;
        };
    }

    private void updateNotificationCooldowns(String playerName, List<String> notificationCooldowns, FilterType type) {
        Map<String, Map<String, Long>> cooldownsMap = switch (type) {
            case BAD_WORDS -> playerBadWordsNotificationCooldowns;
            case LINKS -> playerLinksNotificationCooldowns;
            case CAPS -> playerCapsNotificationCooldowns;
            case BLOCKED_WORDS -> playerBlockedWordsNotificationCooldowns;
            case ANTI_SPAM -> playerAntiSpamNotificationCooldowns;
        };

        Map<String, Long> playerCooldowns = cooldownsMap.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>());
        long currentTime = System.currentTimeMillis();

        for (String cooldown : notificationCooldowns) {
            String[] parts = cooldown.split(":", 2);
            if (parts.length != 2) continue;

            String notificationType = parts[0].trim();
            long durationMillis = parseDuration(parts[1].trim());
            if (durationMillis > 0) {
                playerCooldowns.put(notificationType, currentTime + durationMillis);
            }
        }
    }

    private long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) return 0;

        long duration = 0;
        StringBuilder number = new StringBuilder();
        for (char c : durationStr.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else if (number.length() > 0) {
                try {
                    long value = Long.parseLong(number.toString());
                    switch (c) {
                        case 's' -> duration += value * 1000L;
                        case 'm' -> duration += value * 60 * 1000L;
                        case 'h' -> duration += value * 60 * 60 * 1000L;
                        case 'd' -> duration += value * 24 * 60 * 60 * 1000L;
                    }
                } catch (NumberFormatException ignored) {}
                number = new StringBuilder();
            }
        }
        return duration;
    }

    public void reload() {
        playerBadWordsViolations.clear();
        playerLinksViolations.clear();
        playerCapsViolations.clear();
        playerBlockedWordsViolations.clear();
        playerAntiSpamViolations.clear();
        playerBadWordsNotificationCooldowns.clear();
        playerLinksNotificationCooldowns.clear();
        playerCapsNotificationCooldowns.clear();
        playerBlockedWordsNotificationCooldowns.clear();
        playerAntiSpamNotificationCooldowns.clear();
    }
}