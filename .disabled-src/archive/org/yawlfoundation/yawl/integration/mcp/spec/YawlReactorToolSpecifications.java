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

package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.reactor.LivingProcessReactor;
import org.yawlfoundation.yawl.integration.reactor.ReactorCycle;

import java.time.Instant;
import java.util.*;

/**
 * MCP tool specifications for the Living Process Reactor.
 *
 * <p>Provides two tools:
 * <ul>
 *   <li>{@code yawl_reactor_status} - Returns last 5 cycles as JSON</li>
 *   <li>{@code yawl_reactor_trigger} - Manually triggers one reactor cycle</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlReactorToolSpecifications {

    private final LivingProcessReactor reactor;

    /**
     * Constructs MCP tool specifications for the reactor.
     *
     * @param reactor the LivingProcessReactor instance (non-null)
     * @throws NullPointerException if reactor is null
     */
    public YawlReactorToolSpecifications(LivingProcessReactor reactor) {
        this.reactor = Objects.requireNonNull(reactor, "reactor must not be null");
    }

    /**
     * Creates the yawl_reactor_status MCP tool specification.
     *
     * @return MCP tool for querying reactor status
     */
    public McpServerFeatures.SyncToolSpecification createStatusToolSpec() {
        // No input arguments
        Map<String, Object> props = Map.of();
        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_reactor_status")
                .description("Query the Living Process Reactor status. Returns the last 5 completed cycles " +
                    "with metrics, mutations, and simulation results.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> getReactorStatus()
        );
    }

    /**
     * Creates the yawl_reactor_trigger MCP tool specification.
     *
     * @return MCP tool for triggering a reactor cycle
     */
    public McpServerFeatures.SyncToolSpecification createTriggerToolSpec() {
        // No input arguments
        Map<String, Object> props = Map.of();
        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_reactor_trigger")
                .description("Manually trigger one Living Process Reactor cycle. " +
                    "Returns the completed cycle with results.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> triggerReactorCycle()
        );
    }

    /**
     * Handles the yawl_reactor_status tool invocation.
     *
     * @return MCP result with reactor status
     */
    private McpSchema.CallToolResult getReactorStatus() {
        try {
            StringBuilder response = new StringBuilder();
            response.append("Living Process Reactor Status\n");
            response.append("=============================\n\n");
            response.append("Running: ").append(reactor.isRunning()).append("\n\n");

            // Get last 5 cycles
            List<ReactorCycle> cycles = reactor.getCycleHistory(5);

            if (cycles.isEmpty()) {
                response.append("No cycles executed yet.\n");
            } else {
                response.append("Last 5 Cycles:\n");
                response.append("--------------\n\n");

                for (ReactorCycle cycle : cycles) {
                    formatCycleForDisplay(response, cycle);
                }
            }

            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(response.toString())),
                false, null, null
            );
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(
                    "Error retrieving reactor status: " + e.getMessage()
                )),
                true, null, null
            );
        }
    }

    /**
     * Handles the yawl_reactor_trigger tool invocation.
     *
     * @return MCP result with the triggered cycle
     */
    private McpSchema.CallToolResult triggerReactorCycle() {
        try {
            ReactorCycle cycle = reactor.runCycle();

            StringBuilder response = new StringBuilder();
            response.append("Reactor Cycle Triggered\n");
            response.append("=======================\n\n");
            formatCycleForDisplay(response, cycle);

            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(response.toString())),
                false, null, null
            );
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(
                    "Error triggering reactor cycle: " + e.getMessage()
                )),
                true, null, null
            );
        }
    }

    /**
     * Formats a ReactorCycle for human-readable display.
     *
     * @param response StringBuilder to append formatted output to
     * @param cycle the cycle to format
     */
    private void formatCycleForDisplay(StringBuilder response, ReactorCycle cycle) {
        response.append("Cycle ID: ").append(cycle.cycleId()).append("\n");
        response.append("Time: ").append(cycle.startTime()).append("\n");
        response.append("Duration: ").append(cycle.durationMs()).append(" ms\n");
        response.append("Outcome: ").append(cycle.outcome()).append("\n");

        // Metrics
        if (!cycle.metricsSnapshot().isEmpty()) {
            response.append("Metrics:\n");
            cycle.metricsSnapshot().forEach((k, v) ->
                response.append("  ").append(k).append(": ").append(v).append("\n")
            );
        }

        // Proposed mutation
        if (cycle.proposedMutation() != null) {
            response.append("Mutation: ").append(cycle.proposedMutation().mutationType())
                .append(" on ").append(cycle.proposedMutation().targetElement()).append("\n");
            response.append("  Rationale: ").append(cycle.proposedMutation().rationale()).append("\n");
            response.append("  Risk Level: ").append(cycle.proposedMutation().riskLevel()).append("\n");
        }

        // Simulation result
        if (cycle.simulationResult() != null) {
            response.append("Simulation: ")
                .append(cycle.simulationResult().soundnessOk() ? "SOUND" : "UNSOUND").append("\n");
            response.append("  Success Rate: ")
                .append(String.format("%.1f%%", cycle.simulationResult().successRate())).append("\n");
        }

        response.append("Committed: ").append(cycle.committed()).append("\n\n");
    }
}
