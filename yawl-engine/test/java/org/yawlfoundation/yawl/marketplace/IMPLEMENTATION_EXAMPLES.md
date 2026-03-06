# GCP Marketplace Test Suite - Implementation Examples

## Using the Test Scenarios

### Example 1: Creating a Happy Path Test

```java
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.elements.YSpecification;

@Test
void myMarketplaceTest() {
    // Arrange: Get test data
    YSpecification spec = GcpMarketplaceScenarios.createScenario1Specification();
    Map<String, Object> data = GcpMarketplaceScenarios.createScenario1Data();

    GcpMarketplaceScenarios.Order order =
        (GcpMarketplaceScenarios.Order) data.get("order");
    GcpMarketplaceScenarios.Vendor vendor =
        (GcpMarketplaceScenarios.Vendor) data.get("vendor");

    // Act: Simulate order progression
    // ... update order status ...

    // Assert: Verify state consistency
    assertTrue(GcpMarketplaceScenarios.validateOrderDataConsistency(order));
    assertEquals("DELIVERED", order.status());
}
```

---

## Test Data Factory Patterns

### Pattern 1: Creating Test Orders

```java
// Basic order creation
GcpMarketplaceScenarios.Order order = new GcpMarketplaceScenarios.Order(
    "order-12345",                              // orderId
    "customer-001",                             // customerId
    "vendor-acme-001",                          // vendorId
    Map.of("prod-001", 5),                      // productQuantities
    new BigDecimal("499.95"),                   // totalAmount
    "PENDING",                                  // status
    "PENDING",                                  // paymentStatus
    Instant.now(),                              // createdAt
    Instant.now()                               // lastModifiedAt
);

// Using factory method with pre-built data
Map<String, Object> scenario1 =
    GcpMarketplaceScenarios.createScenario1Data();
GcpMarketplaceScenarios.Order order =
    (GcpMarketplaceScenarios.Order) scenario1.get("order");
```

### Pattern 2: Order State Transitions

```java
// Transition order from PENDING to CONFIRMED
GcpMarketplaceScenarios.Order confirmed =
    new GcpMarketplaceScenarios.Order(
        order.orderId(),
        order.customerId(),
        order.vendorId(),
        order.productQuantities(),
        order.totalAmount(),
        "CONFIRMED",          // Updated status
        "PROCESSING",         // Updated payment status
        order.createdAt(),
        Instant.now()
    );

// Continue to FULFILLING
GcpMarketplaceScenarios.Order fulfilling =
    new GcpMarketplaceScenarios.Order(
        confirmed.orderId(),
        confirmed.customerId(),
        confirmed.vendorId(),
        confirmed.productQuantities(),
        confirmed.totalAmount(),
        "FULFILLING",         // Updated
        "COMPLETED",          // Updated
        confirmed.createdAt(),
        Instant.now()
    );
```

### Pattern 3: Creating Multi-Product Orders

```java
// Create products
GcpMarketplaceScenarios.Product product1 =
    new GcpMarketplaceScenarios.Product(
        "prod-001",
        "vendor-001",
        "Widget",
        "High-quality widget",
        new BigDecimal("99.99"),
        100,
        "ACTIVE",
        Instant.now()
    );

GcpMarketplaceScenarios.Product product2 =
    new GcpMarketplaceScenarios.Product(
        "prod-002",
        "vendor-001",
        "Gadget",
        "Advanced gadget",
        new BigDecimal("149.99"),
        50,
        "ACTIVE",
        Instant.now()
    );

// Create order with multiple products
Map<String, Integer> productQuantities = new HashMap<>();
productQuantities.put(product1.productId(), 3);  // 3 × $99.99
productQuantities.put(product2.productId(), 2);  // 2 × $149.99
// Total: $299.97 + $299.98 = $599.95

BigDecimal total =
    product1.price().multiply(new BigDecimal("3"))
    .add(product2.price().multiply(new BigDecimal("2")));

GcpMarketplaceScenarios.Order multiOrder =
    new GcpMarketplaceScenarios.Order(
        "order-multi-001",
        "customer-002",
        "vendor-001",
        productQuantities,
        total,
        "PENDING",
        "PENDING",
        Instant.now(),
        Instant.now()
    );
```

---

## Payment Processing Examples

### Example 1: Successful Payment

```java
// Initial payment
GcpMarketplaceScenarios.Payment initialPayment =
    new GcpMarketplaceScenarios.Payment(
        "pay-001",
        "order-12345",
        "customer-001",
        "vendor-acme-001",
        new BigDecimal("499.95"),
        "CREDIT_CARD",
        "INITIATED",
        null,
        Instant.now(),
        null
    );

// Payment processing completes
GcpMarketplaceScenarios.Payment completedPayment =
    new GcpMarketplaceScenarios.Payment(
        initialPayment.paymentId(),
        initialPayment.orderId(),
        initialPayment.customerId(),
        initialPayment.vendorId(),
        initialPayment.amount(),
        initialPayment.method(),
        "COMPLETED",      // Status updated
        null,             // No failure reason
        initialPayment.createdAt(),
        Instant.now()     // Completion timestamp
    );

// Verify payment matches order
assertEquals(order.totalAmount(), completedPayment.amount());
assertEquals("COMPLETED", completedPayment.status());
assertNull(completedPayment.failureReason());
```

### Example 2: Payment Failure + Retry

```java
// First payment fails
GcpMarketplaceScenarios.Payment failedPayment =
    new GcpMarketplaceScenarios.Payment(
        "pay-001",
        "order-12345",
        "customer-001",
        "vendor-acme-001",
        new BigDecimal("499.95"),
        "CREDIT_CARD",
        "FAILED",
        "DECLINED",       // Reason for failure
        Instant.now(),
        Instant.now().plusSeconds(5)
    );

// Retry with different method
GcpMarketplaceScenarios.Payment retryPayment =
    new GcpMarketplaceScenarios.Payment(
        "pay-001-retry",
        failedPayment.orderId(),
        failedPayment.customerId(),
        failedPayment.vendorId(),
        failedPayment.amount(),
        "ACH",            // Different method
        "COMPLETED",      // Now succeeds
        null,             // No failure
        Instant.now(),
        Instant.now().plusSeconds(5)
    );

// Assertions
assertEquals("FAILED", failedPayment.status());
assertEquals("DECLINED", failedPayment.failureReason());
assertEquals("COMPLETED", retryPayment.status());
assertNull(retryPayment.failureReason());
```

---

## Fulfillment Examples

### Example 1: Basic Fulfillment

```java
// Create fulfillment center
GcpMarketplaceScenarios.FulfillmentCenter center =
    new GcpMarketplaceScenarios.FulfillmentCenter(
        "center-us-west-001",
        "West Coast Warehouse",
        "us-west-1",
        "AVAILABLE",
        10000,     // Capacity
        2500,      // Current load
        Instant.now()
    );

// Start fulfillment
GcpMarketplaceScenarios.Fulfillment fulfillment =
    new GcpMarketplaceScenarios.Fulfillment(
        "fulfill-001",
        "order-12345",
        center.centerId(),
        "RECEIVED",    // Initial status
        Instant.now(),
        null           // Not completed yet
    );

// Progress through states
GcpMarketplaceScenarios.Fulfillment packing =
    new GcpMarketplaceScenarios.Fulfillment(
        fulfillment.fulfillmentId(),
        fulfillment.orderId(),
        fulfillment.fulfillmentCenterId(),
        "PACKING",
        fulfillment.startedAt(),
        null
    );

GcpMarketplaceScenarios.Fulfillment ready =
    new GcpMarketplaceScenarios.Fulfillment(
        fulfillment.fulfillmentId(),
        fulfillment.orderId(),
        fulfillment.fulfillmentCenterId(),
        "READY_SHIPMENT",
        fulfillment.startedAt(),
        null
    );

GcpMarketplaceScenarios.Fulfillment delivered =
    new GcpMarketplaceScenarios.Fulfillment(
        fulfillment.fulfillmentId(),
        fulfillment.orderId(),
        fulfillment.fulfillmentCenterId(),
        "DELIVERED",
        fulfillment.startedAt(),
        Instant.now()  // Completed
    );
```

### Example 2: Fulfillment Failover

```java
// Primary center becomes unavailable
GcpMarketplaceScenarios.FulfillmentCenter primary =
    new GcpMarketplaceScenarios.FulfillmentCenter(
        "center-primary",
        "Primary Warehouse",
        "us-west-1",
        "UNAVAILABLE",    // Status changed
        5000,
        0,
        Instant.now()
    );

// Fallback center available
GcpMarketplaceScenarios.FulfillmentCenter fallback =
    new GcpMarketplaceScenarios.FulfillmentCenter(
        "center-fallback",
        "Fallback Warehouse",
        "us-east-1",
        "AVAILABLE",
        8000,
        3000,
        Instant.now()
    );

// Order routed to fallback
GcpMarketplaceScenarios.Fulfillment routed =
    new GcpMarketplaceScenarios.Fulfillment(
        "fulfill-001",
        "order-12345",
        fallback.centerId(),  // Routed to fallback
        "RECEIVED",
        Instant.now(),
        null
    );

// Verify assertions
assertEquals("UNAVAILABLE", primary.status());
assertEquals("AVAILABLE", fallback.status());
assertEquals(fallback.centerId(), routed.fulfillmentCenterId());
```

---

## Inventory Management Examples

### Example 1: Stock Deduction

```java
// Initial product with stock
GcpMarketplaceScenarios.Product product =
    new GcpMarketplaceScenarios.Product(
        "prod-001",
        "vendor-001",
        "Widget",
        "Quality widget",
        new BigDecimal("99.99"),
        100,        // 100 units in stock
        "ACTIVE",
        Instant.now()
    );

// Customer orders 5 units
int orderedQuantity = 5;
int remainingStock = product.stockQuantity() - orderedQuantity;

// Updated product
GcpMarketplaceScenarios.Product updated =
    new GcpMarketplaceScenarios.Product(
        product.productId(),
        product.vendorId(),
        product.name(),
        product.description(),
        product.price(),
        remainingStock,    // 95 units remaining
        product.status(),
        product.listedAt()
    );

// Verify
assertEquals(100, product.stockQuantity());
assertEquals(95, updated.stockQuantity());
assertEquals(5, product.stockQuantity() - updated.stockQuantity());
```

### Example 2: Inventory Restoration on Cancellation

```java
// Order cancelled - restore inventory
int cancelledQuantity = 5;
int restoredStock = product.stockQuantity() + cancelledQuantity;

GcpMarketplaceScenarios.Product restored =
    new GcpMarketplaceScenarios.Product(
        product.productId(),
        product.vendorId(),
        product.name(),
        product.description(),
        product.price(),
        restoredStock,     // Restored to original
        product.status(),
        product.listedAt()
    );

// Verify restoration
assertEquals(95, product.stockQuantity());
assertEquals(100, restored.stockQuantity());
```

---

## Vendor Management Examples

### Example 1: Vendor Status Changes

```java
// Active vendor
GcpMarketplaceScenarios.Vendor active =
    new GcpMarketplaceScenarios.Vendor(
        "vendor-001",
        "ACME Corp",
        "supplier@acme.com",
        "ACTIVE",
        new BigDecimal("25000.00"),
        Instant.now().minusSeconds(86400)
    );

// Vendor suspended due to violation
GcpMarketplaceScenarios.Vendor suspended =
    new GcpMarketplaceScenarios.Vendor(
        active.vendorId(),
        active.name(),
        active.email(),
        "SUSPENDED",       // Status changed
        active.balance(),
        active.createdAt()
    );

// Verify status change
assertEquals("ACTIVE", active.accountStatus());
assertEquals("SUSPENDED", suspended.accountStatus());
assertEquals(active.vendorId(), suspended.vendorId());
```

### Example 2: Product Visibility Based on Vendor Status

```java
// Product when vendor is active
GcpMarketplaceScenarios.Product visible =
    new GcpMarketplaceScenarios.Product(
        "prod-001",
        active.vendorId(),
        "Widget",
        "Quality widget",
        new BigDecimal("99.99"),
        100,
        "ACTIVE",       // Visible
        Instant.now()
    );

// Product when vendor is suspended
GcpMarketplaceScenarios.Product hidden =
    new GcpMarketplaceScenarios.Product(
        visible.productId(),
        visible.vendorId(),
        visible.name(),
        visible.description(),
        visible.price(),
        visible.stockQuantity(),
        "HIDDEN",       // Hidden from search
        visible.listedAt()
    );

// Verify visibility change
assertEquals("ACTIVE", visible.status());
assertEquals("HIDDEN", hidden.status());
```

---

## Data Consistency Validation Examples

### Example 1: Order Total Validation

```java
// Calculate expected total
Map<String, BigDecimal> productPrices = new HashMap<>();
productPrices.put("prod-001", new BigDecimal("99.99"));
productPrices.put("prod-002", new BigDecimal("149.99"));

Map<String, Integer> quantities = new HashMap<>();
quantities.put("prod-001", 3);
quantities.put("prod-002", 2);

BigDecimal expectedTotal =
    GcpMarketplaceScenarios.calculateExpectedTotal(quantities, productPrices);

// Create order with this total
GcpMarketplaceScenarios.Order order =
    new GcpMarketplaceScenarios.Order(
        "order-001",
        "customer-001",
        "vendor-001",
        quantities,
        expectedTotal,  // Should be $599.95
        "PENDING",
        "PENDING",
        Instant.now(),
        Instant.now()
    );

// Verify consistency
assertEquals(new BigDecimal("599.95"), order.totalAmount());
assertTrue(
    GcpMarketplaceScenarios.validateOrderDataConsistency(order)
);
```

### Example 2: Payment Amount Validation

```java
// Create order
GcpMarketplaceScenarios.Order order = /* ... */;

// Create matching payment
GcpMarketplaceScenarios.Payment payment =
    new GcpMarketplaceScenarios.Payment(
        "pay-001",
        order.orderId(),
        "customer-001",
        "vendor-001",
        order.totalAmount(),  // Must match order total
        "CREDIT_CARD",
        "COMPLETED",
        null,
        Instant.now(),
        Instant.now()
    );

// Verify amounts match
assertEquals(order.totalAmount(), payment.amount());
```

---

## Concurrent Test Patterns

### Pattern 1: Using CountdownLatch for Synchronization

```java
int numThreads = 100;
CountDownLatch startGate = new CountDownLatch(1);
CountDownLatch endGate = new CountDownLatch(numThreads);
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

for (int i = 0; i < numThreads; i++) {
    final int orderId = i;
    executor.submit(() -> {
        try {
            // Wait for start signal
            startGate.await();

            // All threads start together
            GcpMarketplaceScenarios.Order order =
                createOrderWithId("order-" + orderId);

            // Process order
            processOrder(order);

        } finally {
            endGate.countDown();
        }
    });
}

// Signal all threads to start
startGate.countDown();

// Wait for all to complete
boolean completed = endGate.await(30, TimeUnit.SECONDS);
assertTrue(completed, "All orders should complete");
```

### Pattern 2: Measuring Latency Distribution

```java
List<Long> latencies = Collections.synchronizedList(
    new ArrayList<>()
);

// Measure per-order latency
long startTime = System.nanoTime();
GcpMarketplaceScenarios.Order order = createOrder();
long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
latencies.add(latencyMs);

// Calculate percentiles
latencies.sort(Long::compareTo);
long p50 = latencies.get(latencies.size() / 2);
long p99 = latencies.get((int)(latencies.size() * 0.99));

assertTrue(p50 < 1000, "p50 should be < 1000ms");
assertTrue(p99 < 5000, "p99 should be < 5000ms");
```

---

## Advanced Examples

### Example: Complete Order Lifecycle in Single Test

```java
@Test
void testCompleteOrderLifecycle() {
    // Arrange
    GcpMarketplaceScenarios.Vendor vendor =
        GcpMarketplaceScenarios.Vendor/* ... */;
    GcpMarketplaceScenarios.Product product = /* ... */;
    GcpMarketplaceScenarios.FulfillmentCenter center = /* ... */;

    GcpMarketplaceScenarios.Order pending =
        new GcpMarketplaceScenarios.Order(
            "order-lifecycle-001",
            "customer-001",
            vendor.vendorId(),
            Map.of(product.productId(), 5),
            new BigDecimal("499.95"),
            "PENDING",
            "PENDING",
            Instant.now(),
            Instant.now()
        );

    // Act & Assert - Validate PENDING state
    assertTrue(
        GcpMarketplaceScenarios.validateOrderDataConsistency(pending)
    );
    assertEquals("PENDING", pending.status());

    // Transition to CONFIRMED
    GcpMarketplaceScenarios.Order confirmed =
        transitionOrder(pending, "CONFIRMED", "PROCESSING");
    assertEquals("CONFIRMED", confirmed.status());

    // Transition to FULFILLING
    GcpMarketplaceScenarios.Order fulfilling =
        transitionOrder(confirmed, "FULFILLING", "COMPLETED");
    assertEquals("FULFILLING", fulfilling.status());

    // Transition to DELIVERED
    GcpMarketplaceScenarios.Order delivered =
        transitionOrder(fulfilling, "DELIVERED", "COMPLETED");
    assertEquals("DELIVERED", delivered.status());

    // Final validation
    assertTrue(
        GcpMarketplaceScenarios.validateOrderDataConsistency(delivered)
    );
}

private GcpMarketplaceScenarios.Order transitionOrder(
        GcpMarketplaceScenarios.Order current,
        String newStatus,
        String newPaymentStatus) {
    return new GcpMarketplaceScenarios.Order(
        current.orderId(),
        current.customerId(),
        current.vendorId(),
        current.productQuantities(),
        current.totalAmount(),
        newStatus,
        newPaymentStatus,
        current.createdAt(),
        Instant.now()
    );
}
```

---

## Best Practices

### ✓ DO

- Use factory methods for test data
- Verify state consistency after each transition
- Use records for immutability
- Create new instances for state changes
- Verify payment = order total
- Test both happy path and exceptions
- Use virtual threads for concurrent tests

### ✗ DON'T

- Mutate test data directly
- Assume state without verification
- Use mocks for core domain objects
- Skip consistency checks
- Mix payments and orders without matching
- Ignore race conditions
- Use old-style thread pools (use virtual threads)

---

## Common Patterns Summary

| Pattern | Use Case | Example |
|---------|----------|---------|
| Record Creation | Immutable data | `new Order(...)` |
| State Transition | Update status | Create new order with updated status |
| Factory Method | Test data setup | `createScenario1Data()` |
| Consistency Check | Verify invariants | `validateOrderDataConsistency()` |
| Concurrent Execution | Scale testing | `CountdownLatch` + virtual threads |
| Latency Measurement | Performance | `System.nanoTime()` |

---

**This guide provides practical examples for using the GCP Marketplace test suite. For more details, see the comprehensive documentation files.**
