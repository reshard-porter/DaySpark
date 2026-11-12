package com.dayspark.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.dayspark.app.ui.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Phase 3: ci/emulator-run.sh sets the system font scale to 2.0 before this runs. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class A3_LargeTextTest {

    @Test
    public void screensAtLargestTextSize() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            Ui.waitFor(withText("Morning walk"));
            Ui.shot("15_large_text_dashboard");
            onView(withText("Morning walk")).perform(click());
            Ui.waitFor(withId(R.id.detail_toggle));
            Ui.shot("16_large_text_detail");
            pressBack();
            Ui.waitFor(withText("Morning walk"));
            onView(withId(R.id.action_themes)).perform(click());
            Ui.waitFor(withId(R.id.purchase_status));
            Ui.shot("17_large_text_gallery");
        }
    }
}
