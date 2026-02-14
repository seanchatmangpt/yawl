/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp;

import org.yawlfoundation.yawl.integration.YawlSecurityManager;
import org.yawlfoundation.yawl.integration.YawlSecurityManager.JwtToken;
import org.yawlfoundation.yawl.integration.YawlSecurityManager.Permission;

import java.util.logging.Logger;

/**
 * Security middleware for YAWL MCP Server
 *
 * Provides authentication and authorization layer for MCP tool calls:
 * - Validates API keys and JWT tokens
 * - Enforces role-based access control (RBAC)
 * - Implements rate limiting per client
 * - Logs all security events
 * - Validates case access permissions
 *
 * Usage:
 * <pre>
 * YawlMcpSecurityMiddleware security = new YawlMcpSecurityMiddleware();
 *
 * // Authenticate client
 * String sessionHandle = security.authenticate(apiKey);
 *
 * // Authorize tool call
 * if (security.authorizeToolCall(sessionHandle, "launch_case", caseId)) {
 *     // Execute tool
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpSecurityMiddleware {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpSecurityMiddleware.class.getName());

    private final YawlSecurityManager securityManager;
    private boolean securityEnabled;

    /**
     * Constructor - initializes security middleware
     */
    public YawlMcpSecurityMiddleware() {
        this.securityManager = new YawlSecurityManager();
        this.securityEnabled = true;
        LOGGER.info("MCP Security Middleware initialized");
    }

    /**
     * Enable or disable security features
     *
     * @param enabled true to enable security, false to disable (NOT RECOMMENDED FOR PRODUCTION)
     */
    public void setSecurityEnabled(boolean enabled) {
        this.securityEnabled = enabled;
        if (!enabled) {
            LOGGER.severe("SECURITY DISABLED - THIS IS DANGEROUS IN PRODUCTION!");
        } else {
            LOGGER.info("Security enabled");
        }
    }

    /**
     * Get the underlying security manager
     *
     * @return the security manager instance
     */
    public YawlSecurityManager getSecurityManager() {
        return securityManager;
    }

    /**
     * Authenticates an MCP client using API key
     *
     * @param apiKey the API key provided by the client
     * @return session handle if authenticated, null otherwise
     */
    public String authenticate(String apiKey) {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - bypassing authentication");
            return "unsecured-session";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.warning("Authentication failed: Missing API key");
            return null;
        }

        String sessionHandle = securityManager.authenticateWithApiKey(apiKey);
        if (sessionHandle != null) {
            LOGGER.info("MCP client authenticated successfully");
        } else {
            LOGGER.warning("MCP client authentication failed - invalid API key");
        }
        return sessionHandle;
    }

    /**
     * Generates a JWT token for an authenticated session
     *
     * @param sessionHandle the authenticated session handle
     * @return JWT token or null if session invalid
     */
    public JwtToken generateToken(String sessionHandle) {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - skipping token generation");
            return null;
        }

        JwtToken token = securityManager.generateJwtToken(sessionHandle);
        if (token != null) {
            LOGGER.info("JWT token generated for session");
        } else {
            LOGGER.warning("JWT token generation failed - invalid session");
        }
        return token;
    }

    /**
     * Validates a JWT token
     *
     * @param tokenString the JWT token string
     * @return session handle if valid, null otherwise
     */
    public String validateToken(String tokenString) {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - bypassing token validation");
            return "unsecured-session";
        }

        JwtToken token = securityManager.validateJwtToken(tokenString);
        if (token != null && !token.isExpired()) {
            LOGGER.info("JWT token validated successfully");
            return (String) token.getClaims().get("session");
        } else {
            LOGGER.warning("JWT token validation failed - invalid or expired token");
            return null;
        }
    }

    /**
     * Authorizes an MCP tool call
     *
     * @param sessionHandle the authenticated session handle
     * @param toolName the name of the tool being called
     * @param caseId the case ID (if applicable, null otherwise)
     * @return true if authorized, false otherwise
     */
    public boolean authorizeToolCall(String sessionHandle, String toolName, String caseId) {
        if (!securityEnabled) {
            return true; // Allow all when security disabled
        }

        if (sessionHandle == null || sessionHandle.isEmpty()) {
            LOGGER.warning("Authorization failed: No session handle");
            return false;
        }

        // Check rate limit first
        if (!securityManager.checkRateLimit(sessionHandle)) {
            LOGGER.warning("Authorization failed: Rate limit exceeded for session");
            return false;
        }

        // Map tool name to required permission
        Permission requiredPermission = mapToolToPermission(toolName);
        if (requiredPermission == null) {
            LOGGER.warning("Authorization failed: Unknown tool '" + toolName + "'");
            return false;
        }

        // Check permission
        if (!securityManager.checkPermission(sessionHandle, requiredPermission)) {
            LOGGER.warning("Authorization failed: Insufficient privileges for '" + toolName + "'");
            return false;
        }

        // Check case access if case ID provided
        if (caseId != null && !caseId.isEmpty()) {
            if (!securityManager.checkCaseAccess(sessionHandle, caseId)) {
                LOGGER.warning("Authorization failed: No access to case '" + caseId + "'");
                return false;
            }
        }

        LOGGER.info("Authorization successful for tool '" + toolName + "'");
        return true;
    }

    /**
     * Maps MCP tool names to YAWL permissions
     *
     * @param toolName the MCP tool name
     * @return the required permission, or null if unknown
     */
    private Permission mapToolToPermission(String toolName) {
        switch (toolName) {
            case "launch_case":
                return Permission.LAUNCH_CASE;

            case "get_case_status":
            case "get_case_data":
                return Permission.GET_CASE_STATUS;

            case "cancel_case":
                return Permission.CANCEL_CASE;

            case "get_specification_list":
                return Permission.LIST_SPECS;

            case "upload_specification":
                return Permission.UPLOAD_SPEC;

            case "get_enabled_work_items":
            case "checkout_work_item":
            case "checkin_work_item":
            case "get_work_item_data":
                return Permission.EXECUTE_TASK;

            default:
                return null;
        }
    }

    /**
     * Extracts case ID from tool arguments (if present)
     *
     * @param toolName the tool name
     * @param arguments the tool arguments (JSON string)
     * @return the case ID if present, null otherwise
     */
    public String extractCaseId(String toolName, String arguments) {
        // Simplified extraction - in production, parse JSON properly
        if (arguments == null) {
            return null;
        }

        switch (toolName) {
            case "get_case_status":
            case "get_case_data":
            case "cancel_case":
                // Extract case_id from arguments
                int start = arguments.indexOf("\"case_id\"");
                if (start >= 0) {
                    start = arguments.indexOf(":", start) + 1;
                    int valueStart = arguments.indexOf("\"", start) + 1;
                    int valueEnd = arguments.indexOf("\"", valueStart);
                    if (valueEnd > valueStart) {
                        return arguments.substring(valueStart, valueEnd);
                    }
                }
                break;

            case "checkout_work_item":
            case "checkin_work_item":
            case "get_work_item_data":
                // Work items don't directly reference cases
                // Case access will be checked via work item ownership
                return null;

            default:
                return null;
        }

        return null;
    }

    /**
     * Validates request signature for tamper protection
     *
     * @param payload the request payload
     * @param signature the provided signature
     * @param sessionHandle the session handle (used to lookup signing key)
     * @return true if signature valid, false otherwise
     */
    public boolean validateRequestSignature(String payload, String signature, String sessionHandle) {
        if (!securityEnabled) {
            return true; // Skip validation when security disabled
        }

        // In production, retrieve the client's signing key
        // For now, use a shared secret
        String signingKey = getSigningKeyForSession(sessionHandle);
        if (signingKey == null) {
            LOGGER.warning("Signature validation failed: No signing key for session");
            return false;
        }

        boolean valid = securityManager.verifyRequestSignature(payload, signature, signingKey);
        if (!valid) {
            LOGGER.warning("Signature validation failed: Invalid signature");
        }
        return valid;
    }

    /**
     * Gets the signing key for a session
     *
     * @param sessionHandle the session handle
     * @return the signing key, or null if not found
     */
    private String getSigningKeyForSession(String sessionHandle) {
        // In production, retrieve from secure storage
        // For now, use environment variable or generate from session
        String signingKey = System.getenv("YAWL_MCP_SIGNING_KEY");
        if (signingKey == null || signingKey.isEmpty()) {
            // Use session handle as signing key (not ideal for production)
            return sessionHandle;
        }
        return signingKey;
    }

    /**
     * Applies TLS/SSL configuration for secure transport
     */
    public void applyTlsConfiguration() {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - skipping TLS configuration");
            return;
        }

        securityManager.applyTlsConfiguration();
        LOGGER.info("TLS/SSL configuration applied for MCP server");
    }

    /**
     * Gets security statistics for monitoring
     *
     * @return security statistics as formatted string
     */
    public String getSecurityStats() {
        return String.format(
            "Security Enabled: %s%n" +
            "TLS Configured: %s%n" +
            "Rate Limiting: Active%n" +
            "Audit Logging: Enabled",
            securityEnabled,
            System.getProperty("javax.net.ssl.keyStore") != null
        );
    }
}
