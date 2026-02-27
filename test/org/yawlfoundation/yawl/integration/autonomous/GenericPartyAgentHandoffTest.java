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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffRequestService;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for the classifyHandoffIfNeeded method in GenericPartyAgent.
 * Validates that the method properly handles agent classification and handoff.
 */
class GenericPartyAgentHandoffTest {

    @Mock
    private AgentConfiguration config;

    @Mock
    private AgentRegistryClient registryClient;

    @Mock
    private HandoffRequestService handoffService;

    @Mock
    private WorkItemRecord workItem;

    private GenericPartyAgent agent;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Setup configuration
        AgentCapability capability = new AgentCapability("test-domain", "Test Domain", "test-description");
        when(config.getCapability()).thenReturn(capability);
        when(config.getAgentName()).thenReturn("test-agent");
        when(config.registryClient()).thenReturn(registryClient);
        when(config.handoffService()).thenReturn(handoffService);

        // Create agent with mocked configuration
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
        AgentInfo currentAgent = new AgentInfo("test-agent", "Test Agent", "localhost", 8080, "test-domain");
        when(registryClient.findAgentsByCapability("test-domain"))
            .thenReturn(List.of(currentAgent));

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
        AgentInfo substituteAgent = new AgentInfo("substitute-agent", "Substitute Agent", "localhost", 8081, "test-domain");
        when(registryClient.findAgentsByCapability("test-domain"))
            .thenReturn(List.of(new AgentInfo("test-agent", "Test Agent", "localhost", 8080, "test-domain"), substituteAgent));

        // Mock successful handoff result
        HandoffRequestService.HandoffResult successResult = new HandoffRequestService.HandoffResult(true, "Handoff accepted");
        when(handoffService.initiateHandoff("work-item-123", "test-agent"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(successResult));

        // Execute: should not throw exception
        java.lang.reflect.Method method = GenericPartyAgent.class.getDeclaredMethod(
            "classifyHandoffIfNeeded", String.class, String.class);
        method.setAccessible(true);
        method.invoke(agent, "work-item-123", "session-handle");

        // Verify handoff service was called
        verify(handoffService).initiateHandoff("work-item-123", "test-agent");
    }

    @Test
    void testClassifyHandoffIfNeeded_HandoffFails() throws Exception {
        // Setup: registry returns substitute agents
        AgentInfo substituteAgent = new AgentInfo("substitute-agent", "Substitute Agent", "localhost", 8081, "test-domain");
        when(registryClient.findAgentsByCapability("test-domain"))
            .thenReturn(List.of(new AgentInfo("test-agent", "Test Agent", "localhost", 8080, "test-domain"), substituteAgent));

        // Mock failed handoff result
        HandoffRequestService.HandoffResult failureResult = new HandoffRequestService.HandoffResult(false, "Handoff rejected");
        when(handoffService.initiateHandoff("work-item-123", "test-agent"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(failureResult));

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
    }
}