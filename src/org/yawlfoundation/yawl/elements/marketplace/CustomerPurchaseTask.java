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

import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.util.StringUtil;

import java.math.BigDecimal;
import java.util.*;

/**
 * Specialized atomic task for GCP Marketplace customer purchase operations.
 *
 * <p>CustomerPurchaseTask represents activities in the customer purchase journey,
 * including product selection, cart management, payment processing, and order confirmation.
 * This task type manages multi-instance expansion for handling multiple products per order.</p>
 *
 * <h2>Purchase Task Types</h2>
 * <ul>
 *   <li><b>PRODUCT_SEARCH</b> - Search and filter products in marketplace</li>
 *   <li><b>PRODUCT_SELECTION</b> - Select specific products for purchase</li>
 *   <li><b>CART_MANAGEMENT</b> - Add/remove items from shopping cart</li>
 *   <li><b>PAYMENT_PROCESSING</b> - Process customer payment</li>
 *   <li><b>ORDER_CONFIRMATION</b> - Confirm and finalize order</li>
 *   <li><b>ORDER_REVIEW</b> - Customer reviews and approves order details</li>
 * </ul>
 *
 * <h2>Multi-Instance Behavior</h2>
 * <p>When an order contains multiple products, CustomerPurchaseTask can expand into
 * multiple instances using static or dynamic creation mode. Each instance processes
 * a single product's requirements (inventory check, availability, pricing).</p>
 *
 * <h2>Cancellation Support</h2>
 * <p>Tasks can be cancelled if customer abandons purchase or payment fails.
 * Cancellation automatically triggers refund and inventory restoration.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public final class CustomerPurchaseTask extends YAtomicTask {

    /**
     * Customer purchase task type constants
     */
    public static final String PRODUCT_SEARCH = "PRODUCT_SEARCH";
    public static final String PRODUCT_SELECTION = "PRODUCT_SELECTION";
    public static final String CART_MANAGEMENT = "CART_MANAGEMENT";
    public static final String PAYMENT_PROCESSING = "PAYMENT_PROCESSING";
    public static final String ORDER_CONFIRMATION = "ORDER_CONFIRMATION";
    public static final String ORDER_REVIEW = "ORDER_REVIEW";

    // Order-level state
    private String orderId;
    private String customerAccountId;
    private BigDecimal orderTotal = BigDecimal.ZERO;
    private int orderItemCount = 0;
    private long orderCreatedTime = 0;
    private boolean orderCancelled = false;
    private String cancellationReason;

    // Product item state (used in multi-instance context)
    private String currentProductId;
    private int productQuantity = 1;
    private BigDecimal productPrice = BigDecimal.ZERO;
    private boolean productAvailable = true;
    private String productName;

    // Payment state
    private String paymentMethodId;
    private BigDecimal amountPaid = BigDecimal.ZERO;
    private boolean paymentVerified = false;
    private long paymentVerificationTime = 0;

    // Task-specific state
    private String purchaseTaskType;
    private Map<String, String> customerPreferences = new HashMap<>();

    /**
     * Constructs a new customer purchase task.
     *
     * @param id the task identifier
     * @param joinType the task's join type (YAtomicTask._AND, ._OR, ._XOR)
     * @param splitType the task's split type
     * @param container the task's containing net
     */
    public CustomerPurchaseTask(String id, int joinType, int splitType, YNet container) {
        super(id, joinType, splitType, container);
    }

    /**
     * Initializes order context (called once per purchase workflow).
     *
     * @param orderId the unique order identifier
     * @param customerId the customer account ID
     */
    public void initializeOrder(String orderId, String customerId) {
        if (StringUtil.isNullOrEmpty(orderId) || StringUtil.isNullOrEmpty(customerId)) {
            throw new IllegalArgumentException("Order ID and customer ID are required");
        }
        this.orderId = orderId;
        this.customerAccountId = customerId;
        this.orderCreatedTime = System.currentTimeMillis();
    }

    /**
     * Gets the order identifier.
     *
     * @return the order ID
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * Gets the customer account ID.
     *
     * @return the customer account identifier
     */
    public String getCustomerAccountId() {
        return customerAccountId;
    }

    /**
     * Sets the purchase task type (e.g., PRODUCT_SELECTION, PAYMENT_PROCESSING).
     *
     * @param taskType one of the *_* constants
     */
    public void setPurchaseTaskType(String taskType) {
        this.purchaseTaskType = taskType;
    }

    /**
     * Gets the purchase task type.
     *
     * @return the task type
     */
    public String getPurchaseTaskType() {
        return purchaseTaskType;
    }

    /**
     * Sets the current product details for multi-instance processing.
     * Call this for each product in the order during instance creation.
     *
     * @param productId the product identifier
     * @param productName the product display name
     * @param quantity the quantity to purchase
     * @param price the unit price per item
     */
    public void setProductDetails(String productId, String productName, int quantity, BigDecimal price) {
        if (StringUtil.isNullOrEmpty(productId)) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Product quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }

        this.currentProductId = productId;
        this.productName = productName;
        this.productQuantity = quantity;
        this.productPrice = price;
        this.orderItemCount++;
    }

    /**
     * Gets the current product ID (for multi-instance context).
     *
     * @return the product identifier
     */
    public String getCurrentProductId() {
        return currentProductId;
    }

    /**
     * Gets the product quantity.
     *
     * @return the quantity ordered
     */
    public int getProductQuantity() {
        return productQuantity;
    }

    /**
     * Gets the product price.
     *
     * @return the unit price
     */
    public BigDecimal getProductPrice() {
        return productPrice;
    }

    /**
     * Gets the product name.
     *
     * @return the product display name
     */
    public String getProductName() {
        return productName;
    }

    /**
     * Sets product availability status (checked during inventory verification).
     *
     * @param available true if product is in stock
     */
    public void setProductAvailable(boolean available) {
        this.productAvailable = available;
    }

    /**
     * Checks if the current product is available.
     *
     * @return true if product is available
     */
    public boolean isProductAvailable() {
        return productAvailable;
    }

    /**
     * Calculates line item total (quantity × price).
     *
     * @return the line item total
     */
    public BigDecimal calculateLineItemTotal() {
        return productPrice.multiply(new BigDecimal(productQuantity));
    }

    /**
     * Adds the current line item total to the order total.
     */
    public void addLineItemToOrderTotal() {
        orderTotal = orderTotal.add(calculateLineItemTotal());
    }

    /**
     * Gets the current order total.
     *
     * @return the cumulative order total
     */
    public BigDecimal getOrderTotal() {
        return orderTotal;
    }

    /**
     * Gets the number of distinct items in the order.
     *
     * @return the order item count
     */
    public int getOrderItemCount() {
        return orderItemCount;
    }

    /**
     * Sets payment details for order.
     *
     * @param paymentMethodId the payment method identifier
     * @param amountPaid the amount paid by customer
     */
    public void setPaymentDetails(String paymentMethodId, BigDecimal amountPaid) {
        if (StringUtil.isNullOrEmpty(paymentMethodId)) {
            throw new IllegalArgumentException("Payment method ID cannot be null or empty");
        }
        if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        this.paymentMethodId = paymentMethodId;
        this.amountPaid = amountPaid;
    }

    /**
     * Gets the payment method ID.
     *
     * @return the payment method identifier
     */
    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    /**
     * Gets the amount paid.
     *
     * @return the paid amount
     */
    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    /**
     * Marks payment as verified.
     *
     * @param verified true if payment has been authorized
     */
    public void setPaymentVerified(boolean verified) {
        this.paymentVerified = verified;
        if (verified) {
            this.paymentVerificationTime = System.currentTimeMillis();
        }
    }

    /**
     * Checks if payment has been verified.
     *
     * @return true if payment is verified
     */
    public boolean isPaymentVerified() {
        return paymentVerified;
    }

    /**
     * Validates payment: amount paid >= order total.
     *
     * @return true if payment is sufficient
     */
    public boolean validatePaymentAmount() {
        return paymentVerified && amountPaid.compareTo(orderTotal) >= 0;
    }

    /**
     * Adds customer preference (e.g., delivery method, gift wrapping).
     *
     * @param key the preference key
     * @param value the preference value
     */
    public void setCustomerPreference(String key, String value) {
        if (StringUtil.isNullOrEmpty(key)) {
            throw new IllegalArgumentException("Preference key cannot be null or empty");
        }
        customerPreferences.put(key, value);
    }

    /**
     * Gets customer preference.
     *
     * @param key the preference key
     * @return the preference value, or null if not set
     */
    public String getCustomerPreference(String key) {
        return customerPreferences.get(key);
    }

    /**
     * Cancels the order with a reason.
     *
     * @param reason the cancellation reason
     */
    public void cancelOrder(String reason) {
        this.orderCancelled = true;
        this.cancellationReason = reason;
    }

    /**
     * Checks if order has been cancelled.
     *
     * @return true if order is cancelled
     */
    public boolean isOrderCancelled() {
        return orderCancelled;
    }

    /**
     * Gets the cancellation reason.
     *
     * @return the cancellation reason, or null if not cancelled
     */
    public String getCancellationReason() {
        return cancellationReason;
    }

    /**
     * Exports purchase task state to XML Element for persistence.
     *
     * @return XML Element containing purchase task state
     */
    public Element exportPurchaseState() {
        Element root = new Element("marketplaceCustomerPurchaseTaskState");

        // Order state
        Element orderElem = new Element("order");
        addElement(orderElem, "orderId", orderId);
        addElement(orderElem, "customerAccountId", customerAccountId);
        addElement(orderElem, "orderTotal", orderTotal.toPlainString());
        addElement(orderElem, "orderItemCount", String.valueOf(orderItemCount));
        addElement(orderElem, "orderCreatedTime", String.valueOf(orderCreatedTime));
        addElement(orderElem, "orderCancelled", String.valueOf(orderCancelled));
        addElement(orderElem, "cancellationReason", cancellationReason);
        root.addContent(orderElem);

        // Product state
        Element productElem = new Element("currentProduct");
        addElement(productElem, "productId", currentProductId);
        addElement(productElem, "productName", productName);
        addElement(productElem, "productQuantity", String.valueOf(productQuantity));
        addElement(productElem, "productPrice", productPrice.toPlainString());
        addElement(productElem, "productAvailable", String.valueOf(productAvailable));
        root.addContent(productElem);

        // Payment state
        Element paymentElem = new Element("payment");
        addElement(paymentElem, "paymentMethodId", paymentMethodId);
        addElement(paymentElem, "amountPaid", amountPaid.toPlainString());
        addElement(paymentElem, "paymentVerified", String.valueOf(paymentVerified));
        addElement(paymentElem, "paymentVerificationTime", String.valueOf(paymentVerificationTime));
        root.addContent(paymentElem);

        // Task state
        Element taskElem = new Element("taskState");
        addElement(taskElem, "purchaseTaskType", purchaseTaskType);
        root.addContent(taskElem);

        // Preferences
        Element prefsElem = new Element("customerPreferences");
        for (Map.Entry<String, String> entry : customerPreferences.entrySet()) {
            Element item = new Element("preference");
            item.setAttribute("key", entry.getKey());
            item.setText(entry.getValue() != null ? entry.getValue() : "");
            prefsElem.addContent(item);
        }
        root.addContent(prefsElem);

        return root;
    }

    /**
     * Imports purchase task state from XML Element during deserialization.
     *
     * @param state XML Element containing purchase task state
     */
    public void importPurchaseState(Element state) {
        if (state == null) {
            return;
        }

        Element orderElem = state.getChild("order");
        if (orderElem != null) {
            orderId = getElementText(orderElem, "orderId");
            customerAccountId = getElementText(orderElem, "customerAccountId");
            String totalStr = getElementText(orderElem, "orderTotal");
            if (!StringUtil.isNullOrEmpty(totalStr)) {
                try {
                    orderTotal = new BigDecimal(totalStr);
                } catch (NumberFormatException e) {
                    orderTotal = BigDecimal.ZERO;
                }
            }
            String countStr = getElementText(orderElem, "orderItemCount");
            if (!StringUtil.isNullOrEmpty(countStr)) {
                try {
                    orderItemCount = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    orderItemCount = 0;
                }
            }
            orderCancelled = Boolean.parseBoolean(getElementText(orderElem, "orderCancelled"));
            cancellationReason = getElementText(orderElem, "cancellationReason");
        }

        Element productElem = state.getChild("currentProduct");
        if (productElem != null) {
            currentProductId = getElementText(productElem, "productId");
            productName = getElementText(productElem, "productName");
            String quantStr = getElementText(productElem, "productQuantity");
            if (!StringUtil.isNullOrEmpty(quantStr)) {
                try {
                    productQuantity = Integer.parseInt(quantStr);
                } catch (NumberFormatException e) {
                    productQuantity = 1;
                }
            }
            String priceStr = getElementText(productElem, "productPrice");
            if (!StringUtil.isNullOrEmpty(priceStr)) {
                try {
                    productPrice = new BigDecimal(priceStr);
                } catch (NumberFormatException e) {
                    productPrice = BigDecimal.ZERO;
                }
            }
            productAvailable = Boolean.parseBoolean(getElementText(productElem, "productAvailable"));
        }

        Element paymentElem = state.getChild("payment");
        if (paymentElem != null) {
            paymentMethodId = getElementText(paymentElem, "paymentMethodId");
            String paidStr = getElementText(paymentElem, "amountPaid");
            if (!StringUtil.isNullOrEmpty(paidStr)) {
                try {
                    amountPaid = new BigDecimal(paidStr);
                } catch (NumberFormatException e) {
                    amountPaid = BigDecimal.ZERO;
                }
            }
            paymentVerified = Boolean.parseBoolean(getElementText(paymentElem, "paymentVerified"));
        }

        Element taskElem = state.getChild("taskState");
        if (taskElem != null) {
            purchaseTaskType = getElementText(taskElem, "purchaseTaskType");
        }

        Element prefsElem = state.getChild("customerPreferences");
        if (prefsElem != null) {
            customerPreferences.clear();
            for (Element item : prefsElem.getChildren("preference")) {
                String key = item.getAttributeValue("key");
                String value = item.getText();
                if (!StringUtil.isNullOrEmpty(key)) {
                    customerPreferences.put(key, value);
                }
            }
        }
    }

    /**
     * Checks if purchase task is ready to proceed (order initialized, products set).
     *
     * @return true if task state is valid
     */
    public boolean isReadyForProcessing() {
        return !StringUtil.isNullOrEmpty(orderId) &&
                !StringUtil.isNullOrEmpty(customerAccountId) &&
                !StringUtil.isNullOrEmpty(purchaseTaskType) &&
                !orderCancelled;
    }

    /**
     * Resets purchase-related state after task completion or cancellation.
     */
    public void resetPurchaseState() {
        currentProductId = null;
        productQuantity = 1;
        productPrice = BigDecimal.ZERO;
        productAvailable = true;
        productName = null;
        paymentVerified = false;
        paymentVerificationTime = 0;
        customerPreferences.clear();
    }

    // Helper method to add element with text content
    private void addElement(Element parent, String name, String value) {
        if (value != null) {
            Element elem = new Element(name);
            elem.setText(value);
            parent.addContent(elem);
        }
    }

    // Helper method to get element text content
    private String getElementText(Element parent, String childName) {
        Element child = parent.getChild(childName);
        return child != null ? child.getText() : null;
    }
}
