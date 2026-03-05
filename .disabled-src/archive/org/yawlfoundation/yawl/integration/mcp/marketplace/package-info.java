/**
 * GCP Marketplace Model Context Protocol (MCP) tools for YAWL.
 *
 * <h2>Overview</h2>
 * This package implements 5 MCP tools for GCP Marketplace integration,
 * enabling autonomous agents to manage vendor registration, product listings,
 * orders, fulfillment tracking, and payments.
 *
 * <h2>Tools</h2>
 * <ul>
 *   <li><b>/marketplace/vendors/register:</b> Register vendor (idempotent)</li>
 *   <li><b>/marketplace/products/list:</b> Query product catalog with filters</li>
 *   <li><b>/marketplace/orders/create:</b> Create order (idempotent)</li>
 *   <li><b>/marketplace/fulfillment/track:</b> Track shipment status</li>
 *   <li><b>/marketplace/payments/process:</b> Process payment (authorize/capture/refund,
 *       idempotent)</li>
 * </ul>
 *
 * <h2>Idempotency Guarantees</h2>
 * Tools marked [idempotent] use:
 * <ul>
 *   <li>Request deduplication via idempotency cache</li>
 *   <li>Base64-encoded hash of key fields as cache key</li>
 *   <li>Consistent response for identical inputs</li>
 * </ul>
 *
 * <h2>Message Ordering</h2>
 * All tool responses include:
 * <ul>
 *   <li><b>sequence_number:</b> Monotonic per stream (order guarantee)</li>
 *   <li><b>event:</b> Embedded marketplace event (JSON)</li>
 *   <li><b>timestamp:</b> UTC ISO-8601</li>
 * </ul>
 *
 * @since 6.0.0
 * @author YAWL Marketplace Integration
 */
package org.yawlfoundation.yawl.integration.mcp.marketplace;
