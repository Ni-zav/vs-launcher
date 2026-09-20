package com.vslauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Typed persisted launcher configuration.
 *
 * Values are deliberately discrete instead of arbitrary sliders so rendering
 * stays predictable, compact, and cheap.
 */
final class LauncherPreferences {
    static final String HOME_SLOT_PREFIX = "home_slot_";
    private static final String HOME_SHORTCUT_ID_PREFIX = "home_shortcut_id_";
    private static final String HOME_SHORTCUT_LABEL_PREFIX = "home_shortcut_label_";
    static final int MIN_HOME_APPS = 1;
    static final int MAX_HOME_APPS = 8;

    static final String POSITION_TOP = "top";
    static final String POSITION_CENTER = "center";
    static final String POSITION_BOTTOM = "bottom";

    static final String ALIGN_LEFT = "left";
    static final String ALIGN_CENTER = "center";
    static final String ALIGN_RIGHT = "right";

    static final String DENSITY_DENSE = "dense";
    static final String DENSITY_COMPACT = "compact";
    static final String DENSITY_NORMAL = "normal";
    static final String DENSITY_SPACIOUS = "spacious";

    static final String TEXT_SMALL = "small";
    static final String TEXT_MEDIUM = "medium";
    static final String TEXT_LARGE = "large";

    static final String STATUS_TIME_FIRST = "time_first";
    static final String STATUS_DATE_FIRST = "date_first";
    static final String STATUS_COMPACT = "compact";

    static final String CLOCK_SYSTEM = "system";
    static final String CLOCK_12 = "12h";
    static final String CLOCK_24 = "24h";

    static final String DATE_WEEKDAY = "weekday";
    static final String DATE_SHORT = "short";
    static final String DATE_NUMERIC = "numeric";

    static final String WEATHER_BOTH = "both";
    static final String WEATHER_TEMP = "temperature";
    static final String WEATHER_CONDITION = "condition";

    static final String BATTERY_BOTH = "both";
    static final String BATTERY_ICON = "icon";
    static final String BATTERY_PERCENT = "percent";

    static final String ANIMATION_INSTANT = "instant";
    static final String ANIMATION_FAST = "fast";
    static final String ANIMATION_NORMAL = "normal";

    private static final String PREFS = "launcher_preferences";
    private static final String HOME_MAX = "home_max";
    private static final String QUICK_APP = "quick_app";
    private static final String HOME_POSITION = "home_position";
    private static final String HOME_ALIGNMENT = "home_alignment";
    private static final String HOME_DENSITY = "home_density";
    private static final String HOME_TEXT_SIZE = "home_text_size";
    private static final String APPS_TEXT_SIZE = "apps_text_size";
    private static final String SHOW_TIME = "status_time";
    private static final String SHOW_DATE = "status_date";
    private static final String SHOW_WEATHER = "status_weather";
    private static final String SHOW_BATTERY = "status_battery";
    private static final String STATUS_LAYOUT = "status_layout";
    private static final String CLOCK_FORMAT = "clock_format";
    private static final String DATE_STYLE = "date_style";
    private static final String WEATHER_MODE = "weather_mode";
    private static final String BATTERY_MODE = "battery_mode";
    private static final String ANIMATION_SPEED = "animation_speed";
    private static final String HAPTICS = "haptics";
    private static final String PRIVATE_SPACE_VISIBLE = "private_space_visible";
    private static final String ALIAS_PREFIX = "alias:";
    private static final String HIDDEN_PREFIX = "hidden:";

    private final SharedPreferences prefs;

    LauncherPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int homeMax() {
        return clamp(prefs.getInt(HOME_MAX, 5), MIN_HOME_APPS, MAX_HOME_APPS);
    }

    void setHomeMax(int value) {
        prefs.edit().putInt(HOME_MAX, clamp(value, MIN_HOME_APPS, MAX_HOME_APPS)).apply();
    }

    String homeSlot(int index) {
        return prefs.getString(HOME_SLOT_PREFIX + index, null);
    }

    boolean hasHomeSlot(int index) {
        return prefs.contains(HOME_SLOT_PREFIX + index);
    }

    String homeShortcutId(int index) {
        if (index < 0 || index >= MAX_HOME_APPS) return null;
        String value = prefs.getString(HOME_SHORTCUT_ID_PREFIX + index, null);
        return value == null || value.isEmpty() ? null : value;
    }

    String homeShortcutLabel(int index) {
        if (index < 0 || index >= MAX_HOME_APPS) return null;
        String value = prefs.getString(HOME_SHORTCUT_LABEL_PREFIX + index, null);
        return value == null || value.isEmpty() ? null : value;
    }

    void setHomeSlot(int index, String component) {
        SharedPreferences.Editor edit = prefs.edit();
        if (component == null) edit.remove(HOME_SLOT_PREFIX + index);
        else edit.putString(HOME_SLOT_PREFIX + index, component);
        edit.remove(HOME_SHORTCUT_ID_PREFIX + index);
        edit.remove(HOME_SHORTCUT_LABEL_PREFIX + index);
        edit.apply();
    }

    void setHomeShortcut(int index, String component, String shortcutId, String label) {
        if (index < 0 || index >= MAX_HOME_APPS) return;
        SharedPreferences.Editor edit = prefs.edit();
        if (component == null || component.isEmpty()) edit.remove(HOME_SLOT_PREFIX + index);
        else edit.putString(HOME_SLOT_PREFIX + index, component);
        putNullable(edit, HOME_SHORTCUT_ID_PREFIX + index, shortcutId);
        putNullable(edit, HOME_SHORTCUT_LABEL_PREFIX + index, label);
        edit.apply();
    }

    void clearHomeSlot(int index) {
        prefs.edit()
                .putString(HOME_SLOT_PREFIX + index, "")
                .remove(HOME_SHORTCUT_ID_PREFIX + index)
                .remove(HOME_SHORTCUT_LABEL_PREFIX + index)
                .apply();
    }

    void swapHomeSlots(int first, int second) {
        if (first < 0 || second < 0
                || first >= MAX_HOME_APPS || second >= MAX_HOME_APPS
                || first == second) {
            return;
        }

        String firstValue = homeSlot(first);
        String secondValue = homeSlot(second);
        String firstShortcutId = homeShortcutId(first);
        String secondShortcutId = homeShortcutId(second);
        String firstShortcutLabel = homeShortcutLabel(first);
        String secondShortcutLabel = homeShortcutLabel(second);
        boolean firstExists = hasHomeSlot(first);
        boolean secondExists = hasHomeSlot(second);

        SharedPreferences.Editor edit = prefs.edit();
        if (secondExists) edit.putString(HOME_SLOT_PREFIX + first, secondValue);
        else edit.remove(HOME_SLOT_PREFIX + first);
        if (firstExists) edit.putString(HOME_SLOT_PREFIX + second, firstValue);
        else edit.remove(HOME_SLOT_PREFIX + second);
        putNullable(edit, HOME_SHORTCUT_ID_PREFIX + first, secondShortcutId);
        putNullable(edit, HOME_SHORTCUT_ID_PREFIX + second, firstShortcutId);
        putNullable(edit, HOME_SHORTCUT_LABEL_PREFIX + first, secondShortcutLabel);
        putNullable(edit, HOME_SHORTCUT_LABEL_PREFIX + second, firstShortcutLabel);
        edit.apply();
    }

    String quickApp() {
        return prefs.getString(QUICK_APP, null);
    }

    void setQuickApp(String component) {
        SharedPreferences.Editor edit = prefs.edit();
        if (component == null || component.isEmpty()) edit.remove(QUICK_APP);
        else edit.putString(QUICK_APP, component);
        edit.apply();
    }

    String homePosition() {
        return oneOf(prefs.getString(HOME_POSITION, POSITION_TOP),
                POSITION_TOP, POSITION_CENTER, POSITION_BOTTOM);
    }

    void setHomePosition(String value) {
        prefs.edit().putString(HOME_POSITION,
                oneOf(value, POSITION_TOP, POSITION_CENTER, POSITION_BOTTOM)).apply();
    }

    String homeAlignment() {
        return oneOf(prefs.getString(HOME_ALIGNMENT, ALIGN_LEFT),
                ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT);
    }

    void setHomeAlignment(String value) {
        prefs.edit().putString(HOME_ALIGNMENT,
                oneOf(value, ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT)).apply();
    }

    String density() {
        return oneOf(prefs.getString(HOME_DENSITY, DENSITY_NORMAL),
                DENSITY_DENSE, DENSITY_COMPACT, DENSITY_NORMAL, DENSITY_SPACIOUS);
    }

    void setDensity(String value) {
        prefs.edit().putString(HOME_DENSITY,
                oneOf(value, DENSITY_DENSE, DENSITY_COMPACT, DENSITY_NORMAL, DENSITY_SPACIOUS)).apply();
    }

    String textSize() {
        return oneOf(prefs.getString(HOME_TEXT_SIZE, TEXT_MEDIUM),
                TEXT_SMALL, TEXT_MEDIUM, TEXT_LARGE);
    }

    void setTextSize(String value) {
        SharedPreferences.Editor edit = prefs.edit();
        if (!prefs.contains(APPS_TEXT_SIZE)) {
            // Preserve the pre-0.9 shared text size when Home typography is first changed.
            edit.putString(APPS_TEXT_SIZE, textSize());
        }
        edit.putString(
                HOME_TEXT_SIZE,
                oneOf(value, TEXT_SMALL, TEXT_MEDIUM, TEXT_LARGE)
        ).apply();
    }

    String appsTextSize() {
        if (!prefs.contains(APPS_TEXT_SIZE)) return textSize();
        return oneOf(prefs.getString(APPS_TEXT_SIZE, TEXT_MEDIUM),
                TEXT_SMALL, TEXT_MEDIUM, TEXT_LARGE);
    }

    void setAppsTextSize(String value) {
        prefs.edit().putString(APPS_TEXT_SIZE,
                oneOf(value, TEXT_SMALL, TEXT_MEDIUM, TEXT_LARGE)).apply();
    }

    boolean showTime() { return prefs.getBoolean(SHOW_TIME, true); }
    boolean showDate() { return prefs.getBoolean(SHOW_DATE, true); }
    boolean showWeather() { return prefs.getBoolean(SHOW_WEATHER, true); }
    boolean showBattery() { return prefs.getBoolean(SHOW_BATTERY, true); }

    void setShowTime(boolean value) { prefs.edit().putBoolean(SHOW_TIME, value).apply(); }
    void setShowDate(boolean value) { prefs.edit().putBoolean(SHOW_DATE, value).apply(); }
    void setShowWeather(boolean value) { prefs.edit().putBoolean(SHOW_WEATHER, value).apply(); }
    void setShowBattery(boolean value) { prefs.edit().putBoolean(SHOW_BATTERY, value).apply(); }

    String statusLayout() {
        return oneOf(prefs.getString(STATUS_LAYOUT, STATUS_TIME_FIRST),
                STATUS_TIME_FIRST, STATUS_DATE_FIRST, STATUS_COMPACT);
    }

    void setStatusLayout(String value) {
        prefs.edit().putString(STATUS_LAYOUT,
                oneOf(value, STATUS_TIME_FIRST, STATUS_DATE_FIRST, STATUS_COMPACT)).apply();
    }

    String clockFormat() {
        return oneOf(prefs.getString(CLOCK_FORMAT, CLOCK_SYSTEM),
                CLOCK_SYSTEM, CLOCK_12, CLOCK_24);
    }

    void setClockFormat(String value) {
        prefs.edit().putString(CLOCK_FORMAT,
                oneOf(value, CLOCK_SYSTEM, CLOCK_12, CLOCK_24)).apply();
    }

    String dateStyle() {
        return oneOf(prefs.getString(DATE_STYLE, DATE_WEEKDAY),
                DATE_WEEKDAY, DATE_SHORT, DATE_NUMERIC);
    }

    void setDateStyle(String value) {
        prefs.edit().putString(DATE_STYLE,
                oneOf(value, DATE_WEEKDAY, DATE_SHORT, DATE_NUMERIC)).apply();
    }

    String weatherMode() {
        return oneOf(prefs.getString(WEATHER_MODE, WEATHER_BOTH),
                WEATHER_BOTH, WEATHER_TEMP, WEATHER_CONDITION);
    }

    void setWeatherMode(String value) {
        prefs.edit().putString(WEATHER_MODE,
                oneOf(value, WEATHER_BOTH, WEATHER_TEMP, WEATHER_CONDITION)).apply();
    }

    String batteryMode() {
        return oneOf(prefs.getString(BATTERY_MODE, BATTERY_BOTH),
                BATTERY_BOTH, BATTERY_ICON, BATTERY_PERCENT);
    }

    void setBatteryMode(String value) {
        prefs.edit().putString(BATTERY_MODE,
                oneOf(value, BATTERY_BOTH, BATTERY_ICON, BATTERY_PERCENT)).apply();
    }

    String animationSpeed() {
        return oneOf(prefs.getString(ANIMATION_SPEED, ANIMATION_FAST),
                ANIMATION_INSTANT, ANIMATION_FAST, ANIMATION_NORMAL);
    }

    void setAnimationSpeed(String value) {
        prefs.edit().putString(ANIMATION_SPEED,
                oneOf(value, ANIMATION_INSTANT, ANIMATION_FAST, ANIMATION_NORMAL)).apply();
    }

    boolean haptics() {
        return prefs.getBoolean(HAPTICS, true);
    }

    boolean privateSpaceVisible() {
        return prefs.getBoolean(PRIVATE_SPACE_VISIBLE, true);
    }

    void setHaptics(boolean value) {
        prefs.edit().putBoolean(HAPTICS, value).apply();
    }

    void setPrivateSpaceVisible(boolean value) {
        prefs.edit().putBoolean(PRIVATE_SPACE_VISIBLE, value).apply();
    }

    String alias(String component) {
        if (component == null) return "";
        return prefs.getString(ALIAS_PREFIX + component, "");
    }

    void setAlias(String component, String alias) {
        if (component == null) return;
        String clean = alias == null ? "" : alias.trim();
        SharedPreferences.Editor edit = prefs.edit();
        if (clean.isEmpty()) edit.remove(ALIAS_PREFIX + component);
        else edit.putString(ALIAS_PREFIX + component, clean);
        edit.apply();
    }

    Map<String, String> aliases() {
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(ALIAS_PREFIX) || !(entry.getValue() instanceof String)) {
                continue;
            }
            String value = ((String) entry.getValue()).trim();
            if (!value.isEmpty()) {
                result.put(entry.getKey().substring(ALIAS_PREFIX.length()), value);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    boolean isHidden(String component) {
        return component != null && prefs.getBoolean(HIDDEN_PREFIX + component, false);
    }

    void setHidden(String component, boolean hidden) {
        if (component == null) return;
        SharedPreferences.Editor edit = prefs.edit();
        if (hidden) edit.putBoolean(HIDDEN_PREFIX + component, true);
        else edit.remove(HIDDEN_PREFIX + component);
        edit.apply();
    }

    Set<String> hiddenComponents() {
        HashSet<String> result = new HashSet<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(HIDDEN_PREFIX)
                    && Boolean.TRUE.equals(entry.getValue())) {
                result.add(entry.getKey().substring(HIDDEN_PREFIX.length()));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    String exportJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", 1);
        root.put("homeMax", homeMax());
        root.put("quickApp", JSONObject.wrap(quickApp()));
        root.put("homePosition", homePosition());
        root.put("homeAlignment", homeAlignment());
        root.put("density", density());
        root.put("textSize", textSize());
        root.put("appsTextSize", appsTextSize());
        root.put("showTime", showTime());
        root.put("showDate", showDate());
        root.put("showWeather", showWeather());
        root.put("showBattery", showBattery());
        root.put("statusLayout", statusLayout());
        root.put("clockFormat", clockFormat());
        root.put("dateStyle", dateStyle());
        root.put("weatherMode", weatherMode());
        root.put("batteryMode", batteryMode());
        root.put("animationSpeed", animationSpeed());
        root.put("haptics", haptics());
        root.put("privateSpaceVisible", privateSpaceVisible());

        JSONArray slots = new JSONArray();
        for (int i = 0; i < MAX_HOME_APPS; i++) {
            slots.put(JSONObject.wrap(homeSlot(i)));
        }
        root.put("homeSlots", slots);

        JSONArray shortcutIds = new JSONArray();
        JSONArray shortcutLabels = new JSONArray();
        for (int i = 0; i < MAX_HOME_APPS; i++) {
            shortcutIds.put(JSONObject.wrap(homeShortcutId(i)));
            shortcutLabels.put(JSONObject.wrap(homeShortcutLabel(i)));
        }
        root.put("homeShortcutIds", shortcutIds);
        root.put("homeShortcutLabels", shortcutLabels);

        JSONObject aliasJson = new JSONObject();
        for (Map.Entry<String, String> entry : aliases().entrySet()) {
            aliasJson.put(entry.getKey(), entry.getValue());
        }
        root.put("aliases", aliasJson);

        JSONArray hidden = new JSONArray();
        for (String component : hiddenComponents()) hidden.put(component);
        root.put("hidden", hidden);
        return root.toString(2);
    }

    void importJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("format", -1) != 1) {
            throw new JSONException("Unsupported VS Launcher config format");
        }

        SharedPreferences.Editor edit = prefs.edit().clear();
        edit.putInt(HOME_MAX, clamp(root.optInt("homeMax", 5), MIN_HOME_APPS, MAX_HOME_APPS));
        putNullable(edit, QUICK_APP, nullableString(root, "quickApp"));
        edit.putString(HOME_POSITION, oneOf(root.optString("homePosition", POSITION_TOP),
                POSITION_TOP, POSITION_CENTER, POSITION_BOTTOM));
        edit.putString(HOME_ALIGNMENT, oneOf(root.optString("homeAlignment", ALIGN_LEFT),
                ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT));
        edit.putString(HOME_DENSITY, oneOf(root.optString("density", DENSITY_NORMAL),
                DENSITY_DENSE, DENSITY_COMPACT, DENSITY_NORMAL, DENSITY_SPACIOUS));
        String importedHomeText = oneOf(root.optString("textSize", TEXT_MEDIUM),
                TEXT_SMALL, TEXT_MEDIUM, TEXT_LARGE);
        edit.putString(HOME_TEXT_SIZE, importedHomeText);
        edit.putString(APPS_TEXT_SIZE, oneOf(
                root.optString("appsTextSize", importedHomeText),
                TEXT_SMALL,
                TEXT_MEDIUM,
                TEXT_LARGE
        ));
        edit.putBoolean(SHOW_TIME, root.optBoolean("showTime", true));
        edit.putBoolean(SHOW_DATE, root.optBoolean("showDate", true));
        edit.putBoolean(SHOW_WEATHER, root.optBoolean("showWeather", true));
        edit.putBoolean(SHOW_BATTERY, root.optBoolean("showBattery", true));
        edit.putString(STATUS_LAYOUT, oneOf(root.optString("statusLayout", STATUS_TIME_FIRST),
                STATUS_TIME_FIRST, STATUS_DATE_FIRST, STATUS_COMPACT));
        edit.putString(CLOCK_FORMAT, oneOf(root.optString("clockFormat", CLOCK_SYSTEM),
                CLOCK_SYSTEM, CLOCK_12, CLOCK_24));
        edit.putString(DATE_STYLE, oneOf(root.optString("dateStyle", DATE_WEEKDAY),
                DATE_WEEKDAY, DATE_SHORT, DATE_NUMERIC));
        edit.putString(WEATHER_MODE, oneOf(root.optString("weatherMode", WEATHER_BOTH),
                WEATHER_BOTH, WEATHER_TEMP, WEATHER_CONDITION));
        edit.putString(BATTERY_MODE, oneOf(root.optString("batteryMode", BATTERY_BOTH),
                BATTERY_BOTH, BATTERY_ICON, BATTERY_PERCENT));
        edit.putString(ANIMATION_SPEED, oneOf(root.optString("animationSpeed", ANIMATION_FAST),
                ANIMATION_INSTANT, ANIMATION_FAST, ANIMATION_NORMAL));
        edit.putBoolean(HAPTICS, root.optBoolean("haptics", true));
        edit.putBoolean(
                PRIVATE_SPACE_VISIBLE,
                root.optBoolean("privateSpaceVisible", true)
        );

        JSONArray slots = root.optJSONArray("homeSlots");
        if (slots != null) {
            for (int i = 0; i < Math.min(slots.length(), MAX_HOME_APPS); i++) {
                if (!slots.isNull(i)) putNullable(edit, HOME_SLOT_PREFIX + i, slots.optString(i, null));
            }
        }

        JSONArray shortcutIds = root.optJSONArray("homeShortcutIds");
        JSONArray shortcutLabels = root.optJSONArray("homeShortcutLabels");
        if (shortcutIds != null) {
            for (int i = 0; i < Math.min(shortcutIds.length(), MAX_HOME_APPS); i++) {
                if (!shortcutIds.isNull(i)) {
                    putNullable(
                            edit,
                            HOME_SHORTCUT_ID_PREFIX + i,
                            shortcutIds.optString(i, null)
                    );
                }
                if (shortcutLabels != null
                        && i < shortcutLabels.length()
                        && !shortcutLabels.isNull(i)) {
                    putNullable(
                            edit,
                            HOME_SHORTCUT_LABEL_PREFIX + i,
                            shortcutLabels.optString(i, null)
                    );
                }
            }
        }

        JSONObject aliasJson = root.optJSONObject("aliases");
        if (aliasJson != null) {
            java.util.Iterator<String> keys = aliasJson.keys();
            while (keys.hasNext()) {
                String component = keys.next();
                String alias = aliasJson.optString(component, "").trim();
                if (!component.isEmpty() && !alias.isEmpty()) {
                    edit.putString(ALIAS_PREFIX + component, alias);
                }
            }
        }

        JSONArray hidden = root.optJSONArray("hidden");
        if (hidden != null) {
            for (int i = 0; i < hidden.length(); i++) {
                String component = hidden.optString(i, "");
                if (!component.isEmpty()) edit.putBoolean(HIDDEN_PREFIX + component, true);
            }
        }

        edit.apply();
    }

    private static void putNullable(SharedPreferences.Editor edit, String key, String value) {
        if (value == null || value.isEmpty()) edit.remove(key);
        else edit.putString(key, value);
    }

    private static String nullableString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        String value = object.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String oneOf(String value, String first, String... rest) {
        if (first.equals(value)) return first;
        for (String candidate : rest) if (candidate.equals(value)) return candidate;
        return first;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
