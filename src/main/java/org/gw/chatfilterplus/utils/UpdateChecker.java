package org.gw.chatfilterplus.utils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker implements Listener {

    private final ChatFilterPlus plugin;
    private final HttpClient httpClient;

    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;
    private volatile long lastCheckTime = 0;

    private static final long MIN_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(30);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/MilkyWayTop16/ChatFilterPlus/releases/latest";

    private BukkitTask periodicTask;

    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    public UpdateChecker(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        startChecker();
    }

    public void reload() {
        updateAvailable = false;
        latestVersion = null;
        lastCheckTime = 0;
        cancelPeriodicTask();
        startChecker();
    }

    public void shutdown() {
        cancelPeriodicTask();
    }

    private void startChecker() {
        if (!plugin.isEnabled() || !plugin.getConfigManager().isUpdateCheckerEnabled()) {
            return;
        }

        checkForUpdate();

        String mode = plugin.getConfigManager().getUpdateNotifyMode();
        if ("periodic".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) {
            startPeriodicTask();
        }
    }

    private void startPeriodicTask() {
        cancelPeriodicTask();

        int hours = plugin.getConfigManager().getUpdatePeriodicIntervalHours();
        long delayTicks = hours * 60L * 60L * 20L;

        periodicTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkForUpdate, delayTicks, delayTicks);
    }

    private void cancelPeriodicTask() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
    }

    private void checkForUpdate() {
        if (!plugin.isEnabled()) return;

        long now = System.currentTimeMillis();
        if (now - lastCheckTime < MIN_CHECK_INTERVAL) {
            return;
        }

        lastCheckTime = now;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_URL))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "ChatFilterPlus-UpdateChecker")
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    if (response.statusCode() == 403 || response.statusCode() == 429) {
                        plugin.log("Достигнут лимит запросов GitHub (Http " + response.statusCode() + ")...");
                    } else {
                        plugin.log("Не удалось проверить обновления (Http " + response.statusCode() + ")...");
                    }
                    return;
                }

                String tagName = extractTagName(response.body());
                if (tagName == null || tagName.isEmpty()) {
                    return;
                }

                String cleanLatest = cleanVersion(tagName);
                String cleanCurrent = cleanVersion(plugin.getDescription().getVersion());

                if (!cleanLatest.equals(cleanCurrent)) {
                    updateAvailable = true;
                    latestVersion = tagName;

                    String mode = plugin.getConfigManager().getUpdateNotifyMode();
                    if (!"on-join".equalsIgnoreCase(mode)) {
                        plugin.getConfigManager().executeActions(null, "update.available", createPlaceholders());
                    }
                }
            } catch (Exception e) {
                plugin.log("Не удалось проверить обновления: &#FF5D00" + e.getMessage());
            }
        });
    }

    private String extractTagName(String json) {
        if (json == null || json.isEmpty()) return null;

        Matcher matcher = TAG_NAME_PATTERN.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String cleanVersion(String version) {
        if (version == null) return "";
        return version.replaceFirst("^v", "")
                .replaceAll("[^0-9.]", "")
                .trim();
    }

    private Map<String, String> createPlaceholders() {
        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("latest-version", latestVersion != null ? latestVersion : "Неизвестно");
        placeholders.put("current-version", plugin.getDescription().getVersion());
        placeholders.put("download-link", "https://github.com/MilkyWayTop16/ChatFilterPlus/releases/latest");
        return placeholders;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String mode = plugin.getConfigManager().getUpdateNotifyMode();

        if (updateAvailable && ("on-join".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode))) {
            if (player.hasPermission("chatfilterplus.admin") || player.isOp()) {
                plugin.getConfigManager().executeActions(player, "update.available", createPlaceholders());
            }
        }
    }
}