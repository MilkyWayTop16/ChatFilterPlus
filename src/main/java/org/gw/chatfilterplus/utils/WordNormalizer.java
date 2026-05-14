package org.gw.chatfilterplus.utils;

import org.gw.chatfilterplus.ChatFilterPlus;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class WordNormalizer {

    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final Pattern REPEATED_CHARS = Pattern.compile("([аеёиоуыэюяa-z])\\1+", Pattern.CASE_INSENSITIVE);

    private static final int MAX_CACHE_SIZE = 8000;
    private static final Map<String, String> normalizationCache = new ConcurrentHashMap<>(MAX_CACHE_SIZE);

    private final SafeWordsTrie safeWordsTrie;

    public WordNormalizer(ChatFilterPlus plugin) {
        this.safeWordsTrie = new SafeWordsTrie(plugin.getConfigManager().getSafeWords());
    }

    public void reload(List<String> safeWords) {
        normalizationCache.clear();
        safeWordsTrie.reload(safeWords);
    }

    public static String normalize(String text, String filterLevel) {
        if (text == null || text.length() < 3) {
            return text;
        }

        if ("low".equalsIgnoreCase(filterLevel)) {
            return text.toLowerCase();
        }

        String cacheKey = text + ":" + filterLevel.toLowerCase();

        if (normalizationCache.size() > MAX_CACHE_SIZE) {
            normalizationCache.clear();
        }

        return normalizationCache.computeIfAbsent(cacheKey, k -> performNormalization(text, filterLevel));
    }

    private static String performNormalization(String text, String filterLevel) {
        String normalized = text.toLowerCase();
        normalized = SPECIAL_CHARS.matcher(normalized).replaceAll("");

        if (normalized.length() < 3) {
            return normalized;
        }

        normalized = REPEATED_CHARS.matcher(normalized).replaceAll("$1");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);

        return normalized;
    }

    public boolean isSafeWord(String word) {
        if (word == null || word.length() < 3) {
            return false;
        }
        String cleaned = SPECIAL_CHARS.matcher(word.toLowerCase()).replaceAll("");
        if (cleaned.length() < 3) return false;
        return safeWordsTrie.contains(cleaned);
    }

    private static class SafeWordsTrie {

        private static class TrieNode {
            final Map<Character, TrieNode> children = new ConcurrentHashMap<>();
            boolean isEndOfWord;
        }

        private TrieNode root;

        public SafeWordsTrie(List<String> safeWords) {
            reload(safeWords);
        }

        public void reload(List<String> safeWords) {
            root = new TrieNode();
            for (String word : safeWords) {
                if (word == null || word.length() < 3) continue;
                addWord(word.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", ""));
            }
        }

        private void addWord(String word) {
            TrieNode current = root;
            for (char c : word.toCharArray()) {
                current = current.children.computeIfAbsent(c, k -> new TrieNode());
            }
            current.isEndOfWord = true;
        }

        public boolean contains(String text) {
            if (text.length() < 3) return false;

            for (int i = 0; i < text.length(); i++) {
                TrieNode current = root;
                for (int j = i; j < text.length(); j++) {
                    current = current.children.get(text.charAt(j));
                    if (current == null) break;
                    if (current.isEndOfWord) return true;
                }
            }
            return false;
        }
    }
}