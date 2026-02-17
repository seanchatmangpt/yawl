package org.yawlfoundation.yawl.integration.mcp.stub;

/**
 * MCP Synchronous Server.
 *
 * <p>This is a minimal stub interface for the MCP SDK's sync server.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with official MCP SDK (io.modelcontextprotocol.sdk:mcp-core) when available.
 *             See https://github.com/modelcontextprotocol/java-sdk
 */
@Deprecated
public interface McpSyncServer extends AutoCloseable {

    /**
     * Get the server name.
     *
     * @return server name
     */
    String getServerName();

    /**
     * Get the server version.
     *
     * @return server version
     */
    String getServerVersion();

    /**
     * Close the server gracefully.
     */
    void closeGracefully();

    /**
     * Send a log notification to the client.
     *
     * @param level log level
     * @param message log message
     */
    void sendLogNotification(String level, String message);

    /**
     * Send a logging notification to the client.
     *
     * @param notification the logging notification
     */
    default void loggingNotification(McpSchema.LoggingMessageNotification notification) {
        sendLogNotification(notification.getLevel().name(), notification.getData());
    }

    @Override
    default void close() {
        closeGracefully();
    }
}
