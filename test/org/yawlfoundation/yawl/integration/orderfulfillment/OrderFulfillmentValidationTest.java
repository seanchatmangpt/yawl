/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.orderfulfillment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive order fulfillment integration validation tests.
 * Tests AI agent coordination, capacity checking, and decision reasoning.
 */
@TestMethodOrder(OrderAnnotation.class)
public class OrderFulfillmentValidationTest {

    private Pm4PyClient pm4pyClient;
    private AgentRegistry agentRegistry;
    private CapacityChecker capacityChecker;
    private DecisionWorkflow decisionWorkflow;
    private static final String AGENT_URL = "http://localhost:9092";

    @BeforeEach
    void setUp() {
        pm4pyClient = new Pm4PyClient(AGENT_URL);
        agentRegistry = new AgentRegistry();
        capacityChecker = new CapacityChecker();
        decisionWorkflow = new DecisionWorkflow();
    }

    @Test
    @Order(1)
    @DisplayName("Order Fulfillment: AI Agent Coordination")
    void testAIAgentCoordination() throws Exception {
        // Register AI agents
        AgentCapability capability1 = new AgentCapability(
            "inventory_check",
            "Check inventory availability",
            List.of("low_stock", "out_of_stock")
        );

        AgentCapability capability2 = new AgentCapability(
            "shipping_calculation",
            "Calculate shipping options",
            List.of("express", "standard", "economy")
        );

        agentRegistry.registerAgent("inventory-agent", capability1);
        agentRegistry.registerAgent("shipping-agent", capability2);

        // Simulate order processing
        Map<String, Object> order = Map.of(
            "orderId", "order-123",
            "items", List.of(
                Map.of("productId", "prod-1", "quantity", 2),
                Map.of("productId", "prod-2", "quantity", 1)
            )
        );

        // Execute workflow
        OrderResult result = decisionWorkflow.execute(order);

        // Validate coordination
        assertNotNull(result, "Order processing should produce a result");
        assertEquals("order-123", result.getOrderId(), "Order ID should match");

        // Verify agent utilization
        List<AgentExecution> executions = result.getAgentExecutions();
        assertFalse(executions.isEmpty(), "Should have agent executions");

        System.out.printf("✅ AI Agent Coordination: %d agents executed%n",
            executions.size());
    }

    @Test
    @Order(2)
    @DisplayName("Order Fulfillment: Capacity Checking")
    void testCapacityChecking() {
        // Simulate capacity scenarios
        AgentCapacity inventoryCapacity = new AgentCapacity(
            "inventory-agent",
            100,  // max capacity
            75,   // current load
            20     // max concurrent tasks
        );

        // Check capacity before assignment
        assertTrue(capacityChecker.canAcceptTask(inventoryCapacity),
            "Agent should accept task under capacity");

        // Simulate load increase
        AgentCapacity overloadedCapacity = new AgentCapacity(
            "inventory-agent",
            100,
            99,
            20
        );

        assertFalse(capacityChecker.canAcceptTask(overloadedCapacity),
            "Overloaded agent should reject new tasks");

        // Test graceful degradation
        String[] alternativeAgents = capacityChecker.findAlternativeAgents(
            "inventory-agent", Map.of("category", "inventory"));

        assertNotNull(alternativeAgents, "Should find alternative agents");
        assertTrue(alternativeAgents.length > 0, "Should have alternatives");

        System.out.println("✅ Capacity checking validated");
    }

    @Test
    @Order(3)
    @DisplayName("Order Fulfillment: Decision Reasoning")
    void testDecisionReasoning() {
        // Test decision logic for shipping options
        Map<String, Object> order1 = Map.of(
            "orderId", "order-1",
            "customerLocation", "USA",
            "items", List.of(Map.of("weight", 5.0, "fragile", false)),
            "value", 100.0
        );

        DecisionResult decision1 = decisionWorkflow.makeShippingDecision(order1);
        assertEquals("standard", decision1.getShippingMethod(),
            "Standard shipping for normal order");

        // Test premium customer logic
        Map<String, Object> order2 = Map.of(
            "orderId", "order-2",
            "customerType", "premium",
            "items", List.of(Map.of("weight", 10.0, "fragile", true)),
            "value", 500.0
        );

        DecisionResult decision2 = decisionWorkflow.makeShippingDecision(order2);
        assertEquals("express", decision2.getShippingMethod(),
            "Express shipping for premium customer");

        // Test high-value order security
        Map<String, Object> order3 = Map.of(
            "orderId", "order-3",
            "value", 5000.0,
            "items", List.of(Map.of("weight", 20.0, "high_value", true))
        );

        DecisionResult decision3 = decisionWorkflow.makeShippingDecision(order3);
        assertTrue(decision3.requiresSignature(),
            "High-value order requires signature");

        System.out.println("✅ Decision reasoning validation passed");
    }

    @Test
    @Order(4)
    @DisplayName("Order Fulfillment: XES Export for Process Mining")
    void testXesExportForProcessMining() {
        // Generate order fulfillment events
        List<WorkflowEvent> events = generateOrderFulfillmentEvents();

        // Export to XES
        String xesExport = EventLogExporter.exportToXes(events);

        // Validate XES format
        assertNotNull(xesExport, "XES export should not be null");
        assertTrue(xesExport.contains("<log>"), "Should contain log element");
        assertTrue(xesExport.contains("order_started"), "Should contain order events");
        assertTrue(xesExport.contains("inventory_checked"), "Should contain inventory events");
        assertTrue(xesExport.contains("shipped"), "Should contain shipping events");

        // Validate for PM4Py processing
        String pm4pyResult = pm4pyClient.call("process_mining", xesExport);
        assertNotNull(pm4pyResult, "PM4Py should process the XES log");

        System.out.println("✅ XES export for process mining validated");
    }

    @Test
    @Order(5)
    @DisplayName("Order Fulfillment: Real-time Order Tracking")
    void testRealTimeOrderTracking() {
        // Create order tracking
        OrderTracking tracking = new OrderTracking("order-123");

        // Add status updates
        tracking.addStatusUpdate("order_placed", Instant.now());
        tracking.addStatusUpdate("inventory_checked", Instant.now().plusSeconds(5));
        tracking.addStatusUpdate("packed", Instant.now().plusSeconds(10));
        tracking.addStatusUpdate("shipped", Instant.now().plusSeconds(15));

        // Verify tracking data
        assertEquals("shipped", tracking.getCurrentStatus(),
            "Current status should be shipped");

        // Calculate total processing time
        long processingTime = tracking.getTotalProcessingTime();
        assertTrue(processingTime > 0, "Processing time should be positive");

        // Query status at specific time
        OrderStatus status = tracking.getStatusAtTime(
            Instant.now().plusSeconds(8));
        assertEquals("packed", status.getStatus(),
            "Status at time should be packed");

        System.out.printf("✅ Real-time order tracking: %dms total%n",
            processingTime);
    }

    @Test
    @Order(6)
    @DisplayName("Order Fulfillment: Multi-Agent Circuit Breaker")
    void testCircuitBreaker() throws Exception {
        // Simulate agent failures
        agentRegistry.registerAgent("unavailable-agent", new AgentCapability(
            "broken_service", "Broken service", List.of()
        ));

        // Mark agent as unavailable
        agentRegistry.markUnavailable("unavailable-agent");

        // Test circuit breaker behavior
        Map<String, Object> order = Map.of(
            "orderId", "order-cb-test",
            "items", List.of()
        );

        assertThrows(NoAvailableAgentsException.class, () -> {
            decisionWorkflow.executeWithFallback(order);
        }, "Should fail when no agents available");

        // Test fallback mechanism
        decisionWorkflow.registerFallback("unavailable-agent", "backup-agent");
        OrderResult fallbackResult = decisionWorkflow.executeWithFallback(order);

        assertNotNull(fallbackResult, "Should have fallback result");

        System.out.println("✅ Multi-agent circuit breaker validated");
    }

    // Helper classes and methods

    private List<WorkflowEvent> generateOrderFulfillmentEvents() {
        return List.of(
            new WorkflowEvent(
                UUID.randomUUID().toString(),
                "order_started",
                Instant.now(),
                Map.of("orderId", "order-123", "customer", "test-customer")
            ),
            new WorkflowEvent(
                UUID.randomUUID().toString(),
                "inventory_checked",
                Instant.now().plusSeconds(5),
                Map.of("orderId", "order-123", "available", true)
            ),
            new WorkflowEvent(
                UUID.randomUUID().toString(),
                "order_packed",
                Instant.now().plusSeconds(10),
                Map.of("orderId", "order-123", "weight", 2.5)
            ),
            new WorkflowEvent(
                UUID.randomUUID().toString(),
                "order_shipped",
                Instant.now().plusSeconds(15),
                Map.of("orderId", "order-123", "trackingId", "TRK123")
            )
        );
    }

    // Helper classes for testing

    public static class OrderResult {
        private final String orderId;
        private final List<AgentExecution> agentExecutions;
        private final String status;

        public OrderResult(String orderId, List<AgentExecution> agentExecutions, String status) {
            this.orderId = orderId;
            this.agentExecutions = agentExecutions;
            this.status = status;
        }

        public String getOrderId() { return orderId; }
        public List<AgentExecution> getAgentExecutions() { return agentExecutions; }
        public String getStatus() { return status; }
    }

    public static class AgentExecution {
        private final String agentId;
        private final String task;
        private final Instant startTime;
        private final Instant endTime;

        public AgentExecution(String agentId, String task, Instant startTime, Instant endTime) {
            this.agentId = agentId;
            this.task = task;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getAgentId() { return agentId; }
        public String getTask() { return task; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
    }

    public static class DecisionResult {
        private final String shippingMethod;
        private final boolean requiresSignature;
        private final String reasoning;

        public DecisionResult(String shippingMethod, boolean requiresSignature, String reasoning) {
            this.shippingMethod = shippingMethod;
            this.requiresSignature = requiresSignature;
            this.reasoning = reasoning;
        }

        public String getShippingMethod() { return shippingMethod; }
        public boolean requiresSignature() { return requiresSignature; }
        public String getReasoning() { return reasoning; }
    }

    public static class AgentCapacity {
        private final String agentId;
        private final int maxCapacity;
        private final int currentLoad;
        private final int maxConcurrentTasks;

        public AgentCapacity(String agentId, int maxCapacity, int currentLoad, int maxConcurrentTasks) {
            this.agentId = agentId;
            this.maxCapacity = maxCapacity;
            this.currentLoad = currentLoad;
            this.maxConcurrentTasks = maxConcurrentTasks;
        }

        public String getAgentId() { return agentId; }
        public int getMaxCapacity() { return maxCapacity; }
        public int getCurrentLoad() { return currentLoad; }
        public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
    }

    public static class OrderTracking {
        private final String orderId;
        private final List<OrderStatus> statusUpdates;

        public OrderTracking(String orderId) {
            this.orderId = orderId;
            this.statusUpdates = new ArrayList<>();
        }

        public void addStatusUpdate(String status, Instant timestamp) {
            statusUpdates.add(new OrderStatus(status, timestamp));
        }

        public String getCurrentStatus() {
            if (statusUpdates.isEmpty()) return null;
            return statusUpdates.get(statusUpdates.size() - 1).getStatus();
        }

        public long getTotalProcessingTime() {
            if (statusUpdates.size() < 2) return 0;
            return Duration.between(
                statusUpdates.get(0).getTimestamp(),
                statusUpdates.get(statusUpdates.size() - 1).getTimestamp()
            ).toMillis();
        }

        public OrderStatus getStatusAtTime(Instant timestamp) {
            for (OrderStatus status : statusUpdates) {
                if (status.getTimestamp().isAfter(timestamp)) {
                    return status;
                }
            }
            return statusUpdates.get(statusUpdates.size() - 1);
        }
    }

    public static class OrderStatus {
        private final String status;
        private final Instant timestamp;

        public OrderStatus(String status, Instant timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }

        public String getStatus() { return status; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class WorkflowEvent {
        private final String eventId;
        private final String eventType;
        private final Instant timestamp;
        private final Map<String, Object> data;

        public WorkflowEvent(String eventId, String eventType, Instant timestamp, Map<String, Object> data) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.timestamp = timestamp;
            this.data = data;
        }

        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, Object> getData() { return data; }
    }

    public static class NoAvailableAgentsException extends Exception {
        public NoAvailableAgentsException(String message) {
            super(message);
        }
    }
}