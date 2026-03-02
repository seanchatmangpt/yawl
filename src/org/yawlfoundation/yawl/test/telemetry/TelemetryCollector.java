package org.yawlfoundation.yawl.test.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central telemetry collector for multi-agent test orchestration.
 *
 * Responsibilities:
 * - Aggregate metrics from all 5 test agents
 * - Track token counts (Groq + OpenAI)
 * - Record concurrency statistics
 * - Calculate throughput and latency percentiles
 * - Generate final metrics report
 */
public class TelemetryCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryCollector.class);

    private final MeterRegistry meterRegistry;

    // Aggregated metrics
    private final AtomicLong totalTokensGroq = new AtomicLong(0);
    private final AtomicLong totalTokensOpenAI = new AtomicLong(0);
    private final AtomicLong totalTokensOther = new AtomicLong(0);
    private final AtomicLong totalTests = new AtomicLong(0);
    private final AtomicLong totalTestsPassed = new AtomicLong(0);
    private final AtomicLong totalTestsFailed = new AtomicLong(0);

    // Metrics per agent
    private final Map<String, AgentMetrics> agentMetrics = new HashMap<>();

    // Counters for aggregation
    private final Counter totalTokensCounter;
    private final Counter totalTestsCounter;
    private final Timer aggregationTimer;

    public TelemetryCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.totalTokensCounter = Counter.builder("yawl.test.total.tokens")
                .description("Total tokens consumed across all agents")
                .register(meterRegistry);

        this.totalTestsCounter = Counter.builder("yawl.test.total.count")
                .description("Total tests executed across all agents")
                .register(meterRegistry);

        this.aggregationTimer = Timer.builder("yawl.test.telemetry.aggregation.duration")
                .description("Time to aggregate all agent metrics")
                .register(meterRegistry);
    }

    /**
     * Collect telemetry from an individual test agent.
     */
    public void collectAgentTelemetry(TestAgent agent, AgentTestResults agentResults) {
        LOGGER.debug("Collecting telemetry from agent: {}", agent.getAgentId());

        var metrics = new AgentMetrics(agent.getAgentId());

        // Record test counts
        metrics.testsRun = agentResults.getTestsRun();
        metrics.testsPassed = agentResults.getTestsPassed();
        metrics.testsFailed = agentResults.getTestsFailed();

        // Record token counts
        var tokenCounts = agentResults.getTokenCounts();
        if (tokenCounts.containsKey("groq")) {
            metrics.tokensGroq = tokenCounts.get("groq");
            totalTokensGroq.addAndGet(metrics.tokensGroq);
        }
        if (tokenCounts.containsKey("openai-gpt-oss-20b")) {
            metrics.tokensOpenAI = tokenCounts.get("openai-gpt-oss-20b");
            totalTokensOpenAI.addAndGet(metrics.tokensOpenAI);
        }

        // Record latency percentiles
        var latencies = agentResults.getLatencyPercentiles();
        if (latencies.containsKey("p50")) {
            metrics.latencyP50 = latencies.get("p50");
        }
        if (latencies.containsKey("p95")) {
            metrics.latencyP95 = latencies.get("p95");
        }
        if (latencies.containsKey("p99")) {
            metrics.latencyP99 = latencies.get("p99");
        }

        // Record throughput
        metrics.throughputTestsPerSec = agentResults.getThroughputTestsPerSec();

        // Record concurrency
        metrics.concurrencyMetrics = agentResults.getConcurrencyMetrics();

        // Store agent metrics
        agentMetrics.put(agent.getAgentId(), metrics);

        // Update global counters
        totalTests.addAndGet(metrics.testsRun);
        totalTestsPassed.addAndGet(metrics.testsPassed);
        totalTestsFailed.addAndGet(metrics.testsFailed);

        totalTokensCounter.increment(metrics.tokensGroq + metrics.tokensOpenAI);
        totalTestsCounter.increment(metrics.testsRun);

        LOGGER.debug("Telemetry collected for agent: {} - Tests: {}, Tokens: {}",
                agent.getAgentId(), metrics.testsRun,
                metrics.tokensGroq + metrics.tokensOpenAI);
    }

    /**
     * Aggregate metrics from all agents.
     */
    public void aggregateMetrics(List<TestAgent> agents, AggregatedTestResults results) {
        aggregationTimer.record(() -> {
            LOGGER.info("Aggregating metrics from {} agents", agents.size());

            // Add all agent metrics to results
            for (var entry : agentMetrics.entrySet()) {
                results.addAgentMetrics(entry.getKey(), entry.getValue());
            }

            // Calculate aggregated statistics
            results.setTotalTokensGroq(totalTokensGroq.get());
            results.setTotalTokensOpenAI(totalTokensOpenAI.get());
            results.setTotalTests(totalTests.get());
            results.setTotalTestsPassed(totalTestsPassed.get());
            results.setTotalTestsFailed(totalTestsFailed.get());

            // Calculate pass rate
            if (totalTests.get() > 0) {
                double passRate = (double) totalTestsPassed.get() / totalTests.get() * 100;
                results.setPassRate(passRate);
            }

            // Calculate average throughput
            double avgThroughput = agentMetrics.values().stream()
                    .mapToDouble(m -> m.throughputTestsPerSec)
                    .average()
                    .orElse(0.0);
            results.setAverageThroughput(avgThroughput);

            LOGGER.info("Metrics aggregation complete - Total tests: {}, Pass rate: {:.2f}%",
                    totalTests.get(), results.getPassRate());
        });
    }

    /**
     * Get metrics for a specific agent.
     */
    public AgentMetrics getAgentMetrics(String agentId) {
        return agentMetrics.get(agentId);
    }

    /**
     * Get all agent metrics.
     */
    public Map<String, AgentMetrics> getAllAgentMetrics() {
        return new HashMap<>(agentMetrics);
    }

    /**
     * Get total token counts.
     */
    public long getTotalTokens() {
        return totalTokensGroq.get() + totalTokensOpenAI.get();
    }

    /**
     * Data class for agent-level metrics.
     */
    public static class AgentMetrics {
        public final String agentId;
        public long testsRun;
        public long testsPassed;
        public long testsFailed;
        public long tokensGroq;
        public long tokensOpenAI;
        public double latencyP50;
        public double latencyP95;
        public double latencyP99;
        public double throughputTestsPerSec;
        public AgentTestResults.ConcurrencyMetrics concurrencyMetrics;

        public AgentMetrics(String agentId) {
            this.agentId = agentId;
        }

        public long getTotalTokens() {
            return tokensGroq + tokensOpenAI;
        }

        public double getPassRate() {
            if (testsRun == 0) return 0.0;
            return (double) testsPassed / testsRun * 100;
        }
    }
}
