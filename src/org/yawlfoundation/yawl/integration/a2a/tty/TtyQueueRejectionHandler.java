/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.a2a.tty;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resilience4j event listener for monitoring TtyCommandQueue rejection events.
 *
 * <p>This listener tracks when the queue reaches capacity and can trigger fallback
 * strategies such as routing to a dead letter queue or triggering alerts.
 *
 * <p><b>Metrics tracked:</b>
 * <ul>
 *   <li>Total rejection count when queue is full</li>
 *   <li>Rejection rate for alerting</li>
 *   <li>Integration with Bulkhead for backpressure handling</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * TtyCommandQueue queue = new TtyCommandQueue(100);
 * TtyQueueRejectionHandler handler = new TtyQueueRejectionHandler(queue);
 *
 * Bulkhead bulkhead = Bulkhead.ofDefaults("tty-commands");
 * bulkhead.getEventPublisher().onCallRejected(event -> {
 *     handler.recordRejection();
 * });
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class TtyQueueRejectionHandler implements RegistryEventConsumer<Bulkhead> {

    private static final Logger logger = LogManager.getLogger(TtyQueueRejectionHandler.class);

    private final TtyCommandQueue queue;
    private final AtomicLong rejectionCount = new AtomicLong(0);

    /**
     * Creates a handler for queue rejection monitoring.
     *
     * @param queue the TtyCommandQueue to monitor
     * @throws IllegalArgumentException if queue is null
     */
    public TtyQueueRejectionHandler(TtyCommandQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue cannot be null");
    }

    @Override
    public void onEntryAdded(EntryAddedEvent<Bulkhead> event) {
        Bulkhead bulkhead = event.getAddedEntry();
        logger.debug("Attached rejection handler to bulkhead: {}", bulkhead.getName());

        // Listen for rejection events
        bulkhead.getEventPublisher().onCallRejected(rejectionEvent -> {
            recordRejection();
            logger.warn("Command queue rejection detected for bulkhead: {} - Queue size: {} / {}",
                bulkhead.getName(), queue.size(), queue.getMaxSize());
        });
    }

    @Override
    public void onEntryRemoved(EntryRemovedEvent<Bulkhead> event) {
        logger.debug("Removed rejection handler from bulkhead: {}", event.getRemovedEntry().getName());
    }

    @Override
    public void onEntryReplaced(EntryReplacedEvent<Bulkhead> event) {
        Bulkhead newBulkhead = event.getNewEntry();
        logger.debug("Replaced bulkhead with rejection handler: {}", newBulkhead.getName());

        newBulkhead.getEventPublisher().onCallRejected(rejectionEvent -> {
            recordRejection();
        });
    }

    /**
     * Records a rejection event.
     * Called when queue is full and a command is rejected.
     */
    public void recordRejection() {
        rejectionCount.incrementAndGet();
    }

    /**
     * Gets the total number of rejections recorded.
     *
     * @return rejection count
     */
    public long getRejectionCount() {
        return rejectionCount.get();
    }

    /**
     * Resets the rejection counter.
     */
    public void resetRejectionCount() {
        rejectionCount.set(0);
        logger.debug("Rejection counter reset");
    }

    /**
     * Gets a snapshot of current queue metrics suitable for exposure to monitoring systems.
     *
     * @return a map of metrics including queue utilization and rejection count
     */
    public java.util.Map<String, Number> getMetrics() {
        java.util.Map<String, Number> metrics = new java.util.HashMap<>();
        metrics.putAll(queue.getMetrics());
        metrics.put("queue.rejections.total", rejectionCount.get());
        return metrics;
    }

    /**
     * Creates a virtual thread task for periodic monitoring and cleanup.
     * Can be used with Java 25 virtual threads for high-concurrency monitoring.
     *
     * @return a runnable for periodic queue monitoring
     */
    public Runnable createMonitoringTask() {
        return () -> {
            while (Thread.currentThread().isAlive()) {
                try {
                    Thread.sleep(60_000); // Monitor every minute

                    java.util.Map<String, Number> metrics = getMetrics();
                    double utilization = metrics.get("queue.utilization_percent").doubleValue();

                    if (utilization > 80.0) {
                        logger.warn("TTY queue high utilization: {:.1f}%", utilization);
                    }

                    if (rejectionCount.get() > 0) {
                        logger.info("TTY queue rejections in past minute: {}", rejectionCount.get());
                        resetRejectionCount();
                    }
                } catch (InterruptedException e) {
                    logger.debug("Monitoring task interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        };
    }
}
