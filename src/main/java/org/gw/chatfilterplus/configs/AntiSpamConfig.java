package org.gw.chatfilterplus.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
    private volatile boolean newPlayerChatLockEnabled;
    private volatile long newPlayerChatLockMillis;
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

        if (migrateRemainingPlaceholders()) {
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
        newPlayerChatLockEnabled = config.getBoolean("filter.spam.new-player-chat-lock.enabled", true);
        newPlayerChatLockMillis = Math.max(0L, ConfigUtils.parseDuration(
                config.getString("filter.spam.new-player-chat-lock.duration", "5m")));
        if (newPlayerChatLockMillis <= 0L) {
            newPlayerChatLockMillis = 5L * 60L * 1000L;
        }
        exceptionPlayers = ConfigUtils.cleanStringSet(config.getStringList("filter.spam.exceptions.players"));
        exceptionGroups = ConfigUtils.cleanStringSet(config.getStringList("filter.spam.exceptions.groups"));
    }

    private static final Pattern REMAINING_SEC_SUFFIX = Pattern.compile(
            "\\{remaining\\}\\s*(?:с\\.|с(?![а-яА-Яa-zA-Z])|сек\\.?|sec\\.?)");
    private static final Pattern REMAINING_MIN_SEC_COMBO = Pattern.compile(
            "\\{remaining-min\\}\\s*(?:мин\\.?|м\\.?)\\s*\\{remaining-sec\\}\\s*(?:сек\\.?|с\\.?)");

    private boolean migrateRemainingPlaceholders() {
        if (config == null || !configFile.exists()) return false;

        boolean changed = false;
        for (String path : config.getKeys(true)) {
            if (!config.isList(path)) continue;
            List<?> raw = config.getList(path);
            if (raw == null || raw.isEmpty()) continue;

            List<Object> updated = new ArrayList<>(raw.size());
            boolean listChanged = false;
            for (Object item : raw) {
                if (!(item instanceof String str)) {
                    updated.add(item);
                    continue;
                }
                String fixed = fixRemainingPlaceholders(str);
                if (!fixed.equals(str)) {
                    listChanged = true;
                }
                updated.add(fixed);
            }
            if (listChanged) {
                config.set(path, updated);
                changed = true;
            }
        }

        if (!changed) return false;

        try {
            config.save(configFile);
            plugin.log("Обновлены плейсхолдеры времени в &#ffff00spam.yml &f({remaining})");
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Не удалось сохранить миграцию {remaining} в spam.yml: " + e.getMessage());
            return false;
        }
    }

    static String fixRemainingPlaceholders(String input) {
        if (input == null || input.isEmpty() || !input.contains("{remaining")) {
            return input;
        }
        String s = REMAINING_MIN_SEC_COMBO.matcher(input).replaceAll("{remaining}");
        s = REMAINING_SEC_SUFFIX.matcher(s).replaceAll("{remaining}");
        s = s.replace("{remaining}.", "{remaining}");
        return s;
    }
}
