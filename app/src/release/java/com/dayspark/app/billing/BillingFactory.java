package com.dayspark.app.billing;

import android.app.Activity;
import android.content.Context;

import com.dayspark.app.BuildConfig;

/** Release builds: real Google Play Billing and server verification only. No simulator code ships. */
public final class BillingFactory {

    private BillingFactory() {}

    static BillingGateway createGateway(Context context) {
        return new PlayBillingGateway(context);
    }

    static PurchaseVerifier createVerifier(Context context, String installId) {
        return new BackendPurchaseVerifier(BuildConfig.VERIFY_BASE_URL, installId);
    }

    public static boolean hasDebugTools() {
        return false;
    }

    public static void showDebugTools(Activity activity) {
        // Not available in release builds.
    }
}
