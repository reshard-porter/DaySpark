package com.dayspark.app.billing;

import android.content.Context;
import android.os.SystemClock;

/** Debug-only verifier paired with {@link SimulatedBillingGateway}. Accepts only simulator tokens. */
final class SimulatedPurchaseVerifier implements PurchaseVerifier {

    private final Context context;

    SimulatedPurchaseVerifier(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public VerificationResult verify(PurchaseRecord p) {
        SystemClock.sleep(500); // network latency
        DebugBillingSettings.Scenario s = DebugBillingSettings.scenario(context);
        if (s == DebugBillingSettings.Scenario.VERIFY_OFFLINE) {
            return VerificationResult.unavailable("Simulated network failure");
        }
        if (s == DebugBillingSettings.Scenario.VERIFY_REJECTS
                || !p.purchaseToken.startsWith(SimulatedBillingGateway.TOKEN_PREFIX)) {
            return new VerificationResult(VerificationResult.Status.INVALID, false, "Simulated rejection");
        }
        String revoked = DebugBillingSettings.prefs(context).getString("revoked_token", "");
        if (p.purchaseToken.equals(revoked)) {
            return new VerificationResult(VerificationResult.Status.REVOKED, false, "Simulated refund");
        }
        if (p.state == PurchaseRecord.State.PENDING) {
            return new VerificationResult(VerificationResult.Status.PENDING, false, "");
        }
        return new VerificationResult(VerificationResult.Status.VERIFIED, false, "");
    }
}
