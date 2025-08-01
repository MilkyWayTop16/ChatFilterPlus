package org.gw.chatfilterplus.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatManager implements Listener {
    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final WordsManager wordsManager;
    private final LinksManager linksManager;
    private final NotificationManager notificationManager;
    private final LogCleanupManager logCleanupManager;
    private final PunishmentManager punishmentManager;
    private final MessageCacheManager cacheManager;
    private final File badWordsLogFile;
    private final File linksLogFile;
    private final SimpleDateFormat dateFormat;

    public ChatManager(ChatFilterPlus plugin, ConfigManager configManager, WordsManager wordsManager,
                       LinksManager linksManager, NotificationManager notificationManager,
                       LogCleanupManager logCleanupManager, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.wordsManager = wordsManager;
        this.linksManager = linksManager;
        this.notificationManager = notificationManager;
        this.logCleanupManager = logCleanupManager;
        this.punishmentManager = punishmentManager;
        this.cacheManager = new MessageCacheManager(plugin, configManager, wordsManager, linksManager, 1000);
        this.badWordsLogFile = new File(plugin.getDataFolder(), "badwords-logs.txt");
        this.linksLogFile = new File(plugin.getDataFolder(), "links-logs.txt");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        initializeLogFiles();
    }

    private void initializeLogFiles() {
        try {
            if (!badWordsLogFile.exists()) {
                badWordsLogFile.getParentFile().mkdirs();
                badWordsLogFile.createNewFile();
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Создан файл логов badwords-logs.txt.");
                }
            }
            if (!linksLogFile.exists()) {
                linksLogFile.getParentFile().mkdirs();
                linksLogFile.createNewFile();
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Создан файл логов links-logs.txt.");
                }
            }
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Не удалось создать файлы логов: " + e.getMessage());
            }
        }
    }

    public void updateWordsMap() {
        wordsManager.loadWords();
        cacheManager.clearCache();
        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().info("Обновлён wordsMap, кэш очищен.");
        }
    }

    public void updateLinkPattern() {
        linksManager.loadLinkPattern();
        cacheManager.clearCache();
        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().info("Обновлён linkPattern, кэш очищен.");
        }
    }

    public void clearCache() {
        cacheManager.clearCache();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String originalMessage = event.getMessage();
        long eventTime = System.currentTimeMillis();

        boolean bypassBadWords = player.hasPermission("chatfilterplus.bypass.chatfilter.badwords");
        boolean bypassLinks = player.hasPermission("chatfilterplus.bypass.chatfilter.links");

        if (bypassBadWords && bypassLinks) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Игрок " + player.getName() + " обошёл фильтры с сообщением: " + originalMessage);
            }
            return;
        }

        MessageCacheManager.CachedMessage cachedMessage = cacheManager.analyzeAndCacheMessage(originalMessage, bypassBadWords, bypassLinks);
        String filteredMessage = cachedMessage.getFilteredMessage();
        List<String> badWords = cachedMessage.getBadWords();
        List<String> links = cachedMessage.getLinks();
        boolean containsBadWord = !badWords.isEmpty();
        boolean containsLink = !links.isEmpty();

        if (containsBadWord && configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            if ("block-and-notify".equalsIgnoreCase(configManager.getBadWordsFilterMode())) {
                event.setCancelled(true);
            } else {
                event.setMessage(filteredMessage);
            }
        } else if (containsLink && configManager.isLinksFilterEnabled() && !bypassLinks) {
            if ("block-and-notify".equalsIgnoreCase(configManager.getLinksFilterMode())) {
                event.setCancelled(true);
            } else {
                event.setMessage(filteredMessage);
            }
        } else {
            event.setMessage(filteredMessage);
        }

        if (containsBadWord && configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Обнаружен мат от игрока " + player.getName() + ": " + originalMessage + " (слова: " + String.join(", ", badWords) + ")");
            }
            if (configManager.isBadWordsFileLogsEnabled()) {
                logBadWordsToFile(player.getName(), badWords, eventTime);
            }
            if (configManager.isBadWordsNotificationsEnabled()) {
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    notificationManager.notifyPlayerBadWords(player, badWords.get(0));
                    notificationManager.notifyAdminsBadWords(player, badWords);
                    notificationManager.notifyConsoleBadWords(player, badWords);
                }, 1L);
            }
            punishmentManager.handleBadWordsPunishment(player, badWords);
        }

        if (containsLink && configManager.isLinksFilterEnabled() && !bypassLinks) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Обнаружена ссылка от игрока " + player.getName() + ": " + originalMessage + " (ссылки: " + String.join(", ", links) + ")");
            }
            if (configManager.isLinksFileLogsEnabled()) {
                logLinksToFile(player.getName(), links, eventTime);
            }
            if (configManager.isLinksNotificationsEnabled()) {
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    notificationManager.notifyPlayerLinks(player, links.get(0));
                    notificationManager.notifyAdminsLinks(player, links);
                    notificationManager.notifyConsoleLinks(player, links);
                }, 1L);
            }
            punishmentManager.handleLinksPunishment(player, links);
        }

        if (!containsBadWord && !containsLink && configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().info("Нарушения не обнаружены в сообщении от " + player.getName() + ": " + originalMessage);
        }
    }

    private void logBadWordsToFile(String playerName, List<String> badWords, long eventTime) {
        if (badWords.isEmpty()) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Попытка логирования пустого списка матов для игрока " + playerName);
            }
            return;
        }

        String formattedWords;
        if (badWords.size() == 1) {
            formattedWords = configManager.getBadWordsFileSingleWordTemplate().replace("{word}", badWords.get(0));
        } else {
            StringBuilder wordsBuilder = new StringBuilder();
            for (int i = 0; i < badWords.size(); i++) {
                wordsBuilder.append(configManager.getBadWordsFileWordTemplate().replace("{word}", badWords.get(i)));
                if (i < badWords.size() - 1) {
                    wordsBuilder.append(configManager.getBadWordsFileWordsSeparator());
                }
            }
            formattedWords = wordsBuilder.toString();
        }

        String message = configManager.getBadWordsFileLogMessage()
                .replace("{player}", playerName)
                .replace("{time}", dateFormat.format(new Date(eventTime)))
                .replace("{words}", formattedWords);

        logCleanupManager.appendAndCleanBadWordsLog(message);
    }

    private void logLinksToFile(String playerName, List<String> links, long eventTime) {
        if (links.isEmpty()) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Попытка логирования пустого списка ссылок для игрока " + playerName);
            }
            return;
        }

        String formattedLinks;
        if (links.size() == 1) {
            formattedLinks = configManager.getLinksFileSingleWordTemplate().replace("{link}", links.get(0));
        } else {
            StringBuilder linksBuilder = new StringBuilder();
            for (int i = 0; i < links.size(); i++) {
                linksBuilder.append(configManager.getLinksFileWordTemplate().replace("{link}", links.get(i)));
                if (i < links.size() - 1) {
                    linksBuilder.append(configManager.getLinksFileWordsSeparator());
                }
            }
            formattedLinks = linksBuilder.toString();
        }

        String message = configManager.getLinksFileLogMessage()
                .replace("{player}", playerName)
                .replace("{time}", dateFormat.format(new Date(eventTime)))
                .replace("{links}", formattedLinks);

        logCleanupManager.appendAndCleanLinksLog(message);
    }
}