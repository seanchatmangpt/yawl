package org.yawlfoundation.yawl.engine.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yawlfoundation.yawl.engine.api.dto.AgentDTO;
import org.yawlfoundation.yawl.engine.api.dto.MetricsDTO;
import org.yawlfoundation.yawl.engine.api.dto.WorkflowDefDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for agent management.
 * Provides endpoints to list, create, and manage autonomous agents.
 *
 * Base path: /agents
 */
@RestController
@RequestMapping("/agents")
public class AgentController {

    /**
     * In-memory agent registry for demonstration purposes.
     * In production, this would be backed by persistent storage.
     */
    private final Map<UUID, AgentDTO> agents = new HashMap<>();

    /**
     * List all registered agents.
     *
     * GET /agents
     *
     * @return List of all registered agents
     */
    @GetMapping
    public ResponseEntity<List<AgentDTO>> listAgents() {
        List<AgentDTO> agentList = new ArrayList<>(agents.values());
        return ResponseEntity.ok(agentList);
    }

    /**
     * Get a specific agent by ID.
     *
     * GET /agents/{id}
     *
     * @param id Agent UUID
     * @return Agent details or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<AgentDTO> getAgent(@PathVariable UUID id) {
        AgentDTO agent = agents.get(id);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(agent);
    }

    /**
     * Create a new agent with the specified workflow definition.
     *
     * POST /agents
     *
     * @param workflowDef Workflow definition for the new agent
     * @return Created agent with 201 status code
     */
    @PostMapping
    public ResponseEntity<AgentDTO> createAgent(@RequestBody WorkflowDefDTO workflowDef) {
        if (!workflowDef.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        UUID agentId = UUID.randomUUID();
        AgentDTO newAgent = AgentDTO.create(
                agentId,
                org.yawlfoundation.yawl.engine.agent.AgentStatus.idle(),
                workflowDef.workflowId(),
                0L,
                60000L,  // 60 second TTL
                0L,      // uptime
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        agents.put(agentId, newAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(newAgent);
    }

    /**
     * Stop an agent by ID.
     *
     * DELETE /agents/{id}
     *
     * @param id Agent UUID
     * @return 204 No Content on success, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> stopAgent(@PathVariable UUID id) {
        AgentDTO removed = agents.remove(id);
        if (removed == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * List only healthy agents (not expired, not failed).
     *
     * GET /agents/healthy
     *
     * @return List of healthy agents only
     */
    @GetMapping("/healthy")
    public ResponseEntity<List<AgentDTO>> listHealthyAgents() {
        List<AgentDTO> healthyAgents = agents.values()
                .stream()
                .filter(AgentDTO::isHealthy)
                .toList();
        return ResponseEntity.ok(healthyAgents);
    }

    /**
     * Get aggregated metrics for all agents.
     *
     * GET /agents/metrics
     *
     * @return Agent count, healthy count, and other metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<MetricsDTO> getAgentMetrics() {
        int agentCount = agents.size();
        int healthyCount = (int) agents.values()
                .stream()
                .filter(AgentDTO::isHealthy)
                .count();

        long totalWorkCount = agents.values()
                .stream()
                .mapToLong(AgentDTO::workCount)
                .sum();

        double throughput = agentCount > 0
                ? (totalWorkCount * 60.0) / (agents.values()
                    .stream()
                    .mapToLong(AgentDTO::uptime)
                    .average()
                    .orElse(1000))
                : 0.0;

        long avgLatency = agentCount > 0 ? 100 : 0;  // Placeholder
        long oldestItemAge = 0;  // Would be tracked in real implementation

        MetricsDTO metrics = MetricsDTO.create(agentCount, healthyCount, 0, throughput, avgLatency, oldestItemAge);
        return ResponseEntity.ok(metrics);
    }
}
