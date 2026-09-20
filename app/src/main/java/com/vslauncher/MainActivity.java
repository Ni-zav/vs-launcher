package com.vslauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Color;
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
    private List<LauncherProfile> launcherProfiles = Collections.emptyList();
    private Map<String, AppEntry> appByComponent = Collections.emptyMap();
    private Map<String, String> aliases = Collections.emptyMap();
    private Map<String, String> normalizedAliases = Collections.emptyMap();
    private Map<String, String> aliasInitials = Collections.emptyMap();
    private List<AppEntry> filteredApps = Collections.emptyList();
    private List<SearchResult> searchResults = Collections.emptyList();
    private List<AppEntry> homeApps = Collections.emptyList();
    private AppEntry quickApp;
    private String query = "";
    private String normalizedQuery = "";
    private String latestWeatherText = "Tap for weather";
    private int bottomInset;
    private int maxHomeApps = 5;
    private boolean packageReceiverRegistered;
    private boolean appsBrowseMode;
    private Runnable pendingSingleResultLaunch;
    private Runnable undoAction;
    private Runnable pendingUndoClear;

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
        appRepository.setChangeCallback(this::reloadApps);
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
        clearUndo();
        removeSearch();
        unregisterPackageChanges();
        appRepository.close();
        weatherService.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (surface.getPage() == LauncherSurface.PAGE_APPS) {
            if (search != null) {
                enterAppsBrowseMode();
            } else {
                showPage(LauncherSurface.PAGE_HOME);
            }
            return;
        }

        if (surface.getPage() == LauncherSurface.PAGE_SETTINGS) {
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
        appRepository.load((loaded, profiles) -> {
            allApps = loaded;
            launcherProfiles = profiles;
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
        HashMap<String, String> initials = new HashMap<>(Math.max(16, loaded.size() * 2));
        for (Map.Entry<String, String> entry : loaded.entrySet()) {
            String value = entry.getValue();
            if (value == null) continue;
            String clean = SearchNormalization.normalize(value);
            if (!clean.isEmpty()) {
                normalized.put(entry.getKey(), clean);
                String aliasInitial = SearchRanking.initials(value);
                if (!aliasInitial.isEmpty()) initials.put(entry.getKey(), aliasInitial);
            }
        }
        normalizedAliases = Collections.unmodifiableMap(normalized);
        aliasInitials = Collections.unmodifiableMap(initials);
    }

    private void refreshVisibleApps() {
        Set<String> hidden = launcherPreferences.hiddenComponents();
        boolean privateVisible = launcherPreferences.privateSpaceVisible();
        ArrayList<AppEntry> visible = new ArrayList<>(allApps.size());
        for (AppEntry app : allApps) {
            if (hidden.contains(app.componentKey)) continue;
            if (!ProfilePolicy.visibleInApps(app.profileKind, privateVisible)) continue;
            visible.add(app);
        }
        apps = Collections.unmodifiableList(visible);
        filteredApps = AppRepository.filterNormalized(apps, normalizedQuery, normalizedAliases, aliasInitials);
        searchResults = buildSearchResults(query, normalizedQuery, filteredApps);
        surface.setApps(apps, filteredApps);
        surface.setSearchResults(searchResults, !normalizedQuery.isEmpty());
        surface.setBrowseItems(buildBrowseItems());
        surface.setHiddenAppCount(hidden.size());
    }

    private List<AppListItem> buildBrowseItems() {
        ArrayList<AppListItem> rows = new ArrayList<>(apps.size() + launcherProfiles.size());

        for (AppEntry app : apps) {
            if (app.profileKind == AppEntry.PROFILE_PERSONAL) {
                rows.add(AppListItem.app(app));
            }
        }

        boolean privateVisible = launcherPreferences.privateSpaceVisible();
        for (LauncherProfile profile : launcherProfiles) {
            if (profile.kind == AppEntry.PROFILE_PERSONAL) continue;
            if (!ProfilePolicy.visibleInApps(profile.kind, privateVisible)) continue;

            String value = ProfilePolicy.headerValue(profile.kind, profile.quiet);
            rows.add(AppListItem.profile(
                    profile.kind,
                    profile.serial,
                    profile.label(),
                    value
            ));

            if (profile.quiet) continue;
            for (AppEntry app : apps) {
                if (app.userSerial == profile.serial) rows.add(AppListItem.app(app));
            }
        }

        return Collections.unmodifiableList(rows);
    }

    private void resolveLauncherConfiguration() {
        maxHomeApps = launcherPreferences.homeMax();

        ArrayList<AppEntry> resolved = new ArrayList<>(maxHomeApps);
        ArrayList<String> labels = new ArrayList<>(maxHomeApps);
        ArrayList<String> shortcutIds = new ArrayList<>(maxHomeApps);
        Set<String> used = new HashSet<>();

        for (int index = 0; index < maxHomeApps; index++) {
            String componentName = launcherPreferences.homeSlot(index);
            String shortcutId = launcherPreferences.homeShortcutId(index);
            AppEntry entry = findHomeEligibleApp(componentName);

            if (componentName == null && !launcherPreferences.hasHomeSlot(index)) {
                entry = firstUnusedApp(used);
                if (entry != null) {
                    launcherPreferences.setHomeSlot(index, entry.componentKey);
                }
            }

            resolved.add(entry);
            shortcutIds.add(shortcutId == null ? "" : shortcutId);
            if (entry != null) {
                String component = entry.componentKey;
                if (shortcutId != null && !shortcutId.isEmpty()) {
                    String shortcutLabel = launcherPreferences.homeShortcutLabel(index);
                    labels.add(
                            shortcutLabel == null || shortcutLabel.isEmpty()
                                    ? entry.label
                                    : shortcutLabel
                    );
                } else {
                    String alias = aliases.get(component);
                    labels.add(alias == null || alias.isEmpty() ? entry.label : alias);
                    used.add(component);
                }
            } else if (componentName != null && !componentName.isEmpty()) {
                labels.add(unavailableHomeLabel(componentName));
            } else {
                labels.add("");
            }
        }

        String quickComponent = launcherPreferences.quickApp();
        quickApp = findHomeEligibleApp(quickComponent);
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
                Collections.unmodifiableList(shortcutIds),
                maxHomeApps,
                quickLabel
        );
        surface.setHiddenAppCount(launcherPreferences.hiddenComponents().size());
    }

    private String unavailableHomeLabel(String componentKey) {
        int marker = componentKey.lastIndexOf("@u");
        if (marker >= 0 && marker + 2 < componentKey.length()) {
            try {
                long serial = Long.parseLong(componentKey.substring(marker + 2));
                for (LauncherProfile profile : launcherProfiles) {
                    if (profile.serial != serial) continue;
                    if (profile.kind == AppEntry.PROFILE_WORK && profile.quiet) {
                        return "Work paused";
                    }
                    if (profile.kind == AppEntry.PROFILE_PRIVATE) {
                        return "Private app unavailable";
                    }
                    return "Profile unavailable";
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return "App unavailable";
    }

    private AppEntry firstUnusedApp(Set<String> used) {
        for (AppEntry app : apps) {
            if (!ProfilePolicy.canPersistOnHome(app.profileKind)) continue;
            String component = app.componentKey;
            if (!used.contains(component)) return app;
        }
        return null;
    }

    private AppEntry findApp(String flattenedComponent) {
        if (flattenedComponent == null || flattenedComponent.isEmpty()) return null;
        return appByComponent.get(flattenedComponent);
    }

    private AppEntry findHomeEligibleApp(String componentKey) {
        AppEntry app = findApp(componentKey);
        return app != null && ProfilePolicy.canPersistOnHome(app.profileKind) ? app : null;
    }

    private List<AppEntry> homeEligibleApps() {
        ArrayList<AppEntry> eligible = new ArrayList<>(allApps.size());
        for (AppEntry app : allApps) {
            if (ProfilePolicy.canPersistOnHome(app.profileKind)) eligible.add(app);
        }
        return eligible;
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerPackageChanges() {
        if (packageReceiverRegistered) return;

        // LauncherApps.Callback handles app/package changes across profiles.
        // These generic profile broadcasts handle add/remove and quiet-mode transitions.
        IntentFilter filter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= 34) {
            filter.addAction(Intent.ACTION_PROFILE_ADDED);
            filter.addAction(Intent.ACTION_PROFILE_REMOVED);
        }
        if (Build.VERSION.SDK_INT >= 35) {
            filter.addAction(Intent.ACTION_PROFILE_AVAILABLE);
            filter.addAction(Intent.ACTION_PROFILE_UNAVAILABLE);
        } else {
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        }
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);

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
        if (target == LauncherSurface.PAGE_APPS && focusSearch) {
            appsBrowseMode = false;
            surface.setSearchActive(true);
        }
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
        filteredApps = AppRepository.filterNormalized(apps, normalizedQuery, normalizedAliases, aliasInitials);
        searchResults = buildSearchResults(query, normalizedQuery, filteredApps);
        surface.setFilteredApps(filteredApps);
        surface.setSearchResults(searchResults, !normalizedQuery.isEmpty());

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(DesignTokens.TEXT_PRIMARY);
        search.setHintTextColor(DesignTokens.TEXT_TERTIARY);
        search.setHint("Search");
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.SEARCH_SP);
        search.setTypeface(DesignTokens.BODY);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setBackground(null);
        search.setPadding(dp(8f), 0, dp(8f), 0);
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
                normalizedQuery = SearchNormalization.normalize(query);
                filteredApps = AppRepository.filterNormalized(
                        apps,
                        normalizedQuery,
                        normalizedAliases,
                        aliasInitials
                );
                searchResults = buildSearchResults(query, normalizedQuery, filteredApps);
                surface.setFilteredApps(filteredApps);
                surface.setSearchResults(searchResults, !normalizedQuery.isEmpty());
                scheduleSingleResultLaunch(normalizedQuery, searchResults);
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
            if (!searchResults.isEmpty()) {
                executeSearchResult(searchResults.get(0));
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
                dp(48f),
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
            normalizedQuery = "";
            surface.setSearchActive(false);
            filteredApps = apps;
            searchResults = Collections.emptyList();
            surface.setFilteredApps(filteredApps);
            surface.setSearchResults(searchResults, !normalizedQuery.isEmpty());
            return;
        }

        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);

        root.removeView(search);
        search = null;
        query = "";
        normalizedQuery = "";
        surface.setSearchActive(false);
        filteredApps = apps;
        searchResults = Collections.emptyList();
        surface.setFilteredApps(filteredApps);
        surface.setSearchResults(searchResults, !normalizedQuery.isEmpty());
    }

    private List<SearchResult> buildSearchResults(
            String rawQuery,
            String normalizedQuery,
            List<AppEntry> appMatches
    ) {
        ArrayList<SearchResult> results = new ArrayList<>(
                appMatches.size() + 6
        );
        for (AppEntry app : appMatches) results.add(SearchResult.app(app));

        if (!normalizedQuery.isEmpty()) {
            for (SearchCommand command : SearchCommand.matchingNormalized(normalizedQuery)) {
                results.add(SearchResult.command(command));
            }

            String dial = QueryActions.dialPayload(rawQuery);
            if (dial != null) results.add(SearchResult.dial(dial));

            String url = QueryActions.urlPayload(rawQuery);
            if (url != null) results.add(SearchResult.url(url));

            TimeQueryActions.TimerSpec timer = TimeQueryActions.timer(rawQuery);
            if (timer != null) results.add(SearchResult.timer(timer));

            TimeQueryActions.AlarmSpec alarm = TimeQueryActions.alarmNormalized(normalizedQuery);
            if (alarm != null) results.add(SearchResult.alarm(alarm));

            String calculation = CalculatorAction.evaluate(rawQuery);
            if (calculation != null) results.add(SearchResult.calculation(calculation));

            if (QueryActions.shouldOfferWebFallback(normalizedQuery, results.size())) {
                results.add(SearchResult.web(rawQuery.trim()));
            }
        }

        return results.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(results);
    }

    private void executeSearchResult(SearchResult result) {
        if (result == null) return;
        cancelPendingSingleResultLaunch();

        if (result.isApp()) {
            launchApp(result.app);
            return;
        }

        if (result.type == SearchResult.TYPE_DIAL) {
            launchExternalIntent(new Intent(
                    Intent.ACTION_DIAL,
                    Uri.fromParts("tel", result.payload, null)
            ));
            return;
        }

        if (result.type == SearchResult.TYPE_URL) {
            launchExternalIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(result.payload)));
            return;
        }

        if (result.type == SearchResult.TYPE_TIMER) {
            try {
                int seconds = Integer.parseInt(result.payload);
                Intent timer = new Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "VS Launcher")
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                if (launchExternalIntent(timer)) showTransientStatus("Timer set");
            } catch (NumberFormatException ignored) {
            }
            return;
        }

        if (result.type == SearchResult.TYPE_ALARM) {
            String[] parts = result.payload.split(":", 2);
            if (parts.length == 2) {
                try {
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    Intent alarm = new Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, "VS Launcher")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                    if (launchExternalIntent(alarm)) showTransientStatus("Alarm set · " + result.payload);
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }

        if (result.type == SearchResult.TYPE_CALC) {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Calculation", result.payload));
                showTransientStatus("Copied · " + result.payload);
            }
            return;
        }

        if (result.type == SearchResult.TYPE_WEB) {
            Intent web = new Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, result.payload);
            if (!launchExternalIntent(web)) {
                Uri fallback = new Uri.Builder()
                        .scheme("https")
                        .authority("www.google.com")
                        .path("search")
                        .appendQueryParameter("q", result.payload)
                        .build();
                launchExternalIntent(new Intent(Intent.ACTION_VIEW, fallback));
            }
            return;
        }

        if (result.type == SearchResult.TYPE_COMMAND) {
            executeSystemCommand(result.id);
        }
    }

    private void executeSystemCommand(String id) {
        switch (id) {
            case SearchCommand.WIFI:
                launchSettingsPanelOrFallback(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                ? Settings.Panel.ACTION_WIFI : null,
                        Settings.ACTION_WIFI_SETTINGS
                );
                break;
            case SearchCommand.INTERNET:
                launchSettingsPanelOrFallback(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : null,
                        Settings.ACTION_WIRELESS_SETTINGS
                );
                break;
            case SearchCommand.VOLUME:
                launchSettingsPanelOrFallback(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                ? Settings.Panel.ACTION_VOLUME : null,
                        Settings.ACTION_SOUND_SETTINGS
                );
                break;
            case SearchCommand.NFC:
                launchSettingsPanelOrFallback(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                ? Settings.Panel.ACTION_NFC : null,
                        Settings.ACTION_NFC_SETTINGS
                );
                break;
            case SearchCommand.BLUETOOTH:
                launchExternalIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                break;
            case SearchCommand.BATTERY:
                onBatteryTapped();
                break;
            case SearchCommand.SETTINGS:
                launchExternalIntent(new Intent(Settings.ACTION_SETTINGS));
                break;
            case SearchCommand.LAUNCHER_SETTINGS:
                showPage(LauncherSurface.PAGE_SETTINGS);
                break;
            case SearchCommand.ALARMS:
                onClockTapped();
                break;
            case SearchCommand.CALENDAR:
                onDateTapped();
                break;
            case SearchCommand.STORAGE:
                launchExternalIntent(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
                break;
            case SearchCommand.KEYBOARD:
                launchExternalIntent(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                break;
            case SearchCommand.DISPLAY:
                launchExternalIntent(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                break;
            case SearchCommand.SOUND:
                launchExternalIntent(new Intent(Settings.ACTION_SOUND_SETTINGS));
                break;
            case SearchCommand.LOCATION:
                launchExternalIntent(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                break;
            case SearchCommand.NOTIFICATIONS:
                if (Build.VERSION.SDK_INT >= 33) {
                    launchExternalIntent(new Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS));
                } else {
                    launchExternalIntent(new Intent(Settings.ACTION_SETTINGS));
                }
                break;
            case SearchCommand.HELP:
                showHowToUseDialog();
                break;
            default:
                break;
        }
    }

    private void launchSettingsPanelOrFallback(String panelAction, String fallbackAction) {
        if (panelAction != null && launchExternalIntent(new Intent(panelAction))) return;
        launchExternalIntent(new Intent(fallbackAction));
    }

    private void scheduleSingleResultLaunch(
            String currentNormalizedQuery,
            List<SearchResult> currentResults
    ) {
        cancelPendingSingleResultLaunch();
        if (currentNormalizedQuery.isEmpty()) return;

        SearchResult onlyApp = singleAppResult(currentResults);
        if (onlyApp == null) return;

        pendingSingleResultLaunch = () -> {
            pendingSingleResultLaunch = null;
            if (search == null
                    || surface.getPage() != LauncherSurface.PAGE_APPS
                    || !currentNormalizedQuery.equals(normalizedQuery)
                    || singleAppResult(searchResults) != onlyApp) {
                return;
            }
            launchApp(onlyApp.app);
        };
        mainHandler.postDelayed(pendingSingleResultLaunch, 160L);
    }

    private static SearchResult singleAppResult(List<SearchResult> results) {
        SearchResult only = null;
        for (SearchResult result : results) {
            if (result.blocksAppAutoLaunch()) return null;
            if (!result.isApp()) continue;
            if (only != null) return null;
            only = result;
        }
        return only;
    }

    private void cancelPendingSingleResultLaunch() {
        if (pendingSingleResultLaunch == null) return;
        mainHandler.removeCallbacks(pendingSingleResultLaunch);
        pendingSingleResultLaunch = null;
    }

    @Override public void onPageRequested(int page) {
        showPage(page, page == LauncherSurface.PAGE_APPS);
    }

    @Override public void onOpenApp(AppEntry app) {
        launchApp(app);
    }

    @Override public void onOpenHomeShortcut(AppEntry app, String shortcutId) {
        if (!appRepository.startShortcut(app, shortcutId)) reloadApps();
    }

    @Override public void onSearchResultTapped(SearchResult result) {
        executeSearchResult(result);
    }

    @Override public void onHomeSlotLongPressed(int index) {
        if (index < 0 || index >= maxHomeApps) return;
        showHomeSlotMenu(index);
    }

    @Override public void onEmptyHomeSlotTapped(int index) {
        if (index < 0 || index >= maxHomeApps) return;
        showHomeAppPicker(index);
    }

    @Override public void onAllAppsLongPressed(AppEntry app) {
        if (app == null) return;
        appRepository.loadShortcuts(app, shortcuts -> showAppActions(app, shortcuts));
    }

    private void showAppActions(AppEntry app, List<ShortcutInfo> shortcuts) {
        ArrayList<String> actions = new ArrayList<>();
        ArrayList<ShortcutInfo> shortcutActions = new ArrayList<>();

        int shortcutCount = Math.min(4, shortcuts.size());
        for (int i = 0; i < shortcutCount; i++) {
            ShortcutInfo shortcut = shortcuts.get(i);
            CharSequence label = shortcut.getShortLabel();
            if (label == null || label.length() == 0) continue;
            actions.add(label.toString());
            shortcutActions.add(shortcut);
        }

        int shortcutActionCount = actions.size();
        if (shortcutActionCount > 0 && ProfilePolicy.canPersistOnHome(app.profileKind)) {
            actions.add("Pin shortcut…");
        }
        if (ProfilePolicy.canPersistOnHome(app.profileKind)) actions.add("Add to Home");
        actions.add("Hide");
        actions.add("App info");
        if (app.isPersonal()) actions.add("Uninstall");

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle(app.pickerLabel())
                .setItems(actions.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which < shortcutActionCount) {
                        if (!appRepository.startShortcut(app, shortcutActions.get(which))) {
                            reloadApps();
                        }
                        return;
                    }

                    String action = actions.get(which);
                    switch (action) {
                        case "Pin shortcut…":
                            showShortcutPinPicker(app, shortcutActions);
                            break;
                        case "Add to Home":
                            showAddToHomeSlotPicker(app);
                            break;
                        case "Hide":
                            launcherPreferences.setHidden(app.componentKey, true);
                            refreshVisibleApps();
                            resolveLauncherConfiguration();
                            showUndo(app.label + " hidden", () -> {
                                launcherPreferences.setHidden(app.componentKey, false);
                                refreshVisibleApps();
                                resolveLauncherConfiguration();
                            });
                            break;
                        case "App info":
                            openAppInfo(app);
                            break;
                        case "Uninstall":
                            requestUninstall(app);
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override public void onProfileHeaderTapped(int profileKind, long profileSerial) {
        LauncherProfile profile = findProfile(profileKind, profileSerial);
        if (profile == null) return;

        appRepository.requestQuietMode(!profile.quiet, profile.user);
        mainHandler.postDelayed(this::reloadApps, 500L);
    }

    private LauncherProfile findProfile(int kind, long serial) {
        for (LauncherProfile profile : launcherProfiles) {
            if (profile.kind == kind && profile.serial == serial) return profile;
        }
        return null;
    }

    private boolean hasPrivateProfile() {
        for (LauncherProfile profile : launcherProfiles) {
            if (profile.kind == AppEntry.PROFILE_PRIVATE) return true;
        }
        return false;
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
            case LauncherSurface.ACTION_HELP:
                showHowToUseDialog();
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
        enterAppsBrowseMode();
    }

    @Override public void onAppsSearchRequested() {
        if (surface.getPage() != LauncherSurface.PAGE_APPS) return;
        appsBrowseMode = false;
        if (search == null) addSearch(true);
        else focusSearchField();
    }

    private void enterAppsBrowseMode() {
        appsBrowseMode = true;
        removeSearch();
    }

    private void showHomeSlotMenu(int slot) {
        ArrayList<String> actions = new ArrayList<>();
        actions.add("Change app");

        AppEntry current = slot < homeApps.size() ? homeApps.get(slot) : null;
        String currentShortcutId = launcherPreferences.homeShortcutId(slot);
        boolean shortcutSlot = currentShortcutId != null && !currentShortcutId.isEmpty();
        if (current != null && !shortcutSlot) actions.add("Rename");
        if (slot > 0) actions.add("Move up");
        if (slot + 1 < maxHomeApps) actions.add("Move down");
        actions.add(shortcutSlot ? "Remove shortcut" : "Clear slot");

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
                        case "Remove shortcut":
                            clearHomeSlotWithUndo(slot, shortcutSlot);
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHomeAppPicker(int slot) {
        List<AppEntry> eligible = homeEligibleApps();
        if (eligible.isEmpty()) return;

        CharSequence[] labels = new CharSequence[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) labels[i] = eligible.get(i).pickerLabel();

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Home app " + (slot + 1))
                .setItems(labels, (dialog, which) -> {
                    AppEntry selected = eligible.get(which);
                    AppEntry oldShortcutApp = shortcutAppAtSlot(slot);
                    launcherPreferences.setHomeSlot(slot, selected.componentKey);
                    if (oldShortcutApp != null) repinPackageShortcuts(oldShortcutApp);
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
        input.setTextColor(DesignTokens.TEXT_PRIMARY);
        input.setHintTextColor(DesignTokens.TEXT_TERTIARY);
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
                .setTitle("Add " + app.pickerLabel())
                .setItems(slots, (dialog, which) -> {
                    AppEntry oldShortcutApp = shortcutAppAtSlot(which);
                    launcherPreferences.setHomeSlot(which, app.componentKey);
                    if (oldShortcutApp != null) repinPackageShortcuts(oldShortcutApp);
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showShortcutPinPicker(AppEntry app, List<ShortcutInfo> shortcuts) {
        if (shortcuts.isEmpty()) return;

        CharSequence[] labels = new CharSequence[shortcuts.size()];
        for (int i = 0; i < shortcuts.size(); i++) {
            CharSequence shortLabel = shortcuts.get(i).getShortLabel();
            labels[i] = shortLabel == null || shortLabel.length() == 0
                    ? "Shortcut"
                    : shortLabel;
        }

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Pin shortcut")
                .setItems(labels, (dialog, which) ->
                        showShortcutHomeSlotPicker(app, shortcuts.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showShortcutHomeSlotPicker(AppEntry app, ShortcutInfo shortcut) {
        CharSequence[] slots = new CharSequence[maxHomeApps];
        for (int i = 0; i < maxHomeApps; i++) {
            String label = i < homeApps.size() && homeApps.get(i) != null
                    ? surfaceHomeLabel(i)
                    : "Empty";
            slots[i] = (i + 1) + " · " + label;
        }

        CharSequence shortLabel = shortcut.getShortLabel();
        String label = shortLabel == null || shortLabel.length() == 0
                ? app.label
                : shortLabel.toString();

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Pin " + label)
                .setItems(slots, (dialog, slot) ->
                        pinShortcutToHome(app, shortcut.getId(), label, slot))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String surfaceHomeLabel(int slot) {
        String shortcutLabel = launcherPreferences.homeShortcutLabel(slot);
        if (shortcutLabel != null && !shortcutLabel.isEmpty()) return shortcutLabel;

        AppEntry existing = slot < homeApps.size() ? homeApps.get(slot) : null;
        if (existing == null) return "Empty";
        String alias = aliases.get(existing.componentKey);
        return alias == null || alias.isEmpty() ? existing.label : alias;
    }

    private void pinShortcutToHome(
            AppEntry app,
            String shortcutId,
            String label,
            int slot
    ) {
        if (app == null || shortcutId == null || shortcutId.isEmpty()) return;

        ArrayList<String> targetPinned = shortcutIdsForPackage(app, slot, shortcutId);
        if (!appRepository.pinShortcuts(app, targetPinned)) {
            Toast.makeText(this, "Shortcut could not be pinned", Toast.LENGTH_SHORT).show();
            return;
        }

        AppEntry oldShortcutApp = shortcutAppAtSlot(slot);
        launcherPreferences.setHomeShortcut(slot, app.componentKey, shortcutId, label);
        if (oldShortcutApp != null
                && !oldShortcutApp.componentKey.equals(app.componentKey)) {
            repinPackageShortcuts(oldShortcutApp);
        }
        resolveLauncherConfiguration();
    }

    private ArrayList<String> shortcutIdsForPackage(
            AppEntry app,
            int replacingSlot,
            String replacementId
    ) {
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < LauncherPreferences.MAX_HOME_APPS; i++) {
            if (i == replacingSlot) continue;
            if (!app.componentKey.equals(launcherPreferences.homeSlot(i))) continue;
            String id = launcherPreferences.homeShortcutId(i);
            if (id != null && !id.isEmpty()) ids.add(id);
        }
        if (replacementId != null && !replacementId.isEmpty()) ids.add(replacementId);
        return new ArrayList<>(ids);
    }

    private AppEntry shortcutAppAtSlot(int slot) {
        String id = launcherPreferences.homeShortcutId(slot);
        if (id == null || id.isEmpty()) return null;
        return findApp(launcherPreferences.homeSlot(slot));
    }

    private void repinPackageShortcuts(AppEntry app) {
        if (app == null) return;
        appRepository.pinShortcuts(
                app,
                shortcutIdsForPackage(app, -1, null)
        );
    }

    private void clearHomeSlotWithUndo(int slot, boolean shortcutSlot) {
        String oldComponent = launcherPreferences.homeSlot(slot);
        String oldShortcutId = launcherPreferences.homeShortcutId(slot);
        String oldShortcutLabel = launcherPreferences.homeShortcutLabel(slot);
        boolean oldExists = launcherPreferences.hasHomeSlot(slot);
        AppEntry oldShortcutApp = shortcutAppAtSlot(slot);

        launcherPreferences.clearHomeSlot(slot);
        if (oldShortcutApp != null) repinPackageShortcuts(oldShortcutApp);
        resolveLauncherConfiguration();

        showUndo(shortcutSlot ? "Shortcut removed" : "Home slot cleared", () -> {
            if (oldShortcutId != null && oldComponent != null) {
                launcherPreferences.setHomeShortcut(
                        slot,
                        oldComponent,
                        oldShortcutId,
                        oldShortcutLabel
                );
                AppEntry restored = findApp(oldComponent);
                if (restored != null) repinPackageShortcuts(restored);
            } else if (oldExists) {
                launcherPreferences.setHomeSlot(slot, oldComponent);
            } else {
                launcherPreferences.setHomeSlot(slot, null);
            }
            resolveLauncherConfiguration();
        });
    }

    private void openAppInfo(AppEntry app) {
        if (!appRepository.startAppDetails(app)) {
            Intent fallback = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + app.component.getPackageName())
            );
            launchExternalIntent(fallback);
        }
    }

    private void requestUninstall(AppEntry app) {
        if (app == null || !app.isPersonal()) {
            openAppInfo(app);
            return;
        }
        Intent intent = new Intent(
                Intent.ACTION_DELETE,
                Uri.parse("package:" + app.component.getPackageName())
        );
        startActivity(intent);
    }

    private void showQuickAppPicker() {
        List<AppEntry> eligible = homeEligibleApps();
        if (eligible.isEmpty()) return;

        CharSequence[] labels = new CharSequence[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) labels[i] = eligible.get(i).pickerLabel();

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Swipe-up app")
                .setItems(labels, (picker, which) -> {
                    AppEntry selected = eligible.get(which);
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
        if (!appRepository.startApp(app)) reloadApps();
    }

    private void showUndo(String message, Runnable action) {
        clearUndo();
        undoAction = action;
        surface.setTransientMessage(message, true);

        pendingUndoClear = this::clearUndo;
        mainHandler.postDelayed(pendingUndoClear, 2500L);
    }

    private void showTransientStatus(String message) {
        clearUndo();
        surface.setTransientMessage(message, false);
        pendingUndoClear = this::clearUndo;
        mainHandler.postDelayed(pendingUndoClear, 2500L);
    }

    private void clearUndo() {
        if (pendingUndoClear != null) {
            mainHandler.removeCallbacks(pendingUndoClear);
            pendingUndoClear = null;
        }
        undoAction = null;
        if (surface != null) surface.setTransientMessage("", false);
    }

    @Override public void onUndoRequested() {
        Runnable action = undoAction;
        clearUndo();
        if (action != null) action.run();
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
            launchExternalIntent(new Intent(Settings.ACTION_SETTINGS));
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

    private void showHowToUseDialog() {
        String guide =
                "HOME\n"
                        + "Swipe left: search · right: settings · up: quick app\n"
                        + "Tap time/date/battery/weather for their native actions.\n\n"
                        + "SEARCH\n"
                        + "Type an app name. One stable app result opens automatically.\n"
                        + "Go/Enter runs the first result. Useful queries:\n"
                        + "timer 10m · alarm 07:30 · 23*17 · wifi · example.com\n"
                        + "Type help to reopen this guide.\n\n"
                        + "APPS\n"
                        + "Start scrolling search to enter browse mode. Use A–Z + # on the right.\n"
                        + "Tap APPS or pull down at the top to search again.\n\n"
                        + "LONG PRESS\n"
                        + "Apps: shortcuts, pin to Home, hide, info, uninstall.\n"
                        + "Home rows: change, rename, move, or clear.\n\n"
                        + "BACK\n"
                        + "Search → Browse → Home.";

        new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("How to use VS")
                .setMessage(guide)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showHiddenAppsManager() {
        boolean hasPrivate = hasPrivateProfile();
        int offset = hasPrivate ? 1 : 0;
        int count = allApps.size() + offset;
        if (count == 0) return;

        CharSequence[] labels = new CharSequence[count];
        boolean[] checked = new boolean[count];

        if (hasPrivate) {
            labels[0] = "Hide Private Space";
            checked[0] = !launcherPreferences.privateSpaceVisible();
        }

        for (int i = 0; i < allApps.size(); i++) {
            AppEntry app = allApps.get(i);
            labels[i + offset] = app.pickerLabel();
            checked[i + offset] = launcherPreferences.isHidden(app.componentKey);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_VsLauncher_Dialog)
                .setTitle("Hidden apps")
                .setMultiChoiceItems(labels, checked, (picker, which, isChecked) -> {
                    if (hasPrivate && which == 0) {
                        launcherPreferences.setPrivateSpaceVisible(!isChecked);
                        return;
                    }

                    int appIndex = which - offset;
                    if (appIndex < 0 || appIndex >= allApps.size()) return;
                    AppEntry app = allApps.get(appIndex);
                    launcherPreferences.setHidden(app.componentKey, isChecked);
                })
                .setPositiveButton("Done", (picker, which) -> {
                    refreshVisibleApps();
                    resolveLauncherConfiguration();
                })
                .setNegativeButton("Cancel", (picker, which) -> {
                    // Choices apply immediately; rebuild state so the screen is consistent.
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
            repinConfiguredShortcuts();
            resolveLauncherConfiguration();
            Toast.makeText(this, "Configuration imported", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void repinConfiguredShortcuts() {
        HashSet<String> handled = new HashSet<>();
        for (int i = 0; i < LauncherPreferences.MAX_HOME_APPS; i++) {
            String shortcutId = launcherPreferences.homeShortcutId(i);
            String component = launcherPreferences.homeSlot(i);
            if (shortcutId == null || component == null || !handled.add(component)) continue;

            AppEntry app = findApp(component);
            if (app != null) repinPackageShortcuts(app);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
