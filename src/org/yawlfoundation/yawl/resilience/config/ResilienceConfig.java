package org.yawlfoundation.yawl.resilience.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Platform-level Resilience4j configuration for YAWL.
 *
 * Provides centralized resilience patterns for all YAWL components:
 * - Circuit Breakers for external service calls
 * - Retry with exponential backoff and jitter
 * - Rate limiting for multi-agent fan-out scenarios
 * - Bulkheads for concurrent workflow isolation
 * - Time limiters for operation timeouts
 *
 * All patterns are instrumented with Micrometer metrics for observability.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ResilienceConfig {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceConfig.class);

    private final YawlResilienceProperties properties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    public ResilienceConfig(YawlResilienceProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;

        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        this.bulkheadRegistry = BulkheadRegistry.ofDefaults();
        this.timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();

        initializePatterns();
        registerMetrics(meterRegistry);
        registerEventListeners();

        logger.info("YAWL Resilience4j platform initialized with circuit breakers, retries, rate limiting, and bulkheads");
    }

    private void initializePatterns() {
        createEngineServiceCircuitBreaker();
        createExternalServiceCircuitBreaker();
        createMcpIntegrationCircuitBreaker();
        createA2aIntegrationCircuitBreaker();
        createDefaultRetry();
        createDefaultRateLimiter();
        createDefaultBulkhead();
        createDefaultTimeLimiter();
    }

    private void createEngineServiceCircuitBreaker() {
        var config = properties.getEngineService();
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(config.getFailureRateThreshold())
            .slowCallRateThreshold(config.getSlowCallRateThreshold())
            .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
            .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
            .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
            .waitDurationInOpenState(config.getWaitDurationInOpenState())
            .slidingWindowSize(config.getSlidingWindowSize())
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .automaticTransitionFromOpenToHalfOpenEnabled(config.isAutomaticTransitionFromOpenToHalfOpenEnabled())
            .recordExceptions(IOException.class, TimeoutException.class)
            .build();

        circuitBreakerRegistry.circuitBreaker("engineService", circuitBreakerConfig);
        logger.debug("Created engineService circuit breaker with {}% failure threshold",
            config.getFailureRateThreshold());
    }

    private void createExternalServiceCircuitBreaker() {
        var config = properties.getExternalService();
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(config.getFailureRateThreshold())
            .slowCallRateThreshold(config.getSlowCallRateThreshold())
            .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
            .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
            .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
            .waitDurationInOpenState(config.getWaitDurationInOpenState())
            .slidingWindowSize(config.getSlidingWindowSize())
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .automaticTransitionFromOpenToHalfOpenEnabled(config.isAutomaticTransitionFromOpenToHalfOpenEnabled())
            .recordExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
            .build();

        circuitBreakerRegistry.circuitBreaker("externalService", circuitBreakerConfig);
        logger.debug("Created externalService circuit breaker with {}% failure threshold",
            config.getFailureRateThreshold());
    }

    private void createMcpIntegrationCircuitBreaker() {
        var config = properties.getMcpIntegration();
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(config.getFailureRateThreshold())
            .slowCallRateThreshold(config.getSlowCallRateThreshold())
            .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
            .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
            .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
            .waitDurationInOpenState(config.getWaitDurationInOpenState())
            .slidingWindowSize(config.getSlidingWindowSize())
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .automaticTransitionFromOpenToHalfOpenEnabled(config.isAutomaticTransitionFromOpenToHalfOpenEnabled())
            .recordExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
            .build();

        circuitBreakerRegistry.circuitBreaker("mcpIntegration", circuitBreakerConfig);
        logger.debug("Created mcpIntegration circuit breaker with {}% failure threshold",
            config.getFailureRateThreshold());
    }

    private void createA2aIntegrationCircuitBreaker() {
        var config = properties.getA2aIntegration();
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(config.getFailureRateThreshold())
            .slowCallRateThreshold(config.getSlowCallRateThreshold())
            .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
            .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
            .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
            .waitDurationInOpenState(config.getWaitDurationInOpenState())
            .slidingWindowSize(config.getSlidingWindowSize())
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .automaticTransitionFromOpenToHalfOpenEnabled(config.isAutomaticTransitionFromOpenToHalfOpenEnabled())
            .recordExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
            .build();

        circuitBreakerRegistry.circuitBreaker("a2aIntegration", circuitBreakerConfig);
        logger.debug("Created a2aIntegration circuit breaker with {}% failure threshold",
            config.getFailureRateThreshold());
    }

    private void createDefaultRetry() {
        var config = properties.getRetry();
        var retryConfig = RetryConfig.custom()
            .maxAttempts(config.getMaxAttempts())
            .waitDuration(config.getWaitDuration())
            .intervalFunction(io.github.resilience4j.core.IntervalFunction
                .ofExponentialRandomBackoff(
                    config.getWaitDuration(),
                    config.getExponentialBackoffMultiplier(),
                    config.getRandomizationFactor()
                ))
            .retryExceptions(IOException.class, TimeoutException.class)
            .build();

        retryRegistry.retry("default", retryConfig);
        logger.debug("Created default retry with {} max attempts and exponential backoff",
            config.getMaxAttempts());
    }

    private void createDefaultRateLimiter() {
        var config = properties.getRateLimiter();
        var rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(config.getLimitForPeriod())
            .limitRefreshPeriod(config.getLimitRefreshPeriod())
            .timeoutDuration(config.getTimeoutDuration())
            .build();

        rateLimiterRegistry.rateLimiter("default", rateLimiterConfig);
        logger.debug("Created default rate limiter with {} calls per {}",
            config.getLimitForPeriod(), config.getLimitRefreshPeriod());
    }

    private void createDefaultBulkhead() {
        var config = properties.getBulkhead();
        var bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(config.getMaxConcurrentCalls())
            .maxWaitDuration(config.getMaxWaitDuration())
            .build();

        bulkheadRegistry.bulkhead("default", bulkheadConfig);
        logger.debug("Created default bulkhead with {} max concurrent calls",
            config.getMaxConcurrentCalls());
    }

    private void createDefaultTimeLimiter() {
        var config = properties.getTimeLimiter();
        var timeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(config.getTimeoutDuration())
            .cancelRunningFuture(config.isCancelRunningFuture())
            .build();

        timeLimiterRegistry.timeLimiter("default", timeLimiterConfig);
        logger.debug("Created default time limiter with {} timeout", config.getTimeoutDuration());
    }

    private void registerMetrics(MeterRegistry meterRegistry) {
        if (meterRegistry != null) {
            io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
                .ofCircuitBreakerRegistry(circuitBreakerRegistry)
                .bindTo(meterRegistry);

            io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics
                .ofRetryRegistry(retryRegistry)
                .bindTo(meterRegistry);

            io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics
                .ofRateLimiterRegistry(rateLimiterRegistry)
                .bindTo(meterRegistry);

            io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics
                .ofBulkheadRegistry(bulkheadRegistry)
                .bindTo(meterRegistry);

            io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics
                .ofTimeLimiterRegistry(timeLimiterRegistry)
                .bindTo(meterRegistry);

            logger.info("Registered Resilience4j metrics with Micrometer");
        }
    }

    private void registerEventListeners() {
        circuitBreakerRegistry.circuitBreaker("engineService")
            .getEventPublisher()
            .onStateTransition(event ->
                logger.warn("Engine service circuit breaker state transition: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));

        circuitBreakerRegistry.circuitBreaker("externalService")
            .getEventPublisher()
            .onStateTransition(event ->
                logger.warn("External service circuit breaker state transition: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));

        circuitBreakerRegistry.circuitBreaker("mcpIntegration")
            .getEventPublisher()
            .onStateTransition(event ->
                logger.warn("MCP integration circuit breaker state transition: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));

        circuitBreakerRegistry.circuitBreaker("a2aIntegration")
            .getEventPublisher()
            .onStateTransition(event ->
                logger.warn("A2A integration circuit breaker state transition: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));

        logger.debug("Registered event listeners for circuit breaker state transitions");
    }

    public CircuitBreaker getEngineServiceCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("engineService");
    }

    public CircuitBreaker getExternalServiceCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("externalService");
    }

    public CircuitBreaker getMcpIntegrationCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("mcpIntegration");
    }

    public CircuitBreaker getA2aIntegrationCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("a2aIntegration");
    }

    public Retry getDefaultRetry() {
        return retryRegistry.retry("default");
    }

    public RateLimiter getDefaultRateLimiter() {
        return rateLimiterRegistry.rateLimiter("default");
    }

    public Bulkhead getDefaultBulkhead() {
        return bulkheadRegistry.bulkhead("default");
    }

    public TimeLimiter getDefaultTimeLimiter() {
        return timeLimiterRegistry.timeLimiter("default");
    }

    public CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }

    public RetryRegistry getRetryRegistry() {
        return retryRegistry;
    }

    public RateLimiterRegistry getRateLimiterRegistry() {
        return rateLimiterRegistry;
    }

    public BulkheadRegistry getBulkheadRegistry() {
        return bulkheadRegistry;
    }

    public TimeLimiterRegistry getTimeLimiterRegistry() {
        return timeLimiterRegistry;
    }
}
