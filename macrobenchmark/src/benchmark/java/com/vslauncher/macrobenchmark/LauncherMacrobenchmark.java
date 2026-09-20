package com.vslauncher.macrobenchmark;

import android.content.Intent;

import androidx.benchmark.macro.BaselineProfileMode;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.FrameTimingMetric;
import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import kotlin.Unit;

/**
 * Run on a physical Android device:
 *   gradle :macrobenchmark:connectedBenchmarkAndroidTest
 *
 * The benchmark module and its AndroidX dependencies are never packaged into
 * the normal VS Launcher APK.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public final class LauncherMacrobenchmark {
    private static final String PACKAGE = "com.vslauncher";
    private static final int ITERATIONS = 8;

    @Rule
    public final MacrobenchmarkRule benchmarkRule = new MacrobenchmarkRule();

    private UiDevice device() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private Unit launch(MacrobenchmarkScope scope) {
        scope.startActivityAndWait(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClassName(PACKAGE, PACKAGE + ".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        return Unit.INSTANCE;
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
    public void coldStartup() {
        benchmarkRule.measureRepeated(
                PACKAGE,
                Arrays.asList(new StartupTimingMetric()),
                new CompilationMode.None(),
                StartupMode.COLD,
                ITERATIONS,
                scope -> {
                    scope.pressHome(300L);
                    return Unit.INSTANCE;
                },
                this::launch
        );
    }

    @Test
    public void coldStartupWithBaselineProfile() {
        benchmarkRule.measureRepeated(
                PACKAGE,
                Arrays.asList(new StartupTimingMetric()),
                new CompilationMode.Partial(BaselineProfileMode.Require, 0),
                StartupMode.COLD,
                ITERATIONS,
                scope -> {
                    scope.pressHome(300L);
                    return Unit.INSTANCE;
                },
                this::launch
        );
    }

    @Test
    public void homeToAppsFrames() {
        benchmarkRule.measureRepeated(
                PACKAGE,
                Arrays.asList(new FrameTimingMetric()),
                CompilationMode.DEFAULT,
                null,
                ITERATIONS,
                this::launch,
                scope -> {
                    swipeLeft();
                    return Unit.INSTANCE;
                }
        );
    }

    @Test
    public void homeToSettingsFrames() {
        benchmarkRule.measureRepeated(
                PACKAGE,
                Arrays.asList(new FrameTimingMetric()),
                CompilationMode.DEFAULT,
                null,
                ITERATIONS,
                this::launch,
                scope -> {
                    swipeRight();
                    return Unit.INSTANCE;
                }
        );
    }

    @Test
    public void allAppsFlingFrames() {
        benchmarkRule.measureRepeated(
                PACKAGE,
                Arrays.asList(new FrameTimingMetric()),
                CompilationMode.DEFAULT,
                null,
                ITERATIONS,
                scope -> {
                    launch(scope);
                    swipeLeft();
                    return Unit.INSTANCE;
                },
                scope -> {
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
                    return Unit.INSTANCE;
                }
        );
    }

    @Test
    public void searchFilterFrames() {
        benchmarkRule.measureRepeated(
                PACKAGE,
                Arrays.asList(new FrameTimingMetric()),
                CompilationMode.DEFAULT,
                null,
                ITERATIONS,
                scope -> {
                    launch(scope);
                    swipeLeft();
                    return Unit.INSTANCE;
                },
                scope -> {
                    UiObject2 search = device().findObject(By.desc("Search all apps"));
                    if (search == null) {
                        throw new IllegalStateException("Search field was not found");
                    }
                    search.clear();
                    search.setText("cal");
                    device().waitForIdle();
                    return Unit.INSTANCE;
                }
        );
    }
}
