/**
 * GCP Marketplace Agent-to-Agent (A2A) message handlers for YAWL.
 *
 * <h2>Overview</h2>
 * This package implements A2A message handlers for marketplace events,
 * enabling autonomous agents (vendor, fulfillment, payment) to communicate
 * with the YAWL engine and trigger workflow case processing.
 *
 * <h2>Message Flow</h2>
 * <pre>
 * Vendor Agent
 *   ↓ sends OrderCreatedEvent (A2A message)
 * MarketplaceA2AHandler.handleMarketplaceEvent()
 *   ↓ validates sequence + idempotency
 * routeEvent(EventEnvelope)
 *   ↓ routes by event type
 * handleOrderCreatedEvent()
 *   ↓ launches YAWL workflow case
 * YAWL Engine (ProcessOrder case)
 *   ↓ processes order
 * InterfaceB updateCase() call
 *   ↓ updates case data
 * Vendor Agent receives acknowledgment (seq number, status)
 * </pre>
 *
 * <h2>Event Routing</h2>
 * Each event type routes to specific workflow case:
 * <ul>
 *   <li>OrderCreatedEvent → ProcessOrder</li>
 *   <li>OrderConfirmedEvent → updates ProcessOrder case data</li>
 *   <li>OrderShippedEvent → updates ProcessOrder case data</li>
 *   <li>VendorOnboardedEvent → OnboardVendor</li>
 *   <li>PaymentFailedEvent → HandlePaymentFailure</li>
 * </ul>
 *
 * <h2>Ordering & Idempotency Guarantees</h2>
 * <ul>
 *   <li><b>Sequence validation:</b> Events must arrive in order (seq N, N+1, N+2...)</li>
 *   <li><b>Idempotency:</b> Duplicate events (same idempotencyKey) return cached response</li>
 *   <li><b>Deduplication:</b> processedEvents map tracks (idempotencyKey → timestamp)</li>
 *   <li><b>Out-of-order detection:</b> Logs warning if sequence gap detected</li>
 * </ul>
 *
 * @since 6.0.0
 * @author YAWL Marketplace Integration
 */
package org.yawlfoundation.yawl.integration.a2a.marketplace;
