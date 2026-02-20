package org.yawlfoundation.yawl.integration.claude;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages multi-turn Claude Code CLI conversation sessions.
 *
 * <p>Provides session state management for continuing conversations
 * across multiple CLI invocations. Sessions have configurable TTL
 * and are automatically cleaned up on expiration.</p>
 *
 * <p>Thread-safe implementation using ConcurrentHashMap.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ClaudeSessionManager {

    private static final Logger LOGGER = Logger.getLogger(ClaudeSessionManager.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Default session timeout */
    private static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(30);

    /** Maximum number of concurrent sessions */
    private static final int DEFAULT_MAX_SESSIONS = 100;

    /** Cleanup interval for expired sessions */
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final Duration sessionTimeout;
    private final int maxSessions;
    private final Map<String, SessionState> sessions;
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a new ClaudeSessionManager with default settings.
     */
    public ClaudeSessionManager() {
        this(DEFAULT_SESSION_TIMEOUT, DEFAULT_MAX_SESSIONS);
    }

    /**
     * Creates a new ClaudeSessionManager with custom settings.
     *
     * @param sessionTimeout session TTL
     * @param maxSessions    maximum concurrent sessions
     */
    public ClaudeSessionManager(Duration sessionTimeout, int maxSessions) {
        this.sessionTimeout = sessionTimeout;
        this.maxSessions = maxSessions;
        this.sessions = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "claude-session-cleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            CLEANUP_INTERVAL.toMillis(),
            CLEANUP_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("ClaudeSessionManager initialized with timeout: " + sessionTimeout +
                    ", maxSessions: " + maxSessions);
    }

    /**
     * Creates a new session and returns its ID.
     *
     * @param initialPrompt the initial prompt for context
     * @return the new session ID
     * @throws IllegalStateException if max sessions exceeded
     */
    public String createSession(String initialPrompt) {
        enforceMaxSessions();

        String sessionId = generateSessionId();
        SessionState state = new SessionState(
            sessionId,
            initialPrompt,
            Instant.now(),
            Instant.now().plus(sessionTimeout)
        );

        sessions.put(sessionId, state);
        LOGGER.fine("Created session: " + sessionId);

        return sessionId;
    }

    /**
     * Gets an existing session, extending its TTL.
     *
     * @param sessionId the session ID
     * @return Optional containing session state if found and not expired
     */
    public Optional<SessionState> getSession(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return Optional.empty();
        }

        if (state.isExpired()) {
            sessions.remove(sessionId);
            LOGGER.fine("Session expired: " + sessionId);
            return Optional.empty();
        }

        // Extend TTL on access
        SessionState extended = state.extend(sessionTimeout);
        sessions.put(sessionId, extended);

        return Optional.of(extended);
    }

    /**
     * Updates a session with new context.
     *
     * @param sessionId the session ID
     * @param context   additional context to add
     * @return true if session was updated
     */
    public boolean updateSession(String sessionId, String context) {
        return getSession(sessionId).map(state -> {
            SessionState updated = state.addContext(context).extend(sessionTimeout);
            sessions.put(sessionId, updated);
            return true;
        }).orElse(false);
    }

    /**
     * Records a successful execution in a session.
     *
     * @param sessionId the session ID
     * @param prompt    the prompt that was executed
     * @param output    the output received
     * @return true if recorded successfully
     */
    public boolean recordExecution(String sessionId, String prompt, String output) {
        return getSession(sessionId).map(state -> {
            SessionState updated = state
                .addContext("Prompt: " + truncate(prompt, 500))
                .addContext("Output: " + truncate(output, 1000))
                .incrementTurnCount()
                .extend(sessionTimeout);
            sessions.put(sessionId, updated);
            return true;
        }).orElse(false);
    }

    /**
     * Terminates a session.
     *
     * @param sessionId the session ID to terminate
     * @return true if session was terminated
     */
    public boolean terminateSession(String sessionId) {
        SessionState removed = sessions.remove(sessionId);
        if (removed != null) {
            LOGGER.fine("Terminated session: " + sessionId);
            return true;
        }
        return false;
    }

    /**
     * Gets the current number of active sessions.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Checks if a session exists and is not expired.
     *
     * @param sessionId the session ID
     * @return true if session is active
     */
    public boolean hasSession(String sessionId) {
        return getSession(sessionId).isPresent();
    }

    /**
     * Cleans up all expired sessions.
     *
     * @return number of sessions cleaned up
     */
    public int cleanupExpiredSessions() {
        int count = 0;
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                count++;
            }
        }
        if (count > 0) {
            LOGGER.fine("Cleaned up " + count + " expired sessions");
        }
        return count;
    }

    /**
     * Shuts down the session manager.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        sessions.clear();
        LOGGER.info("ClaudeSessionManager shutdown complete");
    }

    /**
     * Generates a cryptographically secure unique session ID.
     *
     * <p>Uses {@link SecureRandom} to produce an unpredictable session identifier.
     * {@code Math.random()} is not used because it is predictable and must not
     * be used in security-sensitive contexts (session IDs are bearer tokens).</p>
     *
     * @return a unique, cryptographically-random session ID
     */
    private String generateSessionId() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(32);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return "claude-" + System.currentTimeMillis() + "-" + hex;
    }

    /**
     * Enforces maximum session limit.
     */
    private void enforceMaxSessions() {
        if (sessions.size() >= maxSessions) {
            // Try cleanup first
            cleanupExpiredSessions();

            if (sessions.size() >= maxSessions) {
                throw new IllegalStateException(
                    "Maximum session limit reached: " + maxSessions);
            }
        }
    }

    /** Default placeholder for null strings in context */
    private static final String NULL_PLACEHOLDER = "[null]";

    /**
     * Truncates a string to max length, replacing nulls with placeholder.
     *
     * @param s         the string to truncate (may be null)
     * @param maxLength maximum length before truncation
     * @return truncated string with null replaced by placeholder
     */
    private String truncate(String s, int maxLength) {
        String value = (s == null) ? NULL_PLACEHOLDER : s;
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }

    /**
     * Immutable session state record.
     */
    public static final class SessionState {
        private final String sessionId;
        private final String context;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final int turnCount;

        public SessionState(String sessionId, String context,
                           Instant createdAt, Instant expiresAt) {
            this(sessionId, context, createdAt, expiresAt, 1);
        }

        private SessionState(String sessionId, String context,
                            Instant createdAt, Instant expiresAt, int turnCount) {
            this.sessionId = sessionId;
            this.context = context;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.turnCount = turnCount;
        }

        public String sessionId() { return sessionId; }
        public String context() { return context; }
        public Instant createdAt() { return createdAt; }
        public Instant expiresAt() { return expiresAt; }
        public int turnCount() { return turnCount; }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public SessionState extend(Duration duration) {
            return new SessionState(sessionId, context, createdAt,
                                   Instant.now().plus(duration), turnCount);
        }

        public SessionState addContext(String additionalContext) {
            String newContext = context + "\n" + additionalContext;
            return new SessionState(sessionId, newContext, createdAt, expiresAt, turnCount);
        }

        public SessionState incrementTurnCount() {
            return new SessionState(sessionId, context, createdAt, expiresAt, turnCount + 1);
        }
    }
}
