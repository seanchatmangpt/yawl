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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.observability;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite for ObservabilityIntegration.
 *
 * <p>These tests validate that ScopedValue context propagation works correctly
 * with OpenTelemetry tracing and VirtualThreadPool monitoring.</p>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
@ExtendWith(MockitoExtension.class)
class ObservabilityIntegrationTest {

    private ObservabilityIntegration integration;
    private VirtualThreadPool virtualThreadPool;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "test-spec-456";

    @BeforeEach
    void setUp() {
        integration = ObservabilityIntegration.getInstance();
        virtualThreadPool = new VirtualThreadPool(
            "test-pool",
            5,
            10
        );
        virtualThreadPool.start();
    }

    @AfterEach
    void tearDown() {
        virtualThreadPool.shutdown();
    }

    @Test
    void testExecuteWithObservability_Success() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);

        String result = integration.executeWithObservability(
            "test-operation",
            TEST_CASE_ID,
            TEST_SPEC_ID,
            Duration.ofSeconds(10),
            () -> {
                counter.incrementAndGet();
                return "success";
            }
        );

        assertEquals("success", result);
        assertEquals(1, counter.get());
    }

    @Test
    void testExecuteWithObservability_ContextPropagation() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);

        integration.executeWithObservability(
            "context-test",
            TEST_CASE_ID,
            TEST_SPEC_ID,
            Duration.ofSeconds(10),
            () -> {
                // Verify context is propagated
                assertEquals(TEST_CASE_ID, YAWLTracing.getCurrentCaseId());
                assertEquals(TEST_SPEC_ID, YAWLTracing.getCurrentSpecId());
                counter.incrementAndGet();
                return "context-verified";
            }
        );

        assertEquals(1, counter.get());
    }

    @Test
    void testExecuteWithObservability_Timeout() {
        assertThrows(TimeoutException.class, () -> {
            integration.executeWithObservability(
                "slow-operation",
                TEST_CASE_ID,
                TEST_SPEC_ID,
                Duration.ofMillis(100),
                () -> {
                    Thread.sleep(1000); // Intentionally slow
                    return "should-timeout";
                }
            );
        });
    }

    @Test
    void testExecuteWithObservability_Exception() {
        assertThrows(ExecutionException.class, () -> {
            integration.executeWithObservability(
                "failing-operation",
                TEST_CASE_ID,
                TEST_SPEC_ID,
                Duration.ofSeconds(10),
                () -> {
                    throw new RuntimeException("Test exception");
                }
            );
        });
    }

    @Test
    void testExecuteParallelWithObservability() throws Exception {
        List<Callable<String>> tasks = List.of(
            () -> "task1",
            () -> "task2",
            () -> "task3"
        );

        List<String> results = integration.executeParallelWithObservability(
            "parallel-test",
            TEST_CASE_ID,
            TEST_SPEC_ID,
            Duration.ofSeconds(10),
            tasks
        );

        assertEquals(3, results.size());
        assertEquals(List.of("task1", "task2", "task3"), results);
    }

    @Test
    void testVirtualThreadPoolMonitoring() throws Exception {
        // Submit some work to the pool
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        Thread.sleep(100);
                        return "task-" + i;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                virtualThreadPool.getExecutor()
            );
            futures.add(future);
        }

        // Wait for all tasks to complete
        for (CompletableFuture<String> future : futures) {
            future.get();
        }

        // Monitor the pool
        integration.monitorVirtualThreadPool(virtualThreadPool);

        // Get metrics
        ObservabilityIntegration.ObservabilityMetrics metrics = integration.getMetrics();
        assertTrue(metrics.virtualThreadsCreated() > 0);
        assertTrue(metrics.totalVirtualThreadDurationNanos() > 0);
    }

    @Test
    void testScopedValueContextInheritance() throws Exception {
        String childCaseId = "child-case-" + System.currentTimeMillis();

        integration.executeWithObservability(
            "parent-operation",
            TEST_CASE_ID,
            TEST_SPEC_ID,
            Duration.ofSeconds(5),
            () -> {
                // Spawn child thread with inherited context
                Future<String> childResult = virtualThreadPool.submit(() -> {
                    // Context should be inherited
                    assertEquals(TEST_CASE_ID, YAWLTracing.getCurrentCaseId());
                    assertEquals(TEST_SPEC_ID, YAWLTracing.getCurrentSpecId());
                    return "child-completed";
                });

                return childResult.get();
            }
        );
    }

    @Test
    void testVirtualThreadPoolContextPropagation() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);

        // Submit task with context
        Future<String> future = virtualThreadPool.submitWithContext(
            () -> {
                assertEquals(TEST_CASE_ID, YAWLTracing.getCurrentCaseId());
                assertEquals(TEST_SPEC_ID, YAWLTracing.getCurrentSpecId());
                counter.incrementAndGet();
                return "context-propagated";
            },
            TEST_CASE_ID,
            TEST_SPEC_ID
        );

        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("context-propagated", result);
        assertEquals(1, counter.get());
    }

    @Test
    void testVirtualThreadPoolParallelExecution() throws Exception {
        List<Callable<String>> tasks = List.of(
            () -> "parallel1",
            () -> "parallel2",
            () -> "parallel3"
        );

        List<String> results = virtualThreadPool.executeInParallelWithContext(
            "parallel-execution",
            tasks,
            TEST_CASE_ID,
            TEST_SPEC_ID
        );

        assertEquals(3, results.size());
        assertEquals(List.of("parallel1", "parallel2", "parallel3"), results);
    }

    @Test
    void testMetricsCollection() throws Exception {
        // Execute some operations to generate metrics
        for (int i = 0; i < 5; i++) {
            integration.executeWithObservability(
                "metric-operation-" + i,
                TEST_CASE_ID,
                TEST_SPEC_ID,
                Duration.ofSeconds(1),
                () -> {
                    Thread.sleep(50);
                    return "metric-" + i;
                }
            );
        }

        // Allow time for metrics collection
        Thread.sleep(2000);

        // Verify metrics
        ObservabilityIntegration.ObservabilityMetrics metrics = integration.getMetrics();
        assertTrue(metrics.virtualThreadsCreated() >= 5);
        assertTrue(metrics.averageVirtualThreadDurationMs() > 0);
        assertEquals(0.0, metrics.timeoutRate()); // No timeouts expected
    }

    @Test
    void testAndonAlertIntegration() throws Exception {
        // This test verifies that slow operations trigger alerts
        // We can't easily test the alert mechanism itself, but we can verify
        // that the monitoring logic works

        integration.executeWithObservability(
            "slow-operation-for-alert",
            TEST_CASE_ID,
            TEST_SPEC_ID,
            Duration.ofSeconds(10),
            () -> {
                Thread.sleep(2000); // Slow enough to trigger monitoring
                return "slow-completed";
            }
        );

        // Verify the operation completed despite monitoring
        ObservabilityIntegration.ObservabilityMetrics metrics = integration.getMetrics();
        assertTrue(metrics.virtualThreadsCreated() > 0);
    }

    @Test
    void testContextPropagationFailureHandling() throws Exception {
        // Test what happens when context propagation fails
        try {
            integration.executeWithObservability(
                "no-context-test",
                null, // No case ID
                null, // No spec ID
                Duration.ofSeconds(5),
                () -> {
                    // Should work even without context
                    assertNull(YAWLTracing.getCurrentCaseId());
                    return "no-context-test";
                }
            );
        } catch (Exception e) {
            // Context propagation failures should not break execution
            fail("Context propagation failures should be handled gracefully");
        }
    }
}