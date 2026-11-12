package com.dayspark.app.billing;

/**
 * Persistent record of the last <em>verified</em> ownership. Only {@link PremiumEngine} writes it.
 * Pending or unverified purchases are never stored here.
 */
public interface EntitlementStorage {
    boolean isOwned();

    String purchaseToken();

    /** Wall-clock time of the last successful server verification. */
    long verifiedAtMillis();

    boolean isAcknowledged();

    void grant(String purchaseToken, long verifiedAtMillis);

    void markAcknowledged(String purchaseToken);

    void clear();
}
