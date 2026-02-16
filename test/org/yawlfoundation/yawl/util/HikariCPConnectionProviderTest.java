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

package org.yawlfoundation.yawl.util;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Test suite for HikariCP Connection Provider.
 * Tests connection pooling, concurrency, leak detection, and database operations.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 2026-02-15
 */
public class HikariCPConnectionProviderTest {

    private HikariCPConnectionProvider provider;
    private Map<String, Object> config;

    @Before
    public void setUp() {
        provider = new HikariCPConnectionProvider();
        config = new HashMap<>();

        config.put("hibernate.connection.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        config.put("hibernate.connection.username", "sa");
        config.put("hibernate.connection.password", "");
        config.put("hibernate.connection.driver_class", "org.h2.Driver");

        config.put("hibernate.hikari.maximumPoolSize", "10");
        config.put("hibernate.hikari.minimumIdle", "2");
        config.put("hibernate.hikari.connectionTimeout", "30000");
        config.put("hibernate.hikari.idleTimeout", "600000");
        config.put("hibernate.hikari.maxLifetime", "1800000");
    }

    @After
    public void tearDown() {
        if (provider != null) {
            provider.stop();
        }
    }

    @Test
    public void testProviderConfiguration() {
        provider.configure(config);

        HikariDataSource ds = provider.unwrap(HikariDataSource.class);
        assertNotNull("DataSource should be initialized", ds);
        assertEquals("Maximum pool size should be 10", 10, ds.getMaximumPoolSize());
        assertEquals("Minimum idle should be 2", 2, ds.getMinimumIdle());
        assertEquals("Connection timeout should be 30000", 30000, ds.getConnectionTimeout());
    }

    @Test
    public void testConnectionAcquisition() throws SQLException {
        provider.configure(config);

        Connection conn = provider.getConnection();
        assertNotNull("Connection should not be null", conn);
        assertFalse("Connection should not be closed", conn.isClosed());

        provider.closeConnection(conn);
        assertTrue("Connection should be closed", conn.isClosed());
    }

    @Test
    public void testBasicDatabaseOperations() throws SQLException {
        provider.configure(config);

        try (Connection conn = provider.getConnection()) {
            conn.createStatement().execute(
                "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50))"
            );

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO test_table (id, name) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "Test");
                int rows = ps.executeUpdate();
                assertEquals("Should insert 1 row", 1, rows);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM test_table WHERE id = ?")) {
                ps.setInt(1, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Should have results", rs.next());
                    assertEquals("Should retrieve correct name", "Test", rs.getString("name"));
                }
            }

            conn.createStatement().execute("DROP TABLE test_table");
        }
    }

    @Test
    public void testConcurrentConnections() throws Exception {
        provider.configure(config);

        int threadCount = 20;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try (Connection conn = provider.getConnection()) {
                            assertNotNull("Connection should not be null", conn);
                            try (PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
                                ResultSet rs = ps.executeQuery();
                                assertTrue("Should have result", rs.next());
                                assertEquals("Should return 1", 1, rs.getInt(1));
                            }
                        }
                    }
                } catch (SQLException e) {
                    fail("Should not throw SQLException: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads should complete", latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue("Executor should terminate", executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    public void testTransactionRollback() throws SQLException {
        provider.configure(config);

        try (Connection conn = provider.getConnection()) {
            conn.setAutoCommit(false);

            conn.createStatement().execute(
                "CREATE TABLE test_rollback (id INT PRIMARY KEY, value VARCHAR(50))"
            );

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO test_rollback (id, value) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "Before Rollback");
                ps.executeUpdate();
            }

            conn.rollback();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM test_rollback")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                assertEquals("Should have 0 rows after rollback", 0, rs.getInt(1));
            }

            conn.commit();
            conn.createStatement().execute("DROP TABLE test_rollback");
            conn.commit();
        }
    }

    @Test
    public void testPoolMetrics() throws SQLException {
        provider.configure(config);

        HikariDataSource ds = provider.unwrap(HikariDataSource.class);

        Connection conn1 = provider.getConnection();
        Connection conn2 = provider.getConnection();

        assertTrue("Should have active connections",
                ds.getHikariPoolMXBean().getActiveConnections() >= 2);

        provider.closeConnection(conn1);
        provider.closeConnection(conn2);

        assertTrue("Should have idle connections after closing",
                ds.getHikariPoolMXBean().getIdleConnections() > 0);
    }

    @Test
    public void testConnectionValidation() throws SQLException {
        config.put("hibernate.hikari.connectionTestQuery", "SELECT 1");
        provider.configure(config);

        try (Connection conn = provider.getConnection()) {
            assertTrue("Connection should be valid", conn.isValid(5));
        }
    }

    @Test
    public void testAutoCommitConfiguration() throws SQLException {
        config.put("hibernate.connection.autocommit", "false");
        provider.configure(config);

        try (Connection conn = provider.getConnection()) {
            assertFalse("Auto-commit should be false", conn.getAutoCommit());
        }

        provider.stop();

        config.put("hibernate.connection.autocommit", "true");
        provider = new HikariCPConnectionProvider();
        provider.configure(config);

        try (Connection conn = provider.getConnection()) {
            assertTrue("Auto-commit should be true", conn.getAutoCommit());
        }
    }

    @Test
    public void testIsolationLevel() throws SQLException {
        config.put("hibernate.connection.isolation", String.valueOf(Connection.TRANSACTION_READ_COMMITTED));
        provider.configure(config);

        try (Connection conn = provider.getConnection()) {
            assertEquals("Isolation level should be READ_COMMITTED",
                    Connection.TRANSACTION_READ_COMMITTED,
                    conn.getTransactionIsolation());
        }
    }

    @Test
    public void testUnwrap() {
        provider.configure(config);

        HikariCPConnectionProvider unwrapped = provider.unwrap(HikariCPConnectionProvider.class);
        assertNotNull("Should unwrap to HikariCPConnectionProvider", unwrapped);
        assertSame("Should be same instance", provider, unwrapped);

        HikariDataSource ds = provider.unwrap(HikariDataSource.class);
        assertNotNull("Should unwrap to HikariDataSource", ds);
    }

    @Test
    public void testIsUnwrappableAs() {
        provider.configure(config);

        assertTrue("Should be unwrappable as HikariCPConnectionProvider",
                provider.isUnwrappableAs(HikariCPConnectionProvider.class));
        assertTrue("Should be unwrappable as HikariDataSource",
                provider.isUnwrappableAs(HikariDataSource.class));
        assertFalse("Should not be unwrappable as String",
                provider.isUnwrappableAs(String.class));
    }

    @Test
    public void testSupportsAggressiveRelease() {
        provider.configure(config);
        assertFalse("Should not support aggressive release",
                provider.supportsAggressiveRelease());
    }

    @Test
    public void testStop() throws SQLException {
        provider.configure(config);

        Connection conn = provider.getConnection();
        assertNotNull("Should get connection before stop", conn);
        provider.closeConnection(conn);

        provider.stop();

        try {
            provider.getConnection();
            fail("Should throw exception after stop");
        } catch (SQLException e) {
            assertTrue("Exception message should mention initialization",
                    e.getMessage().contains("not initialized"));
        }
    }

    @Test(expected = SQLException.class)
    public void testConnectionBeforeConfiguration() throws SQLException {
        HikariCPConnectionProvider unconfiguredProvider = new HikariCPConnectionProvider();
        unconfiguredProvider.getConnection();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationWithoutUrl() {
        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("hibernate.connection.username", "sa");
        invalidConfig.put("hibernate.connection.password", "");

        provider.configure(invalidConfig);
    }

    @Test
    public void testConnectionRecycling() throws SQLException, InterruptedException {
        provider.configure(config);
        HikariDataSource ds = provider.unwrap(HikariDataSource.class);

        for (int i = 0; i < 100; i++) {
            try (Connection conn = provider.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
                    ResultSet rs = ps.executeQuery();
                    assertTrue(rs.next());
                }
            }
        }

        int totalConnections = ds.getHikariPoolMXBean().getTotalConnections();
        assertTrue("Total connections should be within pool limits",
                totalConnections <= ds.getMaximumPoolSize());
        assertTrue("Should have minimum idle connections",
                totalConnections >= ds.getMinimumIdle());
    }
}
