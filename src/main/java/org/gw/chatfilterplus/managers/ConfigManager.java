package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;
import org.gw.chatfilterplus.utils.PlaceholderUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private volatile boolean updateCheckerEnabled;
    private volatile String updateNotifyMode;
    private volatile int updatePeriodicIntervalHours;
    private volatile boolean consoleLogsEnabled;
    private volatile boolean bStatsEnabled;
    private volatile int cacheMaxSize;
    private volatile long cacheCleanupRetentionMillis;
    private volatile String compatibilityEventPriority;
    private volatile boolean compatibilityAggressiveMode;
    private volatile boolean commandFilteringEnabled;
    private volatile List<String> commandFilteringCommands;

    private volatile boolean antiSpamEnabled;
    private volatile boolean generalCooldownEnabled;
    private volatile int generalCooldownSeconds;
    private volatile int generalCooldownIgnoreIfLongerThan;
    private volatile boolean similarMessageCooldownEnabled;
    private volatile int similarMessageCooldownSeconds;
    private volatile int similarMessageSimilarityPercent;
    private volatile int generalCooldownMinLength;
    private volatile int similarMessageCooldownIgnoreIfLongerThan;
    private volatile int similarMessageCooldownMinLength;
    private volatile boolean characterFloodEnabled;
    private volatile int characterFloodMaxRepeatingChars;
    private volatile int characterFloodMaxRepeatingPattern;
    private volatile int characterFloodCooldownSeconds;

    private volatile boolean badWordsFilterEnabled;
    private volatile String badWordsFilterMode;
    private volatile String badWordsFilterLevel;
    private volatile String badWordsFilterReplacement;
    private volatile List<String> badWordsExceptionPlayers;
    private volatile List<String> badWordsExceptionGroups;

    private volatile boolean linksFilterEnabled;
    private volatile String linksFilterMode;
    private volatile String linksFilterReplacement;
    private volatile String linksRegex;
    private volatile boolean linksListFilterEnabled;
    private volatile String linksListFilterMode;
    private volatile List<String> linksListFilterDomains;
    private volatile List<String> linksExceptionPlayers;
    private volatile List<String> linksExceptionGroups;

    private volatile boolean capsFilterEnabled;
    private volatile String capsFilterMode;
    private volatile int capsMinLength;
    private volatile int capsMaxPercent;
    private volatile boolean capsIgnoreNonLetters;
    private volatile String capsNotificationPriorityBadwords;
    private volatile String capsFilterPriorityBadwords;
    private volatile String capsNotificationPriorityBlockedwords;
    private volatile String capsFilterPriorityBlockedwords;
    private volatile List<String> capsExceptionPlayers;
    private volatile List<String> capsExceptionGroups;

    private volatile boolean blockedWordsFilterEnabled;
    private volatile String blockedWordsFilterMode;
    private volatile String blockedWordsFilterReplacement;
    private volatile String blockedWordsFilterLevel;
    private volatile List<String> blockedWordsExceptionPlayers;
    private volatile List<String> blockedWordsExceptionGroups;

    private volatile List<String> antiSpamExceptionPlayers;
    private volatile List<String> antiSpamExceptionGroups;

    private volatile boolean adminSelfNotifyEnabled;

    private final Map<String, List<ParsedAction>> parsedActionCache = new ConcurrentHashMap<>();

    private static final Pattern RETENTION_PATTERN = Pattern.compile("(\\d+)([smhdwy])");
    private final Object reloadLock = new Object();

    private record ParsedAction(String type, String content) {}

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
            loadCommonSettingsInternal();
            cacheHotSettingsInternal();
            parsedActionCache.clear();
            preParseAllActions();
        }
    }

    private void preParseAllActions() {
        preParseSection("main", mainConfig.getConfigurationSection("actions"));
        preParseSection("badwords", badWordsConfig.getConfigurationSection("notifications.bad-words"));
        preParseSection("links", linksConfig.getConfigurationSection("notifications.links"));
        preParseSection("caps", capsConfig.getConfigurationSection("notifications.caps"));
        preParseSection("blockedwords", blockedWordsConfig.getConfigurationSection("notifications.blocked-words"));
        preParseSection("antispam", antiSpamConfig.getConfigurationSection("notifications.anti-spam"));
    }

    private void preParseSection(String prefix, org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (section.isList(key)) {
                String cacheKey = prefix + "." + key;
                List<String> raw = section.getStringList(key);
                List<ParsedAction> parsed = new ArrayList<>(raw.size());
                for (String line : raw) {
                    ParsedAction pa = parseActionLine(line);
                    if (pa != null) parsed.add(pa);
                }
                parsedActionCache.put(cacheKey, parsed);
            } else if (section.isConfigurationSection(key)) {
                preParseSection(prefix + "." + key, section.getConfigurationSection(key));
            }
        }
    }

    private ParsedAction parseActionLine(String actionLine) {
        if (actionLine == null || !actionLine.startsWith("[")) return null;
        int end = actionLine.indexOf("]");
        if (end == -1) return null;
        return new ParsedAction(actionLine.substring(1, end).toLowerCase(), actionLine.substring(end + 1));
    }

    private void loadCommonSettingsInternal() {
        consoleLogsEnabled = mainConfig.getBoolean("settings.logs.console.enabled", false);
        cacheMaxSize = mainConfig.getInt("settings.cache.max-size", 1000);
        cacheCleanupRetentionMillis = parseRetentionPeriod(mainConfig.getString("settings.cache.cleanup.retention-period", "5m"));
        bStatsEnabled = mainConfig.getBoolean("settings.bstats.enabled", true);
        updateCheckerEnabled = mainConfig.getBoolean("settings.update-checker.enabled", true);
        updateNotifyMode = mainConfig.getString("settings.update-checker.notify-mode", "both").toLowerCase();
        updatePeriodicIntervalHours = Math.max(1, mainConfig.getInt("settings.update-checker.periodic-interval-hours", 6));
        compatibilityEventPriority = mainConfig.getString("settings.compatibility.event-priority", "lowest").toLowerCase();
        compatibilityAggressiveMode = mainConfig.getBoolean("settings.compatibility.aggressive-mode", false);
        commandFilteringEnabled = mainConfig.getBoolean("settings.command-filtering.enabled", true);
        commandFilteringCommands = Collections.unmodifiableList(cleanStringList(mainConfig.getStringList("settings.command-filtering.commands")));

        adminSelfNotifyEnabled = mainConfig.getBoolean("settings.admin-self-notify.enabled", true);

        antiSpamEnabled = antiSpamConfig.getBoolean("filter.anti-spam.enabled", true);
        generalCooldownEnabled = antiSpamConfig.getBoolean("filter.anti-spam.general-cooldown.enabled", true);
        generalCooldownSeconds = Math.max(0, antiSpamConfig.getInt("filter.anti-spam.general-cooldown.seconds", 3));
        generalCooldownIgnoreIfLongerThan = antiSpamConfig.getInt("filter.anti-spam.general-cooldown.ignore-if-longer-than", -1);
        similarMessageCooldownEnabled = antiSpamConfig.getBoolean("filter.anti-spam.similar-message-cooldown.enabled", true);
        similarMessageCooldownSeconds = Math.max(0, antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.seconds", 10));
        similarMessageSimilarityPercent = Math.max(50, Math.min(100, antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.similarity-percent", 75)));
        generalCooldownMinLength = antiSpamConfig.getInt("filter.anti-spam.general-cooldown.min-length", -1);
        similarMessageCooldownIgnoreIfLongerThan = antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.ignore-if-longer-than", -1);
        similarMessageCooldownMinLength = Math.max(0, antiSpamConfig.getInt("filter.anti-spam.similar-message-cooldown.min-length", 10));
        characterFloodEnabled = antiSpamConfig.getBoolean("filter.anti-spam.character-flood.enabled", true);
        characterFloodMaxRepeatingChars = Math.max(2, antiSpamConfig.getInt("filter.anti-spam.character-flood.max-repeating-chars", 5));
        characterFloodMaxRepeatingPattern = Math.max(1, antiSpamConfig.getInt("filter.anti-spam.character-flood.max-repeating-pattern", 3));
        characterFloodCooldownSeconds = Math.max(0, antiSpamConfig.getInt("filter.anti-spam.character-flood.cooldown-seconds", 8));
    }

    private void cacheHotSettingsInternal() {
        badWordsFilterEnabled = badWordsConfig.getBoolean("filter.bad-words.enabled", true);
        badWordsFilterMode = badWordsConfig.getString("filter.bad-words.mode", "send-and-notify").toLowerCase();
        badWordsFilterLevel = badWordsConfig.getString("filter.bad-words.level", "high").toLowerCase();
        badWordsFilterReplacement = badWordsConfig.getString("filter.bad-words.replacement", "*");
        badWordsExceptionPlayers = cleanStringList(badWordsConfig.getStringList("filter.bad-words.exceptions.players"));
        badWordsExceptionGroups = cleanStringList(badWordsConfig.getStringList("filter.bad-words.exceptions.groups"));

        linksFilterEnabled = linksConfig.getBoolean("filter.links.enabled", true);
        linksFilterMode = linksConfig.getString("filter.links.mode", "block-and-notify").toLowerCase();
        linksFilterReplacement = linksConfig.getString("filter.links.replacement", "&#FB8808[Ссылка удалена]&r");
        linksRegex = linksConfig.getString("filter.links.regex", "(?i)(?:h\\s*t\\s*t\\s*p\\s*s?://\\S+|\\S*\\b(?:https?://)?[\\w\\p{L}]+(?:[\\.\\,\\s\\u200B\\u200C\\u200D\\u2060\\uFEFF]+[\\w\\p{L}]+)+[\\.\\,\\s]*(?:ru|com|net|org|io|me|info|biz|co|edu|gov|pro|fun|club|xyz|online|shop|site|tech|store|live|app|blog|world|space|work|game|dev|tv|cc|us|uk|ca|au|de|fr|jp|cn|link|digital|agency|news|media|cloud|page|wiki|art|team|systems|solutions|community|academy|center|group|tools|today|best|win|vip|bet|stream|chat|email|life|company|co\\.uk|co\\.jp|org\\.uk|gov\\.uk|ac\\.uk|edu\\.au|gov\\.au|bit\\.ly|t\\.co|tinyurl\\.com|goo\\.gl)[/\\S]*)");
        linksListFilterEnabled = linksConfig.getBoolean("filter.links.list-filter.enabled", false);
        linksListFilterMode = linksConfig.getString("filter.links.list-filter.mode", "whitelist").toLowerCase();
        linksListFilterDomains = cleanStringList(linksConfig.getStringList("filter.links.list-filter.domains"));
        linksExceptionPlayers = cleanStringList(linksConfig.getStringList("filter.links.exceptions.players"));
        linksExceptionGroups = cleanStringList(linksConfig.getStringList("filter.links.exceptions.groups"));

        capsFilterEnabled = capsConfig.getBoolean("filter.caps.enabled", false);
        capsFilterMode = capsConfig.getString("filter.caps.mode", "replace-and-notify").toLowerCase();
        capsMinLength = Math.max(1, capsConfig.getInt("filter.caps.min-length", 5));
        capsMaxPercent = Math.max(0, Math.min(100, capsConfig.getInt("filter.caps.max-caps-percent", 70)));
        capsIgnoreNonLetters = capsConfig.getBoolean("filter.caps.ignore-non-letters", true);
        capsNotificationPriorityBadwords = capsConfig.getString("filter.caps.badwords-priority.notification-priority", "badwords").toLowerCase();
        capsFilterPriorityBadwords = capsConfig.getString("filter.caps.badwords-priority.filter-priority", "both").toLowerCase();
        capsNotificationPriorityBlockedwords = capsConfig.getString("filter.caps.blockedwords-priority.notification-priority", "blockedwords").toLowerCase();
        capsFilterPriorityBlockedwords = capsConfig.getString("filter.caps.blockedwords-priority.filter-priority", "both").toLowerCase();
        capsExceptionPlayers = cleanStringList(capsConfig.getStringList("filter.caps.exceptions.players"));
        capsExceptionGroups = cleanStringList(capsConfig.getStringList("filter.caps.exceptions.groups"));

        blockedWordsFilterEnabled = blockedWordsConfig.getBoolean("filter.blocked-words.enabled", true);
        blockedWordsFilterMode = blockedWordsConfig.getString("filter.blocked-words.mode", "block-and-notify").toLowerCase();
        blockedWordsFilterReplacement = blockedWordsConfig.getString("filter.blocked-words.replacement", "*");
        blockedWordsFilterLevel = blockedWordsConfig.getString("filter.blocked-words.level", "high").toLowerCase();
        blockedWordsExceptionPlayers = cleanStringList(blockedWordsConfig.getStringList("filter.blocked-words.exceptions.players"));
        blockedWordsExceptionGroups = cleanStringList(blockedWordsConfig.getStringList("filter.blocked-words.exceptions.groups"));

        antiSpamExceptionPlayers = cleanStringList(antiSpamConfig.getStringList("filter.anti-spam.exceptions.players"));
        antiSpamExceptionGroups = cleanStringList(antiSpamConfig.getStringList("filter.anti-spam.exceptions.groups"));
    }

    private List<String> cleanStringList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) {
            if (s != null) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private List<String> cleanWordList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) {
            if (s != null) {
                String trimmed = s.trim().toLowerCase();
                if (trimmed.length() >= 2 && !result.contains(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableList(result);
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
                parsedActionCache.clear();
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

    public void executeActions(CommandSender sender, String path) {
        Player player = sender instanceof Player p ? p : null;
        executeActions(player, path, null);
    }

    public void executeActions(CommandSender sender, String path, Map<String, String> placeholders) {
        Player player = sender instanceof Player p ? p : null;
        executeActions(player, path, placeholders);
    }

    public List<ParsedAction> getParsedActions(String path) {
        if (path == null) return List.of();
        String key = path.startsWith("main.") ? path : "main." + path;
        return parsedActionCache.getOrDefault(key, List.of());
    }

    public List<ParsedAction> getBadWordsParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("badwords." + subPath, List.of());
    }

    public List<ParsedAction> getLinksParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("links." + subPath, List.of());
    }

    public List<ParsedAction> getCapsParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("caps." + subPath, List.of());
    }

    public List<ParsedAction> getBlockedWordsParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("blockedwords." + subPath, List.of());
    }

    public List<ParsedAction> getAntiSpamParsedActions(String subPath) {
        if (subPath == null) return List.of();
        List<ParsedAction> direct = parsedActionCache.get("antispam." + subPath);
        if (direct != null && !direct.isEmpty()) return direct;
        return parsedActionCache.getOrDefault("antispam.player." + subPath, List.of());
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        executeParsedActionList(player, getParsedActions(path), placeholders);
    }

    public void executeActionsFromBadWords(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getBadWordsParsedActions(subPath), placeholders);
    }

    public void executeActionsFromLinks(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getLinksParsedActions(subPath), placeholders);
    }

    public void executeActionsFromCaps(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getCapsParsedActions(subPath), placeholders);
    }

    public void executeActionsFromBlockedWords(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getBlockedWordsParsedActions(subPath), placeholders);
    }

    public void executeActionsFromAntiSpam(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getAntiSpamParsedActions(subPath), placeholders);
    }

    private void executeParsedActionList(Player player, List<ParsedAction> actions, Map<String, String> placeholders) {
        if (actions.isEmpty()) return;

        Map<String, String> ph = new HashMap<>(placeholders != null ? placeholders : Collections.emptyMap());
        if (player != null && !ph.containsKey("player")) ph.put("player", player.getName());

        for (ParsedAction action : actions) {
            String content = action.content();
            for (Map.Entry<String, String> e : ph.entrySet()) {
                content = content.replace("{" + e.getKey() + "}", e.getValue());
            }
            content = PlaceholderUtil.parse(player, content);
            executeParsedAction(player, action.type(), content);
        }
    }

    private void executeParsedAction(Player player, String type, String content) {
        try {
            switch (type) {
                case "message" -> { if (player != null) HexColors.sendMessage(player, content); }
                case "message-console" -> plugin.console(content);
                case "broadcast" -> Bukkit.broadcastMessage(HexColors.translate(content));
                case "sound" -> executeSound(player, content);
                case "title" -> executeTitle(player, content, false);
                case "subtitle" -> executeTitle(player, content, true);
                case "actionbar" -> { if (player != null) player.sendActionBar(HexColors.translateToComponent(content)); }
                case "console-command" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), content);
                case "player-command" -> { if (player != null) plugin.getServer().dispatchCommand(player, content); }
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
                player.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE, HexColors.translateToComponent(lines[0]));
                if (lines.length > 1) {
                    player.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE, HexColors.translateToComponent(lines[1]));
                }
            } else {
                net.kyori.adventure.text.Component comp = HexColors.translateToComponent(rawText);
                if (isSubtitle) {
                    player.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE, comp);
                } else {
                    player.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE, comp);
                }
            }
            player.sendTitlePart(net.kyori.adventure.title.TitlePart.TIMES, times);
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
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return defaultValue; }
    }

    public List<String> getSafeWords() {
        return cleanWordList(badWordsConfig.getStringList("safe-words"));
    }
    public List<String> getBadWordsList() {
        return cleanWordList(badWordsConfig.getStringList("bad-words"));
    }
    public List<String> getBlockedWordsList() {
        return cleanWordList(blockedWordsConfig.getStringList("blocked-words"));
    }
    public List<String> getCapsWhitelist() {
        return cleanStringList(capsConfig.getStringList("filter.caps.whitelist"));
    }
}