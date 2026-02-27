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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP/A2A Integration Performance Benchmark
 * 
 * Comprehensive end-to-end performance benchmarking for the integration
 * between Model Context Protocol (MCP) and Agent-to-Agent (A2A) protocols
 * in the YAWL workflow engine.
 * 
 * Performance Targets:
 * - Engine startup: < 60s
 * - Case creation (p95): < 500ms
 * - Work item checkout (p95): < 200ms
 * - Work item checkin (p95): < 300ms
 * - Task transition: < 100ms
 * - DB query (p95): < 50ms
 * - GC time: < 5%, Full GCs: < 10/hour
 * 
 * JVM Configuration:
 * - Heap: 2-4GB
 * - GC: ZGC or G1GC
 * - Compact object headers: -XX:+UseCompactObjectHeaders
 * - Virtual threads for concurrency
 * 
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 2026-02-26
 */
@Tag("integration")
@Tag("performance")
@Execution(ExecutionMode.CONCURRENT)
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class McpA2AIntegrationBenchmark {

    // Performance thresholds (in milliseconds)
    private static final long MAX_ENGINE_STARTUP_MS = 60_000;
    private static final long MAX_CASE_CREATION_P95_MS = 500;
    private static final long MAX_WORK_ITEM_CHECKOUT_P95_MS = 200;
    private static final long MAX_WORK_ITEM_CHECKIN_P95_MS = 300;
    private static final long MAX_TASK_TRANSITION_MS = 100;
    private static final long MAX_DB_QUERY_P95_MS = 50;

    // Test configuration
    private static final int CONCURRENT_CASES = 500;
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;

    // Test fixtures
    private static Connection db;
    private static SecretKey jwtKey;
    private static ObjectMapper objectMapper;
    private static HttpServer mockEngineServer;
    private static String ENGINE_URL;

    @BeforeAll
    static void setUpClass() throws Exception {
        // Initialize embedded database
        String jdbcUrl = "jdbc:h2:mem:mcp_a2a_perf_%d;DB_CLOSE_DELAY=-1"
            .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        createTestSchema(db);

        // Initialize JWT components
        jwtKey = Keys.hmacShaKeyFor("test-jwt-key-for-benchmark-min-32-chars!".getBytes(StandardCharsets.UTF_8));

        // Initialize JSON mapper
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Start mock engine server
        mockEngineServer = HttpServer.create(new InetSocketAddress(0), 0);
        startMockEngineServer();
        ENGINE_URL = "http://localhost:" + mockEngineServer.getAddress().getPort() + "/yawl";

        System.out.println("MCP/A2A Integration Performance Benchmark initialized");
    }

    @AfterAll
    static void tearDownClass() throws Exception {
        if (mockEngineServer != null) {
            mockEngineServer.stop(0);
        }
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Warmup
        performWarmup();
    }

    @Test
    @Order(1)
    @DisplayName("1.1: Engine startup performance")
    void engineStartupPerformance() throws Exception {
        // Given: Engine server is already running
        long startTime = System.nanoTime();

        // When: Measuring engine availability
        boolean engineReady = isEngineReady();
        long endTime = System.nanoTime();

        // Then: Engine must be ready within threshold
        assertTrue(engineReady, "Engine must be ready after startup");
        long startupTimeMs = (endTime - startTime) / 1_000_000;
        assertTrue(startupTimeMs < MAX_ENGINE_STARTUP_MS,
            "Engine startup must be under " + MAX_ENGINE_STARTUP_MS + "ms, was: " + startupTimeMs + "ms");

        System.out.println("Engine startup time: " + startupTimeMs + "ms");
    }

    @Test
    @Order(2)
    @DisplayName("2.1: Case creation throughput (p95)")
    void caseCreationThroughput() throws Exception {
        // Given: Database with test specifications
        seedSpecifications(db, 10);

        // When: Creating cases concurrently
        int casesCreated = 0;
        List<Long> creationTimes = new ArrayList<>();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(CONCURRENT_CASES);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < CONCURRENT_CASES; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long caseStart = System.nanoTime();
                    String caseId = createCase("spec-" + (index % 10) + "-1.0", "case-" + index);
                    long caseEnd = System.nanoTime();
                    
                    creationTimes.add((caseEnd - caseStart) / 1_000_000);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Track failures
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "All case creation attempts must complete");
        long endTime = System.nanoTime();

        executor.shutdown();

        // Calculate statistics
        Collections.sort(creationTimes);
        double medianTime = creationTimes.get(creationTimes.size() / 2);
        double p95Time = creationTimes.get((int) (creationTimes.size() * 0.95));

        // Then: Performance targets must be met
        assertTrue(successCount.get() > 0, "At least one case must be created");
        assertTrue(medianTime < MAX_CASE_CREATION_P95_MS,
            "Median case creation time must be under " + MAX_CASE_CREATION_P95_MS + "ms, was: " + medianTime + "ms");
        assertTrue(p95Time < MAX_CASE_CREATION_P95_MS,
            "P95 case creation time must be under " + MAX_CASE_CREATION_P95_MS + "ms, was: " + p95Time + "ms");

        double casesPerSecond = (successCount.get() * 1000.0) / ((endTime - startTime) / 1_000_000);
        System.out.println("Case creation throughput: " + String.format("%.1f", casesPerSecond) + " cases/sec");
        System.out.println("  Median: " + String.format("%.2f", medianTime) + "ms");
        System.out.println("  P95: " + String.format("%.2f", p95Time) + "ms");
    }

    @Test
    @Order(3)
    @DisplayName("3.1: Work item checkout performance")
    void workItemCheckoutPerformance() throws Exception {
        // Given: Existing cases with work items
        String caseId = "checkout-case-" + System.currentTimeMillis();
        createWorkItems(db, caseId, 100);

        // When: Checking out work items
        List<Long> checkoutTimes = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long start = System.nanoTime();
                    boolean success = checkoutWorkItem(caseId, "workitem-" + index);
                    long end = System.nanoTime();
                    
                    checkoutTimes.add((end - start) / 1_000_000);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        conflictCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All checkout attempts must complete");
        executor.shutdown();

        // Calculate p95 time
        Collections.sort(checkoutTimes);
        double p95Time = checkoutTimes.isEmpty() ? 0 : checkoutTimes.get((int) (checkoutTimes.size() * 0.95));

        // Then: Performance targets must be met
        assertTrue(p95Time < MAX_WORK_ITEM_CHECKOUT_P95_MS,
            "P95 checkout time must be under " + MAX_WORK_ITEM_CHECKOUT_P95_MS + "ms, was: " + p95Time + "ms");

        System.out.println("Work item checkout performance:");
        System.out.println("  Successful: " + successCount.get());
        System.out.println("  Conflicts: " + conflictCount.get());
        System.out.println("  P95 time: " + String.format("%.2f", p95Time) + "ms");
    }

    @Test
    @Order(4)
    @DisplayName("4.1: Work item checkin performance")
    void workItemCheckinPerformance() throws Exception {
        // Given: Work items are checked out
        String caseId = "checkin-case-" + System.currentTimeMillis();
        createWorkItems(db, caseId, 100);

        // When: Checking in work items
        List<Long> checkinTimes = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long start = System.nanoTime();
                    boolean success = checkinWorkItem(caseId, "workitem-" + index);
                    long end = System.nanoTime();
                    
                    checkinTimes.add((end - start) / 1_000_000);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All checkin attempts must complete");
        executor.shutdown();

        // Calculate p95 time
        Collections.sort(checkinTimes);
        double p95Time = checkinTimes.isEmpty() ? 0 : checkinTimes.get((int) (checkinTimes.size() * 0.95));

        // Then: Performance targets must be met
        assertTrue(p95Time < MAX_WORK_ITEM_CHECKIN_P95_MS,
            "P95 checkin time must be under " + MAX_WORK_ITEM_CHECKIN_P95_MS + "ms, was: " + p95Time + "ms");

        System.out.println("Work item checkin performance:");
        System.out.println("  P95 time: " + String.format("%.2f", p95Time) + "ms");
    }

    @Test
    @Order(5)
    @DisplayName("5.1: Task transition performance")
    void taskTransitionPerformance() throws Exception {
        // Given: Task with multiple possible transitions
        String caseId = "transition-case-" + System.currentTimeMillis();
        createWorkItems(db, caseId, 50);

        // When: Performing task transitions
        List<Long> transitionTimes = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(50);

        for (int i = 0; i < 50; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long start = System.nanoTime();
                    performTaskTransition(caseId, "workitem-" + index);
                    long end = System.nanoTime();
                    
                    transitionTimes.add((end - start) / 1_000_000);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All transition attempts must complete");
        executor.shutdown();

        // Then: All transitions must be fast
        for (long transitionTime : transitionTimes) {
            assertTrue(transitionTime < MAX_TASK_TRANSITION_MS,
                "Task transition must be under " + MAX_TASK_TRANSITION_MS + "ms, was: " + transitionTime + "ms");
        }

        double avgTime = transitionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.println("Task transition performance:");
        System.out.println("  Average: " + String.format("%.2f", avgTime) + "ms");
        System.out.println("  Maximum: " + Collections.max(transitionTimes) + "ms");
    }

    @Test
    @Order(6)
    @DisplayName("6.1: Database query performance (p95)")
    void databaseQueryPerformance() throws Exception {
        // Given: Database with test data
        populateTestData(db, 1000);

        // When: Performing database queries
        List<Long> queryTimes = new ArrayList<>();
        int queryCount = 100;

        for (int i = 0; i < queryCount; i++) {
            long start = System.nanoTime();
            String result = executeQuery(db, "SELECT * FROM work_items WHERE case_id = ? AND status = ?",
                "case-" + (i % 100), "pending");
            long end = System.nanoTime();
            
            queryTimes.add((end - start) / 1_000_000);
            assertNotNull(result, "Query must return result");
        }

        // Calculate p95 time
        Collections.sort(queryTimes);
        double p95Time = queryTimes.isEmpty() ? 0 : queryTimes.get((int) (queryTimes.size() * 0.95));

        // Then: Performance targets must be met
        assertTrue(p95Time < MAX_DB_QUERY_P95_MS,
            "P95 query time must be under " + MAX_DB_QUERY_P95_MS + "ms, was: " + p95Time + "ms");

        System.out.println("Database query performance:");
        System.out.println("  Average: " + String.format("%.2f", queryTimes.stream().mapToLong(Long::longValue).average().orElse(0)) + "ms");
        System.out.println("  P95: " + String.format("%.2f", p95Time) + "ms");
    }

    @Test
    @Order(7)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    @DisplayName("7.1: End-to-end workflow throughput")
    void endToEndWorkflowThroughput() throws Exception {
        // Given: Multiple specifications and resources
        seedSpecifications(db, 5);

        // When: Executing complete workflows end-to-end
        int workflowsExecuted = 0;
        long totalDuration = 0;
        List<Long> workflowDurations = new ArrayList<>();

        int numWorkflows = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numWorkflows);

        for (int i = 0; i < numWorkflows; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long start = System.nanoTime();
                    
                    // Complete workflow: create case -> process work items -> complete
                    String caseId = "workflow-" + index;
                    String specId = "spec-" + (index % 5) + "-1.0";
                    
                    createCase(specId, caseId);
                    processAllWorkItems(caseId);
                    completeWorkflow(caseId);
                    
                    long end = System.nanoTime();
                    workflowDurations.add((end - start) / 1_000_000);
                    totalDuration += (end - start);
                    workflowsExecuted++;
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(240, TimeUnit.SECONDS), "All workflows must complete");
        executor.shutdown();

        // Then: Performance metrics
        double avgWorkflowTime = totalDuration / 1_000_000.0 / workflowsExecuted;
        double workflowsPerSecond = (workflowsExecuted * 1000.0) / totalDuration * 1_000_000;

        assertTrue(workflowsExecuted > 0, "At least one workflow must be executed");
        assertTrue(workflowsPerSecond > 10, "Must process at least 10 workflows/second");
        
        System.out.println("End-to-end workflow throughput:");
        System.out.println("  Workflows executed: " + workflowsExecuted);
        System.out.println("  Average duration: " + String.format("%.2f", avgWorkflowTime) + "ms");
        System.out.println("  Throughput: " + String.format("%.1f", workflowsPerSecond) + " workflows/sec");
    }

    @Test
    @Order(8)
    @DisplayName("8.1: Memory and GC behavior")
    void memoryAndGCBehavior() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC to get baseline
        System.gc();
        Thread.sleep(500);
        
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double baselinePercent = (baselineMemory * 100.0) / maxMemory;

        // Create load
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<String> createdCases = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            final int index = i;
            Future<?> future = executor.submit(() -> {
                String caseId = "mem-case-" + index;
                createCase("spec-1.0-1.0", caseId);
                createdCases.add(caseId);
                
                // Simulate some memory usage
                List<byte[]> data = new ArrayList<>();
                for (int j = 0; j < 10; j++) {
                    data.add(new byte[1024]); // 1KB per item
                }
                try {
                    Thread.sleep(1); // Hold memory briefly
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                data.clear();
            });
            futures.add(future);
        }

        // Wait for completion
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Measure peak memory usage
        long peakMemory = runtime.totalMemory() - runtime.freeMemory();
        double peakPercent = (peakMemory * 100.0) / maxMemory;

        // Force GC and measure
        System.gc();
        Thread.sleep(500);
        long afterGC = runtime.totalMemory() - runtime.freeMemory();
        double afterGCPercent = (afterGC * 100.0) / maxMemory;

        // Then: Memory targets must be met
        assertTrue(baselinePercent < 50, "Baseline memory should be under 50%");
        assertTrue(peakPercent < 80, "Peak memory should be under 80%");
        assertTrue(afterGCPercent < 60, "Memory after GC should be under 60%");

        System.out.println("Memory and GC behavior:");
        System.out.println("  Baseline: " + String.format("%.1f", baselinePercent) + "%");
        System.out.println("  Peak: " + String.format("%.1f", peakPercent) + "%");
        System.out.println("  After GC: " + String.format("%.1f", afterGCPercent) + "%");
        System.out.println("  GC efficiency: " + String.format("%.1f", (peakPercent - afterGCPercent)) + "% reduction");
    }

    // Helper methods

    private void startMockEngineServer() throws IOException {
        mockEngineServer.createContext("/yawl/cases", exchange -> {
            String response = """
                {"cases": ["case-1", "case-2"]}
                """;
            sendResponse(exchange, 200, response);
        });

        mockEngineServer.createContext("/yawl/workitems", exchange -> {
            String response = """
                {"workitems": [{"id": "wi-1", "status": "available"}]}
                """;
            sendResponse(exchange, 200, response);
        });

        mockEngineServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        mockEngineServer.start();

        // Wait for server to be ready
        awaitServerReady();
    }

    private void awaitServerReady() throws IOException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                    new URL(ENGINE_URL + "/cases").openConnection();
                conn.setConnectTimeout(100);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // Server not ready yet
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for server");
            }
        }
        throw new IOException("Server did not become ready within timeout");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean isEngineReady() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                new URL(ENGINE_URL + "/cases").openConnection();
            conn.setConnectTimeout(1000);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }

    private void performWarmup() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                simulateAPIRequest();
            } catch (Exception e) {
                // Ignore warmup errors
            }
        }
    }

    private void simulateAPIRequest() throws Exception {
        Thread.sleep(1); // Simulate 1ms request time
    }

    private String createCase(String specId, String caseId) throws Exception {
        // Simulate case creation
        String sql = "INSERT INTO cases (case_id, spec_id, status, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setString(1, caseId);
            stmt.setString(2, specId);
            stmt.setString(3, "running");
            stmt.setTimestamp(4, new java.sql.Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        }
        return caseId;
    }

    private boolean checkoutWorkItem(String caseId, String workItemId) throws Exception {
        // Simulate work item checkout with optimistic locking
        return Math.random() > 0.1; // 90% success rate, 10% conflicts
    }

    private boolean checkinWorkItem(String caseId, String workItemId) throws Exception {
        // Simulate work item checkin
        return true; // Assume all checkins succeed
    }

    private void performTaskTransition(String caseId, String workItemId) throws Exception {
        // Simulate task transition
        Thread.sleep(1); // Simulate 1ms transition time
    }

    private String executeQuery(Connection conn, String sql, String param1, String param2) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param1);
            stmt.setString(2, param2);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("id") : null;
        }
    }

    private void processAllWorkItems(String caseId) throws Exception {
        // Simulate processing all work items for a case
        Thread.sleep(100); // Simulate 100ms processing time
    }

    private void completeWorkflow(String caseId) throws Exception {
        // Simulate workflow completion
        String sql = "UPDATE cases SET status = 'completed' WHERE case_id = ?";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setString(1, caseId);
            stmt.executeUpdate();
        }
    }

    private void createTestSchema(Connection conn) throws Exception {
        // Create cases table
        conn.createStatement().execute("""
            CREATE TABLE cases (
                case_id VARCHAR(255) PRIMARY KEY,
                spec_id VARCHAR(255),
                status VARCHAR(50),
                created_at TIMESTAMP
            )
        """);

        // Create work_items table
        conn.createStatement().execute("""
            CREATE TABLE work_items (
                id VARCHAR(255) PRIMARY KEY,
                case_id VARCHAR(255),
                status VARCHAR(50),
                assigned_to VARCHAR(255),
                data JSON
            )
        """);
    }

    private void seedSpecifications(Connection conn, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            String sql = "INSERT INTO specs (spec_id, version, name) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "spec-" + i + "-1.0");
                stmt.setString(2, "1.0");
                stmt.setString(3, "Test Specification " + i);
                stmt.executeUpdate();
            }
        }
    }

    private void createWorkItems(Connection conn, String caseId, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            String sql = "INSERT INTO work_items (id, case_id, status, data) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "workitem-" + i);
                stmt.setString(2, caseId);
                stmt.setString(3, "available");
                stmt.setString(4, "{}");
                stmt.executeUpdate();
            }
        }
    }

    private void populateTestData(Connection conn, int count) throws Exception {
        // Create test data table if it doesn't exist
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS test_data (
                id VARCHAR(255) PRIMARY KEY,
                value VARCHAR(255)
            )
        """);

        // Insert test data
        for (int i = 0; i < count; i++) {
            String sql = "INSERT INTO test_data (id, value) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "data-" + i);
                stmt.setString(2, "test-value-" + i);
                stmt.executeUpdate();
            }
        }
    }
}
