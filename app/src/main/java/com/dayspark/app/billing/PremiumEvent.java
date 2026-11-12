package com.dayspark.app.billing;

/** One-time outcomes the UI announces (snackbar plus accessibility announcement). */
public enum PremiumEvent {
    PURCHASE_COMPLETE,
    RESTORED,
    PENDING_STARTED,
    CANCELLED,
    DECLINED,
    ALREADY_OWNED,
    NOTHING_TO_RESTORE,
    RESTORE_FAILED,
    VERIFICATION_FAILED,
    REVOKED,
    ERROR
}
