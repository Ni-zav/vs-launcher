package com.vslauncher;

/** Pure-Java profile visibility/persistence policy shared by runtime and JVM tests. */
final class ProfilePolicy {
    private ProfilePolicy() {}

    static boolean visibleInApps(int profileKind, boolean privateSpaceVisible) {
        return profileKind != AppEntry.PROFILE_PRIVATE || privateSpaceVisible;
    }

    static boolean canPersistOnHome(int profileKind) {
        return profileKind != AppEntry.PROFILE_PRIVATE;
    }

    static String headerValue(int profileKind, boolean quiet) {
        if (profileKind == AppEntry.PROFILE_PRIVATE) return quiet ? "LOCKED" : "LOCK";
        if (profileKind == AppEntry.PROFILE_WORK) return quiet ? "PAUSED" : "PAUSE";
        return "";
    }
}
