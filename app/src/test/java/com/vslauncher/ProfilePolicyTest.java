package com.vslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ProfilePolicyTest {
    @Test public void privateAppsFollowContainerVisibility() {
        assertTrue(ProfilePolicy.visibleInApps(AppEntry.PROFILE_PRIVATE, true));
        assertFalse(ProfilePolicy.visibleInApps(AppEntry.PROFILE_PRIVATE, false));
        assertTrue(ProfilePolicy.visibleInApps(AppEntry.PROFILE_WORK, false));
        assertTrue(ProfilePolicy.visibleInApps(AppEntry.PROFILE_PERSONAL, false));
    }

    @Test public void privateAppsCannotPersistOnHome() {
        assertFalse(ProfilePolicy.canPersistOnHome(AppEntry.PROFILE_PRIVATE));
        assertTrue(ProfilePolicy.canPersistOnHome(AppEntry.PROFILE_WORK));
        assertTrue(ProfilePolicy.canPersistOnHome(AppEntry.PROFILE_PERSONAL));
    }

    @Test public void profileHeadersExposeOnlyCompactActionState() {
        assertEquals("LOCK", ProfilePolicy.headerValue(AppEntry.PROFILE_PRIVATE, false));
        assertEquals("LOCKED", ProfilePolicy.headerValue(AppEntry.PROFILE_PRIVATE, true));
        assertEquals("PAUSE", ProfilePolicy.headerValue(AppEntry.PROFILE_WORK, false));
        assertEquals("PAUSED", ProfilePolicy.headerValue(AppEntry.PROFILE_WORK, true));
        assertEquals("", ProfilePolicy.headerValue(AppEntry.PROFILE_OTHER, false));
    }
}
