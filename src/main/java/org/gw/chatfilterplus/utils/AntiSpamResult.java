package org.gw.chatfilterplus.utils;

public class AntiSpamResult {

    public final String reason;
    public final int remainingSeconds;
    public final boolean duplicateEvent;

    public AntiSpamResult(String reason, int remainingSeconds) {
        this(reason, remainingSeconds, false);
    }

    public AntiSpamResult(String reason, int remainingSeconds, boolean duplicateEvent) {
        this.reason = reason;
        this.remainingSeconds = remainingSeconds;
        this.duplicateEvent = duplicateEvent;
    }

    public AntiSpamResult asDuplicate() {
        return new AntiSpamResult(reason, remainingSeconds, true);
    }
}
