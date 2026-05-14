package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;
import org.gw.chatfilterplus.utils.WordNormalizer;

import java.util.*;
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
        String filteredMessage = originalMessage;

        List<String> badWords = null;
        List<String> links = null;
        List<String> blockedWords = null;

        if (configManager.isBlockedWordsFilterEnabled() && !bypassBlockedWords) {
            blockedWords = new ArrayList<>();
            filteredMessage = applyWordFilter(filteredMessage, blockedWordsManager.getBlockedWordsMap(),
                    blockedWords, configManager.getBlockedWordsFilterReplacement());
        }

        if (configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            badWords = new ArrayList<>();
            String normalized = WordNormalizer.normalize(originalMessage, configManager.getBadWordsFilterLevel());
            filteredMessage = filterBadWords(originalMessage, normalized, badWords);
        }

        if (configManager.isLinksFilterEnabled() && !bypassLinks) {
            links = new ArrayList<>();
            filteredMessage = filterLinks(filteredMessage, links);
        }

        boolean isCaps = false;
        String capsFixedMessage = null;
        if (configManager.isCapsFilterEnabled()) {
            isCaps = capsManager.isCaps(originalMessage);
            if (isCaps) {
                capsFixedMessage = capsManager.fixCaps(originalMessage);
            }
        }

        return new MessageCacheManager.CachedMessage(
                filteredMessage,
                badWords != null ? badWords : Collections.emptyList(),
                links != null ? links : Collections.emptyList(),
                blockedWords != null ? blockedWords : Collections.emptyList(),
                isCaps,
                capsFixedMessage,
                System.currentTimeMillis()
        );
    }

    private String applyWordFilter(String message, Map<Pattern, String> patternMap,
                                   List<String> foundWords, String replacement) {
        if (patternMap.isEmpty() || message.length() < 2) return message;

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

                    String repl = replacement.equals("*")
                            ? generateDynamicReplacement(matchedText)
                            : replacement;

                    replacements.add(new Replacement(matcher.start(), matchedText.length(), repl));
                }
            }

            if (replacements.isEmpty()) return originalMessage;

            replacements.sort(Comparator.comparingInt(r -> -r.start));
            StringBuilder filteredMessage = new StringBuilder(originalMessage);
            for (Replacement rep : replacements) {
                filteredMessage.replace(rep.start, rep.start + rep.length, rep.replacement);
            }
            return filteredMessage.toString();

        } else {
            String[] words = originalMessage.split("\\s+");
            StringBuilder result = new StringBuilder(originalMessage.length() + 16);
            boolean changed = false;

            for (String word : words) {
                String filteredWord = word;
                for (Map.Entry<Pattern, String> entry : wordsManager.getWordsMap().entrySet()) {
                    if (entry.getKey().matcher(word).find()) {
                        String foundWord = entry.getKey().matcher(word).group(1);
                        if (wordNormalizer.isSafeWord(foundWord)) break;

                        badWords.add(word);
                        filteredWord = replacement.equals("*")
                                ? generateDynamicReplacement(word)
                                : replacement;
                        changed = true;
                        break;
                    }
                }
                result.append(filteredWord).append(" ");
            }
            return changed ? result.toString().trim() : originalMessage;
        }
    }

    private String filterLinks(String message, List<String> links) {
        if (message.length() < 5) return message;

        Matcher linkMatcher = linksManager.getLinkPattern().matcher(message);
        StringBuilder sb = new StringBuilder();
        boolean found = false;

        while (linkMatcher.find()) {
            String link = linkMatcher.group();
            if (!linksManager.isLinkAllowed(link)) {
                links.add(link);
                found = true;
                String replacement = HexColors.translate(configManager.getLinksFilterReplacement());
                linkMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                linkMatcher.appendReplacement(sb, Matcher.quoteReplacement(link));
            }
        }

        if (!found) return message;
        linkMatcher.appendTail(sb);
        return sb.toString();
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