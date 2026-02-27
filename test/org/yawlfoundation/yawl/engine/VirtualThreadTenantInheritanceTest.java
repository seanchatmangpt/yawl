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

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test specifically focused on virtual thread inheritance for ScopedValue tenant context.
 * Verifies that virtual threads properly inherit tenant context from parent.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class VirtualThreadTenantInheritanceTest {

    private static final String TEST_TENANT_ID = "test-tenant";
    private static final String TEST_CASE_ID = "test-case-123";

    @Test
    @DisplayName("Virtual thread inherits tenant context from parent thread")
    void testVirtualThreadInheritsTenantContext() {
        TenantContext testTenant = new TenantContext(TEST_TENANT_ID);
        testTenant.registerCase(TEST_CASE_ID);

        AtomicReference<TenantContext> capturedContext = new AtomicReference<>();

        ScopedTenantContext.runWithTenant(testTenant, () -> {
            // Create virtual thread that inherits parent's ScopedValue
            Thread.ofVirtual()
                .name("tenant-inheritance-test")
                .start(() -> {
                    // Virtual thread should inherit parent's tenant context
                    TenantContext context = ScopedTenantContext.getTenantContext();
                    capturedContext.set(context);
                })
                .join(); // Wait for virtual thread to complete

            // Verify virtual thread inherited the context
            assertNotNull(capturedContext.get(), "Virtual thread should inherit tenant context");
            assertEquals(testTenant, capturedContext.get(), "Inherited context should match parent");
            assertEquals(TEST_TENANT_ID, capturedContext.get().getTenantId(),
                "Tenant ID should be correctly inherited");
            assertTrue(capturedContext.get().isAuthorized(TEST_CASE_ID),
                "Virtual thread should inherit authorization");
        });

        // Outside scope, context should be null
        assertNull(ScopedTenantContext.getTenantContext(),
            "Context should be null outside scope");
    }

    @Test
    @DisplayName("Virtual thread with null parent context")
    void testVirtualThreadWithNullParentContext() {
        AtomicReference<TenantContext> capturedContext = new AtomicReference<>();

        ScopedTenantContext.runWithTenant(null, () -> {
            // Virtual thread should have null context
            Thread.ofVirtual()
                .name("null-context-test")
                .start(() -> {
                    capturedContext.set(ScopedTenantContext.getTenantContext());
                })
                .join();

            assertNull(capturedContext.get(), "Virtual thread should have null context");
        });
    }

    @Test
    @DisplayName("Nested virtual threads inherit nested context")
    void testNestedVirtualThreadInheritance() {
        TenantContext outerTenant = new TenantContext("outer-tenant");
        outerTenant.registerCase("outer-case");

        TenantContext innerTenant = new TenantContext("inner-tenant");
        innerTenant.registerCase("inner-case");

        AtomicReference<TenantContext> outerThreadContext = new AtomicReference<>();
        AtomicReference<TenantContext> innerThreadContext = new AtomicReference<>();

        ScopedTenantContext.runWithTenant(outerTenant, () -> {
            // First virtual thread (inherits outer tenant)
            Thread.ofVirtual()
                .name("outer-virtual")
                .start(() -> {
                    outerThreadContext.set(ScopedTenantContext.getTenantContext());
                })
                .join();

            assertEquals(outerTenant, outerThreadContext.get(),
                "Outer virtual thread should have outer tenant");

            // Nested context within outer scope
            ScopedTenantContext.runWithTenant(innerTenant, () -> {
                // Second virtual thread (inherits inner tenant)
                Thread.ofVirtual()
                    .name("inner-virtual")
                    .start(() -> {
                        innerThreadContext.set(ScopedTenantContext.getTenantContext());
                    })
                    .join();

                assertEquals(innerTenant, innerThreadContext.get(),
                    "Inner virtual thread should have inner tenant");
            });
        });

        assertEquals(outerTenant, outerThreadContext.get(),
            "Outer thread should maintain outer tenant");
        assertEquals(innerTenant, innerThreadContext.get(),
            "Inner thread should have inner tenant");
    }

    @Test
    @DisplayName("StructuredTaskScope with tenant context propagation")
    void testStructuredTaskScopeWithTenantContext() {
        TenantContext testTenant = new TenantContext("structured-scope-tenant");
        testTenant.registerCase("structured-case");

        ScopedTenantContext.runWithTenant(testTenant, () -> {
            try (var scope = java.util.concurrent.StructuredTaskScope.ShutdownOnFailure()) {
                // Multiple virtual threads that all inherit tenant context
                AtomicReference<TenantContext> thread1Context = new AtomicReference<>();
                AtomicReference<TenantContext> thread2Context = new AtomicReference<>();
                AtomicReference<TenantContext> thread3Context = new AtomicReference<>();

                scope.fork(() -> {
                    thread1Context.set(ScopedTenantContext.requireTenantContext());
                    return "task1-complete";
                });

                scope.fork(() -> {
                    thread2Context.set(ScopedTenantContext.requireTenantContext());
                    return "task2-complete";
                });

                scope.fork(() -> {
                    thread3Context.set(ScopedTenantContext.requireTenantContext());
                    return "task3-complete";
                });

                scope.join();

                // All tasks should have inherited the tenant context
                assertEquals(testTenant, thread1Context.get(), "Task 1 context");
                assertEquals(testTenant, thread2Context.get(), "Task 2 context");
                assertEquals(testTenant, thread3Context.get(), "Task 3 context");

                // Verify they can still perform tenant-specific operations
                assertTrue(thread1Context.get().isAuthorized("structured-case"),
                    "Task 1 should be authorized");
                assertTrue(thread2Context.get().isAuthorized("structured-case"),
                    "Task 2 should be authorized");
                assertTrue(thread3Context.get().isAuthorized("structured-case"),
                    "Task 3 should be authorized");
            }
        });
    }

    @Test
    @DisplayName("Virtual thread context isolation between different scopes")
    void testVirtualThreadContextIsolation() {
        TenantContext tenant1 = new TenantContext("tenant1");
        tenant1.registerCase("case1");

        TenantContext tenant2 = new TenantContext("tenant2");
        tenant2.registerCase("case2");

        AtomicReference<TenantContext> scope1Thread = new AtomicReference<>();
        AtomicReference<TenantContext> scope2Thread = new AtomicReference<>();

        // First scope
        ScopedTenantContext.runWithTenant(tenant1, () -> {
            Thread.ofVirtual()
                .name("scope1-virtual")
                .start(() -> {
                    scope1Thread.set(ScopedTenantContext.getTenantContext());
                })
                .join();

            assertEquals(tenant1, scope1Thread.get(),
                "Scope1 thread should have tenant1");
            assertTrue(scope1Thread.get().isAuthorized("case1"),
                "Scope1 thread should be authorized for case1");
            assertFalse(scope1Thread.get().isAuthorized("case2"),
                "Scope1 thread should not be authorized for case2");
        });

        // Second scope (different tenant)
        ScopedTenantContext.runWithTenant(tenant2, () -> {
            Thread.ofVirtual()
                .name("scope2-virtual")
                .start(() -> {
                    scope2Thread.set(ScopedTenantContext.getTenantContext());
                })
                .join();

            assertEquals(tenant2, scope2Thread.get(),
                "Scope2 thread should have tenant2");
            assertFalse(scope2Thread.get().isAuthorized("case1"),
                "Scope2 thread should not be authorized for case1");
            assertTrue(scope2Thread.get().isAuthorized("case2"),
                "Scope2 thread should be authorized for case2");
        });

        // Verify isolation
        assertNotEquals(scope1Thread.get(), scope2Thread.get(),
            "Different scopes should have different tenant contexts");
        assertNotEquals(scope1Thread.get().getTenantId(), scope2Thread.get().getTenantId(),
            "Tenant IDs should be different");
    }
}