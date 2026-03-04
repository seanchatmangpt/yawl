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
import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for ErlangNode - Layer 2 encoding/decoding and error handling.
 *
 * <p>This test class focuses on the JVM Domain (Layer 2) API boundary, ensuring:
 * <ul>
 *   <li>Proper Erlang term encoding/decoding</li>
 *   <li>Error handling and exception conversion</li>
 *   <li>Connection management</li>
 *   <li>RPC call handling</li>
 * </ul>
 *
 * @see <a href="../ErlangNode.java">ErlangNode Implementation</a>
 */
@Tag("unit")
@Tag("erlang")
class ErlangNodeTest {

    private ErlangTestNode testNode;
    private ErlangNode erlangNode;

    @BeforeEach
    void setup() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not available - skipping tests");

        testNode = ErlangTestNode.start();
        testNode.awaitReady();

        erlangNode = new ErlangNode(testNode.NODE_NAME);
    }

    @AfterEach
    void cleanup() {
        if (erlangNode != null) {
            erlangNode.close();
        }
        if (testNode != null) {
            testNode.close();
        }
    }

    // =========================================================================
    // Test 1: Connection Management
    // =========================================================================

    /**
     * Verifies successful connection to Erlang node.
     */
    @Test
    @DisplayName("Connection: Connect to node → success")
    void connection_connectToNode_success() throws ErlangConnectionException {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        assertTrue(erlangNode.isConnected(),
            "Node should be connected after successful connect");
    }

    /**
     * Verifies connection failure with wrong credentials.
     */
    @Test
    @DisplayName("Connection: Wrong cookie → ErlangConnectionException")
    void connection_wrongCookie_erlangConnectionException() {
        assertThrows(ErlangConnectionException.class,
            () -> erlangNode.connect(testNode.NODE_NAME, "wrong-cookie"),
            "Wrong cookie should throw ErlangConnectionException");

        assertFalse(erlangNode.isConnected(),
            "Node should not be connected with wrong cookie");
    }

    /**
     * Verifies connection failure with invalid node name.
     */
    @Test
    @DisplayName("Connection: Invalid node name → ErlangConnectionException")
    void connection_invalidNodeName_erlangConnectionException() {
        assertThrows(ErlangConnectionException.class,
            () -> erlangNode.connect("invalid@node", testNode.COOKIE),
            "Invalid node name should throw ErlangConnectionException");

        assertFalse(erlangNode.isConnected(),
            "Node should not be connected with invalid node name");
    }

    // =========================================================================
    // Test 2: Erlang Term Encoding/Decoding
    // =========================================================================

    /**
     * Verifies encoding and decoding of Erlang atoms.
     */
    @Test
    @DisplayName("Encoding: Atom → encode/decode roundtrip")
    void encoding_atom_encodeDecodeRoundtrip() throws Exception {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        // Create atom
        ErlAtom atom = new ErlAtom("test_atom");

        // Encode to binary
        byte[] encoded = erlangNode.encodeTerm(atom);

        // Decode from binary
        ErlAtom decoded = (ErlAtom) erlangNode.decodeTerm(encoded);

        assertEquals(atom.getValue(), decoded.getValue(),
            "Encoded/decoded atom should have same value");
        assertEquals(atom, decoded,
            "Encoded/decoded atom should be equal");
    }

    /**
     * Verifies encoding and decoding of complex data structures.
     */
    @Test
    @DisplayName("Encoding: List of maps → encode/decode roundtrip")
    void encoding_listOfMaps_encodeDecodeRoundtrip() throws Exception {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        // Create complex data structure
        List<Map<String, Object>> complexData = List.of(
            Map.of("activity", "Task_A", "status", "completed", "timestamp", "2024-01-01T10:00:00Z"),
            Map.of("activity", "Task_B", "status", "pending", "timestamp", "2024-01-01T11:00:00Z")
        );

        // Encode to binary
        byte[] encoded = erlangNode.encodeTerm(complexData);

        // Decode from binary
        List<Map<String, Object>> decoded = (List<Map<String, Object>>) erlangNode.decodeTerm(encoded);

        assertEquals(complexData.size(), decoded.size(),
            "Decoded list should have same size");

        // Verify first element
        Map<String, Object> original = complexData.get(0);
        Map<String, Object> result = decoded.get(0);

        assertEquals(original.get("activity"), result.get("activity"),
            "Activity field should match");
        assertEquals(original.get("status"), result.get("status"),
            "Status field should match");
        assertEquals(original.get("timestamp"), result.get("timestamp"),
            "Timestamp field should match");
    }

    /**
     * Verifies encoding of null values throws exception.
     */
    @Test
    @DisplayName("Encoding: Null term → IllegalArgumentException")
    void encoding_nullTerm_illegalArgumentException() {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        assertThrows(IllegalArgumentException.class,
            () -> erlangNode.encodeTerm(null),
            "Encoding null should throw IllegalArgumentException");
    }

    // =========================================================================
    // Test 3: RPC Call Handling
    // =========================================================================

    /**
     * Verifies successful RPC call.
     */
    @Test
    @DisplayName("RPC: Successful call → proper result")
    void rpc_successfulCall_properResult() throws ErlangConnectionException, ErlangRpcException {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        // Simple echo call
        ErlAtom result = erlangNode.rpc("yawl_echo", "echo", List.of(new ErlAtom("hello")));

        assertEquals("hello", result.getValue(),
            "Echo call should return same atom");
    }

    /**
     * Verifies RPC call with wrong module throws exception.
     */
    @Test
    @DisplayName("RPC: Wrong module → ErlangRpcException")
    void rpc_wrongModule_erlangRpcException() {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        assertThrows(ErlangRpcException.class,
            () -> erlangNode.rpc("nonexistent_module", "test", List.of()),
            "RPC to nonexistent module should throw ErlangRpcException");
    }

    /**
     * Verifies RPC call with wrong function throws exception.
     */
    @Test
    @DisplayName("RPC: Wrong function → ErlangRpcException")
    void rpc_wrongFunction_erlangRpcException() {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        assertThrows(ErlangRpcException.class,
            () -> erlangNode.rpc("yawl_echo", "nonexistent_function", List.of()),
            "RPC to nonexistent function should throw ErlangRpcException");
    }

    /**
     * Verifies RPC call timeout behavior.
     */
    @Test
    @DisplayName("RPC: Timeout → ErlangRpcException")
    void rpc_timeout_erlangRpcException() throws ErlangConnectionException {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        // Set short timeout
        erlangNode.setTimeout(100);

        assertThrows(ErlangRpcException.class,
            () -> erlangNode.rpc("yawl_echo", "sleep", List.of(new ErlAtom("200"))),
            "RPC exceeding timeout should throw ErlangRpcException");
    }

    // =========================================================================
    // Test 4: Error Handling
    // =========================================================================

    /**
     * Verifies proper error message extraction from exceptions.
     */
    @Test
    @DisplayName("Errors: Exception messages → informative")
    void errors_exceptionMessages_informative() throws ErlangConnectionException {
        // Test connection error message
        ErlangConnectionException ex = assertThrows(ErlangConnectionException.class,
            () -> erlangNode.connect("invalid@node", "wrong-cookie"),
            "Invalid connection should throw ErlangConnectionException");

        assertNotNull(ex.getMessage(),
            "Exception message should not be null");
        assertTrue(ex.getMessage().length() > 10,
            "Exception message should be informative");
        assertTrue(ex.getMessage().contains("connect") ||
                   ex.getMessage().contains("failed") ||
                   ex.getMessage().contains("error"),
            "Exception message should indicate connection failure");
    }

    /**
     * Verifies RPC exception contains error information.
     */
    @Test
    @DisplayName("Errors: RPC exception → error info")
    void errors_rpcException_errorInfo() throws ErlangConnectionException, ErlangRpcException {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        // Call function that throws error
        ErlangRpcException ex = assertThrows(ErlangRpcException.class,
            () -> erlangNode.rpc("yawl_echo", "error", List.of(new ErlAtom("test_error"))),
            "Error function should throw ErlangRpcException");

        assertNotNull(ex.getMessage(),
            "Exception message should not be null");
        assertTrue(ex.getMessage().contains("error") ||
                   ex.getMessage().contains("exception"),
            "Exception message should indicate error condition");
    }

    // =========================================================================
    // Test 5: Concurrent Access
    // =========================================================================

    /**
     * Verifies thread-safe concurrent RPC calls.
     */
    @Test
    @DisplayName("Concurrency: Multiple RPC calls → thread-safe")
    void concurrency_multipleRpcCalls_threadSafe() throws InterruptedException {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        int numThreads = 10;
        int callsPerThread = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Submit concurrent RPC calls
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < callsPerThread; i++) {
                        String value = "thread-" + threadId + "-call-" + i;
                        ErlAtom result = erlangNode.rpc("yawl_echo", "echo", List.of(new ErlAtom(value)));
                        assertEquals(value, result.getValue(),
                            "Echo result should match input");
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS),
            "All threads should complete within 30 seconds");

        System.out.println("Concurrent RPC Test Results:");
        System.out.println("  Successes: " + successCount.get());
        System.out.println("  Errors: " + errorCount.get());
        System.out.println("  Expected: " + (numThreads * callsPerThread));

        assertEquals(numThreads * callsPerThread, successCount.get(),
            "All RPC calls should succeed");
        assertEquals(0, errorCount.get(),
            "No errors should occur during concurrent access");
    }

    /**
     * Verifies concurrent connections work correctly.
     */
    @Test
    @DisplayName("Concurrency: Multiple connections → isolated")
    void concurrency_multipleConnections_isolated() throws ErlangConnectionException, InterruptedException {
        int numConnections = 5;
        ErlangNode[] nodes = new ErlangNode[numConnections];

        // Create multiple connections
        for (int i = 0; i < numConnections; i++) {
            nodes[i] = new ErlangNode(testNode.NODE_NAME);
            nodes[i].connect(testNode.NODE_NAME, testNode.COOKIE);
            assertTrue(nodes[i].isConnected(),
                "Connection " + i + " should be connected");
        }

        // Test concurrent operations on all connections
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numConnections);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numConnections; i++) {
            final int connectionId = i;
            executor.submit(() -> {
                try {
                    ErlAtom result = nodes[connectionId].rpc("yawl_echo", "echo",
                        List.of(new ErlAtom("connection-" + connectionId)));
                    assertEquals("connection-" + connectionId, result.getValue(),
                        "Echo result should match input");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Connection " + connectionId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS),
            "All connections should complete within 10 seconds");

        assertEquals(numConnections, successCount.get(),
            "All concurrent connections should succeed");

        // Cleanup
        for (ErlangNode node : nodes) {
            node.close();
        }
    }

    // =========================================================================
    // Test 6: Performance Characteristics
    // =========================================================================

    /**
     * Verifies encoding/decoding performance.
     */
    @Test
    @DisplayName("Performance: Encoding/decoding → under 1µs")
    void performance_encodingDecoding_under1us() throws Exception {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        ErlAtom atom = new ErlAtom("performance_test");

        // Measure encoding time
        long startEncode = System.nanoTime();
        byte[] encoded = erlangNode.encodeTerm(atom);
        long endEncode = System.nanoTime();

        // Measure decoding time
        long startDecode = System.nanoTime();
        ErlAtom decoded = (ErlAtom) erlangNode.decodeTerm(encoded);
        long endDecode = System.nanoTime();

        long encodeTimeNs = endEncode - startEncode;
        long decodeTimeNs = endDecode - startDecode;

        System.out.println("Encoding time: " + (encodeTimeNs / 1000.0) + "µs");
        System.out.println("Decoding time: " + (decodeTimeNs / 1000.0) + "µs");

        assertTrue(encodeTimeNs < 1000,
            "Encoding should take under 1000ns (took " + encodeTimeNs + "ns)");
        assertTrue(decodeTimeNs < 1000,
            "Decoding should take under 1000ns (took " + decodeTimeNs + "ns)");
    }

    /**
     * Verifies RPC call performance.
     */
    @Test
    @DisplayName("Performance: RPC call → under 1000µs")
    void performance_rpcCall_under1000us() throws ErlangConnectionException, ErlangRpcException {
        erlangNode.connect(testNode.NODE_NAME, testNode.COOKIE);

        ErlAtom atom = new ErlAtom("performance_test");

        // Measure RPC time
        long start = System.nanoTime();
        ErlAtom result = erlangNode.rpc("yawl_echo", "echo", List.of(atom));
        long end = System.nanoTime();

        long durationNs = end - start;
        long durationUs = durationNs / 1000;

        System.out.println("RPC call time: " + durationUs + "µs");

        assertEquals("performance_test", result.getValue(),
            "Echo result should match input");
        assertTrue(durationUs < 1000,
            "RPC call should take under 1000µs (took " + durationUs + "µs)");
    }

    // =========================================================================
    // Helper Classes and Methods
    // =========================================================================

    /**
     * Helper class for thread-safe counters.
     */
    private static class AtomicInteger {
        private int value = 0;

        public synchronized void incrementAndGet() {
            value++;
        }

        public synchronized int get() {
            return value;
        }

        public synchronized void set(int value) {
            this.value = value;
        }
    }

    /**
     * Alternative implementation of AtomicInteger for testing.
     */
    private static class CountDownLatch {
        private int count;

        public CountDownLatch(int count) {
            this.count = count;
        }

        public synchronized void countDown() {
            count--;
            if (count == 0) {
                notifyAll();
            }
        }

        public synchronized void await() throws InterruptedException {
            while (count > 0) {
                wait();
            }
        }

        public synchronized boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            long timeoutMillis = unit.toMillis(timeout);
            long startTime = System.currentTimeMillis();

            while (count > 0) {
                long remaining = timeoutMillis - (System.currentTimeMillis() - startTime);
                if (remaining <= 0) {
                    return false;
                }
                wait(remaining);
            }
            return true;
        }
    }

    /**
     * Alternative implementation of Executors for testing.
     */
    private static class Executors {
        public static ExecutorService newVirtualThreadPerTaskExecutor() {
            return new VirtualThreadExecutor();
        }
    }

    private static class VirtualThreadExecutor implements ExecutorService {
        private final ExecutorService delegate = Executors.newCachedThreadPool();

        @Override
        public void execute(Runnable command) {
            Thread.ofVirtual()
                .name("virtual-thread-" + Thread.currentThread().threadId())
                .start(command);
        }

        // Additional ExecutorService methods would be implemented here
        // For testing purposes, we only need execute()
        public void shutdown() {
            throw new UnsupportedOperationException(
                "VirtualThreadExecutor.shutdown() not implemented - " +
                "This is a test-only executor that runs tasks in virtual threads"
            );
        }
        public List<Runnable> shutdownNow() { return List.of(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return false; }
        public <T> Future<T> submit(Callable<T> task) { return null; }
        public <T> Future<T> submit(Runnable task, T result) { return null; }
        public Future<?> submit(Runnable task) { return null; }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) { return List.of(); }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { return List.of(); }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException { return null; }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return null; }
    }
}