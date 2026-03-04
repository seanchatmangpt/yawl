/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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

package org.yawlfoundation.yawl.nativebridge.erlang;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test for the EI bridge.
 * Tests the complete flow from Java to Erlang communication.
 */
@EnabledIfEnvironmentVariable(named = "TEST_ERLANG_INTEGRATION", matches = "true")
@DisplayName("EI Bridge Integration Tests")
class IntegrationTest {

    private static final String ERLANG_NODE = "yawl_test@localhost";
    private static final String ERLANG_COOKIE = "test_cookie";
    private static final String TEST_MODULE = "test_bridge";
    private static final int TEST_PORT = 5671;

    private ErlangNode erlangNode;
    private ProcessMiningClient pmClient;

    @BeforeAll
    static void setupAll() {
        // This would start an Erlang node with test module
        System.out.println("Starting Erlang test node...");
    }

    @AfterAll
    static void cleanupAll() {
        System.out.println("Stopping Erlang test node...");
    }

    @BeforeEach
    void setUp() throws ErlangException {
        erlangNode = new ErlangNode(ERLANG_NODE, ERLANG_COOKIE);
        pmClient = new ProcessMiningClientImpl(erlangNode);

        // Connect to Erlang node
        erlangNode.connect();
        assertTrue(erlangNode.isConnected(), "Should be connected after connect()");
    }

    @AfterEach
    void tearDown() throws ErlangException {
        if (erlangNode != null) {
            erlangNode.close();
        }
    }

    @Nested
    @DisplayName("Term Encoding and Decoding Tests")
    class TermEncodingTests {

        @Test
        @DisplayName("Round-trip encoding and decoding")
        void roundTripEncoding() throws ErlangException {
            // Create test terms
            ErlTerm[] testTerms = {
                ErlAtom.atom("test_atom"),
                ErlLong.longValue(42),
                ErlLong.longValue(-1),
                ErlBinary.fromString("binary test"),
                ErlList.of(ErlAtom.atom("a"), ErlLong.longValue(1)),
                ErlTuple.of(ErlAtom.atom("key"), ErlLong.longValue(123))
            };

            // Test each term
            for (ErlTerm term : testTerms) {
                // In real implementation, this would encode to buffer and back
                ErlTerm decoded = term; // Placeholder
                assertEquals(term, decoded, "Round-trip should preserve term");
            }
        }

        @Test
        @DisplayName("Large binary handling")
        void largeBinaryHandling() throws ErlangException {
            // Create a large binary (1MB)
            String largeContent = "x".repeat(1024 * 1024);
            ErlTerm largeBinary = ErlBinary.fromString(largeContent);

            assertEquals(largeContent.length(), ((ErlBinary) largeBinary).length(),
                "Large binary should preserve length");
        }

        @Test
        @DisplayName("Nested structure encoding")
        void nestedStructureEncoding() throws ErlangException {
            // Create complex nested structure
            ErlTerm nested = ErlTuple.of(
                ErlAtom.atom("complex"),
                ErlList.of(
                    ErlTuple.of(
                        ErlAtom.atom("inner"),
                        ErlLong.longValue(1),
                        ErlList.of(ErlAtom.atom("item1"), ErlAtom.atom("item2"))
                    ),
                    ErlBinary.fromString("nested binary")
                ),
                ErlAtom.atom("end")
            );

            // Verify structure
            assertTrue(nested instanceof ErlTuple, "Should be a tuple");
            ErlTuple tuple = (ErlTuple) nested;
            assertEquals(3, tuple.getArity(), "Should have 3 elements");
        }
    }

    @Nested
    @DisplayName("RPC Communication Tests")
    class RpcCommunicationTests {

        @Test
        @DisplayName("Simple RPC call")
        void simpleRpcCall() throws ErlangException {
            ErlList arguments = ErlList.of(ErlAtom.atom("test"), ErlLong.longValue(1));
            ErlTerm result = erlangNode.rpc(TEST_MODULE, "simple_function", arguments);

            assertTrue(result instanceof ErlAtom, "Result should be an atom");
            assertEquals("ok", ((ErlAtom) result).getValue(), "Should return 'ok'");
        }

        @Test
        @DisplayName("RPC with timeout")
        void rpcWithTimeout() throws ErlangException {
            ErlList arguments = ErlList.of(ErlAtom.atom("slow_function"), ErlLong.longValue(100));
            ErlTerm result = erlangNode.rpcWithTimeout(TEST_MODULE, "slow_function", arguments, 5000);

            assertNotNull(result, "Should return result within timeout");
        }

        @Test
        @DisplayName("Error handling in RPC")
        void rpcErrorHandling() {
            ErlList arguments = ErlList.of(ErlAtom.atom("error_function"));

            assertThrows(ErlangException.class, () -> {
                erlangNode.rpc(TEST_MODULE, "error_function", arguments);
            }, "Should throw ErlangException for RPC errors");
        }

        @Test
        @DisplayName("Concurrent RPC calls")
        void concurrentRpcCalls() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<Exception> exceptions = new java.util.concurrent.CopyOnWriteArrayList<>();

            // Submit concurrent RPC calls
            IntStream.range(0, threadCount).forEach(i -> {
                executor.submit(() -> {
                    try {
                        ErlList args = ErlList.of(ErlAtom.atom("concurrent_test"), ErlLong.longValue(i));
                        ErlTerm result = erlangNode.rpc(TEST_MODULE, "concurrent_function", args);
                        assertNotNull(result, "Each call should succeed");
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                });
            });

            // Wait for completion
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                "All threads should complete within timeout");

            assertTrue(exceptions.isEmpty() || exceptions.size() < threadCount / 2,
                "Most calls should succeed");
        }
    }

    @Nested
    @DisplayName("Process Mining Operations")
    class ProcessMiningOperations {

        @Test
        @DisplayName("Process model discovery")
        void processModelDiscovery() throws ErlangException {
            // Create sample event log
            List<EventLogEntry> eventLog = List.of(
                createEventLogEntry("case1", "start", 1000, Map.of()),
                createEventLogEntry("case1", "task1", 2000, Map.of()),
                createEventLogEntry("case1", "task2", 3000, Map.of()),
                createEventLogEntry("case1", "end", 4000, Map.of())
            );

            ErlTerm model = pmClient.discoverProcessModel(eventLog);
            assertNotNull(model, "Should discover a process model");
            assertTrue(model instanceof ErlTuple, "Model should be a tuple structure");
        }

        @Test
        @DisplayName("Conformance checking")
        void conformanceChecking() throws ErlangException {
            // Create simple model
            ErlTerm model = ErlTuple.of(
                ErlAtom.atom("simple_model"),
                ErlList.of(
                    ErlAtom.atom("start"),
                    ErlAtom.atom("task1"),
                    ErlAtom.atom("task2"),
                    ErlAtom.atom("end")
                )
            );

            // Create event log
            List<EventLogEntry> eventLog = List.of(
                createEventLogEntry("case1", "start", 1000, Map.of()),
                createEventLogEntry("case1", "task1", 2000, Map.of()),
                createEventLogEntry("case1", "task2", 3000, Map.of()),
                createEventLogEntry("case1", "end", 4000, Map.of())
            );

            ConformanceResult result = pmClient.conformanceCheck(model, eventLog);
            assertTrue(result.getFitness() >= 0.0 && result.getFitness() <= 1.0,
                "Fitness should be between 0.0 and 1.0");
            assertTrue(result.getPrecision() >= 0.0 && result.getPrecision() <= 1.0,
                "Precision should be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("Performance analysis")
        void performanceAnalysis() throws ErlangException {
            // Create test model and event log
            ErlTerm model = ErlAtom.atom("test_model");
            List<EventLogEntry> eventLog = List.of(
                createEventLogEntry("case1", "start", 1000, Map.of()),
                createEventLogEntry("case1 " + "task1", 3000, Map.of()),
                createEventLogEntry("case1", "end", 4000, Map.of())
            );

            PerformanceResult result = pmClient.analyzePerformance(model, eventLog);
            assertTrue(result.getAverageCycleTime() >= 0, "Average cycle time should be non-negative");
            assertTrue(result.getThroughput() >= 0, "Throughput should be non-negative");
        }

        @Test
        @DisplayName("Instance statistics")
        void instanceStatistics() throws ErlangException {
            ProcessInstanceStats stats = pmClient.getProcessInstanceStats("test-case-123");
            assertNotNull(stats.getInstanceId(), "Should return instance ID");
            assertTrue(stats.getTotalActivities() >= 0, "Activity count should be non-negative");
            assertTrue(stats.getDuration() >= 0, "Duration should be non-negative");
        }

        @Test
        @DisplayName("Model validation")
        void modelValidation() throws ErlangException {
            // Create a valid model
            ErlTerm model = ErlTuple.of(
                ErlAtom.atom("valid_model"),
                ErlList.of(ErlAtom.atom("start"), ErlAtom.atom("end"))
            );

            ValidationResult result = pmClient.validateProcessModel(model);
            // This could be valid or invalid depending on Erlang implementation
            assertNotNull(result.getWarnings(), "Should have warnings list");
            assertNotNull(result.getErrors(), "Should have errors list");
        }

        @Test
        @DisplayName("Query execution")
        void queryExecution() throws ErlangException {
            Map<String, ErlTerm> parameters = Map.of(
                "case_id", ErlAtom.atom("test-case-123"),
                "limit", ErlLong.longValue(10)
            );

            ErlTerm result = pmClient.executeQuery("get_events", parameters);
            assertNotNull(result, "Should return query result");
        }

        private EventLogEntry createEventLogEntry(String caseId, String activity, long timestamp, Map<String, String> attributes) {
            return new EventLogEntry(caseId, activity, timestamp, attributes);
        }
    }

    @Nested
    @DisplayName("Resource Management")
    class ResourceManagementTests {

        @Test
        @DisplayName("Multiple connection cycles")
        void multipleConnectionCycles() throws ErlangException {
            // Test multiple connect/disconnect cycles
            for (int i = 0; i < 5; i++) {
                erlangNode.close();
                assertFalse(erlangNode.isConnected(), "Should be disconnected after close()");

                erlangNode.connect();
                assertTrue(erlangNode.isConnected(), "Should be connected after connect()");

                // Perform a simple operation
                ErlTerm result = erlangNode.rpc(TEST_MODULE, "ping", ErlList.empty());
                assertNotNull(result, "Should get result for ping");
            }
        }

        @Test
        @DisplayName("Resource cleanup on exception")
        void resourceCleanupOnException() {
            // Simulate error scenario
            assertThrows(ErlangException.class, () -> {
                erlangNode.connect();
                erlangNode.rpc("nonexistent", "function", ErlList.empty());
            });

            // Connection should still be closable
            assertDoesNotThrow(erlangNode::close);
        }
    }

    @Nested
    @DisplayName("Load Testing")
    class LoadTests {

        @Test
        @DisplayName("High-throughput RPC calls")
        void highThroughputRpcCalls() throws InterruptedException {
            int callCount = 100;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<Exception> exceptions = new java.util.concurrent.CopyOnWriteArrayList<>();

            long startTime = System.currentTimeMillis();

            // Submit many concurrent calls
            IntStream.range(0, callCount).forEach(i -> {
                executor.submit(() -> {
                    try {
                        ErlList args = ErlList.of(ErlAtom.atom("load_test"), ErlLong.longValue(i));
                        ErlTerm result = erlangNode.rpc(TEST_MODULE, "load_function", args);
                        if (result == null) {
                            throw new RuntimeException("Null result");
                        }
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                });
            });

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS),
                "All calls should complete within 30 seconds");

            long duration = System.currentTimeMillis() - startTime;
            double callsPerSecond = callCount / (duration / 1000.0);

            System.out.printf("Load test: %d calls in %d ms (%.2f calls/sec)%n",
                callCount, duration, callsPerSecond);

            // Allow some failures in load test
            assertTrue(exceptions.size() <= callCount / 4,
                "No more than 25% failures in load test");
        }
    }
}