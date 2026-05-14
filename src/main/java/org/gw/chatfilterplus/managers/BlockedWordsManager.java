package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.PatternFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Getter
public class BlockedWordsManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final Map<Pattern, String> blockedWordsMap = new ConcurrentHashMap<>();

    public BlockedWordsManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadBlockedWords() {
        blockedWordsMap.clear();
        PatternFactory.clearCache();

        String filterLevel = configManager.getBlockedWordsFilterLevel();
        List<String> words = configManager.getBlockedWordsList();

        for (String word : words) {
            try {
                Pattern pattern = PatternFactory.createPattern(word, filterLevel);
                blockedWordsMap.put(pattern, word);
            } catch (Exception e) {
                plugin.console("&#FF5D00Ошибка при создании паттерна для запрещённого слова: " + word);
            }
        }
    }

    public List<String> getBlockedWordsList() {
        return new ArrayList<>(blockedWordsMap.values());
    }

    public boolean addBlockedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String trimmedWord = word.trim();

        if (blockedWordsMap.values().stream().anyMatch(w -> w.equalsIgnoreCase(trimmedWord))) {
            return false;
        }

        try {
            Pattern pattern = PatternFactory.createPattern(trimmedWord, configManager.getBlockedWordsFilterLevel());

            FileConfiguration cfg = configManager.getBlockedWordsConfig();
            List<String> list = new ArrayList<>(cfg.getStringList("blocked-words"));
            list.add(trimmedWord);
            cfg.set("blocked-words", list);

            File file = new File(plugin.getDataFolder(), "blocked-words.yml");
            cfg.save(file);

            blockedWordsMap.put(pattern, trimmedWord);
            plugin.log("Добавлено запрещённое слово: &#ffff00" + trimmedWord);
            return true;

        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при добавлении запрещённого слова '" + trimmedWord + "': " + e.getMessage());
            return false;
        }
    }

    public boolean removeBlockedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String trimmedWord = word.trim();

        boolean removedFromMap = blockedWordsMap.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(trimmedWord));
        if (!removedFromMap) return false;

        try {
            FileConfiguration cfg = configManager.getBlockedWordsConfig();
            List<String> list = new ArrayList<>(cfg.getStringList("blocked-words"));
            list.removeIf(w -> w.equalsIgnoreCase(trimmedWord));
            cfg.set("blocked-words", list);

            File file = new File(plugin.getDataFolder(), "blocked-words.yml");
            cfg.save(file);

            plugin.log("Удалено запрещённое слово: &#ffff00" + trimmedWord);
            return true;

        } catch (Exception e) {
            try {
                Pattern pattern = PatternFactory.createPattern(trimmedWord, configManager.getBlockedWordsFilterLevel());
                blockedWordsMap.put(pattern, trimmedWord);
            } catch (Exception ignored) {}

            plugin.console("&#FF5D00Ошибка при удалении запрещённого слова '" + trimmedWord + "': " + e.getMessage());
            return false;
        }
    }

    public void reload() {
        loadBlockedWords();
    }
}