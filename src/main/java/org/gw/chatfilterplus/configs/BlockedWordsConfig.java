package org.gw.chatfilterplus.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.List;
import java.util.Set;

@Getter
public class BlockedWordsConfig {

    private final ChatFilterPlus plugin;
    private final File configFile;
    private FileConfiguration config;

    private volatile boolean filterEnabled;
    private volatile String filterMode;
    private volatile String filterReplacement;
    private volatile String filterLevel;
    private volatile Set<String> exceptionPlayers;
    private volatile Set<String> exceptionGroups;

    public BlockedWordsConfig(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "blocked-words.yml");
    }

    public void load() {
        config = ConfigUtils.loadWithUpdate(plugin, configFile, "blocked-words.yml");

        filterEnabled = config.getBoolean("filter.blocked-words.enabled", true);
        filterMode = config.getString("filter.blocked-words.mode", "block-and-notify").toLowerCase();
        filterReplacement = config.getString("filter.blocked-words.replacement", "*");
        filterLevel = config.getString("filter.blocked-words.level", "high").toLowerCase();
        exceptionPlayers = ConfigUtils.cleanStringSet(config.getStringList("filter.blocked-words.exceptions.players"));
        exceptionGroups = ConfigUtils.cleanStringSet(config.getStringList("filter.blocked-words.exceptions.groups"));
    }

    public List<String> getBlockedWordsList() {
        return ConfigUtils.cleanWordList(config.getStringList("blocked-words"));
    }
}
