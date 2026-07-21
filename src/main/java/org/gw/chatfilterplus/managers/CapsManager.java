package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class CapsManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;

    private final AtomicReference<Set<String>> whitelist = new AtomicReference<>(Set.of());

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
        whitelist.set(Collections.unmodifiableSet(set));
    }

    public boolean isCaps(String message) {
        if (message == null || message.isEmpty()) return false;

        int minLength = getEffectiveMinLength();
        boolean ignoreNonLetters = configManager.isCapsIgnoreNonLetters();
        Set<String> currentWhitelist = whitelist.get();

        for (String word : message.split("\\s+")) {
            if (isWhitelistedOrTooShort(word, currentWhitelist, ignoreNonLetters, minLength)) continue;
            if (isCapsWord(word, ignoreNonLetters)) return true;
        }
        return false;
    }

    public String fixCaps(String message) {
        if (message == null || message.isEmpty()) return message;

        int minLength = getEffectiveMinLength();
        boolean ignoreNonLetters = configManager.isCapsIgnoreNonLetters();
        Set<String> currentWhitelist = whitelist.get();

        StringBuilder result = new StringBuilder(message.length());
        String[] parts = message.split("(?<=\\s)|(?=\\s)");

        for (String part : parts) {
            if (part.trim().isEmpty()) {
                result.append(part);
                continue;
            }

            if (isWhitelistedOrTooShort(part, currentWhitelist, ignoreNonLetters, minLength) ||
                    !isCapsWord(part, ignoreNonLetters)) {
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

    private boolean isWhitelistedOrTooShort(String word, Set<String> currentWhitelist,
                                            boolean ignoreNonLetters, int minLength) {
        String clean = cleanWord(word, ignoreNonLetters);
        if (clean.isEmpty()) return true;

        if (currentWhitelist.contains(clean.toLowerCase())) return true;
        return clean.length() < minLength;
    }

    private boolean isCapsWord(String word, boolean ignoreNonLetters) {
        String clean = cleanWord(word, ignoreNonLetters);
        if (clean.length() < 2) return false;

        int upperCount = 0;
        for (char c : clean.toCharArray()) {
            if (Character.isUpperCase(c)) upperCount++;
        }

        if (upperCount <= 1) return false;

        double percent = (double) upperCount / clean.length() * 100;
        return percent >= configManager.getCapsMaxPercent();
    }

    private String cleanWord(String word, boolean ignoreNonLetters) {
        if (!ignoreNonLetters) return word;

        int len = word.length();
        StringBuilder clean = null;
        for (int i = 0; i < len; i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                if (clean != null) clean.append(c);
            } else if (clean == null) {
                clean = new StringBuilder(len);
                clean.append(word, 0, i);
            }
        }
        if (clean == null) return word;
        return clean.toString();
    }

    public boolean addWhitelistWord(String word) {
        if (word == null || word.trim().isEmpty()) return false;

        String lowerWord = word.trim().toLowerCase();
        Set<String> current = whitelist.get();

        if (current.contains(lowerWord)) return false;

        List<String> list = new ArrayList<>(configManager.getCapsWhitelist());
        list.add(lowerWord);

        configManager.getCapsConfig().set("filter.caps.whitelist", list);

        try {
            configManager.getCapsConfig().save(new java.io.File(plugin.getDataFolder(), "caps.yml"));
            loadWhitelist();
            plugin.log("Добавлено слово в вайтлист капса: &#ffff00" + lowerWord);
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при добавлении слова в вайтлиста капса: " + e.getMessage());
            return false;
        }
    }

    public boolean removeWhitelistWord(String word) {
        if (word == null || word.trim().isEmpty()) return false;

        String lowerWord = word.trim().toLowerCase();
        Set<String> current = whitelist.get();

        if (!current.contains(lowerWord)) return false;

        List<String> list = new ArrayList<>(configManager.getCapsWhitelist());
        list.removeIf(w -> w.equalsIgnoreCase(lowerWord));

        configManager.getCapsConfig().set("filter.caps.whitelist", list);

        try {
            configManager.getCapsConfig().save(new java.io.File(plugin.getDataFolder(), "caps.yml"));
            loadWhitelist();
            plugin.log("Удалено слово из вайтлиста капса: &#ffff00" + lowerWord);
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при удалении слова из вайтлиста капса: " + e.getMessage());
            return false;
        }
    }

    public void reload() {
        loadWhitelist();
    }
}