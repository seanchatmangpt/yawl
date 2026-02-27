package org.yawlfoundation.yawl.integration.util;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Utility for tracking and logging skill/tool execution time.
 * Used across all A2A skills and MCP tool specifications.
 */
public final class SkillExecutionTimer {

    private static final Logger LOGGER = Logger.getLogger(SkillExecutionTimer.class.getName());

    private final long startTime;
    private final String operationName;

    private SkillExecutionTimer(String operationName) {
        this.operationName = operationName;
        this.startTime = System.currentTimeMillis();
    }

    /** Start timing an operation */
    public static SkillExecutionTimer start(String operationName) {
        return new SkillExecutionTimer(operationName);
    }

    /** Get elapsed time in milliseconds */
    public long elapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    /** End timing and log result */
    public long endAndLog() {
        long elapsed = elapsedMs();
        LOGGER.info(operationName + " completed in " + elapsed + "ms");
        return elapsed;
    }

    /** Execute callable with timing */
    public static <T> TimedResult<T> time(String operationName, Callable<T> operation) {
        SkillExecutionTimer timer = start(operationName);
        try {
            T result = operation.call();
            return new TimedResult<>(result, timer.elapsedMs(), null);
        } catch (Exception e) {
            return new TimedResult<>(null, timer.elapsedMs(), e);
        }
    }

    /** Record for timed operation results */
    public record TimedResult<T>(T result, long elapsedMs, Exception error) {
        public boolean isSuccess() { return error == null; }
    }
}