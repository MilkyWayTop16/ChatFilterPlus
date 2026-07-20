package org.gw.chatfilterplus.managers.profanity;

import org.gw.chatfilterplus.utils.TextNormalizer;

import java.util.Arrays;

final class CompactView {

    final String compact;
    final int[] originIndex;

    CompactView(String compact, int[] originIndex) {
        this.compact = compact;
        this.originIndex = originIndex;
    }

    static CompactView of(String message, boolean collapseRepeats) {
        int len = message.length();
        char[] compactBuf = new char[len];
        int[] originBuf = new int[len];
        int size = 0;
        char last = 0;

        for (int i = 0; i < len; i++) {
            int mapped = TextNormalizer.mapChar(message.charAt(i));
            if (mapped <= 0) continue;
            char ch = (char) mapped;
            if (collapseRepeats && size > 0 && ch == last) {
                originBuf[size - 1] = i;
                continue;
            }
            compactBuf[size] = ch;
            originBuf[size] = i;
            size++;
            last = ch;
        }

        return new CompactView(new String(compactBuf, 0, size), Arrays.copyOf(originBuf, size));
    }

    int originalStart(int compactStart) {
        return originIndex[compactStart];
    }

    int originalEndExclusive(int compactEndExclusive) {
        if (compactEndExclusive <= 0) return 0;
        return originIndex[compactEndExclusive - 1] + 1;
    }
}
