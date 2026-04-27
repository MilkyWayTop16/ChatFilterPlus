package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class FilterProcessor {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final WordsManager wordsManager;
    private final LinksManager linksManager;
    private final CapsManager capsManager;
    private final BlockedWordsManager blockedWordsManager;
    private final WordNormalizer wordNormalizer;

    public FilterProcessor(ChatFilterPlus plugin, ConfigManager configManager, WordsManager wordsManager,
                           LinksManager linksManager, CapsManager capsManager,
                           BlockedWordsManager blockedWordsManager, WordNormalizer wordNormalizer) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.wordsManager = wordsManager;
        this.linksManager = linksManager;
        this.capsManager = capsManager;
        this.blockedWordsManager = blockedWordsManager;
        this.wordNormalizer = wordNormalizer;
    }

    public MessageCacheManager.CachedMessage processMessage(String originalMessage, boolean bypassBadWords,
                                                            boolean bypassLinks, boolean bypassBlockedWords) {
        if (originalMessage == null || originalMessage.isEmpty()) {
            return new MessageCacheManager.CachedMessage("", List.of(), List.of(), List.of(),
                    false, null, System.currentTimeMillis());
        }

        String filteredMessage = originalMessage;
        List<String> badWords = new CopyOnWriteArrayList<>();
        List<String> links = new CopyOnWriteArrayList<>();
        List<String> blockedWords = new CopyOnWriteArrayList<>();

        String normalized = WordNormalizer.normalize(originalMessage, configManager.getBadWordsFilterLevel());

        if (configManager.isBlockedWordsFilterEnabled() && !bypassBlockedWords) {
            filteredMessage = applyWordFilter(filteredMessage, blockedWordsManager.getBlockedWordsMap(),
                    blockedWords, configManager.getBlockedWordsFilterReplacement());
        }

        if (configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            filteredMessage = filterBadWords(originalMessage, normalized, badWords);
        }

        if (configManager.isLinksFilterEnabled() && !bypassLinks) {
            filteredMessage = filterLinks(originalMessage, links);
        }

        boolean isCaps = capsManager.isCaps(originalMessage);
        String capsFixedMessage = null;
        if (configManager.isCapsFilterEnabled() && isCaps) {
            capsFixedMessage = capsManager.fixCaps(originalMessage);
        }

        return new MessageCacheManager.CachedMessage(filteredMessage, badWords, links, blockedWords,
                isCaps, capsFixedMessage, System.currentTimeMillis());
    }

    private String applyWordFilter(String message, Map<Pattern, String> patternMap,
                                   List<String> foundWords, String replacement) {
        if (patternMap.isEmpty()) return message;

        StringBuilder result = new StringBuilder(message);
        boolean found = false;

        for (Map.Entry<Pattern, String> entry : patternMap.entrySet()) {
            Matcher matcher = entry.getKey().matcher(message);
            while (matcher.find()) {
                String matched = matcher.group();
                foundWords.add(matched);
                found = true;

                String repl = replacement.equals("*") ? "*".repeat(matched.length()) : replacement;
                result.replace(matcher.start(), matcher.end(), repl);
            }
        }

        return found ? result.toString() : message;
    }

    private String filterBadWords(String originalMessage, String normalizedMessage, List<String> badWords) {
        if (originalMessage.length() < 3) return originalMessage;

        String replacement = configManager.getBadWordsFilterReplacement();
        String level = configManager.getBadWordsFilterLevel();

        if (!"low".equalsIgnoreCase(level)) {
            List<Replacement> replacements = new ArrayList<>();

            for (Map.Entry<Pattern, String> entry : wordsManager.getWordsMap().entrySet()) {
                Matcher matcher = entry.getKey().matcher(originalMessage);
                while (matcher.find()) {
                    String foundWord = matcher.group(1);
                    if (wordNormalizer.isSafeWord(foundWord)) continue;

                    String matchedText = matcher.group(0);
                    badWords.add(matchedText);

                    String repl = replacement.equals("*") ? generateDynamicReplacement(matchedText) : replacement;
                    replacements.add(new Replacement(matcher.start(), matchedText.length(), repl));
                }
            }

            replacements.sort(Comparator.comparingInt(r -> -r.start));
            StringBuilder filteredMessage = new StringBuilder(originalMessage);
            for (Replacement rep : replacements) {
                filteredMessage.replace(rep.start, rep.start + rep.length, rep.replacement);
            }
            return filteredMessage.toString();
        } else {
            String[] words = originalMessage.split("\\s+");
            StringBuilder result = new StringBuilder(originalMessage.length() + 32);
            for (String word : words) {
                String filteredWord = word;
                for (Map.Entry<Pattern, String> entry : wordsManager.getWordsMap().entrySet()) {
                    Matcher matcher = entry.getKey().matcher(word);
                    if (matcher.find()) {
                        String foundWord = matcher.group(1);
                        if (wordNormalizer.isSafeWord(foundWord)) continue;
                        badWords.add(word);
                        filteredWord = replacement.equals("*") ? generateDynamicReplacement(word) : replacement;
                        break;
                    }
                }
                result.append(filteredWord).append(" ");
            }
            return result.toString().trim();
        }
    }

    private String filterLinks(String originalMessage, List<String> links) {
        String normalized = linksManager.normalizeForDetectionPublic(originalMessage);

        if (normalized.isEmpty()) return originalMessage;

        Matcher linkMatcher = linksManager.getLinkPattern().matcher(normalized);
        boolean foundAnyLink = false;

        while (linkMatcher.find()) {
            String matched = linkMatcher.group();
            if (!linksManager.isLinkAllowed(matched)) {
                foundAnyLink = true;
                links.add(matched);
            }
        }

        if (foundAnyLink) {
            String replacement = HexColors.translate(configManager.getLinksFilterReplacement());
            return replacement;
        }

        return originalMessage;
    }

    private String generateDynamicReplacement(String word) {
        if (word == null || word.length() < 2) return word;

        String letterPart = word.replaceAll("[^\\p{L}]", "");
        if (letterPart.length() < 2) return word;

        int starsCount = letterPart.length() - 2;
        return letterPart.charAt(0) + "*".repeat(Math.max(0, starsCount)) + letterPart.charAt(letterPart.length() - 1);
    }

    private record Replacement(int start, int length, String replacement) {}
}