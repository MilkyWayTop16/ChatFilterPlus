package org.gw.chatfilterplus.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PatternFactory {

    private static final int MAX_CACHE_SIZE = 5000;

    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>(MAX_CACHE_SIZE);

    public static Pattern createPattern(String word, String filterLevel) {
        if (word == null || word.isEmpty()) {
            return null;
        }

        String cacheKey = word + ":" + filterLevel.toLowerCase();
        return patternCache.computeIfAbsent(cacheKey, k -> buildPattern(word, filterLevel));
    }

    private static Pattern buildPattern(String word, String filterLevel) {
        String patternStr;

        if ("low".equalsIgnoreCase(filterLevel)) {
            patternStr = "\\b(" + Pattern.quote(word) + ")\\b";
        } else if ("medium".equalsIgnoreCase(filterLevel)) {
            patternStr = "\\b(" + createFlexiblePattern(word, false) + ")\\b";
        } else {
            patternStr = "\\b(" + createFlexiblePattern(word, true) + ")\\b";
        }

        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
        return Pattern.compile(patternStr, flags);
    }

    private static String createFlexiblePattern(String word, boolean highFlexibility) {
        String normalized = word.toLowerCase()
                .replace('ё', 'е')
                .replace('й', 'и');

        StringBuilder pattern = new StringBuilder(normalized.length() * 6);

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);

            if (highFlexibility) {
                if (c == 'е') {
                    pattern.append("(е|ё)+");
                } else if (c == 'и') {
                    pattern.append("(и|й)+");
                } else {
                    pattern.append('(').append(Pattern.quote(String.valueOf(c))).append(")+");
                }
            } else {
                pattern.append('(').append(Pattern.quote(String.valueOf(c))).append(")+");
            }

            if (i < normalized.length() - 1) {
                if (highFlexibility) {
                    pattern.append("[\\W\\s]{0,3}");
                } else {
                    pattern.append("[а-яА-Я\\s]{0,2}");
                }
            }
        }
        return pattern.toString();
    }

    public static void clearCache() {
        patternCache.clear();
    }
}