package org.gw.chatfilterplus.managers.profanity;

import org.gw.chatfilterplus.utils.TextNormalizer;

public final class ObfuscationDetector {

    private ObfuscationDetector() {
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
}
