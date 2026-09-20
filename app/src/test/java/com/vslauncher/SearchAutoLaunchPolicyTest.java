package com.vslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SearchAutoLaunchPolicyTest {
    @Test public void singletonStillAutoLaunchesAfterQuietPeriod() {
        assertEquals(
                SearchAutoLaunchPolicy.FIRST_SINGLETON_DELAY_MS,
                delay("sho", "sho", "shopee", "", "s", "", false, false)
        );
        assertEquals(
                SearchAutoLaunchPolicy.STABLE_SINGLETON_DELAY_MS,
                delay("shop", "shop", "shopee", "", "s", "", false, true)
        );
        assertEquals(
                SearchAutoLaunchPolicy.EXACT_MATCH_DELAY_MS,
                delay("shopee", "shopee", "shopee", "", "s", "", false, true)
        );
    }

    @Test public void continuedTypingCancelsStaleSingleton() {
        assertEquals(
                SearchAutoLaunchPolicy.BLOCKED,
                delay("shou", "shou", "shopee", "", "s", "", false, true)
        );
    }

    @Test public void formingCalculatorIntentBlocksBeforeExpressionIsComplete() {
        assertEquals(
                SearchAutoLaunchPolicy.NUMERIC_SINGLETON_DELAY_MS,
                delay("1", "1", "1 1 1 1", "", "1", "", false, false)
        );
        assertEquals(
                SearchAutoLaunchPolicy.BLOCKED,
                delay("1+", "1", "1 1 1 1", "", "1", "", false, true)
        );
        assertTrue(SearchAutoLaunchPolicy.isFormingCompetingIntent("23*", "23"));
    }

    @Test public void explicitTimeAndUrlIntentBlockAutoLaunchEarly() {
        assertTrue(SearchAutoLaunchPolicy.isFormingCompetingIntent("timer 1", "timer 1"));
        assertTrue(SearchAutoLaunchPolicy.isFormingCompetingIntent("alarm 0", "alarm 0"));
        assertTrue(SearchAutoLaunchPolicy.isFormingCompetingIntent("example.", "example"));
    }

    @Test public void imeCompositionGuardOnlyAppliesToCompositionSensitiveLanguages() {
        assertFalse(SearchAutoLaunchPolicy.shouldBlockImeComposition(true, "en-US"));
        assertFalse(SearchAutoLaunchPolicy.shouldBlockImeComposition(true, ""));
        assertTrue(SearchAutoLaunchPolicy.shouldBlockImeComposition(true, "zh-CN"));
        assertTrue(SearchAutoLaunchPolicy.shouldBlockImeComposition(true, "ja-JP"));
        assertTrue(SearchAutoLaunchPolicy.shouldBlockImeComposition(true, "ko-KR"));
        assertFalse(SearchAutoLaunchPolicy.shouldBlockImeComposition(false, "ja-JP"));
    }

    @Test public void imeCompositionAndLeadingSpaceAreSafeEscapes() {
        assertEquals(
                SearchAutoLaunchPolicy.BLOCKED,
                delay("sho", "sho", "shopee", "", "s", "", true, false)
        );
        assertEquals(
                SearchAutoLaunchPolicy.BLOCKED,
                delay(" sho", "sho", "shopee", "", "s", "", false, false)
        );
    }

    @Test public void strongMultiWordAppPrefixStillAutoLaunches() {
        assertEquals(
                SearchAutoLaunchPolicy.FIRST_SINGLETON_DELAY_MS,
                delay(
                        "google ma",
                        "google ma",
                        "google maps",
                        "",
                        "gm",
                        "",
                        false,
                        false
                )
        );
    }

    @Test public void sentenceLikeWeakMatchDoesNotStealGeneralText() {
        assertEquals(
                SearchAutoLaunchPolicy.BLOCKED,
                delay(
                        "should i do",
                        "should i do",
                        "prefix should i do suffix",
                        "",
                        "psids",
                        "",
                        false,
                        false
                )
        );
    }

    @Test public void webFallbackCoexistsOnlyWithWeakGeneralTextMatches() {
        assertTrue(SearchAutoLaunchPolicy.shouldOfferWebFallback("unknown query", 0, -1, 0));
        assertTrue(SearchAutoLaunchPolicy.shouldOfferWebFallback(
                "should i do",
                1,
                SearchRanking.CANONICAL_SUBSTRING,
                0
        ));
        assertFalse(SearchAutoLaunchPolicy.shouldOfferWebFallback(
                "google ma",
                1,
                SearchRanking.CANONICAL_PREFIX,
                0
        ));
        assertFalse(SearchAutoLaunchPolicy.shouldOfferWebFallback(
                "should i do",
                1,
                SearchRanking.CANONICAL_SUBSTRING,
                1
        ));
    }

    private static long delay(
            String raw,
            String normalized,
            String label,
            String alias,
            String initials,
            String aliasInitials,
            boolean composing,
            boolean sameCandidate
    ) {
        return SearchAutoLaunchPolicy.delayMs(
                raw,
                normalized,
                label,
                alias,
                initials,
                aliasInitials,
                composing,
                sameCandidate
        );
    }
}
