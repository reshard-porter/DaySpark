package com.dayspark.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.dayspark.app.ui.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Phase 1 on a fresh install (debug build, billing simulator):
 * dashboard → create habit → mark complete → detail, then
 * theme gallery → preview → unlock → pending payment → approved → theme applied.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class A1_HabitAndPurchaseFlowTest {

    @Test
    public void habitFlowThenPurchaseFlow() throws Exception {
        // Purchase will go pending, then be approved by the simulator 6 s later.
        Ui.prefs("dayspark_billing_sim").edit().putString("scenario", "PENDING_APPROVES_LATER").commit();

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // ---- Habit flow
            Ui.waitFor(withId(R.id.empty_create));
            Ui.shot("01_dashboard_empty");

            onView(withId(R.id.fab_add)).perform(click());
            Ui.waitFor(withId(R.id.name_input));
            onView(withId(R.id.save_button)).perform(click());
            Ui.waitFor(withText(R.string.error_name_required));
            Ui.shot("02_create_habit_validation");

            onView(withId(R.id.name_input)).perform(typeText("Morning walk"), closeSoftKeyboard());
            onView(withId(R.id.note_input)).perform(typeText("20 minutes outside"), closeSoftKeyboard());
            Ui.shot("03_create_habit_filled");
            onView(withId(R.id.save_button)).perform(click());
            Ui.waitFor(withText("Morning walk"));

            onView(withId(R.id.fab_add)).perform(click());
            Ui.waitFor(withId(R.id.name_input));
            onView(withId(R.id.name_input)).perform(typeText("Read 10 pages"), closeSoftKeyboard());
            onView(withId(R.id.preset_weekdays)).perform(click());
            onView(withId(R.id.save_button)).perform(click());
            Ui.waitFor(withText("Read 10 pages"));
            Ui.shot("04_dashboard_two_habits");

            onView(withContentDescription("Mark Morning walk complete for today")).perform(click());
            Ui.waitFor(withContentDescription("Mark Morning walk not complete for today"));
            Ui.shot("05_dashboard_marked_complete");

            onView(withText("Morning walk")).perform(click());
            Ui.waitFor(withId(R.id.detail_toggle));
            Ui.shot("06_habit_detail");
            pressBack();
            Ui.waitFor(withText("Morning walk"));

            // ---- Purchase flow
            onView(withId(R.id.action_themes)).perform(click());
            Ui.waitFor(allOf(withId(R.id.purchase_button), withText(startsWith("Unlock for"))));
            Ui.shot("07_theme_gallery");

            onView(withContentDescription("Preview Forest")).perform(scrollTo(), click());
            Ui.waitFor(withId(R.id.preview_list));
            Ui.waitFor(allOf(withId(R.id.purchase_button), withText(startsWith("Unlock for"))));
            Ui.shot("08_preview_forest");
            pressBack();

            onView(withContentDescription("Unlock Forest")).perform(scrollTo(), click());
            onView(withText(startsWith("Unlock for"))).inRoot(isDialog()).check(matches(isDisplayed()));
            Ui.shot("09_unlock_dialog_one_time_purchase");
            onView(withText(startsWith("Unlock for"))).inRoot(isDialog()).perform(click());

            Ui.waitFor(allOf(withId(R.id.purchase_status),
                    withText(startsWith(Ui.app().getString(R.string.purchase_status_pending)))));
            assertEquals("Pending payment must not unlock", "dayspark", Ui.selectedTheme());
            Ui.shot("10_payment_pending_still_locked");

            Ui.waitUntil("Forest applied after payment clears", () -> "forest".equals(Ui.selectedTheme()));
            Ui.waitFor(allOf(withId(R.id.purchase_status),
                    withText(startsWith(Ui.app().getString(R.string.purchase_status_owned)))));
            Ui.shot("11_gallery_forest_applied");

            pressBack();
            Ui.waitFor(withText("Morning walk"));
            Ui.shot("12_dashboard_forest");

            onView(withId(R.id.action_settings)).perform(click());
            Ui.waitFor(withText(R.string.pro_value_owned));
            Ui.shot("13_settings_owned");
        }
    }
}
