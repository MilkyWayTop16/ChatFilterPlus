package org.gw.chatfilterplus.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.AntiSpamResult;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AntiSpamManager {

    private static final int HISTORY_SIZE = 8;
    private static final long CLEANUP_INTERVAL_TICKS = 6000L;
    private static final long ENTRY_LIFETIME_MILLIS = 600_000L;
    private static final int MAX_LEVENSHTEIN_LENGTH_DIFF = 6;

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Deque<RecentMessage>> playerHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCharacterFlood = new ConcurrentHashMap<>();

    public AntiSpamManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        startCleanupTask();
    }

    public AntiSpamResult checkSpam(Player player, String message) {
        if (!configManager.isAntiSpamEnabled()) return null;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Deque<RecentMessage> history = playerHistory.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());

        AntiSpamResult floodResult = checkCharacterFlood(player, message, now);
        if (floodResult != null) return floodResult;

        AntiSpamResult result = checkSimilarMessage(history, message, now);
        if (result != null) return result;

        result = checkGeneralCooldown(history, message, now);
        if (result != null) return result;

        addToHistory(history, message, now);
        return null;
    }

    private AntiSpamResult checkGeneralCooldown(Deque<RecentMessage> history, String message, long now) {
        if (!configManager.isGeneralCooldownEnabled() || history.isEmpty()) return null;

        int minLength = configManager.getGeneralCooldownMinLength();
        if (minLength > 0 && message.length() < minLength) return null;

        int ignoreLength = configManager.getGeneralCooldownIgnoreIfLongerThan();
        if (ignoreLength > 0 && message.length() > ignoreLength) return null;

        RecentMessage last = history.peekLast();
        if (last == null) return null;

        long remaining = configManager.getGeneralCooldownSeconds() * 1000L - (now - last.timestamp);
        if (remaining > 0) {
            return new AntiSpamResult("general-cooldown", (int) Math.ceil(remaining / 1000.0));
        }
        return null;
    }

    private AntiSpamResult checkSimilarMessage(Deque<RecentMessage> history, String message, long now) {
        if (!configManager.isSimilarMessageCooldownEnabled()) return null;

        int minLength = configManager.getSimilarMessageCooldownMinLength();
        if (minLength > 0 && message.length() < minLength) return null;

        int ignoreLength = configManager.getSimilarMessageCooldownIgnoreIfLongerThan();
        if (ignoreLength > 0 && message.length() > ignoreLength) return null;

        String normalized = WordNormalizer.normalize(message, "low");

        for (RecentMessage prev : history) {
            if (isSimilar(normalized, prev.normalized)) {
                long remaining = configManager.getSimilarMessageCooldownSeconds() * 1000L - (now - prev.timestamp);
                if (remaining > 0) {
                    return new AntiSpamResult("similar-message-cooldown", (int) Math.ceil(remaining / 1000.0));
                }
            }
        }
        return null;
    }

    private boolean isSimilar(String msg1, String msg2) {
        if (msg1.equals(msg2)) return true;

        if (Math.abs(msg1.length() - msg2.length()) > MAX_LEVENSHTEIN_LENGTH_DIFF) {
            return false;
        }

        double jaccard = calculateJaccardSimilarity(msg1, msg2);
        int levenshtein = calculateLevenshteinDistance(msg1, msg2);

        if (msg1.length() < 8 || msg2.length() < 8) {
            return jaccard >= 0.85 || levenshtein <= 2;
        }

        return jaccard >= configManager.getSimilarMessageSimilarityPercent() / 100.0 || levenshtein <= 4;
    }

    private double calculateJaccardSimilarity(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        String[] words1 = s1.split("\\s+");
        String[] words2 = s2.split("\\s+");

        Set<String> set1 = new HashSet<>();
        for (String w : words1) {
            String cleaned = w.replaceAll("[^\\p{L}\\p{N}]", "");
            if (!cleaned.isEmpty()) set1.add(cleaned);
        }

        Set<String> set2 = new HashSet<>();
        for (String w : words2) {
            String cleaned = w.replaceAll("[^\\p{L}\\p{N}]", "");
            if (!cleaned.isEmpty()) set2.add(cleaned);
        }

        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        int unionSize = set1.size() + set2.size() - intersection.size();
        return unionSize == 0 ? 0.0 : (double) intersection.size() / unionSize;
    }

    private int calculateLevenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) return 0;
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        int len1 = s1.length();
        int len2 = s2.length();

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) prev[j] = j;

        int maxAllowed = (len1 < 8 || len2 < 8) ? 2 : 4;

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            int minInRow = i;

            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
                if (curr[j] < minInRow) minInRow = curr[j];
            }

            if (minInRow > maxAllowed) {
                return maxAllowed + 1;
            }

            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[len2];
    }

    private void addToHistory(Deque<RecentMessage> history, String message, long now) {
        history.addLast(new RecentMessage(now, message));
        while (history.size() > HISTORY_SIZE) history.removeFirst();
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldEntries, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    private void cleanupOldEntries() {
        long now = System.currentTimeMillis();

        lastCharacterFlood.entrySet().removeIf(entry -> now - entry.getValue() > ENTRY_LIFETIME_MILLIS);

        playerHistory.entrySet().removeIf(entry -> {
            Deque<RecentMessage> history = entry.getValue();
            history.removeIf(msg -> now - msg.timestamp > ENTRY_LIFETIME_MILLIS);
            return history.isEmpty();
        });
    }

    private AntiSpamResult checkCharacterFlood(Player player, String message, long now) {
        if (!configManager.isCharacterFloodEnabled()) return null;

        int maxChars = configManager.getCharacterFloodMaxRepeatingChars();
        int maxPattern = configManager.getCharacterFloodMaxRepeatingPattern();

        boolean isFlood = false;

        int currentStreak = 1;
        for (int i = 1; i < message.length(); i++) {
            if (message.charAt(i) == message.charAt(i - 1)) {
                currentStreak++;
                if (currentStreak > maxChars) {
                    isFlood = true;
                    break;
                }
            } else {
                currentStreak = 1;
            }
        }

        if (!isFlood && maxPattern > 1 && message.length() >= 4) {
            for (int len = 2; len <= 4; len++) {
                for (int i = 0; i <= message.length() - len * 2; i++) {
                    String pattern = message.substring(i, i + len);
                    int count = 1;
                    int pos = i + len;
                    while (pos + len <= message.length() && message.substring(pos, pos + len).equals(pattern)) {
                        count++;
                        pos += len;
                        if (count > maxPattern) {
                            isFlood = true;
                            break;
                        }
                    }
                    if (isFlood) break;
                }
                if (isFlood) break;
            }
        }

        if (!isFlood) return null;

        UUID uuid = player.getUniqueId();
        long last = lastCharacterFlood.getOrDefault(uuid, 0L);
        int cdSeconds = configManager.getCharacterFloodCooldownSeconds();
        long elapsed = now - last;

        if (elapsed < cdSeconds * 1000L) {
            int remaining = (int) Math.ceil((cdSeconds * 1000L - elapsed) / 1000.0);
            return new AntiSpamResult("character-flood", Math.max(1, remaining));
        }

        lastCharacterFlood.put(uuid, now);
        return new AntiSpamResult("character-flood-first", cdSeconds);
    }

    public void reload() {
        playerHistory.clear();
        lastCharacterFlood.clear();
    }

    private static class RecentMessage {
        final long timestamp;
        final String message;
        final String normalized;

        RecentMessage(long timestamp, String message) {
            this.timestamp = timestamp;
            this.message = message;
            this.normalized = WordNormalizer.normalize(message, "low");
        }
    }
}