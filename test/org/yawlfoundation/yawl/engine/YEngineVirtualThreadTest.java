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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test specifically focused on YEngine's use case with virtual threads.
 * Verifies that case operations work correctly with tenant context inheritance.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YEngineVirtualThreadTest {

    private static final String TENANT_ID = "yawl-test-tenant";
    private static final String CASE_ID = "yawl-case-123";
    private static final int THREAD_COUNT = 10;

    @Test
    @DisplayName("Virtual thread case operation inheritance")
    void testVirtualThreadCaseOperationInheritance() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        List<AtomicReference<TenantContext>> threadContexts = new ArrayList<>();

        YEngine.executeWithTenant(tenant, () -> {
            // Create multiple virtual threads that should inherit tenant context
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadNum = i;
                AtomicReference<TenantContext> contextRef = new AtomicReference<>();
                threadContexts.add(contextRef);

                Thread.ofVirtual()
                    .name("yawl-case-thread-" + i)
                    .start(() -> {
                        // Simulate case operation that needs tenant context
                        TenantContext context = ScopedTenantContext.requireTenantContext();
                        contextRef.set(context);

                        // Verify case authorization
                        assertTrue(context.isAuthorized(CASE_ID),
                            "Thread " + threadNum + " should be authorized for case");
                        assertEquals(TENANT_ID, context.getTenantId(),
                            "Thread " + threadNum + " should have correct tenant ID");

                        // Simulate some work
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    })
                    .join(); // Wait for each thread to complete
            }
        });

        // Verify all threads had the correct context
        for (int i = 0; i < THREAD_COUNT; i++) {
            AtomicReference<TenantContext> contextRef = threadContexts.get(i);
            assertNotNull(contextRef.get(), "Thread " + i + " should have captured context");
            assertEquals(tenant, contextRef.get(), "Thread " + i + " should have correct context");
            assertTrue(contextRef.get().isAuthorized(CASE_ID),
                "Thread " + i + " should be authorized");
        }
    }

    @Test
    @DisplayName("Structured task scope for case processing")
    void testStructuredTaskScopeForCaseProcessing() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        List<String> results = new ArrayList<>();

        YEngine.executeWithTenant(tenant, () -> {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                // Submit multiple case processing tasks
                List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();

                for (int i = 0; i < THREAD_COUNT; i++) {
                    final int caseNum = i;
                    subtasks.add(scope.fork(() -> {
                        // All tasks should inherit tenant context
                        TenantContext context = ScopedTenantContext.requireTenantContext();
                        assertTrue(context.isAuthorized(CASE_ID),
                            "Task should be authorized");

                        // Simulate case processing
                        String result = "processed-case-" + caseNum;
                        Thread.sleep(5);
                        return result;
                    }));
                }

                // Wait for all tasks to complete (or fail fast)
                scope.join();

                // Collect results
                for (StructuredTaskScope.Subtask<?> subtask : subtasks) {
                    results.add((String) subtask.get());
                }
            } catch (Exception e) {
                throw new RuntimeException("Structured task scope failed", e);
            }
        });

        // Verify all tasks completed successfully
        assertEquals(THREAD_COUNT, results.size(), "All tasks should complete");
        for (int i = 0; i < THREAD_COUNT; i++) {
            assertEquals("processed-case-" + i, results.get(i),
                "Result should match expected value");
        }
    }

    @Test
    @DisplayName("Parallel case processing with inheritance")
    void testParallelCaseProcessing() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        // Create callable tasks for parallel execution
        Callable<String>[] tasks = new Callable[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int taskNum = i;
            tasks[i] = () -> {
                TenantContext context = ScopedTenantContext.requireTenantContext();
                assertTrue(context.isAuthorized(CASE_ID),
                    "Task " + taskNum + " should be authorized");
                return "result-" + taskNum;
            };
        }

        // Execute all tasks in parallel
        String[] results = YEngine.executeParallel(tenant, tasks);

        // Verify results
        assertEquals(THREAD_COUNT, results.length, "Should have all results");
        for (int i = 0; i < THREAD_COUNT; i++) {
            assertEquals("result-" + i, results[i], "Result should match expected");
        }
    }

    @Test
    @DisplayName("Nested case operations with context propagation")
    void testNestedCaseOperations() {
        TenantContext parentTenant = new TenantContext("parent-tenant");
        parentTenant.registerCase("parent-case");

        TenantContext childTenant = new TenantContext("child-tenant");
        childTenant.registerCase("child-case");

        List<String> executionOrder = new ArrayList<>();

        YEngine.executeWithTenant(parentTenant, () -> {
            executionOrder.add("parent-start");

            // Parent operations
            TenantContext parentContext = ScopedTenantContext.requireTenantContext();
            assertEquals(parentTenant, parentContext);
            assertTrue(parentContext.isAuthorized("parent-case"));

            executionOrder.add("parent-before-child");

            // Nested child operations
            YEngine.executeWithTenant(childTenant, () -> {
                executionOrder.add("child-start");

                TenantContext childContext = ScopedTenantContext.requireTenantContext();
                assertEquals(childTenant, childContext);
                assertTrue(childContext.isAuthorized("child-case"));
                assertFalse(childContext.isAuthorized("parent-case"));

                executionOrder.add("child-end");
            });

            executionOrder.add("parent-after-child");

            // Back to parent context
            TenantContext backToParent = ScopedTenantContext.requireTenantContext();
            assertEquals(parentTenant, backToParent);
            assertTrue(backToParent.isAuthorized("parent-case"));

            executionOrder.add("parent-end");
        });

        // Verify execution order and context isolation
        List<String> expectedOrder = List.of(
            "parent-start", "parent-before-child",
            "child-start", "child-end",
            "parent-after-child", "parent-end"
        );

        assertEquals(expectedOrder, executionOrder, "Execution order should be correct");
    }

    @Test
    @DisplayName("Case isolation between different tenants")
    void testCaseIsolationBetweenTenants() {
        TenantContext tenant1 = new TenantContext("tenant1");
        tenant1.registerCase("case1");

        TenantContext tenant2 = new TenantContext("tenant2");
        tenant2.registerCase("case2");

        List<String> thread1Results = new ArrayList<>();
        List<String> thread2Results = new ArrayList<>();

        // Run both tenants in parallel
        Thread thread1 = new Thread(() -> {
            YEngine.executeWithTenant(tenant1, () -> {
                Thread.ofVirtual()
                    .name("tenant1-virtual")
                    .start(() -> {
                        TenantContext context = ScopedTenantContext.requireTenantContext();
                        thread1Results.add(context.getTenantId());
                        assertTrue(context.isAuthorized("case1"));
                        assertFalse(context.isAuthorized("case2"));
                    })
                    .join();
            });
        });

        Thread thread2 = new Thread(() -> {
            YEngine.executeWithTenant(tenant2, () -> {
                Thread.ofVirtual()
                    .name("tenant2-virtual")
                    .start(() -> {
                        TenantContext context = ScopedTenantContext.requireTenantContext();
                        thread2Results.add(context.getTenantId());
                        assertTrue(context.isAuthorized("case2"));
                        assertFalse(context.isAuthorized("case1"));
                    })
                    .join();
            });
        });

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify isolation
        assertEquals(1, thread1Results.size(), "Thread 1 should have one result");
        assertEquals("tenant1", thread1Results.get(0), "Thread 1 should have tenant1 context");

        assertEquals(1, thread2Results.size(), "Thread 2 should have one result");
        assertEquals("tenant2", thread2Results.get(0), "Thread 2 should have tenant2 context");

        assertNotEquals(thread1Results.get(0), thread2Results.get(0),
            "Different tenants should be isolated");
    }

    @Test
    @DisplayName("Virtual thread context inheritance chain")
    void testVirtualThreadContextInheritanceChain() {
        TenantContext rootTenant = new TenantContext("root-tenant");
        rootTenant.registerCase("root-case");

        List<TenantContext> contextChain = new ArrayList<>();

        YEngine.executeWithTenant(rootTenant, () -> {
            // First level virtual thread
            Thread.ofVirtual()
                .name("level1")
                .start(() -> {
                    TenantContext context = ScopedTenantContext.requireTenantContext();
                    contextChain.add(context);
                    assertEquals(rootTenant, context);

                    // Second level virtual thread
                    Thread.ofVirtual()
                        .name("level2")
                        .start(() -> {
                            TenantContext context2 = ScopedTenantContext.requireTenantContext();
                            contextChain.add(context2);
                            assertEquals(rootTenant, context2);

                            // Third level virtual thread
                            Thread.ofVirtual()
                                .name("level3")
                                .start(() -> {
                                    TenantContext context3 = ScopedTenantContext.requireTenantContext();
                                    contextChain.add(context3);
                                    assertEquals(rootTenant, context3);
                                })
                                .join();
                        })
                        .join();
                })
                .join();
        });

        // Verify inheritance chain
        assertEquals(3, contextChain.size(), "Should have 3 context entries");
        for (TenantContext context : contextChain) {
            assertEquals(rootTenant, context, "All contexts should be root tenant");
            assertTrue(context.isAuthorized("root-case"),
                "All contexts should be authorized for root case");
        }
    }
}