package org.yawlfoundation.yawl.engine.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yawlfoundation.yawl.engine.agent.AgentEngineService;
import org.yawlfoundation.yawl.engine.agent.AgentLifecycle;
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
 * Integrated with YawlAgentEngine for real virtual-thread-based agent execution.
 *
 * Base path: /agents
 */
@RestController
@RequestMapping("/agents")
public class AgentController {
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    private final AgentEngineService engineService;

    public AgentController(AgentEngineService engineService) {
        this.engineService = engineService;
    }

    /**
     * List all registered agents.
     *
     * GET /agents
     *
     * @return List of all registered agents with their current states
     */
    @GetMapping
    public ResponseEntity<List<AgentDTO>> listAgents() {
        if (!engineService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ArrayList<>());
        }

        try {
            var registry = engineService.getRegistry();
            Map<String, AgentLifecycle> agentStates = registry.getAllAgentStates();

            List<AgentDTO> agentList = agentStates.entrySet()
                .stream()
                .map(entry -> AgentDTO.create(
                    UUID.fromString(entry.getKey()),
                    convertLifecycleToStatus(entry.getValue()),
                    null,  // workflowId would be tracked separately
                    0L,    // workCount
                    60000L, // default TTL
                    0L,    // uptime
                    java.time.Instant.now(),
                    java.time.Instant.now()
                ))
                .toList();

            return ResponseEntity.ok(agentList);
        } catch (Exception e) {
            logger.error("Error listing agents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
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
        if (!engineService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            var registry = engineService.getRegistry();
            AgentLifecycle lifecycle = registry.getLifecycle(id);

            if (lifecycle == null) {
                return ResponseEntity.notFound().build();
            }

            AgentDTO agent = AgentDTO.create(
                id,
                convertLifecycleToStatus(lifecycle),
                null,
                0L,
                60000L,
                0L,
                java.time.Instant.now(),
                java.time.Instant.now()
            );

            return ResponseEntity.ok(agent);
        } catch (Exception e) {
            logger.error("Error getting agent " + id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        if (!engineService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (!workflowDef.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID agentId = UUID.randomUUID();

            // Convert DTO to domain model
            var workflowDomain = new org.yawlfoundation.yawl.engine.agent.WorkflowDef(
                workflowDef.workflowId(),
                workflowDef.name(),
                workflowDef.description(),
                workflowDef.version()
            );

            // Start the agent on a virtual thread
            var engine = engineService.getEngine();
            boolean started = engine.startAgent(agentId, workflowDomain);

            if (!started) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
            }

            AgentDTO newAgent = AgentDTO.create(
                agentId,
                org.yawlfoundation.yawl.engine.agent.AgentStatus.running(),
                workflowDef.workflowId(),
                0L,
                60000L,  // 60 second TTL
                0L,      // uptime
                java.time.Instant.now(),
                java.time.Instant.now()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(newAgent);
        } catch (Exception e) {
            logger.error("Error creating agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        if (!engineService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            var engine = engineService.getEngine();
            var registry = engineService.getRegistry();

            if (registry.getLifecycle(id) == null) {
                return ResponseEntity.notFound().build();
            }

            if (engine.isRunning(id)) {
                engine.stopAgent(id);
            }

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error stopping agent " + id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        if (!engineService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ArrayList<>());
        }

        try {
            var registry = engineService.getRegistry();
            List<AgentDTO> healthyAgents = registry.getRunningAgents()
                .stream()
                .map(id -> AgentDTO.create(
                    id,
                    convertLifecycleToStatus(registry.getLifecycle(id)),
                    null,
                    0L,
                    60000L,
                    0L,
                    java.time.Instant.now(),
                    java.time.Instant.now()
                ))
                .filter(AgentDTO::isHealthy)
                .toList();

            return ResponseEntity.ok(healthyAgents);
        } catch (Exception e) {
            logger.error("Error listing healthy agents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
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
        if (!engineService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            var registry = engineService.getRegistry();
            int agentCount = registry.getTotalAgents();
            int healthyCount = registry.getRunningAgents().size();
            int failedCount = registry.getFailedAgents().size();

            double throughput = agentCount > 0 ? 1000.0 / agentCount : 0.0;
            long avgLatency = 50;  // Placeholder for real metric collection
            long oldestItemAge = 0;

            MetricsDTO metrics = MetricsDTO.create(agentCount, healthyCount, failedCount, throughput, avgLatency, oldestItemAge);
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Error getting agent metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Helper method to convert AgentLifecycle enum to AgentStatus for DTO.
     *
     * @param lifecycle The lifecycle state
     * @return Converted agent status
     */
    private org.yawlfoundation.yawl.engine.agent.AgentStatus convertLifecycleToStatus(AgentLifecycle lifecycle) {
        return switch (lifecycle) {
            case RUNNING -> org.yawlfoundation.yawl.engine.agent.AgentStatus.running();
            case IDLE -> org.yawlfoundation.yawl.engine.agent.AgentStatus.idle();
            case FAILED -> org.yawlfoundation.yawl.engine.agent.AgentStatus.failed("Lifecycle: " + lifecycle);
            case STOPPED -> org.yawlfoundation.yawl.engine.agent.AgentStatus.stopped();
            default -> org.yawlfoundation.yawl.engine.agent.AgentStatus.idle();
        };
    }
}
