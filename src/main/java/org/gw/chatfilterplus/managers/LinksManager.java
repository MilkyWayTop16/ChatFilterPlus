package org.gw.chatfilterplus.managers;

import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LinksManager {

    public record LinkMatch(int start, int end, String text) {
    }

    private static final class LinkSettings {
        final Pattern linkPattern;
        final Pattern ipPattern;
        final Pattern discordPattern;
        final Pattern obfuscatedDomainPattern;
        final Pattern inviteCodePattern;
        final Set<String> normalizedDomains;
        final String listFilterMode;
        final Set<String> realTlds;
        final Set<String> quickTriggers;
        final int minDomainParts;
        final boolean requireRealTld;
        final boolean smartDetectionEnabled;
        final String logSingleLinkTemplate;
        final String logLinkTemplate;
        final String logSeparator;
        final String translatedReplacement;

        LinkSettings(Pattern linkPattern,
                     Pattern ipPattern,
                     Pattern discordPattern,
                     Pattern obfuscatedDomainPattern,
                     Pattern inviteCodePattern,
                     Set<String> normalizedDomains,
                     String listFilterMode,
                     Set<String> realTlds,
                     Set<String> quickTriggers,
                     int minDomainParts,
                     boolean requireRealTld,
                     boolean smartDetectionEnabled,
                     String logSingleLinkTemplate,
                     String logLinkTemplate,
                     String logSeparator,
                     String translatedReplacement) {
            this.linkPattern = linkPattern;
            this.ipPattern = ipPattern;
            this.discordPattern = discordPattern;
            this.obfuscatedDomainPattern = obfuscatedDomainPattern;
            this.inviteCodePattern = inviteCodePattern;
            this.normalizedDomains = normalizedDomains;
            this.listFilterMode = listFilterMode;
            this.realTlds = realTlds;
            this.quickTriggers = quickTriggers;
            this.minDomainParts = minDomainParts;
            this.requireRealTld = requireRealTld;
            this.smartDetectionEnabled = smartDetectionEnabled;
            this.logSingleLinkTemplate = logSingleLinkTemplate;
            this.logLinkTemplate = logLinkTemplate;
            this.logSeparator = logSeparator;
            this.translatedReplacement = translatedReplacement;
        }
    }

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final AdaptiveAdFilter adaptiveAdFilter;
    private final AtomicReference<LinkSettings> settingsRef = new AtomicReference<>();

    private static final Pattern INVISIBLE_CHARS = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00A0\\u1680\\u180E\\u2000-\\u200F\\u2028\\u2029\\u202F\\u205F\\u3000]+");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-z0-9-]{2,}\\.[a-z0-9-]{2,}");

    private static final Pattern SCHEME_PREFIX = Pattern.compile("(?i)^\\s*h\\s*t\\s*t\\s*p\\s*s?\\s*:\\s*/\\s*/\\s*");

    private static final Pattern OBFUSCATED_DOT = Pattern.compile(
            "(?i)\\[dot]|\\(dot\\)|\\{dot}|\\.dot\\.|\\s+dot\\s+|\\s*(?:точка|тчк|т\\.ч\\.к\\.|с\\s+точкой|с\\s+тчк)\\s*");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MULTIPLE_DOTS = Pattern.compile("\\.{2,}");
    private static final Pattern EDGE_TRIM = Pattern.compile("^[-.]+|[-.]+$");

    private static final Map<Character, String> HOMOGLYPHS = buildHomoglyphs();

    // Real TLDs that are also common English words/abbreviations and are essentially never used by
    // Russian servers as an advertised zone. They are excluded from the space-separated domain scan
    // so ordinary English-ish phrases ("you are the best", "join our team", "watch tv") are not
    // misread as spaced-out domains. Explicit-separator detection (server.best, team.today) is
    // unaffected — this list only gates the whitespace scan.
    private static final Set<String> SPACED_TLD_STOPWORDS = Set.of(
            "me", "us", "gg", "tv", "co", "cc", "io",
            "art", "team", "best", "today", "news", "win", "life", "live", "chat", "app",
            "media", "page", "group", "tools", "vip", "bet", "wiki",
            "top", "pro", "space", "world", "link", "click", "games", "host", "website");

    public LinksManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.adaptiveAdFilter = new AdaptiveAdFilter(plugin, configManager);
        load();
    }

    public AdaptiveAdFilter getAdaptiveAdFilter() {
        return adaptiveAdFilter;
    }

    private LinkSettings settings() {
        return settingsRef.get();
    }

    private static Map<Character, String> buildHomoglyphs() {
        Map<Character, String> map = new HashMap<>();

        String smallCaps = "ᴍᴄʀᴇᴀʟʏᴡᴏᴜᴅɴᴛꜱɪʙɢʜᴋᴘꜰᴠᴢ";
        String smallCapsLatin = "mcrealywoudntsibghkpfvz";
        for (int i = 0; i < smallCaps.length(); i++) {
            map.put(smallCaps.charAt(i), String.valueOf(smallCapsLatin.charAt(i)));
        }

        String cyrillic = "саеорхукмтнвгдлпфий";
        String cyrillicLatin = "caeopxykmthvgdlnfii";
        for (int i = 0; i < cyrillic.length(); i++) {
            map.put(cyrillic.charAt(i), String.valueOf(cyrillicLatin.charAt(i)));
        }
        map.put('э', "e");
        map.put('ю', "u");
        map.put('я', "ya");
        map.put('ь', "");
        map.put('ъ', "");

        for (char c = 'a'; c <= 'z'; c++) {
            map.put((char) ('ａ' + (c - 'a')), String.valueOf(c));
        }
        return Map.copyOf(map);
    }

    private void load() {
        settingsRef.set(buildSettings());
    }

    private static final String FALLBACK_LINK_REGEX = "(?i)[\\w\\p{L}\\-]+(?:\\.[\\w\\p{L}\\-]+){1,}";

    private LinkSettings buildSettings() {
        Pattern linkPattern = compileLinkRegex(configManager.getLinksRegex());

        Pattern ipPattern = Pattern.compile(
                "(?i)\\b\\d{1,3}(?:\\s*[\\.\\,\\-\\u2024\\u00B7]\\s*\\d{1,3}){3}(?:\\s*:\\s*\\d{2,5})?\\b");
        Pattern discordPattern = Pattern.compile(
                "(?i)(?:https?://)?(?:www\\.|ptb\\.|canary\\.)?(?:discord(?:app)?\\.(?:com|gg)/(?:invite|servers?)/?|discord\\.gg/)[a-z0-9-_]{2,32}");
        // Separators between domain labels: an explicit punctuation dot/comma/slash, or a spelled-out
        // dot alias ("dot"/"точка"/"тчк"). Bare whitespace is deliberately NOT a separator here — a
        // run of ordinary words ("so much fun", "let me play") must not be read as a spaced-out domain.
        String obfSep = "(?:\\s*[\\.\\,\\u2024\\u00B7/;]\\s*|\\s+(?:dot|точка|тчк)\\s+)";
        Pattern obfuscatedDomainPattern = Pattern.compile(
                "(?i)\\b[\\w\\p{L}][\\w\\p{L}\\-]{0,62}" + obfSep
                        + "[\\w\\p{L}][\\w\\p{L}\\-]{0,48}" + obfSep
                        + "[\\w\\p{L}]{2,24}\\b"
                        + "|(?i)\\b[\\w\\p{L}][\\w\\p{L}\\-]{1,62}" + obfSep + "[\\w\\p{L}]{2,12}\\b");
        Pattern inviteCodePattern = Pattern.compile(
                "(?i)\\b(?:t\\.me|telegram\\.me|tg:\\/\\/join|vk\\.com|vk\\.cc|bit\\.ly|goo\\.gl|tinyurl\\.com|youtu\\.be)\\/[\\w\\-]{3,64}\\b"
                        + "|(?i)\\b(?:dsc\\.gg|invite\\.gg)\\/[\\w\\-]{2,32}\\b");

        boolean smartDetectionEnabled = configManager.getLinksConfig().getBoolean("filter.smart-detection.enabled", true);
        Set<String> quickTriggers = toLowerCaseSet(configManager.getLinksConfig().getStringList("filter.smart-detection.quick-triggers"));
        Set<String> realTlds = toLowerCaseSet(configManager.getLinksConfig().getStringList("filter.smart-detection.tlds"));
        int minDomainParts = configManager.getLinksConfig().getInt("filter.smart-detection.min-domain-parts", 2);
        boolean requireRealTld = configManager.getLinksConfig().getBoolean("filter.smart-detection.require-real-tld", true);

        Set<String> normalized = new HashSet<>();
        for (String domain : configManager.getLinksListFilterDomains()) {
            if (domain == null || domain.trim().isEmpty()) continue;
            String clean = extractDomain(normalizeForDetection(domain));
            if (!clean.isEmpty()) normalized.add(clean);
        }
        Set<String> normalizedDomains = Set.copyOf(normalized);
        String listFilterMode = configManager.getLinksListFilterMode().toLowerCase();

        String logSingleLinkTemplate = configManager.getLinksConfig().getString(
                "logs.file.links.links-format.single-link-template", "{link}");
        String logLinkTemplate = configManager.getLinksConfig().getString(
                "logs.file.links.links-format.link-template", "{link}");
        String logSeparator = configManager.getLinksConfig().getString(
                "logs.file.links.links-format.separator", ", ");
        String translatedReplacement = org.gw.chatfilterplus.utils.HexColors.translate(
                configManager.getLinksFilterReplacement());

        return new LinkSettings(
                linkPattern,
                ipPattern,
                discordPattern,
                obfuscatedDomainPattern,
                inviteCodePattern,
                normalizedDomains,
                listFilterMode,
                realTlds,
                quickTriggers,
                minDomainParts,
                requireRealTld,
                smartDetectionEnabled,
                logSingleLinkTemplate,
                logLinkTemplate,
                logSeparator,
                translatedReplacement
        );
    }

    public String getTranslatedReplacement() {
        LinkSettings s = settings();
        String value = s != null ? s.translatedReplacement : null;
        return value != null ? value : org.gw.chatfilterplus.utils.HexColors.translate(
                configManager.getLinksFilterReplacement());
    }

    private Pattern compileLinkRegex(String regex) {
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

        if (regex == null || regex.trim().isEmpty()) {
            plugin.error("В &#ffff00links.yml &fпуть &#ffff00filter.links.regex &fпустой — применён встроенный шаблон.");
            return Pattern.compile(FALLBACK_LINK_REGEX, flags);
        }

        try {
            return Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            plugin.error("Не удалось скомпилировать regex ссылок — применён встроенный шаблон.");
            plugin.error("  файл:    &#ffff00links.yml");
            plugin.error("  путь:    &#ffff00filter.links.regex");
            plugin.error("  ошибка:  &#ffff00" + e.getDescription()
                    + (e.getIndex() >= 0 ? " &f(позиция &#ffff00" + e.getIndex() + "&f)" : ""));
            plugin.error("  шаблон:  &#ffff00" + abbreviate(regex, 160));
            return Pattern.compile(FALLBACK_LINK_REGEX, flags);
        } catch (Exception e) {
            plugin.error("Не удалось скомпилировать regex ссылок (&#ffff00filter.links.regex &fв &#ffff00links.yml&f): "
                    + e.getMessage() + " — применён встроенный шаблон.");
            return Pattern.compile(FALLBACK_LINK_REGEX, flags);
        }
    }

    private static String abbreviate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "… (+" + (text.length() - maxLength) + " симв.)";
    }

    private static boolean isDomainLabelChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static boolean isHardDomainSeparator(char c) {
        return c == '.' || c == ',' || c == ';' || c == '/' || c == '\\'
                || c == '\u2024' || c == '\u00B7' || c == '·';
    }

    private Set<String> toLowerCaseSet(List<String> values) {
        Set<String> set = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) set.add(value.toLowerCase().trim());
        }
        return Set.copyOf(set);
    }

    public List<LinkMatch> findBlockedLinks(String message) {
        return findBlockedLinks(message, null);
    }

    public List<LinkMatch> findBlockedLinks(String message, UUID playerId) {
        if (message == null || message.length() < 3) return List.of();

        LinkSettings s = settings();
        if (s == null) return List.of();

        List<LinkMatch> blocked = new ArrayList<>();

        if (looksLikeLinkCandidate(message, s)) {
            List<LinkMatch> candidates = new ArrayList<>();
            collectPatternMatches(message, s.ipPattern, candidates);
            collectPatternMatches(message, s.discordPattern, candidates);
            collectPatternMatches(message, s.inviteCodePattern, candidates);
            collectPatternMatches(message, s.linkPattern, candidates);
            collectPatternMatches(message, s.obfuscatedDomainPattern, candidates);
            collectUniversalDomainCandidates(message, candidates, s);
            collectSpacedTldCandidates(message, candidates, s);

            if (candidates.size() > 1) {
                candidates.sort(Comparator.comparingInt(LinkMatch::start)
                        .thenComparing((a, b) -> Integer.compare(b.end - b.start, a.end - a.start)));
            }

            int lastEnd = -1;
            for (LinkMatch candidate : candidates) {
                if (candidate.start < lastEnd) continue;
                if (!isLinkAllowed(candidate.text(), s)) {
                    blocked.add(candidate);
                    lastEnd = candidate.end;
                }
            }
        }

        if (adaptiveAdFilter != null && adaptiveAdFilter.isEnabled()) {
            List<AdaptiveAdFilter.AdHit> adaptiveHits = adaptiveAdFilter.evaluate(playerId, message, blocked);
            for (AdaptiveAdFilter.AdHit hit : adaptiveHits) {
                blocked.add(new LinkMatch(hit.start(), hit.end(), hit.text()));
            }
            if (!adaptiveHits.isEmpty()) {
                blocked.sort(Comparator.comparingInt(LinkMatch::start)
                        .thenComparing((a, b) -> Integer.compare(b.end - b.start, a.end - a.start)));
                List<LinkMatch> merged = new ArrayList<>();
                int end = -1;
                for (LinkMatch match : blocked) {
                    if (match.start < end) continue;
                    merged.add(match);
                    end = match.end;
                }
                return merged;
            }
        }

        return blocked;
    }

    private boolean looksLikeLinkCandidate(String message, LinkSettings s) {
        int n = message.length();
        for (int i = 0; i < n; i++) {
            char c = message.charAt(i);
            if (isHardDomainSeparator(c) || c == ':' || c == '@' || c == '[' || c == '(' || c == '{') {
                return true;
            }
        }

        Set<String> triggers = s.quickTriggers;
        if (triggers != null) {
            for (String trigger : triggers) {
                if (trigger != null && !trigger.isEmpty() && containsIgnoreCase(message, trigger)) {
                    return true;
                }
            }
        }

        if (containsIgnoreCase(message, "dot")
                || containsIgnoreCase(message, "точка")
                || containsIgnoreCase(message, "тчк")) {
            return true;
        }

        return hasSpacedKnownTld(message, s);
    }

    private boolean hasSpacedKnownTld(String message, LinkSettings s) {
        Set<String> tlds = s.realTlds;
        if (tlds == null || tlds.isEmpty()) return false;

        int n = message.length();
        int i = 0;
        while (i < n) {
            while (i < n) {
                char c = message.charAt(i);
                if (!Character.isWhitespace(c) && !isHardDomainSeparator(c)) break;
                i++;
            }
            if (i >= n) break;

            int start = i;
            while (i < n) {
                char c = message.charAt(i);
                if (Character.isWhitespace(c) || isHardDomainSeparator(c)) break;
                i++;
            }

            int len = i - start;
            if (len >= 2 && len <= 24) {
                String token = message.substring(start, i).toLowerCase(Locale.ROOT);
                int end = token.length();
                while (end > 0) {
                    char last = token.charAt(end - 1);
                    if (last == '.' || last == ',' || last == ';' || last == '!' || last == '?'
                            || last == ')' || last == ']' || last == '"' || last == '\'') {
                        end--;
                    } else {
                        break;
                    }
                }
                if (end < 2) continue;
                if (end != token.length()) {
                    token = token.substring(0, end);
                }
                if (tlds.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        int nLen = needle.length();
        if (nLen == 0) return true;
        int max = haystack.length() - nLen;
        for (int i = 0; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, nLen)) {
                return true;
            }
        }
        return false;
    }

    private void collectPatternMatches(String message, Pattern pattern, List<LinkMatch> out) {
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String text = matcher.group();
            if (text != null && text.length() >= 4) {
                out.add(new LinkMatch(matcher.start(), matcher.end(), text));
            }
        }
    }

    private void collectUniversalDomainCandidates(String message, List<LinkMatch> out, LinkSettings s) {
        int n = message.length();
        int i = 0;
        while (i < n) {
            if (!isDomainLabelChar(message.charAt(i))) {
                i++;
                continue;
            }

            int start = i;
            int separators = 0;
            int hardSeparators = 0;
            int j = i;

            while (j < n) {
                if (isDomainLabelChar(message.charAt(j))) {
                    j++;
                    continue;
                }

                int sepEnd = skipDomainSeparator(message, j, hardSeparators > 0);
                if (sepEnd <= j) {
                    break;
                }
                if (sepEnd >= n || !isDomainLabelChar(message.charAt(sepEnd))) {
                    break;
                }

                boolean hard = hasHardSeparatorInRange(message, j, sepEnd);
                separators++;
                if (hard) hardSeparators++;
                j = sepEnd;
            }

            int end = j;
            while (end > start && !isDomainLabelChar(message.charAt(end - 1))) {
                end--;
            }

            if (hardSeparators >= 1 && separators >= 1 && end - start >= 4) {
                String candidate = message.substring(start, end);
                String normalized = normalizeForDetection(candidate);
                if (isValidLinkAfterNormalization(normalized, s)) {
                    out.add(new LinkMatch(start, end, candidate));
                }
            }

            i = Math.max(start + 1, end);
        }
    }

    private static boolean isWrapperChar(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    /**
     * Consumes a run of separator characters between two domain labels. A run counts as a real
     * separator when it carries a hard separator (dot/comma/slash…) or a spelled-out dot alias
     * ("dot"/"точка"/"тчк"); bracket wrappers may surround it, so "server[.]net" and
     * "mc(точка)ru" are recognised. Bare whitespace only separates once a hard separator has
     * already appeared in the candidate (allowSpaces), so an ordinary run of words is never glued
     * into a domain.
     */
    /**
     * Catches domains whose labels are split by plain spaces ("play example ru", "hypixel net").
     * These are indistinguishable from ordinary phrases by shape alone, so this pass leans on the
     * fact that the plugin serves a Russian-speaking audience: a real domain is written in LATIN,
     * while ordinary chat is Cyrillic. A run is only treated as a spaced domain when it ends in a
     * real TLD and at least one preceding label is a Latin word (≥3 letters). A Cyrillic word stops
     * the scan, so "да нет" (нет→net), "это гг", "смотрю тв", "я вип" are never misread as domains.
     */
    private void collectSpacedTldCandidates(String message, List<LinkMatch> out, LinkSettings s) {
        Set<String> tlds = s.realTlds;
        if (tlds == null || tlds.isEmpty()) return;

        int n = message.length();
        List<int[]> toks = new ArrayList<>();
        int i = 0;
        while (i < n) {
            while (i < n && !isDomainLabelChar(message.charAt(i))) i++;
            if (i >= n) break;
            int st = i;
            while (i < n && isDomainLabelChar(message.charAt(i))) i++;
            toks.add(new int[]{st, i});
        }

        final int maxParts = 6;
        for (int t = 0; t < toks.size(); t++) {
            String last = message.substring(toks.get(t)[0], toks.get(t)[1]);
            String lastTld = normalizeForDetection(last);
            if (!tlds.contains(lastTld)) continue;
            // Two-letter English homographs (me/us/gg/tv/co/cc/io) are never written space-separated
            // as a domain, but are very common words/abbreviations, so they must not trigger the
            // space scan ("let me", "among us", "watch tv", "wp all gg").
            if (SPACED_TLD_STOPWORDS.contains(lastTld)) continue;

            int firstSld = -1;
            int latinLen = 0;
            int parts = 0;
            for (int j = t - 1; j >= 0 && parts < maxParts; j--) {
                String tk = message.substring(toks.get(j)[0], toks.get(j)[1]);
                if (!isAsciiDomainToken(tk)) break;
                firstSld = j;
                parts++;
                latinLen += latinLetterCount(tk);
            }
            // A real spaced-out domain carries a solid Latin second-level name ("funtime ru",
            // "play world ru", "hypixel net"). Requiring ≥7 Latin letters across the labels rules
            // out short English filler ("so much fun", "good team", "cool app") while keeping real
            // server names. Cyrillic labels contribute nothing here, so Russian chat never qualifies.
            if (firstSld < 0 || latinLen < 7) continue;

            int start = toks.get(firstSld)[0];
            int end = toks.get(t)[1];
            out.add(new LinkMatch(start, end, message.substring(start, end)));
        }
    }

    private static boolean isAsciiDomainToken(String t) {
        if (t.isEmpty() || t.length() > 63) return false;
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    private static int latinLetterCount(String t) {
        int count = 0;
        for (int k = 0; k < t.length(); k++) {
            char c = Character.toLowerCase(t.charAt(k));
            if (c >= 'a' && c <= 'z') count++;
        }
        return count;
    }

    private int skipDomainSeparator(String message, int index, boolean allowSpaces) {
        int n = message.length();
        int end = index;
        boolean sawHardOrAlias = false;
        boolean sawSpace = false;
        while (end < n) {
            char c = message.charAt(end);
            if (isWordDotAlias(message, end)) {
                sawHardOrAlias = true;
                end = skipWordDotAlias(message, end);
                continue;
            }
            if (isHardDomainSeparator(c)) {
                sawHardOrAlias = true;
                end++;
                continue;
            }
            if (isWrapperChar(c)) {
                end++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                sawSpace = true;
                end++;
                continue;
            }
            break;
        }
        if (sawHardOrAlias) return end;
        if (allowSpaces && sawSpace) return end;
        return index;
    }

    private boolean hasHardSeparatorInRange(String message, int from, int to) {
        for (int k = from; k < to; k++) {
            if (isHardDomainSeparator(message.charAt(k)) || isWordDotAlias(message, k)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWordDotAlias(String message, int index) {
        if (index >= message.length()) return false;
        if (regionMatchesIgnoreCase(message, index, "точка")) {
            int after = index + 5;
            return after >= message.length() || !isDomainLabelChar(message.charAt(after));
        }
        if (regionMatchesIgnoreCase(message, index, "тчк") || regionMatchesIgnoreCase(message, index, "dot")) {
            int after = index + 3;
            return after >= message.length() || !isDomainLabelChar(message.charAt(after));
        }
        return false;
    }

    private int skipWordDotAlias(String message, int index) {
        if (regionMatchesIgnoreCase(message, index, "точка")) return index + 5;
        if (regionMatchesIgnoreCase(message, index, "тчк") || regionMatchesIgnoreCase(message, index, "dot")) {
            return index + 3;
        }
        return index + 1;
    }

    private static boolean regionMatchesIgnoreCase(String text, int offset, String needle) {
        return text.regionMatches(true, offset, needle, 0, needle.length());
    }

    public boolean isLinkAllowed(String originalLink) {
        LinkSettings s = settings();
        if (s == null) return true;
        return isLinkAllowed(originalLink, s);
    }

    private boolean isLinkAllowed(String originalLink, LinkSettings s) {
        if (originalLink == null || originalLink.isEmpty()) return true;

        if (s.ipPattern.matcher(originalLink).find()) {
            return isAllowedByList(normalizeForDetection(originalLink), s);
        }
        if (s.discordPattern.matcher(originalLink).find() || s.inviteCodePattern.matcher(originalLink).find()) {
            return isAllowedByList(normalizeForDetection(originalLink), s);
        }

        String normalized = normalizeForDetection(originalLink);
        if (normalized.isEmpty()) return true;

        if (s.smartDetectionEnabled) {
            // Trust the smart check (which enforces a real TLD when require-real-tld is on) as the
            // sole authority. The old fallback to the generic DOMAIN_PATTERN ("xx.yy") overrode that
            // rule and blocked any two words glued by a dot/comma ("лол.короче", "10.000",
            // "you are the") regardless of TLD — a large source of false positives.
            if (!isValidLinkAfterNormalization(normalized, s)) {
                return true;
            }
        } else {
            boolean looksLikeLink = DOMAIN_PATTERN.matcher(normalized).find()
                    || isValidLinkAfterNormalization(normalized, s)
                    || (normalized.contains(".") && normalized.length() > 5);
            if (!looksLikeLink) return true;
        }

        return isAllowedByList(normalized, s);
    }

    private boolean isAllowedByList(String normalized, LinkSettings s) {
        if (!configManager.isLinksListFilterEnabled()) {
            return false;
        }

        String domain = extractDomain(normalized);
        if (domain.isEmpty()) return true;

        boolean inList = s.normalizedDomains.contains(domain);
        return "whitelist".equals(s.listFilterMode) ? inList : !inList;
    }

    private boolean isValidLinkAfterNormalization(String normalized, LinkSettings s) {
        if (normalized == null || normalized.isEmpty()) return false;
        String[] parts = normalized.split("\\.");
        if (parts.length < s.minDomainParts) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 63) return false;
        }
        String tld = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        if (tld.length() < 2) return false;
        if (s.requireRealTld) {
            return s.realTlds.contains(tld);
        }
        return true;
    }

    private String normalizeForDetection(String text) {
        if (text == null || text.isEmpty()) return "";

        String cleaned = INVISIBLE_CHARS.matcher(text).replaceAll("");
        cleaned = SCHEME_PREFIX.matcher(cleaned).replaceFirst("");
        cleaned = stripPath(cleaned);
        cleaned = OBFUSCATED_DOT.matcher(cleaned).replaceAll(".");
        cleaned = NON_ALPHANUMERIC.matcher(cleaned).replaceAll(".");
        cleaned = MULTIPLE_DOTS.matcher(cleaned).replaceAll(".");
        cleaned = EDGE_TRIM.matcher(cleaned).replaceAll("");

        return normalizeHomoglyphs(cleaned.toLowerCase());
    }

    private String stripPath(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '/' || c == '?' || c == '#') return text.substring(0, i);
        }
        return text;
    }

    private String normalizeHomoglyphs(String lowerCased) {
        StringBuilder result = new StringBuilder(lowerCased.length());
        for (int i = 0; i < lowerCased.length(); i++) {
            char c = lowerCased.charAt(i);
            String replacement = HOMOGLYPHS.get(c);
            if (replacement == null) {
                result.append(c);
            } else {
                result.append(replacement);
            }
        }
        return result.toString();
    }

    private String extractDomain(String normalized) {
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    public String getFormattedLinks(List<String> links) {
        if (links.isEmpty()) return "";

        LinkSettings s = settings();
        if (s == null) return String.join(", ", links);

        if (links.size() == 1) {
            return s.logSingleLinkTemplate.replace("{link}", links.get(0));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < links.size(); i++) {
            if (i > 0) sb.append(s.logSeparator);
            sb.append(s.logLinkTemplate.replace("{link}", links.get(i)));
        }
        return sb.toString();
    }

    public boolean addWhitelistDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) return false;

        List<String> domains = new ArrayList<>(configManager.getLinksListFilterDomains());
        if (domains.stream().anyMatch(d -> d.equalsIgnoreCase(domain))) return false;

        domains.add(domain);
        return saveDomains(domains, "Добавлен домен в вайтлист ссылок: &#ffff00" + domain,
                "&#FF5D00Ошибка при добавлении домена в вайтлист ссылок: ");
    }

    public boolean removeWhitelistDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) return false;

        List<String> domains = new ArrayList<>(configManager.getLinksListFilterDomains());
        if (!domains.removeIf(d -> d.equalsIgnoreCase(domain))) return false;

        return saveDomains(domains, "Удалён домен из вайтлиста ссылок: &#ffff00" + domain,
                "&#FF5D00Ошибка при удалении домена из вайтлиста ссылок: ");
    }

    public List<String> getPromoKeywords() {
        return adaptiveAdFilter.getPromoKeywords();
    }

    public boolean addPromoKeyword(String keyword) {
        return adaptiveAdFilter.addPromoKeyword(keyword);
    }

    public boolean removePromoKeyword(String keyword) {
        return adaptiveAdFilter.removePromoKeyword(keyword);
    }

    private boolean saveDomains(List<String> domains, String successMessage, String errorPrefix) {
        configManager.getLinksConfig().set("filter.links.list-filter.domains", domains);
        try {
            configManager.getLinksConfig().save(new File(plugin.getDataFolder(), "links.yml"));
            reload();
            plugin.log(successMessage);
            return true;
        } catch (Exception e) {
            plugin.console(errorPrefix + e.getMessage());
            return false;
        }
    }

    public void reload() {
        load();
        if (adaptiveAdFilter != null) {
            adaptiveAdFilter.reload();
        }
    }
}
