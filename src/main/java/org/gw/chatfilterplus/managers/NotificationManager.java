package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class NotificationManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;
    private final AtomicReference<Map<UUID, Boolean>> notificationsEnabledRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public NotificationManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isNotificationsEnabled(Player player) {
        Map<UUID, Boolean> map = notificationsEnabledRef.get();
        return map.getOrDefault(player.getUniqueId(), player.hasPermission("chatfilterplus.admin.notify"));
    }

    public void setNotificationsEnabled(Player player, boolean enabled) {
        notificationsEnabledRef.get().put(player.getUniqueId(), enabled);
    }

    public void notifyPlayer(Player player, FilterType type, String firstItem) {
        configManager.executeActions(type, player, "player", null);
    }

    public void notifyAdmins(Player player, FilterType type, List<String> items) {
        Map<String, String> placeholders = createPlaceholders(player, items);

        List<Player> onlineAdmins = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (Player admin : onlineAdmins) {
            if (!isNotificationsEnabled(admin)) continue;
            if (configManager.isAdminSelfNotifyEnabled() && admin.equals(player)) continue;

            configManager.executeActions(type, admin, "admin", placeholders);
        }
    }

    public void notifyConsole(Player player, FilterType type, List<String> items) {
        Map<String, String> placeholders = createPlaceholders(player, items);
        configManager.executeActions(type, null, "console", placeholders);
    }

    public void notifyDiscord(Player player, FilterType type, List<String> items) {
        Map<String, String> placeholders = createPlaceholders(player, items);
        placeholders.put("original-message", items.isEmpty() ? "[BLOCKED]" : items.get(0));

        FileConfiguration config = type.config(configManager);
        if (!config.getBoolean(type.notificationPath("discord.enabled"), false)) return;

        String template = config.getString(type.notificationPath("discord.message"), "");
        String webhookUrl = config.getString(type.notificationPath("discord.webhook-url"), "");

        if (template.isEmpty() || webhookUrl.isEmpty()) return;

        String message = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        sendDiscordWebhook(webhookUrl, message);
    }

    private Map<String, String> createPlaceholders(Player player, List<String> items) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player != null ? player.getName() : "Console");
        ph.put("words", String.join(", ", items));
        ph.put("links", String.join(", ", items));
        ph.put("reason", items.isEmpty() ? "" : items.get(0));
        ph.put("original-message", items.isEmpty() ? "" : items.get(0));
        return ph;
    }

    private void sendDiscordWebhook(String webhookUrl, String message) {
        if (webhookUrl.isEmpty() || message.isEmpty()) return;

        String json = "{\"content\":\"" + escapeJson(message) + "\",\"allowed_mentions\":{\"parse\":[]}}";
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

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public void reload() {
        notificationsEnabledRef.set(new ConcurrentHashMap<>());
    }
}