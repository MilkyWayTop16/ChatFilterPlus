package org.gw.chatfilterplus.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdTextAnalyzer {

    private static final Pattern HANDLE_PATTERN = Pattern.compile(
            "(?i)(?:^|[^\\p{L}\\p{N}_])@([\\p{L}\\p{N}_](?:[\\p{L}\\p{N}_\\s.\\-]{2,40})[\\p{L}\\p{N}_])");
    private static final Pattern LOOSE_HANDLE_PATTERN = Pattern.compile(
            "(?i)@([\\p{L}\\p{N}_\\s.\\-]{3,48})");
    private static final Pattern URLISH = Pattern.compile(
            "(?i)(?:https?://|www\\.|t\\.me/|discord\\.gg/|vk\\.com/|dsc\\.gg/)");
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}@]+");

    private AdTextAnalyzer() {
    }

    public static String compact(String text) {
        return TextNormalizer.normalizeCompact(text, true);
    }

    public static Set<String> extractHandles(String message) {
        if (message == null || message.isEmpty()) return Set.of();
        Set<String> handles = new LinkedHashSet<>();

        Matcher loose = LOOSE_HANDLE_PATTERN.matcher(message);
        while (loose.find()) {
            String raw = loose.group(1);
            String norm = compact(raw);
            if (norm.length() >= 4 && norm.length() <= 32) {
                handles.add(norm);
            }
        }

        Matcher strict = HANDLE_PATTERN.matcher(" " + message + " ");
        while (strict.find()) {
            String norm = compact(strict.group(1));
            if (norm.length() >= 4 && norm.length() <= 32) {
                handles.add(norm);
            }
        }
        return handles;
    }

    public static Set<String> extractKeywords(String message, Collection<String> keywords) {
        if (message == null || keywords == null || keywords.isEmpty()) return Set.of();
        String compactMessage = compact(message);
        if (compactMessage.isEmpty()) return Set.of();

        Set<String> messageTokens = significantTokens(message);
        Set<String> found = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            String key = compact(keyword);
            if (key.length() < 2) continue;
            if (matchesKeyword(compactMessage, messageTokens, key)) {
                found.add(key);
            }
        }
        return found;
    }

    public static boolean matchesKeyword(String compactMessage, Set<String> messageTokens, String key) {
        if (key == null || key.length() < 2 || compactMessage == null || compactMessage.isEmpty()) {
            return false;
        }

        if (key.length() <= 3) {
            if (compactMessage.equals(key)) return true;
            for (String token : messageTokens) {
                if (token.equals(key) || token.equals("@" + key)) return true;
            }
            return false;
        }

        if (compactMessage.contains(key)) return true;

        for (String token : messageTokens) {
            if (token.equals(key) || token.contains(key)) return true;
        }

        if (key.length() >= 5 && isSubsequence(compactMessage, key)
                && compactMessage.length() <= key.length() * 3) {
            return true;
        }
        return false;
    }

    public static boolean matchesKeyword(String message, String key) {
        if (message == null || key == null) return false;
        return matchesKeyword(compact(message), significantTokens(message), compact(key));
    }

    public static boolean containsUrlish(String message) {
        return message != null && URLISH.matcher(message).find();
    }

    public static boolean isSubsequence(String haystack, String needle) {
        if (needle.isEmpty()) return true;
        if (haystack.length() < needle.length()) return false;
        int j = 0;
        for (int i = 0; i < haystack.length() && j < needle.length(); i++) {
            if (haystack.charAt(i) == needle.charAt(j)) j++;
        }
        return j == needle.length();
    }

    public static boolean containsSpaced(String haystackCompact, String needleCompact) {
        if (needleCompact == null || needleCompact.length() < 4) return false;
        if (haystackCompact.contains(needleCompact)) return true;
        return isSubsequence(haystackCompact, needleCompact)
                && needleCompact.length() >= 5
                && haystackCompact.length() <= needleCompact.length() * 4;
    }

    public static double similarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;

        Set<String> bigramsA = bigrams(a);
        Set<String> bigramsB = bigrams(b);
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) {
            return lengthRatio(a, b) * charOverlap(a, b);
        }

        int intersection = 0;
        for (String bg : bigramsA) {
            if (bigramsB.contains(bg)) intersection++;
        }
        int union = bigramsA.size() + bigramsB.size() - intersection;
        double jaccard = union == 0 ? 0.0 : (double) intersection / union;

        double containBoost = 0.0;
        if (a.length() >= 6 && b.length() >= 6) {
            if (a.contains(b) || b.contains(a)) {
                containBoost = 0.25;
            } else if (isSubsequence(a, b) || isSubsequence(b, a)) {
                containBoost = 0.15;
            }
        }

        return Math.min(1.0, Math.max(jaccard, jaccard * 0.7 + containBoost + lengthRatio(a, b) * 0.1));
    }

    private static Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s.length() < 2) {
            set.add(s);
            return set;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    private static double lengthRatio(String a, String b) {
        int min = Math.min(a.length(), b.length());
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 0.0 : (double) min / max;
    }

    private static double charOverlap(String a, String b) {
        Map<Character, Integer> fa = new HashMap<>();
        for (int i = 0; i < a.length(); i++) fa.merge(a.charAt(i), 1, Integer::sum);
        int inter = 0;
        int total = a.length() + b.length();
        Map<Character, Integer> fb = new HashMap<>(fa);
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            Integer left = fb.get(c);
            if (left != null && left > 0) {
                inter++;
                fb.put(c, left - 1);
            }
        }
        return total == 0 ? 0.0 : (2.0 * inter) / total;
    }

    public static Set<String> significantTokens(String message) {
        if (message == null || message.isEmpty()) return Set.of();
        String compact = compact(message);
        Set<String> tokens = new LinkedHashSet<>();
        if (compact.length() >= 4) tokens.add(compact);

        String[] parts = TOKEN_SPLIT.split(message.toLowerCase(Locale.ROOT));
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String n = compact(part.startsWith("@") ? part.substring(1) : part);
            if (n.length() >= 4) tokens.add(n);
        }
        return tokens;
    }
}
