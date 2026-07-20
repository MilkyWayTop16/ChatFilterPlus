package org.gw.chatfilterplus.managers.chat;

import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.ConfigManager;
import org.gw.chatfilterplus.managers.FilterType;
import org.gw.chatfilterplus.managers.LinksManager;
import org.gw.chatfilterplus.managers.LogCleanupManager;
import org.gw.chatfilterplus.managers.MessageCacheManager;
import org.gw.chatfilterplus.managers.NotificationManager;
import org.gw.chatfilterplus.managers.PunishmentManager;
import org.gw.chatfilterplus.utils.AntiSpamResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChatNotificationDispatcher {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final NotificationManager notificationManager;
    private final LogCleanupManager logCleanupManager;
    private final PunishmentManager punishmentManager;
    private final LinksManager linksManager;
    private final CapsPriorityPolicy capsPriority;

    private final ThreadLocal<DateTimeFormatter> dateFormatter = ThreadLocal.withInitial(
            () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    );

    public ChatNotificationDispatcher(ChatFilterPlus plugin,
                                      ConfigManager configManager,
                                      NotificationManager notificationManager,
                                      LogCleanupManager logCleanupManager,
                                      PunishmentManager punishmentManager,
                                      LinksManager linksManager,
                                      CapsPriorityPolicy capsPriority) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.notificationManager = notificationManager;
        this.logCleanupManager = logCleanupManager;
        this.punishmentManager = punishmentManager;
        this.linksManager = linksManager;
        this.capsPriority = capsPriority;
    }

    public void sendAllNotifications(Player player,
                                     MessageCacheManager.CachedMessage cached,
                                     Set<FilterType> bypassed,
                                     String originalMessage) {
        if (player == null || !player.isOnline() || cached == null) return;

        FilterType punishmentType = null;
        List<String> punishmentItems = null;

        for (FilterType type : FilterType.PRIORITY_ORDER) {
            if (!capsPriority.isActive(cached, type, bypassed)) continue;

            boolean filterApplies = (type != FilterType.CAPS || capsPriority.shouldApplyCapsFix(cached, bypassed))
                    && capsPriority.isWordFilterSideSelected(type, cached, bypassed, false);
            boolean notifyApplies = (type != FilterType.CAPS || capsPriority.shouldEmitCapsNotifications(cached, bypassed))
                    && capsPriority.isWordFilterSideSelected(type, cached, bypassed, true);

            List<String> items = detectedItems(cached, type, originalMessage);

            if (notifyApplies) {
                sendFilterNotification(type, player, items);
                if (type == FilterType.CAPS
                        && !punishmentManager.isNotificationOnCooldown(player.getName(), type, "player")) {
                    notificationManager.notifyPlayer(player, FilterType.CAPS, "[CAPS]");
                }
            }

            if (punishmentType == null && filterApplies) {
                punishmentType = type;
                punishmentItems = items;
            }
        }

        if (punishmentType != null) {
            punishmentManager.handlePunishment(player, punishmentType, punishmentItems);
        }
    }

    public void sendAntiSpamNotification(Player player, AntiSpamResult result, String originalMessage) {
        if (player == null || !player.isOnline() || result == null) return;

        List<String> items = List.of(originalMessage);
        String playerName = player.getName();
        FilterType type = FilterType.ANTI_SPAM;

        plugin.log(type.getConsoleLabel() + " от &#ffff00" + playerName + " &f→ &#ffff00" + originalMessage);

        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "file")) {
            logToFile(playerName, items, type);
        }
        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "admin")) {
            notificationManager.notifyAdmins(player, type, items);
        }
        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "console")) {
            notificationManager.notifyConsole(player, type, items);
        }
        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "discord")) {
            notificationManager.notifyDiscord(player, type, items);
        }

        String subPath = switch (result.reason) {
            case "similar-message-cooldown" -> "similar-message-cooldown";
            case "character-flood", "character-flood-first" -> "character-flood";
            default -> "general-cooldown";
        };

        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "player")) {
            Map<String, String> placeholders = Map.of("remaining", String.valueOf(result.remainingSeconds));
            configManager.executeActionsFromAntiSpam(player, subPath, placeholders);
        }
        punishmentManager.handlePunishment(player, type, items);
    }

    private void sendFilterNotification(FilterType type, Player player, List<String> items) {
        if (player == null || !player.isOnline()) return;

        String playerName = player.getName();
        plugin.log(type.getConsoleLabel() + " от &#ffff00" + playerName + " &f→ &#ffff00" + String.join(", ", items));

        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "file")) {
            logToFile(playerName, items, type);
        }
        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "admin")) {
            notificationManager.notifyAdmins(player, type, items);
        }
        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "console")) {
            notificationManager.notifyConsole(player, type, items);
        }
        if (!punishmentManager.isNotificationOnCooldown(playerName, type, "discord")) {
            notificationManager.notifyDiscord(player, type, items);
        }
        if (type != FilterType.CAPS
                && !punishmentManager.isNotificationOnCooldown(playerName, type, "player")) {
            notificationManager.notifyPlayer(player, type, items.isEmpty() ? "" : items.get(0));
        }
    }

    private void logToFile(String playerName, List<String> items, FilterType type) {
        if (items.isEmpty()) return;
        if (!type.config(configManager).getBoolean(type.logPath("enabled"), true)) return;
        logCleanupManager.appendLog(type, buildLogMessage(playerName, items, type));
    }

    private String buildLogMessage(String playerName, List<String> items, FilterType type) {
        String template = type.config(configManager)
                .getString(type.logPath("message"), type.getDefaultLogTemplate());

        String wordsFormatted = formatLogItems(type, items, "words-format", "word-template", "single-word-template", "{word}");
        String linksFormatted = type == FilterType.LINKS
                ? linksManager.getFormattedLinks(items)
                : formatLogItems(type, items, "links-format", "link-template", "single-link-template", "{link}");
        String originalFormatted = formatLogItems(type, items, "caps-format", "message-template", "single-message-template", "{original-message}");
        if (originalFormatted.isEmpty()) {
            originalFormatted = String.join(", ", items);
        }

        return template
                .replace("{player}", playerName)
                .replace("{time}", dateFormatter.get().format(LocalDateTime.now()))
                .replace("{words}", wordsFormatted)
                .replace("{links}", linksFormatted)
                .replace("{original-message}", originalFormatted)
                .replace("{reason}", items.isEmpty() ? "" : items.get(0));
    }

    private String formatLogItems(FilterType type, List<String> items,
                                  String formatSection, String itemTemplateKey,
                                  String singleTemplateKey, String itemPlaceholder) {
        if (items == null || items.isEmpty()) return "";

        var cfg = type.config(configManager);
        String base = type.logPath(formatSection);
        String separator = cfg.getString(base + ".separator", ", ");
        if (items.size() == 1) {
            String singleTpl = cfg.getString(base + "." + singleTemplateKey,
                    cfg.getString(base + "." + itemTemplateKey, itemPlaceholder));
            return singleTpl
                    .replace("{word}", items.get(0))
                    .replace("{link}", items.get(0))
                    .replace("{original-message}", items.get(0));
        }

        String itemTpl = cfg.getString(base + "." + itemTemplateKey, itemPlaceholder);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(itemTpl
                    .replace("{word}", items.get(i))
                    .replace("{link}", items.get(i))
                    .replace("{original-message}", items.get(i)));
        }
        return sb.toString();
    }

    private static List<String> detectedItems(MessageCacheManager.CachedMessage cached, FilterType type, String originalMessage) {
        return switch (type) {
            case BAD_WORDS -> uniqueItems(cached.getBadWords());
            case LINKS -> uniqueItems(cached.getLinks());
            case BLOCKED_WORDS -> uniqueItems(cached.getBlockedWords());
            case CAPS -> List.of(originalMessage);
            case ANTI_SPAM -> List.of(originalMessage);
        };
    }

    private static List<String> uniqueItems(List<String> items) {
        if (items.size() <= 1) return items;
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    public interface CapsPriorityPolicy {
        boolean isActive(MessageCacheManager.CachedMessage cached, FilterType type, Set<FilterType> bypassed);

        boolean shouldApplyCapsFix(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed);

        boolean shouldEmitCapsNotifications(MessageCacheManager.CachedMessage cached, Set<FilterType> bypassed);

        boolean isWordFilterSideSelected(FilterType type, MessageCacheManager.CachedMessage cached,
                                         Set<FilterType> bypassed, boolean forNotifications);
    }
}
