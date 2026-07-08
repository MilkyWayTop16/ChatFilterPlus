package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.function.Function;

@Getter
public enum FilterType {

    BAD_WORDS("bad-words", "badwords", ConfigManager::getBadWordsConfig,
            "Обнаружен мат",
            "[{time}] Игрок {player} написал мат(ы): {words}",
            "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) написал мат(ы): {words}"),

    LINKS("links", "links", ConfigManager::getLinksConfig,
            "Обнаружена ссылка",
            "[{time}] Игрок {player} попытался отправить ссылку(и): {links}",
            "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) отправил ссылку(и): {links}"),

    CAPS("caps", "caps", ConfigManager::getCapsConfig,
            "Обнаружен капс",
            "[{time}] Игрок {player} использовал капс: {original-message}",
            "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) использовал капс"),

    BLOCKED_WORDS("blocked-words", "blockedwords", ConfigManager::getBlockedWordsConfig,
            "Обнаружено запрещённое слово",
            "[{time}] Игрок {player} использовал запрещённое слово(а): {words}",
            "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) использовал запрещённое слово(а): {words}"),

    ANTI_SPAM("spam", "spam", ConfigManager::getAntiSpamConfig,
            "Обнаружен спам",
            "[{time}] Игрок {player} спамил сообщениями: {reason}",
            "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) был пойман на спаме");

    public static final List<FilterType> PRIORITY_ORDER = List.of(BLOCKED_WORDS, BAD_WORDS, LINKS, CAPS);

    private final String configKey;
    private final String shortName;
    private final Function<ConfigManager, FileConfiguration> configAccessor;
    private final String consoleLabel;
    private final String defaultLogTemplate;
    private final String defaultPunishmentLogTemplate;

    FilterType(String configKey, String shortName,
               Function<ConfigManager, FileConfiguration> configAccessor,
               String consoleLabel, String defaultLogTemplate, String defaultPunishmentLogTemplate) {
        this.configKey = configKey;
        this.shortName = shortName;
        this.configAccessor = configAccessor;
        this.consoleLabel = consoleLabel;
        this.defaultLogTemplate = defaultLogTemplate;
        this.defaultPunishmentLogTemplate = defaultPunishmentLogTemplate;
    }

    public FileConfiguration config(ConfigManager configManager) {
        return configAccessor.apply(configManager);
    }

    public String filterPath(String suffix) {
        return "filter." + configKey + "." + suffix;
    }

    public String logPath(String suffix) {
        return "logs.file." + configKey + "." + suffix;
    }

    public String punishmentPath(String suffix) {
        return "punishments." + configKey + "." + suffix;
    }

    public String notificationPath(String suffix) {
        return "notifications." + configKey + "." + suffix;
    }

    public String logFileName() {
        return shortName + "-logs.txt";
    }

    public String punishmentLogFileName() {
        return shortName + "-punishments-logs.txt";
    }

    public String bypassPermission() {
        return "chatfilterplus.bypass.chatfilter." + shortName;
    }
}
