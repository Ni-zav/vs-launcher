package com.vslauncher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SearchRankingTest {
    @Test public void canonicalPrefixWins() {
        assertEquals(
                SearchRanking.CANONICAL_PREFIX,
                SearchRanking.rank("calendar", "agenda", "cal")
        );
    }

    @Test public void aliasPrefixBeatsCanonicalSubstring() {
        assertEquals(
                SearchRanking.ALIAS_PREFIX,
                SearchRanking.rank("my calendar", "calendar", "cal")
        );
    }

    @Test public void canonicalSubstringBeatsAliasSubstring() {
        assertEquals(
                SearchRanking.CANONICAL_SUBSTRING,
                SearchRanking.rank("supercalendar", "my calendar", "cal")
        );
    }

    @Test public void aliasSubstringIsLowestMatchTier() {
        assertEquals(
                SearchRanking.ALIAS_SUBSTRING,
                SearchRanking.rank("notes", "work calendar", "cal")
        );
    }

    @Test public void noMatchAndEmptyQueryReturnNoMatch() {
        assertEquals(SearchRanking.NO_MATCH, SearchRanking.rank("notes", "memo", "cal"));
        assertEquals(SearchRanking.NO_MATCH, SearchRanking.rank("notes", "memo", ""));
    }
}
