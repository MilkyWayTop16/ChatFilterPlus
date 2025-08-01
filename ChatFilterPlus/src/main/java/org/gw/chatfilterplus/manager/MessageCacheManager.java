package org.gw.chatfilterplus.manager;

import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageCacheManager {
    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final WordsManager wordsManager;
    private final LinksManager linksManager;
    private final Map<String, CachedMessage> messageCache;
    private final int cacheSize;

    public static class CachedMessage {
        private final String filteredMessage;
        private final List<String> badWords;
        private final List<String> links;

        public CachedMessage(String filteredMessage, List<String> badWords, List<String> links) {
            this.filteredMessage = filteredMessage;
            this.badWords = new ArrayList<>(badWords);
            this.links = new ArrayList<>(links);
        }

        public String getFilteredMessage() {
            return filteredMessage;
        }

        public List<String> getBadWords() {
            return badWords;
        }

        public List<String> getLinks() {
            return links;
        }
    }

    public MessageCacheManager(ChatFilterPlus plugin, ConfigManager configManager, WordsManager wordsManager, LinksManager linksManager, int cacheSize) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.wordsManager = wordsManager;
        this.linksManager = linksManager;
        this.cacheSize = cacheSize;
        this.messageCache = new LinkedHashMap<String, CachedMessage>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
                return size() > cacheSize;
            }
        };
        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().info("MessageCacheManager инициализирован с размером кэша: " + cacheSize);
        }
    }

    public void cacheMessage(String originalMessage, String filteredMessage, List<String> badWords, List<String> links) {
        messageCache.put(originalMessage, new CachedMessage(filteredMessage, badWords, links));
        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().info("Сообщение кэшировано: " + originalMessage + " -> " + filteredMessage +
                    " (badWords: " + badWords + ", links: " + links + ")");
        }
    }

    public CachedMessage analyzeAndCacheMessage(String originalMessage, boolean bypassBadWords, boolean bypassLinks) {
        String filteredMessage = originalMessage;
        List<String> badWords = new ArrayList<>();
        List<String> links = new ArrayList<>();

        // Проверка кэша
        CachedMessage cached = messageCache.get(originalMessage);
        if (cached != null) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Использовано кэшированное сообщение: " + originalMessage);
            }
            return cached;
        }

        // Фильтрация матов
        if (configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            String filterLevel = configManager.getBadWordsFilterLevel();
            String[] words = originalMessage.split("\\s+");
            StringBuilder filteredMessageBuilder = new StringBuilder();

            for (String word : words) {
                String normalizedWord = WordNormalizer.normalize(word, filterLevel);
                boolean wordContainsBad = false;
                String foundBadWord = null;

                for (Map.Entry<Pattern, String> entry : wordsManager.getWordsMap().entrySet()) {
                    Matcher matcher = entry.getKey().matcher(normalizedWord);
                    if (matcher.find()) {
                        String foundWord = "low".equalsIgnoreCase(filterLevel) ? entry.getValue() : matcher.group(1);
                        if ("medium".equalsIgnoreCase(filterLevel) && WordNormalizer.isSafeWord(foundWord)) {
                            continue;
                        }
                        if ("low".equalsIgnoreCase(filterLevel) && !word.equalsIgnoreCase(foundWord)) {
                            continue;
                        }
                        wordContainsBad = true;
                        foundBadWord = word;
                        badWords.add(foundBadWord);

                        StringBuilder letters = new StringBuilder();
                        StringBuilder suffixPunctuation = new StringBuilder();
                        for (char c : word.toCharArray()) {
                            if (Character.isLetter(c)) {
                                letters.append(c);
                            } else {
                                suffixPunctuation.append(c);
                            }
                        }
                        String letterPart = letters.toString();

                        String replacement;
                        if (letterPart.equalsIgnoreCase(foundWord)) {
                            replacement = wordsManager.getWords().get(foundWord);
                            if (replacement == null) {
                                if (configManager.isConsoleLogsEnabled()) {
                                    plugin.getLogger().warning("Замена для слова '" + foundWord + "' не найдена, используется динамическая замена");
                                }
                                replacement = generateDynamicReplacement(letterPart);
                            }
                        } else {
                            replacement = generateDynamicReplacement(letterPart);
                        }
                        word = replacement + suffixPunctuation;
                        break;
                    }
                }
                filteredMessageBuilder.append(word).append(" ");
            }
            filteredMessage = filteredMessageBuilder.toString().trim();
        }

        // Фильтрация ссылок
        if (configManager.isLinksFilterEnabled() && !bypassLinks) {
            Matcher linkMatcher = linksManager.getLinkPattern().matcher(filteredMessage);
            StringBuilder filteredMessageBuilder = new StringBuilder();
            int lastEnd = 0;

            while (linkMatcher.find()) {
                String link = linkMatcher.group();
                // Проверяем, разрешена ли ссылка согласно белому/чёрному списку
                if (!linksManager.isLinkAllowed(link)) {
                    links.add(link);
                    filteredMessageBuilder.append(filteredMessage.substring(lastEnd, linkMatcher.start()));
                    filteredMessageBuilder.append(configManager.getLinksFilterReplacement());
                } else {
                    filteredMessageBuilder.append(filteredMessage.substring(lastEnd, linkMatcher.end()));
                }
                lastEnd = linkMatcher.end();
            }
            filteredMessageBuilder.append(filteredMessage.substring(lastEnd));
            filteredMessage = filteredMessageBuilder.toString();
        }

        // Кэшируем, если были изменения
        if (!badWords.isEmpty() || !links.isEmpty()) {
            cacheMessage(originalMessage, filteredMessage, badWords, links);
        }

        return new CachedMessage(filteredMessage, badWords, links);
    }

    public void clearCache() {
        messageCache.clear();
        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().info("Кэш сообщений очищен");
        }
    }

    private String generateDynamicReplacement(String letterPart) {
        if (letterPart == null || letterPart.length() < 2) {
            return letterPart;
        }
        int starsCount = letterPart.length() - 2;
        StringBuilder replacement = new StringBuilder();
        replacement.append(letterPart.charAt(0));
        replacement.append("*".repeat(starsCount));
        replacement.append(letterPart.charAt(letterPart.length() - 1));
        return replacement.toString();
    }
}