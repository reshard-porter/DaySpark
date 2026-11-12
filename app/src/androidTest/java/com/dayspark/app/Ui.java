package com.dayspark.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.hamcrest.Matcher;

import java.io.File;
import java.util.concurrent.Callable;

/** Waiting and screenshot helpers. Database and billing work is asynchronous, so tests poll. */
final class Ui {

    private static final long TIMEOUT_MS = 20_000;

    private Ui() {}

    static Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    static void waitFor(Matcher<View> matcher) {
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        Throwable last = null;
        while (SystemClock.uptimeMillis() < end) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (Throwable t) {
                last = t;
                SystemClock.sleep(250);
            }
        }
        throw new AssertionError("Timed out waiting for " + matcher, last);
    }

    static void waitUntil(String what, Callable<Boolean> condition) throws Exception {
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < end) {
            if (Boolean.TRUE.equals(condition.call())) return;
            SystemClock.sleep(250);
        }
        throw new AssertionError("Timed out waiting until " + what);
    }

    static void settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(700); // let ripples, snackbars and transitions finish
    }

    /** Saved to the app's external files dir; ci/emulator-run.sh pulls them. */
    static void shot(String name) {
        settle();
        File dir = new File(app().getExternalFilesDir(null), "screenshots");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(new File(dir, name + ".png"));
    }

    static SharedPreferences prefs(String file) {
        return app().getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    static String selectedTheme() {
        return prefs("dayspark_theme").getString("selected", "dayspark");
    }
}
