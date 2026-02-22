/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationException;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A2A authentication tests covering all provider implementations.
 *
 * Chicago TDD: tests real authentication logic using real HttpServer
 * instances that produce real HttpExchange objects. No mocks or stubs.
 * The test starts a real HTTP server, sends HTTP requests with specific
 * headers, and captures the real HttpExchange to exercise the providers.
 *
 * Coverage targets:
 * - AuthenticatedPrincipal: construction, permissions, expiry, equality
 * - ApiKeyAuthenticationProvider: registration, auth success/failure, revocation
 * - JwtAuthenticationProvider: token issue/verify, expired tokens, scope extraction
 * - A2AAuthenticationException: message and scheme propagation
 * - CompositeAuthenticationProvider: chain construction, ordered evaluation
 * - canHandle() detection logic via real HTTP requests
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class A2AAuthenticationTest extends TestCase {

    // Minimum 32-char secret for JWT provider (HMAC-SHA256 requires 256 bits)
    private static final String JWT_SECRET =
        "test-secret-minimum-32-characters-long-for-hs256";

    // Master key for API key provider (min 16 chars)
    private static final String API_KEY_MASTER = "test-master-key-16chars";

    // Port for the real HTTP server used to capture live exchanges
    private static final int AUTH_TEST_PORT = 19870;

    private HttpServer captureServer;

    public A2AAuthenticationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        captureServer = null;
    }

    @Override
    protected void tearDown() throws Exception {
        if (captureServer != null) {
            captureServer.stop(0);
            captureServer = null;
        }
        super.tearDown();
    }

    /**
     * Start a real HttpServer that captures the first incoming HttpExchange.
     * The captured exchange carries real request headers as set by the HTTP client.
     */
    private HttpServer startCaptureServer(AtomicReference<HttpExchange> captured)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(AUTH_TEST_PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/auth-test", exchange -> {
            captured.set(exchange);
            byte[] resp = "OK".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        return server;
    }

    /**
     * Send a real HTTP GET to the capture server with the given header.
     */
    private void sendRealRequest(String headerName, String headerValue) throws IOException {
        URL url = new URL("http://localhost:" + AUTH_TEST_PORT + "/auth-test");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (headerName != null && headerValue != null) {
            conn.setRequestProperty(headerName, headerValue);
        }
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.getResponseCode(); // triggers the request
        conn.disconnect();
    }

    /**
     * Capture a real HttpExchange by sending a real HTTP request with the
     * specified header set.
     */
    private HttpExchange captureExchangeWithHeader(String headerName, String headerValue)
            throws Exception {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        captureServer = startCaptureServer(captured);
        sendRealRequest(headerName, headerValue);

        // Wait briefly for the handler to run
        long deadline = System.currentTimeMillis() + 2000;
        while (captured.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNotNull("Capture server must have received an exchange", captured.get());
        return captured.get();
    }

    /**
     * Capture a real HttpExchange with no extra headers.
     */
    private HttpExchange captureExchangeNoCredentials() throws Exception {
        return captureExchangeWithHeader(null, null);
    }

    // =========================================================================
    // AuthenticatedPrincipal tests - no HTTP needed
    // =========================================================================

    public void testPrincipalConstruction() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "agent-order-processor",
            Set.of(AuthenticatedPrincipal.PERM_ALL),
            "ApiKey",
            Instant.now(),
            null
        );
        assertNotNull("Principal should be created", principal);
        assertEquals("agent-order-processor", principal.getUsername());
        assertTrue("Principal should be authenticated", principal.isAuthenticated());
    }

    public void testPrincipalNullUsernameThrows() {
        try {
            new AuthenticatedPrincipal(null, Set.of(), "ApiKey", Instant.now(), null);
            fail("Expected IllegalArgumentException for null username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testPrincipalBlankUsernameThrows() {
        try {
            new AuthenticatedPrincipal("  ", Set.of(), "ApiKey", Instant.now(), null);
            fail("Expected IllegalArgumentException for blank username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testPrincipalNullPermissionsThrows() {
        try {
            new AuthenticatedPrincipal("user", null, "ApiKey", Instant.now(), null);
            fail("Expected NullPointerException for null permissions");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testPrincipalNullAuthSchemeThrows() {
        try {
            new AuthenticatedPrincipal("user", Set.of(), null, Instant.now(), null);
            fail("Expected NullPointerException for null authScheme");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testPrincipalNullAuthenticatedAtThrows() {
        try {
            new AuthenticatedPrincipal("user", Set.of(), "Bearer", null, null);
            fail("Expected NullPointerException for null authenticatedAt");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testPrincipalHasSpecificPermissions() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "agent",
            Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
                   AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Bearer",
            Instant.now(),
            null
        );
        assertTrue("Should have WORKFLOW_LAUNCH",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertTrue("Should have WORKFLOW_QUERY",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertFalse("Should not have WORKFLOW_CANCEL",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertFalse("Should not have WORKITEM_MANAGE",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
    }

    public void testPrincipalWildcardGrantsAllPermissions() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "admin-agent",
            Set.of(AuthenticatedPrincipal.PERM_ALL),
            "mTLS",
            Instant.now(),
            null
        );
        assertTrue("Wildcard grants WORKFLOW_LAUNCH",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertTrue("Wildcard grants WORKFLOW_QUERY",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertTrue("Wildcard grants WORKFLOW_CANCEL",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertTrue("Wildcard grants WORKITEM_MANAGE",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
    }

    public void testPrincipalEmptyPermissionsDeniesAll() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "readonly-agent", Set.of(), "Bearer", Instant.now(), null);
        assertFalse("Empty permissions should deny WORKFLOW_LAUNCH",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertFalse("Empty permissions should deny wildcard",
            principal.hasPermission(AuthenticatedPrincipal.PERM_ALL));
    }

    public void testPrincipalNullExpiryMeansNonExpiring() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "api-key-agent", Set.of(AuthenticatedPrincipal.PERM_ALL),
            "ApiKey", Instant.now(), null);
        assertFalse("Null expiry means non-expiring credential", principal.isExpired());
    }

    public void testPrincipalPastExpiryIsExpired() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "jwt-agent", Set.of(AuthenticatedPrincipal.PERM_ALL),
            "Bearer", Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(1)
        );
        assertTrue("Past expiry instant should be expired", principal.isExpired());
    }

    public void testPrincipalFutureExpiryIsNotExpired() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "jwt-agent", Set.of(AuthenticatedPrincipal.PERM_ALL),
            "Bearer", Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        assertFalse("Future expiry instant should not be expired", principal.isExpired());
    }

    public void testPrincipalToStringContainsUsername() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            "test-agent", Set.of(AuthenticatedPrincipal.PERM_ALL),
            "ApiKey", Instant.now(), null);
        String str = principal.toString();
        assertTrue("toString should contain username", str.contains("test-agent"));
        assertTrue("toString should contain scheme", str.contains("ApiKey"));
    }

    public void testPrincipalEqualityOnSameFields() {
        Instant now = Instant.now();
        AuthenticatedPrincipal p1 = new AuthenticatedPrincipal(
            "agent", Set.of(), "Bearer", now, null);
        AuthenticatedPrincipal p2 = new AuthenticatedPrincipal(
            "agent", Set.of(), "Bearer", now, null);
        assertEquals("Principals with same username/scheme/time should be equal", p1, p2);
    }

    public void testPrincipalInequalityOnDifferentUsername() {
        Instant now = Instant.now();
        AuthenticatedPrincipal p1 = new AuthenticatedPrincipal(
            "agent1", Set.of(), "Bearer", now, null);
        AuthenticatedPrincipal p2 = new AuthenticatedPrincipal(
            "agent2", Set.of(), "Bearer", now, null);
        assertFalse("Different usernames should not be equal", p1.equals(p2));
    }

    public void testPrincipalHashCodeConsistency() {
        Instant now = Instant.now();
        AuthenticatedPrincipal p1 = new AuthenticatedPrincipal(
            "agent", Set.of(), "Bearer", now, null);
        AuthenticatedPrincipal p2 = new AuthenticatedPrincipal(
            "agent", Set.of(), "Bearer", now, null);
        assertEquals("Equal principals must have same hashCode",
            p1.hashCode(), p2.hashCode());
    }

    // =========================================================================
    // A2AAuthenticationException tests - no HTTP needed
    // =========================================================================

    public void testAuthExceptionMessage() {
        A2AAuthenticationException ex = new A2AAuthenticationException(
            "Invalid API key", "ApiKey");
        assertEquals("Invalid API key", ex.getMessage());
        assertEquals("ApiKey", ex.getSupportedSchemes());
    }

    public void testAuthExceptionNullSchemesDefaultsToBearer() {
        A2AAuthenticationException ex = new A2AAuthenticationException(
            "Auth failed", null);
        assertEquals("Should default to Bearer for null schemes",
            "Bearer", ex.getSupportedSchemes());
    }

    public void testAuthExceptionWithCause() {
        RuntimeException cause = new RuntimeException("Internal error");
        A2AAuthenticationException ex = new A2AAuthenticationException(
            "Auth failed", "Bearer", cause);
        assertEquals("Auth failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    // =========================================================================
    // ApiKeyAuthenticationProvider unit tests - no HTTP needed
    // =========================================================================

    public void testApiKeyProviderScheme() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        assertEquals("ApiKey", provider.scheme());
    }

    public void testApiKeyProviderInitiallyEmpty() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        assertEquals("No keys registered initially", 0, provider.registeredKeyCount());
    }

    public void testApiKeyProviderShortMasterKeyThrows() {
        try {
            new ApiKeyAuthenticationProvider("short");
            fail("Expected IllegalArgumentException for short master key");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention minimum length",
                e.getMessage().contains("16") || e.getMessage().contains("minimum"));
        }
    }

    public void testApiKeyProviderNullMasterKeyThrows() {
        try {
            new ApiKeyAuthenticationProvider(null);
            fail("Expected IllegalArgumentException for null master key");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testApiKeyProviderRegisterAndCount() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        provider.registerKey("agent-1", "agent-order-processor", "my-secret-key",
            Set.of(AuthenticatedPrincipal.PERM_ALL));
        assertEquals("One key should be registered", 1, provider.registeredKeyCount());
    }

    public void testApiKeyProviderMultipleKeys() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        provider.registerKey("agent-1", "agent-1", "key-one",
            Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        provider.registerKey("agent-2", "agent-2", "key-two",
            Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertEquals("Two keys should be registered", 2, provider.registeredKeyCount());
    }

    public void testApiKeyProviderRevokeExistingKey() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        provider.registerKey("agent-1", "agent-1", "my-key",
            Set.of(AuthenticatedPrincipal.PERM_ALL));

        boolean revoked = provider.revokeKey("agent-1");
        assertTrue("Revoke should return true for existing key", revoked);
        assertEquals("Zero keys after revocation", 0, provider.registeredKeyCount());
    }

    public void testApiKeyProviderRevokeNonExistentKeyReturnsFalse() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        boolean revoked = provider.revokeKey("non-existent");
        assertFalse("Revoke of non-existent key should return false", revoked);
    }

    public void testApiKeyProviderRegisterKeyNullIdThrows() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        try {
            provider.registerKey(null, "user", "key", Set.of());
            fail("Expected NullPointerException for null keyId");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testApiKeyProviderRegisterKeyBlankIdThrows() {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        try {
            provider.registerKey("", "user", "key", Set.of());
            fail("Expected IllegalArgumentException for blank keyId");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // ApiKeyAuthenticationProvider with real HttpExchange from real HttpServer
    // =========================================================================

    public void testApiKeyProviderAuthSuccessViaRealExchange() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        String rawKey = "valid-test-key-for-real-auth";
        provider.registerKey("test-agent", "test-agent-user", rawKey,
            Set.of(AuthenticatedPrincipal.PERM_ALL));

        HttpExchange exchange = captureExchangeWithHeader("X-API-Key", rawKey);
        AuthenticatedPrincipal principal = provider.authenticate(exchange);

        assertNotNull("Authentication should succeed with valid key", principal);
        assertEquals("test-agent-user", principal.getUsername());
        assertTrue("Should have full permissions",
            principal.hasPermission(AuthenticatedPrincipal.PERM_ALL));
        assertEquals("ApiKey", principal.getAuthScheme());
        assertNull("API key credentials do not expire", principal.getExpiresAt());
        assertFalse("Should not be expired", principal.isExpired());
    }

    public void testApiKeyProviderAuthFailsWithWrongKeyViaRealExchange() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        provider.registerKey("agent", "user", "correct-key",
            Set.of(AuthenticatedPrincipal.PERM_ALL));

        HttpExchange exchange = captureExchangeWithHeader("X-API-Key", "wrong-key");
        try {
            provider.authenticate(exchange);
            fail("Expected A2AAuthenticationException for invalid key");
        } catch (A2AAuthenticationException e) {
            assertTrue("Error should mention invalid or revoked",
                e.getMessage().contains("invalid") || e.getMessage().contains("revoked"));
        }
    }

    public void testApiKeyProviderAuthFailsWithNoKeyHeaderViaRealExchange() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        provider.registerKey("agent", "user", "a-key",
            Set.of(AuthenticatedPrincipal.PERM_ALL));

        HttpExchange exchange = captureExchangeNoCredentials();
        try {
            provider.authenticate(exchange);
            fail("Expected A2AAuthenticationException for missing key header");
        } catch (A2AAuthenticationException e) {
            assertTrue("Error should mention Missing header",
                e.getMessage().contains("Missing") || e.getMessage().contains("header"));
        }
    }

    public void testApiKeyProviderCanHandleDetectsRealApiKeyHeader() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);

        HttpExchange exchange = captureExchangeWithHeader("X-API-Key", "some-key");
        assertTrue("Provider should detect X-API-Key header",
            provider.canHandle(exchange));
    }

    public void testApiKeyProviderCannotHandleWithoutApiKeyHeader() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);

        HttpExchange exchange = captureExchangeNoCredentials();
        assertFalse("Provider should not handle request without X-API-Key header",
            provider.canHandle(exchange));
    }

    public void testApiKeyProviderAuthFailsWithNoRegisteredKeys() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        // No keys registered

        HttpExchange exchange = captureExchangeWithHeader("X-API-Key", "any-key");
        try {
            provider.authenticate(exchange);
            fail("Expected A2AAuthenticationException when no keys configured");
        } catch (A2AAuthenticationException e) {
            assertTrue("Error should mention no configured keys",
                e.getMessage().contains("No API keys") || e.getMessage().contains("configured"));
        }
    }

    // =========================================================================
    // JwtAuthenticationProvider unit tests - no HTTP needed
    // =========================================================================

    public void testJwtProviderScheme() {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        assertEquals("Bearer", provider.scheme());
    }

    public void testJwtProviderShortSecretThrows() {
        try {
            new JwtAuthenticationProvider("too-short", null);
            fail("Expected IllegalArgumentException for short secret");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention 32 bytes or 256 bits",
                e.getMessage().contains("32") || e.getMessage().contains("256"));
        }
    }

    public void testJwtProviderNullSecretThrows() {
        try {
            new JwtAuthenticationProvider(null, null);
            fail("Expected IllegalArgumentException for null secret");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testJwtIssueTokenProducesNonEmptyToken() {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        String token = provider.issueToken("agent", List.of(), 60_000L);
        assertNotNull("Issued token should not be null", token);
        assertFalse("Issued token should not be empty", token.isEmpty());
    }

    public void testJwtIssueTokenNullSubjectThrows() {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        try {
            provider.issueToken(null, List.of(), 60_000L);
            fail("Expected IllegalArgumentException for null subject");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testJwtIssueTokenZeroValidityThrows() {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        try {
            provider.issueToken("agent", List.of(), 0L);
            fail("Expected IllegalArgumentException for zero validity");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testJwtIssueTokenNegativeValidityThrows() {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        try {
            provider.issueToken("agent", List.of(), -1000L);
            fail("Expected IllegalArgumentException for negative validity");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // JwtAuthenticationProvider with real HttpExchange from real HttpServer
    // =========================================================================

    public void testJwtProviderAuthSuccessViaRealExchange() throws Exception {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        String token = provider.issueToken("agent-processor",
            List.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
                    AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            60_000L);

        HttpExchange exchange = captureExchangeWithHeader("Authorization", "Bearer " + token);
        AuthenticatedPrincipal principal = provider.authenticate(exchange);

        assertNotNull("Authentication with valid token should succeed", principal);
        assertEquals("agent-processor", principal.getUsername());
        assertTrue("Should have WORKFLOW_LAUNCH",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertTrue("Should have WORKFLOW_QUERY",
            principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertEquals("Bearer", principal.getAuthScheme());
    }

    public void testJwtProviderTokenWithNoScopeGrantsWildcardViaRealExchange() throws Exception {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        String token = provider.issueToken("no-scope-agent", List.of(), 60_000L);

        HttpExchange exchange = captureExchangeWithHeader("Authorization", "Bearer " + token);
        AuthenticatedPrincipal principal = provider.authenticate(exchange);

        assertTrue("No-scope token should grant wildcard permission",
            principal.hasPermission(AuthenticatedPrincipal.PERM_ALL));
    }

    public void testJwtProviderRejectsExpiredTokenViaRealExchange() throws Exception {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        String token = provider.issueToken("agent", List.of(), 1L);

        Thread.sleep(50); // Ensure token is expired

        HttpExchange exchange = captureExchangeWithHeader("Authorization", "Bearer " + token);
        try {
            provider.authenticate(exchange);
            fail("Expected A2AAuthenticationException for expired token");
        } catch (A2AAuthenticationException e) {
            assertTrue("Error should mention expired",
                e.getMessage().contains("expired") || e.getMessage().contains("Expired"));
        }
    }

    public void testJwtProviderRejectsInvalidTokenViaRealExchange() throws Exception {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);

        HttpExchange exchange = captureExchangeWithHeader(
            "Authorization", "Bearer not.a.valid.jwt");
        try {
            provider.authenticate(exchange);
            fail("Expected A2AAuthenticationException for invalid token");
        } catch (A2AAuthenticationException e) {
            assertTrue("Error should mention invalid",
                e.getMessage().contains("invalid") || e.getMessage().contains("Invalid"));
        }
    }

    public void testJwtProviderRejectsMissingBearerHeaderViaRealExchange() throws Exception {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);

        HttpExchange exchange = captureExchangeNoCredentials();
        try {
            provider.authenticate(exchange);
            fail("Expected A2AAuthenticationException for missing bearer header");
        } catch (A2AAuthenticationException e) {
            assertTrue("Error should mention Authorization or Bearer",
                e.getMessage().contains("Authorization") ||
                e.getMessage().contains("Bearer"));
        }
    }

    public void testJwtProviderCanHandleRealBearerHeader() throws Exception {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);

        HttpExchange exchange = captureExchangeWithHeader(
            "Authorization", "Bearer some.jwt.token");
        assertTrue("Provider should detect Bearer header",
            provider.canHandle(exchange));
    }

    public void testJwtProviderCannotHandleRealApiKeyHeader() throws Exception {
        JwtAuthenticationProvider provider =
            new JwtAuthenticationProvider(JWT_SECRET, null);

        HttpExchange exchange = captureExchangeWithHeader("X-API-Key", "my-api-key");
        assertFalse("JWT provider should not handle API key header",
            provider.canHandle(exchange));
    }

    // =========================================================================
    // CompositeAuthenticationProvider unit tests - no HTTP needed
    // =========================================================================

    public void testCompositeProviderSingleProviderScheme() {
        ApiKeyAuthenticationProvider apiKeyProvider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        CompositeAuthenticationProvider composite =
            new CompositeAuthenticationProvider(apiKeyProvider);
        assertEquals("ApiKey", composite.scheme());
        assertEquals("Should have 1 provider", 1, composite.getProviders().size());
    }

    public void testCompositeProviderMultipleProvidersScheme() {
        JwtAuthenticationProvider jwtProvider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        ApiKeyAuthenticationProvider apiKeyProvider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);

        CompositeAuthenticationProvider composite =
            new CompositeAuthenticationProvider(jwtProvider, apiKeyProvider);

        String scheme = composite.scheme();
        assertTrue("Composite scheme should include Bearer", scheme.contains("Bearer"));
        assertTrue("Composite scheme should include ApiKey", scheme.contains("ApiKey"));
        assertEquals("Should have 2 providers", 2, composite.getProviders().size());
    }

    public void testCompositeProviderNullListThrows() {
        try {
            new CompositeAuthenticationProvider(
                (java.util.List<
                    org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationProvider>)
                null);
            fail("Expected NullPointerException for null provider list");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testCompositeProviderEmptyListThrows() {
        try {
            new CompositeAuthenticationProvider(java.util.Collections.emptyList());
            fail("Expected IllegalArgumentException for empty provider list");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // CompositeAuthenticationProvider with real HttpExchange
    // =========================================================================

    public void testCompositeAuthWithApiKeyViaRealExchange() throws Exception {
        ApiKeyAuthenticationProvider apiKeyProvider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        String rawKey = "composite-real-test-key";
        apiKeyProvider.registerKey("composite-agent", "composite-user", rawKey,
            Set.of(AuthenticatedPrincipal.PERM_ALL));

        CompositeAuthenticationProvider composite =
            new CompositeAuthenticationProvider(apiKeyProvider);

        HttpExchange exchange = captureExchangeWithHeader("X-API-Key", rawKey);
        AuthenticatedPrincipal principal = composite.authenticate(exchange);

        assertNotNull("Composite auth with API key should succeed", principal);
        assertEquals("composite-user", principal.getUsername());
    }

    public void testCompositeAuthWithJwtViaRealExchange() throws Exception {
        JwtAuthenticationProvider jwtProvider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        String token = jwtProvider.issueToken("jwt-composite-agent",
            List.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY), 60_000L);

        ApiKeyAuthenticationProvider apiKeyProvider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);

        CompositeAuthenticationProvider composite =
            new CompositeAuthenticationProvider(jwtProvider, apiKeyProvider);

        HttpExchange exchange = captureExchangeWithHeader("Authorization", "Bearer " + token);
        AuthenticatedPrincipal principal = composite.authenticate(exchange);

        assertNotNull("Composite auth with JWT should succeed", principal);
        assertEquals("jwt-composite-agent", principal.getUsername());
    }

    public void testCompositeNoCredentialsThrowsViaRealExchange() throws Exception {
        JwtAuthenticationProvider jwtProvider =
            new JwtAuthenticationProvider(JWT_SECRET, null);
        ApiKeyAuthenticationProvider apiKeyProvider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);

        CompositeAuthenticationProvider composite =
            new CompositeAuthenticationProvider(jwtProvider, apiKeyProvider);

        HttpExchange exchange = captureExchangeNoCredentials();
        try {
            composite.authenticate(exchange);
            fail("Expected A2AAuthenticationException when no provider can handle");
        } catch (A2AAuthenticationException e) {
            assertTrue("Error should mention no credentials found",
                e.getMessage().contains("No authentication") ||
                e.getMessage().contains("credentials"));
        }
    }

    public void testCompositeCanHandleDetectsApiKeyViaRealExchange() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        CompositeAuthenticationProvider composite =
            new CompositeAuthenticationProvider(provider);

        HttpExchange exchange = captureExchangeWithHeader("X-API-Key", "some-key");
        assertTrue("Composite should detect API key header", composite.canHandle(exchange));
    }

    public void testCompositeCannotHandleWithoutCredentialsViaRealExchange() throws Exception {
        ApiKeyAuthenticationProvider provider =
            new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        CompositeAuthenticationProvider composite =
            new CompositeAuthenticationProvider(provider);

        HttpExchange exchange = captureExchangeNoCredentials();
        assertFalse("Composite should not handle request without credentials",
            composite.canHandle(exchange));
    }
}
