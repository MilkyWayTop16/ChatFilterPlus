package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.bukkit.scheduler.BukkitTask;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.*;

@Getter
public class MessageCacheManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final FilterProcessor filterProcessor;

    private volatile int cacheSize;
    private volatile long cacheTTL;
    private volatile boolean cleanupEnabled;
    private Map<String, CachedMessage> messageCache;
    private BukkitTask cleanupTask;

    public static class CachedMessage {
        private final String filteredMessage;
        private final List<String> badWords;
        private final List<String> links;
        private final List<String> blockedWords;
        private final boolean isCaps;
        private final long timestamp;

        public CachedMessage(String filteredMessage, List<String> badWords, List<String> links,
                             List<String> blockedWords, boolean isCaps, long timestamp) {
            this.filteredMessage = filteredMessage;
            this.badWords = badWords;
            this.links = links;
            this.blockedWords = blockedWords;
            this.isCaps = isCaps;
            this.timestamp = timestamp;
        }

        public String getFilteredMessage() { return filteredMessage; }
        public List<String> getBadWords() { return badWords; }
        public List<String> getLinks() { return links; }
        public List<String> getBlockedWords() { return blockedWords; }
        public boolean isCaps() { return isCaps; }
        public long getTimestamp() { return timestamp; }
    }

    public MessageCacheManager(ChatFilterPlus plugin, ConfigManager configManager,
                               WordsManager wordsManager, LinksManager linksManager,
                               CapsManager capsManager, BlockedWordsManager blockedWordsManager,
                               WordNormalizer wordNormalizer, int cacheSize) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.filterProcessor = new FilterProcessor(plugin, configManager, wordsManager, linksManager, capsManager, blockedWordsManager, wordNormalizer);
        applyCacheSettings(cacheSize,
                configManager.getCacheCleanupRetentionMillis(),
                configManager.isCacheCleanupEnabled());
        startCleanupTaskIfNeeded();
    }

    private void applyCacheSettings(int size, long ttlMillis, boolean cleanup) {
        this.cacheSize = Math.max(size, 0);
        this.cacheTTL = Math.max(ttlMillis, 1L);
        this.cleanupEnabled = cleanup;
        this.messageCache = createCache(this.cacheSize);
    }

    private Map<String, CachedMessage> createCache(int maxSize) {
        if (maxSize <= 0) return Collections.synchronizedMap(new LinkedHashMap<>());

        int initialCapacity = Math.min(maxSize, 128) + 1;
        return Collections.synchronizedMap(new LinkedHashMap<>(initialCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
                return size() > MessageCacheManager.this.cacheSize;
            }
        });
    }

    private void startCleanupTaskIfNeeded() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (cacheSize > 0 && cleanupEnabled) {
            cleanupTask = plugin.getServer().getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::cleanCache, 20L * 60, 20L * 60);
        }
    }

    private void cleanCache() {
        if (cacheSize <= 0 || !cleanupEnabled) return;

        long now = System.currentTimeMillis();
        long ttl = cacheTTL;
        synchronized (messageCache) {
            messageCache.entrySet().removeIf(entry -> now - entry.getValue().getTimestamp() > ttl);
        }
    }

    public CachedMessage analyzeAndCacheMessage(String originalMessage,
                                                java.util.UUID playerId,
                                                boolean bypassBadWords,
                                                boolean bypassLinks,
                                                boolean bypassBlockedWords,
                                                boolean bypassCaps) {

        AdaptiveAdFilter adaptive = null;
        int suspicion = 0;
        if (playerId != null && !bypassLinks && plugin.getLinksManager() != null) {
            adaptive = plugin.getLinksManager().getAdaptiveAdFilter();
            if (adaptive != null) {
                suspicion = adaptive.getSuspicionLevel(playerId);
            }
        }

        int size = cacheSize;
        long ttl = cacheTTL;

        if (size <= 0 || suspicion > 0) {
            return filterProcessor.processMessage(originalMessage, playerId, bypassBadWords, bypassLinks, bypassBlockedWords, bypassCaps);
        }

        String cacheKey = buildCacheKey(originalMessage, bypassBadWords, bypassLinks, bypassBlockedWords, bypassCaps);

        CachedMessage cached = messageCache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.getTimestamp() < ttl) {
            return applyAdaptiveOnCacheHit(cached, originalMessage, playerId, bypassLinks, adaptive);
        }

        CachedMessage result = filterProcessor.processMessage(originalMessage, playerId, bypassBadWords, bypassLinks, bypassBlockedWords, bypassCaps);
        if (result.getLinks().isEmpty()) {
            messageCache.put(cacheKey, result);
        }
        return result;
    }

    private CachedMessage applyAdaptiveOnCacheHit(CachedMessage cached,
                                                  String originalMessage,
                                                  java.util.UUID playerId,
                                                  boolean bypassLinks,
                                                  AdaptiveAdFilter adaptive) {
        if (bypassLinks || playerId == null || adaptive == null || !adaptive.isEnabled()) {
            return cached;
        }

        List<AdaptiveAdFilter.AdHit> hits = adaptive.evaluate(playerId, originalMessage, List.of());
        if (hits.isEmpty()) {
            return cached;
        }

        List<String> linkItems = new ArrayList<>(hits.size());
        for (AdaptiveAdFilter.AdHit hit : hits) {
            linkItems.add(hit.text());
        }

        String filtered = cached.getFilteredMessage();
        if (configManager.isLinksFilterEnabled() && plugin.getLinksManager() != null) {
            String replacement = plugin.getLinksManager().getTranslatedReplacement();
            hits.sort(Comparator.comparingInt(AdaptiveAdFilter.AdHit::start).reversed());
            StringBuilder sb = new StringBuilder(originalMessage);
            for (AdaptiveAdFilter.AdHit hit : hits) {
                if (hit.start() >= 0 && hit.end() <= sb.length() && hit.start() < hit.end()) {
                    sb.replace(hit.start(), hit.end(), replacement);
                }
            }
            filtered = sb.toString();
        }

        return new CachedMessage(
                filtered,
                cached.getBadWords(),
                linkItems,
                cached.getBlockedWords(),
                cached.isCaps(),
                System.currentTimeMillis()
        );
    }

    private static String buildCacheKey(String message,
                                        boolean bypassBadWords,
                                        boolean bypassLinks,
                                        boolean bypassBlockedWords,
                                        boolean bypassCaps) {
        return message + '|'
                + (bypassBadWords ? '1' : '0')
                + (bypassLinks ? '1' : '0')
                + (bypassBlockedWords ? '1' : '0')
                + (bypassCaps ? '1' : '0');
    }

    public void clearCache() {
        messageCache.clear();
    }

    public void reload() {
        applyCacheSettings(
                configManager.getCacheMaxSize(),
                configManager.getCacheCleanupRetentionMillis(),
                configManager.isCacheCleanupEnabled()
        );
        startCleanupTaskIfNeeded();
    }
}
