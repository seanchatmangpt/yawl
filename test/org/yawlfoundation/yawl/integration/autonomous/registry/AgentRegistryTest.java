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

package org.yawlfoundation.yawl.integration.autonomous.registry;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;

import java.io.IOException;
import java.util.List;

/**
 * Tests for the Agent Registry system.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentRegistryTest extends TestCase {

    private static final int TEST_PORT = 19090;

    private AgentRegistry registry;
    private AgentRegistryClient client;

    @Override
    protected void setUp() throws Exception {
        registry = new AgentRegistry(TEST_PORT);
        registry.start();
        client = new AgentRegistryClient("localhost", TEST_PORT);
        Thread.sleep(100);
    }

    @Override
    protected void tearDown() throws Exception {
        if (registry != null) {
            registry.stop();
        }
    }

    public void testRegisterAgent() throws Exception {
        AgentCapability capability = new AgentCapability("Ordering",
            "procurement, purchase orders, approvals");
        AgentInfo agent = new AgentInfo("agent-1", "Ordering Agent",
            capability, "localhost", 8080);

        boolean result = client.register(agent);
        assertTrue(result, "Registration should succeed");
        assertEquals(1, registry.getAgentCount(, "Should have 1 agent"));
    }

    public void testListAgents() throws Exception {
        AgentCapability cap1 = new AgentCapability("Ordering", "procurement");
        AgentInfo agent1 = new AgentInfo("agent-1", "Agent 1", cap1, "localhost", 8080);

        AgentCapability cap2 = new AgentCapability("Shipping", "logistics");
        AgentInfo agent2 = new AgentInfo("agent-2", "Agent 2", cap2, "localhost", 8081);

        client.register(agent1);
        client.register(agent2);

        List<AgentInfo> agents = client.listAll();
        assertEquals(2, agents.size(, "Should have 2 agents"));
    }

    public void testQueryByCapability() throws Exception {
        AgentCapability cap1 = new AgentCapability("Ordering", "procurement");
        AgentInfo agent1 = new AgentInfo("agent-1", "Agent 1", cap1, "localhost", 8080);

        AgentCapability cap2 = new AgentCapability("Shipping", "logistics");
        AgentInfo agent2 = new AgentInfo("agent-2", "Agent 2", cap2, "localhost", 8081);

        client.register(agent1);
        client.register(agent2);

        List<AgentInfo> orderingAgents = client.queryByCapability("Ordering");
        assertEquals(1, orderingAgents.size(, "Should find 1 ordering agent"));
        assertEquals("agent-1", orderingAgents.get(0, "Should be agent-1").getId());

        List<AgentInfo> shippingAgents = client.queryByCapability("Shipping");
        assertEquals(1, shippingAgents.size(, "Should find 1 shipping agent"));
        assertEquals("agent-2", shippingAgents.get(0, "Should be agent-2").getId());
    }

    public void testHeartbeat() throws Exception {
        AgentCapability capability = new AgentCapability("Test", "test domain");
        AgentInfo agent = new AgentInfo("agent-1", "Test Agent",
            capability, "localhost", 8080);

        client.register(agent);

        long beforeHeartbeat = System.currentTimeMillis();
        Thread.sleep(50);

        boolean result = client.sendHeartbeat("agent-1");
        assertTrue(result, "Heartbeat should succeed");

        List<AgentInfo> agents = client.listAll();
        assertEquals(1, agents.size(, "Should have 1 agent"));
        assertTrue(agents.get(0, "Heartbeat timestamp should be updated").getLastHeartbeat() > beforeHeartbeat);
    }

    public void testUnregister() throws Exception {
        AgentCapability capability = new AgentCapability("Test", "test domain");
        AgentInfo agent = new AgentInfo("agent-1", "Test Agent",
            capability, "localhost", 8080);

        client.register(agent);
        assertEquals(1, registry.getAgentCount(, "Should have 1 agent"));

        boolean result = client.unregister("agent-1");
        assertTrue(result, "Unregister should succeed");
        assertEquals(0, registry.getAgentCount(, "Should have 0 agents"));
    }

    public void testAgentInfoJsonSerialization() throws Exception {
        AgentCapability capability = new AgentCapability("Ordering",
            "procurement, purchase orders");
        AgentInfo original = new AgentInfo("agent-1", "Test Agent",
            capability, "localhost", 8080);

        String json = original.toJson();
        assertNotNull(json, "JSON should not be null");
        assertTrue(json.contains("\"id\":\"agent-1\"", "JSON should contain id"));
        assertTrue(json.contains("\"name\":\"Test Agent\"", "JSON should contain name"));

        AgentInfo deserialized = AgentInfo.fromJson(json);
        assertEquals(original.getId(), deserialized.getId(, "ID should match"));
        assertEquals(original.getName(), deserialized.getName(, "Name should match"));
        assertEquals(original.getHost(), deserialized.getHost(, "Host should match"));
        assertEquals(original.getPort(), deserialized.getPort(, "Port should match"));
        assertEquals(original.getCapability().getDomainName(), deserialized.getCapability(, "Domain should match").getDomainName());
    }

    public void testAgentHealthMonitor() throws Exception {
        AgentCapability capability = new AgentCapability("Test", "test domain");
        AgentInfo agent = new AgentInfo("agent-1", "Test Agent",
            capability, "localhost", 8080);

        client.register(agent);
        assertEquals(1, registry.getAgentCount(, "Should have 1 agent"));

        Thread.sleep(35000);

        assertEquals(0, registry.getAgentCount(, "Agent should be removed after timeout"));
    }

    public void testMultipleAgentsWithSameCapability() throws Exception {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");

        AgentInfo agent1 = new AgentInfo("agent-1", "Agent 1", capability, "host1", 8080);
        AgentInfo agent2 = new AgentInfo("agent-2", "Agent 2", capability, "host2", 8081);

        client.register(agent1);
        client.register(agent2);

        List<AgentInfo> agents = client.queryByCapability("Ordering");
        assertEquals(2, agents.size(, "Should find 2 agents with Ordering capability"));
    }

    public void testInvalidRegistration() throws Exception {
        try {
            client.register(null);
            fail("Should throw IllegalArgumentException for null agent");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(, "Should have meaningful error").contains("required"));
        }
    }

    public void testInvalidHeartbeat() throws Exception {
        try {
            client.sendHeartbeat("");
            fail("Should throw IllegalArgumentException for empty agent ID");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(, "Should have meaningful error").contains("required"));
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(AgentRegistryTest.class);
    }
}
