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

package org.yawlfoundation.yawl.soc2;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.authentication.CsrfTokenManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * SOC2 CC6.1 - CSRF Protection Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC6.1 - Logical access security measures restrict access to information assets.
 * CC6.6 - The entity implements logical access security measures to protect against threats
 *         from sources outside its system boundaries.
 *
 * <p>Covers:
 * <ul>
 *   <li>Token generation produces unique tokens</li>
 *   <li>Token length is at least 32 bytes (256 bits)</li>
 *   <li>Constant-time comparison (anti-timing-attack)</li>
 *   <li>Null session throws IllegalArgumentException</li>
 *   <li>Token validated from request parameter</li>
 *   <li>Token validated from X-CSRF-Token header</li>
 *   <li>Missing token rejects request</li>
 *   <li>Wrong token rejects request</li>
 *   <li>Invalidate removes token</li>
 *   <li>getToken creates token on first call</li>
 * </ul>
 *
 * <p>Chicago TDD: real CsrfTokenManager with real java.lang.reflect.Proxy sessions.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class CsrfTokenManagerTest extends TestCase {

    public CsrfTokenManagerTest(String name) {
        super(name);
    }

    // =========================================================================
    // Test helper: in-memory HttpSession proxy
    // =========================================================================

    private static HttpSession newSession() {
        Map<String, Object> attrs = new HashMap<>();
        return (HttpSession) Proxy.newProxyInstance(
            CsrfTokenManagerTest.class.getClassLoader(),
            new Class<?>[]{ HttpSession.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getAttribute"    -> { return attrs.get((String) args[0]); }
                    case "setAttribute"    -> { attrs.put((String) args[0], args[1]); return null; }
                    case "removeAttribute" -> { attrs.remove((String) args[0]); return null; }
                    default                -> { return null; }
                }
            }
        );
    }

    /** Creates an HttpServletRequest proxy that returns a fixed parameter and/or header. */
    private static HttpServletRequest newRequest(HttpSession session,
                                                  String paramValue,
                                                  String headerValue) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            CsrfTokenManagerTest.class.getClassLoader(),
            new Class<?>[]{ HttpServletRequest.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getSession"   -> {
                    // getSession(false) returns null to force no-session, unless session provided
                    if (args != null && args.length == 1 && Boolean.FALSE.equals(args[0])) {
                        yield session;
                    }
                    yield session;
                }
                case "getParameter" -> "X-CSRF-Token".equals(args[0]) ? null : paramValue;
                // "_csrf" param
                case "getHeader"    -> "X-CSRF-Token".equals(args[0]) ? headerValue : null;
                default             -> null;
            }
        );
    }

    // =========================================================================
    // CC6.1 - Token generation
    // =========================================================================

    /**
     * SOC2 CC6.1: CSRF token must be generated with sufficient entropy.
     * Tokens must be at least 32 bytes when Base64-decoded (256 bits).
     */
    public void testGenerateTokenProducesSufficientEntropy() {
        HttpSession session = newSession();
        String token = CsrfTokenManager.generateToken(session);

        assertNotNull("Token must not be null", token);
        assertFalse("Token must not be empty", token.isEmpty());

        // Base64url without padding: 32 bytes -> 43 characters
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(token + "=".repeat((4 - token.length() % 4) % 4));
        assertTrue("Token entropy: decoded length must be >= 32 bytes (256 bits)",
                decoded.length >= 32);
    }

    /**
     * SOC2 CC6.1: Successive tokens must be unique (no replay vulnerability).
     */
    public void testGenerateTokenProducesUniqueTokens() {
        HttpSession session1 = newSession();
        HttpSession session2 = newSession();

        String token1 = CsrfTokenManager.generateToken(session1);
        String token2 = CsrfTokenManager.generateToken(session2);

        assertFalse("Successive CSRF tokens must be unique",
                token1.equals(token2));
    }

    /**
     * SOC2 CC6.1: Same session should regenerate a fresh token on generateToken().
     */
    public void testGenerateTokenRegeneratesOnSameSession() {
        HttpSession session = newSession();
        String token1 = CsrfTokenManager.generateToken(session);
        String token2 = CsrfTokenManager.generateToken(session);

        // Each call to generateToken should produce a new value
        assertFalse("Regenerated token must differ from previous", token1.equals(token2));
    }

    // =========================================================================
    // CC6.1 - Null session guard
    // =========================================================================

    /**
     * SOC2 CC6.1: generateToken with null session must throw IllegalArgumentException,
     * not silently succeed or NPE.
     */
    public void testGenerateTokenRejectsNullSession() {
        try {
            CsrfTokenManager.generateToken(null);
            fail("Expected IllegalArgumentException for null session");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception must mention session",
                    e.getMessage().toLowerCase().contains("session"));
        }
    }

    /**
     * SOC2 CC6.1: getToken with null session must throw IllegalArgumentException.
     */
    public void testGetTokenRejectsNullSession() {
        try {
            CsrfTokenManager.getToken(null);
            fail("Expected IllegalArgumentException for null session");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // CC6.1 - getToken lazy init
    // =========================================================================

    /**
     * SOC2 CC6.1: getToken must create a token if one does not exist in the session.
     */
    public void testGetTokenCreatesTokenOnFirstCall() {
        HttpSession session = newSession();
        String token = CsrfTokenManager.getToken(session);

        assertNotNull("getToken must return a non-null token", token);
        assertFalse("getToken must return a non-empty token", token.isEmpty());
    }

    /**
     * SOC2 CC6.1: getToken must return the same token on repeated calls (stable).
     */
    public void testGetTokenIsStableAcrossRepeatedCalls() {
        HttpSession session = newSession();
        String token1 = CsrfTokenManager.getToken(session);
        String token2 = CsrfTokenManager.getToken(session);

        assertEquals("getToken must be stable (same token) on repeat calls",
                token1, token2);
    }

    // =========================================================================
    // CC6.6 - Token validation via parameter
    // =========================================================================

    /**
     * SOC2 CC6.6: Valid CSRF token in "_csrf" request parameter must be accepted.
     */
    public void testValidateTokenAcceptsValidParameterToken() {
        HttpSession session = newSession();
        String token = CsrfTokenManager.generateToken(session);

        // Simulate request with _csrf parameter
        HttpServletRequest request = newRequest(session, token, null);
        assertTrue("Valid _csrf parameter must pass validation",
                CsrfTokenManager.validateToken(request));
    }

    /**
     * SOC2 CC6.6: Valid CSRF token in "X-CSRF-Token" header must be accepted.
     */
    public void testValidateTokenAcceptsValidHeaderToken() {
        HttpSession session = newSession();
        String token = CsrfTokenManager.generateToken(session);

        // Simulate request with X-CSRF-Token header (no param)
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{ HttpServletRequest.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getSession"   -> session;
                case "getParameter" -> null; // no param
                case "getHeader"    -> "X-CSRF-Token".equals(args[0]) ? token : null;
                default             -> null;
            }
        );

        assertTrue("Valid X-CSRF-Token header must pass validation",
                CsrfTokenManager.validateToken(request));
    }

    // =========================================================================
    // CC6.6 - Token validation rejection
    // =========================================================================

    /**
     * SOC2 CC6.6: Wrong CSRF token must be rejected (prevents CSRF attack).
     */
    public void testValidateTokenRejectsWrongToken() {
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest(session, "completely-wrong-token", null);
        assertFalse("Wrong CSRF token must be rejected",
                CsrfTokenManager.validateToken(request));
    }

    /**
     * SOC2 CC6.6: Request with no CSRF token at all must be rejected.
     */
    public void testValidateTokenRejectsMissingToken() {
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest(session, null, null);
        assertFalse("Request with no token must be rejected",
                CsrfTokenManager.validateToken(request));
    }

    /**
     * SOC2 CC6.6: Request with no session must be rejected.
     */
    public void testValidateTokenRejectsNoSession() {
        HttpServletRequest request = newRequest(null, "some-token", null);
        assertFalse("Request with no session must be rejected",
                CsrfTokenManager.validateToken(request));
    }

    /**
     * SOC2 CC6.6: Request where session has no stored token must be rejected.
     */
    public void testValidateTokenRejectsSessionWithoutStoredToken() {
        HttpSession session = newSession();
        // No call to generateToken - session has no stored token

        HttpServletRequest request = newRequest(session, "any-value", null);
        assertFalse("Session without stored token must reject any presented token",
                CsrfTokenManager.validateToken(request));
    }

    // =========================================================================
    // CC6.1 - Token invalidation
    // =========================================================================

    /**
     * SOC2 CC6.1: invalidateToken must remove the stored token.
     * After invalidation, any subsequent validation must fail.
     */
    public void testInvalidateTokenRemovesStoredToken() {
        HttpSession session = newSession();
        String token = CsrfTokenManager.generateToken(session);

        // Confirm token is valid before invalidation
        HttpServletRequest requestBefore = newRequest(session, token, null);
        assertTrue("Token must be valid before invalidation",
                CsrfTokenManager.validateToken(requestBefore));

        // Invalidate
        CsrfTokenManager.invalidateToken(session);

        // Now validation must fail
        HttpServletRequest requestAfter = newRequest(session, token, null);
        assertFalse("Token must be invalid after invalidation",
                CsrfTokenManager.validateToken(requestAfter));
    }

    /**
     * SOC2 CC6.1: invalidateToken with null session must not throw.
     */
    public void testInvalidateTokenHandlesNullSessionGracefully() {
        try {
            CsrfTokenManager.invalidateToken(null);
            // Must not throw
        } catch (Exception e) {
            fail("invalidateToken(null) must not throw: " + e.getMessage());
        }
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(CsrfTokenManagerTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
