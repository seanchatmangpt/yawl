package org.yawlfoundation.yawl.resilience.health;

import java.util.HashMap;
import java.util.Map;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Health indicator for Resilience4j circuit breakers.
 *
 * Provides health status and metrics for all configured circuit breakers.
 * Integrates with Spring Boot Actuator health endpoint.
 *
 * Health Status Logic:
 * - UP: All circuit breakers are CLOSED or HALF_OPEN
 * - DOWN: Any circuit breaker is OPEN
 * - UNKNOWN: Circuit breaker state cannot be determined
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CircuitBreakerHealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerHealthIndicator(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Get health status for all circuit breakers.
     *
     * @return health status object
     */
    public HealthStatus health() {
        Map<String, CircuitBreakerStatus> circuitBreakers = new HashMap<>();
        boolean hasOpenCircuit = false;

        for (CircuitBreaker circuitBreaker : circuitBreakerRegistry.allCircuitBreakers()) {
            String name = circuitBreaker.getName();
            CircuitBreaker.State state = circuitBreaker.getState();
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

            CircuitBreakerStatus status = new CircuitBreakerStatus();
            status.setState(state.toString());
            status.setFailureRate(metrics.getFailureRate());
            status.setSlowCallRate(metrics.getSlowCallRate());
            status.setNumberOfBufferedCalls(metrics.getNumberOfBufferedCalls());
            status.setNumberOfFailedCalls(metrics.getNumberOfFailedCalls());
            status.setNumberOfSuccessfulCalls(metrics.getNumberOfSuccessfulCalls());
            status.setNumberOfSlowCalls(metrics.getNumberOfSlowCalls());

            circuitBreakers.put(name, status);

            if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                hasOpenCircuit = true;
            }
        }

        HealthStatus healthStatus = new HealthStatus();
        healthStatus.setStatus(hasOpenCircuit ? "DOWN" : "UP");
        healthStatus.setCircuitBreakers(circuitBreakers);

        return healthStatus;
    }

    /**
     * Get health status for a specific circuit breaker.
     *
     * @param name the circuit breaker name
     * @return health status object
     */
    public HealthStatus health(String name) {
        Map<String, CircuitBreakerStatus> circuitBreakers = new HashMap<>();

        if (!circuitBreakerRegistry.find(name).isPresent()) {
            HealthStatus healthStatus = new HealthStatus();
            healthStatus.setStatus("UNKNOWN");
            healthStatus.setMessage("Circuit breaker not found: " + name);
            return healthStatus;
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        CircuitBreaker.State state = circuitBreaker.getState();
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

        CircuitBreakerStatus status = new CircuitBreakerStatus();
        status.setState(state.toString());
        status.setFailureRate(metrics.getFailureRate());
        status.setSlowCallRate(metrics.getSlowCallRate());
        status.setNumberOfBufferedCalls(metrics.getNumberOfBufferedCalls());
        status.setNumberOfFailedCalls(metrics.getNumberOfFailedCalls());
        status.setNumberOfSuccessfulCalls(metrics.getNumberOfSuccessfulCalls());
        status.setNumberOfSlowCalls(metrics.getNumberOfSlowCalls());

        circuitBreakers.put(name, status);

        HealthStatus healthStatus = new HealthStatus();
        healthStatus.setStatus(
            (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN)
                ? "DOWN" : "UP"
        );
        healthStatus.setCircuitBreakers(circuitBreakers);

        return healthStatus;
    }

    public static class HealthStatus {
        private String status;
        private String message;
        private Map<String, CircuitBreakerStatus> circuitBreakers;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, CircuitBreakerStatus> getCircuitBreakers() {
            return circuitBreakers;
        }

        public void setCircuitBreakers(Map<String, CircuitBreakerStatus> circuitBreakers) {
            this.circuitBreakers = circuitBreakers;
        }
    }

    public static class CircuitBreakerStatus {
        private String state;
        private float failureRate;
        private float slowCallRate;
        private int numberOfBufferedCalls;
        private int numberOfFailedCalls;
        private int numberOfSuccessfulCalls;
        private long numberOfSlowCalls;

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public float getFailureRate() {
            return failureRate;
        }

        public void setFailureRate(float failureRate) {
            this.failureRate = failureRate;
        }

        public float getSlowCallRate() {
            return slowCallRate;
        }

        public void setSlowCallRate(float slowCallRate) {
            this.slowCallRate = slowCallRate;
        }

        public int getNumberOfBufferedCalls() {
            return numberOfBufferedCalls;
        }

        public void setNumberOfBufferedCalls(int numberOfBufferedCalls) {
            this.numberOfBufferedCalls = numberOfBufferedCalls;
        }

        public int getNumberOfFailedCalls() {
            return numberOfFailedCalls;
        }

        public void setNumberOfFailedCalls(int numberOfFailedCalls) {
            this.numberOfFailedCalls = numberOfFailedCalls;
        }

        public int getNumberOfSuccessfulCalls() {
            return numberOfSuccessfulCalls;
        }

        public void setNumberOfSuccessfulCalls(int numberOfSuccessfulCalls) {
            this.numberOfSuccessfulCalls = numberOfSuccessfulCalls;
        }

        public long getNumberOfSlowCalls() {
            return numberOfSlowCalls;
        }

        public void setNumberOfSlowCalls(long numberOfSlowCalls) {
            this.numberOfSlowCalls = numberOfSlowCalls;
        }
    }
}
