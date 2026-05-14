package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;
import org.gw.chatfilterplus.utils.PatternFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Getter
public class WordsManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final Map<Pattern, String> wordsMap = new ConcurrentHashMap<>();

    public WordsManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadWords() {
        wordsMap.clear();
        PatternFactory.clearCache();

        String filterLevel = configManager.getBadWordsFilterLevel();
        List<String> badWordsList = configManager.getBadWordsList();

        List<String> sortedWords = new ArrayList<>(badWordsList);
        sortedWords.sort((a, b) -> Integer.compare(b.length(), a.length()));

        for (String word : sortedWords) {
            try {
                Pattern pattern = PatternFactory.createPattern(word, filterLevel);
                wordsMap.put(pattern, word);
            } catch (Exception e) {
                plugin.console("&#FF5D00Ошибка создания паттерна для слова: " + word);
            }
        }

        plugin.log("Загружено &#ffff00" + wordsMap.size() + " &fплохих слов (уровень фильтра: &#ffff00" + filterLevel + "&f)");
    }

    public void reloadSafeWords() {
        plugin.getWordNormalizer().reload(configManager.getSafeWords());
    }

    public List<String> getBadWordsList() {
        return configManager.getBadWordsList();
    }

    public List<String> getSafeWords() {
        return configManager.getSafeWords();
    }

    public List<String> getBlockedWordsList() {
        return plugin.getBlockedWordsManager().getBlockedWordsList();
    }

    public boolean addBadWord(CommandSender sender, String word, String replacement) {
        if (word == null || word.trim().isEmpty()) return false;

        String trimmed = word.trim();
        FileConfiguration badWordsConfig = configManager.getBadWordsConfig();
        List<String> badWordsList = badWordsConfig.getStringList("bad-words");

        if (badWordsList.contains(trimmed)) {
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-word-already-exists", "").replace("{word}", trimmed));
            return false;
        }

        badWordsList.add(trimmed);
        badWordsConfig.set("bad-words", badWordsList);

        try {
            badWordsConfig.save(new File(plugin.getDataFolder(), "bad-words.yml"));
            loadWords();
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-bad-word-success", "").replace("{word}", trimmed));
            plugin.log("Добавлено плохое слово &#ffff00" + trimmed);
            return true;
        } catch (Exception e) {
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-word-failure", "").replace("{word}", trimmed));
            plugin.console("Ошибка при добавлении плохого слова &#ffff00" + trimmed);
            return false;
        }
    }

    public void addSafeWord(CommandSender sender, String word) {
        if (word == null || word.trim().isEmpty()) return;

        String trimmed = word.trim();
        FileConfiguration badWordsConfig = configManager.getBadWordsConfig();
        List<String> safeWords = badWordsConfig.getStringList("safe-words");

        if (safeWords.contains(trimmed)) {
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-safe-word-already-exists", "").replace("{word}", trimmed));
            return;
        }

        safeWords.add(trimmed);
        badWordsConfig.set("safe-words", safeWords);

        try {
            badWordsConfig.save(new File(plugin.getDataFolder(), "bad-words.yml"));
            reloadSafeWords();
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-safe-word-success", "").replace("{word}", trimmed));
            plugin.log("Добавлено безопасное слово &#ffff00" + trimmed);
        } catch (Exception e) {
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-word-failure", "").replace("{word}", trimmed));
            plugin.console("Ошибка при добавлении безопасного слова &#ffff00" + trimmed);
        }
    }

    public boolean removeBadWord(CommandSender sender, String word) {
        if (word == null || word.trim().isEmpty()) return false;

        String trimmed = word.trim();
        FileConfiguration badWordsConfig = configManager.getBadWordsConfig();
        List<String> badWordsList = badWordsConfig.getStringList("bad-words");

        if (!badWordsList.contains(trimmed)) return false;

        badWordsList.remove(trimmed);
        badWordsConfig.set("bad-words", badWordsList);

        try {
            badWordsConfig.save(new File(plugin.getDataFolder(), "bad-words.yml"));
            loadWords();
            return true;
        } catch (Exception e) {
            plugin.console("Ошибка при удалении плохого слова " + trimmed);
            return false;
        }
    }

    public boolean removeSafeWord(CommandSender sender, String word) {
        if (word == null || word.trim().isEmpty()) return false;

        String trimmed = word.trim();
        FileConfiguration badWordsConfig = configManager.getBadWordsConfig();
        List<String> safeWords = new ArrayList<>(badWordsConfig.getStringList("safe-words"));

        if (!safeWords.contains(trimmed)) return false;

        safeWords.remove(trimmed);
        badWordsConfig.set("safe-words", safeWords);

        try {
            badWordsConfig.save(new File(plugin.getDataFolder(), "bad-words.yml"));
            reloadSafeWords();
            return true;
        } catch (Exception e) {
            plugin.console("Ошибка при удалении безопасного слова " + trimmed);
            return false;
        }
    }

    public void reload() {
        loadWords();
    }
}