package com.dayspark.app.billing;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real Google Play Billing (Play Billing Library 9, Java APIs). */
public final class PlayBillingGateway implements BillingGateway, PurchaseLauncher {

    private static final String TAG = "PlayBilling";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final BillingClient client;
    private final List<ResultCallback> pendingConnects = new ArrayList<>();
    private final Map<String, ProductDetails> detailsCache = new HashMap<>();
    private PurchasesListener listener;
    private boolean connecting;

    public PlayBillingGateway(Context context) {
        client = BillingClient.newBuilder(context.getApplicationContext())
                .setListener(this::onPurchasesUpdated)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build())
                .enableAutoServiceReconnection()
                .build();
    }

    @Override
    public void setPurchasesListener(PurchasesListener listener) {
        this.listener = listener;
    }

    @Override
    public void connect(ResultCallback callback) {
        if (client.isReady()) {
            main.post(() -> callback.onResult(BillingCodes.OK));
            return;
        }
        pendingConnects.add(callback);
        if (connecting) return;
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                main.post(() -> {
                    connecting = false;
                    List<ResultCallback> waiting = new ArrayList<>(pendingConnects);
                    pendingConnects.clear();
                    for (ResultCallback cb : waiting) cb.onResult(result.getResponseCode());
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call.
                main.post(() -> connecting = false);
            }
        });
    }

    @Override
    public void queryProduct(String productId, ProductCallback callback) {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()))
                .build();
        client.queryProductDetailsAsync(params, (result, queryResult) -> {
            ProductInfo info = null;
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && queryResult != null) {
                for (ProductDetails d : queryResult.getProductDetailsList()) {
                    ProductDetails.OneTimePurchaseOfferDetails offer = d.getOneTimePurchaseOfferDetails();
                    if (d.getProductId().equals(productId) && offer != null) {
                        detailsCache.put(productId, d);
                        info = new ProductInfo(productId, d.getName(), offer.getFormattedPrice());
                    }
                }
            } else {
                Log.w(TAG, "Product query failed: " + result.getDebugMessage());
            }
            final ProductInfo out = info;
            main.post(() -> callback.onResult(result.getResponseCode(), out));
        });
    }

    @Override
    public void queryOwnedPurchases(PurchasesCallback callback) {
        client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (result, purchases) -> {
                    List<PurchaseRecord> records = map(purchases);
                    main.post(() -> callback.onResult(result.getResponseCode(), records));
                });
    }

    @Override
    public void acknowledge(String purchaseToken, ResultCallback callback) {
        client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build(),
                result -> main.post(() -> callback.onResult(result.getResponseCode())));
    }

    @Override
    public int launchPurchase(Activity activity, String productId, String obfuscatedAccountId) {
        ProductDetails details = detailsCache.get(productId);
        if (details == null) return BillingCodes.ITEM_UNAVAILABLE;
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .build()))
                .setObfuscatedAccountId(obfuscatedAccountId)
                .build();
        return client.launchBillingFlow(activity, params).getResponseCode();
    }

    @Override
    public void disconnect() {
        client.endConnection();
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    private void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        final int code = result.getResponseCode();
        final int sub = result.getOnPurchasesUpdatedSubResponseCode();
        final List<PurchaseRecord> records = map(purchases);
        main.post(() -> {
            if (listener != null) listener.onPurchasesUpdated(code, sub, records);
        });
    }

    private static List<PurchaseRecord> map(List<Purchase> purchases) {
        List<PurchaseRecord> out = new ArrayList<>();
        if (purchases == null) return out;
        for (Purchase p : purchases) {
            PurchaseRecord.State state;
            switch (p.getPurchaseState()) {
                case Purchase.PurchaseState.PURCHASED:
                    state = PurchaseRecord.State.PURCHASED;
                    break;
                case Purchase.PurchaseState.PENDING:
                    state = PurchaseRecord.State.PENDING;
                    break;
                default:
                    state = PurchaseRecord.State.UNSPECIFIED;
            }
            out.add(new PurchaseRecord(p.getProducts(), p.getPurchaseToken(), p.getOrderId(),
                    p.getPackageName(), state, p.isAcknowledged(), p.getPurchaseTime()));
        }
        return out;
    }
}
