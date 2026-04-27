package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Getter
public class ConfigManager {

    private final ChatFilterPlus plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration badWordsConfig;
    private FileConfiguration linksConfig;
    private FileConfiguration capsConfig;
    private FileConfiguration blockedWordsConfig;
    private FileConfiguration antiSpamConfig;

    private final File configFile;
    private final File badWordsFile;
    private final File linksFile;
    private final File capsFile;
    private final File blockedWordsFile;
    private final File antiSpamFile;

    private boolean updateCheckerEnabled;
    private String updateNotifyMode;
    private int updatePeriodicIntervalHours;
    private boolean consoleLogsEnabled;
    private boolean bStatsEnabled;
    private int cacheMaxSize;
    private long cacheCleanupRetentionMillis;
    private String compatibilityEventPriority;
    private boolean compatibilityAggressiveMode;
    private boolean commandFilteringEnabled;
    private List<String> commandFilteringCommands;

    private boolean antiSpamEnabled;
    private boolean generalCooldownEnabled;
    private int generalCooldownSeconds;
    private int generalCooldownIgnoreIfLongerThan;
    private boolean similarMessageCooldownEnabled;
    private int similarMessageCooldownSeconds;
    private int similarMessageSimilarityPercent;
    private int generalCooldownMinLength;
    private int similarMessageCooldownIgnoreIfLongerThan;
    private int similarMessageCooldownMinLength;
    private boolean characterFloodEnabled;
    private int characterFloodMaxRepeatingChars;
    private int characterFloodMaxRepeatingPattern;
    private int characterFloodCooldownSeconds;

    private boolean badWordsFilterEnabled;
    private String badWordsFilterMode;
    private String badWordsFilterLevel;
    private String badWordsFilterReplacement;
    private List<String> badWordsExceptionPlayers;
    private List<String> badWordsExceptionGroups;

    private boolean linksFilterEnabled;
    private String linksFilterMode;
    private String linksFilterReplacement;
    private String linksRegex;
    private boolean linksListFilterEnabled;
    private String linksListFilterMode;
    private List<String> linksListFilterDomains;
    private List<String> linksExceptionPlayers;
    private List<String> linksExceptionGroups;

    private boolean capsFilterEnabled;
    private String capsFilterMode;
    private int capsMinLength;
    private int capsMaxPercent;
    private boolean capsIgnoreNonLetters;
    private String capsNotificationPriorityBadwords;
    private String capsFilterPriorityBadwords;
    private String capsNotificationPriorityBlockedwords;
    private String capsFilterPriorityBlockedwords;
    private List<String> capsExceptionPlayers;
    private List<String> capsExceptionGroups;

    private boolean blockedWordsFilterEnabled;
    private String blockedWordsFilterMode;
    private String blockedWordsFilterReplacement;
    private String blockedWordsFilterLevel;
    private List<String> blockedWordsExceptionPlayers;
    private List<String> blockedWordsExceptionGroups;

    private List<String> antiSpamExceptionPlayers;
    private List<String> antiSpamExceptionGroups;

    private final Map<String, List<String>> actionCache = new ConcurrentHashMap<>();

    private static final Pattern RETENTION_PATTERN = Pattern.compile("(\\d+)([smhdwy])");
    private final Object reloadLock = new Object();

    public ConfigManager(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.badWordsFile = new File(plugin.getDataFolder(), "bad-words.yml");
        this.linksFile = new File(plugin.getDataFolder(), "links.yml");
        this.capsFile = new File(plugin.getDataFolder(), "caps.yml");
        this.blockedWordsFile = new File(plugin.getDataFolder(), "blocked-words.yml");
        this.antiSpamFile = new File(plugin.getDataFolder(), "anti-spam.yml");
    }

    public void loadAllConfigs() {
        synchronized (reloadLock) {
            loadMainConfig();
            loadBadWordsConfig();
            loadLinksConfig();
            loadCapsConfig();
            loadBlockedWordsConfig();
            loadAntiSpamConfig();
            loadCommonSettings();
            cacheHotSettings();
            actionCache.clear();
        }
    }

    private void loadCommonSettings() {
        consoleLogsEnabled = mainConfig.getBoolean("settings.logs.console.enabled", false);
        cacheMaxSize = mainConfig.getInt("settings.cache.max-size", 1000);
        cacheCleanupRetentionMillis = parseRetentionPeriod(mainConfig.getString("settings.cache.cleanup.retention-period", "5m"));
        bStatsEnabled = mainConfig.getBoolean("settings.bstats.enabled", true);
        updateCheckerEnabled = mainConfig.getBoolean("settings.update-checker.enabled", true);
        updateNotifyMode = mainConfig.getString("settings.update-checker.notify-mode", "both").toLowerCase();
        updatePeriodicIntervalHours = mainConfig.getInt("settings.update-checker.periodic-interval-hours", 6);
        compatibilityEventPriority = mainConfig.getString("settings.compatibility.event-priority", "lowest").toLowerCase();
        compatibilityAggressiveMode = mainConfig.getBoolean("settings.compatibility.aggressive-mode", false);
        commandFilteringEnabled = mainConfig.getBoolean("settings.command-filtering.enabled", true);
        commandFilteringCommands = mainConfig.getStringList("settings.command-filtering.commands");

        antiSpamEnabled = antiSpamConfig.getBoolean("filter.anti-spam.enabled", true);
        generalCooldownEnabled = antiSpamConfig.getBoolean("filter.anti-spam.general-cooldown.enabled", true);
        generalCooldownSeconds = antiSpamConfig.getInt("filter.anti-spam.general-cooldown.seconds", 3);
        generalCooldownIgnoreIfLongerThan = antiSpamConfig.getInt("filter.anti-spam.general-cooldown.ignore-if-longer-than", -1);
        similarMessageCooldownEnabled = antiSpamConfig.getBoolean("filter.anti-spam.similar-message-cooldown.enabled", true);
        similarMessageCooldownSeconds = antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.seconds", 10);
        similarMessageSimilarityPercent = antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.similarity-percent", 75);
        generalCooldownMinLength = antiSpamConfig.getInt("filter.anti-spam.general-cooldown.min-length", -1);
        similarMessageCooldownIgnoreIfLongerThan = antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.ignore-if-longer-than", -1);
        similarMessageCooldownMinLength = antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.min-length", 10);
        characterFloodEnabled = antiSpamConfig.getBoolean("filter.anti-spam.character-flood.enabled", true);
        characterFloodMaxRepeatingChars = antiSpamConfig.getInt("filter.anti-spam.character-flood.max-repeating-chars", 5);
        characterFloodMaxRepeatingPattern = antiSpamConfig.getInt("filter.anti-spam.character-flood.max-repeating-pattern", 3);
        characterFloodCooldownSeconds = antiSpamConfig.getInt("filter.anti-spam.character-flood.cooldown-seconds", 8);
    }

    private void cacheHotSettings() {
        badWordsFilterEnabled = badWordsConfig.getBoolean("filter.bad-words.enabled", true);
        badWordsFilterMode = badWordsConfig.getString("filter.bad-words.mode", "send-and-notify").toLowerCase();
        badWordsFilterLevel = badWordsConfig.getString("filter.bad-words.level", "high").toLowerCase();
        badWordsFilterReplacement = badWordsConfig.getString("filter.bad-words.replacement", "*");
        badWordsExceptionPlayers = badWordsConfig.getStringList("filter.bad-words.exceptions.players");
        badWordsExceptionGroups = badWordsConfig.getStringList("filter.bad-words.exceptions.groups");

        linksFilterEnabled = linksConfig.getBoolean("filter.links.enabled", true);
        linksFilterMode = linksConfig.getString("filter.links.mode", "block-and-notify").toLowerCase();
        linksFilterReplacement = linksConfig.getString("filter.links.replacement", "&#FB8808[Ссылка удалена]&r");
        linksRegex = linksConfig.getString("filter.links.regex", "(?i)(?:h\\s*t\\s*t\\s*p\\s*s?://\\S+|\\S*\\b(?:https?://)?[\\w\\p{L}]+(?:[\\.\\,\\s\\u200B\\u200C\\u200D\\u2060\\uFEFF]+[\\w\\p{L}]+)+[\\.\\,\\s]*(?:ru|com|net|org|io|me|info|biz|co|edu|gov|pro|fun|club|xyz|online|shop|site|tech|store|live|app|blog|world|space|work|game|dev|tv|cc|us|uk|ca|au|de|fr|jp|cn|link|digital|agency|news|media|cloud|page|wiki|art|team|systems|solutions|community|academy|center|group|tools|today|best|win|vip|bet|stream|chat|email|life|company|co\\.uk|co\\.jp|org\\.uk|gov\\.uk|ac\\.uk|edu\\.au|gov\\.au|bit\\.ly|t\\.co|tinyurl\\.com|goo\\.gl)[/\\S]*)");
        linksListFilterEnabled = linksConfig.getBoolean("filter.links.list-filter.enabled", false);
        linksListFilterMode = linksConfig.getString("filter.links.list-filter.mode", "whitelist").toLowerCase();
        linksListFilterDomains = linksConfig.getStringList("filter.links.list-filter.domains");
        linksExceptionPlayers = linksConfig.getStringList("filter.links.exceptions.players");
        linksExceptionGroups = linksConfig.getStringList("filter.links.exceptions.groups");

        capsFilterEnabled = capsConfig.getBoolean("filter.caps.enabled", false);
        capsFilterMode = capsConfig.getString("filter.caps.mode", "replace-and-notify").toLowerCase();
        capsMinLength = capsConfig.getInt("filter.caps.min-length", 5);
        capsMaxPercent = capsConfig.getInt("filter.caps.max-caps-percent", 70);
        capsIgnoreNonLetters = capsConfig.getBoolean("filter.caps.ignore-non-letters", true);
        capsNotificationPriorityBadwords = capsConfig.getString("filter.caps.badwords-priority.notification-priority", "badwords").toLowerCase();
        capsFilterPriorityBadwords = capsConfig.getString("filter.caps.badwords-priority.filter-priority", "both").toLowerCase();
        capsNotificationPriorityBlockedwords = capsConfig.getString("filter.caps.blockedwords-priority.notification-priority", "blockedwords").toLowerCase();
        capsFilterPriorityBlockedwords = capsConfig.getString("filter.caps.blockedwords-priority.filter-priority", "both").toLowerCase();
        capsExceptionPlayers = capsConfig.getStringList("filter.caps.exceptions.players");
        capsExceptionGroups = capsConfig.getStringList("filter.caps.exceptions.groups");

        blockedWordsFilterEnabled = blockedWordsConfig.getBoolean("filter.blocked-words.enabled", true);
        blockedWordsFilterMode = blockedWordsConfig.getString("filter.blocked-words.mode", "block-and-notify").toLowerCase();
        blockedWordsFilterReplacement = blockedWordsConfig.getString("filter.blocked-words.replacement", "*");
        blockedWordsFilterLevel = blockedWordsConfig.getString("filter.blocked-words.level", "high").toLowerCase();
        blockedWordsExceptionPlayers = blockedWordsConfig.getStringList("filter.blocked-words.exceptions.players");
        blockedWordsExceptionGroups = blockedWordsConfig.getStringList("filter.blocked-words.exceptions.groups");

        antiSpamExceptionPlayers = antiSpamConfig.getStringList("filter.anti-spam.exceptions.players");
        antiSpamExceptionGroups = antiSpamConfig.getStringList("filter.anti-spam.exceptions.groups");
    }

    private void loadMainConfig() {
        if (!configFile.exists()) plugin.saveResource("config.yml", false);
        mainConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadBadWordsConfig() {
        if (!badWordsFile.exists()) plugin.saveResource("bad-words.yml", false);
        badWordsConfig = YamlConfiguration.loadConfiguration(badWordsFile);
    }

    private void loadLinksConfig() {
        if (!linksFile.exists()) plugin.saveResource("links.yml", false);
        linksConfig = YamlConfiguration.loadConfiguration(linksFile);
    }

    private void loadCapsConfig() {
        if (!capsFile.exists()) plugin.saveResource("caps.yml", false);
        capsConfig = YamlConfiguration.loadConfiguration(capsFile);
    }

    private void loadBlockedWordsConfig() {
        if (!blockedWordsFile.exists()) plugin.saveResource("blocked-words.yml", false);
        blockedWordsConfig = YamlConfiguration.loadConfiguration(blockedWordsFile);
    }

    private void loadAntiSpamConfig() {
        if (!antiSpamFile.exists()) plugin.saveResource("anti-spam.yml", false);
        antiSpamConfig = YamlConfiguration.loadConfiguration(antiSpamFile);
    }

    public boolean reload() {
        synchronized (reloadLock) {
            try {
                loadAllConfigs();
                return true;
            } catch (Exception e) {
                plugin.console("&#FF5D00Ошибка перезагрузки конфигов: " + e.getMessage());
                return false;
            }
        }
    }

    private long parseRetentionPeriod(String period) {
        if (period == null || period.trim().isEmpty()) return 5 * 60 * 1000L;

        long total = 0;
        java.util.regex.Matcher matcher = RETENTION_PATTERN.matcher(period.toLowerCase());
        while (matcher.find()) {
            try {
                long value = Long.parseLong(matcher.group(1));
                switch (matcher.group(2)) {
                    case "s" -> total += value * 1000L;
                    case "m" -> total += value * 60 * 1000L;
                    case "h" -> total += value * 3600 * 1000L;
                    case "d" -> total += value * 86400 * 1000L;
                    case "w" -> total += value * 604800 * 1000L;
                    case "y" -> total += value * 31536000 * 1000L;
                }
            } catch (NumberFormatException ignored) {}
        }
        return total == 0 ? 5 * 60 * 1000L : total;
    }

    public List<String> getActions(String path) {
        return actionCache.computeIfAbsent("main." + path, k -> mainConfig.getStringList("actions." + path));
    }

    public List<String> getBadWordsActions(String subPath) {
        return actionCache.computeIfAbsent("badwords." + subPath, k -> badWordsConfig.getStringList("notifications.bad-words." + subPath));
    }

    public List<String> getLinksActions(String subPath) {
        return actionCache.computeIfAbsent("links." + subPath, k -> linksConfig.getStringList("notifications.links." + subPath));
    }

    public List<String> getCapsActions(String subPath) {
        return actionCache.computeIfAbsent("caps." + subPath, k -> capsConfig.getStringList("notifications.caps." + subPath));
    }

    public List<String> getBlockedWordsActions(String subPath) {
        return actionCache.computeIfAbsent("blockedwords." + subPath, k -> blockedWordsConfig.getStringList("notifications.blocked-words." + subPath));
    }

    public void executeActions(Player player, String path) {
        executeActions(player, path, null);
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        List<String> actions = getActions(path);
        if (actions == null || actions.isEmpty()) return;
        executeActionList(player, actions, placeholders);
    }

    public void executeActions(CommandSender sender, String path) {
        executeActions(sender, path, null);
    }

    public void executeActions(CommandSender sender, String path, Map<String, String> placeholders) {
        Player player = sender instanceof Player p ? p : null;
        executeActions(player, path, placeholders);
    }

    public void executeActionsFromBadWords(Player player, String subPath, Map<String, String> placeholders) {
        List<String> actions = getBadWordsActions(subPath);
        if (actions == null || actions.isEmpty()) return;
        executeActionList(player, actions, placeholders);
    }

    public void executeActionsFromLinks(Player player, String subPath, Map<String, String> placeholders) {
        List<String> actions = getLinksActions(subPath);
        if (actions == null || actions.isEmpty()) return;
        executeActionList(player, actions, placeholders);
    }

    public void executeActionsFromCaps(Player player, String subPath, Map<String, String> placeholders) {
        List<String> actions = getCapsActions(subPath);
        if (actions == null || actions.isEmpty()) return;
        executeActionList(player, actions, placeholders);
    }

    public void executeActionsFromBlockedWords(Player player, String subPath, Map<String, String> placeholders) {
        List<String> actions = getBlockedWordsActions(subPath);
        if (actions == null || actions.isEmpty()) return;
        executeActionList(player, actions, placeholders);
    }

    public void executeActionsFromAntiSpam(Player player, String subPath, Map<String, String> placeholders) {
        List<String> actions = actionCache.computeIfAbsent("antispam." + subPath, k -> antiSpamConfig.getStringList("notifications.anti-spam." + subPath));
        if (actions == null || actions.isEmpty()) return;
        executeActionList(player, actions, placeholders);
    }

    private void executeActionList(Player player, List<String> actions, Map<String, String> placeholders) {
        Map<String, String> ph = new HashMap<>(placeholders != null ? placeholders : Collections.emptyMap());
        if (player != null) ph.put("player", player.getName());

        for (String action : actions) {
            String processed = action;
            for (Map.Entry<String, String> entry : ph.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            parseAndExecuteAction(player, processed.trim());
        }
    }

    private void parseAndExecuteAction(Player player, String actionLine) {
        if (!actionLine.startsWith("[")) return;

        int end = actionLine.indexOf("]");
        if (end == -1) return;

        String type = actionLine.substring(1, end).toLowerCase();
        String content = actionLine.substring(end + 1).trim();

        try {
            switch (type) {
                case "message" -> {
                    if (player != null) HexColors.sendMessage(player, content);
                    else plugin.console(content);
                }
                case "message-console" -> {
                    if (player != null) plugin.console(content);
                }
                case "broadcast" -> Bukkit.broadcastMessage(HexColors.translate(content));
                case "sound" -> executeSound(player, content);
                case "title" -> executeTitle(player, content, false);
                case "subtitle" -> executeTitle(player, content, true);
                case "actionbar" -> {
                    if (player != null) player.sendActionBar(HexColors.translateToComponent(content));
                }
                case "console-command" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), content);
                case "player-command" -> {
                    if (player != null) plugin.getServer().dispatchCommand(player, content);
                }
                case "effect" -> executePotionEffect(player, content);
                case "teleport" -> executeTeleport(player, content);
                case "give-item" -> executeGiveItem(player, content);
                default -> plugin.console("&#FF5D00Неизвестный тип действия: " + type);
            }
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка выполнения действия [" + type + "]: " + e.getMessage());
        }
    }

    private void executeSound(Player player, String content) {
        if (player == null) return;
        String[] parts = content.split("\\s+");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка воспроизведения звука: " + content);
        }
    }

    private void executeTitle(Player player, String content, boolean isSubtitle) {
        if (player == null) return;

        try {
            String[] parts = content.split(";", 4);
            String rawText = parts[0].trim();

            int fadeIn = parts.length > 1 ? parseIntSafe(parts[1], 10) : 10;
            int stay = parts.length > 2 ? parseIntSafe(parts[2], 70) : 70;
            int fadeOut = parts.length > 3 ? parseIntSafe(parts[3], 20) : 20;

            net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(fadeIn * 50L),
                    java.time.Duration.ofMillis(stay * 50L),
                    java.time.Duration.ofMillis(fadeOut * 50L)
            );

            if (rawText.contains("\n")) {
                String[] lines = rawText.split("\n", 2);

                net.kyori.adventure.text.Component titleComp = HexColors.translateToComponent(lines[0]);
                net.kyori.adventure.text.Component subtitleComp = HexColors.translateToComponent(lines[1]);

                if (isSubtitle) {
                    player.showTitle(net.kyori.adventure.title.Title.title(
                            net.kyori.adventure.text.Component.empty(),
                            subtitleComp,
                            times
                    ));
                } else {
                    player.showTitle(net.kyori.adventure.title.Title.title(
                            titleComp,
                            subtitleComp,
                            times
                    ));
                }
            } else {
                net.kyori.adventure.text.Component comp = HexColors.translateToComponent(rawText);

                player.sendTitlePart(net.kyori.adventure.title.TitlePart.TIMES, times);

                if (isSubtitle) {
                    player.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE, comp);
                } else {
                    player.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE, comp);
                }
            }
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка выполнения тайтла: " + content);
        }
    }

    private void executePotionEffect(Player player, String content) {
        if (player == null) return;
        String[] parts = content.split("\\s+");
        if (parts.length == 0) return;
        try {
            org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(parts[0].toUpperCase());
            int duration = parts.length > 1 ? Integer.parseInt(parts[1]) * 20 : 600;
            int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) - 1 : 0;
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amplifier));
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка применения эффекта: " + content);
        }
    }

    private void executeTeleport(Player player, String content) {
        if (player == null) return;
        String[] parts = content.split("\\s+");
        if (parts.length < 4) return;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            org.bukkit.World world = plugin.getServer().getWorld(parts[3]);
            if (world != null) player.teleport(new org.bukkit.Location(world, x, y, z));
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка телепортации: " + content);
        }
    }

    private void executeGiveItem(Player player, String content) {
        if (player == null) return;
        String[] parts = content.split("\\s+");
        if (parts.length < 2) return;
        try {
            org.bukkit.Material material = org.bukkit.Material.valueOf(parts[0].toUpperCase());
            int amount = Integer.parseInt(parts[1]);
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка выдачи предмета: " + content);
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<String> getSafeWords() { return badWordsConfig.getStringList("safe-words"); }
    public List<String> getBadWordsList() { return badWordsConfig.getStringList("bad-words"); }
    public List<String> getBlockedWordsList() { return blockedWordsConfig.getStringList("blocked-words"); }
    public List<String> getCapsWhitelist() { return capsConfig.getStringList("filter.caps.whitelist"); }
}