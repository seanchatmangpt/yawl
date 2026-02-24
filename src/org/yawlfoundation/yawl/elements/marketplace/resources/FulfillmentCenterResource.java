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

package org.yawlfoundation.yawl.elements.marketplace.resources;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fulfillment center resource descriptor for YAWL resourcing.
 *
 * <p>Represents a warehouse facility with capacity constraints, operating hours,
 * and work queue management. Used for assignment of fulfillment tasks.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public class FulfillmentCenterResource {

    private final String centerId;
    private final String centerName;
    private final String region;
    private final int maxConcurrentTasks;
    private final int maxDailyThroughput;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final AtomicInteger tasksCompletedToday = new AtomicInteger(0);
    private final long centerCreatedTime;
    private boolean operatingStatus = true;
    private List<String> operatingHours = new ArrayList<>();
    private Map<String, Integer> inventoryByProduct = new HashMap<>();
    private Queue<String> workQueue = new LinkedList<>();

    /**
     * Constructs a fulfillment center resource.
     *
     * @param centerId the unique center identifier
     * @param centerName the center display name
     * @param region the geographic region
     * @param maxConcurrentTasks maximum parallel tasks
     * @param maxDailyThroughput maximum daily task completions
     */
    public FulfillmentCenterResource(String centerId, String centerName, String region,
                                      int maxConcurrentTasks, int maxDailyThroughput) {
        this.centerId = Objects.requireNonNull(centerId);
        this.centerName = Objects.requireNonNull(centerName);
        this.region = region;
        this.maxConcurrentTasks = maxConcurrentTasks;
        this.maxDailyThroughput = maxDailyThroughput;
        this.centerCreatedTime = System.currentTimeMillis();
    }

    /**
     * Gets the center ID.
     *
     * @return the center identifier
     */
    public String getCenterId() {
        return centerId;
    }

    /**
     * Gets the center name.
     *
     * @return the center display name
     */
    public String getCenterName() {
        return centerName;
    }

    /**
     * Gets the region.
     *
     * @return the geographic region
     */
    public String getRegion() {
        return region;
    }

    /**
     * Checks if center is currently operating.
     *
     * @return true if center is active
     */
    public boolean isOperating() {
        return operatingStatus;
    }

    /**
     * Sets operating status.
     *
     * @param operating true to activate, false to deactivate
     */
    public void setOperatingStatus(boolean operating) {
        this.operatingStatus = operating;
    }

    /**
     * Gets the maximum concurrent tasks this center can handle.
     *
     * @return max concurrent task capacity
     */
    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    /**
     * Gets the current number of active tasks.
     *
     * @return active task count
     */
    public int getActiveTasks() {
        return activeTasks.get();
    }

    /**
     * Increments active task count.
     *
     * @return the new count
     */
    public int incrementActiveTasks() {
        return activeTasks.incrementAndGet();
    }

    /**
     * Decrements active task count.
     *
     * @return the new count
     */
    public int decrementActiveTasks() {
        return activeTasks.decrementAndGet();
    }

    /**
     * Checks if center has capacity for new tasks.
     *
     * @return true if active tasks < max concurrent
     */
    public boolean hasCapacity() {
        return getActiveTasks() < maxConcurrentTasks;
    }

    /**
     * Gets maximum daily throughput.
     *
     * @return max tasks per day
     */
    public int getMaxDailyThroughput() {
        return maxDailyThroughput;
    }

    /**
     * Gets tasks completed today.
     *
     * @return completed task count
     */
    public int getTasksCompletedToday() {
        return tasksCompletedToday.get();
    }

    /**
     * Records task completion.
     *
     * @return the new completed count
     */
    public int incrementTasksCompletedToday() {
        return tasksCompletedToday.incrementAndGet();
    }

    /**
     * Resets daily counter (call at day boundary).
     */
    public void resetDailyCounter() {
        tasksCompletedToday.set(0);
    }

    /**
     * Checks if center has capacity for today's throughput.
     *
     * @return true if tasks completed < max daily throughput
     */
    public boolean hasRemainingCapacityToday() {
        return getTasksCompletedToday() < maxDailyThroughput;
    }

    /**
     * Sets inventory for a product at this center.
     *
     * @param productId the product identifier
     * @param quantity the quantity in stock
     */
    public void setProductInventory(String productId, int quantity) {
        if (quantity > 0) {
            inventoryByProduct.put(productId, quantity);
        } else {
            inventoryByProduct.remove(productId);
        }
    }

    /**
     * Gets inventory for a product.
     *
     * @param productId the product identifier
     * @return quantity in stock, or 0 if not available
     */
    public int getProductInventory(String productId) {
        return inventoryByProduct.getOrDefault(productId, 0);
    }

    /**
     * Checks if product is in stock.
     *
     * @param productId the product identifier
     * @return true if inventory > 0
     */
    public boolean hasProductInStock(String productId) {
        return getProductInventory(productId) > 0;
    }

    /**
     * Attempts to reserve inventory.
     *
     * @param productId the product identifier
     * @param quantity the quantity to reserve
     * @return true if reservation successful
     */
    public boolean reserveInventory(String productId, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        int current = getProductInventory(productId);
        if (current >= quantity) {
            setProductInventory(productId, current - quantity);
            return true;
        }
        return false;
    }

    /**
     * Restores reserved inventory (e.g., on order cancellation).
     *
     * @param productId the product identifier
     * @param quantity the quantity to restore
     */
    public void restoreInventory(String productId, int quantity) {
        int current = getProductInventory(productId);
        setProductInventory(productId, current + quantity);
    }

    /**
     * Enqueues a task for processing.
     *
     * @param taskId the task identifier
     * @return true if enqueued
     */
    public boolean enqueueTask(String taskId) {
        return workQueue.offer(taskId);
    }

    /**
     * Dequeues the next task.
     *
     * @return the next task ID, or null if queue empty
     */
    public String dequeueTask() {
        return workQueue.poll();
    }

    /**
     * Gets the current work queue size.
     *
     * @return number of pending tasks
     */
    public int getWorkQueueSize() {
        return workQueue.size();
    }

    /**
     * Gets operating hours.
     *
     * @return list of hour ranges (e.g., ["08:00-22:00", "07:00-23:00"])
     */
    public List<String> getOperatingHours() {
        return Collections.unmodifiableList(operatingHours);
    }

    /**
     * Sets operating hours.
     *
     * @param hours list of hour ranges
     */
    public void setOperatingHours(List<String> hours) {
        this.operatingHours = new ArrayList<>(hours);
    }

    /**
     * Gets all products with inventory at this center.
     *
     * @return unmodifiable map of product inventory
     */
    public Map<String, Integer> getAllInventory() {
        return Collections.unmodifiableMap(inventoryByProduct);
    }

    /**
     * Checks if center can handle a specific task type and product.
     *
     * @param productId the product to handle
     * @return true if center is operating, has capacity, and has product
     */
    public boolean canHandleTask(String productId) {
        return isOperating() && hasCapacity() && hasRemainingCapacityToday() &&
                hasProductInStock(productId);
    }
}
