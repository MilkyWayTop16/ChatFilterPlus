package org.gw.chatfilterplus.utils;

public final class DurationFormat {

    public record Settings(
            String days,
            String hours,
            String minutes,
            String seconds,
            String separator,
            boolean hideZero,
            String zero
    ) {
        public static Settings defaults() {
            return new Settings(
                    "{value}д.",
                    "{value}ч.",
                    "{value}м.",
                    "{value}с.",
                    " ",
                    true,
                    "0с."
            );
        }
    }

    private DurationFormat() {
    }

    public static String formatSeconds(long totalSeconds, Settings settings) {
        Settings s = settings != null ? settings : Settings.defaults();
        long total = Math.max(0L, totalSeconds);

        long days = total / 86_400L;
        long hours = (total % 86_400L) / 3_600L;
        long minutes = (total % 3_600L) / 60L;
        long seconds = total % 60L;

        StringBuilder out = new StringBuilder(24);
        appendPart(out, days, s.days(), s.separator(), s.hideZero());
        appendPart(out, hours, s.hours(), s.separator(), s.hideZero());
        appendPart(out, minutes, s.minutes(), s.separator(), s.hideZero());
        appendPart(out, seconds, s.seconds(), s.separator(), s.hideZero());

        if (out.length() == 0) {
            String zero = s.zero() != null ? s.zero() : "0с.";
            return zero.replace("{value}", "0");
        }
        return out.toString();
    }

    private static void appendPart(StringBuilder out, long value, String pattern,
                                   String separator, boolean hideZero) {
        if (hideZero && value == 0L) return;
        if (pattern == null || pattern.isEmpty()) return;
        if (out.length() > 0 && separator != null && !separator.isEmpty()) {
            out.append(separator);
        }
        out.append(pattern.replace("{value}", Long.toString(value)));
    }
}
