package org.yawlfoundation.yawl.test.telemetry;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * JUnit 5 TestExecutionListener for collecting telemetry during test runs.
 *
 * Tracks:
 * - Test execution counts
 * - Thread concurrency
 * - Token usage from LLM tests
 * - Latency metrics
 */
public class TelemetryCollectionListener implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryCollectionListener.class);

    private final AgentTestResults results;
    private final LLMTokenCounter tokenCounter;
    private final EngineTestAgent.ConcurrencyTracker concurrencyTracker;
    private final ThreadMXBean threadMxBean;

    public TelemetryCollectionListener(
            AgentTestResults results,
            LLMTokenCounter tokenCounter,
            Object concurrencyTracker) {
        this.results = results;
        this.tokenCounter = tokenCounter;
        this.concurrencyTracker = (EngineTestAgent.ConcurrencyTracker) concurrencyTracker;
        this.threadMxBean = ManagementFactory.getThreadMXBean();
    }

    @Override
    public void testStarted(TestIdentifier testIdentifier) {
        results.recordTestRun();

        // Track concurrent threads at test start
        int threadCount = threadMxBean.getThreadCount();
        concurrencyTracker.recordThreadCount(threadCount);
    }

    @Override
    public void testFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        // Record test result
        if (result.getStatus() == org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL) {
            results.recordTestPassed();
        } else if (result.getStatus() == org.junit.platform.engine.TestExecutionResult.Status.FAILED) {
            results.recordTestFailed();

            // Record failure as potential ANDON violation
            if (result.getThrowable().isPresent()) {
                var violation = new AgentTestResults.AndonViolation(
                        "TEST_FAILURE",
                        "ERROR",
                        "Test " + testIdentifier.getDisplayName() + " failed: " +
                                result.getThrowable().get().getMessage()
                );
                results.addViolation(violation);
            }
        }
    }

    @Override
    public void testAborted(TestIdentifier testIdentifier, Throwable cause) {
        results.recordTestFailed();
        var violation = new AgentTestResults.AndonViolation(
                "TEST_ABORTED",
                "WARNING",
                "Test " + testIdentifier.getDisplayName() + " aborted: " + cause.getMessage()
        );
        results.addViolation(violation);
    }
}
