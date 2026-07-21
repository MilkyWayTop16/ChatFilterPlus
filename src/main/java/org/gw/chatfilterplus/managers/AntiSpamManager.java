package org.gw.chatfilterplus.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.AntiSpamResult;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

public class AntiSpamManager {

    private static final int HISTORY_SIZE = 8;
    private static final long CLEANUP_INTERVAL_TICKS = 6000L;
    private static final long ENTRY_LIFETIME_MILLIS = 600_000L;
    private static final long DEDUPE_WINDOW_MILLIS = 150L;
    private static final int MAX_LEVENSHTEIN_LENGTH_DIFF = 6;

    private static final int SHORT_MESSAGE_LENGTH = 8;
    private static final double SHORT_MESSAGE_JACCARD = 0.85;
    private static final int SHORT_MESSAGE_MAX_DISTANCE = 2;
    private static final int LONG_MESSAGE_MAX_DISTANCE = 4;

    private static final int MIN_FLOOD_PATTERN_LENGTH = 2;
    private static final int MAX_FLOOD_PATTERN_LENGTH = 4;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]");

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Deque<RecentMessage>> playerHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCharacterFlood = new ConcurrentHashMap<>();
    private final Map<UUID, DedupeEntry> recentChecks = new ConcurrentHashMap<>();

    public AntiSpamManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        startCleanupTask();
    }

    public AntiSpamResult checkSpam(Player player, String message) {
        if (!configManager.isAntiSpamEnabled() || player == null || message == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        RecentMessage current = new RecentMessage(now, message);

        DedupeEntry dedupe = recentChecks.get(uuid);
        if (dedupe != null
                && now - dedupe.time <= DEDUPE_WINDOW_MILLIS
                && dedupe.normalized.equals(current.normalized)) {
            return dedupe.result == null ? null : dedupe.result.asDuplicate();
        }

        AntiSpamResult result = checkNewPlayerChatLock(player, now);
        if (result == null) {
            result = checkCharacterFlood(uuid, message, now);
        }
        if (result == null) {
            Deque<RecentMessage> history = playerHistory.computeIfAbsent(uuid, id -> new ConcurrentLinkedDeque<>());
            result = checkSimilarMessage(history, current, message.length(), now);
            if (result == null) {
                result = checkGeneralCooldown(history, message, now);
            }
            if (result == null) {
                addToHistory(history, current);
            }
        }

        recentChecks.put(uuid, new DedupeEntry(current.normalized, now, result));
        return result;
    }

    private AntiSpamResult checkNewPlayerChatLock(Player player, long now) {
        if (!configManager.isNewPlayerChatLockEnabled()) return null;

        long required = configManager.getNewPlayerChatLockMillis();
        if (required <= 0L) return null;

        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed <= 0L) {
            firstPlayed = now;
        }

        long elapsed = now - firstPlayed;
        if (elapsed >= required) return null;

        int remainingSeconds = (int) Math.max(1L, (required - elapsed + 999L) / 1000L);
        return new AntiSpamResult("new-player-chat-lock", remainingSeconds);
    }

    private AntiSpamResult checkGeneralCooldown(Deque<RecentMessage> history, String message, long now) {
        if (!configManager.isGeneralCooldownEnabled() || history.isEmpty()) return null;

        if (!isLengthInRange(message.length(),
                configManager.getGeneralCooldownMinLength(),
                configManager.getGeneralCooldownIgnoreIfLongerThan())) {
            return null;
        }

        RecentMessage last = history.peekLast();
        if (last == null) return null;

        long remaining = configManager.getGeneralCooldownSeconds() * 1000L - (now - last.timestamp);
        if (remaining > 0) {
            return new AntiSpamResult("general-cooldown", (int) Math.ceil(remaining / 1000.0));
        }
        return null;
    }

    private AntiSpamResult checkSimilarMessage(Deque<RecentMessage> history, RecentMessage current,
                                               int rawLength, long now) {
        if (!configManager.isSimilarMessageCooldownEnabled() || history.isEmpty()) return null;

        if (!isLengthInRange(rawLength,
                configManager.getSimilarMessageCooldownMinLength(),
                configManager.getSimilarMessageCooldownIgnoreIfLongerThan())) {
            return null;
        }

        for (RecentMessage previous : history) {
            if (!isSimilar(current, previous)) continue;

            long remaining = configManager.getSimilarMessageCooldownSeconds() * 1000L - (now - previous.timestamp);
            if (remaining > 0) {
                return new AntiSpamResult("similar-message-cooldown", (int) Math.ceil(remaining / 1000.0));
            }
        }
        return null;
    }

    private boolean isLengthInRange(int length, int minLength, int ignoreIfLongerThan) {
        if (minLength > 0 && length < minLength) return false;
        return ignoreIfLongerThan <= 0 || length <= ignoreIfLongerThan;
    }

    private boolean isSimilar(RecentMessage current, RecentMessage previous) {
        String a = current.normalized;
        String b = previous.normalized;

        if (a.equals(b)) return true;
        if (Math.abs(a.length() - b.length()) > MAX_LEVENSHTEIN_LENGTH_DIFF) return false;

        boolean shortMessage = a.length() < SHORT_MESSAGE_LENGTH || b.length() < SHORT_MESSAGE_LENGTH;

        double requiredJaccard = shortMessage
                ? SHORT_MESSAGE_JACCARD
                : configManager.getSimilarMessageSimilarityPercent() / 100.0;

        if (calculateJaccardSimilarity(current.tokens, previous.tokens) >= requiredJaccard) return true;

        int maxDistance = shortMessage ? SHORT_MESSAGE_MAX_DISTANCE : LONG_MESSAGE_MAX_DISTANCE;
        return calculateLevenshteinDistance(a, b, maxDistance) <= maxDistance;
    }

    private double calculateJaccardSimilarity(Set<String> tokens1, Set<String> tokens2) {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0;

        int intersection = 0;
        for (String token : tokens1) {
            if (tokens2.contains(token)) intersection++;
        }

        int unionSize = tokens1.size() + tokens2.size() - intersection;
        return unionSize == 0 ? 0.0 : (double) intersection / unionSize;
    }

    private int calculateLevenshteinDistance(String s1, String s2, int maxDistance) {
        if (s1.equals(s2)) return 0;
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        int len1 = s1.length();
        int len2 = s2.length();

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) prev[j] = j;

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            int minInRow = i;

            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
                if (curr[j] < minInRow) minInRow = curr[j];
            }

            if (minInRow > maxDistance) {
                return maxDistance + 1;
            }

            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[len2];
    }

    private void addToHistory(Deque<RecentMessage> history, RecentMessage message) {
        history.addLast(message);
        while (history.size() > HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::cleanupOldEntries, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    private void cleanupOldEntries() {
        long now = System.currentTimeMillis();

        lastCharacterFlood.entrySet().removeIf(entry -> now - entry.getValue() > ENTRY_LIFETIME_MILLIS);
        recentChecks.entrySet().removeIf(entry -> now - entry.getValue().time > ENTRY_LIFETIME_MILLIS);

        playerHistory.entrySet().removeIf(entry -> {
            Deque<RecentMessage> history = entry.getValue();
            history.removeIf(msg -> now - msg.timestamp > ENTRY_LIFETIME_MILLIS);
            return history.isEmpty();
        });
    }

    private AntiSpamResult checkCharacterFlood(UUID uuid, String message, long now) {
        if (!configManager.isCharacterFloodEnabled()) return null;

        boolean isFlood = hasRepeatingChars(message, configManager.getCharacterFloodMaxRepeatingChars())
                || hasRepeatingPattern(message, configManager.getCharacterFloodMaxRepeatingPattern());

        if (!isFlood) return null;

        long last = lastCharacterFlood.getOrDefault(uuid, 0L);
        int cooldownSeconds = configManager.getCharacterFloodCooldownSeconds();
        long elapsed = now - last;

        if (elapsed < cooldownSeconds * 1000L) {
            int remaining = (int) Math.ceil((cooldownSeconds * 1000L - elapsed) / 1000.0);
            return new AntiSpamResult("character-flood", Math.max(1, remaining));
        }

        lastCharacterFlood.put(uuid, now);
        return new AntiSpamResult("character-flood-first", cooldownSeconds);
    }

    private boolean hasRepeatingChars(String message, int maxRepeating) {
        int streak = 1;
        for (int i = 1; i < message.length(); i++) {
            if (message.charAt(i) == message.charAt(i - 1)) {
                if (++streak > maxRepeating) return true;
            } else {
                streak = 1;
            }
        }
        return false;
    }

    private boolean hasRepeatingPattern(String message, int maxRepeating) {
        if (maxRepeating <= 1 || message.length() < MIN_FLOOD_PATTERN_LENGTH * 2) return false;

        for (int length = MIN_FLOOD_PATTERN_LENGTH; length <= MAX_FLOOD_PATTERN_LENGTH; length++) {
            for (int start = 0; start + length * 2 <= message.length(); start++) {
                int count = 1;
                int position = start + length;

                while (position + length <= message.length()
                        && message.regionMatches(start, message, position, length)) {
                    if (++count > maxRepeating) return true;
                    position += length;
                }
            }
        }
        return false;
    }

    public void reload() {
        playerHistory.clear();
        lastCharacterFlood.clear();
        recentChecks.clear();
    }

    private static final class DedupeEntry {
        final String normalized;
        final long time;
        final AntiSpamResult result;

        DedupeEntry(String normalized, long time, AntiSpamResult result) {
            this.normalized = normalized;
            this.time = time;
            this.result = result;
        }
    }

    private static final class RecentMessage {
        final long timestamp;
        final String normalized;
        final Set<String> tokens;

        RecentMessage(long timestamp, String message) {
            this.timestamp = timestamp;
            this.normalized = WordNormalizer.normalizeForSimilarity(message);
            this.tokens = tokenize(this.normalized);
        }

        private static Set<String> tokenize(String normalized) {
            if (normalized.isEmpty()) return Set.of();

            Set<String> tokens = new HashSet<>();
            for (String word : WHITESPACE.split(normalized)) {
                String cleaned = NON_ALPHANUMERIC.matcher(word).replaceAll("");
                if (!cleaned.isEmpty()) tokens.add(cleaned);
            }
            return tokens;
        }
    }
}
