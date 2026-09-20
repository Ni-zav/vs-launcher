package com.vslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LauncherLayoutTest {
    @Test public void alphabetBucketsStartWithFallbackHash() {
        assertEquals(0, LauncherLayout.alphabetBucket("1password"));
        assertEquals(0, LauncherLayout.alphabetBucket("µtorrent"));
        assertEquals(0, LauncherLayout.alphabetBucket("数字"));
        assertEquals(1, LauncherLayout.alphabetBucket("alpha"));
        assertEquals(26, LauncherLayout.alphabetBucket("zeta"));
    }

    @Test public void alphabetScrubSnapsToActualAvailableBucket() {
        int[] first = new int[27];
        java.util.Arrays.fill(first, -1);
        first[0] = 0;
        first[2] = 4;
        first[5] = 9;

        assertEquals(0, LauncherLayout.nearestAvailableBucket(first, 0));
        assertEquals(2, LauncherLayout.nearestAvailableBucket(first, 1));
        assertEquals(5, LauncherLayout.nearestAvailableBucket(first, 4));
        assertEquals(2, LauncherLayout.nearestAvailableBucket(first, 3));
    }

    @Test public void visibleRowsFitCommonPhoneHeights() {
        float[] rowHeights = {44f, 54f, 64f};
        float[] contentHeights = {320f, 480f, 640f, 760f};

        for (float rowHeight : rowHeights) {
            for (float contentHeight : contentHeights) {
                int visible = LauncherLayout.visibleRows(8, rowHeight, 100f, 100f + contentHeight);
                assertTrue(visible >= 0);
                assertTrue(visible <= 8);
                assertTrue(visible * rowHeight <= contentHeight + 0.001f);
            }
        }
    }

    @Test public void rowHitTestingRespectsEdges() {
        assertEquals(-1, LauncherLayout.rowIndexAt(99f, 100f, 54f, 5));
        assertEquals(0, LauncherLayout.rowIndexAt(100f, 100f, 54f, 5));
        assertEquals(0, LauncherLayout.rowIndexAt(153.9f, 100f, 54f, 5));
        assertEquals(1, LauncherLayout.rowIndexAt(154f, 100f, 54f, 5));
        assertEquals(-1, LauncherLayout.rowIndexAt(370f, 100f, 54f, 5));
    }

    @Test public void maxScrollNeverNegative() {
        assertEquals(0f, LauncherLayout.maxScroll(3, 54f, 500f), 0.001f);
        assertEquals(40f, LauncherLayout.maxScroll(10, 54f, 500f), 0.001f);
        assertEquals(0f, LauncherLayout.maxScroll(10, 0f, 500f), 0.001f);
    }

    @Test public void sectionedSettingsRowsNeverOverlap() {
        float[] rows = new float[20];
        float[] sections = new float[6];
        int[] starts = {0, 4, 13, 16, 17, 19};

        LauncherLayout.fillSectionedRows(
                rows,
                sections,
                100f,
                54f,
                28f,
                11f,
                starts
        );

        for (int i = 1; i < rows.length; i++) {
            assertTrue(rows[i] >= rows[i - 1] + 54f);
        }

        assertTrue(rows[0] > 100f);
        assertTrue(rows[4] > rows[3] + 54f);
        assertTrue(rows[13] > rows[12] + 54f);
        assertTrue(rows[16] > rows[15] + 54f);
        assertTrue(rows[17] > rows[16] + 54f);
        assertTrue(rows[19] > rows[18] + 54f);
    }

    @Test public void visibleRangeIsClamped() {
        assertEquals(0, LauncherLayout.firstVisibleIndex(100f, 100f, 54f, 10));
        assertEquals(2, LauncherLayout.firstVisibleIndex(208f, 100f, 54f, 10));
        assertEquals(4, LauncherLayout.lastVisibleExclusive(262f, 100f, 54f, 10));
        assertEquals(10, LauncherLayout.lastVisibleExclusive(1000f, 100f, 54f, 10));
    }
}
