/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.elements.marketplace.data;

import java.math.BigDecimal;
import java.util.*;

/**
 * Immutable order data element for GCP Marketplace.
 *
 * <p>Represents a customer order with line items, pricing, and fulfillment tracking.
 * Integrates with multi-instance task expansion for processing products in parallel.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public record Order(
        String orderId,
        String customerId,
        String vendorId,
        List<OrderLineItem> lineItems,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal shippingCost,
        BigDecimal orderTotal,
        String orderStatus,
        String paymentStatus,
        String fulfillmentStatus,
        long createdTime,
        long lastUpdatedTime,
        String shippingAddress,
        String billingAddress,
        String notes,
        Map<String, String> attributes
) {

    /**
     * Order status constants
     */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    /**
     * Payment status constants
     */
    public static final String PAYMENT_PENDING = "PENDING";
    public static final String PAYMENT_AUTHORIZED = "AUTHORIZED";
    public static final String PAYMENT_CAPTURED = "CAPTURED";
    public static final String PAYMENT_FAILED = "FAILED";
    public static final String PAYMENT_REFUNDED = "REFUNDED";

    /**
     * Fulfillment status constants
     */
    public static final String FULFILLMENT_PENDING = "PENDING";
    public static final String FULFILLMENT_ALLOCATED = "ALLOCATED";
    public static final String FULFILLMENT_SHIPPED = "SHIPPED";
    public static final String FULFILLMENT_DELIVERED = "DELIVERED";
    public static final String FULFILLMENT_FAILED = "FAILED";

    /**
     * Compact constructor for validation.
     */
    public Order {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        Objects.requireNonNull(customerId, "Customer ID cannot be null");
        Objects.requireNonNull(vendorId, "Vendor ID cannot be null");
        Objects.requireNonNull(lineItems, "Line items cannot be null");
        Objects.requireNonNull(orderTotal, "Order total cannot be null");
        lineItems = new ArrayList<>(lineItems);
        attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }

    /**
     * Creates an Order builder for fluent construction.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for Order.
     */
    public static class Builder {
        private String orderId;
        private String customerId;
        private String vendorId;
        private List<OrderLineItem> lineItems = new ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO;
        private BigDecimal tax = BigDecimal.ZERO;
        private BigDecimal shippingCost = BigDecimal.ZERO;
        private BigDecimal orderTotal = BigDecimal.ZERO;
        private String orderStatus = STATUS_PENDING;
        private String paymentStatus = PAYMENT_PENDING;
        private String fulfillmentStatus = FULFILLMENT_PENDING;
        private long createdTime = System.currentTimeMillis();
        private long lastUpdatedTime = System.currentTimeMillis();
        private String shippingAddress;
        private String billingAddress;
        private String notes = "";
        private Map<String, String> attributes = new HashMap<>();

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder vendorId(String vendorId) {
            this.vendorId = vendorId;
            return this;
        }

        public Builder addLineItem(OrderLineItem item) {
            this.lineItems.add(item);
            return this;
        }

        public Builder lineItems(List<OrderLineItem> items) {
            this.lineItems = new ArrayList<>(items);
            return this;
        }

        public Builder subtotal(BigDecimal subtotal) {
            this.subtotal = subtotal;
            return this;
        }

        public Builder tax(BigDecimal tax) {
            this.tax = tax;
            return this;
        }

        public Builder shippingCost(BigDecimal cost) {
            this.shippingCost = cost;
            return this;
        }

        public Builder orderTotal(BigDecimal total) {
            this.orderTotal = total;
            return this;
        }

        public Builder orderStatus(String status) {
            this.orderStatus = status;
            return this;
        }

        public Builder paymentStatus(String status) {
            this.paymentStatus = status;
            return this;
        }

        public Builder fulfillmentStatus(String status) {
            this.fulfillmentStatus = status;
            return this;
        }

        public Builder shippingAddress(String address) {
            this.shippingAddress = address;
            return this;
        }

        public Builder billingAddress(String address) {
            this.billingAddress = address;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public Order build() {
            return new Order(
                    orderId, customerId, vendorId, lineItems,
                    subtotal, tax, shippingCost, orderTotal,
                    orderStatus, paymentStatus, fulfillmentStatus,
                    createdTime, lastUpdatedTime,
                    shippingAddress, billingAddress, notes, attributes
            );
        }
    }

    /**
     * Gets the number of distinct items in order.
     *
     * @return line item count
     */
    public int getItemCount() {
        return lineItems.size();
    }

    /**
     * Gets total quantity across all line items.
     *
     * @return sum of all quantities
     */
    public int getTotalQuantity() {
        return lineItems.stream()
                .mapToInt(OrderLineItem::quantity)
                .sum();
    }

    /**
     * Checks if order can be paid.
     *
     * @return true if order is confirmed and payment pending
     */
    public boolean canBePaid() {
        return STATUS_CONFIRMED.equals(orderStatus) && PAYMENT_PENDING.equals(paymentStatus);
    }

    /**
     * Checks if order can be cancelled.
     *
     * @return true if order is not yet shipped
     */
    public boolean canBeCancelled() {
        return !STATUS_SHIPPED.equals(orderStatus) &&
                !STATUS_DELIVERED.equals(orderStatus) &&
                !STATUS_CANCELLED.equals(orderStatus);
    }

    /**
     * Checks if order is complete.
     *
     * @return true if delivered or cancelled/refunded
     */
    public boolean isComplete() {
        return STATUS_DELIVERED.equals(orderStatus) ||
                STATUS_CANCELLED.equals(orderStatus) ||
                STATUS_REFUNDED.equals(orderStatus);
    }

    /**
     * Immutable line item record.
     */
    public record OrderLineItem(
            String lineItemId,
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
        public OrderLineItem {
            Objects.requireNonNull(lineItemId, "Line item ID cannot be null");
            Objects.requireNonNull(productId, "Product ID cannot be null");
            Objects.requireNonNull(quantity, "Quantity cannot be null");
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
        }
    }
}
