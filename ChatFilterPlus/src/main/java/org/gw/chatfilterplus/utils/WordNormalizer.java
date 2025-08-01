package org.gw.chatfilterplus.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class WordNormalizer {
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\p{L}\\p{N}]"); // Удаляет всё, кроме букв и цифр
    private static final Pattern REPEATED_VOWELS = Pattern.compile("([аеёиоуыэюяАЕЁИОУЫЭЮЯ])\\1{2,}"); // Удаляет 2+ повторяющихся гласных
    private static final Pattern REPEATED_CONSONANTS = Pattern.compile("([бвгджзйклмнпрстфхцчшщБВГДЖЗЙКЛМНПРСТФХЦЧШЩ])\\1{2,}"); // Удаляет 2+ повторяющихся согласных

    public static String normalize(String text, String filterLevel) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        if ("low".equalsIgnoreCase(filterLevel)) {
            return text.toLowerCase();
        }

        String normalized = text.toLowerCase();

        normalized = SPECIAL_CHARS.matcher(normalized).replaceAll("");

        normalized = REPEATED_VOWELS.matcher(normalized).replaceAll("$1");

        normalized = REPEATED_CONSONANTS.matcher(normalized).replaceAll("$1");

        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);

        return normalized;
    }

    public static boolean isSafeWord(String word) {
        String[] safeWords = {
                "хуже", "худая", "хулиган", "хулиганить", "хулиганка", "хулиганский", "худой", "худощавый", "худенький",
                "художник", "художественный", "художество", "худоба", "худенькая", "худеть", "худощавость",
                "хумус", "художественность",
                "похоже", "позже", "похвальный", "похвала", "похвалить", "похожий", "похожесть",
                "похлебка", "похолодание", "похоронить", "похороны", "похотливый", "похоть", "похвально",
                "блестящая", "блестящий", "блеск", "блестеть", "блестка", "блестюшка", "блещущий", "блестяще",
                "благо", "благодарность", "благополучие", "благородный", "благоразумие", "блаженство",
                "блаженный", "блажь", "благотворительность",
                "позде", "поздний", "поздно", "поздравить", "поздравление", "поздее", "поздравительный",
                "поздравляться",
                "пиджак", "пиджачок", "пидестал", "пиджама",
                "говор", "говорить", "говорливый", "говорун", "говядина", "говяжий", "говорок",
                "шлюз", "шлюзы", "шлюпка", "шлюпочный", "шлюзование",
                "членство", "членский", "членение", "членистоногий", "членистоногие", "членик", "членики",
                "членовредительство", "членовредитель", "членкор", "членоголовый", "членовредительный",
                "анализ", "аналитика", "аналитик", "аналитический", "аналогия", "аналог", "аналогичный",
                "сукно", "сукноделие", "суконный", "сукровица", "суковатый",
                "муда", "мудачок", "мудабор"
        };
        for (String safeWord : safeWords) {
            if (word.equalsIgnoreCase(safeWord)) {
                return true;
            }
        }
        return false;
    }
}