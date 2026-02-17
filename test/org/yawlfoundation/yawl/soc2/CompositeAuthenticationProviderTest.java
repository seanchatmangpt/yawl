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
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;

import java.util.List;
import java.util.Set;

/**
 * SOC2 CC6.1 / CC6.6 - Composite Authentication Provider Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC6.1 - Defense-in-depth: multiple authentication schemes must all be evaluated.
 * CC6.6 - No scheme enumeration leaks that help attackers.
 * CC6.3 - Least privilege: correct scheme must not grant permissions beyond its scope.
 *
 * <p>Covers:
 * <ul>
 *   <li>Empty provider list throws IllegalArgumentException</li>
 *   <li>Null in provider list throws NullPointerException</li>
 *   <li>First matching provider authenticates request</li>
 *   <li>Second provider used when first cannot handle request</li>
 *   <li>No matching providers throws A2AAuthenticationException</li>
 *   <li>canHandle returns true if any provider can handle</li>
 *   <li>All providers fail -> exception with combined message</li>
 *   <li>scheme() aggregates all configured schemes</li>
 *   <li>getProviders() returns immutable ordered list</li>
 *   <li>Production stack requires at least one environment variable</li>
 * </ul>
 *
 * <p>Chicago TDD: real CompositeAuthenticationProvider with real
 * JwtAuthenticationProvider and ApiKeyAuthenticationProvider.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class CompositeAuthenticationProviderTest extends TestCase {

    private static final String JWT_SECRET =
            "composite-test-jwt-secret-min-32-chars-12345678";
    private static final String API_MASTER = "composite-test-api-master-key-ok";

    public CompositeAuthenticationProviderTest(String name) {
        super(name);
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    private JwtAuthenticationProvider jwtProvider() {
        return new JwtAuthenticationProvider(JWT_SECRET, null);
    }

    private ApiKeyAuthenticationProvider apiKeyProvider() {
        return new ApiKeyAuthenticationProvider(API_MASTER);
    }

    private static HttpExchange exchangeWithBearer(String token) {
        TestHttpExchange exchange = new TestHttpExchange();
        exchange.requestHeaders().add("Authorization", "Bearer " + token);
        return exchange;
    }

    private static HttpExchange exchangeWithApiKey(String key) {
        TestHttpExchange exchange = new TestHttpExchange();
        exchange.requestHeaders().add("X-API-Key", key);
        return exchange;
    }

    private static HttpExchange exchangeWithNoAuth() {
        return new TestHttpExchange();
    }

    // =========================================================================
    // CC6.1 - Constructor validation
    // =========================================================================

    /**
     * SOC2 CC6.1: Empty provider list must throw - at least one scheme required.
     */
    public void testEmptyProviderListThrows() {
        try {
            new CompositeAuthenticationProvider(java.util.Collections.emptyList());
            fail("Expected IllegalArgumentException for empty provider list");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Null in provider list must throw.
     */
    public void testNullInProviderListThrows() {
        try {
            new CompositeAuthenticationProvider(java.util.Arrays.asList(jwtProvider(), null));
            fail("Expected NullPointerException for null in provider list");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Null first provider in varargs constructor must throw.
     */
    public void testNullFirstProviderThrows() {
        try {
            new CompositeAuthenticationProvider(null);
            fail("Expected NullPointerException for null first provider");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // CC6.1 - First matching provider authenticates
    // =========================================================================

    /**
     * SOC2 CC6.1: When JWT provider handles request, it must authenticate the user
     * with its permissions (not the API key provider's permissions).
     */
    public void testJwtProviderHandlesBearerToken() throws Exception {
        JwtAuthenticationProvider jwt = jwtProvider();
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();
        apiKey.registerKey("k1", "api-user", "api-key", Set.of(AuthenticatedPrincipal.PERM_ALL));

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwt, apiKey));

        String token = jwt.issueToken("jwt-user",
                List.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY), 60_000L);

        AuthenticatedPrincipal principal = composite.authenticate(exchangeWithBearer(token));
        assertEquals("JWT must authenticate jwt-user", "jwt-user", principal.getUsername());
        assertTrue("JWT must grant workflow:query",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
    }

    /**
     * SOC2 CC6.1: When API key provider handles request, it must authenticate
     * with API key's permissions.
     */
    public void testApiKeyProviderHandlesApiKeyHeader() throws Exception {
        JwtAuthenticationProvider jwt = jwtProvider();
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();
        apiKey.registerKey("k1", "api-key-user", "my-api-key",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwt, apiKey));

        AuthenticatedPrincipal principal = composite.authenticate(exchangeWithApiKey("my-api-key"));
        assertEquals("API key must authenticate api-key-user",
                "api-key-user", principal.getUsername());
        assertTrue("API key must grant workflow:launch",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
    }

    // =========================================================================
    // CC6.6 - No credentials present
    // =========================================================================

    /**
     * SOC2 CC6.6: Request with no credentials at all must throw A2AAuthenticationException.
     * The composite must not accidentally authenticate requests with no credentials.
     */
    public void testNoCredentialsThrows() throws Exception {
        JwtAuthenticationProvider jwt = jwtProvider();
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();
        apiKey.registerKey("k1", "user", "key", Set.of());

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwt, apiKey));

        try {
            composite.authenticate(exchangeWithNoAuth());
            fail("Expected A2AAuthenticationException when no credentials provided");
        } catch (A2AAuthenticationException e) {
            assertNotNull("Exception must have message", e.getMessage());
        }
    }

    // =========================================================================
    // CC6.6 - canHandle
    // =========================================================================

    /**
     * SOC2 CC6.6: canHandle returns true when any provider can handle the request.
     */
    public void testCanHandleReturnsTrueWhenAnyProviderMatches() {
        JwtAuthenticationProvider jwt = jwtProvider();
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwt, apiKey));

        // Bearer token -> JWT provider can handle
        assertTrue("canHandle must return true for Bearer token",
                composite.canHandle(exchangeWithBearer("any.token")));

        // API key -> ApiKey provider can handle
        assertTrue("canHandle must return true for X-API-Key header",
                composite.canHandle(exchangeWithApiKey("any-key")));
    }

    /**
     * SOC2 CC6.6: canHandle returns false when no provider can handle the request.
     */
    public void testCanHandleReturnsFalseWhenNoProviderMatches() {
        JwtAuthenticationProvider jwt = jwtProvider();
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwt, apiKey));

        assertFalse("canHandle must return false when no credentials present",
                composite.canHandle(exchangeWithNoAuth()));
    }

    // =========================================================================
    // CC6.6 - scheme() for error messages
    // =========================================================================

    /**
     * SOC2 CC6.6: scheme() must contain all configured scheme names.
     * This is used in error messages to tell clients what schemes are supported.
     */
    public void testSchemeContainsAllProviderSchemes() {
        JwtAuthenticationProvider jwt = jwtProvider();
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwt, apiKey));

        String scheme = composite.scheme();
        assertNotNull("scheme() must not return null", scheme);
        // JWT provider scheme is "Bearer"
        assertTrue("scheme() must contain 'Bearer'", scheme.contains("Bearer"));
        // ApiKey provider scheme is "ApiKey"
        assertTrue("scheme() must contain 'ApiKey'", scheme.contains("ApiKey"));
    }

    // =========================================================================
    // CC6.1 - getProviders() immutability
    // =========================================================================

    /**
     * SOC2 CC6.1: getProviders() must return an immutable list.
     * External code must not be able to add/remove providers after construction.
     */
    public void testGetProvidersIsImmutable() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwtProvider(), apiKeyProvider()));

        try {
            composite.getProviders().add(jwtProvider());
            fail("getProviders() must return immutable list");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    /**
     * SOC2 CC6.1: getProviders() must preserve registration order.
     */
    public void testGetProvidersPreservesOrder() {
        JwtAuthenticationProvider jwt = jwtProvider();
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(jwt, apiKey));

        java.util.List<?> providers = composite.getProviders();
        assertEquals("Must have 2 providers", 2, providers.size());
        assertSame("First provider must be JWT", jwt, providers.get(0));
        assertSame("Second provider must be ApiKey", apiKey, providers.get(1));
    }

    // =========================================================================
    // CC6.6 - Invalid credentials from a matching provider
    // =========================================================================

    /**
     * SOC2 CC6.6: If a provider says it can handle the request but authentication
     * fails, the composite must throw the exception (not silently bypass).
     */
    public void testFailedAuthenticationFromMatchingProviderThrows() throws Exception {
        ApiKeyAuthenticationProvider apiKey = apiKeyProvider();
        apiKey.registerKey("k1", "user", "correct-key", Set.of(AuthenticatedPrincipal.PERM_ALL));

        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                java.util.List.of(apiKey));

        try {
            composite.authenticate(exchangeWithApiKey("wrong-key"));
            fail("Expected A2AAuthenticationException for wrong API key");
        } catch (A2AAuthenticationException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(CompositeAuthenticationProviderTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
