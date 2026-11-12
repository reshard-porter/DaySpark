package com.dayspark.app.billing;

import android.app.Activity;
import android.content.Context;

import com.dayspark.app.BuildConfig;

/**
 * Debug builds: choose between the billing simulator and real Google Play (license testers),
 * and between server verification and a debug-only local trust check. Release builds use the
 * release variant of this class, which has none of these options.
 */
public final class BillingFactory {

    private BillingFactory() {}

    static BillingGateway createGateway(Context context) {
        return DebugBillingSettings.useSimulator(context)
                ? new SimulatedBillingGateway(context)
                : new PlayBillingGateway(context);
    }

    static PurchaseVerifier createVerifier(Context context, String installId) {
        if (DebugBillingSettings.useSimulator(context)) {
            return new SimulatedPurchaseVerifier(context);
        }
        if (DebugBillingSettings.trustLocally(context)) {
            return new DebugTrustingVerifier(context.getPackageName());
        }
        return new BackendPurchaseVerifier(BuildConfig.VERIFY_BASE_URL, installId);
    }

    public static boolean hasDebugTools() {
        return true;
    }

    public static void showDebugTools(Activity activity) {
        DebugBillingPanel.show(activity);
    }
}
