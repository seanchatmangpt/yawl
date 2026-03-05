package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.math.BigDecimal;

/**
 * A single line item in an order.
 *
 * @param sku product SKU
 * @param quantity units ordered
 * @param unitPrice price per unit
 * @param subtotal quantity × unitPrice
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OrderLineItem(
    String sku,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {

    /**
     * Construct a line item with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public OrderLineItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
        if (subtotal == null || subtotal.signum() <= 0) {
            throw new IllegalArgumentException("subtotal must be positive");
        }
    }

    /**
     * Builder for OrderLineItem.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for OrderLineItem.
     */
    public static class Builder {
        private String sku;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder subtotal(BigDecimal subtotal) {
            this.subtotal = subtotal;
            return this;
        }

        public OrderLineItem build() {
            return new OrderLineItem(sku, quantity, unitPrice, subtotal);
        }
    }
}
