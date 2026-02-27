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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.performance.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database Connection Pool Efficiency Benchmark
 * 
 * Comprehensive benchmark for database connection pooling efficiency
 * and query performance in YAWL workflow engine.
 * 
 * Performance Targets:
 * - Connection acquisition: < 5ms (p95)
 * - Query execution: < 50ms (p95)
 * - Connection pool efficiency: > 95% utilization
 * - Connection leak detection: < 1s
 * - Connection recovery: < 100ms
 * - Batch operations: 1000 operations < 1s
 * 
 * Configuration:
 * - Pool size: 50 connections for H2, 100 for PostgreSQL
 * - Timeout: 30s for connection acquisition
 * - Validation query: "SELECT 1"
 * - Max lifetime: 30 minutes
 * 
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 2026-02-26
 */
@Tag("integration")
@Tag("performance")
@Tag("database")
@Execution(ExecutionMode.CONCURRENT)
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class DatabaseConnectionBenchmark {

    // Performance thresholds
    private static final long MAX_CONNECTION_ACQUISITION_MS = 5;
    private static final long MAX_QUERY_EXECUTION_MS = 50;
    private static final double MIN_POOL_UTILIZATION = 0.95;
    private static final long MAX_CONNECTION_LEAK_DETECTION_MS = 1000;
    private static final long MAX_CONNECTION_RECOVERY_MS = 100;
    private static final long MAX_BATCH_OPERATION_MS = 1000;

    // Test configuration
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final int MAX_POOL_SIZE = 50;
    private static final int CONCURRENT_THREADS = 100;

    // Test fixtures
    private static ConnectionPoolManager poolManager;
    private static DatabaseBenchmarkDatabase testDatabase;

    @BeforeAll
    static void setUpClass() throws Exception {
        // Initialize test database
        testDatabase = new DatabaseBenchmarkDatabase();
        
        // Initialize connection pool manager
        poolManager = new ConnectionPoolManager(
            "jdbc:h2:mem:db_benchmark_%d;DB_CLOSE_DELAY=-1".formatted(System.nanoTime()),
            "sa",
            "",
            MAX_POOL_SIZE
        );

        System.out.println("Database Connection Pool Efficiency Benchmark initialized");
    }

    @AfterAll
    static void tearDownClass() throws Exception {
        if (poolManager != null) {
            poolManager.closeAll();
        }
        if (testDatabase != null) {
            testDatabase.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Reset test data
        testDatabase.reset();
        // Warmup the pool
        warmupPool();
    }

    @Test
    @Order(1)
    @DisplayName("1.1: Connection acquisition performance")
    void connectionAcquisitionPerformance() throws Exception {
        // Given: Connection pool with available connections
        List<Long> acquisitionTimes = new ArrayList<>();
        int acquisitionCount = 1000;

        // When: Measure connection acquisition time
        for (int i = 0; i < acquisitionCount; i++) {
            long start = System.nanoTime();
            Connection conn = poolManager.getConnection();
            long end = System.nanoTime();
            
            acquisitionTimes.add((end - start) / 1_000_000);
            poolManager.releaseConnection(conn);
        }

        // Calculate statistics
        Collections.sort(acquisitionTimes);
        double avgTime = acquisitionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double p95Time = acquisitionTimes.get((int) (acquisitionTimes.size() * 0.95));
        double p99Time = acquisitionTimes.get((int) (acquisitionTimes.size() * 0.99));

        // Then: Validate performance targets
        assertTrue(avgTime < MAX_CONNECTION_ACQUISITION_MS,
            "Average acquisition time should be under " + MAX_CONNECTION_ACQUISITION_MS + 
            "ms, was: " + String.format("%.2f", avgTime) + "ms");
        assertTrue(p95Time < MAX_CONNECTION_ACQUISITION_MS,
            "P95 acquisition time should be under " + MAX_CONNECTION_ACQUISITION_MS + 
            "ms, was: " + String.format("%.2f", p95Time) + "ms");
        assertTrue(p99Time < 50, // Allow more tolerance for p99
            "P99 acquisition time should be under 50ms, was: " + String.format("%.2f", p99Time) + "ms");

        System.out.println("Connection acquisition performance:");
        System.out.println("  Average: " + String.format("%.2f", avgTime) + "ms");
        System.out.println("  P95: " + String.format("%.2f", p95Time) + "ms");
        System.out.println("  P99: " + String.format("%.2f", p99Time) + "ms");
    }

    @Test
    @Order(2)
    @DisplayName("2.1: Connection pool utilization efficiency")
    void connectionPoolUtilizationEfficiency() throws Exception {
        // Given: Connection pool with limited connections
        int availableConnections = poolManager.getPoolSize();
        int requestsPerThread = 50;
        int numThreads = 20;
        
        AtomicInteger acquiredConnections = new AtomicInteger(0);
        AtomicInteger timeouts = new AtomicInteger(0);
        List<Connection> acquiredConnectionList = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        long startTime = System.nanoTime();

        // When: Concurrent connection requests
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            Connection conn = poolManager.getConnection(100, TimeUnit.MILLISECONDS);
                            if (conn != null) {
                                acquiredConnectionList.add(conn);
                                acquiredConnections.incrementAndGet();
                                
                                // Use the connection briefly
                                try (Statement stmt = conn.createStatement()) {
                                    stmt.executeQuery("SELECT 1");
                                }
                                
                                poolManager.releaseConnection(conn);
                            } else {
                                timeouts.incrementAndGet();
                            }
                        } catch (SQLException e) {
                            // Track failures
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads must complete");
        long endTime = System.nanoTime();

        executor.shutdown();

        // Calculate metrics
        double utilization = (double) acquiredConnections.get() / (availableConnections * requestsPerThread);
        double throughput = (acquiredConnections.get() * 1000.0) / ((endTime - startTime) / 1_000_000);
        double timeoutRate = (double) timeouts.get() / (numThreads * requestsPerThread);

        // Then: Validate pool efficiency
        assertTrue(utilization >= MIN_POOL_UTILIZATION,
            "Pool utilization should be at least " + (MIN_POOL_UTILIZATION * 100) + 
            "%, was: " + String.format("%.2f", utilization * 100) + "%");
        assertTrue(timeoutRate < 0.05,
            "Timeout rate should be less than 5%, was: " + String.format("%.2f", timeoutRate * 100) + "%");
        assertTrue(throughput > 100,
            "Throughput should be >100 connections/sec, was: " + String.format("%.1f", throughput));

        System.out.println("Connection pool utilization:");
        System.out.println("  Pool size: " + availableConnections);
        System.out.println("  Total requests: " + (numThreads * requestsPerThread));
        System.out.println("  Successful acquisitions: " + acquiredConnections.get());
        System.out.println("  Timeouts: " + timeouts.get());
        System.out.println("  Utilization: " + String.format("%.2f", utilization * 100) + "%");
        System.out.println("  Throughput: " + String.format("%.1f", throughput) + " connections/sec");
    }

    @Test
    @Order(3)
    @DisplayName("3.1: Connection leak detection and recovery")
    void connectionLeakDetectionAndRecovery() throws Exception {
        // Given: Some connections are leaked
        int leakCount = 5;
        List<Connection> leakedConnections = new ArrayList<>();

        // Leak some connections
        for (int i = 0; i < leakCount; i++) {
            Connection conn = poolManager.getConnection();
            leakedConnections.add(conn);
            // Don't release this connection - simulate leak
        }

        // When: Detect and recover leaks
        long detectionStart = System.nanoTime();
        int detectedLeaks = poolManager.detectLeaks();
        long detectionEnd = System.nanoTime();

        long recoveryStart = System.nanoTime();
        int recoveredLeaks = poolManager.recoverConnections();
        long recoveryEnd = System.nanoTime();

        // Calculate metrics
        long detectionTime = (detectionEnd - detectionStart) / 1_000_000;
        long recoveryTime = (recoveryEnd - recoveryStart) / 1_000_000;

        // Then: Validate leak detection and recovery
        assertTrue(detectionTime < MAX_CONNECTION_LEAK_DETECTION_MS,
            "Leak detection should be under " + MAX_CONNECTION_LEAK_DETECTION_MS + 
            "ms, was: " + detectionTime + "ms");
        assertTrue(recoveryTime < MAX_CONNECTION_RECOVERY_MS,
            "Connection recovery should be under " + MAX_CONNECTION_RECOVERY_MS + 
            "ms, was: " + recoveryTime + "ms");
        assertTrue(detectedLeaks >= leakCount,
            "Should detect at least " + leakCount + " leaks, detected: " + detectedLeaks);
        assertTrue(recoveredLeaks >= leakCount,
            "Should recover at least " + leakCount + " leaks, recovered: " + recoveredLeaks);

        System.out.println("Connection leak detection and recovery:");
        System.out.println("  Leaked connections: " + leakCount);
        System.out.println("  Detected leaks: " + detectedLeaks);
        System.out.println("  Detection time: " + detectionTime + "ms");
        System.out.println("  Recovered leaks: " + recoveredLeaks);
        System.out.println("  Recovery time: " + recoveryTime + "ms");
    }

    @Test
    @Order(4)
    @DisplayName("4.1: Query performance under load")
    void queryPerformanceUnderLoad() throws Exception {
        // Given: Database with test data
        testDatabase.populateTestData(1000);

        // When: Execute queries under concurrent load
        int queryCount = 1000;
        int concurrentThreads = 50;
        List<Long> queryTimes = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch latch = new CountDownLatch(queryCount);

        long startTime = System.nanoTime();

        for (int i = 0; i < queryCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long queryStart = System.nanoTime();
                    executeQuery("SELECT * FROM test_data WHERE id = ?", "data-" + (index % 1000));
                    long queryEnd = System.nanoTime();
                    queryTimes.add((queryEnd - queryStart) / 1_000_000);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All queries must complete");
        long endTime = System.nanoTime();

        executor.shutdown();

        // Calculate statistics
        Collections.sort(queryTimes);
        double avgQueryTime = queryTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double p95QueryTime = queryTimes.get((int) (queryTimes.size() * 0.95));
        double throughput = (queryCount * 1000.0) / ((endTime - startTime) / 1_000_000);

        // Then: Validate query performance
        assertTrue(avgQueryTime < MAX_QUERY_EXECUTION_MS,
            "Average query time should be under " + MAX_QUERY_EXECUTION_MS + 
            "ms, was: " + String.format("%.2f", avgQueryTime) + "ms");
        assertTrue(p95QueryTime < MAX_QUERY_EXECUTION_MS,
            "P95 query time should be under " + MAX_QUERY_EXECUTION_MS + 
            "ms, was: " + String.format("%.2f", p95QueryTime) + "ms");
        assertTrue(throughput > 50,
            "Query throughput should be >50 queries/sec, was: " + String.format("%.1f", throughput));

        System.out.println("Query performance under load:");
        System.out.println("  Total queries: " + queryCount);
        System.out.println("  Average time: " + String.format("%.2f", avgQueryTime) + "ms");
        System.out.println("  P95 time: " + String.format("%.2f", p95QueryTime) + "ms");
        System.out.println("  Throughput: " + String.format("%.1f", throughput) + " queries/sec");
    }

    @Test
    @Order(5)
    @DisplayName("5.1: Batch operation performance")
    void batchOperationPerformance() throws Exception {
        // Given: Connection pool with available connections
        int batchSize = 1000;
        int numBatches = 10;
        List<Long> batchTimes = new ArrayList<>();

        // When: Execute batch operations
        for (int batchNum = 0; batchNum < numBatches; batchNum++) {
            Connection conn = poolManager.getConnection();
            
            long batchStart = System.nanoTime();
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO test_data (id, value) VALUES (?, ?)")) {
                
                for (int i = 0; i < batchSize; i++) {
                    stmt.setString(1, "batch-" + batchNum + "-" + i);
                    stmt.setString(2, "batch-value-" + i);
                    stmt.addBatch();
                    
                    // Execute batch every 100 statements to avoid memory issues
                    if (i > 0 && i % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch(); // Execute remaining
            }
            
            long batchEnd = System.nanoTime();
            batchTimes.add((batchEnd - batchStart) / 1_000_000);
            poolManager.releaseConnection(conn);
        }

        // Calculate metrics
        double avgBatchTime = batchTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double maxBatchTime = batchTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        double operationsPerSecond = (batchSize * numBatches * 1000.0) / (avgBatchTime);

        // Then: Validate batch performance
        assertTrue(avgBatchTime < MAX_BATCH_OPERATION_MS,
            "Average batch time should be under " + MAX_BATCH_OPERATION_MS + 
            "ms, was: " + String.format("%.2f", avgBatchTime) + "ms");
        assertTrue(operationsPerSecond > 1000,
            "Should process >1000 operations/sec, was: " + String.format("%.1f", operationsPerSecond));

        System.out.println("Batch operation performance:");
        System.out.println("  Batch size: " + batchSize);
        System.out.println("  Number of batches: " + numBatches);
        System.out.println("  Average batch time: " + String.format("%.2f", avgBatchTime) + "ms");
        System.out.println("  Maximum batch time: " + maxBatchTime + "ms");
        System.out.println("  Operations per second: " + String.format("%.1f", operationsPerSecond));
    }

    @Test
    @Order(6)
    @DisplayName("6.1: Connection validation and health checks")
    void connectionValidationAndHealthChecks() throws Exception {
        // Given: Connection pool with some potentially stale connections
        int staleConnections = 10;
        for (int i = 0; i < staleConnections; i++) {
            Connection conn = poolManager.getConnection();
            // Simulate network issues by closing connection without pool knowledge
            conn.close();
        }

        // When: Perform health checks
        long healthCheckStart = System.nanoTime();
        int healthyConnections = poolManager.performHealthCheck();
        long healthCheckEnd = System.nanoTime();

        long healthCheckTime = (healthCheckEnd - healthCheckStart) / 1_000_000;

        // Then: Validate health check performance
        assertTrue(healthCheckTime < 100,
            "Health check should be under 100ms, was: " + healthCheckTime + "ms");
        assertTrue(healthyConnections > 0,
            "Should have at least some healthy connections: " + healthyConnections);

        // Test connection validation
        Connection conn = poolManager.getConnection();
        assertTrue(poolManager.validateConnection(conn),
            "Connection should be valid");
        poolManager.releaseConnection(conn);

        System.out.println("Connection validation and health checks:");
        System.out.println("  Health check time: " + healthCheckTime + "ms");
        System.out.println("  Healthy connections: " + healthyConnections);
    }

    @Test
    @Order(7)
    @DisplayName("7.1: Scalability under extreme load")
    void scalabilityUnderExtremeLoad() throws Exception {
        // Given: Connection pool at maximum capacity
        int extremeLoad = 200;
        int durationSeconds = 10;
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        // When: Apply extreme load
        ExecutorService executor = Executors.newFixedThreadPool(extremeLoad);
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();

        // Start timeout monitor
        timeoutExecutor.scheduleAtFixedRate(() -> {
            System.out.println("Progress - Success: " + successCount.get() + 
                ", Failures: " + failureCount.get() + 
                ", Timeouts: " + timeoutCount.get());
        }, 1, 1, TimeUnit.SECONDS);

        // Submit load
        for (int i = 0; i < extremeLoad; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    // Perform multiple operations per thread
                    for (int op = 0; op < durationSeconds; op++) {
                        long opStart = System.nanoTime();
                        
                        Connection conn = poolManager.getConnection(100, TimeUnit.MILLISECONDS);
                        if (conn != null) {
                            try {
                                executeQuery("SELECT 1", null);
                                successCount.incrementAndGet();
                            } finally {
                                poolManager.releaseConnection(conn);
                            }
                        } else {
                            timeoutCount.incrementAndGet();
                        }
                        
                        long opEnd = System.nanoTime();
                        long opTime = (opEnd - opStart) / 1_000_000;
                        
                        if (opTime > 1000) {
                            failureCount.incrementAndGet();
                        }
                        
                        // Small delay between operations
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        // Wait for duration
        Thread.sleep(durationSeconds * 1000L);
        executor.shutdownNow();
        timeoutExecutor.shutdown();

        long endTime = System.nanoTime();
        long totalDuration = (endTime - startTime) / 1_000_000;

        // Calculate metrics
        double totalOperations = successCount.get() + failureCount.get() + timeoutCount.get();
        double throughput = totalOperations / (totalDuration / 1000.0);
        double successRate = (double) successCount.get() / totalOperations;

        // Then: Validate scalability
        assertTrue(successRate > 0.9,
            "Success rate should be >90%, was: " + String.format("%.2f", successRate * 100) + "%");
        assertTrue(throughput > extremeLoad,
            "Throughput should be >" + extremeLoad + " ops/sec, was: " + String.format("%.1f", throughput));

        System.out.println("Scalability under extreme load:");
        System.out.println("  Load: " + extremeLoad + " concurrent threads");
        System.out.println("  Duration: " + durationSeconds + " seconds");
        System.out.println("  Total operations: " + (int) totalOperations);
        System.out.println("  Success rate: " + String.format("%.2f", successRate * 100) + "%");
        System.out.println("  Throughput: " + String.format("%.1f", throughput) + " ops/sec");
    }

    // Helper methods

    private void warmupPool() throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            Connection conn = poolManager.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
            poolManager.releaseConnection(conn);
        }
    }

    private void executeQuery(String sql, String param) throws Exception {
        Connection conn = poolManager.getConnection();
        try {
            if (param != null) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, param);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            rs.getString("value");
                        }
                    }
                }
            } else {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        rs.getString(1);
                    }
                }
            }
        } finally {
            poolManager.releaseConnection(conn);
        }
    }

    // Inner classes

    private static class ConnectionPoolManager {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final int maxPoolSize;
        private final BlockingQueue<Connection> pool;
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicInteger leakedConnections = new AtomicInteger(0);

        public ConnectionPoolManager(String jdbcUrl, String username, String password, int maxPoolSize) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.maxPoolSize = maxPoolSize;
            this.pool = new LinkedBlockingQueue<>(maxPoolSize);
            initializePool();
        }

        private void initializePool() {
            for (int i = 0; i < maxPoolSize / 2; i++) { // Start with half pool
                try {
                    Connection conn = createNewConnection();
                    pool.offer(conn);
                } catch (SQLException e) {
                    // Continue with fewer connections
                }
            }
        }

        public Connection getConnection() throws SQLException {
            return getConnection(30, TimeUnit.SECONDS);
        }

        public Connection getConnection(long timeout, TimeUnit unit) throws SQLException {
            try {
                Connection conn = pool.poll(timeout, unit);
                if (conn == null) {
                    throw new SQLException("Connection timeout after " + timeout + " " + unit);
                }
                activeConnections.incrementAndGet();
                return new PooledConnectionWrapper(conn);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for connection", e);
            }
        }

        public void releaseConnection(Connection conn) throws SQLException {
            if (conn instanceof PooledConnectionWrapper) {
                PooledConnectionWrapper wrapper = (PooledConnectionWrapper) conn;
                if (validateConnection(wrapper.getDelegate())) {
                    if (!pool.offer(wrapper.getDelegate())) {
                        // Pool is full, close the connection
                        wrapper.getDelegate().close();
                    }
                } else {
                    // Invalid connection, create a new one to replace it
                    try {
                        Connection newConn = createNewConnection();
                        if (!pool.offer(newConn)) {
                            newConn.close();
                        }
                    } catch (SQLException e) {
                        // Failed to create replacement
                    }
                }
                activeConnections.decrementAndGet();
            }
        }

        public boolean validateConnection(Connection conn) {
            try {
                if (conn == null || conn.isClosed()) {
                    return false;
                }
                return conn.isValid(5); // 5 second timeout
            } catch (SQLException e) {
                return false;
            }
        }

        public int detectLeaks() {
            // In a real implementation, this would track connection usage
            // For this benchmark, we'll simulate leak detection
            return leakedConnections.get();
        }

        public int recoverConnections() {
            // In a real implementation, this would clean up leaked connections
            int recovered = leakedConnections.get();
            leakedConnections.set(0);
            return recovered;
        }

        public int getPoolSize() {
            return maxPoolSize;
        }

        public int getActiveConnectionsCount() {
            return activeConnections.get();
        }

        public int getAvailableConnectionsCount() {
            return pool.size();
        }

        public int performHealthCheck() {
            int healthy = 0;
            for (Connection conn : pool) {
                if (validateConnection(conn)) {
                    healthy++;
                }
            }
            return healthy;
        }

        public void closeAll() throws SQLException {
            for (Connection conn : pool) {
                conn.close();
            }
            pool.clear();
        }

        private Connection createNewConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        private class PooledConnectionWrapper implements Connection {
            private final Connection delegate;

            public PooledConnectionWrapper(Connection delegate) {
                this.delegate = delegate;
            }

            public Connection getDelegate() {
                return delegate;
            }

            @Override
            public void close() throws SQLException {
                // Don't actually close the connection, return it to the pool
                // Connection is released via releaseConnection method
            }

            @Override
            public boolean isClosed() throws SQLException {
                return delegate.isClosed();
            }

            @Override
            public void commit() throws SQLException {
                delegate.commit();
            }

            @Override
            public void rollback() throws SQLException {
                delegate.rollback();
            }

            // Delegate other Connection methods
            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                return delegate.unwrap(iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws SQLException {
                return delegate.isWrapperFor(iface);
            }

            @Override
            public Savepoint setSavepoint() throws SQLException {
                return delegate.setSavepoint();
            }

            @Override
            public Savepoint setSavepoint(String name) throws SQLException {
                return delegate.setSavepoint(name);
            }

            @Override
            public void rollback(Savepoint savepoint) throws SQLException {
                delegate.rollback(savepoint);
            }

            @Override
            public void close() throws SQLException {
                // Override to prevent accidental closing
            }

            @Override
            public boolean getAutoCommit() throws SQLException {
                return delegate.getAutoCommit();
            }

            @Override
            public void setAutoCommit(boolean autoCommit) throws SQLException {
                delegate.setAutoCommit(autoCommit);
            }

            @Override
            public DatabaseMetaData getMetaData() throws SQLException {
                return delegate.getMetaData();
            }

            @Override
            public void setReadOnly(boolean readOnly) throws SQLException {
                delegate.setReadOnly(readOnly);
            }

            @Override
            public boolean isReadOnly() throws SQLException {
                return delegate.isReadOnly();
            }

            @Override
            public void setCatalog(String catalog) throws SQLException {
                delegate.setCatalog(catalog);
            }

            @Override
            public String getCatalog() throws SQLException {
                return delegate.getCatalog();
            }

            @Override
            public void setTransactionIsolation(int level) throws SQLException {
                delegate.setTransactionIsolation(level);
            }

            @Override
            public int getTransactionIsolation() throws SQLException {
                return delegate.getTransactionIsolation();
            }

            @Override
            public SQLWarning getWarnings() throws SQLException {
                return delegate.getWarnings();
            }

            @Override
            public void clearWarnings() throws SQLException {
                delegate.clearWarnings();
            }

            @Override
            public Statement createStatement() throws SQLException {
                return delegate.createStatement();
            }

            @Override
            public PreparedStatement prepareStatement(String sql) throws SQLException {
                return delegate.prepareStatement(sql);
            }

            @Override
            public CallableStatement prepareCall(String sql) throws SQLException {
                return delegate.prepareCall(sql);
            }

            @Override
            public String nativeSQL(String sql) throws SQLException {
                return delegate.nativeSQL(sql);
            }

            @Override
            public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException {
                return delegate.createArrayOf(typeName, elements);
            }

            @Override
            public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
                return delegate.createStruct(typeName, attributes);
            }
        }
    }

    private static class DatabaseBenchmarkDatabase {
        private Connection connection;

        public DatabaseBenchmarkDatabase() throws SQLException {
            String jdbcUrl = "jdbc:h2:mem:db_benchmark_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
            connection = DriverManager.getConnection(jdbcUrl, "sa", "");
            createSchema();
        }

        public void close() throws SQLException {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }

        public void reset() throws SQLException {
            connection.createStatement().execute("DROP TABLE IF EXISTS test_data");
            createSchema();
        }

        private void createSchema() throws SQLException {
            connection.createStatement().execute("""
                CREATE TABLE test_data (
                    id VARCHAR(255) PRIMARY KEY,
                    value VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }

        public void populateTestData(int count) throws SQLException {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO test_data (id, value) VALUES (?, ?)")) {
                for (int i = 0; i < count; i++) {
                    stmt.setString(1, "data-" + i);
                    stmt.setString(2, "test-value-" + i);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }
}
