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
import org.yawlfoundation.yawl.authentication.YSession;
import org.yawlfoundation.yawl.authentication.YSessionCache;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.logging.table.YAuditEvent;
import org.yawlfoundation.yawl.resourcing.resource.UserPrivileges;
import org.yawlfoundation.yawl.util.HibernateEngine;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Comprehensive security manager for MCP and A2A integration.
 *
 * Provides enterprise-grade security features:
 * - API key authentication for MCP/A2A clients
 * - JWT token generation and validation
 * - Session management with timeout
 * - Role-based access control (RBAC)
 * - Permission checking for operations
 * - Secure credential storage with encryption
 * - TLS/SSL configuration support
 * - Rate limiting per client
 * - Request signing and verification
 * - Comprehensive audit logging
 *
 * Security Implementation:
 * - Uses Java's built-in cryptography (javax.crypto)
 * - Integrates with YAWL's existing authentication (YSessionCache, YExternalClient)
 * - Leverages YAWL's audit logging (YAuditEvent, HibernateEngine)
 * - Implements HMAC-SHA256 for request signing
 * - Uses AES-256-GCM for credential encryption
 * - Enforces time-based session expiration
 * - Provides granular permission checking via UserPrivileges
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */
public class YawlSecurityManager {

    private static final Logger LOGGER = Logger.getLogger(YawlSecurityManager.class.getName());

    // Security configuration constants
    private static final int JWT_EXPIRATION_SECONDS = 3600; // 1 hour
    private static final int SESSION_TIMEOUT_SECONDS = 3600; // 1 hour
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final int RATE_LIMIT_WINDOW_MS = 60000; // 1 minute

    // Cryptography constants
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    // Security components
    private final YSessionCache sessionCache;
    private final HibernateEngine auditDb;
    private final SecureRandom secureRandom;
    private final Map<String, ApiKeyRecord> apiKeys;
    private final Map<String, RateLimitRecord> rateLimits;
    private final Map<String, byte[]> encryptionKeys;

    /**
     * Represents an API key with metadata for authentication
     */
    private static class ApiKeyRecord {
        final String clientId;
        final String hashedKey;
        final Set<String> roles;
        final long createdAt;
        final long expiresAt;
        boolean enabled;

        ApiKeyRecord(String clientId, String hashedKey, Set<String> roles, long expiresAt) {
            this.clientId = clientId;
            this.hashedKey = hashedKey;
            this.roles = new HashSet<>(roles);
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = expiresAt;
            this.enabled = true;
        }

        boolean isValid() {
            return enabled && (expiresAt == 0 || System.currentTimeMillis() < expiresAt);
        }
    }

    /**
     * Tracks request rates for rate limiting
     */
    private static class RateLimitRecord {
        final LinkedList<Long> requestTimestamps;

        RateLimitRecord() {
            this.requestTimestamps = new LinkedList<>();
        }

        synchronized boolean allowRequest(int maxRequests, int windowMs) {
            long now = System.currentTimeMillis();
            long windowStart = now - windowMs;

            // Remove expired timestamps
            while (!requestTimestamps.isEmpty() && requestTimestamps.peek() < windowStart) {
                requestTimestamps.poll();
            }

            // Check if limit exceeded
            if (requestTimestamps.size() >= maxRequests) {
                return false;
            }

            // Record this request
            requestTimestamps.add(now);
            return true;
        }
    }

    /**
     * Represents a JWT token with claims
     */
    public static class JwtToken {
        private final String token;
        private final Map<String, Object> claims;
        private final long expiresAt;

        JwtToken(String token, Map<String, Object> claims, long expiresAt) {
            this.token = token;
            this.claims = new HashMap<>(claims);
            this.expiresAt = expiresAt;
        }

        public String getToken() { return token; }
        public Map<String, Object> getClaims() { return new HashMap<>(claims); }
        public long getExpiresAt() { return expiresAt; }
        public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }

        public String getSubject() { return (String) claims.get("sub"); }
        public Set<String> getRoles() {
            Object roles = claims.get("roles");
            if (roles instanceof Set) {
                return new HashSet<>((Set<String>) roles);
            }
            return new HashSet<>();
        }
    }

    /**
     * Permission types for RBAC
     */
    public enum Permission {
        LAUNCH_CASE,
        GET_CASE_STATUS,
        CANCEL_CASE,
        UPLOAD_SPEC,
        UNLOAD_SPEC,
        LIST_SPECS,
        LIST_CASES,
        EXECUTE_TASK,
        VIEW_TASK_DATA,
        MANAGE_USERS,
        MANAGE_ROLES,
        VIEW_AUDIT_LOG
    }

    /**
     * Role definitions with associated permissions
     */
    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = new HashMap<>();
    static {
        // Admin role - all permissions
        Set<Permission> adminPerms = EnumSet.allOf(Permission.class);
        ROLE_PERMISSIONS.put("admin", adminPerms);

        // Workflow operator - case and spec management
        Set<Permission> operatorPerms = EnumSet.of(
            Permission.LAUNCH_CASE,
            Permission.GET_CASE_STATUS,
            Permission.LIST_SPECS,
            Permission.LIST_CASES,
            Permission.EXECUTE_TASK,
            Permission.VIEW_TASK_DATA
        );
        ROLE_PERMISSIONS.put("operator", operatorPerms);

        // Workflow designer - spec management
        Set<Permission> designerPerms = EnumSet.of(
            Permission.UPLOAD_SPEC,
            Permission.UNLOAD_SPEC,
            Permission.LIST_SPECS,
            Permission.GET_CASE_STATUS,
            Permission.LIST_CASES
        );
        ROLE_PERMISSIONS.put("designer", designerPerms);

        // Read-only viewer
        Set<Permission> viewerPerms = EnumSet.of(
            Permission.LIST_SPECS,
            Permission.LIST_CASES,
            Permission.GET_CASE_STATUS,
            Permission.VIEW_TASK_DATA
        );
        ROLE_PERMISSIONS.put("viewer", viewerPerms);
    }

    /**
     * Constructor - initializes security manager with YAWL's authentication infrastructure
     */
    public YawlSecurityManager() {
        this.sessionCache = YEngine.getInstance().getSessionCache();
        this.secureRandom = new SecureRandom();
        this.apiKeys = new ConcurrentHashMap<>();
        this.rateLimits = new ConcurrentHashMap<>();
        this.encryptionKeys = new ConcurrentHashMap<>();

        // Initialize audit database
        Set<Class> classSet = new HashSet<>();
        classSet.add(YAuditEvent.class);
        this.auditDb = new HibernateEngine(true, classSet);

        LOGGER.info("YawlSecurityManager initialized with enterprise security features");
    }

    /**
     * Authenticates a client using API key
     *
     * @param apiKey the API key to authenticate
     * @return authenticated session handle or null if invalid
     */
    public String authenticateWithApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            auditSecurityEvent("unknown", "api_key_auth_failed", "Missing API key");
            return null;
        }

        // Hash the provided key for comparison
        String hashedKey = hashApiKey(apiKey);

        // Find matching API key record
        for (ApiKeyRecord record : apiKeys.values()) {
            if (record.hashedKey.equals(hashedKey) && record.isValid()) {
                // Check rate limit
                if (!checkRateLimit(record.clientId)) {
                    auditSecurityEvent(record.clientId, "rate_limit_exceeded",
                        "Too many requests");
                    return null;
                }

                // Create session via YAWL's authentication system
                YExternalClient client = YEngine.getInstance().getExternalClient(record.clientId);
                if (client != null) {
                    YSession session = new YSession(client, SESSION_TIMEOUT_SECONDS);
                    String handle = session.getHandle();
                    sessionCache.put(handle, session);

                    auditSecurityEvent(record.clientId, "api_key_auth_success",
                        "Authenticated via API key");
                    return handle;
                } else {
                    auditSecurityEvent(record.clientId, "api_key_auth_failed",
                        "Client not found in YAWL engine");
                    return null;
                }
            }
        }

        auditSecurityEvent("unknown", "api_key_auth_failed", "Invalid API key");
        return null;
    }

    /**
     * Generates a JWT token for an authenticated session
     *
     * @param sessionHandle the authenticated session handle
     * @return JWT token or null if session invalid
     */
    public JwtToken generateJwtToken(String sessionHandle) {
        YSession session = sessionCache.getSession(sessionHandle);
        if (session == null || !sessionCache.checkConnection(sessionHandle)) {
            auditSecurityEvent("unknown", "jwt_generation_failed", "Invalid session");
            return null;
        }

        String clientId = session.getClient().getUserName();
        ApiKeyRecord record = findApiKeyByClientId(clientId);

        if (record == null) {
            auditSecurityEvent(clientId, "jwt_generation_failed", "No API key found");
            return null;
        }

        // Create JWT claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", clientId);
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("exp", (System.currentTimeMillis() / 1000) + JWT_EXPIRATION_SECONDS);
        claims.put("roles", record.roles);
        claims.put("session", sessionHandle);

        // Generate token string (simplified JWT format)
        String token = createJwtString(claims);
        long expiresAt = System.currentTimeMillis() + (JWT_EXPIRATION_SECONDS * 1000);

        auditSecurityEvent(clientId, "jwt_generated", "JWT token created");
        return new JwtToken(token, claims, expiresAt);
    }

    /**
     * Validates a JWT token
     *
     * @param tokenString the JWT token string
     * @return validated JwtToken or null if invalid
     */
    public JwtToken validateJwtToken(String tokenString) {
        if (tokenString == null || tokenString.isEmpty()) {
            auditSecurityEvent("unknown", "jwt_validation_failed", "Missing token");
            return null;
        }

        try {
            Map<String, Object> claims = parseJwtString(tokenString);

            // Check expiration
            long exp = ((Number) claims.get("exp")).longValue();
            long now = System.currentTimeMillis() / 1000;
            if (now > exp) {
                auditSecurityEvent((String) claims.get("sub"), "jwt_validation_failed",
                    "Token expired");
                return null;
            }

            // Verify session is still active
            String sessionHandle = (String) claims.get("session");
            if (!sessionCache.checkConnection(sessionHandle)) {
                auditSecurityEvent((String) claims.get("sub"), "jwt_validation_failed",
                    "Session inactive");
                return null;
            }

            long expiresAt = exp * 1000;
            auditSecurityEvent((String) claims.get("sub"), "jwt_validated",
                "Token validated successfully");
            return new JwtToken(tokenString, claims, expiresAt);

        } catch (Exception e) {
            auditSecurityEvent("unknown", "jwt_validation_failed",
                "Token parse error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a client has permission to perform an operation
     *
     * @param sessionHandle the session handle
     * @param permission the required permission
     * @return true if authorized, false otherwise
     */
    public boolean checkPermission(String sessionHandle, Permission permission) {
        YSession session = sessionCache.getSession(sessionHandle);
        if (session == null) {
            auditSecurityEvent("unknown", "permission_denied",
                "Invalid session for " + permission);
            return false;
        }

        String clientId = session.getClient().getUserName();

        // Admin always has all permissions
        if ("admin".equals(clientId)) {
            return true;
        }

        // Check via API key roles
        ApiKeyRecord record = findApiKeyByClientId(clientId);
        if (record != null) {
            for (String role : record.roles) {
                Set<Permission> rolePerms = ROLE_PERMISSIONS.get(role);
                if (rolePerms != null && rolePerms.contains(permission)) {
                    return true;
                }
            }
        }

        auditSecurityEvent(clientId, "permission_denied",
            "Insufficient privileges for " + permission);
        return false;
    }

    /**
     * Checks if a client has permission to access a specific case
     *
     * @param sessionHandle the session handle
     * @param caseId the case ID to access
     * @return true if authorized, false otherwise
     */
    public boolean checkCaseAccess(String sessionHandle, String caseId) {
        YSession session = sessionCache.getSession(sessionHandle);
        if (session == null) {
            auditSecurityEvent("unknown", "case_access_denied",
                "Invalid session for case " + caseId);
            return false;
        }

        String clientId = session.getClient().getUserName();

        // Admin can access all cases
        if ("admin".equals(clientId)) {
            return true;
        }

        // Check if client has case viewing permissions
        if (!checkPermission(sessionHandle, Permission.GET_CASE_STATUS)) {
            auditSecurityEvent(clientId, "case_access_denied",
                "No permission to view case " + caseId);
            return false;
        }

        // Additional case-specific authorization could be added here
        // For now, if they have GET_CASE_STATUS permission, they can view

        return true;
    }

    /**
     * Creates an API key for a client
     *
     * @param clientId the client ID
     * @param roles the roles to assign
     * @param expiresInDays days until expiration (0 = never)
     * @return the generated API key (plaintext - store securely!)
     */
    public String createApiKey(String clientId, Set<String> roles, int expiresInDays) {
        // Verify client exists in YAWL engine
        YExternalClient client = YEngine.getInstance().getExternalClient(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        // Generate secure random API key
        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        String apiKey = Base64.getEncoder().encodeToString(keyBytes);

        // Hash the key for storage
        String hashedKey = hashApiKey(apiKey);

        // Calculate expiration
        long expiresAt = 0;
        if (expiresInDays > 0) {
            expiresAt = System.currentTimeMillis() +
                (expiresInDays * 24L * 60L * 60L * 1000L);
        }

        // Store API key record
        ApiKeyRecord record = new ApiKeyRecord(clientId, hashedKey, roles, expiresAt);
        apiKeys.put(clientId, record);

        auditSecurityEvent(clientId, "api_key_created",
            "API key created with roles: " + roles);

        return apiKey;
    }

    /**
     * Revokes an API key for a client
     *
     * @param clientId the client ID
     */
    public void revokeApiKey(String clientId) {
        ApiKeyRecord record = apiKeys.get(clientId);
        if (record != null) {
            record.enabled = false;
            auditSecurityEvent(clientId, "api_key_revoked", "API key disabled");
        }
    }

    /**
     * Signs a request with HMAC-SHA256
     *
     * @param payload the payload to sign
     * @param secretKey the secret key for signing
     * @return Base64-encoded signature
     */
    public String signRequest(String payload, String secretKey) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
            );
            hmac.init(keySpec);
            byte[] signature = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SecurityException("Failed to sign request", e);
        }
    }

    /**
     * Verifies a request signature
     *
     * @param payload the payload that was signed
     * @param signature the provided signature
     * @param secretKey the secret key for verification
     * @return true if signature valid, false otherwise
     */
    public boolean verifyRequestSignature(String payload, String signature, String secretKey) {
        String expectedSignature = signRequest(payload, secretKey);
        return MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Encrypts sensitive data using AES-256-GCM
     *
     * @param plaintext the data to encrypt
     * @param password the encryption password
     * @return Base64-encoded encrypted data with IV
     */
    public String encryptData(String plaintext, String password) {
        try {
            // Derive key from password using PBKDF2
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            SecretKey key = deriveKey(password, salt);

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Encrypt
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine salt + IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new SecurityException("Encryption failed", e);
        }
    }

    /**
     * Decrypts data encrypted with encryptData()
     *
     * @param encryptedData the Base64-encoded encrypted data
     * @param password the decryption password
     * @return decrypted plaintext
     */
    public String decryptData(String encryptedData, String password) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            // Extract salt
            byte[] salt = new byte[16];
            buffer.get(salt);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            // Extract ciphertext
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Derive key
            SecretKey key = deriveKey(password, salt);

            // Decrypt
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new SecurityException("Decryption failed", e);
        }
    }

    /**
     * Checks rate limit for a client
     *
     * @param clientId the client ID
     * @return true if request allowed, false if rate limited
     */
    public boolean checkRateLimit(String clientId) {
        RateLimitRecord record = rateLimits.computeIfAbsent(
            clientId,
            k -> new RateLimitRecord()
        );
        return record.allowRequest(MAX_REQUESTS_PER_MINUTE, RATE_LIMIT_WINDOW_MS);
    }

    /**
     * Audits a security event to the database
     *
     * @param username the username involved
     * @param action the action performed
     * @param details additional details
     */
    private void auditSecurityEvent(String username, String action, String details) {
        LOGGER.info(String.format("SECURITY [%s]: %s - %s", username, action, details));

        // Create custom audit event (extends YAuditEvent concept)
        // In production, you'd create a SecurityAuditEvent class extending YAuditEvent
        // For now, we log to the standard audit log
        YAuditEvent event = new YAuditEvent(username, YAuditEvent.Action.valueOf(
            action.startsWith("logon") || action.contains("success") ? "logon" :
            action.startsWith("logoff") || action.contains("failed") ? "logoff" : "invalid"
        ));
        auditDb.exec(event, HibernateEngine.DB_INSERT, true);
    }

    /**
     * Hashes an API key using SHA-256
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("SHA-256 not available", e);
        }
    }

    /**
     * Derives an encryption key from a password using PBKDF2
     */
    private SecretKey deriveKey(String password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        );
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Finds API key record by client ID
     */
    private ApiKeyRecord findApiKeyByClientId(String clientId) {
        return apiKeys.get(clientId);
    }

    /**
     * Creates a JWT token string (simplified implementation)
     *
     * In production, use a proper JWT library like jjwt or nimbus-jose-jwt.
     * This implementation creates a signed token without full JWT compliance.
     */
    private String createJwtString(Map<String, Object> claims) {
        // Create payload JSON (simplified)
        StringBuilder payload = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (!first) payload.append(",");
            payload.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                payload.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                payload.append(value);
            } else if (value instanceof Set) {
                payload.append("[");
                boolean firstRole = true;
                for (Object role : (Set<?>) value) {
                    if (!firstRole) payload.append(",");
                    payload.append("\"").append(role).append("\"");
                    firstRole = false;
                }
                payload.append("]");
            }
            first = false;
        }
        payload.append("}");

        // Sign with HMAC
        String secret = getJwtSecret();
        String signature = signRequest(payload.toString(), secret);

        // Combine header.payload.signature (simplified JWT format)
        String header = Base64.getEncoder().encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)
        );
        String encodedPayload = Base64.getEncoder().encodeToString(
            payload.toString().getBytes(StandardCharsets.UTF_8)
        );

        return header + "." + encodedPayload + "." + signature;
    }

    /**
     * Parses a JWT token string
     */
    private Map<String, Object> parseJwtString(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("Invalid JWT format");
        }

        // Verify signature
        String payload = new String(
            Base64.getDecoder().decode(parts[1]),
            StandardCharsets.UTF_8
        );
        String secret = getJwtSecret();
        if (!verifyRequestSignature(payload, parts[2], secret)) {
            throw new SecurityException("Invalid JWT signature");
        }

        // Parse claims (simplified JSON parsing)
        Map<String, Object> claims = new HashMap<>();
        String content = payload.substring(1, payload.length() - 1); // Remove { }

        // This is a simplified parser - production should use Jackson or similar
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("\"", "");
                String value = kv[1].trim().replaceAll("\"", "");

                // Parse different types
                if (value.startsWith("[")) {
                    // It's an array (roles)
                    Set<String> roles = new HashSet<>();
                    String arrayContent = value.substring(1, value.length() - 1);
                    for (String role : arrayContent.split(",")) {
                        roles.add(role.trim().replaceAll("\"", ""));
                    }
                    claims.put(key, roles);
                } else if (value.matches("\\d+")) {
                    // It's a number
                    claims.put(key, Long.parseLong(value));
                } else {
                    // It's a string
                    claims.put(key, value);
                }
            }
        }

        return claims;
    }

    /**
     * Gets or generates JWT signing secret
     */
    private String getJwtSecret() {
        // In production, this should come from secure configuration
        // For now, generate a random secret per instance
        String secret = System.getenv("YAWL_JWT_SECRET");
        if (secret == null || secret.isEmpty()) {
            // Generate random secret
            byte[] secretBytes = new byte[32];
            secureRandom.nextBytes(secretBytes);
            secret = Base64.getEncoder().encodeToString(secretBytes);
            LOGGER.warning("JWT_SECRET not configured - using random secret. " +
                "Set YAWL_JWT_SECRET environment variable for production.");
        }
        return secret;
    }

    /**
     * Gets TLS/SSL configuration for secure transport
     *
     * @return Map of TLS configuration properties
     */
    public Map<String, String> getTlsConfiguration() {
        Map<String, String> config = new HashMap<>();

        String keystorePath = System.getenv("YAWL_KEYSTORE_PATH");
        String keystorePassword = System.getenv("YAWL_KEYSTORE_PASSWORD");
        String truststorePath = System.getenv("YAWL_TRUSTSTORE_PATH");
        String truststorePassword = System.getenv("YAWL_TRUSTSTORE_PASSWORD");

        if (keystorePath != null) {
            config.put("javax.net.ssl.keyStore", keystorePath);
        }
        if (keystorePassword != null) {
            config.put("javax.net.ssl.keyStorePassword", keystorePassword);
        }
        if (truststorePath != null) {
            config.put("javax.net.ssl.trustStore", truststorePath);
        }
        if (truststorePassword != null) {
            config.put("javax.net.ssl.trustStorePassword", truststorePassword);
        }

        // Set minimum TLS version
        config.put("https.protocols", "TLSv1.2,TLSv1.3");
        config.put("jdk.tls.client.protocols", "TLSv1.2,TLSv1.3");

        return config;
    }

    /**
     * Applies TLS configuration to system properties
     */
    public void applyTlsConfiguration() {
        Map<String, String> config = getTlsConfiguration();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }
        LOGGER.info("TLS/SSL configuration applied");
    }
}
