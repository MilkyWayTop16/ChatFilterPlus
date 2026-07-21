package org.gw.chatfilterplus.utils;

import java.util.Locale;
import java.util.Set;

public final class AdContactShare {

    private AdContactShare() {
    }

    public static boolean isBenignContactShare(String message,
                                               Set<String> keywords,
                                               Set<String> handles,
                                               boolean hasStandardLink,
                                               boolean hasUrlish) {
        if (hasStandardLink || hasUrlish) return false;
        if (message == null || message.length() < 3 || message.length() > 64) return false;

        String compact = AdTextAnalyzer.compact(message);
        if (hasRecruitmentIntent(message, compact)) return false;
        return hasContactPlatformHint(message, compact, keywords);
    }

    public static boolean hasContactPlatformHint(String message, String compact, Set<String> keywords) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);

        if (containsAny(lower,
                "discord", "дискорд", "telegram", "телеграм", "телега", "телеграмм",
                "тгк", "tgc", "tgk")) {
            return true;
        }
        if (containsWord(lower, "tg")
                || containsWord(lower, "ds")
                || containsWord(lower, "dsc")
                || containsWord(lower, "тг")
                || containsWord(lower, "дс")) {
            return true;
        }
        if (keywords != null) {
            for (String key : keywords) {
                if (isContactPlatformKeyword(key)) return true;
            }
        }
        return false;
    }

    public static boolean hasRecruitmentIntent(String message, String compact) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(lower,
                "лучший", "топовый", "самый",
                "подпишись", "подписка", "подписывайтесь",
                "заходи", "заходите", "залетайте", "переходи", "переходите",
                "ссылка", "ссылку",
                "тгк", "tgc", "tgk", "тг канал", "тг-канал", "телеграм канал",
                "наш тгк", "наш tgc", "наш дискорд", "наш дс", "наш сервер",
                "discord сервер", "дискорд сервер",
                "invite", "инвайт", "промокод", "реклама",
                "сервер", "айпи",
                "грифер", "анарх", "ванильн", "выживани")) {
            return true;
        }
        if (compact != null && containsAny(compact,
                "lucw", "topov", "podpis", "zahodi", "perehod", "ssilka",
                "tgk", "tgc", "sepvep", "invait", "invite", "reklama", "promokod",
                "aipi", "gifer", "anarho", "anarhia")) {
            return true;
        }
        return false;
    }

    public static boolean isContactPlatformKeyword(String key) {
        if (key == null) return false;
        return key.equals("tg")
                || key.equals("тг")
                || key.equals("tgk")
                || key.equals("тгк")
                || key.equals("tgc")
                || key.equals("ds")
                || key.equals("дс")
                || key.equals("dsc")
                || key.equals("discord")
                || key.equals("дискорд")
                || key.equals("telegram")
                || key.equals("телеграм")
                || key.equals("телега")
                || key.equals("телеграмм");
    }

    private static boolean containsWord(String lower, String word) {
        if (lower == null || word == null || word.isEmpty()) return false;
        int from = 0;
        while (from <= lower.length() - word.length()) {
            int i = lower.indexOf(word, from);
            if (i < 0) return false;
            boolean leftOk = i == 0 || !Character.isLetterOrDigit(lower.charAt(i - 1));
            int end = i + word.length();
            boolean rightOk = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
            if (leftOk && rightOk) return true;
            from = i + 1;
        }
        return false;
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) return false;
        for (String n : needles) {
            if (n != null && !n.isEmpty() && haystack.contains(n)) return true;
        }
        return false;
    }
}
