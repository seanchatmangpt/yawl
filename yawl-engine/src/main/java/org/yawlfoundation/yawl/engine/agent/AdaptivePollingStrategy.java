package org.yawlfoundation.yawl.engine.agent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * AdaptivePollingStrategy implements exponential backoff for agent polling,
 * reducing CPU contention when work queues are empty.
 *
 * Algorithm:
 * 1. Initial poll: 1ms timeout (responsive)
 * 2. Empty result: Double timeout (1ms → 2ms → 4ms → 8ms ...)
 * 3. Cap: Never exceed 1000ms timeout
 * 4. Successful dequeue: Reset to 1ms (responsive)
 * 5. Max backoff reached: Stay at 1000ms until work arrives
 *
 * Benefits:
 * - Reduces CPU spinning on empty queues
 * - Fast response when work arrives (immediate reset to 1ms)
 * - Bounded latency (worst case 1000ms wait time)
 * - Per-agent backoff state (independent scaling)
 *
 * Usage:
 * ```java
 * AdaptivePollingStrategy strategy = new AdaptivePollingStrategy(1, 1000);
 * while (running) {
 *     WorkItem item = queue.dequeue(agentId,
 *         strategy.getTimeout(agentId),
 *         TimeUnit.MILLISECONDS
 *     );
 *     if (item != null) {
 *         strategy.recordSuccess(agentId);
 *         processItem(item);
 *     } else {
 *         strategy.recordEmpty(agentId);
 *     }
 * }
 * ```
 *
 * Thread Safety:
 * - Per-agent timeout state protected by ConcurrentHashMap
 * - All operations are thread-safe
 * - No global synchronization
 *
 * @since Java 21
 */
public class AdaptivePollingStrategy {

    /**
     * Initial polling timeout in milliseconds (responsive).
     */
    private final long initialTimeoutMs;

    /**
     * Maximum polling timeout in milliseconds (prevents infinite backoff).
     */
    private final long maxTimeoutMs;

    /**
     * Current timeout per agent, stored in thread-local or external map.
     * In production, this would be in a ConcurrentHashMap or ScopedValue.
     * For this implementation, we track per-agent state externally.
     */
    private static class PollingState {
        long currentTimeoutMs;
        int backoffLevel;

        PollingState(long initialTimeoutMs) {
            this.currentTimeoutMs = initialTimeoutMs;
            this.backoffLevel = 0;
        }
    }

    /**
     * Creates a new AdaptivePollingStrategy.
     *
     * @param initialTimeoutMs Initial timeout in milliseconds (e.g., 1)
     * @param maxTimeoutMs Maximum timeout in milliseconds (e.g., 1000)
     * @throws IllegalArgumentException if timeouts are invalid
     */
    public AdaptivePollingStrategy(long initialTimeoutMs, long maxTimeoutMs) {
        if (initialTimeoutMs <= 0) {
            throw new IllegalArgumentException("initialTimeoutMs must be positive, got: " + initialTimeoutMs);
        }
        if (maxTimeoutMs < initialTimeoutMs) {
            throw new IllegalArgumentException(
                "maxTimeoutMs must be >= initialTimeoutMs, got: " + maxTimeoutMs + " < " + initialTimeoutMs
            );
        }
        this.initialTimeoutMs = initialTimeoutMs;
        this.maxTimeoutMs = maxTimeoutMs;
    }

    /**
     * Gets the current polling timeout for an agent.
     * Returns the adaptive timeout based on recent dequeue success/failure.
     *
     * @param agentId The agent UUID
     * @return Timeout in milliseconds
     * @throws NullPointerException if agentId is null
     */
    public long getTimeout(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        PollingState state = getOrCreateState(agentId);
        return state.currentTimeoutMs;
    }

    /**
     * Records a successful dequeue operation (work item obtained).
     * Resets backoff to initial state for responsive future polls.
     *
     * @param agentId The agent UUID
     * @throws NullPointerException if agentId is null
     */
    public void recordSuccess(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        PollingState state = getOrCreateState(agentId);
        state.currentTimeoutMs = initialTimeoutMs;
        state.backoffLevel = 0;
    }

    /**
     * Records an empty dequeue operation (no work available).
     * Doubles the timeout up to maxTimeoutMs.
     *
     * Backoff progression:
     * Level 0 (initial): 1ms
     * Level 1: 2ms
     * Level 2: 4ms
     * Level 3: 8ms
     * ... (doubling each time)
     * Level N: min(2^N ms, maxTimeoutMs)
     *
     * @param agentId The agent UUID
     * @throws NullPointerException if agentId is null
     */
    public void recordEmpty(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        PollingState state = getOrCreateState(agentId);

        // Double timeout, capped at max
        long newTimeout = Math.min(state.currentTimeoutMs * 2, maxTimeoutMs);
        state.currentTimeoutMs = newTimeout;
        state.backoffLevel++;
    }

    /**
     * Gets the current backoff level for an agent (for monitoring).
     * Useful for understanding how long an agent has been waiting.
     *
     * @param agentId The agent UUID
     * @return Backoff level (0 = initial, increments on each empty)
     * @throws NullPointerException if agentId is null
     */
    public int getBackoffLevel(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        PollingState state = getOrCreateState(agentId);
        return state.backoffLevel;
    }

    /**
     * Resets an agent's backoff state to initial.
     * Used when shutting down an agent or resetting monitoring.
     *
     * @param agentId The agent UUID
     * @throws NullPointerException if agentId is null
     */
    public void reset(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        PollingState state = getOrCreateState(agentId);
        state.currentTimeoutMs = initialTimeoutMs;
        state.backoffLevel = 0;
    }

    /**
     * Clears all backoff state (for testing or shutdown).
     */
    public void resetAll() {
        stateMap.clear();
    }

    /**
     * Per-thread state storage. In production, use ThreadLocal<UUID> or ScopedValue.
     * For simplicity, we use a simple pattern here.
     */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, PollingState> stateMap =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Gets or creates polling state for an agent.
     *
     * @param agentId The agent UUID
     * @return PollingState object (never null)
     */
    private PollingState getOrCreateState(UUID agentId) {
        return stateMap.computeIfAbsent(agentId, _ -> new PollingState(initialTimeoutMs));
    }

    /**
     * Gets the number of agents being tracked.
     * Useful for monitoring agent lifecycle.
     *
     * @return Number of agents with active polling state
     */
    public int getTrackedAgentCount() {
        return stateMap.size();
    }

    /**
     * Diagnostic string representation of polling strategy configuration.
     */
    @Override
    public String toString() {
        return "AdaptivePollingStrategy[initialMs=%d, maxMs=%d, trackedAgents=%d]"
            .formatted(initialTimeoutMs, maxTimeoutMs, getTrackedAgentCount());
    }
}
