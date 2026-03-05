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

package org.yawlfoundation.yawl.elements.marketplace.conditions;

import org.yawlfoundation.yawl.elements.marketplace.data.VendorProfile;
import org.yawlfoundation.yawl.elements.marketplace.data.ProductListing;
import org.yawlfoundation.yawl.elements.marketplace.data.Order;
import org.yawlfoundation.yawl.elements.marketplace.data.PaymentStatus;

/**
 * Condition guards for GCP Marketplace workflow patterns.
 *
 * <p>Provides static methods for evaluating conditions at task boundaries
 * to control workflow routing and task activation/completion.</p>
 *
 * <h2>Guard Types</h2>
 * <ul>
 *   <li><b>Vendor Guards</b> - Verify vendor status, credentials, compliance</li>
 *   <li><b>Product Guards</b> - Check product availability, inventory, pricing</li>
 *   <li><b>Order Guards</b> - Validate order state, items, fulfillment readiness</li>
 *   <li><b>Payment Guards</b> - Confirm payment authorization, capture, refund</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <p>Guards are typically used in XPath predicates or Java routing logic:
 * <pre>
 *   if (MarketplaceConditionGuards.verifyVendorCanSell(vendor)) {
 *       // Route to fulfillment
 *   }
 * </pre>
 * </p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public final class MarketplaceConditionGuards {

    private MarketplaceConditionGuards() {
        // Utility class
    }

    // ========== VENDOR GUARDS ==========

    /**
     * Verifies vendor credentials are valid for marketplace operations.
     *
     * @param vendor the vendor profile
     * @return true if credentials verified and within validity period
     */
    public static boolean verifyVendorCredentials(VendorProfile vendor) {
        if (vendor == null || !vendor.credentialsVerified()) {
            return false;
        }
        // Check if credential verification is not too old (30 days)
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        long ageSinceVerification = System.currentTimeMillis() - vendor.credentialVerificationTime();
        return ageSinceVerification < thirtyDaysMs;
    }

    /**
     * Checks if vendor is compliant with marketplace policies.
     *
     * @param vendor the vendor profile
     * @return true if vendor is approved and account is active
     */
    public static boolean isVendorCompliant(VendorProfile vendor) {
        return vendor != null && vendor.isCompliant() && vendor.accountActive();
    }

    /**
     * Checks if vendor can accept and sell products.
     *
     * @param vendor the vendor profile
     * @return true if vendor meets all requirements for selling
     */
    public static boolean verifyVendorCanSell(VendorProfile vendor) {
        return vendor != null && vendor.canAcceptOrders();
    }

    /**
     * Checks if vendor's account is in good standing (no violations).
     *
     * @param vendor the vendor profile
     * @return true if account is active and compliant
     */
    public static boolean isVendorAccountHealthy(VendorProfile vendor) {
        if (vendor == null || !vendor.accountActive()) {
            return false;
        }
        // Minimum rating threshold
        return vendor.accountRating() >= 3.0;
    }

    /**
     * Checks if vendor is eligible for premium tier benefits.
     *
     * @param vendor the vendor profile
     * @return true if vendor meets premium tier criteria
     */
    public static boolean isVendorPremiumEligible(VendorProfile vendor) {
        if (vendor == null) {
            return false;
        }
        return vendor.totalTransactions() >= 100 &&
                vendor.accountRating() >= 4.5 &&
                VendorProfile.TIER_PREMIUM.equals(vendor.marketplaceTier());
    }

    // ========== PRODUCT GUARDS ==========

    /**
     * Checks if product is available for customer purchase.
     *
     * @param product the product listing
     * @return true if product is active and in stock
     */
    public static boolean isProductAvailableForPurchase(ProductListing product) {
        return product != null && product.isOrderable();
    }

    /**
     * Checks if product inventory is sufficient for order quantity.
     *
     * @param product the product listing
     * @param requestedQuantity the quantity requested
     * @return true if available inventory >= requested quantity
     */
    public static boolean checkProductInventory(ProductListing product, int requestedQuantity) {
        return product != null && product.getAvailableInventory() >= requestedQuantity;
    }

    /**
     * Checks if product inventory is critically low.
     *
     * @param product the product listing
     * @return true if inventory below minimum threshold
     */
    public static boolean isProductLowInventory(ProductListing product) {
        return product != null && product.isLowInventory();
    }

    /**
     * Checks if product is active in marketplace.
     *
     * @param product the product listing
     * @return true if product active and not delisted
     */
    public static boolean isProductActive(ProductListing product) {
        return product != null && product.active();
    }

    /**
     * Checks if product pricing is valid for sale.
     *
     * @param product the product listing
     * @return true if list price > 0
     */
    public static boolean isProductPricingValid(ProductListing product) {
        return product != null && product.listPrice().signum() > 0;
    }

    /**
     * Checks if product qualifies for discount pricing.
     *
     * @param product the product listing
     * @return true if discounted price is set and lower than list price
     */
    public static boolean hasValidDiscount(ProductListing product) {
        if (product == null) {
            return false;
        }
        return product.discountedPrice() != null &&
                product.discountedPrice().signum() > 0 &&
                product.discountedPrice().compareTo(product.listPrice()) < 0;
    }

    // ========== ORDER GUARDS ==========

    /**
     * Checks if order is ready for payment processing.
     *
     * @param order the order
     * @return true if order confirmed and items valid
     */
    public static boolean isOrderReadyForPayment(Order order) {
        return order != null &&
                Order.STATUS_CONFIRMED.equals(order.orderStatus()) &&
                Order.PAYMENT_PENDING.equals(order.paymentStatus()) &&
                order.getItemCount() > 0;
    }

    /**
     * Checks if order can proceed to fulfillment.
     *
     * @param order the order
     * @return true if payment captured and fulfillment pending
     */
    public static boolean isOrderReadyForFulfillment(Order order) {
        return order != null &&
                Order.STATUS_PROCESSING.equals(order.orderStatus()) &&
                Order.PAYMENT_CAPTURED.equals(order.paymentStatus()) &&
                Order.FULFILLMENT_PENDING.equals(order.fulfillmentStatus());
    }

    /**
     * Checks if order can be cancelled.
     *
     * @param order the order
     * @return true if order is cancellable
     */
    public static boolean canCancelOrder(Order order) {
        return order != null && order.canBeCancelled();
    }

    /**
     * Checks if order requires multi-instance expansion.
     *
     * @param order the order
     * @return true if order has multiple distinct items (>1)
     */
    public static boolean requiresMultiInstanceExpansion(Order order) {
        return order != null && order.getItemCount() > 1;
    }

    /**
     * Checks if order is complete.
     *
     * @param order the order
     * @return true if order is in final state
     */
    public static boolean isOrderComplete(Order order) {
        return order != null && order.isComplete();
    }

    /**
     * Checks if order qualifies for expedited processing.
     *
     * @param order the order
     * @return true if order < 10 items and under $500
     */
    public static boolean qualifiesForExpedited(Order order) {
        return order != null &&
                order.getItemCount() <= 10 &&
                order.orderTotal().signum() > 0 &&
                order.orderTotal().compareTo(java.math.BigDecimal.valueOf(500)) < 0;
    }

    // ========== PAYMENT GUARDS ==========

    /**
     * Checks if payment is authorized and ready for capture.
     *
     * @param payment the payment status
     * @return true if authorization succeeded
     */
    public static boolean isPaymentAuthorized(PaymentStatus payment) {
        return payment != null && payment.isAuthorized();
    }

    /**
     * Checks if payment has been captured.
     *
     * @param payment the payment status
     * @return true if funds captured
     */
    public static boolean isPaymentCaptured(PaymentStatus payment) {
        return payment != null && payment.isCaptured();
    }

    /**
     * Checks if payment authorization failed.
     *
     * @param payment the payment status
     * @return true if authorization failed or declined
     */
    public static boolean didPaymentAuthorizationFail(PaymentStatus payment) {
        return payment != null && (
                PaymentStatus.AUTH_FAILED.equals(payment.authorizationStatus()) ||
                PaymentStatus.AUTH_DECLINED.equals(payment.authorizationStatus())
        );
    }

    /**
     * Checks if payment can be refunded.
     *
     * @param payment the payment status
     * @return true if payment can accept refund
     */
    public static boolean canRefundPayment(PaymentStatus payment) {
        return payment != null && payment.canBeRefunded();
    }

    /**
     * Checks if payment has been fully refunded.
     *
     * @param payment the payment status
     * @return true if refund status is completed or partial >= captured
     */
    public static boolean isPaymentFullyRefunded(PaymentStatus payment) {
        if (payment == null) {
            return false;
        }
        return PaymentStatus.REFUND_COMPLETED.equals(payment.refundStatus()) ||
                (PaymentStatus.REFUND_PARTIAL.equals(payment.refundStatus()) &&
                 payment.getRemainingBalance().signum() <= 0);
    }

    /**
     * Checks if payment authorization has expired.
     *
     * @param payment the payment status
     * @return true if authorization is stale (>7 days without capture)
     */
    public static boolean hasPaymentAuthorizationExpired(PaymentStatus payment) {
        if (payment == null || !payment.isAuthorized()) {
            return false;
        }
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;
        return !payment.isCaptured() &&
                (System.currentTimeMillis() - payment.authorizationTime()) > sevenDaysMs;
    }

    /**
     * Checks if payment is in a finalized state.
     *
     * @param payment the payment status
     * @return true if payment lifecycle is complete
     */
    public static boolean isPaymentFinalized(PaymentStatus payment) {
        return payment != null && payment.isFinalized();
    }

    // ========== COMPOSITE GUARDS ==========

    /**
     * Performs end-to-end validation for order to proceed to fulfillment.
     *
     * @param vendor the vendor
     * @param order the order
     * @param payment the payment
     * @return true if all preconditions met
     */
    public static boolean validateOrderForFulfillment(VendorProfile vendor, Order order, PaymentStatus payment) {
        return verifyVendorCanSell(vendor) &&
                isOrderReadyForFulfillment(order) &&
                isPaymentCaptured(payment);
    }

    /**
     * Validates order can be placed with vendor.
     *
     * @param vendor the vendor
     * @param order the order
     * @return true if vendor is capable and order is valid
     */
    public static boolean validateOrderPlacement(VendorProfile vendor, Order order) {
        return verifyVendorCanSell(vendor) &&
                order != null &&
                order.getItemCount() > 0 &&
                order.orderTotal().signum() > 0;
    }
}
