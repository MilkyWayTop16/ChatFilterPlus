package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Getter
public class LinksManager {

    public record LinkMatch(int start, int end, String text) {
    }

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final AdaptiveAdFilter adaptiveAdFilter;

    private volatile Pattern linkPattern;
    private volatile Pattern ipPattern;
    private volatile Pattern discordPattern;
    private volatile Pattern obfuscatedDomainPattern;
    private volatile Pattern inviteCodePattern;
    private volatile Set<String> normalizedDomains;
    private volatile String listFilterMode;

    private volatile Set<String> realTlds;
    private volatile Set<String> quickTriggers;
    private volatile int minDomainParts;
    private volatile boolean requireRealTld;
    private volatile boolean smartDetectionEnabled;

    private volatile String logSingleLinkTemplate;
    private volatile String logLinkTemplate;
    private volatile String logSeparator;
    private volatile String translatedReplacement;

    private static final Pattern INVISIBLE_CHARS = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00A0\\u1680\\u180E\\u2000-\\u200F\\u2028\\u2029\\u202F\\u205F\\u3000]+");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-z0-9-]{2,}\\.[a-z0-9-]{2,}");

    private static final Pattern SCHEME_PREFIX = Pattern.compile("(?i)^\\s*h\\s*t\\s*t\\s*p\\s*s?\\s*:\\s*/\\s*/\\s*");

    private static final Pattern OBFUSCATED_DOT = Pattern.compile(
            "(?i)\\[dot]|\\(dot\\)|\\{dot}|\\.dot\\.|\\s+dot\\s+|\\s*(?:точка|тчк|т\\.ч\\.к\\.|с\\s+точкой|с\\s+тчк)\\s*");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MULTIPLE_DOTS = Pattern.compile("\\.{2,}");
    private static final Pattern EDGE_TRIM = Pattern.compile("^[-.]+|[-.]+$");

    private static final Map<Character, String> HOMOGLYPHS = buildHomoglyphs();

    public LinksManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.adaptiveAdFilter = new AdaptiveAdFilter(plugin, configManager);
        load();
    }

    public AdaptiveAdFilter getAdaptiveAdFilter() {
        return adaptiveAdFilter;
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
        loadPatterns();
        loadSmartDetection();
        loadDomains();
        loadLogTemplates();
        this.translatedReplacement = org.gw.chatfilterplus.utils.HexColors.translate(
                configManager.getLinksFilterReplacement());
    }

    public String getTranslatedReplacement() {
        String value = translatedReplacement;
        return value != null ? value : org.gw.chatfilterplus.utils.HexColors.translate(
                configManager.getLinksFilterReplacement());
    }

    private void loadPatterns() {
        String regex = configManager.getLinksRegex();
        try {
            linkPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
        } catch (Exception e) {
            plugin.console("&#FF5D00Некорректный regex ссылок в links.yml, используется стандартный: " + e.getMessage());
            linkPattern = Pattern.compile("(?i)[\\w\\p{L}\\-]+(?:\\.[\\w\\p{L}\\-]+){1,}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
        }

        ipPattern = Pattern.compile(
                "(?i)\\b\\d{1,3}(?:\\s*[\\.\\,\\-\\u2024\\u00B7]\\s*\\d{1,3}){3}(?:\\s*:\\s*\\d{2,5})?\\b");
        discordPattern = Pattern.compile(
                "(?i)(?:https?://)?(?:www\\.|ptb\\.|canary\\.)?(?:discord(?:app)?\\.(?:com|gg)/(?:invite|servers?)/?|discord\\.gg/)[a-z0-9-_]{2,32}");
        obfuscatedDomainPattern = Pattern.compile(
                "(?i)\\b[\\w\\p{L}][\\w\\p{L}\\-]{0,62}"
                        + "(?:\\s*[\\.\\,\\u2024\\u00B7/;]|\\s+(?:dot|точка|тчк)\\s+|\\s+)"
                        + "[\\w\\p{L}][\\w\\p{L}\\-]{0,48}"
                        + "(?:\\s*[\\.\\,\\u2024\\u00B7/;]|\\s+(?:dot|точка|тчк)\\s+|\\s+)"
                        + "[\\w\\p{L}]{2,24}\\b"
                        + "|(?i)\\b[\\w\\p{L}][\\w\\p{L}\\-]{1,62}\\s*[\\.\\,\\u2024\\u00B7]\\s*[\\w\\p{L}]{2,12}\\b");
        inviteCodePattern = Pattern.compile(
                "(?i)\\b(?:t\\.me|telegram\\.me|tg:\\/\\/join|vk\\.com|vk\\.cc|bit\\.ly|goo\\.gl|tinyurl\\.com|youtu\\.be)\\/[\\w\\-]{3,64}\\b"
                        + "|(?i)\\b(?:dsc\\.gg|invite\\.gg)\\/[\\w\\-]{2,32}\\b");
    }

    private static boolean isDomainLabelChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static boolean isHardDomainSeparator(char c) {
        return c == '.' || c == ',' || c == ';' || c == '/' || c == '\\'
                || c == '\u2024' || c == '\u00B7' || c == '·';
    }

    private void loadDomains() {
        Set<String> normalized = new HashSet<>();

        for (String domain : configManager.getLinksListFilterDomains()) {
            if (domain == null || domain.trim().isEmpty()) continue;
            String clean = extractDomain(normalizeForDetection(domain));
            if (!clean.isEmpty()) normalized.add(clean);
        }

        this.normalizedDomains = Set.copyOf(normalized);
        this.listFilterMode = configManager.getLinksListFilterMode().toLowerCase();
    }

    private void loadSmartDetection() {
        smartDetectionEnabled = configManager.getLinksConfig().getBoolean("filter.smart-detection.enabled", true);

        this.quickTriggers = toLowerCaseSet(configManager.getLinksConfig().getStringList("filter.smart-detection.quick-triggers"));
        this.realTlds = toLowerCaseSet(configManager.getLinksConfig().getStringList("filter.smart-detection.tlds"));

        this.minDomainParts = configManager.getLinksConfig().getInt("filter.smart-detection.min-domain-parts", 2);
        this.requireRealTld = configManager.getLinksConfig().getBoolean("filter.smart-detection.require-real-tld", true);
    }

    private void loadLogTemplates() {
        this.logSingleLinkTemplate = configManager.getLinksConfig().getString("logs.file.links.links-format.single-link-template", "{link}");
        this.logLinkTemplate = configManager.getLinksConfig().getString("logs.file.links.links-format.link-template", "{link}");
        this.logSeparator = configManager.getLinksConfig().getString("logs.file.links.links-format.separator", ", ");
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

        List<LinkMatch> blocked = new ArrayList<>();

        if (looksLikeLinkCandidate(message)) {
            List<LinkMatch> candidates = new ArrayList<>();
            collectPatternMatches(message, ipPattern, candidates);
            collectPatternMatches(message, discordPattern, candidates);
            collectPatternMatches(message, inviteCodePattern, candidates);
            collectPatternMatches(message, linkPattern, candidates);
            collectPatternMatches(message, obfuscatedDomainPattern, candidates);
            collectUniversalDomainCandidates(message, candidates);

            if (candidates.size() > 1) {
                candidates.sort(Comparator.comparingInt(LinkMatch::start)
                        .thenComparing((a, b) -> Integer.compare(b.end - b.start, a.end - a.start)));
            }

            int lastEnd = -1;
            for (LinkMatch candidate : candidates) {
                if (candidate.start < lastEnd) continue;
                if (!isLinkAllowed(candidate.text())) {
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

    private boolean looksLikeLinkCandidate(String message) {
        int n = message.length();
        for (int i = 0; i < n; i++) {
            char c = message.charAt(i);
            if (isHardDomainSeparator(c) || c == ':' || c == '@' || c == '[' || c == '(' || c == '{') {
                return true;
            }
        }

        Set<String> triggers = quickTriggers;
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

        return hasSpacedKnownTld(message);
    }

    private boolean hasSpacedKnownTld(String message) {
        Set<String> tlds = realTlds;
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

    private void collectUniversalDomainCandidates(String message, List<LinkMatch> out) {
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
                if (isValidLinkAfterNormalization(normalized)) {
                    out.add(new LinkMatch(start, end, candidate));
                }
            }

            i = Math.max(start + 1, end);
        }
    }

    private int skipDomainSeparator(String message, int index, boolean allowSpaces) {
        if (isWordDotAlias(message, index)) {
            int after = skipWordDotAlias(message, index);
            while (after < message.length()
                    && (isHardDomainSeparator(message.charAt(after)) || Character.isWhitespace(message.charAt(after)))) {
                after++;
            }
            return after;
        }

        char c = message.charAt(index);
        if (!isHardDomainSeparator(c) && !(allowSpaces && Character.isWhitespace(c))) {
            return index;
        }

        int sepEnd = index;
        while (sepEnd < message.length()) {
            char s = message.charAt(sepEnd);
            if (isHardDomainSeparator(s) || (allowSpaces && Character.isWhitespace(s))
                    || (isHardDomainSeparator(c) && Character.isWhitespace(s))) {
                sepEnd++;
                continue;
            }
            break;
        }
        return sepEnd;
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
        if (originalLink == null || originalLink.isEmpty()) return true;

        if (ipPattern.matcher(originalLink).find()) {
            return isAllowedByList(normalizeForDetection(originalLink));
        }
        if (discordPattern.matcher(originalLink).find() || inviteCodePattern.matcher(originalLink).find()) {
            return isAllowedByList(normalizeForDetection(originalLink));
        }

        String normalized = normalizeForDetection(originalLink);
        if (normalized.isEmpty()) return true;

        if (smartDetectionEnabled) {
            if (!isValidLinkAfterNormalization(normalized) && !DOMAIN_PATTERN.matcher(normalized).find()) {
                return true;
            }
        } else {
            boolean looksLikeLink = DOMAIN_PATTERN.matcher(normalized).find()
                    || isValidLinkAfterNormalization(normalized)
                    || (normalized.contains(".") && normalized.length() > 5);
            if (!looksLikeLink) return true;
        }

        return isAllowedByList(normalized);
    }

    private boolean isAllowedByList(String normalized) {
        if (!configManager.isLinksListFilterEnabled()) {
            return false;
        }

        String domain = extractDomain(normalized);
        if (domain.isEmpty()) return true;

        boolean inList = normalizedDomains.contains(domain);
        return "whitelist".equals(listFilterMode) ? inList : !inList;
    }

    private boolean isValidLinkAfterNormalization(String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;
        String[] parts = normalized.split("\\.");
        if (parts.length < minDomainParts) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 63) return false;
        }
        String tld = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        if (tld.length() < 2) return false;
        if (requireRealTld) {
            return realTlds.contains(tld);
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

        if (links.size() == 1) {
            return logSingleLinkTemplate.replace("{link}", links.get(0));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < links.size(); i++) {
            if (i > 0) sb.append(logSeparator);
            sb.append(logLinkTemplate.replace("{link}", links.get(i)));
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
