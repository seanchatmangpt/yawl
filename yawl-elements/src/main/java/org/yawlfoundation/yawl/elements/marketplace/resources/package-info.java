/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 */

/**
 * Resource types for GCP Marketplace workflow resourcing.
 *
 * <p>This package defines resource descriptors for YAWL's resourcing module,
 * representing physical and logical resources in marketplace operations:
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.elements.marketplace.resources.FulfillmentCenterResource}
 *       — Warehouse facility with capacity, inventory, and queue management</li>
 * </ul>
 *
 * <h2>Resource Allocation Model</h2>
 * <p>Resources are allocated to tasks during the task allocation phase:
 * <ul>
 *   <li><b>Capacity Planning</b> — Center checks max concurrent tasks ∧ daily throughput</li>
 *   <li><b>Inventory Checking</b> — Product availability verified before allocation</li>
 *   <li><b>Work Queue Management</b> — Tasks enqueued in FIFO order for processing</li>
 *   <li><b>Operating Hours</b> — Task execution respects center operating hours</li>
 * </ul>
 * </p>
 *
 * <h2>Concurrency and Thread Safety</h2>
 * <p>Resource state is managed with atomic counters for thread-safe concurrent access:
 * <pre>
 *   // Thread-safe: may be called from multiple task executors
 *   if (center.hasCapacity()) {
 *       center.incrementActiveTasks();
 *       // Process task...
 *       center.decrementActiveTasks();
 *   }
 * </pre>
 * </p>
 *
 * <h2>Integration with Task Execution</h2>
 * <p>Resources are accessed by tasks during execution:
 * <pre>
 *   FulfillmentTask task = ...;
 *   FulfillmentCenterResource center = ...;
 *
 *   // Check if center can handle this product
 *   if (center.canHandleTask(task.getProductId())) {
 *       // Reserve inventory
 *       center.reserveInventory(task.getProductId(), task.getQuantity());
 *       // Proceed with fulfillment
 *   }
 * </pre>
 * </p>
 *
 * <h2>Monitoring and Metrics</h2>
 * <p>Resources track performance metrics for workflow analytics:
 * <ul>
 *   <li>Active task count (real-time load)</li>
 *   <li>Tasks completed today (throughput measurement)</li>
 *   <li>Work queue depth (backlog indicator)</li>
 *   <li>Product inventory levels (stock tracking)</li>
 * </ul>
 * </p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.resourcing
 * @see org.yawlfoundation.yawl.elements.marketplace.FulfillmentTask
 */
package org.yawlfoundation.yawl.elements.marketplace.resources;
