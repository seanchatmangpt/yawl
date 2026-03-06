/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 */

/**
 * Custom task types for GCP Marketplace domain.
 *
 * <p>This package extends YAWL's core task types with marketplace-specific semantics:
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.MarketplaceVendorTask}
 *       — Atomic task for vendor operations (profile management, inventory, pricing)</li>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.CustomerPurchaseTask}
 *       — Atomic task for customer purchase journey (search, selection, payment)</li>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.FulfillmentTask}
 *       — Atomic task for fulfillment center operations (pick, pack, ship, delivery)</li>
 * </ul>
 *
 * <h2>Task Completion Semantics</h2>
 * <p>Each custom task type enforces domain-specific completion invariants:
 * <ul>
 *   <li>MarketplaceVendorTask: Vendor credentials verified ∧ account in good standing</li>
 *   <li>CustomerPurchaseTask: Order initialized ∧ (payment verified ∨ cancelled)</li>
 *   <li>FulfillmentTask: Inventory allocated ∧ fulfillment status terminal (DELIVERED | FAILED)</li>
 * </ul>
 * </p>
 *
 * <h2>Multi-Instance Expansion</h2>
 * <p>CustomerPurchaseTask and FulfillmentTask support dynamic multi-instance expansion:
 * <ul>
 *   <li>CustomerPurchaseTask: One instance per product in order</li>
 *   <li>FulfillmentTask: One instance per order line item</li>
 * </ul>
 * Expansion occurs at task start phase using input mapping queries over order data.</p>
 *
 * <h2>Cancellation Handling</h2>
 * <p>All task types support cancellation with side effects:
 * <ul>
 *   <li>MarketplaceVendorTask: Reset verification state</li>
 *   <li>CustomerPurchaseTask: Reverse inventory reservations, trigger refund</li>
 *   <li>FulfillmentTask: Restore reserved inventory, cancel shipping</li>
 * </ul>
 * Cancellation signals propagate via internal conditions to joined tasks.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.elements.YAtomicTask
 * @see org.yawlfoundation.yawl.elements.marketplace.data
 * @see org.yawlfoundation.yawl.elements.marketplace.conditions
 */
package org.yawlfoundation.yawl.elements.marketplace;
