package org.yawlfoundation.yawl.engine.agent.core;

/**
 * Immutable snapshot of VirtualThreadRuntime health at a point in time.
 * Obtained via VirtualThreadRuntime.stats().
 *
 * All counters are cumulative since runtime creation.
 * activeActors reflects the current live registry size.
 *
 * <p>Capacity formula (from empirical breaking-point measurement):
 * <pre>
 *   maxConcurrent = heapBytes / 1_454   // 1,454 bytes/actor measured at scale
 * </pre>
 *
 * @param activeActors  number of actors currently in the registry
 * @param totalSpawned  cumulative count of spawn() calls since creation
 * @param totalStopped  cumulative count of stop() calls since creation
 * @param totalMessages cumulative count of send() calls since creation
 */
public record RuntimeStats(
    int  activeActors,
    long totalSpawned,
    long totalStopped,
    long totalMessages
) {
    /**
     * Compact constructor validating all fields are non-negative.
     */
    public RuntimeStats {
        if (activeActors < 0) {
            throw new IllegalArgumentException("activeActors must be >= 0, got: " + activeActors);
        }
        if (totalSpawned < 0) {
            throw new IllegalArgumentException("totalSpawned must be >= 0, got: " + totalSpawned);
        }
        if (totalStopped < 0) {
            throw new IllegalArgumentException("totalStopped must be >= 0, got: " + totalStopped);
        }
        if (totalMessages < 0) {
            throw new IllegalArgumentException("totalMessages must be >= 0, got: " + totalMessages);
        }
    }
}
