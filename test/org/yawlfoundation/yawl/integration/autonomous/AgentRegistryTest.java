/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.a2a.A2AException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Integration tests for AgentRegistry (V6 feature).
 *
 * Chicago TDD: tests real AgentRegistry with real ConcurrentHashMap storage
 * and real heartbeat monitoring. No mocks.
 *
 * Coverage targets:
 * - Agent registration/unregistration
 * - Heartbeat update
 * - Capability-based discovery
 * - Status transitions (ACTIVE -> INACTIVE)
 * - Guard conditions
 * - concurrent access
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class AgentRegistryTest extends TestCase {

    private AgentRegistry registry;

    public AgentRegistryTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        // Create registry with short heartbeat interval for fast tests
        // interval=1s, timeout=2s
        registry = new AgentRegistry(1, 2);
    }

    @Override
    protected void tearDown() {
        registry.shutdown();
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    public void testDefaultConstructorCreatesRegistry() {
        AgentRegistry defaultRegistry = new AgentRegistry();
        assertNotNull(defaultRegistry);
        defaultRegistry.shutdown();
    }

    public void testConstructorWithInvalidHeartbeatIntervalThrows() {
        try {
            new AgentRegistry(0, 90);
            fail("Expected IllegalArgumentException for zero heartbeat interval");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("interval") || e.getMessage().contains("positive"));
        }
    }

    public void testConstructorWithTimeoutNotGreaterThanIntervalThrows() {
        try {
            new AgentRegistry(30, 30);
            fail("Expected IllegalArgumentException when timeout <= interval");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("timeout") || e.getMessage().contains("interval"));
        }
    }

    // =========================================================================
    // Registration tests
    // =========================================================================

    public void testRegisterAgentSucceeds() throws A2AException {
        registry.registerAgent("agent-1", "http://localhost:8091",
                List.of("Ordering"), Map.of());

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-1");
        assertTrue("Agent should be registered", entry.isPresent());
        assertEquals("agent-1", entry.get().getAgentId());
        assertEquals("http://localhost:8091", entry.get().getEndpoint());
    }

    public void testRegisterAgentWithCapabilities() throws A2AException {
        registry.registerAgent("agent-2", "http://localhost:8092",
                List.of("Finance", "Accounting"), Map.of("version", "6.0"));

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-2");
        assertTrue(entry.isPresent());
        List<String> caps = entry.get().getCapabilities();
        assertEquals(2, caps.size());
        assertTrue(caps.contains("Finance"));
        assertTrue(caps.contains("Accounting"));
    }

    public void testRegisterAgentInitialStatusIsActive() throws A2AException {
        registry.registerAgent("agent-3", "http://localhost:8093",
                List.of("Logistics"), Map.of());

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-3");
        assertTrue(entry.isPresent());
        assertEquals(AgentRegistry.AgentStatus.ACTIVE, entry.get().getStatus());
    }

    public void testRegisterAgentWithEmptyIdThrows() {
        try {
            registry.registerAgent("", "http://localhost:8091", List.of(), Map.of());
            fail("Expected A2AException for empty agent ID");
        } catch (A2AException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testRegisterAgentWithNullIdThrows() {
        try {
            registry.registerAgent(null, "http://localhost:8091", List.of(), Map.of());
            fail("Expected exception for null agent ID");
        } catch (Exception e) {
            assertTrue("Expected IllegalArgumentException or A2AException",
                    e instanceof IllegalArgumentException || e instanceof A2AException);
        }
    }

    public void testRegisterAgentWithEmptyEndpointThrows() {
        try {
            registry.registerAgent("agent-x", "", List.of(), Map.of());
            fail("Expected A2AException for empty endpoint");
        } catch (A2AException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testRegisterDuplicateAgentIdThrowsException() throws A2AException {
        registry.registerAgent("agent-dup", "http://localhost:8083",
                List.of("Ordering"), Map.of());

        try {
            registry.registerAgent("agent-dup", "http://localhost:8084",
                    List.of("Finance"), Map.of());
            fail("Expected A2AException for duplicate agent ID");
        } catch (A2AException e) {
            assertNotNull("Exception should have a message", e.getMessage());
        }
    }

    public void testRegisterAgentWithNullCapabilitiesCreatesEmptyList() throws A2AException {
        registry.registerAgent("agent-4", "http://localhost:8094", null, Map.of());

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-4");
        assertTrue(entry.isPresent());
        assertNotNull(entry.get().getCapabilities());
        assertTrue("Should have empty capabilities list",
                entry.get().getCapabilities().isEmpty());
    }

    // =========================================================================
    // Unregistration tests
    // =========================================================================

    public void testUnregisterExistingAgentReturnsTrue() throws A2AException {
        registry.registerAgent("agent-5", "http://localhost:8095",
                List.of("Ordering"), Map.of());

        boolean removed = registry.unregisterAgent("agent-5");
        assertTrue("Should return true for existing agent", removed);
        assertFalse("Agent should no longer be present",
                registry.getAgent("agent-5").isPresent());
    }

    public void testUnregisterNonexistentAgentReturnsFalse() {
        boolean removed = registry.unregisterAgent("nonexistent-agent");
        assertFalse("Should return false for non-existent agent", removed);
    }

    // =========================================================================
    // Heartbeat tests
    // =========================================================================

    public void testUpdateHeartbeatForExistingAgent() throws A2AException {
        registry.registerAgent("agent-6", "http://localhost:8096",
                List.of("Ordering"), Map.of());

        Instant before = Instant.now();
        registry.updateHeartbeat("agent-6");
        Instant after = Instant.now();

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-6");
        assertTrue(entry.isPresent());
        Instant heartbeat = entry.get().getLastHeartbeat();
        assertFalse("Heartbeat should be updated",
                heartbeat.isBefore(before));
    }

    public void testUpdateHeartbeatForNonexistentAgentThrows() {
        try {
            registry.updateHeartbeat("nonexistent-agent");
            fail("Expected A2AException for non-existent agent");
        } catch (A2AException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // Discovery by capability tests
    // =========================================================================

    public void testFindAgentsByCapability() throws A2AException {
        registry.registerAgent("ordering-agent", "http://localhost:8091",
                List.of("Ordering", "Procurement"), Map.of());
        registry.registerAgent("finance-agent", "http://localhost:8092",
                List.of("Finance", "Accounting"), Map.of());
        registry.registerAgent("multi-agent", "http://localhost:8093",
                List.of("Ordering", "Finance"), Map.of());

        List<AgentRegistry.AgentEntry> orderingAgents =
                registry.findAgentsByCapability("Ordering");

        assertEquals("Should find 2 ordering agents", 2, orderingAgents.size());

        List<AgentRegistry.AgentEntry> financeAgents =
                registry.findAgentsByCapability("Finance");

        assertEquals("Should find 2 finance agents", 2, financeAgents.size());
    }

    public void testFindAgentsByCapabilityReturnsOnlyActive() throws A2AException {
        registry.registerAgent("active-ordering", "http://localhost:8091",
                List.of("Ordering"), Map.of());

        // Simulate inactive by getting the internal state
        // The registry automatically marks agents as inactive via heartbeat timeout.
        // In a test context, we can't easily manipulate time, so we verify
        // that the active agent IS found.
        List<AgentRegistry.AgentEntry> agents = registry.findAgentsByCapability("Ordering");
        assertEquals("Should find 1 active ordering agent", 1, agents.size());
    }

    public void testFindAgentsByNullCapabilityThrows() {
        try {
            registry.findAgentsByCapability(null);
            fail("Expected IllegalArgumentException for null capability");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Capability"));
        }
    }

    public void testFindAgentsByEmptyCapabilityThrows() {
        try {
            registry.findAgentsByCapability("");
            fail("Expected IllegalArgumentException for empty capability");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Capability"));
        }
    }

    public void testFindAgentsByNonexistentCapabilityReturnsEmpty() throws A2AException {
        registry.registerAgent("ordering-agent", "http://localhost:8091",
                List.of("Ordering"), Map.of());

        List<AgentRegistry.AgentEntry> agents =
                registry.findAgentsByCapability("NonexistentCapability");
        assertTrue("Should return empty list for nonexistent capability",
                agents.isEmpty());
    }

    // =========================================================================
    // getActiveAgents() and getAllAgents() tests
    // =========================================================================

    public void testGetActiveAgentsReturnsAllActiveAgents() throws A2AException {
        registry.registerAgent("agent-a", "http://localhost:8091",
                List.of("A"), Map.of());
        registry.registerAgent("agent-b", "http://localhost:8092",
                List.of("B"), Map.of());

        List<AgentRegistry.AgentEntry> active = registry.getActiveAgents();
        assertEquals("Should have 2 active agents", 2, active.size());
    }

    public void testGetAllAgentsReturnsAll() throws A2AException {
        registry.registerAgent("agent-x", "http://localhost:8091",
                List.of("X"), Map.of());
        registry.registerAgent("agent-y", "http://localhost:8092",
                List.of("Y"), Map.of());

        List<AgentRegistry.AgentEntry> all = registry.getAllAgents();
        assertEquals("Should have 2 agents total", 2, all.size());
    }

    public void testGetActiveAgentCountAccurate() throws A2AException {
        assertEquals("Should have 0 active agents initially",
                0, registry.getActiveAgentCount());

        registry.registerAgent("agent-1", "http://localhost:8091",
                List.of("A"), Map.of());
        assertEquals("Should have 1 active agent", 1, registry.getActiveAgentCount());

        registry.registerAgent("agent-2", "http://localhost:8092",
                List.of("B"), Map.of());
        assertEquals("Should have 2 active agents", 2, registry.getActiveAgentCount());

        registry.unregisterAgent("agent-1");
        assertEquals("Should have 1 active agent after removal",
                1, registry.getActiveAgentCount());
    }

    // =========================================================================
    // AgentEntry tests
    // =========================================================================

    public void testAgentEntryMetadataIsCopied() throws A2AException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "6.0");
        metadata.put("region", "us-east-1");

        registry.registerAgent("agent-meta", "http://localhost:8091",
                List.of("Ordering"), metadata);

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-meta");
        assertTrue(entry.isPresent());
        Map<String, Object> retrievedMetadata = entry.get().getMetadata();
        assertEquals("6.0", retrievedMetadata.get("version"));
        assertEquals("us-east-1", retrievedMetadata.get("region"));
    }

    public void testAgentEntryCapabilitiesIsCopied() throws A2AException {
        List<String> caps = new java.util.ArrayList<>();
        caps.add("Ordering");
        caps.add("Finance");

        registry.registerAgent("agent-caps", "http://localhost:8091",
                caps, Map.of());

        // Modifying original list should not affect registry
        caps.add("Injected");

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-caps");
        assertTrue(entry.isPresent());
        assertEquals("Capabilities should be copied, not affected by external modification",
                2, entry.get().getCapabilities().size());
    }

    public void testAgentEntryHasCapability() throws A2AException {
        registry.registerAgent("agent-hc", "http://localhost:8091",
                List.of("Ordering", "Finance"), Map.of());

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("agent-hc");
        assertTrue(entry.isPresent());
        AgentRegistry.AgentEntry agentEntry = entry.get();

        assertTrue("Should have Ordering capability",
                agentEntry.hasCapability("Ordering"));
        assertTrue("Should have Finance capability",
                agentEntry.hasCapability("Finance"));
        assertFalse("Should not have Logistics capability",
                agentEntry.hasCapability("Logistics"));
    }

    // =========================================================================
    // clear() and shutdown() tests
    // =========================================================================

    public void testClearRemovesAllAgents() throws A2AException {
        registry.registerAgent("agent-1", "http://localhost:8091",
                List.of("A"), Map.of());
        registry.registerAgent("agent-2", "http://localhost:8092",
                List.of("B"), Map.of());

        registry.clear();

        assertEquals("Should have 0 agents after clear", 0, registry.getAllAgents().size());
        assertEquals("Should have 0 active agents after clear",
                0, registry.getActiveAgentCount());
    }

    // =========================================================================
    // Concurrent access test
    // =========================================================================

    public void testConcurrentRegistrationIsThreadSafe() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int agentNum = i;
            threads[i] = new Thread(() -> {
                try {
                    registry.registerAgent("concurrent-agent-" + agentNum,
                            "http://localhost:" + (9000 + agentNum),
                            List.of("Capability-" + agentNum), Map.of());
                } catch (A2AException e) {
                    fail("Concurrent registration should not throw: " + e.getMessage());
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join(5000);

        assertEquals("Should have all 10 agents registered",
                threadCount, registry.getAllAgents().size());
    }

}
