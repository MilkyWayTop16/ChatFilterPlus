package org.gw.chatfilterplus.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.List;
import java.util.Set;

@Getter
public class CapsConfig {

    private final ChatFilterPlus plugin;
    private final File configFile;
    private FileConfiguration config;

    private volatile boolean filterEnabled;
    private volatile String filterMode;
    private volatile int minLength;
    private volatile int maxPercent;
    private volatile boolean ignoreNonLetters;
    private volatile String notificationPriorityBadwords;
    private volatile String filterPriorityBadwords;
    private volatile String notificationPriorityBlockedwords;
    private volatile String filterPriorityBlockedwords;
    private volatile Set<String> exceptionPlayers;
    private volatile Set<String> exceptionGroups;

    public CapsConfig(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "caps.yml");
    }

    public void load() {
        config = ConfigUtils.loadWithUpdate(plugin, configFile, "caps.yml");

        filterEnabled = config.getBoolean("filter.caps.enabled", false);
        filterMode = config.getString("filter.caps.mode", "replace-and-notify").toLowerCase();
        minLength = Math.max(1, config.getInt("filter.caps.min-length", 5));
        maxPercent = Math.max(0, Math.min(100, config.getInt("filter.caps.max-caps-percent", 70)));
        ignoreNonLetters = config.getBoolean("filter.caps.ignore-non-letters", true);
        notificationPriorityBadwords = config.getString("filter.caps.badwords-priority.notification-priority", "badwords").toLowerCase();
        filterPriorityBadwords = config.getString("filter.caps.badwords-priority.filter-priority", "both").toLowerCase();
        notificationPriorityBlockedwords = config.getString("filter.caps.blockedwords-priority.notification-priority", "blockedwords").toLowerCase();
        filterPriorityBlockedwords = config.getString("filter.caps.blockedwords-priority.filter-priority", "both").toLowerCase();
        exceptionPlayers = ConfigUtils.cleanStringSet(config.getStringList("filter.caps.exceptions.players"));
        exceptionGroups = ConfigUtils.cleanStringSet(config.getStringList("filter.caps.exceptions.groups"));
    }

    public List<String> getWhitelist() {
        return ConfigUtils.cleanStringList(config.getStringList("filter.caps.whitelist"));
    }
}
