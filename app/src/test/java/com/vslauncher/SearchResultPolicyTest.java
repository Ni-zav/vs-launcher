package com.vslauncher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class SearchResultPolicyTest {
    @Test public void utilityRowsNeverMasqueradeAsApps() {
        TimeQueryActions.TimerSpec timer = TimeQueryActions.timer("timer 10m");
        TimeQueryActions.AlarmSpec alarm = TimeQueryActions.alarmNormalized("alarm 07 30");
        assertNotNull(timer);
        assertNotNull(alarm);

        assertFalse(SearchResult.timer(timer).isApp());
        assertFalse(SearchResult.alarm(alarm).isApp());
        assertFalse(SearchResult.calculation("391").isApp());
        assertFalse(SearchResult.web("unknown query").isApp());
        assertFalse(SearchResult.dial("+628123456789").isApp());
        assertFalse(SearchResult.url("https://example.com").isApp());
    }
}
