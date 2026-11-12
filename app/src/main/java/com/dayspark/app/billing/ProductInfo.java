package com.dayspark.app.billing;

/** Store listing details for the one-time product. {@code formattedPrice} is localized by Google Play. */
public final class ProductInfo {
    public final String productId;
    public final String title;
    public final String formattedPrice;

    public ProductInfo(String productId, String title, String formattedPrice) {
        this.productId = productId;
        this.title = title;
        this.formattedPrice = formattedPrice;
    }
}
