package org.yawlfoundation.yawl.resilience.config;

import java.time.Duration;

/**
 * Platform-level resilience configuration for YAWL.
 *
 * Provides sensible defaults for circuit breakers, retries, rate limiting,
 * and bulkheads across all YAWL components. Values can be overridden via
 * environment variables or configuration files.
 *
 * Design Philosophy:
 * - Fail fast for user-facing operations
 * - Retry with backoff for remote service calls
 * - Prevent cascade failures with circuit breakers
 * - Limit blast radius with bulkheads
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlResilienceProperties {

    /**
     * Circuit Breaker Configuration
     */
    public static class CircuitBreakerConfig {
        private int failureRateThreshold = 50;
        private int slowCallRateThreshold = 50;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(3);
        private int minimumNumberOfCalls = 10;
        private int permittedNumberOfCallsInHalfOpenState = 5;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int slidingWindowSize = 100;
        private String slidingWindowType = "COUNT_BASED";
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
        private boolean recordExceptions = true;

        public int getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(int failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(int slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public String getSlidingWindowType() {
            return slidingWindowType;
        }

        public void setSlidingWindowType(String slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
        }

        public boolean isAutomaticTransitionFromOpenToHalfOpenEnabled() {
            return automaticTransitionFromOpenToHalfOpenEnabled;
        }

        public void setAutomaticTransitionFromOpenToHalfOpenEnabled(boolean automaticTransitionFromOpenToHalfOpenEnabled) {
            this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
        }

        public boolean isRecordExceptions() {
            return recordExceptions;
        }

        public void setRecordExceptions(boolean recordExceptions) {
            this.recordExceptions = recordExceptions;
        }
    }

    /**
     * Retry Configuration with Exponential Backoff and Jitter
     */
    public static class RetryConfig {
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofMillis(500);
        private double exponentialBackoffMultiplier = 2.0;
        private Duration exponentialMaxWaitDuration = Duration.ofSeconds(10);
        private boolean enableRandomizedWait = true;
        private double randomizationFactor = 0.5;
        private boolean retryOnResult = false;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }

        public double getExponentialBackoffMultiplier() {
            return exponentialBackoffMultiplier;
        }

        public void setExponentialBackoffMultiplier(double exponentialBackoffMultiplier) {
            this.exponentialBackoffMultiplier = exponentialBackoffMultiplier;
        }

        public Duration getExponentialMaxWaitDuration() {
            return exponentialMaxWaitDuration;
        }

        public void setExponentialMaxWaitDuration(Duration exponentialMaxWaitDuration) {
            this.exponentialMaxWaitDuration = exponentialMaxWaitDuration;
        }

        public boolean isEnableRandomizedWait() {
            return enableRandomizedWait;
        }

        public void setEnableRandomizedWait(boolean enableRandomizedWait) {
            this.enableRandomizedWait = enableRandomizedWait;
        }

        public double getRandomizationFactor() {
            return randomizationFactor;
        }

        public void setRandomizationFactor(double randomizationFactor) {
            this.randomizationFactor = randomizationFactor;
        }

        public boolean isRetryOnResult() {
            return retryOnResult;
        }

        public void setRetryOnResult(boolean retryOnResult) {
            this.retryOnResult = retryOnResult;
        }
    }

    /**
     * Rate Limiter Configuration
     */
    public static class RateLimiterConfig {
        private int limitForPeriod = 100;
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);
        private Duration timeoutDuration = Duration.ofMillis(500);
        private boolean writableStackTraceEnabled = true;

        public int getLimitForPeriod() {
            return limitForPeriod;
        }

        public void setLimitForPeriod(int limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        public Duration getLimitRefreshPeriod() {
            return limitRefreshPeriod;
        }

        public void setLimitRefreshPeriod(Duration limitRefreshPeriod) {
            this.limitRefreshPeriod = limitRefreshPeriod;
        }

        public Duration getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(Duration timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }

        public boolean isWritableStackTraceEnabled() {
            return writableStackTraceEnabled;
        }

        public void setWritableStackTraceEnabled(boolean writableStackTraceEnabled) {
            this.writableStackTraceEnabled = writableStackTraceEnabled;
        }
    }

    /**
     * Bulkhead Configuration for Concurrent Workflow Isolation
     */
    public static class BulkheadConfig {
        private int maxConcurrentCalls = 25;
        private Duration maxWaitDuration = Duration.ofMillis(500);
        private boolean writableStackTraceEnabled = true;

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public Duration getMaxWaitDuration() {
            return maxWaitDuration;
        }

        public void setMaxWaitDuration(Duration maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }

        public boolean isWritableStackTraceEnabled() {
            return writableStackTraceEnabled;
        }

        public void setWritableStackTraceEnabled(boolean writableStackTraceEnabled) {
            this.writableStackTraceEnabled = writableStackTraceEnabled;
        }
    }

    /**
     * Time Limiter Configuration
     */
    public static class TimeLimiterConfig {
        private Duration timeoutDuration = Duration.ofSeconds(5);
        private boolean cancelRunningFuture = true;

        public Duration getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(Duration timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }

        public boolean isCancelRunningFuture() {
            return cancelRunningFuture;
        }

        public void setCancelRunningFuture(boolean cancelRunningFuture) {
            this.cancelRunningFuture = cancelRunningFuture;
        }
    }

    private CircuitBreakerConfig engineService = new CircuitBreakerConfig();
    private CircuitBreakerConfig externalService = new CircuitBreakerConfig();
    private CircuitBreakerConfig mcpIntegration = new CircuitBreakerConfig();
    private CircuitBreakerConfig a2aIntegration = new CircuitBreakerConfig();

    private RetryConfig retry = new RetryConfig();
    private RateLimiterConfig rateLimiter = new RateLimiterConfig();
    private BulkheadConfig bulkhead = new BulkheadConfig();
    private TimeLimiterConfig timeLimiter = new TimeLimiterConfig();

    public YawlResilienceProperties() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        engineService.setFailureRateThreshold(60);
        engineService.setSlowCallDurationThreshold(Duration.ofSeconds(2));
        engineService.setWaitDurationInOpenState(Duration.ofSeconds(20));

        externalService.setFailureRateThreshold(50);
        externalService.setSlowCallDurationThreshold(Duration.ofSeconds(5));
        externalService.setWaitDurationInOpenState(Duration.ofSeconds(60));

        mcpIntegration.setFailureRateThreshold(40);
        mcpIntegration.setSlowCallDurationThreshold(Duration.ofSeconds(10));
        mcpIntegration.setWaitDurationInOpenState(Duration.ofSeconds(30));

        a2aIntegration.setFailureRateThreshold(40);
        a2aIntegration.setSlowCallDurationThreshold(Duration.ofSeconds(10));
        a2aIntegration.setWaitDurationInOpenState(Duration.ofSeconds(30));
    }

    public CircuitBreakerConfig getEngineService() {
        return engineService;
    }

    public void setEngineService(CircuitBreakerConfig engineService) {
        this.engineService = engineService;
    }

    public CircuitBreakerConfig getExternalService() {
        return externalService;
    }

    public void setExternalService(CircuitBreakerConfig externalService) {
        this.externalService = externalService;
    }

    public CircuitBreakerConfig getMcpIntegration() {
        return mcpIntegration;
    }

    public void setMcpIntegration(CircuitBreakerConfig mcpIntegration) {
        this.mcpIntegration = mcpIntegration;
    }

    public CircuitBreakerConfig getA2aIntegration() {
        return a2aIntegration;
    }

    public void setA2aIntegration(CircuitBreakerConfig a2aIntegration) {
        this.a2aIntegration = a2aIntegration;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }

    public RateLimiterConfig getRateLimiter() {
        return rateLimiter;
    }

    public void setRateLimiter(RateLimiterConfig rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public BulkheadConfig getBulkhead() {
        return bulkhead;
    }

    public void setBulkhead(BulkheadConfig bulkhead) {
        this.bulkhead = bulkhead;
    }

    public TimeLimiterConfig getTimeLimiter() {
        return timeLimiter;
    }

    public void setTimeLimiter(TimeLimiterConfig timeLimiter) {
        this.timeLimiter = timeLimiter;
    }
}
