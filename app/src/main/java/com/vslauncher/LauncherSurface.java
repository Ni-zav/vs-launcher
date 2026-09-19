package com.vslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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

    interface Host {
        void onPageRequested(int page);
        void onOpenApp(AppEntry app);
        void onUpActionSelected(String action);
        void onWeatherTapped();
    }

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

    private final Host host;
    private final Paint datePaint = textPaint(DesignTokens.DATE_SP, DesignTokens.TEXT_SECONDARY, DesignTokens.BODY);
    private final Paint timePaint = textPaint(DesignTokens.TIME_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.DISPLAY);
    private final Paint metaPaint = textPaint(DesignTokens.META_SP, DesignTokens.TEXT_SECONDARY, DesignTokens.BODY);
    private final Paint labelPaint = textPaint(DesignTokens.LABEL_SP, DesignTokens.TEXT_TERTIARY, DesignTokens.LABEL);
    private final Paint appPaint = textPaint(DesignTokens.APP_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.BODY);
    private final Paint titlePaint = textPaint(DesignTokens.TITLE_SP, DesignTokens.TEXT_PRIMARY, DesignTokens.BODY);
    private final Paint dividerPaint = fillPaint(DesignTokens.DIVIDER);
    private final Paint surfacePaint = fillPaint(DesignTokens.SURFACE);
    private final Paint primaryFillPaint = fillPaint(DesignTokens.TEXT_PRIMARY);
    private final Paint batteryStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;

    private List<AppEntry> apps = Collections.emptyList();
    private List<AppEntry> filteredApps = Collections.emptyList();

    private String dateText = "";
    private String timeText = "";
    private String weatherText = "Weather · tap to enable";
    private String batteryText = "—";
    private String upAction = "Apps";

    private int batteryLevel = -1;
    private boolean charging;
    private int page = PAGE_HOME;
    private int topInset;
    private int bottomInset;

    private float appScroll;
    private float downX;
    private float downY;
    private float lastY;
    private float dragOffsetX;
    private int gestureMode = GESTURE_NONE;
    private VelocityTracker velocityTracker;

    private ValueAnimator pageAnimator;
    private ValueAnimator settleAnimator;
    private int transitionFrom = PAGE_HOME;
    private int transitionTo = PAGE_HOME;
    private float transitionOldOffset;
    private float transitionOldEnd;
    private boolean transitionRunning;

    LauncherSurface(Context context, Host host) {
        super(context);
        this.host = host;
        setFocusable(true);
        setClickable(true);
        setContentDescription("VS Launcher");
        setBackgroundColor(DesignTokens.BLACK);

        ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledTouchSlop();
        minFlingVelocity = config.getScaledMinimumFlingVelocity();
        maxFlingVelocity = config.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(context);

        batteryStrokePaint.setStyle(Paint.Style.STROKE);
        batteryStrokePaint.setStrokeWidth(dp(1f));
        batteryStrokePaint.setColor(DesignTokens.TEXT_SECONDARY);
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
        appScroll = clamp(appScroll, 0f, maxAppScroll());
        invalidate();
    }

    void setClock(String date, String time) {
        dateText = date;
        timeText = time;
        invalidateHome();
    }

    void setBattery(int level, boolean isCharging) {
        batteryLevel = level;
        charging = isCharging;
        batteryText = level < 0 ? "—" : level + "%" + (isCharging ? " · charging" : "");
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

    void setUpAction(String action) {
        upAction = "Settings".equals(action) ? "Settings" : "Apps";
        if (page == PAGE_SETTINGS) invalidate();
    }

    int getPage() {
        return page;
    }

    void setPage(int target) {
        if (target < PAGE_SETTINGS || target > PAGE_APPS || target == page) {
            settleDrag();
            return;
        }

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
        long duration = Math.max(90L, Math.round(180L * fraction));

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
        if (page == PAGE_HOME || transitionFrom == PAGE_HOME || transitionTo == PAGE_HOME) invalidate();
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
                float neighborOffset = dragOffsetX + (dragOffsetX > 0f ? -getWidth() : getWidth());
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
        if (targetPage == PAGE_HOME) drawHome(canvas);
        else if (targetPage == PAGE_APPS) drawApps(canvas);
        else drawSettings(canvas);
        canvas.restoreToCount(save);
    }

    private float contentTop() {
        return topInset + dp(24f);
    }

    private float left() {
        return dp(DesignTokens.PAGE_HORIZONTAL_DP);
    }

    private void drawHome(Canvas canvas) {
        float x = left();
        float top = contentTop();

        canvas.drawText(dateText, x, top + sp(DesignTokens.DATE_SP), datePaint);
        canvas.drawText(timeText, x, top + dp(68f), timePaint);

        float metaY = top + dp(100f);
        float batteryX = x;
        drawBattery(canvas, batteryX, metaY - dp(10f));

        canvas.drawText(batteryText, batteryX + dp(38f), metaY, metaPaint);
        canvas.drawText(weatherText, x, metaY + dp(30f), metaPaint);

        float labelY = top + dp(193f);
        canvas.drawText("HOME", x, labelY, labelPaint);

        float listY = top + dp(230f);
        float hintY = getHeight() - bottomInset - dp(26f);
        int possibleRows = Math.max(0, (int) ((hintY - listY - dp(20f)) / dp(DesignTokens.ROW_HEIGHT_DP)));
        int homeRows = Math.min(8, Math.min(possibleRows, apps.size()));
        drawAppRows(canvas, apps, x, listY, homeRows, 0f, hintY - dp(18f));

        canvas.drawText("swipe · settings  /  apps", x, hintY, metaPaint);
    }

    private void drawBattery(Canvas canvas, float x, float y) {
        float width = dp(28f);
        float height = dp(12f);
        float radius = dp(3f);

        rect.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, radius, radius, batteryStrokePaint);
        rect.set(x + width + dp(2f), y + dp(3f), x + width + dp(4f), y + height - dp(3f));
        canvas.drawRoundRect(rect, dp(1f), dp(1f), primaryFillPaint);

        if (batteryLevel >= 0) {
            float innerWidth = width - dp(4f);
            float fill = innerWidth * clamp(batteryLevel / 100f, 0f, 1f);
            if (fill > 0f) {
                rect.set(x + dp(2f), y + dp(2f), x + dp(2f) + fill, y + height - dp(2f));
                canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), primaryFillPaint);
            }
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
        float row = dp(DesignTokens.ROW_HEIGHT_DP);
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
        float x = left();
        float top = contentTop();

        canvas.drawText("SETTINGS", x, top + sp(DesignTokens.LABEL_SP), labelPaint);
        canvas.drawText("Swipe up", x, top + dp(70f), titlePaint);
        canvas.drawText("Choose the home-screen shortcut", x, top + dp(98f), metaPaint);

        drawSettingRow(canvas, "Apps", "Open all apps", top + dp(126f), "Apps".equals(upAction));
        drawSettingRow(canvas, "Settings", "Open launcher settings", top + dp(190f), "Settings".equals(upAction));

        canvas.drawText("Weather", x, top + dp(304f), titlePaint);
        canvas.drawText("Coarse location · cached for 20 min", x, top + dp(332f), metaPaint);
        canvas.drawText("Weather data · Open-Meteo", x, top + dp(356f), metaPaint);
        canvas.drawText("Tap weather on Home to refresh or enable.", x, top + dp(380f), metaPaint);
    }

    private void drawSettingRow(Canvas canvas, String value, String subtitle, float y, boolean selected) {
        float x = left();
        float right = getWidth() - x;
        rect.set(x, y, right, y + dp(54f));
        if (selected) canvas.drawRoundRect(rect, dp(DesignTokens.CORNER_DP), dp(DesignTokens.CORNER_DP), surfacePaint);

        canvas.drawText(value, x + dp(14f), y + dp(23f), appPaint);
        canvas.drawText(subtitle, x + dp(14f), y + dp(43f), metaPaint);

        float cx = right - dp(18f);
        float cy = y + dp(27f);
        batteryStrokePaint.setColor(selected ? DesignTokens.TEXT_PRIMARY : DesignTokens.TEXT_TERTIARY);
        canvas.drawCircle(cx, cy, dp(6f), batteryStrokePaint);
        if (selected) canvas.drawCircle(cx, cy, dp(3f), primaryFillPaint);
        batteryStrokePaint.setColor(DesignTokens.TEXT_SECONDARY);
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
                return true;

            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                float totalDx = event.getX() - downX;
                float totalDy = event.getY() - downY;

                if (gestureMode == GESTURE_NONE
                        && Math.max(Math.abs(totalDx), Math.abs(totalDy)) > touchSlop) {
                    gestureMode = Math.abs(totalDx) > Math.abs(totalDy)
                            ? GESTURE_HORIZONTAL
                            : GESTURE_VERTICAL;
                }

                if (gestureMode == GESTURE_HORIZONTAL) {
                    dragOffsetX = totalDx;
                    if (neighborForDrag(dragOffsetX) == Integer.MIN_VALUE) dragOffsetX *= 0.22f;
                    postInvalidateOnAnimation();
                } else if (gestureMode == GESTURE_VERTICAL && page == PAGE_APPS) {
                    float dy = event.getY() - lastY;
                    appScroll = clamp(appScroll - dy, 0f, maxAppScroll());
                    lastY = event.getY();
                    postInvalidateOnAnimation();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                float velocityX = 0f;
                float velocityY = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    velocityX = velocityTracker.getXVelocity();
                    velocityY = velocityTracker.getYVelocity();
                }

                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    settleDrag();
                } else if (gestureMode == GESTURE_HORIZONTAL) {
                    finishHorizontalGesture(velocityX);
                } else if (gestureMode == GESTURE_VERTICAL) {
                    finishVerticalGesture(event, velocityY);
                } else {
                    handleTap(event.getX(), event.getY());
                }

                recycleVelocityTracker();
                gestureMode = GESTURE_NONE;
                return true;

            default:
                return true;
        }
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

        float start = dragOffsetX;
        settleAnimator = ValueAnimator.ofFloat(start, 0f);
        settleAnimator.setDuration(120L);
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
        if (page == PAGE_APPS) {
            if (Math.abs(velocityY) >= minFlingVelocity) {
                scroller.fling(
                        0,
                        Math.round(appScroll),
                        0,
                        -Math.round(velocityY),
                        0,
                        0,
                        0,
                        Math.round(maxAppScroll())
                );
                postInvalidateOnAnimation();
            }
            return;
        }

        float dy = event.getY() - downY;
        if (dy < -dp(64f) && Math.abs(dy) > Math.abs(event.getX() - downX)) {
            host.onPageRequested("Settings".equals(upAction) ? PAGE_SETTINGS : PAGE_APPS);
        }
    }

    @Override public void computeScroll() {
        if (!scroller.computeScrollOffset()) return;
        appScroll = scroller.getCurrY();
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
            if (y >= top + dp(108f) && y <= top + dp(154f)) {
                host.onWeatherTapped();
                return;
            }

            float start = top + dp(230f);
            float hintY = getHeight() - bottomInset - dp(26f);
            int possibleRows = Math.max(
                    0,
                    (int) ((hintY - start - dp(20f)) / dp(DesignTokens.ROW_HEIGHT_DP))
            );
            int visibleRows = Math.min(8, Math.min(possibleRows, apps.size()));
            float rowHeight = dp(DesignTokens.ROW_HEIGHT_DP);
            if (y < start || y >= start + visibleRows * rowHeight) return;

            int index = (int) ((y - start) / rowHeight);
            if (index >= 0 && index < visibleRows) host.onOpenApp(apps.get(index));
            return;
        }

        if (page == PAGE_APPS) {
            float viewportTop = top + dp(48f);
            float viewportBottom = getHeight() - bottomInset - dp(96f);
            if (y < viewportTop || y >= viewportBottom) return;

            float start = viewportTop - appScroll;
            int index = (int) ((y - start) / dp(DesignTokens.ROW_HEIGHT_DP));
            if (index >= 0 && index < filteredApps.size()) host.onOpenApp(filteredApps.get(index));
            return;
        }

        if (y >= top + dp(126f) && y <= top + dp(180f)) {
            host.onUpActionSelected("Apps");
        } else if (y >= top + dp(190f) && y <= top + dp(244f)) {
            host.onUpActionSelected("Settings");
        }
    }

    private float maxAppScroll() {
        float viewportTop = contentTop() + dp(48f);
        float viewportBottom = getHeight() - bottomInset - dp(96f);
        float viewport = Math.max(0f, viewportBottom - viewportTop);
        float content = filteredApps.size() * dp(DesignTokens.ROW_HEIGHT_DP);
        return Math.max(0f, content - viewport);
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
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
