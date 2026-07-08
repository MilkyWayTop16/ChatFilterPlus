package org.gw.chatfilterplus.utils;

import java.util.*;

public final class ProfanityEngine {

    public record Match(int start, int end, String dictionaryWord, String matchedText, int confidence, String reason) {
    }

    public record PrecisionOptions(boolean fuzzyRequireObfuscation, int minFuzzyDictLength, int minConfidence) {
        public static PrecisionOptions forLevel(String filterLevel) {
            if (filterLevel != null && "high".equalsIgnoreCase(filterLevel)) {
                return new PrecisionOptions(true, 6, 80);
            }
            return new PrecisionOptions(false, 6, 70);
        }
    }

    private static final int ALPHABET = 36;

    private static final class TrieNode {
        final TrieNode[] children = new TrieNode[ALPHABET];
        String word;
    }

    private static final class CompactView {
        final String compact;
        final int[] originIndex;

        CompactView(String compact, int[] originIndex) {
            this.compact = compact;
            this.originIndex = originIndex;
        }

        static CompactView of(String message, boolean collapseRepeats) {
            int len = message.length();
            char[] compactBuf = new char[len];
            int[] originBuf = new int[len];
            int size = 0;
            char last = 0;

            for (int i = 0; i < len; i++) {
                int mapped = TextNormalizer.mapChar(message.charAt(i));
                if (mapped <= 0) continue;
                char ch = (char) mapped;
                if (collapseRepeats && size > 0 && ch == last) {
                    originBuf[size - 1] = i;
                    continue;
                }
                compactBuf[size] = ch;
                originBuf[size] = i;
                size++;
                last = ch;
            }

            return new CompactView(new String(compactBuf, 0, size), Arrays.copyOf(originBuf, size));
        }

        int originalStart(int compactStart) {
            return originIndex[compactStart];
        }

        int originalEndExclusive(int compactEndExclusive) {
            if (compactEndExclusive <= 0) return 0;
            return originIndex[compactEndExclusive - 1] + 1;
        }
    }

    private final TrieNode root = new TrieNode();
    private final TrieNode fuzzyRoot = new TrieNode();
    private final Set<String> dictionary = new HashSet<>();
    private final Set<String> safeExact = new HashSet<>();
    private final Map<String, String> fuzzyVariants = new HashMap<>();
    private final String level;
    private final int maxGap;
    private final boolean collapseRepeats;
    private final boolean fuzzyEnabled;
    private final PrecisionOptions precision;
    private final int minWordLength = 3;
    private boolean fuzzyHasEntries;

    public ProfanityEngine(Collection<String> badWords,
                           Collection<String> safeWords,
                           String filterLevel,
                           boolean detectEnglishLookalikes) {
        this(badWords, safeWords, filterLevel, detectEnglishLookalikes, PrecisionOptions.forLevel(filterLevel));
    }

    public ProfanityEngine(Collection<String> badWords,
                           Collection<String> safeWords,
                           String filterLevel,
                           boolean detectEnglishLookalikes,
                           PrecisionOptions precision) {
        this.level = filterLevel == null ? "high" : filterLevel.toLowerCase(Locale.ROOT);
        this.collapseRepeats = !"low".equals(this.level);
        this.fuzzyEnabled = "high".equals(this.level);
        this.precision = precision == null ? PrecisionOptions.forLevel(this.level) : precision;
        this.maxGap = switch (this.level) {
            case "low" -> 0;
            case "medium" -> 2;
            default -> 3;
        };

        if (safeWords != null) {
            for (String safe : safeWords) {
                if (safe == null) continue;
                String norm = TextNormalizer.normalizeCompact(safe, true);
                if (norm.length() >= 2) safeExact.add(norm);
            }
        }

        if (badWords != null) {
            for (String word : badWords) {
                addDictionaryWord(word);
                if (detectEnglishLookalikes) {
                    addDictionaryWord(TextNormalizer.toLatinTranslit(word));
                }
            }
        }
    }

    private static int childIndex(char c) {
        if (c >= 'a' && c <= 'z') return c - 'a';
        if (c >= '0' && c <= '9') return 26 + (c - '0');
        return -1;
    }

    private static TrieNode getChild(TrieNode node, char c) {
        int idx = childIndex(c);
        if (idx < 0) return null;
        return node.children[idx];
    }

    private static TrieNode getOrCreateChild(TrieNode node, char c) {
        int idx = childIndex(c);
        if (idx < 0) return null;
        TrieNode child = node.children[idx];
        if (child == null) {
            child = new TrieNode();
            node.children[idx] = child;
        }
        return child;
    }

    private void addDictionaryWord(String word) {
        if (word == null) return;
        String norm = TextNormalizer.normalizeCompact(word, true);
        if (norm.length() < minWordLength) return;
        if (safeExact.contains(norm)) return;
        if (!dictionary.add(norm)) return;

        TrieNode node = root;
        for (int i = 0; i < norm.length(); i++) {
            node = getOrCreateChild(node, norm.charAt(i));
            if (node == null) return;
        }
        node.word = norm;

        int minFuzzy = Math.max(5, precision.minFuzzyDictLength());
        if (fuzzyEnabled && norm.length() >= minFuzzy) {
            for (int skip = 0; skip < norm.length(); skip++) {
                String reduced = norm.substring(0, skip) + norm.substring(skip + 1);
                if (reduced.length() < minFuzzy - 1) continue;
                if (safeExact.contains(reduced)) continue;
                if (dictionary.contains(reduced)) continue;
                if (fuzzyVariants.putIfAbsent(reduced, norm) != null) continue;

                TrieNode fuzzyNode = fuzzyRoot;
                boolean ok = true;
                for (int i = 0; i < reduced.length(); i++) {
                    fuzzyNode = getOrCreateChild(fuzzyNode, reduced.charAt(i));
                    if (fuzzyNode == null) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    if (fuzzyNode.word == null) {
                        fuzzyNode.word = norm;
                    }
                    fuzzyHasEntries = true;
                }
            }
        }
    }

    public boolean isEmpty() {
        return dictionary.isEmpty();
    }

    public int minConfidence() {
        return precision.minConfidence();
    }

    public List<Match> findMatches(String message) {
        if (message == null || message.length() < minWordLength || isEmpty()) {
            return List.of();
        }

        List<Match> matches;
        if ("low".equals(level)) {
            matches = findTokenMatches(message);
        } else {
            matches = new ArrayList<>(findStreamMatches(message));
            if (fuzzyEnabled && fuzzyHasEntries
                    && (!precision.fuzzyRequireObfuscation() || hasObfuscation(message))) {
                matches.addAll(findFuzzyCompactMatches(message));
            }
        }

        if (matches.isEmpty()) return matches;

        List<Match> filtered = new ArrayList<>(matches.size());
        int minConf = precision.minConfidence();
        for (Match match : matches) {
            if (match.confidence() >= minConf) {
                filtered.add(match);
            }
        }
        return mergeOverlaps(filtered);
    }

    public static boolean hasObfuscation(String message) {
        if (message == null || message.isEmpty()) return false;

        boolean sawLetter = false;
        boolean hasLeet = false;
        boolean hasSeparatorBetweenLetters = false;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            if (!hasLeet && isLeetSubstitutionChar(c) && hasAdjacentMappedLetter(message, i)) {
                hasLeet = true;
            }

            int mapped = TextNormalizer.mapChar(c);
            if (mapped > 0) {
                sawLetter = true;
            } else if (mapped < 0 && sawLetter) {
                int j = i + 1;
                while (j < message.length() && TextNormalizer.mapChar(message.charAt(j)) == 0) {
                    j++;
                }
                if (j < message.length() && TextNormalizer.mapChar(message.charAt(j)) > 0) {
                    hasSeparatorBetweenLetters = true;
                }
            }
        }

        return hasLeet || hasSeparatorBetweenLetters;
    }

    private static boolean isLeetSubstitutionChar(char c) {
        return c == '0' || c == '1' || c == '3' || c == '4' || c == '5' || c == '7'
                || c == '@' || c == '$' || c == '*' || c == '!';
    }

    private static boolean hasAdjacentMappedLetter(String message, int index) {
        for (int i = index - 1; i >= 0; i--) {
            int mapped = TextNormalizer.mapChar(message.charAt(i));
            if (mapped > 0) return true;
            if (mapped < 0) break;
        }
        for (int i = index + 1; i < message.length(); i++) {
            int mapped = TextNormalizer.mapChar(message.charAt(i));
            if (mapped > 0) return true;
            if (mapped < 0) break;
        }
        return false;
    }

    private List<Match> findTokenMatches(String message) {
        List<Match> matches = new ArrayList<>();
        int i = 0;
        while (i < message.length()) {
            while (i < message.length() && !TextNormalizer.isLetterOrDigit(message.charAt(i))) i++;
            if (i >= message.length()) break;
            int start = i;
            while (i < message.length() && TextNormalizer.isLetterOrDigit(message.charAt(i))) i++;
            int end = i;

            String token = message.substring(start, end);
            String norm = TextNormalizer.normalizeCompact(token, collapseRepeats);
            if (norm.length() < minWordLength || safeExact.contains(norm)) continue;

            if (dictionary.contains(norm)) {
                matches.add(new Match(start, end, norm, token, 100, "exact-token"));
                continue;
            }

            if (fuzzyEnabled && (!precision.fuzzyRequireObfuscation() || hasObfuscation(token))) {
                String dictWord = fuzzyVariants.get(norm);
                if (dictWord != null && dictWord.length() >= precision.minFuzzyDictLength()) {
                    matches.add(new Match(start, end, dictWord, token, 80, "fuzzy-token"));
                }
            }
        }
        return matches;
    }

    private List<Match> findStreamMatches(String message) {
        List<Match> matches = new ArrayList<>();
        int length = message.length();

        for (int start = 0; start < length; start++) {
            char startChar = message.charAt(start);
            if (isIgnorableNoise(startChar)) continue;

            int mapped = TextNormalizer.mapChar(startChar);
            if (mapped <= 0) continue;

            TrieNode node = getChild(root, (char) mapped);
            if (node == null) continue;

            int lastLetterIndex = start;
            char lastNorm = (char) mapped;
            int pos = start + 1;

            while (true) {
                if (node.word != null) {
                    maybeAddMatch(matches, message, start, lastLetterIndex + 1, node.word, 92, "stream");
                }
                if (pos >= length) break;

                char raw = message.charAt(pos);
                if (isIgnorableNoise(raw)) {
                    pos++;
                    continue;
                }

                int m = TextNormalizer.mapChar(raw);
                if (m > 0) {
                    char ch = (char) m;
                    if (collapseRepeats && ch == lastNorm) {
                        lastLetterIndex = pos;
                        pos++;
                        continue;
                    }
                    TrieNode next = getChild(node, ch);
                    if (next == null) break;
                    node = next;
                    lastNorm = ch;
                    lastLetterIndex = pos;
                    pos++;
                    continue;
                }

                int gapEnd = pos;
                int gapCount = 0;
                while (gapEnd < length && gapCount < maxGap) {
                    char g = message.charAt(gapEnd);
                    if (isIgnorableNoise(g)) {
                        gapEnd++;
                        continue;
                    }
                    if (TextNormalizer.mapChar(g) > 0) break;
                    gapCount++;
                    gapEnd++;
                }
                if (gapCount == 0 || gapEnd >= length) break;

                while (gapEnd < length && isIgnorableNoise(message.charAt(gapEnd))) {
                    gapEnd++;
                }
                if (gapEnd >= length) break;

                int nextMapped = TextNormalizer.mapChar(message.charAt(gapEnd));
                if (nextMapped <= 0) break;
                char nextCh = (char) nextMapped;
                if (collapseRepeats && nextCh == lastNorm) {
                    lastLetterIndex = gapEnd;
                    pos = gapEnd + 1;
                    continue;
                }
                TrieNode next = getChild(node, nextCh);
                if (next == null) break;
                node = next;
                lastNorm = nextCh;
                lastLetterIndex = gapEnd;
                pos = gapEnd + 1;
            }
        }
        return matches;
    }

    private List<Match> findFuzzyCompactMatches(String message) {
        CompactView view = CompactView.of(message, true);
        String text = view.compact;
        int minLen = Math.max(4, precision.minFuzzyDictLength() - 1);
        if (text.length() < minLen) return List.of();

        List<Match> matches = new ArrayList<>();
        int length = text.length();

        for (int start = 0; start < length; start++) {
            TrieNode node = getChild(fuzzyRoot, text.charAt(start));
            if (node == null) continue;

            for (int i = start; i < length; ) {
                if (node.word != null) {
                    String dictWord = node.word;
                    if (dictWord.length() >= precision.minFuzzyDictLength() && !text.contains(dictWord)) {
                        addCompactMatch(matches, message, view, start, i + 1, dictWord, 80, "fuzzy-compact");
                    }
                }
                i++;
                if (i >= length) break;
                TrieNode next = getChild(node, text.charAt(i));
                if (next == null) break;
                node = next;
            }
        }
        return matches;
    }

    private void addCompactMatch(List<Match> matches,
                                 String message,
                                 CompactView view,
                                 int compactStart,
                                 int compactEndExclusive,
                                 String dictWord,
                                 int confidence,
                                 String reason) {
        int origStart = view.originalStart(compactStart);
        int origEnd = expandTrailingSoftSigns(message, view.originalEndExclusive(compactEndExclusive));

        int left = origStart;
        while (left > 0 && TextNormalizer.isLetterOrDigit(message.charAt(left - 1))) left--;
        int right = origEnd;
        while (right < message.length() && TextNormalizer.isLetterOrDigit(message.charAt(right))) right++;

        String fullToken = message.substring(left, right);
        String fullNorm = TextNormalizer.normalizeCompact(fullToken, true);
        if (safeExact.contains(fullNorm)) return;

        if (!fullNorm.equals(dictWord)
                && !dictionary.contains(fullNorm)
                && fullNorm.contains(dictWord)
                && !isFuzzyFormOf(fullNorm, dictWord)) {
            return;
        }

        if (!fullNorm.equals(dictWord)
                && !dictionary.contains(fullNorm)
                && !isFuzzyFormOf(fullNorm, dictWord)
                && fullNorm.length() > dictWord.length() + 2) {
            return;
        }

        int matchStart = left;
        int matchEnd = right;
        if (!fullNorm.equals(dictWord) && !isFuzzyFormOf(fullNorm, dictWord) && !dictionary.contains(fullNorm)) {
            matchStart = origStart;
            matchEnd = origEnd;
        }

        matches.add(new Match(matchStart, matchEnd, dictWord,
                message.substring(matchStart, matchEnd), confidence, reason));
    }

    private static boolean isIgnorableNoise(char c) {
        return TextNormalizer.mapChar(c) == 0;
    }

    private void maybeAddMatch(List<Match> matches, String message, int start, int endExclusive,
                               String dictWord, int confidence, String reason) {
        if (start < 0 || endExclusive > message.length() || start >= endExclusive) return;

        int left = start;
        while (left > 0 && TextNormalizer.isLetterOrDigit(message.charAt(left - 1))) {
            left--;
        }
        int right = endExclusive;
        while (right < message.length() && TextNormalizer.isLetterOrDigit(message.charAt(right))) {
            right++;
        }

        String fullToken = message.substring(left, right);
        String fullNorm = TextNormalizer.normalizeCompact(fullToken, true);
        if (safeExact.contains(fullNorm)) return;

        if (!fullNorm.equals(dictWord)
                && !dictionary.contains(fullNorm)
                && fullNorm.contains(dictWord)
                && !isFuzzyFormOf(fullNorm, dictWord)) {
            return;
        }

        int matchStart = start;
        int matchEnd = expandTrailingSoftSigns(message, endExclusive);
        int score = confidence;
        String matchReason = reason;

        if (fullNorm.equals(dictWord) || dictionary.contains(fullNorm)) {
            matchStart = left;
            matchEnd = right;
            score = Math.max(score, 100);
            matchReason = "exact-word";
        } else if (isFuzzyFormOf(fullNorm, dictWord)) {
            if (precision.fuzzyRequireObfuscation() && !hasObfuscation(fullToken) && !hasObfuscation(message)) {
                return;
            }
            matchStart = left;
            matchEnd = right;
            score = Math.min(score, 80);
            matchReason = "fuzzy-word";
        }

        matches.add(new Match(matchStart, matchEnd, dictWord,
                message.substring(matchStart, matchEnd), score, matchReason));
    }

    private boolean isFuzzyFormOf(String candidate, String dictWord) {
        if (!fuzzyEnabled || dictWord.length() < precision.minFuzzyDictLength()) return false;
        if (candidate.equals(dictWord)) return true;
        if (candidate.length() == dictWord.length() - 1) {
            return dictWord.equals(fuzzyVariants.get(candidate))
                    || isSingleDeletion(dictWord, candidate);
        }
        return false;
    }

    private static boolean isSingleDeletion(String full, String reduced) {
        if (full.length() != reduced.length() + 1) return false;
        int i = 0;
        int j = 0;
        int skipped = 0;
        while (i < full.length() && j < reduced.length()) {
            if (full.charAt(i) == reduced.charAt(j)) {
                i++;
                j++;
            } else {
                skipped++;
                if (skipped > 1) return false;
                i++;
            }
        }
        if (i < full.length()) skipped += full.length() - i;
        return skipped == 1 && j == reduced.length();
    }

    private int expandTrailingSoftSigns(String message, int endExclusive) {
        int end = endExclusive;
        while (end < message.length()) {
            char c = message.charAt(end);
            if (c == 'ь' || c == 'ъ' || c == 'Ь' || c == 'Ъ') {
                end++;
                continue;
            }
            break;
        }
        return end;
    }

    private List<Match> mergeOverlaps(List<Match> matches) {
        if (matches.size() <= 1) return matches;

        matches.sort(Comparator
                .comparingInt(Match::start)
                .thenComparing((a, b) -> Integer.compare(b.confidence(), a.confidence()))
                .thenComparing((a, b) -> Integer.compare(b.end - b.start, a.end - a.start)));

        List<Match> result = new ArrayList<>(matches.size());
        int lastEnd = -1;
        for (Match match : matches) {
            if (match.start < lastEnd) continue;
            result.add(match);
            lastEnd = match.end;
        }
        return result;
    }
}
