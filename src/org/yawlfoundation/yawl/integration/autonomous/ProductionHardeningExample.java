package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck;
import org.yawlfoundation.yawl.integration.autonomous.observability.MetricsCollector;
import org.yawlfoundation.yawl.integration.autonomous.observability.StructuredLogger;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;
import org.yawlfoundation.yawl.integration.autonomous.resilience.FallbackHandler;
import org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Example demonstrating production hardening components.
 * Shows how to integrate resilience patterns and observability.
 *
 * NOT for production use - demonstration only.
 *
 * @author YAWL Production Validator
 * @version 5.2
 */
public class ProductionHardeningExample {


    private static final Logger logger = LogManager.getLogger(ProductionHardeningExample.class);
    public static void main(String[] args) throws Exception {
        System.out.println("=== YAWL Production Hardening Example ===\n");

        demonstrateResilience();
        demonstrateObservability();
    }

    /**
     * Demonstrate resilience patterns.
     */
    private static void demonstrateResilience() throws Exception {
        System.out.println("1. RESILIENCE PATTERNS\n");

        // 1. Retry Policy
        System.out.println("Retry Policy:");
        RetryPolicy retryPolicy = new RetryPolicy(3, 1000);

        try {
            String result = retryPolicy.executeWithRetry(() -> {
                System.out.println("  Attempting operation...");
                if (Math.random() > 0.7) {
                    return "Success!";
                }
                throw new Exception("Transient failure");
            });
            System.out.println("  Result: " + result);
        } catch (Exception e) {
            System.out.println("  Failed after retries: " + e.getMessage());
        }

        System.out.println();

        // 2. Circuit Breaker
        System.out.println("Circuit Breaker:");
        CircuitBreaker circuitBreaker = new CircuitBreaker("external-api", 3, 5000);

        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.execute(() -> {
                    if (Math.random() > 0.5) {
                        throw new Exception("Service unavailable");
                    }
                    return "OK";
                });
                System.out.println("  Attempt " + (i + 1) + ": Success");
            } catch (Exception e) {
                System.out.println("  Attempt " + (i + 1) + ": " + e.getMessage());
            }
            System.out.println("  Circuit state: " + circuitBreaker.getState());
        }

        System.out.println();

        // 3. Fallback Handler
        System.out.println("Fallback Handler:");
        FallbackHandler fallbackHandler = new FallbackHandler();

        String result = fallbackHandler.executeWithFallback(
            (java.util.concurrent.Callable<String>) () -> {
                throw new Exception("Primary failed");
            },
            (java.util.concurrent.Callable<String>) () -> {
                System.out.println("  Using fallback operation");
                return "Fallback result";
            },
            "example-operation"
        );
        System.out.println("  Final result: " + result);

        System.out.println("\n");
    }

    /**
     * Demonstrate observability components.
     */
    private static void demonstrateObservability() throws IOException, InterruptedException {
        System.out.println("2. OBSERVABILITY COMPONENTS\n");

        // 1. Metrics Collector
        System.out.println("Metrics Collector:");
        MetricsCollector metrics = new MetricsCollector(9090);

        Map<String, String> labels = new HashMap<>();
        labels.put("agent", "ordering");
        labels.put("domain", "Ordering");

        metrics.incrementCounter("tasks_completed_total", labels);
        metrics.incrementCounter("tasks_completed_total", labels);
        metrics.recordDuration("task_duration_seconds", 1234, labels);

        System.out.println("  Metrics exported at http://localhost:9090/metrics");
        System.out.println("  Sample output:\n" + metrics.exportMetrics());

        // 2. Structured Logger
        System.out.println("Structured Logger:");
        StructuredLogger logger = new StructuredLogger(ProductionHardeningExample.class);

        String correlationId = logger.setCorrelationId(null);
        System.out.println("  Correlation ID: " + correlationId);

        Map<String, String> context = new HashMap<>();
        context.put("agent", "ordering");
        context.put("caseId", "case-123");

        logger.logTaskStarted("task-456", context);
        logger.logTaskCompleted("task-456", 1500, context);

        logger.clearContext();
        System.out.println();

        // 3. Health Check
        System.out.println("Health Check:");
        HealthCheck healthCheck = new HealthCheck(
            "http://localhost:8080/yawl",
            null,
            3000,
            9091
        );

        // Register custom check
        healthCheck.registerCheck("database", () -> {
            return HealthCheck.CheckResult.healthy("Database connection OK");
        });

        System.out.println("  Health endpoints:");
        System.out.println("    - http://localhost:9091/health (full health check)");
        System.out.println("    - http://localhost:9091/health/ready (readiness probe)");
        System.out.println("    - http://localhost:9091/health/live (liveness probe)");

        HealthCheck.HealthResult health = healthCheck.checkHealth();
        System.out.println("  Current status: " + health.getStatus());
        health.getChecks().forEach((name, result) ->
            System.out.println("    - " + name + ": " +
                             (result.isHealthy() ? "healthy" : "unhealthy") +
                             " (" + result.getMessage() + ")")
        );

        // Cleanup
        Thread.sleep(1000);
        metrics.shutdown();
        healthCheck.shutdown();

        System.out.println("\n=== Example Complete ===");
    }
}
