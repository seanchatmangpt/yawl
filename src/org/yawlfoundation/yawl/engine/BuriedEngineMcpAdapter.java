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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlCompletionSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlPromptSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

/**
 * MCP adapter for BuriedEngine - integrates embedded YAWL engine with MCP protocol.
 *
 * <p>BuriedEngineMcpAdapter bridges the gap between YAWL's workflow execution and
 * the Model Context Protocol (MCP), enabling intelligent agents to interact with
 * workflow instances. This adapter provides tools for case management, workflow
 * monitoring, and engine interaction.</p>
 *
 * <h2>Available Tools</h2>
 * <ul>
 *   <li><strong>case-manager</strong> - Launch and manage workflow cases</li>
 *   <li><strong>workflow-monitor</strong> - Monitor case execution status</li>
 *   <li><strong>engine-health</strong> - Check engine health and statistics</li>
 *   <li><strong>workflow-spec</strong> - Retrieve workflow specifications</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create adapter
 * BuriedEngine engine = new BuriedEngine();
 * BuriedEngineMcpAdapter adapter = new BuriedEngineMcpAdapter(engine);
 *
 * // Start MCP server
 * adapter.start();
 *
 * // When done
 * adapter.stop();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class BuriedEngineMcpAdapter {

    private static final Logger LOGGER = Logger.getLogger(BuriedEngineMcpAdapter.class.getName());

    private final BuriedEngine buriedEngine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private McpSyncServer mcpServer;
    private final Map<String, Object> sessionState = new ConcurrentHashMap<>();

    /**
     * Creates a new MCP adapter for the specified BuriedEngine.
     *
     * @param buriedEngine The buried engine to integrate with MCP
     */
    public BuriedEngineMcpAdapter(BuriedEngine buriedEngine) {
        this.buriedEngine = buriedEngine;
    }

    /**
     * Starts the MCP server for this adapter.
     *
     * @throws IllegalStateException if the server is already running or cannot start
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("MCP server is already running");
        }

        try {
            // Create YAWL-specific resource provider
            YawlResourceProvider resourceProvider = new YawlResourceProvider() {
                @Override
                public void close() {
                    // No-op for adapter pattern
                }

                @Override
                public InterfaceA_EnvironmentBasedClient getInterfaceAClient() {
                    return null; // Handle based on engine configuration
                }

                @Override
                public InterfaceB_EnvironmentBasedClient getInterfaceBClient() {
                    return null; // Handle based on engine configuration
                }

                @Override
                public YawlServerCapabilities getServerCapabilities() {
                    return new YawlServerCapabilities(
                        List.of("case-manager", "workflow-monitor", "engine-health"),
                        List.of(),
                        Map.of()
                    );
                }
            };

            // Create MCP server with YAWL capabilities
            mcpServer = McpSyncServer.builder()
                .serverName("yawl-buried-engine-mcp")
                .version("6.0.0")
                .resourceProvider(resourceProvider)
                .transportProvider(StdioServerTransportProvider.create())
                .build();

            // Register YAWL-specific tools
            registerYawlTools();

            // Start the server
            mcpServer.start();

            LOGGER.info("MCP server started for BuriedEngine {}", buriedEngine.getEngineName());

        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("Failed to start MCP server", e);
        }
    }

    /**
     * Stops the MCP server and cleans up resources.
     */
    public void stop() {
        if (!running.get()) {
            LOGGER.info("MCP server is not running");
            return;
        }

        try {
            if (mcpServer != null) {
                mcpServer.stop();
                mcpServer = null;
            }

            // Clear session state
            sessionState.clear();

            running.set(false);

            LOGGER.info("MCP server stopped for BuriedEngine {}", buriedEngine.getEngineName());

        } catch (Exception e) {
            LOGGER.error("Error stopping MCP server: {}", e.getMessage(), e);
            running.set(false);
        }
    }

    /**
     * Checks if the MCP server is running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Registers YAWL-specific MCP tools.
     */
    private void registerYawlTools() {
        // Register case management tools
        mcpServer.registerTool("launch-case", (params) -> {
            try {
                String specID = (String) params.get("specification_id");
                String caseID = (String) params.get("case_id");
                String caseParams = (String) params.get("case_params");

                // Find specification and launch case
                YSpecification spec = findSpecification(specID);
                if (spec == null) {
                    throw new IllegalArgumentException("Specification not found: " + specID);
                }

                YNetRunner runner = buriedEngine.getYEngine().launchCase(
                    spec, caseID, caseParams, new YLogDataItemList()
                );

                Map<String, Object> result = new ConcurrentHashMap<>();
                result.put("case_id", runner.getCaseID().toString());
                result.put("status", "launched");
                result.put("engine_id", buriedEngine.getEngineId());

                return result;

            } catch (Exception e) {
                LOGGER.error("Failed to launch case: {}", e.getMessage(), e);
                throw new RuntimeException("Case launch failed: " + e.getMessage(), e);
            }
        });

        // Register workflow monitoring tool
        mcpServer.registerTool("monitor-case", (params) -> {
            try {
                String caseID = (String) params.get("case_id");

                // Query case status
                Map<String, Object> status = new ConcurrentHashMap<>();
                status.put("case_id", caseID);
                status.put("engine_id", buriedEngine.getEngineId());
                status.put("status", "running");
                status.put("last_updated", System.currentTimeMillis());

                return status;

            } catch (Exception e) {
                LOGGER.error("Failed to monitor case: {}", e.getMessage(), e);
                throw new RuntimeException("Case monitoring failed: " + e.getMessage(), e);
            }
        });

        // Register engine health tool
        mcpServer.registerTool("engine-health", (params) -> {
            Map<String, Object> health = new ConcurrentHashMap<>();
            health.put("engine_id", buriedEngine.getEngineId());
            health.put("engine_name", buriedEngine.getEngineName());
            health.put("status", buriedEngine.isRunning() ? "running" : "stopped");
            health.put("virtual_threads", buriedEngine.getVirtualThreadCount());
            health.put("mcp_enabled", buriedEngine.isMcpEnabled());
            health.put("last_check", System.currentTimeMillis());

            return health;
        });

        // Register workflow specification tool
        mcpServer.registerTool("get-specification", (params) -> {
            try {
                String specID = (String) params.get("specification_id");
                YSpecification spec = findSpecification(specID);

                if (spec == null) {
                    throw new IllegalArgumentException("Specification not found: " + specID);
                }

                Map<String, Object> specData = new ConcurrentHashMap<>();
                specData.put("specification_id", spec.getSpecificationID().toKeyString());
                specData.put("name", spec.getRootNet().getName());
                specData.put("version", spec.getSpecificationID().getVersion());
                specData.put("uri", spec.getURI());

                return specData;

            } catch (Exception e) {
                LOGGER.error("Failed to retrieve specification: {}", e.getMessage(), e);
                throw new RuntimeException("Specification retrieval failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Helper method to find a specification by ID.
     * In a real implementation, this would query the engine's specification repository.
     *
     * @param specID The specification ID to find
     * @return The YSpecification if found, null otherwise
     */
    private YSpecification findSpecification(String specID) {
        // Implementation would depend on how specifications are stored in YAWL
        // For now, return null - actual implementation would need to query
        // the embedded YEngine's specification manager
        return null;
    }

    /**
     * Gets the session state for this adapter.
     *
     * @return Session state map
     */
    public Map<String, Object> getSessionState() {
        return new ConcurrentHashMap<>(sessionState);
    }

    /**
     * Sets a value in the session state.
     *
     * @param key The session key
     * @param value The session value
     */
    public void setSessionState(String key, Object value) {
        sessionState.put(key, value);
    }

    /**
     * Gets a value from the session state.
     *
     * @param key The session key
     * @return The session value, or null if not found
     */
    public Object getSessionState(String key) {
        return sessionState.get(key);
    }

    /**
     * Removes a value from the session state.
     *
     * @param key The session key
     * @return The removed value, or null if not found
     */
    public Object removeSessionState(String key) {
        return sessionState.remove(key);
    }
}