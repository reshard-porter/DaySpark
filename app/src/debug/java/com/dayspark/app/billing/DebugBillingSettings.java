package com.dayspark.app.billing;

import android.content.Context;
import android.content.SharedPreferences;

/** Debug-only switches and simulator state. Stored separately and excluded from backup. */
final class DebugBillingSettings {

    enum Scenario {
        SUCCESS, CANCEL, DECLINE, PENDING, PENDING_APPROVES_LATER, ALREADY_OWNED, LAUNCH_ERROR,
        ACK_FAILS_TWICE, DUPLICATE_CALLBACKS, VERIFY_OFFLINE, VERIFY_REJECTS
    }

    static final String FILE = "dayspark_billing_sim";

    private DebugBillingSettings() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Simulator is the default in debug builds so the purchase flow works with no Play setup. */
    static boolean useSimulator(Context c) {
        return prefs(c).getBoolean("use_simulator", true);
    }

    static void setUseSimulator(Context c, boolean value) {
        prefs(c).edit().putBoolean("use_simulator", value).commit();
    }

    static boolean trustLocally(Context c) {
        return prefs(c).getBoolean("trust_locally", false);
    }

    static void setTrustLocally(Context c, boolean value) {
        prefs(c).edit().putBoolean("trust_locally", value).commit();
    }

    static Scenario scenario(Context c) {
        try {
            return Scenario.valueOf(prefs(c).getString("scenario", Scenario.SUCCESS.name()));
        } catch (IllegalArgumentException e) {
            return Scenario.SUCCESS;
        }
    }

    static void setScenario(Context c, Scenario s) {
        prefs(c).edit().putString("scenario", s.name()).apply();
    }
}
