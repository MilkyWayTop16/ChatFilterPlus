package org.gw.chatfilterplus.utils;

public class AntiSpamResult {

    public final String reason;
    public final int remainingSeconds;

    public AntiSpamResult(String reason, int remainingSeconds) {
        this.reason = reason;
        this.remainingSeconds = remainingSeconds;
    }
}