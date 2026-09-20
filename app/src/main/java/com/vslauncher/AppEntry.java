package com.vslauncher;

import android.content.ComponentName;
import android.os.Process;
import android.os.UserHandle;

final class AppEntry {
    static final int PROFILE_PERSONAL = 0;
    static final int PROFILE_WORK = 1;
    static final int PROFILE_PRIVATE = 2;
    static final int PROFILE_OTHER = 3;

    final String label;
    final String normalizedLabel;
    final String searchInitials;
    final ComponentName component;
    final UserHandle user;
    final long userSerial;
    final int profileKind;
    final String componentKey;

    AppEntry(
            String label,
            ComponentName component,
            UserHandle user,
            long userSerial,
            int profileKind
    ) {
        this.label = label;
        this.normalizedLabel = label.toLowerCase(java.util.Locale.ROOT);
        this.searchInitials = SearchRanking.initials(label);
        this.component = component;
        this.user = user;
        this.userSerial = userSerial;
        this.profileKind = profileKind;

        // Preserve all existing personal-profile preference keys from 0.5.
        String base = component.flattenToString();
        this.componentKey = user.equals(Process.myUserHandle())
                ? base
                : base + "@u" + userSerial;
    }

    boolean isPersonal() {
        return profileKind == PROFILE_PERSONAL;
    }

    String profileLabel() {
        if (profileKind == PROFILE_WORK) return "Work";
        if (profileKind == PROFILE_PRIVATE) return "Private";
        if (profileKind == PROFILE_OTHER) return "Profile";
        return "";
    }

    String pickerLabel() {
        String profile = profileLabel();
        return profile.isEmpty() ? label : label + " · " + profile;
    }
}
