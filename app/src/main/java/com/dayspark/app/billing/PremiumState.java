package com.dayspark.app.billing;

/** What the purchase UI should show right now. */
public enum PremiumState {
    LOADING,
    /** Product loaded, not owned. */
    READY,
    /** Play returned no product (not configured, or not available to this account/country). */
    PRODUCT_UNAVAILABLE,
    /** Play Billing is unavailable on this device or account. */
    BILLING_UNAVAILABLE,
    PURCHASING,
    PENDING,
    VERIFYING,
    /** Paid, but verification could not complete yet; retrying automatically. */
    VERIFICATION_DELAYED,
    OWNED
}
