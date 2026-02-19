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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for YAWL connection pooling.
 *
 * <p>This class provides configuration properties for the connection pool
 * used to manage YAWL InterfaceB client connections. It defines sensible
 * defaults that can be overridden via application.yml.</p>
 *
 * <h2>Default Configuration</h2>
 * <ul>
 *   <li><strong>maxTotal</strong>: 20 - Maximum number of active connections</li>
 *   <li><strong>maxIdle</strong>: 10 - Maximum number of idle connections</li>
 *   <li><strong>minIdle</strong>: 5 - Minimum number of idle connections</li>
 *   <li><strong>testOnBorrow</strong>: true - Validate connections when borrowed</li>
 *   <li><strong>testWhileIdle</strong>: true - Validate idle connections</li>
 *   <li><strong>timeBetweenEvictionRunsMillis</strong>: 30000 - Eviction check interval</li>
 *   <li><strong>minEvictableIdleTimeMillis</strong>: 600000 - Min idle time before eviction</li>
 * </ul>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * yawl:
 *   connection-pool:
 *     max-total: 30
 *     max-idle: 15
 *     min-idle: 8
 *     test-on-borrow: true
 *     test-while-idle: true
 *     time-between-eviction-runs-millis: 30000
 *     min-evictable-idle-time-millis: 600000
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "yawl.connection-pool")
public class PoolConfiguration {

    /**
     * Default configuration values.
     */
    public static final int DEFAULT_MAX_TOTAL = 20;
    public static final int DEFAULT_MAX_IDLE = 10;
    public static final int DEFAULT_MIN_IDLE = 5;
    public static final boolean DEFAULT_TEST_ON_BORROW = true;
    public static final boolean DEFAULT_TEST_WHILE_IDLE = true;
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = 30000L;
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 600000L;

    /**
     * Maximum number of connections that can be allocated from the pool.
     */
    private int maxTotal = DEFAULT_MAX_TOTAL;

    /**
     * Maximum number of idle connections that can be held in the pool.
     */
    private int maxIdle = DEFAULT_MAX_IDLE;

    /**
     * Minimum number of idle connections that should be maintained.
     */
    private int minIdle = DEFAULT_MIN_IDLE;

    /**
     * Whether connections should be validated when borrowed from the pool.
     */
    private boolean testOnBorrow = DEFAULT_TEST_ON_BORROW;

    /**
     * Whether connections should be validated while idle.
     */
    private boolean testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    /**
     * The time in milliseconds between eviction runs.
     */
    private long timeBetweenEvictionRunsMillis = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * The minimum amount of time an object may sit idle in the pool before it
     * is eligible for eviction.
     */
    private long minEvictableIdleTimeMillis = DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * Gets the maximum number of connections.
     *
     * @return max total connections
     */
    public int getMaxTotal() {
        return maxTotal;
    }

    /**
     * Sets the maximum number of connections.
     *
     * @param maxTotal maximum connections
     */
    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    /**
     * Gets the maximum number of idle connections.
     *
     * @return max idle connections
     */
    public int getMaxIdle() {
        return maxIdle;
    }

    /**
     * Sets the maximum number of idle connections.
     *
     * @param maxIdle max idle connections
     */
    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    /**
     * Gets the minimum number of idle connections.
     *
     * @return min idle connections
     */
    public int getMinIdle() {
        return minIdle;
    }

    /**
     * Sets the minimum number of idle connections.
     *
     * @param minIdle min idle connections
     */
    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    /**
     * Gets whether connections are validated when borrowed.
     *
     * @return test on borrow flag
     */
    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    /**
     * Sets whether connections are validated when borrowed.
     *
     * @param testOnBorrow test on borrow flag
     */
    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    /**
     * Gets whether connections are validated while idle.
     *
     * @return test while idle flag
     */
    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    /**
     * Sets whether connections are validated while idle.
     *
     * @param testWhileIdle test while idle flag
     */
    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    /**
     * Gets the time between eviction runs.
     *
     * @return time in milliseconds
     */
    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the time between eviction runs.
     *
     * @param timeBetweenEvictionRunsMillis time in milliseconds
     */
    public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    /**
     * Gets the minimum evictable idle time.
     *
     * @return time in milliseconds
     */
    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum evictable idle time.
     *
     * @param minEvictableIdleTimeMillis time in milliseconds
     */
    public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }
}