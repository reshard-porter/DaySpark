package com.dayspark.app.billing;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the last verified ownership. Excluded from cloud backup (see res/xml), so a restored
 * device re-establishes ownership with Google Play instead of trusting a copied flag.
 */
public final class PrefsEntitlementStorage implements EntitlementStorage {

    private static final String FILE = "dayspark_entitlement";
    private static final String K_OWNED = "owned";
    private static final String K_TOKEN = "token";
    private static final String K_VERIFIED_AT = "verified_at";
    private static final String K_ACKED = "acknowledged";

    private final SharedPreferences prefs;

    public PrefsEntitlementStorage(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Override public boolean isOwned() { return prefs.getBoolean(K_OWNED, false); }

    @Override public String purchaseToken() { return prefs.getString(K_TOKEN, ""); }

    @Override public long verifiedAtMillis() { return prefs.getLong(K_VERIFIED_AT, 0L); }

    @Override public boolean isAcknowledged() { return prefs.getBoolean(K_ACKED, false); }

    @Override
    public void grant(String purchaseToken, long verifiedAtMillis) {
        boolean sameToken = purchaseToken.equals(purchaseToken());
        prefs.edit()
                .putBoolean(K_OWNED, true)
                .putString(K_TOKEN, purchaseToken)
                .putLong(K_VERIFIED_AT, verifiedAtMillis)
                .putBoolean(K_ACKED, sameToken && isAcknowledged())
                .apply();
    }

    @Override
    public void markAcknowledged(String purchaseToken) {
        if (purchaseToken.equals(purchaseToken())) prefs.edit().putBoolean(K_ACKED, true).apply();
    }

    @Override
    public void clear() {
        prefs.edit().clear().apply();
    }
}
