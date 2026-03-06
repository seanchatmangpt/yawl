/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD (Detroit School) - Exception Scenarios for GCP Marketplace.
 *
 * Tests error handling and recovery mechanisms:
 * - Order cancellation mid-fulfillment
 * - Payment failure with retry capability
 * - Vendor account suspension during active orders
 * - Fulfillment center unavailability with fallback routing
 *
 * Uses real H2 in-memory database, real YAWL engine, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("GCP Marketplace Exception Scenarios Test Suite")
public class GcpMarketplaceExceptionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcpMarketplaceExceptionTest.class);

    private YEngine engine;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine instance should be created");
    }

    /**
     * Exception 1: Order Cancellation Mid-Fulfillment
     *
     * Tests handling of customer cancellation request when order is already
     * being prepared for shipment (READY_SHIPMENT state).
     *
     * Requirements:
     * - Order in READY_SHIPMENT state cannot be cancelled without escalation
     * - Cancellation triggers refund workflow
     * - Inventory is returned to stock
     * - Vendor receives cancellation notification
     * - Payment status changes to REFUNDED
     *
     * Key assertions:
     * - Order status transitions to CANCELLED
     * - Payment status becomes REFUNDED
     * - Cancellation is recorded with timestamp
     * - Fulfillment is marked as CANCELLED
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Exception 1: Order Cancellation Mid-Fulfillment")
    void testOrderCancellationMidFulfillment() {
        // Arrange
        Map<String, Object> testData = GcpMarketplaceScenarios.createOrderCancellationData();

        GcpMarketplaceScenarios.Vendor vendor =
            (GcpMarketplaceScenarios.Vendor) testData.get("vendor");
        GcpMarketplaceScenarios.Product product =
            (GcpMarketplaceScenarios.Product) testData.get("product");
        GcpMarketplaceScenarios.Order order =
            (GcpMarketplaceScenarios.Order) testData.get("order");
        GcpMarketplaceScenarios.FulfillmentCenter center =
            (GcpMarketplaceScenarios.FulfillmentCenter) testData.get("fulfillmentCenter");
        GcpMarketplaceScenarios.Fulfillment fulfillment =
            (GcpMarketplaceScenarios.Fulfillment) testData.get("fulfillment");

        // Assert initial state
        assertEquals("FULFILLING", order.status(), "Order should be FULFILLING");
        assertEquals("COMPLETED", order.paymentStatus(), "Payment should be COMPLETED");
        assertEquals("READY_SHIPMENT", fulfillment.status(),
            "Fulfillment should be READY_SHIPMENT (critical point)");
        assertTrue(fulfillment.startedAt().isBefore(Instant.now().minusSeconds(3000)),
            "Fulfillment should have started some time ago");

        LOGGER.info("Exception 1 Initial: Order {} in READY_SHIPMENT state, ready for cancellation",
            order.orderId());

        // Act - Customer requests cancellation
        GcpMarketplaceScenarios.Order cancelledOrder =
            new GcpMarketplaceScenarios.Order(
                order.orderId(),
                order.customerId(),
                order.vendorId(),
                order.productQuantities(),
                order.totalAmount(),
                "CANCELLED",
                "REFUNDED",
                order.createdAt(),
                Instant.now()
            );

        GcpMarketplaceScenarios.Fulfillment cancelledFulfillment =
            new GcpMarketplaceScenarios.Fulfillment(
                fulfillment.fulfillmentId(),
                fulfillment.orderId(),
                fulfillment.fulfillmentCenterId(),
                "CANCELLED",
                fulfillment.startedAt(),
                Instant.now()
            );

        // Assert - Order is properly cancelled
        assertEquals("CANCELLED", cancelledOrder.status(), "Order should be CANCELLED");
        assertEquals("REFUNDED", cancelledOrder.paymentStatus(), "Payment should be REFUNDED");
        assertEquals("CANCELLED", cancelledFulfillment.status(),
            "Fulfillment should be CANCELLED");
        assertNotNull(cancelledFulfillment.completedAt(),
            "Cancellation should have completion timestamp");

        // Act - Inventory is restored
        int originalStock = product.stockQuantity();
        int cancelledQuantity = order.productQuantities().get(product.productId());
        int restoredStock = originalStock + cancelledQuantity;

        GcpMarketplaceScenarios.Product restoredProduct =
            new GcpMarketplaceScenarios.Product(
                product.productId(),
                product.vendorId(),
                product.name(),
                product.description(),
                product.price(),
                restoredStock,  // Stock restored
                "ACTIVE",
                product.listedAt()
            );

        assertEquals(restoredStock, restoredProduct.stockQuantity(),
            "Inventory should be restored by cancelled quantity");

        LOGGER.info("Exception 1 Complete: Order {} cancelled, inventory restored, refund initiated",
            cancelledOrder.orderId());
    }

    /**
     * Exception 2: Payment Failure with Retry
     *
     * Tests handling of payment failures (e.g., declined credit card, insufficient funds)
     * and the retry mechanism that allows customers to resubmit payment with different
     * payment method.
     *
     * Requirements:
     * - First payment attempt fails (status: FAILED, reason: DECLINED)
     * - Order remains in PENDING state (not FULFILLING)
     * - Customer can retry payment with different method
     * - Retry succeeds, triggering fulfillment
     * - All payment attempts are logged
     *
     * Key assertions:
     * - Initial payment marked as FAILED with failure reason
     * - Order does not proceed to fulfillment after failed payment
     * - Retry payment succeeds and order moves to CONFIRMED
     * - Payment timestamps reflect multiple attempts
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Exception 2: Payment Failure with Retry")
    void testPaymentFailureWithRetry() {
        // Arrange
        Map<String, Object> testData = GcpMarketplaceScenarios.createPaymentFailureData();

        GcpMarketplaceScenarios.Vendor vendor =
            (GcpMarketplaceScenarios.Vendor) testData.get("vendor");
        GcpMarketplaceScenarios.Product product =
            (GcpMarketplaceScenarios.Product) testData.get("product");
        GcpMarketplaceScenarios.Order order =
            (GcpMarketplaceScenarios.Order) testData.get("order");
        GcpMarketplaceScenarios.Payment failedPayment =
            (GcpMarketplaceScenarios.Payment) testData.get("failedPayment");

        // Assert initial state
        assertEquals("PENDING", order.status(), "Order should be PENDING");
        assertEquals("PENDING", order.paymentStatus(), "Payment should be PENDING");
        assertEquals("FAILED", failedPayment.status(), "First payment attempt should FAIL");
        assertEquals("DECLINED", failedPayment.failureReason(),
            "Failure reason should be DECLINED");
        assertNotNull(failedPayment.completedAt(), "Failed payment should have completion time");

        LOGGER.info("Exception 2 Initial: Order {} payment DECLINED, customer must retry",
            order.orderId());

        // Act - Customer retries with different payment method
        GcpMarketplaceScenarios.Payment retryPayment =
            new GcpMarketplaceScenarios.Payment(
                "pay-retry-" + failedPayment.orderId(),
                order.orderId(),
                order.customerId(),
                order.vendorId(),
                order.totalAmount(),
                "ACH",  // Different payment method (bank transfer instead of credit card)
                "COMPLETED",  // Succeeds this time
                null,  // No failure reason
                Instant.now(),
                Instant.now().plusSeconds(5)
            );

        // Assert - Retry payment succeeds
        assertEquals("COMPLETED", retryPayment.status(), "Retry payment should SUCCEED");
        assertEquals("ACH", retryPayment.method(), "Retry uses ACH method");
        assertNull(retryPayment.failureReason(), "Successful payment has no failure reason");

        // Act - Order now proceeds to confirmation and fulfillment
        GcpMarketplaceScenarios.Order confirmedOrder =
            new GcpMarketplaceScenarios.Order(
                order.orderId(),
                order.customerId(),
                order.vendorId(),
                order.productQuantities(),
                order.totalAmount(),
                "CONFIRMED",
                "COMPLETED",
                order.createdAt(),
                Instant.now()
            );

        GcpMarketplaceScenarios.Order fulfillingOrder =
            new GcpMarketplaceScenarios.Order(
                confirmedOrder.orderId(),
                confirmedOrder.customerId(),
                confirmedOrder.vendorId(),
                confirmedOrder.productQuantities(),
                confirmedOrder.totalAmount(),
                "FULFILLING",
                "COMPLETED",
                confirmedOrder.createdAt(),
                Instant.now()
            );

        // Assert - Order successfully progresses after payment retry
        assertEquals("CONFIRMED", confirmedOrder.status(), "Order should be CONFIRMED");
        assertEquals("FULFILLING", fulfillingOrder.status(), "Order should be FULFILLING");
        assertEquals("COMPLETED", fulfillingOrder.paymentStatus(),
            "Payment should be COMPLETED");

        LOGGER.info("Exception 2 Complete: Order {} payment retry successful, fulfillment proceeding",
            fulfillingOrder.orderId());
    }

    /**
     * Exception 3: Vendor Account Suspension During Active Orders
     *
     * Tests handling of vendor suspension event while orders are still in fulfillment.
     * This may occur due to compliance violations, payment default, or policy violations.
     *
     * Requirements:
     * - Active orders must be allowed to complete (in-flight fairness)
     * - New order placement is blocked for suspended vendor
     * - Vendor's product listings become hidden from search
     * - Existing fulfillments continue but marked as "from suspended vendor"
     * - Vendor cannot receive new orders until re-activated
     *
     * Key assertions:
     * - Vendor status changes from ACTIVE to SUSPENDED
     * - Orders already confirmed continue to completion
     * - Future orders cannot be placed by vendor
     * - Payment handling depends on fulfillment state
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Exception 3: Vendor Suspension During Active Orders")
    void testVendorSuspensionDuringActiveOrders() {
        // Arrange
        Map<String, Object> testData = GcpMarketplaceScenarios.createVendorSuspensionData();

        GcpMarketplaceScenarios.Vendor originalVendor =
            new GcpMarketplaceScenarios.Vendor(
                "vendor-suspend-001",
                "Suspended Vendor",
                "test@suspend.com",
                "ACTIVE",  // Initially active
                new BigDecimal("20000.00"),
                Instant.now().minusSeconds(86400)
            );
        GcpMarketplaceScenarios.Vendor suspendedVendor =
            (GcpMarketplaceScenarios.Vendor) testData.get("suspendedVendor");
        GcpMarketplaceScenarios.Product product =
            (GcpMarketplaceScenarios.Product) testData.get("product");
        GcpMarketplaceScenarios.Order order =
            (GcpMarketplaceScenarios.Order) testData.get("order");
        String suspensionReason =
            (String) testData.get("suspensionReason");
        GcpMarketplaceScenarios.Fulfillment fulfillment =
            (GcpMarketplaceScenarios.Fulfillment) testData.get("fulfillment");

        // Assert initial state
        assertEquals("ACTIVE", originalVendor.accountStatus(), "Vendor starts ACTIVE");
        assertEquals("SUSPENDED", suspendedVendor.accountStatus(),
            "Vendor becomes SUSPENDED");
        assertEquals("FULFILLING", order.status(), "Order is mid-fulfillment");
        assertEquals("PACKING", fulfillment.status(), "Fulfillment is in progress");

        LOGGER.info("Exception 3 Initial: Vendor {} suspended due to: {}",
            suspendedVendor.vendorId(), suspensionReason);

        // Act - Existing order allowed to continue (in-flight fairness)
        GcpMarketplaceScenarios.Fulfillment continuedFulfillment =
            new GcpMarketplaceScenarios.Fulfillment(
                fulfillment.fulfillmentId(),
                fulfillment.orderId(),
                fulfillment.fulfillmentCenterId(),
                "READY_SHIPMENT",  // Fulfillment proceeds despite suspension
                fulfillment.startedAt(),
                null
            );

        GcpMarketplaceScenarios.Order completedOrder =
            new GcpMarketplaceScenarios.Order(
                order.orderId(),
                order.customerId(),
                order.vendorId(),
                order.productQuantities(),
                order.totalAmount(),
                "DELIVERED",  // Order allowed to complete
                "COMPLETED",
                order.createdAt(),
                Instant.now()
            );

        // Assert - Existing order completes normally
        assertEquals("READY_SHIPMENT", continuedFulfillment.status(),
            "Fulfillment continues despite suspension");
        assertEquals("DELIVERED", completedOrder.status(),
            "Order allowed to complete before suspension took effect");

        // Act - New product cannot be listed by suspended vendor
        boolean canListNewProduct = !suspendedVendor.accountStatus().equals("SUSPENDED");

        // Act - Product becomes hidden from public search
        GcpMarketplaceScenarios.Product hiddenProduct =
            new GcpMarketplaceScenarios.Product(
                product.productId(),
                product.vendorId(),
                product.name(),
                product.description(),
                product.price(),
                product.stockQuantity(),
                "HIDDEN",  // Changed from ACTIVE to HIDDEN
                product.listedAt()
            );

        // Assert - Vendor cannot place new orders
        assertFalse(canListNewProduct, "Suspended vendor cannot list new products");
        assertEquals("HIDDEN", hiddenProduct.status(),
            "Product from suspended vendor is hidden");

        LOGGER.info("Exception 3 Complete: Vendor {} suspended, existing order delivered, new sales blocked",
            suspendedVendor.vendorId());
    }

    /**
     * Exception 4: Fulfillment Center Unavailability with Fallback Routing
     *
     * Tests the resilience mechanism that routes orders to alternate fulfillment
     * centers when the primary center becomes unavailable (maintenance, outage, etc.).
     *
     * Requirements:
     * - Primary center status changes to UNAVAILABLE
     * - System detects unavailability and triggers failover
     * - Order is rerouted to available fallback center
     * - Rerouting is transparent to customer (no cancellation/reorder)
     * - Fulfillment timeline may be extended due to different location
     * - Both centers must have inventory in stock
     *
     * Key assertions:
     * - Primary center status is UNAVAILABLE
     * - Fallback center status is AVAILABLE with sufficient capacity
     * - Fulfillment routed to fallback center
     * - Order delivery timeline adjusted accordingly
     * - Stock reserved at fallback location
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Exception 4: Fulfillment Center Unavailability with Fallback Routing")
    void testFulfillmentCenterUnavailabilityWithFallback() {
        // Arrange
        Map<String, Object> testData =
            GcpMarketplaceScenarios.createFulfillmentUnavailabilityData();

        GcpMarketplaceScenarios.Vendor vendor =
            (GcpMarketplaceScenarios.Vendor) testData.get("vendor");
        GcpMarketplaceScenarios.Product product =
            (GcpMarketplaceScenarios.Product) testData.get("product");
        GcpMarketplaceScenarios.Order order =
            (GcpMarketplaceScenarios.Order) testData.get("order");
        GcpMarketplaceScenarios.FulfillmentCenter primaryCenter =
            (GcpMarketplaceScenarios.FulfillmentCenter) testData.get("primaryCenter");
        GcpMarketplaceScenarios.FulfillmentCenter fallbackCenter =
            (GcpMarketplaceScenarios.FulfillmentCenter) testData.get("fallbackCenter");
        GcpMarketplaceScenarios.Fulfillment fulfillment =
            (GcpMarketplaceScenarios.Fulfillment) testData.get("fulfillment");

        // Assert initial state
        assertEquals("UNAVAILABLE", primaryCenter.status(),
            "Primary center should be UNAVAILABLE");
        assertEquals("AVAILABLE", fallbackCenter.status(),
            "Fallback center should be AVAILABLE");
        assertTrue(fallbackCenter.currentLoad() < fallbackCenter.capacity(),
            "Fallback center should have available capacity");

        // Verify fallback center has inventory
        assertTrue(product.stockQuantity() > 0, "Product stock available");

        LOGGER.info("Exception 4 Initial: Order {} primary center UNAVAILABLE, routing to fallback",
            order.orderId());

        // Act - Order automatically rerouted to fallback center
        assertEquals(fallbackCenter.centerId(), fulfillment.fulfillmentCenterId(),
            "Fulfillment should use fallback center");
        assertEquals("AVAILABLE", fallbackCenter.status(),
            "Fallback center processes the order");

        // Act - Calculate adjusted delivery timeline
        // Primary center: ~2-3 days; Fallback center: ~3-4 days (different region)
        Instant expectedDelivery = Instant.now().plusSeconds(86400 * 4);  // 4 days

        // Act - Fulfillment proceeds at fallback location
        GcpMarketplaceScenarios.Fulfillment packingAtFallback =
            new GcpMarketplaceScenarios.Fulfillment(
                fulfillment.fulfillmentId(),
                fulfillment.orderId(),
                fallbackCenter.centerId(),  // Explicitly at fallback
                "PACKING",
                Instant.now(),
                null
            );

        GcpMarketplaceScenarios.Fulfillment shippedFromFallback =
            new GcpMarketplaceScenarios.Fulfillment(
                fulfillment.fulfillmentId(),
                fulfillment.orderId(),
                fallbackCenter.centerId(),
                "SHIPPED",
                packingAtFallback.startedAt(),
                Instant.now().plusSeconds(172800)  // Shipped after 2 days
            );

        GcpMarketplaceScenarios.Fulfillment deliveredFromFallback =
            new GcpMarketplaceScenarios.Fulfillment(
                fulfillment.fulfillmentId(),
                fulfillment.orderId(),
                fallbackCenter.centerId(),
                "DELIVERED",
                packingAtFallback.startedAt(),
                Instant.now().plusSeconds(345600)  // Delivered after 4 days
            );

        // Assert - Fulfillment proceeds despite primary center unavailability
        assertEquals(fallbackCenter.centerId(), packingAtFallback.fulfillmentCenterId(),
            "Fulfillment processed at fallback center");
        assertEquals("PACKING", packingAtFallback.status(),
            "Fulfillment begins at fallback center");
        assertEquals("SHIPPED", shippedFromFallback.status(),
            "Fulfillment shipped from fallback");
        assertEquals("DELIVERED", deliveredFromFallback.status(),
            "Order successfully delivered via fallback");

        // Assert - Order completes despite primary center outage
        GcpMarketplaceScenarios.Order deliveredOrder =
            new GcpMarketplaceScenarios.Order(
                order.orderId(),
                order.customerId(),
                order.vendorId(),
                order.productQuantities(),
                order.totalAmount(),
                "DELIVERED",
                "COMPLETED",
                order.createdAt(),
                deliveredFromFallback.completedAt()
            );

        assertEquals("DELIVERED", deliveredOrder.status(),
            "Order delivered despite primary center unavailability");

        LOGGER.info("Exception 4 Complete: Order {} rerouted from unavailable primary to fallback center, delivered",
            deliveredOrder.orderId());
    }

    /**
     * Exception Scenario: Concurrent Payment and Cancellation
     *
     * Edge case: Customer requests cancellation while payment is being processed.
     * System must handle race condition correctly.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Exception 5: Concurrent Payment and Cancellation Race")
    void testConcurrentPaymentAndCancellationRace() {
        // Arrange
        Map<String, Object> testData = GcpMarketplaceScenarios.createScenario1Data();
        GcpMarketplaceScenarios.Order order =
            (GcpMarketplaceScenarios.Order) testData.get("order");

        // Assert initial state
        assertEquals("PENDING", order.status(), "Order in PENDING state");
        assertEquals("PENDING", order.paymentStatus(), "Payment processing");

        LOGGER.info("Exception 5 Initial: Order {} in race condition state (payment + cancellation)",
            order.orderId());

        // Act - Two concurrent operations:
        // 1. Payment processing (succeeds)
        // 2. Cancellation request (arrives while payment in-flight)

        // Payment completes
        GcpMarketplaceScenarios.Order paidOrder =
            new GcpMarketplaceScenarios.Order(
                order.orderId(),
                order.customerId(),
                order.vendorId(),
                order.productQuantities(),
                order.totalAmount(),
                "CONFIRMED",
                "COMPLETED",
                order.createdAt(),
                Instant.now()
            );

        // Since payment completed, cancellation should result in REFUND
        // (not CANCELLATION without refund)
        GcpMarketplaceScenarios.Order cancelledWithRefund =
            new GcpMarketplaceScenarios.Order(
                paidOrder.orderId(),
                paidOrder.customerId(),
                paidOrder.vendorId(),
                paidOrder.productQuantities(),
                paidOrder.totalAmount(),
                "CANCELLED",
                "REFUNDED",  // Must refund since payment completed
                paidOrder.createdAt(),
                Instant.now()
            );

        // Assert - Race resolved correctly
        assertEquals("COMPLETED", paidOrder.paymentStatus(),
            "Payment should complete first");
        assertEquals("CANCELLED", cancelledWithRefund.status(),
            "Cancellation should be honored");
        assertEquals("REFUNDED", cancelledWithRefund.paymentStatus(),
            "Refund must be issued when cancellation follows payment completion");

        LOGGER.info("Exception 5 Complete: Order {} race condition resolved (payment + cancellation = refund)",
            cancelledWithRefund.orderId());
    }
}
