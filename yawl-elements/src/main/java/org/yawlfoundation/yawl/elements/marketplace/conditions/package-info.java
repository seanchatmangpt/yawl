/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 */

/**
 * Condition guards for GCP Marketplace workflow patterns.
 *
 * <p>This package provides predicates for controlling workflow routing and task
 * activation at key decision points:
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.conditions.MarketplaceConditionGuards}
 *       — Static guards for vendor, product, order, and payment validation</li>
 * </ul>
 *
 * <h2>Guard Categories</h2>
 * <p><b>Vendor Guards:</b>
 * <ul>
 *   <li>verifyVendorCredentials() — Credentials verified and not stale</li>
 *   <li>isVendorCompliant() — Compliance status approved</li>
 *   <li>verifyVendorCanSell() — Can accept and process orders</li>
 *   <li>isVendorAccountHealthy() — Account rating above threshold</li>
 * </ul>
 * </p>
 *
 * <p><b>Product Guards:</b>
 * <ul>
 *   <li>isProductAvailableForPurchase() — Active and in stock</li>
 *   <li>checkProductInventory(qty) — Sufficient inventory exists</li>
 *   <li>isProductLowInventory() — Below minimum threshold</li>
 *   <li>isProductPricingValid() — Price > 0</li>
 *   <li>hasValidDiscount() — Discounted price valid</li>
 * </ul>
 * </p>
 *
 * <p><b>Order Guards:</b>
 * <ul>
 *   <li>isOrderReadyForPayment() — Order confirmed, items valid</li>
 *   <li>isOrderReadyForFulfillment() — Payment captured, fulfillment pending</li>
 *   <li>canCancelOrder() — Order is in cancellable state</li>
 *   <li>requiresMultiInstanceExpansion() — Order has multiple items</li>
 *   <li>qualifiesForExpedited() — Order meets expedited criteria</li>
 * </ul>
 * </p>
 *
 * <p><b>Payment Guards:</b>
 * <ul>
 *   <li>isPaymentAuthorized() — Authorization successful</li>
 *   <li>isPaymentCaptured() — Funds captured</li>
 *   <li>didPaymentAuthorizationFail() — Authorization failed/declined</li>
 *   <li>canRefundPayment() — Refund possible</li>
 *   <li>hasPaymentAuthorizationExpired() — Authorization stale (>7 days)</li>
 * </ul>
 * </p>
 *
 * <h2>Usage in XPath Predicates</h2>
 * <p>Guards can be called from task flow conditions using Java extensions:
 * <pre>
 *   &lt;flowsInto&gt;
 *     &lt;nextElementRef id="SendProduct" predicate="
 *       ext:verifyVendorCanSell(vendor) and
 *       ext:isProductAvailableForPurchase(product) and
 *       ext:isOrderReadyForPayment(order)
 *     " /&gt;
 *   &lt;/flowsInto&gt;
 * </pre>
 * </p>
 *
 * <h2>Usage in Java Task Logic</h2>
 * <p>Direct method calls from task implementations:
 * <pre>
 *   if (MarketplaceConditionGuards.validateOrderForFulfillment(vendor, order, payment)) {
 *       fulfillmentTask.initializeForOrder(order.orderId(), fulfillmentCenter.getId());
 *   } else if (MarketplaceConditionGuards.canCancelOrder(order)) {
 *       order.cancelOrder("Vendor verification failed");
 *   }
 * </pre>
 * </p>
 *
 * <h2>Composite Guards</h2>
 * <p>Higher-level guards combine multiple checks:
 * <ul>
 *   <li>validateOrderForFulfillment() — Vendor + Order + Payment all valid</li>
 *   <li>validateOrderPlacement() — Vendor capable + order has items</li>
 * </ul>
 * </p>
 *
 * <h2>Design Rationale</h2>
 * <p>Guards are implemented as pure functions (no side effects) to enable:
 * <ul>
 *   <li>Deterministic evaluation in predicates</li>
 *   <li>Testability without state mutation</li>
 *   <li>Safe parallel evaluation across flow branches</li>
 *   <li>Composability for complex conditions</li>
 * </ul>
 * </p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.elements.YFlow
 * @see org.yawlfoundation.yawl.elements.marketplace.data
 */
package org.yawlfoundation.yawl.elements.marketplace.conditions;
