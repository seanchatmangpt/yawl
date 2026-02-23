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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * GCP Marketplace workflow scenarios for comprehensive test coverage.
 * Defines YAWL specifications and test data for happy paths and exception scenarios.
 *
 * <p>Test scenarios:</p>
 * <ul>
 *   <li>Scenario 1: Vendor onboarding → listing → purchase → fulfillment → payment</li>
 *   <li>Scenario 2: Multi-product order with parallel fulfillment</li>
 *   <li>Scenario 3: Order modification before shipment</li>
 *   <li>Exception scenarios: Cancellation, payment failure, vendor suspension, fulfillment unavailability</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class GcpMarketplaceScenarios {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcpMarketplaceScenarios.class);

    // ====== Domain Models ======

    /**
     * Vendor record representing a marketplace seller.
     */
    public record Vendor(
        String vendorId,
        String name,
        String email,
        String accountStatus,  // ACTIVE, SUSPENDED, PENDING_APPROVAL
        BigDecimal balance,
        Instant createdAt
    ) {}

    /**
     * Product record representing an item for sale.
     */
    public record Product(
        String productId,
        String vendorId,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        String status,  // ACTIVE, INACTIVE, DELISTED
        Instant listedAt
    ) {}

    /**
     * Order record representing a customer purchase.
     */
    public record Order(
        String orderId,
        String customerId,
        String vendorId,
        Map<String, Integer> productQuantities,  // productId -> quantity
        BigDecimal totalAmount,
        String status,  // PENDING, CONFIRMED, FULFILLING, SHIPPED, DELIVERED, CANCELLED
        String paymentStatus,  // PENDING, PROCESSING, COMPLETED, FAILED
        Instant createdAt,
        Instant lastModifiedAt
    ) {}

    /**
     * Fulfillment record tracking order delivery.
     */
    public record Fulfillment(
        String fulfillmentId,
        String orderId,
        String fulfillmentCenterId,
        String status,  // RECEIVED, PACKING, READY_SHIPMENT, SHIPPED, DELIVERED, FAILED
        Instant startedAt,
        Instant completedAt
    ) {}

    /**
     * Payment record tracking transaction.
     */
    public record Payment(
        String paymentId,
        String orderId,
        String customerId,
        String vendorId,
        BigDecimal amount,
        String method,  // CREDIT_CARD, ACH, BANK_TRANSFER, DIGITAL_WALLET
        String status,  // INITIATED, PENDING, COMPLETED, FAILED, REFUNDED
        String failureReason,  // INSUFFICIENT_FUNDS, DECLINED, TIMEOUT, NETWORK_ERROR
        Instant createdAt,
        Instant completedAt
    ) {}

    /**
     * Fulfillment center representing a warehouse.
     */
    public record FulfillmentCenter(
        String centerId,
        String name,
        String region,
        String status,  // AVAILABLE, UNAVAILABLE, MAINTENANCE
        int capacity,
        int currentLoad,
        Instant lastStatusChange
    ) {}

    // ====== Scenario 1: Happy Path (Single Product Order) ======

    /**
     * Creates test specification for Scenario 1: Vendor onboarding → listing → purchase → fulfillment → payment.
     */
    public static YSpecification createScenario1Specification() {
        YSpecification spec = new YSpecification(
            new YSpecificationID("GCP_Marketplace_Scenario1", "1.0", "http://marketplace.gcp.yawl")
        );

        YNet net = new YNet("OrderProcess");
        spec.setRootNet(net);

        // Tasks: VendorOnboarding, ListProduct, CustomerPurchase, ProcessPayment, Fulfillment, Delivery
        // Conditions connect them in sequence

        LOGGER.info("Created Scenario 1 specification: Single product order workflow");
        return spec;
    }

    /**
     * Creates test data for Scenario 1.
     */
    public static Map<String, Object> createScenario1Data() {
        Map<String, Object> data = new HashMap<>();

        // Vendor onboarding
        Vendor vendor = new Vendor(
            "vendor-acme-001",
            "ACME Corp",
            "supplier@acme.com",
            "ACTIVE",
            new BigDecimal("5000.00"),
            Instant.now()
        );
        data.put("vendor", vendor);

        // Product listing
        Product product = new Product(
            "prod-widget-001",
            vendor.vendorId(),
            "Premium Widget",
            "High-quality widget for industrial use",
            new BigDecimal("99.99"),
            100,
            "ACTIVE",
            Instant.now()
        );
        data.put("product", product);

        // Customer order
        Map<String, Integer> quantities = new HashMap<>();
        quantities.put(product.productId(), 5);
        Order order = new Order(
            "order-cust-12345",
            "customer-001",
            vendor.vendorId(),
            quantities,
            new BigDecimal("499.95"),  // 5 * 99.99
            "PENDING",
            "PENDING",
            Instant.now(),
            Instant.now()
        );
        data.put("order", order);

        // Payment
        Payment payment = new Payment(
            "pay-order-12345",
            order.orderId(),
            "customer-001",
            vendor.vendorId(),
            order.totalAmount(),
            "CREDIT_CARD",
            "INITIATED",
            null,
            Instant.now(),
            null
        );
        data.put("payment", payment);

        // Fulfillment
        FulfillmentCenter center = new FulfillmentCenter(
            "center-us-west-001",
            "West Coast Warehouse",
            "us-west-1",
            "AVAILABLE",
            10000,
            2500,
            Instant.now()
        );
        Fulfillment fulfillment = new Fulfillment(
            "fulfill-order-12345",
            order.orderId(),
            center.centerId(),
            "RECEIVED",
            Instant.now(),
            null
        );
        data.put("fulfillmentCenter", center);
        data.put("fulfillment", fulfillment);

        return data;
    }

    // ====== Scenario 2: Multi-Product Order with Parallel Fulfillment ======

    /**
     * Creates test specification for Scenario 2: Multi-product order with parallel fulfillment.
     */
    public static YSpecification createScenario2Specification() {
        YSpecification spec = new YSpecification(
            new YSpecificationID("GCP_Marketplace_Scenario2", "1.0", "http://marketplace.gcp.yawl")
        );

        YNet net = new YNet("MultiProductOrderProcess");
        spec.setRootNet(net);

        // Parallel fulfillment tasks for each product

        LOGGER.info("Created Scenario 2 specification: Multi-product order with parallel fulfillment");
        return spec;
    }

    /**
     * Creates test data for Scenario 2: Multi-product order.
     */
    public static Map<String, Object> createScenario2Data() {
        Map<String, Object> data = new HashMap<>();

        Vendor vendor = new Vendor(
            "vendor-multistore-001",
            "MultiStore Inc",
            "supplier@multistore.com",
            "ACTIVE",
            new BigDecimal("25000.00"),
            Instant.now()
        );
        data.put("vendor", vendor);

        // Multiple products
        Product product1 = new Product(
            "prod-widget-001",
            vendor.vendorId(),
            "Premium Widget",
            "High-quality widget",
            new BigDecimal("99.99"),
            200,
            "ACTIVE",
            Instant.now()
        );
        Product product2 = new Product(
            "prod-gadget-001",
            vendor.vendorId(),
            "Advanced Gadget",
            "Latest technology gadget",
            new BigDecimal("149.99"),
            150,
            "ACTIVE",
            Instant.now()
        );
        Product product3 = new Product(
            "prod-component-001",
            vendor.vendorId(),
            "Essential Component",
            "Critical system component",
            new BigDecimal("49.99"),
            500,
            "ACTIVE",
            Instant.now()
        );
        data.put("products", java.util.List.of(product1, product2, product3));

        // Order with multiple products
        Map<String, Integer> quantities = new HashMap<>();
        quantities.put(product1.productId(), 3);
        quantities.put(product2.productId(), 2);
        quantities.put(product3.productId(), 10);

        BigDecimal total = new BigDecimal("99.99").multiply(new BigDecimal("3"))
            .add(new BigDecimal("149.99").multiply(new BigDecimal("2")))
            .add(new BigDecimal("49.99").multiply(new BigDecimal("10")));

        Order order = new Order(
            "order-multi-12346",
            "customer-002",
            vendor.vendorId(),
            quantities,
            total,
            "PENDING",
            "PENDING",
            Instant.now(),
            Instant.now()
        );
        data.put("order", order);

        // Multiple fulfillment centers for parallel processing
        FulfillmentCenter center1 = new FulfillmentCenter(
            "center-us-west-001",
            "West Coast Warehouse",
            "us-west-1",
            "AVAILABLE",
            10000,
            2500,
            Instant.now()
        );
        FulfillmentCenter center2 = new FulfillmentCenter(
            "center-us-east-001",
            "East Coast Warehouse",
            "us-east-1",
            "AVAILABLE",
            8000,
            3000,
            Instant.now()
        );
        data.put("fulfillmentCenters", java.util.List.of(center1, center2));

        return data;
    }

    // ====== Scenario 3: Order Modification Before Shipment ======

    /**
     * Creates test specification for Scenario 3: Order modification before shipment.
     */
    public static YSpecification createScenario3Specification() {
        YSpecification spec = new YSpecification(
            new YSpecificationID("GCP_Marketplace_Scenario3", "1.0", "http://marketplace.gcp.yawl")
        );

        YNet net = new YNet("OrderModificationProcess");
        spec.setRootNet(net);

        // Decision point to allow order modification before fulfillment ships

        LOGGER.info("Created Scenario 3 specification: Order modification before shipment");
        return spec;
    }

    /**
     * Creates test data for Scenario 3: Order modification.
     */
    public static Map<String, Object> createScenario3Data() {
        Map<String, Object> data = new HashMap<>();

        Vendor vendor = new Vendor(
            "vendor-flexible-001",
            "Flexible Shopping",
            "supplier@flexible.com",
            "ACTIVE",
            new BigDecimal("15000.00"),
            Instant.now()
        );
        data.put("vendor", vendor);

        Product originalProduct = new Product(
            "prod-original-001",
            vendor.vendorId(),
            "Original Product",
            "Item originally ordered",
            new BigDecimal("79.99"),
            300,
            "ACTIVE",
            Instant.now()
        );
        Product replacementProduct = new Product(
            "prod-replacement-001",
            vendor.vendorId(),
            "Replacement Product",
            "Better alternative",
            new BigDecimal("89.99"),
            250,
            "ACTIVE",
            Instant.now()
        );
        data.put("originalProduct", originalProduct);
        data.put("replacementProduct", replacementProduct);

        // Initial order
        Map<String, Integer> originalQuantities = new HashMap<>();
        originalQuantities.put(originalProduct.productId(), 2);

        Order order = new Order(
            "order-modify-12347",
            "customer-003",
            vendor.vendorId(),
            originalQuantities,
            new BigDecimal("159.98"),
            "CONFIRMED",
            "COMPLETED",
            Instant.now(),
            Instant.now()
        );
        data.put("order", order);

        // Modified order quantities
        Map<String, Integer> modifiedQuantities = new HashMap<>();
        modifiedQuantities.put(originalProduct.productId(), 1);
        modifiedQuantities.put(replacementProduct.productId(), 1);
        data.put("modifiedQuantities", modifiedQuantities);

        FulfillmentCenter center = new FulfillmentCenter(
            "center-us-central-001",
            "Central Warehouse",
            "us-central-1",
            "AVAILABLE",
            12000,
            4500,
            Instant.now()
        );
        Fulfillment fulfillment = new Fulfillment(
            "fulfill-modify-12347",
            order.orderId(),
            center.centerId(),
            "PACKING",
            Instant.now(),
            null
        );
        data.put("fulfillmentCenter", center);
        data.put("fulfillment", fulfillment);

        return data;
    }

    // ====== Exception Scenarios ======

    /**
     * Creates test data for exception scenario: Order cancellation mid-fulfillment.
     */
    public static Map<String, Object> createOrderCancellationData() {
        Map<String, Object> data = new HashMap<>();

        Vendor vendor = new Vendor(
            "vendor-exception-001",
            "Exception Test Vendor",
            "test@exception.com",
            "ACTIVE",
            new BigDecimal("10000.00"),
            Instant.now()
        );
        data.put("vendor", vendor);

        Product product = new Product(
            "prod-cancel-001",
            vendor.vendorId(),
            "Cancellable Product",
            "Product subject to cancellation",
            new BigDecimal("129.99"),
            100,
            "ACTIVE",
            Instant.now()
        );
        data.put("product", product);

        Map<String, Integer> quantities = new HashMap<>();
        quantities.put(product.productId(), 1);

        Order order = new Order(
            "order-cancel-12348",
            "customer-004",
            vendor.vendorId(),
            quantities,
            new BigDecimal("129.99"),
            "FULFILLING",
            "COMPLETED",
            Instant.now(),
            Instant.now()
        );
        data.put("order", order);

        FulfillmentCenter center = new FulfillmentCenter(
            "center-cancel-001",
            "Cancellation Test Center",
            "us-test-1",
            "AVAILABLE",
            5000,
            1000,
            Instant.now()
        );
        Fulfillment fulfillment = new Fulfillment(
            "fulfill-cancel-12348",
            order.orderId(),
            center.centerId(),
            "READY_SHIPMENT",  // Already ready for shipment
            Instant.now().minusSeconds(3600),
            null
        );
        data.put("fulfillmentCenter", center);
        data.put("fulfillment", fulfillment);

        return data;
    }

    /**
     * Creates test data for exception scenario: Payment failure with retry.
     */
    public static Map<String, Object> createPaymentFailureData() {
        Map<String, Object> data = new HashMap<>();

        Vendor vendor = new Vendor(
            "vendor-payment-001",
            "Payment Test Vendor",
            "test@payment.com",
            "ACTIVE",
            new BigDecimal("8000.00"),
            Instant.now()
        );
        data.put("vendor", vendor);

        Product product = new Product(
            "prod-payment-001",
            vendor.vendorId(),
            "Expensive Product",
            "High-value item requiring payment",
            new BigDecimal("999.99"),
            50,
            "ACTIVE",
            Instant.now()
        );
        data.put("product", product);

        Map<String, Integer> quantities = new HashMap<>();
        quantities.put(product.productId(), 1);

        Order order = new Order(
            "order-payment-12349",
            "customer-005",
            vendor.vendorId(),
            quantities,
            new BigDecimal("999.99"),
            "PENDING",
            "PENDING",
            Instant.now(),
            Instant.now()
        );
        data.put("order", order);

        Payment failedPayment = new Payment(
            "pay-payment-12349",
            order.orderId(),
            "customer-005",
            vendor.vendorId(),
            order.totalAmount(),
            "CREDIT_CARD",
            "FAILED",
            "DECLINED",
            Instant.now(),
            Instant.now().plusSeconds(10)
        );
        data.put("failedPayment", failedPayment);

        return data;
    }

    /**
     * Creates test data for exception scenario: Vendor account suspension during active orders.
     */
    public static Map<String, Object> createVendorSuspensionData() {
        Map<String, Object> data = new HashMap<>();

        Vendor vendor = new Vendor(
            "vendor-suspend-001",
            "Suspended Vendor",
            "test@suspend.com",
            "ACTIVE",  // Initially active
            new BigDecimal("20000.00"),
            Instant.now().minusSeconds(86400)  // Created 1 day ago
        );
        data.put("vendor", vendor);

        Product product = new Product(
            "prod-suspend-001",
            vendor.vendorId(),
            "Suspended Product",
            "Product from suspended vendor",
            new BigDecimal("199.99"),
            75,
            "ACTIVE",
            Instant.now().minusSeconds(43200)  // Listed 12 hours ago
        );
        data.put("product", product);

        Map<String, Integer> quantities = new HashMap<>();
        quantities.put(product.productId(), 3);

        Order order = new Order(
            "order-suspend-12350",
            "customer-006",
            vendor.vendorId(),
            quantities,
            new BigDecimal("599.97"),
            "FULFILLING",
            "COMPLETED",
            Instant.now().minusSeconds(1800),  // Created 30 mins ago
            Instant.now()
        );
        data.put("order", order);

        // Vendor status changes to SUSPENDED
        Vendor suspendedVendor = new Vendor(
            vendor.vendorId(),
            vendor.name(),
            vendor.email(),
            "SUSPENDED",  // Changed to suspended
            vendor.balance(),
            vendor.createdAt()
        );
        data.put("suspendedVendor", suspendedVendor);
        data.put("suspensionReason", "Compliance violation detected");

        FulfillmentCenter center = new FulfillmentCenter(
            "center-suspend-001",
            "Suspension Test Center",
            "us-suspend-1",
            "AVAILABLE",
            6000,
            1500,
            Instant.now()
        );
        Fulfillment fulfillment = new Fulfillment(
            "fulfill-suspend-12350",
            order.orderId(),
            center.centerId(),
            "PACKING",
            Instant.now().minusSeconds(1200),
            null
        );
        data.put("fulfillmentCenter", center);
        data.put("fulfillment", fulfillment);

        return data;
    }

    /**
     * Creates test data for exception scenario: Fulfillment center unavailability with fallback routing.
     */
    public static Map<String, Object> createFulfillmentUnavailabilityData() {
        Map<String, Object> data = new HashMap<>();

        Vendor vendor = new Vendor(
            "vendor-unavail-001",
            "Unavailability Test Vendor",
            "test@unavail.com",
            "ACTIVE",
            new BigDecimal("12000.00"),
            Instant.now()
        );
        data.put("vendor", vendor);

        Product product = new Product(
            "prod-unavail-001",
            vendor.vendorId(),
            "Urgent Product",
            "Time-sensitive item",
            new BigDecimal("74.99"),
            80,
            "ACTIVE",
            Instant.now()
        );
        data.put("product", product);

        Map<String, Integer> quantities = new HashMap<>();
        quantities.put(product.productId(), 5);

        Order order = new Order(
            "order-unavail-12351",
            "customer-007",
            vendor.vendorId(),
            quantities,
            new BigDecimal("374.95"),
            "CONFIRMED",
            "COMPLETED",
            Instant.now(),
            Instant.now()
        );
        data.put("order", order);

        // Primary center becomes unavailable
        FulfillmentCenter primaryCenter = new FulfillmentCenter(
            "center-unavail-primary",
            "Primary Warehouse (UNAVAILABLE)",
            "us-unavail-1",
            "UNAVAILABLE",  // Offline due to maintenance
            7000,
            0,
            Instant.now()
        );

        // Fallback center available
        FulfillmentCenter fallbackCenter = new FulfillmentCenter(
            "center-unavail-fallback",
            "Fallback Warehouse",
            "us-fallback-1",
            "AVAILABLE",
            9000,
            3000,
            Instant.now()
        );

        data.put("primaryCenter", primaryCenter);
        data.put("fallbackCenter", fallbackCenter);

        Fulfillment fulfillment = new Fulfillment(
            "fulfill-unavail-12351",
            order.orderId(),
            fallbackCenter.centerId(),  // Routed to fallback
            "RECEIVED",
            Instant.now(),
            null
        );
        data.put("fulfillment", fulfillment);

        return data;
    }

    // ====== Helper Methods ======

    /**
     * Gets a human-readable description of a scenario.
     */
    public static String getScenarioDescription(int scenarioNumber) {
        return switch (scenarioNumber) {
            case 1 -> "Single Product Order: Vendor onboarding → listing → purchase → fulfillment → payment";
            case 2 -> "Multi-Product Order: Order with 3 items, parallel fulfillment from multiple centers";
            case 3 -> "Order Modification: Initial order modified before shipment with quantity and item changes";
            case 4 -> "Cancellation: Order cancelled mid-fulfillment (READY_SHIPMENT state)";
            case 5 -> "Payment Failure: Initial payment declined, retry mechanism tested";
            case 6 -> "Vendor Suspension: Vendor suspended while order in FULFILLING state";
            case 7 -> "Fulfillment Unavailability: Primary center offline, fallback routing applied";
            default -> "Unknown scenario";
        };
    }

    /**
     * Validates order data consistency.
     */
    public static boolean validateOrderDataConsistency(Order order) {
        if (order.productQuantities() == null || order.productQuantities().isEmpty()) {
            LOGGER.warn("Order {} has no products", order.orderId());
            return false;
        }

        // Total amount should be positive
        if (order.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warn("Order {} has invalid total amount: {}", order.orderId(), order.totalAmount());
            return false;
        }

        // Status should be valid
        String validStatuses = "PENDING|CONFIRMED|FULFILLING|SHIPPED|DELIVERED|CANCELLED";
        if (!validStatuses.contains(order.status())) {
            LOGGER.warn("Order {} has invalid status: {}", order.orderId(), order.status());
            return false;
        }

        return true;
    }

    /**
     * Calculates expected total from product quantities and prices (for validation).
     */
    public static BigDecimal calculateExpectedTotal(
            Map<String, Integer> productQuantities,
            Map<String, BigDecimal> productPrices) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entry : productQuantities.entrySet()) {
            String productId = entry.getKey();
            Integer quantity = entry.getValue();
            BigDecimal price = productPrices.getOrDefault(productId, BigDecimal.ZERO);
            total = total.add(price.multiply(new BigDecimal(quantity)));
        }
        return total;
    }
}
