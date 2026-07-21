package org.gw.chatfilterplus.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class DomainScriptNormalizer {

    private static final Map<Character, String> HOMOGLYPHS = buildHomoglyphs();

    // TLD-обфускации: только leet (цифры вместо букв) и растяжки. Обычные слова/аббревиатуры
    // (fan=фанат, ton=тон, npo=НПО) НЕ включаем — они превращают нормальную речь в поддельный TLD.
    private static final Map<String, String> TLD_ALIASES = Map.ofEntries(
            Map.entry("phun", "fun"),
            Map.entry("funn", "fun"),
            Map.entry("fuun", "fun"),
            Map.entry("t0p", "top"),
            Map.entry("pr0", "pro"),
            Map.entry("c0m", "com"),
            Map.entry("n3t", "net"),
            Map.entry("0rg", "org"),
            Map.entry("orgg", "org")
    );

    private DomainScriptNormalizer() {
    }

    public static String mapHomoglyphs(String lowerCased) {
        if (lowerCased == null || lowerCased.isEmpty()) return "";
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

    public static String applyTldAliases(String domain) {
        if (domain == null || domain.isEmpty()) return "";
        int lastDot = domain.lastIndexOf('.');
        if (lastDot < 0) {
            String alias = TLD_ALIASES.get(domain.toLowerCase(Locale.ROOT));
            return alias != null ? alias : domain;
        }
        if (lastDot == 0 || lastDot >= domain.length() - 1) {
            return domain;
        }
        String head = domain.substring(0, lastDot);
        String tld = domain.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        String alias = TLD_ALIASES.get(tld);
        if (alias == null) {
            return domain;
        }
        return head + "." + alias;
    }

    public static String toLatinDomain(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastDot = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if (c == '\u2024' || c == '\u00B7' || c == ',' || c == ';') {
                c = '.';
            }
            if (c == '.' || Character.isWhitespace(c)) {
                if (!lastDot && sb.length() > 0) {
                    sb.append('.');
                    lastDot = true;
                }
                continue;
            }
            String mapped = HOMOGLYPHS.get(c);
            if (mapped != null) {
                if (mapped.isEmpty()) continue;
                sb.append(mapped.toLowerCase(Locale.ROOT));
                lastDot = false;
                continue;
            }
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
                lastDot = false;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
            sb.setLength(sb.length() - 1);
        }
        while (sb.length() > 0 && sb.charAt(0) == '.') {
            sb.deleteCharAt(0);
        }
        return applyTldAliases(sb.toString());
    }

    public static boolean isLatinDomainLabel(String label) {
        if (label == null || label.isEmpty() || label.length() > 63) return false;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    public static boolean isDomainTokenAfterNorm(String rawToken) {
        if (rawToken == null || rawToken.isEmpty() || rawToken.length() > 63) return false;
        if (isLatinDomainLabel(rawToken)) return true;

        boolean hasLatin = false;
        boolean hasCyrillic = false;
        for (int i = 0; i < rawToken.length(); i++) {
            char c = rawToken.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLatin = true;
            } else if (Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC) {
                hasCyrillic = true;
            }
        }

        if (hasCyrillic && !hasLatin && rawToken.length() > 3) {
            return false;
        }

        String norm = toLatinDomain(rawToken);
        return isLatinDomainLabel(norm)
                && !norm.isEmpty()
                && !norm.contains(".")
                && norm.length() <= 24;
    }

    public static int latinLetterCount(String t) {
        if (t == null) return 0;
        int n = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = Character.toLowerCase(t.charAt(i));
            if (c >= 'a' && c <= 'z') n++;
        }
        return n;
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
        map.put('ё', "e");

        for (char c = 'a'; c <= 'z'; c++) {
            map.put((char) ('ａ' + (c - 'a')), String.valueOf(c));
        }
        return Map.copyOf(map);
    }
}
