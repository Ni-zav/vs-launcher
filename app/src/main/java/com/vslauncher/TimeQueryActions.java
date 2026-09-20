package com.vslauncher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java parsing for explicit timer/alarm search actions. */
final class TimeQueryActions {
    private static final int MAX_TIMER_SECONDS = 24 * 60 * 60;
    private static final Pattern TIMER_PART = Pattern.compile(
            "(\\d{1,4})\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)",
            Pattern.CASE_INSENSITIVE
    );

    private TimeQueryActions() {}

    static TimerSpec timer(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("timer ")) return null;

        String body = value.substring(6).trim();
        if (body.isEmpty()) return null;

        Matcher matcher = TIMER_PART.matcher(body);
        int cursor = 0;
        int seconds = 0;
        int parts = 0;
        while (matcher.find()) {
            if (!body.substring(cursor, matcher.start()).trim().isEmpty()) return null;

            int amount;
            try {
                amount = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException error) {
                return null;
            }
            if (amount <= 0) return null;

            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            if (unit.startsWith("h")) {
                seconds += amount * 3600;
            } else if (unit.startsWith("m")) {
                seconds += amount * 60;
            } else {
                seconds += amount;
            }
            if (seconds > MAX_TIMER_SECONDS) return null;

            parts++;
            cursor = matcher.end();
        }

        if (parts == 0 || !body.substring(cursor).trim().isEmpty()) return null;
        return new TimerSpec(seconds, formatDuration(seconds));
    }

    static AlarmSpec alarm(String raw) {
        return alarmNormalized(SearchNormalization.normalize(raw));
    }

    static AlarmSpec alarmNormalized(String normalized) {
        if (normalized == null) return null;
        if (normalized.startsWith("set alarm ")) {
            normalized = normalized.substring(10).trim();
        } else if (normalized.startsWith("alarm ")) {
            normalized = normalized.substring(6).trim();
        } else {
            return null;
        }

        String[] parts = normalized.split(" ");
        if (parts.length != 2) return null;

        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return new AlarmSpec(hour, minute);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static String formatDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder out = new StringBuilder();
        if (hours > 0) out.append(hours).append('h');
        if (minutes > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(minutes).append('m');
        }
        if (seconds > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(seconds).append('s');
        }
        return out.toString();
    }

    static final class TimerSpec {
        final int seconds;
        final String display;

        TimerSpec(int seconds, String display) {
            this.seconds = seconds;
            this.display = display;
        }
    }

    static final class AlarmSpec {
        final int hour;
        final int minute;
        final String display;

        AlarmSpec(int hour, int minute) {
            this.hour = hour;
            this.minute = minute;
            this.display = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        }
    }
}
