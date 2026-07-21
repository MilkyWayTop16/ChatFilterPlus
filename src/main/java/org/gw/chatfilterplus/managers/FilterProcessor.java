package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.profanity.ProfanityEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class FilterProcessor {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final WordsManager wordsManager;
    private final LinksManager linksManager;
    private final CapsManager capsManager;
    private final BlockedWordsManager blockedWordsManager;

    public FilterProcessor(ChatFilterPlus plugin, ConfigManager configManager, WordsManager wordsManager,
                           LinksManager linksManager, CapsManager capsManager,
                           BlockedWordsManager blockedWordsManager,
                           org.gw.chatfilterplus.utils.WordNormalizer wordNormalizer) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.wordsManager = wordsManager;
        this.linksManager = linksManager;
        this.capsManager = capsManager;
        this.blockedWordsManager = blockedWordsManager;
    }

    public MessageCacheManager.CachedMessage processMessage(String originalMessage,
                                                            java.util.UUID playerId,
                                                            boolean bypassBadWords,
                                                            boolean bypassLinks,
                                                            boolean bypassBlockedWords,
                                                            boolean bypassCaps) {

        String filteredMessage = originalMessage;

        List<String> badWords = Collections.emptyList();
        List<String> links = Collections.emptyList();
        List<String> blockedWords = Collections.emptyList();

        if (configManager.isBlockedWordsFilterEnabled() && !bypassBlockedWords) {
            EngineResult blockedResult = applyEngineFilter(
                    filteredMessage,
                    blockedWordsManager.getEngine(),
                    configManager.getBlockedWordsFilterReplacement(),
                    configManager.isBlockedWordsKeepEdgeLetters()
            );
            filteredMessage = blockedResult.message();
            blockedWords = blockedResult.foundWords();
        }

        if (configManager.isBadWordsFilterEnabled() && !bypassBadWords) {
            EngineResult badResult = applyEngineFilter(
                    filteredMessage,
                    wordsManager.getEngine(),
                    configManager.getBadWordsFilterReplacement(),
                    configManager.isBadWordsKeepEdgeLetters()
            );
            filteredMessage = badResult.message();
            badWords = badResult.foundWords();
        }

        if (configManager.isLinksFilterEnabled() && !bypassLinks) {
            LinkResult linkResult = filterLinks(filteredMessage, playerId);
            filteredMessage = linkResult.message();
            links = linkResult.links();
        }

        boolean isCaps = configManager.isCapsFilterEnabled() && !bypassCaps && capsManager.isCaps(originalMessage);

        return new MessageCacheManager.CachedMessage(
                filteredMessage,
                badWords,
                links,
                blockedWords,
                isCaps,
                System.currentTimeMillis()
        );
    }

    private record EngineResult(String message, List<String> foundWords) {
    }

    private record LinkResult(String message, List<String> links) {
    }

    private EngineResult applyEngineFilter(String message,
                                           ProfanityEngine engine,
                                           String replacement,
                                           boolean keepEdgeLetters) {
        if (engine == null || engine.isEmpty() || message.length() < 2) {
            return new EngineResult(message, Collections.emptyList());
        }

        List<ProfanityEngine.Match> matches = engine.findMatches(message);
        if (matches.isEmpty()) {
            return new EngineResult(message, Collections.emptyList());
        }

        String mask = (replacement == null || replacement.isEmpty()) ? "*" : replacement;
        List<String> foundWords = new ArrayList<>(matches.size());
        StringBuilder result = new StringBuilder(message);
        boolean logReasons = configManager.isConsoleLogsEnabled();

        for (int i = matches.size() - 1; i >= 0; i--) {
            ProfanityEngine.Match match = matches.get(i);
            foundWords.add(match.matchedText());
            if (logReasons) {
                plugin.log("Мат match=&#ffff00" + match.matchedText()
                        + " &fdict=&#ffff00" + match.dictionaryWord()
                        + " &fconf=&#ffff00" + match.confidence()
                        + " &freason=&#ffff00" + match.reason());
            }
            int span = Math.max(1, match.end() - match.start());
            String repl = buildWordReplacement(match.matchedText(), mask, keepEdgeLetters, span);
            result.replace(match.start(), match.end(), repl);
        }

        if (foundWords.size() > 1) {
            Collections.reverse(foundWords);
        }
        return new EngineResult(result.toString(), foundWords);
    }

    private String buildWordReplacement(String matched, String mask, boolean keepEdgeLetters, int span) {
        if (keepEdgeLetters && mask.length() == 1) {
            return generateDynamicReplacement(matched, mask.charAt(0));
        }
        if (mask.length() == 1) {
            return String.valueOf(mask.charAt(0)).repeat(span);
        }
        return mask;
    }

    private LinkResult filterLinks(String message, java.util.UUID playerId) {
        if (message.length() < 3) {
            return new LinkResult(message, Collections.emptyList());
        }

        List<LinksManager.LinkMatch> matches = linksManager.findBlockedLinks(message, playerId);
        if (matches.isEmpty()) {
            return new LinkResult(message, Collections.emptyList());
        }

        List<String> links = new ArrayList<>(matches.size());
        StringBuilder result = new StringBuilder(message);
        String replacement = linksManager.getTranslatedReplacement();

        for (int i = matches.size() - 1; i >= 0; i--) {
            LinksManager.LinkMatch match = matches.get(i);
            links.add(match.text());
            result.replace(match.start(), match.end(), replacement);
        }

        if (links.size() > 1) {
            Collections.reverse(links);
        }
        return new LinkResult(result.toString(), links);
    }

    private String generateDynamicReplacement(String word, char maskChar) {
        if (word == null || word.length() < 2) {
            return word == null ? "" : word;
        }

        int first = -1;
        int last = -1;
        int letterCount = 0;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                if (first < 0) first = i;
                last = i;
                letterCount++;
            }
        }
        if (letterCount < 2 || first < 0 || last < 0) {
            return String.valueOf(maskChar).repeat(Math.max(1, word.length()));
        }

        int starsCount = letterCount - 2;
        return word.charAt(first)
                + String.valueOf(maskChar).repeat(Math.max(0, starsCount))
                + word.charAt(last);
    }
}
