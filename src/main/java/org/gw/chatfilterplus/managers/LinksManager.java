package org.gw.chatfilterplus.managers;

import lombok.Getter;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinksManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;

    @Getter
    private Pattern linkPattern;
    private Pattern ipPattern;
    private Pattern discordPattern;
    private Set<String> normalizedDomains;
    private String listFilterMode;

    private static final Pattern INVISIBLE_CHARS = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00A0\\u1680\\u180E\\u2000-\\u200F\\u2028\\u2029\\u202F\\u205F\\u3000]+");

    private static final Pattern DOT_OBFUSCATION = Pattern.compile(
            "\\[dot\\]|\\(dot\\)|\\{dot\\}| dot |\\.dot\\.|\\s+dot\\s+|точка|тчк|т\\.ч\\.к\\.|период|собака|@|с точкой|с тчк|с периодом|dot|т\\.чк|тч\\.к|ком|ру",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern SEPARATOR_CLEANUP = Pattern.compile("[#|\\-_/\\\\,;:]+");

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-z0-9-]{2,}\\.[a-z0-9-]{2,}");

    public LinksManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    private void load() {
        loadPatterns();
        loadDomains();
    }

    private void loadPatterns() {
        String regex = configManager.getLinksRegex();
        try {
            linkPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
        } catch (Exception e) {
            linkPattern = Pattern.compile("(?i)[\\w\\p{L}\\-]+(?:\\.[\\w\\p{L}\\-]+){1,}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
        }

        ipPattern = Pattern.compile("\\b\\d{1,3}(?:\\s*[\\.\\-]\\s*\\d{1,3}){3}\\b");
        discordPattern = Pattern.compile("(?i)(?:https?://)?(?:www\\.|ptb\\.|canary\\.)?(?:discord(?:app)?\\.(?:com|gg)/(?:invite|servers?)/?|discord\\.gg/)[a-z0-9-_]{2,32}");
    }

    private void loadDomains() {
        List<String> domains = configManager.getLinksListFilterDomains();
        Set<String> normalized = new HashSet<>(domains.size());

        for (String domain : domains) {
            if (domain == null || domain.trim().isEmpty()) continue;

            String clean = domain.toLowerCase().trim()
                    .replaceAll("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]", "")
                    .replaceAll("^(https?://)?", "")
                    .replaceAll("/.*$", "")
                    .replace(',', '.')
                    .replace(" . ", ".");

            if (!clean.isEmpty()) normalized.add(clean);
        }

        this.normalizedDomains = Set.copyOf(normalized);
        this.listFilterMode = configManager.getLinksListFilterMode().toLowerCase();
    }

    public boolean isLinkAllowed(String originalLink) {
        String normalized = normalizeForDetection(originalLink);
        if (normalized.isEmpty()) return true;

        boolean looksLikeLink = ipPattern.matcher(normalized).find() ||
                linkPattern.matcher(normalized).find() ||
                discordPattern.matcher(normalized).find() ||
                DOMAIN_PATTERN.matcher(normalized).find();

        if (!looksLikeLink && normalized.contains(".") && normalized.length() > 5) {
            looksLikeLink = true;
        }

        if (!looksLikeLink) return true;

        if (!configManager.isLinksListFilterEnabled()) {
            return false;
        }

        String domain = extractDomain(normalized);
        if (domain.isEmpty()) return true;

        boolean inList = normalizedDomains.contains(domain);

        if ("whitelist".equals(listFilterMode)) {
            return inList;
        } else {
            return !inList;
        }
    }

    private String normalizeForDetection(String text) {
        if (text == null || text.isEmpty()) return "";

        String cleaned = INVISIBLE_CHARS.matcher(text).replaceAll("");
        cleaned = DOT_OBFUSCATION.matcher(cleaned).replaceAll(".");
        cleaned = SEPARATOR_CLEANUP.matcher(cleaned).replaceAll(".");
        cleaned = cleaned.replaceAll("[\\s,;:]+", ".");
        cleaned = cleaned.replaceAll("[^\\p{L}\\p{N}]+", ".");
        cleaned = cleaned.replaceAll("\\.{2,}", ".");
        cleaned = cleaned.replaceAll("^-+|-+$|^\\.+|\\.+$", "");

        cleaned = normalizeHomoglyphs(cleaned);

        return cleaned.toLowerCase();
    }

    private String normalizeHomoglyphs(String text) {
        return text
                // Latin small caps
                .replace("ᴍ", "m").replace("ᴄ", "c").replace("ʀ", "r").replace("ᴇ", "e")
                .replace("ᴀ", "a").replace("ʟ", "l").replace("ʏ", "y").replace("ᴡ", "w")
                .replace("ᴏ", "o").replace("ᴜ", "u").replace("ᴅ", "d").replace("ɴ", "n")
                .replace("ᴛ", "t").replace("ꜱ", "s").replace("ɪ", "i").replace("ʙ", "b")
                .replace("ɢ", "g").replace("ʜ", "h").replace("ᴋ", "k").replace("ᴘ", "p")
                .replace("ꜰ", "f").replace("ᴠ", "v").replace("ᴢ", "z")
                // Cyrillic confusables + транслит
                .replace("с", "c").replace("а", "a").replace("е", "e").replace("о", "o")
                .replace("р", "p").replace("х", "x").replace("у", "y").replace("к", "k")
                .replace("м", "m").replace("т", "t").replace("н", "h").replace("в", "v")
                .replace("г", "g").replace("д", "d").replace("л", "l").replace("п", "n")
                .replace("ф", "f").replace("и", "i").replace("й", "i").replace("ь", "")
                .replace("ъ", "").replace("э", "e").replace("ю", "u").replace("я", "ya")
                // Fullwidth
                .replace("ａ", "a").replace("ｂ", "b").replace("ｃ", "c").replace("ｄ", "d")
                .replace("ｅ", "e").replace("ｆ", "f").replace("ｇ", "g").replace("ｈ", "h")
                .replace("ｉ", "i").replace("ｊ", "j").replace("ｋ", "k").replace("ｌ", "l")
                .replace("ｍ", "m").replace("ｎ", "n").replace("ｏ", "o").replace("ｐ", "p")
                .replace("ｑ", "q").replace("ｒ", "r").replace("ｓ", "s").replace("ｔ", "t")
                .replace("ｕ", "u").replace("ｖ", "v").replace("ｗ", "w").replace("ｘ", "x")
                .replace("ｙ", "y").replace("ｚ", "z");
    }

    private String extractDomain(String normalized) {
        String domain = normalized
                .replaceFirst("^(https?://|h\\s*t\\s*t\\s*p\\s*(?:s)?\\s*:\\s*/\\s*/)", "")
                .replaceAll("[\\s/].*$", "")
                .trim();

        if (domain.startsWith("www.")) domain = domain.substring(4);
        return domain;
    }

    public String getFormattedLinks(List<String> links) {
        if (links.isEmpty()) return "";

        String templateSingle = configManager.getLinksConfig().getString("logs.file.links.links-format.single-link-template", "{link}");
        String separator = configManager.getLinksConfig().getString("logs.file.links.links-format.separator", ", ");
        String templateMulti = configManager.getLinksConfig().getString("logs.file.links.links-format.link-template", "{link}");

        if (links.size() == 1) {
            return templateSingle.replace("{link}", links.get(0));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < links.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(templateMulti.replace("{link}", links.get(i)));
        }
        return sb.toString();
    }

    public boolean addWhitelistDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) return false;

        List<String> domains = new ArrayList<>(configManager.getLinksListFilterDomains());
        if (domains.stream().anyMatch(d -> d.equalsIgnoreCase(domain))) {
            return false;
        }

        domains.add(domain);
        configManager.getLinksConfig().set("filter.links.list-filter.domains", domains);

        try {
            configManager.getLinksConfig().save(new File(plugin.getDataFolder(), "links.yml"));
            reload();
            plugin.log("Добавлен домен в вайтлист ссылок: &#ffff00" + domain);
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при добавлении домена в вайтлист ссылок: " + e.getMessage());
            return false;
        }
    }

    public boolean removeWhitelistDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) return false;

        List<String> domains = new ArrayList<>(configManager.getLinksListFilterDomains());
        boolean removed = domains.removeIf(d -> d.equalsIgnoreCase(domain));
        if (!removed) return false;

        configManager.getLinksConfig().set("filter.links.list-filter.domains", domains);

        try {
            configManager.getLinksConfig().save(new File(plugin.getDataFolder(), "links.yml"));
            reload();
            plugin.log("Удалён домен из вайтлиста ссылок: &#ffff00" + domain);
            return true;
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка при удалении домена из вайтлист ссылок: " + e.getMessage());
            return false;
        }
    }

    public String normalizeForDetectionPublic(String text) {
        return normalizeForDetection(text);
    }

    public void reload() {
        load();
    }
}