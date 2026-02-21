/**
 * Marketplace Event Domain Model
 *
 * Sealed hierarchy of marketplace events for GCP marketplace integration:
 * - OrderEvent: Order lifecycle (created, confirmed, shipped, delivered, cancelled)
 * - VendorEvent: Vendor lifecycle (onboarded, verified, suspended, offboarded)
 * - PaymentEvent: Payment transactions (authorized, captured, refunded, failed)
 *
 * <p><b>Core Properties</b>:
 * <ul>
 *   <li>Idempotency: (agent_id, idempotency_token) pairs uniquely identify events</li>
 *   <li>Message Ordering: Events from same agent processed strictly in order</li>
 *   <li>Immutable Records: All events are Java records (equals, hashCode, toString auto-generated)</li>
 *   <li>Sealed Hierarchy: Pattern matching on event types with exhaustive switch</li>
 * </ul>
 *
 * <p><b>Usage Example</b>:
 * <pre>
 * OrderEvent order = OrderEvent.builder()
 *     .agentId("vendor-42")
 *     .idempotencyToken("order-123:v1")
 *     .orderId("ORD-2026-001")
 *     .customerId("CUST-999")
 *     .status(OrderEvent.Status.PENDING)
 *     .items(List.of(
 *         OrderLineItem.builder()
 *             .sku("WIDGET-001")
 *             .quantity(5)
 *             .unitPrice(new BigDecimal("99.99"))
 *             .subtotal(new BigDecimal("499.95"))
 *             .build()
 *     ))
 *     .totalAmount(new BigDecimal("549.95"))
 *     .currency("USD")
 *     .build();
 *
 * // Process with idempotency
 * String dedupKey = order.deduplicationKey(); // "vendor-42:order-123:v1"
 * if (!dedupStore.exists(dedupKey)) {
 *     processOrder(order);
 *     dedupStore.record(dedupKey);
 * }
 *
 * // Pattern match on event type
 * switch (marketplaceEvent) {
 *     case OrderEvent e -> handleOrderEvent(e);
 *     case VendorEvent e -> handleVendorEvent(e);
 *     case PaymentEvent e -> handlePaymentEvent(e);
 * }
 * </pre>
 *
 * <p><b>JSON Schema</b>:
 * Events can be serialized to JSON using Jackson or similar JSON mappers.
 * The sealed hierarchy structure translates to a JSON object with a type discriminator:
 * <pre>
 * {
 *   "eventType": "OrderCreated",
 *   "agentId": "vendor-42",
 *   "idempotencyToken": "order-123:v1",
 *   "timestamp": "2026-02-21T10:30:45Z",
 *   "version": "1.0",
 *   "metadata": {
 *     "traceId": "trace-abc123",
 *     "correlationId": "corr-xyz789",
 *     "userId": "user-42",
 *     "tags": {}
 *   },
 *   "orderId": "ORD-2026-001",
 *   "customerId": "CUST-999",
 *   ...
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 2026-02-21
 */
package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.MarketplaceEvent;
