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

/**
 * Agent Registry for multi-agent coordination and discovery.
 *
 * <h2>Overview</h2>
 * Provides a centralized registry for autonomous agents to register, discover,
 * and coordinate with each other. Implements a REST API over HTTP with health
 * monitoring and capability-based discovery.
 *
 * <h2>Components</h2>
 * <ul>
 * <li>{@link AgentRegistry} - Central registry server with embedded HTTP server</li>
 * <li>{@link AgentRegistryClient} - Client library for agent registration and discovery</li>
 * <li>{@link AgentInfo} - Data model for agent registration information</li>
 * <li>{@link AgentHealthMonitor} - Background health checker for registered agents</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Start registry server
 * AgentRegistry registry = new AgentRegistry(9090);
 * registry.start();
 *
 * // Register agent from client
 * AgentRegistryClient client = new AgentRegistryClient("localhost", 9090);
 * AgentCapability capability = new AgentCapability("Ordering", "procurement");
 * AgentInfo agent = new AgentInfo("agent-1", "My Agent", capability, "localhost", 8080);
 * client.register(agent);
 *
 * // Send heartbeat periodically
 * client.sendHeartbeat("agent-1");
 *
 * // Query agents by capability
 * List&lt;AgentInfo&gt; orderingAgents = client.queryByCapability("Ordering");
 *
 * // Cleanup
 * client.unregister("agent-1");
 * registry.stop();
 * </pre>
 *
 * <h2>REST API</h2>
 * <ul>
 * <li>POST /agents/register - Register new agent</li>
 * <li>GET /agents - List all registered agents</li>
 * <li>GET /agents/by-capability?domain=X - Query by capability domain</li>
 * <li>DELETE /agents/{id} - Unregister agent</li>
 * <li>POST /agents/{id}/heartbeat - Update agent heartbeat</li>
 * </ul>
 *
 * <h2>Health Monitoring</h2>
 * Agents must send heartbeats every 30 seconds. The health monitor runs every 10
 * seconds and removes agents that haven't sent heartbeats within the timeout period.
 *
 * <h2>Running Standalone</h2>
 * <pre>
 * java -cp yawl-lib-5.2.jar:lib/* \
 *   org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistry [port]
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
package org.yawlfoundation.yawl.integration.autonomous.registry;
