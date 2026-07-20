package org.gw.chatfilterplus.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Getter
public class MainConfig {

    private final ChatFilterPlus plugin;
    private final File configFile;
    private FileConfiguration config;

    private volatile boolean consoleLogsEnabled;
    private volatile boolean bStatsEnabled;
    private volatile boolean updateCheckerEnabled;
    private volatile String updateNotifyMode;
    private volatile int updatePeriodicIntervalHours;
    private volatile int cacheMaxSize;
    private volatile long cacheCleanupRetentionMillis;
    private volatile boolean cacheCleanupEnabled;
    private volatile String compatibilityEventPriority;
    private volatile boolean compatibilityAggressiveMode;
    private volatile boolean commandFilteringEnabled;
    private volatile List<String> commandFilteringCommands;
    private volatile boolean adminSelfNotifyEnabled;

    public MainConfig(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        config = ConfigUtils.loadWithUpdate(plugin, configFile, "config.yml");

        consoleLogsEnabled = config.getBoolean("settings.logs.console.enabled", false);
        cacheMaxSize = config.getInt("settings.cache.max-size", 1000);
        cacheCleanupEnabled = config.getBoolean("settings.cache.cleanup.enabled", true);
        cacheCleanupRetentionMillis = ConfigUtils.parseRetentionPeriod(
                config.getString("settings.cache.cleanup.retention-period", "5m"));
        bStatsEnabled = config.getBoolean("settings.bstats.enabled", true);
        updateCheckerEnabled = config.getBoolean("settings.update-checker.enabled", true);
        updateNotifyMode = config.getString("settings.update-checker.notify-mode", "both").toLowerCase();
        updatePeriodicIntervalHours = Math.max(1, config.getInt("settings.update-checker.periodic-interval-hours", 6));
        compatibilityEventPriority = config.getString("settings.compatibility.event-priority", "lowest").toLowerCase();
        compatibilityAggressiveMode = config.getBoolean("settings.compatibility.aggressive-mode", false);
        commandFilteringEnabled = config.getBoolean("settings.command-filtering.enabled", true);
        commandFilteringCommands = Collections.unmodifiableList(
                ConfigUtils.cleanStringList(config.getStringList("settings.command-filtering.commands")));
        adminSelfNotifyEnabled = config.getBoolean("settings.admin-self-notify.enabled", true);
    }
}
