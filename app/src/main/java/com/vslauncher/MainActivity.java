package com.vslauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A deliberately small launcher, built without a UI framework dependency. */
public final class MainActivity extends Activity {
    private static final int HOME_APP_LIMIT = 8;
    private static final int PAGE_HOME = 0;
    private static final int PAGE_APPS = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final String PREFS = "launcher_preferences";
    private static final String UP_ACTION = "up_action";

    private final Handler clockHandler = new Handler();
    private final List<AppEntry> apps = new ArrayList<>();
    private FrameLayout root;
    private LauncherSurface surface;
    private EditText search;
    private int page = PAGE_HOME;
    private String query = "";
    private int appScroll;
    private boolean charging;
    private int batteryLevel = -1;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (surface != null) surface.invalidate();
            clockHandler.postDelayed(this, 30_000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadApps();
        root = new FrameLayout(this);
        surface = new LauncherSurface(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        registerBattery();
        clockHandler.post(clockTick);
    }

    @Override protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
        unregisterReceiverSafe();
    }

    private android.content.BroadcastReceiver batteryReceiver;

    private void registerBattery() {
        if (batteryReceiver != null) return;
        batteryReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                batteryLevel = level >= 0 ? Math.round(level * 100f / scale) : -1;
                int status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0);
                charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                        || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
                surface.invalidate();
            }
        };
        registerReceiver(batteryReceiver, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void unregisterReceiverSafe() {
        if (batteryReceiver == null) return;
        unregisterReceiver(batteryReceiver);
        batteryReceiver = null;
    }

    private void loadApps() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager manager = getPackageManager();
        for (ResolveInfo info : manager.queryIntentActivities(launcherIntent, 0)) {
            String label = info.loadLabel(manager).toString();
            if (!getPackageName().equals(info.activityInfo.packageName)) {
                apps.add(new AppEntry(label, new ComponentName(info.activityInfo.packageName, info.activityInfo.name)));
            }
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
    }

    private void showPage(int target) {
        page = target;
        query = "";
        appScroll = 0;
        if (search != null) {
            root.removeView(search);
            search = null;
        }
        if (page == PAGE_APPS) addSearch();
        surface.invalidate();
    }

    private void addSearch() {
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(172, 172, 164));
        search.setHint("Search apps");
        search.setTextSize(18);
        search.setBackground(searchBackground());
        search.setPadding(dp(24), 0, dp(24), 0);
        search.setContentDescription("Search all apps");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                surface.invalidate();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM);
        params.setMargins(dp(16), 0, dp(16), dp(16));
        root.addView(search, params);
        search.requestFocus();
        search.postDelayed(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT), 160L);
    }

    private StateListDrawable searchBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_focused }, searchField(Color.rgb(230, 169, 62)));
        states.addState(new int[] {}, searchField(Color.rgb(104, 105, 95)));
        return states;
    }

    private GradientDrawable searchField(int stroke) {
        GradientDrawable field = new GradientDrawable();
        field.setColor(Color.rgb(16, 17, 15));
        field.setCornerRadius(dp(8));
        field.setStroke(dp(2), stroke);
        return field;
    }

    private void open(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(app.component);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(intent); } catch (Exception ignored) { }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class LauncherSurface extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float downX;
        private float downY;

        LauncherSurface(Context context) {
            super(context);
            setFocusable(true);
            setContentDescription("VS Launcher");
            paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(16, 17, 15));
            if (page == PAGE_HOME) drawHome(canvas);
            else if (page == PAGE_APPS) drawApps(canvas);
            else drawSettings(canvas);
        }

        private void text(Canvas c, String value, float x, float y, float size, int color) {
            paint.setTextSize(dp(size));
            paint.setColor(color);
            paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            c.drawText(value, x, y, paint);
        }

        private void drawHome(Canvas c) {
            int warmWhite = Color.rgb(244, 242, 232);
            int soft = Color.rgb(190, 190, 179);
            String date = new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date());
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            text(c, date, dp(24), dp(56), 16, soft);
            text(c, time, dp(24), dp(112), 45, warmWhite);
            String battery = batteryLevel < 0 ? "Battery unavailable" : batteryLevel + "%" + (charging ? " charging" : " battery");
            text(c, battery, dp(24), dp(142), 14, soft);
            text(c, "Weather not configured", dp(24), dp(172), 14, soft);
            text(c, "HOME", dp(24), dp(238), 12, Color.rgb(230, 169, 62));
            drawAppList(c, apps.subList(0, Math.min(HOME_APP_LIMIT, apps.size())), dp(24), dp(274), false);
            text(c, "Swipe left for all apps", dp(24), getHeight() - dp(36), 14, soft);
        }

        private void drawApps(Canvas c) {
            text(c, "ALL APPS", dp(24), dp(62), 13, Color.rgb(230, 169, 62));
            List<AppEntry> matching = new ArrayList<>();
            for (AppEntry app : apps) {
                if (app.label.toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault()))) matching.add(app);
            }
            if (matching.isEmpty()) text(c, "No apps match this search.", dp(24), dp(122), 17, Color.rgb(190, 190, 179));
            else drawAppList(c, matching, dp(24), dp(108) - appScroll, true);
        }

        private void drawSettings(Canvas c) {
            int white = Color.rgb(244, 242, 232);
            int soft = Color.rgb(190, 190, 179);
            text(c, "SETTINGS", dp(24), dp(62), 13, Color.rgb(230, 169, 62));
            text(c, "Swipe up", dp(24), dp(126), 21, white);
            String action = getSharedPreferences(PREFS, MODE_PRIVATE).getString(UP_ACTION, "Apps");
            text(c, "Currently opens " + action, dp(24), dp(153), 15, soft);
            drawRow(c, "Open all apps", dp(24), dp(188));
            drawRow(c, "Open settings", dp(24), dp(254));
            text(c, "Weather", dp(24), dp(356), 21, white);
            text(c, "Not configured. Add a provider before showing local weather.", dp(24), dp(383), 14, soft);
        }

        private void drawRow(Canvas c, String label, float x, float y) {
            paint.setColor(Color.rgb(104, 105, 95));
            rect.set(x, y, getWidth() - dp(24), y + dp(52));
            c.drawRoundRect(rect, dp(8), dp(8), paint);
            text(c, label, x + dp(16), y + dp(32), 16, Color.rgb(244, 242, 232));
        }

        private void drawAppList(Canvas c, List<AppEntry> list, float x, float startY, boolean allApps) {
            float y = startY;
            int max = allApps ? list.size() : Math.min(HOME_APP_LIMIT, list.size());
            for (int i = 0; i < max; i++) {
                AppEntry app = list.get(i);
                text(c, app.label, x, y, 20, Color.rgb(244, 242, 232));
                paint.setColor(Color.rgb(104, 105, 95));
                c.drawRect(x, y + dp(18), getWidth() - dp(24), y + dp(19), paint);
                y += dp(54);
                if (allApps && y > getHeight() - dp(92)) break;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX(); downY = event.getY(); return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (page == PAGE_APPS && Math.abs(dy) > dp(24) && Math.abs(dy) > Math.abs(dx)) {
                        int maxScroll = Math.max(0, filteredApps().size() * dp(54) - (getHeight() - dp(200)));
                        appScroll = Math.max(0, Math.min(maxScroll, appScroll - Math.round(dy)));
                        invalidate();
                        return true;
                    }
                    if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy)) {
                        showPage(dx > 0 ? PAGE_SETTINGS : PAGE_APPS);
                        return true;
                    }
                    if (dy < -dp(72) && Math.abs(dy) > Math.abs(dx)) {
                        String action = getSharedPreferences(PREFS, MODE_PRIVATE).getString(UP_ACTION, "Apps");
                        showPage("Settings".equals(action) ? PAGE_SETTINGS : PAGE_APPS);
                        return true;
                    }
                    handleTap(event.getX(), event.getY());
                    return true;
            }
            return true;
        }

        private void handleTap(float x, float y) {
            if (page == PAGE_SETTINGS) {
                if (y >= dp(188) && y <= dp(240)) setUpAction("Apps");
                else if (y >= dp(254) && y <= dp(306)) setUpAction("Settings");
                return;
            }
            if (page == PAGE_HOME || page == PAGE_APPS) {
                float start = page == PAGE_HOME ? dp(274) : dp(108) - appScroll;
                int index = (int) ((y - start + dp(12)) / dp(54));
                List<AppEntry> visible = page == PAGE_HOME ? apps : filteredApps();
                if (index >= 0 && index < visible.size()) open(visible.get(index));
            }
        }

        private List<AppEntry> filteredApps() {
            List<AppEntry> result = new ArrayList<>();
            for (AppEntry app : apps) if (app.label.toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault()))) result.add(app);
            return result;
        }

        private void setUpAction(String action) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(UP_ACTION, action).apply();
            invalidate();
        }
    }

    private static final class AppEntry {
        final String label;
        final ComponentName component;
        AppEntry(String label, ComponentName component) { this.label = label; this.component = component; }
    }
}
