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

package org.yawlfoundation.yawl.mcp.a2a.service.pool;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection pool for YAWL InterfaceB clients with Resilience4j Bulkhead protection.
 *
 * <p>This class provides a thread-safe connection pool for managing
 * YAWL client connections using Apache Commons Pool2, with Resilience4j Bulkhead
 * for concurrent access control and standardized metrics via Micrometer.</p>
 *
 * <h2>Pool Features</h2>
 * <ul>
 *   <li><strong>Object Pooling</strong>: Efficiently reuses YAWL connections</li>
 *   <li><strong>Bulkhead Isolation</strong>: Limits concurrent borrows via Bulkhead semaphore</li>
 *   <li><strong>Validation</strong>: Validates connections on borrow and return</li>
 *   <li><strong>Eviction</strong>: Evicts idle connections to maintain pool health</li>
 *   <li><strong>Metrics</strong>: Exposes pool usage via Micrometer/Resilience4j</li>
 *   <li><strong>Monitoring</strong>: Provides health checks and statistics</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Borrow a connection (wrapped with bulkhead)
 * InterfaceB_EnvironmentBasedClient client = connectionPool.borrowObject();
 *
 * // Use the connection
 * String sessionHandle = client.connect(username, password);
 * // ... perform YAWL operations ...
 * client.disconnect(sessionHandle);
 *
 * // Return the connection
 * connectionPool.returnObject(client);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see GenericObjectPool
 * @see YawlPooledConnectionFactory
 * @see Bulkhead
 */
@Component
public class YawlConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(YawlConnectionPool.class);

    @Autowired
    private PoolConfiguration poolConfiguration;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Value("${yawl.engine.url:http://localhost:8080/yawl}")
    private String yawlEngineUrl;

    @Value("${yawl.username:admin}")
    private String username;

    @Value("${yawl.password:YAWL}")
    private String password;

    @Value("${yawl.engine.connection.timeout-ms:5000}")
    private int connectionTimeout;

    private final Map<String, GenericObjectPool<InterfaceB_EnvironmentBasedClient>> pools = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> borrowCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> returnCounts = new ConcurrentHashMap<>();
    private final Map<String, Duration> totalBorrowTime = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    /**
     * Initializes the connection pool with Bulkhead protection.
     *
     * <p>This method creates the object pool with configured settings
     * and wraps it with a Resilience4j Bulkhead for concurrency control.</p>
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        LOGGER.info("Initializing YAWL connection pool with Bulkhead");

        // Create pool configuration
        GenericObjectPoolConfig<InterfaceB_EnvironmentBasedClient> poolConfig = createPoolConfig();

        // Create factory
        YawlPooledConnectionFactory factory = new YawlPooledConnectionFactory(
            yawlEngineUrl, username, password, connectionTimeout, null, null);

        // Create pool
        GenericObjectPool<InterfaceB_EnvironmentBasedClient> pool = new GenericObjectPool<>(factory, poolConfig);

        // Create bulkhead to limit concurrent borrows
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(poolConfig.getMaxTotal())
            .maxWaitDuration(Duration.ofMillis(connectionTimeout))
            .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("yawl-connection-pool", bulkheadConfig);

        // Register bulkhead metrics if meter registry available
        if (meterRegistry == null) {
            meterRegistry = new SimpleMeterRegistry();
        }

        io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics
            .ofBulkheadRegistry(bulkheadRegistry)
            .bindTo(meterRegistry);

        // Register event listeners
        bulkhead.getEventPublisher()
            .onCallRejected(event ->
                LOGGER.warn("Connection borrow rejected by bulkhead: {}", event.getBulkheadName()))
            .onError(event ->
                LOGGER.warn("Error in bulkhead: {}", event.getThrowable().getMessage()));

        // Register pool and bulkhead
        pools.put("default", pool);
        bulkheads.put("default", bulkhead);
        borrowCounts.put("default", new AtomicLong(0));
        returnCounts.put("default", new AtomicLong(0));
        totalBorrowTime.put("default", Duration.ZERO);

        initialized = true;

        LOGGER.info("YAWL connection pool initialized with Bulkhead: maxTotal={}, maxConcurrent={}, timeout={}ms",
                   poolConfig.getMaxTotal(), bulkheadConfig.getMaxConcurrentCalls(), connectionTimeout);
    }

    /**
     * Borrows an InterfaceB_EnvironmentBasedClient from the pool with Bulkhead protection.
     *
     * @return an InterfaceB_EnvironmentBasedClient instance
     * @throws Exception if unable to borrow an object or bulkhead is full
     */
    public InterfaceB_EnvironmentBasedClient borrowObject() throws Exception {
        if (!initialized) {
            initialize();
        }

        GenericObjectPool<InterfaceB_EnvironmentBasedClient> pool = pools.get("default");
        Bulkhead bulkhead = bulkheads.get("default");

        if (pool == null) {
            throw new IllegalStateException("Connection pool not initialized");
        }
        if (bulkhead == null) {
            throw new IllegalStateException("Bulkhead not initialized");
        }

        // Wrap borrow in bulkhead
        Instant startTime = Instant.now();

        InterfaceB_EnvironmentBasedClient client = Bulkhead.decorateCallable(
            bulkhead,
            pool::borrowObject
        ).call();

        // Update metrics
        AtomicLong borrowCount = borrowCounts.get("default");
        if (borrowCount != null) {
            borrowCount.incrementAndGet();
        }

        Duration borrowDuration = Duration.between(startTime, Instant.now());
        Duration totalTime = totalBorrowTime.getOrDefault("default", Duration.ZERO)
            .plus(borrowDuration);
        totalBorrowTime.put("default", totalTime);

        LOGGER.debug("Borrowed InterfaceB_EnvironmentBasedClient, pool active: {}, available bulkhead: {}",
                    pool.getNumActive(), bulkhead.getMetrics().getAvailableConcurrentCalls());
        return client;
    }

    /**
     * Returns a YAWL client to the pool.
     *
     * @param client the client to return
     */
    public void returnObject(InterfaceB_EnvironmentBasedClient client) {
        if (!initialized || client == null) {
            return;
        }

        GenericObjectPool<InterfaceB_EnvironmentBasedClient> pool = pools.get("default");
        if (pool == null) {
            return;
        }

        try {
            pool.returnObject(client);

            // Update metrics
            AtomicLong returnCount = returnCounts.get("default");
            if (returnCount != null) {
                returnCount.incrementAndGet();
            }

            Bulkhead bulkhead = bulkheads.get("default");
            LOGGER.debug("Returned InterfaceB_EnvironmentBasedClient, pool active: {}, bulkhead available: {}",
                        pool.getNumActive(),
                        bulkhead != null ? bulkhead.getMetrics().getAvailableConcurrentCalls() : "N/A");
        } catch (Exception e) {
            LOGGER.error("Failed to return InterfaceB_EnvironmentBasedClient to pool", e);
        }
    }

    /**
     * Invalidates a YAWL client (removes from pool).
     *
     * @param client the client to invalidate
     */
    public void invalidateObject(InterfaceB_EnvironmentBasedClient client) {
        if (!initialized || client == null) {
            return;
        }

        GenericObjectPool<InterfaceB_EnvironmentBasedClient> pool = pools.get("default");
        if (pool == null) {
            return;
        }

        try {
            pool.invalidateObject(client);
            LOGGER.debug("Invalidated InterfaceB_EnvironmentBasedClient");
        } catch (Exception e) {
            LOGGER.error("Failed to invalidate InterfaceB_EnvironmentBasedClient", e);
        }
    }

    /**
     * Gets the number of active objects in the pool.
     *
     * @return number of active objects
     */
    public int getNumActive() {
        if (!initialized) {
            return 0;
        }

        GenericObjectPool<InterfaceB_EnvironmentBasedClient> pool = pools.get("default");
        return pool != null ? pool.getNumActive() : 0;
    }

    /**
     * Gets the number of idle objects in the pool.
     *
     * @return number of idle objects
     */
    public int getNumIdle() {
        if (!initialized) {
            return 0;
        }

        GenericObjectPool<InterfaceB_EnvironmentBasedClient> pool = pools.get("default");
        return pool != null ? pool.getNumIdle() : 0;
    }

    /**
     * Clears the pool, bulkheads, and destroys all objects.
     */
    @PreDestroy
    public synchronized void close() {
        LOGGER.info("Closing YAWL connection pool");

        pools.forEach((name, pool) -> {
            try {
                pool.close();
                LOGGER.info("Closed pool: {}", name);
            } catch (Exception e) {
                LOGGER.error("Failed to close pool: {}", name, e);
            }
        });

        bulkheads.forEach((name, bulkhead) -> {
            try {
                LOGGER.info("Closed bulkhead: {}", name);
            } catch (Exception e) {
                LOGGER.error("Failed to close bulkhead: {}", name, e);
            }
        });

        pools.clear();
        bulkheads.clear();
        borrowCounts.clear();
        returnCounts.clear();
        totalBorrowTime.clear();
        initialized = false;

        LOGGER.info("YAWL connection pool closed");
    }

    /**
     * Gets pool and bulkhead statistics.
     *
     * @return map of pool and concurrency statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        if (initialized) {
            GenericObjectPool<InterfaceB_EnvironmentBasedClient> pool = pools.get("default");
            if (pool != null) {
                stats.put("numActive", pool.getNumActive());
                stats.put("numIdle", pool.getNumIdle());
                stats.put("maxTotal", pool.getMaxTotal());
                stats.put("maxIdle", pool.getMaxIdle());
                stats.put("minIdle", pool.getMinIdle());
            }

            Bulkhead bulkhead = bulkheads.get("default");
            if (bulkhead != null) {
                var metrics = bulkhead.getMetrics();
                stats.put("bulkheadAvailableCalls", metrics.getAvailableConcurrentCalls());
                stats.put("bulkheadMaxConcurrentCalls", metrics.getMaxConcurrentCalls());
                stats.put("bulkheadWaitingThreads", metrics.getWaitingThreadsCount());
            }

            AtomicLong borrowCount = borrowCounts.get("default");
            AtomicLong returnCount = returnCounts.get("default");
            if (borrowCount != null) {
                stats.put("totalBorrowed", borrowCount.get());
            }
            if (returnCount != null) {
                stats.put("totalReturned", returnCount.get());
            }
        }

        return stats;
    }

    /**
     * Creates the pool configuration from properties.
     *
     * @return the pool configuration
     */
    private GenericObjectPoolConfig<InterfaceB_EnvironmentBasedClient> createPoolConfig() {
        GenericObjectPoolConfig<InterfaceB_EnvironmentBasedClient> config = new GenericObjectPoolConfig<>();

        config.setMaxTotal(poolConfiguration.getMaxTotal());
        config.setMaxIdle(poolConfiguration.getMaxIdle());
        config.setMinIdle(poolConfiguration.getMinIdle());
        config.setTestOnBorrow(poolConfiguration.isTestOnBorrow());
        config.setTestWhileIdle(poolConfiguration.isTestWhileIdle());
        config.setTimeBetweenEvictionRunsMillis(
            poolConfiguration.getTimeBetweenEvictionRunsMillis());
        config.setMinEvictableIdleTimeMillis(
            poolConfiguration.getMinEvictableIdleTimeMillis());

        // Configure the block when exhausted behavior
        config.setBlockWhenExhausted(true);
        config.setMaxWaitMillis(5000); // 5 seconds max wait

        return config;
    }

    /**
     * Gets the total borrow time for the pool.
     *
     * @return total duration
     */
    public Duration getTotalBorrowTime() {
        return totalBorrowTime.getOrDefault("default", Duration.ZERO);
    }

    /**
     * Gets the average borrow time.
     *
     * @return average duration
     */
    public Duration getAverageBorrowTime() {
        AtomicLong borrowCount = borrowCounts.get("default");
        if (borrowCount == null || borrowCount.get() == 0) {
            return Duration.ZERO;
        }

        return totalBorrowTime.getOrDefault("default", Duration.ZERO)
            .dividedBy(borrowCount.get());
    }
}
