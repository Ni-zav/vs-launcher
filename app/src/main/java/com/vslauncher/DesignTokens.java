package com.vslauncher;

import android.graphics.Color;
import android.graphics.Typeface;

/**
 * Central visual system for VS Launcher.
 *
 * The production UI is deliberately binary: #000000 and #FFFFFF only.
 * Hierarchy comes from typography, spacing, geometry, and inversion rather
 * than alpha/gray tones.
 */
final class DesignTokens {
    private DesignTokens() {}

    static final int BLACK = Color.BLACK;
    static final int WHITE = Color.WHITE;

    static final int TEXT_PRIMARY = WHITE;
    static final int TEXT_SECONDARY = WHITE;
    static final int TEXT_TERTIARY = WHITE;
    static final int DIVIDER = WHITE;
    static final int SURFACE = BLACK;
    static final int FOCUS = WHITE;

    static final Typeface DISPLAY = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface BODY = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface LABEL = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static final float PAGE_HORIZONTAL_DP = 24f;
    static final float ROW_HEIGHT_DP = 54f;
    static final float CORNER_DP = 12f;

    static final float DATE_SP = 13f;
    static final float TIME_SP = 62f;
    static final float META_SP = 13f;
    static final float LABEL_SP = 11f;
    static final float APP_SP = 19f;
    static final float TITLE_SP = 20f;
    static final float SEARCH_SP = 17f;
}
