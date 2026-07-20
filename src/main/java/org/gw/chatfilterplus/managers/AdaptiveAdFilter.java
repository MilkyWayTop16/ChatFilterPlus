package org.gw.chatfilterplus.managers;

import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.configs.ConfigUtils;
import org.gw.chatfilterplus.utils.AdTextAnalyzer;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AdaptiveAdFilter {

    public record AdHit(int start, int end, String text, String reason, int score) {
    }

    private static final class AdFingerprint {
        final String compact;
        final Set<String> handles;
        final Set<String> keywords;
        final Set<String> tokens;
        final long timestamp;

        AdFingerprint(String message, Set<String> handles, Set<String> keywords) {
            this.compact = AdTextAnalyzer.compact(message);
            this.handles = Set.copyOf(handles);
            this.keywords = Set.copyOf(keywords);
            this.tokens = Set.copyOf(AdTextAnalyzer.significantTokens(message));
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final class PlayerAdState {
        private int level;
        private long lastViolationAt;
        final Deque<AdFingerprint> history = new ConcurrentLinkedDeque<>();
        final Set<String> knownHandles = ConcurrentHashMap.newKeySet();
        final Set<String> knownKeywords = ConcurrentHashMap.newKeySet();

        synchronized int currentLevel() {
            return level;
        }

        synchronized long lastActivityAt() {
            return lastViolationAt;
        }

        synchronized int escalate(int maxLevel) {
            level = Math.min(maxLevel, level + 1);
            lastViolationAt = System.currentTimeMillis();
            return level;
        }

        synchronized boolean expireIfStale(long ttlMillis) {
            if (level <= 0) return false;
            if (System.currentTimeMillis() - lastViolationAt <= ttlMillis) return false;
            level = 0;
            return true;
        }
    }

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final Map<UUID, PlayerAdState> states = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile boolean highPrecisionMode;
    private volatile long suspicionTtlMillis;
    private volatile int historySize;
    private volatile int maxLevel;
    private volatile int elevatedSimilarityPercent;
    private volatile int highSimilarityPercent;
    private volatile int minScoreToBlock;
    private volatile boolean trackSimilarity;
    private volatile boolean blockWholeMessageOnAdaptive;
    private volatile List<String> promoKeywords;

    public AdaptiveAdFilter(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        reload();
        startCleanupTask();
    }

    public void reload() {
        var cfg = configManager.getLinksConfig();
        enabled = cfg.getBoolean("filter.adaptive-ad-filter.enabled", true);
        highPrecisionMode = cfg.getBoolean("filter.adaptive-ad-filter.high-precision-mode", true);
        suspicionTtlMillis = ConfigUtils.parseRetentionPeriod(
                cfg.getString("filter.adaptive-ad-filter.suspicion-ttl", "45m"), 45 * 60 * 1000L);
        historySize = Math.max(1, cfg.getInt("filter.adaptive-ad-filter.history-size", 10));
        maxLevel = Math.max(1, cfg.getInt("filter.adaptive-ad-filter.max-level", 5));
        elevatedSimilarityPercent = clampPercent(cfg.getInt("filter.adaptive-ad-filter.elevated-similarity-percent",
                highPrecisionMode ? 68 : 52));
        highSimilarityPercent = clampPercent(cfg.getInt("filter.adaptive-ad-filter.high-similarity-percent",
                highPrecisionMode ? 55 : 38));
        minScoreToBlock = Math.max(1, cfg.getInt("filter.adaptive-ad-filter.min-score-to-block",
                highPrecisionMode ? 70 : 50));
        trackSimilarity = cfg.getBoolean("filter.adaptive-ad-filter.track-similarity", true);
        blockWholeMessageOnAdaptive = cfg.getBoolean("filter.adaptive-ad-filter.block-whole-message", true);
        promoKeywords = ConfigUtils.cleanStringList(cfg.getStringList("filter.adaptive-ad-filter.promo-keywords"));
        if (promoKeywords.isEmpty()) {
            promoKeywords = defaultKeywords();
        }
        if (!enabled) {
            clear();
        }
    }

    private static int clampPercent(int value) {
        return Math.max(10, Math.min(100, value));
    }

    private static List<String> defaultKeywords() {
        return List.of(
                "тгк", "tgk", "тг", "tg", "телега", "телеграм", "telegram", "телеграмм",
                "дс", "дискорд", "discord", "dsc",
                "подпишись", "подписка", "ссылка", "ссылку", "переходи",
                "лучший тгк", "мой тгк", "наш тгк", "тг канал", "тг-канал",
                "invite", "инвайт", "промокод", "реклама",
                "айпи", "donate", "донат"
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getPromoKeywords() {
        return List.copyOf(promoKeywords);
    }

    public boolean addPromoKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return false;
        String trimmed = keyword.trim();
        List<String> list = new ArrayList<>(promoKeywords);
        if (list.stream().anyMatch(k -> k.equalsIgnoreCase(trimmed))) return false;

        list.add(trimmed);
        return savePromoKeywords(list, "Добавлено ключевое слово рекламы &#ffff00" + trimmed);
    }

    public boolean removePromoKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return false;
        String trimmed = keyword.trim();
        List<String> list = new ArrayList<>(promoKeywords);
        if (!list.removeIf(k -> k.equalsIgnoreCase(trimmed))) return false;

        return savePromoKeywords(list, "Удалено ключевое слово рекламы &#ffff00" + trimmed);
    }

    private boolean savePromoKeywords(List<String> list, String successMessage) {
        configManager.getLinksConfig().set("filter.adaptive-ad-filter.promo-keywords", list);
        try {
            configManager.getLinksConfig().save(new File(plugin.getDataFolder(), "links.yml"));
            this.promoKeywords = ConfigUtils.cleanStringList(list);
            plugin.log(successMessage);
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка сохранения параметра promo-keywords: " + e.getMessage());
            return false;
        }
    }

    public int getSuspicionLevel(UUID playerId) {
        if (playerId == null || !enabled) return 0;
        PlayerAdState state = states.get(playerId);
        if (state == null) return 0;
        expireIfNeeded(state);
        return state.currentLevel();
    }

    public List<AdHit> evaluate(UUID playerId, String message, List<LinksManager.LinkMatch> standardMatches) {
        if (!enabled || message == null || message.length() < 3) {
            return List.of();
        }

        boolean hasStandard = standardMatches != null && !standardMatches.isEmpty();

        PlayerAdState existing = playerId == null ? null : states.get(playerId);
        if (existing != null) {
            expireIfNeeded(existing);
        }
        int level = existing == null ? 0 : existing.currentLevel();

        if (level == 0 && !hasStandard
                && message.indexOf('@') < 0
                && !AdTextAnalyzer.containsUrlish(message)) {
            return List.of();
        }

        PlayerAdState state = playerId == null ? null : states.computeIfAbsent(playerId, id -> new PlayerAdState());
        if (state != null && state != existing) {
            expireIfNeeded(state);
        }
        level = state == null ? 0 : state.currentLevel();

        List<AdHit> hits = new ArrayList<>();

        Set<String> handles = AdTextAnalyzer.extractHandles(message);
        Set<String> keywords = AdTextAnalyzer.extractKeywords(message, promoKeywords);
        String compact = AdTextAnalyzer.compact(message);
        boolean hasUrlish = AdTextAnalyzer.containsUrlish(message);
        boolean strongKeyword = hasStrongPromoKeyword(keywords);
        boolean weakOnlyKeywords = !keywords.isEmpty() && !strongKeyword;

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (hasStandard) {
            score += 80;
            reasons.add("hard-link");
        }
        if (hasUrlish && !hasStandard) {
            score += 55;
            reasons.add("urlish");
        }
        if (strongKeyword) {
            score += 35;
            reasons.add("strong-keyword");
        }
        if (!handles.isEmpty()) {
            score += 30;
            reasons.add("handle");
        }
        if (strongKeyword && (!handles.isEmpty() || hasStandard || hasUrlish)) {
            score += 25;
            reasons.add("promo-combo");
        }
        if (weakOnlyKeywords) {
            score += 5;
            reasons.add("weak-keyword");
        }

        if (level == 0) {
            if (strongKeyword && (!handles.isEmpty() || hasUrlish || hasStandard)) {
                hits.add(hit(message, "promo-combo", score));
            } else if (!handles.isEmpty() && hasUrlish) {
                hits.add(hit(message, "handle-url", score));
            }
        } else if (state != null) {
            collectElevatedHits(message, compact, handles, keywords, strongKeyword,
                    hasStandard, hasUrlish, state, level, score, hits);
        }

        List<AdHit> accepted = new ArrayList<>();
        for (AdHit h : mergeHits(hits)) {
            if (h.score() >= minScoreToBlock) {
                accepted.add(h);
            }
        }

        if (state != null && playerId != null) {
            if (!accepted.isEmpty() || hasStandard) {
                recordViolation(state, message, handles, keywords,
                        hasStandard || hasUrlish || !handles.isEmpty() || !accepted.isEmpty());
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.log("Реклама счёт=&#ffff00" + score
                            + " &fуровень=&#ffff00" + state.currentLevel()
                            + " &fпричина=&#ffff00" + String.join(",", reasons)
                            + " &fв &#ffff00" + message);
                }
            }
        }

        return accepted;
    }

    private void collectElevatedHits(String message,
                                     String compact,
                                     Set<String> handles,
                                     Set<String> keywords,
                                     boolean strongKeyword,
                                     boolean hasStandard,
                                     boolean hasUrlish,
                                     PlayerAdState state,
                                     int level,
                                     int baseScore,
                                     List<AdHit> hits) {
        if (hasStandard || (strongKeyword && (!handles.isEmpty() || hasUrlish))) {
            hits.add(hit(message, "elevated-base", baseScore + 10));
        }

        if (!state.knownHandles.isEmpty()) {
            for (String known : state.knownHandles) {
                if (known.length() < 4) continue;
                if (AdTextAnalyzer.containsSpaced(compact, known)) {
                    hits.add(hit(message, "known-handle:" + known, baseScore + 50));
                }
                for (String handle : handles) {
                    if (handle.equals(known) || AdTextAnalyzer.similarity(handle, known) >= 0.90) {
                        hits.add(hit(message, "handle-match:" + known, baseScore + 55));
                    }
                }
            }
        }

        if (trackSimilarity && !state.history.isEmpty()
                && (strongKeyword || !handles.isEmpty() || hasStandard || hasUrlish || level >= 3)) {
            double threshold = level >= 3
                    ? highSimilarityPercent / 100.0
                    : elevatedSimilarityPercent / 100.0;

            for (AdFingerprint fp : state.history) {
                double sim = AdTextAnalyzer.similarity(compact, fp.compact);
                if (sim >= threshold && compact.length() >= 8 && fp.compact.length() >= 8) {
                    int simScore = baseScore + (int) (sim * 40);
                    hits.add(hit(message, "similar-ad:" + (int) (sim * 100), simScore));
                    break;
                }

                for (String known : fp.handles) {
                    if (known.length() >= 4 && AdTextAnalyzer.containsSpaced(compact, known)) {
                        hits.add(hit(message, "history-handle:" + known, baseScore + 50));
                        break;
                    }
                }
            }
        }

        if (level >= 3 && strongKeyword && (!handles.isEmpty() || hasStandard || hasUrlish)) {
            hits.add(hit(message, "elevated-strong", baseScore + 20));
        }
    }

    private AdHit hit(String message, String reason, int score) {
        if (blockWholeMessageOnAdaptive) {
            return new AdHit(0, message.length(), message, reason, score);
        }
        int end = Math.min(message.length(), 32);
        return new AdHit(0, end, message.substring(0, end), reason, score);
    }

    private boolean hasStrongPromoKeyword(Set<String> keywords) {
        if (keywords.isEmpty()) return false;
        for (String key : keywords) {
            if (isWeakPromoKeyword(key)) continue;
            return true;
        }
        return false;
    }

    private boolean isWeakPromoKeyword(String key) {
        if (key == null || key.length() <= 3) return true;
        return key.equals("ip")
                || key.equals("сервер")
                || key.equals("канал")
                || key.equals("ds")
                || key.equals("dsc")
                || key.equals("tg")
                || key.equals("tgk")
                || key.equals("заходи");
    }

    private void recordViolation(PlayerAdState state,
                                 String message,
                                 Set<String> handles,
                                 Set<String> keywords,
                                 boolean hadLinkSignal) {
        int newLevel = state.escalate(maxLevel);
        for (String handle : handles) {
            if (handle.length() >= 4) state.knownHandles.add(handle);
        }
        for (String key : keywords) {
            if (!isWeakPromoKeyword(key)) state.knownKeywords.add(key);
        }

        AdFingerprint fp = new AdFingerprint(message, handles, keywords);
        state.history.addLast(fp);
        while (state.history.size() > historySize) {
            state.history.removeFirst();
        }

        if (configManager.isConsoleLogsEnabled() && (hadLinkSignal || !handles.isEmpty())) {
            plugin.log("Адаптивная анти-реклама: уровень &#ffff00" + newLevel
                    + " &f(handles: &#ffff00" + handles.size() + "&f)");
        }
    }

    private void expireIfNeeded(PlayerAdState state) {
        if (state.expireIfStale(suspicionTtlMillis)) {
            state.history.clear();
            state.knownHandles.clear();
            state.knownKeywords.clear();
        }
    }

    private List<AdHit> mergeHits(List<AdHit> hits) {
        if (hits.size() <= 1) return hits;
        hits.sort(Comparator.comparingInt(AdHit::start)
                .thenComparing((a, b) -> Integer.compare(b.score(), a.score()))
                .thenComparing((a, b) -> Integer.compare(b.end - b.start, a.end - a.start)));
        List<AdHit> result = new ArrayList<>();
        int lastEnd = -1;
        for (AdHit hit : hits) {
            if (hit.start < lastEnd) continue;
            result.add(hit);
            lastEnd = hit.end;
        }
        return result;
    }

    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            states.entrySet().removeIf(entry -> {
                PlayerAdState state = entry.getValue();
                expireIfNeeded(state);
                if (state.currentLevel() <= 0 && state.history.isEmpty()) return true;
                return now - state.lastActivityAt() > suspicionTtlMillis * 2;
            });
        }, 20L * 60, 20L * 60);
    }

    public void clear() {
        states.clear();
    }
}
