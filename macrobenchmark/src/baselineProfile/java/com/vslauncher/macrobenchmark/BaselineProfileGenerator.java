package com.vslauncher.macrobenchmark;

import android.content.Intent;

import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import kotlin.Unit;

/**
 * Generates human-readable profile rules with the baselineProfile variant.
 *
 * Run against the non-obfuscated baselineProfile build variant so collected
 * method/class signatures remain meaningful. Do not copy generated rules into
 * the production app until the physical-device A/B measurement shows benefit.
 */
@RunWith(AndroidJUnit4.class)
public final class BaselineProfileGenerator {
    private static final String PACKAGE = "com.vslauncher";

    @Rule
    public final BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    private UiDevice device() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private void swipeLeft() {
        UiDevice device = device();
        int y = device.getDisplayHeight() / 2;
        device.swipe(
                device.getDisplayWidth() * 4 / 5,
                y,
                device.getDisplayWidth() / 5,
                y,
                16
        );
        device.waitForIdle();
    }

    private void swipeRight() {
        UiDevice device = device();
        int y = device.getDisplayHeight() / 2;
        device.swipe(
                device.getDisplayWidth() / 5,
                y,
                device.getDisplayWidth() * 4 / 5,
                y,
                16
        );
        device.waitForIdle();
    }

    @Test
    public void startupProfile() {
        baselineProfileRule.collect(
                PACKAGE,
                15,
                3,
                "vs-launcher-startup",
                true,
                false,
                rule -> true,
                scope -> {
                    scope.pressHome(300L);
                    scope.startActivityAndWait(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setClassName(PACKAGE, PACKAGE + ".MainActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    return Unit.INSTANCE;
                }
        );
    }

    @Test
    public void commonLauncherJourneys() {
        baselineProfileRule.collect(
                PACKAGE,
                15,
                3,
                "vs-launcher-journeys",
                false,
                false,
                rule -> true,
                scope -> {
                    scope.pressHome(300L);
                    scope.startActivityAndWait(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setClassName(PACKAGE, PACKAGE + ".MainActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

                    // Home -> All Apps, then exercise fling work.
                    swipeLeft();
                    UiDevice device = device();
                    int x = device.getDisplayWidth() / 2;
                    device.swipe(
                            x,
                            device.getDisplayHeight() * 4 / 5,
                            x,
                            device.getDisplayHeight() / 5,
                            12
                    );
                    device.waitForIdle();

                    // Return Home, then visit Settings.
                    swipeRight();
                    swipeRight();
                    swipeLeft();

                    // Home -> focused Apps search and filter once.
                    swipeLeft();
                    UiObject2 search = device.findObject(By.desc("Search all apps"));
                    if (search == null) {
                        throw new IllegalStateException("Search field was not found");
                    }
                    search.clear();
                    search.setText("cal");
                    device.waitForIdle();

                    return Unit.INSTANCE;
                }
        );
    }
}
