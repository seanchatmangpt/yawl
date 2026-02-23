/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 */

/**
 * Immutable data elements for GCP Marketplace workflows.
 *
 * <p>This package provides strongly-typed data records for marketplace entities,
 * compatible with YAWL's variable typing system:
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.data.VendorProfile}
 *       — Vendor account information, credentials, compliance status</li>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.data.ProductListing}
 *       — Product catalog entry with inventory and pricing</li>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.data.Order}
 *       — Customer order with line items and fulfillment tracking</li>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.data.PaymentStatus}
 *       — Payment authorization, capture, and refund lifecycle</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <p>All data elements are implemented as Java records (sealed types) following
 * Java 25 modern patterns:
 * <ul>
 *   <li><b>Immutability</b> — No post-construction mutations (thread-safe)</li>
 *   <li><b>Value Equality</b> — Structural equality via auto-generated equals/hashCode</li>
 *   <li><b>Builder Pattern</b> — Fluent construction via static Builder classes</li>
 *   <li><b>Domain Validation</b> — Constructor contracts enforce invariants</li>
 *   <li><b>Factory Methods</b> — Helper methods for common state transitions</li>
 * </ul>
 * </p>
 *
 * <h2>Mapping to YAWL Variables</h2>
 * <p>Each data element maps to a workflow variable type:
 * <pre>
 *   &lt;variable&gt;
 *     &lt;name&gt;vendor&lt;/name&gt;
 *     &lt;type&gt;VendorProfile&lt;/type&gt;
 *     &lt;namespace&gt;org.yawlfoundation.yawl.elements.marketplace.data&lt;/namespace&gt;
 *   &lt;/variable&gt;
 * </pre>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <p><b>Creating a vendor:</b>
 * <pre>
 *   VendorProfile vendor = VendorProfile.builder()
 *       .vendorId("V-12345")
 *       .vendorName("Acme Corp")
 *       .businessLicense("BL-67890")
 *       .contactEmail("vendor@acme.com")
 *       .credentialsVerified(true)
 *       .marketplaceTier("PREMIUM")
 *       .build();
 * </pre>
 * </p>
 *
 * <p><b>Checking order readiness:</b>
 * <pre>
 *   if (order.getTotalQuantity() > 0 && order.getOrderTotal().signum() > 0) {
 *       // Order is ready for payment
 *   }
 * </pre>
 * </p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 * @see java.lang.Record
 * @see org.yawlfoundation.yawl.elements.data.YVariable
 */
package org.yawlfoundation.yawl.elements.marketplace.data;
