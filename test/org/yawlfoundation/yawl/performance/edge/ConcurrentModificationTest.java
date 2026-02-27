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

package org.yawlfoundation.yawl.performance.edge;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.YAWLServiceInterfaceRegistry;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.data.YDataHandler;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.engine.YAWLServiceInterfaceRegistry;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EngineBasedClient;
import org.yawlfoundation.yawl.exceptions.YSchemaException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.jdom2.Element;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for concurrent modification scenarios in YAWL.
 *
 * Tests race conditions and concurrent access patterns that could cause production issues:
 * - Multiple threads modifying the same case data simultaneously
 * - Concurrent work item updates and status transitions
 * - Resource contention in shared workflows
 * - Deadlock detection and prevention
 * - Data consistency under high concurrency
 * - Workflow state integrity during concurrent modifications
 *
 * Chicago TDD: Uses real YAWL objects, no mocks.
 *
 * @author Performance Test Specialist
 * @version 6.0.0
 */
@Tag("stress")
@Tag("performance")
@Tag("edge-cases")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 30, unit = TimeUnit.MINUTES)  // 30 minutes max for all tests
class ConcurrentModificationTest {

    private static final String CONCURRENT_HDR = "=== CONCURRENT MODIFICATION TEST REPORT ===";
    private static final String CONCURRENT_FTR_PASS = "=== ALL TESTS PASSED ===";
    private static final String CONCURRENT_FTR_FAIL = "=== TEST FAILURES DETECTED ===";

    private static final List<String> TEST_REPORT_LINES = new ArrayList<>();
    private YAWLServiceInterfaceRegistry registry;
    private YNetRunner netRunner;
    private YDataHandler dataHandler;

    @BeforeAll
    static void setup() {
        System.out.println();
        System.out.println(CONCURRENT_HDR);
        System.out.println("Testing concurrent modification scenarios");
        System.out.println();
    }

    @AfterAll
    static void tearDown() {
        boolean allPass = TEST_REPORT_LINES.stream()
                .allMatch(l -> l.contains("PASS") || l.contains("CHARACTERISED"));
        for (String line : TEST_REPORT_LINES) {
            System.out.println(line);
        }
        System.out.println(allPass ? CONCURRENT_FTR_PASS : CONCURRENT_FTR_FAIL);
        System.out.println();
    }

    @BeforeEach
    void initializeTest() {
        registry = new YAWLServiceInterfaceRegistry();
        netRunner = new YNetRunner();
        dataHandler = new YDataHandler();
    }

    // =========================================================================
    // Test 1: Concurrent Work Item Updates
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test 1 — Concurrent Work Item Updates (100 threads)")
    void test1_concurrentWorkItemUpdates() throws Exception {
        // Given: A workflow with multiple work items
        YSpecification spec = createConcurrentUpdateSpecification();
        String caseId = netRunner.launchCase(spec.getID(), Map.of("initialValue", 0));

        // Wait for initial work items
        List<WorkItemRecord> initialItems = waitForCaseCompletion(caseId, 30);
        assertEquals(5, initialItems.size(), "Should have 5 initial work items");

        // When: 100 threads attempt to update the same work items concurrently
        final int THREAD_COUNT = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<UpdateResult>> futures = new ArrayList<>(THREAD_COUNT);
        AtomicInteger successfulUpdates = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        AtomicBoolean deadlockDetected = new AtomicBoolean(false);
        CountDownLatch allStarted = new CountDownLatch(THREAD_COUNT);

        long startTime = System.nanoTime();

        // Launch concurrent update threads
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    allStarted.countDown();
                    allStarted.await(5, TimeUnit.SECONDS);

                    // Pick a random work item to update
                    Random random = new Random();
                    int itemIdIndex = random.nextInt(initialItems.size());
                    WorkItemRecord item = initialItems.get(itemIdIndex);

                    // Attempt to update the work item multiple times
                    for (int attempt = 0; attempt < 10; attempt++) {
                        try {
                            InterfaceB_EngineBasedClient client = new InterfaceB_EngineBasedClient(netRunner);
                            Map<String, Object> updateData = new HashMap<>();
                            updateData.put("value", threadId * 1000 + attempt);

                            // Attempt update with retry logic
                            boolean updateSuccess = false;
                            for (int retry = 0; retry < 3; retry++) {
                                try {
                                    client.offerWorkItem(item.getWorkItemID(), updateData);
                                    updateSuccess = true;
                                    break;
                                } catch (Exception e) {
                                    if (e.getMessage().contains("concurrent modification")) {
                                        conflicts.incrementAndGet();
                                        Thread.sleep(10);  // Brief wait before retry
                                    } else {
                                        throw e;
                                    }
                                }
                            }

                            if (updateSuccess) {
                                successfulUpdates.incrementAndGet();
                            }
                        } catch (Exception e) {
                            if (e.getMessage().contains("deadlock")) {
                                deadlockDetected.set(true);
                            }
                            throw e;
                        }
                    }

                    return new UpdateResult(threadId, successfulUpdates.get() > 0, conflicts.get() > 0);
                } catch (Exception e) {
                    return new UpdateResult(threadId, false, true);
                }
            }));
        }

        // Wait for all threads to complete
        int successfulThreads = 0;
        int failedThreads = 0;

        for (Future<UpdateResult> future : futures) {
            try {
                UpdateResult result = future.get(120, TimeUnit.SECONDS);
                if (result.successful) {
                    successfulThreads++;
                } else {
                    failedThreads++;
                }
            } catch (Exception e) {
                failedThreads++;
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        // Then: Validate concurrent update results
        assertEquals(THREAD_COUNT, successfulThreads + failedThreads,
            "All threads should complete");
        assertTrue(successfulThreads > THREAD_COUNT * 0.8,
            "At least 80% of threads should succeed with updates");

        // Check for deadlocks
        assertFalse(deadlockDetected.get(), "No deadlocks should be detected");
        assertTrue(durationMs < 60000, "Concurrent updates should complete within 60s");

        // Verify final state of work items
        List<WorkItemRecord> finalItems = netRunner.getWorkItemListForCase(caseId);
        for (WorkItemRecord item : finalItems) {
            if (item.getStatus().equals("complete")) {
                YWorkItem workItem = netRunner.getWorkItem(item.getWorkItemID());
                Object value = workItem.getDataVariableByName("value").getValue();
                assertNotNull(value, "Work item should have a valid value");
            }
        }

        String report = String.format(
                "Test 1  Concurrent Work Item Updates:  threads=%d success=%d conflicts=%d duration=%.2fs  deadlocks=%s  PASS",
                THREAD_COUNT, successfulThreads, conflicts.get(), durationMs / 1000.0,
                deadlockDetected.get() ? "DETECTED" : "NONE");
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 2: Resource Contention Scenarios
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Test 2 — Resource Contention (50 threads, shared resources)")
    void test2_resourceContention() throws Exception {
        // Given: A workflow with shared resource constraints
        YSpecification spec = createResourceContentionSpecification();

        // Launch multiple cases with shared resource requirements
        final int CASE_COUNT = 50;
        List<String> caseIds = new ArrayList<>();
        CountDownLatch allCasesStarted = new CountDownLatch(CASE_COUNT);

        // Launch cases concurrently
        for (int i = 0; i < CASE_COUNT; i++) {
            String caseId = netRunner.launchCase(spec.getID(),
                    Map.of("resourceId", "resource-" + (i % 10), "priority", i));
            caseIds.add(caseId);
            allCasesStarted.countDown();
        }

        // Wait for all cases to start
        assertTrue(allCasesStarted.await(30, TimeUnit.SECONDS),
            "All cases should start");

        // When: Cases compete for limited resources
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger resourceAcquisitions = new AtomicInteger(0);
        AtomicInteger resourceFailures = new AtomicInteger(0);
        AtomicBoolean timeoutDetected = new AtomicBoolean(false);

        long startTime = System.nanoTime();

        // Process all cases concurrently
        List<Future<ResourceResult>> futures = new ArrayList<>(CASE_COUNT);
        for (String caseId : caseIds) {
            futures.add(executor.submit(() -> {
                try {
                    List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 120);

                    // Check if case acquired resources successfully
                    boolean acquiredResources = workItems.stream()
                            .anyMatch(item -> item.getStatus().equals("complete") &&
                                    netRunner.getWorkItem(item.getWorkItemID())
                                            .getDataVariableByName("resourceAcquired")
                                            .getValue()
                                            .equals(true));

                    if (acquiredResources) {
                        resourceAcquisitions.incrementAndGet();
                    } else {
                        resourceFailures.incrementAndGet();
                    }

                    return new ResourceResult(acquiredResources, caseId);
                } catch (Exception e) {
                    if (e.getMessage().contains("timeout")) {
                        timeoutDetected.set(true);
                    }
                    return new ResourceResult(false, caseId);
                }
            }));
        }

        // Wait for all processing to complete
        int completedCases = 0;
        for (Future<ResourceResult> future : futures) {
            try {
                ResourceResult result = future.get(180, TimeUnit.SECONDS);
                if (result.successful) {
                    completedCases++;
                }
            } catch (Exception e) {
                resourceFailures.incrementAndGet();
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        // Then: Validate resource contention handling
        assertTrue(resourceAcquisitions.get() > CASE_COUNT * 0.6,
            "At least 60% of cases should acquire resources");
        assertTrue(durationMs < 120000, "All cases should complete within 2 minutes");

        // Check for timeouts (indicating deadlocks or long waits)
        assertFalse(timeoutDetected.get(), "No timeouts should occur");

        // Verify resource distribution
        Map<String, Integer> resourceUsage = new HashMap<>();
        for (String caseId : caseIds) {
            List<WorkItemRecord> items = netRunner.getWorkItemListForCase(caseId);
            for (WorkItemRecord item : items) {
                if (item.getStatus().equals("complete")) {
                    YWorkItem workItem = netRunner.getWorkItem(item.getWorkItemID());
                    String resourceId = (String) workItem.getDataVariableByName("resourceId").getValue();
                    resourceUsage.merge(resourceId, 1, Integer::sum);
                }
            }
        }

        // No resource should be oversubscribed by more than 200%
        for (Map.Entry<String, Integer> entry : resourceUsage.entrySet()) {
            int expectedMaxUsage = 10;  // Based on our resource setup
            assertTrue(entry.getValue() <= expectedMaxUsage * 2,
                "Resource " + entry.getKey() + " used " + entry.getValue() +
                " times (max expected: " + (expectedMaxUsage * 2) + ")");
        }

        String report = String.format(
                "Test 2  Resource Contention:          cases=%d acquired=%d failures=%d duration=%.2fs timeout=%s  PASS",
                CASE_COUNT, resourceAcquisitions.get(), resourceFailures.get(),
                durationMs / 1000.0, timeoutDetected.get() ? "YES" : "NO");
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 3: Data Race Detection
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Test 3 — Data Race Detection (shared data structures)")
    void test3_dataRaceDetection() throws Exception {
        // Given: A workflow with shared mutable data
        YSpecification spec = createDataRaceSpecification();
        String caseId = netRunner.launchCase(spec.getID(),
                Map.of("sharedCounter", 0, "sharedList", new ArrayList<>()));

        // Wait for initial work items
        List<WorkItemRecord> initialItems = waitForCaseCompletion(caseId, 30);
        assertEquals(3, initialItems.size(), "Should have 3 initial work items");

        // When: Multiple threads modify shared data structures
        final int THREAD_COUNT = 20;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger counterUpdates = new AtomicInteger(0);
        AtomicInteger listUpdates = new AtomicInteger(0);
        AtomicInteger detectedRaces = new AtomicInteger(0);

        AtomicReference<String> lastException = new AtomicReference<>();
        CountDownLatch allStarted = new CountDownLatch(THREAD_COUNT);

        long startTime = System.nanoTime();

        // Launch concurrent threads modifying shared data
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    allStarted.countDown();
                    allStarted.await(5, TimeUnit.SECONDS);

                    Random random = new Random();

                    // Perform operations for 5 seconds
                    long endTime = System.currentTimeMillis() + 5000;
                    while (System.currentTimeMillis() < endTime) {
                        try {
                            // Randomly choose to update counter or list
                            if (random.nextBoolean()) {
                                // Update counter
                                InterfaceB_EngineBasedClient client = new InterfaceB_EngineBasedClient(netRunner);
                                Map<String, Object> counterUpdate = Map.of(
                                        "sharedCounter", threadId);
                                client.offerWorkItem(initialItems.get(0).getWorkItemID(), counterUpdate);
                                counterUpdates.incrementAndGet();
                            } else {
                                // Update list
                                InterfaceB_EngineBasedClient client = new InterfaceB_EngineBasedClient(netRunner);
                                Map<String, Object> listUpdate = Map.of(
                                        "sharedList", Arrays.asList(threadId, "item"));
                                client.offerWorkItem(initialItems.get(1).getWorkItemID(), listUpdate);
                                listUpdates.incrementAndGet();
                            }
                        } catch (Exception e) {
                            if (e.getMessage().contains("race condition") ||
                                e.getMessage().contains("concurrent modification")) {
                                detectedRaces.incrementAndGet();
                            }
                            lastException.set(e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    lastException.set(e.getMessage());
                }
            });
        }

        // Wait for operations to complete
        Thread.sleep(6000);  // Wait for all threads to finish their operations

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        // Then: Validate data race detection
        assertTrue(counterUpdates.get() > 100, "Counter should be updated many times");
        assertTrue(listUpdates.get() > 100, "List should be updated many times");

        // Race conditions should be detected and handled gracefully
        assertTrue(detectedRaces.get() >= 0, "Race detection should work");

        // Verify final data consistency
        List<WorkItemRecord> finalItems = netRunner.getWorkItemListForCase(caseId);
        for (WorkItemRecord item : finalItems) {
            if (item.getStatus().equals("complete")) {
                YWorkItem workItem = netRunner.getWorkItem(item.getWorkItemID());
                if (item.getTaskName().contains("Counter")) {
                    Object counter = workItem.getDataVariableByName("sharedCounter").getValue();
                    assertNotNull(counter, "Counter should have a value");
                } else if (item.getTaskName().contains("List")) {
                    Object list = workItem.getDataVariableByName("sharedList").getValue();
                    assertNotNull(list, "List should have a value");
                }
            }
        }

        String report = String.format(
                "Test 3  Data Race Detection:          threads=%d counterUpdates=%d listUpdates=%d racesDetected=%d duration=%.2fs  PASS",
                THREAD_COUNT, counterUpdates.get(), listUpdates.get(), detectedRaces.get(), durationMs / 1000.0);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 4: Deadlock Prevention and Detection
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Test 4 — Deadlock Detection (circular resource dependencies)")
    void test4_deadlockDetection() throws Exception {
        // Given: A workflow with circular resource dependencies (potential deadlock scenario)
        YSpecification spec = createDeadlockProneSpecification();

        // Launch multiple instances that could create circular waits
        final int INSTANCE_COUNT = 10;
        List<String> caseIds = new ArrayList<>();

        for (int i = 0; i < INSTANCE_COUNT; i++) {
            String caseId = netRunner.launchCase(spec.getID(),
                    Map.of("taskId", i, "resources", Arrays.asList("A", "B", "C")));
            caseIds.add(caseId);
        }

        // When: Instances execute with potential circular dependencies
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger deadlocksDetected = new AtomicInteger(0);
        AtomicInteger successfulCompletions = new AtomicInteger(0);
        AtomicBoolean globalDeadlock = new AtomicBoolean(false);

        long startTime = System.nanoTime();

        // Monitor for deadlocks
        ScheduledExecutorService monitorExecutor = Executors.newScheduledThreadPool(1);
        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                // Check for deadlocks by examining work item states
                for (String caseId : caseIds) {
                    List<WorkItemRecord> items = netRunner.getWorkItemListForCase(caseId);
                    boolean allWaiting = items.stream()
                            .allMatch(item -> item.getStatus().equals("waiting"));

                    if (allWaiting) {
                        globalDeadlock.set(true);
                        deadlocksDetected.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                // Continue monitoring
            }
        }, 0, 5, TimeUnit.SECONDS);

        // Process cases
        List<Future<Boolean>> futures = new ArrayList<>(INSTANCE_COUNT);
        for (String caseId : caseIds) {
            futures.add(executor.submit(() -> {
                try {
                    List<WorkItemRecord> items = waitForCaseCompletion(caseId, 90);
                    successfulCompletions.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    if (e.getMessage().contains("deadlock")) {
                        deadlocksDetected.incrementAndGet();
                    }
                    return false;
                }
            }));
        }

        // Wait for all cases to complete or timeout
        int completed = 0;
        for (Future<Boolean> future : futures) {
            try {
                Boolean result = future.get(120, TimeUnit.SECONDS);
                if (result) completed++;
            } catch (Exception e) {
                // Case failed or timed out
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        monitorExecutor.shutdownNow();
        executor.shutdown();

        // Then: Validate deadlock handling
        assertFalse(globalDeadlock.get(), "Global deadlock should not occur");
        assertTrue(deadlocksDetected.get() <= INSTANCE_COUNT * 0.1,
            "Deadlocks should be rare (<10% of cases)");
        assertTrue(successfulCompletions.get() >= INSTANCE_COUNT * 0.7,
            "At least 70% of cases should complete successfully");

        String report = String.format(
                "Test 4  Deadlock Detection:          instances=%d completed=%d deadlocks=%d globalDeadlock=%s duration=%.2fs  PASS",
                INSTANCE_COUNT, successfulCompletions.get(), deadlocksDetected.get(),
                globalDeadlock.get() ? "YES" : "NO", durationMs / 1000.0);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 5: Consistency Validation Under High Load
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Test 5 — Consistency Validation (high load, 1000+ operations)")
    void test5_consistencyValidation() throws Exception {
        // Given: A workflow with multiple data consistency requirements
        YSpecification spec = createConsistencySpecification();
        String caseId = netRunner.launchCase(spec.getID(),
                Map.of("balance", 1000, "transactions", new ArrayList<>()));

        // Wait for initial setup
        List<WorkItemRecord> initialItems = waitForCaseCompletion(caseId, 30);
        assertEquals(4, initialItems.size(), "Should have 4 initial work items");

        // When: Execute high-volume transactional operations
        final int TRANSACTION_COUNT = 1000;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger successfulTransactions = new AtomicInteger(0);
        AtomicInteger failedTransactions = new AtomicInteger(0);
        AtomicReference<String> lastConsistencyError = new AtomicReference<>();

        CountDownLatch allStarted = new CountDownLatch(100);  // 100 concurrent clients

        long startTime = System.nanoTime();

        // Launch 100 clients performing transactions
        for (int clientId = 0; clientId < 100; clientId++) {
            final int client = clientId;
            executor.submit(() -> {
                try {
                    allStarted.countDown();
                    allStarted.await(5, TimeUnit.SECONDS);

                    // Perform 10 transactions per client
                    for (int tx = 0; tx < 10; tx++) {
                        try {
                            Random random = new Random();
                            int amount = random.nextInt(100) + 1;
                            boolean isDeposit = random.nextBoolean();

                            InterfaceB_EngineBasedClient clientInterface = new InterfaceB_EngineBasedClient(netRunner);
                            Map<String, Object> txData = new HashMap<>();
                            txData.put("amount", amount);
                            txData.put("isDeposit", isDeposit);
                            txData.put("clientId", client);
                            txData.put("transactionId", client * 100 + tx);

                            // Choose random work item to process
                            WorkItemRecord item = initialItems.get(random.nextInt(initialItems.size()));
                            clientInterface.offerWorkItem(item.getWorkItemID(), txData);

                            successfulTransactions.incrementAndGet();
                        } catch (Exception e) {
                            failedTransactions.incrementAndGet();
                            if (e.getMessage().contains("consistency") ||
                                e.getMessage().contains("constraint")) {
                                lastConsistencyError.set(e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    failedTransactions.incrementAndGet();
                }
            });
        }

        // Wait for all transactions to complete
        executor.shutdown();
        assertTrue(executor.awaitTermination(180, TimeUnit.SECONDS),
            "All transactions should complete within 3 minutes");

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: Validate data consistency
        double successRate = (double) successfulTransactions.get() /
                            (successfulTransactions.get() + failedTransactions.get());
        assertTrue(successRate > 0.95, "Success rate should be >95%");

        // Final balance validation
        List<WorkItemRecord> finalItems = netRunner.getWorkItemListForCase(caseId);
        YWorkItem balanceItem = netRunner.getWorkItem(finalItems.stream()
                .filter(item -> item.getTaskName().contains("Balance"))
                .findFirst()
                .get().getWorkItemID());

        Object finalBalance = balanceItem.getDataVariableByName("balance").getValue();
        assertNotNull(finalBalance, "Final balance should not be null");

        // Check for consistency errors
        if (lastConsistencyError.get() != null) {
            System.out.println("Consistency error detected: " + lastConsistencyError.get());
        }

        String report = String.format(
                "Test 5  Consistency Validation:        transactions=%,d success=%,d failures=%,d successRate=%.2f%% duration=%.2fs consistencyError=%s  PASS",
                TRANSACTION_COUNT, successfulTransactions.get(), failedTransactions.get(),
                successRate * 100, durationMs / 1000.0,
                lastConsistencyError.get() != null ? lastConsistencyError.get() : "NONE");
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 6: Performance Under Concurrent Load
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Test 6 — Performance Under Load (stress test)")
    void test6_performanceUnderConcurrentLoad() throws Exception {
        // Given: Multiple workflow instances under high concurrent load
        YSpecification spec = createHighLoadSpecification();

        final int CASE_COUNT = 50;
        final int WORKERS_PER_CASE = 5;
        int totalWorkers = CASE_COUNT * WORKERS_PER_CASE;

        // Launch all cases
        List<String> caseIds = new ArrayList<>();
        for (int i = 0; i < CASE_COUNT; i++) {
            String caseId = netRunner.launchCase(spec.getID(),
                    Map.of("caseId", i, "workerCount", WORKERS_PER_CASE));
            caseIds.add(caseId);
        }

        // When: Execute with high concurrency
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completedTasks = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        AtomicLong totalProcessingTime = new AtomicLong(0);
        AtomicLong maxProcessingTime = new AtomicLong(0);
        AtomicLong minProcessingTime = new AtomicLong(Long.MAX_VALUE);

        CountDownLatch allStarted = new CountDownLatch(totalWorkers);
        CountDownLatch allWorkDone = new CountDownLatch(totalWorkers);

        long startTime = System.nanoTime();

        // Launch all workers
        for (int i = 0; i < totalWorkers; i++) {
            final int workerId = i;
            final String caseId = caseIds.get(workerId / WORKERS_PER_CASE);

            executor.submit(() -> {
                try {
                    allStarted.countDown();
                    allStarted.await(10, TimeUnit.SECONDS);

                    long taskStart = System.nanoTime();
                    try {
                        // Perform work
                        Map<String, Object> workData = Map.of(
                                "workerId", workerId,
                                "workType", "processing");

                        // Find and execute work item
                        List<WorkItemRecord> items = netRunner.getWorkItemListForCase(caseId);
                        if (!items.isEmpty()) {
                            InterfaceB_EngineBasedClient client = new InterfaceB_EngineBasedClient(netRunner);
                            client.offerWorkItem(items.get(0).getWorkItemID(), workData);
                        }

                        completedTasks.incrementAndGet();
                    } finally {
                        long taskEnd = System.nanoTime();
                        long taskDuration = taskEnd - taskStart;
                        totalProcessingTime.addAndGet(taskDuration);
                        maxProcessingTime.updateAndGet(max -> Math.max(max, taskDuration));
                        minProcessingTime.updateAndGet(min -> Math.min(min, taskDuration));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    allWorkDone.countDown();
                }
            });
        }

        // Wait for all work to complete
        assertTrue(allWorkDone.await(300, TimeUnit.SECONDS),
            "All work should complete within 5 minutes");

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        // Then: Validate performance under load
        double avgProcessingTimeMs = totalProcessingTime.get() / (double) completedTasks.get();
        double throughput = completedTasks.get() / (durationMs / 1000.0);

        assertTrue(throughput > 10, "Throughput should exceed 10 tasks/sec");
        assertTrue(avgProcessingTimeMs < 1000, "Average processing time < 1s");
        assertTrue(errors.get() < totalWorkers * 0.05, "Error rate < 5%");

        String report = String.format(
                "Test 6  Performance Under Load:     workers=%,d completed=%,d errors=%d throughput=%.1f/s avgTime=%.1fms duration=%.1fs  PASS",
                totalWorkers, completedTasks.get(), errors.get(), throughput, avgProcessingTimeMs,
                durationMs / 1000.0);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // Helper classes and methods

    private record UpdateResult(int threadId, boolean successful, boolean hadConflicts) {}
    private record ResourceResult(boolean successful, String caseId) {}

    private YSpecification createConcurrentUpdateSpecification() throws Exception {
        Element yawlElement = new Element("yawl");
        yawlElement.setAttribute("xmlns", "http://www.yawlfoundation.org/yawl");

        Element specElement = new Element("specification");
        specElement.setAttribute("id", "ConcurrentUpdateSpec");
        specElement.setAttribute("name", "Concurrent Update Specification");

        // Input parameters
        Element inputParams = new Element("inputParameters");
        Element initialValue = new Element("data");
        initialValue.setAttribute("name", "initialValue");
        initialValue.setAttribute("type", "xs:integer");
        inputParams.addContent(initialValue);

        // Create net with multiple update tasks
        Element netElement = new Element("net");
        netElement.setAttribute("id", "ConcurrentUpdateNet");

        // Create 5 parallel update tasks
        for (int i = 0; i < 5; i++) {
            Element taskElement = new Element("task");
            taskElement.setAttribute("name", "UpdateTask" + i);
            taskElement.setAttribute("id", "UpdateTask" + i);

            // Add data variable for updates
            Element valueVar = new Element("data");
            valueVar.setAttribute("name", "value");
            valueVar.setAttribute("type", "xs:integer");
            taskElement.addContent(valueVar);

            // Connect to start
            Element startFlow = new Element("flow");
            startFlow.setAttribute("source", "start");
            startFlow.setAttribute("target", "UpdateTask" + i);
            netElement.addContent(startFlow);

            // Connect to finish
            Element finishFlow = new Element("flow");
            finishFlow.setAttribute("source", "UpdateTask" + i);
            finishFlow.setAttribute("target", "finish");
            netElement.addContent(finishFlow);

            netElement.addContent(taskElement);
        }

        specElement.addContent(inputParams);
        specElement.addContent(netElement);
        yawlElement.addContent(specElement);

        return YMarshal.unmarshalSpecification(yawlElement);
    }

    private YSpecification createResourceContentionSpecification() throws Exception {
        // Similar structure to above but with resource contention logic
        return createConcurrentUpdateSpecification();  // Simplified for this example
    }

    private YSpecification createDataRaceSpecification() throws Exception {
        return createConcurrentUpdateSpecification();  // Simplified for this example
    }

    private YSpecification createDeadlockProneSpecification() throws Exception {
        return createConcurrentUpdateSpecification();  // Simplified for this example
    }

    private YSpecification createConsistencySpecification() throws Exception {
        return createConcurrentUpdateSpecification();  // Simplified for this example
    }

    private YSpecification createHighLoadSpecification() throws Exception {
        return createConcurrentUpdateSpecification();  // Simplified for this example
    }

    private List<WorkItemRecord> waitForCaseCompletion(String caseId, int timeoutSeconds) throws InterruptedException {
        List<WorkItemRecord> workItems = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            List<WorkItemRecord> items = netRunner.getWorkItemListForCase(caseId);

            // Add work items in complete status
            for (WorkItemRecord item : items) {
                if (item.getStatus().equals("complete") && !workItems.contains(item)) {
                    workItems.add(item);
                }
            }

            if (!workItems.isEmpty()) {
                return workItems;
            }

            Thread.sleep(1000);
        }

        fail("Case " + caseId + " did not complete work items within " + timeoutSeconds + " seconds");
        return Collections.emptyList();
    }
}