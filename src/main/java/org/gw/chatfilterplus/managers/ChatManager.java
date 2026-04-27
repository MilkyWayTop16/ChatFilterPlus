package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.AntiSpamResult;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

@Getter
public class ChatManager implements Listener {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final WordsManager wordsManager;
    private final LinksManager linksManager;
    private final CapsManager capsManager;
    private final NotificationManager notificationManager;
    private final LogCleanupManager logCleanupManager;
    private final PunishmentManager punishmentManager;
    private final MessageCacheManager cacheManager;
    private final BlockedWordsManager blockedWordsManager;
    private final AntiSpamManager antiSpamManager;

    private final File badWordsLogFile;
    private final File linksLogFile;
    private final SimpleDateFormat dateFormat;

    public ChatManager(ChatFilterPlus plugin, ConfigManager configManager, WordsManager wordsManager,
                       LinksManager linksManager, CapsManager capsManager,
                       BlockedWordsManager blockedWordsManager,
                       NotificationManager notificationManager, LogCleanupManager logCleanupManager,
                       PunishmentManager punishmentManager, MessageCacheManager cacheManager,
                       AntiSpamManager antiSpamManager) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.wordsManager = wordsManager;
        this.linksManager = linksManager;
        this.capsManager = capsManager;
        this.blockedWordsManager = blockedWordsManager;
        this.notificationManager = notificationManager;
        this.logCleanupManager = logCleanupManager;
        this.punishmentManager = punishmentManager;
        this.cacheManager = cacheManager;
        this.antiSpamManager = antiSpamManager;

        this.badWordsLogFile = new File(plugin.getDataFolder() + File.separator + "logs", "badwords-logs.txt");
        this.linksLogFile = new File(plugin.getDataFolder() + File.separator + "logs", "links-logs.txt");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        initializeLogFiles();
    }

    private void initializeLogFiles() {
        try {
            File logsDir = badWordsLogFile.getParentFile();
            if (!logsDir.exists()) logsDir.mkdirs();

            if (!badWordsLogFile.exists()) badWordsLogFile.createNewFile();
            if (!linksLogFile.exists()) linksLogFile.createNewFile();
        } catch (Exception e) {
            plugin.console("&#FF5D00Не удалось создать файлы логов...");
        }
    }

    public void clearCache() {
        if (configManager.getCacheMaxSize() > 0) {
            cacheManager.clearCache();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        handleChat(event.getPlayer(), event.getMessage(), event::setMessage, event::setCancelled);
    }

    public void handleCommandMessage(Player player, String originalMessage,
                                     java.util.function.Consumer<String> setFinalMessage) {

        boolean bypassBadWords = isPlayerBypassingFilter("badwords", player);
        boolean bypassLinks = isPlayerBypassingFilter("links", player);
        boolean bypassCaps = isPlayerBypassingFilter("caps", player);
        boolean bypassBlockedWords = isPlayerBypassingFilter("blockedwords", player);

        if (bypassBadWords && bypassLinks && bypassCaps && bypassBlockedWords) {
            setFinalMessage.accept(originalMessage);
            return;
        }

        MessageCacheManager.CachedMessage cached = processMessage(player, originalMessage,
                bypassBadWords, bypassLinks, bypassCaps, bypassBlockedWords);

        String finalMessage = determineFinalMessage(cached, bypassBadWords, bypassLinks, bypassCaps, bypassBlockedWords);

        if (!finalMessage.equals(originalMessage)) {
            setFinalMessage.accept(finalMessage);
        }
    }

    private void handleChat(Player player, String originalMessage,
                            java.util.function.Consumer<String> setMessage,
                            java.util.function.Consumer<Boolean> cancelEvent) {

        boolean bypassBadWords = isPlayerBypassingFilter("badwords", player);
        boolean bypassLinks = isPlayerBypassingFilter("links", player);
        boolean bypassCaps = isPlayerBypassingFilter("caps", player);
        boolean bypassBlockedWords = isPlayerBypassingFilter("blockedwords", player);
        boolean bypassAntiSpam = isPlayerBypassingFilter("antispam", player);

        if (bypassBadWords && bypassLinks && bypassCaps && bypassBlockedWords && bypassAntiSpam) {
            return;
        }

        if (!bypassAntiSpam) {
            AntiSpamResult spamResult = antiSpamManager.checkSpam(player, originalMessage);
            if (spamResult != null) {
                sendAntiSpamNotification(player, spamResult, originalMessage);

                if (!"character-flood-first".equals(spamResult.reason)) {
                    cancelEvent.accept(true);
                    return;
                }
            }
        }

        MessageCacheManager.CachedMessage cached = processMessage(player, originalMessage,
                bypassBadWords, bypassLinks, bypassCaps, bypassBlockedWords);

        String finalMessage = determineFinalMessage(cached, bypassBadWords, bypassLinks, bypassCaps, bypassBlockedWords);

        if (shouldBlockMessage(cached, bypassBadWords, bypassLinks, bypassCaps, bypassBlockedWords)) {
            cancelEvent.accept(true);
        } else if (!finalMessage.equals(originalMessage)) {
            setMessage.accept(finalMessage);
        }
    }

    private MessageCacheManager.CachedMessage processMessage(Player player, String originalMessage,
                                                             boolean bypassBadWords, boolean bypassLinks,
                                                             boolean bypassCaps, boolean bypassBlockedWords) {

        MessageCacheManager.CachedMessage cached = cacheManager.analyzeAndCacheMessage(
                originalMessage, bypassBadWords, bypassLinks, bypassBlockedWords, bypassCaps);

        sendAllNotifications(player, cached, bypassBadWords, bypassLinks, bypassCaps, bypassBlockedWords, originalMessage);

        return cached;
    }

    private String determineFinalMessage(MessageCacheManager.CachedMessage cached,
                                         boolean bypassBadWords, boolean bypassLinks,
                                         boolean bypassCaps, boolean bypassBlockedWords) {

        String message = cached.getFilteredMessage();

        if (cached.isCaps() && configManager.isCapsFilterEnabled() && !bypassCaps) {
            if (shouldApplyCapsFix(cached, bypassBadWords, bypassBlockedWords)) {
                message = capsManager.fixCaps(message);
            }
        }
        return message;
    }

    private boolean shouldApplyCapsFix(MessageCacheManager.CachedMessage cached,
                                       boolean bypassBadWords, boolean bypassBlockedWords) {

        if (!cached.getBlockedWords().isEmpty() && configManager.isBlockedWordsFilterEnabled() && !bypassBlockedWords) {
            return !"blockedwords".equalsIgnoreCase(configManager.getCapsFilterPriorityBlockedwords());
        }
        if (!cached.getBadWords().isEmpty() && configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            return !"badwords".equalsIgnoreCase(configManager.getCapsFilterPriorityBadwords());
        }
        return true;
    }

    private boolean shouldBlockMessage(MessageCacheManager.CachedMessage cached,
                                       boolean bypassBadWords, boolean bypassLinks,
                                       boolean bypassCaps, boolean bypassBlockedWords) {

        if (!cached.getBlockedWords().isEmpty() && configManager.isBlockedWordsFilterEnabled() && !bypassBlockedWords) {
            return "block-and-notify".equalsIgnoreCase(configManager.getBlockedWordsFilterMode());
        }
        if (!cached.getBadWords().isEmpty() && configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            return "block-and-notify".equalsIgnoreCase(configManager.getBadWordsFilterMode());
        }
        if (!cached.getLinks().isEmpty() && configManager.isLinksFilterEnabled() && !bypassLinks) {
            return "block-and-notify".equalsIgnoreCase(configManager.getLinksFilterMode());
        }
        if (cached.isCaps() && configManager.isCapsFilterEnabled() && !bypassCaps) {
            return "block-and-notify".equalsIgnoreCase(configManager.getCapsFilterMode());
        }
        return false;
    }

    private void sendAntiSpamNotification(Player player, AntiSpamResult result, String originalMessage) {
        plugin.log("Обнаружен спам от &#ffff00" + player.getName() + " &f→ &#ffff00" + originalMessage);

        List<String> items = List.of(originalMessage);
        logToFile(player.getName(), items, FilterType.ANTI_SPAM);

        Map<String, String> placeholders = Map.of("remaining", String.valueOf(result.remainingSeconds));

        notificationManager.notifyAdmins(player, FilterType.ANTI_SPAM, items);
        notificationManager.notifyConsole(player, FilterType.ANTI_SPAM, items);
        notificationManager.notifyDiscord(player, FilterType.ANTI_SPAM, items);

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                configManager.executeActionsFromAntiSpam(player, "player." + result.reason, placeholders), 3L);

        punishmentManager.handlePunishment(player, FilterType.ANTI_SPAM, items);
    }

    private void sendAllNotifications(Player player, MessageCacheManager.CachedMessage cached,
                                      boolean bypassBadWords, boolean bypassLinks,
                                      boolean bypassCaps, boolean bypassBlockedWords, String originalMessage) {

        if (!cached.getBlockedWords().isEmpty() && configManager.isBlockedWordsFilterEnabled() && !bypassBlockedWords) {
            sendBlockedWordsNotification(player, cached.getBlockedWords());
        }
        if (!cached.getBadWords().isEmpty() && configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            sendBadWordsNotification(player, cached.getBadWords());
        }
        if (cached.isCaps() && configManager.isCapsFilterEnabled() && !bypassCaps) {
            sendCapsStaffNotifications(player, originalMessage);

            if (shouldSendCapsPlayerNotification(cached, bypassBadWords, bypassBlockedWords)) {
                sendCapsPlayerNotification(player);
            }
        }
        if (!cached.getLinks().isEmpty() && configManager.isLinksFilterEnabled() && !bypassLinks) {
            sendLinksNotification(player, cached.getLinks());
        }
    }

    private boolean shouldSendCapsPlayerNotification(MessageCacheManager.CachedMessage cached,
                                                     boolean bypassBadWords, boolean bypassBlockedWords) {
        if (!cached.getBlockedWords().isEmpty() && configManager.isBlockedWordsFilterEnabled() && !bypassBlockedWords) {
            return !"blockedwords".equalsIgnoreCase(configManager.getCapsNotificationPriorityBlockedwords());
        }
        if (!cached.getBadWords().isEmpty() && configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            return !"badwords".equalsIgnoreCase(configManager.getCapsNotificationPriorityBadwords());
        }
        return true;
    }

    private void sendCapsStaffNotifications(Player player, String originalMessage) {
        plugin.log("Обнаружен капс от &#ffff00" + player.getName() + " &f→ &#ffff00" + originalMessage);

        List<String> items = List.of(originalMessage);
        logToFile(player.getName(), items, FilterType.CAPS);

        notificationManager.notifyAdmins(player, FilterType.CAPS, items);
        notificationManager.notifyConsole(player, FilterType.CAPS, items);
        notificationManager.notifyDiscord(player, FilterType.CAPS, items);

        punishmentManager.handlePunishment(player, FilterType.CAPS, List.of("[CAPS]"));
    }

    private void sendCapsPlayerNotification(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                notificationManager.notifyPlayer(player, FilterType.CAPS, "[CAPS]"), 3L);
    }

    private void sendBlockedWordsNotification(Player player, List<String> words) {
        List<String> uniqueWords = new ArrayList<>(new LinkedHashSet<>(words));

        plugin.log("Обнаружено запрещённое слово от &#ffff00" + player.getName() + " &f→ &#ffff00" + String.join(", ", uniqueWords));
        logToFile(player.getName(), uniqueWords, FilterType.BLOCKED_WORDS);

        notificationManager.notifyAdmins(player, FilterType.BLOCKED_WORDS, uniqueWords);
        notificationManager.notifyConsole(player, FilterType.BLOCKED_WORDS, uniqueWords);
        notificationManager.notifyDiscord(player, FilterType.BLOCKED_WORDS, uniqueWords);
        punishmentManager.handlePunishment(player, FilterType.BLOCKED_WORDS, uniqueWords);

        Bukkit.getScheduler().runTask(plugin, () ->
                notificationManager.notifyPlayer(player, FilterType.BLOCKED_WORDS, uniqueWords.get(0)));
    }

    private void sendBadWordsNotification(Player player, List<String> words) {
        List<String> uniqueWords = new ArrayList<>(new LinkedHashSet<>(words));

        plugin.log("Обнаружен мат от &#ffff00" + player.getName() + " &f→ &#ffff00" + String.join(", ", uniqueWords));
        logToFile(player.getName(), uniqueWords, FilterType.BAD_WORDS);

        notificationManager.notifyAdmins(player, FilterType.BAD_WORDS, uniqueWords);
        notificationManager.notifyConsole(player, FilterType.BAD_WORDS, uniqueWords);
        notificationManager.notifyDiscord(player, FilterType.BAD_WORDS, uniqueWords);
        punishmentManager.handlePunishment(player, FilterType.BAD_WORDS, uniqueWords);

        Bukkit.getScheduler().runTask(plugin, () ->
                notificationManager.notifyPlayer(player, FilterType.BAD_WORDS, uniqueWords.get(0)));
    }

    private void sendLinksNotification(Player player, List<String> links) {
        plugin.log("Обнаружена ссылка от &#ffff00" + player.getName() + " &f→ &#ffff00" + String.join(", ", links));

        if (linksLogFile.exists()) {
            logToFile(player.getName(), links, FilterType.LINKS);
        }

        notificationManager.notifyAdmins(player, FilterType.LINKS, links);
        notificationManager.notifyConsole(player, FilterType.LINKS, links);
        notificationManager.notifyDiscord(player, FilterType.LINKS, links);
        punishmentManager.handlePunishment(player, FilterType.LINKS, links);

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                notificationManager.notifyPlayer(player, FilterType.LINKS, links.get(0)), 3L);
    }

    private void logToFile(String playerName, List<String> items, FilterType type) {
        if (items.isEmpty()) return;

        String message = buildLogMessage(playerName, items, type);

        switch (type) {
            case BAD_WORDS -> logCleanupManager.appendAndCleanBadWordsLog(message);
            case LINKS -> logCleanupManager.appendAndCleanLinksLog(message);
            case CAPS -> logCleanupManager.appendAndCleanCapsLog(message);
            case BLOCKED_WORDS -> logCleanupManager.appendAndCleanBlockedWordsLog(message);
            case ANTI_SPAM -> logCleanupManager.appendAndCleanAntiSpamLog(message);
        }
    }

    private String buildLogMessage(String playerName, List<String> items, FilterType type) {
        String template = switch (type) {
            case BAD_WORDS -> configManager.getBadWordsConfig().getString("logs.file.bad-words.message");
            case LINKS -> configManager.getLinksConfig().getString("logs.file.links.message");
            case CAPS -> configManager.getCapsConfig().getString("logs.file.caps.message", "[{time}] Игрок {player} использовал капс: {original-message}");
            case BLOCKED_WORDS -> configManager.getBlockedWordsConfig().getString("logs.file.blocked-words.message", "[{time}] Игрок {player} использовал запрещённое слово: {words}");
            case ANTI_SPAM -> configManager.getAntiSpamConfig().getString("logs.file.anti-spam.message", "[{time}] Игрок {player} пойман на спаме: {original-message}");
        };

        String linksFormatted = type == FilterType.LINKS ? linksManager.getFormattedLinks(items) : String.join(", ", items);

        return template
                .replace("{player}", playerName)
                .replace("{time}", dateFormat.format(new Date()))
                .replace("{words}", String.join(", ", items))
                .replace("{links}", linksFormatted)
                .replace("{original-message}", items.isEmpty() ? "" : String.join(", ", items));
    }

    private boolean isPlayerBypassingFilter(String filterName, Player player) {
        String perm = "chatfilterplus.bypass.chatfilter." + filterName;

        if (player.hasPermission(perm)) {
            return true;
        }

        if (player.isOp()) {
            return false;
        }

        List<String> exceptionPlayers;
        List<String> exceptionGroups;

        switch (filterName) {
            case "badwords" -> {
                exceptionPlayers = configManager.getBadWordsExceptionPlayers();
                exceptionGroups = configManager.getBadWordsExceptionGroups();
            }
            case "links" -> {
                exceptionPlayers = configManager.getLinksExceptionPlayers();
                exceptionGroups = configManager.getLinksExceptionGroups();
            }
            case "caps" -> {
                exceptionPlayers = configManager.getCapsExceptionPlayers();
                exceptionGroups = configManager.getCapsExceptionGroups();
            }
            case "blockedwords" -> {
                exceptionPlayers = configManager.getBlockedWordsExceptionPlayers();
                exceptionGroups = configManager.getBlockedWordsExceptionGroups();
            }
            case "antispam" -> {
                exceptionPlayers = configManager.getAntiSpamExceptionPlayers();
                exceptionGroups = configManager.getAntiSpamExceptionGroups();
            }
            default -> {
                return false;
            }
        }

        if (exceptionPlayers.contains(player.getName())) {
            return true;
        }

        for (String group : exceptionGroups) {
            if (player.hasPermission("group." + group)) {
                return true;
            }
        }
        return false;
    }

    public void reload() {
        clearCache();
    }
}