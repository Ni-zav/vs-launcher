package com.vslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SearchPipelineScaleTest {
    @Test public void thousandSyntheticEntriesUseDeterministicLinearRanking() {
        int visited = 0;
        int matches = 0;
        String query = "app 99";

        for (int i = 0; i < 1000; i++) {
            String label = "App " + i + " Utility";
            String normalized = SearchNormalization.normalize(label);
            String initials = SearchRanking.initials(label);
            int rank = SearchRanking.rank(normalized, "", initials, "", query);
            visited++;
            if (rank >= 0) matches++;
        }

        assertEquals(1000, visited);
        assertTrue(matches > 0);
        assertTrue(matches < 1000);
    }

    @Test public void normalizedAlarmPathConsumesPreNormalizedInput() {
        TimeQueryActions.AlarmSpec spec =
                TimeQueryActions.alarmNormalized("alarm 06 45");
        assertTrue(spec != null);
        assertEquals(6, spec.hour);
        assertEquals(45, spec.minute);
    }
}
