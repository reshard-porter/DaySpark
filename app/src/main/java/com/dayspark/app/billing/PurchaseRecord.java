package com.dayspark.app.billing;

import java.util.Collections;
import java.util.List;

/** Immutable copy of the purchase fields DaySpark needs, independent of the Play library types. */
public final class PurchaseRecord {

    public enum State { PURCHASED, PENDING, UNSPECIFIED }

    public final List<String> productIds;
    public final String purchaseToken;
    public final String orderId;
    public final String packageName;
    public final State state;
    public final boolean acknowledged;
    public final long purchaseTimeMillis;

    public PurchaseRecord(List<String> productIds, String purchaseToken, String orderId,
                          String packageName, State state, boolean acknowledged,
                          long purchaseTimeMillis) {
        this.productIds = Collections.unmodifiableList(productIds);
        this.purchaseToken = purchaseToken;
        this.orderId = orderId == null ? "" : orderId;
        this.packageName = packageName == null ? "" : packageName;
        this.state = state;
        this.acknowledged = acknowledged;
        this.purchaseTimeMillis = purchaseTimeMillis;
    }

    public boolean isFor(String productId) {
        return productIds.contains(productId);
    }
}
