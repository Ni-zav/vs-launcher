package com.vslauncher;

import android.graphics.Color;
import android.graphics.Typeface;

/**
 * Central visual system for VS Launcher.
 *
 * The launcher is intentionally monochrome: pure-black wallpaper, white content,
 * and alpha-driven hierarchy. Keeping visual values here avoids per-frame object
 * creation and prevents design drift across screens.
 */
final class DesignTokens {
    private DesignTokens() {}

    static final int BLACK = Color.BLACK;
    static final int WHITE = Color.WHITE;

    static final int TEXT_PRIMARY = Color.argb(245, 255, 255, 255);
    static final int TEXT_SECONDARY = Color.argb(184, 255, 255, 255);
    static final int TEXT_TERTIARY = Color.argb(117, 255, 255, 255);
    static final int DIVIDER = Color.argb(36, 255, 255, 255);
    static final int SURFACE = Color.argb(22, 255, 255, 255);
    static final int SURFACE_PRESSED = Color.argb(34, 255, 255, 255);
    static final int FOCUS = Color.argb(225, 255, 255, 255);

    static final Typeface DISPLAY = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface BODY = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface LABEL = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static final float PAGE_HORIZONTAL_DP = 24f;
    static final float TOP_DP = 32f;
    static final float ROW_HEIGHT_DP = 54f;
    static final float CORNER_DP = 12f;
    static final float HAIRLINE_DP = 1f;

    static final float DATE_SP = 14f;
    static final float TIME_SP = 52f;
    static final float META_SP = 13f;
    static final float LABEL_SP = 11f;
    static final float APP_SP = 19f;
    static final float TITLE_SP = 22f;
    static final float SEARCH_SP = 17f;
}
