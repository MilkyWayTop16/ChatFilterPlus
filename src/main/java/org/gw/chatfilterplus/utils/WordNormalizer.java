package org.gw.chatfilterplus.utils;

import org.gw.chatfilterplus.ChatFilterPlus;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class WordNormalizer {

    private static final Pattern REPEATED_CHARS = Pattern.compile("(\\p{L})\\1+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final SafeWordsTrie safeWordsTrie;

    public WordNormalizer(ChatFilterPlus plugin) {
        this.safeWordsTrie = new SafeWordsTrie(plugin.getConfigManager().getSafeWords());
    }

    public void reload(List<String> safeWords) {
        safeWordsTrie.reload(safeWords);
    }

    public static String normalizeForSimilarity(String text) {
        if (text == null || text.isEmpty()) return "";

        String normalized = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFKC);
        return REPEATED_CHARS.matcher(normalized).replaceAll("$1").trim();
    }

    public boolean isSafeWord(String word) {
        if (word == null || word.length() < 2) {
            return false;
        }
        String cleaned = TextNormalizer.normalizeCompact(word, true);
        if (cleaned.length() < 2) return false;
        return safeWordsTrie.contains(cleaned);
    }

    private static class SafeWordsTrie {

        private static class TrieNode {
            final Map<Character, TrieNode> children = new HashMap<>();
            boolean isEndOfWord;
        }

        private volatile TrieNode root;

        public SafeWordsTrie(List<String> safeWords) {
            reload(safeWords);
        }

        public void reload(List<String> safeWords) {
            TrieNode newRoot = new TrieNode();
            for (String word : safeWords) {
                if (word == null || word.length() < 2) continue;
                String norm = TextNormalizer.normalizeCompact(word, true);
                if (norm.length() >= 2) addWord(newRoot, norm);
            }
            root = newRoot;
        }

        private void addWord(TrieNode target, String word) {
            TrieNode current = target;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                current = current.children.computeIfAbsent(c, k -> new TrieNode());
            }
            current.isEndOfWord = true;
        }

        public boolean contains(String text) {
            if (text.length() < 3) return false;

            TrieNode rootNode = root;
            for (int i = 0; i < text.length(); i++) {
                TrieNode current = rootNode;
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
