# GCP Marketplace Integration Usage Examples

Complete working examples for MCP tools and A2A message handlers.

## MCP Tool Usage Examples

### Example 1: Register a Vendor (Idempotent)

**Tool:** `marketplace_vendor_register`

**Request:**
```json
{
  "company_name": "ACME Corporation",
  "contact_email": "vendor@acme.com",
  "region": "us-east1",
  "tier": "premium"
}
```

**Response:**
```json
{
  "vendor_id": "vendor-a1b2c3d4",
  "status": "onboarded",
  "tier": "premium",
  "event": {
    "event_id": "evt-001",
    "event_type": "VendorOnboardedEvent",
    "vendor_id": "vendor-a1b2c3d4",
    "company_name": "ACME Corporation",
    "contact_email": "vendor@acme.com",
    "region": "us-east1",
    "tier": "premium",
    "onboarded_timestamp_utc": "2026-02-21T14:30:00Z",
    "sequence_number": 1,
    "metadata": { "source": "mcp-tool" }
  }
}
```

**Idempotency Test:**

```bash
# First call
curl -X POST http://localhost:8080/mcp/tools/marketplace_vendor_register \
  -H "Content-Type: application/json" \
  -d '{
    "company_name": "ACME Corporation",
    "contact_email": "vendor@acme.com",
    "region": "us-east1",
    "tier": "premium"
  }'
# Response: { "vendor_id": "vendor-a1b2c3d4", ... }

# Second call (same input)
curl -X POST http://localhost:8080/mcp/tools/marketplace_vendor_register \
  -H "Content-Type: application/json" \
  -d '{
    "company_name": "ACME Corporation",
    "contact_email": "vendor@acme.com",
    "region": "us-east1",
    "tier": "premium"
  }'
# Response: { "vendor_id": "vendor-a1b2c3d4", ... }  ← SAME vendor ID
# (cached response, no duplicate event emitted)
```

### Example 2: Create an Order (Idempotent)

**Tool:** `marketplace_orders_create`

**Request:**
```json
{
  "vendor_id": "vendor-a1b2c3d4",
  "product_id": "prod-enterprise-suite",
  "quantity": 5,
  "customer_id": "cust-xyz789",
  "region": "us-east1"
}
```

**Response:**
```json
{
  "order_id": "order-e5f6g7h8",
  "status": "created",
  "total_price_cents": 499500,
  "event": {
    "event_id": "evt-002",
    "event_type": "OrderCreatedEvent",
    "order_id": "order-e5f6g7h8",
    "vendor_id": "vendor-a1b2c3d4",
    "product_id": "prod-enterprise-suite",
    "quantity": 5,
    "unit_price_cents": 99900,
    "total_price_cents": 499500,
    "customer_id": "cust-xyz789",
    "region": "us-east1",
    "timestamp_utc": "2026-02-21T14:35:00Z",
    "sequence_number": 2,
    "metadata": { "source": "mcp-tool" }
  }
}
```

**Workflow Integration:**
- MCP tool emits `OrderCreatedEvent`
- YAWL engine receives event via A2A handler
- A2A handler launches `ProcessOrder` case with:
  - orderId: order-e5f6g7h8
  - vendorId: vendor-a1b2c3d4
  - productId: prod-enterprise-suite
  - quantity: 5
  - totalPrice: 499500
  - customerId: cust-xyz789
  - region: us-east1

### Example 3: List Products (with Filtering)

**Tool:** `marketplace_products_list`

**Request (Filter by vendor):**
```json
{
  "vendor_id": "vendor-a1b2c3d4",
  "limit": 10,
  "offset": 0
}
```

**Response:**
```json
{
  "products": [
    {
      "product_id": "prod-enterprise-suite",
      "vendor_id": "vendor-a1b2c3d4",
      "name": "Enterprise Workflow Suite",
      "category": "software",
      "price_cents": 99900,
      "available": true
    },
    {
      "product_id": "prod-support-addon",
      "vendor_id": "vendor-a1b2c3d4",
      "name": "Premium Support Add-on",
      "category": "services",
      "price_cents": 29900,
      "available": true
    }
  ],
  "total": 2,
  "limit": 10,
  "offset": 0
}
```

**Request (Filter by category):**
```json
{
  "category": "software",
  "limit": 50,
  "offset": 0
}
```

**Response:**
```json
{
  "products": [
    { "product_id": "prod-1", "category": "software", ... },
    { "product_id": "prod-2", "category": "software", ... },
    ...
  ],
  "total": 47,
  "limit": 50,
  "offset": 0
}
```

### Example 4: Track Fulfillment

**Tool:** `marketplace_fulfillment_track`

**Request:**
```json
{
  "order_id": "order-e5f6g7h8"
}
```

**Response:**
```json
{
  "order_id": "order-e5f6g7h8",
  "shipment_id": "ship-i9j0k1l2",
  "status": "shipped",
  "carrier": "FedEx",
  "tracking_number": "1Z999AA10123456784",
  "estimated_delivery_days": 5,
  "event": {
    "event_id": "evt-003",
    "event_type": "OrderShippedEvent",
    "order_id": "order-e5f6g7h8",
    "shipment_id": "ship-i9j0k1l2",
    "carrier": "FedEx",
    "tracking_number": "1Z999AA10123456784",
    "estimated_delivery_days": 5,
    "shipped_timestamp_utc": "2026-02-21T15:00:00Z",
    "sequence_number": 3,
    "metadata": { "source": "mcp-tool" }
  }
}
```

**Workflow Integration:**
- MCP tool emits `OrderShippedEvent`
- A2A handler updates `ProcessOrder` case with shipment details
- Shipment status becomes visible to customer via MCP resource

### Example 5: Process Payment (Authorize)

**Tool:** `marketplace_payments_process`

**Request (Authorize):**
```json
{
  "order_id": "order-e5f6g7h8",
  "amount_cents": 499500,
  "operation": "authorize",
  "payment_method": "credit_card",
  "gateway_id": "txn-12345"
}
```

**Response:**
```json
{
  "order_id": "order-e5f6g7h8",
  "status": "authorized",
  "amount_cents": 499500,
  "event": {
    "event_id": "evt-004",
    "event_type": "PaymentAuthorizedEvent",
    "order_id": "order-e5f6g7h8",
    "authorization_id": "auth-m3n4o5p6",
    "amount_cents": 499500,
    "currency": "USD",
    "payment_method": "credit_card",
    "authorized_timestamp_utc": "2026-02-21T14:35:00Z",
    "expiration_timestamp_utc": "2026-02-28T14:35:00Z",
    "sequence_number": 4,
    "metadata": { "source": "mcp-tool" }
  }
}
```

**Request (Capture):**
```json
{
  "order_id": "order-e5f6g7h8",
  "amount_cents": 499500,
  "operation": "capture",
  "payment_method": "credit_card",
  "gateway_id": "txn-12345"
}
```

**Response:**
```json
{
  "order_id": "order-e5f6g7h8",
  "status": "captured",
  "amount_cents": 499500,
  "event": {
    "event_id": "evt-005",
    "event_type": "PaymentCapturedEvent",
    "authorization_id": "auth-m3n4o5p6",
    "capture_id": "capt-q7r8s9t0",
    "amount_cents": 499500,
    "captured_timestamp_utc": "2026-02-21T14:36:00Z",
    "settlement_window_hours": 24,
    "sequence_number": 5,
    "metadata": { "source": "mcp-tool" }
  }
}
```

**Request (Refund):**
```json
{
  "order_id": "order-e5f6g7h8",
  "amount_cents": 499500,
  "operation": "refund",
  "payment_method": "credit_card",
  "gateway_id": "txn-12345"
}
```

**Response:**
```json
{
  "order_id": "order-e5f6g7h8",
  "status": "refunded",
  "amount_cents": 499500,
  "event": {
    "event_id": "evt-006",
    "event_type": "OrderReturnedEvent",
    "order_id": "order-e5f6g7h8",
    "return_id": "ret-u1v2w3x4",
    "reason": "refund_via_mcp_tool",
    "refund_cents": 499500,
    "returned_timestamp_utc": "2026-02-21T14:37:00Z",
    "sequence_number": 6,
    "metadata": { "source": "mcp-tool" }
  }
}
```

## A2A Message Handler Examples

### Example 1: Vendor Agent Sends OrderCreatedEvent

**Agent:** Vendor Agent running on vendor infrastructure

**Message (A2A POST):**
```bash
curl -X POST http://yawl-a2a-server:8081/ \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "evt-001",
    "event_type": "OrderCreatedEvent",
    "event": {
      "order_id": "order-vendor-001",
      "vendor_id": "vendor-a1b2c3d4",
      "product_id": "prod-enterprise-suite",
      "quantity": 10,
      "unit_price_cents": 99900,
      "total_price_cents": 999000,
      "customer_id": "cust-acme-inc",
      "region": "us-east1",
      "timestamp_utc": "2026-02-21T14:30:00Z",
      "sequence_number": 1,
      "metadata": { "source": "vendor_agent" }
    },
    "sequence_number": 1,
    "idempotency_key": "Y3VzdC1hY21lLWluY3xwcm9kLWVudGVycHJpc2Utc3VpdGV8MjAyNi0wMi0yMVQxNDozMDowMFo=",
    "timestamp_utc": "2026-02-21T14:30:00Z",
    "source_agent": "vendor-agent-001",
    "source_region": "us-east1"
  }'
```

**Response (A2A Handler):**
```json
{
  "status": "processed",
  "event_id": "evt-001",
  "sequence_number": 1
}
```

**YAWL Engine Action:**
- Handler validates sequence: 1 >= 0 + 1? YES
- Handler checks idempotency: not in cache? YES
- Handler routes to OrderCreatedEvent handler
- Handler calls `launchWorkflowCase("ProcessOrder", {...})`
  - Result: Case ID created (e.g., "ProcessOrder-42")
- Handler caches idempotency key
- Handler returns ACK to vendor agent

**Workflow Case Created:**
```
Specification: ProcessOrder v1.0
Case ID: ProcessOrder-42
Data:
  orderId: "order-vendor-001"
  vendorId: "vendor-a1b2c3d4"
  productId: "prod-enterprise-suite"
  quantity: 10
  totalPrice: 999000
  customerId: "cust-acme-inc"
  region: "us-east1"
Status: Running
```

### Example 2: Duplicate OrderCreatedEvent (Idempotency)

**Vendor Agent retries** (network timeout → automatic retry):

```bash
curl -X POST http://yawl-a2a-server:8081/ \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "evt-001",
    "event_type": "OrderCreatedEvent",
    "event": {
      "order_id": "order-vendor-001",
      "vendor_id": "vendor-a1b2c3d4",
      ...
    },
    "sequence_number": 1,
    "idempotency_key": "Y3VzdC1hY21lLWluY3xwcm9kLWVudGVycHJpc2Utc3VpdGV8MjAyNi0wMi0yMVQxNDozMDowMFo=",
    ...
  }'
```

**Response (Cached):**
```json
{
  "status": "duplicate",
  "event_id": "evt-001",
  "sequence_number": 1
}
```

**Result:** No duplicate case created. Same case ID still exists.

### Example 3: Out-of-Order Event Detection

**Vendor Agent sends events:**

```
Message 1: seq=1, orderId="order-vendor-001"
  → Handler processes, updates lastSeq to 1

Message 3: seq=3, orderId="order-vendor-003"
  → Handler detects: 3 > 1 + 1 (gap!)
  → Handler logs: "Sequence gap detected: expected 2, got 3"
  → Handler accepts anyway (doesn't reject, logs warning)
  → Updates lastSeq to 3

Message 2: seq=2, orderId="order-vendor-002" (retry/reorder)
  → Handler detects: 2 < 3 (out of order!)
  → Handler logs: "Out-of-order event received: seq 2 from vendor-agent-001"
  → Handler returns: { "status": "out_of_order", "error_code": "sequence_invalid" }
  → YAWL engine DOES NOT process this event
```

**Result:** Ordering preserved despite network packet reordering.

### Example 4: Fulfillment Agent Sends OrderShippedEvent

**Fulfillment Agent sends shipment notification:**

```bash
curl -X POST http://yawl-a2a-server:8081/ \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "evt-002",
    "event_type": "OrderShippedEvent",
    "event": {
      "order_id": "order-vendor-001",
      "shipment_id": "ship-fed-12345",
      "carrier": "FedEx",
      "tracking_number": "1Z999AA10123456784",
      "estimated_delivery_days": 5,
      "shipped_timestamp_utc": "2026-02-21T15:00:00Z",
      "sequence_number": 2,
      "metadata": { "source": "fulfillment_agent" }
    },
    "sequence_number": 2,
    "idempotency_key": "c2hpcC1mZWQtMTIzNDV8MTYyMzE3NTYwMA==",
    "timestamp_utc": "2026-02-21T15:00:00Z",
    "source_agent": "fulfillment-agent-001",
    "source_region": "us-east1"
  }'
```

**Response:**
```json
{
  "status": "processed",
  "event_id": "evt-002",
  "sequence_number": 2
}
```

**YAWL Engine Action:**
- Handler routes to OrderShippedEvent handler
- Handler calls `updateWorkflowCaseData("order-vendor-001", {...})`
  - Updates ProcessOrder case with shipment tracking
  - Case data now includes:
    - shipmentId: "ship-fed-12345"
    - carrier: "FedEx"
    - trackingNumber: "1Z999AA10123456784"
    - estimatedDeliveryDays: 5

### Example 5: Payment Processor Sends PaymentFailedEvent

**Payment Processor sends failure notification:**

```bash
curl -X POST http://yawl-a2a-server:8081/ \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "evt-003",
    "event_type": "PaymentFailedEvent",
    "event": {
      "order_id": "order-vendor-001",
      "failure_id": "fail-001",
      "failure_reason": "insufficient_funds",
      "failure_code": "INSUFFICIENT_FUNDS",
      "failed_timestamp_utc": "2026-02-21T14:35:30Z",
      "retryable": true,
      "retry_after_seconds": 300,
      "sequence_number": 3,
      "metadata": { "source": "payment_processor" }
    },
    "sequence_number": 3,
    "idempotency_key": "b3JkZXItdmVuZG9yLTAwMXxmYWlsLWZhaWwtMDAx",
    "timestamp_utc": "2026-02-21T14:35:30Z",
    "source_agent": "payment-processor-001",
    "source_region": "us-east1"
  }'
```

**Response:**
```json
{
  "status": "processed",
  "event_id": "evt-003",
  "sequence_number": 3
}
```

**YAWL Engine Action:**
- Handler routes to PaymentFailedEvent handler
- Handler calls `launchWorkflowCase("HandlePaymentFailure", {...})`
  - New case: HandlePaymentFailure-43
  - Data:
    - orderId: "order-vendor-001"
    - failureCode: "INSUFFICIENT_FUNDS"
    - reason: "insufficient_funds"
    - retryable: true
    - retryAfterSeconds: 300
  - Case executes: notify customer, schedule retry after 5 minutes

## Sequence Diagram: Complete Order Flow

```
Vendor Agent          MCP Tool           A2A Handler        YAWL Engine
    │                   │                    │                   │
    ├─ Create Order ──→ /marketplace/orders/create
    │                   │                    │
    │                   ├─ Check cache ──────┤
    │                   ├─ Generate ID ──────┤
    │                   ├─ Emit event ───────┤
    │                   ├─ Update cache ─────┤
    │                   ├─ Return order-123 ┤
    │ ← order-123 ──────┤                    │
    │                   │                    │
    │ OrderCreatedEvent (A2A message)
    ├─────────────────────────────────────→ │
    │                   │                    ├─ Validate seq
    │                   │                    ├─ Check dedup
    │                   │                    ├─ Route event
    │                   │                    ├─ launchCase
    │                   │                    ├─→ ProcessOrder-42
    │                   │                    │
    │ ← ACK (seq=1) ────────────────────────┤
    │                   │                    │
    │
    │ [Fulfillment Agent ships order]
    │
    ├─ OrderShippedEvent (A2A) ────────────→ │
    │                   │                    ├─ Validate seq
    │                   │                    ├─ Check dedup
    │                   │                    ├─ Route event
    │                   │                    ├─ updateCase
    │                   │                    ├─→ ProcessOrder-42
    │                   │                    │
    │ ← ACK (seq=2) ────────────────────────┤
    │
    │ [Payment Processor confirms payment]
    │
    ├─ PaymentCapturedEvent (A2A) ─────────→ │
    │                   │                    ├─ Validate seq
    │                   │                    ├─ Check dedup
    │                   │                    ├─ Route event
    │                   │                    ├─ updateCase
    │                   │                    ├─→ ProcessOrder-42
    │                   │                    │
    │ ← ACK (seq=3) ────────────────────────┤
    │
    │ [Customer receives order]
    │
    ├─ OrderDeliveredEvent (A2A) ──────────→ │
    │                   │                    ├─ Validate seq
    │                   │                    ├─ Check dedup
    │                   │                    ├─ Route event
    │                   │                    ├─ updateCase
    │                   │                    ├─→ ProcessOrder-42
    │                   │                    │
    │ ← ACK (seq=4) ────────────────────────┤
    │
    └────────────────────────────────────────┘
```

## Testing Checklist

- [x] MCP tool vendor registration (idempotent)
- [x] MCP tool order creation (idempotent)
- [x] MCP tool product listing (with filters)
- [x] MCP tool fulfillment tracking
- [x] MCP tool payment processing (all 3 operations)
- [x] A2A message ordering (validate seq >= last+1)
- [x] A2A message idempotency (duplicate detection)
- [x] A2A event routing (all 12 event types)
- [x] Workflow case launch (ProcessOrder, OnboardVendor, etc.)
- [x] Case data updates (shipment, payment, vendor)
- [x] Out-of-order event rejection
- [x] Sequence gap detection (with logging)

---

**Complete integration examples ready for deployment.**
