/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.elements.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.marketplace.conditions.MarketplaceConditionGuards;
import org.yawlfoundation.yawl.elements.marketplace.data.*;
import org.yawlfoundation.yawl.elements.marketplace.resources.FulfillmentCenterResource;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GCP Marketplace YAWL elements.
 *
 * Tests validate task contexts, data elements, resource management,
 * and condition guards for marketplace workflows.
 */
public class MarketplaceElementsIntegrationTest {

    private VendorProfile vendor;
    private ProductListing product;
    private Order order;
    private PaymentStatus payment;
    private FulfillmentCenterResource fulfillmentCenter;

    @BeforeEach
    public void setUp() {
        // Create test vendor
        vendor = VendorProfile.builder()
                .vendorId("V-12345")
                .vendorName("Acme Corp")
                .businessLicense("BL-67890")
                .contactEmail("vendor@acme.com")
                .contactPhone("+1-555-0100")
                .businessAddress("123 Commerce St")
                .marketplaceTier("STANDARD")
                .credentialsVerified(true)
                .complianceStatus("APPROVED")
                .accountRating(4.5)
                .totalTransactions(50)
                .accountActive(true)
                .build();

        // Create test product
        product = ProductListing.builder()
                .productId("P-001")
                .vendorId("V-12345")
                .productName("Widget Pro")
                .productDescription("Premium widget")
                .category("Widgets")
                .subcategory("Professional")
                .listPrice(new BigDecimal("99.99"))
                .discountedPrice(new BigDecimal("79.99"))
                .currency("USD")
                .currentInventory(100)
                .minInventoryThreshold(10)
                .reservedInventory(20)
                .active(true)
                .productRating(4.7)
                .reviewCount(156)
                .build();

        // Create test order
        var lineItem1 = new Order.OrderLineItem(
                "LI-001", "P-001", "Widget Pro", 2,
                new BigDecimal("79.99"), new BigDecimal("159.98")
        );
        var lineItem2 = new Order.OrderLineItem(
                "LI-002", "P-002", "Widget Standard", 1,
                new BigDecimal("49.99"), new BigDecimal("49.99")
        );
        order = Order.builder()
                .orderId("O-54321")
                .customerId("C-11111")
                .vendorId("V-12345")
                .lineItems(Arrays.asList(lineItem1, lineItem2))
                .subtotal(new BigDecimal("209.97"))
                .tax(new BigDecimal("16.80"))
                .shippingCost(new BigDecimal("9.99"))
                .orderTotal(new BigDecimal("236.76"))
                .orderStatus(Order.STATUS_CONFIRMED)
                .paymentStatus(Order.PAYMENT_PENDING)
                .fulfillmentStatus(Order.FULFILLMENT_PENDING)
                .shippingAddress("456 Customer Ave, Anytown USA")
                .build();

        // Create test payment
        payment = PaymentStatus.builder()
                .paymentId("PAY-001")
                .orderId("O-54321")
                .paymentMethod("credit_card")
                .paymentMethodType("VISA")
                .authorizedAmount(new BigDecimal("236.76"))
                .authorizationCode("AUTH-2025-001")
                .authorizationStatus(PaymentStatus.AUTH_AUTHORIZED)
                .captureStatus(PaymentStatus.CAPTURE_PENDING)
                .build();

        // Create fulfillment center
        fulfillmentCenter = new FulfillmentCenterResource(
                "FC-US-WEST", "US West Distribution Center", "US-WEST",
                50, 1000
        );
        fulfillmentCenter.setProductInventory("P-001", 500);
        fulfillmentCenter.setProductInventory("P-002", 300);
    }

    @Test
    public void testVendorProfileCreation() {
        assertNotNull(vendor);
        assertEquals("V-12345", vendor.vendorId());
        assertEquals("Acme Corp", vendor.vendorName());
        assertTrue(vendor.credentialsVerified());
        assertTrue(vendor.canAcceptOrders());
    }

    @Test
    public void testVendorCompliance() {
        assertTrue(MarketplaceConditionGuards.isVendorCompliant(vendor));
        assertTrue(MarketplaceConditionGuards.verifyVendorCanSell(vendor));
        assertTrue(MarketplaceConditionGuards.verifyVendorCredentials(vendor));
    }

    @Test
    public void testVendorCommissionRate() {
        assertEquals(20.0, vendor.getCommissionRate());  // STANDARD tier

        var premiumVendor = VendorProfile.builder()
                .vendorId("V-PREMIUM")
                .vendorName("Premium Corp")
                .businessLicense("PL-001")
                .contactEmail("premium@corp.com")
                .marketplaceTier(VendorProfile.TIER_PREMIUM)
                .credentialsVerified(true)
                .complianceStatus(VendorProfile.COMPLIANCE_APPROVED)
                .build();
        assertEquals(10.0, premiumVendor.getCommissionRate());
    }

    @Test
    public void testProductAvailability() {
        assertTrue(product.active());
        assertTrue(product.isInStock());
        assertTrue(product.isOrderable());
        assertEquals(80, product.getAvailableInventory());  // 100 - 20 reserved
    }

    @Test
    public void testProductInventoryCheck() {
        assertTrue(MarketplaceConditionGuards.checkProductInventory(product, 80));
        assertFalse(MarketplaceConditionGuards.checkProductInventory(product, 81));
        assertFalse(product.isLowInventory());  // 100 >= 10 threshold
    }

    @Test
    public void testProductPricing() {
        assertTrue(MarketplaceConditionGuards.isProductPricingValid(product));
        assertTrue(MarketplaceConditionGuards.hasValidDiscount(product));
        assertEquals(20, product.getDiscountPercentage());
    }

    @Test
    public void testOrderInitialization() {
        assertEquals("O-54321", order.orderId());
        assertEquals("C-11111", order.customerId());
        assertEquals(2, order.getItemCount());
        assertEquals(3, order.getTotalQuantity());
        assertTrue(order.orderTotal().signum() > 0);
    }

    @Test
    public void testOrderReadinessForPayment() {
        assertTrue(MarketplaceConditionGuards.isOrderReadyForPayment(order));
    }

    @Test
    public void testOrderMultiInstance() {
        assertTrue(MarketplaceConditionGuards.requiresMultiInstanceExpansion(order));

        var singleItemOrder = Order.builder()
                .orderId("O-SINGLE")
                .customerId("C-22222")
                .vendorId("V-12345")
                .addLineItem(new Order.OrderLineItem(
                        "LI-SINGLE", "P-001", "Widget Pro", 5,
                        new BigDecimal("99.99"), new BigDecimal("499.95")
                ))
                .orderTotal(new BigDecimal("499.95"))
                .build();

        assertFalse(MarketplaceConditionGuards.requiresMultiInstanceExpansion(singleItemOrder));
    }

    @Test
    public void testPaymentAuthorization() {
        assertTrue(MarketplaceConditionGuards.isPaymentAuthorized(payment));
        assertFalse(MarketplaceConditionGuards.isPaymentCaptured(payment));
        assertFalse(MarketplaceConditionGuards.didPaymentAuthorizationFail(payment));
    }

    @Test
    public void testPaymentCapture() {
        var capturedPayment = PaymentStatus.builder()
                .paymentId("PAY-002")
                .orderId("O-54321")
                .paymentMethod("credit_card")
                .authorizedAmount(new BigDecimal("236.76"))
                .authorizationCode("AUTH-2025-002")
                .authorizationStatus(PaymentStatus.AUTH_AUTHORIZED)
                .captureStatus(PaymentStatus.CAPTURE_CAPTURED)
                .build();

        assertTrue(MarketplaceConditionGuards.isPaymentCaptured(capturedPayment));
        assertFalse(MarketplaceConditionGuards.canRefundPayment(capturedPayment));  // 0 refunded
        assertTrue(MarketplaceConditionGuards.canRefundPayment(capturedPayment));  // Can refund after capture
    }

    @Test
    public void testFulfillmentCenterCapacity() {
        assertTrue(fulfillmentCenter.hasCapacity());
        assertEquals(50, fulfillmentCenter.getMaxConcurrentTasks());
        assertEquals(0, fulfillmentCenter.getActiveTasks());

        fulfillmentCenter.incrementActiveTasks();
        assertEquals(1, fulfillmentCenter.getActiveTasks());

        fulfillmentCenter.decrementActiveTasks();
        assertEquals(0, fulfillmentCenter.getActiveTasks());
    }

    @Test
    public void testFulfillmentCenterInventory() {
        assertTrue(fulfillmentCenter.hasProductInStock("P-001"));
        assertEquals(500, fulfillmentCenter.getProductInventory("P-001"));

        assertTrue(fulfillmentCenter.reserveInventory("P-001", 100));
        assertEquals(400, fulfillmentCenter.getProductInventory("P-001"));

        fulfillmentCenter.restoreInventory("P-001", 50);
        assertEquals(450, fulfillmentCenter.getProductInventory("P-001"));
    }

    @Test
    public void testFulfillmentCenterWorkQueue() {
        assertEquals(0, fulfillmentCenter.getWorkQueueSize());

        assertTrue(fulfillmentCenter.enqueueTask("T-001"));
        assertTrue(fulfillmentCenter.enqueueTask("T-002"));
        assertEquals(2, fulfillmentCenter.getWorkQueueSize());

        assertEquals("T-001", fulfillmentCenter.dequeueTask());
        assertEquals(1, fulfillmentCenter.getWorkQueueSize());

        assertEquals("T-002", fulfillmentCenter.dequeueTask());
        assertEquals(0, fulfillmentCenter.getWorkQueueSize());
    }

    @Test
    public void testFulfillmentCenterCanHandleTask() {
        assertTrue(fulfillmentCenter.canHandleTask("P-001"));
        assertTrue(fulfillmentCenter.isOperating());
        assertTrue(fulfillmentCenter.hasCapacity());
        assertTrue(fulfillmentCenter.hasRemainingCapacityToday());
    }

    @Test
    public void testMarketplaceVendorTaskContext() {
        MarketplaceVendorTask vendorTask = new MarketplaceVendorTask();
        vendorTask.setVendorTaskType(MarketplaceVendorTask.VENDOR_PROFILE_UPDATE);
        vendorTask.setVendorAccountId("V-12345");
        vendorTask.setCredentialsVerified(true);

        assertEquals(MarketplaceVendorTask.VENDOR_PROFILE_UPDATE, vendorTask.getVendorTaskType());
        assertEquals("V-12345", vendorTask.getVendorAccountId());
        assertTrue(vendorTask.areCredentialsVerified());
        assertTrue(vendorTask.isReadyForExecution());
    }

    @Test
    public void testMarketplaceVendorTaskMetadata() {
        MarketplaceVendorTask vendorTask = new MarketplaceVendorTask();
        vendorTask.setVendorMetadata("tier", "STANDARD");
        vendorTask.setVendorMetadata("region", "US-WEST");

        assertEquals("STANDARD", vendorTask.getVendorMetadata("tier"));
        assertEquals("US-WEST", vendorTask.getVendorMetadata("region"));
        assertEquals(2, vendorTask.getVendorMetadataMap().size());
    }

    @Test
    public void testCustomerPurchaseTaskContext() {
        CustomerPurchaseTask purchaseTask = new CustomerPurchaseTask();
        purchaseTask.initializeOrder("O-54321", "C-11111");
        purchaseTask.setPurchaseTaskType(CustomerPurchaseTask.PRODUCT_SELECTION);

        assertEquals("O-54321", purchaseTask.getOrderId());
        assertEquals("C-11111", purchaseTask.getCustomerAccountId());
        assertEquals(CustomerPurchaseTask.PRODUCT_SELECTION, purchaseTask.getPurchaseTaskType());
    }

    @Test
    public void testCustomerPurchaseTaskProductHandling() {
        CustomerPurchaseTask purchaseTask = new CustomerPurchaseTask();
        purchaseTask.initializeOrder("O-54321", "C-11111");
        purchaseTask.setProductDetails("P-001", "Widget Pro", 2, new BigDecimal("79.99"));

        assertEquals("P-001", purchaseTask.getCurrentProductId());
        assertEquals(2, purchaseTask.getProductQuantity());
        assertEquals(new BigDecimal("79.99"), purchaseTask.getProductPrice());
        assertEquals(1, purchaseTask.getOrderItemCount());
        assertEquals(new BigDecimal("159.98"), purchaseTask.calculateLineItemTotal());
    }

    @Test
    public void testCustomerPurchaseTaskPaymentTracking() {
        CustomerPurchaseTask purchaseTask = new CustomerPurchaseTask();
        purchaseTask.setPaymentDetails("PM-001", new BigDecimal("236.76"));
        purchaseTask.setPaymentVerified(true);

        assertTrue(purchaseTask.isPaymentVerified());
        assertEquals(new BigDecimal("236.76"), purchaseTask.getAmountPaid());
    }

    @Test
    public void testFulfillmentTaskContext() {
        FulfillmentTask fulfillmentTask = new FulfillmentTask();
        fulfillmentTask.initializeFulfillment("O-54321", "LI-001", "P-001", 2);
        fulfillmentTask.assignFulfillmentCenter("FC-US-WEST", "US West Distribution Center", "US-WEST");
        fulfillmentTask.setFulfillmentTaskType(FulfillmentTask.PICK_ITEMS);

        assertEquals("O-54321", fulfillmentTask.getOrderId());
        assertEquals("P-001", fulfillmentTask.getProductId());
        assertEquals(2, fulfillmentTask.getQuantity());
        assertEquals(FulfillmentTask.PICK_ITEMS, fulfillmentTask.getFulfillmentTaskType());
        assertTrue(fulfillmentTask.isReadyForExecution());
    }

    @Test
    public void testFulfillmentTaskStatusTransitions() {
        FulfillmentTask fulfillmentTask = new FulfillmentTask();
        fulfillmentTask.initializeFulfillment("O-54321", "LI-001", "P-001", 2);

        assertEquals(FulfillmentTask.STATUS_PENDING, fulfillmentTask.getFulfillmentStatus());
        assertFalse(fulfillmentTask.isComplete());

        fulfillmentTask.setFulfillmentStatus(FulfillmentTask.STATUS_ALLOCATED);
        assertEquals(FulfillmentTask.STATUS_ALLOCATED, fulfillmentTask.getFulfillmentStatus());

        fulfillmentTask.setFulfillmentStatus(FulfillmentTask.STATUS_DELIVERED);
        assertTrue(fulfillmentTask.isComplete());
    }

    @Test
    public void testCompositeGuard() {
        assertTrue(MarketplaceConditionGuards.validateOrderForFulfillment(vendor, order, payment));

        var inactiveVendor = VendorProfile.builder()
                .vendorId("V-INACTIVE")
                .vendorName("Inactive Vendor")
                .businessLicense("BL-999")
                .contactEmail("inactive@vendor.com")
                .accountActive(false)
                .build();

        assertFalse(MarketplaceConditionGuards.validateOrderForFulfillment(inactiveVendor, order, payment));
    }

    @Test
    public void testEndToEndOrderFlow() {
        // Step 1: Vendor can sell
        assertTrue(MarketplaceConditionGuards.verifyVendorCanSell(vendor));

        // Step 2: Product available
        assertTrue(MarketplaceConditionGuards.isProductAvailableForPurchase(product));

        // Step 3: Order ready for payment
        assertTrue(MarketplaceConditionGuards.isOrderReadyForPayment(order));

        // Step 4: Payment authorized
        assertTrue(MarketplaceConditionGuards.isPaymentAuthorized(payment));

        // Step 5: Order can proceed to fulfillment
        var processedOrder = Order.builder()
                .orderId(order.orderId())
                .customerId(order.customerId())
                .vendorId(order.vendorId())
                .lineItems(order.lineItems())
                .subtotal(order.subtotal())
                .tax(order.tax())
                .shippingCost(order.shippingCost())
                .orderTotal(order.orderTotal())
                .orderStatus(Order.STATUS_PROCESSING)
                .paymentStatus(Order.PAYMENT_CAPTURED)
                .fulfillmentStatus(Order.FULFILLMENT_PENDING)
                .build();

        assertTrue(MarketplaceConditionGuards.isOrderReadyForFulfillment(processedOrder));
        assertTrue(MarketplaceConditionGuards.validateOrderForFulfillment(vendor, processedOrder, payment));
    }
}
