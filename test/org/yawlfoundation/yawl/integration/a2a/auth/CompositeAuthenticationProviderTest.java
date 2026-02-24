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

package org.yawlfoundation.yawl.integration.a2a.auth;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeAuthenticationProviderTest {

    private static final String JWT_SECRET = "jwt-secret-key-32-bytes-long";
    private static final String API_MASTER_KEY = "api-master-key-32-bytes-long";

    private JwtAuthenticationProvider jwtProvider;
    private ApiKeyAuthenticationProvider apiKeyProvider;
    private TestAuthProvider testProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
        apiKeyProvider = new ApiKeyAuthenticationProvider(API_MASTER_KEY);
        testProvider = new TestAuthProvider();

        apiKeyProvider.registerKey("test-key", "test-user", "valid-api-key", Collections.emptySet());
    }

    @Nested
    @DisplayName("Constructor Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConstructorValidation {

        @Test
        @Order(1)
        @DisplayName("constructor_withList_succeeds")
        void constructor_withList_succeeds() {
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            assertNotNull(composite);
            assertEquals("Bearer, ApiKey", composite.scheme());
            assertEquals(2, composite.getProviders().size());
        }

        @Test
        @Order(2)
        @DisplayName("constructor_withEmptyList_throwsIllegalArgumentException")
        void constructor_withEmptyList_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new CompositeAuthenticationProvider(Collections.emptyList());
            });
            assertTrue(e.getMessage().contains("requires at least one provider"));
        }

        @Test
        @Order(3)
        @DisplayName("constructor_withNullList_throwsIllegalArgumentException")
        void constructor_withNullList_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new CompositeAuthenticationProvider(null);
            });
            assertTrue(e.getMessage().contains("providers list must not be null"));
        }

        @Test
        @Order(4)
        @DisplayName("constructor_withListContainingNull_throwsIllegalArgumentException")
        void constructor_withListContainingNull_throwsIllegalArgumentException() {
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, null);
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new CompositeAuthenticationProvider(providers);
            });
            assertTrue(e.getMessage().contains("provider in list must not be null"));
        }

        @Test
        @Order(5)
        @DisplayName("constructor_withVarargs_succeeds")
        void constructor_withVarargs_succeeds() {
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(jwtProvider, apiKeyProvider);

            assertNotNull(composite);
            assertEquals("Bearer, ApiKey", composite.scheme());
            assertEquals(2, composite.getProviders().size());
        }

        @Test
        @Order(6)
        @DisplayName("constructor_withVarargsContainingNull_throwsIllegalArgumentException")
        void constructor_withVarargsContainingNull_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new CompositeAuthenticationProvider(jwtProvider, null);
            });
            assertTrue(e.getMessage().contains("provider in rest must not be null"));
        }

        @Test
        @Order(7)
        @DisplayName("constructor_withNullFirstProvider_throwsIllegalArgumentException")
        void constructor_withNullFirstProvider_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new CompositeAuthenticationProvider((A2AAuthenticationProvider) null, apiKeyProvider);
            });
            assertTrue(e.getMessage().contains("first provider must not be null"));
        }
    }

    @Nested
    @DisplayName("Scheme and CanHandle")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SchemeAndCanHandle {

        @Test
        @Order(1)
        @DisplayName("scheme_returnsCombinedSchemes")
        void scheme_returnsCombinedSchemes() {
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider, testProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            assertEquals("Bearer, ApiKey, Test", composite.scheme());
        }

        @Test
        @Order(2)
        @DisplayName("canHandle_withJwtHeader_returnsTrue")
        void canHandle_withJwtHeader_returnsTrue() throws Exception {
            HttpExchange exchange = createMockExchange("Authorization", "Bearer jwt-token");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            assertTrue(composite.canHandle(exchange));
        }

        @Test
        @Order(3)
        @DisplayName("canHandle_withApiKeyHeader_returnsTrue")
        void canHandle_withApiKeyHeader_returnsTrue() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "api-key");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            assertTrue(composite.canHandle(exchange));
        }

        @Test
        @Order(4)
        @DisplayName("canHandle_withTestHeader_returnsTrue")
        void canHandle_withTestHeader_returnsTrue() throws Exception {
            HttpExchange exchange = createMockExchange("X-Test-Auth", "test-value");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, testProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            assertTrue(composite.canHandle(exchange));
        }

        @Test
        @Order(5)
        @DisplayName("canHandle_withNoSupportedHeaders_returnsFalse")
        void canHandle_withNoSupportedHeaders_returnsFalse() throws Exception {
            HttpExchange exchange = createMockExchange("Other-Header", "value");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            assertFalse(composite.canHandle(exchange));
        }

        @Test
        @Order(6)
        @DisplayName("canHandle_withMultipleHeaders_returnsTrueForAny")
        void canHandle_withMultipleHeaders_returnsTrueForAny() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "api-key", "Authorization", "Bearer jwt");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            assertTrue(composite.canHandle(exchange)); // Either header should make it true
        }
    }

    @Nested
    @DisplayName("Authentication Success")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AuthenticationSuccess {

        @Test
        @Order(1)
        @DisplayName("authenticate_jwtProvider_succeeds")
        void authenticate_jwtProvider_succeeds() throws Exception {
            String token = jwtProvider.issueToken("jwt-user", List.of("workflow:launch"), 3600000);
            HttpExchange exchange = createMockExchange("Authorization", "Bearer " + token);
            List<A2AAuthenticationProvider> providers = List.of(apiKeyProvider, jwtProvider); // JWT second
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            AuthenticatedPrincipal principal = composite.authenticate(exchange);

            assertEquals("jwt-user", principal.getUsername());
            assertTrue(principal.hasPermission("workflow:launch"));
            assertEquals("Bearer", principal.getAuthScheme());
        }

        @Test
        @Order(2)
        @DisplayName("authenticate_apiKeyProvider_succeeds")
        void authenticate_apiKeyProvider_succeeds() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "valid-api-key");
            List<A2AAuthenticationProvider> providers = List.of(testProvider, apiKeyProvider); // API key second
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            AuthenticatedPrincipal principal = composite.authenticate(exchange);

            assertEquals("test-user", principal.getUsername());
            assertTrue(principal.hasPermission("workflow:launch"));
            assertEquals("ApiKey", principal.getAuthScheme());
        }

        @Test
        @Order(3)
        @DisplayName("authenticate_firstProviderSucceeds_stopsChain")
        void authenticate_firstProviderSucceeds_stopsChain() throws Exception {
            HttpExchange exchange = createMockExchange("Authorization", "Bearer valid-token");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, testProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            // Mock test provider to track if authenticate was called
            TestAuthenticationTracker tracker = new TestAuthenticationTracker();
            testProvider.setTracker(tracker);

            AuthenticatedPrincipal principal = composite.authenticate(exchange);

            assertEquals("jwt-user", principal.getUsername()); // From JWT provider
            assertFalse(tracker.wasCalled()); // Test provider should not have been called
        }
    }

    @Nested
    @DisplayName("Authentication Failure")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AuthenticationFailure {

        @Test
        @Order(1)
        @DisplayName("authenticate_jwtProviderFails_apiKeySucceeds")
        void authenticate_jwtProviderFails_apiKeySucceeds() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "valid-api-key");
            List<A2AAuthenticationProvider> providers = List.of(createFailingProvider(), apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            AuthenticatedPrincipal principal = composite.authenticate(exchange);

            assertEquals("test-user", principal.getUsername());
        }

        @Test
        @Order(2)
        @DisplayName("authenticate_allProvidersFail_throwsA2AAuthenticationException")
        void authenticate_allProvidersFail_throwsA2AAuthenticationException() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "invalid-key");
            List<A2AAuthenticationProvider> providers = List.of(createFailingProvider(), testProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                composite.authenticate(exchange);
            });

            assertEquals("Bearer, Test", e.getSupportedSchemes());
            assertTrue(e.getMessage().contains("No authentication credentials found"));
        }

        @Test
        @Order(3)
        @DisplayName("authenticate_noProvidersCanHandle_throwsA2AAuthenticationException")
        void authenticate_noProvidersCanHandle_throwsA2AAuthenticationException() throws Exception {
            HttpExchange exchange = createMockExchange("Other-Header", "value");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                composite.authenticate(exchange);
            });

            assertEquals("Bearer, ApiKey", e.getSupportedSchemes());
            assertTrue(e.getMessage().contains("No authentication credentials found"));
        }

        @Test
        @Order(4)
        @DisplayName("authenticate_providerRejectsWithDetails_includesDetails")
        void authenticate_providerRejectsWithDetails_includesDetails() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "invalid-key");
            FailingProvider failingProvider = new FailingProvider("Invalid API format");
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, failingProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                composite.authenticate(exchange);
            });

            // The exception should include the failure reason from the last provider that handled it
            assertTrue(e.getMessage().contains("Invalid API format"));
        }
    }

    @Nested
    @DisplayName("Production Factory")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ProductionFactory {

        @Test
        @Order(1)
        @DisplayName("production_withoutEnvironment_throwsIllegalStateException")
        void production_withoutEnvironment_throwsIllegalStateException() {
            // Clear environment
            System.clearProperty("A2A_JWT_SECRET");
            System.clearProperty("A2A_API_KEY_MASTER");
            System.clearProperty("A2A_SPIFFE_TRUST_DOMAIN");

            Exception e = assertThrows(IllegalStateException.class, () -> {
                CompositeAuthenticationProvider.production();
            });

            assertTrue(e.getMessage().contains("No authentication providers could be configured"));
            assertTrue(e.getMessage().contains("A2A_JWT_SECRET"));
            assertTrue(e.getMessage().contains("A2A_API_KEY_MASTER"));
            assertTrue(e.getMessage().contains("A2A_SPIFFE_TRUST_DOMAIN"));
        }

        @Test
        @Order(2)
        @DisplayName("production_withJwtSecret_onlyIncludesJwtProvider")
        void production_withJwtSecret_onlyIncludesJwtProvider() {
            // Set only JWT secret
            System.setProperty("A2A_JWT_SECRET", JWT_SECRET);
            System.clearProperty("A2A_API_KEY_MASTER");
            System.clearProperty("A2A_SPIFFE_TRUST_DOMAIN");

            CompositeAuthenticationProvider composite = CompositeAuthenticationProvider.production();

            assertEquals(1, composite.getProviders().size());
            assertTrue(composite.getProviders().get(0) instanceof JwtAuthenticationProvider);

            System.clearProperty("A2A_JWT_SECRET");
        }

        @Test
        @Order(3)
        @DisplayName("production_withAllEnvironmentVariables_includesAllProviders")
        void production_withAllEnvironmentVariables_includesAllProviders() {
            // Set all environment variables
            System.setProperty("A2A_JWT_SECRET", JWT_SECRET);
            System.setProperty("A2A_API_KEY_MASTER", API_MASTER_KEY);
            System.setProperty("A2A_SPIFFE_TRUST_DOMAIN", "example.com");
            System.setProperty("A2A_API_KEY", "test-api-key");

            CompositeAuthenticationProvider composite = CompositeAuthenticationProvider.production();

            // Should include at least JWT, API Key, and Handoff providers
            // SPIFFE might be included if environment supports it
            assertTrue(composite.getProviders().size() >= 3);

            // Verify the types of providers
            List<Class<?>> providerTypes = composite.getProviders().stream()
                .map(Object::getClass)
                .toList();

            assertTrue(providerTypes.contains(JwtAuthenticationProvider.class));
            assertTrue(providerTypes.contains(ApiKeyAuthenticationProvider.class));

            // Clear environment
            System.clearProperty("A2A_JWT_SECRET");
            System.clearProperty("A2A_API_KEY_MASTER");
            System.clearProperty("A2A_SPIFFE_TRUST_DOMAIN");
            System.clearProperty("A2A_API_KEY");
        }

        @Test
        @Order(4)
        @DisplayName("production_reportsConfigurationErrors")
        void production_reportsConfigurationErrors() {
            // Set invalid JWT secret
            System.setProperty("A2A_JWT_SECRET", "short");
            System.setProperty("A2A_SPIFFE_TRUST_DOMAIN", "example.com");

            Exception e = assertThrows(IllegalStateException.class, () -> {
                CompositeAuthenticationProvider.production();
            });

            assertTrue(e.getMessage().contains("Provider errors:"));
            assertTrue(e.getMessage().contains("JwtAuthenticationProvider: A2A_JWT_SECRET must be at least 32 bytes"));

            // Clear environment
            System.clearProperty("A2A_JWT_SECRET");
            System.clearProperty("A2A_SPIFFE_TRUST_DOMAIN");
        }
    }

    @Nested
    @DisplayName("Provider Order")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ProviderOrder {

        @Test
        @Order(1)
        @DisplayName("authentication_usesRegistrationOrder")
        void authentication_usesRegistrationOrder() throws Exception {
            String token = jwtProvider.issueToken("jwt-user", List.of("workflow:launch"), 3600000);
            HttpExchange exchange = createMockExchange("Authorization", "Bearer " + token);

            // Create a composite where test provider comes first
            List<A2AAuthenticationProvider> providers = List.of(testProvider, jwtProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            // Mock test provider to return false for authentication
            TestAuthenticationFailing failingProvider = new TestAuthenticationFailing();
            testProvider.setFailing(failingProvider);

            AuthenticatedPrincipal principal = composite.authenticate(exchange);

            // Should succeed with JWT provider since test provider fails
            assertEquals("jwt-user", principal.getUsername());
            assertTrue(principal.hasPermission("workflow:launch"));
        }

        @Test
        @Order(2)
        @DisplayName("authentication_preferOverFail")
        void authentication_preferOverFail() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "valid-api-key");

            // Create a composite where failing provider comes first
            List<A2AAuthenticationProvider> providers = List.of(createFailingProvider(), apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            AuthenticatedPrincipal principal = composite.authenticate(exchange);

            // Should succeed with API key provider since first provider fails
            assertEquals("test-user", principal.getUsername());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EdgeCases {

        @Test
        @Order(1)
        @DisplayName("authenticate_withSameProviderType_stillWorks")
        void authenticate_withSameProviderType_stillWorks() throws Exception {
            // Create two JWT providers with different secrets
            JwtAuthenticationProvider jwt1 = new JwtAuthenticationProvider("secret1", null);
            JwtAuthenticationProvider jwt2 = new JwtAuthenticationProvider("secret2", null);

            String token = jwt1.issueToken("user1", List.of("workflow:launch"), 3600000);
            HttpExchange exchange = createMockExchange("Authorization", "Bearer " + token);

            List<A2AAuthenticationProvider> providers = List.of(jwt2, jwt1); // Reverse order
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            AuthenticatedPrincipal principal = composite.authenticate(exchange);

            assertEquals("user1", principal.getUsername());
            assertTrue(principal.hasPermission("workflow:launch"));
        }

        @Test
        @Order(2)
        @DisplayName("getProviders_returnsImmutableList")
        void getProviders_returnsImmutableList() {
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            List<A2AAuthenticationProvider> returned = composite.getProviders();

            assertThrows(UnsupportedOperationException.class, () -> {
                returned.add(testProvider);
            });
        }

        @Test
        @Order(3)
        @DisplayName("authenticate_withNullExchange_throwsNullPointerException")
        void authenticate_withNullExchange_throwsNullPointerException() throws Exception {
            List<A2AAuthenticationProvider> providers = List.of(jwtProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            Exception e = assertThrows(NullPointerException.class, () -> {
                composite.authenticate(null);
            });
        }
    }

    // Helper classes for testing

    private static class TestAuthProvider implements A2AAuthenticationProvider {
        private TestAuthenticationTracker tracker = new TestAuthenticationTracker();
        private TestAuthenticationFailing failing = null;

        public void setTracker(TestAuthenticationTracker tracker) {
            this.tracker = tracker;
        }

        public void setFailing(TestAuthenticationFailing failing) {
            this.failing = failing;
        }

        @Override
        public AuthenticatedPrincipal authenticate(HttpExchange exchange) throws A2AAuthenticationException {
            if (tracker != null) {
                tracker.called();
            }

            if (failing != null) {
                throw failing.exception();
            }

            return new AuthenticatedPrincipal(
                "test-user",
                Set.of("workflow:launch"),
                "Test",
                java.time.Instant.now(),
                null
            );
        }

        @Override
        public String scheme() {
            return "Test";
        }

        @Override
        public boolean canHandle(HttpExchange exchange) {
            Headers headers = exchange.getRequestHeaders();
            return headers.getFirst("X-Test-Auth") != null;
        }
    }

    private static class TestAuthenticationTracker {
        private boolean called = false;

        public void called() {
            this.called = true;
        }

        public boolean wasCalled() {
            return called;
        }
    }

    private static class TestAuthenticationFailing {
        public A2AAuthenticationException exception() {
            return new A2AAuthenticationException("Test provider failed", "Test");
        }
    }

    private static class FailingProvider implements A2AAuthenticationProvider {
        private final String failureMessage;

        public FailingProvider(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        @Override
        public AuthenticatedPrincipal authenticate(HttpExchange exchange) throws A2AAuthenticationException {
            throw new A2AAuthenticationException(failureMessage, "Test");
        }

        @Override
        public String scheme() {
            return "Test";
        }

        @Override
        public boolean canHandle(HttpExchange exchange) {
            return true; // Claims to handle everything but always fails
        }
    }

    private A2AAuthenticationProvider createFailingProvider() {
        return new FailingProvider("Authentication failed");
    }

    // Helper method to create mock HttpExchange for testing
    private HttpExchange createMockExchange() throws Exception {
        return createMockExchange();
    }

    private HttpExchange createMockExchange(String headerName, String headerValue) throws Exception {
        return createMockExchange(headerName, headerValue, null, null);
    }

    private HttpExchange createMockExchange(String headerName1, String headerValue1,
                                         String headerName2, String headerValue2) throws Exception {
        // Create a real HttpExchange implementation for testing
        HttpExchange exchange = new HttpExchange() {
            private Headers headers = new Headers();

            @Override
            public String getRequestMethod() {
                return "GET";
            }

            @Override
            public URI getRequestURI() {
                return URI.create("/test");
            }

            @Override
            public String getProtocol() {
                return "HTTP/1.1";
            }

            @Override
            public Headers getRequestHeaders() {
                return headers;
            }

            @Override
            public Headers getResponseHeaders() {
                return new Headers();
            }

            @Override
            public java.net.InetSocketAddress getRemoteAddress() {
                return new java.net.InetSocketAddress("127.0.0.1", 8080);
            }

            @Override
            public java.net.InetSocketAddress getLocalAddress() {
                return new java.net.InetSocketAddress("127.0.0.1", 80);
            }

            @Override
            public java.io.InputStream getRequestBody() {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }

            @Override
            public java.io.OutputStream getResponseBody() {
                return new java.io.ByteArrayOutputStream();
            }

            @Override
            public void sendResponseHeaders(int rCode, long responseLength) {
                // No-op for testing
            }

            @Override
            public java.io.Closeable getCloseable() {
                return () -> {}; // No-op for testing
            }
        };

        if (headerName1 != null && headerValue1 != null) {
            headers.add(headerName1, headerValue1);
        }
        if (headerName2 != null && headerValue2 != null) {
            headers.add(headerName2, headerValue2);
        }

        return exchange;
    }
}