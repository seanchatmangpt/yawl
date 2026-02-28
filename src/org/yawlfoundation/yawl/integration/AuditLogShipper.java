/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.logging.table.YAuditEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Forwards YAWL security audit events to one or more external sinks asynchronously.
 * Decouples audit logging from application performance via bounded queue forwarding.
 * @since YAWL 6.0
 */
public class AuditLogShipper {

    private static final Logger LOGGER = LogManager.getLogger(AuditLogShipper.class);
    private final List<AuditSink> sinks;
    private final BlockingQueue<YAuditEvent> eventQueue;
    private final ReentrantReadWriteLock sinksLock;
    private volatile boolean running;
    private Thread shipper;
    private static final int MAX_QUEUE_SIZE = 10_000;
    private static final long SHUTDOWN_TIMEOUT_SECS = 30;

    public AuditLogShipper() {
        this.sinks = new ArrayList<>();
        this.eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.sinksLock = new ReentrantReadWriteLock();
        this.running = false;
    }

    public void addSink(AuditSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        sinksLock.writeLock().lock();
        try {
            sinks.add(sink);
            LOGGER.info("Registered audit sink: {}", sink.sinkName());
        } finally {
            sinksLock.writeLock().unlock();
        }
    }

    public synchronized void start() {
        if (running) throw new IllegalStateException("AuditLogShipper already started");
        running = true;
        shipper = Thread.ofVirtual().name("audit-shipper").start(this::shipperLoop);
        LOGGER.info("AuditLogShipper started");
    }

    public void ship(YAuditEvent event) throws InterruptedException {
        Objects.requireNonNull(event, "event must not be null");
        if (!running) throw new IllegalStateException("AuditLogShipper not running; call start() first");
        eventQueue.put(event);
    }

    public synchronized void shutdown() {
        if (!running) return;
        running = false;
        LOGGER.info("Shutting down AuditLogShipper...");
        try {
            if (shipper != null) {
                shipper.join(TimeUnit.SECONDS.toMillis(SHUTDOWN_TIMEOUT_SECS));
                if (shipper.isAlive()) {
                    LOGGER.warn("Shipper thread did not finish within {} seconds", SHUTDOWN_TIMEOUT_SECS);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for shipper to shutdown", e);
            Thread.currentThread().interrupt();
        }
        LOGGER.info("AuditLogShipper shutdown complete. {} events remain in queue", eventQueue.size());
    }

    private void shipperLoop() {
        while (running || !eventQueue.isEmpty()) {
            try {
                YAuditEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
                if (event == null) continue;
                sinksLock.readLock().lock();
                try {
                    for (AuditSink sink : sinks) {
                        if (!sink.isHealthy()) {
                            LOGGER.warn("Sink {} is unhealthy, skipping event", sink.sinkName());
                            continue;
                        }
                        try {
                            sink.ship(event);
                        } catch (AuditShippingException e) {
                            LOGGER.error("Failed to ship event to {}: {}", sink.sinkName(), e.getMessage(), e);
                            throw new AuditShippingException("Permanent failure shipping to " + sink.sinkName(), e);
                        }
                    }
                } finally {
                    sinksLock.readLock().unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug("Shipper interrupted", e);
            } catch (AuditShippingException e) {
                LOGGER.error("Fatal audit shipping error: {}", e.getMessage(), e);
                running = false;
            }
        }
    }

    public int getQueueDepth() { return eventQueue.size(); }

    public int getSinkCount() {
        sinksLock.readLock().lock();
        try { return sinks.size(); }
        finally { sinksLock.readLock().unlock(); }
    }
}
