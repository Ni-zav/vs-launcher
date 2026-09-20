package com.vslauncher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SearchRankingTest {
    @Test public void canonicalPrefixWins() {
        assertEquals(
                SearchRanking.CANONICAL_PREFIX,
                SearchRanking.rank("calendar", "agenda", "c", "a", "cal")
        );
    }

    @Test public void aliasPrefixBeatsInitialsAndSubstring() {
        assertEquals(
                SearchRanking.ALIAS_PREFIX,
                SearchRanking.rank("my calendar", "calendar", "mc", "c", "cal")
        );
    }

    @Test public void canonicalInitialsBeatSubstring() {
        assertEquals(
                SearchRanking.CANONICAL_INITIALS,
                SearchRanking.rank("youtube music", "", "ytm", "", "ytm")
        );
    }

    @Test public void camelCaseInitialsAreCaptured() {
        assertEquals("ytm", SearchRanking.initials("YouTube Music"));
        assertEquals("gm", SearchRanking.initials("Google Maps"));
    }

    @Test public void canonicalSubstringBeatsFuzzy() {
        assertEquals(
                SearchRanking.CANONICAL_SUBSTRING,
                SearchRanking.rank("supercalendar", "", "s", "", "cal")
        );
    }

    @Test public void boundedFuzzyMatchesOrderedCharacters() {
        assertEquals(
                SearchRanking.CANONICAL_FUZZY,
                SearchRanking.rank("calculator", "", "c", "", "cltr")
        );
    }

    @Test public void fuzzyRejectsLooseOrReversedMatches() {
        assertEquals(
                SearchRanking.NO_MATCH,
                SearchRanking.rank("calculator", "", "c", "", "rtl")
        );
        assertEquals(
                SearchRanking.NO_MATCH,
                SearchRanking.rank("abcdefghijklmnopqrstuv", "", "a", "", "av")
        );
    }

    @Test public void noMatchAndEmptyQueryReturnNoMatch() {
        assertEquals(
                SearchRanking.NO_MATCH,
                SearchRanking.rank("notes", "memo", "n", "m", "cal")
        );
        assertEquals(
                SearchRanking.NO_MATCH,
                SearchRanking.rank("notes", "memo", "n", "m", "")
        );
    }
}
