package org.gw.chatfilterplus.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class WordsManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private Map<Pattern, String> wordsMap;
    private final File wordsFile;

    public WordsManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.wordsMap = new HashMap<>();
        this.wordsFile = new File(plugin.getDataFolder(), "words.yml");
    }

    public void loadWords() {
        wordsMap.clear();
        FileConfiguration wordsConfig = configManager.getWordsConfig();
        boolean consoleLogsEnabled = configManager.isConsoleLogsEnabled();
        String filterLevel = configManager.getBadWordsFilterLevel();

        if (wordsConfig.contains("words") && wordsConfig.getConfigurationSection("words") != null) {
            for (String key : wordsConfig.getConfigurationSection("words").getKeys(false)) {
                String value = wordsConfig.getString("words." + key);
                if (value != null) {
                    try {
                        String patternStr;
                        if ("low".equalsIgnoreCase(filterLevel)) {
                            patternStr = Pattern.quote(key);
                        } else if ("medium".equalsIgnoreCase(filterLevel)) {
                            patternStr = createMediumPattern(key);
                        } else {
                            patternStr = createHighPattern(key);
                        }
                        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                        wordsMap.put(pattern, key);
                        if (consoleLogsEnabled) {
                            plugin.getLogger().info("Загружено слово: " + key + " с заменой: " + value);
                        }
                    } catch (Exception e) {
                        if (consoleLogsEnabled) {
                            plugin.getLogger().log(Level.WARNING, "Ошибка при компиляции шаблона для слова '" + key + "': " + e.getMessage(), e);
                        }
                    }
                }
            }
        } else if (!wordsFile.exists()) {
            Map<String, String> defaultWords = new HashMap<>();
            defaultWords.put("хуй", "х*й");
            defaultWords.put("пиздец", "п****ц");
            defaultWords.put("блять", "б***ь");
            defaultWords.put("пидор", "п***р");
            defaultWords.put("пидорас", "п*****с");
            wordsConfig.createSection("words", defaultWords);
            try {
                configManager.saveWordsConfig();
                if (consoleLogsEnabled) {
                    plugin.getLogger().info("Создан новый words.yml с дефолтным списком слов.");
                }
                for (String key : defaultWords.keySet()) {
                    try {
                        String patternStr;
                        if ("low".equalsIgnoreCase(filterLevel)) {
                            patternStr = Pattern.quote(key);
                        } else if ("medium".equalsIgnoreCase(filterLevel)) {
                            patternStr = createMediumPattern(key);
                        } else {
                            patternStr = createHighPattern(key);
                        }
                        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                        wordsMap.put(pattern, key);
                        if (consoleLogsEnabled) {
                            plugin.getLogger().info("Загружено дефолтное слово: " + key + " с заменой: " + defaultWords.get(key));
                        }
                    } catch (Exception e) {
                        if (consoleLogsEnabled) {
                            plugin.getLogger().log(Level.WARNING, "Ошибка при компиляции шаблона для слова '" + key + "': " + e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                if (consoleLogsEnabled) {
                    plugin.getLogger().log(Level.WARNING, "Не удалось создать или сохранить words.yml: " + e.getMessage(), e);
                }
            }
        }

        if (consoleLogsEnabled) {
            plugin.getLogger().info("Загружено " + wordsMap.size() + " слов из words.yml");
        }
    }

    private String createMediumPattern(String word) {
        StringBuilder flexiblePattern = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            flexiblePattern.append(Pattern.quote(String.valueOf(word.charAt(i))));
            if (i < word.length() - 1) {
                flexiblePattern.append("[а-яА-Я]*");
            }
        }
        return "(?:[а-яА-Я]*?)(" + flexiblePattern + ")(?:[а-яА-Я]*)";
    }

    private String createHighPattern(String word) {
        StringBuilder flexiblePattern = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            flexiblePattern.append(Pattern.quote(String.valueOf(word.charAt(i))));
            if (i < word.length() - 1) {
                flexiblePattern.append("[\\s\\W]*");
            }
        }
        return "(?:[а-яА-Я]*?)(" + flexiblePattern + ")(?:[а-яА-Я]*)";
    }

    public Map<Pattern, String> getWordsMap() {
        return wordsMap;
    }

    public Map<String, String> getWords() {
        Map<String, String> words = new HashMap<>();
        FileConfiguration wordsConfig = configManager.getWordsConfig();
        if (wordsConfig.contains("words") && wordsConfig.getConfigurationSection("words") != null) {
            for (String key : wordsConfig.getConfigurationSection("words").getKeys(false)) {
                String value = wordsConfig.getString("words." + key);
                if (value != null) {
                    words.put(key, value);
                }
            }
        }
        return words;
    }

    public boolean addWord(CommandSender sender, String word, String replacement) {
        FileConfiguration wordsConfig = configManager.getWordsConfig();
        boolean consoleLogsEnabled = configManager.isConsoleLogsEnabled();

        if (wordsConfig.contains("words." + word)) {
            String existingReplacement = wordsConfig.getString("words." + word);
            if (existingReplacement != null && existingReplacement.equals(replacement)) {
                String errorMsg = plugin.getConfig().getString("messages.add-word-already-exists", "&#FB8808▶️ Ошибка! &fСлово &#FFFF00«{word}» &fс заменой на &#FFFF00«{replacement}» &fуже существует!");
                errorMsg = errorMsg.replace("{word}", word).replace("{replacement}", replacement);
                HexColors.sendMessage(sender, errorMsg);
                return false;
            }
        }

        wordsConfig.set("words." + word, replacement);
        try {
            configManager.saveWordsConfig();
            loadWords();
            String successMsg = plugin.getConfig().getString("messages.add-word-success", "&#FFFF00◆ &fСлово &#FFFF00«{word}» &fс заменой на &#FFFF00«{replacement}» &fуспешно добавлено!");
            successMsg = successMsg.replace("{word}", word).replace("{replacement}", replacement);
            HexColors.sendMessage(sender, successMsg);
            return true;
        } catch (Exception e) {
            if (consoleLogsEnabled) {
                plugin.getLogger().log(Level.WARNING, "Не удалось сохранить words.yml: " + e.getMessage(), e);
            }
            String errorMsg = plugin.getConfig().getString("messages.add-word-failure", "&#FB8808▶️ Ошибка! &fПочему-то &#FB8808не удалось &fдобавить слово &#FFFF00«{word}»&f...");
            errorMsg = errorMsg.replace("{word}", word);
            HexColors.sendMessage(sender, errorMsg);
            return false;
        }
    }

    public boolean removeWord(CommandSender sender, String word) {
        FileConfiguration wordsConfig = configManager.getWordsConfig();
        boolean consoleLogsEnabled = configManager.isConsoleLogsEnabled();

        if (!wordsConfig.contains("words." + word)) {
            String errorMsg = plugin.getConfig().getString("messages.remove-word-not-found", "&#FB8808▶️ Ошибка! &fСлово &#FFFF00«{word}» &fне найдено в списке!");
            errorMsg = errorMsg.replace("{word}", word);
            HexColors.sendMessage(sender, errorMsg);
            return false;
        }

        wordsConfig.set("words." + word, null);
        try {
            configManager.saveWordsConfig();
            loadWords();
            String successMsg = plugin.getConfig().getString("messages.remove-word-success", "&#FFFF00◆ &fСлово &#FFFF00«{word}» &fуспешно удалено!");
            successMsg = successMsg.replace("{word}", word);
            HexColors.sendMessage(sender, successMsg);
            return true;
        } catch (Exception e) {
            if (consoleLogsEnabled) {
                plugin.getLogger().log(Level.WARNING, "Не удалось сохранить words.yml: " + e.getMessage(), e);
            }
            String errorMsg = plugin.getConfig().getString("messages.remove-word-failure", "&#FB8808▶️ Ошибка! &fПочему-то &#FB8808не удалось &fудалить слово &#FFFF00«{word}»&f...");
            errorMsg = errorMsg.replace("{word}", word);
            HexColors.sendMessage(sender, errorMsg);
            return false;
        }
    }
}