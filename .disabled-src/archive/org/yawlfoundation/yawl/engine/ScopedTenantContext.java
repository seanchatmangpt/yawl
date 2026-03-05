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

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ScopedValue-based tenant context carrier for virtual thread safety (JEP 487).
 *
 * <p>Replaces the ThreadLocal pattern used by {@link YEngine#setTenantContext} /
 * {@link YEngine#clearTenantContext}. Key advantages over ThreadLocal:
 * <ul>
 *   <li>Auto-released on scope exit — no manual {@code clearTenantContext()} required.</li>
 *   <li>Immutable within a scope — no silent overwrites by nested calls.</li>
 *   <li>Inherited by child virtual threads via {@code StructuredTaskScope.fork()} —
 *       propagates correctly without manual plumbing.</li>
 *   <li>Zero leakage between unrelated virtual threads — each scope is isolated.</li>
 * </ul>
 *
 * <p><b>Usage (preferred new API)</b>:
 * <pre>{@code
 *   TenantContext tenant = new TenantContext("customer-123");
 *   ScopedTenantContext.runWithTenant(tenant, () -> {
 *       // All engine calls here run under the tenant context.
 *       // Child virtual threads inherit it automatically.
 *   });
 *   // Context is auto-released here — no clearTenantContext() needed.
 * }</pre>
 *
 * <p>{@link YEngine#getTenantContext()} prefers this ScopedValue over the legacy ThreadLocal,
 * so code inside a {@code runWithTenant} scope sees the correct context via either API.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see YEngine#getTenantContext()
 * @see YEngine#setTenantContext(TenantContext)
 */
public final class ScopedTenantContext {

    // ScopedValue: immutable per-scope, auto-inherited by StructuredTaskScope children,
    // zero cross-thread leakage. Package-private so YEngine can check isBound().
    static final ScopedValue<TenantContext> TENANT = ScopedValue.newInstance();

    private ScopedTenantContext() { /* utility class */ }

    // -------------------------------------------------------------------------
    // Read access
    // -------------------------------------------------------------------------

    /**
     * Returns the tenant context bound to the current scope, or {@code null} if none.
     *
     * @return current {@link TenantContext}, or {@code null}
     */
    public static TenantContext getTenantContext() {
        return TENANT.isBound() ? TENANT.get() : null;
    }

    /**
     * Returns {@code true} if a tenant context is bound to the current scope.
     *
     * @return {@code true} if context is present
     */
    public static boolean hasTenantContext() {
        return TENANT.isBound();
    }

    /**
     * Returns the tenant context bound to the current scope, throwing if absent.
     *
     * @return current {@link TenantContext} (never null)
     * @throws IllegalStateException if no context is bound
     */
    public static TenantContext requireTenantContext() {
        if (!TENANT.isBound()) {
            throw new IllegalStateException(
                    "No tenant context bound for current virtual thread. " +
                    "Wrap the call in ScopedTenantContext.runWithTenant(tenant, work).");
        }
        return TENANT.get();
    }

    // -------------------------------------------------------------------------
    // Scope entry — Runnable
    // -------------------------------------------------------------------------

    /**
     * Runs {@code action} with {@code tenant} bound to the current scope.
     *
     * <p>The binding is released automatically when {@code action} completes (or throws).
     * Child virtual threads created inside {@code action} — including those forked via
     * {@code StructuredTaskScope.fork()} — inherit the binding automatically.
     *
     * @param tenant the {@link TenantContext} to bind (may be {@code null} to run unbound)
     * @param action the work to perform under the tenant context
     */
    public static void runWithTenant(TenantContext tenant, Runnable action) {
        ScopedValue.where(TENANT, tenant).run(action);
    }

    // -------------------------------------------------------------------------
    // Scope entry — Callable<T>
    // -------------------------------------------------------------------------

    /**
     * Runs {@code action} with {@code tenant} bound, returning its result.
     *
     * <p>Checked exceptions from {@code action} are wrapped in {@link RuntimeException}.
     * {@link RuntimeException} and {@link Error} propagate as-is.
     *
     * @param tenant the {@link TenantContext} to bind
     * @param action the work to perform under the tenant context
     * @param <T>    result type
     * @return the value returned by {@code action}
     */
    public static <T> T runWithTenant(TenantContext tenant, Callable<T> action) {
        try {
            // Use lambda to bridge Callable<T> → CallableOp<T, Exception> (Java 25 API)
            return ScopedValue.where(TENANT, tenant).call(() -> action.call());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Callable under tenant scope threw checked exception", e);
        }
    }

    // -------------------------------------------------------------------------
    // Parallel execution — all tasks share the same tenant context
    // -------------------------------------------------------------------------

    /**
     * Runs {@code tasks} in parallel virtual threads, all sharing {@code tenant} as context.
     *
     * <p>Returns an array of results in the same order as {@code tasks}. If any task throws,
     * the first exception is wrapped and re-thrown after all tasks complete.
     * Uses {@code .inheritInheritableThreadLocals(false)} to enforce isolation.
     *
     * @param tenant the {@link TenantContext} shared by all parallel tasks
     * @param tasks  callable tasks to run in parallel
     * @param <T>    result type
     * @return results array, one entry per task (in original order)
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] runParallel(TenantContext tenant, Callable<T>[] tasks) {
        Object[] results = new Object[tasks.length];
        CountDownLatch latch = new CountDownLatch(tasks.length);
        AtomicReference<Exception> firstError = new AtomicReference<>();

        for (int i = 0; i < tasks.length; i++) {
            final int idx = i;
            Thread.ofVirtual()
                    .inheritInheritableThreadLocals(false)
                    .start(() -> ScopedValue.where(TENANT, tenant).run(() -> {
                        try {
                            results[idx] = tasks[idx].call();
                        } catch (Exception e) {
                            firstError.compareAndSet(null, e);
                        } finally {
                            latch.countDown();
                        }
                    }));
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for parallel tenant tasks", e);
        }

        if (firstError.get() != null) {
            throw new RuntimeException("Parallel tenant task failed", firstError.get());
        }
        return (T[]) results;
    }
}
