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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceSpec;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.CoordinationCostProfile;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.TransitionSlotQuery;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CapabilityMatcher} and {@link ResourceManager}: marketplace routing,
 * economic bounds, staleness, and no-silent-fallback dispatch invariant.
 *
 * <p>Chicago TDD: Uses real {@link AgentMarketplace}, real listings, real queries.
 * {@link YWorkItem} is constructed via the no-arg constructor + field reflection to
 * avoid requiring a full YAWL engine context.
 */
@Tag("unit")
class CapabilityMatcherTest {

    private AgentMarketplace marketplace;
    private CapabilityMatcher matcher;

    @BeforeEach
    void setUp() {
        marketplace = new AgentMarketplace();
        matcher = new CapabilityMatcher(marketplace, Double.MAX_VALUE, Long.MAX_VALUE,
                Duration.ofMinutes(5));
    }

    // -----------------------------------------------------------------------
    // Scenario 1: Live agent with keyword matching task name → AgentRoute
    // -----------------------------------------------------------------------

    @Test
    void match_liveAgentWithMatchingSkill_returnsAgentRoute() throws Exception {
        AgentMarketplaceListing listing = buildListing("agent-1", "ApprovalAgent",
                "approval", "localhost", 8090, 0.10, Instant.now());
        marketplace.publish(listing);

        YWorkItem workItem = workItemWithTask("approval");
        RoutingDecision decision = matcher.match(workItem);

        assertInstanceOf(RoutingDecision.AgentRoute.class, decision,
                "Live agent with matching keyword should yield AgentRoute");
        RoutingDecision.AgentRoute route = (RoutingDecision.AgentRoute) decision;
        assertEquals("ApprovalAgent", route.listing().agentInfo().getName());
        assertNotNull(route.rationale());
        assertFalse(route.rationale().isBlank());
    }

    // -----------------------------------------------------------------------
    // Scenario 2: Empty marketplace → HumanRoute
    // -----------------------------------------------------------------------

    @Test
    void match_emptyMarketplace_returnsHumanRoute() throws Exception {
        YWorkItem workItem = workItemWithTask("SomeTask");

        RoutingDecision decision = matcher.match(workItem);

        assertInstanceOf(RoutingDecision.HumanRoute.class, decision,
                "Empty marketplace should yield HumanRoute");
    }

    // -----------------------------------------------------------------------
    // Scenario 3: Agent cost exceeds budget → HumanRoute
    // -----------------------------------------------------------------------

    @Test
    void match_agentExceedsCostBound_returnsHumanRoute() throws Exception {
        // Budget: 0.05; agent costs 0.50 → filtered out
        CapabilityMatcher tightMatcher = new CapabilityMatcher(
                marketplace, 0.05, Long.MAX_VALUE, Duration.ofMinutes(5));

        AgentMarketplaceListing listing = buildListing("agent-2", "ExpensiveAgent",
                "approval", "localhost", 8091, 0.50, Instant.now());
        marketplace.publish(listing);

        YWorkItem workItem = workItemWithTask("approval");
        RoutingDecision decision = tightMatcher.match(workItem);

        assertInstanceOf(RoutingDecision.HumanRoute.class, decision,
                "Agent exceeding cost bound should yield HumanRoute");
    }

    // -----------------------------------------------------------------------
    // Scenario 4: Stale heartbeat → HumanRoute
    // -----------------------------------------------------------------------

    @Test
    void match_staleHeartbeat_returnsHumanRoute() throws Exception {
        // Heartbeat more than 5 minutes ago
        Instant staleHeartbeat = Instant.now().minus(Duration.ofMinutes(10));
        AgentMarketplaceListing staleListing = buildListing("agent-3", "StaleAgent",
                "approval", "localhost", 8092, 0.10, staleHeartbeat);
        marketplace.publish(staleListing);

        YWorkItem workItem = workItemWithTask("approval");
        RoutingDecision decision = matcher.match(workItem);

        assertInstanceOf(RoutingDecision.HumanRoute.class, decision,
                "Agent with stale heartbeat should yield HumanRoute");
    }

    // -----------------------------------------------------------------------
    // Scenario 5: buildQuery uses task ID as capability keyword
    // -----------------------------------------------------------------------

    @Test
    void buildQuery_taskNameUsedAsKeyword() throws Exception {
        YWorkItem workItem = workItemWithTask("CreditCheck");

        TransitionSlotQuery query = matcher.buildQuery(workItem);

        assertTrue(query.capabilityKeyword().isPresent(),
                "Query should have a capability keyword");
        assertEquals("CreditCheck", query.capabilityKeyword().get(),
                "Capability keyword should match the task ID");
    }

    // -----------------------------------------------------------------------
    // Scenario 6: buildRationale returns non-null, non-blank string
    // -----------------------------------------------------------------------

    @Test
    void buildRationale_nonNull_nonBlank() {
        AgentMarketplaceListing listing = buildListing("agent-4", "RationaleAgent",
                "finance", "localhost", 8093, 0.25, Instant.now());

        String rationale = matcher.buildRationale(listing, "FinanceTask");

        assertNotNull(rationale, "Rationale must not be null");
        assertFalse(rationale.isBlank(), "Rationale must not be blank");
        assertTrue(rationale.contains("FinanceTask"),
                "Rationale should mention the task name");
        assertTrue(rationale.contains("RationaleAgent"),
                "Rationale should mention the agent name");
    }

    // -----------------------------------------------------------------------
    // Scenario 7: ResourceManager — agent dispatch failure throws AgentRoutingException
    // -----------------------------------------------------------------------

    @Test
    void resourceManager_agentRouted_noSilentFallback_throwsOnA2AFail() throws Exception {
        // Use port 1 — connection will be immediately refused on any OS
        AgentMarketplaceListing listing = buildListing("agent-5", "UnreachableAgent",
                "approval", "localhost", 1, 0.10, Instant.now());

        ResourceManager rm = new ResourceManager(matcher);
        YWorkItem workItem = workItemWithTask("approval");

        // executeDispatch is package-private; test calls it directly to verify
        // the no-silent-fallback invariant synchronously.
        assertThrows(AgentRoutingException.class,
                () -> rm.executeDispatch(listing, workItem),
                "Dispatch to unreachable agent must throw AgentRoutingException");
    }

    // -----------------------------------------------------------------------
    // Scenario 8: ResourceManager — human route increments humanRouteCount
    // -----------------------------------------------------------------------

    @Test
    void resourceManager_humanRoute_incrementsHumanRouteCount() throws Exception {
        // Empty marketplace → all decisions are HumanRoute
        ResourceManager rm = new ResourceManager(matcher);
        YWorkItem workItem = workItemWithTask("SomeTask");
        YWorkItemEvent event = new YWorkItemEvent(YEventType.ITEM_ENABLED, workItem);

        rm.handleWorkItemEvent(event);

        assertEquals(1, rm.getHumanRouteCount(),
                "HumanRoute should increment humanRouteCount");
        assertEquals(0, rm.getAgentDispatchCount(),
                "AgentDispatchCount should remain zero for HumanRoute");
    }

    // -----------------------------------------------------------------------
    // Scenario 9: ResourceManager — non-ITEM_ENABLED event is ignored
    // -----------------------------------------------------------------------

    @Test
    void resourceManager_nonEnabledEvent_ignored() throws Exception {
        ResourceManager rm = new ResourceManager(matcher);
        YWorkItem workItem = workItemWithTask("SomeTask");
        YWorkItemEvent event = new YWorkItemEvent(YEventType.ITEM_COMPLETED, workItem);

        rm.handleWorkItemEvent(event);

        assertEquals(0, rm.getHumanRouteCount(),
                "Non-ITEM_ENABLED events must not affect humanRouteCount");
        assertEquals(0, rm.getAgentDispatchCount(),
                "Non-ITEM_ENABLED events must not affect agentDispatchCount");
    }

    // -----------------------------------------------------------------------
    // Scenario 10: RoutingDecision.AgentRoute record equality
    // -----------------------------------------------------------------------

    @Test
    void routingDecision_agentRoute_recordEquality() {
        AgentMarketplaceListing listing = buildListing("agent-6", "EqualityAgent",
                "test", "localhost", 8094, 0.10, Instant.now());

        RoutingDecision.AgentRoute r1 = new RoutingDecision.AgentRoute(listing, "rationale-1");
        RoutingDecision.AgentRoute r2 = new RoutingDecision.AgentRoute(listing, "rationale-1");

        assertEquals(r1, r2, "AgentRoute records with same fields should be equal");
        assertEquals(r1.hashCode(), r2.hashCode(),
                "Equal AgentRoute records should have equal hash codes");
        assertEquals(listing, r1.listing());
        assertEquals("rationale-1", r1.rationale());
    }

    // -----------------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------------

    /**
     * Constructs a minimal YWorkItem via no-arg constructor + field reflection.
     * Only sets the _workItemID field so that getTaskID() and getCaseID() work.
     */
    private static YWorkItem workItemWithTask(String taskId) throws Exception {
        YWorkItem item = new YWorkItem();
        YIdentifier caseId = new YIdentifier("case-test");
        YWorkItemID wid = new YWorkItemID(caseId, taskId);
        Field f = YWorkItem.class.getDeclaredField("_workItemID");
        f.setAccessible(true);
        f.set(item, wid);
        return item;
    }

    /**
     * Builds an {@link AgentMarketplaceListing} for testing.
     *
     * @param id          agent ID
     * @param name        agent display name
     * @param domain      capability domain keyword
     * @param host        agent host
     * @param port        agent port
     * @param cost        base price per cycle
     * @param heartbeatAt last heartbeat timestamp
     */
    private static AgentMarketplaceListing buildListing(String id, String name,
                                                         String domain, String host, int port,
                                                         double cost, Instant heartbeatAt) {
        AgentInfo info = new AgentInfo(id, name, List.of(domain), host, port);
        AgentCapability capability = new AgentCapability(domain, domain + " processing");
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0", capability)
                .costProfile(CoordinationCostProfile.pureInference(cost))
                .build();
        Instant registeredAt = heartbeatAt.isBefore(Instant.now().minus(Duration.ofHours(1)))
                ? heartbeatAt
                : Instant.now().minus(Duration.ofMinutes(1));
        return new AgentMarketplaceListing(info, spec, registeredAt, heartbeatAt);
    }
}
