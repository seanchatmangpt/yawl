package org.yawlfoundation.yawl.integration.mcp.sdk;

/**
 * MCP Synchronous Server lifecycle interface.
 *
 * <p>Represents a running MCP server that processes client requests synchronously.
 * Instances are created via {@link McpServer#sync(Object)} builder chain and started
 * by calling {@link McpServer.SyncServerBuilder#build()}.</p>
 *
 * <p>This interface mirrors the {@code io.modelcontextprotocol.sdk.McpSyncServer} API
 * as defined in the MCP specification.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface McpSyncServer extends AutoCloseable {

    /**
     * Returns the server name as declared in {@link McpServer.SyncServerBuilder#serverInfo(String, String)}.
     *
     * @return server name string
     */
    String getServerName();

    /**
     * Returns the server version as declared in {@link McpServer.SyncServerBuilder#serverInfo(String, String)}.
     *
     * @return server version string
     */
    String getServerVersion();

    /**
     * Closes the server gracefully, allowing in-flight requests to complete.
     * After this call returns, the server will not accept new requests.
     */
    void closeGracefully();

    /**
     * Sends a log notification message to connected clients.
     *
     * @param level the log severity level string (e.g. "INFO", "WARNING", "ERROR")
     * @param message the log message text
     */
    void sendLogNotification(String level, String message);

    /**
     * Sends a structured MCP logging notification to connected clients.
     *
     * @param notification the notification object carrying level, logger name, and data
     */
    default void loggingNotification(McpSchema.LoggingMessageNotification notification) {
        sendLogNotification(notification.getLevel().name(), notification.getData());
    }

    /**
     * Closes the server via the {@link AutoCloseable} contract.
     * Delegates to {@link #closeGracefully()}.
     */
    @Override
    default void close() {
        closeGracefully();
    }
}
