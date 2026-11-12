package com.dayspark.app.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.dayspark.app.BuildConfig;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App-wide entry point for the Pro theme pack purchase. Wires {@link PremiumEngine} to Android:
 * main-thread delivery, a background thread for verification, and the gateway/verifier chosen
 * by the build type's {@code BillingFactory} (real Play in release; simulator option in debug).
 */
public final class PremiumManager {

    public static final String PRODUCT_ID = BuildConfig.PRO_THEMES_PRODUCT_ID;

    private static PremiumManager instance;

    private final BillingGateway gateway;
    private final PremiumEngine engine;
    private final String installId;

    private PremiumManager(Context context) {
        Context app = context.getApplicationContext();
        installId = loadInstallId(app);
        gateway = BillingFactory.createGateway(app);
        PurchaseVerifier verifier = BillingFactory.createVerifier(app, installId);
        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService io = Executors.newSingleThreadExecutor();
        engine = new PremiumEngine(PRODUCT_ID, gateway, verifier, new PrefsEntitlementStorage(app),
                io, main::post, main::postDelayed, System::currentTimeMillis);
    }

    /** Main thread only. */
    public static PremiumManager get(Context context) {
        if (instance == null) instance = new PremiumManager(context);
        return instance;
    }

    public void start() { engine.start(); }

    public void refresh() { engine.refresh(); }

    public void restore() { engine.restore(); }

    public boolean isPremiumActive() { return engine.isPremiumActive(); }

    public PremiumSnapshot snapshot() { return engine.snapshot(); }

    public void addListener(PremiumEngine.Listener l) { engine.addListener(l); }

    public void removeListener(PremiumEngine.Listener l) { engine.removeListener(l); }

    public BillingGateway gateway() { return gateway; }

    /** Opens the Google Play purchase sheet for the Pro theme pack. */
    public void purchase(Activity activity) {
        PremiumSnapshot s = engine.snapshot();
        if (s.premiumActive || s.state == PremiumState.PURCHASING || s.state == PremiumState.VERIFYING) {
            return; // ignore double taps and purchases of something already owned
        }
        engine.onPurchaseFlowStarting();
        int code = ((PurchaseLauncher) gateway).launchPurchase(activity, PRODUCT_ID, installId);
        if (code != BillingCodes.OK) engine.onPurchaseFlowLaunchFailed(code);
    }

    private static String loadInstallId(Context app) {
        SharedPreferences prefs = app.getSharedPreferences("dayspark_install", Context.MODE_PRIVATE);
        String id = prefs.getString("install_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("install_id", id).apply();
        }
        return id;
    }
}
