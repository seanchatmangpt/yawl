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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test to ensure YEngine migration from ThreadLocal to ScopedValue
 * is backward compatible and provides improved functionality.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YEngineMigrationTest {

    private static final String TENANT_ID = "migration-test-tenant";
    private static final String CASE_ID = "migration-test-case";

    @BeforeEach
    void setUp() {
        YEngine.clearTenantContext();
    }

    @Test
    @DisplayName("Backward compatibility - ThreadLocal methods still work")
    void testBackwardCompatibility() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        // Test old ThreadLocal methods still work
        YEngine.setTenantContext(tenant);
        assertEquals(tenant, YEngine.getTenantContext(),
            "getTenantContext() should work with old implementation");

        YEngine.clearTenantContext();
        assertNull(YEngine.getTenantContext(),
            "clearTenantContext() should work with old implementation");
    }

    @Test
    @DisplayName("New ScopedValue methods work correctly")
    void testNewScopedValueMethods() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        // Test new ScopedValue methods
        String result = YEngine.executeWithTenant(tenant, () -> {
            assertEquals(tenant, ScopedTenantContext.requireTenantContext(),
                "Tenant context should be available in scoped execution");
            return "execution-result";
        });

        assertEquals("execution-result", result,
            "Execute method should return correct result");

        // Outside scope, context should be null
        assertNull(ScopedTenantContext.getTenantContext(),
            "Context should be null outside scope");
    }

    @Test
    @DisplayName("Thread safety during concurrent access")
    void testThreadSafety() {
        TenantContext tenant1 = new TenantContext("tenant-1");
        tenant1.registerCase("case-1");
        TenantContext tenant2 = new TenantContext("tenant-2");
        tenant2.registerCase("case-2");

        AtomicBoolean thread1Success = new AtomicBoolean(false);
        AtomicBoolean thread2Success = new AtomicBoolean(false);

        Thread thread1 = new Thread(() -> {
            YEngine.runWithTenant(tenant1, () -> {
                assertEquals(tenant1, ScopedTenantContext.requireTenantContext(),
                    "Thread 1 should have tenant1 context");
                assertTrue(ScopedTenantContext.requireTenantContext().isAuthorized("case-1"),
                    "Thread 1 should be authorized for case1");
                assertFalse(ScopedTenantContext.requireTenantContext().isAuthorized("case-2"),
                    "Thread 1 should not be authorized for case2");
                thread1Success.set(true);
            });
        });

        Thread thread2 = new Thread(() -> {
            YEngine.runWithTenant(tenant2, () -> {
                assertEquals(tenant2, ScopedTenantContext.requireTenantContext(),
                    "Thread 2 should have tenant2 context");
                assertFalse(ScopedTenantContext.requireTenantContext().isAuthorized("case-1"),
                    "Thread 2 should not be authorized for case1");
                assertTrue(ScopedTenantContext.requireTenantContext().isAuthorized("case-2"),
                    "Thread 2 should be authorized for case2");
                thread2Success.set(true);
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

        assertTrue(thread1Success.get(), "Thread 1 should complete successfully");
        assertTrue(thread2Success.get(), "Thread 2 should complete successfully");
    }

    @Test
    @DisplayName("Virtual thread context inheritance works with YEngine methods")
    void testVirtualThreadInheritanceWithYEngine() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        AtomicReference<TenantContext> inheritedContext = new AtomicReference<>();

        YEngine.executeWithTenant(tenant, () -> {
            // Virtual thread should inherit parent context
            Thread.ofVirtual()
                .name("inheritance-test")
                .start(() -> {
                    inheritedContext.set(ScopedTenantContext.requireTenantContext());
                })
                .join();

            // Verify inheritance
            assertNotNull(inheritedContext.get(), "Virtual thread should inherit context");
            assertEquals(tenant, inheritedContext.get(), "Inherited context should match");
            assertTrue(inheritedContext.get().isAuthorized(CASE_ID),
                "Inherited context should be authorized");
        });

        // Outside scope
        assertNull(ScopedTenantContext.getTenantContext(), "Context should be null outside scope");
    }

    @Test
    @DisplayName("Parallel execution with tenant context")
    void testParallelExecution() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        Callable<String> task1 = () -> {
            assertEquals(tenant, ScopedTenantContext.requireTenantContext(),
                "Task 1 should have context");
            return "task1";
        };

        Callable<String> task2 = () -> {
            assertEquals(tenant, ScopedTenantContext.requireTenantContext(),
                "Task 2 should have context");
            return "task2";
        };

        String[] results = YEngine.executeParallel(tenant, new Callable[]{task1, task2});

        assertEquals(2, results.length, "Should have 2 results");
        assertEquals("task1", results[0], "Task 1 should complete");
        assertEquals("task2", results[1], "Task 2 should complete");
    }

    @Test
    @DisplayName("Migration helper utilities work correctly")
    void testMigrationHelperUtilities() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        // Test thread type detection
        boolean isVirtual = MigrationHelper.isVirtualThread();
        String threadInfo = MigrationHelper.getThreadTypeInfo();
        System.out.println("Thread info: " + threadInfo);

        // Test scoped usage validation
        assertDoesNotThrow(() -> MigrationHelper.validateScopedUsage(null),
            "Should not throw for null context outside scope");

        YEngine.executeWithTenant(tenant, () -> {
            assertDoesNotThrow(() -> MigrationHelper.validateScopedUsage(tenant),
                "Should not throw for valid context within scope");

            // Test statistics tracking
            String stats = MigrationHelper.getMigrationStatistics();
            assertNotNull(stats, "Statistics should be non-null");
        });
    }

    @Test
    @DisplayName("Context isolation between different scopes")
    void testContextIsolation() {
        TenantContext tenant1 = new TenantContext("tenant1");
        tenant1.registerCase("case1");
        TenantContext tenant2 = new TenantContext("tenant2");
        tenant2.registerCase("case2");

        AtomicReference<String> scope1Context = new AtomicReference<>();
        AtomicReference<String> scope2Context = new AtomicReference<>();

        // First scope
        YEngine.executeWithTenant(tenant1, () -> {
            scope1Context.set(ScopedTenantContext.getTenantContext().getTenantId());
            assertTrue(ScopedTenantContext.getTenantContext().isAuthorized("case1"),
                "Should be authorized in scope1");
            assertFalse(ScopedTenantContext.getTenantContext().isAuthorized("case2"),
                "Should not be authorized in scope1");
        });

        // Second scope
        YEngine.executeWithTenant(tenant2, () -> {
            scope2Context.set(ScopedTenantContext.getTenantContext().getTenantId());
            assertFalse(ScopedTenantContext.getTenantContext().isAuthorized("case1"),
                "Should not be authorized in scope2");
            assertTrue(ScopedTenantContext.getTenantContext().isAuthorized("case2"),
                "Should be authorized in scope2");
        });

        // Verify isolation
        assertEquals("tenant1", scope1Context.get(), "Scope1 should have tenant1");
        assertEquals("tenant2", scope2Context.get(), "Scope2 should have tenant2");
        assertNotEquals(scope1Context.get(), scope2Context.get(),
            "Different scopes should have different tenants");
    }

    @Test
    @DisplayName("Nested scope handling")
    void testNestedScopes() {
        TenantContext outer = new TenantContext("outer");
        outer.registerCase("outer-case");
        TenantContext inner = new TenantContext("inner");
        inner.registerCase("inner-case");

        AtomicReference<String> outerTenant = new AtomicReference<>();
        AtomicReference<String> innerTenant = new AtomicReference<>();

        YEngine.executeWithTenant(outer, () -> {
            outerTenant.set(ScopedTenantContext.getTenantContext().getTenantId());

            // Nested scope
            YEngine.executeWithTenant(inner, () -> {
                innerTenant.set(ScopedTenantContext.getTenantContext().getTenantId());
            });
        });

        assertEquals("outer", outerTenant.get(), "Outer scope should have outer tenant");
        assertEquals("inner", innerTenant.get(), "Inner scope should have inner tenant");
    }

    @Test
    @DisplayName("Null tenant handling")
    void testNullTenantHandling() {
        YEngine.executeWithTenant(null, () -> {
            assertNull(ScopedTenantContext.getTenantContext(),
                "Should be null with no tenant");
            assertFalse(ScopedTenantContext.hasTenantContext(),
                "Should not have context with null tenant");

            assertThrows(IllegalStateException.class,
                ScopedTenantContext::requireTenantContext,
                "Should throw when requiring null context");
        });
    }

    @Test
    @DisplayName("Case authorization after migration")
    void testCaseAuthorizationAfterMigration() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        YEngine.executeWithTenant(tenant, () -> {
            // Test that tenant context is properly set and can authorize cases
            assertTrue(ScopedTenantContext.requireTenantContext().isAuthorized(CASE_ID),
                "Should be authorized for own case");

            // Test that it's not authorized for other cases
            assertFalse(ScopedTenantContext.requireTenantContext().isAuthorized("other-case"),
                "Should not be authorized for other cases");

            // Test null case handling
            assertFalse(ScopedTenantContext.requireTenantContext().isAuthorized(null),
                "Should return false for null case");
        });
    }

    @Test
    @DisplayName("Performance comparison between old and new approaches")
    void testPerformanceComparison() {
        TenantContext tenant = new TenantContext(TENANT_ID);
        tenant.registerCase(CASE_ID);

        // Time the old approach
        long startTime = System.nanoTime();
        YEngine.setTenantContext(tenant);
        boolean authorizedOld = YEngine.getTenantContext().isAuthorized(CASE_ID);
        YEngine.clearTenantContext();
        long oldTime = System.nanoTime() - startTime;

        // Time the new approach
        startTime = System.nanoTime();
        YEngine.executeWithTenant(tenant, () -> {
            boolean authorizedNew = ScopedTenantContext.requireTenantContext().isAuthorized(CASE_ID);
            return authorizedNew;
        });
        long newTime = System.nanoTime() - startTime;

        // Both should work and return the same result
        assertTrue(authorizedOld, "Old approach should authorize");
        assertTrue(authorizedNew, "New approach should authorize");

        System.out.printf("Old approach: %d ns%n", oldTime);
        System.out.printf("New approach: %d ns%n", newTime);

        // New approach should be competitive
        double ratio = (double) newTime / oldTime;
        System.out.printf("Ratio (new/old): %.2f%n", ratio);
        assertTrue(ratio < 3.0, "New approach should not be more than 3x slower");
    }
}