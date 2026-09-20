package com.vslauncher;

import java.util.Locale;
import java.util.regex.Pattern;

final class QueryActions {
    private static final Pattern PHONE_CHARS =
            Pattern.compile("^\\+?[0-9() .-]{3,}$");
    private static final Pattern DOMAIN =
            Pattern.compile(
                    "^(?:https?://)?(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(?::[0-9]{1,5})?(?:/[^\\s]*)?$",
                    Pattern.CASE_INSENSITIVE
            );

    private QueryActions() {}

    static String dialPayload(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (!PHONE_CHARS.matcher(value).matches()) return null;

        int digits = 0;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
                out.append(c);
            } else if (c == '+' && out.length() == 0) {
                out.append(c);
            }
        }
        return digits >= 3 ? out.toString() : null;
    }

    static String urlPayload(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.indexOf(' ') >= 0) return null;
        if (!DOMAIN.matcher(value).matches()) return null;

        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://")
                ? value
                : "https://" + value;
    }
}
