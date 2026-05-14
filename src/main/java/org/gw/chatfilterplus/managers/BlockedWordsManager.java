package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.PatternFactory;

import java.io.File;
import java.util.*;
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
        PatternFactory.clearCache();

        String filterLevel = configManager.getBlockedWordsFilterLevel();
        List<String> words = configManager.getBlockedWordsList();

        List<String> sortedWords = new ArrayList<>(words);
        sortedWords.sort((a, b) -> Integer.compare(b.length(), a.length()));

        Map<Pattern, String> newMap = new ConcurrentHashMap<>();

        for (String word : sortedWords) {
            try {
                Pattern pattern = PatternFactory.createPattern(word, filterLevel);
                if (pattern != null) {
                    newMap.put(pattern, word);
                }
            } catch (Exception e) {
                plugin.console("&#FF5D00Ошибка при создании паттерна для запрещённого слова: " + word);
            }
        }

        blockedWordsMap.clear();
        blockedWordsMap.putAll(newMap);
    }

    public List<String> getBlockedWordsList() {
        return new ArrayList<>(blockedWordsMap.values());
    }

    public boolean addBlockedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String trimmed = word.trim();

        if (blockedWordsMap.values().stream().anyMatch(w -> w.equalsIgnoreCase(trimmed))) {
            return false;
        }

        try {
            Pattern pattern = PatternFactory.createPattern(trimmed, configManager.getBlockedWordsFilterLevel());

            FileConfiguration cfg = configManager.getBlockedWordsConfig();
            List<String> list = new ArrayList<>(cfg.getStringList("blocked-words"));
            list.add(trimmed);
            cfg.set("blocked-words", list);

            File file = new File(plugin.getDataFolder(), "blocked-words.yml");
            cfg.save(file);

            blockedWordsMap.put(pattern, trimmed);
            plugin.log("Добавлено запрещённое слово: &#ffff00" + trimmed);
            return true;

        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при добавлении запрещённого слова '" + trimmed + "': " + e.getMessage());
            return false;
        }
    }

    public boolean removeBlockedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String trimmed = word.trim();

        boolean removed = blockedWordsMap.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(trimmed));
        if (!removed) {
            return false;
        }

        try {
            FileConfiguration cfg = configManager.getBlockedWordsConfig();
            List<String> list = new ArrayList<>(cfg.getStringList("blocked-words"));
            list.removeIf(w -> w.equalsIgnoreCase(trimmed));
            cfg.set("blocked-words", list);

            File file = new File(plugin.getDataFolder(), "blocked-words.yml");
            cfg.save(file);

            plugin.log("Удалено запрещённое слово: &#ffff00" + trimmed);
            return true;

        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при удалении запрещённого слова '" + trimmed + "': " + e.getMessage());
            return false;
        }
    }

    public void reload() {
        loadBlockedWords();
    }
}