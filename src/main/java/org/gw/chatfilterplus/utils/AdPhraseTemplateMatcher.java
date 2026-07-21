package org.gw.chatfilterplus.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdPhraseTemplateMatcher {

    public record Match(String template, String target, String kind, double similarity) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(name|@name|domain)}", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_@][\\p{L}\\p{N}_@.-]{0,62}");
    private static final Pattern DOMAINISH = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])[\\p{L}\\p{N}][\\p{L}\\p{N}-]{0,62}"
                    + "(?:\\s*[.\\u2024\\u00B7]\\s*[\\p{L}\\p{N}][\\p{L}\\p{N}-]{0,62}){1,5}(?![\\p{L}\\p{N}])",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final String SLOT = "\u0001";

    private static final Set<String> DOMAIN_TLDS = Set.of(
            "ru", "su", "com", "net", "org", "io", "me", "gg", "fun", "top", "pro", "space",
            "site", "online", "xyz", "club", "shop", "store", "host", "icu", "games", "world",
            "link", "click", "info", "biz", "cc", "tv", "us", "uk", "de", "app", "live", "tech"
    );

    private static final Set<String> STOP_NAME_TOKENS = Set.of(
            "тгк", "тг", "tgk", "tgc", "tg", "телега", "телеграм", "телеграмм", "telegram",
            "дс", "дискорд", "discord", "dsc", "сервер", "канал", "ссылка", "ссылку",
            "подпишись", "подписка", "переходи", "лучший", "самый", "топовый",
            "наш", "мой", "invite", "инвайт", "промокод", "реклама", "айпи", "donate", "донат",
            "гриферский", "анархо", "анархия", "выживание", "mini", "games", "mc", "мс",
            "ip", "порт", "заходи", "играй", "здесь", "тут", "там", "это", "the", "best", "server"
    );

    private static final Set<String> FILLER_ANCHORS = Set.of(
            "sami", "samii", "lucwi", "lucwii", "lucsi", "luci", "topovi", "topovii",
            "moi", "nas", "eto", "kak", "dla", "ili", "ocen", "prosto", "ochen",
            "i", "na", "v", "po", "za", "ot", "do", "iz", "so", "the", "and", "best", "top",
            "ms", "mc"
    );

    private static final Set<String> CRITICAL_PROMO_MARKERS = Set.of(
            "tgk", "tgc", "telegram", "telega", "telegpam", "telegpamm",
            "discord", "dsc", "invite", "invait",
            "sepvep", "tg", "ip", "ds", "aipi"
    );

    private static final Set<String> SHORT_CRITICAL_ANCHORS = Set.of(
            "tg", "ip", "ds"
    );

    private static final int PADDED_BRAND_MIN_LEN = 5;

    private final List<CompiledTemplate> templates;
    private final double minSimilarity;
    private final int minMessageLength;

    private AdPhraseTemplateMatcher(List<CompiledTemplate> templates, double minSimilarity, int minMessageLength) {
        this.templates = List.copyOf(templates);
        this.minSimilarity = minSimilarity;
        this.minMessageLength = minMessageLength;
    }

    public boolean isEmpty() {
        return templates.isEmpty();
    }

    public static AdPhraseTemplateMatcher compile(List<String> rawTemplates,
                                                  int minSimilarityPercent,
                                                  int minMessageLength) {
        double minSim = Math.max(0.50, Math.min(1.0, minSimilarityPercent / 100.0));
        int minLen = Math.max(6, minMessageLength);
        List<CompiledTemplate> compiled = new ArrayList<>();
        if (rawTemplates != null) {
            for (String raw : rawTemplates) {
                CompiledTemplate ct = compileOne(raw);
                if (ct != null) compiled.add(ct);
            }
        }
        return new AdPhraseTemplateMatcher(compiled, minSim, minLen);
    }

    public Match match(String message) {
        if (message == null || message.length() < minMessageLength || templates.isEmpty()) {
            return null;
        }

        String compactMsg = AdTextAnalyzer.compact(message);
        if (compactMsg.length() < minMessageLength - 2) {
            return null;
        }

        List<TargetCandidate> targets = collectTargets(message);
        if (targets.isEmpty()) {
            return null;
        }

        Match best = null;
        for (CompiledTemplate template : templates) {
            for (TargetCandidate target : targets) {
                if (!targetCompatible(template, target)) continue;

                String msgShape = shapeMessage(message, target);
                String msgCompact = AdTextAnalyzer.compact(msgShape);
                if (msgCompact.length() < 2) continue;

                if (!skeletonHasAnchors(msgCompact, template.anchorCompacts)) continue;

                double sim = AdTextAnalyzer.similarity(msgCompact, template.skeletonCompact);
                boolean nameLike = "name".equals(target.kind()) || "handle".equals(target.kind());
                boolean criticalPromo = hasCriticalPromoMarker(msgCompact)
                        || hasCriticalPromoMarker(template.skeletonCompact);
                boolean solidBrand = target.compact != null && target.compact.length() >= PADDED_BRAND_MIN_LEN;

                if ("domain".equals(target.kind()) && sim < minSimilarity
                        && strongAnchorsPresent(msgCompact, template.anchorCompacts) >= 2) {
                    sim = Math.max(sim, minSimilarity + 0.01);
                }

                if (nameLike && sim < minSimilarity && solidBrand && criticalPromo
                        && strongAnchorsPresent(msgCompact, template.anchorCompacts) >= 1) {
                    sim = Math.max(sim, minSimilarity + 0.01);
                }

                if (sim < minSimilarity) continue;

                int maxLen = template.skeletonCompact.length() * 2 + 10;
                if ("domain".equals(target.kind())) {
                    maxLen = template.skeletonCompact.length() * 4 + 20;
                } else if (nameLike && solidBrand && criticalPromo) {
                    maxLen = template.skeletonCompact.length() * 6 + 40;
                }
                if (sim < 0.93 && msgCompact.length() > maxLen) continue;

                if (best == null || sim > best.similarity()) {
                    best = new Match(template.raw, target.raw(), target.kind(), sim);
                }
            }
        }
        return best;
    }

    private static boolean skeletonHasAnchors(String msgCompact, List<String> anchors) {
        return strongAnchorsPresent(msgCompact, anchors) >= 1;
    }

    private static int strongAnchorsPresent(String msgCompact, List<String> anchors) {
        if (anchors == null || anchors.isEmpty() || msgCompact == null) return 0;
        int longTotal = 0;
        int longHits = 0;
        int shortHits = 0;
        for (String anchor : anchors) {
            if (anchor == null || anchor.isEmpty()) continue;
            if (FILLER_ANCHORS.contains(anchor)) continue;
            if (anchor.length() >= 3) {
                longTotal++;
                if (msgCompact.contains(anchor)) longHits++;
            } else if (SHORT_CRITICAL_ANCHORS.contains(anchor) && msgCompact.contains(anchor)) {
                shortHits++;
            }
        }
        if (longTotal > 0) return longHits;
        return shortHits;
    }

    private static boolean hasCriticalPromoMarker(String compact) {
        if (compact == null || compact.length() < 2) return false;
        for (String marker : CRITICAL_PROMO_MARKERS) {
            if (marker == null || marker.length() < 2) continue;
            if (compact.contains(marker)) return true;
        }
        return false;
    }

    private static boolean targetCompatible(CompiledTemplate template, TargetCandidate target) {
        return switch (template.targetKind) {
            case AT_NAME -> "handle".equals(target.kind);
            case NAME -> "handle".equals(target.kind) || "name".equals(target.kind);
            case DOMAIN -> "domain".equals(target.kind);
        };
    }

    private static String shapeMessage(String message, TargetCandidate target) {
        String shaped = message;
        if (target.start >= 0 && target.end <= message.length() && target.start < target.end) {
            shaped = message.substring(0, target.start) + SLOT + message.substring(target.end);
        } else {
            int idx = indexOfIgnoreCase(message, target.raw);
            if (idx >= 0) {
                shaped = message.substring(0, idx) + SLOT + message.substring(idx + target.raw.length());
            }
        }
        return shaped;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isEmpty()) return -1;
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static List<TargetCandidate> collectTargets(String message) {
        List<TargetCandidate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String handle : AdTextAnalyzer.extractHandles(message)) {
            if (seen.add("h:" + handle)) {
                int[] span = findHandleSpan(message, handle);
                out.add(new TargetCandidate(handle, "handle",
                        span != null ? message.substring(span[0], span[1]) : "@" + handle,
                        span != null ? span[0] : -1,
                        span != null ? span[1] : -1));
            }
        }

        Matcher domainMatcher = DOMAINISH.matcher(message);
        while (domainMatcher.find()) {
            String raw = domainMatcher.group();
            String norm = DomainScriptNormalizer.toLatinDomain(raw);
            if (!isDomainLike(norm)) continue;
            if (seen.add("d:" + norm)) {
                out.add(new TargetCandidate(norm, "domain", raw, domainMatcher.start(), domainMatcher.end()));
            }
        }

        collectSpacedDomains(message, out, seen);
        collectBareNames(message, out, seen);

        return out;
    }

    private static void collectSpacedDomains(String message, List<TargetCandidate> out, Set<String> seen) {
        List<int[]> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(message);
        while (m.find()) {
            tokens.add(new int[]{m.start(), m.end()});
        }
        for (int i = 0; i < tokens.size(); i++) {
            String last = message.substring(tokens.get(i)[0], tokens.get(i)[1]);
            String tld = DomainScriptNormalizer.toLatinDomain(last);
            if (!DOMAIN_TLDS.contains(tld)) continue;
            if (!DomainScriptNormalizer.isDomainTokenAfterNorm(last)) continue;

            int first = i;
            int latinLetters = DomainScriptNormalizer.latinLetterCount(tld);
            for (int j = i - 1; j >= 0 && i - j < 5; j--) {
                String tok = message.substring(tokens.get(j)[0], tokens.get(j)[1]);
                if (!DomainScriptNormalizer.isDomainTokenAfterNorm(tok)) break;
                first = j;
                // Только настоящие латинские буквы токена (домен пишут латиницей); кириллическая речь
                // не должна давать длину, иначе "вот это да net" читается как домен. См. LinksManager.
                latinLetters += DomainScriptNormalizer.latinLetterCount(tok);
            }
            if (i - first < 1 || latinLetters < 6) continue;

            int start = tokens.get(first)[0];
            int end = tokens.get(i)[1];
            String raw = message.substring(start, end);
            String norm = DomainScriptNormalizer.toLatinDomain(raw);
            if (!isDomainLike(norm) && !norm.contains(".")) {
                StringBuilder sb = new StringBuilder();
                for (int k = first; k <= i; k++) {
                    if (k > first) sb.append('.');
                    sb.append(DomainScriptNormalizer.toLatinDomain(
                            message.substring(tokens.get(k)[0], tokens.get(k)[1])));
                }
                norm = DomainScriptNormalizer.applyTldAliases(sb.toString());
            }
            if (!isDomainLike(norm)) continue;
            if (seen.add("d:" + norm)) {
                out.add(new TargetCandidate(norm, "domain", raw, start, end));
            }
        }
    }

    private static void collectBareNames(String message, List<TargetCandidate> out, Set<String> seen) {
        Matcher m = TOKEN.matcher(message);
        while (m.find()) {
            String raw = m.group();
            if (raw.startsWith("@")) continue;
            if (raw.indexOf('.') >= 0) continue;
            if (!isLatinBrandToken(raw)) continue;
            String compact = AdTextAnalyzer.compact(raw);
            if (compact.length() < 4 || compact.length() > 32) continue;
            if (STOP_NAME_TOKENS.contains(compact)) continue;
            if (DOMAIN_TLDS.contains(compact)) continue;
            if (DomainScriptNormalizer.latinLetterCount(raw) < 4) continue;
            if (seen.add("n:" + compact)) {
                out.add(new TargetCandidate(compact, "name", raw, m.start(), m.end()));
            }
        }
    }

    private static boolean isLatinBrandToken(String t) {
        boolean letter = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                letter = true;
                continue;
            }
            if ((c >= '0' && c <= '9') || c == '_' || c == '-') continue;
            return false;
        }
        return letter;
    }

    private static int[] findHandleSpan(String message, String compactHandle) {
        // No \s here either — see AdTextAnalyzer.HANDLE_PATTERN for why embedded whitespace made
        // this over-capture trailing prose.
        Matcher m = Pattern.compile("@([\\p{L}\\p{N}_.\\-]{3,48})").matcher(message);
        while (m.find()) {
            String norm = AdTextAnalyzer.compact(m.group(1));
            if (norm.equals(compactHandle)) {
                return new int[]{m.start(), m.end()};
            }
        }
        return null;
    }

    private static boolean isDomainLike(String norm) {
        if (norm == null || norm.length() < 4) return false;
        String[] parts = norm.split("\\.");
        if (parts.length < 2) return false;
        String tld = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        if (!DOMAIN_TLDS.contains(tld)) return false;
        int latin = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 63) return false;
            latin += DomainScriptNormalizer.latinLetterCount(parts[i]);
        }
        if (parts.length >= 3 && latin >= 3) return true;
        return latin >= 4;
    }

    private enum TargetKind {NAME, AT_NAME, DOMAIN}

    private record TargetCandidate(String compact, String kind, String raw, int start, int end) {
    }

    private record CompiledTemplate(String raw,
                                    String skeletonCompact,
                                    TargetKind targetKind,
                                    List<String> anchorCompacts) {
    }

    private static CompiledTemplate compileOne(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.length() < 6) return null;

        Matcher ph = PLACEHOLDER.matcher(trimmed);
        if (!ph.find()) return null;

        String kindToken = ph.group(1).toLowerCase(Locale.ROOT);
        TargetKind kind = switch (kindToken) {
            case "@name" -> TargetKind.AT_NAME;
            case "domain" -> TargetKind.DOMAIN;
            default -> TargetKind.NAME;
        };

        if (ph.find()) return null;

        String skeleton = PLACEHOLDER.matcher(trimmed).replaceAll(SLOT);
        String skeletonCompact = AdTextAnalyzer.compact(skeleton);
        if (skeletonCompact.length() < 2) return null;

        List<String> anchors = extractAnchors(skeleton);
        if (anchors.isEmpty()) return null;

        return new CompiledTemplate(trimmed, skeletonCompact, kind, anchors);
    }

    private static List<String> extractAnchors(String skeletonWithoutSlot) {
        List<String> anchors = new ArrayList<>();
        for (String part : skeletonWithoutSlot.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (part.isEmpty()) continue;
            String c = AdTextAnalyzer.compact(part);
            if (c.length() < 2) continue;
            if (c.length() < 3 && !SHORT_CRITICAL_ANCHORS.contains(c)) continue;
            anchors.add(c);
        }
        anchors.sort((a, b) -> Integer.compare(b.length(), a.length()));
        if (anchors.size() > 8) {
            return List.copyOf(anchors.subList(0, 8));
        }
        return List.copyOf(anchors);
    }
}
