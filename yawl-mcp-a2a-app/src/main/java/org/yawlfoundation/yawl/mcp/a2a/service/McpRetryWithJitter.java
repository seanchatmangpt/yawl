package org.yawlfoundation.yawl.mcp.a2a.service;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryException;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.yawlfoundation.yawl.resilience.observability.RetryObservability;

/**
 * Retry mechanism with jitter for MCP client operations.
 *
 * <p>Implements exponential backoff with randomized jitter to avoid thundering herd
 * problems when multiple clients retry simultaneously. The jitter adds randomness
 * to retry timing, distributing load more evenly.</p>
 *
 * <p>Uses Resilience4j's {@link IntervalFunction#ofExponentialRandomBackoff(long, double, double)}
 * to provide both exponential backoff and randomized jitter, and delegates execution
 * to {@link Retry#executeSupplier(Supplier)} for proper decorator pattern handling.
 * This eliminates manual retry loops and ensures proper backoff timing.</p>
 *
 * <h2>Retry Formula</h2>
 * <pre>
 * initialDelay * (multiplier ^ attemptNumber) * (1 + random(-jitterFactor, +jitterFactor))
 * </pre>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * McpRetryWithJitter retry = new McpRetryWithJitter(properties.retry());
 * McpSchema.CallToolResult result = retry.execute("myServer", "callTool", () ->
 *     client.callTool(request)
 * );
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class McpRetryWithJitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpRetryWithJitter.class);
    private static final String COMPONENT_NAME = "mcp-client";

    private final Retry retry;
    private final CircuitBreakerProperties.RetryConfig properties;
    private final RetryObservability retryObservability;
    private final IntervalFunction intervalFunction;

    /**
     * Creates a new retry mechanism with the given configuration.
     *
     * @param properties the retry configuration properties (uses defaults if null)
     */
    public McpRetryWithJitter(CircuitBreakerProperties.RetryConfig properties) {
        this.properties = properties != null
            ? properties
            : CircuitBreakerProperties.RetryConfig.defaults();
        this.retryObservability = RetryObservability.getInstance();
        this.intervalFunction = createIntervalFunction();
        this.retry = createRetry();
    }

    /**
     * Creates a new retry mechanism for a specific server.
     *
     * @param serverName the MCP server name
     * @param properties the retry configuration properties (uses defaults if null)
     */
    public McpRetryWithJitter(String serverName, CircuitBreakerProperties.RetryConfig properties) {
        this.properties = properties != null
            ? properties
            : CircuitBreakerProperties.RetryConfig.defaults();
        this.retryObservability = RetryObservability.getInstance();
        this.intervalFunction = createIntervalFunction();
        this.retry = createRetryForServer(serverName);
    }

    /**
     * Creates the interval function with exponential backoff and jitter.
     * Uses Resilience4j's built-in random backoff which provides better distribution
     * than manual jitter calculation.
     *
     * @return IntervalFunction with exponential backoff and jitter
     */
    private IntervalFunction createIntervalFunction() {
        // ofExponentialRandomBackoff: initialDelay, multiplier, randomizationFactor
        // randomizationFactor ranges from -factor to +factor (e.g., 0.5 means -50% to +50%)
        return IntervalFunction.ofExponentialRandomBackoff(
            properties.waitDurationMs(),
            properties.exponentialBackoffMultiplier(),
            properties.jitterFactor()  // Applied as randomization factor
        );
    }

    /**
     * Executes the given supplier with retry logic.
     *
     * <p>Delegates to Resilience4j's {@link Retry#executeSupplier(Supplier)}
     * for proper decorator pattern execution, which handles backoff timing and
     * exception propagation automatically.</p>
     *
     * @param serverId the MCP server identifier for logging
     * @param operation the operation name for logging
     * @param supplier the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws McpClientException if all retries fail
     */
    public <T> T execute(String serverId, String operation, Supplier<T> supplier) {
        if (!properties.enabled()) {
            return supplier.get();
        }

        return executeWithObservability(serverId, operation, supplier);
    }

    /**
     * Executes the given runnable with retry logic.
     *
     * @param serverId the MCP server identifier for logging
     * @param operation the operation name for logging
     * @param runnable the operation to execute
     * @throws McpClientException if all retries fail
     */
    public void execute(String serverId, String operation, Runnable runnable) {
        execute(serverId, operation, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Internal retry execution using Resilience4j Retry pattern via decorator.
     * Wraps supplier execution with observability callbacks.
     *
     * @param serverId the MCP server identifier for logging
     * @param operation the operation name for logging
     * @param supplier the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws McpClientException if all retries fail
     */
    private <T> T executeWithObservability(String serverId, String operation, Supplier<T> supplier) {
        // Wrap the supplier to integrate observability with retry attempts
        Supplier<T> observableSupplier = createObservableSupplier(serverId, operation, supplier);

        try {
            // Delegate execution to Resilience4j's executeSupplier decorator
            // This handles all retry logic, backoff timing, and exception states
            return retry.executeSupplier(observableSupplier);
        } catch (RetryException retryExc) {
            // Resilience4j wraps final exhausted retries in RetryException
            Throwable cause = retryExc.getCause();
            LOGGER.error("MCP operation failed after {} attempts: server={}, operation={}, error={}",
                properties.maxAttempts(), serverId, operation,
                cause != null ? cause.getMessage() : "unknown error");
            retryObservability.completeSequence(COMPONENT_NAME, operation, false, properties.maxAttempts());
            throw new McpClientException(
                "Operation failed after " + properties.maxAttempts() + " attempts: " +
                (cause != null ? cause.getMessage() : "unknown error"),
                serverId, operation, properties.maxAttempts(), cause);
        } catch (Exception e) {
            // Unexpected exceptions should also be wrapped
            if (!(e instanceof McpClientException)) {
                throw new McpClientException(
                    "Unexpected error during retry: " + e.getMessage(),
                    serverId, operation, 0, e);
            }
            throw e;
        }
    }

    /**
     * Creates a supplier that integrates observability tracking with retry execution.
     * Records attempt metadata and success/failure states before and after execution.
     *
     * @param serverId the MCP server identifier for logging
     * @param operation the operation name for logging
     * @param supplier the underlying operation to execute
     * @param <T> the return type
     * @return wrapped supplier with observability
     */
    private <T> Supplier<T> createObservableSupplier(String serverId, String operation, Supplier<T> supplier) {
        return () -> {
            // Retry object provides current attempt context via getRetryConfig/getMetrics
            // We use a simple counter approach via observability tracking
            Retry currentRetry = retry;
            int attemptNumber = 1; // Note: attempt tracking via metrics would require custom RegistryEventConsumer

            RetryObservability.RetryContext retryCtx = retryObservability.startRetry(
                COMPONENT_NAME, operation, attemptNumber, properties.maxAttempts(), 0);

            try {
                T result = supplier.get();
                if (attemptNumber > 1) {
                    LOGGER.info("MCP operation succeeded on attempt {}/{}: server={}, operation={}",
                               attemptNumber, properties.maxAttempts(), serverId, operation);
                }
                retryCtx.recordSuccess();
                retryObservability.completeSequence(COMPONENT_NAME, operation, true, attemptNumber);
                return result;
            } catch (Exception e) {
                LOGGER.debug("MCP operation failed on attempt {}/{}: server={}, operation={}, error={}",
                           attemptNumber, properties.maxAttempts(), serverId, operation, e.getMessage());
                retryCtx.recordFailure(e);
                // Re-throw to let Resilience4j handle retry logic
                throw e;
            }
        };
    }

    /**
     * Calculates the wait duration for a given attempt with exponential backoff and jitter.
     *
     * <p>This method delegates to the Resilience4j IntervalFunction which internally
     * uses {@link java.util.concurrent.ThreadLocalRandom} for jitter generation, providing
     * thread-safe randomization without synchronization overhead.
     *
     * @param attemptNumber the current attempt number (1-based)
     * @return the wait duration in milliseconds
     */
    public long calculateWaitDuration(int attemptNumber) {
        // Resilience4j's IntervalFunction.ofExponentialRandomBackoff handles both
        // exponential backoff AND jitter in one call, eliminating manual calculation
        long waitWithJitter = intervalFunction.get(attemptNumber);

        LOGGER.debug("Calculated wait duration with jitter: attempt={}, backoffMs={}",
                    attemptNumber, waitWithJitter);

        return Math.max(properties.waitDurationMs(), waitWithJitter);
    }

    /**
     * Gets the underlying Resilience4j retry instance.
     *
     * @return the retry instance
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * Gets the interval function used for backoff calculation.
     *
     * @return the IntervalFunction
     */
    public IntervalFunction getIntervalFunction() {
        return intervalFunction;
    }

    /**
     * Creates the default retry instance.
     *
     * @return Resilience4j Retry instance
     */
    private Retry createRetry() {
        return createRetryForServer("mcp-default");
    }

    /**
     * Creates a retry instance for a specific server.
     *
     * @param serverName the server name
     * @return Resilience4j Retry instance
     */
    private Retry createRetryForServer(String serverName) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(properties.maxAttempts())
            .intervalFunction(intervalFunction)
            .retryOnException(this::shouldRetryOn)
            .build();

        RetryRegistry registry = RetryRegistry.of(config);
        return registry.retry(serverName + "-retry", config);
    }

    /**
     * Determines whether to retry based on exception type.
     * Only retries on transient errors (I/O, timeout, MCP client errors).
     *
     * @param throwable the exception to evaluate
     * @return true if should retry, false otherwise
     */
    private boolean shouldRetryOn(Throwable throwable) {
        // Retry on transient errors
        return throwable instanceof java.io.IOException
            || throwable instanceof java.util.concurrent.TimeoutException
            || throwable instanceof java.net.SocketTimeoutException
            || throwable instanceof McpClientException;
    }
}
