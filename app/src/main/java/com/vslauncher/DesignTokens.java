package com.vslauncher;

import android.graphics.Typeface;

/**
 * Central visual system for VS Launcher.
 *
 * The production UI stays strictly monochrome on absolute black, but no longer
 * treats every foreground element as equally important. Hierarchy comes from a
 * small neutral luminance scale plus typography, spacing, geometry and motion.
 * There are no chromatic theme colors, gradients, blur, shadows or wallpaper.
 */
final class DesignTokens {
    private DesignTokens() {}

    static final int BLACK = 0xFF000000;
    static final int FOCUS = 0xFFF0F0F0;
    static final int TEXT_PRIMARY = 0xFFDCDCDC;
    static final int TEXT_APP = 0xFFC2C2C2;
    static final int TEXT_SECONDARY = 0xFF909090;
    static final int TEXT_TERTIARY = 0xFF646464;
    static final int TEXT_DISABLED = 0xFF464646;
    static final int DIVIDER = 0xFF2C2C2C;
    static final int SURFACE = BLACK;

    static final Typeface DISPLAY = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface BODY = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface LABEL = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static final float PAGE_HORIZONTAL_DP = 24f;
    static final float ROW_HEIGHT_DP = 54f;
    static final float PROFILE_HEADER_LEAD_DP = 16f;
    static final float CORNER_DP = 12f;

    static final float DATE_SP = 13f;
    static final float TIME_SP = 62f;
    static final float META_SP = 13f;
    static final float LABEL_SP = 11f;
    static final float APP_SP = 19f;
    static final float TITLE_SP = 20f;
    static final float SEARCH_SP = 17f;
}
