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
import org.yawlfoundation.yawl.util.StringUtil;

import java.time.Instant;
import java.util.*;

/**
 * Fulfillment task context for GCP Marketplace operations.
 *
 * <p>FulfillmentTask provides fulfillment-specific attributes and behaviors for warehouse
 * and logistics operations including pick, pack, ship, and delivery activities.
 * Integrates with fulfillment centers and provides real-time tracking of order
 * fulfillment status.</p>
 *
 * <h2>Fulfillment Task Types</h2>
 * <ul>
 *   <li><b>INVENTORY_ALLOCATION</b> - Reserve inventory from fulfillment center</li>
 *   <li><b>PICK_ITEMS</b> - Pick items from warehouse shelves</li>
 *   <li><b>PACK_ORDER</b> - Pack items for shipment</li>
 *   <li><b>GENERATE_SHIPPING_LABEL</b> - Create shipping labels and documentation</li>
 *   <li><b>HAND_OFF_TO_CARRIER</b> - Transfer package to shipping carrier</li>
 *   <li><b>DELIVERY_TRACKING</b> - Monitor delivery progress and status</li>
 *   <li><b>DELIVERY_CONFIRMATION</b> - Confirm successful delivery</li>
 * </ul>
 *
 * <h2>Fulfillment Center Integration</h2>
 * <p>Tasks are assigned to specific fulfillment centers based on inventory location
 * and delivery region. Each center maintains separate inventory and processing queues.</p>
 *
 * <h2>Tracking and Status</h2>
 * <p>Real-time tracking status is maintained throughout fulfillment lifecycle:
 * allocated → picked → packed → shipped → in_transit → delivered.</p>
 *
 * <h2>Integration with YAtomicTask</h2>
 * <p>Use this class as a data container attached to YAtomicTask instances via
 * YAWL data mappings and task attributes.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public final class FulfillmentTask {

    /**
     * Fulfillment task type constants
     */
    public static final String INVENTORY_ALLOCATION = "INVENTORY_ALLOCATION";
    public static final String PICK_ITEMS = "PICK_ITEMS";
    public static final String PACK_ORDER = "PACK_ORDER";
    public static final String GENERATE_SHIPPING_LABEL = "GENERATE_SHIPPING_LABEL";
    public static final String HAND_OFF_TO_CARRIER = "HAND_OFF_TO_CARRIER";
    public static final String DELIVERY_TRACKING = "DELIVERY_TRACKING";
    public static final String DELIVERY_CONFIRMATION = "DELIVERY_CONFIRMATION";

    /**
     * Fulfillment status constants
     */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ALLOCATED = "ALLOCATED";
    public static final String STATUS_PICKED = "PICKED";
    public static final String STATUS_PACKED = "PACKED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";

    // Order fulfillment context
    private String orderId;
    private String lineItemId;
    private int quantity;
    private String productId;

    // Fulfillment center assignment
    private String fulfillmentCenterId;
    private String fulfillmentCenterName;
    private String fulfillmentCenterRegion;
    private boolean inventoryAvailable = false;

    // Task execution state
    private String fulfillmentTaskType;
    private String fulfillmentStatus = STATUS_PENDING;
    private long taskStartTime = 0;
    private long taskCompleteTime = 0;

    // Shipping details
    private String trackingNumber;
    private String carrierId;
    private String carrierName;
    private String shippingAddress;
    private long estimatedDeliveryTime = 0;
    private long actualDeliveryTime = 0;

    // Exception handling
    private String exceptionReason;
    private List<String> exceptionLog = new ArrayList<>();

    // Metrics and audit
    private Map<String, String> fulfillmentMetrics = new HashMap<>();
    private List<String> statusHistory = new ArrayList<>();

    /**
     * Creates a new fulfillment task context with empty state.
     */
    public FulfillmentTask() {
        // No-arg constructor for data mapping
    }

    /**
     * Initializes fulfillment context with order and product details.
     *
     * @param orderId the order identifier
     * @param lineItemId the order line item identifier
     * @param productId the product identifier
     * @param quantity the quantity to fulfill
     */
    public void initializeFulfillment(String orderId, String lineItemId, String productId, int quantity) {
        if (StringUtil.isNullOrEmpty(orderId) || StringUtil.isNullOrEmpty(lineItemId) ||
                StringUtil.isNullOrEmpty(productId) || quantity <= 0) {
            throw new IllegalArgumentException("Invalid fulfillment initialization parameters");
        }
        this.orderId = orderId;
        this.lineItemId = lineItemId;
        this.productId = productId;
        this.quantity = quantity;
        recordStatusTransition(STATUS_PENDING);
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
     * Gets the line item identifier.
     *
     * @return the line item ID
     */
    public String getLineItemId() {
        return lineItemId;
    }

    /**
     * Gets the product identifier.
     *
     * @return the product ID
     */
    public String getProductId() {
        return productId;
    }

    /**
     * Gets the quantity to fulfill.
     *
     * @return the quantity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Assigns this fulfillment task to a specific fulfillment center.
     *
     * @param centerId the fulfillment center ID
     * @param centerName the fulfillment center name
     * @param region the fulfillment center region
     */
    public void assignFulfillmentCenter(String centerId, String centerName, String region) {
        if (StringUtil.isNullOrEmpty(centerId) || StringUtil.isNullOrEmpty(centerName)) {
            throw new IllegalArgumentException("Fulfillment center ID and name are required");
        }
        this.fulfillmentCenterId = centerId;
        this.fulfillmentCenterName = centerName;
        this.fulfillmentCenterRegion = region;
    }

    /**
     * Gets the assigned fulfillment center ID.
     *
     * @return the fulfillment center ID
     */
    public String getFulfillmentCenterId() {
        return fulfillmentCenterId;
    }

    /**
     * Gets the fulfillment center name.
     *
     * @return the fulfillment center name
     */
    public String getFulfillmentCenterName() {
        return fulfillmentCenterName;
    }

    /**
     * Gets the fulfillment center region.
     *
     * @return the region code
     */
    public String getFulfillmentCenterRegion() {
        return fulfillmentCenterRegion;
    }

    /**
     * Sets the fulfillment task type.
     *
     * @param taskType one of the FULFILLMENT_* constants
     */
    public void setFulfillmentTaskType(String taskType) {
        if (StringUtil.isNullOrEmpty(taskType)) {
            throw new IllegalArgumentException("Fulfillment task type cannot be null or empty");
        }
        this.fulfillmentTaskType = taskType;
    }

    /**
     * Gets the fulfillment task type.
     *
     * @return the task type
     */
    public String getFulfillmentTaskType() {
        return fulfillmentTaskType;
    }

    /**
     * Updates fulfillment status.
     *
     * @param newStatus one of the STATUS_* constants
     */
    public void setFulfillmentStatus(String newStatus) {
        if (!StringUtil.isNullOrEmpty(newStatus)) {
            this.fulfillmentStatus = newStatus;
            recordStatusTransition(newStatus);
            if (fulfillmentStatus.equals(STATUS_SHIPPED) && taskStartTime == 0) {
                this.taskStartTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * Gets the current fulfillment status.
     *
     * @return the status
     */
    public String getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    /**
     * Checks if inventory is available at fulfillment center.
     *
     * @return true if inventory is allocated
     */
    public boolean isInventoryAvailable() {
        return inventoryAvailable;
    }

    /**
     * Marks inventory as available/allocated.
     *
     * @param available true if inventory can be allocated
     */
    public void setInventoryAvailable(boolean available) {
        this.inventoryAvailable = available;
    }

    /**
     * Assigns tracking number (called after package is handed off to carrier).
     *
     * @param trackingNumber the carrier tracking number
     * @param carrierId the carrier identifier
     * @param carrierName the carrier name
     */
    public void assignTrackingNumber(String trackingNumber, String carrierId, String carrierName) {
        if (StringUtil.isNullOrEmpty(trackingNumber)) {
            throw new IllegalArgumentException("Tracking number cannot be null or empty");
        }
        this.trackingNumber = trackingNumber;
        this.carrierId = carrierId;
        this.carrierName = carrierName;
        recordStatusTransition(STATUS_SHIPPED);
    }

    /**
     * Gets the tracking number.
     *
     * @return the carrier tracking number
     */
    public String getTrackingNumber() {
        return trackingNumber;
    }

    /**
     * Gets the carrier ID.
     *
     * @return the carrier identifier
     */
    public String getCarrierId() {
        return carrierId;
    }

    /**
     * Gets the carrier name.
     *
     * @return the carrier name
     */
    public String getCarrierName() {
        return carrierName;
    }

    /**
     * Sets shipping address for delivery.
     *
     * @param address the delivery address
     */
    public void setShippingAddress(String address) {
        this.shippingAddress = address;
    }

    /**
     * Gets the shipping address.
     *
     * @return the delivery address
     */
    public String getShippingAddress() {
        return shippingAddress;
    }

    /**
     * Sets estimated delivery time.
     *
     * @param estimatedTime epoch milliseconds for estimated delivery
     */
    public void setEstimatedDeliveryTime(long estimatedTime) {
        this.estimatedDeliveryTime = estimatedTime;
    }

    /**
     * Gets estimated delivery time.
     *
     * @return epoch milliseconds, or 0 if not set
     */
    public long getEstimatedDeliveryTime() {
        return estimatedDeliveryTime;
    }

    /**
     * Records actual delivery time (call when delivery confirmed).
     *
     * @param deliveryTime epoch milliseconds of actual delivery
     */
    public void setActualDeliveryTime(long deliveryTime) {
        this.actualDeliveryTime = deliveryTime;
        this.taskCompleteTime = System.currentTimeMillis();
        recordStatusTransition(STATUS_DELIVERED);
    }

    /**
     * Gets actual delivery time.
     *
     * @return epoch milliseconds, or 0 if not yet delivered
     */
    public long getActualDeliveryTime() {
        return actualDeliveryTime;
    }

    /**
     * Records an exception/error during fulfillment.
     *
     * @param reason the reason for the exception
     */
    public void recordException(String reason) {
        this.exceptionReason = reason;
        this.exceptionLog.add(Instant.now() + " - " + reason);
        recordStatusTransition(STATUS_FAILED);
    }

    /**
     * Gets the current exception reason.
     *
     * @return the exception reason, or null if no exception
     */
    public String getExceptionReason() {
        return exceptionReason;
    }

    /**
     * Gets the complete exception log.
     *
     * @return unmodifiable list of exception entries
     */
    public List<String> getExceptionLog() {
        return Collections.unmodifiableList(exceptionLog);
    }

    /**
     * Records a fulfillment metric.
     *
     * @param key the metric key (e.g., "pick_time_ms", "pack_time_ms")
     * @param value the metric value
     */
    public void recordFulfillmentMetric(String key, String value) {
        if (!StringUtil.isNullOrEmpty(key)) {
            fulfillmentMetrics.put(key, value);
        }
    }

    /**
     * Gets a fulfillment metric.
     *
     * @param key the metric key
     * @return the metric value, or null if not found
     */
    public String getFulfillmentMetric(String key) {
        return fulfillmentMetrics.get(key);
    }

    /**
     * Gets all fulfillment metrics.
     *
     * @return unmodifiable map of metrics
     */
    public Map<String, String> getFulfillmentMetrics() {
        return Collections.unmodifiableMap(fulfillmentMetrics);
    }

    /**
     * Gets the complete status history.
     *
     * @return unmodifiable list of status transitions
     */
    public List<String> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    /**
     * Checks if fulfillment is complete.
     *
     * @return true if status is DELIVERED or FAILED
     */
    public boolean isComplete() {
        return STATUS_DELIVERED.equals(fulfillmentStatus) || STATUS_FAILED.equals(fulfillmentStatus);
    }

    /**
     * Exports fulfillment task state to XML Element for persistence.
     *
     * @return XML Element containing fulfillment task state
     */
    public Element exportFulfillmentState() {
        Element root = new Element("marketplaceFulfillmentTaskState");

        // Order context
        Element orderElem = new Element("orderContext");
        addElement(orderElem, "orderId", orderId);
        addElement(orderElem, "lineItemId", lineItemId);
        addElement(orderElem, "productId", productId);
        addElement(orderElem, "quantity", String.valueOf(quantity));
        root.addContent(orderElem);

        // Fulfillment center
        Element centerElem = new Element("fulfillmentCenter");
        addElement(centerElem, "centerId", fulfillmentCenterId);
        addElement(centerElem, "centerName", fulfillmentCenterName);
        addElement(centerElem, "region", fulfillmentCenterRegion);
        addElement(centerElem, "inventoryAvailable", String.valueOf(inventoryAvailable));
        root.addContent(centerElem);

        // Task execution
        Element taskElem = new Element("taskExecution");
        addElement(taskElem, "taskType", fulfillmentTaskType);
        addElement(taskElem, "status", fulfillmentStatus);
        addElement(taskElem, "taskStartTime", String.valueOf(taskStartTime));
        addElement(taskElem, "taskCompleteTime", String.valueOf(taskCompleteTime));
        root.addContent(taskElem);

        // Shipping
        Element shippingElem = new Element("shipping");
        addElement(shippingElem, "trackingNumber", trackingNumber);
        addElement(shippingElem, "carrierId", carrierId);
        addElement(shippingElem, "carrierName", carrierName);
        addElement(shippingElem, "shippingAddress", shippingAddress);
        addElement(shippingElem, "estimatedDeliveryTime", String.valueOf(estimatedDeliveryTime));
        addElement(shippingElem, "actualDeliveryTime", String.valueOf(actualDeliveryTime));
        root.addContent(shippingElem);

        // Exceptions
        Element excElem = new Element("exceptions");
        addElement(excElem, "exceptionReason", exceptionReason);
        Element logElem = new Element("log");
        for (String entry : exceptionLog) {
            Element entryElem = new Element("entry");
            entryElem.setText(entry);
            logElem.addContent(entryElem);
        }
        excElem.addContent(logElem);
        root.addContent(excElem);

        // Metrics
        Element metricsElem = new Element("fulfillmentMetrics");
        for (Map.Entry<String, String> entry : fulfillmentMetrics.entrySet()) {
            Element metricElem = new Element("metric");
            metricElem.setAttribute("key", entry.getKey());
            metricElem.setText(entry.getValue() != null ? entry.getValue() : "");
            metricsElem.addContent(metricElem);
        }
        root.addContent(metricsElem);

        // Status history
        Element historyElem = new Element("statusHistory");
        for (String status : statusHistory) {
            Element statusElem = new Element("status");
            statusElem.setText(status);
            historyElem.addContent(statusElem);
        }
        root.addContent(historyElem);

        return root;
    }

    /**
     * Imports fulfillment task state from XML Element during deserialization.
     *
     * @param state XML Element containing fulfillment task state
     */
    public void importFulfillmentState(Element state) {
        if (state == null) {
            return;
        }

        Element orderElem = state.getChild("orderContext");
        if (orderElem != null) {
            orderId = getElementText(orderElem, "orderId");
            lineItemId = getElementText(orderElem, "lineItemId");
            productId = getElementText(orderElem, "productId");
            String qtyStr = getElementText(orderElem, "quantity");
            if (!StringUtil.isNullOrEmpty(qtyStr)) {
                try {
                    quantity = Integer.parseInt(qtyStr);
                } catch (NumberFormatException e) {
                    quantity = 0;
                }
            }
        }

        Element centerElem = state.getChild("fulfillmentCenter");
        if (centerElem != null) {
            fulfillmentCenterId = getElementText(centerElem, "centerId");
            fulfillmentCenterName = getElementText(centerElem, "centerName");
            fulfillmentCenterRegion = getElementText(centerElem, "region");
            inventoryAvailable = Boolean.parseBoolean(getElementText(centerElem, "inventoryAvailable"));
        }

        Element taskElem = state.getChild("taskExecution");
        if (taskElem != null) {
            fulfillmentTaskType = getElementText(taskElem, "taskType");
            String statusStr = getElementText(taskElem, "status");
            if (!StringUtil.isNullOrEmpty(statusStr)) {
                fulfillmentStatus = statusStr;
            }
            String startStr = getElementText(taskElem, "taskStartTime");
            if (!StringUtil.isNullOrEmpty(startStr)) {
                try {
                    taskStartTime = Long.parseLong(startStr);
                } catch (NumberFormatException e) {
                    taskStartTime = 0;
                }
            }
        }

        Element shippingElem = state.getChild("shipping");
        if (shippingElem != null) {
            trackingNumber = getElementText(shippingElem, "trackingNumber");
            carrierId = getElementText(shippingElem, "carrierId");
            carrierName = getElementText(shippingElem, "carrierName");
            shippingAddress = getElementText(shippingElem, "shippingAddress");
            String estDelivStr = getElementText(shippingElem, "estimatedDeliveryTime");
            if (!StringUtil.isNullOrEmpty(estDelivStr)) {
                try {
                    estimatedDeliveryTime = Long.parseLong(estDelivStr);
                } catch (NumberFormatException e) {
                    estimatedDeliveryTime = 0;
                }
            }
        }

        Element excElem = state.getChild("exceptions");
        if (excElem != null) {
            exceptionReason = getElementText(excElem, "exceptionReason");
            Element logElem = excElem.getChild("log");
            if (logElem != null) {
                exceptionLog.clear();
                for (Element entry : logElem.getChildren("entry")) {
                    exceptionLog.add(entry.getText());
                }
            }
        }

        Element metricsElem = state.getChild("fulfillmentMetrics");
        if (metricsElem != null) {
            fulfillmentMetrics.clear();
            for (Element metricElem : metricsElem.getChildren("metric")) {
                String key = metricElem.getAttributeValue("key");
                String value = metricElem.getText();
                if (!StringUtil.isNullOrEmpty(key)) {
                    fulfillmentMetrics.put(key, value);
                }
            }
        }

        Element historyElem = state.getChild("statusHistory");
        if (historyElem != null) {
            statusHistory.clear();
            for (Element statusElem : historyElem.getChildren("status")) {
                statusHistory.add(statusElem.getText());
            }
        }
    }

    /**
     * Checks if fulfillment task is ready for execution.
     *
     * @return true if all required context is set
     */
    public boolean isReadyForExecution() {
        return !StringUtil.isNullOrEmpty(orderId) &&
                !StringUtil.isNullOrEmpty(fulfillmentCenterId) &&
                !StringUtil.isNullOrEmpty(fulfillmentTaskType) &&
                quantity > 0;
    }

    /**
     * Resets fulfillment state after completion or cancellation.
     */
    public void resetFulfillmentState() {
        fulfillmentStatus = STATUS_PENDING;
        taskStartTime = 0;
        taskCompleteTime = 0;
        trackingNumber = null;
        exceptionReason = null;
        exceptionLog.clear();
        fulfillmentMetrics.clear();
    }

    // Helper methods for XML handling
    private void addElement(Element parent, String name, String value) {
        if (value != null) {
            Element elem = new Element(name);
            elem.setText(value);
            parent.addContent(elem);
        }
    }

    private String getElementText(Element parent, String childName) {
        Element child = parent.getChild(childName);
        return child != null ? child.getText() : null;
    }

    private void recordStatusTransition(String status) {
        statusHistory.add(Instant.now() + " - " + status);
    }
}
