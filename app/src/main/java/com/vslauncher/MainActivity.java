package com.vslauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Minimal native launcher shell.
 *
 * UI-thread work stays bounded to Canvas drawing, cached strings and visible rows.
 * App discovery and weather I/O remain on background executors.
 */
public final class MainActivity extends Activity implements LauncherSurface.Host {
    private static final int REQUEST_COARSE_LOCATION = 41;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE · d MMM", Locale.getDefault());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private FrameLayout root;
    private LauncherSurface surface;
    private EditText search;
    private AppRepository appRepository;
    private WeatherService weatherService;
    private LauncherPreferences launcherPreferences;
    private LauncherUiConfig uiConfig = LauncherUiConfig.defaults();

    private List<AppEntry> apps = Collections.emptyList();
    private Map<String, AppEntry> appByComponent = Collections.emptyMap();
    private List<AppEntry> filteredApps = Collections.emptyList();
    private List<AppEntry> homeApps = Collections.emptyList();
    private AppEntry quickApp;
    private String query = "";
    private String latestWeatherText = "Tap for weather";
    private int bottomInset;
    private int maxHomeApps = 5;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        launcherPreferences = new LauncherPreferences(this);
        uiConfig = LauncherUiConfig.from(launcherPreferences);

        root = new FrameLayout(this);
        root.setBackgroundColor(DesignTokens.BLACK);

        surface = new LauncherSurface(this, this);
        surface.setUiConfig(uiConfig);
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets navigation =
                        insets.getInsets(WindowInsets.Type.navigationBars());
                android.graphics.Insets ime =
                        insets.getInsets(WindowInsets.Type.ime());
                bottom = Math.max(navigation.bottom, ime.bottom);
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }

            bottomInset = bottom;
            // Status content deliberately owns the top edge; the system status bar is hidden.
            surface.setInsets(0, bottom);
            updateSearchLayout();
            return insets;
        });

        setContentView(root);
        applyMinimalSystemUi();
        root.requestApplyInsets();

        appRepository = new AppRepository(this);
        weatherService = new WeatherService(this, text -> {
            latestWeatherText = text;
            surface.setWeather(formatWeather(text));
        });

        updateClock();
        reloadApps();
        registerPackageChanges();
    }

    @Override protected void onResume() {
        super.onResume();
        applyMinimalSystemUi();
        registerBattery();
        mainHandler.removeCallbacks(clockTick);
        mainHandler.post(clockTick);
        weatherService.publishCacheAndRefresh();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyMinimalSystemUi();
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

    private void applyMinimalSystemUi() {
        getWindow().setStatusBarColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void updateClock() {
        TimeZone zone = TimeZone.getDefault();
        dateFormat.setTimeZone(zone);
        timeFormat.setTimeZone(zone);

        if (LauncherPreferences.CLOCK_24.equals(uiConfig.clockFormat)) {
            timeFormat.applyPattern("HH:mm");
        } else if (LauncherPreferences.CLOCK_12.equals(uiConfig.clockFormat)) {
            timeFormat.applyPattern("h:mm");
        } else {
            timeFormat.applyPattern(
                    android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm"
            );
        }

        if (LauncherPreferences.DATE_NUMERIC.equals(uiConfig.dateStyle)) {
            dateFormat.applyPattern("dd.MM.yyyy");
        } else if (LauncherPreferences.DATE_SHORT.equals(uiConfig.dateStyle)) {
            dateFormat.applyPattern("EEE · d MMM");
        } else {
            dateFormat.applyPattern("EEEE · d MMM");
        }

        Date now = new Date();
        surface.setClock(dateFormat.format(now), timeFormat.format(now));
    }

    private void applyUiConfiguration() {
        uiConfig = LauncherUiConfig.from(launcherPreferences);
        surface.setUiConfig(uiConfig);
        surface.setWeather(formatWeather(latestWeatherText));
        updateClock();
    }

    private String formatWeather(String text) {
        if (text == null || text.isEmpty()) return "Weather";
        int split = text.indexOf(" · ");
        if (split <= 0) return text;

        if (LauncherPreferences.WEATHER_TEMP.equals(uiConfig.weatherMode)) {
            return text.substring(0, split);
        }
        if (LauncherPreferences.WEATHER_CONDITION.equals(uiConfig.weatherMode)) {
            return text.substring(split + 3);
        }
        return text;
    }

    private void reloadApps() {
        appRepository.load(loaded -> {
            apps = loaded;
            HashMap<String, AppEntry> index = new HashMap<>(Math.max(16, loaded.size() * 2));
            for (AppEntry app : loaded) {
                index.put(app.component.flattenToString(), app);
            }
            appByComponent = Collections.unmodifiableMap(index);
            filteredApps = AppRepository.filter(apps, query);
            surface.setApps(apps, filteredApps);
            resolveLauncherConfiguration();
        });
    }

    private void resolveLauncherConfiguration() {
        maxHomeApps = launcherPreferences.homeMax();

        ArrayList<AppEntry> resolved = new ArrayList<>(maxHomeApps);
        Set<String> used = new HashSet<>();

        for (int index = 0; index < maxHomeApps; index++) {
            String componentName = launcherPreferences.homeSlot(index);
            AppEntry entry = findApp(componentName);

            if (componentName == null && !launcherPreferences.hasHomeSlot(index)) {
                entry = firstUnusedApp(used);
                if (entry != null) {
                    launcherPreferences.setHomeSlot(index, entry.component.flattenToString());
                }
            }

            resolved.add(entry);
            if (entry != null) used.add(entry.component.flattenToString());
        }

        String quickComponent = launcherPreferences.quickApp();
        quickApp = findApp(quickComponent);
        String quickLabel;
        if (quickApp != null) {
            quickLabel = quickApp.label;
        } else if (quickComponent != null) {
            quickLabel = "Unavailable";
        } else {
            quickLabel = "Not set";
        }

        homeApps = Collections.unmodifiableList(resolved);
        surface.setHomeConfiguration(homeApps, maxHomeApps, quickLabel);
    }

    private AppEntry firstUnusedApp(Set<String> used) {
        for (AppEntry app : apps) {
            String component = app.component.flattenToString();
            if (!used.contains(component)) return app;
        }
        return null;
    }

    private AppEntry findApp(String flattenedComponent) {
        if (flattenedComponent == null || flattenedComponent.isEmpty()) return null;
        return appByComponent.get(flattenedComponent);
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
        launchApp(app);
    }

    @Override public void onHomeSlotLongPressed(int index) {
        if (index < 0 || index >= maxHomeApps) return;
        showHomeAppPicker(index);
    }

    @Override public void onHomeMaxChanged(int requestedMax) {
        int safeMax = clamp(
                requestedMax,
                LauncherPreferences.MIN_HOME_APPS,
                LauncherPreferences.MAX_HOME_APPS
        );
        if (safeMax == maxHomeApps) return;

        launcherPreferences.setHomeMax(safeMax);
        resolveLauncherConfiguration();
    }

    @Override public void onQuickAppPickerRequested() {
        showQuickAppPicker();
    }

    @Override public void onQuickLaunchRequested() {
        if (quickApp != null) {
            launchApp(quickApp);
        } else {
            showPage(LauncherSurface.PAGE_SETTINGS);
        }
    }

    private void showHomeAppPicker(int slot) {
        if (apps.isEmpty()) return;

        CharSequence[] labels = new CharSequence[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;

        new AlertDialog.Builder(this)
                .setTitle("Home app " + (slot + 1))
                .setItems(labels, (dialog, which) -> {
                    AppEntry selected = apps.get(which);
                    launcherPreferences.setHomeSlot(
                            slot,
                            selected.component.flattenToString()
                    );
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showQuickAppPicker() {
        if (apps.isEmpty()) return;

        CharSequence[] labels = new CharSequence[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Swipe-up app")
                .setItems(labels, (picker, which) -> {
                    AppEntry selected = apps.get(which);
                    launcherPreferences.setQuickApp(selected.component.flattenToString());
                    resolveLauncherConfiguration();
                })
                .setNeutralButton("Clear", (picker, which) -> {
                    launcherPreferences.setQuickApp(null);
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    private void launchApp(AppEntry app) {
        if (app == null) return;

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

    @Override public void onWeatherTapped() {
        if (!weatherService.hasLocationPermission()) {
            requestPermissions(
                    new String[] { Manifest.permission.ACCESS_COARSE_LOCATION },
                    REQUEST_COARSE_LOCATION
            );
            return;
        }

        latestWeatherText = "Updating weather…";
        surface.setWeather(latestWeatherText);
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
            latestWeatherText = "Updating weather…";
            surface.setWeather(latestWeatherText);
            weatherService.refreshNow();
        } else {
            latestWeatherText = "Weather needs location";
            surface.setWeather(latestWeatherText);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
