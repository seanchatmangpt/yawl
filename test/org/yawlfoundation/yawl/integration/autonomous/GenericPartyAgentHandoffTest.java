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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffResult;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the classifyHandoffIfNeeded method in GenericPartyAgent.
 * Chicago TDD implementation using real service implementations instead of mocks.
 *
 * <p>This test validates that the method properly handles agent classification
 * and handoff using real implementations of all dependencies.</p>
 */
class GenericPartyAgentHandoffTest {

    private TestAgentRegistryClient registryClient;
    private TestHandoffService handoffService;
    private AgentConfiguration config;
    private GenericPartyAgent agent;

    @BeforeEach
    void setUp() {
        // Create real implementations of dependencies
        registryClient = new TestAgentRegistryClient();
        handoffService = new TestHandoffService();

        // Setup configuration with real implementations
        AgentCapability capability = new AgentCapability("test-domain", "Test Domain", "test-description");

        config = AgentConfiguration.builder("test-agent", "http://localhost:8080", "test-user", "test-password")
            .capability(capability)
            .registryClient(registryClient)
            .handoffService(handoffService)
            .build();

        // Create agent with real configuration
        agent = new GenericPartyAgent(config) {
            // Override the discovery loop to prevent it from starting
            @Override
            protected void startDiscoveryLoop() {
                // Do nothing for testing
            }
        };
    }

    @Test
    void testClassifyHandoffIfNeeded_NoSubstituteAgents() throws Exception {
        // Setup: registry returns only the current agent
        registryClient.registerAgent("test-agent", "Test Agent", "localhost", 8080, "test-domain");

        // Execute & Verify: should throw HandoffException
        HandoffException exception = assertThrows(
            HandoffException.class,
            () -> {
                // Use reflection to access private method
                java.lang.reflect.Method method = GenericPartyAgent.class.getDeclaredMethod(
                    "classifyHandoffIfNeeded", String.class, String.class);
                method.setAccessible(true);
                method.invoke(agent, "work-item-123", "session-handle");
            }
        );

        assertEquals("No substitute agents available for work item: work-item-123", exception.getMessage());
    }

    @Test
    void testClassifyHandoffIfNeeded_HandoffSucceeds() throws Exception {
        // Setup: registry returns substitute agents
        registryClient.registerAgent("test-agent", "Test Agent", "localhost", 8080, "test-domain");
        registryClient.registerAgent("substitute-agent", "Substitute Agent", "localhost", 8081, "test-domain");

        // Configure handoff service to succeed
        handoffService.setHandoffResult(new HandoffResult(true, "Handoff accepted"));

        // Execute: should not throw exception
        java.lang.reflect.Method method = GenericPartyAgent.class.getDeclaredMethod(
            "classifyHandoffIfNeeded", String.class, String.class);
        method.setAccessible(true);
        method.invoke(agent, "work-item-123", "session-handle");

        // Verify handoff service was called
        assertTrue(handoffService.wasCalled(), "Handoff service should have been called");
        assertEquals("work-item-123", handoffService.getLastWorkItemId(), "Correct work item ID should be passed");
    }

    @Test
    void testClassifyHandoffIfNeeded_HandoffFails() throws Exception {
        // Setup: registry returns substitute agents
        registryClient.registerAgent("test-agent", "Test Agent", "localhost", 8080, "test-domain");
        registryClient.registerAgent("substitute-agent", "Substitute Agent", "localhost", 8081, "test-domain");

        // Configure handoff service to fail
        handoffService.setHandoffResult(new HandoffResult(false, "Handoff rejected"));

        // Execute & Verify: should throw HandoffException
        HandoffException exception = assertThrows(
            HandoffException.class,
            () -> {
                java.lang.reflect.Method method = GenericPartyAgent.class.getDeclaredMethod(
                    "classifyHandoffIfNeeded", String.class, String.class);
                method.setAccessible(true);
                method.invoke(agent, "work-item-123", "session-handle");
            }
        );

        assertEquals("Handoff rejected: Handoff rejected", exception.getMessage());
        assertTrue(handoffService.wasCalled(), "Handoff service should have been called");
    }

    /**
     * Real implementation of AgentRegistryClient for testing.
     * Uses in-memory storage instead of external service calls.
     */
    static class TestAgentRegistryClient extends AgentRegistryClient {
        private boolean initialized = false;

        public TestAgentRegistryClient() {
            super();
        }

        /**
         * Helper method to register an agent for testing.
         */
        public void registerAgent(String id, String name, String host, int port, String domain) {
            AgentInfo agentInfo = new AgentInfo(id, name, host, port, domain);
            super.register(agentInfo);
        }

        /**
         * Override to ensure registry is in a known state.
         */
        @Override
        public List<AgentInfo> findAgentsByCapability(String capability) {
            if (!initialized) {
                // Initialize with default test data
                registerAgent("test-agent", "Test Agent", "localhost", 8080, "test-domain");
                initialized = true;
            }
            return super.findAgentsByCapability(capability);
        }
    }

    /**
     * Real implementation of HandoffRequestService for testing.
     * Provides controlled behavior for testing handoff scenarios.
     */
    static class TestHandoffService extends HandoffRequestService {
        private HandoffResult handoffResult;
        private boolean called = false;
        private String lastWorkItemId;

        public TestHandoffService() {
            // Simple constructor for testing with minimal dependencies
            super(null, null, null, "test-session");
        }

        /**
         * Set the result for the next handoff call.
         */
        public void setHandoffResult(HandoffResult result) {
            this.handoffResult = result;
        }

        /**
         * Check if the service was called.
         */
        public boolean wasCalled() {
            return called;
        }

        /**
         * Get the last work item ID passed to the service.
         */
        public String getLastWorkItemId() {
            return lastWorkItemId;
        }

        /**
         * Override handoff initiation to provide controlled test behavior.
         */
        @Override
        public CompletableFuture<HandoffResult> initiateHandoff(String workItemId, String fromAgentId) {
            called = true;
            lastWorkItemId = workItemId;

            if (handoffResult != null) {
                return CompletableFuture.completedFuture(handoffResult);
            }

            // Default to success if no result is set
            return CompletableFuture.completedFuture(new HandoffResult(true, "Default success"));
        }
    }
}