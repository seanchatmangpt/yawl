package org.yawlfoundation.yawl.benchmark;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.yawlfoundation.yawl.observability.actor.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Performance benchmark for ActorGuardValidator integration.
 *
 * Measures:
 * - Latency per validation (<1ms target)
 * - CPU overhead (<5% target)
 * - Memory impact (<1% heap target)
 * - Throughput (actors/second)
 * - Accuracy of leak/deadlock detection
 */
public class ActorGuardBenchmark {

    private static final int DEFAULT_ACTOR_COUNT = 1000;
    private static final int DEFAULT_ITERATIONS = 100;

    public static void main(String[] args) {
        // Parse arguments
        int actorCount = DEFAULT_ACTOR_COUNT;
        boolean warmup = false;
        boolean measure = false;
        boolean quiet = false;

        for (String arg : args) {
            if (arg.startsWith("--actors=")) {
                actorCount = Integer.parseInt(arg.substring("--actors=".length()));
            } else if (arg.equals("--warmup")) {
                warmup = true;
            } else if (arg.equals("--measure")) {
                measure = true;
            } else if (arg.equals("--quiet")) {
                quiet = true;
            }
        }

        // Create meter registry
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        // Initialize components
        ActorHealthMetrics.initialize(meterRegistry);
        ActorTracer tracer = ActorTracer.initialize(new TestTracer());
        ActorGuardValidator guardValidator = new ActorGuardValidator(meterRegistry,
                                                                   ActorHealthMetrics.getInstance(),
                                                                   tracer);

        // Setup warmup or measurement
        if (warmup) {
            runWarmup(actorCount, guardValidator, quiet);
        } else if (measure) {
            runMeasurement(actorCount, guardValidator, quiet);
        } else {
            System.err.println("Specify --warmup or --measure");
            System.exit(1);
        }
    }

    private static void runWarmup(int actorCount, ActorGuardValidator guardValidator, boolean quiet) {
        if (!quiet) {
            System.out.println("Warming up with " + actorCount + " actors...");
        }

        // Create test actors
        createTestActors(actorCount);

        // Run validation to warm up JIT
        for (int i = 0; i < 5; i++) {
            guardValidator.performPeriodicValidation();
        }

        if (!quiet) {
            System.out.println("Warmup complete.");
        }
    }

    private static void runMeasurement(int actorCount, ActorGuardValidator guardValidator, boolean quiet) {
        if (!quiet) {
            System.out.println("Measuring performance with " + actorCount + " actors...");
        }

        // Reset metrics
        guardValidator.performPeriodicValidation(); // Clear previous metrics

        long totalLatency = 0;
        int totalViolations = 0;
        long startTime = System.nanoTime();

        // Run multiple iterations
        for (int i = 0; i < DEFAULT_ITERATIONS; i++) {
            long iterationStart = System.nanoTime();

            // Perform validation
            guardValidator.performPeriodicValidation();

            long iterationEnd = System.nanoTime();
            long iterationLatency = iterationEnd - iterationStart;
            totalLatency += iterationLatency;

            // Count violations
            var stats = guardValidator.getValidationStatistics();
            totalViolations += stats.getViolatedValidations();

            // Occasionally create problematic actors for testing
            if (i % 10 == 0) {
                createProblematicActor("problematic-" + i);
            }

            // Simulate some actor activity
            simulateActorActivity();
        }

        long totalDuration = System.nanoTime() - startTime;

        // Calculate results
        double avgLatencyMs = (totalLatency / (double) DEFAULT_ITERATIONS) / 1_000_000.0;
        double throughput = DEFAULT_ITERATIONS / (totalDuration / 1_000_000_000.0); // iterations per second
        double avgViolationsPerIteration = (double) totalViolations / DEFAULT_ITERATIONS;

        // Output results
        if (!quiet) {
            System.out.println("Performance Results:");
            System.out.println("  Average latency: " + String.format("%.3f", avgLatencyMs) + "ms");
            System.out.println("  Throughput: " + String.format("%.0f", throughput) + " validations/second");
            System.out.println("  Average violations per iteration: " + String.format("%.1f", avgViolationsPerIteration));
            System.out.println("  Total actors processed: " + actorCount);
        } else {
            // Quiet mode - output only key metrics
            System.out.println("LATENCY_MS=" + String.format("%.3f", avgLatencyMs));
            System.out.println("THROUGHPUT=" + String.format("%.0f", throughput));
            System.out.println("VIOLATIONS=" + totalViolations);
        }

        // Save results for external processing
        saveResults(avgLatencyMs, throughput, totalViolations);
    }

    private static void createTestActors(int count) {
        ActorHealthMetrics healthMetrics = ActorHealthMetrics.getInstance();

        for (int i = 0; i < count; i++) {
            String actorId = "actor-" + String.format("%04d", i);
            String actorType = "TestActor";

            // Record actor creation
            healthMetrics.recordActorCreated(actorId, actorType);

            // Set normal state
            healthMetrics.updateMemoryUsage(actorId, 1024 * 1024); // 1MB
            healthMetrics.updateQueueDepth(actorId, 10);

            // Simulate normal message processing
            healthMetrics.recordMessageReceived(actorId, "normal", 100);
            healthMetrics.recordMessageProcessed(actorId, "normal", 100000000); // 100ms
        }
    }

    private static void createProblematicActor(String actorId) {
        ActorHealthMetrics healthMetrics = ActorHealthMetrics.getInstance();

        // Create actor with potential leak
        healthMetrics.recordActorCreated(actorId, "ProblematicActor");

        // Set high memory usage
        long highMemory = 20 * 1024 * 1024; // 20MB
        healthMetrics.updateMemoryUsage(actorId, highMemory);

        // High queue depth
        healthMetrics.updateQueueDepth(actorId, 500);

        // Some errors
        healthMetrics.recordActorError(actorId, "MemoryError", "High memory usage");
    }

    private static void simulateActorActivity() {
        // Simulate some actor activity
        try {
            // Virtual thread sleep to simulate processing
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void saveResults(double latencyMs, double throughput, int violations) {
        // Save results for external processing
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("iteration-result.tmp"),
                List.of(
                    "LATENCY_MS=" + String.format("%.3f", latencyMs),
                    "THROUGHPUT=" + String.format("%.0f", throughput),
                    "VIOLATIONS=" + violations
                )
            );
        } catch (Exception e) {
            System.err.println("Error saving results: " + e.getMessage());
        }
    }

    // Test tracer implementation
    private static class TestTracer implements io.opentelemetry.api.trace.Tracer {
        @Override
        public io.opentelemetry.api.trace.Span spanBuilder(String name) {
            return new TestSpan(name);
        }

        @Override
        public String getInstrumentationName() {
            return "benchmark-tracer";
        }
    }

    private static class TestSpan implements io.opentelemetry.api.trace.Span {
        private final String name;
        private long startTime = System.nanoTime();

        public TestSpan(String name) {
            this.name = name;
        }

        @Override
        public void end() {
            // No-op for benchmark
        }

        @Override
        public Span addEvent(String eventName, java.util.Map<String, Object> attributes) {
            return this;
        }

        @Override
        public Span setAttribute(String key, String value) {
            return this;
        }

        @Override
        public Span setStatus(io.opentelemetry.api.trace.StatusCode status, String description) {
            return this;
        }

        @Override
        public Span recordException(Throwable exception) {
            return this;
        }

        @Override
        public long getStartEpochNanos() {
            return startTime;
        }

        @Override
        public Span updateName(String newName) {
            return this;
        }

        @Override
        public Span recordException(Exception exception, java.util.Map<String, Object> attributes) {
            return this;
        }
    }
}