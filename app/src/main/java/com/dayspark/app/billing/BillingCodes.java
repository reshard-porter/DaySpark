package com.dayspark.app.billing;

/**
 * Response codes used inside DaySpark's billing components. Values match
 * {@code BillingClient.BillingResponseCode} and {@code OnPurchasesUpdatedSubResponseCode}, so the
 * Play gateway passes codes straight through while the purchase logic stays free of Android types.
 */
public final class BillingCodes {
    public static final int SERVICE_DISCONNECTED = -1;
    public static final int FEATURE_NOT_SUPPORTED = -2;
    public static final int OK = 0;
    public static final int USER_CANCELED = 1;
    public static final int SERVICE_UNAVAILABLE = 2;
    public static final int BILLING_UNAVAILABLE = 3;
    public static final int ITEM_UNAVAILABLE = 4;
    public static final int DEVELOPER_ERROR = 5;
    public static final int ERROR = 6;
    public static final int ITEM_ALREADY_OWNED = 7;
    public static final int ITEM_NOT_OWNED = 8;
    public static final int NETWORK_ERROR = 12;

    public static final int SUB_NONE = 0;
    public static final int SUB_PAYMENT_DECLINED_INSUFFICIENT_FUNDS = 1;

    private BillingCodes() {}

    /** Codes worth retrying automatically with backoff. */
    public static boolean isRetriable(int code) {
        return code == SERVICE_DISCONNECTED || code == SERVICE_UNAVAILABLE
                || code == NETWORK_ERROR || code == ERROR;
    }
}
