package com.vslauncher;

import java.text.Normalizer;
import java.util.Locale;

/** Pure-Java search normalization shared by app, alias and command matching. */
final class SearchNormalization {
    private SearchNormalization() {}

    static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(decomposed.length());
        boolean pendingSpace = false;

        for (int i = 0; i < decomposed.length(); i++) {
            char current = decomposed.charAt(i);
            int type = Character.getType(current);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }

            if (Character.isLetterOrDigit(current)) {
                if (pendingSpace && out.length() > 0) out.append(' ');
                out.append(Character.toLowerCase(current));
                pendingSpace = false;
            } else {
                pendingSpace = out.length() > 0;
            }
        }

        return out.toString().trim().toLowerCase(Locale.ROOT);
    }
}
