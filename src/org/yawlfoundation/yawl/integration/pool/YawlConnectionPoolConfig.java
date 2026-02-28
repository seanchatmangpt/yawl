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

import java.time.Duration;

/**
 * Configuration for YAWL connection pooling.
 *
 * <p>Provides all configurable parameters for the connection pool including
 * size limits, timeouts, validation settings, and health check intervals.</p>
 *
 * <h2>Configuration Example (application.yml)</h2>
 * <pre>{@code
 * yawl:
 *   pool:
 *     enabled: true
 *     max-total: 20
 *     max-idle: 10
 *     min-idle: 2
 *     max-wait-ms: 5000
 *     validation-on-borrow: true
 *     validation-on-return: false
 *     validation-while-idle: true
 *     time-between-eviction-runs-ms: 60000
 *     min-evictable-idle-time-ms: 300000
 *     test-while-idle: true
 *     health-check-interval-ms: 30000
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class YawlConnectionPoolConfig {

    // Pool sizing
    private int maxTotal = 20;
    private int maxIdle = 10;
    private int minIdle = 2;

    // Timeouts
    private long maxWaitMs = 5000;
    private long connectionTimeoutMs = 10000;

    // Validation settings
    private boolean validationOnBorrow = true;
    private boolean validationOnReturn = false;
    private boolean validationWhileIdle = true;
    private boolean testOnCreate = false;

    // Eviction settings
    private long timeBetweenEvictionRunsMs = 60000;
    private long minEvictableIdleTimeMs = 300000;
    private long softMinEvictableIdleTimeMs = 180000;
    private int numTestsPerEvictionRun = 3;

    // Health check
    private long healthCheckIntervalMs = 30000;
    private int healthCheckRetryAttempts = 3;
    private long healthCheckRetryDelayMs = 1000;

    // Connection settings
    private String engineUrl;
    private String username = "admin";
    private String password;
    private int connectionRetryAttempts = 3;
    private long connectionRetryDelayMs = 1000;

    // Lifecycle
    private boolean lifo = true;
    private boolean fairness = false;
    private boolean blockWhenExhausted = true;

    /**
     * Get the maximum number of active connections.
     * Default: 20
     *
     * @return max total connections
     */
    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    /**
     * Get the maximum number of idle connections.
     * Default: 10
     *
     * @return max idle connections
     */
    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    /**
     * Get the minimum number of idle connections to maintain.
     * Default: 2
     *
     * @return min idle connections
     */
    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    /**
     * Get the maximum time to wait for a connection (milliseconds).
     * Default: 5000ms
     *
     * @return max wait time in ms
     */
    public long getMaxWaitMs() {
        return maxWaitMs;
    }

    public void setMaxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
    }

    /**
     * Get the connection timeout (milliseconds).
     * Default: 10000ms
     *
     * @return connection timeout in ms
     */
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    /**
     * Check if connections should be validated when borrowed.
     * Default: true
     *
     * @return true if validation on borrow
     */
    public boolean isValidationOnBorrow() {
        return validationOnBorrow;
    }

    public void setValidationOnBorrow(boolean validationOnBorrow) {
        this.validationOnBorrow = validationOnBorrow;
    }

    /**
     * Check if connections should be validated when returned.
     * Default: false
     *
     * @return true if validation on return
     */
    public boolean isValidationOnReturn() {
        return validationOnReturn;
    }

    public void setValidationOnReturn(boolean validationOnReturn) {
        this.validationOnReturn = validationOnReturn;
    }

    /**
     * Check if idle connections should be validated.
     * Default: true
     *
     * @return true if validation while idle
     */
    public boolean isValidationWhileIdle() {
        return validationWhileIdle;
    }

    public void setValidationWhileIdle(boolean validationWhileIdle) {
        this.validationWhileIdle = validationWhileIdle;
    }

    /**
     * Check if connections should be validated on creation.
     * Default: false
     *
     * @return true if test on create
     */
    public boolean isTestOnCreate() {
        return testOnCreate;
    }

    public void setTestOnCreate(boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
    }

    /**
     * Get the time between eviction runs (milliseconds).
     * Default: 60000ms (1 minute)
     *
     * @return time between eviction runs in ms
     */
    public long getTimeBetweenEvictionRunsMs() {
        return timeBetweenEvictionRunsMs;
    }

    public void setTimeBetweenEvictionRunsMs(long timeBetweenEvictionRunsMs) {
        this.timeBetweenEvictionRunsMs = timeBetweenEvictionRunsMs;
    }

    /**
     * Get the minimum idle time before eviction (milliseconds).
     * Default: 300000ms (5 minutes)
     *
     * @return min evictable idle time in ms
     */
    public long getMinEvictableIdleTimeMs() {
        return minEvictableIdleTimeMs;
    }

    public void setMinEvictableIdleTimeMs(long minEvictableIdleTimeMs) {
        this.minEvictableIdleTimeMs = minEvictableIdleTimeMs;
    }

    /**
     * Get the soft minimum idle time before eviction (milliseconds).
     * Connections above minIdle are evicted after this time.
     * Default: 180000ms (3 minutes)
     *
     * @return soft min evictable idle time in ms
     */
    public long getSoftMinEvictableIdleTimeMs() {
        return softMinEvictableIdleTimeMs;
    }

    public void setSoftMinEvictableIdleTimeMs(long softMinEvictableIdleTimeMs) {
        this.softMinEvictableIdleTimeMs = softMinEvictableIdleTimeMs;
    }

    /**
     * Get the number of connections to test per eviction run.
     * Default: 3
     *
     * @return number of tests per eviction run
     */
    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Get the health check interval (milliseconds).
     * Default: 30000ms (30 seconds)
     *
     * @return health check interval in ms
     */
    public long getHealthCheckIntervalMs() {
        return healthCheckIntervalMs;
    }

    public void setHealthCheckIntervalMs(long healthCheckIntervalMs) {
        this.healthCheckIntervalMs = healthCheckIntervalMs;
    }

    /**
     * Get the number of health check retry attempts.
     * Default: 3
     *
     * @return health check retry attempts
     */
    public int getHealthCheckRetryAttempts() {
        return healthCheckRetryAttempts;
    }

    public void setHealthCheckRetryAttempts(int healthCheckRetryAttempts) {
        this.healthCheckRetryAttempts = healthCheckRetryAttempts;
    }

    /**
     * Get the delay between health check retries (milliseconds).
     * Default: 1000ms
     *
     * @return health check retry delay in ms
     */
    public long getHealthCheckRetryDelayMs() {
        return healthCheckRetryDelayMs;
    }

    public void setHealthCheckRetryDelayMs(long healthCheckRetryDelayMs) {
        this.healthCheckRetryDelayMs = healthCheckRetryDelayMs;
    }

    /**
     * Get the YAWL engine URL.
     *
     * @return engine URL
     */
    public String getEngineUrl() {
        return engineUrl;
    }

    public void setEngineUrl(String engineUrl) {
        this.engineUrl = engineUrl;
    }

    /**
     * Get the YAWL username.
     * Default: "admin"
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Get the YAWL password.
     *
     * @return password
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Get the number of connection retry attempts.
     * Default: 3
     *
     * @return connection retry attempts
     */
    public int getConnectionRetryAttempts() {
        return connectionRetryAttempts;
    }

    public void setConnectionRetryAttempts(int connectionRetryAttempts) {
        this.connectionRetryAttempts = connectionRetryAttempts;
    }

    /**
     * Get the delay between connection retries (milliseconds).
     * Default: 1000ms
     *
     * @return connection retry delay in ms
     */
    public long getConnectionRetryDelayMs() {
        return connectionRetryDelayMs;
    }

    public void setConnectionRetryDelayMs(long connectionRetryDelayMs) {
        this.connectionRetryDelayMs = connectionRetryDelayMs;
    }

    /**
     * Check if pool uses LIFO (last-in-first-out) order.
     * Default: true
     *
     * @return true if LIFO
     */
    public boolean isLifo() {
        return lifo;
    }

    public void setLifo(boolean lifo) {
        this.lifo = lifo;
    }

    /**
     * Check if pool uses fairness for waiting threads.
     * Default: false
     *
     * @return true if fairness enabled
     */
    public boolean isFairness() {
        return fairness;
    }

    public void setFairness(boolean fairness) {
        this.fairness = fairness;
    }

    /**
     * Check if pool blocks when exhausted.
     * Default: true
     *
     * @return true if blocks when exhausted
     */
    public boolean isBlockWhenExhausted() {
        return blockWhenExhausted;
    }

    public void setBlockWhenExhausted(boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
    }

    /**
     * Create a copy of this configuration.
     *
     * @return a new YawlConnectionPoolConfig with same values
     */
    public YawlConnectionPoolConfig copy() {
        YawlConnectionPoolConfig copy = new YawlConnectionPoolConfig();
        copy.setMaxTotal(this.maxTotal);
        copy.setMaxIdle(this.maxIdle);
        copy.setMinIdle(this.minIdle);
        copy.setMaxWaitMs(this.maxWaitMs);
        copy.setConnectionTimeoutMs(this.connectionTimeoutMs);
        copy.setValidationOnBorrow(this.validationOnBorrow);
        copy.setValidationOnReturn(this.validationOnReturn);
        copy.setValidationWhileIdle(this.validationWhileIdle);
        copy.setTestOnCreate(this.testOnCreate);
        copy.setTimeBetweenEvictionRunsMs(this.timeBetweenEvictionRunsMs);
        copy.setMinEvictableIdleTimeMs(this.minEvictableIdleTimeMs);
        copy.setSoftMinEvictableIdleTimeMs(this.softMinEvictableIdleTimeMs);
        copy.setNumTestsPerEvictionRun(this.numTestsPerEvictionRun);
        copy.setHealthCheckIntervalMs(this.healthCheckIntervalMs);
        copy.setHealthCheckRetryAttempts(this.healthCheckRetryAttempts);
        copy.setHealthCheckRetryDelayMs(this.healthCheckRetryDelayMs);
        copy.setEngineUrl(this.engineUrl);
        copy.setUsername(this.username);
        copy.setPassword(this.password);
        copy.setConnectionRetryAttempts(this.connectionRetryAttempts);
        copy.setConnectionRetryDelayMs(this.connectionRetryDelayMs);
        copy.setLifo(this.lifo);
        copy.setFairness(this.fairness);
        copy.setBlockWhenExhausted(this.blockWhenExhausted);
        return copy;
    }

    /**
     * Create default configuration.
     *
     * @return default configuration instance
     */
    public static YawlConnectionPoolConfig defaults() {
        return new YawlConnectionPoolConfig();
    }

    /**
     * Create configuration for development (smaller pool).
     *
     * @return development configuration
     */
    public static YawlConnectionPoolConfig development() {
        YawlConnectionPoolConfig config = new YawlConnectionPoolConfig();
        config.setMaxTotal(5);
        config.setMaxIdle(3);
        config.setMinIdle(1);
        config.setMaxWaitMs(3000);
        config.setHealthCheckIntervalMs(60000);
        return config;
    }

    /**
     * Create configuration for production (larger pool with stricter validation).
     *
     * @return production configuration
     */
    public static YawlConnectionPoolConfig production() {
        YawlConnectionPoolConfig config = new YawlConnectionPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(20);
        config.setMinIdle(5);
        config.setMaxWaitMs(10000);
        config.setValidationOnBorrow(true);
        config.setValidationWhileIdle(true);
        config.setTimeBetweenEvictionRunsMs(30000);
        config.setMinEvictableIdleTimeMs(180000);
        config.setHealthCheckIntervalMs(15000);
        return config;
    }

    @Override
    public String toString() {
        return "YawlConnectionPoolConfig{" +
                "maxTotal=" + maxTotal +
                ", maxIdle=" + maxIdle +
                ", minIdle=" + minIdle +
                ", maxWaitMs=" + maxWaitMs +
                ", validationOnBorrow=" + validationOnBorrow +
                ", engineUrl='" + engineUrl + '\'' +
                '}';
    }

    /**
     * Creates a {@link ConnectionPoolGuard} sized to this configuration's {@code maxTotal}.
     *
     * <p>The guard enforces a 100ms fail-fast timeout so callers never block
     * indefinitely when the pool is exhausted.</p>
     *
     * @return a new {@link ConnectionPoolGuard} for this pool configuration
     */
    public ConnectionPoolGuard createGuard() {
        return ConnectionPoolGuard.of(this.maxTotal);
    }
}
