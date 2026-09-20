package com.vslauncher;

final class AppListItem {
    static final int TYPE_APP = 0;
    static final int TYPE_PROFILE = 1;

    final int type;
    final AppEntry app;
    final int profileKind;
    final String label;
    final String value;

    private AppListItem(int type, AppEntry app, int profileKind, String label, String value) {
        this.type = type;
        this.app = app;
        this.profileKind = profileKind;
        this.label = label;
        this.value = value;
    }

    static AppListItem app(AppEntry app) {
        return new AppListItem(TYPE_APP, app, app.profileKind, "", "");
    }

    static AppListItem profile(int profileKind, String label, String value) {
        return new AppListItem(TYPE_PROFILE, null, profileKind, label, value == null ? "" : value);
    }

    boolean isApp() {
        return type == TYPE_APP && app != null;
    }
}
