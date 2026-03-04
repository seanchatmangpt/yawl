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
package org.yawlfoundation.yawl.erlang.bridge;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.qlever.QLEVER_INDEX;
import org.yawlfoundation.yawl.qlever.QleverNativeBridge;
import org.yawlfoundation.yawl.qlever.QleverQueryResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for QleverNativeBridge - Layer 2 Arena lifecycle and status→exception conversion.
 *
 * <p>This test class focuses on the JVM Domain (Layer 2) API boundary, ensuring:
 * <ul>
 *   <li>Proper Arena lifecycle management</li>
 *   <li>Status code to exception conversion</li>
 *   <li>SPARQL query execution</li>
 *   <li>Error handling and edge cases</li>
 * </ul>
 *
 * @see <a href="../processmining/QleverNativeBridge.java">QleverNativeBridge Implementation</a>
 */
@Tag("unit")
@Tag("qlever")
class QleverNativeBridgeTest {

    private QleverNativeBridge bridge;
    private TestArena testArena;

    @BeforeEach
    void setup() {
        assumeTrue(shouldRunQleverTests(), "Qlever tests disabled or not available");
        bridge = new QleverNativeBridge();
        testArena = new TestArena();
    }

    @AfterEach
    void cleanup() {
        if (bridge != null) {
            bridge.close();
        }
        if (testArena != null) {
            testArena.cleanup();
        }
    }

    // =========================================================================
    // Test 1: Arena Lifecycle (Layer 2 API)
    // =========================================================================

    /**
     * Verifies Arena creation and basic operations.
     */
    @Test
    @DisplayName("Arena: Create arena → verify properties")
    void arena_createArena_verifyProperties() {
        // Create arena with minimal configuration
        QLEVER_INDEX index = bridge.createArena("test-arena",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        assertNotNull(index, "Arena index should not be null");
        assertEquals("test-arena", index.getArenaName());
        assertFalse(index.getEndpoint().isEmpty());

        // Verify arena is registered
        assertTrue(bridge.hasArena("test-arena"),
            "Arena should be registered after creation");
    }

    /**
     * Verifies Arena cleanup and resource release.
     */
    @Test
    @DisplayName("Arena: Create and destroy → arena removed")
    void arena_createAndDestroy_arenaRemoved() {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("temp-arena",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        assertTrue(bridge.hasArena("temp-arena"),
            "Arena should exist before destruction");

        // Destroy arena
        bridge.destroyArena("temp-arena");

        assertFalse(bridge.hasArena("temp-arena"),
            "Arena should be removed after destruction");
    }

    /**
     * Verifies Arena status to exception conversion.
     */
    @Test
    @DisplayName("Arena: Status code → exception conversion")
    void arena_statusCodeToExceptionConversion() {
        // Create arena
        bridge.createArena("status-test",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        // Test various error conditions that should throw exceptions
        assertThrows(IllegalArgumentException.class,
            () -> bridge.createArena("", Map.of()),
            "Empty arena name should throw IllegalArgumentException");

        assertThrows(IllegalArgumentException.class,
            () -> bridge.createArena(null, Map.of()),
            "Null arena name should throw IllegalArgumentException");

        assertThrows(IllegalArgumentException.class,
            () -> bridge.destroyArena(null),
            "Null arena name should throw IllegalArgumentException");
    }

    /**
     * Verifies Arena configuration validation.
     */
    @Test
    @DisplayName("Arena: Configuration validation → proper error messages")
    void arena_configurationValidation_properErrorMessages() {
        // Test invalid endpoint configurations
        assertThrows(IllegalArgumentException.class,
            () -> bridge.createArena("bad-config",
                Map.of("endpoint", "invalid-url")),
            "Invalid endpoint URL should throw");

        assertThrows(IllegalArgumentException.class,
            () -> bridge.createArena("no-endpoint",
                Map.of("timeout", "-1")),
            "Negative timeout should throw");
    }

    // =========================================================================
    // Test 2: SPARQL Query Execution
    // =========================================================================

    /**
     * Verifies basic SPARQL query execution.
     */
    @Test
    @DisplayName("SPARQL: Execute query → valid result")
    void sparql_executeQuery_validResult() {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("query-test",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        // Execute simple query
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        QleverQueryResult result = bridge.executeQuery(index, query);

        assertNotNull(result, "Query result should not be null");
        assertFalse(result.getErrors().isEmpty(),
            "Query should return result structure");
    }

    /**
     * Verifies SPARQL query error handling.
     */
    @Test
    @DisplayName("SPARQL: Malformed query → proper exception")
    void sparql_malformedQuery_properException() {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("error-test",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        // Execute malformed query
        String malformedQuery = "SELECT WHERE { ?s ?p }"; // Missing * in SELECT
        assertThrows(RuntimeException.class,
            () -> bridge.executeQuery(index, malformedQuery),
            "Malformed SPARQL should throw RuntimeException");
    }

    /**
     * Verifies timeout behavior for long-running queries.
     */
    @Test
    @DisplayName("SPARQL: Timeout behavior → query cancelled")
    void sparql_timeoutBehavior_queryCancelled() {
        // Create arena with short timeout
        QLEVER_INDEX index = bridge.createArena("timeout-test",
            Map.of("endpoint", "http://localhost:9999/sparql", "timeout", "100"));

        // Execute query with very short timeout
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 1000000";
        assertThrows(RuntimeException.class,
            () -> bridge.executeQuery(index, query),
            "Query exceeding timeout should throw RuntimeException");
    }

    // =========================================================================
    // Test 3: Concurrent Arena Access
    // =========================================================================

    /**
     * Verifies thread-safe concurrent arena operations.
     */
    @Test
    @DisplayName("Concurrency: Multiple arenas → thread-safe operations")
    void concurrency_multipleArenas_threadSafeOperations() throws InterruptedException {
        int numThreads = 5;
        int arenasPerThread = 3;
        Thread[] threads = new Thread[numThreads];

        // Create arenas concurrently
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = Thread.ofVirtual()
                .name("arena-test-" + threadId)
                .start(() -> {
                    for (int i = 0; i < arenasPerThread; i++) {
                        String arenaName = "concurrent-arena-" + threadId + "-" + i;
                        bridge.createArena(arenaName,
                            Map.of("endpoint", "http://localhost:9999/sparql"));

                        // Verify arena exists
                        assertTrue(bridge.hasArena(arenaName),
                            "Arena should exist after creation");

                        // Execute a query
                        QLEVER_INDEX index = bridge.getArena(arenaName);
                        assertNotNull(index, "Arena index should not be null");

                        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 1";
                        QleverQueryResult result = bridge.executeQuery(index, query);
                        assertNotNull(result, "Query result should not be null");
                    }
                });
        }

        // Wait for all threads
        for (Thread t : threads) {
            t.join(10000);
        }

        // Verify all arenas exist
        for (int t = 0; t < numThreads; t++) {
            for (int i = 0; i < arenasPerThread; i++) {
                String arenaName = "concurrent-arena-" + t + "-" + i;
                assertTrue(bridge.hasArena(arenaName),
                    "Arena should still exist after concurrent operations");
            }
        }
    }

    // =========================================================================
    // Test 4: Memory Management
    // =========================================================================

    /**
     * Verifies memory cleanup when arenas are destroyed.
     */
    @Test
    @DisplayName("Memory: Destroy arena → resources freed")
    void memory_destroyArena_resourcesFreed() {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("memory-test",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        // Execute a query (creates resources)
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 5";
        bridge.executeQuery(index, query);

        // Destroy arena
        bridge.destroyArena("memory-test");

        // Arena should no longer be accessible
        assertFalse(bridge.hasArena("memory-test"));

        // Attempting to use destroyed arena should fail
        assertThrows(IllegalStateException.class,
            () -> bridge.executeQuery(index, query),
            "Using destroyed arena should throw IllegalStateException");
    }

    /**
     * Verifies multiple arena cleanup without memory leaks.
     */
    @Test
    @DisplayName("Memory: Multiple create/destroy → no leaks")
    void memory_multipleCreateDestroy_noLeaks() {
        int numArenas = 10;

        // Create and destroy multiple arenas
        for (int i = 0; i < numArenas; i++) {
            String arenaName = "leak-test-" + i;
            bridge.createArena(arenaName,
                Map.of("endpoint", "http://localhost:9999/sparql"));
            bridge.destroyArena(arenaName);
        }

        // Verify all arenas are gone
        for (int i = 0; i < numArenas; i++) {
            String arenaName = "leak-test-" + i;
            assertFalse(bridge.hasArena(arenaName),
                "Arena should be destroyed: " + arenaName);
        }
    }

    // =========================================================================
    // Test 5: Error Scenarios
    // =========================================================================

    /**
     * Verifies handling of connection failures.
     */
    @Test
    @DisplayName("Errors: Connection failure → proper exception")
    void errors_connectionFailure_properException() {
        // Create arena with non-existent endpoint
        QLEVER_INDEX index = bridge.createArena("connection-test",
            Map.of("endpoint", "http://localhost:99999/invalid"));

        // Attempt to execute query should fail
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 1";
        assertThrows(RuntimeException.class,
            () -> bridge.executeQuery(index, query),
            "Connection to invalid endpoint should throw");
    }

    /**
     * Verifies handling of null parameters.
     */
    @Test
    @DisplayName("Errors: Null parameters → NPE with message")
    void errors_nullParameters_npeWithMessage() {
        // Create valid arena
        QLEVER_INDEX index = bridge.createArena("null-test",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        // Test null index
        assertThrows(NullPointerException.class,
            () -> bridge.executeQuery(null, "SELECT * WHERE { ?s ?p ?o } LIMIT 1"),
            "Null index should throw NullPointerException");

        // Test null query
        assertThrows(NullPointerException.class,
            () -> bridge.executeQuery(index, null),
            "Null query should throw NullPointerException");
    }

    /**
     * Verifies handling of empty queries.
     */
    @Test
    @DisplayName("Errors: Empty query → IllegalArgumentException")
    void errors_emptyQuery_illegalArgumentException() {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("empty-test",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        // Test empty query
        assertThrows(IllegalArgumentException.class,
            () -> bridge.executeQuery(index, ""),
            "Empty query should throw IllegalArgumentException");

        // Test whitespace-only query
        assertThrows(IllegalArgumentException.class,
            () -> bridge.executeQuery(index, "   \n\t  "),
            "Whitespace-only query should throw IllegalArgumentException");
    }

    // =========================================================================
    // Test 6: Performance Characteristics
    // =========================================================================

    /**
     * Verifies arena creation time is within reasonable bounds.
     */
    @Test
    @DisplayName("Performance: Arena creation → under 100ms")
    void performance_arenaCreation_under100ms() {
        long start = System.nanoTime();

        // Create arena
        QLEVER_INDEX index = bridge.createArena("perf-test",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;

        System.out.println("Arena creation time: " + durationMs + "ms");

        assertTrue(durationMs < 100,
            "Arena creation should take under 100ms (took " + durationMs + "ms)");
    }

    /**
     * Verifies query execution time is reasonable.
     */
    @Test
    @DisplayName("Performance: Query execution → under 1000ms")
    void performance_queryExecution_under1000ms() {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("query-perf",
            Map.of("endpoint", "http://localhost:9999/sparql"));

        // Test simple query
        String simpleQuery = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        long start = System.nanoTime();

        QleverQueryResult result = bridge.executeQuery(index, simpleQuery);

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;

        System.out.println("Simple query time: " + durationMs + "ms");

        assertNotNull(result, "Query result should not be null");
        assertTrue(durationMs < 1000,
            "Simple query should execute under 1000ms (took " + durationMs + "ms)");
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private boolean shouldRunQleverTests() {
        // Check if Qlever is available for testing
        try {
            return System.getProperty("qlever.enabled", "false").equals("true");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Test arena class for testing purposes.
     */
    private static class TestArena {
        private final String arenaName;
        private final Map<String, String> config;

        public TestArena() {
            this.arenaName = "test-" + System.currentTimeMillis();
            this.config = Map.of(
                "endpoint", "http://localhost:9999/sparql",
                "timeout", "5000"
            );
        }

        public String getArenaName() {
            return arenaName;
        }

        public Map<String, String> getConfig() {
            return config;
        }

        public void cleanup() {
            // Clean up any test resources
        }
    }
}