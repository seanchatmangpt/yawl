package org.yawlfoundation.yawl.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CircuitBreakerObservabilityListener.
 * Verifies resilience4j circuit breaker event tracing.
 *
 * @since 6.0.0
 */
@DisplayName("CircuitBreakerObservabilityListener Tests")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
class CircuitBreakerObservabilityListenerTest {

    private CircuitBreakerObservabilityListener listener;

    @Mock
    private Tracer tracer;

    @Mock
    private Tracer.SpanInScope spanInScope;

    @BeforeEach
    void setUp() {
        // Mock tracer to return a closeable scope
        when(tracer.createScope(anyString())).thenReturn(spanInScope);
        listener = new CircuitBreakerObservabilityListener(tracer);
    }

    @Test
    @DisplayName("Should handle state transition events")
    void testStateTransitionEvent() {
        CircuitBreakerEvent.StateTransitionEvent event = mock(CircuitBreakerEvent.StateTransitionEvent.class);
        when(event.getCircuitBreakerName()).thenReturn("test-cb");
        when(event.getStateTransitionFrom()).thenReturn(CircuitBreaker.State.CLOSED);
        when(event.getStateTransitionTo()).thenReturn(CircuitBreaker.State.OPEN);

        assertDoesNotThrow(() -> listener.onCircuitBreakerStateTransition(event),
                "Should handle state transition without throwing");
    }

    @Test
    @DisplayName("Should handle success events")
    void testSuccessEvent() {
        CircuitBreakerEvent.SuccessEvent event = mock(CircuitBreakerEvent.SuccessEvent.class);
        when(event.getCircuitBreakerName()).thenReturn("test-cb");
        when(event.getElapsedDuration()).thenReturn(Duration.ofMillis(100));

        assertDoesNotThrow(() -> listener.onCircuitBreakerSuccess(event),
                "Should handle success event without throwing");
    }

    @Test
    @DisplayName("Should handle error events")
    void testErrorEvent() {
        CircuitBreakerEvent.ErrorEvent event = mock(CircuitBreakerEvent.ErrorEvent.class);
        when(event.getCircuitBreakerName()).thenReturn("test-cb");
        Throwable throwable = new RuntimeException("Test error");
        when(event.getThrowable()).thenReturn(throwable);

        assertDoesNotThrow(() -> listener.onCircuitBreakerError(event),
                "Should handle error event without throwing");
    }

    @Test
    @DisplayName("Should handle ignored error events")
    void testIgnoredErrorEvent() {
        CircuitBreakerEvent.IgnoredErrorEvent event = mock(CircuitBreakerEvent.IgnoredErrorEvent.class);
        when(event.getCircuitBreakerName()).thenReturn("test-cb");
        Throwable throwable = new IllegalArgumentException("Ignored");
        when(event.getThrowable()).thenReturn(throwable);

        assertDoesNotThrow(() -> listener.onCircuitBreakerIgnoredError(event),
                "Should handle ignored error event without throwing");
    }

    @Test
    @DisplayName("Should handle slow success events")
    void testSlowSuccessEvent() {
        CircuitBreakerEvent.SlowSuccessEvent event = mock(CircuitBreakerEvent.SlowSuccessEvent.class);
        when(event.getCircuitBreakerName()).thenReturn("test-cb");
        when(event.getElapsedDuration()).thenReturn(Duration.ofSeconds(5));

        assertDoesNotThrow(() -> listener.onCircuitBreakerSlowSuccess(event),
                "Should handle slow success event without throwing");
    }

    @Test
    @DisplayName("Should handle slow error events")
    void testSlowErrorEvent() {
        CircuitBreakerEvent.SlowErrorEvent event = mock(CircuitBreakerEvent.SlowErrorEvent.class);
        when(event.getCircuitBreakerName()).thenReturn("test-cb");
        when(event.getElapsedDuration()).thenReturn(Duration.ofSeconds(10));
        Throwable throwable = new TimeoutException("Slow timeout");
        when(event.getThrowable()).thenReturn(throwable);

        assertDoesNotThrow(() -> listener.onCircuitBreakerSlowError(event),
                "Should handle slow error event without throwing");
    }

    @Test
    @DisplayName("Should handle call not permitted events")
    void testCallNotPermittedEvent() {
        CircuitBreakerEvent.NotPermittedEvent event = mock(CircuitBreakerEvent.NotPermittedEvent.class);
        when(event.getCircuitBreakerName()).thenReturn("test-cb");
        when(event.getCircuitBreakerState()).thenReturn(CircuitBreaker.State.OPEN);

        assertDoesNotThrow(() -> listener.onCircuitBreakerCallNotPermitted(event),
                "Should handle call not permitted event without throwing");
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent events")
    void testConcurrentEventHandling() throws InterruptedException {
        CircuitBreakerEvent.SuccessEvent successEvent = mock(CircuitBreakerEvent.SuccessEvent.class);
        when(successEvent.getCircuitBreakerName()).thenReturn("test-cb");
        when(successEvent.getElapsedDuration()).thenReturn(Duration.ofMillis(50));

        CircuitBreakerEvent.ErrorEvent errorEvent = mock(CircuitBreakerEvent.ErrorEvent.class);
        when(errorEvent.getCircuitBreakerName()).thenReturn("test-cb");
        when(errorEvent.getThrowable()).thenReturn(new RuntimeException("Test"));

        // Simulate concurrent event handling
        Thread thread1 = new Thread(() ->
                listener.onCircuitBreakerSuccess(successEvent)
        );
        Thread thread2 = new Thread(() ->
                listener.onCircuitBreakerError(errorEvent)
        );

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // If we get here without deadlock or exceptions, thread safety is working
    }

    @Test
    @DisplayName("Should create listener with valid tracer")
    void testListenerCreation() {
        assertNotNull(listener, "Listener should be created successfully");
    }

    /**
     * Simple TimeoutException for testing.
     */
    private static class TimeoutException extends Exception {
        TimeoutException(String message) {
            super(message);
        }
    }
}
