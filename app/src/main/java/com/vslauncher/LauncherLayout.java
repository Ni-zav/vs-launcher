package com.vslauncher;

/** Pure-Java layout math shared by LauncherSurface and JVM tests. */
final class LauncherLayout {
    private LauncherLayout() {}

    static int visibleRows(int maxRows, float rowHeight, float start, float end) {
        if (maxRows <= 0 || rowHeight <= 0f || end <= start) return 0;
        int fit = Math.max(0, (int) Math.floor((end - start) / rowHeight));
        return Math.min(maxRows, fit);
    }

    static float maxScroll(int itemCount, float rowHeight, float viewportHeight) {
        if (itemCount <= 0 || rowHeight <= 0f || viewportHeight <= 0f) return 0f;
        return Math.max(0f, itemCount * rowHeight - viewportHeight);
    }

    static int rowIndexAt(float y, float start, float rowHeight, int count) {
        if (count <= 0 || rowHeight <= 0f || y < start || y >= start + count * rowHeight) {
            return -1;
        }
        int index = (int) ((y - start) / rowHeight);
        return index >= 0 && index < count ? index : -1;
    }

    static int firstVisibleIndex(float clipTop, float start, float rowHeight, int count) {
        if (count <= 0 || rowHeight <= 0f) return 0;
        return Math.max(0, Math.min(count, (int) Math.floor((clipTop - start) / rowHeight)));
    }

    static int lastVisibleExclusive(float clipBottom, float start, float rowHeight, int count) {
        if (count <= 0 || rowHeight <= 0f) return 0;
        int value = (int) Math.ceil((clipBottom - start) / rowHeight) + 1;
        return Math.max(0, Math.min(count, value));
    }

    static void fillSectionedRows(
            float[] rowTops,
            float[] sectionBaselines,
            float viewportTop,
            float rowHeight,
            float sectionHeaderHeight,
            float labelBaselineOffset,
            int[] sectionStarts
    ) {
        float y = viewportTop;
        int section = 0;
        for (int index = 0; index < rowTops.length; index++) {
            if (section < sectionStarts.length && index == sectionStarts[section]) {
                sectionBaselines[section] = y + labelBaselineOffset;
                y += sectionHeaderHeight;
                section++;
            }
            rowTops[index] = y;
            y += rowHeight;
        }
    }
}
