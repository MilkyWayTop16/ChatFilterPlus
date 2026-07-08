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

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+");

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

    public MessageCacheManager.CachedMessage processMessage(String originalMessage,
                                                            boolean bypassBadWords,
                                                            boolean bypassLinks,
                                                            boolean bypassBlockedWords,
                                                            boolean bypassCaps) {

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
            filteredMessage = filterBadWords(filteredMessage, badWords);
        }

        if (configManager.isLinksFilterEnabled() && !bypassLinks) {
            links = new ArrayList<>();
            filteredMessage = filterLinks(filteredMessage, links);
        }

        boolean isCaps = false;
        String capsFixedMessage = null;
        if (configManager.isCapsFilterEnabled() && !bypassCaps) {
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

        List<Replacement> replacements = new ArrayList<>();

        for (Map.Entry<Pattern, String> entry : patternMap.entrySet()) {
            Matcher matcher = entry.getKey().matcher(message);
            while (matcher.find()) {
                String matched = matcher.group();
                foundWords.add(matched);

                String repl = replacement.equals("*") ? "*".repeat(matched.length()) : replacement;
                replacements.add(new Replacement(matcher.start(), matched.length(), repl));
            }
        }

        if (replacements.isEmpty()) return message;

        replacements.sort(Comparator.comparingInt(r -> -r.start));
        StringBuilder result = new StringBuilder(message);
        for (Replacement rep : replacements) {
            result.replace(rep.start, rep.start + rep.length, rep.replacement);
        }
        return result.toString();
    }

    private String filterBadWords(String message, List<String> badWords) {
        if (message.length() < 3) return message;

        String replacement = configManager.getBadWordsFilterReplacement();
        String level = configManager.getBadWordsFilterLevel();

        if (!"low".equalsIgnoreCase(level)) {
            List<Replacement> replacements = new ArrayList<>();

            for (Map.Entry<Pattern, String> entry : wordsManager.getWordsMap().entrySet()) {
                Matcher matcher = entry.getKey().matcher(message);
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

            if (replacements.isEmpty()) return message;

            replacements.sort(Comparator.comparingInt(r -> -r.start));
            StringBuilder filteredMessage = new StringBuilder(message);
            for (Replacement rep : replacements) {
                filteredMessage.replace(rep.start, rep.start + rep.length, rep.replacement);
            }
            return filteredMessage.toString();

        } else {
            Matcher tokenMatcher = TOKEN_PATTERN.matcher(message);
            List<Replacement> replacements = new ArrayList<>();

            while (tokenMatcher.find()) {
                String word = tokenMatcher.group();
                for (Map.Entry<Pattern, String> entry : wordsManager.getWordsMap().entrySet()) {
                    Matcher m = entry.getKey().matcher(word);
                    if (m.find()) {
                        String foundWord = m.group(1);
                        if (wordNormalizer.isSafeWord(foundWord)) break;

                        badWords.add(word);
                        String repl = replacement.equals("*")
                                ? generateDynamicReplacement(word)
                                : replacement;
                        replacements.add(new Replacement(tokenMatcher.start(), word.length(), repl));
                        break;
                    }
                }
            }

            if (replacements.isEmpty()) return message;

            replacements.sort(Comparator.comparingInt(r -> -r.start));
            StringBuilder filteredMessage = new StringBuilder(message);
            for (Replacement rep : replacements) {
                filteredMessage.replace(rep.start, rep.start + rep.length, rep.replacement);
            }
            return filteredMessage.toString();
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