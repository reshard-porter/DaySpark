package com.dayspark.app.billing;

import java.util.List;

/**
 * Store connection used by {@link PremiumEngine}. Implementations: {@code PlayBillingGateway}
 * (real Google Play) and, in debug builds only, {@code SimulatedBillingGateway}.
 *
 * Contract: every callback is delivered on the main thread.
 */
public interface BillingGateway {

    interface PurchasesListener {
        void onPurchasesUpdated(int code, int subCode, List<PurchaseRecord> purchases);
    }

    interface ResultCallback {
        void onResult(int code);
    }

    interface ProductCallback {
        void onResult(int code, ProductInfo product);
    }

    interface PurchasesCallback {
        void onResult(int code, List<PurchaseRecord> purchases);
    }

    void setPurchasesListener(PurchasesListener listener);

    /** Connects if needed. Calls back immediately with OK when already connected. */
    void connect(ResultCallback callback);

    void queryProduct(String productId, ProductCallback callback);

    /** Current one-time purchases owned by the signed-in Play account (includes pending ones). */
    void queryOwnedPurchases(PurchasesCallback callback);

    void acknowledge(String purchaseToken, ResultCallback callback);

    void disconnect();

    /** True for the debug simulator, so the UI can label it clearly. */
    boolean isSimulated();
}
