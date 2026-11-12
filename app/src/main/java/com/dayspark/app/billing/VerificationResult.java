package com.dayspark.app.billing;

/** Answer from the purchase verification service. */
public final class VerificationResult {

    public enum Status {
        /** Google confirms a completed purchase of the product for this app. */
        VERIFIED,
        /** Payment not finished yet (for example cash payment at a store). */
        PENDING,
        /** Token is unknown, for another app or product, or malformed. */
        INVALID,
        /** Purchase was refunded, charged back or otherwise voided. */
        REVOKED,
        /** Service could not be reached or failed temporarily. Says nothing about ownership. */
        UNAVAILABLE
    }

    public final Status status;
    /** True when the server already acknowledged the purchase with Google. */
    public final boolean acknowledgedByServer;
    public final String detail;

    public VerificationResult(Status status, boolean acknowledgedByServer, String detail) {
        this.status = status;
        this.acknowledgedByServer = acknowledgedByServer;
        this.detail = detail == null ? "" : detail;
    }

    public static VerificationResult unavailable(String detail) {
        return new VerificationResult(Status.UNAVAILABLE, false, detail);
    }
}
