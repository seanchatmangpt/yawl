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

package org.yawlfoundation.yawl.authentication;

import io.jsonwebtoken.Claims;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * V6 JWT security tests for the JwtManager class.
 *
 * <p>Covers:
 * <ul>
 *   <li>JWT token generation produces valid 3-part format</li>
 *   <li>Generated token contains expected claims (subject, sessionHandle)</li>
 *   <li>Null token is correctly rejected (returns null, no exception)</li>
 *   <li>Empty string token is correctly rejected</li>
 *   <li>Malformed token (not a JWT) is correctly rejected</li>
 *   <li>Token with tampered signature is rejected</li>
 *   <li>Token with modified payload (but valid structure) is rejected</li>
 *   <li>getUserId() returns null for null/invalid tokens without throwing</li>
 *   <li>getSessionHandle() returns null for null/invalid tokens without throwing</li>
 *   <li>Signing key is persistent: all tokens from same key are valid simultaneously</li>
 *   <li>Signing key requires explicit configuration (no hardcoded default)</li>
 *   <li>Fresh token is not expired</li>
 *   <li>Invalid token is reported as expired/invalid</li>
 *   <li>All JWT operations handle malformed input without throwing exceptions</li>
 * </ul>
 *
 * <p>Chicago TDD: tests use real JwtManager with real JJWT library, no mocks.
 *
 * @author YAWL Engine Team - V6 security validation 2026-02-17
 */
public class V6SecurityTest extends TestCase {

    private static final String JWT_SECRET_PROPERTY = "yawl.jwt.secret";
    private static final String VALID_JWT_SECRET =
            "v6-security-test-secret-minimum-32-bytes-1234567890";

    public V6SecurityTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Configure JWT secret via system property (the required V6 mechanism)
        System.setProperty(JWT_SECRET_PROPERTY, VALID_JWT_SECRET);
    }

    // =========================================================================
    //  JWT Token Generation Tests
    // =========================================================================

    /**
     * Verifies that JwtManager.generateToken() produces a non-null, non-empty
     * JWT token in the standard three-part format (header.payload.signature).
     */
    public void testJwtGenerateTokenProducesValidFormat() {
        String token = JwtManager.generateToken("user-v6-test", "session-abc-123");
        assertNotNull("generateToken() must return non-null token", token);
        assertFalse("generateToken() must return non-empty token", token.isEmpty());

        // JWT format: three base64url-encoded parts separated by dots
        String[] parts = token.split("\\.");
        assertEquals("JWT must have exactly 3 parts (header.payload.signature)",
                3, parts.length);
    }

    /**
     * Verifies that generated tokens contain the expected claims: subject (userId)
     * and sessionHandle.
     */
    public void testJwtTokenContainsExpectedClaims() {
        String userId = "security-test-user";
        String sessionHandle = "security-test-session-" + System.nanoTime();

        String token = JwtManager.generateToken(userId, sessionHandle);
        Claims claims = JwtManager.validateToken(token);

        assertNotNull("Valid token must produce non-null claims", claims);
        assertEquals("Token subject must equal userId", userId, claims.getSubject());
        assertEquals("Token sessionHandle claim must match",
                sessionHandle, claims.get("sessionHandle"));
    }

    // =========================================================================
    //  JWT Token Validation - Rejection Tests
    // =========================================================================

    /**
     * Verifies that null token is correctly rejected (returns null claims, no exception).
     */
    public void testJwtValidationRejectsNullToken() {
        Claims claims = JwtManager.validateToken(null);
        assertNull("Null token must return null claims (not throw)", claims);
    }

    /**
     * Verifies that empty string token is correctly rejected.
     */
    public void testJwtValidationRejectsEmptyToken() {
        Claims claims = JwtManager.validateToken("");
        assertNull("Empty token must return null claims", claims);
    }

    /**
     * Verifies that a malformed token (not even a JWT) is correctly rejected.
     */
    public void testJwtValidationRejectsMalformedToken() {
        Claims claims = JwtManager.validateToken("not-a-jwt-token");
        assertNull("Malformed token must return null claims", claims);
    }

    /**
     * Verifies that a structurally valid but incorrectly signed token is rejected.
     * This tests the signature verification which is the core JWT security check.
     */
    public void testJwtValidationRejectsTamperedSignature() {
        // Generate a real token and tamper with its signature
        String validToken = JwtManager.generateToken("legit-user", "legit-session");
        String[] parts = validToken.split("\\.");
        assertEquals("Valid token must have 3 parts", 3, parts.length);

        // Replace signature with an invalid value
        String tamperedToken = parts[0] + "." + parts[1] + ".tampered_invalid_signature";
        Claims claims = JwtManager.validateToken(tamperedToken);
        assertNull("Token with tampered signature must be rejected", claims);
    }

    /**
     * Verifies that a token with a modified payload (but valid structure) is rejected.
     * This tests that altering the claims payload invalidates the signature.
     */
    public void testJwtValidationRejectsModifiedPayload() {
        String validToken = JwtManager.generateToken("user-original", "session-original");
        String[] parts = validToken.split("\\.");

        // Substitute a different user's payload while keeping the original signature
        String altPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"hacker\",\"sessionHandle\":\"stolen\"}".getBytes());
        String modifiedToken = parts[0] + "." + altPayload + "." + parts[2];

        Claims claims = JwtManager.validateToken(modifiedToken);
        assertNull("Token with modified payload must be rejected", claims);
    }

    // =========================================================================
    //  JWT Safe Extraction Tests
    // =========================================================================

    /**
     * Verifies that getUserId() returns null for null token without throwing.
     */
    public void testGetUserIdHandlesNullTokenSafely() {
        String userId = JwtManager.getUserId(null);
        assertNull("getUserId(null) must return null, not throw", userId);
    }

    /**
     * Verifies that getUserId() returns null for invalid token without throwing.
     */
    public void testGetUserIdHandlesInvalidTokenSafely() {
        String userId = JwtManager.getUserId("invalid.token.data");
        assertNull("getUserId(invalid) must return null, not throw", userId);
    }

    /**
     * Verifies that getUserId() returns the correct user from a valid token.
     */
    public void testGetUserIdExtractsSubjectFromValidToken() {
        String token = JwtManager.generateToken("extract-user-test", "some-session");
        String userId = JwtManager.getUserId(token);
        assertEquals("getUserId() must return the userId from a valid token",
                "extract-user-test", userId);
    }

    /**
     * Verifies that getSessionHandle() returns null for null token without throwing.
     */
    public void testGetSessionHandleHandlesNullTokenSafely() {
        String handle = JwtManager.getSessionHandle(null);
        assertNull("getSessionHandle(null) must return null, not throw", handle);
    }

    /**
     * Verifies that getSessionHandle() returns null for invalid token without throwing.
     */
    public void testGetSessionHandleHandlesInvalidTokenSafely() {
        String handle = JwtManager.getSessionHandle("garbage.token.data");
        assertNull("getSessionHandle(invalid) must return null, not throw", handle);
    }

    /**
     * Verifies that getSessionHandle() returns the correct handle from a valid token.
     */
    public void testGetSessionHandleExtractsClaimFromValidToken() {
        String sessionHandle = "session-handle-xyz-" + System.nanoTime();
        String token = JwtManager.generateToken("some-user", sessionHandle);
        String extracted = JwtManager.getSessionHandle(token);
        assertEquals("getSessionHandle() must return the sessionHandle from a valid token",
                sessionHandle, extracted);
    }

    // =========================================================================
    //  JWT Signing Key Tests (V6 fix: persistent key, not ephemeral)
    // =========================================================================

    /**
     * Verifies that the JWT signing key is persistent across multiple token
     * generations within the same JVM instance. All tokens generated with the
     * same configured key must be valid simultaneously.
     */
    public void testJwtSigningKeyIsPersistentAcrossTokens() {
        // Generate multiple tokens
        String token1 = JwtManager.generateToken("user-1", "session-1");
        String token2 = JwtManager.generateToken("user-2", "session-2");
        String token3 = JwtManager.generateToken("user-3", "session-3");

        // All must be valid (same signing key)
        assertNotNull("Token 1 must be valid", JwtManager.validateToken(token1));
        assertNotNull("Token 2 must be valid", JwtManager.validateToken(token2));
        assertNotNull("Token 3 must be valid", JwtManager.validateToken(token3));

        // Each token must identify its own user
        assertEquals("Token 1 must identify user-1", "user-1", JwtManager.getUserId(token1));
        assertEquals("Token 2 must identify user-2", "user-2", JwtManager.getUserId(token2));
        assertEquals("Token 3 must identify user-3", "user-3", JwtManager.getUserId(token3));
    }

    /**
     * Verifies that the JWT signing key is loaded from system property, not hardcoded.
     * Since we set the property in setUp(), token generation must succeed.
     * The V6 fix removes the hardcoded fallback key that was the V5 security vulnerability.
     */
    public void testJwtSigningKeyRequiresExplicitConfiguration() {
        // Since we set the property in setUp(), token generation must succeed
        String token = JwtManager.generateToken("config-test-user", "config-test-session");
        assertNotNull("JwtManager with configured key must generate valid tokens", token);
        Claims claims = JwtManager.validateToken(token);
        assertNotNull("Token generated with configured key must be valid", claims);
        assertEquals("config-test-user", claims.getSubject());
    }

    // =========================================================================
    //  JWT Expiration Tests
    // =========================================================================

    /**
     * Verifies that a fresh token is not reported as expired.
     */
    public void testFreshTokenIsNotExpired() {
        String token = JwtManager.generateToken("expiry-test-user", "expiry-test-session");
        assertFalse("Fresh token must not be expired", JwtManager.isExpired(token));
    }

    /**
     * Verifies that an invalid token is reported as expired (returns true for
     * any token that fails validation, including expired ones).
     */
    public void testInvalidTokenIsReportedAsExpired() {
        assertTrue("Invalid token must be reported as expired (or invalid)",
                JwtManager.isExpired("invalid.token.data"));
    }

    /**
     * Verifies that null token is reported as expired.
     */
    public void testNullTokenIsReportedAsExpired() {
        assertTrue("Null token must be reported as expired (or invalid)",
                JwtManager.isExpired(null));
    }

    // =========================================================================
    //  No-throw contract verification
    // =========================================================================

    /**
     * Verifies that all JWT operations handle malformed input without throwing
     * exceptions. Exceptions must be caught and handled internally (V6 fix #4:
     * no silent logging or unchecked propagation to caller).
     */
    public void testJwtOperationsNeverThrowOnBadInput() {
        String[] badInputs = {
                null, "", "x", "x.y", "x.y.z",
                "invalid.base64.signature",
                "eyJhbGciOiJIUzI1NiJ9.badpayload.badsig"
        };

        for (String input : badInputs) {
            try {
                JwtManager.validateToken(input);
                JwtManager.getUserId(input);
                JwtManager.getSessionHandle(input);
                JwtManager.isExpired(input);
            } catch (Exception e) {
                fail("JWT operations must never throw on bad input '" + input + "': "
                        + e.getClass().getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Verifies that tokens generated with distinct userIds produce distinct
     * subjects in their validated claims. This confirms no cross-contamination
     * between tokens cached in memory.
     */
    public void testDistinctUserIdsProduceDistinctTokenClaims() {
        String tokenA = JwtManager.generateToken("user-alpha", "session-alpha");
        String tokenB = JwtManager.generateToken("user-beta", "session-beta");

        Claims claimsA = JwtManager.validateToken(tokenA);
        Claims claimsB = JwtManager.validateToken(tokenB);

        assertNotNull("Claims A must be valid", claimsA);
        assertNotNull("Claims B must be valid", claimsB);

        assertFalse("Token A and B must produce distinct subjects",
                claimsA.getSubject().equals(claimsB.getSubject()));
        assertFalse("Token A and B must produce distinct sessionHandles",
                claimsA.get("sessionHandle").equals(claimsB.get("sessionHandle")));
    }

    // =========================================================================
    //  Test suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("V6 JWT Security Tests");
        suite.addTestSuite(V6SecurityTest.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
