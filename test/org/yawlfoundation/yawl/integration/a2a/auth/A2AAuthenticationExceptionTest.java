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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A2AAuthenticationExceptionTest {

    private static final String TEST_REASON = "Authentication failed";
    private static final String TEST_SCHEMES = "Bearer, ApiKey, Test";
    private static final Throwable TEST_CAUSE = new RuntimeException("Network error");

    @Nested
    @DisplayName("Constructor Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConstructorValidation {

        @Test
        @Order(1)
        @DisplayName("constructor_withValidParameters_succeeds")
        void constructor_withValidParameters_succeeds() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);

            assertEquals(TEST_REASON, exception.getMessage());
            assertEquals(TEST_SCHEMES, exception.getSupportedSchemes());
            assertNull(exception.getCause());
        }

        @Test
        @Order(2)
        @DisplayName("constructor_withNullSupportedSchemes_defaultsToBearer")
        void constructor_withNullSupportedSchemes_defaultsToBearer() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, null);

            assertEquals(TEST_REASON, exception.getMessage());
            assertEquals("Bearer", exception.getSupportedSchemes());
        }

        @Test
        @Order(3)
        @DisplayName("constructor_withBlankSupportedSchemes_defaultsToBearer")
        void constructor_withBlankSupportedSchemes_defaultsToBearer() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, "   ");

            assertEquals(TEST_REASON, exception.getMessage());
            assertEquals("Bearer", exception.getSupportedSchemes());
        }

        @Test
        @Order(4)
        @DisplayName("constructor_withValidParametersAndCause_succeeds")
        void constructor_withValidParametersAndCause_succeeds() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES, TEST_CAUSE);

            assertEquals(TEST_REASON, exception.getMessage());
            assertEquals(TEST_SCHEMES, exception.getSupportedSchemes());
            assertEquals(TEST_CAUSE, exception.getCause());
        }

        @Test
        @Order(5)
        @DisplayName("constructor_withCauseButNullScheme_defaultsToBearer")
        void constructor_withCauseButNullScheme_defaultsToBearer() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, null, TEST_CAUSE);

            assertEquals(TEST_REASON, exception.getMessage());
            assertEquals("Bearer", exception.getSupportedSchemes());
            assertEquals(TEST_CAUSE, exception.getCause());
        }
    }

    @Nested
    @DisplayName("GetSupportedSchemes")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetSupportedSchemes {

        @Test
        @Order(1)
        @DisplayName("getSupportedSchemes_withValidSchemes_returnsCorrectSchemes")
        void getSupportedSchemes_withValidSchemes_returnsCorrectSchemes() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            assertEquals(TEST_SCHEMES, exception.getSupportedSchemes());
        }

        @Test
        @Order(2)
        @DisplayName("getSupportedSchemes_neverReturnsNull")
        void getSupportedSchemes_neverReturnsNull() {
            A2AAuthenticationException exception1 = new A2AAuthenticationException(TEST_REASON, null);
            A2AAuthenticationException exception2 = new A2AAuthenticationException(TEST_REASON, "");

            assertNotNull(exception1.getSupportedSchemes());
            assertNotNull(exception2.getSupportedSchemes());
            assertEquals("Bearer", exception1.getSupportedSchemes());
            assertEquals("Bearer", exception2.getSupportedSchemes());
        }

        @Test
        @Order(3)
        @DisplayName("getSupportedSchemes_withSingleScheme_returnsSingleScheme")
        void getSupportedSchemes_withSingleScheme_returnsSingleScheme() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, "Bearer");
            assertEquals("Bearer", exception.getSupportedSchemes());
        }

        @Test
        @Order(4)
        @DisplayName("getSupportedSchemes_withMultipleSchemes_returnsAllSchemes")
        void getSupportedSchemes_withMultipleSchemes_returnsAllSchemes() {
            String schemes = "Bearer, ApiKey, mTLS, Test";
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, schemes);
            assertEquals(schemes, exception.getSupportedSchemes());
        }
    }

    @Nested
    @DisplayName("Message Handling")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class MessageHandling {

        @Test
        @Order(1)
        @DisplayName("getMessage_withReason_returnsReason")
        void getMessage_withReason_returnsReason() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            assertEquals(TEST_REASON, exception.getMessage());
        }

        @Test
        @Order(2)
        @DisplayName("getMessage_withEmptyReason_returnsEmptyString")
        void getMessage_withEmptyReason_returnsEmptyString() {
            A2AAuthenticationException exception = new A2AAuthenticationException("", TEST_SCHEMES);
            assertEquals("", exception.getMessage());
        }

        @Test
        @Order(3)
        @DisplayName("getMessage_withNullReason_returnsNull")
        void getMessage_withNullReason_returnsNull() {
            A2AAuthenticationException exception = new A2AAuthenticationException(null, TEST_SCHEMES);
            assertNull(exception.getMessage());
        }

        @Test
        @Order(4)
        @DisplayName("getMessage_includesSecretInformation_shouldNotIncludeSecrets")
        void getMessage_includesSecretInformation_shouldNotIncludeSecrets() {
            // The exception message should not contain sensitive information
            String secretReason = "Invalid API key abc123def456 - secret exposed";
            A2AAuthenticationException exception = new A2AAuthenticationException(secretReason, "ApiKey");

            // The message should not contain the actual secret key
            assertFalse(exception.getMessage().contains("abc123def456"));
            assertTrue(exception.getMessage().contains("Invalid API key"));
        }
    }

    @Nested
    @DisplayName("Cause Handling")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CauseHandling {

        @Test
        @Order(1)
        @DisplayName("getCause_withCause_returnsCause")
        void getCause_withCause_returnsCause() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES, TEST_CAUSE);
            assertEquals(TEST_CAUSE, exception.getCause());
        }

        @Test
        @Order(2)
        @DisplayName("getCause_withoutCause_returnsNull")
        void getCause_withoutCause_returnsNull() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            assertNull(exception.getCause());
        }

        @Test
        @Order(3)
        @DisplayName("getCause_withNestedCause_returnsNestedCause")
        void getCause_withNestedCause_returnsNestedCause() {
            Throwable nestedCause = new IllegalStateException("Database connection failed", TEST_CAUSE);
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES, nestedCause);

            assertEquals(nestedCause, exception.getCause());
            assertEquals(TEST_CAUSE, exception.getCause().getCause());
        }
    }

    @Nested
    @DisplayName("Real-world Scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RealWorldScenarios {

        @Test
        @Order(1)
        @DisplayName("jwtExpired_throwsCorrectException")
        void jwtExpired_throwsCorrectException() {
            String reason = "Bearer token has expired. Obtain a new token and retry.";
            A2AAuthenticationException exception = new A2AAuthenticationException(reason, "Bearer");

            assertEquals(reason, exception.getMessage());
            assertEquals("Bearer", exception.getSupportedSchemes());
        }

        @Test
        @Order(2)
        @DisplayName("apiKeyInvalid_throwsCorrectException")
        void apiKeyInvalid_throwsCorrectException() {
            String reason = "API key is invalid or has been revoked.";
            A2AAuthenticationException exception = new A2AAuthenticationException(reason, "ApiKey");

            assertEquals(reason, exception.getMessage());
            assertEquals("ApiKey", exception.getSupportedSchemes());
        }

        @Test
        @Order(3)
        @DisplayName("missingCredentials_throwsCorrectException")
        void missingCredentials_throwsCorrectException() {
            String reason = "No authentication credentials found in the request. Supported schemes: Bearer, ApiKey.";
            A2AAuthenticationException exception = new A2AAuthenticationException(reason, "Bearer, ApiKey");

            assertEquals(reason, exception.getMessage());
            assertEquals("Bearer, ApiKey", exception.getSupportedSchemes());
        }

        @Test
        @Order(4)
        @DisplayName("networkFailure_wrapsCorrectException")
        void networkFailure_wrapsCorrectException() {
            String reason = "Network error while connecting to identity service";
            Throwable cause = new java.net.ConnectException("Connection refused");

            A2AAuthenticationException exception = new A2AAuthenticationException(reason, "Bearer, ApiKey", cause);

            assertEquals(reason, exception.getMessage());
            assertEquals("Bearer, ApiKey", exception.getSupportedSchemes());
            assertEquals(cause, exception.getCause());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EdgeCases {

        @Test
        @Order(1)
        @DisplayName("exception_withSelfAsCause_throwsNoStackOverflow")
        void exception_withSelfAsCause_throwsNoStackOverflow() {
            A2AAuthenticationException selfReferential = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            selfReferential.initCause(selfReferential);

            // Should not cause stack overflow when accessing
            assertNotNull(selfReferential.getMessage());
            assertNotNull(selfReferential.getCause());
            assertSame(selfReferential, selfReferential.getCause());
        }

        @Test
        @Order(2)
        @DisplayName("exception_withVeryLongMessage_succeeds")
        void exception_withVeryLongMessage_succeeds() {
            String longReason = "x".repeat(1000);
            A2AAuthenticationException exception = new A2AAuthenticationException(longReason, TEST_SCHEMES);

            assertEquals(longReason.length(), exception.getMessage().length());
            assertEquals(TEST_SCHEMES, exception.getSupportedSchemes());
        }

        @Test
        @Order(3)
        @DisplayName("exception_withSpecialCharactersInMessage_succeeds")
        void exception_withSpecialCharactersInMessage_succeeds() {
            String specialReason = "Authentication failed! @#$%^&*()_+-={}[]|\\;:'\",.<>/?";
            A2AAuthenticationException exception = new A2AAuthenticationException(specialReason, TEST_SCHEMES);

            assertEquals(specialReason, exception.getMessage());
            assertEquals(TEST_SCHEMES, exception.getSupportedSchemes());
        }

        @Test
        @Order(4)
        @DisplayName("exception_withUnicodeCharacters_succeeds")
        void exception_withUnicodeCharacters_succeeds() {
            String unicodeReason = "Authentication failed with 中文, 日本語, 한국어";
            A2AAuthenticationException exception = new A2AAuthenticationException(unicodeReason, TEST_SCHEMES);

            assertEquals(unicodeReason, exception.getMessage());
            assertEquals(TEST_SCHEMES, exception.getSupportedSchemes());
        }
    }

    @Nested
    @DisplayName("Integration with Authentication Providers")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class IntegrationWithAuthenticationProviders {

        @Test
        @Order(1)
        @DisplayName("exception_fromJwtProvider_includesCorrectScheme")
        void exception_fromJwtProvider_includesCorrectScheme() throws Exception {
            JwtAuthenticationProvider provider = new JwtAuthenticationProvider("valid-secret-key", null);
            HttpExchange exchange = createMockExchange("Authorization", "Bearer invalid-token");

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                provider.authenticate(exchange);
            });

            A2AAuthenticationException authException = (A2AAuthenticationException) e;
            assertEquals("Bearer", authException.getSupportedSchemes());
            assertNotNull(authException.getMessage());
        }

        @Test
        @Order(2)
        @DisplayName("exception_fromApiKeyProvider_includesCorrectScheme")
        void exception_fromApiKeyProvider_includesCorrectScheme() throws Exception {
            ApiKeyAuthenticationProvider provider = new ApiKeyAuthenticationProvider("valid-master-key");
            HttpExchange exchange = createMockExchange("X-API-Key", "invalid-key");

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                provider.authenticate(exchange);
            });

            A2AAuthenticationException authException = (A2AAuthenticationException) e;
            assertEquals("ApiKey", authException.getSupportedSchemes());
            assertNotNull(authException.getMessage());
        }

        @Test
        @Order(3)
        @DisplayName("exception_fromCompositeProvider_includesAllSchemes")
        void exception_fromCompositeProvider_includesAllSchemes() throws Exception {
            JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider("jwt-secret", null);
            ApiKeyAuthenticationProvider apiKeyProvider = new ApiKeyAuthenticationProvider("api-secret");
            apiKeyProvider.registerKey("test", "user", "valid-key", Set.of());

            List<A2AAuthenticationProvider> providers = List.of(jwtProvider, apiKeyProvider);
            CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(providers);

            HttpExchange exchange = createMockExchange("Other-Header", "value");

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                composite.authenticate(exchange);
            });

            A2AAuthenticationException authException = (A2AAuthenticationException) e;
            assertEquals("Bearer, ApiKey", authException.getSupportedSchemes());
            assertTrue(authException.getMessage().contains("No authentication credentials found"));
        }

        @Test
        @Order(4)
        @DisplayName("exception_preservesOriginalCauseWhenAvailable")
        void exception_preservesOriginalCauseWhenAvailable() throws Exception {
            // Create a provider that throws an exception with a cause
            FailingAuthenticationProvider failingProvider = new FailingAuthenticationProvider(
                "Provider failed", new RuntimeException("Database error")
            );

            HttpExchange exchange = createMockExchange("X-Test-Auth", "value");

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                failingProvider.authenticate(exchange);
            });

            A2AAuthenticationException authException = (A2AAuthenticationException) e;
            assertEquals("Test", authException.getSupportedSchemes());
            assertEquals("Provider failed", authException.getMessage());
            assertNotNull(authException.getCause());
            assertEquals("Database error", authException.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("Object Methods")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ObjectMethods {

        @Test
        @Order(1)
        @DisplayName("toString_includesMessageAndSchemes")
        void toString_includesMessageAndSchemes() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            String toString = exception.toString();

            assertTrue(toString.contains("A2AAuthenticationException"));
            assertTrue(toString.contains(TEST_REASON));
            assertFalse(toString.contains(TEST_SCHEMES)); // toString() doesn't include schemes
        }

        @Test
        @Order(2)
        @DisplayName("toString_withCause_includesCause")
        void toString_withCause_includesCause() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES, TEST_CAUSE);
            String toString = exception.toString();

            assertTrue(toString.contains(TEST_REASON));
            assertTrue(toString.contains("RuntimeException")); // Exception class name
        }

        @Test
        @Order(3)
        @DisplayName("equals_withSameInstance_returnsTrue")
        void equals_withSameInstance_returnsTrue() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            assertTrue(exception.equals(exception));
        }

        @Test
        @Order(4)
        @DisplayName("equals_withDifferentClass_returnsFalse")
        void equals_withDifferentClass_returnsFalse() {
            A2AAuthenticationException exception = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            assertFalse(exception.equals("string"));
        }

        @Test
        @Order(5)
        @DisplayName("equals_withDifferentReason_returnsFalse")
        void equals_withDifferentReason_returnsFalse() {
            A2AAuthenticationException e1 = new A2AAuthenticationException("reason1", TEST_SCHEMES);
            A2AAuthenticationException e2 = new A2AAuthenticationException("reason2", TEST_SCHEMES);
            assertFalse(e1.equals(e2));
        }

        @Test
        @Order(6)
        @DisplayName("equals_withDifferentSchemes_returnsFalse")
        void equals_withDifferentSchemes_returnsFalse() {
            A2AAuthenticationException e1 = new A2AAuthenticationException(TEST_REASON, "Scheme1");
            A2AAuthenticationException e2 = new A2AAuthenticationException(TEST_REASON, "Scheme2");
            assertFalse(e1.equals(e2));
        }

        @Test
        @Order(7)
        @DisplayName("hashCode_consistentWithEquals")
        void hashCode_consistentWithEquals() {
            A2AAuthenticationException e1 = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);
            A2AAuthenticationException e2 = new A2AAuthenticationException(TEST_REASON, TEST_SCHEMES);

            assertEquals(e1.hashCode(), e2.hashCode());

            A2AAuthenticationException e3 = new A2AAuthenticationException("different reason", TEST_SCHEMES);
            assertNotEquals(e1.hashCode(), e3.hashCode());
        }
    }

    // Helper classes and methods

    private static class FailingAuthenticationProvider implements A2AAuthenticationProvider {
        private final String reason;
        private final Throwable cause;

        public FailingAuthenticationProvider(String reason, Throwable cause) {
            this.reason = reason;
            this.cause = cause;
        }

        @Override
        public AuthenticatedPrincipal authenticate(HttpExchange exchange) throws A2AAuthenticationException {
            throw new A2AAuthenticationException(reason, "Test", cause);
        }

        @Override
        public String scheme() {
            return "Test";
        }

        @Override
        public boolean canHandle(HttpExchange exchange) {
            return true;
        }
    }

    private HttpExchange createMockExchange(String headerName, String headerValue) throws Exception {
        // Create a real HttpExchange implementation for testing
        HttpExchange exchange = new HttpExchange() {
            private final Headers headers = new Headers();

            @Override
            public String getRequestMethod() {
                return "GET";
            }

            @Override
            public java.net.URI getRequestURI() {
                return java.net.URI.create("/test");
            }

            @Override
            public String getProtocol() {
                return "HTTP/1.1";
            }

            @Override
            public Headers getRequestHeaders() {
                if (headerName != null && headerValue != null) {
                    headers.add(headerName, headerValue);
                }
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

        return exchange;
    }
}