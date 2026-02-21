# GCP Marketplace Integration Architecture

## System Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        GCP Marketplace Ecosystem                          │
└─────────────────────────────────────────────────────────────────────────┘
         │                    │                     │
         ↓                    ↓                     ↓
    ┌─────────┐         ┌──────────┐        ┌──────────────┐
    │ Vendor  │         │Fulfillment│        │   Payment    │
    │ Agent   │         │  Agent    │        │ Processor    │
    └────┬────┘         └─────┬─────┘        └──────┬───────┘
         │                    │                     │
         │ A2A Messages       │ A2A Messages        │ A2A Messages
         │ (OrderCreated,     │ (OrderShipped,      │ (PaymentAuth,
         │  VendorOnboarded)  │  OrderDelivered)    │  PaymentFailed)
         │                    │                     │
         └─────────┬──────────┴──────────────┬──────┘
                   │                         │
                   ↓                         ↓
          ┌──────────────────────────────────────────┐
          │  YAWL A2A Server (MarketplaceA2AHandler) │
          │                                          │
          │  Responsibilities:                       │
          │  • Validate message sequence             │
          │  • Detect duplicates (idempotency)       │
          │  • Route events to handlers              │
          │  • Launch/update YAWL cases              │
          │  • Return ACK with sequence number       │
          │                                          │
          │  Guarantees:                             │
          │  • FIFO ordering per stream              │
          │  • At-most-once semantics (idempotent)   │
          │  • Message ordering validation           │
          └──────────────────┬───────────────────────┘
                             │
                             ↓
          ┌──────────────────────────────────────────┐
          │      YAWL Engine (InterfaceB)            │
          │                                          │
          │  Specification Repository:               │
          │  • ProcessOrder.yawl                     │
          │  • OnboardVendor.yawl                    │
          │  • HandlePaymentFailure.yawl             │
          │  • ProcessReturn.yawl                    │
          │  • HandleVendorSuspension.yawl           │
          │  • ProcessPayout.yawl                    │
          │                                          │
          │  Running Cases:                          │
          │  • ProcessOrder-42 (order-123)           │
          │  • OnboardVendor-15 (vendor-001)         │
          │  • HandlePaymentFailure-8 (order-456)    │
          │  • ... (others)                          │
          │                                          │
          │  API:                                    │
          │  • launchCase(specId, caseData)          │
          │  • updateCaseData(caseId, updates)       │
          │  • getCaseState(caseId)                  │
          └──────────────────────────────────────────┘
                             │
                             ↓
              ┌──────────────────────────┐
              │  Persistence Layer       │
              │  (YAWL Case Database)    │
              │                          │
              │  Tables:                 │
              │  • Specifications        │
              │  • Cases                 │
              │  • Work Items            │
              │  • Case Data             │
              │  • Event History         │
              └──────────────────────────┘
```

## Component Architecture

### Layer 1: Event Schema (Domain Model)

```
MarketplaceEventSchema.java
├── OrderCreatedEvent (record)
├── OrderConfirmedEvent (record)
├── OrderShippedEvent (record)
├── OrderDeliveredEvent (record)
├── OrderReturnedEvent (record)
├── VendorOnboardedEvent (record)
├── VendorVerifiedEvent (record)
├── VendorSuspendedEvent (record)
├── PaymentAuthorizedEvent (record)
├── PaymentCapturedEvent (record)
├── PaymentFailedEvent (record)
├── PayoutInitiatedEvent (record)
├── EventEnvelope (record)
│   ├── eventId: String
│   ├── eventType: String
│   ├── event: Object (one of above)
│   ├── sequenceNumber: long
│   ├── idempotencyKey: String
│   ├── timestampUtc: String
│   ├── sourceAgent: String
│   └── sourceRegion: String
└── MarketplaceEvent (sealed interface)
    └── permits all 12 event types
```

**Key Property:** All records use Jackson `@JsonProperty` annotations for JSON serialization.

### Layer 2: MCP Tools (Integration API)

```
GcpMarketplaceMcpTools.java
├── Constructor
│   ├── interfaceB: InterfaceB_EnvironmentBasedClient
│   ├── sessionHandle: String
│   └── caches:
│       ├── idempotencyCache: ConcurrentHashMap<String, String>
│       └── sequenceTrackers: Map<String, AtomicLong>
│
├── Tool 1: vendorRegisterTool()
│   └── executeVendorRegister()
│       └── emits VendorOnboardedEvent
│
├── Tool 2: productListTool()
│   └── executeProductList()
│       └── returns filtered catalog
│
├── Tool 3: orderCreateTool()
│   └── executeOrderCreate()
│       └── emits OrderCreatedEvent
│
├── Tool 4: fulfillmentTrackTool()
│   └── executeFulfillmentTrack()
│       └── emits OrderShippedEvent
│
├── Tool 5: paymentProcessTool()
│   └── executePaymentProcess()
│       └── emits Payment* events
│
└── Helper Methods:
    ├── nextSequenceNumber(stream)
    └── hashIdempotencyKey(values)
```

**Key Property:** Idempotency cache prevents duplicate event emission.

### Layer 3: A2A Message Handler (Event Processing)

```
MarketplaceA2AHandler.java
├── Constructor
│   ├── interfaceB: InterfaceB_EnvironmentBasedClient
│   ├── objectMapper: ObjectMapper
│   └── state:
│       ├── processedEvents: Map<String, Long>
│       └── lastSeenSequence: Map<String, Long>
│
├── handleMarketplaceEvent(Message)
│   ├── extractTextContent(message)
│   ├── parseEventEnvelope(json)
│   ├── validateEventSequence()
│   │   └── seq >= lastSeenSeq + 1?
│   ├── isEventDuplicate()
│   │   └── idempotencyKey in processedEvents?
│   ├── routeEvent()
│   │   └── dispatch by eventType
│   └── buildAckResponse()
│
├── 12 Event Handlers:
│   ├── handleOrderCreatedEvent() → launchCase("ProcessOrder", ...)
│   ├── handleOrderConfirmedEvent() → updateCaseData(...)
│   ├── handleOrderShippedEvent() → updateCaseData(...)
│   ├── handleOrderDeliveredEvent() → updateCaseData(...)
│   ├── handleOrderReturnedEvent() → launchCase("ProcessReturn", ...)
│   ├── handleVendorOnboardedEvent() → launchCase("OnboardVendor", ...)
│   ├── handleVendorVerifiedEvent() → updateCaseData(...)
│   ├── handleVendorSuspendedEvent() → launchCase("HandleVendorSuspension", ...)
│   ├── handlePaymentAuthorizedEvent() → updateCaseData(...)
│   ├── handlePaymentCapturedEvent() → updateCaseData(...)
│   ├── handlePaymentFailedEvent() → launchCase("HandlePaymentFailure", ...)
│   └── handlePayoutInitiatedEvent() → launchCase("ProcessPayout", ...)
│
├── Sequence Validation:
│   ├── validateEventSequence(envelope)
│   │   └── checks monotonic ordering per source_agent:source_region
│   └── recordSequence(sourceAgent, seqNumber)
│       └── updates lastSeenSequence
│
├── Idempotency Check:
│   └── isEventDuplicate(envelope)
│       └── checks idempotencyKey in processedEvents
│
└── Helper Methods:
    ├── launchWorkflowCase(specId, caseData)
    ├── updateWorkflowCaseData(caseId, updates)
    └── buildAckResponse() / buildErrorResponse()
```

**Key Property:** Sequence + idempotency validation BEFORE event routing.

## Data Flow: Complete Order Journey

```
Timeline: T0 to T+20 minutes

T0: Customer places order (vendor agent)
    └─→ MCP Tool: /marketplace/orders/create
        └─→ Action: Generate order-123, emit OrderCreatedEvent, cache response
        └─→ Response: { "order_id": "order-123", "event": {...} }

T+30s: Order created event reaches YAWL (A2A)
    └─→ A2A Handler: handleMarketplaceEvent()
        ├─ validateEventSequence() → seq 1 >= 0+1? YES
        ├─ isEventDuplicate() → not in cache? YES
        ├─ routeEvent() → handleOrderCreatedEvent()
        │  └─ launchWorkflowCase("ProcessOrder", {
        │      orderId: "order-123",
        │      vendorId: "vendor-001",
        │      productId: "prod-xyz",
        │      quantity: 5,
        │      totalPrice: 499500,
        │      customerId: "cust-456",
        │      region: "us-east1"
        │    })
        │  └─ Result: Case "ProcessOrder-42" created, running
        ├─ buildAckResponse() → return ack
        └─→ Response: { "status": "processed", "sequence_number": 1 }

T+1m: Vendor prepares order
    └─→ Vendor system checks case status via MCP Resource: yawl://cases/ProcessOrder-42

T+5m: Fulfillment agent ships order
    └─→ A2A Handler: handleOrderShippedEvent()
        ├─ validateEventSequence() → seq 2 >= 1+1? YES
        ├─ isEventDuplicate() → not in cache? YES
        ├─ routeEvent() → handleOrderShippedEvent()
        │  └─ updateWorkflowCaseData("ProcessOrder-42", {
        │      shipmentId: "ship-456",
        │      carrier: "FedEx",
        │      trackingNumber: "1Z999AA...",
        │      estimatedDeliveryDays: 5
        │    })
        │  └─ Result: Case data updated, workflow transitions
        ├─ buildAckResponse()
        └─→ Response: { "status": "processed", "sequence_number": 2 }

T+6m: Payment processor authorizes charge
    └─→ A2A Handler: handlePaymentAuthorizedEvent()
        ├─ validateEventSequence() → seq 3 >= 2+1? YES
        ├─ routeEvent() → handlePaymentAuthorizedEvent()
        │  └─ updateWorkflowCaseData("ProcessOrder-42", {
        │      authorizationId: "auth-789",
        │      authorizedAmount: 499500
        │    })
        │  └─ Result: Case data updated, payment confirmed
        └─→ Response: { "status": "processed", "sequence_number": 3 }

T+6.5m: Payment processor captures funds
    └─→ A2A Handler: handlePaymentCapturedEvent()
        ├─ validateEventSequence() → seq 4 >= 3+1? YES
        ├─ routeEvent() → handlePaymentCapturedEvent()
        │  └─ updateWorkflowCaseData("ProcessOrder-42", {
        │      captureId: "capt-101",
        │      capturedAmount: 499500,
        │      settlementDate: "2026-02-22"
        │    })
        │  └─ Result: Case data updated, settlement scheduled
        └─→ Response: { "status": "processed", "sequence_number": 4 }

T+20m: Customer receives order
    └─→ A2A Handler: handleOrderDeliveredEvent()
        ├─ validateEventSequence() → seq 5 >= 4+1? YES
        ├─ routeEvent() → handleOrderDeliveredEvent()
        │  └─ updateWorkflowCaseData("ProcessOrder-42", {
        │      deliveredAt: "2026-02-21T14:50:00Z"
        │    })
        │  └─ Result: Case transitions to COMPLETED state
        └─→ Response: { "status": "processed", "sequence_number": 5 }

Final State: ProcessOrder-42 (COMPLETED)
├── orderId: "order-123"
├── vendorId: "vendor-001"
├── productId: "prod-xyz"
├── quantity: 5
├── totalPrice: 499500
├── customerId: "cust-456"
├── shipmentId: "ship-456"
├── carrier: "FedEx"
├── trackingNumber: "1Z999AA..."
├── authorizationId: "auth-789"
├── captureId: "capt-101"
├── deliveredAt: "2026-02-21T14:50:00Z"
└── status: COMPLETED
```

## Guarantee Model

### Message Ordering Guarantee (FIFO)

**Mechanism:** Monotonic sequence numbers per (sourceAgent, sourceRegion) pair

```
Events from vendor-agent-001 (us-east1):
  1. seq=1: OrderCreatedEvent
  2. seq=2: OrderConfirmedEvent
  3. seq=3: OrderShippedEvent
  4. seq=4: OrderDeliveredEvent
  ✓ All processed in order

Out-of-order arrival:
  1. seq=1: OrderCreatedEvent (processed)
  2. seq=3: OrderShippedEvent (seq 3 > 1+1, gap detected, logged, accepted)
  3. seq=2: OrderConfirmedEvent (seq 2 < 3, OUT OF ORDER, rejected)
  ✓ Ordering enforced despite network reordering
```

### Idempotency Guarantee (At-Most-Once)

**Mechanism:** Immutable hash key + cache

```
First invocation:
  POST /marketplace/orders/create
  {
    "customer_id": "cust-123",
    "product_id": "prod-xyz",
    "timestamp_utc": "2026-02-21T14:30:00Z"
  }
  → idempotencyKey = hash("cust-123|prod-xyz|2026-02-21T14:30:00Z")
  → key NOT in cache
  → Create order-999
  → Cache[key] = { "order_id": "order-999", ... }
  → Return order-999

Duplicate invocation (same parameters, network retry):
  POST /marketplace/orders/create
  {
    "customer_id": "cust-123",
    "product_id": "prod-xyz",
    "timestamp_utc": "2026-02-21T14:30:00Z"
  }
  → idempotencyKey = hash("cust-123|prod-xyz|2026-02-21T14:30:00Z") [SAME]
  → key IS in cache
  → Return cached response { "order_id": "order-999", ... }
  ✓ SAME order ID, no duplicate case launched
```

### Data Consistency Guarantee

**Mechanism:** Transactional case updates via InterfaceB

```
Invariant: Case data reflects all processed events in sequence order

Before:
  ProcessOrder-42: { orderId: "order-123", status: "CREATED" }

Event: OrderShippedEvent (seq=2)
  ├─ updateCaseData() call via InterfaceB
  └─ Atomic update: add shipmentId, carrier, trackingNumber

After:
  ProcessOrder-42: {
    orderId: "order-123",
    status: "SHIPPED",
    shipmentId: "ship-456",
    carrier: "FedEx",
    trackingNumber: "1Z999AA..."
  }

✓ Update is atomic (all-or-nothing), no partial state
```

## Scalability Considerations

### Virtual Thread Performance

```
Traditional Threads:
  • 1 thread per A2A request
  • ~1MB memory per thread
  • OS scheduler overhead
  • Max ~1000 concurrent connections

Virtual Threads (Java 25):
  • Millions of light-weight threads possible
  • ~10KB memory per virtual thread
  • JVM scheduler (no OS overhead)
  • Support 10,000+ concurrent connections

Example:
  1,000 concurrent orders being processed
  ├─ Traditional: 1000 × 1MB = 1GB RAM
  └─ Virtual: 1000 × 10KB = 10MB RAM
```

### Idempotency Cache Scaling

```
Bounded Cache Strategy (recommended for production):

maxSize = 100,000 entries (idempotency keys)
expiration = 24 hours (after which duplicates OK)
memoryUsage = 100K × 300 bytes ≈ 30MB

If cache fills:
  1. Evict oldest entries (FIFO)
  2. Log eviction rate
  3. Alert if >90% full
```

### Sequence Tracker Scaling

```
Lightweight:
  • 1 counter per (sourceAgent, sourceRegion) pair
  • ~200 bytes per tracker
  • 1000 agents × 10 regions = 2,000 trackers
  • Memory: 2,000 × 200 bytes = 400KB

No performance impact.
```

## Failure Handling

### Network Failures

```
Scenario: Agent retries event after timeout

Event 1 (attempt 1): seq=1, idempotencyKey=ABC
  → Timeout → no response → agent retries

Event 1 (attempt 2): seq=1, idempotencyKey=ABC
  → Handler receives duplicate
  → Handler returns cached response
  → Agent: success ✓

Guarantee: No duplicate processing (idempotency)
```

### Out-of-Order Events

```
Scenario: Network packet reordering

Send: seq=1, seq=2, seq=3
Receive: seq=1, seq=3, seq=2

Processing:
  1. seq=1: OK (1 >= 0+1)
  2. seq=3: OK but gap (3 > 1+1) → log warning
  3. seq=2: FAIL (2 < 3) → reject

Agent must implement retry with backoff:
  Wait 5s → resend seq=2
  Handler: still out of order → reject
  Wait 10s → resend seq=2 again
  ...eventually: network reordering clears, seq=2 succeeds

Guarantee: Ordering validated, out-of-order explicitly rejected
```

### Database Failures

```
Scenario: InterfaceB connection fails during launchCase()

Attempt 1:
  launchCase("ProcessOrder", ...)
  → Connection timeout
  → Exception thrown

Handler catches exception:
  ├─ Does NOT cache response (no cached failure responses)
  ├─ Does NOT mark event processed
  └─ Returns error response to agent

Agent:
  ├─ Receives error
  ├─ Retries with same idempotencyKey after backoff
  └─ Eventually succeeds (or reaches max retries)

Guarantee: Transient failures don't corrupt idempotency (failures not cached)
```

---

**Architecture Status:** Production-ready with message ordering & idempotency guarantees.
