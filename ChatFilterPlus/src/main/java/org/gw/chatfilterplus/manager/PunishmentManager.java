package org.gw.chatfilterplus.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PunishmentManager {
    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final File badWordsPunishmentLogFile;
    private final File linksPunishmentLogFile;
    private final SimpleDateFormat dateFormat;
    private final Map<String, Integer> playerBadWordsViolations;
    private final Map<String, Integer> playerLinksViolations;
    private final Map<String, Map<String, Long>> playerBadWordsNotificationCooldowns;
    private final Map<String, Map<String, Long>> playerLinksNotificationCooldowns;

    public PunishmentManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.badWordsPunishmentLogFile = new File(plugin.getDataFolder(), "badwords-punishments-logs.txt");
        this.linksPunishmentLogFile = new File(plugin.getDataFolder(), "links-punishments-logs.txt");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.playerBadWordsViolations = new HashMap<>();
        this.playerLinksViolations = new HashMap<>();
        this.playerBadWordsNotificationCooldowns = new HashMap<>();
        this.playerLinksNotificationCooldowns = new HashMap<>();
        initializePunishmentLogFiles();
    }

    private void initializePunishmentLogFiles() {
        try {
            if (!badWordsPunishmentLogFile.exists()) {
                badWordsPunishmentLogFile.getParentFile().mkdirs();
                badWordsPunishmentLogFile.createNewFile();
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Создан файл логов наказаний: badwords-punishments-logs.txt");
                }
            }
            if (!linksPunishmentLogFile.exists()) {
                linksPunishmentLogFile.getParentFile().mkdirs();
                linksPunishmentLogFile.createNewFile();
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Создан файл логов наказаний: links-punishments-logs.txt");
                }
            }
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Не удалось создать файлы логов наказаний: " + e.getMessage());
            }
        }
    }

    public void handleBadWordsPunishment(Player player, List<String> badWords) {
        if (!configManager.isBadWordsPunishmentsEnabled()) {
            return;
        }

        String playerName = player.getName();
        if (isPlayerExempt(player, true)) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Игрок " + playerName + " освобождён от наказаний за маты.");
            }
            return;
        }

        int violations = playerBadWordsViolations.getOrDefault(playerName, 0) + 1;
        playerBadWordsViolations.put(playerName, violations);

        ConfigurationSection punishments = configManager.getWordsConfig().getConfigurationSection("punishments");
        if (punishments == null) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Секция punishments отсутствует в words.yml");
            }
            return;
        }

        String stage = String.valueOf(violations);
        List<String> commands = punishments.getStringList(stage + ".commands");
        List<String> notificationCooldowns = punishments.getStringList(stage + ".notification-cooldowns");
        long eventTime = System.currentTimeMillis();

        if (!commands.isEmpty()) {
            executeCommands(player, commands);
            logPunishment(playerName, badWords, violations, stage, commands, eventTime, true);
        }

        updateNotificationCooldowns(playerName, notificationCooldowns, true);
    }

    public void handleLinksPunishment(Player player, List<String> links) {
        if (!configManager.isLinksPunishmentsEnabled()) {
            return;
        }

        String playerName = player.getName();
        if (isPlayerExempt(player, false)) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Игрок " + playerName + " освобождён от наказаний за ссылки.");
            }
            return;
        }

        int violations = playerLinksViolations.getOrDefault(playerName, 0) + 1;
        playerLinksViolations.put(playerName, violations);

        ConfigurationSection punishments = configManager.getWordsConfig().getConfigurationSection("punishments");
        if (punishments == null) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Секция punishments отсутствует в words.yml");
            }
            return;
        }

        String stage = String.valueOf(violations);
        List<String> commands = punishments.getStringList(stage + ".commands");
        List<String> notificationCooldowns = punishments.getStringList(stage + ".notification-cooldowns");
        long eventTime = System.currentTimeMillis();

        if (!commands.isEmpty()) {
            executeCommands(player, commands);
            logPunishment(playerName, links, violations, stage, commands, eventTime, false);
        }

        updateNotificationCooldowns(playerName, notificationCooldowns, false);
    }

    private void executeCommands(Player player, List<String> commands) {
        new ArrayList<>(commands).forEach(command -> {
            String formattedCommand = command.replace("{player}", player.getName());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), formattedCommand);
                    if (configManager.isConsoleLogsEnabled()) {
                        plugin.getLogger().info("Выполнена команда наказания для " + player.getName() + ": " + formattedCommand);
                    }
                } catch (Exception e) {
                    if (configManager.isConsoleLogsEnabled()) {
                        plugin.getLogger().warning("Ошибка при выполнении команды наказания для " + player.getName() + ": " + formattedCommand + ", ошибка: " + e.getMessage());
                    }
                }
            });
        });
    }

    private void logPunishment(String playerName, List<String> items, int violations, String stage, List<String> commands, long eventTime, boolean isBadWords) {
        if ((isBadWords && !configManager.isBadWordsPunishmentLogsEnabled()) || (!isBadWords && !configManager.isLinksPunishmentLogsEnabled())) {
            return;
        }

        String formattedItems;
        if (items.size() == 1) {
            if (isBadWords) {
                formattedItems = configManager.getBadWordsPunishmentSingleWordTemplate().replace("{word}", items.get(0));
            } else {
                formattedItems = configManager.getLinksPunishmentSingleLinkTemplate().replace("{link}", items.get(0));
            }
        } else {
            StringBuilder itemsBuilder = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (isBadWords) {
                    itemsBuilder.append(configManager.getBadWordsPunishmentWordTemplate().replace("{word}", items.get(i)));
                    if (i < items.size() - 1) {
                        itemsBuilder.append(configManager.getBadWordsPunishmentWordsSeparator());
                    }
                } else {
                    itemsBuilder.append(configManager.getLinksPunishmentLinkTemplate().replace("{link}", items.get(i)));
                    if (i < items.size() - 1) {
                        itemsBuilder.append(configManager.getLinksPunishmentLinksSeparator());
                    }
                }
            }
            formattedItems = itemsBuilder.toString();
        }

        String formattedCommands;
        if (commands.size() == 1) {
            if (isBadWords) {
                formattedCommands = configManager.getBadWordsPunishmentSingleCommandTemplate().replace("{command}", commands.get(0));
            } else {
                formattedCommands = configManager.getLinksPunishmentSingleCommandTemplate().replace("{command}", commands.get(0));
            }
        } else {
            StringBuilder commandsBuilder = new StringBuilder();
            for (int i = 0; i < commands.size(); i++) {
                if (isBadWords) {
                    commandsBuilder.append(configManager.getBadWordsPunishmentCommandTemplate().replace("{command}", commands.get(i)));
                    if (i < commands.size() - 1) {
                        commandsBuilder.append(configManager.getBadWordsPunishmentCommandsSeparator());
                    }
                } else {
                    commandsBuilder.append(configManager.getLinksPunishmentCommandTemplate().replace("{command}", commands.get(i)));
                    if (i < commands.size() - 1) {
                        commandsBuilder.append(configManager.getLinksPunishmentCommandsSeparator());
                    }
                }
            }
            formattedCommands = commandsBuilder.toString();
        }

        String message = (isBadWords ? configManager.getBadWordsPunishmentLogMessage() : configManager.getLinksPunishmentLogMessage())
                .replace("{player}", playerName)
                .replace("{time}", dateFormat.format(new Date(eventTime)))
                .replace(isBadWords ? "{words}" : "{links}", formattedItems)
                .replace("{violations}", String.valueOf(violations))
                .replace("{stage}", stage)
                .replace("{commands}", formattedCommands);

        File logFile = isBadWords ? badWordsPunishmentLogFile : linksPunishmentLogFile;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(message);
            writer.newLine();
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Записано наказание в " + logFile.getName() + ": " + message);
            }
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Не удалось записать наказание в " + logFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private void updateNotificationCooldowns(String playerName, List<String> notificationCooldowns, boolean isBadWords) {
        Map<String, Long> cooldownsMap = isBadWords ? playerBadWordsNotificationCooldowns.computeIfAbsent(playerName, k -> new HashMap<>()) :
                playerLinksNotificationCooldowns.computeIfAbsent(playerName, k -> new HashMap<>());
        long currentTime = System.currentTimeMillis();

        for (String cooldown : notificationCooldowns) {
            String[] parts = cooldown.split(":", 2);
            if (parts.length != 2) {
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().warning("Неверный формат notification-cooldowns: " + cooldown);
                }
                continue;
            }
            String type = parts[0].trim();
            String durationStr = parts[1].trim();
            long durationMillis = parseDuration(durationStr);
            if (durationMillis > 0) {
                cooldownsMap.put(type, currentTime + durationMillis);
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Установлен кулдаун для " + playerName + ", тип: " + type + ", длительность: " + durationMillis + " мс");
                }
            }
        }
    }

    public boolean isBadWordsNotificationDisabled(Player player, String type) {
        Map<String, Long> cooldowns = playerBadWordsNotificationCooldowns.getOrDefault(player.getName(), new HashMap<>());
        Long cooldownEnd = cooldowns.get(type);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Уведомление типа " + type + " для матов отключено для " + player.getName() + " до " + dateFormat.format(new Date(cooldownEnd)));
            }
            return true;
        }
        return false;
    }

    public boolean isLinksNotificationDisabled(Player player, String type) {
        Map<String, Long> cooldowns = playerLinksNotificationCooldowns.getOrDefault(player.getName(), new HashMap<>());
        Long cooldownEnd = cooldowns.get(type);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Уведомление типа " + type + " для ссылок отключено для " + player.getName() + " до " + dateFormat.format(new Date(cooldownEnd)));
            }
            return true;
        }
        return false;
    }

    private boolean isPlayerExempt(Player player, boolean isBadWords) {
        String playerName = player.getName();
        List<String> exceptionPlayers = isBadWords ? configManager.getBadWordsExceptionPlayers() : configManager.getLinksExceptionPlayers();
        List<String> exceptionGroups = isBadWords ? configManager.getBadWordsExceptionGroups() : configManager.getLinksExceptionGroups();
        String bypassPermission = isBadWords ? configManager.getBadWordsBypassPermission() : configManager.getLinksBypassPermission();

        if (exceptionPlayers.contains(playerName)) {
            return true;
        }

        if (player.hasPermission(bypassPermission)) {
            return true;
        }

        for (String group : exceptionGroups) {
            if (player.hasPermission("group." + group)) {
                return true;
            }
        }

        return false;
    }

    private long parseDuration(String durationStr) {
        try {
            long duration = 0;
            StringBuilder number = new StringBuilder();
            for (char c : durationStr.toLowerCase().toCharArray()) {
                if (Character.isDigit(c)) {
                    number.append(c);
                } else {
                    if (number.length() > 0) {
                        long value = Long.parseLong(number.toString());
                        switch (c) {
                            case 's':
                                duration += value * 1000L;
                                break;
                            case 'm':
                                duration += value * 60 * 1000L;
                                break;
                            case 'h':
                                duration += value * 60 * 60 * 1000L;
                                break;
                            case 'd':
                                duration += value * 24 * 60 * 60 * 1000L;
                                break;
                            default:
                                if (configManager.isConsoleLogsEnabled()) {
                                    plugin.getLogger().warning("Неизвестная единица времени в notification-cooldowns: " + c);
                                }
                        }
                        number = new StringBuilder();
                    }
                }
            }
            return duration;
        } catch (NumberFormatException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Неверный формат длительности в notification-cooldowns: " + durationStr);
            }
            return 0;
        }
    }
}