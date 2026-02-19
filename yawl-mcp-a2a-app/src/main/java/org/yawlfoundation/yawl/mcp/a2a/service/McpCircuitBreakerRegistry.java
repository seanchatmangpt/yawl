package org.yawlfoundation.yawl.mcp.a2a.service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.EventProcessor;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;

/**
 * Registry for MCP server circuit breakers.
 *
 * <p>Manages circuit breaker instances for different MCP server connections.
 * Each MCP server connection gets its own circuit breaker with configurable
 * thresholds and behaviors.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Per-server circuit breaker instances</li>
 *   <li>Configurable failure rate thresholds</li>
 *   <li>Automatic state transition logging</li>
 *   <li>Micrometer metrics integration</li>
 *   <li>Thread-safe state management</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class McpCircuitBreakerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpCircuitBreakerRegistry.class);

    private final CircuitBreakerRegistry resilience4jRegistry;
    private final Map<String, AtomicReference<McpCircuitBreakerState>> stateMap;
    private final CircuitBreakerProperties properties;

    /**
     * Creates a new MCP circuit breaker registry with the given properties.
     *
     * @param properties the circuit breaker configuration properties
     */
    public McpCircuitBreakerRegistry(CircuitBreakerProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.stateMap = new ConcurrentHashMap<>();
        this.resilience4jRegistry = createResilience4jRegistry();
    }

    /**
     * Gets or creates a circuit breaker for the specified MCP server.
     *
     * @param serverName the MCP server identifier
     * @return the circuit breaker for the server
     */
    public CircuitBreaker getOrCreate(String serverName) {
        CircuitBreaker circuitBreaker = resilience4jRegistry.circuitBreaker(serverName, () ->
            buildCircuitBreakerConfig(serverName));

        // Initialize state tracking if not present
        stateMap.computeIfAbsent(serverName, name -> {
            AtomicReference<McpCircuitBreakerState> ref = new AtomicReference<>(
                McpCircuitBreakerState.Closed.create(name));
            LOGGER.info("Initialized circuit breaker state for MCP server: {}", name);
            return ref;
        });

        return circuitBreaker;
    }

    /**
     * Gets the current state of the circuit breaker for the specified server.
     *
     * @param serverName the MCP server identifier
     * @return the current circuit breaker state
     */
    public McpCircuitBreakerState getState(String serverName) {
        AtomicReference<McpCircuitBreakerState> ref = stateMap.get(serverName);
        if (ref == null) {
            return McpCircuitBreakerState.Closed.create(serverName);
        }
        return ref.get();
    }

    /**
     * Records a successful call for the specified server.
     *
     * @param serverName the MCP server identifier
     */
    public void recordSuccess(String serverName) {
        AtomicReference<McpCircuitBreakerState> ref = stateMap.get(serverName);
        if (ref == null) {
            return;
        }

        ref.updateAndGet(current -> switch (current) {
            case McpCircuitBreakerState.Closed closed -> {
                int windowSize = properties.circuitBreaker().slidingWindowSize();
                yield closed.recordSuccess(windowSize);
            }
            case McpCircuitBreakerState.HalfOpen halfOpen -> {
                McpCircuitBreakerState.HalfOpen updated = halfOpen.recordSuccess();
                if (updated.shouldTransitionToClosed()) {
                    LOGGER.info("Circuit breaker for MCP server {} transitioning to CLOSED after successful test calls",
                               serverName);
                    yield McpCircuitBreakerState.Closed.create(serverName);
                }
                yield updated;
            }
            case McpCircuitBreakerState.Open open -> current;
        });
    }

    /**
     * Records a failed call for the specified server.
     *
     * @param serverName the MCP server identifier
     * @param errorMessage the error message from the failure
     */
    public void recordFailure(String serverName, String errorMessage) {
        AtomicReference<McpCircuitBreakerState> ref = stateMap.get(serverName);
        if (ref == null) {
            return;
        }

        ref.updateAndGet(current -> switch (current) {
            case McpCircuitBreakerState.Closed closed -> {
                int windowSize = properties.circuitBreaker().slidingWindowSize();
                McpCircuitBreakerState.Closed updated = closed.recordFailure(windowSize);
                if (updated.currentFailureRate() >= properties.circuitBreaker().failureRateThreshold()) {
                    LOGGER.warn("Circuit breaker for MCP server {} transitioning to OPEN (failure rate: {}%)",
                               serverName, updated.currentFailureRate());
                    yield McpCircuitBreakerState.Open.create(
                        serverName,
                        Duration.ofSeconds(properties.circuitBreaker().waitDurationOpenStateSeconds()).toMillis(),
                        updated.failureCount(),
                        errorMessage);
                }
                yield updated;
            }
            case McpCircuitBreakerState.HalfOpen halfOpen -> {
                McpCircuitBreakerState.HalfOpen updated = halfOpen.recordFailure();
                LOGGER.warn("Circuit breaker for MCP server {} transitioning back to OPEN after failed test call",
                           serverName);
                yield McpCircuitBreakerState.Open.create(
                    serverName,
                    Duration.ofSeconds(properties.circuitBreaker().waitDurationOpenStateSeconds()).toMillis(),
                    halfOpen.permittedCalls(),
                    errorMessage);
            }
            case McpCircuitBreakerState.Open open -> current;
        });
    }

    /**
     * Checks if calls are permitted for the specified server.
     *
     * @param serverName the MCP server identifier
     * @return true if calls are permitted, false if circuit is open
     */
    public boolean isCallPermitted(String serverName) {
        McpCircuitBreakerState state = getState(serverName);

        return switch (state) {
            case McpCircuitBreakerState.Closed closed -> true;
            case McpCircuitBreakerState.HalfOpen halfOpen -> halfOpen.callsRemaining() > 0;
            case McpCircuitBreakerState.Open open -> {
                if (open.shouldTransitionToHalfOpen()) {
                    transitionToHalfOpen(serverName);
                    yield true;
                }
                yield false;
            }
        };
    }

    /**
     * Gets all registered server names.
     *
     * @return iterable of server names
     */
    public Iterable<String> getServerNames() {
        return stateMap.keySet();
    }

    /**
     * Removes the circuit breaker for the specified server.
     *
     * @param serverName the MCP server identifier
     */
    public void remove(String serverName) {
        stateMap.remove(serverName);
        resilience4jRegistry.remove(serverName);
        LOGGER.info("Removed circuit breaker for MCP server: {}", serverName);
    }

    /**
     * Resets all circuit breakers to closed state.
     */
    public void resetAll() {
        stateMap.forEach((serverName, ref) -> {
            ref.set(McpCircuitBreakerState.Closed.create(serverName));
            CircuitBreaker cb = resilience4jRegistry.circuitBreaker(serverName);
            if (cb != null) {
                cb.reset();
            }
        });
        LOGGER.info("Reset all circuit breakers to CLOSED state");
    }

    /**
     * Gets the underlying Resilience4j registry for metrics integration.
     *
     * @return the Resilience4j circuit breaker registry
     */
    public CircuitBreakerRegistry getResilience4jRegistry() {
        return resilience4jRegistry;
    }

    private void transitionToHalfOpen(String serverName) {
        AtomicReference<McpCircuitBreakerState> ref = stateMap.get(serverName);
        if (ref == null) {
            return;
        }

        ref.updateAndGet(current -> {
            if (current instanceof McpCircuitBreakerState.Open open) {
                LOGGER.info("Circuit breaker for MCP server {} transitioning to HALF_OPEN",
                           serverName);
                return McpCircuitBreakerState.HalfOpen.create(
                    serverName,
                    properties.circuitBreaker().permittedNumberOfCallsInHalfOpenState());
            }
            return current;
        });
    }

    private CircuitBreakerRegistry createResilience4jRegistry() {
        CircuitBreakerConfig defaultConfig = buildCircuitBreakerConfig("default");

        RegistryEventConsumer<CircuitBreaker> eventConsumer = new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                LOGGER.debug("Circuit breaker added: {}", entryAddedEvent.getAddedEntry().getName());
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemovedEvent) {
                LOGGER.debug("Circuit breaker removed: {}", entryRemovedEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                LOGGER.debug("Circuit breaker replaced: {} -> {}",
                           entryReplacedEvent.getOldEntry().getName(),
                           entryReplacedEvent.getNewEntry().getName());
            }
        };

        return CircuitBreakerRegistry.of(defaultConfig, eventConsumer);
    }

    private CircuitBreakerConfig buildCircuitBreakerConfig(String serverName) {
        CircuitBreakerProperties.CircuitBreakerConfig cbProps = properties.circuitBreaker();

        return CircuitBreakerConfig.custom()
            .failureRateThreshold(cbProps.failureRateThreshold())
            .slowCallRateThreshold(cbProps.slowCallRateThreshold())
            .slowCallDurationThreshold(Duration.ofSeconds(cbProps.slowCallDurationSeconds()))
            .waitDurationInOpenState(Duration.ofSeconds(cbProps.waitDurationOpenStateSeconds()))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(cbProps.slidingWindowSize())
            .minimumNumberOfCalls(cbProps.minimumNumberOfCalls())
            .permittedNumberOfCallsInHalfOpenState(cbProps.permittedNumberOfCallsInHalfOpenState())
            .automaticTransitionFromOpenToHalfOpenEnabled(cbProps.automaticTransitionFromOpenToHalfOpen())
            .recordExceptions(IOException.class, TimeoutException.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
    }

    // Inner classes for exception configuration
    private static final class IOException extends java.io.IOException {}
    private static final class TimeoutException extends java.util.concurrent.TimeoutException {}
}
