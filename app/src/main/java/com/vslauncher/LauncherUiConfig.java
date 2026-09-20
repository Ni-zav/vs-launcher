package com.vslauncher;

/**
 * Immutable render/interaction snapshot derived from persisted preferences.
 * LauncherSurface consumes this object without reading SharedPreferences.
 */
final class LauncherUiConfig {
    final String homePosition;
    final String homeAlignment;
    final String density;
    final String textSize;
    final String appsTextSize;
    final boolean showTime;
    final boolean showDate;
    final boolean showWeather;
    final boolean showBattery;
    final String statusLayout;
    final String clockFormat;
    final String dateStyle;
    final String weatherMode;
    final String batteryMode;
    final String animationSpeed;
    final boolean haptics;

    private LauncherUiConfig(
            String homePosition,
            String homeAlignment,
            String density,
            String textSize,
            String appsTextSize,
            boolean showTime,
            boolean showDate,
            boolean showWeather,
            boolean showBattery,
            String statusLayout,
            String clockFormat,
            String dateStyle,
            String weatherMode,
            String batteryMode,
            String animationSpeed,
            boolean haptics
    ) {
        this.homePosition = homePosition;
        this.homeAlignment = homeAlignment;
        this.density = density;
        this.textSize = textSize;
        this.appsTextSize = appsTextSize;
        this.showTime = showTime;
        this.showDate = showDate;
        this.showWeather = showWeather;
        this.showBattery = showBattery;
        this.statusLayout = statusLayout;
        this.clockFormat = clockFormat;
        this.dateStyle = dateStyle;
        this.weatherMode = weatherMode;
        this.batteryMode = batteryMode;
        this.animationSpeed = animationSpeed;
        this.haptics = haptics;
    }

    static LauncherUiConfig defaults() {
        return new LauncherUiConfig(
                LauncherPreferences.POSITION_TOP,
                LauncherPreferences.ALIGN_LEFT,
                LauncherPreferences.DENSITY_NORMAL,
                LauncherPreferences.TEXT_MEDIUM,
                LauncherPreferences.TEXT_MEDIUM,
                true, true, true, true,
                LauncherPreferences.STATUS_TIME_FIRST,
                LauncherPreferences.CLOCK_SYSTEM,
                LauncherPreferences.DATE_WEEKDAY,
                LauncherPreferences.WEATHER_BOTH,
                LauncherPreferences.BATTERY_BOTH,
                LauncherPreferences.ANIMATION_FAST,
                true
        );
    }

    static LauncherUiConfig from(LauncherPreferences prefs) {
        return new LauncherUiConfig(
                prefs.homePosition(),
                prefs.homeAlignment(),
                prefs.density(),
                prefs.textSize(),
                prefs.appsTextSize(),
                prefs.showTime(),
                prefs.showDate(),
                prefs.showWeather(),
                prefs.showBattery(),
                prefs.statusLayout(),
                prefs.clockFormat(),
                prefs.dateStyle(),
                prefs.weatherMode(),
                prefs.batteryMode(),
                prefs.animationSpeed(),
                prefs.haptics()
        );
    }

    float rowHeightDp() {
        if (LauncherPreferences.DENSITY_DENSE.equals(density)) return 38f;
        if (LauncherPreferences.DENSITY_COMPACT.equals(density)) return 44f;
        if (LauncherPreferences.DENSITY_SPACIOUS.equals(density)) return 64f;
        return 54f;
    }

    float homeTextSp() {
        if (LauncherPreferences.TEXT_SMALL.equals(textSize)) return 16f;
        if (LauncherPreferences.TEXT_LARGE.equals(textSize)) return 22f;
        return 19f;
    }

    float appTextSp() {
        if (LauncherPreferences.TEXT_SMALL.equals(appsTextSize)) return 16f;
        if (LauncherPreferences.TEXT_LARGE.equals(appsTextSize)) return 22f;
        return 19f;
    }

    long pageDurationMs() {
        if (LauncherPreferences.ANIMATION_INSTANT.equals(animationSpeed)) return 0L;
        if (LauncherPreferences.ANIMATION_NORMAL.equals(animationSpeed)) return 180L;
        return 110L;
    }

    long settleDurationMs() {
        if (LauncherPreferences.ANIMATION_INSTANT.equals(animationSpeed)) return 0L;
        if (LauncherPreferences.ANIMATION_NORMAL.equals(animationSpeed)) return 140L;
        return 90L;
    }
}
