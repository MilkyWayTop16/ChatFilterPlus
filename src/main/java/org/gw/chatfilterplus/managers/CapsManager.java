package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CapsManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private Set<String> whitelist;

    public CapsManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        loadWhitelist();
    }

    private void loadWhitelist() {
        Set<String> set = new HashSet<>();
        for (String word : configManager.getCapsWhitelist()) {
            if (word != null && !word.isEmpty()) {
                set.add(word.toLowerCase());
            }
        }
        this.whitelist = Collections.unmodifiableSet(set);
    }

    public boolean isCaps(String message) {
        if (message == null || message.isEmpty()) return false;

        int minLength = getEffectiveMinLength();
        boolean ignoreNonLetters = configManager.isCapsIgnoreNonLetters();

        for (String word : message.split("\\s+")) {
            if (isWhitelistedOrTooShort(word, ignoreNonLetters, minLength)) continue;
            if (isCapsWord(word, ignoreNonLetters)) return true;
        }
        return false;
    }

    public String fixCaps(String message) {
        if (message == null || message.isEmpty()) return message;

        int minLength = getEffectiveMinLength();
        boolean ignoreNonLetters = configManager.isCapsIgnoreNonLetters();

        StringBuilder result = new StringBuilder(message.length());
        String[] parts = message.split("(?<=\\s)|(?=\\s)");

        for (String part : parts) {
            if (part.trim().isEmpty()) {
                result.append(part);
                continue;
            }

            if (isWhitelistedOrTooShort(part, ignoreNonLetters, minLength) || !isCapsWord(part, ignoreNonLetters)) {
                result.append(part);
            } else {
                result.append(part.toLowerCase());
            }
        }
        return result.toString();
    }

    private int getEffectiveMinLength() {
        int minLength = configManager.getCapsMinLength();
        return minLength == -1 ? 1 : minLength;
    }

    private boolean isWhitelistedOrTooShort(String word, boolean ignoreNonLetters, int minLength) {
        String clean = ignoreNonLetters ? word.replaceAll("[^\\p{L}]", "") : word;
        if (clean.isEmpty()) return true;

        String lowerClean = clean.toLowerCase();
        if (whitelist.contains(lowerClean)) return true;

        return clean.length() < minLength;
    }

    private boolean isCapsWord(String word, boolean ignoreNonLetters) {
        String clean = ignoreNonLetters ? word.replaceAll("[^\\p{L}]", "") : word;
        if (clean.length() < 2) return false;

        int upperCount = 0;
        for (char c : clean.toCharArray()) {
            if (Character.isUpperCase(c)) upperCount++;
        }

        if (upperCount <= 1) return false;

        double percent = (double) upperCount / clean.length() * 100;
        return percent >= configManager.getCapsMaxPercent();
    }

    public boolean addWhitelistWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String lowerWord = word.trim().toLowerCase();

        if (whitelist.contains(lowerWord)) {
            return false;
        }

        List<String> currentWhitelist = new ArrayList<>(configManager.getCapsWhitelist());
        currentWhitelist.add(lowerWord);

        configManager.getCapsConfig().set("filter.caps.whitelist", currentWhitelist);

        try {
            configManager.getCapsConfig().save(new java.io.File(plugin.getDataFolder(), "caps.yml"));
            reload();
            plugin.log("Добавлено слово в whitelist капса: &#ffff00" + lowerWord);
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при добавлении слова в caps whitelist: " + e.getMessage());
            return false;
        }
    }

    public boolean removeWhitelistWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String lowerWord = word.trim().toLowerCase();

        List<String> currentWhitelist = new ArrayList<>(configManager.getCapsWhitelist());
        boolean removed = currentWhitelist.removeIf(w -> w.equalsIgnoreCase(lowerWord));

        if (!removed) return false;

        configManager.getCapsConfig().set("filter.caps.whitelist", currentWhitelist);

        try {
            configManager.getCapsConfig().save(new java.io.File(plugin.getDataFolder(), "caps.yml"));
            reload();
            plugin.log("Удалено слово из whitelist капса: &#ffff00" + lowerWord);
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при удалении слова из caps whitelist: " + e.getMessage());
            return false;
        }
    }

    public void reload() {
        loadWhitelist();
    }
}