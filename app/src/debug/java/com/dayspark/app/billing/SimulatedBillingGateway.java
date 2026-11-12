package com.dayspark.app.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Debug-only stand-in for Google Play. Simulates purchase outcomes chosen in the debug panel
 * and keeps "owned" state across restarts so recovery paths can be exercised without Play.
 * Never compiled into release builds.
 */
final class SimulatedBillingGateway implements BillingGateway, PurchaseLauncher {

    private static final long LATENCY_MS = 600;
    static final String TOKEN_PREFIX = "sim-";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PurchasesListener listener;

    SimulatedBillingGateway(Context context) {
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return DebugBillingSettings.prefs(context);
    }

    @Override public void setPurchasesListener(PurchasesListener l) { listener = l; }

    @Override public void connect(ResultCallback cb) { main.post(() -> cb.onResult(BillingCodes.OK)); }

    @Override
    public void queryProduct(String productId, ProductCallback cb) {
        main.postDelayed(() -> cb.onResult(BillingCodes.OK,
                new ProductInfo(productId, "Pro theme pack", "$2.99")), LATENCY_MS);
    }

    @Override
    public void queryOwnedPurchases(PurchasesCallback cb) {
        main.postDelayed(() -> cb.onResult(BillingCodes.OK, owned()), LATENCY_MS);
    }

    @Override
    public void acknowledge(String token, ResultCallback cb) {
        int failures = prefs().getInt("ack_failures_left", 0);
        if (failures > 0) {
            prefs().edit().putInt("ack_failures_left", failures - 1).apply();
            main.postDelayed(() -> cb.onResult(BillingCodes.SERVICE_UNAVAILABLE), LATENCY_MS);
            return;
        }
        prefs().edit().putBoolean("acknowledged", true).apply();
        main.postDelayed(() -> cb.onResult(BillingCodes.OK), LATENCY_MS);
    }

    @Override public void disconnect() {}

    @Override public boolean isSimulated() { return true; }

    @Override
    public int launchPurchase(Activity activity, String productId, String accountId) {
        DebugBillingSettings.Scenario s = DebugBillingSettings.scenario(context);
        if (s == DebugBillingSettings.Scenario.LAUNCH_ERROR) return BillingCodes.SERVICE_UNAVAILABLE;
        if (prefs().getString("token", null) != null && "purchased".equals(prefs().getString("state", ""))) {
            deliver(BillingCodes.ITEM_ALREADY_OWNED, BillingCodes.SUB_NONE, new ArrayList<>());
            return BillingCodes.OK;
        }
        switch (s) {
            case CANCEL:
                deliver(BillingCodes.USER_CANCELED, BillingCodes.SUB_NONE, new ArrayList<>());
                break;
            case DECLINE:
                deliver(BillingCodes.ERROR, BillingCodes.SUB_PAYMENT_DECLINED_INSUFFICIENT_FUNDS, new ArrayList<>());
                break;
            case ALREADY_OWNED:
                store("purchased", false);
                deliver(BillingCodes.ITEM_ALREADY_OWNED, BillingCodes.SUB_NONE, new ArrayList<>());
                break;
            case PENDING:
                store("pending", false);
                deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
                break;
            case PENDING_APPROVES_LATER:
                // Like Play's "slow test card, approves after a few minutes", but in 6 seconds.
                store("pending", false);
                deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
                main.postDelayed(this::completePendingPayment, 6_000);
                break;
            case ACK_FAILS_TWICE:
                prefs().edit().putInt("ack_failures_left", 2).apply();
                store("purchased", false);
                deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
                break;
            case DUPLICATE_CALLBACKS:
                store("purchased", false);
                deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
                deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
                deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
                break;
            default:
                store("purchased", false);
                deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
        }
        return BillingCodes.OK;
    }

    /** Debug panel: a pending cash payment clears, as Play would report it. */
    void completePendingPayment() {
        if (!"pending".equals(prefs().getString("state", ""))) return;
        prefs().edit().putString("state", "purchased").apply();
        deliver(BillingCodes.OK, BillingCodes.SUB_NONE, owned());
    }

    /** Debug panel: the purchase is refunded; Play stops reporting it. */
    void refund() {
        String token = prefs().getString("token", null);
        if (token != null) {
            prefs().edit().remove("token").remove("state").remove("acknowledged")
                    .putString("revoked_token", token).apply();
        }
    }

    void reset() {
        prefs().edit().remove("token").remove("state").remove("acknowledged")
                .remove("revoked_token").remove("ack_failures_left").apply();
    }

    private void store(String state, boolean acknowledged) {
        prefs().edit()
                .putString("token", TOKEN_PREFIX + UUID.randomUUID())
                .putString("state", state)
                .putBoolean("acknowledged", acknowledged)
                .apply();
    }

    private List<PurchaseRecord> owned() {
        String token = prefs().getString("token", null);
        if (token == null) return new ArrayList<>();
        PurchaseRecord.State state = "pending".equals(prefs().getString("state", ""))
                ? PurchaseRecord.State.PENDING : PurchaseRecord.State.PURCHASED;
        List<PurchaseRecord> list = new ArrayList<>();
        list.add(new PurchaseRecord(Collections.singletonList(PremiumManager.PRODUCT_ID), token,
                "SIM.ORDER", context.getPackageName(), state,
                prefs().getBoolean("acknowledged", false), System.currentTimeMillis()));
        return list;
    }

    private void deliver(int code, int sub, List<PurchaseRecord> purchases) {
        main.postDelayed(() -> {
            if (listener != null) listener.onPurchasesUpdated(code, sub, purchases);
        }, LATENCY_MS);
    }
}
