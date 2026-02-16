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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background health monitor for registered agents.
 *
 * Periodically checks agent heartbeats and removes agents that have
 * not sent heartbeats within the timeout period (30 seconds).
 *
 * Runs every 10 seconds as a virtual thread. Virtual threads are lightweight
 * and ideal for this periodic I/O-bound monitoring task.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @updated 2026-02-16 Virtual thread migration (Java 25)
 */
public final class AgentHealthMonitor implements Runnable {

    private static final Logger logger = LogManager.getLogger(AgentHealthMonitor.class);

    private static final long HEARTBEAT_TIMEOUT_MILLIS = 30_000;
    private static final long CHECK_INTERVAL_MILLIS = 10_000;

    private final ConcurrentHashMap<String, AgentInfo> agentRegistry;
    private volatile boolean running = true;

    /**
     * Create health monitor for the given agent registry.
     *
     * @param agentRegistry the registry to monitor
     */
    public AgentHealthMonitor(ConcurrentHashMap<String, AgentInfo> agentRegistry) {
        if (agentRegistry == null) {
            throw new IllegalArgumentException("agentRegistry is required");
        }
        this.agentRegistry = agentRegistry;
    }

    /**
     * Start the health monitor in a virtual thread.
     * Virtual threads are more efficient than platform threads for this periodic
     * monitoring task, using minimal memory and system resources.
     *
     * @return the started thread
     */
    public Thread start() {
        Thread thread = Thread.ofVirtual()
                .name("AgentHealthMonitor")
                .start(this);
        logger.info("Agent health monitor started (check interval: {}ms, timeout: {}ms)",
                   CHECK_INTERVAL_MILLIS, HEARTBEAT_TIMEOUT_MILLIS);
        return thread;
    }

    /**
     * Stop the health monitor.
     */
    public void stop() {
        running = false;
        logger.info("Agent health monitor stopped");
    }

    @Override
    public void run() {
        while (running) {
            try {
                checkAgentHealth();
                Thread.sleep(CHECK_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                logger.warn("Health monitor interrupted", e);
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                logger.error("Error checking agent health", e);
            }
        }
    }

    /**
     * Check all registered agents and remove those with stale heartbeats.
     */
    private void checkAgentHealth() {
        List<String> deadAgents = new ArrayList<>();

        for (Map.Entry<String, AgentInfo> entry : agentRegistry.entrySet()) {
            AgentInfo agent = entry.getValue();
            if (!agent.isAlive(HEARTBEAT_TIMEOUT_MILLIS)) {
                deadAgents.add(agent.getId());
            }
        }

        for (String agentId : deadAgents) {
            AgentInfo removed = agentRegistry.remove(agentId);
            if (removed != null) {
                long timeSinceHeartbeat = System.currentTimeMillis() - removed.getLastHeartbeat();
                logger.warn("Removed dead agent: {} (last heartbeat: {}ms ago)",
                          removed.getName(), timeSinceHeartbeat);
            }
        }

        if (!deadAgents.isEmpty()) {
            logger.info("Health check removed {} dead agent(s), {} active agent(s) remaining",
                       deadAgents.size(), agentRegistry.size());
        }
    }

    /**
     * Get the heartbeat timeout in milliseconds.
     *
     * @return timeout value
     */
    public static long getHeartbeatTimeout() {
        return HEARTBEAT_TIMEOUT_MILLIS;
    }

    /**
     * Get the check interval in milliseconds.
     *
     * @return check interval
     */
    public static long getCheckInterval() {
        return CHECK_INTERVAL_MILLIS;
    }
}
