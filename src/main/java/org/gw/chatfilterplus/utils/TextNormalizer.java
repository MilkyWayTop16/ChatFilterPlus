package org.gw.chatfilterplus.utils;

import java.text.Normalizer;

public final class TextNormalizer {

    private static final int[] CHAR_MAP = new int[65536];
    private static final boolean[] LETTER_OR_DIGIT = new boolean[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            char c = (char) i;
            CHAR_MAP[i] = computeMapChar(c);
            LETTER_OR_DIGIT[i] = Character.isLetterOrDigit(c);
        }
    }

    private TextNormalizer() {
    }

    public static boolean isZeroWidth(char c) {
        return c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060'
                || c == '\uFEFF' || c == '\u00AD' || c == '\u180E';
    }

    public static boolean isLetterOrDigit(char c) {
        return LETTER_OR_DIGIT[c];
    }

    public static int mapChar(char c) {
        return CHAR_MAP[c];
    }

    private static int computeMapChar(char c) {
        if (isZeroWidth(c)) return 0;

        char ch = Character.toLowerCase(c);

        if (ch == 'ъ' || ch == 'ь' || ch == '\'' || ch == '`' || ch == '´' || ch == '"' || ch == '′') {
            return 0;
        }

        return switch (ch) {
            case 'а', 'a', '@', '4', 'à', 'á', 'â', 'ã', 'ä', 'å' -> 'a';
            case 'б', 'b', '6' -> 'b';
            case 'в', 'v' -> 'v';
            case 'г', 'g', 'ґ' -> 'g';
            case 'д', 'd' -> 'd';
            case 'е', 'ё', 'э', 'e', '3', 'é', 'è', 'ê', 'ë' -> 'e';
            case 'ж' -> 'j';
            case 'з', 'z', '2' -> 'z';
            case 'и', 'й', 'ы', 'i', '1', '!', '|', 'í', 'ì', 'î', 'ï', 'y', 'ý' -> 'i';
            case 'к', 'k' -> 'k';
            case 'л', 'l', 'ł' -> 'l';
            case 'м', 'm' -> 'm';
            case 'н', 'n', 'ñ' -> 'n';
            case 'о', 'o', '0', 'ó', 'ò', 'ô', 'õ', 'ö' -> 'o';
            case 'п', 'p' -> 'p';
            case 'р' -> 'p';
            case 'r' -> 'r';
            case 'с', 's', '5', '$', 'ś', 'š' -> 's';
            case 'c', 'ç' -> 'c';
            case 'т', 't', '7', 'ť' -> 't';
            case 'у', 'u', 'ú', 'ù', 'û', 'ü' -> 'u';
            case 'ф', 'f' -> 'f';
            case 'х', 'x', 'h', '×' -> 'h';
            case 'ц' -> 'c';
            case 'ч' -> 'c';
            case 'ш' -> 's';
            case 'щ' -> 's';
            case 'ю' -> 'u';
            case 'я' -> 'a';
            case 'w' -> 'w';
            case 'q' -> 'q';
            default -> {
                if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                    yield ch;
                }
                if (Character.isLetter(ch) || Character.isDigit(ch)) {
                    yield ch;
                }
                yield -1;
            }
        };
    }

    public static String normalizeCompact(String text, boolean collapseRepeats) {
        if (text == null || text.isEmpty()) return "";

        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(nfkc.length());
        char last = 0;

        for (int i = 0; i < nfkc.length(); i++) {
            int mapped = mapChar(nfkc.charAt(i));
            if (mapped <= 0) continue;
            char ch = (char) mapped;
            if (collapseRepeats && ch == last) continue;
            out.append(ch);
            last = ch;
        }
        return out.toString();
    }

    public static String toLatinTranslit(String text) {
        return normalizeCompact(text, true);
    }
}
