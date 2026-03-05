/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.authentication;

import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.logging.table.YAuditEvent;
import org.yawlfoundation.yawl.util.Argon2PasswordEncryptor;
import org.yawlfoundation.yawl.util.HibernateEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.logging.table.YAuditEvent;
import org.yawlfoundation.yawl.util.HibernateEngine;

/**
 * Thread-safe session cache managing connections to the engine from custom services
 * and external applications.
 * <p>
 * The cache maps session handles to sessions using {@link ConcurrentHashMap}
 * with atomic operations for thread safety. Session timeouts are managed via
 * a {@link ScheduledExecutorService} with virtual threads (Java 25+).
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>All public methods are thread-safe</li>
 *   <li>Uses atomic compute operations for session management</li>
 *   <li>Read-write locks for iteration operations</li>
 *   <li>Virtual thread executor for timeout scheduling</li>
 * </ul>
 *
 * <h2>Java 25 Features</h2>
 * <ul>
 *   <li>Virtual threads via {@link Executors#newVirtualThreadPerTaskExecutor()}</li>
 *   <li>Structured concurrency patterns</li>
 *   <li>Atomic concurrent operations</li>
 * </ul>
 *
 * @author Michael Adams
 * @since 2.1
 * @see YSession
 * @see YSessionTimer
 */
public final class YSessionCache implements ISessionCache {

    private static final Logger LOGGER = Logger.getLogger(YSessionCache.class.getName());

    /** Default session timeout in milliseconds (60 minutes) */
    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(60);

    /** Session storage: handle -> session mapping */
    private final ConcurrentHashMap<String, YSession> sessions;

    /** Session timeout scheduler using virtual threads */
    private final ScheduledExecutorService scheduler;

    /** Timeout task tracking for cancellation */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutTasks;

    /** Database writer for audit events */
    private volatile HibernateEngine database;

    /** Shutdown flag for graceful termination */
    private final AtomicBoolean shutdownRequested;

    /** Read-write lock for bulk operations */
    private final ReentrantReadWriteLock rwLock;

    /** Metrics: total connections count */
    private final AtomicLong connectionCount;

    /** Metrics: active sessions count */
    private final AtomicLong activeSessionsCount;

    /**
     * Creates a new session cache with default settings.
     * Initializes the timeout scheduler with virtual threads.
     * Virtual threads are named for improved debugging and observability.
     */
    public YSessionCache() {
        this.sessions = new ConcurrentHashMap<>();
        this.timeoutTasks = new ConcurrentHashMap<>();
        // Create virtual thread factory with descriptive naming for observability
        // Thread names follow pattern: session-timeout-<counter> for identification in logs
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual()
                    .name("session-timeout-", 1)  // Named thread: session-timeout-1
                    .factory()
        );
        this.shutdownRequested = new AtomicBoolean(false);
        this.rwLock = new ReentrantReadWriteLock();
        this.connectionCount = new AtomicLong(0);
        this.activeSessionsCount = new AtomicLong(0);
        initializeDatabase();
    }

    // ========================================================================
    // PUBLIC API - Connection Management
    // ========================================================================

    /**
     * Creates and stores a new session between the Engine and a custom service
     * or external application.
     *
     * @param name the username of the external client
     * @param password the corresponding (hashed) password
     * @param timeOutSeconds the maximum idle time for this session (in seconds).
     *                       A value of 0 defaults to 60 minutes; negative means no timeout.
     * @return a valid session handle, or an error message wrapped in XML
     */
    @Override
    public String connect(String name, String password, long timeOutSeconds) {
        if (name == null) {
            return failureMessage("Username cannot be null");
        }

        if (shutdownRequested.get()) {
            return failureMessage("Cache is shutting down");
        }

        connectionCount.incrementAndGet();

        // Check for external client first
        YExternalClient client = YEngine.getInstance().getExternalClient(name);
        if (client != null) {
            return authenticateClient(client, password, timeOutSeconds, name);
        }

        // Check for YAWL service
        YAWLServiceReference service = findService(name);
        if (service != null) {
            return authenticateService(service, password, timeOutSeconds, name);
        }

        return handleUnknownUser(name);
    }

    /**
     * Checks that a session handle represents an active session.
     * If valid, resets the session idle timer.
     *
     * @param handle the session handle held by a client or service
     * @return true if the handle's session is active, false otherwise
     */
    @Override
    public boolean checkConnection(String handle) {
        if (handle == null || shutdownRequested.get()) {
            return false;
        }

        YSession session = sessions.get(handle);
        if (session == null) {
            return false;
        }

        // Reset the timer atomically
        resetTimeout(session);
        return true;
    }

    /**
     * Checks that a particular custom service has an active session with the Engine.
     *
     * @param uri the URI of the custom service
     * @return true if the service has an active session
     */
    public boolean isServiceConnected(String uri) {
        if (uri == null) {
            return false;
        }

        rwLock.readLock().lock();
        try {
            return sessions.values().stream()
                    .anyMatch(session -> uri.equals(session.getURI()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Checks that a particular external client has an active session with the Engine.
     *
     * @param client the client to check
     * @return true if the client has an active session
     */
    public boolean isClientConnected(YExternalClient client) {
        if (client == null) {
            return false;
        }

        rwLock.readLock().lock();
        try {
            return sessions.values().stream()
                    .anyMatch(session -> session.getClient() == client);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Gets the session associated with a session handle.
     *
     * @param handle a session handle
     * @return the session object, or null if handle is invalid or inactive
     */
    @Override
    public YSession getSession(String handle) {
        if (handle == null) {
            return null;
        }
        return sessions.get(handle);
    }

    /**
     * Removes a session from the active set after an idle timeout.
     * Writes the expiration to the session audit log.
     *
     * @param handle the session handle to expire
     */
    @Override
    public void expire(String handle) {
        removeSession(handle, YAuditEvent.Action.expired);
    }

    /**
     * Ends an active session by client reference.
     *
     * @param client the service or application to disconnect
     */
    public void disconnect(YClient client) {
        if (client == null) {
            return;
        }

        // Find and remove the first matching session
        sessions.entrySet().stream()
                .filter(entry -> entry.getValue().getClient() == client)
                .findFirst()
                .ifPresent(entry -> disconnect(entry.getKey()));
    }

    /**
     * Ends an active session by handle.
     * Writes the disconnection to the session audit log.
     *
     * @param handle the session handle to disconnect
     */
    @Override
    public void disconnect(String handle) {
        removeSession(handle, YAuditEvent.Action.logoff);
    }

    /**
     * Called when the hosting server shuts down.
     * Writes shutdown records for all active sessions to the audit log.
     */
    @Override
    public void shutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            return; // Already shutting down
        }

        LOGGER.info("Shutting down session cache...");

        // Cancel all pending timeouts
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();

        // Write shutdown audit for each session
        rwLock.writeLock().lock();
        try {
            sessions.values().forEach(session -> {
                YClient client = session.getClient();
                if (client != null) {
                    writeAuditEvent(client.getUserName(), YAuditEvent.Action.shutdown);
                }
            });
            sessions.clear();
            activeSessionsCount.set(0);
        } finally {
            rwLock.writeLock().unlock();
        }

        // Shutdown the scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("Session cache shutdown complete");
    }

    // ========================================================================
    // PUBLIC API - Metrics
    // ========================================================================

    /**
     * Returns the current number of active sessions.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Returns the total number of connection attempts since startup.
     *
     * @return total connection count
     */
    public long getTotalConnectionCount() {
        return connectionCount.get();
    }

    /**
     * Returns all currently active session handles.
     *
     * @return set of active session handles
     */
    public Set<String> getActiveHandles() {
        rwLock.readLock().lock();
        try {
            return new HashSet<>(sessions.keySet());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears all active sessions without shutting down the cache.
     * Primarily intended for testing purposes to reset state between tests.
     *
     * <p>This method cancels all pending timeouts and removes all sessions,
     * but keeps the scheduler and other infrastructure alive for reuse.</p>
     */
    public void clear() {
        // Cancel all pending timeouts
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();

        // Clear all sessions
        rwLock.writeLock().lock();
        try {
            sessions.clear();
            activeSessionsCount.set(0);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // PRIVATE - Authentication
    // ========================================================================

    private String authenticateClient(YExternalClient client, String password,
                                       long timeoutSeconds, String name) {
        if (!validateClientCredentials(client, password)) {
            return handleBadPassword(name);
        }

        YSession session = "admin".equals(name)
                ? new YSession(client, timeoutSeconds)
                : new YExternalSession(client, timeoutSeconds);

        return storeSession(session);
    }

    private String authenticateService(YAWLServiceReference service, String password,
                                        long timeoutSeconds, String name) {
        if (!validateServiceCredentials(service, password)) {
            return handleBadPassword(name);
        }

        YSession session = new YServiceSession(service, timeoutSeconds);
        return storeSession(session);
    }

    /**
     * Validates client credentials with support for both Argon2id (new) and SHA-1 (legacy)
     * stored hashes to enable backward-compatible migration.
     *
     * <p>SOC2 CRITICAL#4 remediation (2026-02-17): New accounts created by
     * {@code YDefClientsLoader} use Argon2id PHC strings (prefixed {@code $argon2id$}).
     * Existing SHA-1 hashes (Base64-encoded, no prefix) are verified with constant-time
     * comparison during the migration window. Once all hashes have been rotated to
     * Argon2id, SHA-1 verification can be removed.
     *
     * <p>Migration: when a user authenticates successfully with a SHA-1 hash, the operator
     * should trigger a password rotation via the admin API to upgrade the stored hash to
     * Argon2id. See SECURITY.md section "SHA-1 to Argon2id migration procedure".
     *
     * @param client the client to validate
     * @param password the password to verify (may be SHA-1 pre-hashed by the client)
     * @return true if credentials are valid, false otherwise
     */
    private boolean validateClientCredentials(YExternalClient client, String password) {
        String storedPassword = client.getPassword();
        return verifyPassword(storedPassword, password);
    }

    /**
     * Validates service credentials with support for both Argon2id (new) and SHA-1 (legacy)
     * stored hashes. See {@link #validateClientCredentials} for migration details.
     *
     * @param service the service to validate
     * @param password the password to verify (may be SHA-1 pre-hashed by the client)
     * @return true if credentials are valid, false otherwise
     */
    private boolean validateServiceCredentials(YAWLServiceReference service, String password) {
        String storedPassword = service.getServicePassword();
        return verifyPassword(storedPassword, password);
    }

    /**
     * Verifies a password against a stored hash, supporting both Argon2id (new) and
     * SHA-1 (legacy) hash formats for backward-compatible migration.
     *
     * <p>Detection: if the stored hash starts with {@code $argon2id$}, it is an Argon2id
     * PHC string and is verified via {@link Argon2PasswordEncryptor#verify}. Otherwise,
     * it is treated as a SHA-1 Base64-encoded hash and compared with constant-time equality.
     *
     * <p>SOC2 CRITICAL#4: All new password hashes use Argon2id. SHA-1 hashes remain
     * supported only for the migration window. See SECURITY.md.
     *
     * @param storedHash the stored password hash (Argon2id PHC string or SHA-1 Base64)
     * @param provided   the password as provided by the client
     * @return true if the password matches the stored hash, false otherwise
     */
    private boolean verifyPassword(String storedHash, String provided) {
        if (storedHash == null || provided == null) {
            return false;
        }
        if (storedHash.startsWith("$argon2id$")) {
            // SOC2 CRITICAL#4: New Argon2id hash - use proper Argon2id verification
            try {
                return Argon2PasswordEncryptor.verify(storedHash, provided);
            } catch (Exception e) {
                LOGGER.warning("Argon2id verification failed for stored hash: " + e.getMessage());
                return false;
            }
        }
        // Legacy SHA-1 hash: constant-time comparison for migration window
        // MIGRATION NOTE: Trigger password rotation via admin API to upgrade to Argon2id.
        return constantTimeEquals(storedHash, provided);
    }

    /**
     * Compares two strings in constant time to prevent timing attacks.
     * <p>
     * Uses {@link MessageDigest#isEqual(byte[], byte[])} which is designed for
     * constant-time comparison of sensitive data like passwords and cryptographic tokens.
     * </p>
     *
     * @param expected the expected value (stored password)
     * @param provided the provided value (user input)
     * @return true if strings match, false otherwise (including null cases)
     */
    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    private YAWLServiceReference findService(String name) {
        YEngine engine = YEngine.getInstance();

        if ("DefaultWorklist".equals(name)) {
            return engine.getDefaultWorklist();
        }

        return engine.getYAWLServices().stream()
                .filter(service -> name.equals(service.getServiceName()))
                .findFirst()
                .orElse(null);
    }

    // ========================================================================
    // PRIVATE - Session Storage
    // ========================================================================

    private String storeSession(YSession session) {
        String handle = session.getHandle();

        // Use computeIfAbsent for atomic put-if-absent
        YSession existing = sessions.putIfAbsent(handle, session);

        if (existing != null) {
            // Handle collision - should be extremely rare with UUID
            LOGGER.warning("Session handle collision detected for: " + handle);
            return failureMessage("Session handle collision - please retry");
        }

        activeSessionsCount.incrementAndGet();

        // Schedule timeout
        scheduleTimeout(session);

        // Write audit log (database)
        YClient client = session.getClient();
        if (client != null) {
            writeAuditEvent(client.getUserName(), YAuditEvent.Action.logon);
            // SOC2 HIGH: All successful logins must be logged with timestamp, user, result
            SecurityAuditLogger.loginSuccess(client.getUserName(), "engine", "session created");
            SecurityAuditLogger.sessionCreated(client.getUserName(), handle, "engine");
        }

        return handle;
    }

    private Optional<YSession> removeSession(String handle, YAuditEvent.Action action) {
        if (handle == null) {
            return Optional.empty();
        }

        // Cancel any pending timeout
        cancelTimeout(handle);

        // Remove session atomically
        YSession session = sessions.remove(handle);

        if (session != null) {
            activeSessionsCount.decrementAndGet();

            YClient client = session.getClient();
            if (client != null) {
                writeAuditEvent(client.getUserName(), action);
            }
        }

        return Optional.ofNullable(session);
    }

    // ========================================================================
    // PRIVATE - Timeout Management
    // ========================================================================

    private void scheduleTimeout(YSession session) {
        long intervalMs = session.getInterval();

        // Negative interval means never timeout
        if (intervalMs <= 0) {
            return;
        }

        String handle = session.getHandle();

        ScheduledFuture<?> future = scheduler.schedule(
                () -> expireSession(handle),
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        timeoutTasks.put(handle, future);
    }

    private void resetTimeout(YSession session) {
        String handle = session.getHandle();

        // Cancel existing timeout
        cancelTimeout(handle);

        // Schedule new timeout
        scheduleTimeout(session);
    }

    private void cancelTimeout(String handle) {
        ScheduledFuture<?> future = timeoutTasks.remove(handle);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void expireSession(String handle) {
        // Only expire if still present and not being accessed
        sessions.computeIfPresent(handle, (h, session) -> {
            expire(handle);
            return null; // Remove the entry
        });
    }

    // ========================================================================
    // PRIVATE - Error Handling & Audit
    // ========================================================================

    private String handleBadPassword(String username) {
        writeAuditEvent(username, YAuditEvent.Action.invalid);
        // SOC2 HIGH: All authentication failures must be logged with timestamp, user, result
        SecurityAuditLogger.loginFailure(username, "engine", "bad credentials");
        return failureMessage("Incorrect Password");
    }

    private String handleUnknownUser(String username) {
        writeAuditEvent(username, YAuditEvent.Action.unknown);
        // SOC2 HIGH: Unknown user attempts must be logged as auth failures
        SecurityAuditLogger.loginFailure(username, "engine", "unknown user");
        return failureMessage("Unknown service or client: " + username);
    }

    private String failureMessage(String message) {
        return String.format("<failure>%s</failure>", message);
    }

    private void writeAuditEvent(String username, YAuditEvent.Action action) {
        if (database != null) {
            try {
                database.exec(
                        new YAuditEvent(username, action),
                        HibernateEngine.DB_INSERT,
                        true
                );
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Failed to write audit event for user: " + username, e);
            }
        }
    }

    // ========================================================================
    // PRIVATE - Initialization
    // ========================================================================

    private void initializeDatabase() {
        try {
            Set<Class> classSet = new HashSet<>();
            classSet.add(YAuditEvent.class);
            database = new HibernateEngine(true, classSet);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database for audit logging", e);
            database = null;
        }
    }
}
