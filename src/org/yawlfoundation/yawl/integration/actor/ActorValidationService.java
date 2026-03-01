package org.yawlfoundation.yawl.integration.actor;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Actor Validation Service integrated with MCP
 *
 * Provides MCP tools for actor validation including:
 * - Memory leak detection
 * - Deadlock analysis
 * - Performance monitoring
 * - Health checks
 *
 * @since 6.0.0
 */
public class ActorValidationService {

    private final ActorValidator validator;
    private final McpServer mcpServer;
    private final Map<String, Map<String, Object>> validationResults = new ConcurrentHashMap<>();

    public ActorValidationService(ActorValidator validator, McpServer mcpServer) {
        this.validator = validator;
        this.mcpServer = mcpServer;
    }

    /**
     * Initialize MCP tools for actor validation
     */
    public void initializeMcpTools() {
        // Add actor validation tools to MCP server
        addActorValidationTools();
        addActorMonitoringTools();
        addActorHealthTools();
    }

    /**
     * Add actor validation tools
     */
    private void addActorValidationTools() {
        // Tool: Validate actor for memory leaks
        mcpServer.addTool("actor_validate_memory", new McpSchema.Tool(
            new McpSchema.Tool.InputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "case_id", Map.of(
                            "type", "string",
                            "description", "The case ID to validate"
                        ),
                        "threshold_mb", Map.of(
                            "type", "number",
                            "description", "Memory leak detection threshold in MB",
                            "default", 100.0
                        )
                    ),
                    "required", List.of("case_id")
                )
            )
        ), arguments -> {
            String caseId = (String) arguments.get("case_id");
            double threshold = ((Number) arguments.getOrDefault("threshold_mb", 100.0)).doubleValue();

            try {
                // Get current metrics
                var metrics = validator.getMetrics(caseId);
                if (metrics.isEmpty()) {
                    return Map.of(
                        "success", false,
                        "error", "No metrics found for case: " + caseId
                    );
                }

                // Check memory leak
                double currentUsage = metrics.get().getCurrentMemoryUsage();
                boolean memoryLeak = currentUsage > threshold;

                // Store result
                Map<String, Object> result = new HashMap<>();
                result.put("case_id", caseId);
                result.put("current_mb", currentUsage);
                result.put("threshold_mb", threshold);
                result.put("memory_leak_detected", memoryLeak);
                result.put("timestamp", System.currentTimeMillis());

                validationResults.put(caseId + "_memory", result);

                return Map.of(
                    "success", true,
                    "case_id", caseId,
                    "current_mb", currentUsage,
                    "threshold_mb", threshold,
                    "memory_leak_detected", memoryLeak,
                    "status", memoryLeak ? "WARNING" : "OK"
                );

            } catch (Exception e) {
                return Map.of(
                    "success", false,
                    "error", "Memory validation failed: " + e.getMessage()
                );
            }
        });

        // Tool: Validate actor for deadlock potential
        mcpServer.addTool("actor_validate_deadlock", new McpSchema.Tool(
            new McpSchema.Tool.InputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "case_id", Map.of(
                            "type", "string",
                            "description", "The case ID to validate"
                        )
                    ),
                    "required", List.of("case_id")
                )
            )
        ), arguments -> {
            String caseId = (String) arguments.get("case_id");

            try {
                // Get current metrics
                var metrics = validator.getMetrics(caseId);
                if (metrics.isEmpty()) {
                    return Map.of(
                        "success", false,
                        "error", "No metrics found for case: " + caseId
                    );
                }

                // Simulate deadlock detection (would use real analysis)
                boolean deadlockDetected = metrics.get().getDeadlockCount() > 0;

                // Store result
                Map<String, Object> result = new HashMap<>();
                result.put("case_id", caseId);
                result.put("deadlock_detected", deadlockDetected);
                result.put("deadlock_count", metrics.get().getDeadlockCount());
                result.put("timestamp", System.currentTimeMillis());

                validationResults.put(caseId + "_deadlock", result);

                return Map.of(
                    "success", true,
                    "case_id", caseId,
                    "deadlock_detected", deadlockDetected,
                    "deadlock_count", metrics.get().getDeadlockCount(),
                    "status", deadlockDetected ? "CRITICAL" : "OK"
                );

            } catch (Exception e) {
                return Map.of(
                    "success", false,
                    "error", "Deadlock validation failed: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Add actor monitoring tools
     */
    private void addActorMonitoringTools() {
        // Tool: Get actor performance metrics
        mcpServer.addTool("actor_performance_metrics", new McpSchema.Tool(
            new McpSchema.Tool.InputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "case_id", Map.of(
                            "type", "string",
                            "description", "The case ID to monitor"
                        ),
                        "time_range_minutes", Map.of(
                            "type", "number",
                            "description",
                            "Time range for metrics (minutes)",
                            "default", 60.0
                        )
                    ),
                    "required", List.of("case_id")
                )
            )
        ), arguments -> {
            String caseId = (String) arguments.get("case_id");

            try {
                var metrics = validator.getMetrics(caseId);
                if (metrics.isEmpty()) {
                    return Map.of(
                        "success", false,
                        "error", "No metrics found for case: " + caseId
                    );
                }

                ActorValidationMetrics m = metrics.get();
                Map<String, Object> performance = new HashMap<>();
                performance.put("case_id", caseId);
                performance.put("validation_count", m.getValidationCount());
                performance.put("average_processing_ms", m.getAverageProcessingTime().toMillis());
                performance.put("current_memory_mb", m.getCurrentMemoryUsage());
                performance.put("slow_processing_count", m.getSlowProcessingCount());
                performance.put("error_count", m.getErrorCount());
                performance.put("status", m.getHealthStatus());
                performance.put("timestamp", System.currentTimeMillis());

                return Map.of(
                    "success", true,
                    "performance", performance
                );

            } catch (Exception e) {
                return Map.of(
                    "success", false,
                    "error", "Performance metrics failed: " + e.getMessage()
                );
            }
        });

        // Tool: List all active actors
        mcpServer.addTool("actor_list_active", new McpSchema.Tool(
            new McpSchema.Tool.InputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of()
                )
            )
        ), arguments -> {
            try {
                Collection<ActorValidationMetrics> allMetrics = validator.getAllMetrics();

                List<Map<String, Object>> actors = new ArrayList<>();
                for (ActorValidationMetrics metrics : allMetrics) {
                    Map<String, Object> actorInfo = new HashMap<>();
                    actorInfo.put("case_id", metrics.getCaseId());
                    actorInfo.put("status", metrics.getHealthStatus());
                    actorInfo.put("validation_count", metrics.getValidationCount());
                    actorInfo.put("memory_leak_count", metrics.getMemoryLeakCount());
                    actorInfo.put("deadlock_count", metrics.getDeadlockCount());
                    actorInfo.put("last_validation", System.currentTimeMillis());
                    actors.add(actorInfo);
                }

                return Map.of(
                    "success", true,
                    "actors", actors,
                    "total_count", actors.size()
                );

            } catch (Exception e) {
                return Map.of(
                    "success", false,
                    "error", "Failed to list active actors: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Add actor health tools
     */
    private void addActorHealthTools() {
        // Tool: Get actor health summary
        mcpServer.addTool("actor_health_summary", new McpSchema.Tool(
            new McpSchema.Tool.InputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "case_id", Map.of(
                            "type", "string",
                            "description", "The case ID to check"
                        )
                    ),
                    "required", List.of("case_id")
                )
            )
        ), arguments -> {
            String caseId = (String) arguments.get("case_id");

            try {
                var metrics = validator.getMetrics(caseId);
                if (metrics.isEmpty()) {
                    return Map.of(
                        "success", false,
                        "error", "No metrics found for case: " + caseId
                    );
                }

                ActorValidationMetrics m = metrics.get();
                Map<String, Object> health = new HashMap<>();
                health.put("case_id", caseId);
                health.put("status", m.getHealthStatus());
                health.put("memory_leak_detected", m.getMemoryLeakCount() > 0);
                health.put("deadlock_detected", m.getDeadlockCount() > 0);
                health.put("performance_ok", m.getSlowProcessingCount() <= 5);
                health.put("error_rate_ok", m.getErrorCount() <= 3);
                health.put("last_validation", System.currentTimeMillis());

                Map<String, String> recommendations = new ArrayList<>() {{
                    add("Monitor memory usage closely");
                    add("Check for deadlock patterns");
                    add("Review processing time");
                    add("Review error logs");
                }};

                if (m.getHealthStatus().equals("CRITICAL")) {
                    recommendations.add("Immediate investigation required");
                }

                health.put("recommendations", recommendations);

                return Map.of(
                    "success", true,
                    "health", health
                );

            } catch (Exception e) {
                return Map.of(
                    "success", false,
                    "error", "Health check failed: " + e.getMessage()
                );
            }
        });

        // Tool: Validate all actors
        mcpServer.addTool("actor_validate_all", new McpSchema.Tool(
            new McpSchema.Tool.InputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of()
                )
            )
        ), arguments -> {
            try {
                // Trigger validation for all actors
                validator.validateAllActors();

                // Get summary results
                Collection<ActorValidationMetrics> allMetrics = validator.getAllMetrics();
                int criticalCount = (int) allMetrics.stream()
                    .filter(m -> m.getHealthStatus().equals("CRITICAL"))
                    .count();
                int warningCount = (int) allMetrics.stream()
                    .filter(m -> m.getHealthStatus().equals("WARNING"))
                    .count();

                return Map.of(
                    "success", true,
                    "total_actors", allMetrics.size(),
                    "critical_count", criticalCount,
                    "warning_count", warningCount,
                    "healthy_count", allMetrics.size() - criticalCount - warningCount,
                    "timestamp", System.currentTimeMillis()
                );

            } catch (Exception e) {
                return Map.of(
                    "success", false,
                    "error", "Global validation failed: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Get validation results
     */
    public Map<String, Object> getValidationResults(String caseId) {
        Map<String, Object> results = new HashMap<>();

        // Memory validation
        Map<String, Object> memoryResults = validationResults.get(caseId + "_memory");
        if (memoryResults != null) {
            results.put("memory", memoryResults);
        }

        // Deadlock validation
        Map<String, Object> deadlockResults = validationResults.get(caseId + "_deadlock");
        if (deadlockResults != null) {
            results.put("deadlock", deadlockResults);
        }

        return results;
    }

    /**
     * Clear validation results
     */
    public void clearValidationResults(String caseId) {
        validationResults.remove(caseId + "_memory");
        validationResults.remove(caseId + "_deadlock");
    }

    /**
     * Clear all validation results
     */
    public void clearAllValidationResults() {
        validationResults.clear();
    }
}