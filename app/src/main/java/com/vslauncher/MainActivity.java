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
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
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
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    private static final int REQUEST_EXPORT_CONFIG = 52;
    private static final int REQUEST_IMPORT_CONFIG = 53;

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

    private List<AppEntry> allApps = Collections.emptyList();
    private List<AppEntry> apps = Collections.emptyList();
    private Map<String, AppEntry> appByComponent = Collections.emptyMap();
    private Map<String, String> aliases = Collections.emptyMap();
    private Map<String, String> normalizedAliases = Collections.emptyMap();
    private List<AppEntry> filteredApps = Collections.emptyList();
    private List<AppEntry> homeApps = Collections.emptyList();
    private AppEntry quickApp;
    private String query = "";
    private String latestWeatherText = "Tap for weather";
    private int bottomInset;
    private int maxHomeApps = 5;
    private boolean packageReceiverRegistered;
    private boolean appsBrowseMode;
    private Runnable pendingSingleResultLaunch;

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
        refreshAliasCache();

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
            allApps = loaded;
            HashMap<String, AppEntry> index = new HashMap<>(Math.max(16, loaded.size() * 2));
            for (AppEntry app : loaded) {
                index.put(app.componentKey, app);
            }
            appByComponent = Collections.unmodifiableMap(index);
            refreshVisibleApps();
            resolveLauncherConfiguration();
        });
    }

    private void refreshAliasCache() {
        Map<String, String> loaded = launcherPreferences.aliases();
        aliases = loaded;

        HashMap<String, String> normalized = new HashMap<>(Math.max(16, loaded.size() * 2));
        for (Map.Entry<String, String> entry : loaded.entrySet()) {
            String value = entry.getValue();
            if (value == null) continue;
            String clean = value.trim().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty()) normalized.put(entry.getKey(), clean);
        }
        normalizedAliases = Collections.unmodifiableMap(normalized);
    }

    private void refreshVisibleApps() {
        Set<String> hidden = launcherPreferences.hiddenComponents();
        ArrayList<AppEntry> visible = new ArrayList<>(allApps.size());
        for (AppEntry app : allApps) {
            if (!hidden.contains(app.componentKey)) visible.add(app);
        }
        apps = Collections.unmodifiableList(visible);
        filteredApps = AppRepository.filter(apps, query, normalizedAliases);
        surface.setApps(apps, filteredApps);
        surface.setHiddenAppCount(hidden.size());
    }

    private void resolveLauncherConfiguration() {
        maxHomeApps = launcherPreferences.homeMax();

        ArrayList<AppEntry> resolved = new ArrayList<>(maxHomeApps);
        ArrayList<String> labels = new ArrayList<>(maxHomeApps);
        Set<String> used = new HashSet<>();

        for (int index = 0; index < maxHomeApps; index++) {
            String componentName = launcherPreferences.homeSlot(index);
            AppEntry entry = findApp(componentName);

            if (componentName == null && !launcherPreferences.hasHomeSlot(index)) {
                entry = firstUnusedApp(used);
                if (entry != null) {
                    launcherPreferences.setHomeSlot(index, entry.componentKey);
                }
            }

            resolved.add(entry);
            if (entry != null) {
                String component = entry.componentKey;
                String alias = aliases.get(component);
                labels.add(alias == null || alias.isEmpty() ? entry.label : alias);
                used.add(component);
            } else {
                labels.add("");
            }
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
        surface.setHomeConfiguration(
                homeApps,
                Collections.unmodifiableList(labels),
                maxHomeApps,
                quickLabel
        );
        surface.setHiddenAppCount(launcherPreferences.hiddenComponents().size());
    }

    private AppEntry firstUnusedApp(Set<String> used) {
        for (AppEntry app : apps) {
            String component = app.componentKey;
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
        showPage(target, false);
    }

    private void showPage(int target, boolean focusSearch) {
        if (target != LauncherSurface.PAGE_APPS) appsBrowseMode = false;
        if (surface.getPage() == target) {
            if (target == LauncherSurface.PAGE_APPS && focusSearch) {
                if (search == null) addSearch(true);
                else focusSearchField();
            }
            return;
        }

        removeSearch();
        surface.setPage(target);

        if (target == LauncherSurface.PAGE_APPS) {
            long delay = uiConfig.pageDurationMs() == 0L
                    ? 0L
                    : uiConfig.pageDurationMs() + 20L;
            mainHandler.postDelayed(() -> {
                if (surface.getPage() == LauncherSurface.PAGE_APPS
                        && search == null
                        && !appsBrowseMode) {
                    addSearch(focusSearch);
                }
            }, delay);
        }
    }

    private void addSearch(boolean focus) {
        surface.setSearchActive(true);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(DesignTokens.TEXT_PRIMARY);
        search.setHintTextColor(DesignTokens.TEXT_TERTIARY);
        search.setHint("Search apps");
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.SEARCH_SP);
        search.setTypeface(DesignTokens.BODY);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
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
                filteredApps = AppRepository.filter(
                        apps,
                        query,
                        normalizedAliases
                );
                surface.setFilteredApps(filteredApps);
                scheduleSingleResultLaunch(query, filteredApps);
            }

            @Override public void afterTextChanged(Editable s) {
            }
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_GO
                    && !(event != null
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_UP)) {
                return false;
            }
            if (!filteredApps.isEmpty()) {
                launchApp(filteredApps.get(0));
                return true;
            }
            return false;
        });

        root.addView(search, searchLayoutParams());
        if (focus) focusSearchField();
    }

    private void focusSearchField() {
        if (search == null) return;
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
        cancelPendingSingleResultLaunch();
        if (search == null) {
            query = "";
            surface.setSearchActive(false);
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
        surface.setSearchActive(false);
        filteredApps = apps;
        surface.setFilteredApps(filteredApps);
    }

    private void scheduleSingleResultLaunch(String currentQuery, List<AppEntry> currentResults) {
        cancelPendingSingleResultLaunch();
        String normalizedQuery = currentQuery == null ? "" : currentQuery.trim();
        if (normalizedQuery.isEmpty() || currentResults.size() != 1) return;

        AppEntry only = currentResults.get(0);
        pendingSingleResultLaunch = () -> {
            pendingSingleResultLaunch = null;
            if (search == null
                    || surface.getPage() != LauncherSurface.PAGE_APPS
                    || !normalizedQuery.equals(query.trim())
                    || filteredApps.size() != 1
                    || filteredApps.get(0) != only) {
                return;
            }
            launchApp(only);
        };
        mainHandler.postDelayed(pendingSingleResultLaunch, 160L);
    }

    private void cancelPendingSingleResultLaunch() {
        if (pendingSingleResultLaunch == null) return;
        mainHandler.removeCallbacks(pendingSingleResultLaunch);
        pendingSingleResultLaunch = null;
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
        showPage(page, page == LauncherSurface.PAGE_APPS);
    }

    @Override public void onOpenApp(AppEntry app) {
        launchApp(app);
    }

    @Override public void onHomeSlotLongPressed(int index) {
        if (index < 0 || index >= maxHomeApps) return;
        showHomeSlotMenu(index);
    }

    @Override public void onAllAppsLongPressed(AppEntry app) {
        if (app == null) return;
        CharSequence[] actions = {"Add to Home", "Hide", "App info", "Uninstall"};
        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle(app.label)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showAddToHomeSlotPicker(app);
                            break;
                        case 1:
                            launcherPreferences.setHidden(app.componentKey, true);
                            refreshVisibleApps();
                            resolveLauncherConfiguration();
                            break;
                        case 2:
                            openAppInfo(app);
                            break;
                        case 3:
                            requestUninstall(app);
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    @Override public void onSettingAction(int action) {
        switch (action) {
            case LauncherSurface.ACTION_HOME_POSITION:
                launcherPreferences.setHomePosition(next(
                        uiConfig.homePosition,
                        LauncherPreferences.POSITION_TOP,
                        LauncherPreferences.POSITION_CENTER,
                        LauncherPreferences.POSITION_BOTTOM
                ));
                break;
            case LauncherSurface.ACTION_HOME_DENSITY:
                launcherPreferences.setDensity(next(
                        uiConfig.density,
                        LauncherPreferences.DENSITY_COMPACT,
                        LauncherPreferences.DENSITY_NORMAL,
                        LauncherPreferences.DENSITY_SPACIOUS
                ));
                break;
            case LauncherSurface.ACTION_HOME_TEXT:
                launcherPreferences.setTextSize(next(
                        uiConfig.textSize,
                        LauncherPreferences.TEXT_SMALL,
                        LauncherPreferences.TEXT_MEDIUM,
                        LauncherPreferences.TEXT_LARGE
                ));
                break;
            case LauncherSurface.ACTION_TOGGLE_TIME:
                launcherPreferences.setShowTime(!uiConfig.showTime);
                break;
            case LauncherSurface.ACTION_TOGGLE_DATE:
                launcherPreferences.setShowDate(!uiConfig.showDate);
                break;
            case LauncherSurface.ACTION_TOGGLE_WEATHER:
                launcherPreferences.setShowWeather(!uiConfig.showWeather);
                break;
            case LauncherSurface.ACTION_TOGGLE_BATTERY:
                launcherPreferences.setShowBattery(!uiConfig.showBattery);
                break;
            case LauncherSurface.ACTION_STATUS_LAYOUT:
                launcherPreferences.setStatusLayout(next(
                        uiConfig.statusLayout,
                        LauncherPreferences.STATUS_TIME_FIRST,
                        LauncherPreferences.STATUS_DATE_FIRST,
                        LauncherPreferences.STATUS_COMPACT
                ));
                break;
            case LauncherSurface.ACTION_CLOCK_FORMAT:
                launcherPreferences.setClockFormat(next(
                        uiConfig.clockFormat,
                        LauncherPreferences.CLOCK_SYSTEM,
                        LauncherPreferences.CLOCK_24,
                        LauncherPreferences.CLOCK_12
                ));
                break;
            case LauncherSurface.ACTION_DATE_STYLE:
                launcherPreferences.setDateStyle(next(
                        uiConfig.dateStyle,
                        LauncherPreferences.DATE_WEEKDAY,
                        LauncherPreferences.DATE_SHORT,
                        LauncherPreferences.DATE_NUMERIC
                ));
                break;
            case LauncherSurface.ACTION_WEATHER_MODE:
                launcherPreferences.setWeatherMode(next(
                        uiConfig.weatherMode,
                        LauncherPreferences.WEATHER_BOTH,
                        LauncherPreferences.WEATHER_TEMP,
                        LauncherPreferences.WEATHER_CONDITION
                ));
                break;
            case LauncherSurface.ACTION_BATTERY_MODE:
                launcherPreferences.setBatteryMode(next(
                        uiConfig.batteryMode,
                        LauncherPreferences.BATTERY_BOTH,
                        LauncherPreferences.BATTERY_ICON,
                        LauncherPreferences.BATTERY_PERCENT
                ));
                break;
            case LauncherSurface.ACTION_ANIMATION:
                launcherPreferences.setAnimationSpeed(next(
                        uiConfig.animationSpeed,
                        LauncherPreferences.ANIMATION_INSTANT,
                        LauncherPreferences.ANIMATION_FAST,
                        LauncherPreferences.ANIMATION_NORMAL
                ));
                break;
            case LauncherSurface.ACTION_HAPTICS:
                launcherPreferences.setHaptics(!uiConfig.haptics);
                break;
            case LauncherSurface.ACTION_HIDDEN_APPS:
                showHiddenAppsManager();
                return;
            case LauncherSurface.ACTION_EXPORT_CONFIG:
                exportConfiguration();
                return;
            case LauncherSurface.ACTION_IMPORT_CONFIG:
                importConfiguration();
                return;
            default:
                return;
        }

        applyUiConfiguration();
    }

    @Override public void onQuickLaunchRequested() {
        if (quickApp != null) {
            launchApp(quickApp);
        } else {
            showPage(LauncherSurface.PAGE_SETTINGS);
        }
    }

    @Override public void onAppsBrowseGestureStarted() {
        if (surface.getPage() != LauncherSurface.PAGE_APPS || appsBrowseMode) return;
        appsBrowseMode = true;
        removeSearch();
    }

    private void showHomeSlotMenu(int slot) {
        ArrayList<String> actions = new ArrayList<>();
        actions.add("Change app");

        AppEntry current = slot < homeApps.size() ? homeApps.get(slot) : null;
        if (current != null) actions.add("Rename");
        if (slot > 0) actions.add("Move up");
        if (slot + 1 < maxHomeApps) actions.add("Move down");
        actions.add("Clear slot");

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Home slot " + (slot + 1))
                .setItems(actions.toArray(new CharSequence[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    switch (action) {
                        case "Change app":
                            showHomeAppPicker(slot);
                            break;
                        case "Rename":
                            showRenameDialog(slot);
                            break;
                        case "Move up":
                            launcherPreferences.swapHomeSlots(slot, slot - 1);
                            resolveLauncherConfiguration();
                            break;
                        case "Move down":
                            launcherPreferences.swapHomeSlots(slot, slot + 1);
                            resolveLauncherConfiguration();
                            break;
                        case "Clear slot":
                            launcherPreferences.clearHomeSlot(slot);
                            resolveLauncherConfiguration();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHomeAppPicker(int slot) {
        if (allApps.isEmpty()) return;

        CharSequence[] labels = new CharSequence[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) labels[i] = allApps.get(i).label;

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Home app " + (slot + 1))
                .setItems(labels, (dialog, which) -> {
                    AppEntry selected = allApps.get(which);
                    launcherPreferences.setHomeSlot(
                            slot,
                            selected.componentKey
                    );
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog(int slot) {
        AppEntry app = slot < homeApps.size() ? homeApps.get(slot) : null;
        if (app == null) return;

        String component = app.componentKey;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(DesignTokens.WHITE);
        input.setHintTextColor(DesignTokens.WHITE);
        input.setBackgroundColor(DesignTokens.BLACK);
        input.setHint(app.label);
        String existing = launcherPreferences.alias(component);
        if (!existing.isEmpty()) {
            input.setText(existing);
            input.setSelection(existing.length());
        }

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Rename " + app.label)
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    launcherPreferences.setAlias(component, input.getText().toString());
                    refreshAliasCache();
                    refreshVisibleApps();
                    resolveLauncherConfiguration();
                })
                .setNeutralButton("Reset", (dialog, which) -> {
                    launcherPreferences.setAlias(component, "");
                    refreshAliasCache();
                    refreshVisibleApps();
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddToHomeSlotPicker(AppEntry app) {
        CharSequence[] slots = new CharSequence[maxHomeApps];
        for (int i = 0; i < maxHomeApps; i++) {
            AppEntry existing = i < homeApps.size() ? homeApps.get(i) : null;
            String name = existing == null ? "Empty" : existing.label;
            slots[i] = (i + 1) + " · " + name;
        }

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Add " + app.label)
                .setItems(slots, (dialog, which) -> {
                    launcherPreferences.setHomeSlot(
                            which,
                            app.componentKey
                    );
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAppInfo(AppEntry app) {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.component.getPackageName())
        );
        startActivity(intent);
    }

    private void requestUninstall(AppEntry app) {
        Intent intent = new Intent(
                Intent.ACTION_DELETE,
                Uri.parse("package:" + app.component.getPackageName())
        );
        startActivity(intent);
    }

    private void showQuickAppPicker() {
        if (allApps.isEmpty()) return;

        CharSequence[] labels = new CharSequence[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) labels[i] = allApps.get(i).label;

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Swipe-up app")
                .setItems(labels, (picker, which) -> {
                    AppEntry selected = allApps.get(which);
                    launcherPreferences.setQuickApp(selected.componentKey);
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

    @Override public void onClockTapped() {
        launchExternalIntent(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
    }

    @Override public void onDateTapped() {
        Uri uri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(Long.toString(System.currentTimeMillis()))
                .build();
        launchExternalIntent(new Intent(Intent.ACTION_VIEW, uri));
    }

    @Override public void onBatteryTapped() {
        Intent primary = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        if (!launchExternalIntent(primary)) {
            launchExternalIntent(new Intent(Settings.ACTION_BATTERY_SETTINGS));
        }
    }

    private boolean launchExternalIntent(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            return false;
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

    private static String next(String current, String... values) {
        if (values.length == 0) return current;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private void showHiddenAppsManager() {
        if (allApps.isEmpty()) return;

        CharSequence[] labels = new CharSequence[allApps.size()];
        boolean[] checked = new boolean[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) {
            AppEntry app = allApps.get(i);
            labels[i] = app.label;
            checked[i] = launcherPreferences.isHidden(app.componentKey);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Hidden apps")
                .setMultiChoiceItems(labels, checked, (picker, which, isChecked) -> {
                    AppEntry app = allApps.get(which);
                    launcherPreferences.setHidden(
                            app.componentKey,
                            isChecked
                    );
                })
                .setPositiveButton("Done", (picker, which) -> {
                    refreshVisibleApps();
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", (picker, which) -> {
                    // Choices apply immediately; rebuild state so the screen is always consistent.
                    refreshVisibleApps();
                    resolveLauncherConfiguration();
                })
                .create();
        dialog.show();
    }

    private void exportConfiguration() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "vs-launcher-config.json");
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG);
    }

    private void importConfiguration() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_CONFIG) {
            writeConfiguration(uri);
        } else if (requestCode == REQUEST_IMPORT_CONFIG) {
            readConfiguration(uri);
        }
    }

    private void writeConfiguration(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("Unable to open destination");
            output.write(launcherPreferences.exportJson().getBytes(StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(this, "Configuration exported", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void readConfiguration(Uri uri) {
        try (BufferedInputStream input = new BufferedInputStream(
                getContentResolver().openInputStream(uri)
        ); ByteArrayOutputStream output = new ByteArrayOutputStream(4096)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 256 * 1024) {
                    throw new IllegalArgumentException("Configuration is too large");
                }
                output.write(buffer, 0, count);
            }

            launcherPreferences.importJson(
                    new String(output.toByteArray(), StandardCharsets.UTF_8)
            );
            applyUiConfiguration();
            refreshAliasCache();
            refreshVisibleApps();
            resolveLauncherConfiguration();
            Toast.makeText(this, "Configuration imported", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
