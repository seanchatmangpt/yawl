package org.yawlfoundation.yawl.integration.mcp.autonomic;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

/**
 * Autonomic Session Manager — Self-healing session with auto-reconnect (80/20 Win).
 *
 * <p>Automatically detects session expiry and reconnects without human intervention.
 * Uses exponential backoff for reliability. Provides transparent session handle
 * via Supplier<String> that always returns valid session (reconnecting if needed).
 *
 * <p>Single method: get() returns active session or reconnects automatically.
 * Enables truly autonomous workflow execution without session management overhead.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class AutonomicSessionManager implements Supplier<String> {

    private final InterfaceB_EnvironmentBasedClient client;
    private final String username;
    private final String password;
    private final AtomicReference<String> currentSession = new AtomicReference<>();

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 500;
    private static final long MAX_BACKOFF_MS = 5000;

    public AutonomicSessionManager(
            InterfaceB_EnvironmentBasedClient client,
            String username,
            String password,
            String initialSession) {
        this.client = client;
        this.username = username;
        this.password = password;
        this.currentSession.set(initialSession);
    }

    /**
     * Get current session handle, reconnecting if necessary.
     * This is the primary interface for all MCP operations.
     *
     * @return valid YAWL session handle
     * @throws IOException if reconnection attempts fail
     */
    @Override
    public String get() {
        String session = currentSession.get();

        // Optimistic path: current session likely valid
        if (session != null && isSessionValid(session)) {
            return session;
        }

        // Pessimistic path: reconnect with exponential backoff
        return reconnectWithBackoff();
    }

    /**
     * Check if session is still valid without throwing exceptions.
     */
    private boolean isSessionValid(String session) {
        try {
            // Light-weight test: list specs
            client.getSpecificationList(session);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reconnect with exponential backoff for resilience.
     */
    private String reconnectWithBackoff() {
        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
            try {
                String newSession = client.connect(username, password);
                currentSession.set(newSession);
                return newSession;

            } catch (IOException e) {
                if (attempt == MAX_RECONNECT_ATTEMPTS) {
                    throw new RuntimeException(
                        "Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS +
                        " attempts: " + e.getMessage(), e);
                }

                // Exponential backoff: 500ms, 1s, 2s, 4s, 5s (max)
                long backoffMs = Math.min(
                    INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt - 1),
                    MAX_BACKOFF_MS);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Reconnection interrupted", ie);
                }
            }
        }

        throw new RuntimeException("Session reconnection failed");
    }

    /**
     * Explicitly reconnect (useful for diagnostic or maintenance).
     */
    public String reconnect() throws IOException {
        String newSession = client.connect(username, password);
        currentSession.set(newSession);
        return newSession;
    }

    /**
     * Get current session without attempting reconnect.
     */
    public String getCurrentSession() {
        return currentSession.get();
    }

    /**
     * Graceful disconnect and cleanup.
     */
    public void disconnect() {
        String session = currentSession.getAndSet(null);
        if (session != null) {
            try {
                client.disconnect(session);
            } catch (IOException e) {
                // Log but don't throw - cleanup operation
                System.err.println("Warning: failed to disconnect session: " + e.getMessage());
            }
        }
    }

    /**
     * Health check: is session currently valid?
     */
    public boolean isHealthy() {
        String session = currentSession.get();
        return session != null && isSessionValid(session);
    }

    /**
     * Diagnostic: how many reconnections occurred?
     * (Would be tracked in production with metrics)
     */
    public String getDiagnostics() {
        String session = currentSession.get();
        return String.format(
            "AutonomicSessionManager{session=%s, healthy=%s}",
            session != null ? session.substring(0, Math.min(10, session.length())) + "..." : "null",
            isHealthy());
    }
}
