package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.configs.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
public class ConfigManager {

    private final ChatFilterPlus plugin;
    private final MainConfig mainConfig;
    private final BadWordsConfig badWords;
    private final LinksConfig links;
    private final CapsConfig caps;
    private final BlockedWordsConfig blockedWords;
    private final AntiSpamConfig antiSpam;
    private final ActionManager actionManager;

    private final Object reloadLock = new Object();

    public ConfigManager(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.mainConfig = new MainConfig(plugin);
        this.badWords = new BadWordsConfig(plugin);
        this.links = new LinksConfig(plugin);
        this.caps = new CapsConfig(plugin);
        this.blockedWords = new BlockedWordsConfig(plugin);
        this.antiSpam = new AntiSpamConfig(plugin);
        this.actionManager = new ActionManager(plugin);
    }

    public void loadAllConfigs() {
        synchronized (reloadLock) {
            mainConfig.load();
            badWords.load();
            links.load();
            caps.load();
            blockedWords.load();
            antiSpam.load();
            actionManager.reload(mainConfig, badWords, links, caps, blockedWords, antiSpam);
        }
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

    public FileConfiguration getConfig() {
        return mainConfig.getConfig();
    }

    public FileConfiguration getBadWordsConfig() {
        return badWords.getConfig();
    }

    public FileConfiguration getLinksConfig() {
        return links.getConfig();
    }

    public FileConfiguration getCapsConfig() {
        return caps.getConfig();
    }

    public FileConfiguration getBlockedWordsConfig() {
        return blockedWords.getConfig();
    }

    public FileConfiguration getAntiSpamConfig() {
        return antiSpam.getConfig();
    }

    public FileConfiguration getConfig(FilterType type) {
        return type.config(this);
    }

    public boolean isFilterEnabled(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> badWords.isFilterEnabled();
            case LINKS -> links.isFilterEnabled();
            case CAPS -> caps.isFilterEnabled();
            case BLOCKED_WORDS -> blockedWords.isFilterEnabled();
            case ANTI_SPAM -> antiSpam.isFilterEnabled();
        };
    }

    public String getFilterMode(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> badWords.getFilterMode();
            case LINKS -> links.getFilterMode();
            case CAPS -> caps.getFilterMode();
            case BLOCKED_WORDS -> blockedWords.getFilterMode();
            case ANTI_SPAM -> "";
        };
    }

    public Set<String> getExceptionPlayers(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> badWords.getExceptionPlayers();
            case LINKS -> links.getExceptionPlayers();
            case CAPS -> caps.getExceptionPlayers();
            case BLOCKED_WORDS -> blockedWords.getExceptionPlayers();
            case ANTI_SPAM -> antiSpam.getExceptionPlayers();
        };
    }

    public Set<String> getExceptionGroups(FilterType type) {
        return switch (type) {
            case BAD_WORDS -> badWords.getExceptionGroups();
            case LINKS -> links.getExceptionGroups();
            case CAPS -> caps.getExceptionGroups();
            case BLOCKED_WORDS -> blockedWords.getExceptionGroups();
            case ANTI_SPAM -> antiSpam.getExceptionGroups();
        };
    }

    public void executeActions(FilterType type, Player player, String subPath, Map<String, String> placeholders) {
        switch (type) {
            case BAD_WORDS -> actionManager.executeActionsFromBadWords(player, subPath, placeholders);
            case LINKS -> actionManager.executeActionsFromLinks(player, subPath, placeholders);
            case CAPS -> actionManager.executeActionsFromCaps(player, subPath, placeholders);
            case BLOCKED_WORDS -> actionManager.executeActionsFromBlockedWords(player, subPath, placeholders);
            case ANTI_SPAM -> actionManager.executeActionsFromAntiSpam(player, subPath, placeholders);
        }
    }

    public boolean isConsoleLogsEnabled() {
        return mainConfig.isConsoleLogsEnabled();
    }

    public boolean isBStatsEnabled() {
        return mainConfig.isBStatsEnabled();
    }

    public boolean isUpdateCheckerEnabled() {
        return mainConfig.isUpdateCheckerEnabled();
    }

    public String getUpdateNotifyMode() {
        return mainConfig.getUpdateNotifyMode();
    }

    public int getUpdatePeriodicIntervalHours() {
        return mainConfig.getUpdatePeriodicIntervalHours();
    }

    public int getCacheMaxSize() {
        return mainConfig.getCacheMaxSize();
    }

    public long getCacheCleanupRetentionMillis() {
        return mainConfig.getCacheCleanupRetentionMillis();
    }

    public boolean isCacheCleanupEnabled() {
        return mainConfig.isCacheCleanupEnabled();
    }

    public String getCompatibilityEventPriority() {
        return mainConfig.getCompatibilityEventPriority();
    }

    public boolean isCompatibilityAggressiveMode() {
        return mainConfig.isCompatibilityAggressiveMode();
    }

    public boolean isCommandFilteringEnabled() {
        return mainConfig.isCommandFilteringEnabled();
    }

    public List<String> getCommandFilteringCommands() {
        return mainConfig.getCommandFilteringCommands();
    }

    public boolean isAdminSelfNotifyEnabled() {
        return mainConfig.isAdminSelfNotifyEnabled();
    }

    public boolean isBadWordsFilterEnabled() {
        return badWords.isFilterEnabled();
    }

    public String getBadWordsFilterMode() {
        return badWords.getFilterMode();
    }

    public String getBadWordsFilterLevel() {
        return badWords.getFilterLevel();
    }

    public String getBadWordsFilterReplacement() {
        return badWords.getFilterReplacement();
    }

    public boolean isBadWordsDetectEnglishLookalikes() {
        return badWords.isDetectEnglishLookalikes();
    }

    public Set<String> getBadWordsExceptionPlayers() {
        return badWords.getExceptionPlayers();
    }

    public Set<String> getBadWordsExceptionGroups() {
        return badWords.getExceptionGroups();
    }

    public List<String> getSafeWords() {
        return badWords.getSafeWords();
    }

    public List<String> getBadWordsList() {
        return badWords.getBadWordsList();
    }

    public boolean isLinksFilterEnabled() {
        return links.isFilterEnabled();
    }

    public String getLinksFilterMode() {
        return links.getFilterMode();
    }

    public String getLinksFilterReplacement() {
        return links.getFilterReplacement();
    }

    public String getLinksRegex() {
        return links.getLinksRegex();
    }

    public boolean isLinksListFilterEnabled() {
        return links.isListFilterEnabled();
    }

    public String getLinksListFilterMode() {
        return links.getListFilterMode();
    }

    public List<String> getLinksListFilterDomains() {
        return links.getListFilterDomains();
    }

    public Set<String> getLinksExceptionPlayers() {
        return links.getExceptionPlayers();
    }

    public Set<String> getLinksExceptionGroups() {
        return links.getExceptionGroups();
    }

    public boolean isCapsFilterEnabled() {
        return caps.isFilterEnabled();
    }

    public String getCapsFilterMode() {
        return caps.getFilterMode();
    }

    public int getCapsMinLength() {
        return caps.getMinLength();
    }

    public int getCapsMaxPercent() {
        return caps.getMaxPercent();
    }

    public boolean isCapsIgnoreNonLetters() {
        return caps.isIgnoreNonLetters();
    }

    public String getCapsNotificationPriorityBadwords() {
        return caps.getNotificationPriorityBadwords();
    }

    public String getCapsFilterPriorityBadwords() {
        return caps.getFilterPriorityBadwords();
    }

    public String getCapsNotificationPriorityBlockedwords() {
        return caps.getNotificationPriorityBlockedwords();
    }

    public String getCapsFilterPriorityBlockedwords() {
        return caps.getFilterPriorityBlockedwords();
    }

    public Set<String> getCapsExceptionPlayers() {
        return caps.getExceptionPlayers();
    }

    public Set<String> getCapsExceptionGroups() {
        return caps.getExceptionGroups();
    }

    public List<String> getCapsWhitelist() {
        return caps.getWhitelist();
    }

    public boolean isBlockedWordsFilterEnabled() {
        return blockedWords.isFilterEnabled();
    }

    public String getBlockedWordsFilterMode() {
        return blockedWords.getFilterMode();
    }

    public String getBlockedWordsFilterReplacement() {
        return blockedWords.getFilterReplacement();
    }

    public String getBlockedWordsFilterLevel() {
        return blockedWords.getFilterLevel();
    }

    public Set<String> getBlockedWordsExceptionPlayers() {
        return blockedWords.getExceptionPlayers();
    }

    public Set<String> getBlockedWordsExceptionGroups() {
        return blockedWords.getExceptionGroups();
    }

    public List<String> getBlockedWordsList() {
        return blockedWords.getBlockedWordsList();
    }

    public boolean isAntiSpamEnabled() {
        return antiSpam.isFilterEnabled();
    }

    public boolean isGeneralCooldownEnabled() {
        return antiSpam.isGeneralCooldownEnabled();
    }

    public int getGeneralCooldownSeconds() {
        return antiSpam.getGeneralCooldownSeconds();
    }

    public int getGeneralCooldownIgnoreIfLongerThan() {
        return antiSpam.getGeneralCooldownIgnoreIfLongerThan();
    }

    public int getGeneralCooldownMinLength() {
        return antiSpam.getGeneralCooldownMinLength();
    }

    public boolean isSimilarMessageCooldownEnabled() {
        return antiSpam.isSimilarMessageCooldownEnabled();
    }

    public int getSimilarMessageCooldownSeconds() {
        return antiSpam.getSimilarMessageCooldownSeconds();
    }

    public int getSimilarMessageSimilarityPercent() {
        return antiSpam.getSimilarMessageSimilarityPercent();
    }

    public int getSimilarMessageCooldownIgnoreIfLongerThan() {
        return antiSpam.getSimilarMessageCooldownIgnoreIfLongerThan();
    }

    public int getSimilarMessageCooldownMinLength() {
        return antiSpam.getSimilarMessageCooldownMinLength();
    }

    public boolean isCharacterFloodEnabled() {
        return antiSpam.isCharacterFloodEnabled();
    }

    public int getCharacterFloodMaxRepeatingChars() {
        return antiSpam.getCharacterFloodMaxRepeatingChars();
    }

    public int getCharacterFloodMaxRepeatingPattern() {
        return antiSpam.getCharacterFloodMaxRepeatingPattern();
    }

    public int getCharacterFloodCooldownSeconds() {
        return antiSpam.getCharacterFloodCooldownSeconds();
    }

    public Set<String> getAntiSpamExceptionPlayers() {
        return antiSpam.getExceptionPlayers();
    }

    public Set<String> getAntiSpamExceptionGroups() {
        return antiSpam.getExceptionGroups();
    }

    public void executeActions(CommandSender sender, String path) {
        actionManager.executeActions(sender, path);
    }

    public void executeActions(CommandSender sender, String path, Map<String, String> placeholders) {
        actionManager.executeActions(sender, path, placeholders);
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        actionManager.executeActions(player, path, placeholders);
    }

    public void executeActionsFromBadWords(Player player, String subPath, Map<String, String> placeholders) {
        actionManager.executeActionsFromBadWords(player, subPath, placeholders);
    }

    public void executeActionsFromLinks(Player player, String subPath, Map<String, String> placeholders) {
        actionManager.executeActionsFromLinks(player, subPath, placeholders);
    }

    public void executeActionsFromCaps(Player player, String subPath, Map<String, String> placeholders) {
        actionManager.executeActionsFromCaps(player, subPath, placeholders);
    }

    public void executeActionsFromBlockedWords(Player player, String subPath, Map<String, String> placeholders) {
        actionManager.executeActionsFromBlockedWords(player, subPath, placeholders);
    }

    public void executeActionsFromAntiSpam(Player player, String subPath, Map<String, String> placeholders) {
        actionManager.executeActionsFromAntiSpam(player, subPath, placeholders);
    }

    public List<ActionManager.ParsedAction> getParsedActions(String path) {
        return actionManager.getParsedActions(path);
    }

    public List<ActionManager.ParsedAction> getBadWordsParsedActions(String subPath) {
        return actionManager.getBadWordsParsedActions(subPath);
    }

    public List<ActionManager.ParsedAction> getLinksParsedActions(String subPath) {
        return actionManager.getLinksParsedActions(subPath);
    }

    public List<ActionManager.ParsedAction> getCapsParsedActions(String subPath) {
        return actionManager.getCapsParsedActions(subPath);
    }

    public List<ActionManager.ParsedAction> getBlockedWordsParsedActions(String subPath) {
        return actionManager.getBlockedWordsParsedActions(subPath);
    }

    public List<ActionManager.ParsedAction> getAntiSpamParsedActions(String subPath) {
        return actionManager.getAntiSpamParsedActions(subPath);
    }
}
