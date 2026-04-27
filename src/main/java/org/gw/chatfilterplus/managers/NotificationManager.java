package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class NotificationManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;
    private final Map<UUID, Boolean> notificationsEnabled;

    public NotificationManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.httpClient = HttpClient.newHttpClient();
        this.notificationsEnabled = new ConcurrentHashMap<>();
    }

    public boolean isNotificationsEnabled(Player player) {
        return notificationsEnabled.getOrDefault(player.getUniqueId(), player.hasPermission("chatfilterplus.admin.notify"));
    }

    public void setNotificationsEnabled(Player player, boolean enabled) {
        notificationsEnabled.put(player.getUniqueId(), enabled);
    }

    public void notifyPlayer(Player player, FilterType type, String firstItem) {
        if (type == FilterType.BAD_WORDS) {
            configManager.executeActionsFromBadWords(player, "player", null);
        } else if (type == FilterType.LINKS) {
            configManager.executeActionsFromLinks(player, "player", null);
        } else if (type == FilterType.CAPS) {
            configManager.executeActionsFromCaps(player, "player", null);
        } else if (type == FilterType.BLOCKED_WORDS) {
            configManager.executeActionsFromBlockedWords(player, "player", null);
        } else if (type == FilterType.ANTI_SPAM) {
            configManager.executeActionsFromAntiSpam(player, "player", null);
        }
    }

    public void notifyAdmins(Player player, FilterType type, List<String> items) {
        Map<String, String> placeholders = createPlaceholders(player, items);

        if (type == FilterType.BAD_WORDS) {
            configManager.executeActionsFromBadWords(null, "admin", placeholders);
        } else if (type == FilterType.LINKS) {
            configManager.executeActionsFromLinks(null, "admin", placeholders);
        } else if (type == FilterType.CAPS) {
            configManager.executeActionsFromCaps(null, "admin", placeholders);
        } else if (type == FilterType.BLOCKED_WORDS) {
            configManager.executeActionsFromBlockedWords(null, "admin", placeholders);
        } else if (type == FilterType.ANTI_SPAM) {
            configManager.executeActionsFromAntiSpam(null, "admin", placeholders);
        }
    }

    public void notifyConsole(Player player, FilterType type, List<String> items) {
        Map<String, String> placeholders = createPlaceholders(player, items);

        if (type == FilterType.BAD_WORDS) {
            configManager.executeActionsFromBadWords(null, "console", placeholders);
        } else if (type == FilterType.LINKS) {
            configManager.executeActionsFromLinks(null, "console", placeholders);
        } else if (type == FilterType.CAPS) {
            configManager.executeActionsFromCaps(null, "console", placeholders);
        } else if (type == FilterType.BLOCKED_WORDS) {
            configManager.executeActionsFromBlockedWords(null, "console", placeholders);
        } else if (type == FilterType.ANTI_SPAM) {
            configManager.executeActionsFromAntiSpam(null, "console", placeholders);
        }
    }

    public void notifyDiscord(Player player, FilterType type, List<String> items) {
        Map<String, String> placeholders = createPlaceholders(player, items);
        placeholders.put("original-message", items.isEmpty() ? "[BLOCKED]" : items.get(0));

        String template = "";
        String webhookUrl = "";

        if (type == FilterType.BAD_WORDS) {
            template = configManager.getBadWordsConfig().getString("notifications.bad-words.discord.message", "");
            webhookUrl = configManager.getBadWordsConfig().getString("notifications.bad-words.discord.webhook-url", "");
        } else if (type == FilterType.LINKS) {
            template = configManager.getLinksConfig().getString("notifications.links.discord.message", "");
            webhookUrl = configManager.getLinksConfig().getString("notifications.links.discord.webhook-url", "");
        } else if (type == FilterType.CAPS) {
            template = configManager.getCapsConfig().getString("notifications.caps.discord.message", "");
            webhookUrl = configManager.getCapsConfig().getString("notifications.caps.discord.webhook-url", "");
        } else if (type == FilterType.BLOCKED_WORDS) {
            template = configManager.getBlockedWordsConfig().getString("notifications.blocked-words.discord.message", "");
            webhookUrl = configManager.getBlockedWordsConfig().getString("notifications.blocked-words.discord.webhook-url", "");
        } else if (type == FilterType.ANTI_SPAM) {
            template = configManager.getAntiSpamConfig().getString("notifications.anti-spam.discord.message", "");
            webhookUrl = configManager.getAntiSpamConfig().getString("notifications.anti-spam.discord.webhook-url", "");
        }

        if (template.isEmpty() || webhookUrl.isEmpty()) return;

        String message = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        sendDiscordWebhook(webhookUrl, message);
    }

    private Map<String, String> createPlaceholders(Player player, List<String> items) {
        Map<String, String> ph = new java.util.HashMap<>();
        ph.put("player", player.getName());
        ph.put("words", String.join(", ", items));
        ph.put("links", String.join(", ", items));
        ph.put("reason", items.isEmpty() ? "" : items.get(0));
        ph.put("original-message", items.isEmpty() ? "" : items.get(0));
        return ph;
    }

    private void sendDiscordWebhook(String webhookUrl, String message) {
        if (webhookUrl.isEmpty() || message.isEmpty()) return;

        String escaped = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");

        String json = "{\"content\":\"" + escaped + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                plugin.console("&#FF5D00Ошибка отправки в Дискорд: " + e.getMessage());
            }
        });
    }

    public void reload() {
        notificationsEnabled.clear();
    }
}