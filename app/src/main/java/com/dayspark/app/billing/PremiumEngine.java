package com.dayspark.app.billing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Purchase ownership rules for the single non-consumable {@code dayspark_pro_themes} product.
 *
 * <p>Plain Java with injected dependencies, so every rule below is covered by unit tests.
 * All public methods and listener callbacks run on the main executor.
 *
 * <p>Rules:
 * <ol>
 *   <li>Access is granted only after the verifier returns VERIFIED. Pending or unverified
 *       purchases never unlock themes.</li>
 *   <li>Verified purchases are acknowledged (unless the server already did). Failed
 *       acknowledgements retry with backoff, and again on every app start until Play reports
 *       the purchase as acknowledged. Unacknowledged purchases are refunded by Google after
 *       three days, so this matters.</li>
 *   <li>Duplicate callbacks for the same token are ignored while a verification or
 *       acknowledgement is in flight.</li>
 *   <li>Offline policy: a previously verified purchase stays active for
 *       {@link #OFFLINE_GRACE_MS} after the last successful verification when the service is
 *       unreachable. After that, themes fall back to the default until verification succeeds.</li>
 *   <li>Refunds and revocations: ownership is removed when the server reports REVOKED or
 *       INVALID, or when Google Play successfully reports that the account no longer owns the
 *       product.</li>
 * </ol>
 */
public final class PremiumEngine {

    public interface Listener {
        void onPremiumChanged(PremiumSnapshot snapshot);

        void onPremiumEvent(PremiumEvent event);
    }

    public interface Scheduler {
        /** Runs {@code task} on the main executor after {@code delayMillis}. */
        void schedule(Runnable task, long delayMillis);
    }

    public interface Clock {
        long nowMillis();
    }

    public static final long OFFLINE_GRACE_MS = TimeUnit.DAYS.toMillis(30);
    public static final long REVERIFY_AFTER_MS = TimeUnit.HOURS.toMillis(24);
    static final long[] RETRY_DELAYS_MS = {2_000, 5_000, 15_000, 60_000, 300_000};

    private final String productId;
    private final BillingGateway gateway;
    private final PurchaseVerifier verifier;
    private final EntitlementStorage storage;
    private final Executor io;
    private final Executor main;
    private final Scheduler scheduler;
    private final Clock clock;

    private final List<Listener> listeners = new ArrayList<>();
    private final Set<String> verifying = new HashSet<>();
    private final Set<String> acknowledging = new HashSet<>();
    private final Map<String, Integer> verifyAttempts = new HashMap<>();
    private final Map<String, Integer> ackAttempts = new HashMap<>();

    private PremiumState state = PremiumState.LOADING;
    private ProductInfo product;
    private boolean purchaseFlowActive;
    private boolean restoreRequested;
    /** Token of a purchase seen as pending, so its later completion counts as a new purchase. */
    private String pendingToken;

    public PremiumEngine(String productId, BillingGateway gateway, PurchaseVerifier verifier,
                         EntitlementStorage storage, Executor io, Executor main,
                         Scheduler scheduler, Clock clock) {
        this.productId = productId;
        this.gateway = gateway;
        this.verifier = verifier;
        this.storage = storage;
        this.io = io;
        this.main = main;
        this.scheduler = scheduler;
        this.clock = clock;
        if (isPremiumActive()) state = PremiumState.OWNED;
    }

    // ---------------------------------------------------------------- public API

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.onPremiumChanged(snapshot());
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public PremiumSnapshot snapshot() {
        return new PremiumSnapshot(state, isPremiumActive(),
                product == null ? null : product.formattedPrice, gateway.isSimulated());
    }

    /** True only for a verified purchase within the offline grace window. */
    public boolean isPremiumActive() {
        if (!storage.isOwned()) return false;
        long age = clock.nowMillis() - storage.verifiedAtMillis();
        return age <= OFFLINE_GRACE_MS; // a clock moved backwards (age < 0) keeps access
    }

    /** Connects, loads the product and reconciles ownership. Safe to call repeatedly (app start, resume). */
    public void start() {
        gateway.setPurchasesListener(this::onPurchasesUpdated);
        refresh();
    }

    public void refresh() {
        connectThen(() -> {
            if (product == null) loadProduct();
            queryOwnership(false);
        });
    }

    /** "Restore purchases": always re-queries Google Play and re-verifies. */
    public void restore() {
        restoreRequested = true;
        connectThen(() -> queryOwnership(true));
    }

    /** Called by the UI just before it launches the Play purchase sheet. */
    public void onPurchaseFlowStarting() {
        purchaseFlowActive = true;
        setState(PremiumState.PURCHASING);
    }

    /** Called when launching the purchase sheet itself failed. */
    public void onPurchaseFlowLaunchFailed(int code) {
        onPurchasesUpdated(code, BillingCodes.SUB_NONE, new ArrayList<>());
    }

    public ProductInfo product() {
        return product;
    }

    // ---------------------------------------------------------------- store callbacks

    void onPurchasesUpdated(int code, int subCode, List<PurchaseRecord> purchases) {
        switch (code) {
            case BillingCodes.OK:
                boolean found = false;
                if (purchases != null) {
                    for (PurchaseRecord p : purchases) {
                        if (p.isFor(productId)) {
                            found = true;
                            handlePurchase(p, true);
                        }
                    }
                }
                if (!found) endPurchaseFlow(null);
                break;
            case BillingCodes.USER_CANCELED:
                endPurchaseFlow(PremiumEvent.CANCELLED);
                break;
            case BillingCodes.ITEM_ALREADY_OWNED:
                purchaseFlowActive = false;
                emit(PremiumEvent.ALREADY_OWNED);
                restore();
                break;
            case BillingCodes.BILLING_UNAVAILABLE:
                purchaseFlowActive = false;
                setState(isPremiumActive() ? PremiumState.OWNED : PremiumState.BILLING_UNAVAILABLE);
                emit(PremiumEvent.ERROR);
                break;
            case BillingCodes.ERROR:
                endPurchaseFlow(subCode == BillingCodes.SUB_PAYMENT_DECLINED_INSUFFICIENT_FUNDS
                        ? PremiumEvent.DECLINED : PremiumEvent.ERROR);
                break;
            default:
                endPurchaseFlow(PremiumEvent.ERROR);
                break;
        }
    }

    // ---------------------------------------------------------------- internals

    private void connectThen(Runnable action) {
        gateway.connect(code -> {
            if (code == BillingCodes.OK) {
                action.run();
            } else {
                if (restoreRequested) {
                    restoreRequested = false;
                    emit(PremiumEvent.RESTORE_FAILED);
                }
                if (!isPremiumActive()) {
                    setState(code == BillingCodes.BILLING_UNAVAILABLE
                            ? PremiumState.BILLING_UNAVAILABLE : PremiumState.PRODUCT_UNAVAILABLE);
                }
            }
        });
    }

    private void loadProduct() {
        gateway.queryProduct(productId, (code, info) -> {
            if (info != null) product = info;
            if (!isPremiumActive() && isIdleState()) {
                setState(product != null ? PremiumState.READY : PremiumState.PRODUCT_UNAVAILABLE);
            } else {
                notifyChanged();
            }
        });
    }

    private void queryOwnership(boolean userInitiated) {
        gateway.queryOwnedPurchases((code, purchases) -> {
            if (code != BillingCodes.OK) {
                // Unknown, not "not owned": keep any verified access (offline policy).
                if (userInitiated) {
                    restoreRequested = false;
                    emit(PremiumEvent.RESTORE_FAILED);
                }
                return;
            }
            PurchaseRecord match = null;
            for (PurchaseRecord p : purchases) {
                if (p.isFor(productId)) {
                    match = p;
                    break;
                }
            }
            if (match == null) {
                if (storage.isOwned()) {
                    revoke(); // refunded or revoked: Play no longer reports ownership
                } else if (isIdleState() || state == PremiumState.PENDING) {
                    setState(product != null ? PremiumState.READY : state);
                }
                if (userInitiated) {
                    restoreRequested = false;
                    emit(PremiumEvent.NOTHING_TO_RESTORE);
                }
                return;
            }
            handlePurchase(match, userInitiated || needsReverify(match));
        });
    }

    private boolean needsReverify(PurchaseRecord p) {
        if (!storage.isOwned() || !p.purchaseToken.equals(storage.purchaseToken())) return true;
        return clock.nowMillis() - storage.verifiedAtMillis() > REVERIFY_AFTER_MS;
    }

    private void handlePurchase(PurchaseRecord p, boolean forceVerify) {
        if (p.state == PurchaseRecord.State.PENDING) {
            boolean wasPending = state == PremiumState.PENDING;
            purchaseFlowActive = false;
            pendingToken = p.purchaseToken;
            if (!isPremiumActive()) {
                setState(PremiumState.PENDING);
                if (!wasPending) emit(PremiumEvent.PENDING_STARTED);
            }
            if (restoreRequested) restoreRequested = false;
            return;
        }
        if (p.state != PurchaseRecord.State.PURCHASED) return;

        boolean knownAndFresh = storage.isOwned()
                && p.purchaseToken.equals(storage.purchaseToken())
                && isPremiumActive();
        if (knownAndFresh && !forceVerify) {
            if (!p.acknowledged) acknowledge(p.purchaseToken);
            setState(PremiumState.OWNED);
            return;
        }
        verify(p);
    }

    private void verify(PurchaseRecord p) {
        if (!verifying.add(p.purchaseToken)) return; // duplicate callback while in flight
        if (!isPremiumActive()) setState(PremiumState.VERIFYING);
        io.execute(() -> {
            VerificationResult result;
            try {
                result = verifier.verify(p);
            } catch (RuntimeException e) {
                result = VerificationResult.unavailable(e.getClass().getSimpleName());
            }
            final VerificationResult r = result;
            main.execute(() -> onVerified(p, r));
        });
    }

    private void onVerified(PurchaseRecord p, VerificationResult r) {
        verifying.remove(p.purchaseToken);
        boolean wasActive = isPremiumActive();
        switch (r.status) {
            case VERIFIED:
                verifyAttempts.remove(p.purchaseToken);
                storage.grant(p.purchaseToken, clock.nowMillis());
                if (p.acknowledged || r.acknowledgedByServer) {
                    storage.markAcknowledged(p.purchaseToken);
                } else {
                    acknowledge(p.purchaseToken);
                }
                setState(PremiumState.OWNED);
                boolean wasPendingPurchase = p.purchaseToken.equals(pendingToken);
                pendingToken = null;
                if (purchaseFlowActive || wasPendingPurchase) {
                    emit(PremiumEvent.PURCHASE_COMPLETE);
                } else if (!wasActive || restoreRequested) {
                    emit(PremiumEvent.RESTORED);
                }
                purchaseFlowActive = false;
                restoreRequested = false;
                break;
            case PENDING:
                handlePurchase(new PurchaseRecord(p.productIds, p.purchaseToken, p.orderId,
                        p.packageName, PurchaseRecord.State.PENDING, false,
                        p.purchaseTimeMillis), false);
                break;
            case INVALID:
            case REVOKED:
                verifyAttempts.remove(p.purchaseToken);
                purchaseFlowActive = false;
                restoreRequested = false;
                if (storage.isOwned()) {
                    revoke();
                } else {
                    setState(product != null ? PremiumState.READY : PremiumState.PRODUCT_UNAVAILABLE);
                    emit(PremiumEvent.VERIFICATION_FAILED);
                }
                break;
            case UNAVAILABLE:
            default:
                if (wasActive && p.purchaseToken.equals(storage.purchaseToken())) {
                    setState(PremiumState.OWNED); // offline grace keeps verified access
                } else {
                    setState(PremiumState.VERIFICATION_DELAYED);
                }
                if (restoreRequested) {
                    restoreRequested = false;
                    if (!wasActive) emit(PremiumEvent.RESTORE_FAILED);
                }
                scheduleVerifyRetry(p);
                break;
        }
    }

    private void scheduleVerifyRetry(PurchaseRecord p) {
        int attempt = verifyAttempts.getOrDefault(p.purchaseToken, 0);
        if (attempt >= RETRY_DELAYS_MS.length) return; // next app start or resume tries again
        verifyAttempts.put(p.purchaseToken, attempt + 1);
        scheduler.schedule(() -> verify(p), RETRY_DELAYS_MS[attempt]);
    }

    private void acknowledge(String token) {
        if (!acknowledging.add(token)) return;
        gateway.acknowledge(token, code -> {
            acknowledging.remove(token);
            if (code == BillingCodes.OK) {
                ackAttempts.remove(token);
                if (token.equals(storage.purchaseToken())) storage.markAcknowledged(token);
            } else if (BillingCodes.isRetriable(code)) {
                int attempt = ackAttempts.getOrDefault(token, 0);
                if (attempt < RETRY_DELAYS_MS.length) {
                    ackAttempts.put(token, attempt + 1);
                    scheduler.schedule(() -> acknowledge(token), RETRY_DELAYS_MS[attempt]);
                }
            }
        });
    }

    private void revoke() {
        storage.clear();
        setState(product != null ? PremiumState.READY : PremiumState.PRODUCT_UNAVAILABLE);
        emit(PremiumEvent.REVOKED);
    }

    private void endPurchaseFlow(PremiumEvent event) {
        purchaseFlowActive = false;
        if (state == PremiumState.PURCHASING) {
            setState(isPremiumActive() ? PremiumState.OWNED
                    : (product != null ? PremiumState.READY : PremiumState.PRODUCT_UNAVAILABLE));
        }
        if (event != null) emit(event);
    }

    private boolean isIdleState() {
        return state == PremiumState.LOADING || state == PremiumState.READY
                || state == PremiumState.PRODUCT_UNAVAILABLE || state == PremiumState.BILLING_UNAVAILABLE;
    }

    private void setState(PremiumState newState) {
        state = newState;
        notifyChanged();
    }

    private void notifyChanged() {
        PremiumSnapshot s = snapshot();
        for (Listener l : new ArrayList<>(listeners)) l.onPremiumChanged(s);
    }

    private void emit(PremiumEvent event) {
        for (Listener l : new ArrayList<>(listeners)) l.onPremiumEvent(event);
    }
}
