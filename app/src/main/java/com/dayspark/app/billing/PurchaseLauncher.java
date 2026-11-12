package com.dayspark.app.billing;

import android.app.Activity;

/** Opens the store purchase sheet. Implemented by each {@link BillingGateway}. */
public interface PurchaseLauncher {
    /** Returns a {@link BillingCodes} value; OK means the sheet opened (result arrives later). */
    int launchPurchase(Activity activity, String productId, String obfuscatedAccountId);
}
