package org.gw.chatfilterplus.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class ConfigManager {
    private final ChatFilterPlus plugin;
    private FileConfiguration wordsConfig;
    private final File wordsFile;
    private File configFile;
    // Логирование
    private boolean consoleLogsEnabled;
    private boolean badWordsFileLogsEnabled;
    private String badWordsFileLogMessage;
    private String badWordsFileWordsSeparator;
    private String badWordsFileWordTemplate;
    private String badWordsFileSingleWordTemplate;
    private boolean linksFileLogsEnabled;
    private String linksFileLogMessage;
    private String linksFileWordsSeparator;
    private String linksFileWordTemplate;
    private String linksFileSingleWordTemplate;
    // Очистка логов
    private boolean badWordsCleanupEnabled;
    private String badWordsCleanupMode;
    private long badWordsCleanupMaxLines;
    private long badWordsCleanupRetentionMillis;
    private boolean linksCleanupEnabled;
    private String linksCleanupMode;
    private long linksCleanupMaxLines;
    private long linksCleanupRetentionMillis;
    // Фильтры
    private boolean badWordsFilterEnabled;
    private String badWordsFilterMode;
    private String badWordsFilterLevel;
    private boolean linksFilterEnabled;
    private String linksFilterMode;
    private String linksFilterReplacement;
    private String linksRegex;
    // Новые поля для белого/чёрного списка
    private boolean linksListFilterEnabled;
    private String linksListFilterMode;
    private List<String> linksListFilterDomains;
    // Уведомления для матов
    private boolean badWordsNotificationsEnabled;
    private boolean badWordsChatEnabled;
    private List<String> badWordsFilterWarningMessage;
    private boolean badWordsTitleEnabled;
    private String badWordsTitleText;
    private long badWordsTitleFadeInTicks;
    private long badWordsTitleStayTicks;
    private long badWordsTitleFadeOutTicks;
    private boolean badWordsActionBarEnabled;
    private String badWordsActionBarText;
    private String badWordsAdminNotifyMessage;
    private String badWordsAdminWordsSeparator;
    private String badWordsAdminWordTemplate;
    private String badWordsAdminSingleWordTemplate;
    private boolean badWordsPlayerSoundEnabled;
    private String badWordsPlayerSoundName;
    private float badWordsPlayerSoundVolume;
    private float badWordsPlayerSoundPitch;
    private boolean badWordsAdminSoundEnabled;
    private String badWordsAdminSoundName;
    private float badWordsAdminSoundVolume;
    private float badWordsAdminSoundPitch;
    private boolean badWordsConsoleNotificationsEnabled;
    private String badWordsConsoleNotifyMessage;
    private String badWordsConsoleWordsSeparator;
    private String badWordsConsoleWordTemplate;
    private String badWordsConsoleSingleWordTemplate;
    // Уведомления для ссылок
    private boolean linksNotificationsEnabled;
    private boolean linksChatEnabled;
    private List<String> linksFilterWarningMessage;
    private boolean linksTitleEnabled;
    private String linksTitleText;
    private long linksTitleFadeInTicks;
    private long linksTitleStayTicks;
    private long linksTitleFadeOutTicks;
    private boolean linksActionBarEnabled;
    private String linksActionBarText;
    private String linksAdminNotifyMessage;
    private String linksAdminLinksSeparator;
    private String linksAdminLinkTemplate;
    private String linksAdminSingleLinkTemplate;
    private boolean linksPlayerSoundEnabled;
    private String linksPlayerSoundName;
    private float linksPlayerSoundVolume;
    private float linksPlayerSoundPitch;
    private boolean linksAdminSoundEnabled;
    private String linksAdminSoundName;
    private float linksAdminSoundVolume;
    private float linksAdminSoundPitch;
    private boolean linksConsoleNotificationsEnabled;
    private String linksConsoleNotifyMessage;
    private String linksConsoleLinksSeparator;
    private String linksConsoleLinkTemplate;
    private String linksConsoleSingleLinkTemplate;
    // Наказания
    private boolean badWordsPunishmentsEnabled;
    private boolean badWordsPunishmentLogsEnabled;
    private String badWordsPunishmentLogMessage;
    private String badWordsPunishmentWordsSeparator;
    private String badWordsPunishmentWordTemplate;
    private String badWordsPunishmentSingleWordTemplate;
    private String badWordsPunishmentCommandsSeparator;
    private String badWordsPunishmentCommandTemplate;
    private String badWordsPunishmentSingleCommandTemplate;
    private List<String> badWordsExceptionPlayers;
    private List<String> badWordsExceptionGroups;
    private String badWordsBypassPermission;
    private boolean linksPunishmentsEnabled;
    private boolean linksPunishmentLogsEnabled;
    private String linksPunishmentLogMessage;
    private String linksPunishmentLinksSeparator;
    private String linksPunishmentLinkTemplate;
    private String linksPunishmentSingleLinkTemplate;
    private String linksPunishmentCommandsSeparator;
    private String linksPunishmentCommandTemplate;
    private String linksPunishmentSingleCommandTemplate;
    private List<String> linksExceptionPlayers;
    private List<String> linksExceptionGroups;
    private String linksBypassPermission;

    public ConfigManager(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.wordsFile = new File(plugin.getDataFolder(), "words.yml");
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void loadWordsConfig() {
        if (!wordsFile.exists()) {
            try {
                plugin.saveResource("words.yml", false);
                if (isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Создан новый words.yml из ресурсов плагина.");
                }
            } catch (Exception e) {
                if (isConsoleLogsEnabled()) {
                    plugin.getLogger().log(Level.WARNING, "Не удалось создать words.yml из ресурсов: " + e.getMessage(), e);
                }
            }
        }

        try {
            wordsConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(wordsFile), StandardCharsets.UTF_8));
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().info("Файл words.yml успешно загружен.");
            }
        } catch (Exception e) {
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Ошибка при загрузке words.yml: " + e.getMessage(), e);
            }
            wordsConfig = new YamlConfiguration();
        }
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try {
                plugin.saveResource("config.yml", false);
                if (isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Создан новый config.yml из ресурсов плагина.");
                }
            } catch (Exception e) {
                if (isConsoleLogsEnabled()) {
                    plugin.getLogger().log(Level.WARNING, "Не удалось создать config.yml из ресурсов: " + e.getMessage(), e);
                }
            }
        }

        FileConfiguration config;
        try {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8));
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().info("Файл config.yml успешно загружен. Путь: " + configFile.getAbsolutePath());
            }
        } catch (Exception e) {
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Ошибка при загрузке config.yml: " + e.getMessage(), e);
            }
            config = new YamlConfiguration();
        }

        plugin.reloadConfig();
        config = plugin.getConfig();

        consoleLogsEnabled = config.getBoolean("settings.logs.console.enabled", false);
        badWordsFileLogsEnabled = config.getBoolean("settings.logs.file.bad-words.enabled", true);
        badWordsFileLogMessage = config.getString("settings.logs.file.bad-words.message", "[{time}] Игрок {player} написал мат(ы): {words}");
        badWordsFileWordsSeparator = config.getString("settings.logs.file.bad-words.words-format.separator", ", ");
        badWordsFileWordTemplate = config.getString("settings.logs.file.bad-words.words-format.word-template", "{word}");
        badWordsFileSingleWordTemplate = config.getString("settings.logs.file.bad-words.words-format.single-word-template", "{word}");
        linksFileLogsEnabled = config.getBoolean("settings.logs.file.links.enabled", true);
        linksFileLogMessage = config.getString("settings.logs.file.links.message", "[{time}] Игрок {player} попытался отправить ссылку(и): {links}");
        linksFileWordsSeparator = config.getString("settings.logs.file.links.links-format.separator", ", ");
        linksFileWordTemplate = config.getString("settings.logs.file.links.links-format.link-template", "{link}");
        linksFileSingleWordTemplate = config.getString("settings.logs.file.links.links-format.single-link-template", "{link}");
        badWordsCleanupEnabled = config.getBoolean("settings.logs.file.bad-words.cleanup.enabled", true);
        badWordsCleanupMode = config.getString("settings.logs.file.bad-words.cleanup.mode", "remove-oldest");
        badWordsCleanupMaxLines = config.getLong("settings.logs.file.bad-words.cleanup.max-lines", 10000);
        badWordsCleanupRetentionMillis = parseRetentionPeriod(config.getString("settings.logs.file.bad-words.cleanup.retention-period", "7d"));
        linksCleanupEnabled = config.getBoolean("settings.logs.file.links.cleanup.enabled", true);
        linksCleanupMode = config.getString("settings.logs.file.links.cleanup.mode", "remove-oldest");
        linksCleanupMaxLines = config.getLong("settings.logs.file.links.cleanup.max-lines", 10000);
        linksCleanupRetentionMillis = parseRetentionPeriod(config.getString("settings.logs.file.links.cleanup.retention-period", "7d"));
        badWordsFilterEnabled = config.getBoolean("settings.filter.bad-words.enabled", true);
        badWordsFilterMode = config.getString("settings.filter.bad-words.mode", "send-and-notify");
        badWordsFilterLevel = config.getString("settings.filter.bad-words.level", "high");
        linksFilterEnabled = config.getBoolean("settings.filter.links.enabled", true);
        linksFilterMode = config.getString("settings.filter.links.mode", "replace-and-notify");
        linksFilterReplacement = config.getString("settings.filter.links.replacement", "&#FB8808[Ссылка удалена]&r");
        linksRegex = config.getString("settings.filter.links.regex", "(https?://\\S+|\\S+\\.(com|org|net|ru|io|me|info|biz|co|edu|gov)\\S*)");
        linksListFilterEnabled = config.getBoolean("settings.filter.links.list-filter.enabled", false);
        linksListFilterMode = config.getString("settings.filter.links.list-filter.mode", "whitelist");
        linksListFilterDomains = config.getStringList("settings.filter.links.list-filter.domains");
        badWordsNotificationsEnabled = config.getBoolean("settings.notifications.bad-words.enabled", true);
        badWordsChatEnabled = config.getBoolean("settings.notifications.bad-words.player.chat.enabled", true);
        String badWordsChatMessage = config.getString("settings.notifications.bad-words.player.chat.message", "&#FB8808◆ &fСтоять! &#FB8808Не матерись &fи &#FB8808не ругайся &fв чате!");
        badWordsFilterWarningMessage = (badWordsChatMessage == null || badWordsChatMessage.trim().isEmpty()) ? new ArrayList<>() : List.of(badWordsChatMessage);
        badWordsTitleEnabled = config.getBoolean("settings.notifications.bad-words.player.title.enabled", true);
        badWordsTitleText = config.getString("settings.notifications.bad-words.player.title.text", "&#FB8808◆ &fА ну-ка, цыц!\n&#FB8808▶️ &fНе матерись в чате ;)");
        badWordsTitleFadeInTicks = config.getLong("settings.notifications.bad-words.player.title.fade-in", 10);
        badWordsTitleStayTicks = config.getLong("settings.notifications.bad-words.player.title.stay", 60);
        badWordsTitleFadeOutTicks = config.getLong("settings.notifications.bad-words.player.title.fade-out", 10);
        badWordsActionBarEnabled = config.getBoolean("settings.notifications.bad-words.player.actionbar.enabled", true);
        badWordsActionBarText = config.getString("settings.notifications.bad-words.player.actionbar.text", "&#FB8808◆ Стоять! &fВам &#FB8808нельзя писать &fматы и ругательства)");
        badWordsAdminNotifyMessage = config.getString("settings.notifications.bad-words.admin.chat.message", "&#FFA500(ᴄʜᴀᴛ-ꜰɪʟᴛᴇʀ) &f| Игрок &#FFA500{player} &fнаписал мат(ы): &#FFA500{words}");
        badWordsAdminWordsSeparator = config.getString("settings.notifications.bad-words.admin.chat.words-format.separator", ", ");
        badWordsAdminWordTemplate = config.getString("settings.notifications.bad-words.admin.chat.words-format.word-template", "{word}");
        badWordsAdminSingleWordTemplate = config.getString("settings.notifications.bad-words.admin.chat.words-format.single-word-template", "{word}");
        badWordsPlayerSoundEnabled = config.getBoolean("settings.notifications.bad-words.player.sound.enabled", true);
        badWordsPlayerSoundName = config.getString("settings.notifications.bad-words.player.sound.name", "ENTITY_VILLAGER_NO");
        badWordsPlayerSoundVolume = (float) config.getDouble("settings.notifications.bad-words.player.sound.volume", 1.0);
        badWordsPlayerSoundPitch = (float) config.getDouble("settings.notifications.bad-words.player.sound.pitch", 1.0);
        badWordsAdminSoundEnabled = config.getBoolean("settings.notifications.bad-words.admin.sound.enabled", true);
        badWordsAdminSoundName = config.getString("settings.notifications.bad-words.admin.sound.name", "BLOCK_NOTE_BLOCK_PLING");
        badWordsAdminSoundVolume = (float) config.getDouble("settings.notifications.bad-words.admin.sound.volume", 1.0);
        badWordsAdminSoundPitch = (float) config.getDouble("settings.notifications.bad-words.admin.sound.pitch", 1.0);
        badWordsConsoleNotificationsEnabled = config.getBoolean("settings.notifications.bad-words.console.enabled", true);
        badWordsConsoleNotifyMessage = config.getString("settings.notifications.bad-words.console.message", "&#FFA500(ᴄʜᴀᴛ-ꜰɪʟᴛᴇʀ) &f| Игрок &#FFA500{player} &fнаписал мат(ы): &#FFA500{words}");
        badWordsConsoleWordsSeparator = config.getString("settings.notifications.bad-words.console.words-format.separator", ", ");
        badWordsConsoleWordTemplate = config.getString("settings.notifications.bad-words.console.words-format.word-template", "{word}");
        badWordsConsoleSingleWordTemplate = config.getString("settings.notifications.bad-words.console.words-format.single-word-template", "{word}");
        linksNotificationsEnabled = config.getBoolean("settings.notifications.links.enabled", true);
        linksChatEnabled = config.getBoolean("settings.notifications.links.player.chat.enabled", true);
        String linksChatMessage = config.getString("settings.notifications.links.player.chat.message", "&#FB8808◆ &fСтоять! &fВы &#FB8808не можете &fотправлять ссылки в чат!");
        linksFilterWarningMessage = (linksChatMessage == null || linksChatMessage.trim().isEmpty()) ? new ArrayList<>() : List.of(linksChatMessage);
        linksTitleEnabled = config.getBoolean("settings.notifications.links.player.title.enabled", true);
        linksTitleText = config.getString("settings.notifications.links.player.title.text", "&#FB8808▶️ &fСтоп!\n&#FB8808◆ &f&fЧто ты там рекламируешь?)");
        linksTitleFadeInTicks = config.getLong("settings.notifications.links.player.title.fade-in", 10);
        linksTitleStayTicks = config.getLong("settings.notifications.links.player.title.stay", 60);
        linksTitleFadeOutTicks = config.getLong("settings.notifications.links.player.title.fade-out", 10);
        linksActionBarEnabled = config.getBoolean("settings.notifications.links.player.actionbar.enabled", true);
        linksActionBarText = config.getString("settings.notifications.links.player.actionbar.text", "&#FB8808◆ &fСтоять! &fТак, &#FB8808хватит &fотправлять ссылки в чат!");
        linksAdminNotifyMessage = config.getString("settings.notifications.links.admin.chat.message", "&#FFA500(ᴄʜᴀᴛ-ꜰɪʟᴛᴇʀ) &f| Игрок &#FFA500{player} &fпопытался отправить ссылку(и): &#FFA500{links}");
        linksAdminLinksSeparator = config.getString("settings.notifications.links.admin.chat.links-format.separator", ", ");
        linksAdminLinkTemplate = config.getString("settings.notifications.links.admin.chat.links-format.link-template", "{link}");
        linksAdminSingleLinkTemplate = config.getString("settings.notifications.links.admin.chat.links-format.single-link-template", "{link}");
        linksPlayerSoundEnabled = config.getBoolean("settings.notifications.links.player.sound.enabled", true);
        linksPlayerSoundName = config.getString("settings.notifications.links.player.sound.name", "ENTITY_VILLAGER_NO");
        linksPlayerSoundVolume = (float) config.getDouble("settings.notifications.links.player.sound.volume", 1.0);
        linksPlayerSoundPitch = (float) config.getDouble("settings.notifications.links.player.sound.pitch", 1.0);
        linksAdminSoundEnabled = config.getBoolean("settings.notifications.links.admin.sound.enabled", true);
        linksAdminSoundName = config.getString("settings.notifications.links.admin.sound.name", "BLOCK_NOTE_BLOCK_PLING");
        linksAdminSoundVolume = (float) config.getDouble("settings.notifications.links.admin.sound.volume", 1.0);
        linksAdminSoundPitch = (float) config.getDouble("settings.notifications.links.admin.sound.pitch", 1.0);
        linksConsoleNotificationsEnabled = config.getBoolean("settings.notifications.links.console.enabled", true);
        linksConsoleNotifyMessage = config.getString("settings.notifications.links.console.message", "&#FFA500(ᴄʜᴀᴛ-ꜰɪʟᴛᴇʀ) &f| Игрок &#FFA500{player} &fпопытался отправить ссылку(и): &#FFA500{links}");
        linksConsoleLinksSeparator = config.getString("settings.notifications.links.console.links-format.separator", ", ");
        linksConsoleLinkTemplate = config.getString("settings.notifications.links.console.links-format.link-template", "{link}");
        linksConsoleSingleLinkTemplate = config.getString("settings.notifications.links.console.links-format.single-link-template", "{link}");
        badWordsPunishmentsEnabled = config.getBoolean("settings.punishments.bad-words.enabled", false);
        badWordsPunishmentLogsEnabled = config.getBoolean("settings.punishments.bad-words.logs.enabled", true);
        badWordsPunishmentLogMessage = config.getString("settings.punishments.bad-words.logs.message", "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) написал мат(ы): {words}. Выполнены команды: {commands}");
        badWordsPunishmentWordsSeparator = config.getString("settings.punishments.bad-words.logs.words-format.separator", ", ");
        badWordsPunishmentWordTemplate = config.getString("settings.punishments.bad-words.logs.words-format.word-template", "{word}");
        badWordsPunishmentSingleWordTemplate = config.getString("settings.punishments.bad-words.logs.words-format.single-word-template", "{word}");
        badWordsPunishmentCommandsSeparator = config.getString("settings.punishments.bad-words.logs.commands-format.separator", ", ");
        badWordsPunishmentCommandTemplate = config.getString("settings.punishments.bad-words.logs.commands-format.command-template", "{command}");
        badWordsPunishmentSingleCommandTemplate = config.getString("settings.punishments.bad-words.logs.commands-format.single-command-template", "{command}");
        badWordsExceptionPlayers = config.getStringList("settings.punishments.bad-words.exceptions.players");
        badWordsExceptionGroups = config.getStringList("settings.punishments.bad-words.exceptions.groups");
        badWordsBypassPermission = config.getString("settings.punishments.bad-words.bypass-permission", "chatfilterplus.bypass.punishment.badwords");
        linksPunishmentsEnabled = config.getBoolean("settings.punishments.links.enabled", false);
        linksPunishmentLogsEnabled = config.getBoolean("settings.punishments.links.logs.enabled", true);
        linksPunishmentLogMessage = config.getString("settings.punishments.links.logs.message", "[{time}] Игрок {player} (нарушений: {violations}, стадия: {stage}) отправил ссылку(и): {links}. Выполнены команды: {commands}");
        linksPunishmentLinksSeparator = config.getString("settings.punishments.links.logs.links-format.separator", ", ");
        linksPunishmentLinkTemplate = config.getString("settings.punishments.links.logs.links-format.link-template", "{link}");
        linksPunishmentSingleLinkTemplate = config.getString("settings.punishments.links.logs.links-format.single-link-template", "{link}");
        linksPunishmentCommandsSeparator = config.getString("settings.punishments.links.logs.commands-format.separator", ", ");
        linksPunishmentCommandTemplate = config.getString("settings.punishments.links.logs.commands-format.command-template", "{command}");
        linksPunishmentSingleCommandTemplate = config.getString("settings.punishments.links.logs.commands-format.single-command-template", "{command}");
        linksExceptionPlayers = config.getStringList("settings.punishments.links.exceptions.players");
        linksExceptionGroups = config.getStringList("settings.punishments.links.exceptions.groups");
        linksBypassPermission = config.getString("settings.punishments.links.bypass-permission", "chatfilterplus.bypass.punishment.links");

        if (consoleLogsEnabled) {
            plugin.getLogger().info("Загружены настройки конфигурации:");
            plugin.getLogger().info("Логирование в консоль: " + consoleLogsEnabled);
            plugin.getLogger().info("Логирование матов в файл: " + badWordsFileLogsEnabled);
            plugin.getLogger().info("Логирование ссылок в файл: " + linksFileLogsEnabled);
            plugin.getLogger().info("Логирование наказаний за маты: " + badWordsPunishmentLogsEnabled);
            plugin.getLogger().info("Логирование наказаний за ссылки: " + linksPunishmentLogsEnabled);
            plugin.getLogger().info("Фильтр матов включен: " + badWordsFilterEnabled + ", режим: " + badWordsFilterMode + ", уровень: " + badWordsFilterLevel);
            plugin.getLogger().info("Фильтр ссылок включен: " + linksFilterEnabled + ", режим: " + linksFilterMode);
            plugin.getLogger().info("Белый/чёрный список ссылок включен: " + linksListFilterEnabled + ", режим: " + linksListFilterMode + ", домены: " + linksListFilterDomains);
            plugin.getLogger().info("Очистка логов матов включена: " + badWordsCleanupEnabled + ", режим: " + badWordsCleanupMode);
            plugin.getLogger().info("Очистка логов ссылок включена: " + linksCleanupEnabled + ", режим: " + linksCleanupMode);
            plugin.getLogger().info("Наказания за маты включены: " + badWordsPunishmentsEnabled);
            plugin.getLogger().info("Наказания за ссылки включены: " + linksPunishmentsEnabled);
        }
    }

    private long parseRetentionPeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Некорректное значение retention-period, используется значение по умолчанию: 7 дней.");
            }
            return 7 * 24 * 60 * 60 * 1000L;
        }

        long totalMillis = 0;
        Pattern pattern = Pattern.compile("(\\d+)([smhdwy])");
        Matcher matcher = pattern.matcher(period.toLowerCase());

        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "s":
                    totalMillis += value * 1000L;
                    break;
                case "m":
                    totalMillis += value * 60 * 1000L;
                    break;
                case "h":
                    totalMillis += value * 60 * 60 * 1000L;
                    break;
                case "d":
                    totalMillis += value * 24 * 60 * 60 * 1000L;
                    break;
                case "w":
                    totalMillis += value * 7 * 24 * 60 * 60 * 1000L;
                    break;
                case "y":
                    totalMillis += value * 365 * 24 * 60 * 60 * 1000L;
                    break;
                default:
                    if (isConsoleLogsEnabled()) {
                        plugin.getLogger().warning("Неизвестная единица времени в retention-period: " + unit);
                    }
            }
        }

        if (totalMillis == 0) {
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Не удалось распознать retention-period: " + period + ", используется значение по умолчанию: 7 дней.");
            }
            return 7 * 24 * 60 * 60 * 1000L;
        }

        return totalMillis;
    }

    public void saveWordsConfig() {
        try {
            wordsConfig.save(wordsFile);
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().info("Файл words.yml успешно сохранён.");
            }
        } catch (IOException e) {
            if (isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Не удалось сохранить words.yml: " + e.getMessage(), e);
            }
        }
    }

    public FileConfiguration getWordsConfig() {
        if (wordsConfig == null) {
            loadWordsConfig();
        }
        return wordsConfig;
    }

    public boolean isConsoleLogsEnabled() { return consoleLogsEnabled; }
    public boolean isBadWordsFileLogsEnabled() { return badWordsFileLogsEnabled; }
    public String getBadWordsFileLogMessage() { return badWordsFileLogMessage; }
    public String getBadWordsFileWordsSeparator() { return badWordsFileWordsSeparator; }
    public String getBadWordsFileWordTemplate() { return badWordsFileWordTemplate; }
    public String getBadWordsFileSingleWordTemplate() { return badWordsFileSingleWordTemplate; }
    public boolean isLinksFileLogsEnabled() { return linksFileLogsEnabled; }
    public String getLinksFileLogMessage() { return linksFileLogMessage; }
    public String getLinksFileWordsSeparator() { return linksFileWordsSeparator; }
    public String getLinksFileWordTemplate() { return linksFileWordTemplate; }
    public String getLinksFileSingleWordTemplate() { return linksFileSingleWordTemplate; }
    public boolean isBadWordsCleanupEnabled() { return badWordsCleanupEnabled; }
    public String getBadWordsCleanupMode() { return badWordsCleanupMode; }
    public long getBadWordsCleanupMaxLines() { return badWordsCleanupMaxLines; }
    public long getBadWordsCleanupRetentionMillis() { return badWordsCleanupRetentionMillis; }
    public boolean isLinksCleanupEnabled() { return linksCleanupEnabled; }
    public String getLinksCleanupMode() { return linksCleanupMode; }
    public long getLinksCleanupMaxLines() { return linksCleanupMaxLines; }
    public long getLinksCleanupRetentionMillis() { return linksCleanupRetentionMillis; }
    public boolean isBadWordsFilterEnabled() { return badWordsFilterEnabled; }
    public String getBadWordsFilterMode() { return badWordsFilterMode; }
    public String getBadWordsFilterLevel() { return badWordsFilterLevel; }
    public boolean isLinksFilterEnabled() { return linksFilterEnabled; }
    public String getLinksFilterMode() { return linksFilterMode; }
    public String getLinksFilterReplacement() { return linksFilterReplacement; }
    public String getLinksRegex() { return linksRegex; }
    public boolean isLinksListFilterEnabled() { return linksListFilterEnabled; }
    public String getLinksListFilterMode() { return linksListFilterMode; }
    public List<String> getLinksListFilterDomains() { return linksListFilterDomains; }
    public boolean isBadWordsNotificationsEnabled() { return badWordsNotificationsEnabled; }
    public boolean isBadWordsChatEnabled() { return badWordsChatEnabled; }
    public List<String> getBadWordsFilterWarningMessage() { return badWordsFilterWarningMessage; }
    public boolean isBadWordsTitleEnabled() { return badWordsTitleEnabled; }
    public String getBadWordsTitleText() { return badWordsTitleText; }
    public long getBadWordsTitleFadeInTicks() { return badWordsTitleFadeInTicks; }
    public long getBadWordsTitleStayTicks() { return badWordsTitleStayTicks; }
    public long getBadWordsTitleFadeOutTicks() { return badWordsTitleFadeOutTicks; }
    public boolean isBadWordsActionBarEnabled() { return badWordsActionBarEnabled; }
    public String getBadWordsActionBarText() { return badWordsActionBarText; }
    public String getBadWordsAdminNotifyMessage() { return badWordsAdminNotifyMessage; }
    public String getBadWordsAdminWordsSeparator() { return badWordsAdminWordsSeparator; }
    public String getBadWordsAdminWordTemplate() { return badWordsAdminWordTemplate; }
    public String getBadWordsAdminSingleWordTemplate() { return badWordsAdminSingleWordTemplate; }
    public boolean isBadWordsPlayerSoundEnabled() { return badWordsPlayerSoundEnabled; }
    public String getBadWordsPlayerSoundName() { return badWordsPlayerSoundName; }
    public float getBadWordsPlayerSoundVolume() { return badWordsPlayerSoundVolume; }
    public float getBadWordsPlayerSoundPitch() { return badWordsPlayerSoundPitch; }
    public boolean isBadWordsAdminSoundEnabled() { return badWordsAdminSoundEnabled; }
    public String getBadWordsAdminSoundName() { return badWordsAdminSoundName; }
    public float getBadWordsAdminSoundVolume() { return badWordsAdminSoundVolume; }
    public float getBadWordsAdminSoundPitch() { return badWordsAdminSoundPitch; }
    public boolean isBadWordsConsoleNotificationsEnabled() { return badWordsConsoleNotificationsEnabled; }
    public String getBadWordsConsoleNotifyMessage() { return badWordsConsoleNotifyMessage; }
    public String getBadWordsConsoleWordsSeparator() { return badWordsConsoleWordsSeparator; }
    public String getBadWordsConsoleWordTemplate() { return badWordsConsoleWordTemplate; }
    public String getBadWordsConsoleSingleWordTemplate() { return badWordsConsoleSingleWordTemplate; }
    public boolean isLinksNotificationsEnabled() { return linksNotificationsEnabled; }
    public boolean isLinksChatEnabled() { return linksChatEnabled; }
    public List<String> getLinksFilterWarningMessage() { return linksFilterWarningMessage; }
    public boolean isLinksTitleEnabled() { return linksTitleEnabled; }
    public String getLinksTitleText() { return linksTitleText; }
    public long getLinksTitleFadeInTicks() { return linksTitleFadeInTicks; }
    public long getLinksTitleStayTicks() { return linksTitleStayTicks; }
    public long getLinksTitleFadeOutTicks() { return linksTitleFadeOutTicks; }
    public boolean isLinksActionBarEnabled() { return linksActionBarEnabled; }
    public String getLinksActionBarText() { return linksActionBarText; }
    public String getLinksAdminNotifyMessage() { return linksAdminNotifyMessage; }
    public String getLinksAdminLinksSeparator() { return linksAdminLinksSeparator; }
    public String getLinksAdminLinkTemplate() { return linksAdminLinkTemplate; }
    public String getLinksAdminSingleLinkTemplate() { return linksAdminSingleLinkTemplate; }
    public boolean isLinksPlayerSoundEnabled() { return linksPlayerSoundEnabled; }
    public String getLinksPlayerSoundName() { return linksPlayerSoundName; }
    public float getLinksPlayerSoundVolume() { return linksPlayerSoundVolume; }
    public float getLinksPlayerSoundPitch() { return linksPlayerSoundPitch; }
    public boolean isLinksAdminSoundEnabled() { return linksAdminSoundEnabled; }
    public String getLinksAdminSoundName() { return linksAdminSoundName; }
    public float getLinksAdminSoundVolume() { return linksAdminSoundVolume; }
    public float getLinksAdminSoundPitch() { return linksAdminSoundPitch; }
    public boolean isLinksConsoleNotificationsEnabled() { return linksConsoleNotificationsEnabled; }
    public String getLinksConsoleNotifyMessage() { return linksConsoleNotifyMessage; }
    public String getLinksConsoleLinksSeparator() { return linksConsoleLinksSeparator; }
    public String getLinksConsoleLinkTemplate() { return linksConsoleLinkTemplate; }
    public String getLinksConsoleSingleLinkTemplate() { return linksConsoleSingleLinkTemplate; }
    public boolean isBadWordsPunishmentsEnabled() { return badWordsPunishmentsEnabled; }
    public boolean isBadWordsPunishmentLogsEnabled() { return badWordsPunishmentLogsEnabled; }
    public String getBadWordsPunishmentLogMessage() { return badWordsPunishmentLogMessage; }
    public String getBadWordsPunishmentWordsSeparator() { return badWordsPunishmentWordsSeparator; }
    public String getBadWordsPunishmentWordTemplate() { return badWordsPunishmentWordTemplate; }
    public String getBadWordsPunishmentSingleWordTemplate() { return badWordsPunishmentSingleWordTemplate; }
    public String getBadWordsPunishmentCommandsSeparator() { return badWordsPunishmentCommandsSeparator; }
    public String getBadWordsPunishmentCommandTemplate() { return badWordsPunishmentCommandTemplate; }
    public String getBadWordsPunishmentSingleCommandTemplate() { return badWordsPunishmentSingleCommandTemplate; }
    public List<String> getBadWordsExceptionPlayers() { return badWordsExceptionPlayers; }
    public List<String> getBadWordsExceptionGroups() { return badWordsExceptionGroups; }
    public String getBadWordsBypassPermission() { return badWordsBypassPermission; }
    public boolean isLinksPunishmentsEnabled() { return linksPunishmentsEnabled; }
    public boolean isLinksPunishmentLogsEnabled() { return linksPunishmentLogsEnabled; }
    public String getLinksPunishmentLogMessage() { return linksPunishmentLogMessage; }
    public String getLinksPunishmentLinksSeparator() { return linksPunishmentLinksSeparator; }
    public String getLinksPunishmentLinkTemplate() { return linksPunishmentLinkTemplate; }
    public String getLinksPunishmentSingleLinkTemplate() { return linksPunishmentSingleLinkTemplate; }
    public String getLinksPunishmentCommandsSeparator() { return linksPunishmentCommandsSeparator; }
    public String getLinksPunishmentCommandTemplate() { return linksPunishmentCommandTemplate; }
    public String getLinksPunishmentSingleCommandTemplate() { return linksPunishmentSingleCommandTemplate; }
    public List<String> getLinksExceptionPlayers() { return linksExceptionPlayers; }
    public List<String> getLinksExceptionGroups() { return linksExceptionGroups; }
    public String getLinksBypassPermission() { return linksBypassPermission; }
}