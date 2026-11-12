package com.dayspark.app.billing;

/** Confirms a purchase before access is granted. Called on a background thread; may block. */
public interface PurchaseVerifier {
    VerificationResult verify(PurchaseRecord purchase);
}
