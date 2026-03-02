package org.yawlfoundation.yawl.test.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * Base class for YAWL test agents in multi-agent orchestration.
 *
 * Each agent:
 * - Executes a specific test suite
 * - Collects telemetry (token counts, concurrency, latency)
 * - Reports results and violations
 * - Integrates with LLM testing (Groq/OpenAI)
 */
public abstract class TestAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAgent.class);

    protected final String agentId;
    protected final String testSuiteName;
    protected final MeterRegistry meterRegistry;

    // Metrics per agent
    protected final Counter agentTestsRun;
    protected final Counter agentTestsPassed;
    protected final Counter agentTestsFailed;
    protected final Timer agentExecutionTimer;
    protected final Counter agentAndonViolations;

    protected TestAgent(String agentId, String testSuiteName, MeterRegistry meterRegistry) {
        this.agentId = Objects.requireNonNull(agentId);
        this.testSuiteName = Objects.requireNonNull(testSuiteName);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);

        // Initialize per-agent metrics
        this.agentTestsRun = Counter.builder("yawl.test.agent.run")
                .tag("agent", agentId)
                .description("Tests run by agent " + agentId)
                .register(meterRegistry);

        this.agentTestsPassed = Counter.builder("yawl.test.agent.passed")
                .tag("agent", agentId)
                .description("Tests passed by agent " + agentId)
                .register(meterRegistry);

        this.agentTestsFailed = Counter.builder("yawl.test.agent.failed")
                .tag("agent", agentId)
                .description("Tests failed by agent " + agentId)
                .register(meterRegistry);

        this.agentExecutionTimer = Timer.builder("yawl.test.agent.duration")
                .tag("agent", agentId)
                .description("Total execution time for agent " + agentId)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.agentAndonViolations = Counter.builder("yawl.test.agent.andon.violations")
                .tag("agent", agentId)
                .description("ANDON violations detected by agent " + agentId)
                .register(meterRegistry);
    }

    /**
     * Execute the test suite assigned to this agent.
     *
     * @return AgentTestResults with test metrics and telemetry data
     */
    public AgentTestResults executeTests() {
        LOGGER.info("Agent {} starting test execution for suite: {}", agentId, testSuiteName);

        return agentExecutionTimer.recordCallable(() -> {
            final var startTime = Instant.now();
            final var results = new AgentTestResults(agentId, testSuiteName);

            try {
                // Run the specific test suite
                runTestSuite(results);

                // Update success metrics
                agentTestsRun.increment(results.getTestsRun());
                agentTestsPassed.increment(results.getTestsPassed());
                agentTestsFailed.increment(results.getTestsFailed());

                // Collect telemetry
                collectTelemetry(results);

                final var endTime = Instant.now();
                results.setEndTime(endTime);
                results.setDurationMs(endTime.toEpochMilli() - startTime.toEpochMilli());

                LOGGER.info("Agent {} completed with results: {}", agentId, results.getSummary());

                return results;

            } catch (Exception e) {
                agentTestsFailed.increment();
                results.recordException(e);
                LOGGER.error("Agent {} execution failed", agentId, e);
                throw new RuntimeException("Agent " + agentId + " failed", e);
            }
        });
    }

    /**
     * Run the specific test suite for this agent.
     * Implemented by subclasses for each test type.
     */
    protected abstract void runTestSuite(AgentTestResults results);

    /**
     * Collect telemetry data from the test execution.
     * Can be overridden by subclasses for agent-specific telemetry.
     */
    protected void collectTelemetry(AgentTestResults results) {
        // Default: subclasses can override for specific telemetry
    }

    /**
     * Check for ANDON violations in test results.
     *
     * @return true if violations found, false otherwise
     */
    public boolean checkAndonViolations(AgentTestResults results) {
        if (results.hasViolations()) {
            agentAndonViolations.increment(results.getViolationCount());
            return true;
        }
        return false;
    }

    // Getters
    public String getAgentId() {
        return agentId;
    }

    public String getTestSuiteName() {
        return testSuiteName;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
