package org.gw.chatfilterplus.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.List;
import java.util.Set;

@Getter
public class AntiSpamConfig {

    private final ChatFilterPlus plugin;
    private final File configFile;
    private FileConfiguration config;

    private volatile boolean filterEnabled;
    private volatile boolean generalCooldownEnabled;
    private volatile int generalCooldownSeconds;
    private volatile int generalCooldownIgnoreIfLongerThan;
    private volatile int generalCooldownMinLength;
    private volatile boolean similarMessageCooldownEnabled;
    private volatile int similarMessageCooldownSeconds;
    private volatile int similarMessageSimilarityPercent;
    private volatile int similarMessageCooldownIgnoreIfLongerThan;
    private volatile int similarMessageCooldownMinLength;
    private volatile boolean characterFloodEnabled;
    private volatile int characterFloodMaxRepeatingChars;
    private volatile int characterFloodMaxRepeatingPattern;
    private volatile int characterFloodCooldownSeconds;
    private volatile Set<String> exceptionPlayers;
    private volatile Set<String> exceptionGroups;

    public AntiSpamConfig(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "spam.yml");
    }

    public void load() {
        new SpamConfigMigrator(plugin).migrate();

        config = ConfigUtils.loadWithUpdate(plugin, configFile, "spam.yml");

        if (config.contains("filter.anti-spam")
                || config.contains("logs.file.anti-spam")
                || config.contains("notifications.anti-spam")
                || config.contains("punishments.anti-spam")) {
            new SpamConfigMigrator(plugin).migrate();
            config = ConfigUtils.loadWithUpdate(plugin, configFile, "spam.yml");
        }

        filterEnabled = config.getBoolean("filter.spam.enabled", true);
        generalCooldownEnabled = config.getBoolean("filter.spam.general-cooldown.enabled", true);
        generalCooldownSeconds = Math.max(0, config.getInt("filter.spam.general-cooldown.seconds", 3));
        generalCooldownIgnoreIfLongerThan = config.getInt("filter.spam.general-cooldown.ignore-if-longer-than", -1);
        generalCooldownMinLength = config.getInt("filter.spam.general-cooldown.min-length", -1);
        similarMessageCooldownEnabled = config.getBoolean("filter.spam.similar-message-cooldown.enabled", true);
        similarMessageCooldownSeconds = Math.max(0, config.getInt("filter.spam.similar-message-cooldown.seconds", 10));
        similarMessageSimilarityPercent = Math.max(50, Math.min(100,
                config.getInt("filter.spam.similar-message-cooldown.similarity-percent", 75)));
        similarMessageCooldownIgnoreIfLongerThan = config.getInt("filter.spam.similar-message-cooldown.ignore-if-longer-than", -1);
        similarMessageCooldownMinLength = Math.max(0, config.getInt("filter.spam.similar-message-cooldown.min-length", 10));
        characterFloodEnabled = config.getBoolean("filter.spam.character-flood.enabled", true);
        characterFloodMaxRepeatingChars = Math.max(2, config.getInt("filter.spam.character-flood.max-repeating-chars", 5));
        characterFloodMaxRepeatingPattern = Math.max(1, config.getInt("filter.spam.character-flood.max-repeating-pattern", 3));
        characterFloodCooldownSeconds = Math.max(0, config.getInt("filter.spam.character-flood.cooldown-seconds", 8));
        exceptionPlayers = ConfigUtils.cleanStringSet(config.getStringList("filter.spam.exceptions.players"));
        exceptionGroups = ConfigUtils.cleanStringSet(config.getStringList("filter.spam.exceptions.groups"));
    }
}
