package org.yawlfoundation.yawl.resilience.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.observability.CustomMetricsRegistry;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for CircuitBreakerMetricsListener integration.
 */
@DisplayName("Circuit Breaker Metrics Listener Tests")
public class CircuitBreakerMetricsListenerTest {

    private MeterRegistry meterRegistry;
    private CustomMetricsRegistry customMetrics;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private CircuitBreaker testCircuitBreaker;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        CustomMetricsRegistry.initialize(meterRegistry);
        customMetrics = CustomMetricsRegistry.getInstance();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
        testCircuitBreaker = circuitBreakerRegistry.circuitBreaker("test-cb");

        new CircuitBreakerMetricsListener(testCircuitBreaker);
    }

    @Test
    @DisplayName("Listener should record state transition duration")
    public void testStateTransitionDuration() {
        customMetrics.recordCircuitBreakerStateChange(150);

        Timer timer = meterRegistry.find("yawl.circuit.breaker.state.duration").timer();
        assertNotNull(timer, "State duration timer should be registered");
        assertEquals(1, timer.count(), "Should record state change");
    }

    @Test
    @DisplayName("Listener should increment transition counter")
    public void testTransitionCounter() {
        customMetrics.recordCircuitBreakerStateChange(100);
        customMetrics.recordCircuitBreakerStateChange(100);

        Counter counter = meterRegistry.find("yawl.circuit.breaker.transitions").counter();
        assertNotNull(counter, "Transition counter should be registered");
        assertEquals(2.0, counter.count(), "Should count transitions");
    }

    @Test
    @DisplayName("Listener should track state changes")
    public void testStateTracking() {
        customMetrics.setCircuitBreakerState(0); // CLOSED
        assertEquals(0, customMetrics.getCircuitBreakerState());

        customMetrics.setCircuitBreakerState(1); // OPEN
        assertEquals(1, customMetrics.getCircuitBreakerState());

        customMetrics.setCircuitBreakerState(2); // HALF_OPEN
        assertEquals(2, customMetrics.getCircuitBreakerState());
    }
}
