package com.dayspark.app.billing;

/** Immutable view of premium status for the UI. */
public final class PremiumSnapshot {
    public final PremiumState state;
    public final boolean premiumActive;
    /** Localized price from Google Play, or null until the product has loaded. */
    public final String formattedPrice;
    public final boolean simulated;

    public PremiumSnapshot(PremiumState state, boolean premiumActive, String formattedPrice,
                           boolean simulated) {
        this.state = state;
        this.premiumActive = premiumActive;
        this.formattedPrice = formattedPrice;
        this.simulated = simulated;
    }
}
