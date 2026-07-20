package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.profanity.ProfanityEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class BlockedWordsManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final AtomicReference<ProfanityEngine> engineRef =
            new AtomicReference<>(new ProfanityEngine(List.of(), List.of(), "high", false));
    private final AtomicReference<List<String>> wordsListRef =
            new AtomicReference<>(List.of());

    public BlockedWordsManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public ProfanityEngine getEngine() {
        return engineRef.get();
    }

    public void loadBlockedWords() {
        List<String> words = configManager.getBlockedWordsList();
        String level = configManager.getBlockedWordsFilterLevel();

        ProfanityEngine engine = new ProfanityEngine(words, List.of(), level, false,
                ProfanityEngine.PrecisionOptions.forLevel(level), plugin.protectedNameSupplier());
        engineRef.set(engine);
        wordsListRef.set(List.copyOf(words));
    }

    public List<String> getBlockedWordsList() {
        return new ArrayList<>(wordsListRef.get());
    }

    public boolean addBlockedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String trimmed = word.trim();
        List<String> current = wordsListRef.get();
        if (current.stream().anyMatch(w -> w.equalsIgnoreCase(trimmed))) {
            return false;
        }

        try {
            FileConfiguration cfg = configManager.getBlockedWordsConfig();
            List<String> list = new ArrayList<>(cfg.getStringList("blocked-words"));
            list.add(trimmed);
            cfg.set("blocked-words", list);
            cfg.save(new File(plugin.getDataFolder(), "blocked-words.yml"));

            loadBlockedWords();
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
        try {
            FileConfiguration cfg = configManager.getBlockedWordsConfig();
            List<String> list = new ArrayList<>(cfg.getStringList("blocked-words"));
            boolean removed = list.removeIf(w -> w.equalsIgnoreCase(trimmed));
            if (!removed) return false;

            cfg.set("blocked-words", list);
            cfg.save(new File(plugin.getDataFolder(), "blocked-words.yml"));

            loadBlockedWords();
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
