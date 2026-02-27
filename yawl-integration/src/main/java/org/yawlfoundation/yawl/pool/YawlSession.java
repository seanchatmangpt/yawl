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

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

/**
 * Wrapper object for pooled YAWL engine connections.
 *
 * <p>Each YawlSession wraps an InterfaceB client with an active session handle,
 * providing connection validation, usage tracking, and lifecycle management.
 * Instances are managed by {@link YawlConnectionPool} and should be returned
 * to the pool after use via {@link #close()}.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>Instances are NOT thread-safe. Each thread should borrow its own session
 * from the pool. The pool itself is thread-safe for borrow/return operations.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (YawlSession session = pool.borrowSession()) {
 *     String caseId = session.getClient().launchCase(specId, params, session.getHandle());
 *     // ... work with the session ...
 * } // Automatically returned to pool
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @see YawlConnectionPool
 */
public class YawlSession implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(YawlSession.class.getName());

    private final String sessionId;
    private final InterfaceB_EnvironmentBasedClient client;
    private final String sessionHandle;
    private final Instant createdAt;
    private final AtomicLong lastUsedAt;
    private final AtomicLong borrowCount;
    private final AtomicBoolean valid;
    private final AtomicBoolean closed;

    private volatile YawlConnectionPool owningPool;

    /**
     * Create a new YawlSession with an established connection.
     *
     * @param sessionId     unique identifier for this session
     * @param client        the InterfaceB client
     * @param sessionHandle the active session handle from YAWL engine
     * @throws IllegalArgumentException if any required parameter is null
     */
    public YawlSession(String sessionId,
                       InterfaceB_EnvironmentBasedClient client,
                       String sessionHandle) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId is required");
        this.client = Objects.requireNonNull(client, "client is required");
        this.sessionHandle = Objects.requireNonNull(sessionHandle, "sessionHandle is required");
        this.createdAt = Instant.now();
        this.lastUsedAt = new AtomicLong(System.currentTimeMillis());
        this.borrowCount = new AtomicLong(0);
        this.valid = new AtomicBoolean(true);
        this.closed = new AtomicBoolean(false);

        LOGGER.fine(() -> "Created YawlSession: " + sessionId);
    }

    /**
     * Get the unique session identifier.
     *
     * @return session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get the InterfaceB client for making YAWL API calls.
     *
     * @return the client instance
     */
    public InterfaceB_EnvironmentBasedClient getClient() {
        touch();
        return client;
    }

    /**
     * Get the active session handle for YAWL API calls.
     *
     * @return session handle string
     */
    public String getHandle() {
        touch();
        return sessionHandle;
    }

    /**
     * Get the backend URI this session connects to.
     *
     * @return backend URI string
     */
    public String getBackendUri() {
        return client.getBackEndURI();
    }

    /**
     * Get the creation timestamp of this session.
     *
     * @return instant when session was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the timestamp of last use.
     *
     * @return epoch millis of last use
     */
    public long getLastUsedAt() {
        return lastUsedAt.get();
    }

    /**
     * Get the number of times this session has been borrowed.
     *
     * @return borrow count
     */
    public long getBorrowCount() {
        return borrowCount.get();
    }

    /**
     * Check if this session is still valid.
     *
     * @return true if session is valid for use
     */
    public boolean isValid() {
        return valid.get() && !closed.get();
    }

    /**
     * Check if this session has been closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Validate the session by checking connection to YAWL engine.
     *
     * @return true if session is valid and engine is responsive
     */
    public boolean validate() {
        if (closed.get()) {
            return false;
        }

        try {
            String result = client.checkConnection(sessionHandle);
            boolean isValid = result != null && !result.contains("<failure>");
            valid.set(isValid);
            return isValid;
        } catch (IOException e) {
            LOGGER.warning(() -> "Session validation failed for " + sessionId + ": " + e.getMessage());
            valid.set(false);
            return false;
        }
    }

    /**
     * Disconnect this session from the YAWL engine.
     * Called by the pool when evicting invalid or idle sessions.
     */
    public void disconnect() {
        if (closed.compareAndSet(false, true)) {
            valid.set(false);
            try {
                client.disconnect(sessionHandle);
                LOGGER.fine(() -> "Disconnected YawlSession: " + sessionId);
            } catch (IOException e) {
                LOGGER.warning(() -> "Error disconnecting session " + sessionId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Set the owning pool for automatic return on close.
     *
     * @param pool the connection pool
     */
    void setOwningPool(YawlConnectionPool pool) {
        this.owningPool = pool;
    }

    /**
     * Mark this session as borrowed from the pool.
     * Called by the pool when lending out the session.
     */
    void markBorrowed() {
        borrowCount.incrementAndGet();
        touch();
    }

    /**
     * Mark this session as returned to the pool.
     * Called by the pool when the session is returned.
     */
    void markReturned() {
        touch();
    }

    /**
     * Mark this session as invalid.
     * Called by the pool when validation fails.
     */
    void invalidate() {
        valid.set(false);
    }

    /**
     * Update the last used timestamp.
     */
    private void touch() {
        lastUsedAt.set(System.currentTimeMillis());
    }

    /**
     * Close this session and return it to the pool.
     * If no owning pool is set, disconnects the session instead.
     */
    @Override
    public void close() {
        if (owningPool != null) {
            owningPool.returnSession(this);
        } else {
            disconnect();
        }
    }

    @Override
    public String toString() {
        return String.format("YawlSession{id=%s, uri=%s, created=%s, borrows=%d, valid=%s}",
                sessionId, getBackendUri(), createdAt, borrowCount.get(), valid.get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YawlSession that = (YawlSession) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
