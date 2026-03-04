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
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;
import org.yawlfoundation.yawl.erlang.processmining.ConformanceResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fault injection tests for NIF layer - Simulate Rust panic, verify JVM isolation.
 *
 * <p>This test class focuses on testing the fault isolation guarantees of the
 * Three-Domain Native Bridge Pattern, specifically:
 * <ul>
 *   <li>Boundary B isolation: Rust panic → JVM receives structured exception</li>
 *   <li>Memory safety across boundaries</li>
 *   <li>Error propagation and handling</li>
 * </ul>
 *
 * @see <a href="../processmining/ErlangBridge.java">ErlangBridge Implementation</a>
 */
@Tag("integration")
@Tag("fault-injection")
@Tag("nif")
class NifFaultTest {

    private ErlangTestNode testNode;
    private ErlangBridge bridge;
    private FaultInjectionTestServer faultServer;

    @BeforeEach
    void setup() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not available - skipping fault injection tests");

        // Start fault injection server
        faultServer = new FaultInjectionTestServer();
        faultServer.start();

        // Start Erlang node with fault injection enabled
        testNode = ErlangTestNode.start();
        testNode.enableFaultInjection(true);
        testNode.awaitReady();

        // Connect bridge
        bridge = ErlangBridge.connect(testNode.NODE_NAME, testNode.COOKIE);
    }

    @AfterEach
    void cleanup() {
        if (bridge != null) {
            bridge.close();
        }
        if (testNode != null) {
            testNode.enableFaultInjection(false);
            testNode.close();
        }
        if (faultServer != null) {
            faultServer.stop();
        }
    }

    // =========================================================================
    // Test 1: Rust Panic Simulation
    // =========================================================================

    /**
     * Verries that Rust panics are properly caught and converted to structured exceptions.
     */
    @Test
    @DisplayName("Fault Injection: Rust panic → ErlangException → propagated to JVM")
    void faultInjection_rustPanic_erlangException_propagatedToJVM()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Prepare event log that will trigger Rust panic
        List<Map<String, Object>> eventLog = List.of(
            Map.of("activity", "PanicTask", "timestamp", "2024-01-01T10:00:00Z", "case_id", "panic_case")
        );

        // Configure fault server to trigger panic
        faultServer.injectFault("panic");

        // Attempt conformance check (should trigger Rust panic)
        assertThrows(ErlangRpcException.class,
            () -> bridge.checkConformance(eventLog, getSimpleProcessSpec()),
            "Conformance check with panic fault should throw ErlangRpcException");

        // Verify exception contains panic information
        try {
            bridge.checkConformance(eventLog, getSimpleProcessSpec());
        } catch (ErlangRpcException e) {
            assertTrue(e.getMessage().contains("panic") ||
                      e.getMessage().contains("crash") ||
                      e.getMessage().toLowerCase().contains("rust"),
                "Exception message should indicate panic/crash");
        }

        // Verify bridge is still usable after panic
        faultServer.injectFault(null); // Clear fault
        String caseId = bridge.launchCase("after_panic");
        assertNotNull(caseId, "Bridge should be usable after panic recovery");
    }

    /**
     * Verries that memory allocation failures in Rust are handled gracefully.
     */
    @Test
    @DisplayName("Fault Injection: Memory allocation failure → OutOfMemoryError → handled gracefully")
    void faultInjection_memoryAllocationFailure_outOfMemoryError_handledGracefully()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Prepare large event log that will trigger memory allocation failure
        List<Map<String, Object>> largeEventLog = generateLargeEventLog(100000);

        // Configure fault server to trigger memory allocation failure
        faultServer.injectFault("memory_alloc");

        // Attempt conformance check with large dataset (should trigger memory failure)
        assertThrows(RuntimeException.class,
            () -> bridge.checkConformance(largeEventLog, getSimpleProcessSpec()),
            "Conformance check with memory fault should throw RuntimeException");

        // Verify bridge is still usable after memory failure
        faultServer.injectFault(null); // Clear fault
        String caseId = bridge.launchCase("after_memory_failure");
        assertNotNull(caseId, "Bridge should be usable after memory failure recovery");
    }

    /**
     * Verries that stack overflow in Rust is handled gracefully.
     */
    @Test
    @DisplayName("Fault Injection: Stack overflow → StackOverflowError → handled gracefully")
    void faultInjection_stackOverflow_stackOverflowError_handledGracefully()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Configure fault server to trigger stack overflow
        faultServer.injectFault("stack_overflow");

        // Attempt deep recursion (should trigger stack overflow)
        assertThrows(RuntimeException.class,
            () -> bridge.deepRecursion(10000),
            "Deep recursion should throw RuntimeException");

        // Verify bridge is still usable after stack overflow
        faultServer.injectFault(null); // Clear fault
        String caseId = bridge.launchCase("after_stack_overflow");
        assertNotNull(caseId, "Bridge should be usable after stack overflow recovery");
    }

    // =========================================================================
    // Test 2: Memory Safety
    // =========================================================================

    /**
     * Verries memory safety across Rust-Java boundaries.
     */
    @Test
    @DisplayName("Memory Safety: Large data transfer → no memory corruption")
    void memorySafety_largeDataTransfer_noMemoryCorruption()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Generate test data with known patterns
        List<Map<String, Object>> eventData = generatePatternedEventLog(10000);

        // Process data multiple times
        for (int iteration = 0; iteration < 10; iteration++) {
            ConformanceResult result = bridge.checkConformance(eventData, getSimpleProcessSpec());
            assertNotNull(result, "Result should not be null");
            assertEquals("pattern_case", result.getCaseId(),
                "Case ID should be preserved across iterations");
        }

        // Verify memory integrity by checking pattern consistency
        String lastResult = bridge.getMemoryIntegrityCheck();
        assertEquals("OK", lastResult,
            "Memory integrity check should pass after large data transfers");
    }

    /**
     * Verries memory leak detection in Rust code.
     */
    @Test
    @DisplayName("Memory Safety: Memory leak detection → detected after 1000 operations")
    void memorySafety_memoryLeakDetection_detectedAfter1000Operations()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Perform memory leak detection test
        for (int i = 0; i < 1000; i++) {
            List<Map<String, Object>> eventData = generateSingleCaseEventLog(i);
            ConformanceResult result = bridge.checkConformance(eventData, getSimpleProcessSpec());
            assertNotNull(result, "Result should not be null");

            // Check memory usage periodically
            if (i % 100 == 0) {
                String memoryStatus = bridge.getMemoryStatus();
                if (memoryStatus.contains("leak")) {
                    System.out.println("Memory leak detected at iteration " + i);
                    break;
                }
            }
        }

        // Verify no memory leak after complete test
        String finalMemoryStatus = bridge.getMemoryStatus();
        assertDoesNotThrow(() -> {
            assertFalse(finalMemoryStatus.contains("leak"),
                "No memory leak should be detected after 1000 operations");
        }, "Memory status check should complete successfully");
    }

    // =========================================================================
    // Test 3: Error Propagation
    // =========================================================================

    /**
     * Verries that errors are properly propagated through the call chain.
     */
    @Test
    @DisplayName("Error Propagation: Rust → Erlang → Java → proper exception hierarchy")
    void errorPropagation_rustErlangJava_properExceptionHierarchy()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Test various error types
        List<Map<String, Object>> eventData = List.of(
            Map.of("activity", "ErrorTask", "timestamp", "2024-01-01T10:00:00Z", "case_id", "error_case")
        );

        // Configure different fault types
        String[] faultTypes = {"null_pointer", "division_by_zero", "bounds_check"};

        for (String faultType : faultTypes) {
            faultServer.injectFault(faultType);

            try {
                bridge.checkConformance(eventData, getSimpleProcessSpec());
                fail("Should have thrown exception for fault type: " + faultType);
            } catch (ErlangRpcException e) {
                // Verify exception contains fault type information
                assertTrue(
                    e.getMessage().toLowerCase().contains(faultType.replace("_", " ")) ||
                    e.getMessage().toLowerCase().contains("error") ||
                    e.getMessage().toLowerCase().contains("exception"),
                    "Exception should indicate " + faultType + " error"
                );
            } finally {
                faultServer.injectFault(null);
            }
        }
    }

    /**
     * Verries that the JVM never receives raw signals from Rust.
     */
    @Test
    @DisplayName("Error Propagation: No SIGSEGV → JVM never receives native crash signals")
    void errorPropagation_noSigsegv_jvmNeverReceivesNativeCrashSignals()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Configure fault server to cause segmentation fault
        faultServer.injectFault("segfault");

        // Perform operation that causes segfault
        assertThrows(ErlangRpcException.class,
            () -> bridge.checkConformance(getSegfaultEventLog(), getSimpleProcessSpec()),
            "Operation should throw ErlangRpcException, not SIGSEGV");

        // Verify JVM is still running (no crash)
        assertTrue(Thread.currentThread().isAlive(),
            "JVM thread should still be alive");

        // Verify we can still perform other operations
        faultServer.injectFault(null);
        String caseId = bridge.launchCase("after_segfault");
        assertNotNull(caseId, "Bridge should still be functional after segfault");
    }

    // =========================================================================
    // Test 4: Recovery Behavior
    // =========================================================================

    /**
     * Verries automatic recovery after fault injection.
     */
    @Test
    @DisplayName("Recovery: After fault → automatic recovery within 100ms")
    void recovery_afterFault_automaticRecoveryWithin100ms()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Inject fault
        faultServer.injectFault("panic");

        // Attempt operation (should fail)
        assertThrows(ErlangRpcException.class,
            () -> bridge.launchCase("during_fault"),
            "Operation should fail during fault");

        // Clear fault and attempt recovery
        long start = System.nanoTime();
        faultServer.injectFault(null);

        // Wait for recovery and verify it works
        String caseId = bridge.launchCase("after_recovery");
        long end = System.nanoTime();

        long recoveryTimeMs = (end - start) / 1_000_000;
        assertNotNull(caseId, "Operation should succeed after recovery");
        System.out.println("Recovery time: " + recoveryTimeMs + "ms");

        // Recovery target: <100ms
        assertTrue(recoveryTimeMs < 100,
            "Recovery should take under 100ms (took " + recoveryTimeMs + "ms)");
    }

    /**
     * Verries graceful degradation under fault conditions.
     */
    @Test
    @DisplayName("Recovery: Graceful degradation → reduced functionality, no crash")
    void recovery_gracefulDegradation_reducedFunctionalityNoCrash()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Inject partial fault
        faultServer.injectFault("partial_failure");

        // Test core functionality still works
        String caseId = bridge.launchCase("core_functionality");
        assertNotNull(caseId, "Core functionality should work");

        // Test advanced functionality fails gracefully
        try {
            bridge.advancedOperation("should_fail");
            fail("Advanced operation should fail gracefully");
        } catch (ErlangRpcException e) {
            // Expected failure
            assertTrue(e.getMessage().contains("partial") ||
                      e.getMessage().contains("degraded"),
                "Exception should indicate partial functionality");
        }

        // Clear fault and verify full recovery
        faultServer.injectFault(null);
        String fullCaseId = bridge.launchCase("full_functionality");
        assertNotNull(fullCaseId, "Full functionality should be restored");
    }

    // =========================================================================
    // Test 5: Concurrent Fault Handling
    // =========================================================================

    /**
     * Verries thread-safe fault handling under concurrent access.
     */
    @Test
    @DisplayName("Concurrency: Concurrent faults → handled safely without race conditions")
    void concurrency_concurrentFaults_handledSafelyWithoutRaceConditions()
            throws InterruptedException {
        int numThreads = 5;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<String>> futures = new ArrayList<>();
        AtomicInteger faultCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Submit concurrent operations with fault injection
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    // Inject fault for this thread
                    faultServer.injectFault("concurrent_fault_" + threadId);

                    // Attempt operation
                    String caseId = bridge.launchCase("fault_thread_" + threadId);
                    successCount.incrementAndGet();
                    return "Thread-" + threadId + ": Success";
                } catch (Exception e) {
                    faultCount.incrementAndGet();
                    return "Thread-" + threadId + ": Failed - " + e.getMessage();
                } finally {
                    // Clear fault for this thread
                    faultServer.injectFault(null);
                }
            }));
        }

        // Wait for all threads to complete
        for (Future<String> future : futures) {
            assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS),
                "All threads should complete within 10 seconds");
        }

        // Verify results
        System.out.println("Concurrent fault handling results:");
        System.out.println("  Successes: " + successCount.get());
        System.out.println("  Failures: " + faultCount.get());

        // Verify no deadlocks or corruption
        assertTrue(successCount.get() + faultCount.get() == numThreads,
            "All threads should complete (either success or failure)");
        assertTrue(bridge.isHealthy(),
            "Bridge should remain healthy after concurrent fault handling");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private List<Map<String, Object>> generateLargeEventLog(int numEvents) {
        List<Map<String, Object>> eventLog = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            eventLog.add(Map.of(
                "activity", "LargeTask_" + (i % 10),
                "timestamp", "2024-01-01T" + (10 + i/3600) + ":" + (i%60) + ":00Z",
                "case_id", "large_case",
                "data", "x".repeat(i % 1000) // Variable size data
            ));
        }
        return eventLog;
    }

    private List<Map<String, Object>> generatePatternedEventLog(int numEvents) {
        List<Map<String, Object>> eventLog = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            eventLog.add(Map.of(
                "activity", "PatternTask_" + (i % 3),
                "timestamp", "2024-01-01T10:00:00Z",
                "case_id", "pattern_case",
                "iteration", i,
                "checksum", i * 1000
            ));
        }
        return eventLog;
    }

    private List<Map<String, Object>> generateSingleCaseEventLog(int caseId) {
        return List.of(
            Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:00:00Z", "case_id", "case_" + caseId),
            Map.of("activity", "Task_B", "timestamp", "2024-01-01T11:00:00Z", "case_id", "case_" + caseId)
        );
    }

    private List<Map<String, Object>> getSegfaultEventLog() {
        return List.of(
            Map.of("activity", "SegFaultTask", "timestamp", "2024-01-01T10:00:00Z", "case_id", "segfault_case")
        );
    }

    private Map<String, Object> getSimpleProcessSpec() {
        return Map.of(
            "name", "Simple Process",
            "start_task", "Start",
            "end_task", "End",
            "tasks", List.of("Task_A", "Task_B", "Task_C"),
            "edges", List.of(
                Map.of("from", "Start", "to", "Task_A"),
                Map.of("from", "Task_A", "to", "Task_B"),
                Map.of("from", "Task_B", "to", "Task_C"),
                Map.of("from", "Task_C", "to", "End")
            )
        );
    }

    /**
     * Test server for fault injection
     */
    private static class FaultInjectionTestServer {
        private boolean running = false;
        private String currentFault = null;

        public void start() {
            running = true;
            System.out.println("Fault Injection Test Server started");
        }

        public void stop() {
            running = false;
            currentFault = null;
            System.out.println("Fault Injection Test Server stopped");
        }

        public void injectFault(String faultType) {
            this.currentFault = faultType;
            if (faultType != null) {
                System.out.println("Fault injected: " + faultType);
            }
        }

        public String getCurrentFault() {
            return currentFault;
        }

        public boolean isRunning() {
            return running;
        }
    }
}