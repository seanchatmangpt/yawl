/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.resourcing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceSpec;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.CoordinationCostProfile;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.engine.YWorkItemID;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the virtual-thread exception handling path in {@link ResourceManager}.
 *
 * <p>Specifically verifies that when {@link AgentRoutingException} is thrown inside the
 * virtual thread spawned by {@code handleWorkItemEvent}, the exception does NOT propagate
 * to the caller and {@link ResourceManager#getAgentDispatchFailureCount()} is incremented.
 *
 * <p>Chicago TDD: Uses real {@link AgentMarketplace} with a listing pointing to port 1
 * (TCP connection refused on any OS) to trigger a real {@link AgentRoutingException}.
 */
@Tag("unit")
class ResourceManagerExceptionTest {

    private AgentMarketplace marketplace;
    private CapabilityMatcher matcher;
    private ResourceManager resourceManager;

    @BeforeEach
    void setUp() {
        marketplace = new AgentMarketplace();
        // Port 1 listing: connection refused immediately, no network required
        AgentMarketplaceListing unreachable = buildListing(
                "agent-unreachable", "UnreachableAgent", "approval", "localhost", 1,
                0.10, Instant.now());
        marketplace.publish(unreachable);

        matcher = new CapabilityMatcher(marketplace, Double.MAX_VALUE, Long.MAX_VALUE,
                Duration.ofMinutes(5));
        resourceManager = new ResourceManager(matcher);
    }

    // -----------------------------------------------------------------------
    // Scenario 1: AgentRoutingException on virtual thread does not propagate
    // -----------------------------------------------------------------------

    @Test
    void handleWorkItemEvent_dispatchFails_exceptionDoesNotPropagateToCallerThread()
            throws Exception {
        YWorkItem workItem = workItemWithTask("approval");
        YWorkItemEvent event = new YWorkItemEvent(YEventType.ITEM_ENABLED, workItem);

        // Must not throw — virtual thread absorbs the AgentRoutingException
        assertDoesNotThrow(() -> resourceManager.handleWorkItemEvent(event),
                "AgentRoutingException on virtual thread must not propagate to caller");
    }

    // -----------------------------------------------------------------------
    // Scenario 2: agentDispatchCount incremented before dispatch attempt
    // -----------------------------------------------------------------------

    @Test
    void handleWorkItemEvent_agentRouted_agentDispatchCountIncremented() throws Exception {
        YWorkItem workItem = workItemWithTask("approval");
        YWorkItemEvent event = new YWorkItemEvent(YEventType.ITEM_ENABLED, workItem);

        resourceManager.handleWorkItemEvent(event);

        assertEquals(1, resourceManager.getAgentDispatchCount(),
                "agentDispatchCount must be incremented when an AgentRoute is chosen");
    }

    // -----------------------------------------------------------------------
    // Scenario 3: agentDispatchFailureCount incremented after virtual thread fails
    // -----------------------------------------------------------------------

    @Test
    void handleWorkItemEvent_dispatchFails_failureCountIncremented() throws Exception {
        YWorkItem workItem = workItemWithTask("approval");
        YWorkItemEvent event = new YWorkItemEvent(YEventType.ITEM_ENABLED, workItem);

        resourceManager.handleWorkItemEvent(event);

        // Virtual threads are lightweight but asynchronous; wait up to 5s for completion.
        long deadline = System.currentTimeMillis() + 5_000;
        while (resourceManager.getAgentDispatchFailureCount() == 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertEquals(1, resourceManager.getAgentDispatchFailureCount(),
                "agentDispatchFailureCount must be incremented when dispatch throws AgentRoutingException");
    }

    // -----------------------------------------------------------------------
    // Scenario 4: humanRouteCount unaffected when agent is routed
    // -----------------------------------------------------------------------

    @Test
    void handleWorkItemEvent_agentRouted_humanRouteCountUnchanged() throws Exception {
        YWorkItem workItem = workItemWithTask("approval");
        YWorkItemEvent event = new YWorkItemEvent(YEventType.ITEM_ENABLED, workItem);

        resourceManager.handleWorkItemEvent(event);

        assertEquals(0, resourceManager.getHumanRouteCount(),
                "humanRouteCount must remain zero when an AgentRoute is chosen");
    }

    // -----------------------------------------------------------------------
    // Scenario 5: multiple failed dispatches accumulate in failure count
    // -----------------------------------------------------------------------

    @Test
    void handleWorkItemEvent_multipleFailures_failureCountAccumulates() throws Exception {
        for (int i = 0; i < 3; i++) {
            YWorkItem workItem = workItemWithTask("approval");
            YWorkItemEvent event = new YWorkItemEvent(YEventType.ITEM_ENABLED, workItem);
            resourceManager.handleWorkItemEvent(event);
        }

        // Wait for all 3 virtual threads to complete
        long deadline = System.currentTimeMillis() + 10_000;
        while (resourceManager.getAgentDispatchFailureCount() < 3
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertEquals(3, resourceManager.getAgentDispatchFailureCount(),
                "Each failed dispatch must increment agentDispatchFailureCount");
        assertEquals(3, resourceManager.getAgentDispatchCount(),
                "Each dispatch attempt must increment agentDispatchCount");
    }

    // -----------------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------------

    private static YWorkItem workItemWithTask(String taskId) throws Exception {
        YWorkItem item = new YWorkItem();
        YIdentifier caseId = new YIdentifier("case-test");
        YWorkItemID wid = new YWorkItemID(caseId, taskId);
        Field f = YWorkItem.class.getDeclaredField("_workItemID");
        f.setAccessible(true);
        f.set(item, wid);
        return item;
    }

    private static AgentMarketplaceListing buildListing(String id, String name,
                                                         String domain, String host, int port,
                                                         double cost, Instant heartbeatAt) {
        AgentInfo info = new AgentInfo(id, name, List.of(domain), host, port);
        AgentCapability capability = new AgentCapability(domain, domain + " processing");
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0", capability)
                .costProfile(CoordinationCostProfile.pureInference(cost))
                .build();
        Instant registeredAt = Instant.now().minus(Duration.ofMinutes(1));
        return new AgentMarketplaceListing(info, spec, registeredAt, heartbeatAt);
    }
}
