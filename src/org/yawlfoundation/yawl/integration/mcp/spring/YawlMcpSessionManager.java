package org.yawlfoundation.yawl.integration.mcp.spring;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Spring-managed YAWL session lifecycle manager.
 *
 * <p>Manages the YAWL engine session handle with automatic connection, reconnection,
 * and disconnection. Thread-safe for concurrent access.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Automatic session establishment on startup</li>
 *   <li>Thread-safe session handle access</li>
 *   <li>Automatic reconnection with exponential backoff</li>
 *   <li>Clean shutdown via Spring lifecycle hooks</li>
 *   <li>Session validity checking</li>
 * </ul>
 *
 * <p>This bean is automatically configured by {@link YawlMcpConfiguration}
 * and can be injected into tools and resources that need session handles.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpSessionManager {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpSessionManager.class.getName());

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpProperties properties;
    private final Lock sessionLock = new ReentrantLock();

    private volatile String sessionHandle;
    private volatile boolean connected = false;

    /**
     * Construct a session manager with YAWL client and configuration.
     *
     * @param interfaceBClient YAWL InterfaceB client for session operations
     * @param properties MCP configuration properties
     */
    public YawlMcpSessionManager(InterfaceB_EnvironmentBasedClient interfaceBClient,
                                 YawlMcpProperties properties) {
        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (properties == null) {
            throw new IllegalArgumentException("properties is required");
        }
        this.interfaceBClient = interfaceBClient;
        this.properties = properties;
    }

    /**
     * Connect to the YAWL engine and establish a session.
     * Called automatically by Spring on bean initialization.
     *
     * @throws IOException if connection fails after all retry attempts
     */
    public void connect() throws IOException {
        sessionLock.lock();
        try {
            if (connected) {
                LOGGER.info("Already connected to YAWL engine");
                return;
            }

            int attempts = properties.getConnection().getRetryAttempts();
            long delayMs = properties.getConnection().getRetryDelayMs();
            IOException lastException = null;

            for (int i = 0; i < attempts; i++) {
                try {
                    LOGGER.info("Connecting to YAWL engine: " + properties.getEngineUrl() +
                                " (attempt " + (i + 1) + "/" + attempts + ")");

                    sessionHandle = interfaceBClient.connect(
                        properties.getUsername(),
                        properties.getPassword()
                    );

                    if (sessionHandle == null || sessionHandle.contains("<failure>")) {
                        throw new IOException(
                            "Failed to connect to YAWL engine. " +
                            "Verify the engine is running and credentials are correct. " +
                            "Response: " + sessionHandle);
                    }

                    connected = true;
                    LOGGER.info("Successfully connected to YAWL engine (session established)");
                    return;

                } catch (IOException e) {
                    lastException = e;
                    LOGGER.warning("Connection attempt " + (i + 1) + " failed: " + e.getMessage());

                    if (i < attempts - 1) {
                        try {
                            Thread.sleep(delayMs * (i + 1)); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Connection interrupted", ie);
                        }
                    }
                }
            }

            throw new IOException(
                "Failed to connect to YAWL engine after " + attempts + " attempts. " +
                "Ensure the YAWL engine is running at " + properties.getEngineUrl() +
                " and credentials are valid.", lastException);

        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Disconnect from the YAWL engine and invalidate the session.
     * Called automatically by Spring on bean destruction.
     */
    public void disconnect() {
        sessionLock.lock();
        try {
            if (!connected || sessionHandle == null) {
                return;
            }

            try {
                LOGGER.info("Disconnecting from YAWL engine");
                interfaceBClient.disconnect(sessionHandle);
                LOGGER.info("Successfully disconnected from YAWL engine");
            } catch (IOException e) {
                LOGGER.warning("Error during disconnect: " + e.getMessage());
            } finally {
                sessionHandle = null;
                connected = false;
            }
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Get the current YAWL session handle.
     * Automatically reconnects if the session is invalid.
     *
     * @return session handle for YAWL API calls
     * @throws IllegalStateException if not connected and reconnection fails
     */
    public String getSessionHandle() {
        if (!connected || sessionHandle == null) {
            try {
                connect();
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Not connected to YAWL engine and reconnection failed: " + e.getMessage(), e);
            }
        }
        return sessionHandle;
    }

    /**
     * Check if currently connected to the YAWL engine.
     *
     * @return true if connected with valid session handle
     */
    public boolean isConnected() {
        return connected && sessionHandle != null;
    }

    /**
     * Reconnect to the YAWL engine.
     * Disconnects existing session and establishes a new one.
     *
     * @throws IOException if reconnection fails
     */
    public void reconnect() throws IOException {
        LOGGER.info("Reconnecting to YAWL engine");
        disconnect();
        connect();
    }

    /**
     * Check if the current session is valid by testing a simple API call.
     *
     * @return true if session is valid and engine is responsive
     */
    public boolean isSessionValid() {
        if (!connected || sessionHandle == null) {
            return false;
        }

        try {
            // Test session by calling a lightweight API
            String result = interfaceBClient.checkConnection(sessionHandle);
            return result != null && !result.contains("<failure>");
        } catch (Exception e) {
            LOGGER.warning("Session validation failed: " + e.getMessage());
            return false;
        }
    }
}
