package org.yawlfoundation.yawl.test.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Multi-Agent Test Orchestrator for YAWL telemetry collection.
 *
 * Coordinates 5 independent test agents running different test suites in parallel:
 * 1. EngineTestAgent - Core engine functionality tests
 * 2. StatelessTestAgent - Stateless execution tests
 * 3. IntegrationTestAgent - Cross-module integration tests
 * 4. A2ATestAgent - Agent-to-Agent protocol tests
 * 5. AutonomousAgentTestAgent - Autonomous agent pattern tests
 *
 * Each agent collects:
 * - Token counts (for LLM-based tests)
 * - Concurrency metrics
 * - Latency/throughput measurements
 * - ANDON violations
 */
public class MultiAgentTestOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiAgentTestOrchestrator.class);

    private final MeterRegistry meterRegistry;
    private final ExecutorService executorService;
    private final List<TestAgent> agents;
    private final TelemetryCollector telemetryCollector;
    private final AndonMonitor andonMonitor;

    private final Counter testsStartedCounter;
    private final Counter testsCompletedCounter;
    private final Counter testFailuresCounter;
    private final Timer orchestrationTimer;
    private final AtomicLong activeConcurrency;

    public MultiAgentTestOrchestrator(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.agents = new ArrayList<>();
        this.telemetryCollector = new TelemetryCollector(meterRegistry);
        this.andonMonitor = new AndonMonitor(meterRegistry);

        // Use virtual thread executor for scalability (Java 21+)
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();

        // Initialize metrics
        this.testsStartedCounter = Counter.builder("yawl.test.started")
                .description("Total tests started by orchestrator")
                .register(meterRegistry);

        this.testsCompletedCounter = Counter.builder("yawl.test.completed")
                .description("Total tests completed successfully")
                .register(meterRegistry);

        this.testFailuresCounter = Counter.builder("yawl.test.failures")
                .description("Total test failures across all agents")
                .register(meterRegistry);

        this.orchestrationTimer = Timer.builder("yawl.test.orchestration.duration")
                .description("Total orchestration time for all agents")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.activeConcurrency = new AtomicLong(0);

        // Register concurrency gauge
        io.micrometer.core.instrument.Gauge.builder(
                "yawl.test.concurrency.active",
                activeConcurrency::get
        ).description("Active concurrent test agents")
         .register(meterRegistry);

        initializeAgents();
    }

    /**
     * Initialize the 5 test agents with their respective test suites.
     */
    private void initializeAgents() {
        agents.add(new EngineTestAgent("engine-tests", "EngineTestSuite", meterRegistry));
        agents.add(new StatelessTestAgent("stateless-tests", "StatelessTestSuite", meterRegistry));
        agents.add(new IntegrationTestAgent("integration-tests", "IntegrationTestSuite", meterRegistry));
        agents.add(new A2ATestAgent("a2a-tests", "A2ATestSuite", meterRegistry));
        agents.add(new AutonomousAgentTestAgent("autonomous-tests", "AutonomousTestSuite", meterRegistry));

        LOGGER.info("Initialized {} test agents", agents.size());
    }

    /**
     * Execute all test agents in parallel with telemetry collection.
     *
     * @return aggregated test results from all agents
     */
    public AggregatedTestResults executeAllAgents() {
        LOGGER.info("Starting multi-agent test orchestration at {}", Instant.now());

        return orchestrationTimer.recordCallable(() -> {
            final var results = new AggregatedTestResults();
            final var startTime = Instant.now();

            // Submit all agents for concurrent execution
            var futures = agents.stream()
                    .map(agent -> submitAgent(agent, results))
                    .collect(Collectors.toList());

            // Wait for all agents to complete with timeout
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(5, TimeUnit.MINUTES)
                    .join();

            final var endTime = Instant.now();
            results.setEndTime(endTime);
            results.setTotalDurationMs(endTime.toEpochMilli() - startTime.toEpochMilli());

            // Collect final metrics
            telemetryCollector.aggregateMetrics(agents, results);
            andonMonitor.checkAllViolations(results);

            LOGGER.info("Orchestration completed at {}. Results: {}", endTime, results.getSummary());

            return results;
        });
    }

    /**
     * Submit an individual test agent for execution with telemetry tracking.
     */
    private CompletableFuture<Void> submitAgent(TestAgent agent, AggregatedTestResults results) {
        return CompletableFuture.runAsync(() -> {
            activeConcurrency.incrementAndGet();
            testsStartedCounter.increment();

            try {
                LOGGER.debug("Starting test agent: {}", agent.getAgentId());

                // Execute agent tests
                var agentResults = agent.executeTests();

                // Collect telemetry
                telemetryCollector.collectAgentTelemetry(agent, agentResults);

                // Aggregate results
                results.addAgentResults(agentResults);

                // Check for ANDON violations
                if (agentResults.hasViolations()) {
                    andonMonitor.recordViolation(agent.getAgentId(), agentResults);
                }

                testsCompletedCounter.increment();
                LOGGER.debug("Completed test agent: {}", agent.getAgentId());

            } catch (Exception e) {
                testFailuresCounter.increment();
                LOGGER.error("Test agent {} failed with exception", agent.getAgentId(), e);
                results.addAgentException(agent.getAgentId(), e);

            } finally {
                activeConcurrency.decrementAndGet();
            }

        }, executorService);
    }

    /**
     * Get the telemetry collector for metric details.
     */
    public TelemetryCollector getTelemetryCollector() {
        return telemetryCollector;
    }

    /**
     * Get the ANDON monitor for violation details.
     */
    public AndonMonitor getAndonMonitor() {
        return andonMonitor;
    }

    /**
     * Shutdown the orchestrator and release resources.
     */
    public void shutdown() {
        LOGGER.info("Shutting down multi-agent test orchestrator");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    /**
     * Builder for fluent configuration.
     */
    public static class Builder {
        private MeterRegistry meterRegistry;

        public Builder withMeterRegistry(MeterRegistry registry) {
            this.meterRegistry = registry;
            return this;
        }

        public MultiAgentTestOrchestrator build() {
            if (meterRegistry == null) {
                throw new IllegalStateException("MeterRegistry must be set");
            }
            return new MultiAgentTestOrchestrator(meterRegistry);
        }
    }
}
