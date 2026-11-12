package com.dayspark.app;

import android.app.Application;

import com.dayspark.app.billing.PremiumEngine;
import com.dayspark.app.billing.PremiumEvent;
import com.dayspark.app.billing.PremiumManager;
import com.dayspark.app.billing.PremiumSnapshot;
import com.dayspark.app.theme.ThemePreferences;

public final class DaySparkApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PremiumManager premium = PremiumManager.get(this);
        // Refunded or revoked: return to the free theme. Habit data is never touched.
        premium.addListener(new PremiumEngine.Listener() {
            @Override public void onPremiumChanged(PremiumSnapshot snapshot) {}

            @Override public void onPremiumEvent(PremiumEvent event) {
                if (event == PremiumEvent.REVOKED) {
                    new ThemePreferences(DaySparkApp.this).resetAfterRevocation();
                }
            }
        });
        // Reconciles ownership on every launch: recovers interrupted checkouts and
        // reinstalls, retries acknowledgement, and detects refunds.
        premium.start();
    }
}
