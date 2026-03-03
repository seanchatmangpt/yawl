package org.yawlfoundation.yawl.bridge.erlang.transport;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.yawlfoundation.yawl.bridge.erlang.ErlangException;
import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;

/**
 * Health checker for BEAM node connectivity.
 *
 * <p>This class monitors BEAM node health by pinging every 5 seconds and
 * triggering reconnection if the node is down for more than 30 seconds.</p>
 *
 * <p>HYPER_STANDARDS: No mock code, only real health checks or throw.</p>
 *
 * @since 1.0.0
 */
public final class BeamHealthCheck implements AutoCloseable {

    private final ErlangConnectionPool connectionPool;
    private final Duration pingInterval;
    private final Duration reconnectThreshold;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastSuccessfulPing = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastFailureTimestamp = new AtomicReference<>(null);
    private final AtomicReference<Exception> lastError = new AtomicReference<>(null);
    private volatile boolean closed = false;

    /**
     * Creates a beam health check with default configuration.
     *
     * @param connectionPool The connection pool to monitor
     * @throws ErlangException if health check initialization fails
     */
    public BeamHealthCheck(ErlangConnectionPool connectionPool) {
        this(connectionPool, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    /**
     * Creates a beam health check with custom configuration.
     *
     * @param connectionPool The connection pool to monitor
     * @param pingInterval Time between pings
     * @param reconnectThreshold Time threshold before triggering reconnection
     * @throws ErlangException if health check initialization fails
     * @throws IllegalArgumentException if intervals are invalid
     */
    public BeamHealthCheck(ErlangConnectionPool connectionPool,
                          Duration pingInterval,
                          Duration reconnectThreshold) {
        if (connectionPool == null) {
            throw new ErlangException("Connection pool cannot be null");
        }
        if (pingInterval == null || pingInterval.isNegative() || pingInterval.isZero()) {
            throw new ErlangException("Ping interval must be positive");
        }
        if (reconnectThreshold == null || reconnectThreshold.isNegative() || reconnectThreshold.isZero()) {
            throw new ErlangException("Reconnect threshold must be positive");
        }

        this.connectionPool = connectionPool;
        this.pingInterval = pingInterval;
        this.reconnectThreshold = reconnectThreshold;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "beam-health-check-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
    }

    /**
     * Starts the health check monitoring.
     *
     * @throws ErlangException if health check fails to start
     * @throws IllegalStateException if already running or closed
     */
    public void start() {
        if (closed) {
            throw new IllegalStateException("Health check is closed");
        }
        if (running.get()) {
            throw new IllegalStateException("Health check is already running");
        }

        try {
            // Start scheduled ping task
            scheduler.scheduleAtFixedRate(
                this::performPing,
                0,
                pingInterval.toSeconds(),
                TimeUnit.SECONDS);

            running.set(true);

        } catch (Exception e) {
            throw new ErlangException(
                "Failed to start beam health check", e);
        }
    }

    /**
     * Stops the health check monitoring.
     *
     * @throws IllegalStateException if not running or already stopped
     */
    public void stop() {
        if (!running.get()) {
            throw new IllegalStateException("Health check is not running");
        }

        scheduler.shutdown();
        running.set(false);
    }

    /**
     * Performs a ping to the BEAM node.
     */
    private void performPing() {
        if (closed || !running.get()) {
            return;
        }

        try {
            // Ping the erlang:node() function to check connectivity
            Object result = connectionPool.rpc(
                "erlang",
                "node"
            );

            if (result != null) {
                // Ping successful
                lastSuccessfulPing.set(Instant.now());
                lastFailureTimestamp.set(null);
                lastError.set(null);
            }

        } catch (ErlangException e) {
            // Ping failed
            lastError.set(e);
            Instant failureTime = Instant.now();

            if (lastFailureTimestamp.get() == null) {
                // First failure
                lastFailureTimestamp.set(failureTime);
            } else {
                // Check if we should trigger reconnection
                Duration failureDuration = Duration.between(lastFailureTimestamp.get(), failureTime);
                if (failureDuration.compareTo(reconnectThreshold) >= 0) {
                    triggerReconnection();
                }
            }
        }
    }

    /**
     * Triggers reconnection of the connection pool.
     */
    private void triggerReconnection() {
        try {
            // Log the reconnection event
            System.err.println("BEAM node health check failed for " + reconnectThreshold +
                             ", triggering reconnection");

            // Reconnect all failed connections
            connectionPool.reconnectAllFailed();

            // Reset failure timestamp
            lastFailureTimestamp.set(null);
            lastError.set(null);

            // Update last successful ping to now
            lastSuccessfulPing.set(Instant.now());

        } catch (ErlangException e) {
            // Log reconnection failure but don't propagate
            System.err.println("Reconnection failed: " + e.getMessage());
        }
    }

    /**
     * Gets the current health status.
     *
     * @return HealthStatus enum
     */
    public HealthStatus getHealthStatus() {
        if (closed) {
            return HealthStatus.CLOSED;
        }
        if (!running.get()) {
            return HealthStatus.STOPPED;
        }

        Instant lastPing = lastSuccessfulPing.get();
        Instant lastFailure = lastFailureTimestamp.get();

        if (lastFailure == null) {
            // No recent failures
            return HealthStatus.HEALTHY;
        } else {
            Duration failureDuration = Duration.between(lastFailure, Instant.now());
            if (failureDuration.compareTo(reconnectThreshold) >= 0) {
                return HealthStatus.UNHEALTHY;
            } else {
                // Temporary failure - still recovering
                return HealthStatus.DEGRADED;
            }
        }
    }

    /**
     * Gets the age of the last successful ping.
     *
     * @return Duration since last successful ping
     */
    public Duration getLastSuccessfulPingAge() {
        Instant lastPing = lastSuccessfulPing.get();
        return Duration.between(lastPing, Instant.now());
    }

    /**
     * Gets the last error encountered.
     *
     * @return Last error or null if no error
     */
    public Exception getLastError() {
        return lastError.get();
    }

    /**
     * Gets the current connection pool health.
     *
     * @return true if all connections are healthy
     */
    public boolean isConnectionPoolHealthy() {
        return connectionPool.isAllConnectionsHealthy();
    }

    /**
     * Gets the number of active connections.
     *
     * @return number of active connections
     */
    public int getActiveConnectionCount() {
        return connectionPool.getActiveConnectionCount();
    }

    /**
     * Gets the total number of connections.
     *
     * @return total connections
     */
    public int getTotalConnectionCount() {
        return connectionPool.getTotalConnectionCount();
    }

    /**
     * Checks if the health check is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Closes the health check and releases resources.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;

            if (running.get()) {
                stop();
            }

            // Shutdown scheduler
            try {
                if (!scheduler.isShutdown()) {
                    scheduler.shutdownNow();
                }
            } catch (Exception e) {
                // Log warning but don't propagate
                System.err.println("Warning: Error shutting down health check scheduler: " + e.getMessage());
            }
        }
    }

    /**
     * Health status enumeration.
     */
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        STOPPED,
        CLOSED
    }

    /**
     * Factory for creating beam health checks with common configurations.
     */
    public static final class Factory {
        /**
         * Creates a beam health check with default configuration.
         *
         * @param connectionPool The connection pool to monitor
         * @return configured BeamHealthCheck
         * @throws ErlangException if initialization fails
         */
        public static BeamHealthCheck create(ErlangConnectionPool connectionPool) {
            return new BeamHealthCheck(connectionPool);
        }

        /**
         * Creates a beam health check with custom configuration.
         *
         * @param connectionPool The connection pool to monitor
         * @param pingInterval Time between pings
         * @param reconnectThreshold Time threshold before triggering reconnection
         * @return configured BeamHealthCheck
         * @throws ErlangException if initialization fails
         */
        public static BeamHealthCheck create(ErlangConnectionPool connectionPool,
                                           Duration pingInterval,
                                           Duration reconnectThreshold) {
            return new BeamHealthCheck(connectionPool, pingInterval, reconnectThreshold);
        }
    }
}