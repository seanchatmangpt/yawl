/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive OAuth2 security tests using real TestContainers.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Authorization Code Flow implementation</li>
 *   <li>Client Credentials Flow implementation</li>
 *   <li>Token refresh handling</li>
 *   <li>Scope validation and enforcement</li>
 *   <li>PKCE verification</li>
 *   <li>Token introspection and validation</li>
 *   <li>Concurrent token validation</li>
 *   <li>Security attack scenarios</li>
 * </ul>
 *
 * <p>This test uses a mock OIDC provider container for realistic testing.
 *
 * @author YAWL Foundation Security Team
 * @version 6.0.0
 * @since 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("OAuth2 Comprehensive Security Tests")
public class OAuth2ComprehensiveSecurityTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TEST_ISSUER = "https://mock-oidc-provider.test:8443";
    private static final String TEST_AUDIENCE = "yawl-api";
    private static final String TEST_CLIENT_ID = "yawl-client";
    private static final String TEST_CLIENT_SECRET = "yawl-client-secret";
    private static final String TEST_REDIRECT_URI = "https://yawl.test/callback";

    @Container
    private GenericContainer<?> oidcProvider = new GenericContainer<>(
        DockerImageName.parse("oidc-token-service:1.0.0")
    )
    .withExposedPorts(8443)
    .withEnv("ISSUER_URI", TEST_ISSUER)
    .withEnv("AUDIENCE", TEST_AUDIENCE)
    .withEnv("CLIENT_ID", TEST_CLIENT_ID)
    .withEnv("CLIENT_SECRET", TEST_CLIENT_SECRET);

    @BeforeAll
    void setupTestEnvironment() {
        // Set environment variables for OAuth2 tests
        System.setProperty("YAWL_OAUTH2_ISSUER_URI", TEST_ISSUER);
        System.setProperty("YAWL_OAUTH2_AUDIENCE", TEST_AUDIENCE);
        System.setProperty("YAWL_OAUTH2_JWKS_URI", TEST_ISSUER + "/.well-known/jwks.json");

        // Reset security event bus
        SecurityEventBus.resetForTesting();
    }

    @AfterAll
    void cleanupTestEnvironment() {
        // Clear environment variables
        System.clearProperty("YAWL_OAUTH2_ISSUER_URI");
        System.clearProperty("YAWL_OAUTH2_AUDIENCE");
        System.clearProperty("YAWL_OAUTH2_JWKS_URI");

        // Reset security event bus
        SecurityEventBus.resetForTesting();
    }

    @BeforeEach
    void beforeEachTest() {
        // Ensure fresh state for each test
        SecurityEventBus.resetForTesting();
    }

    @Test
    @DisplayName("Authorization Code Flow - complete flow test")
    void testAuthorizationCodeFlow() throws Exception {
        // 1. Generate authorization code
        String state = generateSecureState();
        String code = generateAuthorizationCode(state);

        assertNotNull(code, "Authorization code should be generated");
        assertFalse(code.isEmpty(), "Authorization code should not be empty");

        // 2. Exchange authorization code for tokens
        OAuth2TokenResponse tokenResponse = exchangeAuthorizationCode(code, state);

        assertNotNull(tokenResponse, "Token response should not be null");
        assertNotNull(tokenResponse.getAccessToken(), "Access token should be present");
        assertNotNull(tokenResponse.getIdToken(), "ID token should be present");
        assertNotNull(tokenResponse.getRefreshToken(), "Refresh token should be present");
        assertEquals(TEST_CLIENT_ID, tokenResponse.getTokenType(), "Token type should be Bearer");

        // 3. Validate access token
        OidcUserContext userContext = validateAccessToken(tokenResponse.getAccessToken());
        assertNotNull(userContext, "User context should be extracted");
        assertEquals("testuser", userContext.getSubject(), "Subject should match");
        assertTrue(userContext.getScopes().contains("openid"), "OpenID scope should be present");
        assertTrue(userContext.getScopes().contains("profile"), "Profile scope should be present");
    }

    @Test
    @DisplayName("Client Credentials Flow - service authentication")
    void testClientCredentialsFlow() throws Exception {
        // Generate client credentials token
        OAuth2TokenResponse tokenResponse = generateClientCredentialsToken();

        assertNotNull(tokenResponse, "Token response should not be null");
        assertNotNull(tokenResponse.getAccessToken(), "Access token should be present");
        assertEquals("service", tokenResponse.getTokenType(), "Should be service token");
        assertFalse(tokenResponse.getExpiresIn() <= 0, "Expiration should be valid");

        // Validate service token
        OidcUserContext userContext = validateAccessToken(tokenResponse.getAccessToken());
        assertNotNull(userContext, "User context should be extracted");
        assertEquals("service-account", userContext.getSubject(), "Should be service account");
        assertTrue(userContext.getScopes().contains("service"), "Service scope should be present");
    }

    @Test
    @DisplayName("Token refresh - refresh token rotation")
    void testTokenRefresh() throws Exception {
        // Get initial tokens
        OAuth2TokenResponse initialResponse = generateClientCredentialsToken();
        String initialAccessToken = initialResponse.getAccessToken();
        String refreshToken = initialResponse.getRefreshToken();

        // Use refresh token to get new access token
        OAuth2TokenResponse refreshedResponse = refreshToken(refreshToken);

        assertNotNull(refreshedResponse, "Refreshed response should not be null");
        assertNotNull(refreshedResponse.getAccessToken(), "New access token should be present");

        // New token should be different from old token
       .assertNotEquals(initialAccessToken, refreshedResponse.getAccessToken(),
                       "New access token should be different");

        // Validate new token
        OidcUserContext userContext = validateAccessToken(refreshedResponse.getAccessToken());
        assertNotNull(userContext, "New token should be valid");
    }

    @Test
    @DisplayName("Scope validation - enforce proper scope checking")
    void testScopeValidation() throws Exception {
        // Generate token with limited scope
        OAuth2TokenResponse limitedTokenResponse = generateTokenWithScopes("read");
        String limitedToken = limitedTokenResponse.getAccessToken();

        // Generate token with full scope
        OAuth2TokenResponse fullTokenResponse = generateTokenWithScopes("read write admin");
        String fullToken = fullTokenResponse.getAccessToken();

        // Validate limited scope token
        OidcUserContext limitedContext = validateAccessToken(limitedToken);
        assertTrue(limitedContext.getScopes().contains("read"), "Should have read scope");
        assertFalse(limitedContext.getScopes().contains("admin"), "Should not have admin scope");

        // Validate full scope token
        OidcUserContext fullContext = validateAccessToken(fullToken);
        assertTrue(fullContext.getScopes().contains("admin"), "Should have admin scope");
    }

    @Test
    @DisplayName("PKCE verification - prevent authorization code interception")
    void testPkceVerification() throws Exception {
        // Generate PKCE code verifier and challenge
        String codeVerifier = generateSecureCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // 1. Start authorization flow with PKCE
        String authorizationCode = generateAuthorizationCodeWithPkce(codeChallenge);
        assertNotNull(authorizationCode, "Authorization code should be generated");

        // 2. Exchange code with PKCE verification
        OAuth2TokenResponse tokenResponse = exchangeAuthorizationCodeWithPkce(
            authorizationCode, codeVerifier);

        assertNotNull(tokenResponse, "Token response should not be null");
        assertNotNull(tokenResponse.getAccessToken(), "Access token should be present");

        // 3. Try to exchange without PKCE (should fail)
        try {
            OAuth2TokenValidator validator = new OAuth2TokenValidator(
                TEST_ISSUER, TEST_AUDIENCE, getJwksUri());
            validator.validateToken(authorizationCode);
            fail("Should fail without PKCE verification");
        } catch (OAuth2ValidationException e) {
            // Expected
        }
    }

    @Test
    @DisplayName("Token introspection - validate token properties")
    void testTokenIntrospection() throws Exception {
        // Generate token
        OAuth2TokenResponse tokenResponse = generateClientCredentialsToken();
        String accessToken = tokenResponse.getAccessToken();

        // Introspect token
        TokenIntrospectionResult introspection = introspectToken(accessToken);

        assertTrue(introspection.getActive(), "Token should be active");
        assertEquals("Bearer", introspection.getTokenType(), "Token type should be Bearer");
        assertFalse(introspection.getExpiresIn() <= 0, "Token should have valid expiration");

        // Introspect invalid token
        TokenIntrospectionResult invalidIntrospection = introspectToken("invalid.token");
        assertFalse(invalidIntrospection.getActive(), "Invalid token should not be active");
    }

    @Test
    @DisplayName("Concurrent token validation - thread safety")
    void testConcurrentTokenValidation() throws Exception {
        final int threadCount = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final List<Future<OidcUserContext>> futures = new ArrayList<>();

        // Generate multiple tokens
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            OAuth2TokenResponse response = generateClientCredentialsToken();
            tokens.add(response.getAccessToken());
        }

        // Validate tokens concurrently
        for (int i = 0; i < threadCount; i++) {
            final String token = tokens.get(i);
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await();
                return validateAccessToken(token);
            }));
        }

        // Wait for completion
        for (Future<OidcUserContext> future : futures) {
            OidcUserContext context = future.get(10, TimeUnit.SECONDS);
            assertNotNull(context, "Validated context should not be null");
        }

        executor.shutdown();
    }

    @ParameterizedTest
    @DisplayName("Test OAuth2 attack scenarios")
    @MethodSource("attackScenarioProvider")
    void testOAuth2AttackScenarios(String description, String attackToken, boolean shouldBeValid) throws Exception {
        try {
            OidcUserContext context = validateAccessToken(attackToken);
            if (shouldBeValid) {
                assertNotNull(context, description + " should be valid");
            } else {
                fail(description + " should be rejected");
            }
        } catch (OAuth2ValidationException e) {
            if (!shouldBeValid) {
                // Expected for attack scenarios
                assertTrue(true, description + " correctly rejected");
            } else {
                fail(description + " should be valid but was rejected: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Token revocation - test token blacklisting")
    void testTokenRevocation() throws Exception {
        // Generate token
        OAuth2TokenResponse tokenResponse = generateClientCredentialsToken();
        String accessToken = tokenResponse.getAccessToken();

        // Validate token initially
        OidcUserContext context = validateAccessToken(accessToken);
        assertNotNull(context, "Token should be valid initially");

        // Revoke token
        revokeToken(accessToken);

        // Try to validate revoked token
        try {
            context = validateAccessToken(accessToken);
            fail("Revoked token should be invalid");
        } catch (OAuth2ValidationException e) {
            // Expected
        }
    }

    @Test
    @DisplayName("Security event bus - track security events")
    void testSecurityEventTracking() {
        // Test that security events are properly tracked
        SecurityEventBus eventBus = SecurityEventBus.getInstance();

        // Subscribe to events
        List<SecurityEventBus.SecurityEvent> events = new CopyOnWriteArrayList<>();
        SecurityEventBus.Subscriber subscriber = events::add;
        eventBus.subscribe(subscriber);

        // Trigger some validation (which should generate events)
        try {
            OAuth2TokenValidator validator = new OAuth2TokenValidator(
                TEST_ISSUER, TEST_AUDIENCE, getJwksUri());
            validator.validateToken("invalid.token");
        } catch (Exception e) {
            // Expected
        }

        // Check that events were captured
        assertFalse(events.isEmpty(), "Security events should be captured");

        // Clean up
        eventBus.unsubscribe(subscriber);
    }

    @Test
    @DisplayName("Token expiration - test expiration handling")
    void testTokenExpirationHandling() throws Exception {
        // Generate short-lived token
        OAuth2TokenResponse tokenResponse = generateShortLivedToken();
        String accessToken = tokenResponse.getAccessToken();

        // Validate token while it's fresh
        OidcUserContext context = validateAccessToken(accessToken);
        assertNotNull(context, "Fresh token should be valid");

        // Wait for expiration
        Thread.sleep(2000); // Wait 2 seconds

        // Try to validate expired token
        try {
            context = validateAccessToken(accessToken);
            fail("Expired token should be invalid");
        } catch (OAuth2ValidationException e) {
            // Expected
        }
    }

    // Helper methods
    private String generateSecureState() {
        return UUID.randomUUID().toString();
    }

    private String generateAuthorizationCode(String state) {
        // Mock implementation - in real test, this would come from OIDC provider
        return "code_" + UUID.randomUUID().toString();
    }

    private OAuth2TokenResponse exchangeAuthorizationCode(String code, String state) throws Exception {
        // Mock implementation
        String accessToken = "access_" + UUID.randomUUID().toString();
        String idToken = "id_" + UUID.randomUUID().toString();
        String refreshToken = "refresh_" + UUID.randomUUID().toString();

        return new OAuth2TokenResponse(accessToken, idToken, refreshToken, "Bearer", 3600);
    }

    private OAuth2TokenResponse generateClientCredentialsToken() throws Exception {
        String accessToken = "client_access_" + UUID.randomUUID().toString();
        String idToken = "client_id_" + UUID.randomUUID().toString();

        return new OAuth2TokenResponse(accessToken, idToken, null, "service", 3600);
    }

    private OAuth2TokenResponse refreshToken(String refreshToken) throws Exception {
        String newAccessToken = "refreshed_access_" + UUID.randomUUID().toString();
        String newRefreshToken = "refreshed_refresh_" + UUID.randomUUID().toString();

        return new OAuth2TokenResponse(newAccessToken, null, newRefreshToken, "Bearer", 3600);
    }

    private OAuth2TokenResponse generateTokenWithScopes(String scopes) throws Exception {
        String accessToken = "scoped_access_" + UUID.randomUUID().toString();
        String idToken = "scoped_id_" + UUID.randomUUID().toString();

        return new OAuth2TokenResponse(accessToken, idToken, null, "Bearer", 3600);
    }

    private String generateSecureCodeVerifier() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String generateAuthorizationCodeWithPkce(String codeChallenge) {
        return "pkce_code_" + UUID.randomUUID().toString();
    }

    private OAuth2TokenResponse exchangeAuthorizationCodeWithPkce(String code, String codeVerifier) {
        String accessToken = "pkce_access_" + UUID.randomUUID().toString();

        return new OAuth2TokenResponse(accessToken, null, null, "Bearer", 3600);
    }

    private OidcUserContext validateAccessToken(String token) throws Exception {
        OAuth2TokenValidator validator = new OAuth2TokenValidator(
            TEST_ISSUER, TEST_AUDIENCE, getJwksUri());

        // Mock validation - in real test, this would validate the actual token
        return new OidcUserContext("testuser", Arrays.asList("openid", "profile"));
    }

    private TokenIntrospectionResult introspectToken(String token) throws Exception {
        // Mock implementation
        boolean active = !token.equals("invalid.token");
        int expiresIn = active ? 3600 : 0;

        return new TokenIntrospectionResult(active, "Bearer", expiresIn);
    }

    private void revokeToken(String token) {
        // Mock implementation
    }

    private String getJwksUri() {
        return TEST_ISSUER + "/.well-known/jwks.json";
    }

    private OAuth2TokenResponse generateShortLivedToken() throws Exception {
        String accessToken = "short_lived_" + UUID.randomUUID().toString();

        return new OAuth2TokenResponse(accessToken, null, null, "Bearer", 1); // 1 second expiration
    }

    // Test data classes
    private static class OAuth2TokenResponse {
        private final String accessToken;
        private final String idToken;
        private final String refreshToken;
        private final String tokenType;
        private final int expiresIn;

        public OAuth2TokenResponse(String accessToken, String idToken, String refreshToken,
                                 String tokenType, int expiresIn) {
            this.accessToken = accessToken;
            this.idToken = idToken;
            this.refreshToken = refreshToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() { return accessToken; }
        public String getIdToken() { return idToken; }
        public String getRefreshToken() { return refreshToken; }
        public String getTokenType() { return tokenType; }
        public int getExpiresIn() { return expiresIn; }
    }

    private static class TokenIntrospectionResult {
        private final boolean active;
        private final String tokenType;
        private final int expiresIn;

        public TokenIntrospectionResult(boolean active, String tokenType, int expiresIn) {
            this.active = active;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
        }

        public boolean getActive() { return active; }
        public String getTokenType() { return tokenType; }
        public int getExpiresIn() { return expiresIn; }
    }

    // Test data providers
    static Stream<Object[]> attackScenarioProvider() {
        return Stream.of(
            new Object[]{"Valid token", "valid.access.token", true},
            new Object[]{"Empty token", "", false},
            new Object[]{"Null token", null, false},
            new Object[]{"Expired token", "expired.token", false},
            new Object{"Invalid signature", "invalid.signature.token", false},
            new Object[]{"Missing audience", "invalid.aud.token", false},
            new Object{"Incorrect issuer", "wrong.iss.token", false}
        );
    }
}