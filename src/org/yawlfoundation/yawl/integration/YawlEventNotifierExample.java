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

package org.yawlfoundation.yawl.integration;

import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.interfce.EngineGateway;
import org.yawlfoundation.yawl.engine.interfce.EngineGatewayImpl;
import org.yawlfoundation.yawl.exceptions.YAWLException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Example demonstrating how to use YawlEventNotifier for real-time event notifications.
 *
 * This example shows:
 * 1. How to integrate YawlEventNotifier with YAWL Engine
 * 2. How to register webhook endpoints for HTTP notifications
 * 3. How to poll events from the in-memory queue
 * 4. How to monitor event delivery metrics
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlEventNotifierExample {

    private static final Logger logger = Logger.getLogger(YawlEventNotifierExample.class.getName());

    /**
     * Example 1: Basic integration with YAWL Engine
     */
    public static void basicIntegration() throws YAWLException, YPersistenceException {
        // Create event notifier
        YawlEventNotifier notifier = new YawlEventNotifier();

        // Register webhook endpoint (HTTP POST notifications)
        notifier.registerWebhook("http://localhost:9000/yawl/events");
        notifier.registerWebhook("https://your-server.com/api/yawl/webhook");

        // Get YAWL Engine gateway
        EngineGateway gateway = new EngineGatewayImpl(true);

        // Register notifier as observer gateway (InterfaceE integration)
        gateway.registerObserverGateway(notifier);

        logger.info("YawlEventNotifier integrated with YAWL Engine");
        logger.info("All workflow events will be sent to registered webhooks");

        // Now all YAWL events will be automatically published:
        // - case.started
        // - case.completed
        // - case.cancelled
        // - workitem.enabled
        // - workitem.started
        // - workitem.completed
        // - timer.expired (deadline approaching)
        // - exception.deadlock
        // - resource.allocated
    }

    /**
     * Example 2: Polling events from in-memory queue
     */
    public static void pollingEvents() throws InterruptedException {
        YawlEventNotifier notifier = new YawlEventNotifier();

        // Poll with timeout
        YawlEventNotifier.YawlEvent event = notifier.pollEvent(5, TimeUnit.SECONDS);

        if (event != null) {
            logger.info("Received event: " + event.getEventType());
            logger.info("Event ID: " + event.getEventId());
            logger.info("Timestamp: " + event.getTimestamp());
            logger.info("Case ID: " + event.getCaseId());
            logger.info("Description: " + event.getDescription());
        } else {
            logger.info("No events available (timeout)");
        }
    }

    /**
     * Example 3: Batch processing pending events
     */
    public static void batchProcessing() {
        YawlEventNotifier notifier = new YawlEventNotifier();

        // Get all pending events without blocking
        var pendingEvents = notifier.getPendingEvents();

        logger.info("Processing " + pendingEvents.size() + " pending events");

        for (YawlEventNotifier.YawlEvent event : pendingEvents) {
            processEvent(event);
        }
    }

    /**
     * Example 4: Monitoring metrics
     */
    public static void monitoringMetrics() {
        YawlEventNotifier notifier = new YawlEventNotifier();

        // Get delivery metrics
        var metrics = notifier.getMetrics();

        logger.info("=== YawlEventNotifier Metrics ===");
        logger.info("Events Published: " + metrics.get("eventsPublished"));
        logger.info("Webhook Failures: " + metrics.get("webhookFailures"));
        logger.info("SSE Failures: " + metrics.get("sseFailures"));
        logger.info("Pending Events: " + metrics.get("pendingEvents"));
        logger.info("Webhook Endpoints: " + metrics.get("webhookEndpoints"));
        logger.info("SSE Clients: " + metrics.get("sseClients"));
        logger.info("WebSocket Sessions: " + metrics.get("webSocketSessions"));

        // Calculate success rate
        long published = metrics.get("eventsPublished");
        long failures = metrics.get("webhookFailures");
        if (published > 0) {
            double successRate = ((published - failures) / (double) published) * 100;
            logger.info("Webhook Success Rate: " + String.format("%.2f%%", successRate));
        }
    }

    /**
     * Example 5: Custom SSE emitter implementation
     */
    public static class SimpleSseEmitter implements YawlEventNotifier.SseEmitter {
        private boolean completed = false;

        @Override
        public void send(String data, String eventType) {
            if (!completed) {
                // In real implementation, this would write to HTTP response stream
                System.out.println("SSE Event [" + eventType + "]: " + data);
            }
        }

        @Override
        public void complete() {
            completed = true;
            System.out.println("SSE stream completed");
        }
    }

    /**
     * Example 6: Custom WebSocket session implementation
     */
    public static class SimpleWebSocketSession implements YawlEventNotifier.WebSocketSession {
        private boolean open = true;

        @Override
        public void send(String message) {
            if (open) {
                // In real implementation, this would send via WebSocket protocol
                System.out.println("WebSocket Message: " + message);
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            System.out.println("WebSocket session closed");
        }
    }

    /**
     * Example 7: Complete workflow monitoring setup
     */
    public static void completeSetup() throws YAWLException, YPersistenceException {
        // Create notifier
        YawlEventNotifier notifier = new YawlEventNotifier();

        // Register multiple webhook endpoints (load balancing, redundancy)
        notifier.registerWebhook("http://primary-server:9000/events");
        notifier.registerWebhook("http://backup-server:9000/events");

        // Register SSE clients (for web dashboard real-time updates)
        notifier.registerSseClient("dashboard-client-1", new SimpleSseEmitter());

        // Register WebSocket sessions (for mobile app notifications)
        notifier.registerWebSocket("mobile-session-1", new SimpleWebSocketSession());

        // Integrate with YAWL Engine
        EngineGateway gateway = new EngineGatewayImpl(true);
        gateway.registerObserverGateway(notifier);

        logger.info("Complete event notification system configured");
        logger.info("Events will be delivered via:");
        logger.info("  - HTTP Webhooks (POST)");
        logger.info("  - Server-Sent Events (SSE)");
        logger.info("  - WebSocket push");
        logger.info("  - In-memory queue (polling)");

        // Monitor metrics periodically
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // Every minute
                    monitoringMetrics();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    /**
     * Helper method to process an event
     */
    private static void processEvent(YawlEventNotifier.YawlEvent event) {
        logger.info("Processing event: " + event.getEventType() +
                   " (ID: " + event.getEventId() + ")");

        // Handle different event types
        switch (event.getEventType()) {
            case "case.started":
                logger.info("New case started: " + event.getCaseId());
                break;

            case "case.completed":
                logger.info("Case completed: " + event.getCaseId());
                break;

            case "workitem.enabled":
                logger.info("Work item enabled and ready");
                if (event.getWorkItem() != null) {
                    logger.info("Task: " + event.getWorkItem().getTaskID());
                }
                break;

            case "timer.expired":
                logger.info("Deadline approaching for work item");
                break;

            case "exception.deadlock":
                logger.severe("DEADLOCK DETECTED in case: " + event.getCaseId());
                break;

            default:
                logger.info("Event type: " + event.getEventType());
        }
    }

    /**
     * Main method demonstrating usage
     */
    public static void main(String[] args) {
        try {
            logger.info("=== YawlEventNotifier Examples ===\n");

            // Example 1: Basic integration
            logger.info("Example 1: Basic Integration");
            basicIntegration();

            // Example 2: Polling events
            logger.info("\nExample 2: Polling Events");
            pollingEvents();

            // Example 3: Batch processing
            logger.info("\nExample 3: Batch Processing");
            batchProcessing();

            // Example 4: Monitoring metrics
            logger.info("\nExample 4: Monitoring Metrics");
            monitoringMetrics();

            // Example 5-7: Complete setup
            logger.info("\nExample 7: Complete Setup");
            completeSetup();

            logger.info("\n=== Examples Complete ===");

        } catch (Exception e) {
            logger.severe("Error running examples: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
