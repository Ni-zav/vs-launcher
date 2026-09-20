package com.vslauncher;

import android.os.UserHandle;

final class LauncherProfile {
    final UserHandle user;
    final long serial;
    final int kind;
    final boolean quiet;

    LauncherProfile(UserHandle user, long serial, int kind, boolean quiet) {
        this.user = user;
        this.serial = serial;
        this.kind = kind;
        this.quiet = quiet;
    }

    String label() {
        if (kind == AppEntry.PROFILE_WORK) return "WORK";
        if (kind == AppEntry.PROFILE_PRIVATE) return "PRIVATE";
        if (kind == AppEntry.PROFILE_OTHER) return "PROFILE";
        return "";
    }
}
