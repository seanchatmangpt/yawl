/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * Reusable TestContainers fixtures for YAWL integration tests.
 *
 * Provides lifecycle-managed Docker containers for:
 * - PostgreSQL (primary persistence backend)
 * - MySQL (secondary persistence backend)
 *
 * All containers are started with the YAWL workflow schema pre-applied via
 * the initDb helper. Tests use real JDBC connections — no mocks, no stubs.
 *
 * Usage pattern (Chicago TDD):
 * <pre>
 *   class MyTest {
 *       static final PostgreSQLContainer<?> postgres =
 *           YawlContainerFixtures.createPostgres();
 *
 *       static { postgres.start(); }
 *
 *       void testSomething() throws Exception {
 *           try (Connection c = YawlContainerFixtures.connectTo(postgres)) {
 *               YawlContainerFixtures.applyYawlSchema(c);
 *               // exercise real queries
 *           }
 *       }
 *   }
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
public final class YawlContainerFixtures {

    /** PostgreSQL image pinned for reproducible CI runs. */
    public static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:16.3-alpine");

    /** MySQL image pinned for reproducible CI runs. */
    public static final DockerImageName MYSQL_IMAGE =
            DockerImageName.parse("mysql:8.4.0");

    /** YAWL database name used in all containers. */
    public static final String YAWL_DB_NAME     = "yawl";
    public static final String YAWL_DB_USER     = "yawl";
    public static final String YAWL_DB_PASSWORD = "yawl_test_password";

    /** Maximum time to wait for a database container to become ready. */
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private YawlContainerFixtures() {
        throw new UnsupportedOperationException(
                "YawlContainerFixtures is a static utility class");
    }

    // =========================================================================
    // Container Factory Methods
    // =========================================================================

    /**
     * Creates a PostgreSQL container configured for YAWL integration tests.
     * The returned container is NOT started; the caller controls lifecycle.
     *
     * @return configured but not-yet-started PostgreSQLContainer
     */
    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> createPostgres() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(YAWL_DB_NAME)
                .withUsername(YAWL_DB_USER)
                .withPassword(YAWL_DB_PASSWORD)
                .withStartupTimeout(STARTUP_TIMEOUT)
                .waitingFor(Wait.forListeningPort());
    }

    /**
     * Creates a MySQL container configured for YAWL integration tests.
     * The returned container is NOT started; the caller controls lifecycle.
     *
     * @return configured but not-yet-started MySQLContainer
     */
    @SuppressWarnings("resource")
    public static MySQLContainer<?> createMySQL() {
        return new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName(YAWL_DB_NAME)
                .withUsername(YAWL_DB_USER)
                .withPassword(YAWL_DB_PASSWORD)
                .withStartupTimeout(STARTUP_TIMEOUT)
                .waitingFor(Wait.forListeningPort());
    }

    /**
     * Creates a generic container for Chaos-Engineering scenarios (Toxiproxy).
     * Toxiproxy is a configurable network-failure proxy used by chaos tests.
     *
     * @return configured but not-yet-started GenericContainer for Toxiproxy
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createToxiproxy() {
        return new GenericContainer<>(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.7.0"))
                .withExposedPorts(8474, 5432)
                .withStartupTimeout(STARTUP_TIMEOUT)
                .waitingFor(Wait.forHttp("/version").forPort(8474).withStartupTimeout(STARTUP_TIMEOUT));
    }

    // =========================================================================
    // Connection Helpers
    // =========================================================================

    /**
     * Opens a JDBC connection to any {@link JdbcDatabaseContainer}.
     *
     * @param container a running database container
     * @return open JDBC Connection (caller must close)
     * @throws SQLException if the connection cannot be established
     */
    public static Connection connectTo(JdbcDatabaseContainer<?> container) throws SQLException {
        return DriverManager.getConnection(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword());
    }

    // =========================================================================
    // Schema Bootstrap
    // =========================================================================

    /**
     * Applies the minimal YAWL workflow schema to an open JDBC connection.
     *
     * Creates the tables required by YAWL's persistence layer:
     * - yawl_specification  — loaded specification metadata
     * - yawl_net_runner     — active net-runner state
     * - yawl_work_item      — work-item rows with status lifecycle
     * - yawl_case_event     — audit log of case events
     *
     * The DDL uses ANSI SQL types supported by both PostgreSQL and MySQL.
     *
     * @param connection open JDBC connection (schema owner role required)
     * @throws SQLException if any DDL statement fails
     */
    public static void applyYawlSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS yawl_specification (
                    spec_id         VARCHAR(255) NOT NULL,
                    spec_version    VARCHAR(50)  NOT NULL,
                    spec_name       VARCHAR(255),
                    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (spec_id, spec_version)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS yawl_net_runner (
                    runner_id       VARCHAR(255) PRIMARY KEY,
                    spec_id         VARCHAR(255) NOT NULL,
                    spec_version    VARCHAR(50)  NOT NULL,
                    net_id          VARCHAR(255) NOT NULL,
                    state           VARCHAR(50)  NOT NULL,
                    started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (spec_id, spec_version)
                        REFERENCES yawl_specification(spec_id, spec_version)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS yawl_work_item (
                    item_id         VARCHAR(255) PRIMARY KEY,
                    runner_id       VARCHAR(255) NOT NULL,
                    task_id         VARCHAR(255) NOT NULL,
                    status          VARCHAR(50)  NOT NULL,
                    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    completed_at    TIMESTAMP,
                    FOREIGN KEY (runner_id) REFERENCES yawl_net_runner(runner_id)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS yawl_case_event (
                    event_id        BIGINT       NOT NULL,
                    runner_id       VARCHAR(255) NOT NULL,
                    event_type      VARCHAR(100) NOT NULL,
                    event_data      TEXT,
                    event_timestamp TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (event_id)
                )
                """);
        }
    }

    /**
     * Drops all YAWL schema objects in the correct dependency order.
     * Safe to call even if objects do not exist (IF EXISTS semantics).
     *
     * @param connection open JDBC connection
     * @throws SQLException if any DDL statement fails
     */
    public static void dropYawlSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS yawl_case_event");
            stmt.execute("DROP TABLE IF EXISTS yawl_work_item");
            stmt.execute("DROP TABLE IF EXISTS yawl_net_runner");
            stmt.execute("DROP TABLE IF EXISTS yawl_specification");
        }
    }
}
