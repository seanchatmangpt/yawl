package org.yawlfoundation.yawl.resilience.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.yawlfoundation.yawl.observability.CustomMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metrics listener for Resilience4j CircuitBreaker state transitions.
 *
 * Records:
 * - State transition duration (histogram with 10ms, 100ms, 1s, 10s, 60s buckets)
 * - Current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
 * - Total state transitions (counter)
 *
 * Integrates with CustomMetricsRegistry to populate advanced Prometheus metrics.
 */
public final class CircuitBreakerMetricsListener implements
        RegistryEventConsumer<CircuitBreaker>,
        CircuitBreaker.EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerMetricsListener.class);

    private final CircuitBreaker circuitBreaker;
    private volatile long lastStateChangeTime = System.currentTimeMillis();

    /**
     * Creates a new listener for the given CircuitBreaker instance.
     */
    public CircuitBreakerMetricsListener(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        this.lastStateChangeTime = System.currentTimeMillis();

        // Register this listener with the circuit breaker's event publisher
        circuitBreaker.getEventPublisher()
                .onStateTransition(this::onStateTransition)
                .onCircuitOpened(this::onCircuitOpened)
                .onCircuitClosed(this::onCircuitClosed)
                .onCircuitHalfOpen(this::onCircuitHalfOpen);

        LOGGER.info("CircuitBreakerMetricsListener registered for: {}", circuitBreaker.getName());
    }

    /**
     * Called when a CircuitBreaker is added to the registry.
     */
    @Override
    public void onEntryAdded(EntryAddedEvent<CircuitBreaker> event) {
        LOGGER.debug("CircuitBreaker added to registry: {}", event.getAddedEntry().getName());
    }

    /**
     * Called when a CircuitBreaker is removed from the registry.
     */
    @Override
    public void onEntryRemoved(EntryRemovedEvent<CircuitBreaker> event) {
        LOGGER.debug("CircuitBreaker removed from registry: {}", event.getRemovedEntry().getName());
    }

    /**
     * Called when a CircuitBreaker is replaced in the registry.
     */
    @Override
    public void onEntryReplaced(EntryReplacedEvent<CircuitBreaker> event) {
        LOGGER.debug("CircuitBreaker replaced in registry: {}", event.getNewEntry().getName());
    }

    /**
     * Records state transition with duration measurement.
     */
    private void onStateTransition(CircuitBreaker.StateTransitionEvent event) {
        long currentTime = System.currentTimeMillis();
        long durationMs = currentTime - lastStateChangeTime;
        lastStateChangeTime = currentTime;

        String fromState = event.getStateTransition().getFromState().toString();
        String toState = event.getStateTransition().getToState().toString();

        LOGGER.info("CircuitBreaker '{}' state transition: {} -> {} (duration: {}ms)",
                circuitBreaker.getName(), fromState, toState, durationMs);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.recordCircuitBreakerStateChange(durationMs);
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping metrics recording", e);
        }
    }

    /**
     * Records circuit open event.
     */
    private void onCircuitOpened(CircuitBreaker.CircuitOpened event) {
        LOGGER.warn("CircuitBreaker '{}' OPENED (opened at: {})",
                circuitBreaker.getName(), event.getCreationTime());

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.setCircuitBreakerState(1); // 1 = OPEN
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping state update", e);
        }
    }

    /**
     * Records circuit closed event.
     */
    private void onCircuitClosed(CircuitBreaker.CircuitClosed event) {
        LOGGER.info("CircuitBreaker '{}' CLOSED (closed at: {})",
                circuitBreaker.getName(), event.getCreationTime());

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.setCircuitBreakerState(0); // 0 = CLOSED
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping state update", e);
        }
    }

    /**
     * Records circuit half-open event.
     */
    private void onCircuitHalfOpen(CircuitBreaker.CircuitHalfOpen event) {
        LOGGER.info("CircuitBreaker '{}' HALF_OPEN (at: {})",
                circuitBreaker.getName(), event.getCreationTime());

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.setCircuitBreakerState(2); // 2 = HALF_OPEN
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping state update", e);
        }
    }
}
