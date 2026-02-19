package org.yawlfoundation.yawl.mcp.a2a.service;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Retry mechanism with jitter for MCP client operations.
 *
 * <p>Implements exponential backoff with randomized jitter to avoid thundering herd
 * problems when multiple clients retry simultaneously. The jitter adds randomness
 * to retry timing, distributing load more evenly.</p>
 *
 * <h2>Retry Formula</h2>
 * <pre>
 * baseInterval * (multiplier ^ attemptNumber) * (1 + random(-jitter, +jitter))
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

    private final Retry retry;
    private final CircuitBreakerProperties.RetryConfig properties;

    /**
     * Creates a new retry mechanism with the given configuration.
     *
     * @param properties the retry configuration properties
     */
    public McpRetryWithJitter(CircuitBreakerProperties.RetryConfig properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.retry = createRetry();
    }

    /**
     * Creates a new retry mechanism for a specific server.
     *
     * @param serverName the MCP server name
     * @param properties the retry configuration properties
     */
    public McpRetryWithJitter(String serverName, CircuitBreakerProperties.RetryConfig properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.retry = createRetryForServer(serverName);
    }

    /**
     * Executes the given supplier with retry logic.
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

        int attempts = 0;
        Exception lastException = null;

        while (attempts < properties.maxAttempts()) {
            attempts++;
            try {
                T result = supplier.get();
                if (attempts > 1) {
                    LOGGER.info("MCP operation succeeded on attempt {}/{}: server={}, operation={}",
                               attempts, properties.maxAttempts(), serverId, operation);
                }
                return result;
            } catch (Exception e) {
                lastException = e;

                if (attempts >= properties.maxAttempts()) {
                    LOGGER.error("MCP operation failed after {} attempts: server={}, operation={}, error={}",
                                attempts, serverId, operation, e.getMessage());
                    break;
                }

                long waitMillis = calculateWaitDuration(attempts);
                LOGGER.warn("MCP operation failed (attempt {}/{}), retrying in {}ms: server={}, operation={}, error={}",
                           attempts, properties.maxAttempts(), waitMillis, serverId, operation, e.getMessage());

                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new McpClientException(
                        "Retry interrupted", serverId, operation, attempts, ie);
                }
            }
        }

        throw new McpClientException(
            "Operation failed after " + attempts + " attempts: " +
            (lastException != null ? lastException.getMessage() : "unknown error"),
            serverId, operation, attempts, lastException);
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
     * Calculates the wait duration for a given attempt with jitter.
     *
     * @param attemptNumber the current attempt number (1-based)
     * @return the wait duration in milliseconds
     */
    public long calculateWaitDuration(int attemptNumber) {
        // Exponential backoff: baseInterval * (multiplier ^ (attemptNumber - 1))
        long exponentialWait = (long) (properties.waitDurationMs() *
            Math.pow(properties.exponentialBackoffMultiplier(), attemptNumber - 1));

        // Apply jitter: multiply by (1 + random factor in range [-jitter, +jitter])
        double jitterRange = properties.jitterFactor();
        double jitterMultiplier = 1.0 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterRange;

        long waitWithJitter = (long) (exponentialWait * jitterMultiplier);

        LOGGER.debug("Calculated wait duration: attempt={}, exponential={}ms, jitter={}%, final={}ms",
                    attemptNumber, exponentialWait, (int)(jitterMultiplier * 100), waitWithJitter);

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

    private Retry createRetry() {
        return createRetryForServer("mcp-default");
    }

    private Retry createRetryForServer(String serverName) {
        IntervalFunction intervalFunction = attempt -> calculateWaitDuration(attempt);

        RetryConfig config = RetryConfig.custom()
            .maxAttempts(properties.maxAttempts())
            .intervalFunction(intervalFunction)
            .retryOnException(this::shouldRetryOn)
            .build();

        RetryRegistry registry = RetryRegistry.of(config);
        return registry.retry(serverName + "-retry", config);
    }

    private boolean shouldRetryOn(Throwable throwable) {
        // Retry on transient errors
        return throwable instanceof java.io.IOException
            || throwable instanceof java.util.concurrent.TimeoutException
            || throwable instanceof java.net.SocketTimeoutException
            || throwable instanceof McpClientException;
    }
}
