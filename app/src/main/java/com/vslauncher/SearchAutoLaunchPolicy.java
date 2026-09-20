package com.vslauncher;

/**
 * Pure, allocation-light policy for deciding when a singleton app search result
 * is safe to auto-launch.
 *
 * Filtering/ranking still owns app discovery. This class only inspects the
 * already available query/candidate state and never traverses the app list.
 */
final class SearchAutoLaunchPolicy {
    static final long BLOCKED = -1L;
    static final long FIRST_SINGLETON_DELAY_MS = 650L;
    static final long STABLE_SINGLETON_DELAY_MS = 400L;
    static final long EXACT_MATCH_DELAY_MS = 180L;
    static final long NUMERIC_SINGLETON_DELAY_MS = 1800L;

    private SearchAutoLaunchPolicy() {}

    static long delayMs(
            String rawQuery,
            String normalizedQuery,
            String normalizedLabel,
            String normalizedAlias,
            String canonicalInitials,
            String aliasInitials,
            boolean imeComposing,
            boolean sameCandidate
    ) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) return BLOCKED;
        if (imeComposing || hasLeadingWhitespace(rawQuery)) return BLOCKED;
        if (isFormingCompetingIntent(rawQuery, normalizedQuery)) return BLOCKED;

        int rank = rank(
                normalizedLabel,
                normalizedAlias,
                canonicalInitials,
                aliasInitials,
                normalizedQuery
        );
        if (rank == SearchRanking.NO_MATCH) return BLOCKED;

        // A sentence-like multi-word query may coincidentally leave one weak
        // substring/fuzzy app match. Prefix matches such as "google ma" are
        // still treated as explicit app intent.
        if (isGeneralText(normalizedQuery) && rank > SearchRanking.ALIAS_PREFIX) {
            return BLOCKED;
        }

        // Numeric-only text needs a longer grace period because common Android
        // keyboards expose operators such as '+' through a long-press popup.
        // Once an operator arrives, competing-intent detection blocks immediately.
        if (isDigitsOnly(rawQuery)) return NUMERIC_SINGLETON_DELAY_MS;

        if (normalizedQuery.equals(safe(normalizedLabel))
                || normalizedQuery.equals(safe(normalizedAlias))) {
            return EXACT_MATCH_DELAY_MS;
        }
        return sameCandidate ? STABLE_SINGLETON_DELAY_MS : FIRST_SINGLETON_DELAY_MS;
    }

    static int rank(
            String normalizedLabel,
            String normalizedAlias,
            String canonicalInitials,
            String aliasInitials,
            String normalizedQuery
    ) {
        return SearchRanking.rank(
                normalizedLabel,
                normalizedAlias,
                canonicalInitials,
                aliasInitials,
                normalizedQuery
        );
    }

    static boolean shouldOfferWebFallback(
            String normalizedQuery,
            int appMatchCount,
            int firstAppRank,
            int nonAppResultCount
    ) {
        if (normalizedQuery == null || normalizedQuery.length() < 2) return false;
        if (nonAppResultCount != 0) return false;
        if (appMatchCount == 0) return true;

        // Keep app-like queries visually quiet. WEB only coexists with apps
        // when the text looks sentence-like and the best app match is weak.
        return isGeneralText(normalizedQuery)
                && firstAppRank > SearchRanking.ALIAS_PREFIX;
    }

    static boolean isFormingCompetingIntent(String rawQuery, String normalizedQuery) {
        return looksLikeArithmetic(rawQuery)
                || looksLikeUrl(rawQuery)
                || startsWithToken(normalizedQuery, "timer")
                || startsWithToken(normalizedQuery, "alarm")
                || "set alarm".equals(normalizedQuery)
                || normalizedQuery.startsWith("set alarm ");
    }

    static boolean shouldBlockImeComposition(boolean composing, String languageTag) {
        if (!composing || languageTag == null || languageTag.isEmpty()) return false;
        return languageTag.regionMatches(true, 0, "zh", 0, 2)
                || languageTag.regionMatches(true, 0, "ja", 0, 2)
                || languageTag.regionMatches(true, 0, "ko", 0, 2);
    }

    private static boolean isGeneralText(String normalizedQuery) {
        return normalizedQuery != null && normalizedQuery.indexOf(' ') >= 0;
    }

    private static boolean isDigitsOnly(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return false;
        for (int i = 0; i < rawQuery.length(); i++) {
            if (!Character.isDigit(rawQuery.charAt(i))) return false;
        }
        return true;
    }

    private static boolean hasLeadingWhitespace(String rawQuery) {
        return rawQuery != null
                && !rawQuery.isEmpty()
                && Character.isWhitespace(rawQuery.charAt(0));
    }

    private static boolean looksLikeArithmetic(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return false;
        boolean hasDigit = false;
        boolean hasOperator = false;

        for (int i = 0; i < rawQuery.length(); i++) {
            char c = rawQuery.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (c == '+'
                    || c == '-'
                    || c == '*'
                    || c == '/'
                    || c == '%'
                    || c == '='
                    || c == '('
                    || c == ')') {
                hasOperator = true;
            }
            if (hasDigit && hasOperator) return true;
        }
        return false;
    }

    private static boolean looksLikeUrl(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return false;
        boolean hasDotOrPath = false;
        boolean hasAlphaNumeric = false;

        for (int i = 0; i < rawQuery.length(); i++) {
            char c = rawQuery.charAt(i);
            if (Character.isWhitespace(c)) return false;
            if (Character.isLetterOrDigit(c)) hasAlphaNumeric = true;
            if (c == '.' || c == ':' || c == '/') hasDotOrPath = true;
        }
        return hasAlphaNumeric && hasDotOrPath;
    }

    private static boolean startsWithToken(String normalizedQuery, String token) {
        if (normalizedQuery == null) return false;
        return normalizedQuery.equals(token)
                || normalizedQuery.startsWith(token + " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
