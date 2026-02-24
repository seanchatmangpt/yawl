# GCP Marketplace Integration Guide (YAWL v6.0.0)

## Overview

This guide describes the GCP Marketplace integration for YAWL, enabling:

1. **Autonomous agents** to send marketplace events (orders, payments, shipments) to YAWL
2. **MCP tools** for marketplace management (vendor registration, product listing, order creation)
3. **A2A message handlers** for event processing and workflow orchestration
4. **Message ordering and idempotency guarantees** across all marketplace operations

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    GCP Marketplace Agents                        │
│  (Vendor Agent, Fulfillment Agent, Payment Processor Agent)      │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         ↓                               ↓
    MCP Tools                    A2A Message Handler
    (/marketplace/*)            (MarketplaceA2AHandler)
         │                               │
         ├─ /vendors/register          ├─ handleOrderCreatedEvent
         ├─ /products/list             ├─ handleVendorOnboardedEvent
         ├─ /orders/create             ├─ handlePaymentFailedEvent
         ├─ /fulfillment/track         └─ ... (12 total)
         └─ /payments/process
         │                              │
         └──────────────┬───────────────┘
                        ↓
         ┌──────────────────────────────┐
         │     YAWL Engine              │
         │  (Workflow Case Execution)   │
         │                              │
         │  - ProcessOrder case         │
         │  - OnboardVendor case        │
         │  - HandlePaymentFailure case │
         │  - ... (others)              │
         └──────────────────────────────┘
```

## Event Schema

### Message Ordering Guarantee

All marketplace events include:

```json
{
  "event_id": "evt-12345",
  "event_type": "OrderCreatedEvent",
  "event": { /* actual event data */ },
  "sequence_number": 42,
  "idempotency_key": "base64-encoded-hash",
  "timestamp_utc": "2026-02-21T14:30:00Z",
  "source_agent": "vendor-agent-001",
  "source_region": "us-east1"
}
```

**Guarantees:**
- `sequence_number`: Monotonically increasing per agent + region
- `idempotency_key`: Unique identifier for deduplication
- `timestamp_utc`: Causality tracking (UTC ISO-8601)

### Order Events

#### OrderCreatedEvent
Triggered when customer purchases a product. Idempotency key: `orderId`.

```json
{
  "order_id": "order-abc123",
  "vendor_id": "vendor-001",
  "product_id": "prod-xyz789",
  "quantity": 5,
  "unit_price_cents": 99900,
  "total_price_cents": 499500,
  "customer_id": "cust-456",
  "region": "us-east1",
  "timestamp_utc": "2026-02-21T14:30:00Z",
  "sequence_number": 42,
  "metadata": { "source": "gcp_marketplace" }
}
```

**Workflow:** Launches `ProcessOrder` case with order details.

#### OrderConfirmedEvent
Triggered when payment is authorized. Idempotency key: `(orderId, confirmationId)` pair.

```json
{
  "order_id": "order-abc123",
  "confirmation_id": "conf-789",
  "payment_method": "credit_card",
  "timestamp_utc": "2026-02-21T14:35:00Z",
  "sequence_number": 43,
  "metadata": {}
}
```

**Workflow:** Updates `ProcessOrder` case data with confirmation ID.

#### OrderShippedEvent
Triggered when order is shipped. Idempotency key: `(orderId, shipmentId)` pair.

```json
{
  "order_id": "order-abc123",
  "shipment_id": "ship-456",
  "carrier": "FedEx",
  "tracking_number": "1Z999AA10123456784",
  "estimated_delivery_days": 5,
  "shipped_timestamp_utc": "2026-02-21T15:00:00Z",
  "sequence_number": 44,
  "metadata": {}
}
```

**Workflow:** Updates `ProcessOrder` case with shipment tracking info.

#### OrderDeliveredEvent
Triggered when order is delivered. Idempotency key: `(orderId, shipmentId, deliveryTimestamp)` triple.

```json
{
  "order_id": "order-abc123",
  "shipment_id": "ship-456",
  "delivered_timestamp_utc": "2026-02-21T20:00:00Z",
  "signature_required": false,
  "sequence_number": 45,
  "metadata": {}
}
```

**Workflow:** Marks order as delivered in `ProcessOrder` case.

#### OrderReturnedEvent
Triggered when customer initiates return. Idempotency key: `(orderId, returnId)` pair.

```json
{
  "order_id": "order-abc123",
  "return_id": "ret-789",
  "reason": "defective_product",
  "refund_cents": 499500,
  "returned_timestamp_utc": "2026-02-21T21:00:00Z",
  "sequence_number": 46,
  "metadata": {}
}
```

**Workflow:** Launches `ProcessReturn` case for return handling.

### Vendor Events

#### VendorOnboardedEvent
Triggered when vendor registers. Idempotency key: `vendorId`.

```json
{
  "vendor_id": "vendor-001",
  "company_name": "ACME Corp",
  "contact_email": "vendor@acme.com",
  "region": "us-east1",
  "tier": "premium",
  "onboarded_timestamp_utc": "2026-02-21T10:00:00Z",
  "sequence_number": 1,
  "metadata": {}
}
```

**Workflow:** Launches `OnboardVendor` case for vendor setup and verification.

#### VendorVerifiedEvent
Triggered when vendor passes compliance. Idempotency key: `(vendorId, verificationId)` pair.

```json
{
  "vendor_id": "vendor-001",
  "verification_id": "verif-123",
  "verification_level": "premium",
  "verified_timestamp_utc": "2026-02-21T12:00:00Z",
  "sequence_number": 2,
  "metadata": {}
}
```

**Workflow:** Updates `OnboardVendor` case to mark vendor as verified.

#### VendorSuspendedEvent
Triggered when vendor violates policies. Idempotency key: `(vendorId, suspensionId)` pair.

```json
{
  "vendor_id": "vendor-001",
  "suspension_id": "susp-456",
  "reason": "policy_violation",
  "suspended_timestamp_utc": "2026-02-21T14:00:00Z",
  "appeal_deadline_utc": "2026-03-07T14:00:00Z",
  "sequence_number": 3,
  "metadata": {}
}
```

**Workflow:** Launches `HandleVendorSuspension` case for suspension handling and appeals.

### Payment Events

#### PaymentAuthorizedEvent
Triggered when payment is authorized. Idempotency key: `(orderId, authorizationId)` pair.

```json
{
  "order_id": "order-abc123",
  "authorization_id": "auth-789",
  "amount_cents": 499500,
  "currency": "USD",
  "payment_method": "credit_card",
  "authorized_timestamp_utc": "2026-02-21T14:30:00Z",
  "expiration_timestamp_utc": "2026-02-28T14:30:00Z",
  "sequence_number": 50,
  "metadata": {}
}
```

**Workflow:** Updates order case with authorization details.

#### PaymentCapturedEvent
Triggered when payment is captured (settled). Idempotency key: `(authorizationId, captureId)` pair.

```json
{
  "authorization_id": "auth-789",
  "capture_id": "capt-101",
  "amount_cents": 499500,
  "captured_timestamp_utc": "2026-02-21T15:00:00Z",
  "settlement_window_hours": 24,
  "sequence_number": 51,
  "metadata": {}
}
```

**Workflow:** Confirms payment capture in order case.

#### PaymentFailedEvent
Triggered on authorization/capture failure. Idempotency key: `(orderId, failureId, failureTimestamp)` triple. **Retryable:** depends on failure code.

```json
{
  "order_id": "order-abc123",
  "failure_id": "fail-202",
  "failure_reason": "insufficient_funds",
  "failure_code": "insufficient_funds",
  "failed_timestamp_utc": "2026-02-21T14:35:00Z",
  "retryable": true,
  "retry_after_seconds": 300,
  "sequence_number": 52,
  "metadata": {}
}
```

**Workflow:** Launches `HandlePaymentFailure` case for retry/escalation.

#### PayoutInitiatedEvent
Triggered when payout to vendor is initiated. Idempotency key: `(vendorId, payoutId)` pair.

```json
{
  "vendor_id": "vendor-001",
  "payout_id": "payout-303",
  "amount_cents": 250000,
  "currency": "USD",
  "period_start_utc": "2026-02-01T00:00:00Z",
  "period_end_utc": "2026-02-28T23:59:59Z",
  "initiated_timestamp_utc": "2026-03-01T10:00:00Z",
  "expected_settlement_date": "2026-03-05",
  "sequence_number": 60,
  "metadata": {}
}
```

**Workflow:** Launches `ProcessPayout` case for vendor settlement.

## MCP Tools

### Tool 1: /marketplace/vendors/register

**Purpose:** Register a new vendor on GCP marketplace.

**Idempotency:** Keyed by `(companyName, contactEmail)` hash. Same input returns cached vendor ID.

**Input:**
```json
{
  "company_name": "ACME Corp",
  "contact_email": "vendor@acme.com",
  "region": "us-east1",
  "tier": "premium"  // or "standard", "enterprise"
}
```

**Output:**
```json
{
  "vendor_id": "vendor-001",
  "status": "onboarded",
  "tier": "premium",
  "event": {
    "event_id": "evt-...",
    "event_type": "VendorOnboardedEvent",
    ...
  }
}
```

**Side Effects:**
- Generates unique `vendor_id`
- Emits `VendorOnboardedEvent`
- Increments sequence number for vendor stream

### Tool 2: /marketplace/products/list

**Purpose:** Query product catalog with optional filtering.

**Input:**
```json
{
  "vendor_id": "vendor-001",        // optional
  "category": "software",            // optional
  "limit": 50,                       // default 50, max 100
  "offset": 0                        // default 0
}
```

**Output:**
```json
{
  "products": [
    {
      "product_id": "prod-xyz789",
      "vendor_id": "vendor-001",
      "name": "Enterprise Workflow Suite",
      "category": "software",
      "price_cents": 99900,
      "available": true
    }
  ],
  "total": 1,
  "limit": 50,
  "offset": 0
}
```

### Tool 3: /marketplace/orders/create

**Purpose:** Create a new order (place purchase).

**Idempotency:** Keyed by `(customerId, productId, timestamp)` triple. Same input returns cached order ID.

**Input:**
```json
{
  "vendor_id": "vendor-001",
  "product_id": "prod-xyz789",
  "quantity": 5,
  "customer_id": "cust-456",
  "region": "us-east1"
}
```

**Output:**
```json
{
  "order_id": "order-abc123",
  "status": "created",
  "total_price_cents": 499500,
  "event": {
    "event_id": "evt-...",
    "event_type": "OrderCreatedEvent",
    ...
  }
}
```

**Side Effects:**
- Generates unique `order_id`
- Emits `OrderCreatedEvent`
- Launches `ProcessOrder` workflow case

### Tool 4: /marketplace/fulfillment/track

**Purpose:** Track order fulfillment status (shipment, delivery).

**Input:**
```json
{
  "order_id": "order-abc123",
  "shipment_id": "ship-456"          // optional
}
```

**Output:**
```json
{
  "order_id": "order-abc123",
  "shipment_id": "ship-456",
  "status": "shipped",
  "carrier": "FedEx",
  "tracking_number": "1Z999AA10123456784",
  "estimated_delivery_days": 5,
  "event": {
    "event_id": "evt-...",
    "event_type": "OrderShippedEvent",
    ...
  }
}
```

### Tool 5: /marketplace/payments/process

**Purpose:** Process payment (authorize, capture, refund).

**Idempotency:** Keyed by `(orderId, gatewayId)` pair. Same input returns cached result.

**Input:**
```json
{
  "order_id": "order-abc123",
  "amount_cents": 499500,
  "operation": "authorize",     // or "capture", "refund"
  "payment_method": "credit_card",
  "gateway_id": "txn-789"
}
```

**Output (authorize):**
```json
{
  "order_id": "order-abc123",
  "status": "authorized",
  "amount_cents": 499500,
  "event": {
    "event_id": "evt-...",
    "event_type": "PaymentAuthorizedEvent",
    ...
  }
}
```

**Output (capture):**
```json
{
  "order_id": "order-abc123",
  "status": "captured",
  "amount_cents": 499500,
  "event": {
    "event_id": "evt-...",
    "event_type": "PaymentCapturedEvent",
    ...
  }
}
```

## A2A Message Handlers

The `MarketplaceA2AHandler` class processes incoming marketplace events from autonomous agents.

### Message Flow

1. **Vendor Agent sends event** (A2A message with JSON event)
2. **Handler receives & validates** sequence + idempotency
3. **Handler deduplicates** (checks idempotency cache)
4. **Handler routes** event by type to appropriate handler
5. **Handler launches/updates** YAWL workflow case
6. **Handler sends ACK** back to agent (with sequence number)

### Ordering Guarantee

Events must arrive in sequence order:

```
Vendor Agent sends seq 1, 2, 3, 4, 5...
Handler validates: seq >= last_seen_seq + 1
If seq < last_seen_seq: OUT_OF_ORDER (reject)
If seq > last_seen_seq + 1: GAP (log warning, accept)
If seq == last_seen_seq: DUPLICATE (return cached response)
```

### Idempotency Guarantee

Events are deduplicated by `idempotency_key`:

```
1st call:  idempotencyKey="key1" → process event → cache response
2nd call:  idempotencyKey="key1" → return cached response (no reprocessing)
```

### Event Routing

| Event Type | Workflow Case | Description |
|------------|---------------|-------------|
| OrderCreatedEvent | ProcessOrder | Launch case with order details |
| OrderConfirmedEvent | ProcessOrder (update) | Update case data with confirmation |
| OrderShippedEvent | ProcessOrder (update) | Update case with shipment tracking |
| OrderDeliveredEvent | ProcessOrder (update) | Mark order delivered |
| OrderReturnedEvent | ProcessReturn | Launch return handling case |
| VendorOnboardedEvent | OnboardVendor | Launch vendor onboarding case |
| VendorVerifiedEvent | OnboardVendor (update) | Mark vendor verified |
| VendorSuspendedEvent | HandleVendorSuspension | Launch suspension handling case |
| PaymentAuthorizedEvent | ProcessOrder (update) | Update with payment auth details |
| PaymentCapturedEvent | ProcessOrder (update) | Confirm payment capture |
| PaymentFailedEvent | HandlePaymentFailure | Launch failure retry/escalation |
| PayoutInitiatedEvent | ProcessPayout | Launch vendor settlement case |

## Configuration & Setup

### Environment Variables

**MCP Server:**
```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=<password>
```

**A2A Server:**
```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=<password>
export A2A_PORT=8081
export A2A_JWT_SECRET=<32+ char secret for JWT>
# or
export A2A_API_KEY_MASTER=<16+ char master key>
export A2A_API_KEY=<API key to auto-register>
```

### Starting the Servers

**MCP Server (STDIO):**
```bash
java -cp yawl-engine.jar:yawl-mcp.jar \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

**A2A Server (HTTP):**
```bash
java -cp yawl-engine.jar:yawl-a2a.jar \
  org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
```

## Usage Examples

### Using MCP Tools (via Claude or other MCP client)

```
User: Register a new vendor on GCP marketplace
Claude -> tool: marketplace_vendor_register
  Input: {
    "company_name": "ACME Corp",
    "contact_email": "vendor@acme.com",
    "region": "us-east1",
    "tier": "premium"
  }
Claude receives response with vendor_id, onboarding event

User: List products from vendor ACME
Claude -> tool: marketplace_products_list
  Input: {
    "vendor_id": "vendor-001",
    "limit": 10
  }
Claude receives list of products with pricing

User: Create an order for 5 licenses
Claude -> tool: marketplace_orders_create
  Input: {
    "vendor_id": "vendor-001",
    "product_id": "prod-xyz789",
    "quantity": 5,
    "customer_id": "cust-456",
    "region": "us-east1"
  }
Claude receives order_id, total price, OrderCreatedEvent

User: Track shipment status
Claude -> tool: marketplace_fulfillment_track
  Input: {
    "order_id": "order-abc123"
  }
Claude receives shipment tracking, carrier, delivery estimate
```

### Using A2A Messages (from autonomous agents)

**Vendor Agent sends OrderCreatedEvent:**
```json
POST http://yawl-a2a-server:8081/
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "event_id": "evt-12345",
  "event_type": "OrderCreatedEvent",
  "event": {
    "order_id": "order-abc123",
    "vendor_id": "vendor-001",
    "product_id": "prod-xyz789",
    ...
  },
  "sequence_number": 42,
  "idempotency_key": "base64...",
  "timestamp_utc": "2026-02-21T14:30:00Z",
  "source_agent": "vendor-agent-001",
  "source_region": "us-east1"
}
```

**YAWL A2A Handler responds:**
```json
{
  "status": "processed",
  "event_id": "evt-12345",
  "sequence_number": 42
}
```

**YAWL Engine:**
- Launches `ProcessOrder` workflow case with order data
- Case executes payment verification, fulfillment, delivery

## Testing

### Test Idempotency

```bash
# Send same order creation request twice
curl -X POST http://localhost:8081/ \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "order_id": "test-order-1",
    "customer_id": "cust-001",
    "product_id": "prod-001",
    "timestamp_utc": "2026-02-21T14:00:00Z"
  }'

# Response 1: status="processed"
# Response 2: status="duplicate" (same idempotency key)
```

### Test Message Ordering

```bash
# Send events in correct order (seq 1, 2, 3)
# All succeed

# Send event with seq 5 (skip 2, 3, 4)
# Handler returns: "sequence gap detected, continuing..."

# Send event with seq 2 (lower than 5)
# Handler returns: "out_of_order", rejects event
```

### Test Workflow Case Launch

```bash
# Send OrderCreatedEvent via A2A
# Check YAWL engine for new "ProcessOrder" case
yawlctl list-cases  # or via MCP tool

# Case should exist with variables:
#   orderId, vendorId, productId, quantity, etc.
```

## Advanced Topics

### Custom Workflow Specifications

To integrate with your own workflows, modify the `routeEvent()` method in `MarketplaceA2AHandler`:

```java
private String routeEvent(EventEnvelope envelope) throws IOException {
    return switch (envelope.eventType()) {
        case "OrderCreatedEvent" -> {
            // Launch your custom workflow
            String caseId = launchWorkflowCase("MyCustomOrderWorkflow", ...);
            yield String.format("{\"case_id\": \"%s\"}", caseId);
        }
        // ... other cases
    };
}
```

### Extending Event Types

To add new marketplace events:

1. Create new record in `MarketplaceEventSchema`:
   ```java
   public record MyCustomEvent(
       @JsonProperty("event_id") String eventId,
       @JsonProperty("custom_field") String customField,
       ...
   ) {}
   ```

2. Add to sealed interface:
   ```java
   public sealed interface MarketplaceEvent
       permits ... MyCustomEvent, ...
   ```

3. Add handler in `MarketplaceA2AHandler`:
   ```java
   case "MyCustomEvent" -> handleMyCustomEvent(envelope);
   ```

### Monitoring & Logging

All marketplace operations are logged to `org.yawlfoundation.yawl.integration.a2a.marketplace`:

```bash
# Log file: yawl-marketplace-integration.log
# Examples:
# [INFO] Event processed successfully: event_id=evt-12345, type=OrderCreatedEvent
# [WARN] Sequence gap detected: expected 42, got 45
# [ERROR] Failed to process marketplace event: Payment authorization failed
```

## Troubleshooting

### Duplicate event processing

**Symptom:** Same order appears twice in YAWL

**Cause:** Idempotency key collision or cache miss

**Solution:**
- Verify idempotency key is based on immutable fields (orderId, not timestamp)
- Check idempotency cache size (default: unbounded)
- Monitor `processedEvents` map for growth

### Out-of-order events

**Symptom:** Events processed out of sequence, causing state corruption

**Cause:** Network reordering, agent retry logic

**Solution:**
- Ensure agents use monotonically increasing sequence numbers
- Check for gaps in sequence (indicates lost messages)
- Implement upstream retry-with-backoff

### Missing workflow cases

**Symptom:** A2A event received, but no YAWL case launched

**Cause:** InterfaceB connection issue, specification not loaded

**Solution:**
- Verify `YAWL_ENGINE_URL`, `YAWL_USERNAME`, `YAWL_PASSWORD` are correct
- Load marketplace workflow specifications (ProcessOrder, OnboardVendor, etc.)
- Check YAWL engine logs for case launch errors

## References

- `.claude/rules/integration/mcp-a2a-conventions.md` — MCP/A2A protocol rules
- `.claude/rules/integration/autonomous-agents.md` — Autonomous agent framework
- `MarketplaceEventSchema.java` — Event record definitions
- `GcpMarketplaceMcpTools.java` — MCP tool implementations
- `MarketplaceA2AHandler.java` — A2A message handler

## License

Copyright (c) 2004-2025 The YAWL Foundation. Licensed under GNU LGPL v2.1 or later.
