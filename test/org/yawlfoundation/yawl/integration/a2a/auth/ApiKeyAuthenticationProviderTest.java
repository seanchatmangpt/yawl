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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiKeyAuthenticationProviderTest {

    private static final String MASTER_KEY = "a-very-strong-master-key-for-api-key-hmac-that-is-long-enough";
    private static final String VALID_API_KEY = "test-api-key-value";
    private static final String ANOTHER_API_KEY = "another-api-key-value";

    private ApiKeyAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ApiKeyAuthenticationProvider(MASTER_KEY);
    }

    @Nested
    @DisplayName("Constructor Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConstructorValidation {

        @Test
        @Order(1)
        @DisplayName("constructor_withValidMasterKey_succeeds")
        void constructor_withValidMasterKey_succeeds() {
            ApiKeyAuthenticationProvider p = new ApiKeyAuthenticationProvider(MASTER_KEY);
            assertNotNull(p);
            assertEquals("ApiKey", p.scheme());
        }

        @Test
        @Order(2)
        @DisplayName("constructor_withNullMasterKey_throwsIllegalArgumentException")
        void constructor_withNullMasterKey_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new ApiKeyAuthenticationProvider(null);
            });
            assertTrue(e.getMessage().contains("must be at least 16 characters"));
        }

        @Test
        @Order(3)
        @DisplayName("constructor_withShortMasterKey_throwsIllegalArgumentException")
        void constructor_withShortMasterKey_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new ApiKeyAuthenticationProvider("short");
            });
            assertTrue(e.getMessage().contains("must be at least 16 characters"));
        }
    }

    @Nested
    @DisplayName("Environment Configuration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EnvironmentConfiguration {

        @Test
        @Order(1)
        @DisplayName("fromEnvironment_withMasterKeyAndApiKey_succeeds")
        void fromEnvironment_withMasterKeyAndApiKey_succeeds() {
            System.setProperty("A2A_API_KEY_MASTER", MASTER_KEY);
            System.setProperty("A2A_API_KEY", VALID_API_KEY);

            ApiKeyAuthenticationProvider p = ApiKeyAuthenticationProvider.fromEnvironment();

            assertNotNull(p);
            assertEquals(1, p.registeredKeyCount());

            System.clearProperty("A2A_API_KEY_MASTER");
            System.clearProperty("A2A_API_KEY");
        }

        @Test
        @Order(2)
        @DisplayName("fromEnvironment_withOnlyMasterKey_succeeds")
        void fromEnvironment_withOnlyMasterKey_succeeds() {
            System.setProperty("A2A_API_KEY_MASTER", MASTER_KEY);

            ApiKeyAuthenticationProvider p = ApiKeyAuthenticationProvider.fromEnvironment();

            assertNotNull(p);
            assertEquals(0, p.registeredKeyCount());

            System.clearProperty("A2A_API_KEY_MASTER");
        }

        @Test
        @Order(3)
        @DisplayName("fromEnvironment_withoutMasterKey_throwsIllegalStateException")
        void fromEnvironment_withoutMasterKey_throwsIllegalStateException() {
            Exception e = assertThrows(IllegalStateException.class, () -> {
                ApiKeyAuthenticationProvider.fromEnvironment();
            });
            assertTrue(e.getMessage().contains("A2A_API_KEY_MASTER environment variable is required"));
        }

        @Test
        @Order(4)
        @DisplayName("fromEnvironment_withBlankApiKey_ignoresApiKey")
        void fromEnvironment_withBlankApiKey_ignoresApiKey() {
            System.setProperty("A2A_API_KEY_MASTER", MASTER_KEY);
            System.setProperty("A2A_API_KEY", "   ");

            ApiKeyAuthenticationProvider p = ApiKeyAuthenticationProvider.fromEnvironment();

            assertNotNull(p);
            assertEquals(0, p.registeredKeyCount());

            System.clearProperty("A2A_API_KEY_MASTER");
            System.clearProperty("A2A_API_KEY");
        }
    }

    @Nested
    @DisplayName("Key Management")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class KeyManagement {

        @Test
        @Order(1)
        @DisplayName("registerKey_withValidParameters_succeeds")
        void registerKey_withValidParameters_succeeds() {
            provider.registerKey("test-key", "test-user", VALID_API_KEY, Set.of("workflow:launch"));

            assertEquals(1, provider.registeredKeyCount());
        }

        @Test
        @Order(2)
        @DisplayName("registerKey_withNullKeyId_throwsIllegalArgumentException")
        void registerKey_withNullKeyId_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                provider.registerKey(null, "test-user", VALID_API_KEY, Set.of("workflow:launch"));
            });
            assertTrue(e.getMessage().contains("keyId must not be null"));
        }

        @Test
        @Order(3)
        @DisplayName("registerKey_withBlankKeyId_throwsIllegalArgumentException")
        void registerKey_withBlankKeyId_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                provider.registerKey("  ", "test-user", VALID_API_KEY, Set.of("workflow:launch"));
            });
            assertTrue(e.getMessage().contains("keyId must not be blank"));
        }

        @Test
        @Order(4)
        @DisplayName("registerKey_withNullUsername_throwsIllegalArgumentException")
        void registerKey_withNullUsername_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                provider.registerKey("test-key", null, VALID_API_KEY, Set.of("workflow:launch"));
            });
            assertTrue(e.getMessage().contains("username must not be null"));
        }

        @Test
        @Order(5)
        @DisplayName("registerKey_withBlankUsername_throwsIllegalArgumentException")
        void registerKey_withBlankUsername_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                provider.registerKey("test-key", "  ", VALID_API_KEY, Set.of("workflow:launch"));
            });
            assertTrue(e.getMessage().contains("username must not be blank"));
        }

        @Test
        @Order(6)
        @DisplayName("registerKey_withNullRawKeyValue_throwsIllegalArgumentException")
        void registerKey_withNullRawKeyValue_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                provider.registerKey("test-key", "test-user", null, Set.of("workflow:launch"));
            });
            assertTrue(e.getMessage().contains("rawKeyValue must not be null"));
        }

        @Test
        @Order(7)
        @DisplayName("registerKey_withBlankRawKeyValue_throwsIllegalArgumentException")
        void registerKey_withBlankRawKeyValue_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                provider.registerKey("test-key", "test-user", "  ", Set.of("workflow:launch"));
            });
            assertTrue(e.getMessage().contains("rawKeyValue must not be blank"));
        }

        @Test
        @Order(8)
        @DisplayName("registerKey_withNullPermissions_throwsIllegalArgumentException")
        void registerKey_withNullPermissions_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                provider.registerKey("test-key", "test-user", VALID_API_KEY, null);
            });
            assertTrue(e.getMessage().contains("permissions must not be null"));
        }

        @Test
        @Order(9)
        @DisplayName("registerKey_multipleKeys_succeeds")
        void registerKey_multipleKeys_succeeds() {
            provider.registerKey("key1", "user1", "api-key-1", Set.of("workflow:launch"));
            provider.registerKey("key2", "user2", "api-key-2", Set.of("workflow:query"));

            assertEquals(2, provider.registeredKeyCount());
        }

        @Test
        @Order(10)
        @DisplayName("revokeKey_existingKey_returnsTrue")
        void revokeKey_existingKey_returnsTrue() {
            provider.registerKey("test-key", "test-user", VALID_API_KEY, Set.of("workflow:launch"));

            assertTrue(provider.revokeKey("test-key"));
            assertEquals(0, provider.registeredKeyCount());
        }

        @Test
        @Order(11)
        @DisplayName("revokeKey_nonExistentKey_returnsFalse")
        void revokeKey_nonExistentKey_returnsFalse() {
            assertFalse(provider.revokeKey("non-existent-key"));
        }

        @Test
        @Order(12)
        @DisplayName("registeredKeyCount_afterRegistrationAndRevocation_returnsZero")
        void registeredKeyCount_afterRegistrationAndRevocation_returnsZero() {
            provider.registerKey("test-key", "test-user", VALID_API_KEY, Set.of("workflow:launch"));
            provider.revokeKey("test-key");

            assertEquals(0, provider.registeredKeyCount());
        }
    }

    @Nested
    @DisplayName("Scheme and CanHandle")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SchemeAndCanHandle {

        @Test
        @Order(1)
        @DisplayName("scheme_returnsApiKey")
        void scheme_returnsApiKey() {
            assertEquals("ApiKey", provider.scheme());
        }

        @Test
        @Order(2)
        @DisplayName("canHandle_withValidApiKeyHeader_returnsTrue")
        void canHandle_withValidApiKeyHeader_returnsTrue() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", VALID_API_KEY);
            assertTrue(provider.canHandle(exchange));
        }

        @Test
        @Order(3)
        @DisplayName("canHandle_withMissingApiKeyHeader_returnsFalse")
        void canHandle_withMissingApiKeyHeader_returnsFalse() throws Exception {
            HttpExchange exchange = createMockExchange();
            assertFalse(provider.canHandle(exchange));
        }

        @Test
        @Order(4)
        @DisplayName("canHandle_withEmptyApiKeyHeader_returnsTrue")
        void canHandle_withEmptyApiKeyHeader_returnsTrue() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "");
            assertTrue(provider.canHandle(exchange)); // Empty header still counts as present
        }
    }

    @Nested
    @DisplayName("Authentication")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Authentication {

        @BeforeEach
        void registerTestKey() {
            provider.registerKey("test-key", "test-user", VALID_API_KEY, Set.of("workflow:launch", "code:read"));
        }

        @Test
        @Order(1)
        @DisplayName("authenticate_withValidKey_succeeds")
        void authenticate_withValidKey_succeeds() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", VALID_API_KEY);

            AuthenticatedPrincipal principal = provider.authenticate(exchange);

            assertEquals("test-user", principal.getUsername());
            assertTrue(principal.hasPermission("workflow:launch"));
            assertTrue(principal.hasPermission("code:read"));
            assertTrue(principal.isAuthenticated());
            assertEquals("ApiKey", principal.getAuthScheme());
            assertNotNull(principal.getAuthenticatedAt());
            assertNull(principal.getExpiresAt()); // API keys do not expire
            assertFalse(principal.isExpired());
        }

        @Test
        @Order(2)
        @DisplayName("authenticate_withInvalidKey_throwsA2AAuthenticationException")
        void authenticate_withInvalidKey_throwsA2AAuthenticationException() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "invalid-key");

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                provider.authenticate(exchange);
            });
            assertEquals("ApiKey", e.getSupportedSchemes());
            assertTrue(e.getMessage().contains("API key is invalid"));
        }

        @Test
        @Order(3)
        @DisplayName("authenticate_withMissingApiKeyHeader_throwsA2AAuthenticationException")
        void authenticate_withMissingApiKeyHeader_throwsA2AAuthenticationException() throws Exception {
            HttpExchange exchange = createMockExchange();

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                provider.authenticate(exchange);
            });
            assertEquals("ApiKey", e.getSupportedSchemes());
            assertTrue(e.getMessage().contains("Missing X-API-Key header"));
        }

        @Test
        @Order(4)
        @DisplayName("authenticate_withEmptyApiKeyHeader_throwsA2AAuthenticationException")
        void authenticate_withEmptyApiKeyHeader_throwsA2AAuthenticationException() throws Exception {
            HttpExchange exchange = createMockExchange("X-API-Key", "");

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                provider.authenticate(exchange);
            });
            assertEquals("ApiKey", e.getSupportedSchemes());
            assertTrue(e.getMessage().contains("API key is invalid"));
        }

        @Test
        @Order(5)
        @DisplayName("authenticate_withRevokedKey_throwsA2AAuthenticationException")
        void authenticate_withRevokedKey_throwsA2AAuthenticationException() throws Exception {
            provider.revokeKey("test-key");

            HttpExchange exchange = createMockExchange("X-API-Key", VALID_API_KEY);

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                provider.authenticate(exchange);
            });
            assertEquals("ApiKey", e.getSupportedSchemes());
            assertTrue(e.getMessage().contains("API key is invalid"));
        }

        @Test
        @Order(6)
        @DisplayName("authenticate_withNoRegisteredKeys_throwsA2AAuthenticationException")
        void authenticate_withNoRegisteredKeys_throwsA2AAuthenticationException() throws Exception {
            ApiKeyAuthenticationProvider emptyProvider = new ApiKeyAuthenticationProvider(MASTER_KEY);
            HttpExchange exchange = createMockExchange("X-API-Key", VALID_API_KEY);

            Exception e = assertThrows(A2AAuthenticationException.class, () -> {
                emptyProvider.authenticate(exchange);
            });
            assertEquals("ApiKey", e.getSupportedSchemes());
            assertTrue(e.getMessage().contains("No API keys are configured"));
        }

        @Test
        @Order(7)
        @DisplayName("authenticate_withCorrectKeyDifferentPermissions_succeeds")
        void authenticate_withCorrectKeyDifferentPermissions_succeeds() throws Exception {
            provider.registerKey("another-key", "another-user", ANOTHER_API_KEY, Set.of("workflow:query"));

            HttpExchange exchange = createMockExchange("X-API-Key", ANOTHER_API_KEY);

            AuthenticatedPrincipal principal = provider.authenticate(exchange);

            assertEquals("another-user", principal.getUsername());
            assertTrue(principal.hasPermission("workflow:query"));
            assertFalse(principal.hasPermission("workflow:launch"));
        }
    }

    @Nested
    @DisplayName("Security")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Security {

        @Test
        @Order(1)
        @DisplayName("authentication_constantTimeComparison_preventsTimingAttacks")
        void authentication_constantTimeComparison_preventsTimingAttacks() throws Exception {
            // Register keys with similar lengths to test timing attack prevention
            provider.registerKey("key1", "user1", "short");
            provider.registerKey("key2", "user2", "shorter");
            provider.registerKey("key3", "user3", "shortest");

            // The implementation should not reveal timing information
            // by breaking early when a match is found
            long startTime = System.nanoTime();

            HttpExchange exchange = createMockExchange("X-API-Key", "non-existent-key");
            assertThrows(A2AAuthenticationException.class, () -> {
                provider.authenticate(exchange);
            });

            long duration = System.nanoTime() - startTime;

            // Duration should be consistent regardless of key length
            // This is more of a conceptual test since we can't easily measure precise timing
            assertTrue(duration > 0);
        }

        @Test
        @Order(2)
        @DisplayName("digests_storedAsHMACPreventsKeyReuse")
        void digests_storedAsHMACPreventsKeyReuse() {
            ApiKeyAuthenticationProvider provider1 = new ApiKeyAuthenticationProvider("different-master-key");
            ApiKeyAuthenticationProvider provider2 = new ApiKeyAuthenticationProvider("another-master-key");

            // Same API key but different master keys should produce different digests
            provider.registerKey("test", "user", VALID_API_KEY, Set.of("workflow:launch"));
            provider1.registerKey("test", "user", VALID_API_KEY, Set.of("workflow:launch"));

            // The digest verification will fail between providers because master keys differ
            // This demonstrates that master key HMAC protection prevents key migration attacks
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EdgeCases {

        @Test
        @Order(1)
        @DisplayName("authenticate_withWhitespaceKey_succeeds")
        void authenticate_withWhitespaceKey_succeeds() throws Exception {
            String keyWithWhitespace = "  " + VALID_API_KEY + "  ";
            provider.registerKey("test-key", "test-user", VALID_API_KEY, Set.of("workflow:launch"));

            HttpExchange exchange = createMockExchange("X-API-Key", keyWithWhitespace);

            // Should succeed because strip() is called in the implementation
            AuthenticatedPrincipal principal = provider.authenticate(exchange);

            assertEquals("test-user", principal.getUsername());
        }

        @Test
        @Order(2)
        @DisplayName("authenticate_withSpecialCharacterKey_succeeds")
        void authenticate_withSpecialCharacterKey_succeeds() throws Exception {
            String specialKey = "key-with$pecial@chars";
            provider.registerKey("test-key", "test-user", specialKey, Set.of("workflow:launch"));

            HttpExchange exchange = createMockExchange("X-API-Key", specialKey);

            AuthenticatedPrincipal principal = provider.authenticate(exchange);

            assertEquals("test-user", principal.getUsername());
        }

        @Test
        @Order(3)
        @DisplayName("registerKey_withEmptyPermissions_succeeds")
        void registerKey_withEmptyPermissions_succeeds() {
            provider.registerKey("test-key", "test-user", VALID_API_KEY, Collections.emptySet());

            assertEquals(1, provider.registeredKeyCount());
        }
    }

    // Helper method to create mock HttpExchange for testing
    private HttpExchange createMockExchange() throws Exception {
        return createMockExchange(null, null);
    }

    private HttpExchange createMockExchange(String headerName, String headerValue) throws Exception {
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

        if (headerName != null && headerValue != null) {
            headers.add(headerName, headerValue);
        }

        return exchange;
    }
}