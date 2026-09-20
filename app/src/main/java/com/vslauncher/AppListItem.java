package com.vslauncher;

final class AppListItem {
    static final int TYPE_APP = 0;
    static final int TYPE_PROFILE = 1;

    final int type;
    final AppEntry app;
    final int profileKind;
    final long profileSerial;
    final String label;
    final String value;
    final int alphabetBucket;

    private AppListItem(
            int type,
            AppEntry app,
            int profileKind,
            long profileSerial,
            String label,
            String value,
            int alphabetBucket
    ) {
        this.type = type;
        this.app = app;
        this.profileKind = profileKind;
        this.profileSerial = profileSerial;
        this.label = label;
        this.value = value;
        this.alphabetBucket = alphabetBucket;
    }

    static AppListItem app(AppEntry app) {
        return new AppListItem(
                TYPE_APP,
                app,
                app.profileKind,
                app.userSerial,
                "",
                "",
                LauncherLayout.alphabetBucket(app.normalizedLabel)
        );
    }

    static AppListItem profile(
            int profileKind,
            long profileSerial,
            String label,
            String value
    ) {
        return new AppListItem(
                TYPE_PROFILE,
                null,
                profileKind,
                profileSerial,
                label,
                value == null ? "" : value,
                -1
        );
    }

    boolean isApp() {
        return type == TYPE_APP && app != null;
    }
}
