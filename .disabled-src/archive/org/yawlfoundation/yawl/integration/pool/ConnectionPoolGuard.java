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

package org.yawlfoundation.yawl.integration.pool;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Java 25 Semaphore-based bulkhead guard for connection pools.
 *
 * <p>Wraps any connection-acquiring {@link Callable} with a bounded {@link Semaphore}
 * and a 100ms fail-fast timeout. If all permits are held, callers receive a
 * {@link PoolExhaustedException} within 100ms instead of blocking indefinitely.</p>
 *
 * <p>This eliminates thread accumulation caused by
 * {@code blockWhenExhausted=true} in connection pool configurations when the
 * downstream database is slow or the pool is under-sized.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ConnectionPoolGuard guard = ConnectionPoolGuard.of(config.getMaxTotal());
 * String result = guard.withConnection(() -> jdbcTemplate.queryForObject(sql, String.class));
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ConnectionPoolGuard {

    /** Maximum wait before failing fast when all permits are held. */
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofMillis(100);

    private final Semaphore _bulkhead;
    private final int _capacity;
    private final LongAdder _timeouts = new LongAdder();
    private final LongAdder _successes = new LongAdder();

    private ConnectionPoolGuard(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        _capacity = capacity;
        _bulkhead = new Semaphore(capacity, true);  // fair
    }

    /**
     * Creates a guard with the specified number of permits (= pool maxTotal).
     *
     * @param capacity maximum concurrent holders; must be &gt; 0
     * @return new guard instance
     */
    public static ConnectionPoolGuard of(int capacity) {
        return new ConnectionPoolGuard(capacity);
    }

    /**
     * Acquires a permit, executes {@code operation}, and releases the permit.
     *
     * <p>If no permit is available within 100ms, throws {@link PoolExhaustedException}
     * immediately — the caller is never blocked beyond the timeout.</p>
     *
     * @param <T>       return type of the operation
     * @param operation the callable to execute with a pooled connection
     * @return result of the operation
     * @throws PoolExhaustedException if all permits are held beyond the timeout
     * @throws Exception              any exception thrown by {@code operation}
     */
    public <T> T withConnection(Callable<T> operation) throws Exception {
        if (!_bulkhead.tryAcquire(ACQUIRE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            _timeouts.increment();
            throw new PoolExhaustedException(_capacity - _bulkhead.availablePermits(), _capacity);
        }
        try {
            _successes.increment();
            return operation.call();
        } finally {
            _bulkhead.release();
        }
    }

    /** Returns the number of currently available permits. */
    public int availablePermits() {
        return _bulkhead.availablePermits();
    }

    /** Returns the pool capacity this guard was configured with. */
    public int capacity() {
        return _capacity;
    }

    /** Returns the total number of times a timeout/rejection occurred. */
    public long timeoutCount() {
        return _timeouts.sum();
    }

    /** Returns the total number of successful permit acquisitions. */
    public long successCount() {
        return _successes.sum();
    }

    /**
     * Thrown when all pool permits are held and the caller times out waiting.
     *
     * <p>Callers should catch this and return a degraded response or propagate
     * a 503 Service Unavailable rather than accumulating blocked threads.</p>
     */
    public static final class PoolExhaustedException extends Exception {

        private final int active;
        private final int capacity;

        /**
         * @param active   number of currently active connections
         * @param capacity total pool capacity
         */
        public PoolExhaustedException(int active, int capacity) {
            super("Connection pool exhausted: " + active + "/" + capacity
                  + " connections active; waited " + ACQUIRE_TIMEOUT.toMillis() + "ms");
            this.active = active;
            this.capacity = capacity;
        }

        /** Returns the number of active connections at time of exhaustion. */
        public int getActive() { return active; }

        /** Returns the pool capacity. */
        public int getCapacity() { return capacity; }
    }
}
