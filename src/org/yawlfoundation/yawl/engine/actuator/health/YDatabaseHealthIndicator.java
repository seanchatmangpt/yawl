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

package org.yawlfoundation.yawl.engine.actuator.health;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.engine.YPersistenceManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for YAWL database connectivity and performance.
 *
 * This indicator reports on:
 * - Database connection availability
 * - Query execution time
 * - Connection pool statistics
 * - Database version and type
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component
public class YDatabaseHealthIndicator implements HealthIndicator {

    private static final Logger _logger = LogManager.getLogger(YDatabaseHealthIndicator.class);

    private static final long QUERY_TIMEOUT_MS = 5000;
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000;

    private final YPersistenceManager persistenceManager;

    public YDatabaseHealthIndicator() {
        this.persistenceManager = YPersistenceManager.getInstance();
    }

    @Override
    public Health health() {
        try {
            if (!persistenceManager.isPersisting()) {
                return Health.up()
                    .withDetail("persistence", "disabled")
                    .withDetail("reason", "Database persistence is not enabled")
                    .build();
            }

            SessionFactory sessionFactory = persistenceManager.getSessionFactory();
            if (sessionFactory == null || sessionFactory.isClosed()) {
                return Health.down()
                    .withDetail("connection", "unavailable")
                    .withDetail("reason", "Session factory is not available")
                    .build();
            }

            Map<String, Object> details = new HashMap<>();
            details.put("persistence", "enabled");

            long startTime = System.currentTimeMillis();
            boolean querySuccess = executeHealthCheck(sessionFactory, details);
            long queryTime = System.currentTimeMillis() - startTime;

            details.put("queryTime", queryTime + "ms");

            if (!querySuccess) {
                return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "Health check query failed")
                    .build();
            }

            if (queryTime > QUERY_TIMEOUT_MS) {
                return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "Query timeout exceeded")
                    .build();
            }

            Health.Builder healthBuilder = Health.up().withDetails(details);

            if (queryTime > SLOW_QUERY_THRESHOLD_MS) {
                healthBuilder.status("WARNING")
                    .withDetail("warning", "Database response time is slow");
            }

            addConnectionPoolStats(sessionFactory, healthBuilder);

            return healthBuilder.build();

        } catch (Exception e) {
            _logger.error("Error checking database health", e);
            return Health.down()
                .withDetail("error", e.getClass().getName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }

    private boolean executeHealthCheck(SessionFactory sessionFactory, Map<String, Object> details) {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            Query<?> query = session.createNativeQuery("SELECT 1");
            query.setTimeout((int) (QUERY_TIMEOUT_MS / 1000));
            Object result = query.uniqueResult();

            details.put("connection", "available");
            details.put("queryResult", result != null ? "success" : "no_result");

            addDatabaseInfo(session, details);

            return result != null;

        } catch (Exception e) {
            _logger.error("Database health check query failed", e);
            details.put("connection", "failed");
            details.put("error", e.getMessage());
            return false;
        } finally {
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                } catch (Exception e) {
                    _logger.warn("Failed to close health check session", e);
                }
            }
        }
    }

    private void addDatabaseInfo(Session session, Map<String, Object> details) {
        try {
            String dbProductName = session.doReturningWork(connection ->
                connection.getMetaData().getDatabaseProductName()
            );
            String dbProductVersion = session.doReturningWork(connection ->
                connection.getMetaData().getDatabaseProductVersion()
            );

            details.put("database", dbProductName);
            details.put("version", dbProductVersion);

        } catch (Exception e) {
            _logger.debug("Could not retrieve database metadata", e);
        }
    }

    private void addConnectionPoolStats(SessionFactory sessionFactory, Health.Builder healthBuilder) {
        try {
            Map<String, Object> statistics = persistenceManager.getStatistics();
            if (statistics != null && !statistics.isEmpty()) {
                healthBuilder.withDetail("connectionPool", statistics);
            }
        } catch (Exception e) {
            _logger.debug("Could not retrieve connection pool statistics", e);
        }
    }
}
