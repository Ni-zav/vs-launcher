package com.vslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import java.util.Collections;
import java.util.List;

final class LauncherSurface extends View {
    static final int PAGE_SETTINGS = -1;
    static final int PAGE_HOME = 0;
    static final int PAGE_APPS = 1;

    static final int ACTION_HOME_POSITION = 1;
    static final int ACTION_HOME_DENSITY = 2;
    static final int ACTION_HOME_TEXT = 3;
    static final int ACTION_TOGGLE_TIME = 4;
    static final int ACTION_TOGGLE_DATE = 5;
    static final int ACTION_TOGGLE_WEATHER = 6;
    static final int ACTION_TOGGLE_BATTERY = 7;
    static final int ACTION_STATUS_LAYOUT = 8;
    static final int ACTION_CLOCK_FORMAT = 9;
    static final int ACTION_DATE_STYLE = 10;
    static final int ACTION_WEATHER_MODE = 11;
    static final int ACTION_BATTERY_MODE = 12;
    static final int ACTION_ANIMATION = 13;
    static final int ACTION_HAPTICS = 14;
    static final int ACTION_HIDDEN_APPS = 15;
    static final int ACTION_EXPORT_CONFIG = 16;
    static final int ACTION_IMPORT_CONFIG = 17;
    static final int ACTION_HELP = 18;
    static final int ACTION_HOME_ALIGNMENT = 19;
    static final int ACTION_APPS_TEXT = 20;

    private static final int SETTINGS_ROW_COUNT = 22;
    private static final int SETTINGS_SECTION_COUNT = 6;
    private static final int[] SETTINGS_SECTION_STARTS = {0, 5, 14, 17, 19, 21};
    private static final String[] SETTINGS_SECTION_LABELS = {
            "HOME", "STATUS", "GESTURES", "APPS", "DATA", "HELP"
    };

    interface Host {
        void onPageRequested(int page);
        void onOpenApp(AppEntry app);
        void onOpenHomeShortcut(AppEntry app, String shortcutId);
        void onSearchResultTapped(SearchResult result);
        void onHomeSlotLongPressed(int index);
        void onEmptyHomeSlotTapped(int index);
        void onAllAppsLongPressed(AppEntry app);
        void onProfileHeaderTapped(int profileKind, long profileSerial);
        void onHomeMaxChanged(int max);
        void onQuickAppPickerRequested();
        void onQuickLaunchRequested();
        void onAppsBrowseGestureStarted();
        void onAppsSearchRequested();
        void onSettingAction(int action);
        void onClockTapped();
        void onDateTapped();
        void onWeatherTapped();
        void onBatteryTapped();
        void onUndoRequested();
    }

    private static final String[] ALPHABET_LABELS = {
            "#","A","B","C","D","E","F","G","H","I","J","K","L","M",
            "N","O","P","Q","R","S","T","U","V","W","X","Y","Z"
    };

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

    private static final int A11Y_NONE = Integer.MIN_VALUE;
    private static final int A11Y_TIME = 1;
    private static final int A11Y_DATE = 2;
    private static final int A11Y_WEATHER = 3;
    private static final int A11Y_BATTERY = 4;
    private static final int A11Y_APPS_SEARCH = 5;
    private static final int A11Y_UNDO = 6;
    private static final int A11Y_HOME_BASE = 100;
    private static final int A11Y_APP_BASE = 1000;
    private static final int A11Y_SETTINGS_BASE = 2000;

    private final Host host;
    private final Paint datePaint =
            textPaint(DesignTokens.DATE_SP, DesignTokens.TEXT_SECONDARY, DesignTokens.LABEL);
    private final Paint timePaint =
            textPaint(DesignTokens.TIME_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.DISPLAY);
    private final Paint metaPaint =
            textPaint(DesignTokens.META_SP, DesignTokens.TEXT_SECONDARY, DesignTokens.BODY);
    private final Paint labelPaint =
            textPaint(DesignTokens.LABEL_SP, DesignTokens.TEXT_TERTIARY, DesignTokens.LABEL);
    private final Paint labelPressedPaint =
            textPaint(DesignTokens.LABEL_SP, DesignTokens.FOCUS, DesignTokens.LABEL);
    private final Paint homePaint =
            textPaint(DesignTokens.APP_SP, DesignTokens.TEXT_APP, DesignTokens.BODY);
    private final Paint homePressedPaint =
            textPaint(DesignTokens.APP_SP, DesignTokens.FOCUS, DesignTokens.BODY);
    private final Paint homeHintPaint =
            textPaint(DesignTokens.LABEL_SP, DesignTokens.TEXT_TERTIARY, DesignTokens.LABEL);
    private final Paint homeHintPressedPaint =
            textPaint(DesignTokens.LABEL_SP, DesignTokens.FOCUS, DesignTokens.LABEL);
    private final Paint alphabetPaint =
            textPaint(9f, DesignTokens.TEXT_TERTIARY, DesignTokens.LABEL);
    private final Paint alphabetUnavailablePaint =
            textPaint(9f, DesignTokens.TEXT_DISABLED, DesignTokens.LABEL);
    private final Paint appPaint =
            textPaint(DesignTokens.APP_SP, DesignTokens.TEXT_APP, DesignTokens.BODY);
    private final Paint appPrimaryPaint =
            textPaint(DesignTokens.APP_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.BODY);
    private final Paint appPressedPaint =
            textPaint(DesignTokens.APP_SP, DesignTokens.FOCUS, DesignTokens.BODY);
    private final Paint appDisabledPaint =
            textPaint(DesignTokens.APP_SP, DesignTokens.TEXT_DISABLED, DesignTokens.BODY);
    private final Paint metaPressedPaint =
            textPaint(DesignTokens.META_SP, DesignTokens.FOCUS, DesignTokens.BODY);
    private final Paint titlePaint =
            textPaint(DesignTokens.TITLE_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.BODY);
    private final Paint surfacePaint = fillPaint(DesignTokens.SURFACE);
    private final Paint statusFillPaint = fillPaint(DesignTokens.TEXT_SECONDARY);
    private final Paint batteryFillPaint = fillPaint(DesignTokens.TEXT_SECONDARY);
    private final Paint batteryTextPaint =
            textPaint(DesignTokens.META_SP, DesignTokens.TEXT_SECONDARY, DesignTokens.BODY);
    private final Paint batteryStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int[] accessibilityScreenLocation = new int[2];
    private final AccessibilityManager accessibilityManager;
    private LauncherAccessibilityProvider accessibilityProvider;
    private int accessibilityHoverId = A11Y_NONE;
    private int accessibilityFocusedId = A11Y_NONE;
    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;
    private final int longPressTimeout;

    private List<AppEntry> apps = Collections.emptyList();
    private List<AppEntry> filteredApps = Collections.emptyList();
    private List<SearchResult> searchResults = Collections.emptyList();
    private float[] searchMetaWidths = new float[0];
    private List<AppListItem> browseItems = Collections.emptyList();
    private float[] browseValueWidths = new float[0];
    private List<AppEntry> homeApps = Collections.emptyList();
    private List<String> homeLabels = Collections.emptyList();
    private List<String> homeShortcutIds = Collections.emptyList();

    private String dateText = "";
    private String timeText = "";
    private String weatherText = "Weather · tap to enable";
    private String batteryText = "—";
    private String quickAppLabel = "Not set";
    private String transientMessage = "";
    private float transientMessageWidth;
    private final String undoLabel = "UNDO";
    private float undoLabelWidth;
    private boolean transientUndoVisible;
    private LauncherUiConfig uiConfig = LauncherUiConfig.defaults();
    private float dateTextWidth;

    private int batteryLevel = -1;
    private boolean charging;
    private int maxHomeApps = 5;
    private int page = PAGE_HOME;
    private int topInset;
    private int bottomInset;

    // Recomputed only when size/insets/configuration change.
    private float contentTopPx;
    private float leftPx;
    private float rightPx;
    private float rowHeightPx;
    private float homeRowHeightPx;
    private float homeTextXPx;
    private float homeListStartPx;
    private float settingsMaxRowTopPx;
    private float settingsQuickRowTopPx;
    private float settingsViewportTopPx;
    private float settingsViewportBottomPx;
    private float appsViewportTopPx;
    private float appsViewportBottomPx;
    private int visibleHomeRowsCache;
    private float batteryTextWidth;
    private String homeCountText = "5";
    private float homeCountWidth;
    private String appCountText = "0";
    private float appCountWidth;

    // Draw-time constants cached with geometry/configuration.
    private float dividerThicknessPx;
    private float timeBaselinePx;
    private float timeDateFirstBaselinePx;
    private float dateBaselinePx;
    private float dateTopBaselinePx;
    private float statusBaselinePx;
    private float weatherDotXOffsetPx;
    private float weatherDotYOffsetPx;
    private float weatherOuterRadiusPx;
    private float weatherInnerRadiusPx;
    private float weatherTextOffsetPx;
    private float batteryWidthPx;
    private float batteryHeightPx;
    private float batteryRadiusPx;
    private float batteryTerminalGapPx;
    private float batteryTerminalWidthPx;
    private float batteryTerminalInsetPx;
    private float batteryInnerInsetPx;
    private float batteryInnerRadiusPx;
    private float batteryTextGapPx;
    private float batteryOnlyRightInsetPx;
    private float batteryTopOffsetPx;
    private float chargingXOffsetPx;
    private float chargingCrossRadiusPx;
    private float allAppsTitleBaselinePx;
    private float emptyAppsBaselinePx;
    private float settingsTitleBaselinePx;
    private float gestureThresholdPx;
    private float weatherTapTopPx;
    private float weatherTapBottomPx;
    private float settingsSectionHeaderHeightPx;
    private float settingsSectionLabelOffsetPx;
    private float profileHeaderLeadPx;
    private final float[] settingsRowTops = new float[SETTINGS_ROW_COUNT];
    private final float[] settingsSectionBaselines = new float[SETTINGS_SECTION_COUNT];
    private final String[] settingsValues = new String[SETTINGS_ROW_COUNT];
    private final float[] settingsValueWidths = new float[SETTINGS_ROW_COUNT];

    private float appScroll;
    private float settingsScroll;
    private int hiddenAppCount;
    private boolean searchActive;
    private boolean searchHasQuery;
    private final int[] alphabetFirstIndex = new int[27];
    private final float[] alphabetWidths = new float[27];
    private final float[] alphabetActiveWidths = new float[27];
    private boolean alphabetScrubbing;
    private int alphabetActiveIndex = -1;
    private float alphabetTouchLeftPx;
    private float alphabetRailX;
    private float alphabetStepPx;
    private float alphabetFirstBaselinePx;
    private float appsSearchPullThresholdPx;
    private float transientBaselinePx;
    private float transientTapTopPx;
    private float transientTapBottomPx;
    private float transientUndoLeftPx;
    private boolean appsSearchPullTriggered;
    private float downX;
    private float downY;
    private float lastY;
    private float dragOffsetX;
    private int gestureMode = GESTURE_NONE;
    private VelocityTracker velocityTracker;

    private int pressedHomeIndex = -1;
    private int pressedAppIndex = -1;
    private int pressedSettingsIndex = -1;
    private boolean longPressTriggered;

    private ValueAnimator pageAnimator;
    private ValueAnimator settleAnimator;
    private int transitionFrom = PAGE_HOME;
    private int transitionTo = PAGE_HOME;
    private float transitionOldOffset;
    private float transitionOldEnd;
    private boolean transitionRunning;

    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (gestureMode != GESTURE_NONE || transitionRunning) return;

            if (page == PAGE_HOME && pressedHomeIndex >= 0) {
                longPressTriggered = true;
                if (uiConfig.haptics) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                host.onHomeSlotLongPressed(pressedHomeIndex);
                return;
            }

            if (page == PAGE_APPS && pressedAppIndex >= 0) {
                AppEntry pressed = appAtVisibleIndex(pressedAppIndex);
                if (pressed == null) return;
                longPressTriggered = true;
                if (uiConfig.haptics) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                host.onAllAppsLongPressed(pressed);
            }
        }
    };

    LauncherSurface(Context context, Host host) {
        super(context);
        this.host = host;
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription("VS Launcher");
        setBackgroundColor(DesignTokens.BLACK);

        ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledTouchSlop();
        minFlingVelocity = config.getScaledMinimumFlingVelocity();
        maxFlingVelocity = config.getScaledMaximumFlingVelocity();
        longPressTimeout = ViewConfiguration.getLongPressTimeout();
        scroller = new OverScroller(context);
        accessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        batteryStrokePaint.setStyle(Paint.Style.STROKE);
        batteryStrokePaint.setStrokeWidth(dp(1.2f));
        batteryStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        batteryStrokePaint.setColor(DesignTokens.TEXT_SECONDARY);

        java.util.Arrays.fill(alphabetFirstIndex, -1);
        for (int i = 0; i < ALPHABET_LABELS.length; i++) {
            alphabetWidths[i] = alphabetPaint.measureText(ALPHABET_LABELS[i]);
            alphabetActiveWidths[i] = titlePaint.measureText(ALPHABET_LABELS[i]);
        }
        undoLabelWidth = metaPressedPaint.measureText(undoLabel);

        statusStrokePaint.setStyle(Paint.Style.STROKE);
        statusStrokePaint.setStrokeWidth(dp(1.2f));
        statusStrokePaint.setColor(DesignTokens.TEXT_SECONDARY);
        refreshSettingsValueCache();
    }

    private Paint textPaint(float sp, int color, android.graphics.Typeface typeface) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(sp(sp));
        paint.setColor(color);
        paint.setTypeface(typeface);
        return paint;
    }

    private static Paint fillPaint(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        return paint;
    }

    void setInsets(int top, int bottom) {
        if (topInset == top && bottomInset == bottom) return;
        topInset = top;
        bottomInset = bottom;
        recalculateGeometry();
        appScroll = clamp(appScroll, 0f, maxAppScroll());
        invalidate();
    }

    void setUiConfig(LauncherUiConfig config) {
        uiConfig = config == null ? LauncherUiConfig.defaults() : config;

        float homeTextPx = sp(uiConfig.homeTextSp());
        homePaint.setTextSize(homeTextPx);
        homePressedPaint.setTextSize(homeTextPx);

        float appTextPx = sp(uiConfig.appTextSp());
        appPaint.setTextSize(appTextPx);
        appPrimaryPaint.setTextSize(appTextPx);
        appPressedPaint.setTextSize(appTextPx);
        appDisabledPaint.setTextSize(appTextPx);

        Paint.Align homeAlign = Paint.Align.LEFT;
        if (LauncherPreferences.ALIGN_CENTER.equals(uiConfig.homeAlignment)) {
            homeAlign = Paint.Align.CENTER;
        } else if (LauncherPreferences.ALIGN_RIGHT.equals(uiConfig.homeAlignment)) {
            homeAlign = Paint.Align.RIGHT;
        }
        homePaint.setTextAlign(homeAlign);
        homePressedPaint.setTextAlign(homeAlign);
        homeHintPaint.setTextAlign(homeAlign);
        homeHintPressedPaint.setTextAlign(homeAlign);

        recalculateGeometry();
        refreshSettingsValueCache();
        invalidate();
    }

    void setClock(String date, String time) {
        dateText = date;
        timeText = time;
        dateTextWidth = datePaint.measureText(dateText);
        invalidateHome();
    }

    void setBattery(int level, boolean isCharging) {
        batteryLevel = level;
        charging = isCharging;
        batteryText = level < 0 ? "—" : level + "%";
        int batteryColor = level >= 0 && level <= 20 && !isCharging
                ? DesignTokens.TEXT_PRIMARY
                : DesignTokens.TEXT_SECONDARY;
        batteryTextPaint.setColor(batteryColor);
        batteryStrokePaint.setColor(batteryColor);
        batteryFillPaint.setColor(batteryColor);
        batteryTextWidth = batteryTextPaint.measureText(batteryText);
        invalidateHome();
    }

    void setWeather(String text) {
        weatherText = text;
        invalidateHome();
    }

    void setApps(List<AppEntry> all, List<AppEntry> filtered) {
        apps = all == null ? Collections.emptyList() : all;
        filteredApps = filtered == null ? apps : filtered;
        updateAppCountCache();
        refreshAlphabetIndex();
        appScroll = clamp(appScroll, 0f, maxAppScroll());
        invalidate();
    }

    void setSearchResults(List<SearchResult> results, boolean hasQuery) {
        searchResults = results == null ? Collections.emptyList() : results;
        searchHasQuery = hasQuery;
        searchMetaWidths = new float[searchResults.size()];
        for (int i = 0; i < searchResults.size(); i++) {
            String meta = searchResults.get(i).meta;
            searchMetaWidths[i] = meta.isEmpty() ? 0f : labelPaint.measureText(meta);
        }
        updateAppCountCache();
        appScroll = 0f;
        scroller.abortAnimation();
        if (page == PAGE_APPS) invalidate();
        notifyAccessibilityContentChanged();
    }

    void setBrowseItems(List<AppListItem> items) {
        browseItems = items == null ? Collections.emptyList() : items;
        browseValueWidths = new float[browseItems.size()];
        for (int i = 0; i < browseItems.size(); i++) {
            String value = browseItems.get(i).value;
            browseValueWidths[i] = value == null || value.isEmpty()
                    ? 0f
                    : metaPaint.measureText(value);
        }
        updateAppCountCache();
        refreshAlphabetIndex();
        appScroll = clamp(appScroll, 0f, maxAppScroll());
        if (page == PAGE_APPS) invalidate();
        notifyAccessibilityContentChanged();
    }

    void setFilteredApps(List<AppEntry> filtered) {
        filteredApps = filtered == null ? apps : filtered;
        updateAppCountCache();
        refreshAlphabetIndex();
        appScroll = 0f;
        scroller.abortAnimation();
        if (page == PAGE_APPS) invalidate();
    }

    private void updateAppCountCache() {
        int count;
        if (searchActive) {
            count = searchResults.size();
        } else {
            count = 0;
            for (AppListItem item : browseItems) if (item.isApp()) count++;
        }
        appCountText = Integer.toString(count);
        appCountWidth = labelPaint.measureText(appCountText);
    }

    private void refreshAlphabetIndex() {
        java.util.Arrays.fill(alphabetFirstIndex, -1);

        for (int index = 0; index < browseItems.size(); index++) {
            AppListItem item = browseItems.get(index);
            if (item.isApp()) cacheAlphabetRow(index, item.app);
        }
    }

    private void cacheAlphabetRow(int row, AppEntry app) {
        String normalized = app.normalizedLabel;
        if (normalized.isEmpty()) return;
        int bucket = LauncherLayout.alphabetBucket(normalized);
        if (alphabetFirstIndex[bucket] < 0) alphabetFirstIndex[bucket] = row;
    }

    void setSearchActive(boolean active) {
        if (searchActive == active) return;
        searchActive = active;
        recalculateGeometry();
        updateAppCountCache();
        refreshAlphabetIndex();
        appScroll = clamp(appScroll, 0f, maxAppScroll());
        if (page == PAGE_APPS) invalidate();
        notifyAccessibilityContentChanged();
    }

    void setTransientMessage(String message, boolean showUndo) {
        transientMessage = message == null ? "" : message;
        transientMessageWidth = transientMessage.isEmpty()
                ? 0f
                : metaPaint.measureText(transientMessage);
        transientUndoVisible = showUndo && !transientMessage.isEmpty();
        invalidate();
    }

    void setHiddenAppCount(int count) {
        hiddenAppCount = Math.max(0, count);
        refreshSettingsValueCache();
        if (page == PAGE_SETTINGS) invalidate();
    }

    void setHomeConfiguration(
            List<AppEntry> home,
            List<String> labels,
            List<String> shortcutIds,
            int max,
            String quickLabel
    ) {
        homeApps = home == null ? Collections.emptyList() : home;
        homeLabels = labels == null ? Collections.emptyList() : labels;
        homeShortcutIds = shortcutIds == null ? Collections.emptyList() : shortcutIds;
        maxHomeApps = Math.max(1, Math.min(8, max));
        quickAppLabel = quickLabel == null ? "Not set" : quickLabel;
        homeCountText = Integer.toString(maxHomeApps);
        homeCountWidth = titlePaint.measureText(homeCountText);
        recalculateGeometry();
        refreshSettingsValueCache();
        invalidate();
        notifyAccessibilityContentChanged();
    }

    int getPage() {
        return page;
    }

    void setPage(int target) {
        if (target < PAGE_SETTINGS || target > PAGE_APPS || target == page) {
            settleDrag();
            return;
        }

        cancelPendingLongPress();
        cancelPageAnimator();
        if (settleAnimator != null) {
            settleAnimator.cancel();
            settleAnimator = null;
        }

        transitionFrom = page;
        transitionTo = target;
        transitionOldOffset = dragOffsetX;
        transitionOldEnd = target > page ? -getWidth() : getWidth();
        dragOffsetX = 0f;
        gestureMode = GESTURE_NONE;
        page = target;
        appScroll = target == PAGE_APPS ? appScroll : 0f;

        float distance = Math.abs(transitionOldEnd - transitionOldOffset);
        float fraction = getWidth() <= 0 ? 1f : Math.min(1f, distance / getWidth());
        long baseDuration = uiConfig.pageDurationMs();
        if (baseDuration == 0L) {
            transitionRunning = false;
            transitionOldOffset = 0f;
            invalidate();
            notifyAccessibilityContentChanged();
            return;
        }
        long duration = Math.max(60L, Math.round(baseDuration * fraction));

        transitionRunning = true;
        pageAnimator = ValueAnimator.ofFloat(0f, 1f);
        pageAnimator.setDuration(duration);
        pageAnimator.setInterpolator(new DecelerateInterpolator());
        final float start = transitionOldOffset;
        pageAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            transitionOldOffset = start + (transitionOldEnd - start) * progress;
            postInvalidateOnAnimation();
        });
        pageAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                transitionRunning = false;
                transitionOldOffset = 0f;
                pageAnimator = null;
                invalidate();
                notifyAccessibilityContentChanged();
            }
        });
        pageAnimator.start();
    }

    private void cancelPageAnimator() {
        if (pageAnimator != null) {
            pageAnimator.cancel();
            pageAnimator = null;
        }
        transitionRunning = false;
        transitionOldOffset = 0f;
    }

    private void invalidateHome() {
        if (page == PAGE_HOME || transitionFrom == PAGE_HOME || transitionTo == PAGE_HOME) {
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(DesignTokens.BLACK);

        if (transitionRunning) {
            drawPage(canvas, transitionFrom, transitionOldOffset);
            drawPage(canvas, transitionTo, transitionOldOffset - transitionOldEnd);
            drawTransientMessage(canvas);
            return;
        }

        if (gestureMode == GESTURE_HORIZONTAL && dragOffsetX != 0f) {
            int neighbor = neighborForDrag(dragOffsetX);
            if (neighbor != Integer.MIN_VALUE) {
                drawPage(canvas, page, dragOffsetX);
                float neighborOffset =
                        dragOffsetX + (dragOffsetX > 0f ? -getWidth() : getWidth());
                drawPage(canvas, neighbor, neighborOffset);
            } else {
                drawPage(canvas, page, dragOffsetX * 0.18f);
            }
            drawTransientMessage(canvas);
            return;
        }

        drawPage(canvas, page, 0f);
        drawTransientMessage(canvas);
    }

    private void drawTransientMessage(Canvas canvas) {
        if (transientMessage.isEmpty()) return;
        canvas.drawText(transientMessage, leftPx, transientBaselinePx, metaPaint);
        if (transientUndoVisible) {
            canvas.drawText(
                    undoLabel,
                    transientUndoLeftPx,
                    transientBaselinePx,
                    metaPressedPaint
            );
        }
    }

    private void drawPage(Canvas canvas, int targetPage, float offsetX) {
        int save = canvas.save();
        canvas.translate(offsetX, 0f);
        if (targetPage == PAGE_HOME) {
            drawHome(canvas);
        } else if (targetPage == PAGE_APPS) {
            drawApps(canvas);
        } else {
            drawSettings(canvas);
        }
        canvas.restoreToCount(save);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalculateGeometry();
        appScroll = clamp(appScroll, 0f, maxAppScroll());
    }

    private void recalculateGeometry() {
        leftPx = dp(DesignTokens.PAGE_HORIZONTAL_DP);
        rightPx = Math.max(leftPx, getWidth() - leftPx);
        rowHeightPx = dp(uiConfig.rowHeightDp());
        homeRowHeightPx = LauncherLayout.homeRowHeight(
                rowHeightPx,
                sp(uiConfig.homeTextSp())
        );
        if (LauncherPreferences.ALIGN_CENTER.equals(uiConfig.homeAlignment)) {
            homeTextXPx = getWidth() * 0.5f;
        } else if (LauncherPreferences.ALIGN_RIGHT.equals(uiConfig.homeAlignment)) {
            homeTextXPx = rightPx;
        } else {
            homeTextXPx = leftPx;
        }
        contentTopPx = topInset + dp(28f);
        settingsMaxRowTopPx = contentTopPx + dp(72f);
        settingsQuickRowTopPx = contentTopPx + dp(198f);
        settingsViewportTopPx = contentTopPx + dp(48f);
        settingsViewportBottomPx = Math.max(settingsViewportTopPx, getHeight() - bottomInset - dp(20f));
        appsViewportTopPx = contentTopPx + dp(48f);
        float appsBottomReserveDp = searchActive ? 96f : 20f;
        appsViewportBottomPx = Math.max(
                appsViewportTopPx,
                getHeight() - bottomInset - dp(appsBottomReserveDp)
        );
        alphabetTouchLeftPx = Math.max(0f, getWidth() - dp(36f));
        alphabetRailX = Math.max(0f, getWidth() - dp(13f));
        alphabetStepPx = Math.max(
                1f,
                (appsViewportBottomPx - appsViewportTopPx) / ALPHABET_LABELS.length
        );
        alphabetFirstBaselinePx = appsViewportTopPx + alphabetStepPx * 0.72f;
        appsSearchPullThresholdPx = dp(34f);
        float transientReserveDp = searchActive ? 76f : 18f;
        transientBaselinePx = getHeight() - bottomInset - dp(transientReserveDp);
        transientTapTopPx = transientBaselinePx - dp(24f);
        transientTapBottomPx = transientBaselinePx + dp(12f);
        transientUndoLeftPx = rightPx - undoLabelWidth;

        dividerThicknessPx = dp(1f);
        timeBaselinePx = contentTopPx + dp(58f);
        timeDateFirstBaselinePx = contentTopPx + dp(82f);
        dateBaselinePx = contentTopPx + dp(88f);
        dateTopBaselinePx = contentTopPx + dp(22f);
        statusBaselinePx = contentTopPx + dp(126f);
        weatherDotXOffsetPx = dp(5f);
        weatherDotYOffsetPx = dp(4f);
        weatherOuterRadiusPx = dp(4.5f);
        weatherInnerRadiusPx = dp(1.5f);
        weatherTextOffsetPx = dp(18f);
        batteryWidthPx = dp(24f);
        batteryHeightPx = dp(10f);
        batteryRadiusPx = dp(3f);
        batteryTerminalGapPx = dp(2f);
        batteryTerminalWidthPx = dp(2f);
        batteryTerminalInsetPx = dp(2.5f);
        batteryInnerInsetPx = dp(2f);
        batteryInnerRadiusPx = dp(1.5f);
        batteryTextGapPx = dp(34f);
        batteryOnlyRightInsetPx = dp(28f);
        batteryTopOffsetPx = dp(11f);
        chargingXOffsetPx = dp(7f);
        chargingCrossRadiusPx = dp(2f);
        allAppsTitleBaselinePx = contentTopPx + sp(DesignTokens.LABEL_SP);
        emptyAppsBaselinePx = appsViewportTopPx + dp(24f);
        settingsTitleBaselinePx = contentTopPx + sp(DesignTokens.LABEL_SP);
        gestureThresholdPx = dp(64f);
        weatherTapTopPx = contentTopPx + dp(101f);
        weatherTapBottomPx = contentTopPx + dp(143f);
        settingsSectionHeaderHeightPx = dp(32f);
        settingsSectionLabelOffsetPx = dp(16f);
        profileHeaderLeadPx = dp(DesignTokens.PROFILE_HEADER_LEAD_DP);
        recalculateSettingsGeometry();

        float defaultHomeStart = contentTopPx + dp(164f);
        float homeEnd = Math.max(defaultHomeStart, getHeight() - bottomInset - dp(20f));
        float available = Math.max(0f, homeEnd - defaultHomeStart);
        float requestedHeight = maxHomeApps * homeRowHeightPx;

        if (requestedHeight < available
                && LauncherPreferences.POSITION_CENTER.equals(uiConfig.homePosition)) {
            homeListStartPx = defaultHomeStart + (available - requestedHeight) / 2f;
        } else if (requestedHeight < available
                && LauncherPreferences.POSITION_BOTTOM.equals(uiConfig.homePosition)) {
            homeListStartPx = homeEnd - requestedHeight;
        } else {
            homeListStartPx = defaultHomeStart;
        }

        visibleHomeRowsCache = LauncherLayout.visibleRows(
                maxHomeApps,
                homeRowHeightPx,
                homeListStartPx,
                homeEnd
        );
    }

    private void recalculateSettingsGeometry() {
        LauncherLayout.fillSectionedRows(
                settingsRowTops,
                settingsSectionBaselines,
                settingsViewportTopPx,
                rowHeightPx,
                settingsSectionHeaderHeightPx,
                settingsSectionLabelOffsetPx,
                SETTINGS_SECTION_STARTS
        );
    }

    private float contentTop() {
        return contentTopPx;
    }

    private float left() {
        return leftPx;
    }

    private void drawHome(Canvas canvas) {
        float x = left();
        float right = rightPx;
        if (LauncherPreferences.STATUS_DATE_FIRST.equals(uiConfig.statusLayout)) {
            if (uiConfig.showDate) canvas.drawText(dateText, x, dateTopBaselinePx, datePaint);
            if (uiConfig.showTime) canvas.drawText(timeText, x, timeDateFirstBaselinePx, timePaint);
        } else if (LauncherPreferences.STATUS_COMPACT.equals(uiConfig.statusLayout)) {
            if (uiConfig.showTime) canvas.drawText(timeText, x, timeBaselinePx, timePaint);
            if (uiConfig.showDate) {
                canvas.drawText(dateText, right - dateTextWidth, dateTopBaselinePx, datePaint);
            }
        } else {
            if (uiConfig.showTime) canvas.drawText(timeText, x, timeBaselinePx, timePaint);
            if (uiConfig.showDate) canvas.drawText(dateText, x, dateBaselinePx, datePaint);
        }

        float statusBaseline = statusBaselinePx;

        if (uiConfig.showWeather) {
            float weatherCenterX = x + weatherDotXOffsetPx;
            float weatherCenterY = statusBaseline - weatherDotYOffsetPx;
            canvas.drawCircle(weatherCenterX, weatherCenterY, weatherOuterRadiusPx, statusStrokePaint);
            canvas.drawCircle(weatherCenterX, weatherCenterY, weatherInnerRadiusPx, statusFillPaint);
            canvas.drawText(weatherText, x + weatherTextOffsetPx, statusBaseline, metaPaint);
        }

        if (uiConfig.showBattery) {
            boolean icon = !LauncherPreferences.BATTERY_PERCENT.equals(uiConfig.batteryMode);
            boolean percent = !LauncherPreferences.BATTERY_ICON.equals(uiConfig.batteryMode);
            boolean batterySharesStatusLine = uiConfig.showWeather;

            if (batterySharesStatusLine) {
                if (icon && percent) {
                    float batteryTextX = right - batteryTextWidth;
                    drawBattery(
                            canvas,
                            batteryTextX - batteryTextGapPx,
                            statusBaseline - batteryTopOffsetPx
                    );
                    canvas.drawText(batteryText, batteryTextX, statusBaseline, batteryTextPaint);
                } else if (icon) {
                    drawBattery(
                            canvas,
                            right - batteryOnlyRightInsetPx,
                            statusBaseline - batteryTopOffsetPx
                    );
                } else {
                    canvas.drawText(
                            batteryText,
                            right - batteryTextWidth,
                            statusBaseline,
                            batteryTextPaint
                    );
                }
            } else {
                float batteryX = x + chargingXOffsetPx;
                if (icon) {
                    drawBattery(canvas, batteryX, statusBaseline - batteryTopOffsetPx);
                }
                if (percent) {
                    float batteryTextX = icon ? batteryX + batteryTextGapPx : x;
                    canvas.drawText(batteryText, batteryTextX, statusBaseline, batteryTextPaint);
                }
            }
        }

        drawHomeRows(canvas, homeListStartPx, visibleHomeRowsCache);
    }

    private void drawHomeRows(Canvas canvas, float startY, int count) {
        if (count <= 0) return;

        float rowHeight = homeRowHeightPx;
        float baselineOffset = rowHeight * 0.62f;
        boolean emptyHintDrawn = false;

        for (int index = 0; index < count; index++) {
            float rowTop = startY + index * rowHeight;
            AppEntry app = index < homeApps.size() ? homeApps.get(index) : null;
            boolean pressed = index == pressedHomeIndex;

            Paint rowPaint = pressed ? homePressedPaint : homePaint;
            Paint hintPaint = pressed ? homeHintPressedPaint : homeHintPaint;

            String configuredLabel = index < homeLabels.size() ? homeLabels.get(index) : "";
            if (app != null) {
                String label = configuredLabel.isEmpty() ? app.label : configuredLabel;
                canvas.drawText(label, homeTextXPx, rowTop + baselineOffset, rowPaint);
            } else if (!configuredLabel.isEmpty()) {
                canvas.drawText(configuredLabel, homeTextXPx, rowTop + baselineOffset, hintPaint);
            } else if (!emptyHintDrawn) {
                canvas.drawText("+ ADD APP", homeTextXPx, rowTop + baselineOffset, hintPaint);
                emptyHintDrawn = true;
            }
        }
    }

    private void drawBattery(Canvas canvas, float x, float y) {
        rect.set(x, y, x + batteryWidthPx, y + batteryHeightPx);
        canvas.drawRoundRect(rect, batteryRadiusPx, batteryRadiusPx, batteryStrokePaint);

        rect.set(
                x + batteryWidthPx + batteryTerminalGapPx,
                y + batteryTerminalInsetPx,
                x + batteryWidthPx + batteryTerminalGapPx + batteryTerminalWidthPx,
                y + batteryHeightPx - batteryTerminalInsetPx
        );
        canvas.drawRoundRect(rect, dividerThicknessPx, dividerThicknessPx, batteryFillPaint);

        if (batteryLevel >= 0) {
            float innerWidth = batteryWidthPx - batteryInnerInsetPx * 2f;
            float fill = innerWidth * clamp(batteryLevel / 100f, 0f, 1f);
            if (fill > 0f) {
                rect.set(
                        x + batteryInnerInsetPx,
                        y + batteryInnerInsetPx,
                        x + batteryInnerInsetPx + fill,
                        y + batteryHeightPx - batteryInnerInsetPx
                );
                canvas.drawRoundRect(
                        rect,
                        batteryInnerRadiusPx,
                        batteryInnerRadiusPx,
                        batteryFillPaint
                );
            }
        }

        if (charging) {
            float cx = x - chargingXOffsetPx;
            float cy = y + batteryHeightPx / 2f;
            canvas.drawLine(
                    cx - chargingCrossRadiusPx,
                    cy,
                    cx + chargingCrossRadiusPx,
                    cy,
                    statusStrokePaint
            );
            canvas.drawLine(
                    cx,
                    cy - chargingCrossRadiusPx,
                    cx,
                    cy + chargingCrossRadiusPx,
                    statusStrokePaint
            );
        }
    }

    private void drawApps(Canvas canvas) {
        float x = left();
        canvas.drawText(searchActive ? "SEARCH" : "APPS", x, allAppsTitleBaselinePx, labelPaint);
        canvas.drawText(appCountText, rightPx - appCountWidth, allAppsTitleBaselinePx, labelPaint);

        float listStart = appsViewportTopPx;
        float listBottom = appsViewportBottomPx;

        if (searchActive) {
            if (searchResults.isEmpty()) {
                canvas.drawText("No matches", x, emptyAppsBaselinePx, metaPaint);
                return;
            }
            drawSearchRows(
                    canvas,
                    x,
                    listStart - appScroll,
                    listStart,
                    listBottom
            );
            return;
        }

        if (browseItems.isEmpty()) {
            canvas.drawText("No apps", x, emptyAppsBaselinePx, metaPaint);
            return;
        }

        drawBrowseRows(canvas, x, listStart - appScroll, listStart, listBottom);
        drawAlphabetRail(canvas);
    }

    private void drawAlphabetRail(Canvas canvas) {
        for (int i = 0; i < ALPHABET_LABELS.length; i++) {
            float baseline = alphabetFirstBaselinePx + i * alphabetStepPx;
            canvas.drawText(
                    ALPHABET_LABELS[i],
                    alphabetRailX - alphabetWidths[i] * 0.5f,
                    baseline,
                    alphabetFirstIndex[i] >= 0 ? alphabetPaint : alphabetUnavailablePaint
            );
        }

        if (alphabetScrubbing && alphabetActiveIndex >= 0) {
            String active = ALPHABET_LABELS[alphabetActiveIndex];
            float baseline = alphabetFirstBaselinePx + alphabetActiveIndex * alphabetStepPx;
            canvas.drawText(
                    active,
                    alphabetRailX - alphabetActiveWidths[alphabetActiveIndex] * 0.5f,
                    baseline,
                    titlePaint
            );
        }
    }

    private void drawSearchRows(
            Canvas canvas,
            float x,
            float startY,
            float clipTop,
            float clipBottom
    ) {
        int count = searchResults.size();
        int first = LauncherLayout.firstVisibleIndex(clipTop, startY, rowHeightPx, count);
        int last = LauncherLayout.lastVisibleExclusive(clipBottom, startY, rowHeightPx, count);
        if (last <= first) return;

        int save = canvas.save();
        canvas.clipRect(x, clipTop, getWidth() - x, clipBottom);

        float baselineOffset = rowHeightPx * 0.62f;
        float right = getWidth() - x;
        for (int i = first; i < last; i++) {
            float rowTop = startY + i * rowHeightPx;
            SearchResult result = searchResults.get(i);
            boolean pressed = i == pressedAppIndex;
            Paint rowPaint = pressed
                    ? appPressedPaint
                    : searchHasQuery && i == 0 ? appPrimaryPaint : appPaint;

            canvas.drawText(result.label, x, rowTop + baselineOffset, rowPaint);
            if (!result.meta.isEmpty()) {
                canvas.drawText(
                        result.meta,
                        right - searchMetaWidths[i],
                        rowTop + baselineOffset,
                        pressed ? metaPressedPaint : labelPaint
                );
            }
        }

        canvas.restoreToCount(save);
    }

    private void drawAppRows(
            Canvas canvas,
            List<AppEntry> source,
            float x,
            float startY,
            int count,
            float clipTop,
            float clipBottom
    ) {
        float row = rowHeightPx;
        int first = LauncherLayout.firstVisibleIndex(clipTop, startY, row, count);
        int last = LauncherLayout.lastVisibleExclusive(clipBottom, startY, row, count);
        if (last <= first) return;

        int save = canvas.save();
        canvas.clipRect(x, clipTop, getWidth() - x, clipBottom);

        float baselineOffset = rowHeightPx * 0.62f;
        float right = getWidth() - x;
        for (int i = first; i < last; i++) {
            float rowTop = startY + i * row;
            boolean pressed = i == pressedAppIndex;
            Paint rowPaint;
            if (pressed) {
                rowPaint = appPressedPaint;
            } else if (searchActive && searchHasQuery && i == 0) {
                rowPaint = appPrimaryPaint;
            } else {
                rowPaint = appPaint;
            }
            canvas.drawText(source.get(i).label, x, rowTop + baselineOffset, rowPaint);
        }
        canvas.restoreToCount(save);
    }

    private void drawBrowseRows(
            Canvas canvas,
            float x,
            float startY,
            float clipTop,
            float clipBottom
    ) {
        int count = browseItems.size();
        int first = LauncherLayout.firstVisibleIndex(clipTop, startY, rowHeightPx, count);
        int last = LauncherLayout.lastVisibleExclusive(clipBottom, startY, rowHeightPx, count);
        if (last <= first) return;

        int save = canvas.save();
        canvas.clipRect(x, clipTop, getWidth() - x, clipBottom);

        float baselineOffset = rowHeightPx * 0.62f;
        float right = getWidth() - x;
        for (int i = first; i < last; i++) {
            float rowTop = startY + i * rowHeightPx;
            AppListItem item = browseItems.get(i);
            boolean pressed = i == pressedAppIndex;

            if (item.isApp()) {
                Paint rowPaint;
                if (pressed) {
                    rowPaint = appPressedPaint;
                } else if (alphabetScrubbing && alphabetActiveIndex >= 0) {
                    int bucket = LauncherLayout.alphabetBucket(item.app.normalizedLabel);
                    rowPaint = bucket == alphabetActiveIndex
                            ? appPrimaryPaint
                            : appDisabledPaint;
                } else {
                    rowPaint = appPaint;
                }
                canvas.drawText(item.app.label, x, rowTop + baselineOffset, rowPaint);
            } else {
                float profileBaseline = rowTop + baselineOffset + profileHeaderLeadPx;
                canvas.drawText(
                        item.label,
                        x,
                        profileBaseline,
                        pressed ? labelPressedPaint : labelPaint
                );
                if (!item.value.isEmpty()) {
                    canvas.drawText(
                            item.value,
                            right - browseValueWidths[i],
                            profileBaseline,
                            pressed ? metaPressedPaint : metaPaint
                    );
                }
            }
        }

        canvas.restoreToCount(save);
    }

    private void drawSettings(Canvas canvas) {
        float x = leftPx;
        float right = rightPx;

        canvas.drawText("SETTINGS", x, settingsTitleBaselinePx, labelPaint);

        int save = canvas.save();
        canvas.clipRect(x, settingsViewportTopPx, right, settingsViewportBottomPx);

        for (int section = 0; section < SETTINGS_SECTION_COUNT; section++) {
            float baseline = settingsSectionBaselines[section] - settingsScroll;
            if (baseline >= settingsViewportTopPx - settingsSectionHeaderHeightPx
                    && baseline <= settingsViewportBottomPx) {
                canvas.drawText(SETTINGS_SECTION_LABELS[section], x, baseline, labelPaint);
            }
        }

        for (int index = 0; index < SETTINGS_ROW_COUNT; index++) {
            float y = settingsRowTops[index] - settingsScroll;
            if (y + rowHeightPx < settingsViewportTopPx || y > settingsViewportBottomPx) continue;
            drawSettingsRow(canvas, index, y, x, right);
        }

        canvas.restoreToCount(save);
    }

    private void drawSettingsRow(
            Canvas canvas,
            int index,
            float y,
            float x,
            float right
    ) {
        float baseline = y + rowHeightPx * 0.58f;
        String label = settingsLabel(index);
        String value = settingsValues[index];
        boolean pressed = index == pressedSettingsIndex;

        canvas.drawText(label, x, baseline, pressed ? appPressedPaint : appPaint);
        if (value != null && !value.isEmpty()) {
            canvas.drawText(
                    value,
                    right - settingsValueWidths[index],
                    baseline,
                    pressed ? metaPressedPaint : metaPaint
            );
        }

    }

    private String settingsLabel(int index) {
        switch (index) {
            case 0: return "Visible apps";
            case 1: return "Home position";
            case 2: return "Alignment";
            case 3: return "Density";
            case 4: return "Home text";
            case 5: return "Time";
            case 6: return "Date";
            case 7: return "Weather";
            case 8: return "Battery";
            case 9: return "Status layout";
            case 10: return "Time format";
            case 11: return "Date style";
            case 12: return "Weather detail";
            case 13: return "Battery detail";
            case 14: return "Swipe up";
            case 15: return "Animation";
            case 16: return "Haptics";
            case 17: return "Apps text";
            case 18: return "Hidden apps";
            case 19: return "Export config";
            case 20: return "Import config";
            case 21: return "How to use";
            default: return "";
        }
    }

    private void refreshSettingsValueCache() {
        for (int index = 0; index < SETTINGS_ROW_COUNT; index++) {
            String value;
            switch (index) {
                case 0: value = Integer.toString(maxHomeApps); break;
                case 1: value = titleCase(uiConfig.homePosition); break;
                case 2: value = titleCase(uiConfig.homeAlignment); break;
                case 3: value = titleCase(uiConfig.density); break;
                case 4: value = titleCase(uiConfig.textSize); break;
                case 5: value = onOff(uiConfig.showTime); break;
                case 6: value = onOff(uiConfig.showDate); break;
                case 7: value = onOff(uiConfig.showWeather); break;
                case 8: value = onOff(uiConfig.showBattery); break;
                case 9:
                    if (LauncherPreferences.STATUS_DATE_FIRST.equals(uiConfig.statusLayout)) value = "Date first";
                    else if (LauncherPreferences.STATUS_COMPACT.equals(uiConfig.statusLayout)) value = "Compact";
                    else value = "Time first";
                    break;
                case 10:
                    if (LauncherPreferences.CLOCK_12.equals(uiConfig.clockFormat)) value = "12h";
                    else if (LauncherPreferences.CLOCK_24.equals(uiConfig.clockFormat)) value = "24h";
                    else value = "System";
                    break;
                case 11: value = titleCase(uiConfig.dateStyle); break;
                case 12:
                    if (LauncherPreferences.WEATHER_TEMP.equals(uiConfig.weatherMode)) value = "Temperature";
                    else if (LauncherPreferences.WEATHER_CONDITION.equals(uiConfig.weatherMode)) value = "Condition";
                    else value = "Both";
                    break;
                case 13: value = titleCase(uiConfig.batteryMode); break;
                case 14: value = quickAppLabel; break;
                case 15: value = titleCase(uiConfig.animationSpeed); break;
                case 16: value = onOff(uiConfig.haptics); break;
                case 17: value = titleCase(uiConfig.appsTextSize); break;
                case 18: value = hiddenAppCount == 0 ? "None" : Integer.toString(hiddenAppCount); break;
                case 21: value = "Guide"; break;
                default: value = ""; break;
            }
            settingsValues[index] = value;
            settingsValueWidths[index] = value.isEmpty() ? 0f : metaPaint.measureText(value);
        }
    }

    private float settingsRowTop(int index) {
        return settingsRowTops[index];
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String titleCase(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (transitionRunning && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            cancelPageAnimator();
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (settleAnimator != null) {
                    settleAnimator.cancel();
                    settleAnimator = null;
                }
                if (!scroller.isFinished()) scroller.abortAnimation();

                recycleVelocityTracker();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);

                downX = event.getX();
                downY = event.getY();
                lastY = downY;
                dragOffsetX = 0f;
                gestureMode = GESTURE_NONE;
                longPressTriggered = false;
                appsSearchPullTriggered = false;

                if (page == PAGE_APPS
                        && !searchActive
                        && downX >= alphabetTouchLeftPx
                        && downY >= appsViewportTopPx
                        && downY < appsViewportBottomPx) {
                    alphabetScrubbing = true;
                    host.onAppsBrowseGestureStarted();
                    updateAlphabetScrub(downY);
                    return true;
                }

                pressedHomeIndex = page == PAGE_HOME
                        ? homeIndexAt(downX, downY)
                        : -1;
                pressedAppIndex = page == PAGE_APPS
                        ? allAppsIndexAt(downX, downY)
                        : -1;
                pressedSettingsIndex = page == PAGE_SETTINGS
                        ? settingsIndexAt(downX, downY)
                        : -1;
                if (pressedHomeIndex >= 0
                        || (pressedAppIndex >= 0 && appAtVisibleIndex(pressedAppIndex) != null)) {
                    postDelayed(longPressRunnable, longPressTimeout);
                }
                if (pressedHomeIndex >= 0 || pressedAppIndex >= 0 || pressedSettingsIndex >= 0) {
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (appsSearchPullTriggered) return true;
                if (alphabetScrubbing) {
                    updateAlphabetScrub(event.getY());
                    return true;
                }
                if (velocityTracker != null) velocityTracker.addMovement(event);

                float totalDx = event.getX() - downX;
                float totalDy = event.getY() - downY;
                if (Math.max(Math.abs(totalDx), Math.abs(totalDy)) > touchSlop) {
                    cancelPendingLongPress();
                }

                if (gestureMode == GESTURE_NONE
                        && Math.max(Math.abs(totalDx), Math.abs(totalDy)) > touchSlop) {
                    gestureMode = Math.abs(totalDx) > Math.abs(totalDy)
                            ? GESTURE_HORIZONTAL
                            : GESTURE_VERTICAL;
                    if (gestureMode == GESTURE_VERTICAL && page == PAGE_APPS) {
                        host.onAppsBrowseGestureStarted();
                    }
                }

                if (gestureMode == GESTURE_HORIZONTAL) {
                    dragOffsetX = totalDx;
                    if (neighborForDrag(dragOffsetX) == Integer.MIN_VALUE) {
                        dragOffsetX *= 0.22f;
                    }
                    postInvalidateOnAnimation();
                } else if (gestureMode == GESTURE_VERTICAL && page == PAGE_APPS) {
                    if (!searchActive
                            && appScroll <= 0f
                            && totalDy >= appsSearchPullThresholdPx) {
                        appsSearchPullTriggered = true;
                        host.onAppsSearchRequested();
                        return true;
                    }

                    float dy = event.getY() - lastY;
                    appScroll = clamp(appScroll - dy, 0f, maxAppScroll());
                    lastY = event.getY();
                    postInvalidateOnAnimation();
                } else if (gestureMode == GESTURE_VERTICAL && page == PAGE_SETTINGS) {
                    float dy = event.getY() - lastY;
                    settingsScroll = clamp(settingsScroll - dy, 0f, maxSettingsScroll());
                    lastY = event.getY();
                    postInvalidateOnAnimation();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (appsSearchPullTriggered) {
                    appsSearchPullTriggered = false;
                    recycleVelocityTracker();
                    gestureMode = GESTURE_NONE;
                    return true;
                }
                if (alphabetScrubbing) {
                    alphabetScrubbing = false;
                    alphabetActiveIndex = -1;
                    invalidate();
                    recycleVelocityTracker();
                    return true;
                }
                cancelPendingLongPress();

                if (velocityTracker != null) velocityTracker.addMovement(event);
                float velocityX = 0f;
                float velocityY = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    velocityX = velocityTracker.getXVelocity();
                    velocityY = velocityTracker.getYVelocity();
                }

                if (longPressTriggered) {
                    // The long press already opened its picker; never also launch the app.
                } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    settleDrag();
                } else if (gestureMode == GESTURE_HORIZONTAL) {
                    finishHorizontalGesture(velocityX);
                } else if (gestureMode == GESTURE_VERTICAL) {
                    finishVerticalGesture(event, velocityY);
                } else {
                    performClick();
                    handleTap(event.getX(), event.getY());
                }

                recycleVelocityTracker();
                gestureMode = GESTURE_NONE;
                pressedHomeIndex = -1;
                pressedAppIndex = -1;
                pressedSettingsIndex = -1;
                return true;

            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void finishHorizontalGesture(float velocityX) {
        int target = neighborForDrag(dragOffsetX);
        boolean distanceCommit = Math.abs(dragOffsetX) >= getWidth() * 0.18f;
        boolean velocityCommit = Math.abs(velocityX) >= minFlingVelocity
                && Math.signum(velocityX) == Math.signum(dragOffsetX);

        if (target != Integer.MIN_VALUE && (distanceCommit || velocityCommit)) {
            host.onPageRequested(target);
        } else {
            settleDrag();
        }
    }

    private void settleDrag() {
        if (dragOffsetX == 0f) return;
        if (settleAnimator != null) settleAnimator.cancel();

        long duration = uiConfig.settleDurationMs();
        if (duration == 0L) {
            dragOffsetX = 0f;
            invalidate();
            return;
        }

        float start = dragOffsetX;
        settleAnimator = ValueAnimator.ofFloat(start, 0f);
        settleAnimator.setDuration(duration);
        settleAnimator.setInterpolator(new DecelerateInterpolator());
        settleAnimator.addUpdateListener(animation -> {
            dragOffsetX = (float) animation.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        settleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                dragOffsetX = 0f;
                settleAnimator = null;
                invalidate();
            }
        });
        settleAnimator.start();
    }

    private void finishVerticalGesture(MotionEvent event, float velocityY) {
        if (page == PAGE_APPS || page == PAGE_SETTINGS) {
            if (Math.abs(velocityY) >= minFlingVelocity) {
                int current = page == PAGE_APPS
                        ? Math.round(appScroll)
                        : Math.round(settingsScroll);
                int max = page == PAGE_APPS
                        ? Math.round(maxAppScroll())
                        : Math.round(maxSettingsScroll());
                scroller.fling(
                        0,
                        current,
                        0,
                        -Math.round(velocityY),
                        0,
                        0,
                        0,
                        max
                );
                postInvalidateOnAnimation();
            }
            return;
        }

        if (page != PAGE_HOME) return;

        float dy = event.getY() - downY;
        if (Math.abs(dy) <= Math.abs(event.getX() - downX)) return;

        if (dy < -gestureThresholdPx) {
            host.onQuickLaunchRequested();
        }
    }

    private void updateAlphabetScrub(float y) {
        if (alphabetStepPx <= 0f) return;
        int requested = (int) ((y - appsViewportTopPx) / alphabetStepPx);
        requested = Math.max(0, Math.min(ALPHABET_LABELS.length - 1, requested));

        int actual = LauncherLayout.nearestAvailableBucket(alphabetFirstIndex, requested);
        alphabetActiveIndex = actual >= 0 ? actual : requested;
        if (actual >= 0) {
            int row = alphabetFirstIndex[actual];
            appScroll = clamp(row * rowHeightPx, 0f, maxAppScroll());
        }
        postInvalidateOnAnimation();
    }

    @Override public void computeScroll() {
        if (!scroller.computeScrollOffset()) return;
        if (page == PAGE_SETTINGS) settingsScroll = scroller.getCurrY();
        else appScroll = scroller.getCurrY();
        postInvalidateOnAnimation();
    }

    private int neighborForDrag(float dx) {
        if (dx > 0f) {
            if (page == PAGE_APPS) return PAGE_HOME;
            if (page == PAGE_HOME) return PAGE_SETTINGS;
        } else if (dx < 0f) {
            if (page == PAGE_SETTINGS) return PAGE_HOME;
            if (page == PAGE_HOME) return PAGE_APPS;
        }
        return Integer.MIN_VALUE;
    }

    private void handleTap(float x, float y) {
        float top = contentTop();

        if (transientUndoVisible
                && x >= transientUndoLeftPx - dp(18f)
                && x <= rightPx
                && y >= transientTapTopPx
                && y <= transientTapBottomPx) {
            host.onUndoRequested();
            return;
        }

        if (page == PAGE_HOME) {
            if (handleStatusTap(x, y)) return;

            int index = homeIndexAt(x, y);
            if (index >= 0 && index < homeApps.size()) {
                AppEntry app = homeApps.get(index);
                String shortcutId = index < homeShortcutIds.size()
                        ? homeShortcutIds.get(index)
                        : "";
                if (app != null && shortcutId != null && !shortcutId.isEmpty()) {
                    host.onOpenHomeShortcut(app, shortcutId);
                } else if (app != null) {
                    host.onOpenApp(app);
                } else if (isFirstVisibleEmptyHomeSlot(index)) {
                    host.onEmptyHomeSlotTapped(index);
                }
            }
            return;
        }

        if (page == PAGE_APPS) {
            if (!searchActive
                    && x >= leftPx
                    && x <= rightPx
                    && y >= contentTopPx
                    && y < appsViewportTopPx) {
                host.onAppsSearchRequested();
                return;
            }

            int index = allAppsIndexAt(x, y);
            if (index < 0) return;

            if (searchActive) {
                if (index < searchResults.size()) host.onSearchResultTapped(searchResults.get(index));
                return;
            }

            AppEntry app = appAtVisibleIndex(index);
            if (app != null) {
                host.onOpenApp(app);
                return;
            }

            AppListItem profile = profileItemAtVisibleIndex(index);
            if (profile != null) {
                host.onProfileHeaderTapped(profile.profileKind, profile.profileSerial);
            }
            return;
        }

        int settingsIndex = settingsIndexAt(x, y);
        if (settingsIndex >= 0) handleSettingsRow(settingsIndex);
    }

    private boolean handleStatusTap(float x, float y) {
        float top = contentTopPx;
        float midpoint = getWidth() * 0.5f;

        if (y >= weatherTapTopPx && y <= weatherTapBottomPx) {
            if (uiConfig.showWeather && uiConfig.showBattery) {
                if (x >= midpoint) host.onBatteryTapped();
                else host.onWeatherTapped();
                return true;
            }
            if (uiConfig.showBattery) {
                host.onBatteryTapped();
                return true;
            }
            if (uiConfig.showWeather) {
                host.onWeatherTapped();
                return true;
            }
        }

        if (LauncherPreferences.STATUS_DATE_FIRST.equals(uiConfig.statusLayout)) {
            if (uiConfig.showDate && y >= top && y < top + dp(38f)) {
                host.onDateTapped();
                return true;
            }
            if (uiConfig.showTime && y >= top + dp(38f) && y < top + dp(104f)) {
                host.onClockTapped();
                return true;
            }
        } else if (LauncherPreferences.STATUS_COMPACT.equals(uiConfig.statusLayout)) {
            if (uiConfig.showDate && x >= midpoint && y >= top && y < top + dp(42f)) {
                host.onDateTapped();
                return true;
            }
            if (uiConfig.showTime && x < midpoint && y >= top && y < top + dp(78f)) {
                host.onClockTapped();
                return true;
            }
        } else {
            if (uiConfig.showTime && y >= top && y < top + dp(70f)) {
                host.onClockTapped();
                return true;
            }
            if (uiConfig.showDate && y >= top + dp(70f) && y < top + dp(106f)) {
                host.onDateTapped();
                return true;
            }
        }
        return false;
    }

    private void handleSettingsRow(int index) {
        switch (index) {
            case 0:
                int next = maxHomeApps >= LauncherPreferences.MAX_HOME_APPS
                        ? LauncherPreferences.MIN_HOME_APPS
                        : maxHomeApps + 1;
                host.onHomeMaxChanged(next);
                break;
            case 1: host.onSettingAction(ACTION_HOME_POSITION); break;
            case 2: host.onSettingAction(ACTION_HOME_ALIGNMENT); break;
            case 3: host.onSettingAction(ACTION_HOME_DENSITY); break;
            case 4: host.onSettingAction(ACTION_HOME_TEXT); break;
            case 5: host.onSettingAction(ACTION_TOGGLE_TIME); break;
            case 6: host.onSettingAction(ACTION_TOGGLE_DATE); break;
            case 7: host.onSettingAction(ACTION_TOGGLE_WEATHER); break;
            case 8: host.onSettingAction(ACTION_TOGGLE_BATTERY); break;
            case 9: host.onSettingAction(ACTION_STATUS_LAYOUT); break;
            case 10: host.onSettingAction(ACTION_CLOCK_FORMAT); break;
            case 11: host.onSettingAction(ACTION_DATE_STYLE); break;
            case 12: host.onSettingAction(ACTION_WEATHER_MODE); break;
            case 13: host.onSettingAction(ACTION_BATTERY_MODE); break;
            case 14: host.onQuickAppPickerRequested(); break;
            case 15: host.onSettingAction(ACTION_ANIMATION); break;
            case 16: host.onSettingAction(ACTION_HAPTICS); break;
            case 17: host.onSettingAction(ACTION_APPS_TEXT); break;
            case 18: host.onSettingAction(ACTION_HIDDEN_APPS); break;
            case 19: host.onSettingAction(ACTION_EXPORT_CONFIG); break;
            case 20: host.onSettingAction(ACTION_IMPORT_CONFIG); break;
            case 21: host.onSettingAction(ACTION_HELP); break;
            default: break;
        }
    }

    private int settingsIndexAt(float x, float y) {
        if (x < leftPx || x > rightPx) return -1;
        if (y < settingsViewportTopPx || y >= settingsViewportBottomPx) return -1;

        float contentY = y + settingsScroll;
        for (int index = 0; index < SETTINGS_ROW_COUNT; index++) {
            float rowTop = settingsRowTops[index];
            if (contentY >= rowTop && contentY < rowTop + rowHeightPx) return index;
        }
        return -1;
    }

    private int allAppsIndexAt(float x, float y) {
        if (x < leftPx || x > rightPx) return -1;
        if (y < appsViewportTopPx || y >= appsViewportBottomPx || rowHeightPx <= 0f) return -1;

        float start = appsViewportTopPx - appScroll;
        int count = searchActive ? searchResults.size() : browseItems.size();
        return LauncherLayout.rowIndexAt(y, start, rowHeightPx, count);
    }

    private AppEntry appAtVisibleIndex(int index) {
        if (index < 0) return null;
        if (searchActive) {
            if (index >= searchResults.size()) return null;
            SearchResult result = searchResults.get(index);
            return result.isApp() ? result.app : null;
        }
        if (index >= browseItems.size()) return null;
        AppListItem item = browseItems.get(index);
        return item.isApp() ? item.app : null;
    }

    private AppListItem profileItemAtVisibleIndex(int index) {
        if (searchActive || index < 0 || index >= browseItems.size()) return null;
        AppListItem item = browseItems.get(index);
        return item.type == AppListItem.TYPE_PROFILE ? item : null;
    }

    private boolean isFirstVisibleEmptyHomeSlot(int candidate) {
        for (int i = 0; i < visibleHomeRowsCache; i++) {
            AppEntry app = i < homeApps.size() ? homeApps.get(i) : null;
            String label = i < homeLabels.size() ? homeLabels.get(i) : "";
            if (app == null && label.isEmpty()) return i == candidate;
        }
        return false;
    }

    private int homeIndexAt(float x, float y) {
        float start = homeListStartPx;
        float row = homeRowHeightPx;
        int visible = visibleHomeRowsCache;

        if (x < leftPx || x > rightPx) return -1;
        if (y < start || y >= start + visible * row) return -1;

        return LauncherLayout.rowIndexAt(y, start, row, visible);
    }

    private float homeListStart() {
        return homeListStartPx;
    }

    private int visibleHomeRows() {
        return visibleHomeRowsCache;
    }

    private float settingsMaxRowTop() {
        return settingsMaxRowTopPx;
    }

    private float settingsQuickRowTop() {
        return settingsQuickRowTopPx;
    }

    private float maxAppScroll() {
        float viewport = Math.max(0f, appsViewportBottomPx - appsViewportTopPx);
        int count = searchActive ? searchResults.size() : browseItems.size();
        return LauncherLayout.maxScroll(count, rowHeightPx, viewport);
    }

    private float maxSettingsScroll() {
        float contentBottom = settingsRowTops[SETTINGS_ROW_COUNT - 1] + rowHeightPx;
        float viewport = Math.max(0f, settingsViewportBottomPx - settingsViewportTopPx);
        return Math.max(0f, contentBottom - settingsViewportTopPx - viewport);
    }

    private void cancelPendingLongPress() {
        boolean hadPressed = pressedHomeIndex >= 0
                || pressedAppIndex >= 0
                || pressedSettingsIndex >= 0;
        removeCallbacks(longPressRunnable);
        pressedHomeIndex = -1;
        pressedAppIndex = -1;
        pressedSettingsIndex = -1;
        if (hadPressed) invalidate();
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (accessibilityProvider == null) {
            accessibilityProvider = new LauncherAccessibilityProvider();
        }
        return accessibilityProvider;
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        if (accessibilityManager == null
                || !accessibilityManager.isEnabled()
                || !accessibilityManager.isTouchExplorationEnabled()) {
            return super.dispatchHoverEvent(event);
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            updateAccessibilityHover(A11Y_NONE);
            return true;
        }
        if (action != MotionEvent.ACTION_HOVER_ENTER && action != MotionEvent.ACTION_HOVER_MOVE) {
            return super.dispatchHoverEvent(event);
        }

        int virtualId = accessibilityVirtualIdAt(event.getX(), event.getY());
        updateAccessibilityHover(virtualId);
        return virtualId != A11Y_NONE;
    }

    private void updateAccessibilityHover(int virtualId) {
        if (accessibilityHoverId == virtualId) return;
        int previous = accessibilityHoverId;
        accessibilityHoverId = virtualId;
        if (previous != A11Y_NONE) {
            sendAccessibilityEventForVirtualView(
                    previous,
                    AccessibilityEvent.TYPE_VIEW_HOVER_EXIT
            );
        }
        if (virtualId != A11Y_NONE) {
            sendAccessibilityEventForVirtualView(
                    virtualId,
                    AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
            );
        }
    }

    private void notifyAccessibilityContentChanged() {
        if (accessibilityManager != null && accessibilityManager.isEnabled()) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    private void sendAccessibilityEventForVirtualView(int virtualId, int eventType) {
        if (accessibilityManager == null || !accessibilityManager.isEnabled()) return;
        CharSequence label = accessibilityLabel(virtualId);
        if (label == null) return;

        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(getContext().getPackageName());
        event.setClassName("android.widget.Button");
        event.setSource(this, virtualId);
        event.getText().add(label);
        if (getParent() != null) getParent().requestSendAccessibilityEvent(this, event);
    }

    private int accessibilityVirtualIdAt(float x, float y) {
        if (transientUndoVisible
                && x >= transientUndoLeftPx - dp(18f)
                && x <= rightPx
                && y >= transientTapTopPx
                && y <= transientTapBottomPx) {
            return A11Y_UNDO;
        }

        if (page == PAGE_HOME) {
            int status = accessibilityStatusIdAt(x, y);
            if (status != A11Y_NONE) return status;

            int index = homeIndexAt(x, y);
            if (index >= 0 && homeAccessibilityLabel(index) != null) {
                return A11Y_HOME_BASE + index;
            }
            return A11Y_NONE;
        }

        if (page == PAGE_APPS) {
            if (!searchActive
                    && x >= leftPx
                    && x <= rightPx
                    && y >= contentTopPx
                    && y < appsViewportTopPx) {
                return A11Y_APPS_SEARCH;
            }
            int index = allAppsIndexAt(x, y);
            return index >= 0 ? A11Y_APP_BASE + index : A11Y_NONE;
        }

        int index = settingsIndexAt(x, y);
        return index >= 0 ? A11Y_SETTINGS_BASE + index : A11Y_NONE;
    }

    private int accessibilityStatusIdAt(float x, float y) {
        float midpoint = getWidth() * 0.5f;
        if (y >= weatherTapTopPx && y <= weatherTapBottomPx) {
            if (uiConfig.showBattery && x >= midpoint) return A11Y_BATTERY;
            if (uiConfig.showWeather) return A11Y_WEATHER;
        }

        float top = contentTopPx;
        if (LauncherPreferences.STATUS_DATE_FIRST.equals(uiConfig.statusLayout)) {
            if (uiConfig.showDate && y >= top && y < top + dp(38f)) return A11Y_DATE;
            if (uiConfig.showTime && y >= top + dp(38f) && y < top + dp(104f)) {
                return A11Y_TIME;
            }
        } else if (LauncherPreferences.STATUS_COMPACT.equals(uiConfig.statusLayout)) {
            if (uiConfig.showDate && x >= midpoint && y >= top && y < top + dp(42f)) {
                return A11Y_DATE;
            }
            if (uiConfig.showTime && x < midpoint && y >= top && y < top + dp(78f)) {
                return A11Y_TIME;
            }
        } else {
            if (uiConfig.showTime && y >= top && y < top + dp(70f)) return A11Y_TIME;
            if (uiConfig.showDate && y >= top + dp(70f) && y < top + dp(106f)) {
                return A11Y_DATE;
            }
        }
        return A11Y_NONE;
    }

    private CharSequence accessibilityLabel(int virtualId) {
        if (virtualId == A11Y_TIME) return "Time " + timeText + ". Open alarms";
        if (virtualId == A11Y_DATE) return "Date " + dateText + ". Open calendar";
        if (virtualId == A11Y_WEATHER) return weatherText + ". Refresh weather";
        if (virtualId == A11Y_BATTERY) {
            return "Battery " + batteryText + ". Open battery settings";
        }
        if (virtualId == A11Y_APPS_SEARCH) return "Search apps";
        if (virtualId == A11Y_UNDO) return "Undo " + transientMessage;

        if (virtualId >= A11Y_HOME_BASE && virtualId < A11Y_APP_BASE) {
            return homeAccessibilityLabel(virtualId - A11Y_HOME_BASE);
        }

        if (virtualId >= A11Y_APP_BASE && virtualId < A11Y_SETTINGS_BASE) {
            int index = virtualId - A11Y_APP_BASE;
            if (searchActive) {
                if (index < 0 || index >= searchResults.size()) return null;
                SearchResult result = searchResults.get(index);
                return result.meta.isEmpty()
                        ? result.label
                        : result.label + ", " + result.meta;
            }
            if (index < 0 || index >= browseItems.size()) return null;
            AppListItem item = browseItems.get(index);
            return item.value.isEmpty() ? item.label : item.label + ", " + item.value;
        }

        if (virtualId >= A11Y_SETTINGS_BASE) {
            int index = virtualId - A11Y_SETTINGS_BASE;
            if (index < 0 || index >= SETTINGS_ROW_COUNT) return null;
            String value = settingsValues[index];
            return value == null || value.isEmpty()
                    ? settingsLabel(index)
                    : settingsLabel(index) + ", " + value;
        }
        return null;
    }

    private CharSequence homeAccessibilityLabel(int index) {
        if (index < 0 || index >= visibleHomeRowsCache) return null;
        AppEntry app = index < homeApps.size() ? homeApps.get(index) : null;
        String configured = index < homeLabels.size() ? homeLabels.get(index) : "";
        if (app != null) {
            String label = configured.isEmpty() ? app.label : configured;
            String shortcut = index < homeShortcutIds.size() ? homeShortcutIds.get(index) : "";
            return shortcut == null || shortcut.isEmpty()
                    ? label + ", app"
                    : label + ", shortcut";
        }
        if (!configured.isEmpty()) return configured + ", unavailable";
        return isFirstVisibleEmptyHomeSlot(index) ? "Add app" : null;
    }

    private boolean homeAccessibilityLongClickable(int index) {
        if (index < 0 || index >= visibleHomeRowsCache) return false;
        AppEntry app = index < homeApps.size() ? homeApps.get(index) : null;
        String configured = index < homeLabels.size() ? homeLabels.get(index) : "";
        return app != null || !configured.isEmpty();
    }

    private boolean accessibilityBounds(int virtualId, Rect out) {
        if (virtualId == A11Y_UNDO) {
            if (!transientUndoVisible) return false;
            out.set(
                    Math.round(transientUndoLeftPx - dp(18f)),
                    Math.round(transientTapTopPx),
                    Math.round(rightPx),
                    Math.round(transientTapBottomPx)
            );
            return true;
        }

        if (virtualId == A11Y_APPS_SEARCH) {
            if (page != PAGE_APPS || searchActive) return false;
            out.set(
                    Math.round(leftPx),
                    Math.round(contentTopPx),
                    Math.round(rightPx),
                    Math.round(appsViewportTopPx)
            );
            return true;
        }

        if (virtualId == A11Y_TIME
                || virtualId == A11Y_DATE
                || virtualId == A11Y_WEATHER
                || virtualId == A11Y_BATTERY) {
            return accessibilityStatusBounds(virtualId, out);
        }

        if (virtualId >= A11Y_HOME_BASE && virtualId < A11Y_APP_BASE) {
            if (page != PAGE_HOME) return false;
            int index = virtualId - A11Y_HOME_BASE;
            if (homeAccessibilityLabel(index) == null) return false;
            float top = homeListStartPx + index * homeRowHeightPx;
            out.set(
                    Math.round(leftPx),
                    Math.round(top),
                    Math.round(rightPx),
                    Math.round(top + homeRowHeightPx)
            );
            return true;
        }

        if (virtualId >= A11Y_APP_BASE && virtualId < A11Y_SETTINGS_BASE) {
            if (page != PAGE_APPS) return false;
            int index = virtualId - A11Y_APP_BASE;
            int count = searchActive ? searchResults.size() : browseItems.size();
            if (index < 0 || index >= count) return false;
            float top = appsViewportTopPx - appScroll + index * rowHeightPx;
            if (top + rowHeightPx <= appsViewportTopPx || top >= appsViewportBottomPx) return false;
            out.set(
                    Math.round(leftPx),
                    Math.round(Math.max(top, appsViewportTopPx)),
                    Math.round(rightPx),
                    Math.round(Math.min(top + rowHeightPx, appsViewportBottomPx))
            );
            return true;
        }

        if (virtualId >= A11Y_SETTINGS_BASE) {
            if (page != PAGE_SETTINGS) return false;
            int index = virtualId - A11Y_SETTINGS_BASE;
            if (index < 0 || index >= SETTINGS_ROW_COUNT) return false;
            float top = settingsRowTops[index] - settingsScroll;
            if (top + rowHeightPx <= settingsViewportTopPx || top >= settingsViewportBottomPx) {
                return false;
            }
            out.set(
                    Math.round(leftPx),
                    Math.round(Math.max(top, settingsViewportTopPx)),
                    Math.round(rightPx),
                    Math.round(Math.min(top + rowHeightPx, settingsViewportBottomPx))
            );
            return true;
        }
        return false;
    }

    private boolean accessibilityStatusBounds(int virtualId, Rect out) {
        if (page != PAGE_HOME) return false;
        float midpoint = getWidth() * 0.5f;
        float top = contentTopPx;

        if (virtualId == A11Y_WEATHER) {
            if (!uiConfig.showWeather) return false;
            out.set(
                    Math.round(leftPx),
                    Math.round(weatherTapTopPx),
                    Math.round(uiConfig.showBattery ? midpoint : rightPx),
                    Math.round(weatherTapBottomPx)
            );
            return true;
        }
        if (virtualId == A11Y_BATTERY) {
            if (!uiConfig.showBattery) return false;
            out.set(
                    Math.round(uiConfig.showWeather ? midpoint : leftPx),
                    Math.round(weatherTapTopPx),
                    Math.round(rightPx),
                    Math.round(weatherTapBottomPx)
            );
            return true;
        }

        if (LauncherPreferences.STATUS_DATE_FIRST.equals(uiConfig.statusLayout)) {
            if (virtualId == A11Y_DATE && uiConfig.showDate) {
                out.set(
                        Math.round(leftPx),
                        Math.round(top),
                        Math.round(rightPx),
                        Math.round(top + dp(38f))
                );
                return true;
            }
            if (virtualId == A11Y_TIME && uiConfig.showTime) {
                out.set(
                        Math.round(leftPx),
                        Math.round(top + dp(38f)),
                        Math.round(rightPx),
                        Math.round(top + dp(104f))
                );
                return true;
            }
        } else if (LauncherPreferences.STATUS_COMPACT.equals(uiConfig.statusLayout)) {
            if (virtualId == A11Y_DATE && uiConfig.showDate) {
                out.set(
                        Math.round(midpoint),
                        Math.round(top),
                        Math.round(rightPx),
                        Math.round(top + dp(42f))
                );
                return true;
            }
            if (virtualId == A11Y_TIME && uiConfig.showTime) {
                out.set(
                        Math.round(leftPx),
                        Math.round(top),
                        Math.round(midpoint),
                        Math.round(top + dp(78f))
                );
                return true;
            }
        } else {
            if (virtualId == A11Y_TIME && uiConfig.showTime) {
                out.set(
                        Math.round(leftPx),
                        Math.round(top),
                        Math.round(rightPx),
                        Math.round(top + dp(70f))
                );
                return true;
            }
            if (virtualId == A11Y_DATE && uiConfig.showDate) {
                out.set(
                        Math.round(leftPx),
                        Math.round(top + dp(70f)),
                        Math.round(rightPx),
                        Math.round(top + dp(106f))
                );
                return true;
            }
        }
        return false;
    }

    private boolean performAccessibilityClick(int virtualId) {
        if (virtualId == A11Y_TIME) {
            host.onClockTapped();
            return true;
        }
        if (virtualId == A11Y_DATE) {
            host.onDateTapped();
            return true;
        }
        if (virtualId == A11Y_WEATHER) {
            host.onWeatherTapped();
            return true;
        }
        if (virtualId == A11Y_BATTERY) {
            host.onBatteryTapped();
            return true;
        }
        if (virtualId == A11Y_APPS_SEARCH) {
            host.onAppsSearchRequested();
            return true;
        }
        if (virtualId == A11Y_UNDO && transientUndoVisible) {
            host.onUndoRequested();
            return true;
        }

        if (virtualId >= A11Y_HOME_BASE && virtualId < A11Y_APP_BASE) {
            int index = virtualId - A11Y_HOME_BASE;
            if (index < 0 || index >= visibleHomeRowsCache) return false;
            AppEntry app = index < homeApps.size() ? homeApps.get(index) : null;
            String shortcutId = index < homeShortcutIds.size()
                    ? homeShortcutIds.get(index)
                    : "";
            if (app != null && shortcutId != null && !shortcutId.isEmpty()) {
                host.onOpenHomeShortcut(app, shortcutId);
                return true;
            }
            if (app != null) {
                host.onOpenApp(app);
                return true;
            }
            if (isFirstVisibleEmptyHomeSlot(index)) {
                host.onEmptyHomeSlotTapped(index);
                return true;
            }
            return false;
        }

        if (virtualId >= A11Y_APP_BASE && virtualId < A11Y_SETTINGS_BASE) {
            int index = virtualId - A11Y_APP_BASE;
            if (searchActive) {
                if (index < 0 || index >= searchResults.size()) return false;
                host.onSearchResultTapped(searchResults.get(index));
                return true;
            }
            AppEntry app = appAtVisibleIndex(index);
            if (app != null) {
                host.onOpenApp(app);
                return true;
            }
            AppListItem profile = profileItemAtVisibleIndex(index);
            if (profile != null) {
                host.onProfileHeaderTapped(profile.profileKind, profile.profileSerial);
                return true;
            }
            return false;
        }

        if (virtualId >= A11Y_SETTINGS_BASE) {
            int index = virtualId - A11Y_SETTINGS_BASE;
            if (index < 0 || index >= SETTINGS_ROW_COUNT) return false;
            handleSettingsRow(index);
            return true;
        }
        return false;
    }

    private boolean performAccessibilityLongClick(int virtualId) {
        if (virtualId >= A11Y_HOME_BASE && virtualId < A11Y_APP_BASE) {
            int index = virtualId - A11Y_HOME_BASE;
            if (!homeAccessibilityLongClickable(index)) return false;
            host.onHomeSlotLongPressed(index);
            return true;
        }

        if (virtualId >= A11Y_APP_BASE && virtualId < A11Y_SETTINGS_BASE) {
            int index = virtualId - A11Y_APP_BASE;
            AppEntry app = appAtVisibleIndex(index);
            if (app == null) return false;
            host.onAllAppsLongPressed(app);
            return true;
        }
        return false;
    }

    private boolean accessibilityScroll(boolean forward) {
        float old;
        float next;
        if (page == PAGE_APPS) {
            old = appScroll;
            float viewport = Math.max(1f, appsViewportBottomPx - appsViewportTopPx);
            next = clamp(
                    old + (forward ? viewport * 0.8f : -viewport * 0.8f),
                    0f,
                    maxAppScroll()
            );
            if (next == old) return false;
            scroller.abortAnimation();
            appScroll = next;
        } else if (page == PAGE_SETTINGS) {
            old = settingsScroll;
            float viewport = Math.max(1f, settingsViewportBottomPx - settingsViewportTopPx);
            next = clamp(
                    old + (forward ? viewport * 0.8f : -viewport * 0.8f),
                    0f,
                    maxSettingsScroll()
            );
            if (next == old) return false;
            scroller.abortAnimation();
            settingsScroll = next;
        } else {
            return false;
        }
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
        return true;
    }

    private final class LauncherAccessibilityProvider extends AccessibilityNodeProvider {
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            if (virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) {
                return createHostNode();
            }

            CharSequence label = accessibilityLabel(virtualViewId);
            if (label == null) return null;

            Rect bounds = new Rect();
            if (!accessibilityBounds(virtualViewId, bounds)) return null;

            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
            node.setPackageName(getContext().getPackageName());
            node.setClassName("android.widget.Button");
            node.setSource(LauncherSurface.this, virtualViewId);
            node.setParent(LauncherSurface.this);
            node.setText(label);
            node.setVisibleToUser(true);
            node.setEnabled(true);
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfo.ACTION_CLICK);

            boolean longClickable = false;
            if (virtualViewId >= A11Y_HOME_BASE && virtualViewId < A11Y_APP_BASE) {
                longClickable = homeAccessibilityLongClickable(
                        virtualViewId - A11Y_HOME_BASE
                );
            } else if (virtualViewId >= A11Y_APP_BASE
                    && virtualViewId < A11Y_SETTINGS_BASE) {
                longClickable = appAtVisibleIndex(virtualViewId - A11Y_APP_BASE) != null;
            }
            node.setLongClickable(longClickable);
            if (longClickable) node.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);

            node.setAccessibilityFocused(accessibilityFocusedId == virtualViewId);
            node.addAction(
                    accessibilityFocusedId == virtualViewId
                            ? AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                            : AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
            );

            node.setBoundsInParent(bounds);
            getLocationOnScreen(accessibilityScreenLocation);
            bounds.offset(accessibilityScreenLocation[0], accessibilityScreenLocation[1]);
            node.setBoundsInScreen(bounds);
            return node;
        }

        private AccessibilityNodeInfo createHostNode() {
            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(LauncherSurface.this);
            node.setPackageName(getContext().getPackageName());
            node.setClassName(LauncherSurface.class.getName());
            node.setSource(LauncherSurface.this);
            node.setContentDescription("VS Launcher");

            if (transientUndoVisible) node.addChild(LauncherSurface.this, A11Y_UNDO);

            if (page == PAGE_HOME) {
                if (uiConfig.showTime) node.addChild(LauncherSurface.this, A11Y_TIME);
                if (uiConfig.showDate) node.addChild(LauncherSurface.this, A11Y_DATE);
                if (uiConfig.showWeather) node.addChild(LauncherSurface.this, A11Y_WEATHER);
                if (uiConfig.showBattery) node.addChild(LauncherSurface.this, A11Y_BATTERY);
                for (int index = 0; index < visibleHomeRowsCache; index++) {
                    if (homeAccessibilityLabel(index) != null) {
                        node.addChild(LauncherSurface.this, A11Y_HOME_BASE + index);
                    }
                }
                return node;
            }

            if (page == PAGE_APPS) {
                if (!searchActive) node.addChild(LauncherSurface.this, A11Y_APPS_SEARCH);
                int count = searchActive ? searchResults.size() : browseItems.size();
                float start = appsViewportTopPx - appScroll;
                int first = LauncherLayout.firstVisibleIndex(
                        appsViewportTopPx,
                        start,
                        rowHeightPx,
                        count
                );
                int last = LauncherLayout.lastVisibleExclusive(
                        appsViewportBottomPx,
                        start,
                        rowHeightPx,
                        count
                );
                for (int index = first; index < last; index++) {
                    node.addChild(LauncherSurface.this, A11Y_APP_BASE + index);
                }
                if (maxAppScroll() > 0f) {
                    node.setScrollable(true);
                    node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                }
                return node;
            }

            for (int index = 0; index < SETTINGS_ROW_COUNT; index++) {
                float top = settingsRowTops[index] - settingsScroll;
                if (top + rowHeightPx <= settingsViewportTopPx
                        || top >= settingsViewportBottomPx) {
                    continue;
                }
                node.addChild(LauncherSurface.this, A11Y_SETTINGS_BASE + index);
            }
            if (maxSettingsScroll() > 0f) {
                node.setScrollable(true);
                node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }
            return node;
        }

        @Override public boolean performAction(
                int virtualViewId,
                int action,
                Bundle arguments
        ) {
            if (virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                    return accessibilityScroll(true);
                }
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    return accessibilityScroll(false);
                }
                return false;
            }

            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                if (accessibilityFocusedId == virtualViewId) return false;
                int previous = accessibilityFocusedId;
                accessibilityFocusedId = virtualViewId;
                if (previous != A11Y_NONE) {
                    sendAccessibilityEventForVirtualView(
                            previous,
                            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                    );
                }
                sendAccessibilityEventForVirtualView(
                        virtualViewId,
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                );
                invalidate();
                return true;
            }

            if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                if (accessibilityFocusedId != virtualViewId) return false;
                accessibilityFocusedId = A11Y_NONE;
                sendAccessibilityEventForVirtualView(
                        virtualViewId,
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                );
                invalidate();
                return true;
            }

            if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                boolean handled = performAccessibilityClick(virtualViewId);
                if (handled) {
                    sendAccessibilityEventForVirtualView(
                            virtualViewId,
                            AccessibilityEvent.TYPE_VIEW_CLICKED
                    );
                }
                return handled;
            }

            if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                boolean handled = performAccessibilityLongClick(virtualViewId);
                if (handled) {
                    sendAccessibilityEventForVirtualView(
                            virtualViewId,
                            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                    );
                }
                return handled;
            }
            return false;
        }
    }

    @Override protected void onDetachedFromWindow() {
        cancelPendingLongPress();
        cancelPageAnimator();
        if (settleAnimator != null) settleAnimator.cancel();
        recycleVelocityTracker();
        super.onDetachedFromWindow();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
