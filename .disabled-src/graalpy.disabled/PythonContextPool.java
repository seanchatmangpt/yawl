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

package org.yawlfoundation.yawl.graalpy;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Thread-safe pool of {@link PythonExecutionContext} instances.
 *
 * <p>GraalPy contexts implement a Global Interpreter Lock (GIL) internally, so
 * concurrent Java threads must each hold their own context. This pool manages
 * a configurable number of pre-warmed contexts, reducing per-invocation overhead
 * from GraalPy initialisation (~50-200ms) to near-zero borrow cost.</p>
 *
 * <h2>Usage pattern</h2>
 * <pre>{@code
 * try (PythonContextPool pool = PythonContextPool.create(sandboxConfig, 4)) {
 *     Object result = pool.execute(ctx -> {
 *         Value val = ctx.eval("sum([1, 2, 3])");
 *         return TypeMarshaller.toJava(val);
 *     });
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>This class is fully thread-safe. Multiple Java threads may call
 * {@link #execute(ContextCallback)} concurrently; each call borrows an exclusive
 * context from the pool, uses it, and returns it.</p>
 *
 * <h2>GIL release</h2>
 * <p>When a Python context is returned to the pool, GraalPy's GIL is implicitly
 * released, allowing the next borrower's thread to enter Python execution without
 * deadlock.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonContextPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PythonContextPool.class);

    /** Functional interface for operations performed on a borrowed context. */
    @FunctionalInterface
    public interface ContextCallback<T> {
        /**
         * Executes a Python operation on the given context.
         *
         * @param context  the borrowed Python execution context; never null
         * @return the result of the operation; may be null
         * @throws PythonException  if the Python operation fails
         */
        T execute(PythonExecutionContext context);
    }

    private final GenericObjectPool<PythonExecutionContext> pool;
    private final PythonSandboxConfig sandboxConfig;

    private PythonContextPool(PythonSandboxConfig sandboxConfig, int maxPoolSize,
                               Duration maxWait, Duration minEvictableIdleTime) {
        this.sandboxConfig = sandboxConfig;

        GenericObjectPoolConfig<PythonExecutionContext> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxPoolSize);
        config.setMinIdle(1);
        config.setMaxIdle(maxPoolSize);
        config.setMaxWait(maxWait);
        config.setMinEvictableIdleTime(minEvictableIdleTime);
        config.setTimeBetweenEvictionRuns(Duration.ofMinutes(5));
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);
        config.setBlockWhenExhausted(true);

        this.pool = new GenericObjectPool<>(new ContextFactory(sandboxConfig), config);

        log.info("PythonContextPool initialised: maxSize={}, sandbox={}", maxPoolSize, sandboxConfig.getMode());
    }

    /**
     * Creates a new pool with the given sandbox configuration and maximum pool size.
     *
     * @param sandboxConfig  security policy for all contexts in this pool
     * @param maxPoolSize    maximum number of concurrent Python contexts (≥ 1)
     * @return a new pool; never null
     * @throws IllegalArgumentException  if maxPoolSize is less than 1
     */
    public static PythonContextPool create(PythonSandboxConfig sandboxConfig, int maxPoolSize) {
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("maxPoolSize must be at least 1, got: " + maxPoolSize);
        }
        return new PythonContextPool(sandboxConfig, maxPoolSize,
                Duration.ofSeconds(30), Duration.ofMinutes(10));
    }

    /**
     * Creates a pool using a {@link Builder} for full configuration.
     *
     * @return a new Builder; never null
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Borrows a Python context, executes the callback, and returns the context to the pool.
     *
     * <p>The borrowed context is held exclusively by the calling thread for the duration
     * of the callback. If the pool is exhausted, the call blocks until a context becomes
     * available or the configured {@code maxWait} duration elapses.</p>
     *
     * @param callback  operation to execute on the borrowed context; must not be null
     * @param <T>  return type of the callback
     * @return the result returned by {@code callback}
     * @throws PythonException  if no context is available within the wait timeout,
     *                           context initialisation fails, or the callback raises an exception
     */
    public <T> T execute(ContextCallback<T> callback) {
        PythonExecutionContext context;
        try {
            context = pool.borrowObject();
        } catch (Exception e) {
            throw new PythonException(
                    "Failed to borrow Python context from pool. Pool exhausted or context failed to initialise. "
                    + "Pool state: active=" + pool.getNumActive() + ", idle=" + pool.getNumIdle()
                    + ". Cause: " + e.getMessage(),
                    PythonException.ErrorKind.CONTEXT_ERROR, e);
        }
        try {
            return callback.execute(context);
        } finally {
            pool.returnObject(context);
        }
    }

    /**
     * Executes a void operation on a borrowed Python context.
     *
     * @param action  operation to execute; must not be null
     * @throws PythonException  if the pool is exhausted or the action fails
     */
    public void executeVoid(Consumer<PythonExecutionContext> action) {
        execute(ctx -> { action.accept(ctx); return null; });
    }

    /** Returns the number of contexts currently borrowed from the pool. */
    public int getActiveCount() { return pool.getNumActive(); }

    /** Returns the number of idle contexts currently in the pool. */
    public int getIdleCount() { return pool.getNumIdle(); }

    /** Returns the maximum number of contexts this pool manages. */
    public int getMaxTotal() { return pool.getMaxTotal(); }

    /** Returns the sandbox configuration shared by all contexts in this pool. */
    public PythonSandboxConfig getSandboxConfig() { return sandboxConfig; }

    /**
     * Closes the pool and all contexts it manages.
     *
     * <p>Blocks until all borrowed contexts are returned, then closes each one.
     * After this call, further invocations of {@link #execute} will throw.</p>
     */
    @Override
    public void close() {
        pool.close();
        log.info("PythonContextPool closed");
    }

    // ── Inner types ──────────────────────────────────────────────────────────────

    /** Commons Pool2 factory that creates and validates PythonExecutionContext objects. */
    private static final class ContextFactory extends BasePooledObjectFactory<PythonExecutionContext> {

        private final PythonSandboxConfig sandboxConfig;

        ContextFactory(PythonSandboxConfig sandboxConfig) {
            this.sandboxConfig = sandboxConfig;
        }

        @Override
        public PythonExecutionContext create() {
            return new PythonExecutionContext(sandboxConfig);
        }

        @Override
        public PooledObject<PythonExecutionContext> wrap(PythonExecutionContext ctx) {
            return new DefaultPooledObject<>(ctx);
        }

        @Override
        public boolean validateObject(PooledObject<PythonExecutionContext> pooledObj) {
            PythonExecutionContext ctx = pooledObj.getObject();
            return ctx != null && !ctx.isClosed();
        }

        @Override
        public void destroyObject(PooledObject<PythonExecutionContext> pooledObj) {
            PythonExecutionContext ctx = pooledObj.getObject();
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    /** Builder for full pool configuration. */
    public static final class Builder {
        private PythonSandboxConfig sandboxConfig = PythonSandboxConfig.standard();
        private int maxPoolSize = Runtime.getRuntime().availableProcessors();
        private Duration maxWait = Duration.ofSeconds(30);
        private Duration minEvictableIdleTime = Duration.ofMinutes(10);

        private Builder() {}

        public Builder sandboxConfig(PythonSandboxConfig config) { this.sandboxConfig = config; return this; }
        public Builder maxPoolSize(int size) { this.maxPoolSize = size; return this; }
        public Builder maxWait(Duration wait) { this.maxWait = wait; return this; }
        public Builder minEvictableIdleTime(Duration time) { this.minEvictableIdleTime = time; return this; }

        public PythonContextPool build() {
            if (maxPoolSize < 1) {
                throw new IllegalArgumentException("maxPoolSize must be at least 1");
            }
            return new PythonContextPool(sandboxConfig, maxPoolSize, maxWait, minEvictableIdleTime);
        }
    }
}
