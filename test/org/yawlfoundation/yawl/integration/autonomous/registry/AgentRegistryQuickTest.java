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

import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;

import java.util.List;

/**
 * Quick integration test for Agent Registry.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentRegistryQuickTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Agent Registry Quick Test...");

        AgentRegistry registry = new AgentRegistry(19090);
        registry.start();
        System.out.println("✓ Registry started on port 19090");

        Thread.sleep(200);

        AgentRegistryClient client = new AgentRegistryClient("localhost", 19090);

        AgentCapability cap1 = new AgentCapability("Ordering", "procurement, purchase orders");
        AgentInfo agent1 = new AgentInfo("agent-1", "Ordering Agent", cap1, "localhost", 8080);

        boolean registered = client.register(agent1);
        System.out.println("✓ Agent registered: " + registered);

        List<AgentInfo> agents = client.listAll();
        System.out.println("✓ Agent count: " + agents.size());

        if (agents.size() != 1) {
            throw new AssertionError("Expected 1 agent, got " + agents.size());
        }

        List<AgentInfo> orderingAgents = client.queryByCapability("Ordering");
        System.out.println("✓ Query by capability found: " + orderingAgents.size());

        if (orderingAgents.size() != 1) {
            throw new AssertionError("Expected 1 ordering agent, got " + orderingAgents.size());
        }

        boolean heartbeat = client.sendHeartbeat("agent-1");
        System.out.println("✓ Heartbeat sent: " + heartbeat);

        AgentCapability cap2 = new AgentCapability("Shipping", "logistics, carriers");
        AgentInfo agent2 = new AgentInfo("agent-2", "Shipping Agent", cap2, "localhost", 8081);
        client.register(agent2);

        agents = client.listAll();
        System.out.println("✓ Total agents after second registration: " + agents.size());

        if (agents.size() != 2) {
            throw new AssertionError("Expected 2 agents, got " + agents.size());
        }

        String json = agent1.toJson();
        System.out.println("✓ JSON serialization: " + (json.length() > 0 ? "OK" : "FAIL"));

        AgentInfo deserialized = AgentInfo.fromJson(json);
        if (!deserialized.getId().equals(agent1.getId())) {
            throw new AssertionError("Deserialization failed: ID mismatch");
        }
        System.out.println("✓ JSON deserialization: OK");

        boolean unregistered = client.unregister("agent-1");
        System.out.println("✓ Agent unregistered: " + unregistered);

        agents = client.listAll();
        System.out.println("✓ Remaining agents: " + agents.size());

        if (agents.size() != 1) {
            throw new AssertionError("Expected 1 agent after unregister, got " + agents.size());
        }

        registry.stop();
        System.out.println("✓ Registry stopped");

        System.out.println("\n========================================");
        System.out.println("✓ ALL TESTS PASSED");
        System.out.println("========================================");
    }
}
