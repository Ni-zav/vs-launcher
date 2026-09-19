package com.vslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
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

    private static final int SETTINGS_ROW_COUNT = 19;

    interface Host {
        void onPageRequested(int page);
        void onOpenApp(AppEntry app);
        void onHomeSlotLongPressed(int index);
        void onHomeMaxChanged(int max);
        void onQuickAppPickerRequested();
        void onQuickLaunchRequested();
        void onSettingAction(int action);
        void onWeatherTapped();
    }

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

    private final Host host;
    private final Paint datePaint =
            textPaint(DesignTokens.DATE_SP, DesignTokens.TEXT_SECONDARY, DesignTokens.LABEL);
    private final Paint timePaint =
            textPaint(DesignTokens.TIME_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.DISPLAY);
    private final Paint metaPaint =
            textPaint(DesignTokens.META_SP, DesignTokens.TEXT_SECONDARY, DesignTokens.BODY);
    private final Paint labelPaint =
            textPaint(DesignTokens.LABEL_SP, DesignTokens.TEXT_TERTIARY, DesignTokens.LABEL);
    private final Paint appPaint =
            textPaint(DesignTokens.APP_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.BODY);
    private final Paint titlePaint =
            textPaint(DesignTokens.TITLE_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.BODY);
    private final Paint dividerPaint = fillPaint(DesignTokens.DIVIDER);
    private final Paint surfacePaint = fillPaint(DesignTokens.SURFACE);
    private final Paint primaryFillPaint = fillPaint(DesignTokens.TEXT_PRIMARY);
    private final Paint batteryStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;
    private final int longPressTimeout;

    private List<AppEntry> apps = Collections.emptyList();
    private List<AppEntry> filteredApps = Collections.emptyList();
    private List<AppEntry> homeApps = Collections.emptyList();
    private List<String> homeLabels = Collections.emptyList();

    private String dateText = "";
    private String timeText = "";
    private String weatherText = "Weather · tap to enable";
    private String batteryText = "—";
    private String quickAppLabel = "Not set";
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

    private float appScroll;
    private float settingsScroll;
    private int hiddenAppCount;
    private float downX;
    private float downY;
    private float lastY;
    private float dragOffsetX;
    private int gestureMode = GESTURE_NONE;
    private VelocityTracker velocityTracker;

    private int pressedHomeIndex = -1;
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
            if (pressedHomeIndex < 0
                    || page != PAGE_HOME
                    || gestureMode != GESTURE_NONE
                    || transitionRunning) {
                return;
            }

            longPressTriggered = true;
            if (uiConfig.haptics) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            host.onHomeSlotLongPressed(pressedHomeIndex);
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

        batteryStrokePaint.setStyle(Paint.Style.STROKE);
        batteryStrokePaint.setStrokeWidth(dp(1.2f));
        batteryStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        batteryStrokePaint.setColor(DesignTokens.TEXT_SECONDARY);

        statusStrokePaint.setStyle(Paint.Style.STROKE);
        statusStrokePaint.setStrokeWidth(dp(1.2f));
        statusStrokePaint.setColor(DesignTokens.TEXT_SECONDARY);
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
        appPaint.setTextSize(sp(uiConfig.appTextSp()));
        recalculateGeometry();
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
        batteryTextWidth = metaPaint.measureText(batteryText);
        invalidateHome();
    }

    void setWeather(String text) {
        weatherText = text;
        invalidateHome();
    }

    void setApps(List<AppEntry> all, List<AppEntry> filtered) {
        apps = all == null ? Collections.emptyList() : all;
        filteredApps = filtered == null ? apps : filtered;
        appScroll = clamp(appScroll, 0f, maxAppScroll());
        invalidate();
    }

    void setFilteredApps(List<AppEntry> filtered) {
        filteredApps = filtered == null ? apps : filtered;
        appScroll = 0f;
        scroller.abortAnimation();
        if (page == PAGE_APPS) invalidate();
    }

    void setHiddenAppCount(int count) {
        hiddenAppCount = Math.max(0, count);
        if (page == PAGE_SETTINGS) invalidate();
    }

    void setHomeConfiguration(
            List<AppEntry> home,
            List<String> labels,
            int max,
            String quickLabel
    ) {
        homeApps = home == null ? Collections.emptyList() : home;
        homeLabels = labels == null ? Collections.emptyList() : labels;
        maxHomeApps = Math.max(1, Math.min(8, max));
        quickAppLabel = quickLabel == null ? "Not set" : quickLabel;
        homeCountText = Integer.toString(maxHomeApps);
        homeCountWidth = titlePaint.measureText(homeCountText);
        recalculateGeometry();
        invalidate();
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
            return;
        }

        drawPage(canvas, page, 0f);
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
        contentTopPx = topInset + dp(28f);
        settingsMaxRowTopPx = contentTopPx + dp(72f);
        settingsQuickRowTopPx = contentTopPx + dp(198f);
        settingsViewportTopPx = contentTopPx + dp(42f);
        settingsViewportBottomPx = Math.max(settingsViewportTopPx, getHeight() - bottomInset - dp(20f));
        appsViewportTopPx = contentTopPx + dp(48f);
        appsViewportBottomPx = Math.max(appsViewportTopPx, getHeight() - bottomInset - dp(96f));

        float defaultHomeStart = contentTopPx + dp(174f);
        float homeEnd = Math.max(defaultHomeStart, getHeight() - bottomInset - dp(20f));
        float available = Math.max(0f, homeEnd - defaultHomeStart);
        float requestedHeight = maxHomeApps * rowHeightPx;

        if (requestedHeight < available
                && LauncherPreferences.POSITION_CENTER.equals(uiConfig.homePosition)) {
            homeListStartPx = defaultHomeStart + (available - requestedHeight) / 2f;
        } else if (requestedHeight < available
                && LauncherPreferences.POSITION_BOTTOM.equals(uiConfig.homePosition)) {
            homeListStartPx = homeEnd - requestedHeight;
        } else {
            homeListStartPx = defaultHomeStart;
        }

        float homeAvailable = homeEnd - homeListStartPx;
        int fit = rowHeightPx <= 0f ? 0 : Math.max(0, (int) Math.floor(homeAvailable / rowHeightPx));
        visibleHomeRowsCache = Math.min(maxHomeApps, fit);
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
        float top = contentTop();

        if (LauncherPreferences.STATUS_DATE_FIRST.equals(uiConfig.statusLayout)) {
            if (uiConfig.showDate) canvas.drawText(dateText, x, top + dp(22f), datePaint);
            if (uiConfig.showTime) canvas.drawText(timeText, x, top + dp(82f), timePaint);
        } else if (LauncherPreferences.STATUS_COMPACT.equals(uiConfig.statusLayout)) {
            if (uiConfig.showTime) canvas.drawText(timeText, x, top + dp(58f), timePaint);
            if (uiConfig.showDate) {
                canvas.drawText(dateText, right - dateTextWidth, top + dp(22f), datePaint);
            }
        } else {
            if (uiConfig.showTime) canvas.drawText(timeText, x, top + dp(58f), timePaint);
            if (uiConfig.showDate) canvas.drawText(dateText, x, top + dp(88f), datePaint);
        }

        float statusBaseline = top + dp(126f);

        if (uiConfig.showWeather) {
            float weatherCenterX = x + dp(5f);
            float weatherCenterY = statusBaseline - dp(4f);
            canvas.drawCircle(weatherCenterX, weatherCenterY, dp(4.5f), statusStrokePaint);
            canvas.drawCircle(weatherCenterX, weatherCenterY, dp(1.5f), primaryFillPaint);
            canvas.drawText(weatherText, x + dp(18f), statusBaseline, metaPaint);
        }

        if (uiConfig.showBattery) {
            boolean icon = !LauncherPreferences.BATTERY_PERCENT.equals(uiConfig.batteryMode);
            boolean percent = !LauncherPreferences.BATTERY_ICON.equals(uiConfig.batteryMode);

            if (icon && percent) {
                float batteryTextX = right - batteryTextWidth;
                drawBattery(canvas, batteryTextX - dp(34f), statusBaseline - dp(11f));
                canvas.drawText(batteryText, batteryTextX, statusBaseline, metaPaint);
            } else if (icon) {
                drawBattery(canvas, right - dp(28f), statusBaseline - dp(11f));
            } else {
                canvas.drawText(batteryText, right - batteryTextWidth, statusBaseline, metaPaint);
            }
        }

        float dividerY = top + dp(153f);
        canvas.drawRect(x, dividerY, right, dividerY + dp(1f), dividerPaint);

        drawHomeRows(canvas, homeListStartPx, visibleHomeRowsCache);
    }

    private void drawHomeRows(Canvas canvas, float startY, int count) {
        if (count <= 0) return;

        float x = left();
        float right = getWidth() - x;
        float rowHeight = rowHeightPx;
        float baselineOffset = rowHeight * 0.62f;

        for (int index = 0; index < count; index++) {
            float rowTop = startY + index * rowHeight;
            AppEntry app = index < homeApps.size() ? homeApps.get(index) : null;

            if (app != null) {
                String label = index < homeLabels.size() ? homeLabels.get(index) : app.label;
                canvas.drawText(label, x, rowTop + baselineOffset, appPaint);
            } else {
                canvas.drawText("Hold to choose app", x, rowTop + baselineOffset, metaPaint);
            }

            canvas.drawRect(
                    x,
                    rowTop + rowHeight - dp(1f),
                    right,
                    rowTop + rowHeight,
                    dividerPaint
            );
        }
    }

    private void drawBattery(Canvas canvas, float x, float y) {
        float width = dp(24f);
        float height = dp(10f);
        float radius = dp(3f);

        rect.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, radius, radius, batteryStrokePaint);

        rect.set(
                x + width + dp(2f),
                y + dp(2.5f),
                x + width + dp(4f),
                y + height - dp(2.5f)
        );
        canvas.drawRoundRect(rect, dp(1f), dp(1f), primaryFillPaint);

        if (batteryLevel >= 0) {
            float innerWidth = width - dp(4f);
            float fill = innerWidth * clamp(batteryLevel / 100f, 0f, 1f);
            if (fill > 0f) {
                rect.set(
                        x + dp(2f),
                        y + dp(2f),
                        x + dp(2f) + fill,
                        y + height - dp(2f)
                );
                canvas.drawRoundRect(
                        rect,
                        dp(1.5f),
                        dp(1.5f),
                        primaryFillPaint
                );
            }
        }

        if (charging) {
            float cx = x - dp(7f);
            float cy = y + height / 2f;
            canvas.drawLine(cx - dp(2f), cy, cx + dp(2f), cy, statusStrokePaint);
            canvas.drawLine(cx, cy - dp(2f), cx, cy + dp(2f), statusStrokePaint);
        }
    }

    private void drawApps(Canvas canvas) {
        float x = left();
        float top = contentTop();
        canvas.drawText("ALL APPS", x, top + sp(DesignTokens.LABEL_SP), labelPaint);

        float listStart = top + dp(48f);
        float listBottom = getHeight() - bottomInset - dp(96f);

        if (filteredApps.isEmpty()) {
            canvas.drawText("No matching apps", x, listStart + dp(24f), metaPaint);
            return;
        }

        drawAppRows(
                canvas,
                filteredApps,
                x,
                listStart - appScroll,
                filteredApps.size(),
                listStart,
                listBottom
        );
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
        int first = Math.max(0, (int) Math.floor((clipTop - startY) / row));
        int last = Math.min(count, (int) Math.ceil((clipBottom - startY) / row) + 1);
        if (last <= first) return;

        int save = canvas.save();
        canvas.clipRect(x, clipTop, getWidth() - x, clipBottom);

        float baselineOffset = dp(31f);
        for (int i = first; i < last; i++) {
            float rowTop = startY + i * row;
            canvas.drawText(source.get(i).label, x, rowTop + baselineOffset, appPaint);
            canvas.drawRect(
                    x,
                    rowTop + row - dp(1f),
                    getWidth() - x,
                    rowTop + row,
                    dividerPaint
            );
        }
        canvas.restoreToCount(save);
    }

    private void drawSettings(Canvas canvas) {
        float x = leftPx;
        float right = rightPx;
        float top = contentTopPx;

        canvas.drawText("SETTINGS", x, top + sp(DesignTokens.LABEL_SP), labelPaint);

        int save = canvas.save();
        canvas.clipRect(x, settingsViewportTopPx, right, settingsViewportBottomPx);

        for (int index = 0; index < SETTINGS_ROW_COUNT; index++) {
            float y = settingsRowTop(index) - settingsScroll;
            if (y + rowHeightPx < settingsViewportTopPx || y > settingsViewportBottomPx) continue;

            drawSettingsRow(
                    canvas,
                    settingsLabel(index),
                    settingsValue(index),
                    y,
                    x,
                    right
            );
        }

        canvas.restoreToCount(save);
    }

    private void drawSettingsRow(
            Canvas canvas,
            String label,
            String value,
            float y,
            float x,
            float right
    ) {
        float baseline = y + rowHeightPx * 0.58f;
        canvas.drawText(label, x, baseline, appPaint);

        if (value != null && !value.isEmpty()) {
            float width = metaPaint.measureText(value);
            canvas.drawText(value, right - width, baseline, metaPaint);
        }

        canvas.drawRect(
                x,
                y + rowHeightPx - dp(1f),
                right,
                y + rowHeightPx,
                dividerPaint
        );
    }

    private String settingsLabel(int index) {
        switch (index) {
            case 0: return "Visible apps";
            case 1: return "Home position";
            case 2: return "Density";
            case 3: return "Text size";
            case 4: return "Time";
            case 5: return "Date";
            case 6: return "Weather";
            case 7: return "Battery";
            case 8: return "Status layout";
            case 9: return "Time format";
            case 10: return "Date style";
            case 11: return "Weather detail";
            case 12: return "Battery detail";
            case 13: return "Swipe up";
            case 14: return "Animation";
            case 15: return "Haptics";
            case 16: return "Hidden apps";
            case 17: return "Export config";
            case 18: return "Import config";
            default: return "";
        }
    }

    private String settingsValue(int index) {
        switch (index) {
            case 0: return Integer.toString(maxHomeApps);
            case 1: return titleCase(uiConfig.homePosition);
            case 2: return titleCase(uiConfig.density);
            case 3: return titleCase(uiConfig.textSize);
            case 4: return onOff(uiConfig.showTime);
            case 5: return onOff(uiConfig.showDate);
            case 6: return onOff(uiConfig.showWeather);
            case 7: return onOff(uiConfig.showBattery);
            case 8:
                if (LauncherPreferences.STATUS_DATE_FIRST.equals(uiConfig.statusLayout)) return "Date first";
                if (LauncherPreferences.STATUS_COMPACT.equals(uiConfig.statusLayout)) return "Compact";
                return "Time first";
            case 9:
                if (LauncherPreferences.CLOCK_12.equals(uiConfig.clockFormat)) return "12h";
                if (LauncherPreferences.CLOCK_24.equals(uiConfig.clockFormat)) return "24h";
                return "System";
            case 10: return titleCase(uiConfig.dateStyle);
            case 11:
                if (LauncherPreferences.WEATHER_TEMP.equals(uiConfig.weatherMode)) return "Temperature";
                if (LauncherPreferences.WEATHER_CONDITION.equals(uiConfig.weatherMode)) return "Condition";
                return "Both";
            case 12: return titleCase(uiConfig.batteryMode);
            case 13: return quickAppLabel;
            case 14: return titleCase(uiConfig.animationSpeed);
            case 15: return onOff(uiConfig.haptics);
            case 16: return hiddenAppCount == 0 ? "None" : Integer.toString(hiddenAppCount);
            default: return "";
        }
    }

    private float settingsRowTop(int index) {
        float y = settingsViewportTopPx;
        for (int i = 0; i < index; i++) {
            y += rowHeightPx;
            if (i == 3 || i == 12 || i == 15) y += dp(24f);
        }
        return y;
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

                pressedHomeIndex = page == PAGE_HOME
                        ? homeIndexAt(downX, downY)
                        : -1;
                if (pressedHomeIndex >= 0) {
                    postDelayed(longPressRunnable, longPressTimeout);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
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
                }

                if (gestureMode == GESTURE_HORIZONTAL) {
                    dragOffsetX = totalDx;
                    if (neighborForDrag(dragOffsetX) == Integer.MIN_VALUE) {
                        dragOffsetX *= 0.22f;
                    }
                    postInvalidateOnAnimation();
                } else if (gestureMode == GESTURE_VERTICAL && page == PAGE_APPS) {
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
        if (dy < -dp(64f) && Math.abs(dy) > Math.abs(event.getX() - downX)) {
            host.onQuickLaunchRequested();
        }
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

        if (page == PAGE_HOME) {
            if (y >= top + dp(101f) && y <= top + dp(143f)) {
                host.onWeatherTapped();
                return;
            }

            int index = homeIndexAt(x, y);
            if (index >= 0 && index < homeApps.size()) {
                AppEntry app = homeApps.get(index);
                if (app != null) host.onOpenApp(app);
            }
            return;
        }

        if (page == PAGE_APPS) {
            float viewportTop = top + dp(48f);
            float viewportBottom = getHeight() - bottomInset - dp(96f);
            if (y < viewportTop || y >= viewportBottom) return;

            float start = viewportTop - appScroll;
            int index = (int) ((y - start) / dp(DesignTokens.ROW_HEIGHT_DP));
            if (index >= 0 && index < filteredApps.size()) {
                host.onOpenApp(filteredApps.get(index));
            }
            return;
        }

        float contentY = y + settingsScroll;
        for (int index = 0; index < SETTINGS_ROW_COUNT; index++) {
            float rowTop = settingsRowTop(index);
            if (contentY >= rowTop && contentY < rowTop + rowHeightPx) {
                handleSettingsRow(index);
                return;
            }
        }
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
            case 2: host.onSettingAction(ACTION_HOME_DENSITY); break;
            case 3: host.onSettingAction(ACTION_HOME_TEXT); break;
            case 4: host.onSettingAction(ACTION_TOGGLE_TIME); break;
            case 5: host.onSettingAction(ACTION_TOGGLE_DATE); break;
            case 6: host.onSettingAction(ACTION_TOGGLE_WEATHER); break;
            case 7: host.onSettingAction(ACTION_TOGGLE_BATTERY); break;
            case 8: host.onSettingAction(ACTION_STATUS_LAYOUT); break;
            case 9: host.onSettingAction(ACTION_CLOCK_FORMAT); break;
            case 10: host.onSettingAction(ACTION_DATE_STYLE); break;
            case 11: host.onSettingAction(ACTION_WEATHER_MODE); break;
            case 12: host.onSettingAction(ACTION_BATTERY_MODE); break;
            case 13: host.onQuickAppPickerRequested(); break;
            case 14: host.onSettingAction(ACTION_ANIMATION); break;
            case 15: host.onSettingAction(ACTION_HAPTICS); break;
            case 16: host.onSettingAction(ACTION_HIDDEN_APPS); break;
            case 17: host.onSettingAction(ACTION_EXPORT_CONFIG); break;
            case 18: host.onSettingAction(ACTION_IMPORT_CONFIG); break;
            default: break;
        }
    }

    private int homeIndexAt(float x, float y) {
        float start = homeListStartPx;
        float row = rowHeightPx;
        int visible = visibleHomeRowsCache;

        if (x < leftPx || x > rightPx) return -1;
        if (y < start || y >= start + visible * row) return -1;

        int index = (int) ((y - start) / row);
        return index >= 0 && index < visible ? index : -1;
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
        float content = filteredApps.size() * rowHeightPx;
        return Math.max(0f, content - viewport);
    }

    private float maxSettingsScroll() {
        float contentBottom = settingsRowTop(SETTINGS_ROW_COUNT - 1) + rowHeightPx;
        float viewport = Math.max(0f, settingsViewportBottomPx - settingsViewportTopPx);
        return Math.max(0f, contentBottom - settingsViewportTopPx - viewport);
    }

    private void cancelPendingLongPress() {
        removeCallbacks(longPressRunnable);
        pressedHomeIndex = -1;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
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
