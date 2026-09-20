package com.vslauncher;

/** Pure-Java search ranking contract used by AppRepository and JVM tests. */
final class SearchRanking {
    static final int NO_MATCH = -1;
    static final int CANONICAL_PREFIX = 0;
    static final int ALIAS_PREFIX = 1;
    static final int CANONICAL_INITIALS = 2;
    static final int ALIAS_INITIALS = 3;
    static final int CANONICAL_SUBSTRING = 4;
    static final int ALIAS_SUBSTRING = 5;
    static final int CANONICAL_FUZZY = 6;
    static final int ALIAS_FUZZY = 7;

    private SearchRanking() {}

    static int rank(
            String normalizedLabel,
            String normalizedAlias,
            String canonicalInitials,
            String aliasInitials,
            String normalizedQuery
    ) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) return NO_MATCH;
        String label = normalizedLabel == null ? "" : normalizedLabel;
        String alias = normalizedAlias == null ? "" : normalizedAlias;
        String labelInitials = canonicalInitials == null ? "" : canonicalInitials;
        String aliasInitialsSafe = aliasInitials == null ? "" : aliasInitials;

        if (label.startsWith(normalizedQuery)) return CANONICAL_PREFIX;
        if (alias.startsWith(normalizedQuery)) return ALIAS_PREFIX;
        if (labelInitials.startsWith(normalizedQuery)) return CANONICAL_INITIALS;
        if (aliasInitialsSafe.startsWith(normalizedQuery)) return ALIAS_INITIALS;
        if (label.contains(normalizedQuery)) return CANONICAL_SUBSTRING;
        if (alias.contains(normalizedQuery)) return ALIAS_SUBSTRING;
        if (isBoundedSubsequence(label, normalizedQuery)) return CANONICAL_FUZZY;
        if (isBoundedSubsequence(alias, normalizedQuery)) return ALIAS_FUZZY;
        return NO_MATCH;
    }

    static String initials(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(Math.min(8, value.length()));
        char previous = 0;

        for (int i = 0; i < value.length() && out.length() < 8; i++) {
            char current = value.charAt(i);
            if (!Character.isLetterOrDigit(current)) {
                previous = current;
                continue;
            }

            boolean wordStart = i == 0 || !Character.isLetterOrDigit(previous);
            boolean camelBoundary = i > 0
                    && Character.isUpperCase(current)
                    && Character.isLowerCase(previous);

            if (wordStart || camelBoundary) {
                out.append(Character.toLowerCase(current));
            }
            previous = current;
        }
        return out.toString();
    }

    private static boolean isBoundedSubsequence(String value, String query) {
        if (query.length() < 2 || query.length() > 12 || value.isEmpty()) return false;

        int queryIndex = 0;
        int firstMatch = -1;
        int lastMatch = -1;
        for (int i = 0; i < value.length() && queryIndex < query.length(); i++) {
            if (value.charAt(i) == query.charAt(queryIndex)) {
                if (firstMatch < 0) firstMatch = i;
                lastMatch = i;
                queryIndex++;
            }
        }
        if (queryIndex != query.length()) return false;

        // Avoid extremely loose matches: the matched span may be at most
        // queryLength * 4 characters, capped at 24.
        int maxSpan = Math.min(24, query.length() * 4);
        return lastMatch - firstMatch + 1 <= maxSpan;
    }
}
