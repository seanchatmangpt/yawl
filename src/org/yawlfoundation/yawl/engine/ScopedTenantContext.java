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

import java.lang.ScopedValue;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * ScopedValue-based tenant context for multi-tenant YAWL deployments.
 *
 * <p>This class provides the Java 25 ScopedValue alternative to the ThreadLocal-based
 * tenant context in {@link YEngine}. Key advantages:</p>
 * <ul>
 *   <li><b>Virtual thread safety</b>: ScopedValues are inherited automatically by
 *       child virtual threads without carrier thread pinning</li>
 *   <li><b>Automatic cleanup</b>: Context is released when the scope exits — no
 *       manual {@code clearTenantContext()} call required</li>
 *   <li><b>Immutable binding</b>: Context cannot be modified within a scope, preventing
 *       accidental cross-tenant contamination</li>
 *   <li><b>Structured concurrency</b>: Compatible with {@link StructuredTaskScope} for
 *       parallel tenant-scoped task execution where all subtasks inherit the context</li>
 * </ul>
 *
 * <p><b>Usage</b>:</p>
 * <pre>{@code
 * TenantContext ctx = new TenantContext("customer-123");
 * ScopedTenantContext.runWithTenant(ctx, () -> {
 *     // All code here (and any spawned virtual threads) sees the context
 *     TenantContext current = ScopedTenantContext.getTenantContext(); // returns ctx
 *     // Context is automatically released when this block exits
 * });
 * }</pre>
 *
 * <p><b>Migration</b>: The old {@code YEngine.setTenantContext()} / {@code clearTenantContext()}
 * ThreadLocal API remains available for backward compatibility. New code should use
 * {@code ScopedTenantContext.runWithTenant()} instead.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see java.lang.ScopedValue
 * @see java.util.concurrent.StructuredTaskScope
 * @see TenantContext
 */
public final class ScopedTenantContext {

    /**
     * The ScopedValue backing the tenant context.
     *
     * <p>Inherited automatically by all child virtual threads forked within the binding
     * scope, including {@link StructuredTaskScope} subtasks. Released when the enclosing
     * {@link ScopedValue#where} scope exits — no manual cleanup required.</p>
     */
    static final ScopedValue<TenantContext> CURRENT_TENANT = ScopedValue.newInstance();

    // Utility class — not instantiable
    private ScopedTenantContext() {
        throw new UnsupportedOperationException("ScopedTenantContext is a utility class");
    }

    // ─── Read accessors ───────────────────────────────────────────────────────

    /**
     * Returns the tenant context bound to the current scope, or {@code null} if none.
     *
     * @return the current {@link TenantContext}, or {@code null} if no tenant scope is active
     */
    public static TenantContext getTenantContext() {
        return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : null;
    }

    /**
     * Returns the tenant context bound to the current scope.
     *
     * @return the current {@link TenantContext} (never null)
     * @throws IllegalStateException if no tenant context is bound to the current scope
     */
    public static TenantContext requireTenantContext() {
        if (!CURRENT_TENANT.isBound()) {
            throw new IllegalStateException(
                "No tenant context is bound to the current scope. " +
                "Wrap the calling code in ScopedTenantContext.runWithTenant(ctx, ...).");
        }
        return CURRENT_TENANT.get();
    }

    /**
     * Returns {@code true} if a tenant context is bound to the current scope.
     *
     * @return {@code true} if {@link #CURRENT_TENANT} is bound
     */
    public static boolean hasTenantContext() {
        return CURRENT_TENANT.isBound();
    }

    // ─── Scope binders ────────────────────────────────────────────────────────

    /**
     * Runs the given action with the specified tenant context bound for its duration.
     * The context is automatically released when the action completes (normally or
     * exceptionally).
     *
     * <p>If {@code ctx} is {@code null}, the action runs without binding any tenant
     * context — {@link #getTenantContext()} returns {@code null} inside the action.</p>
     *
     * @param ctx  the tenant context to bind; may be {@code null}
     * @param work the action to run within the tenant scope
     */
    public static void runWithTenant(TenantContext ctx, Runnable work) {
        if (ctx == null) {
            work.run();
        } else {
            ScopedValue.where(CURRENT_TENANT, ctx).run(work);
        }
    }

    /**
     * Calls the given callable with the specified tenant context bound for its duration.
     * The context is automatically released when the callable completes (normally or
     * exceptionally).
     *
     * <p>If {@code ctx} is {@code null}, the callable runs without binding any tenant
     * context — {@link #getTenantContext()} returns {@code null} inside the callable.</p>
     *
     * @param <T>  the return type of the callable
     * @param ctx  the tenant context to bind; may be {@code null}
     * @param work the callable to execute within the tenant scope
     * @return the result of {@code work.call()}
     * @throws RuntimeException wrapping any checked exception thrown by the callable
     */
    public static <T> T runWithTenant(TenantContext ctx, Callable<T> work) {
        try {
            if (ctx == null) {
                return work.call();
            } else {
                // Wrap in lambda: Carrier.call() expects CallableOp (not java.util.concurrent.Callable)
                return ScopedValue.where(CURRENT_TENANT, ctx).call(() -> work.call());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Tenant-scoped callable failed", e);
        }
    }

    /**
     * Executes multiple callables in parallel, all sharing the same tenant context.
     *
     * <p>Uses {@link StructuredTaskScope} for structured concurrency: all subtasks
     * automatically inherit {@link #CURRENT_TENANT} from the parent scope. If any task
     * fails, the scope cancels all remaining tasks before propagating the exception.</p>
     *
     * @param ctx   the tenant context to bind for all tasks; may be {@code null}
     * @param tasks the tasks to execute in parallel (each must return a {@code String})
     * @return results of all tasks in input order
     * @throws RuntimeException if any task fails or the scope is interrupted
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String[] runParallel(TenantContext ctx, Callable<?>[] tasks) {
        return runWithTenant(ctx, () -> {
            try (var scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<String>awaitAllSuccessfulOrThrow())) {
                List<StructuredTaskScope.Subtask<String>> subtasks = Arrays.stream(tasks)
                    .map(t -> scope.fork(() -> (String) ((Callable) t).call()))
                    .toList();
                scope.join();
                return subtasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .toArray(String[]::new);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel tenant execution interrupted", e);
            }
        });
    }
}
