package org.yawlfoundation.yawl.resilience.metrics;

import java.util.HashMap;
import java.util.Map;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Metrics collector for Resilience4j patterns.
 *
 * Provides a unified view of all resilience pattern metrics for monitoring
 * and alerting. Metrics are exposed via Micrometer and can be scraped by
 * Prometheus or other monitoring systems.
 *
 * Collected Metrics:
 * - Circuit Breaker: state, failure rate, slow call rate, call counts
 * - Retry: success/failure counts, retry attempts
 * - Rate Limiter: available permissions, waiting threads
 * - Bulkhead: available concurrent calls, queue depth
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ResilienceMetricsCollector {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public ResilienceMetricsCollector(
        CircuitBreakerRegistry circuitBreakerRegistry,
        RetryRegistry retryRegistry,
        RateLimiterRegistry rateLimiterRegistry,
        BulkheadRegistry bulkheadRegistry) {

        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    /**
     * Collect all circuit breaker metrics.
     *
     * @return map of circuit breaker name to metrics
     */
    public Map<String, CircuitBreakerMetrics> collectCircuitBreakerMetrics() {
        Map<String, CircuitBreakerMetrics> metrics = new HashMap<>();

        for (CircuitBreaker circuitBreaker : circuitBreakerRegistry.allCircuitBreakers()) {
            String name = circuitBreaker.getName();
            CircuitBreaker.Metrics cbMetrics = circuitBreaker.getMetrics();

            CircuitBreakerMetrics metric = new CircuitBreakerMetrics();
            metric.setName(name);
            metric.setState(circuitBreaker.getState().toString());
            metric.setFailureRate(cbMetrics.getFailureRate());
            metric.setSlowCallRate(cbMetrics.getSlowCallRate());
            metric.setNumberOfBufferedCalls(cbMetrics.getNumberOfBufferedCalls());
            metric.setNumberOfFailedCalls(cbMetrics.getNumberOfFailedCalls());
            metric.setNumberOfSuccessfulCalls(cbMetrics.getNumberOfSuccessfulCalls());
            metric.setNumberOfSlowCalls(cbMetrics.getNumberOfSlowCalls());
            metric.setNumberOfSlowSuccessfulCalls(cbMetrics.getNumberOfSlowSuccessfulCalls());
            metric.setNumberOfSlowFailedCalls(cbMetrics.getNumberOfSlowFailedCalls());
            metric.setNumberOfNotPermittedCalls(cbMetrics.getNumberOfNotPermittedCalls());

            metrics.put(name, metric);
        }

        return metrics;
    }

    /**
     * Collect all retry metrics.
     *
     * @return map of retry name to metrics
     */
    public Map<String, RetryMetrics> collectRetryMetrics() {
        Map<String, RetryMetrics> metrics = new HashMap<>();

        for (Retry retry : retryRegistry.allRetries()) {
            String name = retry.getName();
            Retry.Metrics retryMetrics = retry.getMetrics();

            RetryMetrics metric = new RetryMetrics();
            metric.setName(name);
            metric.setNumberOfSuccessfulCallsWithoutRetryAttempt(
                retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt());
            metric.setNumberOfSuccessfulCallsWithRetryAttempt(
                retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt());
            metric.setNumberOfFailedCallsWithoutRetryAttempt(
                retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt());
            metric.setNumberOfFailedCallsWithRetryAttempt(
                retryMetrics.getNumberOfFailedCallsWithRetryAttempt());

            metrics.put(name, metric);
        }

        return metrics;
    }

    /**
     * Collect all rate limiter metrics.
     *
     * @return map of rate limiter name to metrics
     */
    public Map<String, RateLimiterMetrics> collectRateLimiterMetrics() {
        Map<String, RateLimiterMetrics> metrics = new HashMap<>();

        for (RateLimiter rateLimiter : rateLimiterRegistry.allRateLimiters()) {
            String name = rateLimiter.getName();
            RateLimiter.Metrics rlMetrics = rateLimiter.getMetrics();

            RateLimiterMetrics metric = new RateLimiterMetrics();
            metric.setName(name);
            metric.setAvailablePermissions(rlMetrics.getAvailablePermissions());
            metric.setNumberOfWaitingThreads(rlMetrics.getNumberOfWaitingThreads());

            metrics.put(name, metric);
        }

        return metrics;
    }

    /**
     * Collect all bulkhead metrics.
     *
     * @return map of bulkhead name to metrics
     */
    public Map<String, BulkheadMetrics> collectBulkheadMetrics() {
        Map<String, BulkheadMetrics> metrics = new HashMap<>();

        for (Bulkhead bulkhead : bulkheadRegistry.allBulkheads()) {
            String name = bulkhead.getName();
            Bulkhead.Metrics bhMetrics = bulkhead.getMetrics();

            BulkheadMetrics metric = new BulkheadMetrics();
            metric.setName(name);
            metric.setAvailableConcurrentCalls(bhMetrics.getAvailableConcurrentCalls());
            metric.setMaxAllowedConcurrentCalls(bhMetrics.getMaxAllowedConcurrentCalls());

            metrics.put(name, metric);
        }

        return metrics;
    }

    /**
     * Collect all resilience metrics in a single call.
     *
     * @return comprehensive metrics snapshot
     */
    public ResilienceMetricsSnapshot collectAll() {
        ResilienceMetricsSnapshot snapshot = new ResilienceMetricsSnapshot();
        snapshot.setCircuitBreakers(collectCircuitBreakerMetrics());
        snapshot.setRetries(collectRetryMetrics());
        snapshot.setRateLimiters(collectRateLimiterMetrics());
        snapshot.setBulkheads(collectBulkheadMetrics());
        snapshot.setTimestamp(System.currentTimeMillis());
        return snapshot;
    }

    public static class CircuitBreakerMetrics {
        private String name;
        private String state;
        private float failureRate;
        private float slowCallRate;
        private int numberOfBufferedCalls;
        private int numberOfFailedCalls;
        private int numberOfSuccessfulCalls;
        private long numberOfSlowCalls;
        private long numberOfSlowSuccessfulCalls;
        private long numberOfSlowFailedCalls;
        private long numberOfNotPermittedCalls;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public float getFailureRate() { return failureRate; }
        public void setFailureRate(float failureRate) { this.failureRate = failureRate; }
        public float getSlowCallRate() { return slowCallRate; }
        public void setSlowCallRate(float slowCallRate) { this.slowCallRate = slowCallRate; }
        public int getNumberOfBufferedCalls() { return numberOfBufferedCalls; }
        public void setNumberOfBufferedCalls(int numberOfBufferedCalls) { this.numberOfBufferedCalls = numberOfBufferedCalls; }
        public int getNumberOfFailedCalls() { return numberOfFailedCalls; }
        public void setNumberOfFailedCalls(int numberOfFailedCalls) { this.numberOfFailedCalls = numberOfFailedCalls; }
        public int getNumberOfSuccessfulCalls() { return numberOfSuccessfulCalls; }
        public void setNumberOfSuccessfulCalls(int numberOfSuccessfulCalls) { this.numberOfSuccessfulCalls = numberOfSuccessfulCalls; }
        public long getNumberOfSlowCalls() { return numberOfSlowCalls; }
        public void setNumberOfSlowCalls(long numberOfSlowCalls) { this.numberOfSlowCalls = numberOfSlowCalls; }
        public long getNumberOfSlowSuccessfulCalls() { return numberOfSlowSuccessfulCalls; }
        public void setNumberOfSlowSuccessfulCalls(long numberOfSlowSuccessfulCalls) { this.numberOfSlowSuccessfulCalls = numberOfSlowSuccessfulCalls; }
        public long getNumberOfSlowFailedCalls() { return numberOfSlowFailedCalls; }
        public void setNumberOfSlowFailedCalls(long numberOfSlowFailedCalls) { this.numberOfSlowFailedCalls = numberOfSlowFailedCalls; }
        public long getNumberOfNotPermittedCalls() { return numberOfNotPermittedCalls; }
        public void setNumberOfNotPermittedCalls(long numberOfNotPermittedCalls) { this.numberOfNotPermittedCalls = numberOfNotPermittedCalls; }
    }

    public static class RetryMetrics {
        private String name;
        private long numberOfSuccessfulCallsWithoutRetryAttempt;
        private long numberOfSuccessfulCallsWithRetryAttempt;
        private long numberOfFailedCallsWithoutRetryAttempt;
        private long numberOfFailedCallsWithRetryAttempt;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getNumberOfSuccessfulCallsWithoutRetryAttempt() { return numberOfSuccessfulCallsWithoutRetryAttempt; }
        public void setNumberOfSuccessfulCallsWithoutRetryAttempt(long numberOfSuccessfulCallsWithoutRetryAttempt) { this.numberOfSuccessfulCallsWithoutRetryAttempt = numberOfSuccessfulCallsWithoutRetryAttempt; }
        public long getNumberOfSuccessfulCallsWithRetryAttempt() { return numberOfSuccessfulCallsWithRetryAttempt; }
        public void setNumberOfSuccessfulCallsWithRetryAttempt(long numberOfSuccessfulCallsWithRetryAttempt) { this.numberOfSuccessfulCallsWithRetryAttempt = numberOfSuccessfulCallsWithRetryAttempt; }
        public long getNumberOfFailedCallsWithoutRetryAttempt() { return numberOfFailedCallsWithoutRetryAttempt; }
        public void setNumberOfFailedCallsWithoutRetryAttempt(long numberOfFailedCallsWithoutRetryAttempt) { this.numberOfFailedCallsWithoutRetryAttempt = numberOfFailedCallsWithoutRetryAttempt; }
        public long getNumberOfFailedCallsWithRetryAttempt() { return numberOfFailedCallsWithRetryAttempt; }
        public void setNumberOfFailedCallsWithRetryAttempt(long numberOfFailedCallsWithRetryAttempt) { this.numberOfFailedCallsWithRetryAttempt = numberOfFailedCallsWithRetryAttempt; }
    }

    public static class RateLimiterMetrics {
        private String name;
        private int availablePermissions;
        private int numberOfWaitingThreads;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAvailablePermissions() { return availablePermissions; }
        public void setAvailablePermissions(int availablePermissions) { this.availablePermissions = availablePermissions; }
        public int getNumberOfWaitingThreads() { return numberOfWaitingThreads; }
        public void setNumberOfWaitingThreads(int numberOfWaitingThreads) { this.numberOfWaitingThreads = numberOfWaitingThreads; }
    }

    public static class BulkheadMetrics {
        private String name;
        private int availableConcurrentCalls;
        private int maxAllowedConcurrentCalls;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAvailableConcurrentCalls() { return availableConcurrentCalls; }
        public void setAvailableConcurrentCalls(int availableConcurrentCalls) { this.availableConcurrentCalls = availableConcurrentCalls; }
        public int getMaxAllowedConcurrentCalls() { return maxAllowedConcurrentCalls; }
        public void setMaxAllowedConcurrentCalls(int maxAllowedConcurrentCalls) { this.maxAllowedConcurrentCalls = maxAllowedConcurrentCalls; }
    }

    public static class ResilienceMetricsSnapshot {
        private long timestamp;
        private Map<String, CircuitBreakerMetrics> circuitBreakers;
        private Map<String, RetryMetrics> retries;
        private Map<String, RateLimiterMetrics> rateLimiters;
        private Map<String, BulkheadMetrics> bulkheads;

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public Map<String, CircuitBreakerMetrics> getCircuitBreakers() { return circuitBreakers; }
        public void setCircuitBreakers(Map<String, CircuitBreakerMetrics> circuitBreakers) { this.circuitBreakers = circuitBreakers; }
        public Map<String, RetryMetrics> getRetries() { return retries; }
        public void setRetries(Map<String, RetryMetrics> retries) { this.retries = retries; }
        public Map<String, RateLimiterMetrics> getRateLimiters() { return rateLimiters; }
        public void setRateLimiters(Map<String, RateLimiterMetrics> rateLimiters) { this.rateLimiters = rateLimiters; }
        public Map<String, BulkheadMetrics> getBulkheads() { return bulkheads; }
        public void setBulkheads(Map<String, BulkheadMetrics> bulkheads) { this.bulkheads = bulkheads; }
    }
}
