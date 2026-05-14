package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Getter
public class MessageCacheManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final FilterProcessor filterProcessor;
    private final Map<String, CachedMessage> messageCache;
    private final ConcurrentLinkedDeque<String> accessOrder;
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
            this.badWords = badWords;
            this.links = links;
            this.blockedWords = blockedWords;
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
        this.cacheSize = Math.max(cacheSize, 0);
        this.cacheTTL = configManager.getCacheCleanupRetentionMillis();
        this.messageCache = new ConcurrentHashMap<>(Math.max(this.cacheSize, 128));
        this.accessOrder = new ConcurrentLinkedDeque<>();

        if (this.cacheSize > 0) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanCache, 20L * 60, 20L * 60);
        }
    }

    private void cleanCache() {
        if (cacheSize <= 0) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CachedMessage>> it = messageCache.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, CachedMessage> entry = it.next();
            if (now - entry.getValue().getTimestamp() > cacheTTL) {
                it.remove();
                accessOrder.remove(entry.getKey());
            }
        }
    }

    public CachedMessage analyzeAndCacheMessage(String originalMessage,
                                                boolean bypassBadWords,
                                                boolean bypassLinks,
                                                boolean bypassBlockedWords) {

        if (cacheSize <= 0) {
            return filterProcessor.processMessage(originalMessage, bypassBadWords, bypassLinks, bypassBlockedWords);
        }

        String cacheKey = originalMessage + "|" + bypassBadWords + "|" + bypassLinks + "|" + bypassBlockedWords;

        CachedMessage cached = messageCache.get(cacheKey);
        if (cached != null) {
            if (System.currentTimeMillis() - cached.getTimestamp() < cacheTTL) {
                moveToRecent(cacheKey);
                return cached;
            } else {
                messageCache.remove(cacheKey);
                accessOrder.remove(cacheKey);
            }
        }

        CachedMessage result = filterProcessor.processMessage(originalMessage, bypassBadWords, bypassLinks, bypassBlockedWords);

        if (!result.getBadWords().isEmpty() || !result.getLinks().isEmpty() || !result.getBlockedWords().isEmpty()) {
            evictIfNeeded();
            messageCache.put(cacheKey, result);
            accessOrder.addLast(cacheKey);
        }

        return result;
    }

    private void moveToRecent(String key) {
        accessOrder.remove(key);
        accessOrder.addLast(key);
    }

    private void evictIfNeeded() {
        while (messageCache.size() >= cacheSize && !accessOrder.isEmpty()) {
            String oldest = accessOrder.pollFirst();
            if (oldest != null) {
                messageCache.remove(oldest);
            }
        }
    }

    public void clearCache() {
        messageCache.clear();
        accessOrder.clear();
    }

    public void reload() {
        clearCache();
    }
}