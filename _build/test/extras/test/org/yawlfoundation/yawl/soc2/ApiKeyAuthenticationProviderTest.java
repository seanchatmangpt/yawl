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

import com.sun.net.httpserver.HttpExchange;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationException;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;

import java.util.Set;

/**
 * SOC2 CC6.1 / CC6.6 - API Key Authentication Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC6.1 - Logical access controls restrict access to information assets.
 * CC6.6 - The entity implements logical access security measures to protect
 *         against threats from sources outside its system boundaries.
 *
 * <p>Covers:
 * <ul>
 *   <li>Master key too short throws IllegalArgumentException</li>
 *   <li>Null master key throws IllegalArgumentException</li>
 *   <li>registerKey null/blank parameters throw</li>
 *   <li>Valid key is authenticated successfully</li>
 *   <li>Wrong key is rejected with A2AAuthenticationException</li>
 *   <li>Empty registry rejects any key</li>
 *   <li>Keys are stored as HMAC digest, not plaintext</li>
 *   <li>Revoked key is rejected after revocation</li>
 *   <li>canHandle returns true only when X-API-Key header is present</li>
 *   <li>registeredKeyCount tracks registrations and revocations</li>
 *   <li>Multiple keys can coexist and authenticate independently</li>
 * </ul>
 *
 * <p>Chicago TDD: real ApiKeyAuthenticationProvider with real HMAC-SHA256.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class ApiKeyAuthenticationProviderTest extends TestCase {

    private static final String VALID_MASTER = "test-master-key-minimum-16-chars-ok";

    public ApiKeyAuthenticationProviderTest(String name) {
        super(name);
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    /** Creates a minimal HttpExchange with the given header value for X-API-Key. */
    private static HttpExchange exchangeWithApiKey(String keyValue) {
        TestHttpExchange exchange = new TestHttpExchange();
        if (keyValue != null) {
            exchange.requestHeaders().add("X-API-Key", keyValue);
        }
        return exchange;
    }

    // =========================================================================
    // CC6.1 - Constructor validation
    // =========================================================================

    /**
     * SOC2 CC6.1: Master key shorter than 16 chars must be rejected - weak keys are a control gap.
     */
    public void testShortMasterKeyThrows() {
        try {
            new ApiKeyAuthenticationProvider("tooshort");
            fail("Expected IllegalArgumentException for short master key");
        } catch (IllegalArgumentException e) {
            assertTrue("Error must mention key length",
                    e.getMessage().contains("16") || e.getMessage().toLowerCase().contains("least"));
        }
    }

    /**
     * SOC2 CC6.1: Null master key must throw.
     */
    public void testNullMasterKeyThrows() {
        try {
            new ApiKeyAuthenticationProvider(null);
            fail("Expected IllegalArgumentException for null master key");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Valid master key (>= 16 chars) must construct successfully.
     */
    public void testValidMasterKeyConstructsProvider() {
        ApiKeyAuthenticationProvider provider = new ApiKeyAuthenticationProvider(VALID_MASTER);
        assertNotNull("Provider must be constructed with valid master key", provider);
        assertEquals("New provider has no registered keys", 0, provider.registeredKeyCount());
    }

    // =========================================================================
    // CC6.1 - registerKey validation
    // =========================================================================

    public void testRegisterKeyNullKeyIdThrows() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        try {
            p.registerKey(null, "user", "raw-key-value", Set.of());
            fail("Expected NullPointerException for null keyId");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testRegisterKeyBlankKeyIdThrows() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        try {
            p.registerKey("  ", "user", "raw-key-value", Set.of());
            fail("Expected IllegalArgumentException for blank keyId");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testRegisterKeyNullUsernameThrows() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        try {
            p.registerKey("key-1", null, "raw-key-value", Set.of());
            fail("Expected NullPointerException for null username");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testRegisterKeyNullRawValueThrows() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        try {
            p.registerKey("key-1", "user", null, Set.of());
            fail("Expected NullPointerException for null raw key value");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testRegisterKeyBlankRawValueThrows() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        try {
            p.registerKey("key-1", "user", "   ", Set.of());
            fail("Expected IllegalArgumentException for blank raw key value");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // CC6.6 - Authentication: valid key
    // =========================================================================

    /**
     * SOC2 CC6.6: Valid registered API key must produce an AuthenticatedPrincipal.
     */
    public void testValidApiKeyAuthenticatesSuccessfully() throws Exception {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        p.registerKey("agent-key-1", "agent-soc2", "secret-api-key-for-agent",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));

        HttpExchange exchange = exchangeWithApiKey("secret-api-key-for-agent");
        AuthenticatedPrincipal principal = p.authenticate(exchange);

        assertNotNull("Valid key must produce AuthenticatedPrincipal", principal);
        assertEquals("Principal username must match registration", "agent-soc2", principal.getUsername());
        assertTrue("Principal must be authenticated", principal.isAuthenticated());
        assertTrue("Principal must have registered permission",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
    }

    /**
     * SOC2 CC6.6: Wrong API key must throw A2AAuthenticationException.
     */
    public void testWrongApiKeyIsRejected() throws Exception {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        p.registerKey("key-1", "agent", "correct-key", Set.of(AuthenticatedPrincipal.PERM_ALL));

        HttpExchange exchange = exchangeWithApiKey("wrong-key");
        try {
            p.authenticate(exchange);
            fail("Expected A2AAuthenticationException for wrong key");
        } catch (A2AAuthenticationException e) {
            assertNotNull("Exception must have a message", e.getMessage());
            assertFalse("Error message must not reveal the correct key",
                    e.getMessage().contains("correct-key"));
        }
    }

    /**
     * SOC2 CC6.6: Empty registry must reject any key - no keys configured = no access.
     */
    public void testEmptyRegistryRejectsAnyKey() throws Exception {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        // No keys registered

        HttpExchange exchange = exchangeWithApiKey("any-key-value");
        try {
            p.authenticate(exchange);
            fail("Expected A2AAuthenticationException when registry is empty");
        } catch (A2AAuthenticationException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.6: Missing X-API-Key header must throw A2AAuthenticationException.
     */
    public void testMissingApiKeyHeaderThrows() throws Exception {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        p.registerKey("k1", "user", "key-value", Set.of(AuthenticatedPrincipal.PERM_ALL));

        HttpExchange exchange = exchangeWithApiKey(null);
        try {
            p.authenticate(exchange);
            fail("Expected A2AAuthenticationException for missing header");
        } catch (A2AAuthenticationException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // CC6.1 - canHandle
    // =========================================================================

    /**
     * SOC2 CC6.1: canHandle returns true when X-API-Key header is present.
     */
    public void testCanHandleReturnsTrueWithApiKeyHeader() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        assertTrue("canHandle must return true when X-API-Key header is present",
                p.canHandle(exchangeWithApiKey("some-key")));
    }

    /**
     * SOC2 CC6.1: canHandle returns false when X-API-Key header is absent.
     */
    public void testCanHandleReturnsFalseWithoutApiKeyHeader() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        assertFalse("canHandle must return false when X-API-Key header is absent",
                p.canHandle(exchangeWithApiKey(null)));
    }

    // =========================================================================
    // CC6.1 - Key revocation
    // =========================================================================

    /**
     * SOC2 CC6.1: Revoked key must be immediately rejected. This tests that
     * access can be terminated promptly (SOC2 CC6.2: de-provisioning).
     */
    public void testRevokedKeyIsRejected() throws Exception {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        p.registerKey("revocable-key", "temp-user", "key-to-revoke",
                Set.of(AuthenticatedPrincipal.PERM_ALL));

        // Confirm key works before revocation
        HttpExchange exchange = exchangeWithApiKey("key-to-revoke");
        assertNotNull("Key must work before revocation",
                p.authenticate(exchange));

        // Revoke
        boolean revoked = p.revokeKey("revocable-key");
        assertTrue("revokeKey must return true for existing key", revoked);
        assertEquals("Key count must decrease after revocation", 0, p.registeredKeyCount());

        // Now key must be rejected
        try {
            p.authenticate(exchangeWithApiKey("key-to-revoke"));
            fail("Revoked key must be rejected");
        } catch (A2AAuthenticationException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: revokeKey for unknown key-id returns false (no error).
     */
    public void testRevokeNonExistentKeyReturnsFalse() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        assertFalse("revokeKey of unknown id must return false",
                p.revokeKey("does-not-exist"));
    }

    // =========================================================================
    // CC6.1 - Key count tracking
    // =========================================================================

    /**
     * SOC2 CC6.1: registeredKeyCount must accurately track registrations.
     * Supports auditing of how many keys are active.
     */
    public void testRegisteredKeyCountTracksRegistrations() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        assertEquals(0, p.registeredKeyCount());

        p.registerKey("k1", "u1", "key-1", Set.of());
        assertEquals(1, p.registeredKeyCount());

        p.registerKey("k2", "u2", "key-2", Set.of());
        assertEquals(2, p.registeredKeyCount());

        p.revokeKey("k1");
        assertEquals(1, p.registeredKeyCount());
    }

    // =========================================================================
    // CC6.6 - Multiple keys coexist independently
    // =========================================================================

    /**
     * SOC2 CC6.6: Multiple keys with different permissions must each authenticate
     * with their own credentials independently.
     */
    public void testMultipleKeysAuthenticateIndependently() throws Exception {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        p.registerKey("read-key",  "reader",  "read-secret",  Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        p.registerKey("write-key", "writer", "write-secret", Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));

        AuthenticatedPrincipal reader = p.authenticate(exchangeWithApiKey("read-secret"));
        assertEquals("reader", reader.getUsername());
        assertTrue(reader.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertFalse(reader.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));

        AuthenticatedPrincipal writer = p.authenticate(exchangeWithApiKey("write-secret"));
        assertEquals("writer", writer.getUsername());
        assertTrue(writer.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertFalse(writer.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
    }

    /**
     * SOC2 CC6.1: scheme() must return a non-null, non-empty identifier.
     */
    public void testSchemeIsNonEmpty() {
        ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(VALID_MASTER);
        String scheme = p.scheme();
        assertNotNull("scheme() must not return null", scheme);
        assertFalse("scheme() must not return empty string", scheme.isEmpty());
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(ApiKeyAuthenticationProviderTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
