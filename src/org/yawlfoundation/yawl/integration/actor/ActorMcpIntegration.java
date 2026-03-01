package org.yawlfoundation.yawl.integration.actor;

import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;
import org.yawlfoundation.yawl.integration.observability.ObservabilityService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Integration for Actor Validation
 *
 * Integrates actor validation tools into the YAWL MCP server
 * providing real-time actor health monitoring and validation.
 *
 * @since 6.0.0
 */
public class ActorMcpIntegration {

    private final YawlMcpServer mcpServer;
    private final ObservabilityService observabilityService;
    private final Map<String, ActorValidator> validators = new ConcurrentHashMap<>();

    public ActorMcpIntegration(YawlMcpServer mcpServer, ObservabilityService observabilityService) {
        this.mcpServer = mcpServer;
        this.observabilityService = observabilityService;
    }

    /**
     * Initialize actor validation MCP integration
     */
    public void initialize() {
        // Create actor validator
        ActorValidator validator = createActorValidator();
        validators.put("default", validator);

        // Create validation service
        ActorValidationService validationService = new ActorValidationService(validator, mcpServer);

        // Initialize MCP tools
        validationService.initializeMcpTools();

        // Add resources
        addActorValidationResources(validationService);

        // Emit initialization event
        observabilityService.emitEvent("actor.mcp.integration.initialized", Map.of(
            "timestamp", System.currentTimeMillis(),
            "tools_added", 6,
            "resources_added", 3
        ));
    }

    /**
     * Create actor validator with proper dependencies
     */
    private ActorValidator createActorValidator() {
        try {
            // Get existing clients from MCP server
            var interfaceAClient = mcpServer.getInterfaceAClient();
            var interfaceBClient = mcpServer.getInterfaceBClient();

            // Create A2A server instance
            var a2aServer = new VirtualThreadYawlA2AServer(interfaceAClient, interfaceBClient);

            // Create actor validator
            ActorValidator validator = new ActorValidator(
                interfaceAClient,
                interfaceBClient,
                a2aServer,
                mcpServer,
                observabilityService
            );

            // Initialize validator
            validator.initialize();

            return validator;

        } catch (Exception e) {
            throw new UnsupportedOperationException(
                "Failed to create actor validator. " +
                "Ensure MCP server is properly initialized with interface clients. " +
                "See IMPLEMENTATION_GUIDED.md for MCP setup."
            );
        }
    }

    /**
     * Add actor validation resources to MCP
     */
    private void addActorValidationResources(ActorValidationService validationService) {
        // Resource: Actor validation results
        mcpServer.addResource("actor_validation", new YawlMcpServer.Resource(
            "Actor Validation Results",
            "application/json",
            () -> {
                // Return aggregated validation results
                Map<String, Object> results = new ConcurrentHashMap<>();
                results.put("timestamp", System.currentTimeMillis());
                results.put("type", "actor_validation_summary");

                // Add all validator results
                for (var validator : validators.values()) {
                    Collection<ActorValidationMetrics> metrics = validator.getAllMetrics();
                    results.put("total_actors", metrics.size());

                    long criticalCount = metrics.stream()
                        .filter(m -> m.getHealthStatus().equals("CRITICAL"))
                        .count();
                    long warningCount = metrics.stream()
                        .filter(m -> m.getHealthStatus().equals("WARNING"))
                        .count();

                    results.put("critical_count", criticalCount);
                    results.put("warning_count", warningCount);
                    results.put("healthy_count", metrics.size() - criticalCount - warningCount);
                }

                return results;
            }
        ));

        // Resource: Actor health status
        mcpServer.addResource("actor_health", new YawlMcpServer.Resource(
            "Actor Health Status",
            "application/json",
            () -> {
                Map<String, Object> health = new ConcurrentHashMap<>();
                health.put("timestamp", System.currentTimeMillis());
                health.put("type", "actor_health_status");

                for (var validator : validators.values()) {
                    Collection<ActorValidationMetrics> metrics = validator.getAllMetrics();
                    for (var metric : metrics) {
                        Map<String, Object> actorHealth = new ConcurrentHashMap<>();
                        actorHealth.put("case_id", metric.getCaseId());
                        actorHealth.put("status", metric.getHealthStatus());
                        actorHealth.put("memory_leak_count", metric.getMemoryLeakCount());
                        actorHealth.put("deadlock_count", metric.getDeadlockCount());
                        actorHealth.put("last_validation", System.currentTimeMillis());

                        health.put("actor_" + metric.getCaseId(), actorHealth);
                    }
                }

                return health;
            }
        ));

        // Resource: Performance metrics
        mcpServer.addResource("actor_performance", new YawlMcpServer.Resource(
            "Actor Performance Metrics",
            "application/json",
            () -> {
                Map<String, Object> performance = new ConcurrentHashMap<>();
                performance.put("timestamp", System.currentTimeMillis());
                performance.put("type", "actor_performance_metrics");

                for (var validator : validators.values()) {
                    Collection<ActorValidationMetrics> metrics = validator.getAllMetrics();
                    for (var metric : metrics) {
                        Map<String, Object> actorPerformance = new ConcurrentHashMap<>();
                        actorPerformance.put("case_id", metric.getCaseId());
                        actorPerformance.put("average_processing_ms", metric.getAverageProcessingTime().toMillis());
                        actorPerformance.put("total_processing_ms", metric.getTotalProcessingTime().toMillis());
                        actorPerformance.put("slow_processing_count", metric.getSlowProcessingCount());
                        actorPerformance.put("error_count", metric.getErrorCount());
                        actorPerformance.put("current_memory_mb", metric.getCurrentMemoryUsage());

                        performance.put("actor_" + metric.getCaseId(), actorPerformance);
                    }
                }

                return performance;
            }
        ));
    }

    /**
     * Get actor validator
     */
    public ActorValidator getValidator(String name) {
        return validators.get(name);
    }

    /**
     * Add a new validator
     */
    public void addValidator(String name, ActorValidator validator) {
        validators.put(name, validator);
    }

    /**
     * Shutdown all validators
     */
    public void shutdown() {
        for (var validator : validators.values()) {
            try {
                validator.shutdown();
            } catch (Exception e) {
                // Log error
            }
        }
        validators.clear();

        // Emit shutdown event
        observabilityService.emitEvent("actor.mcp.integration.shutdown", Map.of(
            "timestamp", System.currentTimeMillis()
        ));
    }
}