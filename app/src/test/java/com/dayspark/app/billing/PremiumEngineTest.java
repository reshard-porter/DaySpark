package com.dayspark.app.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Covers the purchase rules in {@link PremiumEngine} with in-memory fakes. */
public class PremiumEngineTest {

    private static final String PID = "dayspark_pro_themes";
    private static final String PKG = "com.dayspark.app";

    private FakeGateway gateway;
    private FakeVerifier verifier;
    private MemoryStorage storage;
    private ManualScheduler scheduler;
    private QueueExecutor io;
    private long now = TimeUnit.DAYS.toMillis(20_000);
    private final List<PremiumEvent> events = new ArrayList<>();
    private PremiumSnapshot last;
    private PremiumEngine engine;

    @Before
    public void setUp() {
        gateway = new FakeGateway();
        verifier = new FakeVerifier();
        storage = new MemoryStorage();
        scheduler = new ManualScheduler();
        io = new QueueExecutor();
    }

    private void startEngine() {
        Executor direct = Runnable::run;
        engine = new PremiumEngine(PID, gateway, verifier, storage, io, direct, scheduler, () -> now);
        engine.addListener(new PremiumEngine.Listener() {
            @Override public void onPremiumChanged(PremiumSnapshot s) { last = s; }

            @Override public void onPremiumEvent(PremiumEvent e) { events.add(e); }
        });
        engine.start();
        io.runAll();
    }

    private static PurchaseRecord purchase(String token, PurchaseRecord.State state, boolean acked) {
        return new PurchaseRecord(Collections.singletonList(PID), token, "GPA.1", PKG, state, acked, 0);
    }

    private void buy(PurchaseRecord p) {
        engine.onPurchaseFlowStarting();
        gateway.emit(BillingCodes.OK, BillingCodes.SUB_NONE, Collections.singletonList(p));
        io.runAll();
    }

    // ------------------------------------------------------------------ purchase outcomes

    @Test
    public void successfulPurchaseIsVerifiedGrantedAndAcknowledged() {
        startEngine();
        assertEquals(PremiumState.READY, last.state);
        assertEquals("$2.99", last.formattedPrice);

        buy(purchase("t1", PurchaseRecord.State.PURCHASED, false));

        assertTrue(engine.isPremiumActive());
        assertEquals(PremiumState.OWNED, last.state);
        assertEquals(1, verifier.calls);
        assertEquals(1, gateway.ackCalls);
        assertTrue(storage.acked);
        assertTrue(events.contains(PremiumEvent.PURCHASE_COMPLETE));
    }

    @Test
    public void pendingPurchaseNeverUnlocksUntilItCompletes() {
        startEngine();
        buy(purchase("t1", PurchaseRecord.State.PENDING, false));
        assertFalse(engine.isPremiumActive());
        assertEquals(PremiumState.PENDING, last.state);
        assertEquals(0, verifier.calls);
        assertEquals(0, gateway.ackCalls);
        assertTrue(events.contains(PremiumEvent.PENDING_STARTED));

        // Payment clears later: Play reports PURCHASED.
        gateway.emit(BillingCodes.OK, BillingCodes.SUB_NONE,
                Collections.singletonList(purchase("t1", PurchaseRecord.State.PURCHASED, false)));
        io.runAll();
        assertTrue(engine.isPremiumActive());
        // A cleared pending payment is announced as a completed purchase, not a restore.
        assertTrue(events.contains(PremiumEvent.PURCHASE_COMPLETE));
        assertFalse(events.contains(PremiumEvent.RESTORED));
    }

    @Test
    public void serverSaysPendingKeepsLocked() {
        verifier.next(VerificationResult.Status.PENDING);
        startEngine();
        buy(purchase("t1", PurchaseRecord.State.PURCHASED, false));
        assertFalse(engine.isPremiumActive());
        assertEquals(PremiumState.PENDING, last.state);
    }

    @Test
    public void unverifiedPurchaseStaysLockedAndRetries() {
        verifier.next(VerificationResult.Status.UNAVAILABLE);
        startEngine();
        buy(purchase("t1", PurchaseRecord.State.PURCHASED, false));
        assertFalse(engine.isPremiumActive());
        assertEquals(PremiumState.VERIFICATION_DELAYED, last.state);
        assertEquals(0, gateway.ackCalls); // never acknowledge what we haven't verified

        scheduler.runAll(); // backoff retry; verifier now succeeds
        io.runAll();
        assertTrue(engine.isPremiumActive());
        assertTrue(events.contains(PremiumEvent.PURCHASE_COMPLETE));
    }

    @Test
    public void invalidPurchaseIsRejected() {
        verifier.next(VerificationResult.Status.INVALID);
        startEngine();
        buy(purchase("forged", PurchaseRecord.State.PURCHASED, false));
        assertFalse(engine.isPremiumActive());
        assertEquals(PremiumState.READY, last.state);
        assertTrue(events.contains(PremiumEvent.VERIFICATION_FAILED));
    }

    @Test
    public void cancelledPurchaseReturnsToReady() {
        startEngine();
        engine.onPurchaseFlowStarting();
        gateway.emit(BillingCodes.USER_CANCELED, BillingCodes.SUB_NONE, null);
        assertEquals(PremiumState.READY, last.state);
        assertEquals(Collections.singletonList(PremiumEvent.CANCELLED), events);
    }

    @Test
    public void declinedPaymentIsReportedAsDeclined() {
        startEngine();
        engine.onPurchaseFlowStarting();
        gateway.emit(BillingCodes.ERROR, BillingCodes.SUB_PAYMENT_DECLINED_INSUFFICIENT_FUNDS, null);
        assertEquals(PremiumState.READY, last.state);
        assertTrue(events.contains(PremiumEvent.DECLINED));
    }

    @Test
    public void launchFailureEndsFlow() {
        startEngine();
        engine.onPurchaseFlowStarting();
        engine.onPurchaseFlowLaunchFailed(BillingCodes.SERVICE_UNAVAILABLE);
        assertEquals(PremiumState.READY, last.state);
        assertTrue(events.contains(PremiumEvent.ERROR));
    }

    @Test
    public void alreadyOwnedTriggersRestore() {
        startEngine();
        gateway.owned.add(purchase("t1", PurchaseRecord.State.PURCHASED, true));
        engine.onPurchaseFlowStarting();
        gateway.emit(BillingCodes.ITEM_ALREADY_OWNED, BillingCodes.SUB_NONE, null);
        io.runAll();
        assertTrue(engine.isPremiumActive());
        assertTrue(events.contains(PremiumEvent.ALREADY_OWNED));
        assertTrue(events.contains(PremiumEvent.RESTORED));
        assertEquals(0, gateway.ackCalls); // already acknowledged on Play
    }

    // ------------------------------------------------------------------ robustness

    @Test
    public void duplicateCallbacksVerifyAndAcknowledgeOnce() {
        startEngine();
        PurchaseRecord p = purchase("t1", PurchaseRecord.State.PURCHASED, false);
        engine.onPurchaseFlowStarting();
        gateway.emit(BillingCodes.OK, BillingCodes.SUB_NONE, Collections.singletonList(p));
        gateway.emit(BillingCodes.OK, BillingCodes.SUB_NONE, Collections.singletonList(p));
        gateway.emit(BillingCodes.OK, BillingCodes.SUB_NONE, Collections.singletonList(p));
        io.runAll();
        assertEquals(1, verifier.calls);
        assertEquals(1, gateway.ackCalls);
        assertEquals(1, Collections.frequency(events, PremiumEvent.PURCHASE_COMPLETE));
    }

    @Test
    public void failedAcknowledgementRetriesWithBackoff() {
        gateway.ackResults.add(BillingCodes.SERVICE_UNAVAILABLE);
        gateway.ackResults.add(BillingCodes.NETWORK_ERROR);
        startEngine();
        buy(purchase("t1", PurchaseRecord.State.PURCHASED, false));
        assertTrue(engine.isPremiumActive());
        assertFalse(storage.acked);
        assertEquals(1, gateway.ackCalls);

        scheduler.runAll();
        assertEquals(3, gateway.ackCalls);
        assertTrue(storage.acked);
        assertEquals(PremiumEngine.RETRY_DELAYS_MS[0], (long) scheduler.delays.get(0));
        assertEquals(PremiumEngine.RETRY_DELAYS_MS[1], (long) scheduler.delays.get(1));
    }

    @Test
    public void serverAcknowledgedPurchaseIsNotAcknowledgedAgain() {
        verifier.serverAcks = true;
        startEngine();
        buy(purchase("t1", PurchaseRecord.State.PURCHASED, false));
        assertTrue(engine.isPremiumActive());
        assertEquals(0, gateway.ackCalls);
        assertTrue(storage.acked);
    }

    // ------------------------------------------------------------------ recovery

    @Test
    public void interruptedCheckoutIsRecoveredOnNextStart() {
        // App was killed after payment, before verification: Play still reports the purchase.
        gateway.owned.add(purchase("t1", PurchaseRecord.State.PURCHASED, false));
        startEngine();
        assertTrue(engine.isPremiumActive());
        assertEquals(1, gateway.ackCalls);
        assertTrue(events.contains(PremiumEvent.RESTORED));
    }

    @Test
    public void reinstallRestoresFromPlay() {
        gateway.owned.add(purchase("t1", PurchaseRecord.State.PURCHASED, true));
        startEngine(); // empty storage, like a fresh install
        assertTrue(engine.isPremiumActive());
        assertEquals(0, gateway.ackCalls);
    }

    @Test
    public void unacknowledgedPurchaseIsAcknowledgedOnNextStart() {
        storage.grant("t1", now - 1000); // verified, but ack never landed
        gateway.owned.add(purchase("t1", PurchaseRecord.State.PURCHASED, false));
        startEngine();
        assertEquals(0, verifier.calls); // fresh verification, no need to re-verify
        assertEquals(1, gateway.ackCalls);
        assertTrue(storage.acked);
    }

    @Test
    public void restoreWithNothingOwnedSaysSo() {
        startEngine();
        engine.restore();
        assertTrue(events.contains(PremiumEvent.NOTHING_TO_RESTORE));
        assertFalse(engine.isPremiumActive());
    }

    // ------------------------------------------------------------------ refunds and offline

    @Test
    public void refundDetectedByPlayRevokesAccess() {
        storage.grant("t1", now - 1000);
        storage.acked = true;
        startEngine(); // Play no longer lists the purchase
        assertFalse(engine.isPremiumActive());
        assertFalse(storage.isOwned());
        assertTrue(events.contains(PremiumEvent.REVOKED));
    }

    @Test
    public void refundDetectedByServerRevokesAccess() {
        storage.grant("t1", now - TimeUnit.HOURS.toMillis(30)); // stale: re-verified on start
        gateway.owned.add(purchase("t1", PurchaseRecord.State.PURCHASED, true));
        verifier.next(VerificationResult.Status.REVOKED);
        startEngine();
        assertFalse(engine.isPremiumActive());
        assertTrue(events.contains(PremiumEvent.REVOKED));
    }

    @Test
    public void offlineWithinGraceKeepsVerifiedAccess() {
        storage.grant("t1", now - TimeUnit.DAYS.toMillis(10));
        gateway.owned.add(purchase("t1", PurchaseRecord.State.PURCHASED, true));
        verifier.next(VerificationResult.Status.UNAVAILABLE);
        startEngine();
        assertTrue(engine.isPremiumActive());
        assertEquals(PremiumState.OWNED, last.state);
        assertFalse(events.contains(PremiumEvent.REVOKED));
    }

    @Test
    public void offlineBeyondGraceLocksUntilVerified() {
        storage.grant("t1", now - PremiumEngine.OFFLINE_GRACE_MS - 1);
        gateway.owned.add(purchase("t1", PurchaseRecord.State.PURCHASED, true));
        verifier.next(VerificationResult.Status.UNAVAILABLE);
        startEngine();
        assertFalse(engine.isPremiumActive());
        assertTrue(storage.isOwned()); // not revoked: unlocks again once verification succeeds
        scheduler.runAll();
        io.runAll();
        assertTrue(engine.isPremiumActive());
    }

    @Test
    public void playQueryFailureIsNotTreatedAsRefund() {
        storage.grant("t1", now - 1000);
        gateway.queryCode = BillingCodes.SERVICE_UNAVAILABLE;
        startEngine();
        assertTrue(engine.isPremiumActive());
        assertFalse(events.contains(PremiumEvent.REVOKED));
    }

    // ------------------------------------------------------------------ fakes

    static final class FakeGateway implements BillingGateway {
        PurchasesListener listener;
        final List<PurchaseRecord> owned = new ArrayList<>();
        final Deque<Integer> ackResults = new ArrayDeque<>();
        int ackCalls;
        int queryCode = BillingCodes.OK;

        void emit(int code, int sub, List<PurchaseRecord> purchases) {
            listener.onPurchasesUpdated(code, sub, purchases);
        }

        @Override public void setPurchasesListener(PurchasesListener l) { listener = l; }
        @Override public void connect(ResultCallback cb) { cb.onResult(BillingCodes.OK); }
        @Override public void queryProduct(String id, ProductCallback cb) {
            cb.onResult(BillingCodes.OK, new ProductInfo(id, "Pro themes", "$2.99"));
        }
        @Override public void queryOwnedPurchases(PurchasesCallback cb) {
            cb.onResult(queryCode, queryCode == BillingCodes.OK ? new ArrayList<>(owned) : new ArrayList<>());
        }
        @Override public void acknowledge(String token, ResultCallback cb) {
            ackCalls++;
            Integer r = ackResults.poll();
            cb.onResult(r == null ? BillingCodes.OK : r);
        }
        @Override public void disconnect() {}
        @Override public boolean isSimulated() { return false; }
    }

    static final class FakeVerifier implements PurchaseVerifier {
        final Deque<VerificationResult.Status> queue = new ArrayDeque<>();
        boolean serverAcks;
        int calls;

        void next(VerificationResult.Status s) { queue.add(s); }

        @Override public VerificationResult verify(PurchaseRecord p) {
            calls++;
            VerificationResult.Status s = queue.poll();
            if (s == null) s = VerificationResult.Status.VERIFIED;
            return new VerificationResult(s, serverAcks && s == VerificationResult.Status.VERIFIED, "");
        }
    }

    static final class MemoryStorage implements EntitlementStorage {
        boolean owned;
        String token = "";
        long verifiedAt;
        boolean acked;

        @Override public boolean isOwned() { return owned; }
        @Override public String purchaseToken() { return token; }
        @Override public long verifiedAtMillis() { return verifiedAt; }
        @Override public boolean isAcknowledged() { return acked; }
        @Override public void grant(String t, long at) {
            if (!t.equals(token)) acked = false;
            owned = true; token = t; verifiedAt = at;
        }
        @Override public void markAcknowledged(String t) { if (t.equals(token)) acked = true; }
        @Override public void clear() { owned = false; token = ""; verifiedAt = 0; acked = false; }
    }

    /** Holds background work until the test releases it, like a real worker thread. */
    static final class QueueExecutor implements Executor {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable r) { tasks.add(r); }
        void runAll() { while (!tasks.isEmpty()) tasks.poll().run(); }
    }

    final class ManualScheduler implements PremiumEngine.Scheduler {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        final List<Long> delays = new ArrayList<>();
        @Override public void schedule(Runnable task, long delay) { tasks.add(task); delays.add(delay); }
        void runAll() {
            int guard = 0;
            while (!tasks.isEmpty() && guard++ < 50) {
                tasks.poll().run();
                io.runAll();
            }
        }
    }
}
