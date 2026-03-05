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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Migration utilities for the ThreadLocal → ScopedValue transition (Phase 0a).
 *
 * <p>Provides diagnostic helpers that assist in verifying correct behaviour during
 * the migration from {@link YEngine#setTenantContext} / {@link YEngine#clearTenantContext}
 * (ThreadLocal) to {@link ScopedTenantContext#runWithTenant} (ScopedValue).</p>
 *
 * <p>All methods are thread-safe and safe to call from virtual threads.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class MigrationHelper {

    private static final AtomicLong _scopedCallCount = new AtomicLong(0);
    private static final AtomicLong _threadLocalCallCount = new AtomicLong(0);

    private MigrationHelper() {
        throw new UnsupportedOperationException("MigrationHelper is a utility class");
    }

    /**
     * Returns {@code true} if the current thread is a virtual thread.
     *
     * @return {@code true} for virtual threads, {@code false} for platform threads
     */
    public static boolean isVirtualThread() {
        return Thread.currentThread().isVirtual();
    }

    /**
     * Returns a human-readable description of the current thread type.
     *
     * @return string describing whether this is a virtual or platform thread
     */
    public static String getThreadTypeInfo() {
        Thread t = Thread.currentThread();
        return String.format("Thread[name=%s, virtual=%b, id=%d]",
                t.getName(), t.isVirtual(), t.threadId());
    }

    /**
     * Validates that the given tenant context matches the currently scoped context,
     * if a scoped context is active. Increments migration statistics counters.
     *
     * <p>This method is a no-op when {@code ctx} is null and no scoped context is active.</p>
     *
     * @param ctx the tenant context to validate against the current scope; may be null
     * @throws IllegalStateException if a scoped context is active but does not match {@code ctx}
     */
    public static void validateScopedUsage(TenantContext ctx) {
        if (ScopedTenantContext.hasTenantContext()) {
            _scopedCallCount.incrementAndGet();
            TenantContext scoped = ScopedTenantContext.getTenantContext();
            if (ctx != null && !ctx.equals(scoped)) {
                throw new IllegalStateException(
                        "TenantContext mismatch: expected " + ctx.getTenantId() +
                        " but scoped context is " + (scoped != null ? scoped.getTenantId() : "null"));
            }
        } else {
            _threadLocalCallCount.incrementAndGet();
        }
    }

    /**
     * Returns a summary of migration statistics collected via {@link #validateScopedUsage}.
     *
     * @return human-readable statistics string
     */
    public static String getMigrationStatistics() {
        return String.format(
                "MigrationStats{scopedCalls=%d, threadLocalCalls=%d, virtualThread=%b}",
                _scopedCallCount.get(),
                _threadLocalCallCount.get(),
                isVirtualThread());
    }

    /**
     * Resets migration statistics counters. Intended for test isolation only.
     */
    public static void resetStatistics() {
        _scopedCallCount.set(0);
        _threadLocalCallCount.set(0);
    }
}
