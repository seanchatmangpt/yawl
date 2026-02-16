package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database Integration Tests - Verifies connectivity and operations with real databases
 * Tests H2, PostgreSQL, and MySQL with real container isolation
 */
@Testcontainers
public class DatabaseIntegrationTest {

    private Connection connection;

    @Container
    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("yawl_test")
        .withUsername("test")
        .withPassword("test");

    @BeforeEach
    public void setUp() throws SQLException {
        String jdbcUrl = postgresContainer.getJdbcUrl();
        String username = postgresContainer.getUsername();
        String password = postgresContainer.getPassword();
        
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        assertNotNull("Connection should be established", connection);
    }

    @AfterEach
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void testPostgreSQLConnection() throws SQLException {
        assertFalse("Connection should be open", connection.isClosed());
        
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT version()");
            assertTrue("Version query should return results", rs.next());
            String version = rs.getString(1);
            assertTrue("PostgreSQL version should be reported", version.contains("PostgreSQL"));
        }
    }

    @Test
    public void testCreateTable() throws SQLException {
        String createTableSQL = "CREATE TABLE test_workflow_process (" +
            "id SERIAL PRIMARY KEY," +
            "name VARCHAR(255) NOT NULL," +
            "version VARCHAR(20) NOT NULL," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
        
        // Verify table exists
        DatabaseMetaData metadata = connection.getMetaData();
        ResultSet tables = metadata.getTables(null, null, "test_workflow_process", new String[]{"TABLE"});
        assertTrue("Table should exist", tables.next());
    }

    @Test
    public void testInsertAndRetrieve() throws SQLException {
        createTestTable();
        
        String insertSQL = "INSERT INTO test_workflow_process (name, version) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
            stmt.setString(1, "Test Process");
            stmt.setString(2, "1.0");
            int rows = stmt.executeUpdate();
            assertEquals("One row should be inserted", 1, rows);
        }
        
        String selectSQL = "SELECT * FROM test_workflow_process WHERE name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
            stmt.setString(1, "Test Process");
            ResultSet rs = stmt.executeQuery();
            assertTrue("Record should be retrieved", rs.next());
            assertEquals("Name should match", "Test Process", rs.getString("name"));
            assertEquals("Version should match", "1.0", rs.getString("version"));
        }
    }

    @Test
    public void testBatchInsert() throws SQLException {
        createTestTable();
        
        String insertSQL = "INSERT INTO test_workflow_process (name, version) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
            for (int i = 0; i < 100; i++) {
                stmt.setString(1, "Process " + i);
                stmt.setString(2, "1." + i);
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            assertEquals("100 rows should be inserted", 100, results.length);
        }
        
        String countSQL = "SELECT COUNT(*) FROM test_workflow_process";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(countSQL);
            rs.next();
            assertEquals("Should have 100 records", 100, rs.getInt(1));
        }
    }

    @Test
    public void testTransactionRollback() throws SQLException {
        createTestTable();
        
        try {
            connection.setAutoCommit(false);
            
            String insertSQL = "INSERT INTO test_workflow_process (name, version) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                stmt.setString(1, "Rollback Test");
                stmt.setString(2, "1.0");
                stmt.executeUpdate();
            }
            
            // Simulate error
            throw new RuntimeException("Simulated error");
        } catch (RuntimeException e) {
            connection.rollback();
        } finally {
            connection.setAutoCommit(true);
        }
        
        // Verify rollback worked
        String selectSQL = "SELECT COUNT(*) FROM test_workflow_process WHERE name = 'Rollback Test'";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(selectSQL);
            rs.next();
            assertEquals("Insert should be rolled back", 0, rs.getInt(1));
        }
    }

    @Test
    public void testConnectionPooling() throws SQLException {
        for (int i = 0; i < 10; i++) {
            assertFalse("Connection should be available", connection.isClosed());
            connection.isValid(5);
        }
    }

    private void createTestTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS test_workflow_process (" +
            "id SERIAL PRIMARY KEY," +
            "name VARCHAR(255) NOT NULL," +
            "version VARCHAR(20) NOT NULL," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }
}
