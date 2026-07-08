package org.gw.chatfilterplus.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.List;
import java.util.Set;

@Getter
public class BadWordsConfig {

    private final ChatFilterPlus plugin;
    private final File configFile;
    private FileConfiguration config;

    private volatile boolean filterEnabled;
    private volatile String filterMode;
    private volatile String filterLevel;
    private volatile String filterReplacement;
    private volatile boolean detectEnglishLookalikes;
    private volatile Set<String> exceptionPlayers;
    private volatile Set<String> exceptionGroups;

    public BadWordsConfig(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "bad-words.yml");
    }

    public void load() {
        config = ConfigUtils.loadWithUpdate(plugin, configFile, "bad-words.yml");

        filterEnabled = config.getBoolean("filter.bad-words.enabled", true);
        filterMode = config.getString("filter.bad-words.mode", "send-and-notify").toLowerCase();
        filterLevel = config.getString("filter.bad-words.level", "high").toLowerCase();
        filterReplacement = config.getString("filter.bad-words.replacement", "*");
        detectEnglishLookalikes = config.getBoolean("filter.bad-words.detect-english-lookalikes", true);
        exceptionPlayers = ConfigUtils.cleanStringSet(config.getStringList("filter.bad-words.exceptions.players"));
        exceptionGroups = ConfigUtils.cleanStringSet(config.getStringList("filter.bad-words.exceptions.groups"));
    }

    public List<String> getSafeWords() {
        return ConfigUtils.cleanWordList(config.getStringList("safe-words"));
    }

    public List<String> getBadWordsList() {
        return ConfigUtils.cleanWordList(config.getStringList("bad-words"));
    }
}
