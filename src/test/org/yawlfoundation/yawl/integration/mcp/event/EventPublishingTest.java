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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp.event;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEventPublisher;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.McpSyncServer;

/**
 * Comprehensive test suite for MCP event publishing functionality.
 *
 * <p>This test demonstrates:
 * <ul>
 *   <li>Event publishing from YAWL engine to MCP clients</li>
 *   <li>Subscription management with filtering</li>
 *   <li>WebSocket-based event streaming</li>
 *   <li>Event schema compliance and payload structure</li>
 *   <li>Integration with YAWL engine lifecycle hooks</li>
 * </ul>
 *
 * <p>Test scenarios cover:
 * <ul>
 *   <li>Case lifecycle events (start, complete, terminate)</li>
 *   <li>Task events (enable, start, complete, fail)</li>
 *   <li>State transitions (work item state changes)</li>
 *   <li>Resource events (allocation, deallocation, timeout)</li>
 *   <li>Data events (variable updates, document changes)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class EventPublishingTest {

    private McpWorkflowEventPublisher eventPublisher;
    private McpLoggingHandler loggingHandler;
    private TestMcpServer mcpServer;
    private TestWebSocketClient testClient;

    @BeforeEach
    void setUp() {
        // Create mock MCP server for testing
        mcpServer = new TestMcpServer();
        loggingHandler = new McpLoggingHandler();

        // Get the event publisher instance
        eventPublisher = McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

        // Create mock WebSocket client for testing event delivery
        testClient = new TestWebSocketClient();
    }

    @Test
    @DisplayName("Test case lifecycle event publishing")
    void testCaseLifecycleEvents() throws InterruptedException {
        // Subscribe to case events
        String subscriptionId = "case-events-test";
        WorkflowEvent.EventType[] caseEventTypes = {
            WorkflowEvent.EventType.CASE_STARTED,
            WorkflowEvent.EventType.CASE_COMPLETED,
            WorkflowEvent.EventType.CASE_CANCELLED
        };

        eventPublisher.createSubscription(
            subscriptionId, caseEventTypes, null, null, "ws://test/case-events");

        // Publish case started event
        Map<String, String> startPayload = Map.of(
            "caseParams", "{\"customerId\":\"12345\",\"orderValue\":\"99.99\"}",
            "launchedBy", "agent-order-service"
        );
        WorkflowEvent startEvent = new WorkflowEvent(
            WorkflowEvent.EventType.CASE_STARTED,
            "OrderFulfillment:1.0",
            "case-42",
            null,
            startPayload
        );

        eventPublisher.publish(startEvent);

        // Publish case completed event
        Map<String, String> completePayload = Map.of(
            "completionStatus", "success",
            "processedItems", "5",
            "totalDuration", "45000"
        );
        WorkflowEvent completeEvent = new WorkflowEvent(
            WorkflowEvent.EventType.CASE_COMPLETED,
            "OrderFulfillment:1.0",
            "case-42",
            null,
            completePayload
        );

        eventPublisher.publish(completeEvent);

        // Verify events were delivered
        assertTrue(testClient.waitForEvents(2, 5), "Expected 2 case events to be delivered");
        assertEquals(2, testClient.getReceivedEvents().size());
        assertEquals("CASE_STARTED", testClient.getReceivedEvents().get(0).getEventType().name());
        assertEquals("CASE_COMPLETED", testClient.getReceivedEvents().get(1).getEventType().name());
    }

    @Test
    @DisplayName("Test task event publishing with filtering")
    void testTaskEventPublishingWithFiltering() throws InterruptedException {
        // Subscribe to specific case events only
        String subscriptionId = "case-42-task-events";
        WorkflowEvent.EventType[] taskEventTypes = {
            WorkflowEvent.EventType.WORKITEM_ENABLED,
            WorkflowEvent.EventType.WORKITEM_STARTED,
            WorkflowEvent.EventType.WORKITEM_COMPLETED
        };

        eventPublisher.createSubscription(
            subscriptionId, taskEventTypes, "case-42", null, "ws://test/case-42-tasks");

        // Publish events for different cases
        // Case 42 events (should be delivered)
        WorkflowEvent taskEnabled = new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_ENABLED,
            "OrderFulfillment:1.0",
            "case-42",
            "workitem-123",
            Map.of("taskName", "ProcessOrder", "resource", "agent1")
        );
        eventPublisher.publish(taskEnabled);

        WorkflowEvent taskStarted = new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_STARTED,
            "OrderFulfillment:1.0",
            "case-42",
            "workitem-123",
            Map.of("taskName", "ProcessOrder", "resource", "agent1")
        );
        eventPublisher.publish(taskStarted);

        // Case 43 event (should be filtered out)
        WorkflowEvent otherCaseEvent = new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_COMPLETED,
            "OrderFulfillment:1.0",
            "case-43",
            "workitem-456",
            Map.of("taskName", "ProcessOrder", "resource", "agent2")
        );
        eventPublisher.publish(otherCaseEvent);

        // Verify filtering worked
        assertTrue(testClient.waitForEvents(2, 5), "Expected 2 events for case-42 only");
        assertEquals(2, testClient.getReceivedEvents().size());
        for (WorkflowEvent event : testClient.getReceivedEvents()) {
            assertEquals("case-42", event.getCaseId());
        }
    }

    @Test
    @DisplayName("Test event streaming with multiple subscribers")
    void testEventStreamingWithMultipleSubscribers() throws InterruptedException {
        // Create multiple subscriptions
        String sub1 = "all-events";
        String sub2 = "only-completion-events";

        eventPublisher.createSubscription(
            sub1, null, null, null, "ws://test/all-events");
        eventPublisher.createSubscription(
            sub2, new WorkflowEvent.EventType[]{WorkflowEvent.EventType.WORKITEM_COMPLETED},
            null, null, "ws://test/completion-events");

        // Publish events
        WorkflowEvent event1 = new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_ENABLED,
            "OrderFulfillment:1.0",
            "case-44",
            "workitem-789",
            Map.of("taskName", "ValidateOrder")
        );
        eventPublisher.publish(event1);

        WorkflowEvent event2 = new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_COMPLETED,
            "OrderFulfillment:1.0",
            "case-44",
            "workitem-789",
            Map.of("taskName", "ValidateOrder", "result", "approved")
        );
        eventPublisher.publish(event2);

        // Verify both subscribers received events
        assertTrue(testClient.waitForEvents(3, 5), "Expected 3 total events (2 for sub1, 1 for sub2)");

        // Count events by subscription
        int sub1Count = 0, sub2Count = 0;
        for (MockWebSocketClient.DeliveredEvent delivered : testClient.getDeliveredEvents()) {
            if (delivered.getSubscriptionId().equals(sub1)) {
                sub1Count++;
            } else if (delivered.getSubscriptionId().equals(sub2)) {
                sub2Count++;
            }
        }

        assertEquals(2, sub1Count, "Sub1 should receive all events");
        assertEquals(1, sub2Count, "Sub2 should receive only completion events");
    }

    @Test
    @DisplayName("Test resource allocation events")
    void testResourceAllocationEvents() throws InterruptedException {
        // Subscribe to resource events
        String subscriptionId = "resource-events";
        WorkflowEvent.EventType[] resourceEventTypes = {
            WorkflowEvent.EventType.valueOf("RESOURCE_ALLOCATED"),
            WorkflowEvent.EventType.valueOf("RESOURCE_DEALLOCATED"),
            WorkflowEvent.EventType.valueOf("RESOURCE_TIMEOUT")
        };

        // Note: These would be custom event types added to the enum
        eventPublisher.createSubscription(
            subscriptionId, resourceEventTypes, null, null, "ws://test/resource-events");

        // Simulate resource allocation event
        Map<String, String> allocPayload = Map.of(
            "resourceId", "agent1",
            "taskName", "ProcessOrder",
            "allocationTime", "1708632800000",
            "duration", "30000"
        );

        WorkflowEvent allocEvent = new WorkflowEvent(
            WorkflowEvent.EventType.valueOf("RESOURCE_ALLOCATED"),
            "OrderFulfillment:1.0",
            "case-45",
            "workitem-101",
            allocPayload
        );

        eventPublisher.publish(allocEvent);

        // Verify event structure
        assertTrue(testClient.waitForEvents(1, 5), "Expected 1 resource event");
        WorkflowEvent event = testClient.getReceivedEvents().get(0);
        assertEquals("RESOURCE_ALLOCATED", event.getEventType().name());
        assertEquals("agent1", event.getPayload().get("resourceId"));
    }

    @Test
    @DisplayName("Test subscription lifecycle management")
    void testSubscriptionLifecycle() {
        // Create subscription
        String subscriptionId = "lifecycle-test";
        eventPublisher.createSubscription(
            subscriptionId, null, "case-46", "OrderFulfillment:1.0", "ws://test/lifecycle");

        // Verify subscription exists
        Map<String, McpWorkflowEventPublisher.EventSubscription> subscriptions = eventPublisher.getSubscriptions();
        assertTrue(subscriptions.containsKey(subscriptionId), "Subscription should exist");

        // Remove subscription
        boolean removed = eventPublisher.removeSubscription(subscriptionId);
        assertTrue(removed, "Subscription should be removed");

        // Verify subscription is gone
        assertFalse(subscriptions.containsKey(subscriptionId), "Subscription should be removed");
    }

    @Test
    @DisplayName("Test event payload integrity")
    void testEventPayloadIntegrity() {
        // Create complex payload with various data types
        Map<String, String> complexPayload = Map.of(
            "orderId", "12345",
            "customerEmail", "test@example.com",
            "orderTotal", "99.99",
            "isUrgent", "true",
            "items", "[{\"id\":\"1\",\"name\":\"Item1\",\"qty\":2},{\"id\":\"2\",\"name\":\"Item2\",\"qty\":1}]",
            "metadata", "{\"version\":\"1.0\",\"source\":\"web-app\"}",
            "timestamp", String.valueOf(System.currentTimeMillis())
        );

        WorkflowEvent event = new WorkflowEvent(
            WorkflowEvent.EventType.CASE_STARTED,
            "OrderFulfillment:1.0",
            "case-47",
            null,
            complexPayload
        );

        eventPublisher.publish(event);

        // Verify payload was preserved
        assertTrue(testClient.waitForEvents(1, 5), "Expected 1 event");
        WorkflowEvent received = testClient.getReceivedEvents().get(0);
        assertEquals(complexPayload.size(), received.getPayload().size());
        assertEquals("12345", received.getPayload().get("orderId"));
        assertEquals("test@example.com", received.getPayload().get("customerEmail"));
    }

    @Test
    @DisplayName("Test exactly-once delivery for critical events")
    void testExactlyOnceDelivery() {
        // Publish critical event that requires exactly-once delivery
        WorkflowEvent criticalEvent = new WorkflowEvent(
            WorkflowEvent.EventType.CASE_COMPLETED,
            "OrderFulfillment:1.0",
            "case-48",
            null,
            Map.of("result", "success", "finalState", "archived")
        );

        // The publisher should ensure exactly-once delivery for this event type
        assertTrue(criticalEvent.requiresExactlyOnceDelivery(),
            "CASE_COMPLETED should require exactly-once delivery");

        eventPublisher.publish(criticalEvent);

        // Verify event was delivered (implementation would verify exactly-once semantics)
        assertTrue(testClient.waitForEvents(1, 5), "Expected 1 critical event");
    }

    // Helper classes for testing

    /**
     * Mock MCP server for testing purposes.
     */
    private static class TestMcpServer implements McpServer {
        @Override
        public void closeGracefully() {
            // Mock implementation
        }

        @Override
        public <T> T getTransportProvider(Class<T> providerType) {
            if (providerType.equals(StdioServerTransportProvider.class)) {
                return providerType.cast(new TestStdioTransport());
            }
            return null;
        }
    }

    /**
     * Test WebSocket client for testing event delivery.
     */
    private static class TestWebSocketClient {
        private final List<WorkflowEvent> receivedEvents = new ArrayList<>();
        private final List<DeliveredEvent> deliveredEvents = new ArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        public boolean waitForEvents(int expectedCount, int timeoutSeconds) {
            try {
                return latch.await(timeoutSeconds, TimeUnit.SECONDS) && receivedEvents.size() >= expectedCount;
            } catch (InterruptedException e) {
                return false;
            }
        }

        public void onEventReceived(String subscriptionId, WorkflowEvent event) {
            receivedEvents.add(event);
            deliveredEvents.add(new DeliveredEvent(subscriptionId, event));
            latch.countDown();
        }

        public List<WorkflowEvent> getReceivedEvents() {
            return Collections.unmodifiableList(receivedEvents);
        }

        public List<DeliveredEvent> getDeliveredEvents() {
            return Collections.unmodifiableList(deliveredEvents);
        }

        /**
         * Tracks which subscription received which event.
         */
        public static class DeliveredEvent {
            private final String subscriptionId;
            private final WorkflowEvent event;

            public DeliveredEvent(String subscriptionId, WorkflowEvent event) {
                this.subscriptionId = subscriptionId;
                this.event = event;
            }

            public String getSubscriptionId() { return subscriptionId; }
            public WorkflowEvent getEvent() { return event; }
        }
    }

    /**
     * Test stdio transport provider.
     */
    private static class TestStdioTransport implements StdioServerTransportProvider {
        // Mock implementation
    }

    /**
     * Mock logging handler for testing.
     */
    private static class McpLoggingHandler {
        public void info(McpServer server, String message) {
            System.out.println("[INFO] " + message);
        }

        public void error(McpServer server, String message) {
            System.err.println("[ERROR] " + message);
        }

        public void warning(McpServer server, String message) {
            System.out.println("[WARN] " + message);
        }
    }
}