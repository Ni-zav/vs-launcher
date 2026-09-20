package com.vslauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SearchCommand {
    static final String WIFI = "wifi";
    static final String INTERNET = "internet";
    static final String VOLUME = "volume";
    static final String BLUETOOTH = "bluetooth";
    static final String BATTERY = "battery";
    static final String SETTINGS = "settings";
    static final String LAUNCHER_SETTINGS = "launcher_settings";
    static final String ALARMS = "alarms";
    static final String CALENDAR = "calendar";
    static final String STORAGE = "storage";
    static final String KEYBOARD = "keyboard";
    static final String NFC = "nfc";
    static final String DISPLAY = "display";
    static final String SOUND = "sound";
    static final String LOCATION = "location";
    static final String NOTIFICATIONS = "notifications";

    private static final SearchCommand[] ALL = {
            new SearchCommand(WIFI, "Wi-Fi", "wifi", "wi fi", "wireless"),
            new SearchCommand(INTERNET, "Internet", "internet", "network", "connectivity"),
            new SearchCommand(VOLUME, "Volume", "volume", "sound level"),
            new SearchCommand(BLUETOOTH, "Bluetooth", "bluetooth", "bt"),
            new SearchCommand(BATTERY, "Battery", "battery", "battery saver", "power"),
            new SearchCommand(SETTINGS, "Settings", "settings", "system settings"),
            new SearchCommand(
                    LAUNCHER_SETTINGS,
                    "Launcher settings",
                    "launcher settings",
                    "home settings",
                    "vs settings"
            ),
            new SearchCommand(ALARMS, "Alarms", "alarm", "alarms", "clock"),
            new SearchCommand(CALENDAR, "Calendar", "calendar", "date"),
            new SearchCommand(STORAGE, "Storage", "storage", "device storage"),
            new SearchCommand(KEYBOARD, "Keyboard", "keyboard", "input method", "ime"),
            new SearchCommand(NFC, "NFC", "nfc"),
            new SearchCommand(DISPLAY, "Display", "display", "screen settings"),
            new SearchCommand(SOUND, "Sound", "sound", "audio settings"),
            new SearchCommand(LOCATION, "Location", "location", "gps"),
            new SearchCommand(
                    NOTIFICATIONS,
                    "Notifications",
                    "notifications",
                    "notification settings"
            )
    };

    final String id;
    final String label;
    private final String normalizedLabel;
    private final String[] normalizedKeywords;

    private SearchCommand(String id, String label, String... keywords) {
        this.id = id;
        this.label = label;
        normalizedLabel = SearchNormalization.normalize(label);
        normalizedKeywords = new String[keywords.length];
        for (int i = 0; i < keywords.length; i++) {
            normalizedKeywords[i] = SearchNormalization.normalize(keywords[i]);
        }
    }

    private boolean matches(String normalizedQuery) {
        if (normalizedQuery.length() < 2) return false;
        if (normalizedLabel.startsWith(normalizedQuery)) return true;
        for (String keyword : normalizedKeywords) {
            if (keyword.startsWith(normalizedQuery) || keyword.equals(normalizedQuery)) return true;
        }
        return false;
    }

    static List<SearchCommand> matching(String query) {
        return matchingNormalized(SearchNormalization.normalize(query));
    }

    static List<SearchCommand> matchingNormalized(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.length() < 2) {
            return Collections.emptyList();
        }

        ArrayList<SearchCommand> result = new ArrayList<>();
        for (SearchCommand command : ALL) {
            if (command.matches(normalizedQuery)) result.add(command);
        }
        return result.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(result);
    }
}
