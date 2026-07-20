package org.gw.chatfilterplus.managers.profanity;

import org.gw.chatfilterplus.utils.TextNormalizer;

import java.util.*;
import java.util.function.Supplier;

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

    private static final int SPACED_FRAGMENTS_CONFIDENCE = 95;
    private static final int MAX_FRAGMENT_LENGTH = 3;
    private static final int MIN_SPACED_TOKENS = 2;

    private static final String[] FUNCTION_WORDS_RAW = {
            "у", "в", "на", "за", "по", "до", "от", "из", "с", "к", "о", "об", "под", "над", "при",
            "про", "для", "без", "через", "между", "перед", "после", "около", "среди", "вокруг",
            "и", "а", "но", "да", "нет", "не", "ну", "же", "ли", "бы", "то", "так", "как", "что",
            "это", "вот", "там", "тут", "здесь", "уже", "еще", "ещё", "только", "даже", "тоже",
            "если", "чтобы", "когда", "где", "куда", "или", "либо", "ведь", "разве", "неужели",
            "я", "ты", "он", "она", "оно", "мы", "вы", "они", "мне", "тебе", "ему", "ей", "нам",
            "вам", "им", "меня", "тебя", "его", "ее", "её", "нас", "вас", "их", "кто", "чей",
            "весь", "все", "вся", "себя", "свой", "мой", "твой", "наш", "ваш"
    };

    private static final Set<String> FUNCTION_WORDS = Set.copyOf(Arrays.asList(FUNCTION_WORDS_RAW));

    private final ProfanityTrie exactTrie = new ProfanityTrie();
    private final ProfanityTrie fuzzyTrie = new ProfanityTrie();
    private final ProfanityTrie safeTrie = new ProfanityTrie();
    private final Set<String> dictionary = new HashSet<>();
    private final Set<String> safeExact = new HashSet<>();
    private final Supplier<Set<String>> protectedNames;
    private final Map<String, String> fuzzyVariants = new HashMap<>();
    private final String level;
    private final int maxGap;
    private final boolean collapseRepeats;
    private final boolean fuzzyEnabled;
    private final PrecisionOptions precision;
    private final int minWordLength = 3;
    private boolean fuzzyHasEntries;
    private boolean safeTrieHasEntries;

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
        this(badWords, safeWords, filterLevel, detectEnglishLookalikes, precision, Set::of);
    }

    public ProfanityEngine(Collection<String> badWords,
                           Collection<String> safeWords,
                           String filterLevel,
                           boolean detectEnglishLookalikes,
                           PrecisionOptions precision,
                           Supplier<Set<String>> protectedNames) {
        this.protectedNames = protectedNames == null ? Set::of : protectedNames;
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
                if (norm.length() >= 2) {
                    safeExact.add(norm);
                    if (norm.length() >= minWordLength) {
                        safeTrie.insertWord(norm);
                        safeTrieHasEntries = true;
                    }
                }
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

    private static String lexicalForm(String token) {
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private void addDictionaryWord(String word) {
        if (word == null) return;
        String norm = TextNormalizer.normalizeCompact(word, true);
        if (norm.length() < minWordLength) return;
        if (safeExact.contains(norm)) return;
        if (!dictionary.add(norm)) return;

        exactTrie.insertWord(norm);

        int minFuzzy = Math.max(5, precision.minFuzzyDictLength());
        if (fuzzyEnabled && norm.length() >= minFuzzy) {
            for (int skip = 0; skip < norm.length(); skip++) {
                String reduced = norm.substring(0, skip) + norm.substring(skip + 1);
                if (reduced.length() < minFuzzy - 1) continue;
                if (safeExact.contains(reduced)) continue;
                if (dictionary.contains(reduced)) continue;
                if (fuzzyVariants.putIfAbsent(reduced, norm) != null) continue;

                if (fuzzyTrie.insertReduced(reduced, norm)) {
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

    public static boolean hasObfuscation(String message) {
        return ObfuscationDetector.hasObfuscation(message);
    }

    private record Token(String text, int start, String compact) {
        int end() {
            return start + text.length();
        }
    }

    private List<Token> tokenize(String message) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = message.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(message.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && !Character.isWhitespace(message.charAt(i))) i++;
            String text = message.substring(start, i);
            tokens.add(new Token(text, start, TextNormalizer.normalizeCompact(text, collapseRepeats)));
        }
        return tokens;
    }

    public List<Match> findMatches(String message) {
        if (message == null || message.length() < minWordLength || isEmpty()) {
            return List.of();
        }

        List<Match> matches;
        if ("low".equals(level)) {
            matches = findTokenMatches(message);
        } else {
            List<Token> tokens = tokenize(message);
            matches = new ArrayList<>();
            for (Token token : tokens) {
                if (token.compact().length() < minWordLength && token.text().length() < minWordLength) {
                    continue;
                }
                addShifted(matches, findStreamMatches(token.text()), token.start());

                if (fuzzyEnabled && fuzzyHasEntries
                        && (!precision.fuzzyRequireObfuscation() || hasObfuscation(token.text()))) {
                    addShifted(matches, findFuzzyCompactMatches(token.text()), token.start());
                }
            }
            matches.addAll(findSpacedFragmentMatches(message, tokens));
        }

        if (matches.isEmpty()) return matches;

        List<int[]> protectedRegions = "low".equals(level)
                ? List.of()
                : findProtectedRegions(tokenize(message));

        List<Match> filtered = new ArrayList<>(matches.size());
        int minConf = precision.minConfidence();
        for (Match match : matches) {
            if (match.confidence() < minConf) continue;
            if (overlapsProtected(match, protectedRegions)) continue;
            filtered.add(match);
        }
        return mergeOverlaps(filtered);
    }

    private List<int[]> findProtectedRegions(List<Token> tokens) {
        Set<String> names = protectedNames.get();
        boolean hasNames = names != null && !names.isEmpty();
        if (!hasNames && !safeTrieHasEntries) {
            return List.of();
        }

        List<int[]> regions = new ArrayList<>();
        for (Token token : tokens) {
            if (hasNames && isProtectedNameToken(token, names)) {
                regions.add(new int[]{token.start(), token.end()});
                continue;
            }
            collectSafeRegions(token, regions);
        }
        return regions;
    }

    private boolean isProtectedNameToken(Token token, Set<String> names) {
        String lexical = lexicalForm(token.text());
        if (lexical.isEmpty() || !names.contains(lexical)) return false;
        return !carriesProfanity(token.compact());
    }

    private boolean carriesProfanity(String compact) {
        if (compact == null || compact.length() < minWordLength) return false;
        return containsWord(exactTrie, compact) || (fuzzyHasEntries && containsWord(fuzzyTrie, compact));
    }

    private boolean containsWord(ProfanityTrie trie, String compact) {
        for (int s = 0; s + minWordLength <= compact.length(); s++) {
            ProfanityTrie.Node node = trie.root;
            for (int e = s; e < compact.length(); e++) {
                node = ProfanityTrie.getChild(node, compact.charAt(e));
                if (node == null) break;
                if (node.word != null) return true;
            }
        }
        return false;
    }

    private void collectSafeRegions(Token token, List<int[]> regions) {
        if (!safeTrieHasEntries) return;

        String text = token.text();
        if (text.length() < minWordLength) return;

        CompactView view = CompactView.of(text, collapseRepeats);
        String compact = view.compact;
        if (compact.length() < minWordLength) return;

        for (int s = 0; s < compact.length(); s++) {
            ProfanityTrie.Node node = safeTrie.root;
            for (int e = s; e < compact.length(); e++) {
                node = ProfanityTrie.getChild(node, compact.charAt(e));
                if (node == null) break;
                if (node.word == null || e - s + 1 < minWordLength) continue;

                int start = token.start() + view.originalStart(s);
                int end = token.start() + view.originalEndExclusive(e + 1);
                regions.add(new int[]{start, end});
            }
        }
    }

    private static boolean overlapsProtected(Match match, List<int[]> regions) {
        if (regions.isEmpty()) return false;
        for (int[] region : regions) {
            if (match.start() < region[1] && region[0] < match.end()) {
                return true;
            }
        }
        return false;
    }

    private void addShifted(List<Match> out, List<Match> found, int offset) {
        if (offset == 0) {
            out.addAll(found);
            return;
        }
        for (Match m : found) {
            out.add(new Match(m.start() + offset, m.end() + offset,
                    m.dictionaryWord(), m.matchedText(), m.confidence(), m.reason()));
        }
    }

    private List<Match> findSpacedFragmentMatches(String message, List<Token> tokens) {
        List<Match> out = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            String compact = tokens.get(i).compact();
            if (compact.isEmpty() || compact.length() > MAX_FRAGMENT_LENGTH) {
                i++;
                continue;
            }
            int j = i;
            while (j < tokens.size()) {
                String next = tokens.get(j).compact();
                if (next.isEmpty() || next.length() > MAX_FRAGMENT_LENGTH) break;
                j++;
            }
            if (j - i >= MIN_SPACED_TOKENS) {
                collectFragmentRunMatches(message, tokens, i, j, out);
            }
            i = j == i ? i + 1 : j;
        }
        return out;
    }

    private void collectFragmentRunMatches(String message, List<Token> tokens, int from, int to, List<Match> out) {
        int runLen = to - from;
        int[] compactStart = new int[runLen];
        int[] compactEnd = new int[runLen];
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < runLen; k++) {
            compactStart[k] = sb.length();
            sb.append(tokens.get(from + k).compact());
            compactEnd[k] = sb.length();
        }
        String run = sb.toString();
        if (run.length() < minWordLength) return;

        boolean[] boundaryStart = new boolean[run.length() + 1];
        boolean[] boundaryEnd = new boolean[run.length() + 1];
        int[] tokenAtStart = new int[run.length() + 1];
        int[] tokenAtEnd = new int[run.length() + 1];
        Arrays.fill(tokenAtStart, -1);
        Arrays.fill(tokenAtEnd, -1);
        for (int k = 0; k < runLen; k++) {
            boundaryStart[compactStart[k]] = true;
            boundaryEnd[compactEnd[k]] = true;
            tokenAtStart[compactStart[k]] = k;
            tokenAtEnd[compactEnd[k]] = k;
        }

        for (int s = 0; s < run.length(); s++) {
            if (!boundaryStart[s]) continue;

            ProfanityTrie.Node node = exactTrie.root;
            for (int e = s; e < run.length(); e++) {
                node = ProfanityTrie.getChild(node, run.charAt(e));
                if (node == null) break;

                int endExclusive = e + 1;
                if (node.word == null || endExclusive - s < minWordLength) continue;
                if (!boundaryEnd[endExclusive]) continue;
                if (safeExact.contains(node.word)) continue;

                int tokenFrom = tokenAtStart[s];
                int tokenTo = tokenAtEnd[endExclusive];
                if (tokenFrom < 0 || tokenTo < 0 || tokenTo - tokenFrom + 1 < MIN_SPACED_TOKENS) continue;

                // This pass catches a single word spelled out with spaces. Two shapes are legitimate:
                //  - fully atomised, one fragment per letter ("х у й", "с у к а", "п и з д а"): the
                //    fragment lengths sum to exactly (endExclusive - s), so tokenCount == letterCount
                //    means every fragment is a single character — always a deliberate evasion;
                //  - a word split into a few non-word chunks ("нах уй", "пи зда", "бл ять").
                // What must NOT match is ordinary short words that merely abut into a dictionary
                // word. Every such false positive leans on a function word gluing the pieces
                // ("у род"→урод, "ах у"→аху, "иди от"→идиот, "род он ку"→подонку), so a multi-letter
                // run that contains any function-word fragment is rejected. Fully atomised runs are
                // always allowed, even when every fragment is a function word ("с у к а"): the only
                // dictionary words spellable from single function-word letters (у в с к о я) are
                // сук/сука/суки, i.e. profanity themselves.
                int spanFrom = from + tokenFrom;
                int spanTo = from + tokenTo + 1;
                boolean atomised = tokenTo - tokenFrom + 1 >= endExclusive - s;
                if (!atomised && anyFunctionWord(tokens, spanFrom, spanTo)) continue;

                Token startTok = tokens.get(from + tokenFrom);
                Token endTok = tokens.get(from + tokenTo);
                int start = startTok.start();
                int end = endTok.end();
                out.add(new Match(start, end, node.word, message.substring(start, end),
                        SPACED_FRAGMENTS_CONFIDENCE, "spaced-fragments"));
            }
        }
    }

    private boolean anyFunctionWord(List<Token> tokens, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (FUNCTION_WORDS.contains(lexicalForm(tokens.get(i).text()))) {
                return true;
            }
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

            ProfanityTrie.Node node = ProfanityTrie.getChild(exactTrie.root, (char) mapped);
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
                    ProfanityTrie.Node next = ProfanityTrie.getChild(node, ch);
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
                ProfanityTrie.Node next = ProfanityTrie.getChild(node, nextCh);
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
            ProfanityTrie.Node node = ProfanityTrie.getChild(fuzzyTrie.root, text.charAt(start));
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
                ProfanityTrie.Node next = ProfanityTrie.getChild(node, text.charAt(i));
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
