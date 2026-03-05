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

/**
 * Connection pooling for YAWL engine calls.
 *
 * <p>This package provides a thread-safe connection pool for YAWL engine
 * sessions using Apache Commons Pool2. The pool improves performance and
 * reliability by reusing connections, validating session health, and
 * automatically reconnecting on session expiry.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.pool.YawlConnectionPool} -
 *       Main pool implementation with borrow/return semantics</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.pool.YawlSession} -
 *       Wrapper for pooled YAWL connections</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.pool.YawlConnectionPoolConfig} -
 *       Configuration for pool sizing, timeouts, and validation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.pool.YawlConnectionPoolMetrics} -
 *       Metrics collection for monitoring pool usage</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create and configure pool
 * YawlConnectionPoolConfig config = new YawlConnectionPoolConfig();
 * config.setEngineUrl("http://localhost:8080/yawl");
 * config.setUsername("admin");
 * config.setPassword(System.getenv("YAWL_PASSWORD"));
 * config.setMaxTotal(20);
 *
 * YawlConnectionPool pool = new YawlConnectionPool(config);
 * pool.initialize();
 *
 * // Use connections (try-with-resources ensures return to pool)
 * try (YawlSession session = pool.borrowSession()) {
 *     String caseId = session.getClient()
 *         .launchCase(specId, params, session.getHandle());
 * }
 *
 * // Monitor pool health
 * YawlConnectionPoolMetrics metrics = pool.getMetrics();
 * System.out.println("Active: " + metrics.getActiveConnections());
 * System.out.println("Idle: " + metrics.getIdleConnections());
 *
 * // Graceful shutdown
 * pool.shutdown();
 * }</pre>
 *
 * <h2>Configuration Properties</h2>
 * <p>Configure the pool in application.yml:</p>
 * <pre>{@code
 * yawl:
 *   pool:
 *     enabled: true
 *     engine-url: http://localhost:8080/yawl
 *     username: ${YAWL_USERNAME:admin}
 *     password: ${YAWL_PASSWORD}
 *     max-total: 20
 *     max-idle: 10
 *     min-idle: 2
 *     max-wait-ms: 5000
 *     validation-on-borrow: true
 *     validation-while-idle: true
 *     health-check-interval-ms: 30000
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>The pool is thread-safe for concurrent borrow/return operations.
 * Each thread should borrow its own session. Sessions are NOT thread-safe
 * and should not be shared between threads.</p>
 *
 * <h2>Connection Lifecycle</h2>
 * <ol>
 *   <li>Borrow: Get session from pool (creates new if needed)</li>
 *   <li>Use: Call YAWL APIs via session.getClient() and session.getHandle()</li>
 *   <li>Return: Return session to pool (try-with-resources recommended)</li>
 *   <li>Eviction: Idle sessions evicted after minEvictableIdleTimeMs</li>
 *   <li>Validation: Sessions validated on borrow and periodically</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @see org.yawlfoundation.yawl.integration.pool.YawlConnectionPool
 */
package org.yawlfoundation.yawl.integration.pool;
