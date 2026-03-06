/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.yawlfoundation.yawl.dspy.otp.builder.DspyOtpProgramBuilder;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNodePool;

import java.lang.foreign.Arena;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Layer 3 fluent API entry point for executing DSPy programs via OTP/Erlang.
 *
 * Hides all OTP and FFM complexity behind a clean, type-safe API.
 * Connection pooling and keepalive are handled automatically.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DspyOtpBridge bridge = DspyOtpBridge.connect("yawl_dspy@localhost", "secret");
 *
 * String result = bridge.program("sentiment-analyzer")
 *     .input("text", "This is amazing!")
 *     .execute()
 *     .await(5, TimeUnit.SECONDS)
 *     .getOutput("sentiment");
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe. Multiple threads may safely call methods
 * on the same bridge instance without synchronization.
 *
 * <h2>Connection Lifecycle</h2>
 * Connections are managed by the underlying {@link ErlangNodePool}.
 * The bridge acquires a connection on first use and holds it until
 * explicitly closed or a fatal error occurs (auto-reconnect on transient errors).
 *
 * @see DspyOtpProgramBuilder
 * @see org.yawlfoundation.yawl.dspy.otp.async.DspyOtpAsyncExecutor
 */
@NullMarked
public final class DspyOtpBridge implements AutoCloseable {

    private final String erlangNodeName;
    private final String cookie;
    private final ErlangNodePool nodePool;
    private final Duration defaultTimeout;
    private final ReentrantLock stateLock = new ReentrantLock();
    private volatile @Nullable ErlangNode activeNode;
    private volatile boolean closed;

    /**
     * Create a new OTP bridge for DSPy execution.
     *
     * @param erlangNodeName the Erlang node name (e.g., "yawl_dspy@localhost")
     * @param cookie the OTP cookie for authentication
     * @param nodePool the underlying connection pool
     * @param defaultTimeout default timeout for RPC calls
     */
    private DspyOtpBridge(String erlangNodeName, String cookie,
                          ErlangNodePool nodePool,
                          Duration defaultTimeout) {
        this.erlangNodeName = Objects.requireNonNull(erlangNodeName, "erlangNodeName");
        this.cookie = Objects.requireNonNull(cookie, "cookie");
        this.nodePool = Objects.requireNonNull(nodePool, "nodePool");
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout");
        this.closed = false;
    }

    /**
     * Connect to an Erlang node running DSPy bridge service.
     *
     * Acquires a connection from the pool. May block briefly if pool is saturated.
     *
     * @param erlangNodeName Erlang node identifier (e.g., "yawl_dspy@localhost")
     * @param cookie OTP cookie for authentication
     * @return a ready-to-use DSPy bridge
     * @throws DspyOtpException if connection fails (network, OTP unavailable)
     */
    public static DspyOtpBridge connect(String erlangNodeName, String cookie) {
        return connect(erlangNodeName, cookie, Duration.ofSeconds(5));
    }

    /**
     * Connect with custom default timeout.
     *
     * @param erlangNodeName Erlang node identifier
     * @param cookie OTP cookie
     * @param defaultTimeout default timeout for RPC calls
     * @return a ready-to-use DSPy bridge
     * @throws DspyOtpException if connection fails
     */
    public static DspyOtpBridge connect(String erlangNodeName, String cookie,
                                        Duration defaultTimeout) {
        ErlangNodePool pool = ErlangNodePool.instance();
        return new DspyOtpBridge(erlangNodeName, cookie, pool, defaultTimeout);
    }

    /**
     * Start building a fluent request to execute a DSPy program.
     *
     * @param programName the name of the DSPy program (e.g., "sentiment-analyzer")
     * @return a fluent builder for specifying inputs
     * @throws DspyOtpException if bridge is closed or connection lost
     */
    public DspyOtpProgramBuilder program(String programName) {
        ensureOpen();
        ErlangNode node = getOrAcquireNode();
        return new DspyOtpProgramBuilder(programName, node, erlangNodeName, defaultTimeout);
    }

    /**
     * Get the default timeout for this bridge (used if not overridden in execute()).
     *
     * @return the timeout duration
     */
    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    /**
     * Check if this bridge is still open and connected.
     *
     * @return true if operational
     */
    public boolean isOpen() {
        return !closed && activeNode != null;
    }

    /**
     * Explicitly close this bridge and release its connection.
     * After closing, any call to {@link #program(String)} will raise an exception.
     */
    @Override
    public void close() {
        stateLock.lock();
        try {
            if (!closed && activeNode != null) {
                nodePool.release(activeNode);
                activeNode = null;
            }
            closed = true;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Internal: get or acquire the active Erlang node.
     * Lazy initializes on first access.
     */
    ErlangNode getOrAcquireNode() {
        if (activeNode != null) {
            return activeNode;
        }

        stateLock.lock();
        try {
            if (activeNode == null) {
                activeNode = nodePool.acquire(erlangNodeName, cookie);
            }
            return activeNode;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Internal: validate bridge state.
     */
    private void ensureOpen() {
        if (closed) {
            throw new DspyOtpException("Bridge is closed. Cannot execute new operations.");
        }
    }

    @Override
    public String toString() {
        return "DspyOtpBridge{" +
                "erlangNodeName='" + erlangNodeName + '\'' +
                ", connected=" + isOpen() +
                '}';
    }
}
