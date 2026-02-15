package org.yawlfoundation.yawl.integration.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpError;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * JSON-RPC Protocol Handler for MCP messages.
 *
 * Handles JSON-RPC 2.0 message parsing, routing, and error formatting
 * for the YAWL MCP server implementation.
 *
 * Features:
 * - Request/response correlation via JSON-RPC id
 * - Method routing to appropriate handlers
 * - Standard MCP error formatting
 * - Notification broadcasting support
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpProtocolHandler {

    private static final Logger LOGGER = Logger.getLogger(McpProtocolHandler.class.getName());

    private final ObjectMapper mapper;
    private final Map<String, MethodHandler> handlers;

    /**
     * Functional interface for method handlers.
     */
    @FunctionalInterface
    public interface MethodHandler {
        JsonNode handle(JsonNode params) throws McpException;
    }

    /**
     * Exception for MCP protocol errors.
     */
    public static class McpException extends Exception {
        private final int code;

        public McpException(int code, String message) {
            super(message);
            this.code = code;
        }

        public McpException(int code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    /**
     * Creates a new protocol handler with default ObjectMapper.
     */
    public McpProtocolHandler() {
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
        this.handlers = new HashMap<>();
    }

    /**
     * Creates a new protocol handler with custom ObjectMapper.
     *
     * @param mapper the ObjectMapper to use
     */
    public McpProtocolHandler(ObjectMapper mapper) {
        this.mapper = mapper;
        this.handlers = new HashMap<>();
    }

    /**
     * Registers a handler for a specific method.
     *
     * @param method the method name
     * @param handler the handler function
     */
    public void registerHandler(String method, MethodHandler handler) {
        handlers.put(method, handler);
        LOGGER.fine("Registered handler for method: " + method);
    }

    /**
     * Handles an incoming JSON-RPC request.
     *
     * @param jsonRequest the raw JSON request string
     * @return the JSON response string
     */
    public String handleRequest(String jsonRequest) {
        try {
            JsonNode request = mapper.readTree(jsonRequest);
            return handleRequest(request);
        } catch (IOException e) {
            LOGGER.warning("Failed to parse request: " + e.getMessage());
            return formatErrorResponse(null, McpError.PARSE_ERROR, "Parse error", null);
        }
    }

    /**
     * Handles a parsed JSON-RPC request.
     *
     * @param request the parsed request node
     * @return the JSON response string
     */
    public String handleRequest(JsonNode request) {
        JsonNode idNode = request.get("id");
        Object id = idNode != null ? extractId(idNode) : null;

        // Validate JSON-RPC version
        JsonNode jsonrpc = request.get("jsonrpc");
        if (jsonrpc == null || !"2.0".equals(jsonrpc.asText())) {
            return formatErrorResponse(id, McpError.INVALID_REQUEST, "Invalid JSON-RPC version", null);
        }

        // Get method
        JsonNode methodNode = request.get("method");
        if (methodNode == null || !methodNode.isTextual()) {
            return formatErrorResponse(id, McpError.INVALID_REQUEST, "Missing or invalid method", null);
        }

        String method = methodNode.asText();
        JsonNode params = request.get("params");

        // Handle notification (no id)
        if (id == null) {
            handleNotification(method, params);
            return null; // Notifications don't get responses
        }

        // Route to handler
        MethodHandler handler = handlers.get(method);
        if (handler == null) {
            return formatErrorResponse(id, McpError.METHOD_NOT_FOUND, "Method not found: " + method, null);
        }

        try {
            JsonNode result = handler.handle(params != null ? params : mapper.getNodeFactory().objectNode());
            return formatSuccessResponse(id, result);
        } catch (McpException e) {
            LOGGER.warning("MCP error handling method " + method + ": " + e.getMessage());
            return formatErrorResponse(id, e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.severe("Internal error handling method " + method + ": " + e.getMessage());
            return formatErrorResponse(id, McpError.INTERNAL_ERROR, "Internal error", null);
        }
    }

    /**
     * Handles a notification (request without id).
     *
     * @param method the method name
     * @param params the parameters
     */
    private void handleNotification(String method, JsonNode params) {
        MethodHandler handler = handlers.get(method);
        if (handler != null) {
            try {
                handler.handle(params != null ? params : mapper.getNodeFactory().objectNode());
            } catch (McpException e) {
                LOGGER.warning("Error handling notification " + method + ": " + e.getMessage());
            }
        } else {
            LOGGER.fine("No handler for notification: " + method);
        }
    }

    /**
     * Formats a success response.
     *
     * @param id the request id
     * @param result the result
     * @return the JSON response string
     */
    public String formatSuccessResponse(Object id, JsonNode result) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", result);
            return mapper.writeValueAsString(response);
        } catch (IOException e) {
            LOGGER.severe("Failed to format success response: " + e.getMessage());
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    /**
     * Formats an error response.
     *
     * @param id the request id
     * @param code the error code
     * @param message the error message
     * @param data additional error data
     * @return the JSON response string
     */
    public String formatErrorResponse(Object id, int code, String message, Object data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            Map<String, Object> error = new HashMap<>();
            error.put("code", code);
            error.put("message", message);
            if (data != null) {
                error.put("data", data);
            }
            response.put("error", error);

            return mapper.writeValueAsString(response);
        } catch (IOException e) {
            LOGGER.severe("Failed to format error response: " + e.getMessage());
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    /**
     * Creates an McpError instance.
     *
     * @param code the error code
     * @param message the error message
     * @param data additional error data
     * @return a new McpError instance
     */
    public McpError createError(int code, String message, Object data) {
        return new McpError(code, message, data);
    }

    /**
     * Extracts the id value from a JSON node.
     *
     * @param idNode the id node
     * @return the extracted id value
     */
    private Object extractId(JsonNode idNode) {
        if (idNode.isTextual()) {
            return idNode.asText();
        } else if (idNode.isNumber()) {
            return idNode.asLong();
        } else if (idNode.isNull()) {
            return null;
        }
        return idNode.toString();
    }

    /**
     * Gets the ObjectMapper used for JSON processing.
     *
     * @return the ObjectMapper instance
     */
    public ObjectMapper getMapper() {
        return mapper;
    }
}
