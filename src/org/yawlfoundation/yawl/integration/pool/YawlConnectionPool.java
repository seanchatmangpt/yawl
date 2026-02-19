/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.pool;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.resilience.observability.FallbackObservability;
import org.yawlfoundation.yawl.resilience.observability.RetryObservability;

/**
 * Thread-safe connection pool for YAWL engine connections.
 *
 * <p>This pool manages YAWL engine sessions using Apache Commons Pool2,
 * providing connection reuse, validation, health checks, and automatic
 * reconnection on session expiry.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Thread-safe for concurrent borrow/return operations</li>
 *   <li>Connection validation on borrow, return, and idle</li>
 *   <li>Automatic reconnection on session expiry</li>
 *   <li>Configurable pool size and timeouts</li>
 *   <li>Comprehensive metrics collection</li>
 *   <li>Graceful shutdown with connection cleanup</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create configuration
 * YawlConnectionPoolConfig config = new YawlConnectionPoolConfig();
 * config.setEngineUrl("http://localhost:8080/yawl");
 * config.setUsername("admin");
 * config.setPassword(System.getenv("YAWL_PASSWORD"));
 * config.setMaxTotal(20);
 *
 * // Create pool
 * YawlConnectionPool pool = new YawlConnectionPool(config);
 * pool.initialize();
 *
 * // Use connections
 * try (YawlSession session = pool.borrowSession()) {
 *     String caseId = session.getClient().launchCase(specId, params, session.getHandle());
 * } // Automatically returned to pool
 *
 * // Check metrics
 * YawlConnectionPoolMetrics metrics = pool.getMetrics();
 * System.out.println("Active: " + metrics.getActiveConnections());
 *
 * // Shutdown
 * pool.shutdown();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @see YawlSession
 * @see YawlConnectionPoolConfig
 * @see YawlConnectionPoolMetrics
 */
public class YawlConnectionPool {

    private static final Logger LOGGER = Logger.getLogger(YawlConnectionPool.class.getName());
    private static final String COMPONENT_NAME = "connection-pool";

    private final YawlConnectionPoolConfig config;
    private final YawlConnectionPoolMetrics metrics;
    private final AtomicLong sessionIdCounter = new AtomicLong(0);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final ReentrantLock healthCheckLock = new ReentrantLock();

    private volatile GenericObjectPool<YawlSession> pool;
    private volatile Thread healthCheckThread;
    private volatile RetryObservability retryObservability;

    /**
     * Create a new connection pool with the given configuration.
     *
     * @param config pool configuration
     * @throws IllegalArgumentException if config is null or invalid
     */
    public YawlConnectionPool(YawlConnectionPoolConfig config) {
        Objects.requireNonNull(config, "config is required");
        validateConfig(config);
        this.config = config.copy();
        this.metrics = new YawlConnectionPoolMetrics();
    }

    /**
     * Validate configuration parameters.
     *
     * @param config configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void validateConfig(YawlConnectionPoolConfig config) {
        if (config.getEngineUrl() == null || config.getEngineUrl().isBlank()) {
            throw new IllegalArgumentException("engineUrl is required in pool configuration");
        }
        if (config.getPassword() == null || config.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required in pool configuration. " +
                    "Set via YAWL_PASSWORD environment variable.");
        }
        if (config.getMaxTotal() <= 0) {
            throw new IllegalArgumentException("maxTotal must be positive");
        }
        if (config.getMaxIdle() < 0) {
            throw new IllegalArgumentException("maxIdle cannot be negative");
        }
        if (config.getMinIdle() < 0) {
            throw new IllegalArgumentException("minIdle cannot be negative");
        }
        if (config.getMinIdle() > config.getMaxIdle()) {
            throw new IllegalArgumentException("minIdle cannot exceed maxIdle");
        }
        if (config.getMaxIdle() > config.getMaxTotal()) {
            throw new IllegalArgumentException("maxIdle cannot exceed maxTotal");
        }
    }

    /**
     * Initialize the connection pool.
     * Creates the underlying pool and starts health check thread.
     *
     * @throws IllegalStateException if already initialized or during initialization
     */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("Pool already initialized");
        }

        LOGGER.info("Initializing YAWL connection pool: " + config.getEngineUrl());

        // Initialize retry observability
        retryObservability = RetryObservability.getInstance();

        GenericObjectPoolConfig<YawlSession> poolConfig = createPoolConfig();
        PooledObjectFactory<YawlSession> factory = createSessionFactory();

        pool = new GenericObjectPool<>(factory, poolConfig);

        // Start health check thread
        startHealthCheckThread();

        LOGGER.info("YAWL connection pool initialized: maxTotal=" + config.getMaxTotal() +
                ", maxIdle=" + config.getMaxIdle() + ", minIdle=" + config.getMinIdle());
    }

    /**
     * Create Apache Commons Pool configuration from our config.
     */
    private GenericObjectPoolConfig<YawlSession> createPoolConfig() {
        GenericObjectPoolConfig<YawlSession> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(config.getMaxTotal());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(config.getMinIdle());
        poolConfig.setMaxWait(Duration.ofMillis(config.getMaxWaitMs()));
        poolConfig.setTestOnBorrow(config.isValidationOnBorrow());
        poolConfig.setTestOnReturn(config.isValidationOnReturn());
        poolConfig.setTestWhileIdle(config.isValidationWhileIdle());
        poolConfig.setTestOnCreate(config.isTestOnCreate());
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(config.getTimeBetweenEvictionRunsMs()));
        poolConfig.setMinEvictableIdleDuration(Duration.ofMillis(config.getMinEvictableIdleTimeMs()));
        poolConfig.setSoftMinEvictableIdleDuration(Duration.ofMillis(config.getSoftMinEvictableIdleTimeMs()));
        poolConfig.setNumTestsPerEvictionRun(config.getNumTestsPerEvictionRun());
        poolConfig.setLifo(config.isLifo());
        poolConfig.setFairness(config.isFairness());
        poolConfig.setBlockWhenExhausted(config.isBlockWhenExhausted());
        return poolConfig;
    }

    /**
     * Create the session factory for the pool.
     */
    private PooledObjectFactory<YawlSession> createSessionFactory() {
        return new PooledObjectFactory<>() {
            @Override
            public PooledObject<YawlSession> makeObject() throws Exception {
                return doMakeObject();
            }

            @Override
            public void destroyObject(PooledObject<YawlSession> p) throws Exception {
                doDestroyObject(p);
            }

            @Override
            public boolean validateObject(PooledObject<YawlSession> p) {
                return doValidateObject(p);
            }

            @Override
            public void activateObject(PooledObject<YawlSession> p) throws Exception {
                p.getObject().markBorrowed();
            }

            @Override
            public void passivateObject(PooledObject<YawlSession> p) throws Exception {
                p.getObject().markReturned();
            }
        };
    }

    /**
     * Create a new YawlSession.
     */
    private PooledObject<YawlSession> doMakeObject() throws Exception {
        long startTime = System.currentTimeMillis();
        String sessionId = "yawl-session-" + sessionIdCounter.incrementAndGet();

        LOGGER.fine(() -> "Creating new YawlSession: " + sessionId);

        InterfaceB_EnvironmentBasedClient client =
                new InterfaceB_EnvironmentBasedClient(config.getEngineUrl() + "/ib");

        // Connect with retry
        String sessionHandle = connectWithRetry(client, sessionId);

        YawlSession session = new YawlSession(sessionId, client, sessionHandle);
        session.setOwningPool(YawlConnectionPool.this);

        long createTime = System.currentTimeMillis() - startTime;
        metrics.recordConnectionCreated(createTime);

        LOGGER.fine(() -> "Created YawlSession: " + sessionId + " in " + createTime + "ms");

        return new DefaultPooledObject<>(session);
    }

    /**
     * Connect to YAWL engine with retry logic.
     */
    private String connectWithRetry(InterfaceB_EnvironmentBasedClient client, String sessionId)
            throws IOException {
        int attempts = config.getConnectionRetryAttempts();
        long delayMs = config.getConnectionRetryDelayMs();
        IOException lastException = null;
        long backoffMs = 0;

        for (int i = 0; i < attempts; i++) {
            int attemptNumber = i + 1;

            RetryObservability.RetryContext retryCtx = retryObservability.startRetry(
                    COMPONENT_NAME, "connect", attemptNumber, attempts, backoffMs);

            try {
                String handle = client.connect(config.getUsername(), config.getPassword());

                if (handle == null || handle.contains("<failure>")) {
                    throw new IOException("Connection failed: " + handle);
                }

                retryCtx.recordSuccess();
                retryObservability.completeSequence(COMPONENT_NAME, "connect", true, attemptNumber);
                return handle;
            } catch (IOException e) {
                lastException = e;
                retryCtx.recordFailure(e);

                final int currentAttemptNumber = attemptNumber;
                final int totalAttempts = attempts;
                final String errorMessage = e.getMessage();
                LOGGER.warning(() -> "Connection attempt " + currentAttemptNumber + "/" + totalAttempts +
                        " failed for " + sessionId + ": " + errorMessage);

                if (i < attempts - 1) {
                    backoffMs = delayMs * (i + 1);
                    retryObservability.recordBackoff(COMPONENT_NAME, "connect", backoffMs);

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        retryObservability.completeSequence(COMPONENT_NAME, "connect", false, attemptNumber);
                        throw new IOException("Connection interrupted", ie);
                    }
                }
            }
        }

        metrics.recordConnectionError("connection_failed");
        retryObservability.completeSequence(COMPONENT_NAME, "connect", false, attempts);
        throw new IOException("Failed to connect to YAWL engine after " + attempts +
                " attempts: " + config.getEngineUrl(), lastException);
    }

    /**
     * Destroy a YawlSession.
     */
    private void doDestroyObject(PooledObject<YawlSession> p) {
        YawlSession session = p.getObject();
        LOGGER.fine(() -> "Destroying YawlSession: " + session.getSessionId());

        try {
            session.disconnect();
            metrics.recordConnectionDestroyed();
        } catch (Exception e) {
            LOGGER.warning(() -> "Error destroying session " + session.getSessionId() + ": " + e.getMessage());
        }
    }

    /**
     * Validate a YawlSession.
     */
    private boolean doValidateObject(PooledObject<YawlSession> p) {
        YawlSession session = p.getObject();
        boolean valid = session.validate();

        if (valid) {
            metrics.recordValidationPassed();
        } else {
            metrics.recordValidationFailed();
            LOGGER.warning(() -> "Session validation failed: " + session.getSessionId());
        }

        return valid;
    }

    /**
     * Borrow a session from the pool.
     * Blocks if no sessions available and pool is at max capacity.
     *
     * @return a valid YawlSession
     * @throws NoSuchElementException if timeout waiting for session
     * @throws IllegalStateException  if pool is shutdown
     * @throws RuntimeException       if connection creation fails
     */
    public YawlSession borrowSession() {
        return borrowSession(config.getMaxWaitMs());
    }

    /**
     * Borrow a session from the pool with specified timeout.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return a valid YawlSession
     * @throws NoSuchElementException if timeout waiting for session
     * @throws IllegalStateException  if pool is shutdown
     */
    public YawlSession borrowSession(long timeoutMs) {
        if (shutdown.get()) {
            throw new IllegalStateException("Pool is shutdown");
        }
        if (!initialized.get()) {
            throw new IllegalStateException("Pool not initialized");
        }

        long startTime = System.currentTimeMillis();

        try {
            YawlSession session = pool.borrowObject(Duration.ofMillis(timeoutMs));
            long waitTime = System.currentTimeMillis() - startTime;
            metrics.recordBorrowed(waitTime);
            updateMetrics();
            return session;
        } catch (NoSuchElementException e) {
            metrics.recordBorrowTimeout();
            throw new NoSuchElementException("Timeout waiting for YAWL connection after " + timeoutMs + "ms");
        } catch (Exception e) {
            metrics.recordConnectionError("borrow_failed");
            throw new RuntimeException("Failed to borrow YawlSession: " + e.getMessage(), e);
        }
    }

    /**
     * Return a session to the pool.
     *
     * @param session the session to return
     * @throws IllegalArgumentException if session is null
     */
    public void returnSession(YawlSession session) {
        if (session == null) {
            return;
        }

        try {
            pool.returnObject(session);
            metrics.recordReturned();
            updateMetrics();
        } catch (Exception e) {
            LOGGER.warning(() -> "Error returning session " + session.getSessionId() + ": " + e.getMessage());
            metrics.recordConnectionError("return_failed");
        }
    }

    /**
     * Invalidate a session (remove from pool and destroy).
     *
     * @param session the session to invalidate
     */
    public void invalidateSession(YawlSession session) {
        if (session == null) {
            return;
        }

        try {
            session.invalidate();
            pool.invalidateObject(session);
            metrics.recordConnectionEvicted();
            updateMetrics();
            LOGGER.fine(() -> "Invalidated session: " + session.getSessionId());
        } catch (Exception e) {
            LOGGER.warning(() -> "Error invalidating session " + session.getSessionId() + ": " + e.getMessage());
        }
    }

    /**
     * Update metrics from pool state.
     */
    private void updateMetrics() {
        if (pool != null) {
            metrics.setActiveConnections(pool.getNumActive());
            metrics.setIdleConnections(pool.getNumIdle());
        }
    }

    /**
     * Start the health check thread.
     */
    private void startHealthCheckThread() {
        if (config.getHealthCheckIntervalMs() <= 0) {
            LOGGER.info("Health check disabled (interval <= 0)");
            return;
        }

        healthCheckThread = Thread.ofVirtual()
                .name("yawl-pool-health-check")
                .unstarted(this::runHealthCheck);

        healthCheckThread.setDaemon(true);
        healthCheckThread.start();

        LOGGER.info("Health check thread started (interval: " + config.getHealthCheckIntervalMs() + "ms)");
    }

    /**
     * Health check loop.
     */
    private void runHealthCheck() {
        while (!shutdown.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(config.getHealthCheckIntervalMs());

                if (shutdown.get()) {
                    break;
                }

                performHealthCheck();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.warning(() -> "Health check error: " + e.getMessage());
            }
        }

        LOGGER.info("Health check thread stopped");
    }

    /**
     * Perform a health check on the pool.
     */
    private void performHealthCheck() {
        healthCheckLock.lock();
        try {
            updateMetrics();

            boolean healthy = true;
            int attempts = config.getHealthCheckRetryAttempts();
            long delayMs = config.getHealthCheckRetryDelayMs();
            long backoffMs = 0;

            // Try to borrow and validate a session
            for (int i = 0; i < attempts; i++) {
                int attemptNumber = i + 1;
                YawlSession session = null;

                RetryObservability.RetryContext retryCtx = retryObservability.startRetry(
                        COMPONENT_NAME, "health-check", attemptNumber, attempts, backoffMs);

                try {
                    session = pool.borrowObject(Duration.ofSeconds(5));
                    boolean valid = session.validate();
                    pool.returnObject(session);

                    if (valid) {
                        healthy = true;
                        retryCtx.recordSuccess();
                        retryObservability.completeSequence(COMPONENT_NAME, "health-check", true, attemptNumber);
                        break;
                    } else {
                        healthy = false;
                        retryCtx.recordFailure(new RuntimeException("Session validation failed"));
                        LOGGER.warning("Health check: session validation failed (attempt " + attemptNumber + "/" + attempts + ")");
                    }
                } catch (Exception e) {
                    healthy = false;
                    retryCtx.recordFailure(e);
                    LOGGER.warning("Health check failed (attempt " + attemptNumber + "/" + attempts + "): " + e.getMessage());

                    if (session != null) {
                        try {
                            pool.invalidateObject(session);
                        } catch (Exception ignored) {
                        }
                    }

                    if (i < attempts - 1) {
                        backoffMs = delayMs;
                        retryObservability.recordBackoff(COMPONENT_NAME, "health-check", backoffMs);

                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            retryObservability.completeSequence(COMPONENT_NAME, "health-check", false, attemptNumber);
                            break;
                        }
                    }
                }
            }

            metrics.recordHealthCheck(healthy);

            if (healthy) {
                LOGGER.fine(() -> "Health check passed: active=" + metrics.getActiveConnections() +
                        ", idle=" + metrics.getIdleConnections());
            } else {
                LOGGER.warning("Health check failed: engine may be unavailable");
                retryObservability.completeSequence(COMPONENT_NAME, "health-check", false, attempts);
            }

        } finally {
            healthCheckLock.unlock();
        }
    }

    /**
     * Get current pool metrics.
     *
     * @return metrics snapshot
     */
    public YawlConnectionPoolMetrics getMetrics() {
        updateMetrics();
        return metrics;
    }

    /**
     * Get the pool configuration.
     *
     * @return configuration (copy)
     */
    public YawlConnectionPoolConfig getConfig() {
        return config.copy();
    }

    /**
     * Check if pool is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Check if pool is shutdown.
     *
     * @return true if shutdown
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * Get the number of active (borrowed) connections.
     *
     * @return active count
     */
    public int getActiveCount() {
        return pool != null ? pool.getNumActive() : 0;
    }

    /**
     * Get the number of idle connections.
     *
     * @return idle count
     */
    public int getIdleCount() {
        return pool != null ? pool.getNumIdle() : 0;
    }

    /**
     * Clear all idle connections from the pool.
     */
    public void clearIdle() {
        if (pool != null) {
            LOGGER.info("Clearing idle connections from pool");
            pool.clear();
            updateMetrics();
        }
    }

    /**
     * Gracefully shutdown the pool.
     * Closes all connections and stops health check thread.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }

        LOGGER.info("Shutting down YAWL connection pool");

        // Stop health check thread
        if (healthCheckThread != null) {
            healthCheckThread.interrupt();
            try {
                healthCheckThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Close pool
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception e) {
                LOGGER.warning("Error closing pool: " + e.getMessage());
            }
        }

        LOGGER.info("YAWL connection pool shutdown complete: " +
                "created=" + metrics.getTotalCreated() +
                ", destroyed=" + metrics.getTotalDestroyed() +
                ", borrowed=" + metrics.getTotalBorrowed());
    }

    /**
     * Borrow a session with fallback when pool is exhausted.
     * If no session is available within timeout, uses the fallback supplier.
     * Records fallback operations with FallbackObservability.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @param fallbackSupplier supplier for fallback session when pool exhausted
     * @param fallbackDataTimestamp timestamp when fallback session data was created
     * @return a valid YawlSession (from pool or fallback)
     */
    public YawlSession borrowSessionWithFallback(long timeoutMs,
                                                 Supplier<YawlSession> fallbackSupplier,
                                                 Instant fallbackDataTimestamp) {
        if (shutdown.get()) {
            throw new IllegalStateException("Pool is shutdown");
        }
        if (!initialized.get()) {
            throw new IllegalStateException("Pool not initialized");
        }

        long startTime = System.currentTimeMillis();

        try {
            YawlSession session = pool.borrowObject(Duration.ofMillis(timeoutMs));
            long waitTime = System.currentTimeMillis() - startTime;
            metrics.recordBorrowed(waitTime);
            updateMetrics();
            return session;
        } catch (NoSuchElementException e) {
            // Pool exhausted - use fallback with observability
            metrics.recordBorrowTimeout();

            if (fallbackSupplier == null) {
                throw new NoSuchElementException("Timeout waiting for YAWL connection after " + timeoutMs + "ms and no fallback provided");
            }

            FallbackObservability fallbackObs = FallbackObservability.getInstance();
            FallbackObservability.FallbackResult result = fallbackObs.recordFallback(
                "connection-pool",
                "borrowSession",
                FallbackObservability.FallbackReason.BULKHEAD_FULL,
                FallbackObservability.FallbackSource.SECONDARY_SERVICE,
                fallbackSupplier,
                fallbackDataTimestamp,
                e
            );

            if (result.isStale()) {
                LOGGER.warning(() -> "Connection pool fallback served stale session (age=" +
                    result.getDataAgeMs() + "ms)");
            }

            LOGGER.info(() -> "Used fallback session due to pool exhaustion");
            @SuppressWarnings("unchecked")
            YawlSession fallbackSession = (YawlSession) result.getValue();
            return fallbackSession;
        } catch (Exception e) {
            metrics.recordConnectionError("borrow_failed");

            if (fallbackSupplier != null) {
                FallbackObservability fallbackObs = FallbackObservability.getInstance();
                FallbackObservability.FallbackResult result = fallbackObs.recordFallback(
                    "connection-pool",
                    "borrowSession",
                    FallbackObservability.FallbackReason.SERVICE_ERROR,
                    FallbackObservability.FallbackSource.SECONDARY_SERVICE,
                    fallbackSupplier,
                    Instant.now(),
                    e
                );

                @SuppressWarnings("unchecked")
                YawlSession fallbackSession = (YawlSession) result.getValue();
                return fallbackSession;
            }

            throw new RuntimeException("Failed to borrow YawlSession: " + e.getMessage(), e);
        }
    }

    /**
     * Execute an operation with a borrowed session.
     * Session is automatically returned to pool after operation.
     *
     * @param <T>       return type
     * @param operation operation to execute
     * @return operation result
     * @throws Exception if operation fails
     */
    public <T> T withSession(SessionOperation<T> operation) throws Exception {
        YawlSession session = borrowSession();
        try {
            return operation.execute(session);
        } finally {
            returnSession(session);
        }
    }

    /**
     * Functional interface for session operations.
     *
     * @param <T> return type
     */
    @FunctionalInterface
    public interface SessionOperation<T> {
        T execute(YawlSession session) throws Exception;
    }
}
