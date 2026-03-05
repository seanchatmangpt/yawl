package org.yawlfoundation.yawl.resilience.provider;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.resilience.config.ResilienceConfig;
import org.yawlfoundation.yawl.resilience.config.YawlResilienceProperties;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Platform-level resilience provider for YAWL.
 *
 * Provides a simple, consistent API for applying resilience patterns to any operation.
 * Teams get resilience by default without writing boilerplate code.
 *
 * Usage Examples:
 * <pre>
 * // Engine service call with circuit breaker and retry
 * String result = provider.executeEngineCall(() -&gt; engine.launchCase(spec));
 *
 * // External service call with full resilience stack
 * Response response = provider.executeExternalCall(() -&gt; httpClient.post(url, data));
 *
 * // MCP integration with custom patterns
 * McpResponse mcpResult = provider.executeMcpCall(() -&gt; mcpClient.sendRequest(request));
 *
 * // Multi-agent fan-out with rate limiting and bulkhead
 * CompletableFuture&lt;List&lt;Result&gt;&gt; results = provider.executeMultiAgentFanout(
 *     () -&gt; agents.parallelStream().map(Agent::execute).collect(toList())
 * );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlResilienceProvider {

    private static final Logger logger = LoggerFactory.getLogger(YawlResilienceProvider.class);

    private static YawlResilienceProvider instance;
    private final ResilienceConfig resilienceConfig;

    private YawlResilienceProvider(ResilienceConfig resilienceConfig) {
        this.resilienceConfig = resilienceConfig;
    }

    /**
     * Get the singleton instance of the resilience provider.
     * Creates a default instance if none exists.
     *
     * @return the resilience provider instance
     */
    public static synchronized YawlResilienceProvider getInstance() {
        if (instance == null) {
            YawlResilienceProperties properties = new YawlResilienceProperties();
            MeterRegistry meterRegistry = new SimpleMeterRegistry();
            ResilienceConfig config = new ResilienceConfig(properties, meterRegistry);
            instance = new YawlResilienceProvider(config);
            logger.info("Initialized YAWL Resilience Provider with default configuration");
        }
        return instance;
    }

    /**
     * Initialize the resilience provider with custom configuration.
     *
     * @param properties custom resilience properties
     * @param meterRegistry meter registry for metrics
     * @return the initialized resilience provider
     */
    public static synchronized YawlResilienceProvider initialize(
        YawlResilienceProperties properties,
        MeterRegistry meterRegistry) {

        if (instance != null) {
            logger.warn("Resilience provider already initialized, recreating with new configuration");
        }

        ResilienceConfig config = new ResilienceConfig(properties, meterRegistry);
        instance = new YawlResilienceProvider(config);
        logger.info("Initialized YAWL Resilience Provider with custom configuration");
        return instance;
    }

    /**
     * Execute a YAWL engine service call with circuit breaker and retry.
     * Suitable for InterfaceB/InterfaceA calls.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails after retries
     */
    public <T> T executeEngineCall(Callable<T> callable) throws Exception {
        CircuitBreaker circuitBreaker = resilienceConfig.getEngineServiceCircuitBreaker();
        Retry retry = resilienceConfig.getDefaultRetry();

        return Decorators.ofCallable(callable)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .decorate()
            .call();
    }

    /**
     * Execute an external service call with full resilience patterns.
     * Includes circuit breaker, retry with exponential backoff, and timeout.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails after retries
     */
    public <T> T executeExternalCall(Callable<T> callable) throws Exception {
        CircuitBreaker circuitBreaker = resilienceConfig.getExternalServiceCircuitBreaker();
        Retry retry = resilienceConfig.getDefaultRetry();

        return Decorators.ofCallable(callable)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .decorate()
            .call();
    }

    /**
     * Execute an MCP integration call with circuit breaker and retry.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails after retries
     */
    public <T> T executeMcpCall(Callable<T> callable) throws Exception {
        CircuitBreaker circuitBreaker = resilienceConfig.getMcpIntegrationCircuitBreaker();
        Retry retry = resilienceConfig.getDefaultRetry();

        return Decorators.ofCallable(callable)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .decorate()
            .call();
    }

    /**
     * Execute an A2A integration call with circuit breaker and retry.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails after retries
     */
    public <T> T executeA2aCall(Callable<T> callable) throws Exception {
        CircuitBreaker circuitBreaker = resilienceConfig.getA2aIntegrationCircuitBreaker();
        Retry retry = resilienceConfig.getDefaultRetry();

        return Decorators.ofCallable(callable)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .decorate()
            .call();
    }

    /**
     * Execute a multi-agent fan-out operation with rate limiting and bulkhead isolation.
     * Prevents cascade failures when multiple agents are invoked concurrently.
     *
     * @param supplier the operation to execute
     * @param <T> the return type
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeMultiAgentFanout(Supplier<T> supplier) {
        RateLimiter rateLimiter = resilienceConfig.getDefaultRateLimiter();
        Bulkhead bulkhead = resilienceConfig.getDefaultBulkhead();
        Retry retry = resilienceConfig.getDefaultRetry();

        Supplier<CompletionStage<T>> decoratedSupplier = Decorators.ofSupplier(() ->
            CompletableFuture.supplyAsync(supplier)
        )
            .withBulkhead(bulkhead)
            .withRateLimiter(rateLimiter)
            .withRetry(retry)
            .decorate();

        return decoratedSupplier.get().toCompletableFuture();
    }

    /**
     * Execute an operation with custom circuit breaker name.
     *
     * @param circuitBreakerName the circuit breaker name
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails after retries
     */
    public <T> T executeWithCustomCircuitBreaker(String circuitBreakerName, Callable<T> callable) throws Exception {
        CircuitBreaker circuitBreaker = resilienceConfig.getCircuitBreakerRegistry()
            .circuitBreaker(circuitBreakerName);
        Retry retry = resilienceConfig.getDefaultRetry();

        return Decorators.ofCallable(callable)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .decorate()
            .call();
    }

    /**
     * Execute an operation with rate limiting only.
     * Useful for controlling request rates to external APIs.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    public <T> T executeWithRateLimit(Callable<T> callable) throws Exception {
        RateLimiter rateLimiter = resilienceConfig.getDefaultRateLimiter();

        return Decorators.ofCallable(callable)
            .withRateLimiter(rateLimiter)
            .decorate()
            .call();
    }

    /**
     * Execute an operation with bulkhead isolation only.
     * Useful for limiting concurrent executions.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    public <T> T executeWithBulkhead(Callable<T> callable) throws Exception {
        Bulkhead bulkhead = resilienceConfig.getDefaultBulkhead();

        return Decorators.ofCallable(callable)
            .withBulkhead(bulkhead)
            .decorate()
            .call();
    }

    /**
     * Execute an operation with retry only.
     * Includes exponential backoff and jitter.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails after retries
     */
    public <T> T executeWithRetry(Callable<T> callable) throws Exception {
        Retry retry = resilienceConfig.getDefaultRetry();

        return Decorators.ofCallable(callable)
            .withRetry(retry)
            .decorate()
            .call();
    }

    /**
     * Get the underlying resilience configuration for advanced usage.
     *
     * @return the resilience configuration
     */
    public ResilienceConfig getResilienceConfig() {
        return resilienceConfig;
    }

    /**
     * Get a circuit breaker by name.
     *
     * @param name the circuit breaker name
     * @return the circuit breaker instance
     */
    public CircuitBreaker getCircuitBreaker(String name) {
        return resilienceConfig.getCircuitBreakerRegistry().circuitBreaker(name);
    }

    /**
     * Get a retry instance by name.
     *
     * @param name the retry name
     * @return the retry instance
     */
    public Retry getRetry(String name) {
        return resilienceConfig.getRetryRegistry().retry(name);
    }

    /**
     * Get a rate limiter by name.
     *
     * @param name the rate limiter name
     * @return the rate limiter instance
     */
    public RateLimiter getRateLimiter(String name) {
        return resilienceConfig.getRateLimiterRegistry().rateLimiter(name);
    }

    /**
     * Get a bulkhead by name.
     *
     * @param name the bulkhead name
     * @return the bulkhead instance
     */
    public Bulkhead getBulkhead(String name) {
        return resilienceConfig.getBulkheadRegistry().bulkhead(name);
    }
}
