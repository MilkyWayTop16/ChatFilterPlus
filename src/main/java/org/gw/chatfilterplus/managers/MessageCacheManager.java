package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class MessageCacheManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final FilterProcessor filterProcessor;
    private final Map<String, CachedMessage> messageCache;
    private final int cacheSize;
    private final long cacheTTL;
    private final BlockedWordsManager blockedWordsManager;
    private final WordNormalizer wordNormalizer;

    public static class CachedMessage {
        private final String filteredMessage;
        private final List<String> badWords;
        private final List<String> links;
        private final List<String> blockedWords;
        private final boolean isCaps;
        private final String capsFixedMessage;
        private final long timestamp;

        public CachedMessage(String filteredMessage, List<String> badWords, List<String> links,
                             List<String> blockedWords, boolean isCaps, String capsFixedMessage, long timestamp) {
            this.filteredMessage = filteredMessage;
            this.badWords = new ArrayList<>(badWords);
            this.links = new ArrayList<>(links);
            this.blockedWords = new ArrayList<>(blockedWords);
            this.isCaps = isCaps;
            this.capsFixedMessage = capsFixedMessage;
            this.timestamp = timestamp;
        }

        public String getFilteredMessage() { return filteredMessage; }
        public List<String> getBadWords() { return badWords; }
        public List<String> getLinks() { return links; }
        public List<String> getBlockedWords() { return blockedWords; }
        public boolean isCaps() { return isCaps; }
        public String getCapsFixedMessage() { return capsFixedMessage; }
        public long getTimestamp() { return timestamp; }
    }

    public MessageCacheManager(ChatFilterPlus plugin, ConfigManager configManager,
                               WordsManager wordsManager, LinksManager linksManager,
                               CapsManager capsManager, BlockedWordsManager blockedWordsManager,
                               WordNormalizer wordNormalizer, int cacheSize) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.blockedWordsManager = blockedWordsManager;
        this.wordNormalizer = wordNormalizer;
        this.filterProcessor = new FilterProcessor(plugin, configManager, wordsManager, linksManager, capsManager, blockedWordsManager, wordNormalizer);
        this.cacheSize = cacheSize;
        this.cacheTTL = configManager.getCacheCleanupRetentionMillis();

        LinkedHashMap<String, CachedMessage> lhm = new LinkedHashMap<>(cacheSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
                return size() > cacheSize;
            }
        };
        this.messageCache = Collections.synchronizedMap(lhm);

        if (cacheSize > 0) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanCache, 20L * 60, 20L * 60);
        }
    }

    private void cleanCache() {
        if (cacheSize <= 0) return;
        long currentTime = System.currentTimeMillis();
        synchronized (messageCache) {
            messageCache.entrySet().removeIf(entry -> (currentTime - entry.getValue().getTimestamp()) > cacheTTL);
        }
    }

    public CachedMessage analyzeAndCacheMessage(String originalMessage, boolean bypassBadWords, boolean bypassLinks,
                                                boolean bypassBlockedWords, boolean bypassCaps) {
        if (originalMessage == null || originalMessage.isEmpty()) {
            return new CachedMessage("", List.of(), List.of(), List.of(), false, null, System.currentTimeMillis());
        }

        if (cacheSize <= 0) {
            return filterProcessor.processMessage(originalMessage, bypassBadWords, bypassLinks, bypassBlockedWords);
        }

        String cacheKey = originalMessage + "|" + bypassBadWords + "|" + bypassLinks + "|" + bypassBlockedWords;
        CachedMessage cached = messageCache.get(cacheKey);

        if (cached != null && (System.currentTimeMillis() - cached.getTimestamp()) < cacheTTL) {
            return cached;
        }

        CachedMessage result = filterProcessor.processMessage(originalMessage, bypassBadWords, bypassLinks, bypassBlockedWords);

        if (!result.getBadWords().isEmpty() || !result.getLinks().isEmpty() || !result.getBlockedWords().isEmpty()) {
            messageCache.put(cacheKey, result);
        }

        return result;
    }

    public void clearCache() {
        if (cacheSize > 0) messageCache.clear();
    }

    public void reload() {
        if (cacheSize > 0) messageCache.clear();
    }
}