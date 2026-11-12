package com.dayspark.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.accessibility.AccessibilityChecks;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.dayspark.app.ui.MainActivity;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Phase 4: Google's Accessibility Test Framework runs on the whole screen at every interaction
 * (labels, touch-target size, text contrast, duplicate descriptions). Any finding fails the test.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class A4_AccessibilityChecksTest {

    @BeforeClass
    public static void enableChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true);
    }

    @Test
    public void mainScreensPassAccessibilityChecks() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            Ui.waitFor(withText("Morning walk"));
            onView(withContentDescription("Mark Morning walk not complete for today")).perform(click());
            onView(withContentDescription("Mark Morning walk complete for today")).perform(click());
            onView(withText("Morning walk")).perform(click());
            Ui.waitFor(withId(R.id.detail_toggle));
            onView(withId(R.id.detail_toggle)).perform(click());
            onView(withId(R.id.detail_toggle)).perform(click());
            pressBack();
            Ui.waitFor(withId(R.id.fab_add));
            onView(withId(R.id.fab_add)).perform(click());
            Ui.waitFor(withId(R.id.name_input));
            onView(withId(R.id.name_input)).perform(closeSoftKeyboard());
            pressBack();
            Ui.waitFor(withId(R.id.fab_add));
            onView(withId(R.id.action_themes)).perform(click());
            Ui.waitFor(withId(R.id.purchase_status));
            onView(withId(R.id.purchase_restore)).perform(click());
            pressBack();
            Ui.waitFor(withId(R.id.fab_add));
            onView(withId(R.id.action_settings)).perform(click());
            Ui.waitFor(withId(R.id.row_restore));
            onView(withId(R.id.row_restore)).perform(click());
        }
    }
}
