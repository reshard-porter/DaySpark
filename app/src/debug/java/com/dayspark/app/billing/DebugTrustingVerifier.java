package com.dayspark.app.billing;

/**
 * DEBUG ONLY. Lets license testers try real Google Play purchases before the verification
 * service is deployed. It checks package, product and state but cannot detect forged or
 * refunded tokens, which is why it never ships in release builds.
 */
final class DebugTrustingVerifier implements PurchaseVerifier {

    private final String packageName;

    DebugTrustingVerifier(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public VerificationResult verify(PurchaseRecord p) {
        if (!packageName.equals(p.packageName) || !p.isFor(PremiumManager.PRODUCT_ID)) {
            return new VerificationResult(VerificationResult.Status.INVALID, false, "Wrong package or product");
        }
        if (p.state == PurchaseRecord.State.PENDING) {
            return new VerificationResult(VerificationResult.Status.PENDING, false, "");
        }
        return new VerificationResult(VerificationResult.Status.VERIFIED, false, "Trusted locally (debug)");
    }
}
