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
 * Immutable product listing data element for GCP Marketplace.
 *
 * <p>Represents product catalog entry with pricing, inventory, and availability
 * information. Integrates with vendor inventory management and customer search workflows.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public record ProductListing(
        String productId,
        String vendorId,
        String productName,
        String productDescription,
        String category,
        String subcategory,
        BigDecimal listPrice,
        BigDecimal discountedPrice,
        String currency,
        int currentInventory,
        int minInventoryThreshold,
        int reservedInventory,
        boolean active,
        double productRating,
        int reviewCount,
        long createdTime,
        long lastUpdatedTime,
        String fulfillmentMethod,
        String sku,
        Map<String, String> metadata
) {

    /**
     * Fulfillment method constants
     */
    public static final String FULFILLMENT_FBA = "FBA";  // Fulfilled by Amazon equivalent
    public static final String FULFILLMENT_MFN = "MFN";  // Merchant Fulfilled Network

    /**
     * Compact constructor for validation.
     */
    public ProductListing {
        Objects.requireNonNull(productId, "Product ID cannot be null");
        Objects.requireNonNull(vendorId, "Vendor ID cannot be null");
        Objects.requireNonNull(productName, "Product name cannot be null");
        Objects.requireNonNull(listPrice, "List price cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        if (listPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("List price cannot be negative");
        }
        if (currentInventory < 0) {
            throw new IllegalArgumentException("Inventory cannot be negative");
        }
        metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * Creates a ProductListing builder for fluent construction.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ProductListing.
     */
    public static class Builder {
        private String productId;
        private String vendorId;
        private String productName;
        private String productDescription = "";
        private String category;
        private String subcategory;
        private BigDecimal listPrice;
        private BigDecimal discountedPrice;
        private String currency = "USD";
        private int currentInventory = 0;
        private int minInventoryThreshold = 10;
        private int reservedInventory = 0;
        private boolean active = true;
        private double productRating = 0.0;
        private int reviewCount = 0;
        private long createdTime = System.currentTimeMillis();
        private long lastUpdatedTime = System.currentTimeMillis();
        private String fulfillmentMethod = FULFILLMENT_MFN;
        private String sku;
        private Map<String, String> metadata = new HashMap<>();

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder vendorId(String vendorId) {
            this.vendorId = vendorId;
            return this;
        }

        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public Builder productDescription(String description) {
            this.productDescription = description;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder subcategory(String subcategory) {
            this.subcategory = subcategory;
            return this;
        }

        public Builder listPrice(BigDecimal price) {
            this.listPrice = price;
            return this;
        }

        public Builder discountedPrice(BigDecimal price) {
            this.discountedPrice = price;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder currentInventory(int inventory) {
            this.currentInventory = inventory;
            return this;
        }

        public Builder minInventoryThreshold(int threshold) {
            this.minInventoryThreshold = threshold;
            return this;
        }

        public Builder reservedInventory(int reserved) {
            this.reservedInventory = reserved;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder productRating(double rating) {
            this.productRating = Math.min(5.0, Math.max(0.0, rating));
            return this;
        }

        public Builder reviewCount(int count) {
            this.reviewCount = count;
            return this;
        }

        public Builder fulfillmentMethod(String method) {
            this.fulfillmentMethod = method;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder metadataEntry(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public ProductListing build() {
            return new ProductListing(
                    productId, vendorId, productName, productDescription,
                    category, subcategory, listPrice, discountedPrice,
                    currency, currentInventory, minInventoryThreshold,
                    reservedInventory, active, productRating, reviewCount,
                    createdTime, lastUpdatedTime, fulfillmentMethod, sku, metadata
            );
        }
    }

    /**
     * Calculates available inventory (current - reserved).
     *
     * @return available quantity
     */
    public int getAvailableInventory() {
        return Math.max(0, currentInventory - reservedInventory);
    }

    /**
     * Checks if product is in stock.
     *
     * @return true if available inventory > 0
     */
    public boolean isInStock() {
        return getAvailableInventory() > 0;
    }

    /**
     * Checks if inventory is below minimum threshold.
     *
     * @return true if current inventory < minimum threshold
     */
    public boolean isLowInventory() {
        return currentInventory < minInventoryThreshold;
    }

    /**
     * Gets the effective sale price (discounted or list price).
     *
     * @return the price customer will pay
     */
    public BigDecimal getEffectivePrice() {
        return discountedPrice != null && discountedPrice.compareTo(BigDecimal.ZERO) > 0
                ? discountedPrice
                : listPrice;
    }

    /**
     * Calculates discount percentage if applicable.
     *
     * @return discount percentage, or 0 if no discount
     */
    public BigDecimal getDiscountPercentage() {
        if (discountedPrice == null || discountedPrice.compareTo(listPrice) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = listPrice.subtract(discountedPrice);
        return discount.divide(listPrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100));
    }

    /**
     * Checks if product can be ordered.
     *
     * @return true if active and in stock
     */
    public boolean isOrderable() {
        return active && isInStock();
    }
}
