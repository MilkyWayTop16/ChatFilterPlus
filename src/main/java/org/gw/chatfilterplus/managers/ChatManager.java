package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.chat.ChatConflictDetector;
import org.gw.chatfilterplus.managers.chat.ChatDecisionStore;
import org.gw.chatfilterplus.managers.chat.ChatNotificationDispatcher;
import org.gw.chatfilterplus.utils.AntiSpamResult;
import org.gw.chatfilterplus.utils.PermissionCompat;

import java.util.*;
import java.util.function.Consumer;

@Getter
public class ChatManager implements Listener, ChatNotificationDispatcher.CapsPriorityPolicy {

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

    private final ChatDecisionStore decisionStore;
    private final ChatNotificationDispatcher notifications;
    private final ChatConflictDetector conflictDetector;

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

        this.decisionStore = new ChatDecisionStore(plugin);
        this.decisionStore.start();
        this.conflictDetector = new ChatConflictDetector(plugin);
        this.notifications = new ChatNotificationDispatcher(
                plugin, configManager, notificationManager, logCleanupManager,
                punishmentManager, linksManager, this);
    }

    public void clearCache() {
        if (configManager.getCacheMaxSize() > 0) {
            cacheManager.clearCache();
        }
    }

    public void shutdown() {
        decisionStore.shutdown();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        decisionStore.clearPlayer(event.getPlayer().getUniqueId());
    }

    public interface ChatEventAccess {
        boolean isCancelled();

        void setMessage(String plainMessage);

        void block();

        void uncancel();
    }

    public void onPlayerChat(AsyncPlayerChatEvent event, boolean readOnly) {
        onChat(event.getPlayer(), event.getMessage(), readOnly, legacyAccess(event));
    }

    public void enforcePlayerChat(AsyncPlayerChatEvent event) {
        enforceChat(event.getPlayer(), event.getMessage(), null, legacyAccess(event));
    }

    public void verifyPlayerChat(AsyncPlayerChatEvent event, org.bukkit.event.EventPriority ourPriority) {
        verifyChat(event.getPlayer(), event.getMessage(), event.isCancelled(),
                AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners(), ourPriority);
    }

    public void verifyChat(Player player,
                           String actualMessage,
                           boolean actualCancelled,
                           org.bukkit.plugin.RegisteredListener[] listeners,
                           org.bukkit.event.EventPriority ourPriority) {
        if (player == null) return;
        decisionStore.verifyChat(player.getUniqueId(), actualMessage, actualCancelled,
                conflictDetector, listeners, ourPriority);
    }

    public void onChat(Player player, String originalMessage, boolean readOnly, ChatEventAccess access) {
        if (access == null || player == null || originalMessage == null) return;

        if (!configManager.isCompatibilityAggressiveMode() && access.isCancelled()) {
            return;
        }

        if (!readOnly && decisionStore.tryReapplyExisting(
                player.getUniqueId(),
                originalMessage,
                access,
                configManager.isCompatibilityAggressiveMode())) {
            return;
        }

        final boolean[] modified = {false};
        final boolean[] blocked = {false};
        final String[] finalMessage = {originalMessage};

        handleMessage(player, originalMessage,
                message -> {
                    if (!readOnly) {
                        access.setMessage(message);
                        finalMessage[0] = message;
                        modified[0] = true;
                    }
                },
                cancel -> {
                    if (!readOnly && cancel) {
                        access.block();
                        blocked[0] = true;
                    }
                });

        if (!readOnly) {
            if (configManager.isCompatibilityAggressiveMode() && modified[0] && !blocked[0]) {
                access.uncancel();
            }
            decisionStore.putChat(player.getUniqueId(),
                    originalMessage, finalMessage[0], blocked[0], modified[0]);
        }
    }

    public void enforceChat(Player player, String currentMessage, String eventOriginalPlain, ChatEventAccess access) {
        if (player == null) return;
        decisionStore.enforceChat(
                player.getUniqueId(),
                currentMessage,
                eventOriginalPlain,
                access,
                configManager.isCompatibilityAggressiveMode());
    }

    private static ChatEventAccess legacyAccess(AsyncPlayerChatEvent event) {
        return new ChatEventAccess() {
            @Override
            public boolean isCancelled() {
                return event.isCancelled();
            }

            @Override
            public void setMessage(String plainMessage) {
                event.setMessage(plainMessage);
            }

            @Override
            public void block() {
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

            @Override
            public void uncancel() {
                event.setCancelled(false);
            }
        };
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
        decisionStore.putCommand(player.getUniqueId(),
                commandLabel, originalMessage, full, blocked[0], modified[0]);
    }

    public void handleCommandMessage(Player player, String originalMessage,
                                     Consumer<String> setFinalMessage,
                                     Runnable cancelCommand) {
        handleCommandMessage(player, "", originalMessage, setFinalMessage, cancelCommand);
    }

    public void enforceCommand(PlayerCommandPreprocessEvent event) {
        decisionStore.enforceCommand(event);
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
                scheduleAfterChat(() -> notifications.sendAntiSpamNotification(player, spamResult, originalMessage));

                if (!"character-flood-first".equals(spamResult.reason)) {
                    if (cancelEvent != null) cancelEvent.accept(true);
                    return;
                }
            }
        }

        MessageCacheManager.CachedMessage analyzed = cacheManager.analyzeAndCacheMessage(
                originalMessage,
                player.getUniqueId(),
                bypassed.contains(FilterType.BAD_WORDS),
                bypassed.contains(FilterType.LINKS),
                bypassed.contains(FilterType.BLOCKED_WORDS),
                bypassed.contains(FilterType.CAPS));

        final MessageCacheManager.CachedMessage cached =
                applyCapsFilterPriorityToCache(analyzed, originalMessage, player.getUniqueId(), bypassed);

        if (shouldBlockMessage(cached, bypassed)) {
            if (cancelEvent != null) cancelEvent.accept(true);
        } else {
            String finalMessage = determineFinalMessage(cached, bypassed);
            if (setFinalMessage != null && !finalMessage.equals(originalMessage)) {
                setFinalMessage.accept(finalMessage);
            }
        }

        if (hasAnyTrigger(cached)) {
            scheduleAfterChat(() -> notifications.sendAllNotifications(player, cached, bypassed, originalMessage));
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

    @Override
    public boolean isActive(MessageCacheManager.CachedMessage cached, FilterType type, Set<FilterType> bypassed) {
        return isTriggered(cached, type) && configManager.isFilterEnabled(type) && !bypassed.contains(type);
    }

    private String determineFinalMessage(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        String message = cached.getFilteredMessage();

        if (isActive(cached, FilterType.CAPS, bypassed) && shouldApplyCapsFix(cached, bypassed)) {
            message = capsManager.fixCaps(message);
        }
        return message;
    }

    private MessageCacheManager.CachedMessage applyCapsFilterPriorityToCache(
            MessageCacheManager.CachedMessage cached,
            String originalMessage,
            java.util.UUID playerId,
            Set<FilterType> bypassed) {

        if (cached == null || !cached.isCaps() || bypassed.contains(FilterType.CAPS)) {
            return cached;
        }

        boolean dropBad = isActive(cached, FilterType.BAD_WORDS, bypassed)
                && "caps".equalsIgnoreCase(configManager.getCapsFilterPriorityBadwords());
        boolean dropBlocked = isActive(cached, FilterType.BLOCKED_WORDS, bypassed)
                && "caps".equalsIgnoreCase(configManager.getCapsFilterPriorityBlockedwords());

        if (!dropBad && !dropBlocked) {
            return cached;
        }

        return cacheManager.analyzeAndCacheMessage(
                originalMessage,
                playerId,
                bypassed.contains(FilterType.BAD_WORDS) || dropBad,
                bypassed.contains(FilterType.LINKS),
                bypassed.contains(FilterType.BLOCKED_WORDS) || dropBlocked,
                bypassed.contains(FilterType.CAPS));
    }

    @Override
    public boolean shouldApplyCapsFix(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        return isCapsSideSelected(cached, bypassed,
                configManager.getCapsFilterPriorityBlockedwords(),
                configManager.getCapsFilterPriorityBadwords());
    }

    @Override
    public boolean shouldEmitCapsNotifications(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        return isCapsSideSelected(cached, bypassed,
                configManager.getCapsNotificationPriorityBlockedwords(),
                configManager.getCapsNotificationPriorityBadwords());
    }

    private boolean isCapsSideSelected(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed,
                                       String blockedWordsPriority, String badWordsPriority) {
        if (isActive(cached, FilterType.BLOCKED_WORDS, bypassed)) {
            return !"blockedwords".equalsIgnoreCase(blockedWordsPriority);
        }
        if (isActive(cached, FilterType.BAD_WORDS, bypassed)) {
            return !"badwords".equalsIgnoreCase(badWordsPriority);
        }
        return true;
    }

    @Override
    public boolean isWordFilterSideSelected(FilterType type, MessageCacheManager.CachedMessage cached,
                                            Set<FilterType> bypassed, boolean forNotifications) {
        if (type != FilterType.BAD_WORDS && type != FilterType.BLOCKED_WORDS) return true;
        if (!isActive(cached, FilterType.CAPS, bypassed)) return true;

        String priority = type == FilterType.BLOCKED_WORDS
                ? (forNotifications
                    ? configManager.getCapsNotificationPriorityBlockedwords()
                    : configManager.getCapsFilterPriorityBlockedwords())
                : (forNotifications
                    ? configManager.getCapsNotificationPriorityBadwords()
                    : configManager.getCapsFilterPriorityBadwords());

        return !"caps".equalsIgnoreCase(priority);
    }

    private boolean shouldBlockMessage(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed) {
        for (FilterType type : FilterType.PRIORITY_ORDER) {
            if (!isActive(cached, type, bypassed)) continue;
            if (type == FilterType.CAPS && !shouldApplyCapsFix(cached, bypassed)) continue;
            if (!isWordFilterSideSelected(type, cached, bypassed, false)) continue;
            return "block-and-notify".equalsIgnoreCase(configManager.getFilterMode(type));
        }
        return false;
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

    public void reload() {
        if (cacheManager != null) {
            cacheManager.reload();
        }
        decisionStore.clear();
    }
}
