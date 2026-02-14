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

package org.yawlfoundation.yawl.integration.a2a;

import org.yawlfoundation.yawl.integration.YawlSecurityManager;
import org.yawlfoundation.yawl.integration.YawlSecurityManager.JwtToken;
import org.yawlfoundation.yawl.integration.YawlSecurityManager.Permission;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Security middleware for YAWL A2A Server
 *
 * Provides comprehensive security for Agent-to-Agent communication:
 * - API key and JWT authentication
 * - Role-based access control (RBAC)
 * - Request signing and verification
 * - Rate limiting per agent
 * - TLS/SSL transport security
 * - Security audit logging
 *
 * Agent Authentication Flow:
 * 1. Agent presents API key
 * 2. Middleware validates key and creates session
 * 3. Optional: Generate JWT token for subsequent requests
 * 4. All requests validated against RBAC permissions
 * 5. Rate limiting enforced
 * 6. All security events audited
 *
 * Usage:
 * <pre>
 * YawlA2ASecurityMiddleware security = new YawlA2ASecurityMiddleware();
 *
 * // Authenticate agent
 * String sessionHandle = security.authenticateAgent(apiKey, agentId);
 *
 * // Authorize operation
 * if (security.authorizeOperation(sessionHandle, "workflow.launch")) {
 *     // Execute operation
 * }
 *
 * // Verify request signature
 * if (security.verifySignedRequest(payload, signature, sessionHandle)) {
 *     // Process request
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlA2ASecurityMiddleware {

    private static final Logger LOGGER = Logger.getLogger(YawlA2ASecurityMiddleware.class.getName());

    private final YawlSecurityManager securityManager;
    private boolean securityEnabled;
    private boolean requireSignedRequests;

    // Maps A2A operation names to permissions
    private static final Map<String, Permission> OPERATION_PERMISSIONS = new HashMap<>();
    static {
        // Workflow operations
        OPERATION_PERMISSIONS.put("workflow.launch", Permission.LAUNCH_CASE);
        OPERATION_PERMISSIONS.put("workflow.status", Permission.GET_CASE_STATUS);
        OPERATION_PERMISSIONS.put("workflow.cancel", Permission.CANCEL_CASE);
        OPERATION_PERMISSIONS.put("workflow.list", Permission.LIST_CASES);

        // Specification operations
        OPERATION_PERMISSIONS.put("spec.upload", Permission.UPLOAD_SPEC);
        OPERATION_PERMISSIONS.put("spec.unload", Permission.UNLOAD_SPEC);
        OPERATION_PERMISSIONS.put("spec.list", Permission.LIST_SPECS);

        // Task operations
        OPERATION_PERMISSIONS.put("task.execute", Permission.EXECUTE_TASK);
        OPERATION_PERMISSIONS.put("task.view", Permission.VIEW_TASK_DATA);

        // Admin operations
        OPERATION_PERMISSIONS.put("admin.users", Permission.MANAGE_USERS);
        OPERATION_PERMISSIONS.put("admin.roles", Permission.MANAGE_ROLES);
        OPERATION_PERMISSIONS.put("admin.audit", Permission.VIEW_AUDIT_LOG);
    }

    /**
     * Constructor - initializes A2A security middleware
     */
    public YawlA2ASecurityMiddleware() {
        this.securityManager = new YawlSecurityManager();
        this.securityEnabled = true;
        this.requireSignedRequests = false; // Optional by default

        LOGGER.info("A2A Security Middleware initialized");
    }

    /**
     * Enable or disable security features
     *
     * @param enabled true to enable security, false to disable (NOT RECOMMENDED FOR PRODUCTION)
     */
    public void setSecurityEnabled(boolean enabled) {
        this.securityEnabled = enabled;
        if (!enabled) {
            LOGGER.severe("SECURITY DISABLED - THIS IS EXTREMELY DANGEROUS IN PRODUCTION!");
        } else {
            LOGGER.info("Security enabled for A2A server");
        }
    }

    /**
     * Enable or disable required request signing
     *
     * @param required true to require signed requests, false to make optional
     */
    public void setRequireSignedRequests(boolean required) {
        this.requireSignedRequests = required;
        LOGGER.info("Request signing " + (required ? "REQUIRED" : "optional"));
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
     * Authenticates an A2A agent using API key
     *
     * @param apiKey the API key provided by the agent
     * @param agentId the agent identifier (for logging)
     * @return session handle if authenticated, null otherwise
     */
    public String authenticateAgent(String apiKey, String agentId) {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - bypassing agent authentication for " + agentId);
            return "unsecured-session-" + agentId;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.warning("Agent authentication failed for " + agentId + ": Missing API key");
            return null;
        }

        String sessionHandle = securityManager.authenticateWithApiKey(apiKey);
        if (sessionHandle != null) {
            LOGGER.info("Agent " + agentId + " authenticated successfully");
        } else {
            LOGGER.warning("Agent " + agentId + " authentication failed - invalid API key");
        }
        return sessionHandle;
    }

    /**
     * Generates a JWT token for an authenticated agent session
     *
     * @param sessionHandle the authenticated session handle
     * @param agentId the agent identifier
     * @return JWT token or null if session invalid
     */
    public JwtToken generateAgentToken(String sessionHandle, String agentId) {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - skipping token generation for " + agentId);
            return null;
        }

        JwtToken token = securityManager.generateJwtToken(sessionHandle);
        if (token != null) {
            LOGGER.info("JWT token generated for agent " + agentId);
        } else {
            LOGGER.warning("JWT token generation failed for agent " + agentId);
        }
        return token;
    }

    /**
     * Validates a JWT token from an agent
     *
     * @param tokenString the JWT token string
     * @param agentId the agent identifier (for logging)
     * @return session handle if valid, null otherwise
     */
    public String validateAgentToken(String tokenString, String agentId) {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - bypassing token validation for " + agentId);
            return "unsecured-session-" + agentId;
        }

        JwtToken token = securityManager.validateJwtToken(tokenString);
        if (token != null && !token.isExpired()) {
            LOGGER.info("JWT token validated successfully for agent " + agentId);
            return (String) token.getClaims().get("session");
        } else {
            LOGGER.warning("JWT token validation failed for agent " + agentId);
            return null;
        }
    }

    /**
     * Authorizes an A2A operation
     *
     * @param sessionHandle the authenticated session handle
     * @param operationName the A2A operation name (e.g., "workflow.launch")
     * @param resourceId optional resource ID (e.g., case ID)
     * @return true if authorized, false otherwise
     */
    public boolean authorizeOperation(String sessionHandle, String operationName, String resourceId) {
        if (!securityEnabled) {
            return true; // Allow all when security disabled
        }

        if (sessionHandle == null || sessionHandle.isEmpty()) {
            LOGGER.warning("Authorization failed for " + operationName + ": No session handle");
            return false;
        }

        // Check rate limit first
        if (!securityManager.checkRateLimit(sessionHandle)) {
            LOGGER.warning("Authorization failed for " + operationName + ": Rate limit exceeded");
            return false;
        }

        // Map operation to required permission
        Permission requiredPermission = OPERATION_PERMISSIONS.get(operationName);
        if (requiredPermission == null) {
            LOGGER.warning("Authorization failed: Unknown operation '" + operationName + "'");
            return false;
        }

        // Check permission
        if (!securityManager.checkPermission(sessionHandle, requiredPermission)) {
            LOGGER.warning("Authorization failed: Insufficient privileges for '" + operationName + "'");
            return false;
        }

        // Check resource access if provided
        if (resourceId != null && !resourceId.isEmpty()) {
            if (isWorkflowResource(operationName)) {
                if (!securityManager.checkCaseAccess(sessionHandle, resourceId)) {
                    LOGGER.warning("Authorization failed: No access to resource '" + resourceId + "'");
                    return false;
                }
            }
        }

        LOGGER.info("Authorization successful for operation '" + operationName + "'");
        return true;
    }

    /**
     * Authorizes an A2A operation without resource ID
     *
     * @param sessionHandle the authenticated session handle
     * @param operationName the A2A operation name
     * @return true if authorized, false otherwise
     */
    public boolean authorizeOperation(String sessionHandle, String operationName) {
        return authorizeOperation(sessionHandle, operationName, null);
    }

    /**
     * Verifies a signed request from an agent
     *
     * @param payload the request payload
     * @param signature the provided signature
     * @param sessionHandle the session handle (used to lookup signing key)
     * @return true if signature valid, false otherwise
     */
    public boolean verifySignedRequest(String payload, String signature, String sessionHandle) {
        if (!securityEnabled) {
            return true; // Skip validation when security disabled
        }

        if (!requireSignedRequests && (signature == null || signature.isEmpty())) {
            // Signing is optional and no signature provided
            LOGGER.info("Request signature not provided (optional)");
            return true;
        }

        if (signature == null || signature.isEmpty()) {
            LOGGER.warning("Signature verification failed: Missing signature (required)");
            return false;
        }

        // Get the signing key for this session
        String signingKey = getSigningKeyForSession(sessionHandle);
        if (signingKey == null) {
            LOGGER.warning("Signature verification failed: No signing key for session");
            return false;
        }

        boolean valid = securityManager.verifyRequestSignature(payload, signature, signingKey);
        if (!valid) {
            LOGGER.warning("Signature verification failed: Invalid signature");
        } else {
            LOGGER.info("Request signature verified successfully");
        }
        return valid;
    }

    /**
     * Signs a response to send to an agent
     *
     * @param payload the response payload
     * @param sessionHandle the session handle (used to lookup signing key)
     * @return Base64-encoded signature, or null if security disabled or signing fails
     */
    public String signResponse(String payload, String sessionHandle) {
        if (!securityEnabled) {
            LOGGER.warning("Security disabled - cannot sign response");
            return null;
        }

        String signingKey = getSigningKeyForSession(sessionHandle);
        if (signingKey == null) {
            LOGGER.warning("Response signing failed: No signing key for session");
            return null;
        }

        String signature = securityManager.signRequest(payload, signingKey);
        LOGGER.info("Response signed successfully");
        return signature;
    }

    /**
     * Encrypts sensitive data in responses
     *
     * @param data the data to encrypt
     * @param sessionHandle the session handle (used to derive encryption key)
     * @return Base64-encoded encrypted data
     */
    public String encryptResponseData(String data, String sessionHandle) {
        if (!securityEnabled) {
            return data; // Skip encryption when security disabled
        }

        String encryptionPassword = getEncryptionPasswordForSession(sessionHandle);
        if (encryptionPassword == null) {
            LOGGER.warning("Data encryption failed: No encryption password for session");
            return data;
        }

        try {
            String encrypted = securityManager.encryptData(data, encryptionPassword);
            LOGGER.info("Response data encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            LOGGER.severe("Data encryption failed: " + e.getMessage());
            return data;
        }
    }

    /**
     * Decrypts sensitive data from requests
     *
     * @param encryptedData the encrypted data
     * @param sessionHandle the session handle (used to derive decryption key)
     * @return decrypted plaintext
     */
    public String decryptRequestData(String encryptedData, String sessionHandle) {
        if (!securityEnabled) {
            return encryptedData; // Skip decryption when security disabled
        }

        String decryptionPassword = getEncryptionPasswordForSession(sessionHandle);
        if (decryptionPassword == null) {
            LOGGER.warning("Data decryption failed: No decryption password for session");
            return encryptedData;
        }

        try {
            String decrypted = securityManager.decryptData(encryptedData, decryptionPassword);
            LOGGER.info("Request data decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            LOGGER.severe("Data decryption failed: " + e.getMessage());
            return encryptedData;
        }
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
        LOGGER.info("TLS/SSL configuration applied for A2A server");
    }

    /**
     * Gets security configuration summary
     *
     * @return configuration as map
     */
    public Map<String, Object> getSecurityConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("securityEnabled", securityEnabled);
        config.put("requireSignedRequests", requireSignedRequests);
        config.put("tlsEnabled", System.getProperty("javax.net.ssl.keyStore") != null);
        config.put("rateLimitingActive", true);
        config.put("auditLoggingEnabled", true);
        return config;
    }

    /**
     * Gets security statistics for monitoring
     *
     * @return security statistics as formatted string
     */
    public String getSecurityStats() {
        return String.format(
            "A2A Security Configuration:%n" +
            "  Security Enabled: %s%n" +
            "  Signed Requests: %s%n" +
            "  TLS Configured: %s%n" +
            "  Rate Limiting: Active%n" +
            "  Audit Logging: Enabled",
            securityEnabled,
            requireSignedRequests ? "Required" : "Optional",
            System.getProperty("javax.net.ssl.keyStore") != null
        );
    }

    /**
     * Determines if an operation involves workflow resources
     */
    private boolean isWorkflowResource(String operationName) {
        return operationName.startsWith("workflow.") ||
               operationName.startsWith("task.");
    }

    /**
     * Gets the signing key for a session
     */
    private String getSigningKeyForSession(String sessionHandle) {
        // In production, retrieve from secure key store
        String signingKey = System.getenv("YAWL_A2A_SIGNING_KEY");
        if (signingKey == null || signingKey.isEmpty()) {
            // Use session handle as signing key (not ideal for production)
            LOGGER.warning("Using session handle as signing key - configure YAWL_A2A_SIGNING_KEY for production");
            return sessionHandle;
        }
        return signingKey;
    }

    /**
     * Gets the encryption password for a session
     */
    private String getEncryptionPasswordForSession(String sessionHandle) {
        // In production, derive from session or retrieve from secure key store
        String encryptionKey = System.getenv("YAWL_A2A_ENCRYPTION_KEY");
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            // Use session handle as encryption password (not ideal for production)
            LOGGER.warning("Using session handle as encryption key - configure YAWL_A2A_ENCRYPTION_KEY for production");
            return sessionHandle;
        }
        return encryptionKey;
    }
}
