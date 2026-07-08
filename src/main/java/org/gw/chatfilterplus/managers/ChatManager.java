package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.AntiSpamResult;
import org.gw.chatfilterplus.utils.PermissionCompat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Getter
public class ChatManager implements Listener {

    private static final long DECISION_TTL_MILLIS = 3_000L;
    private static final long NOTIFICATION_DELAY_TICKS = 1L;

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

    private final Map<UUID, PendingChatDecision> pendingChatDecisions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCommandDecision> pendingCommandDecisions = new ConcurrentHashMap<>();

    private final ThreadLocal<DateTimeFormatter> dateFormatter = ThreadLocal.withInitial(
            () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    );

    private static final class PendingChatDecision {
        final String originalMessage;
        final String finalMessage;
        final boolean blocked;
        final boolean modified;
        final long createdAt;

        PendingChatDecision(String originalMessage, String finalMessage, boolean blocked, boolean modified) {
            this.originalMessage = originalMessage;
            this.finalMessage = finalMessage;
            this.blocked = blocked;
            this.modified = modified;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DECISION_TTL_MILLIS;
        }

        boolean matches(String current) {
            return originalMessage.equals(current) || finalMessage.equals(current);
        }
    }

    private static final class PendingCommandDecision {
        final String commandLabel;
        final String originalArgs;
        final String finalFullMessage;
        final boolean blocked;
        final boolean modified;
        final long createdAt;

        PendingCommandDecision(String commandLabel, String originalArgs, String finalFullMessage,
                               boolean blocked, boolean modified) {
            this.commandLabel = commandLabel;
            this.originalArgs = originalArgs;
            this.finalFullMessage = finalFullMessage;
            this.blocked = blocked;
            this.modified = modified;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DECISION_TTL_MILLIS;
        }
    }

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
    }

    public void clearCache() {
        if (configManager.getCacheMaxSize() > 0) {
            cacheManager.clearCache();
        }
    }

    public void onPlayerChat(AsyncPlayerChatEvent event, boolean readOnly) {
        if (!configManager.isCompatibilityAggressiveMode() && event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String originalMessage = event.getMessage();
        final boolean[] modified = {false};
        final boolean[] blocked = {false};
        final String[] finalMessage = {originalMessage};

        handleMessage(player, originalMessage,
                message -> {
                    if (!readOnly) {
                        event.setMessage(message);
                        finalMessage[0] = message;
                        modified[0] = true;
                    }
                },
                cancel -> {
                    if (!readOnly && cancel) {
                        applyChatBlock(event);
                        blocked[0] = true;
                    }
                });

        if (!readOnly) {
            if (configManager.isCompatibilityAggressiveMode() && modified[0] && !blocked[0]) {
                event.setCancelled(false);
            }
            pendingChatDecisions.put(player.getUniqueId(),
                    new PendingChatDecision(originalMessage, finalMessage[0], blocked[0], modified[0]));
        }
    }

    public void enforcePlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingChatDecision decision = pendingChatDecisions.get(uuid);
        if (decision == null || decision.isExpired()) {
            if (decision != null) pendingChatDecisions.remove(uuid, decision);
            return;
        }

        if (!decision.matches(event.getMessage()) && !decision.originalMessage.equals(event.getMessage())) {
            return;
        }

        if (decision.blocked) {
            applyChatBlock(event);
            return;
        }

        if (decision.modified) {
            event.setMessage(decision.finalMessage);
            if (configManager.isCompatibilityAggressiveMode() && event.isCancelled()) {
                event.setCancelled(false);
            }
        }
    }

    private void applyChatBlock(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        try {
            event.getRecipients().clear();
        } catch (UnsupportedOperationException | ConcurrentModificationException ignored) {
            try {
                Set<Player> recipients = event.getRecipients();
                Iterator<Player> iterator = recipients.iterator();
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            } catch (Exception ignored2) {
            }
        }
    }

    public void handleCommandMessage(Player player, String commandLabel, String originalMessage,
                                     Consumer<String> setFinalMessage,
                                     Runnable cancelCommand) {
        final boolean[] blocked = {false};
        final boolean[] modified = {false};
        final String[] finalArgs = {originalMessage};

        handleMessage(player, originalMessage,
                message -> {
                    setFinalMessage.accept(message);
                    finalArgs[0] = message;
                    modified[0] = true;
                },
                cancel -> {
                    if (cancel) {
                        if (cancelCommand != null) cancelCommand.run();
                        blocked[0] = true;
                    }
                });

        String full = "/" + commandLabel + (finalArgs[0].isEmpty() ? "" : " " + finalArgs[0]);
        pendingCommandDecisions.put(player.getUniqueId(),
                new PendingCommandDecision(commandLabel, originalMessage, full, blocked[0], modified[0]));
    }

    public void handleCommandMessage(Player player, String originalMessage,
                                     Consumer<String> setFinalMessage,
                                     Runnable cancelCommand) {
        handleCommandMessage(player, "", originalMessage, setFinalMessage, cancelCommand);
    }

    public void enforceCommand(PlayerCommandPreprocessEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingCommandDecision decision = pendingCommandDecisions.get(uuid);
        if (decision == null || decision.isExpired()) {
            if (decision != null) pendingCommandDecisions.remove(uuid, decision);
            return;
        }

        String current = event.getMessage();
        if (decision.blocked) {
            event.setCancelled(true);
            return;
        }

        if (decision.modified && decision.finalFullMessage != null && !decision.finalFullMessage.isEmpty()) {
            String withoutSlash = current.startsWith("/") ? current.substring(1) : current;
            int space = withoutSlash.indexOf(' ');
            String label = space == -1 ? withoutSlash : withoutSlash.substring(0, space);
            if (decision.commandLabel.isEmpty() || decision.commandLabel.equalsIgnoreCase(label)) {
                event.setMessage(decision.finalFullMessage);
            }
        }
    }

    private void handleMessage(Player player, String originalMessage,
                               Consumer<String> setFinalMessage,
                               Consumer<Boolean> cancelEvent) {

        if (player == null) return;

        Set<FilterType> bypassed = collectBypassedFilters(player);

        if (bypassed.size() == FilterType.values().length) {
            if (setFinalMessage != null) setFinalMessage.accept(originalMessage);
            return;
        }

        if (!bypassed.contains(FilterType.ANTI_SPAM)) {
            AntiSpamResult spamResult = antiSpamManager.checkSpam(player, originalMessage);
            if (spamResult != null) {
                scheduleAfterChat(() -> sendAntiSpamNotification(player, spamResult, originalMessage));

                if (!"character-flood-first".equals(spamResult.reason)) {
                    if (cancelEvent != null) cancelEvent.accept(true);
                    return;
                }
            }
        }

        MessageCacheManager.CachedMessage cached = cacheManager.analyzeAndCacheMessage(
                originalMessage,
                player.getUniqueId(),
                bypassed.contains(FilterType.BAD_WORDS),
                bypassed.contains(FilterType.LINKS),
                bypassed.contains(FilterType.BLOCKED_WORDS),
                bypassed.contains(FilterType.CAPS));

        if (shouldBlockMessage(cached, bypassed)) {
            if (cancelEvent != null) cancelEvent.accept(true);
        } else {
            String finalMessage = determineFinalMessage(cached, bypassed);
            if (setFinalMessage != null && !finalMessage.equals(originalMessage)) {
                setFinalMessage.accept(finalMessage);
            }
        }

        if (hasAnyTrigger(cached)) {
            scheduleAfterChat(() -> sendAllNotifications(player, cached, bypassed, originalMessage));
        }
    }

    private boolean hasAnyTrigger(MessageCacheManager.CachedMessage cached) {
        for (FilterType type : FilterType.PRIORITY_ORDER) {
            if (isTriggered(cached, type)) return true;
        }
        return false;
    }

    private void scheduleAfterChat(Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, NOTIFICATION_DELAY_TICKS);
    }

    private boolean isTriggered(MessageCacheManager.CachedMessage cached, FilterType type) {
        return switch (type) {
            case BAD_WORDS -> !cached.getBadWords().isEmpty();
            case LINKS -> !cached.getLinks().isEmpty();
            case CAPS -> cached.isCaps();
            case BLOCKED_WORDS -> !cached.getBlockedWords().isEmpty();
            case ANTI_SPAM -> false;
        };
    }

    private boolean isActive(MessageCacheManager.CachedMessage cached, FilterType type, Set<FilterType> bypassed) {
        return isTriggered(cached, type) && configManager.isFilterEnabled(type) && !bypassed.contains(type);
    }

    private List<String> detectedItems(MessageCacheManager.CachedMessage cached, FilterType type, String originalMessage) {
        return switch (type) {
            case BAD_WORDS -> uniqueItems(cached.getBadWords());
            case LINKS -> uniqueItems(cached.getLinks());
            case BLOCKED_WORDS -> uniqueItems(cached.getBlockedWords());
            case CAPS -> List.of(originalMessage);
            case ANTI_SPAM -> List.of(originalMessage);
        };
    }

    private String determineFinalMessage(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        String message = cached.getFilteredMessage();

        if (isActive(cached, FilterType.CAPS, bypassed) && shouldApplyCapsFix(cached, bypassed)) {
            message = capsManager.fixCaps(message);
        }
        return message;
    }

    private boolean shouldApplyCapsFix(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        return capsYieldsTo(cached, bypassed,
                configManager.getCapsFilterPriorityBlockedwords(),
                configManager.getCapsFilterPriorityBadwords());
    }

    private boolean shouldSendCapsPlayerNotification(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        return capsYieldsTo(cached, bypassed,
                configManager.getCapsNotificationPriorityBlockedwords(),
                configManager.getCapsNotificationPriorityBadwords());
    }

    private boolean capsYieldsTo(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed,
                                 String blockedWordsPriority, String badWordsPriority) {
        if (isActive(cached, FilterType.BLOCKED_WORDS, bypassed)) {
            return !"blockedwords".equalsIgnoreCase(blockedWordsPriority);
        }
        if (isActive(cached, FilterType.BAD_WORDS, bypassed)) {
            return !"badwords".equalsIgnoreCase(badWordsPriority);
        }
        return true;
    }

    private boolean shouldBlockMessage(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        for (FilterType type : FilterType.PRIORITY_ORDER) {
            if (isActive(cached, type, bypassed)) {
                return "block-and-notify".equalsIgnoreCase(configManager.getFilterMode(type));
            }
        }
        return false;
    }

    private void sendAllNotifications(Player player, MessageCacheManager.CachedMessage cached,
                                      Set<FilterType> bypassed, String originalMessage) {

        if (player == null || !player.isOnline()) return;

        FilterType punishmentType = null;
        List<String> punishmentItems = null;

        for (FilterType type : FilterType.PRIORITY_ORDER) {
            if (!isActive(cached, type, bypassed)) continue;

            List<String> items = detectedItems(cached, type, originalMessage);
            sendFilterNotification(type, player, items, originalMessage);

            if (type == FilterType.CAPS && shouldSendCapsPlayerNotification(cached, bypassed)) {
                notificationManager.notifyPlayer(player, FilterType.CAPS, "[CAPS]");
            }

            if (punishmentType == null) {
                punishmentType = type;
                punishmentItems = items;
            }
        }

        if (punishmentType != null) {
            punishmentManager.handlePunishment(player, punishmentType, punishmentItems);
        }
    }

    private Set<FilterType> collectBypassedFilters(Player player) {
        Set<FilterType> bypassed = EnumSet.noneOf(FilterType.class);
        for (FilterType type : FilterType.values()) {
            if (!configManager.isFilterEnabled(type) || isPlayerBypassingFilter(player, type)) {
                bypassed.add(type);
            }
        }
        return bypassed;
    }

    private boolean isPlayerBypassingFilter(Player player, FilterType type) {
        if (PermissionCompat.hasChatFilterBypass(player, type)) return true;
        if (configManager.getExceptionPlayers(type).contains(player.getName())) return true;

        for (String group : configManager.getExceptionGroups(type)) {
            if (player.hasPermission("group." + group)) return true;
        }
        return false;
    }

    private List<String> uniqueItems(List<String> items) {
        if (items.size() <= 1) return items;
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    private void sendFilterNotification(FilterType type, Player player, List<String> items, String originalMessage) {
        if (player == null || !player.isOnline()) return;

        plugin.log(type.getConsoleLabel() + " от &#ffff00" + player.getName() + " &f→ &#ffff00" + String.join(", ", items));
        logToFile(player.getName(), items, type);

        notificationManager.notifyAdmins(player, type, items);
        notificationManager.notifyConsole(player, type, items);
        notificationManager.notifyDiscord(player, type, items);

        if (type != FilterType.CAPS) {
            notificationManager.notifyPlayer(player, type, items.isEmpty() ? "" : items.get(0));
        }
    }

    private void sendAntiSpamNotification(Player player, AntiSpamResult result, String originalMessage) {
        if (player == null || !player.isOnline()) return;

        List<String> items = List.of(originalMessage);

        plugin.log(FilterType.ANTI_SPAM.getConsoleLabel() + " от &#ffff00" + player.getName() + " &f→ &#ffff00" + originalMessage);
        logToFile(player.getName(), items, FilterType.ANTI_SPAM);

        notificationManager.notifyAdmins(player, FilterType.ANTI_SPAM, items);
        notificationManager.notifyConsole(player, FilterType.ANTI_SPAM, items);
        notificationManager.notifyDiscord(player, FilterType.ANTI_SPAM, items);

        String subPath = switch (result.reason) {
            case "similar-message-cooldown" -> "similar-message-cooldown";
            case "character-flood", "character-flood-first" -> "character-flood";
            default -> "general-cooldown";
        };

        Map<String, String> placeholders = Map.of("remaining", String.valueOf(result.remainingSeconds));
        configManager.executeActionsFromAntiSpam(player, subPath, placeholders);
        punishmentManager.handlePunishment(player, FilterType.ANTI_SPAM, items);
    }

    private void logToFile(String playerName, List<String> items, FilterType type) {
        if (items.isEmpty()) return;
        logCleanupManager.appendLog(type, buildLogMessage(playerName, items, type));
    }

    private String buildLogMessage(String playerName, List<String> items, FilterType type) {
        String template = type.config(configManager)
                .getString(type.logPath("message"), type.getDefaultLogTemplate());

        String joined = String.join(", ", items);
        String linksFormatted = type == FilterType.LINKS ? linksManager.getFormattedLinks(items) : joined;

        return template
                .replace("{player}", playerName)
                .replace("{time}", dateFormatter.get().format(LocalDateTime.now()))
                .replace("{words}", joined)
                .replace("{links}", linksFormatted)
                .replace("{original-message}", joined)
                .replace("{reason}", items.get(0));
    }

    public void reload() {
        clearCache();
        pendingChatDecisions.clear();
        pendingCommandDecisions.clear();
    }
}
