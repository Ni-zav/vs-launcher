package com.vslauncher;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherUserInfo;
import android.content.pm.ShortcutInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppRepository {
    interface Callback {
        void onLoaded(List<AppEntry> apps);
    }

    interface ShortcutsCallback {
        void onLoaded(List<ShortcutInfo> shortcuts);
    }

    private final Context context;
    private final LauncherApps launcherApps;
    private final UserManager userManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vs-app-index");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    AppRepository(Context context) {
        this.context = context.getApplicationContext();
        launcherApps = this.context.getSystemService(LauncherApps.class);
        userManager = this.context.getSystemService(UserManager.class);
    }

    void load(Callback callback) {
        executor.execute(() -> {
            ArrayList<AppEntry> result = new ArrayList<>();
            if (launcherApps != null && userManager != null) {
                try {
                    for (UserHandle user : launcherApps.getProfiles()) {
                        int kind = profileKind(user);
                        long serial = userManager.getSerialNumberForUser(user);
                        List<LauncherActivityInfo> activities;
                        try {
                            activities = launcherApps.getActivityList(null, user);
                        } catch (IllegalStateException | SecurityException error) {
                            continue;
                        }

                        for (LauncherActivityInfo info : activities) {
                            if (info == null
                                    || context.getPackageName().equals(
                                    info.getComponentName().getPackageName())) {
                                continue;
                            }

                            CharSequence loadedLabel = info.getLabel();
                            String label = loadedLabel == null
                                    ? info.getComponentName().getPackageName()
                                    : loadedLabel.toString().trim();
                            if (label.isEmpty()) {
                                label = info.getComponentName().getPackageName();
                            }

                            result.add(new AppEntry(
                                    label,
                                    info.getComponentName(),
                                    user,
                                    serial,
                                    kind
                            ));
                        }
                    }
                } catch (IllegalStateException | SecurityException ignored) {
                }
            }

            result.sort(Comparator
                    .comparingInt((AppEntry app) -> app.profileKind)
                    .thenComparing(app -> app.normalizedLabel)
                    .thenComparing(app -> app.component.getPackageName()));

            List<AppEntry> immutable = Collections.unmodifiableList(result);
            main.post(() -> callback.onLoaded(immutable));
        });
    }

    private int profileKind(UserHandle user) {
        if (user.equals(Process.myUserHandle())) return AppEntry.PROFILE_PERSONAL;

        if (Build.VERSION.SDK_INT >= 35 && launcherApps != null) {
            try {
                LauncherUserInfo info = launcherApps.getLauncherUserInfo(user);
                if (info != null) {
                    String type = info.getUserType();
                    if (UserManager.USER_TYPE_PROFILE_PRIVATE.equals(type)) {
                        return AppEntry.PROFILE_PRIVATE;
                    }
                    if (UserManager.USER_TYPE_PROFILE_MANAGED.equals(type)) {
                        return AppEntry.PROFILE_WORK;
                    }
                }
            } catch (IllegalStateException | SecurityException ignored) {
            }
        }
        return AppEntry.PROFILE_WORK;
    }

    boolean startApp(AppEntry app) {
        if (app == null || launcherApps == null) return false;
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null);
            return true;
        } catch (IllegalStateException | SecurityException error) {
            return false;
        }
    }

    boolean startAppDetails(AppEntry app) {
        if (app == null || launcherApps == null) return false;
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null);
            return true;
        } catch (IllegalStateException | SecurityException error) {
            return false;
        }
    }

    boolean canUseShortcuts() {
        return launcherApps != null && launcherApps.hasShortcutHostPermission();
    }

    void loadShortcuts(AppEntry app, ShortcutsCallback callback) {
        if (app == null || launcherApps == null || !canUseShortcuts()) {
            callback.onLoaded(Collections.emptyList());
            return;
        }

        executor.execute(() -> {
            List<ShortcutInfo> shortcuts;
            try {
                LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery()
                        .setPackage(app.component.getPackageName())
                        .setQueryFlags(
                                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                        | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        );
                shortcuts = launcherApps.getShortcuts(query, app.user);
                if (shortcuts == null) shortcuts = Collections.emptyList();
            } catch (IllegalStateException | SecurityException error) {
                shortcuts = Collections.emptyList();
            }

            ArrayList<ShortcutInfo> visible = new ArrayList<>(shortcuts.size());
            for (ShortcutInfo shortcut : shortcuts) {
                if (shortcut != null && shortcut.isEnabled()) visible.add(shortcut);
            }
            List<ShortcutInfo> immutable = Collections.unmodifiableList(visible);
            main.post(() -> callback.onLoaded(immutable));
        });
    }

    boolean startShortcut(AppEntry app, ShortcutInfo shortcut) {
        if (app == null || shortcut == null || launcherApps == null) return false;
        try {
            launcherApps.startShortcut(
                    app.component.getPackageName(),
                    shortcut.getId(),
                    null,
                    null,
                    app.user
            );
            return true;
        } catch (IllegalStateException | SecurityException error) {
            return false;
        }
    }

    boolean isQuietModeEnabled(UserHandle user) {
        return userManager != null && userManager.isQuietModeEnabled(user);
    }

    boolean requestQuietMode(boolean enabled, UserHandle user) {
        if (userManager == null || user == null) return false;
        try {
            return userManager.requestQuietModeEnabled(enabled, user);
        } catch (IllegalArgumentException | SecurityException error) {
            return false;
        }
    }

    static List<AppEntry> filter(
            List<AppEntry> source,
            String query,
            Map<String, String> normalizedAliases
    ) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return source;

        ArrayList<AppEntry> canonicalPrefix = new ArrayList<>();
        ArrayList<AppEntry> aliasPrefix = new ArrayList<>();
        ArrayList<AppEntry> canonicalSubstring = new ArrayList<>();
        ArrayList<AppEntry> aliasSubstring = new ArrayList<>();

        for (AppEntry app : source) {
            String alias = normalizedAliases == null
                    ? null
                    : normalizedAliases.get(app.componentKey);

            switch (SearchRanking.rank(app.normalizedLabel, alias, normalized)) {
                case SearchRanking.CANONICAL_PREFIX:
                    canonicalPrefix.add(app);
                    break;
                case SearchRanking.ALIAS_PREFIX:
                    aliasPrefix.add(app);
                    break;
                case SearchRanking.CANONICAL_SUBSTRING:
                    canonicalSubstring.add(app);
                    break;
                case SearchRanking.ALIAS_SUBSTRING:
                    aliasSubstring.add(app);
                    break;
                default:
                    break;
            }
        }

        int total = canonicalPrefix.size()
                + aliasPrefix.size()
                + canonicalSubstring.size()
                + aliasSubstring.size();
        ArrayList<AppEntry> matches = new ArrayList<>(total);
        matches.addAll(canonicalPrefix);
        matches.addAll(aliasPrefix);
        matches.addAll(canonicalSubstring);
        matches.addAll(aliasSubstring);
        return matches;
    }

    void close() {
        executor.shutdownNow();
    }
}
