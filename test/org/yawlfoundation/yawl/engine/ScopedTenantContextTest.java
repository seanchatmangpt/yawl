/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will to useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ScopedTenantContext functionality.
 * Tests thread-safety, virtual thread inheritance, and migration compatibility.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@ExtendWith(ThreadPerTenantContextExtension.class)
public class ScopedTenantContextTest {

    private static final String TENANT_ID_1 = "tenant-1";
    private static final String TENANT_ID_2 = "tenant-2";
    private static final String CASE_ID_1 = "case-123";
    private static final String CASE_ID_2 = "case-456";

    private TenantContext tenant1;
    private TenantContext tenant2;

    @BeforeEach
    void setUp() {
        tenant1 = new TenantContext(TENANT_ID_1);
        tenant1.registerCase(CASE_ID_1);

        tenant2 = new TenantContext(TENANT_ID_2);
        tenant2.registerCase(CASE_ID_2);
    }

    @Test
    @DisplayName("Basic ScopedValue binding and retrieval")
    void testBasicScopedValueOperations() {
        // Test no context initially
        assertNull(ScopedTenantContext.getTenantContext(), "Initial context should be null");

        // Test context within scope
        ScopedTenantContext.runWithTenant(tenant1, () -> {
            assertEquals(tenant1, ScopedTenantContext.getTenantContext(),
                "Context should be accessible within scope");
            assertEquals(TENANT_ID_1, ScopedTenantContext.getTenantContext().getTenantId(),
                "Tenant ID should match");
            assertTrue(ScopedTenantContext.getTenantContext().isAuthorized(CASE_ID_1),
                "Should be authorized for own case");
        });

        // Test context outside scope
        assertNull(ScopedTenantContext.getTenantContext(),
            "Context should not leak outside scope");
    }

    @Test
    @DisplayName("Callable execution with tenant context")
    void testCallableExecution() {
        String result = ScopedTenantContext.runWithTenant(tenant1, () -> {
            assertEquals(tenant1, ScopedTenantContext.requireTenantContext(),
                "Context should be available in callable");
            return "result-from-tenant1";
        });

        assertEquals("result-from-tenant1", result, "Callable should execute with context");
    }

    @Test
    @DisplayName("Virtual thread inheritance")
    void testVirtualThreadInheritance() {
        AtomicReference<TenantContext> capturedContext = new AtomicReference<>();

        ScopedTenantContext.runWithTenant(tenant1, () -> {
            // Virtual thread inherits parent context
            Thread.ofVirtual()
                .name("inheritance-test")
                .start(() -> {
                    capturedContext.set(ScopedTenantContext.getTenantContext());
                })
                .join();

            // Check that virtual thread inherited the context
            assertNotNull(capturedContext.get(), "Virtual thread should inherit parent context");
            assertEquals(tenant1, capturedContext.get(), "Inherited context should match parent");
            assertEquals(TENANT_ID_1, capturedContext.get().getTenantId(), "Tenant ID should match");
            assertTrue(capturedContext.get().isAuthorized(CASE_ID_1),
                "Should be authorized in virtual thread");
        });
    }

    @Test
    @DisplayName("Parallel execution with tenant context")
    void testParallelExecution() {
        Callable<String> task1 = () -> {
            assertEquals(tenant1, ScopedTenantContext.requireTenantContext(),
                "Task 1 should have context");
            return "task1-" + Thread.currentThread().getName();
        };

        Callable<String> task2 = () -> {
            assertEquals(tenant1, ScopedTenantContext.requireTenantContext(),
                "Task 2 should have context");
            return "task2-" + Thread.currentThread().getName();
        };

        Callable<String> task3 = () -> {
            assertEquals(tenant1, ScopedTenantContext.requireTenantContext(),
                "Task 3 should have context");
            return "task3-" + Thread.currentThread().getName();
        };

        String[] results = ScopedTenantContext.runParallel(tenant1, new Callable[]{task1, task2, task3});

        assertEquals(3, results.length, "Should have 3 results");
        assertTrue(results[0].startsWith("task1-"), "Task 1 result should match");
        assertTrue(results[1].startsWith("task2-"), "Task 2 result should match");
        assertTrue(results[2].startsWith("task3-"), "Task 3 result should match");
    }

    @Test
    @DisplayName("Concurrent tenant isolation")
    void testConcurrentTenantIsolation() {
        ConcurrentHashMap<String, String> threadResults = new ConcurrentHashMap<>();

        // Multiple threads with different tenants
        Thread[] threads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final TenantContext tenant = (i % 2 == 0) ? tenant1 : tenant2;
            final int threadIndex = i;

            threads[i] = new Thread(() -> {
                ScopedTenantContext.runWithTenant(tenant, () -> {
                    TenantContext currentContext = ScopedTenantContext.requireTenantContext();
                    threadResults.put("thread-" + threadIndex, currentContext.getTenantId());

                    // Small delay to increase chance of interleaving
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Verify context is still correct after delay
                    assertEquals(tenant.getTenantId(), currentContext.getTenantId(),
                        "Context should remain correct after delay");
                });
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Verify all threads had correct tenant isolation
        assertEquals(TENANT_ID_1, threadResults.get("thread-0"), "Thread 0 should have tenant 1");
        assertEquals(TENANT_ID_1, threadResults.get("thread-2"), "Thread 2 should have tenant 1");
        assertEquals(TENANT_ID_2, threadResults.get("thread-1"), "Thread 1 should have tenant 2");
    }

    @Test
    @DisplayName("Nested scopes maintain correct context")
    void testNestedScopes() {
        AtomicReference<String> outerTenant = new AtomicReference<>();
        AtomicReference<String> middleTenant = new AtomicReference<>();
        AtomicReference<String> innerTenant = new AtomicReference<>();

        ScopedTenantContext.runWithTenant(tenant1, () -> {
            outerTenant.set(ScopedTenantContext.requireTenantContext().getTenantId());

            ScopedTenantContext.runWithTenant(tenant2, () -> {
                middleTenant.set(ScopedTenantContext.requireTenantContext().getTenantId());

                ScopedTenantContext.runWithTenant(tenant1, () -> {
                    innerTenant.set(ScopedTenantContext.requireTenantContext().getTenantId());
                });
            });
        });

        assertEquals(TENANT_ID_1, outerTenant.get(), "Outer scope should have tenant 1");
        assertEquals(TENANT_ID_2, middleTenant.get(), "Middle scope should have tenant 2");
        assertEquals(TENANT_ID_1, innerTenant.get(), "Inner scope should have tenant 1");
    }

    @Test
    @DisplayName("Null tenant handling")
    void testNullTenantHandling() {
        ScopedTenantContext.runWithTenant(null, () -> {
            assertNull(ScopedTenantContext.getTenantContext(),
                "Null tenant should result in null context");
            assertFalse(ScopedTenantContext.hasTenantContext(),
                "Null tenant should not have context");

            try {
                ScopedTenantContext.requireTenantContext();
                fail("Should throw IllegalStateException for null context");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("No tenant context is bound"),
                    "Error message should indicate no tenant is bound");
            }
        });
    }

    @Test
    @DisplayName("Case authorization within scoped context")
    void testCaseAuthorization() {
        ScopedTenantContext.runWithTenant(tenant1, () -> {
            TenantContext context = ScopedTenantContext.requireTenantContext();

            // Should be authorized for own case
            assertTrue(context.isAuthorized(CASE_ID_1),
                "Should be authorized for own case");

            // Should not be authorized for other tenant's case
            assertFalse(context.isAuthorized(CASE_ID_2),
                "Should not be authorized for other tenant's case");

            // Should handle null case ID gracefully
            assertFalse(context.isAuthorized(null),
                "Should return false for null case ID");
        });
    }

    @ParameterizedTest
    @DisplayName("Test with different virtual thread counts")
    @MethodSource("virtualThreadCounts")
    void testWithDifferentVirtualThreadCounts(int threadCount) {
        AtomicReference<Boolean> allSuccess = new AtomicReference<>(true);
        AtomicInteger successfulThreads = new AtomicInteger(0);

        ScopedTenantContext.runWithTenant(tenant1, () -> {
            for (int i = 0; i < threadCount; i++) {
                final int threadNum = i;
                Thread.ofVirtual()
                    .name("virtual-test-" + i)
                    .start(() -> {
                        try {
                            TenantContext context = ScopedTenantContext.requireTenantContext();
                            assertEquals(TENANT_ID_1, context.getTenantId(),
                                "Virtual thread should have correct tenant");
                            successfulThreads.incrementAndGet();
                        } catch (Exception e) {
                            allSuccess.set(false);
                        }
                    });
            }
        });

        assertTrue(allSuccess.get(), "All virtual threads should succeed");
        assertEquals(threadCount, successfulThreads.get(),
            "All threads should have executed successfully");
    }

    private static Stream<Integer> virtualThreadCounts() {
        return Stream.of(1, 10, 50, 100);
    }

    @Test
    @DisplayName("Migration compatibility - verify same behavior as old ThreadLocal")
    void testMigrationCompatibility() {
        // Simulate the old ThreadLocal pattern
        YEngine.setTenantContext(tenant1);
        assertEquals(tenant1, YEngine.getTenantContext(),
            "ThreadLocal should work before migration");
        YEngine.clearTenantContext();
        assertNull(YEngine.getTenantContext(),
            "ThreadLocal should be cleared");

        // Verify ScopedValue works the same way
        ScopedTenantContext.runWithTenant(tenant1, () -> {
            assertEquals(tenant1, ScopedTenantContext.getTenantContext(),
                "ScopedValue should work same as ThreadLocal within scope");
            assertTrue(ScopedTenantContext.getTenantContext().isAuthorized(CASE_ID_1),
                "Should be authorized within scope");
        });

        assertNull(ScopedTenantContext.getTenantContext(),
            "ScopedValue should not leak outside scope, like ThreadLocal");
    }
}