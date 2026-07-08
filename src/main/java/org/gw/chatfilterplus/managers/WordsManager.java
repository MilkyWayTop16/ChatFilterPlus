package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;
import org.gw.chatfilterplus.utils.ProfanityEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class WordsManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;

    private final AtomicReference<ProfanityEngine> engineRef =
            new AtomicReference<>(new ProfanityEngine(List.of(), List.of(), "high", true));

    public WordsManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public ProfanityEngine getEngine() {
        return engineRef.get();
    }

    public void loadWords() {
        List<String> badWordsList = configManager.getBadWordsList();
        List<String> safeWords = configManager.getSafeWords();
        String level = configManager.getBadWordsFilterLevel();
        boolean english = configManager.isBadWordsDetectEnglishLookalikes();

        ProfanityEngine engine = new ProfanityEngine(badWordsList, safeWords, level, english);
        engineRef.set(engine);

        plugin.log("Загружено &#ffff00" + badWordsList.size()
                + " &fплохих слов (уровень: &#ffff00" + level
                + "&f, lookalikes: &#ffff00" + english + "&f)");
    }

    public void reloadSafeWords() {
        loadWords();
        if (plugin.getWordNormalizer() != null) {
            plugin.getWordNormalizer().reload(configManager.getSafeWords());
        }
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

        String trimmed = word.trim().toLowerCase();
        FileConfiguration cfg = configManager.getBadWordsConfig();
        List<String> list = new ArrayList<>(cfg.getStringList("bad-words"));

        if (list.stream().anyMatch(w -> w.equalsIgnoreCase(trimmed))) {
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-word-already-exists", "").replace("{word}", trimmed));
            return false;
        }

        list.add(trimmed);
        cfg.set("bad-words", list);

        try {
            cfg.save(new File(plugin.getDataFolder(), "bad-words.yml"));
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
        FileConfiguration cfg = configManager.getBadWordsConfig();
        List<String> list = new ArrayList<>(cfg.getStringList("safe-words"));

        if (list.stream().anyMatch(w -> w.equalsIgnoreCase(trimmed))) {
            HexColors.sendMessage(sender, plugin.getConfig().getString("messages.add-safe-word-already-exists", "").replace("{word}", trimmed));
            return;
        }

        list.add(trimmed);
        cfg.set("safe-words", list);

        try {
            cfg.save(new File(plugin.getDataFolder(), "bad-words.yml"));
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
        FileConfiguration cfg = configManager.getBadWordsConfig();
        List<String> list = new ArrayList<>(cfg.getStringList("bad-words"));

        boolean removed = list.removeIf(w -> w.equalsIgnoreCase(trimmed));
        if (!removed) return false;

        cfg.set("bad-words", list);

        try {
            cfg.save(new File(plugin.getDataFolder(), "bad-words.yml"));
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
        FileConfiguration cfg = configManager.getBadWordsConfig();
        List<String> list = new ArrayList<>(cfg.getStringList("safe-words"));

        boolean removed = list.removeIf(w -> w.equalsIgnoreCase(trimmed));
        if (!removed) return false;

        cfg.set("safe-words", list);

        try {
            cfg.save(new File(plugin.getDataFolder(), "bad-words.yml"));
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
