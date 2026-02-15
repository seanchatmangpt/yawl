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

package org.yawlfoundation.yawl.integration;

import org.yawlfoundation.yawl.authentication.YExternalClient;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.integration.YawlSecurityManager.JwtToken;
import org.yawlfoundation.yawl.integration.YawlSecurityManager.Permission;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Usage examples for YAWL Security Manager
 *
 * Demonstrates common security patterns for MCP and A2A integration.
 * This class is for documentation purposes only.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SecurityUsageExample {

    /**
     * Example 1: Setting up a new client with API key
     */
    public void setupNewClient() {
        // Initialize security manager
        YawlSecurityManager security = new YawlSecurityManager();

        // Create external client in YAWL engine
        String clientId = "workflow-agent-001";
        String hashedPassword = hashPassword("initial-password");
        YExternalClient client = new YExternalClient(
            clientId,
            hashedPassword,
            "External workflow automation agent"
        );

        // Add client to YAWL engine
        YEngine.getInstance().addExternalClient(client);

        // Create API key with operator role
        Set<String> roles = new HashSet<>(Arrays.asList("operator"));
        String apiKey = security.createApiKey(clientId, roles, 365); // Valid for 1 year

        // Securely provide API key to client
        System.out.println("API Key for " + clientId + ": " + apiKey);
        System.out.println("Store this key securely - it cannot be retrieved later!");
    }

    /**
     * Example 2: Authenticating a client
     */
    public void authenticateClient(String apiKey) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Authenticate using API key
        String sessionHandle = security.authenticateWithApiKey(apiKey);

        if (sessionHandle != null) {
            System.out.println("Authentication successful!");
            System.out.println("Session: " + sessionHandle);

            // Optionally generate JWT token for subsequent requests
            JwtToken jwt = security.generateJwtToken(sessionHandle);
            if (jwt != null) {
                System.out.println("JWT Token: " + jwt.getToken());
                System.out.println("Expires at: " + jwt.getExpiresAt());
            }
        } else {
            System.out.println("Authentication failed - invalid API key");
        }
    }

    /**
     * Example 3: Checking permissions before operation
     */
    public void checkPermissionExample(String sessionHandle) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Check if client can launch cases
        if (security.checkPermission(sessionHandle, Permission.LAUNCH_CASE)) {
            System.out.println("Client authorized to launch cases");
            // Proceed with launching case
            launchCase(sessionHandle);
        } else {
            System.out.println("Client not authorized to launch cases");
        }

        // Check if client can upload specifications
        if (security.checkPermission(sessionHandle, Permission.UPLOAD_SPEC)) {
            System.out.println("Client authorized to upload specs");
            // Proceed with spec upload
        } else {
            System.out.println("Client not authorized to upload specs");
        }
    }

    /**
     * Example 4: Validating JWT token
     */
    public void validateJwtExample(String tokenString) {
        YawlSecurityManager security = new YawlSecurityManager();

        JwtToken token = security.validateJwtToken(tokenString);

        if (token != null && !token.isExpired()) {
            System.out.println("Token valid!");
            System.out.println("Subject: " + token.getSubject());
            System.out.println("Roles: " + token.getRoles());
            System.out.println("Session: " + token.getClaims().get("session"));

            // Use the session from token
            String sessionHandle = (String) token.getClaims().get("session");
            // Proceed with authorized operations
        } else {
            System.out.println("Token invalid or expired");
        }
    }

    /**
     * Example 5: Checking case access
     */
    public void checkCaseAccessExample(String sessionHandle, String caseId) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Check if client has access to specific case
        if (security.checkCaseAccess(sessionHandle, caseId)) {
            System.out.println("Client has access to case: " + caseId);
            // Retrieve case data
            getCaseData(sessionHandle, caseId);
        } else {
            System.out.println("Client does not have access to case: " + caseId);
        }
    }

    /**
     * Example 6: Signing and verifying requests
     */
    public void requestSigningExample(String sessionHandle) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Client signs request
        String payload = "{\"operation\":\"launch_case\",\"spec_id\":\"OrderFulfillment\"}";
        String secretKey = "shared-secret-key";
        String signature = security.signRequest(payload, secretKey);

        System.out.println("Request signature: " + signature);

        // Server verifies signature
        boolean valid = security.verifyRequestSignature(payload, signature, secretKey);
        if (valid) {
            System.out.println("Signature valid - processing request");
            // Process the request
        } else {
            System.out.println("Signature invalid - rejecting request");
        }
    }

    /**
     * Example 7: Encrypting sensitive data
     */
    public void encryptionExample() {
        YawlSecurityManager security = new YawlSecurityManager();

        // Encrypt sensitive data
        String plaintext = "sensitive-workflow-data";
        String password = "encryption-password";
        String encrypted = security.encryptData(plaintext, password);

        System.out.println("Encrypted: " + encrypted);

        // Decrypt on the other end
        String decrypted = security.decryptData(encrypted, password);
        System.out.println("Decrypted: " + decrypted);

        // Verify it matches
        if (plaintext.equals(decrypted)) {
            System.out.println("Encryption/decryption successful!");
        }
    }

    /**
     * Example 8: Rate limiting check
     */
    public void rateLimitingExample(String clientId) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Check if client has exceeded rate limit
        if (security.checkRateLimit(clientId)) {
            System.out.println("Request allowed - within rate limit");
            // Process request
        } else {
            System.out.println("Request denied - rate limit exceeded");
            // Return 429 Too Many Requests
        }
    }

    /**
     * Example 9: Revoking an API key
     */
    public void revokeApiKeyExample(String clientId) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Revoke API key for security reasons
        security.revokeApiKey(clientId);
        System.out.println("API key revoked for client: " + clientId);

        // Generate new API key
        Set<String> roles = new HashSet<>(Arrays.asList("operator"));
        String newApiKey = security.createApiKey(clientId, roles, 365);
        System.out.println("New API key: " + newApiKey);
    }

    /**
     * Example 10: TLS configuration
     */
    public void tlsConfigurationExample() {
        YawlSecurityManager security = new YawlSecurityManager();

        // Apply TLS configuration from environment
        security.applyTlsConfiguration();
        System.out.println("TLS/SSL configuration applied");

        // Check configuration
        System.out.println("Keystore: " + System.getProperty("javax.net.ssl.keyStore"));
        System.out.println("Protocols: " + System.getProperty("https.protocols"));
    }

    /**
     * Example 11: Complete authentication flow for MCP client
     */
    public void mcpClientFlowExample(String apiKey) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Step 1: Authenticate with API key
        System.out.println("Step 1: Authenticating...");
        String sessionHandle = security.authenticateWithApiKey(apiKey);
        if (sessionHandle == null) {
            System.out.println("Authentication failed!");
            return;
        }
        System.out.println("Authenticated: " + sessionHandle);

        // Step 2: Generate JWT token
        System.out.println("\nStep 2: Generating JWT...");
        JwtToken jwt = security.generateJwtToken(sessionHandle);
        if (jwt == null) {
            System.out.println("JWT generation failed!");
            return;
        }
        System.out.println("JWT: " + jwt.getToken());

        // Step 3: Check rate limit
        System.out.println("\nStep 3: Checking rate limit...");
        if (!security.checkRateLimit(sessionHandle)) {
            System.out.println("Rate limit exceeded!");
            return;
        }
        System.out.println("Within rate limit");

        // Step 4: Check permission to launch case
        System.out.println("\nStep 4: Checking permissions...");
        if (!security.checkPermission(sessionHandle, Permission.LAUNCH_CASE)) {
            System.out.println("Insufficient permissions!");
            return;
        }
        System.out.println("Authorized to launch case");

        // Step 5: Execute operation
        System.out.println("\nStep 5: Executing operation...");
        System.out.println("Launching workflow case...");
        // Actual launch happens here

        System.out.println("\nComplete MCP flow successful!");
    }

    /**
     * Example 12: Complete authentication flow for A2A agent
     */
    public void a2aAgentFlowExample(String apiKey, String agentId) {
        YawlSecurityManager security = new YawlSecurityManager();

        // Step 1: Authenticate agent
        System.out.println("Step 1: Authenticating agent " + agentId);
        String sessionHandle = security.authenticateWithApiKey(apiKey);
        if (sessionHandle == null) {
            System.out.println("Agent authentication failed!");
            return;
        }

        // Step 2: Prepare signed request
        System.out.println("\nStep 2: Preparing signed request...");
        String payload = "{\"operation\":\"workflow.launch\",\"agent_id\":\"" + agentId + "\"}";
        String secretKey = "agent-signing-key";
        String signature = security.signRequest(payload, secretKey);
        System.out.println("Request signed: " + signature);

        // Step 3: Verify signature (server-side)
        System.out.println("\nStep 3: Verifying signature...");
        if (!security.verifyRequestSignature(payload, signature, secretKey)) {
            System.out.println("Invalid signature!");
            return;
        }
        System.out.println("Signature verified");

        // Step 4: Check authorization
        System.out.println("\nStep 4: Checking authorization...");
        if (!security.checkPermission(sessionHandle, Permission.LAUNCH_CASE)) {
            System.out.println("Operation not authorized!");
            return;
        }
        System.out.println("Operation authorized");

        // Step 5: Execute and encrypt response
        System.out.println("\nStep 5: Executing and encrypting response...");
        String responseData = "case-123-launched-successfully";
        String encryptedResponse = security.encryptData(responseData, secretKey);
        System.out.println("Encrypted response: " + encryptedResponse.substring(0, 50) + "...");

        System.out.println("\nComplete A2A flow successful!");
    }

    // Helper methods (stubs for example purposes)
    private String hashPassword(String password) {
        // In production, use proper password hashing (BCrypt, Argon2, etc.)
        return "hashed-" + password;
    }

    private void launchCase(String sessionHandle) {
        System.out.println("Launching case with session: " + sessionHandle);
    }

    private void getCaseData(String sessionHandle, String caseId) {
        System.out.println("Retrieving data for case: " + caseId);
    }

    /**
     * Main method - runs all examples
     */
    public static void main(String[] args) {
        SecurityUsageExample examples = new SecurityUsageExample();

        System.out.println("=== YAWL Security Manager Usage Examples ===\n");

        // Example API key for demonstration
        String exampleApiKey = "demo-api-key-123456789";

        System.out.println("Example 11: Complete MCP Client Flow");
        System.out.println("=====================================");
        examples.mcpClientFlowExample(exampleApiKey);

        System.out.println("\n\nExample 12: Complete A2A Agent Flow");
        System.out.println("====================================");
        examples.a2aAgentFlowExample(exampleApiKey, "agent-001");
    }
}
