package com.vslauncher;

/** Pure-Java search ranking contract used by AppRepository and JVM tests. */
final class SearchRanking {
    static final int NO_MATCH = -1;
    static final int CANONICAL_PREFIX = 0;
    static final int ALIAS_PREFIX = 1;
    static final int CANONICAL_SUBSTRING = 2;
    static final int ALIAS_SUBSTRING = 3;

    private SearchRanking() {}

    static int rank(String normalizedLabel, String normalizedAlias, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) return NO_MATCH;
        String label = normalizedLabel == null ? "" : normalizedLabel;
        String alias = normalizedAlias == null ? "" : normalizedAlias;

        if (label.startsWith(normalizedQuery)) return CANONICAL_PREFIX;
        if (alias.startsWith(normalizedQuery)) return ALIAS_PREFIX;
        if (label.contains(normalizedQuery)) return CANONICAL_SUBSTRING;
        if (alias.contains(normalizedQuery)) return ALIAS_SUBSTRING;
        return NO_MATCH;
    }
}
