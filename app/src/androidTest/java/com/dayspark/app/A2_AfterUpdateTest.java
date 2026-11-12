package com.dayspark.app;

import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.dayspark.app.billing.PremiumManager;
import com.dayspark.app.ui.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Phase 2: runs after ci/emulator-run.sh reinstalls the APK over itself (an app update) and the
 * process has been killed. Habits, today's check-in, the theme and ownership must all survive.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class A2_AfterUpdateTest {

    @Test
    public void dataThemeAndOwnershipSurviveUpdateAndRestart() throws Exception {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            Ui.waitFor(withText("Morning walk"));
            Ui.waitFor(withText("Read 10 pages"));
            Ui.waitFor(withContentDescription("Mark Morning walk not complete for today"));
            assertEquals("forest", Ui.selectedTheme());
            Ui.waitUntil("ownership re-established", () -> {
                boolean[] active = new boolean[1];
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        active[0] = PremiumManager.get(Ui.app()).isPremiumActive());
                return active[0];
            });
            assertTrue(true);
            Ui.shot("14_after_update_and_restart");
        }
    }
}
