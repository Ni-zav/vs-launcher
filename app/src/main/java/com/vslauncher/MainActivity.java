package com.vslauncher;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Minimal native launcher shell.
 *
 * UI-thread work is deliberately bounded: Canvas drawing, cached string state and
 * O(visible rows) rendering. App discovery and weather I/O live on background
 * executors.
 */
public final class MainActivity extends Activity implements LauncherSurface.Host {
    private static final String PREFS = "launcher_preferences";
    private static final String UP_ACTION = "up_action";
    private static final int REQUEST_COARSE_LOCATION = 41;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private FrameLayout root;
    private LauncherSurface surface;
    private EditText search;
    private AppRepository appRepository;
    private WeatherService weatherService;

    private List<AppEntry> apps = Collections.emptyList();
    private List<AppEntry> filteredApps = Collections.emptyList();
    private String query = "";
    private int bottomInset;
    private boolean packageReceiverRegistered;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            long untilNextMinute = 60_000L - (now % 60_000L) + 20L;
            mainHandler.postDelayed(this, untilNextMinute);
        }
    };

    private BroadcastReceiver batteryReceiver;
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(DesignTokens.BLACK);

        surface = new LauncherSurface(this, this);
        surface.setUpAction(
                getSharedPreferences(PREFS, MODE_PRIVATE).getString(UP_ACTION, "Apps")
        );
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            bottomInset = bottom;
            surface.setInsets(top, bottom);
            updateSearchLayout();
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();

        appRepository = new AppRepository(this);
        weatherService = new WeatherService(this, text -> surface.setWeather(text));

        updateClock();
        reloadApps();
        registerPackageChanges();
    }

    @Override protected void onResume() {
        super.onResume();
        registerBattery();
        mainHandler.removeCallbacks(clockTick);
        mainHandler.post(clockTick);
        weatherService.publishCacheAndRefresh();
    }

    @Override protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(clockTick);
        unregisterBattery();
    }

    @Override protected void onDestroy() {
        removeSearch();
        unregisterPackageChanges();
        appRepository.close();
        weatherService.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (surface.getPage() != LauncherSurface.PAGE_HOME) {
            showPage(LauncherSurface.PAGE_HOME);
        }
    }

    private void updateClock() {
        Date now = new Date();
        surface.setClock(dateFormat.format(now), timeFormat.format(now));
    }

    private void reloadApps() {
        appRepository.load(loaded -> {
            apps = loaded;
            filteredApps = AppRepository.filter(apps, query);
            surface.setApps(apps, filteredApps);
        });
    }

    private void registerPackageChanges() {
        if (packageReceiverRegistered) return;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
        packageReceiverRegistered = true;
    }

    private void unregisterPackageChanges() {
        if (!packageReceiverRegistered) return;
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        packageReceiverRegistered = false;
    }

    private void registerBattery() {
        if (batteryReceiver != null) return;

        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int percent = level >= 0 && scale > 0
                        ? Math.round(level * 100f / scale)
                        : -1;

                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                surface.setBattery(percent, charging);
            }
        };

        registerReceiver(
                batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
    }

    private void unregisterBattery() {
        if (batteryReceiver == null) return;
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        batteryReceiver = null;
    }

    private void showPage(int target) {
        if (surface.getPage() == target) return;

        removeSearch();
        surface.setPage(target);

        if (target == LauncherSurface.PAGE_APPS) {
            mainHandler.postDelayed(() -> {
                if (surface.getPage() == LauncherSurface.PAGE_APPS && search == null) {
                    addSearch();
                }
            }, 185L);
        }
    }

    private void addSearch() {
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(DesignTokens.TEXT_PRIMARY);
        search.setHintTextColor(DesignTokens.TEXT_TERTIARY);
        search.setHint("Search apps");
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.SEARCH_SP);
        search.setTypeface(DesignTokens.BODY);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setBackground(searchBackground());
        search.setPadding(dp(18f), 0, dp(18f), 0);
        search.setContentDescription("Search all apps");

        if (!query.isEmpty()) {
            search.setText(query);
            search.setSelection(search.length());
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                filteredApps = AppRepository.filter(apps, query);
                surface.setFilteredApps(filteredApps);
            }

            @Override public void afterTextChanged(Editable s) {
            }
        });

        root.addView(search, searchLayoutParams());
        search.requestFocus();
        search.postDelayed(() -> {
            if (search == null || !search.hasFocus()) return;
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 80L);
    }

    private FrameLayout.LayoutParams searchLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(56f),
                Gravity.BOTTOM
        );
        params.setMargins(dp(16f), 0, dp(16f), dp(12f) + bottomInset);
        return params;
    }

    private void updateSearchLayout() {
        if (search == null) return;
        search.setLayoutParams(searchLayoutParams());
    }

    private void removeSearch() {
        if (search == null) {
            query = "";
            filteredApps = apps;
            surface.setFilteredApps(filteredApps);
            return;
        }

        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);

        root.removeView(search);
        search = null;
        query = "";
        filteredApps = apps;
        surface.setFilteredApps(filteredApps);
    }

    private StateListDrawable searchBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[] { android.R.attr.state_focused },
                searchField(DesignTokens.FOCUS)
        );
        states.addState(new int[] {}, searchField(DesignTokens.DIVIDER));
        return states;
    }

    private GradientDrawable searchField(int strokeColor) {
        GradientDrawable field = new GradientDrawable();
        field.setColor(Color.BLACK);
        field.setCornerRadius(dp(DesignTokens.CORNER_DP));
        field.setStroke(dp(1f), strokeColor);
        return field;
    }

    @Override public void onPageRequested(int page) {
        showPage(page);
    }

    @Override public void onOpenApp(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            reloadApps();
        }
    }

    @Override public void onUpActionSelected(String action) {
        String safeAction = "Settings".equals(action) ? "Settings" : "Apps";
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.edit().putString(UP_ACTION, safeAction).apply();
        surface.setUpAction(safeAction);
    }

    @Override public void onWeatherTapped() {
        if (!weatherService.hasLocationPermission()) {
            requestPermissions(
                    new String[] { Manifest.permission.ACCESS_COARSE_LOCATION },
                    REQUEST_COARSE_LOCATION
            );
            return;
        }

        surface.setWeather("Weather · updating");
        weatherService.refreshNow();
    }

    @Override public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_COARSE_LOCATION) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            surface.setWeather("Weather · updating");
            weatherService.refreshNow();
        } else {
            surface.setWeather("Weather · location permission");
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
