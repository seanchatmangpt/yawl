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

package org.yawlfoundation.yawl.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.StructuredTaskScope;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.yawlfoundation.yawl.integration.TestConstants.*;

/**
 * Base class for YAWL integration tests with embedded H2 database.
 *
 * <p>Provides common test infrastructure including:</p>
 * <ul>
 *   <li>Embedded H2 database with PostgreSQL compatibility mode</li>
 *   <li>Virtual thread support for concurrent tests</li>
 *   <li>Scoped values for test context propagation</li>
 *   <li>Common assertions and utilities</li>
 *   <li>Resource cleanup and lifecycle management</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * class MyIntegrationTest extends IntegrationTestBase {
 *     @Test
 *     void testWorkflow() throws Exception {
 *         // Database is automatically available via getDataSource()
 *         try (Connection conn = getDataSource().getConnection()) {
 *             // ... test logic
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class IntegrationTestBase {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    protected final ObjectMapper objectMapper = new ObjectMapper();

    private static final AtomicInteger testCounter = new AtomicInteger(0);

    private HikariDataSource dataSource;
    private ExecutorService virtualThreadExecutor;
    private Instant testStartTime;

    /**
     * Scoped value for propagating test context across virtual threads.
     */
    public static final ScopedValue<TestContext> TEST_CONTEXT = ScopedValue.newInstance();

    /**
     * Sealed interface for test result outcomes.
     */
    public sealed interface TestResult permits TestResult.Success, TestResult.Failure, TestResult.Skipped {
        record Success(Duration duration, String message) implements TestResult {}
        record Failure(Duration duration, Throwable error) implements TestResult {}
        record Skipped(Duration duration, String reason) implements TestResult {}
    }

    /**
     * Test context record for scoped value propagation.
     *
     * @param testId unique test identifier
     * @param testName name of the test
     * @param startTime test start time
     * @param testCaseId test case identifier (for correlation)
     */
    public record TestContext(
        String testId,
        String testName,
        Instant startTime,
        String testCaseId
    ) {
        /**
         * Creates a test context for the current test.
         *
         * @param testName name of the test
         * @return new test context
         */
        public static TestContext create(String testName) {
            return new TestContext(
                "test-" + System.currentTimeMillis() + "-" + testCounter.incrementAndGet(),
                testName,
                Instant.now(),
                TestDataGenerator.generateCaseId()
            );
        }
    }

    // ============ Lifecycle Methods ============

    /**
     * Initializes the test environment before all tests.
     *
     * @throws Exception if initialization fails
     */
    @BeforeAll
    protected void setUpBase() throws Exception {
        testStartTime = Instant.now();
        logger.info(() -> "Starting test suite: " + getClass().getSimpleName());

        // Initialize H2 database
        dataSource = createDataSource();
        initializeDatabase();

        // Create virtual thread executor for concurrent tests
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Allow subclasses to perform additional setup
        onSetUp();
    }

    /**
     * Cleans up the test environment after all tests.
     *
     * @throws Exception if cleanup fails
     */
    @AfterAll
    protected void tearDownBase() throws Exception {
        // Allow subclasses to perform additional cleanup
        onTearDown();

        // Shutdown virtual thread executor
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.shutdown();
            if (!virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        }

        // Close data source
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        Duration totalDuration = Duration.between(testStartTime, Instant.now());
        logger.info(() -> "Completed test suite: " + getClass().getSimpleName() +
                         " in " + totalDuration.toMillis() + "ms");
    }

    /**
     * Hook for subclass setup before each test.
     *
     * @throws Exception if setup fails
     */
    @BeforeEach
    protected void setUpEach() throws Exception {
        // Subclasses can override
    }

    /**
     * Hook for subclass cleanup after each test.
     *
     * @throws Exception if cleanup fails
     */
    @AfterEach
    protected void tearDownEach() throws Exception {
        // Subclasses can override
    }

    // ============ Abstract Hooks ============

    /**
     * Hook for additional setup in subclasses.
     *
     * @throws Exception if setup fails
     */
    protected void onSetUp() throws Exception {
        // Subclasses can override for additional setup
    }

    /**
     * Hook for additional cleanup in subclasses.
     *
     * @throws Exception if cleanup fails
     */
    protected void onTearDown() throws Exception {
        // Subclasses can override for additional cleanup
    }

    /**
     * Returns the SQL statements to initialize the database schema.
     * Subclasses should override to provide custom schema.
     *
     * @return list of SQL statements
     */
    protected List<String> getSchemaStatements() {
        return List.of(
            // Default workflow events table
            """
            CREATE TABLE IF NOT EXISTS workflow_events (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                event_id VARCHAR(36) NOT NULL UNIQUE,
                spec_id VARCHAR(255) NOT NULL,
                case_id VARCHAR(255) NOT NULL,
                seq_num BIGINT NOT NULL,
                event_type VARCHAR(64) NOT NULL,
                event_timestamp TIMESTAMP(6) NOT NULL,
                schema_version VARCHAR(16) NOT NULL DEFAULT '1.0',
                payload_json TEXT NOT NULL,
                created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY ux_case_seq (case_id, seq_num)
            )
            """,
            // Work items table
            """
            CREATE TABLE IF NOT EXISTS work_items (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                work_item_id VARCHAR(64) NOT NULL UNIQUE,
                case_id VARCHAR(255) NOT NULL,
                task_id VARCHAR(128) NOT NULL,
                status VARCHAR(32) NOT NULL,
                priority VARCHAR(16) DEFAULT 'normal',
                created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
                data_json TEXT,
                INDEX idx_case_id (case_id),
                INDEX idx_status (status)
            )
            """,
            // Agent registrations table
            """
            CREATE TABLE IF NOT EXISTS agent_registrations (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                agent_id VARCHAR(128) NOT NULL UNIQUE,
                agent_name VARCHAR(255) NOT NULL,
                capabilities TEXT,
                protocol_version VARCHAR(32),
                registered_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
                last_heartbeat TIMESTAMP(6),
                status VARCHAR(32) DEFAULT 'active',
                INDEX idx_status (status)
            )
            """
        );
    }

    // ============ Database Access ============

    /**
     * Gets the data source for database operations.
     *
     * @return HikariCP data source
     */
    protected DataSource getDataSource() {
        assertNotNull(dataSource, "DataSource not initialized");
        return dataSource;
    }

    /**
     * Gets a new database connection from the pool.
     *
     * @return database connection
     * @throws SQLException if connection fails
     */
    protected Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Executes SQL statements in a transaction.
     *
     * @param statements SQL statements to execute
     * @throws SQLException if execution fails
     */
    protected void executeInTransaction(String... statements) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    stmt.execute(sql);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ============ Virtual Thread Support ============

    /**
     * Runs a task in a virtual thread and returns the result.
     *
     * @param task task to run
     * @param <T> result type
     * @return future containing the result
     */
    protected <T> CompletableFuture<T> runAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, virtualThreadExecutor);
    }

    /**
     * Runs multiple tasks concurrently using structured concurrency.
     *
     * @param tasks tasks to run
     * @return list of results
     * @throws Exception if any task fails
     */
    protected <T> List<T> runConcurrent(List<Callable<T>> tasks) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();
            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(task));
            }
            scope.join();
            scope.throwIfFailed();
            return subtasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .toList();
        }
    }

    /**
     * Runs a task with scoped test context.
     *
     * @param context test context
     * @param task task to run
     * @param <T> result type
     * @return task result
     * @throws Exception if task fails
     */
    protected <T> T runWithContext(TestContext context, Callable<T> task) throws Exception {
        return ScopedValue.where(TEST_CONTEXT, context).call(task::call);
    }

    // ============ Assertions ============

    /**
     * Asserts that an operation completes within the specified timeout.
     *
     * @param timeout maximum duration
     * @param operation operation to time
     */
    protected void assertCompletesWithin(Duration timeout, Executable operation) {
        assertTimeoutPreemptively(timeout, operation,
            () -> "Operation did not complete within " + timeout.toMillis() + "ms");
    }

    /**
     * Asserts that a condition becomes true within the specified timeout.
     *
     * @param condition condition supplier
     * @param timeout maximum wait time
     */
    protected void assertEventuallyTrue(java.util.function.Supplier<Boolean> condition, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(POLLING_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for condition");
            }
        }
        fail("Condition did not become true within " + timeout.toMillis() + "ms");
    }

    /**
     * Asserts that a JSON string is valid.
     *
     * @param json JSON string to validate
     */
    protected void assertValidJson(String json) {
        assertDoesNotThrow(() -> objectMapper.readTree(json),
            "String is not valid JSON: " + (json != null ? json.substring(0, Math.min(100, json.length())) : "null"));
    }

    /**
     * Asserts that a map contains all required keys.
     *
     * @param map map to check
     * @param keys required keys
     */
    protected void assertContainsKeys(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            assertTrue(map.containsKey(key), "Map should contain key: " + key);
        }
    }

    // ============ Utility Methods ============

    /**
     * Creates a unique test identifier.
     *
     * @return unique identifier
     */
    protected String uniqueTestId() {
        return getClass().getSimpleName() + "-" + testCounter.incrementAndGet();
    }

    /**
     * Gets the current test context if available.
     *
     * @return current test context or null
     */
    protected TestContext currentContext() {
        return TEST_CONTEXT.orElse(null);
    }

    /**
     * Logs a test message with timestamp.
     *
     * @param message message to log
     */
    protected void logTest(String message) {
        logger.info(() -> "[" + Instant.now() + "] " + message);
    }

    // ============ Private Methods ============

    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(H2_MEM_URL);
        config.setDriverClassName(H2_DRIVER);
        config.setUsername(H2_USERNAME);
        config.setPassword(H2_PASSWORD);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(DEFAULT_DB_TIMEOUT.toMillis());
        config.setPoolName("yawl-test-pool-" + getClass().getSimpleName());
        return new HikariDataSource(config);
    }

    private void initializeDatabase() throws SQLException {
        List<String> statements = getSchemaStatements();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
        logger.info(() -> "Database initialized with " + statements.size() + " schema statements");
    }
}
