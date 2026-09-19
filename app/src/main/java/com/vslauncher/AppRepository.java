package com.vslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppRepository {
    interface Callback {
        void onLoaded(List<AppEntry> apps);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vs-app-index");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    AppRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    void load(Callback callback) {
        executor.execute(() -> {
            PackageManager manager = context.getPackageManager();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null)
                    .addCategory(Intent.CATEGORY_LAUNCHER);

            List<AppEntry> result = new ArrayList<>();
            for (ResolveInfo info : manager.queryIntentActivities(launcherIntent, 0)) {
                if (info.activityInfo == null
                        || context.getPackageName().equals(info.activityInfo.packageName)) {
                    continue;
                }

                CharSequence loadedLabel = info.loadLabel(manager);
                String label = loadedLabel == null
                        ? info.activityInfo.packageName
                        : loadedLabel.toString().trim();
                if (label.isEmpty()) label = info.activityInfo.packageName;

                result.add(new AppEntry(
                        label,
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                ));
            }

            result.sort(Comparator
                    .comparing((AppEntry app) -> app.normalizedLabel)
                    .thenComparing(app -> app.component.getPackageName()));

            List<AppEntry> immutable = Collections.unmodifiableList(result);
            main.post(() -> callback.onLoaded(immutable));
        });
    }

    static List<AppEntry> filter(List<AppEntry> source, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return source;

        ArrayList<AppEntry> matches = new ArrayList<>();
        for (AppEntry app : source) {
            if (app.normalizedLabel.contains(normalized)) matches.add(app);
        }
        return matches;
    }

    void close() {
        executor.shutdownNow();
    }
}
