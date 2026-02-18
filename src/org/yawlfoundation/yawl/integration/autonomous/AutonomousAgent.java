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

import java.io.IOException;

/**
 * Core interface for autonomous YAWL agents.
 *
 * <p>Autonomous agents operate independently to discover work items,
 * evaluate eligibility using pluggable reasoning strategies, execute
 * decisions, and complete workflow tasks without central orchestration.</p>
 *
 * <p>Agent Lifecycle:
 * <ol>
 *   <li>Construction via {@link AgentFactory} with configuration</li>
 *   <li>Start agent (connects to YAWL engine, starts discovery loop)</li>
 *   <li>Discovery cycles run continuously until stopped</li>
 *   <li>Stop agent gracefully (completes in-flight work, disconnects)</li>
 * </ol>
 * </p>
 *
 * <p>Discovery Cycle (per iteration):
 * <ol>
 *   <li>Use {@link org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy}
 *       to find available work items</li>
 *   <li>Filter using {@link org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner}</li>
 *   <li>Checkout eligible work items from YAWL engine</li>
 *   <li>Execute using {@link org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner}</li>
 *   <li>Checkin with generated output data</li>
 * </ol>
 * </p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see AgentFactory
 * @see AgentConfiguration
 */
public interface AutonomousAgent {

    /**
     * Start the autonomous agent.
     *
     * <p>Initializes connection to YAWL engine, starts the discovery loop,
     * and optionally starts an HTTP server for A2A discovery endpoints
     * (/.well-known/agent.json).</p>
     *
     * @throws IOException if connection to YAWL engine fails or HTTP server cannot start
     * @throws IllegalStateException if agent is already running
     */
    void start() throws IOException;

    /**
     * Stop the autonomous agent gracefully.
     *
     * <p>Stops the discovery loop, shuts down HTTP server if running,
     * and disconnects from the YAWL engine. Allows in-flight work items
     * to complete within a timeout period.</p>
     *
     * @throws IllegalStateException if agent is not running
     */
    void stop();

    /**
     * Check if the agent is currently running.
     *
     * @return true if agent is running (discovery loop active), false otherwise
     */
    boolean isRunning();

    /**
     * Get the agent's capability descriptor.
     *
     * <p>The capability describes the agent's domain expertise and is used
     * by eligibility reasoners to determine if the agent should handle a
     * work item.</p>
     *
     * @return the capability descriptor
     */
    AgentCapability getCapability();

    /**
     * Get the agent's configuration.
     *
     * @return immutable configuration object
     */
    AgentConfiguration getConfiguration();

    /**
     * Get the agent's A2A discovery card as JSON.
     *
     * <p>The agent card is exposed at /.well-known/agent.json and conforms
     * to the Agent-to-Agent (A2A) protocol for autonomous agent discovery.</p>
     *
     * @return JSON string conforming to agent.json schema
     */
    String getAgentCard();
}
