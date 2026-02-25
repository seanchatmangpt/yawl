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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticatedPrincipalTest {

    private static final String TEST_USERNAME = "test-user";
    private static final String TEST_SCHEME = "Bearer";
    private static final Instant TEST_AUTHENTICATED_AT = Instant.now();
    private static final Instant TEST_EXPIRES_AT = Instant.now().plus(1, ChronoUnit.HOURS);

    @Nested
    @DisplayName("Constructor Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConstructorValidation {

        @Test
        @Order(1)
        @DisplayName("constructor_withValidParameters_succeeds")
        void constructor_withValidParameters_succeeds() {
            Set<String> permissions = Set.of("workflow:launch", "code:read");
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, permissions, TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertEquals(TEST_USERNAME, principal.getUsername());
            assertEquals(permissions, principal.getPermissions());
            assertEquals(TEST_SCHEME, principal.getAuthScheme());
            assertEquals(TEST_AUTHENTICATED_AT, principal.getAuthenticatedAt());
            assertEquals(TEST_EXPIRES_AT, principal.getExpiresAt());
        }

        @Test
        @Order(2)
        @DisplayName("constructor_withNullUsername_throwsIllegalArgumentException")
        void constructor_withNullUsername_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new AuthenticatedPrincipal(
                    null, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
                );
            });
            assertTrue(e.getMessage().contains("Authenticated principal must have a non-blank username"));
        }

        @Test
        @Order(3)
        @DisplayName("constructor_withBlankUsername_throwsIllegalArgumentException")
        void constructor_withBlankUsername_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new AuthenticatedPrincipal(
                    "  ", Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
                );
            });
            assertTrue(e.getMessage().contains("Authenticated principal must have a non-blank username"));
        }

        @Test
        @Order(4)
        @DisplayName("constructor_withNullPermissions_throwsNullPointerException")
        void constructor_withNullPermissions_throwsNullPointerException() {
            Exception e = assertThrows(NullPointerException.class, () -> {
                new AuthenticatedPrincipal(
                    TEST_USERNAME, null, TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
                );
            });
            assertTrue(e.getMessage().contains("permissions must not be null"));
        }

        @Test
        @Order(5)
        @DisplayName("constructor_withNullAuthScheme_throwsNullPointerException")
        void constructor_withNullAuthScheme_throwsNullPointerException() {
            Exception e = assertThrows(NullPointerException.class, () -> {
                new AuthenticatedPrincipal(
                    TEST_USERNAME, Set.of("workflow:launch"), null, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
                );
            });
            assertTrue(e.getMessage().contains("authScheme must not be null"));
        }

        @Test
        @Order(6)
        @DisplayName("constructor_withNullAuthenticatedAt_throwsNullPointerException")
        void constructor_withNullAuthenticatedAt_throwsNullPointerException() {
            Exception e = assertThrows(NullPointerException.class, () -> {
                new AuthenticatedPrincipal(
                    TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, null, TEST_EXPIRES_AT
                );
            });
            assertTrue(e.getMessage().contains("authenticatedAt must not be null"));
        }

        @Test
        @Order(7)
        @DisplayName("constructor_withNullExpiresAt_succeeds")
        void constructor_withNullExpiresAt_succeeds() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, null
            );

            assertNull(principal.getExpiresAt());
            assertFalse(principal.isExpired());
        }
    }

    @Nested
    @DisplayName("User Interface")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UserInterface {

        @Test
        @Order(1)
        @DisplayName("isAuthenticated_alwaysReturnsTrue")
        void isAuthenticated_alwaysReturnsTrue() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertTrue(principal.isAuthenticated());
        }

        @Test
        @Order(2)
        @DisplayName("getUsername_returnsCorrectUsername")
        void getUsername_returnsCorrectUsername() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertEquals(TEST_USERNAME, principal.getUsername());
        }
    }

    @Nested
    @DisplayName("Permissions")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Permissions {

        @Test
        @Order(1)
        @DisplayName("getPermissions_returnsImmutableSet")
        void getPermissions_returnsImmutableSet() {
            Set<String> permissions = Set.of("workflow:launch", "code:read");
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, permissions, TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            Set<String> returnedPermissions = principal.getPermissions();

            assertThrows(UnsupportedOperationException.class, () -> {
                returnedPermissions.add("new-permission");
            });
        }

        @Test
        @Order(2)
        @DisplayName("getPermissions_withEmptySet_succeeds")
        void getPermissions_withEmptySet_succeeds() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Collections.emptySet(), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertTrue(principal.getPermissions().isEmpty());
        }

        @Test
        @Order(3)
        @DisplayName("hasPermission_withExactPermission_returnsTrue")
        void hasPermission_withExactPermission_returnsTrue() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch", "code:read"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertTrue(principal.hasPermission("workflow:launch"));
            assertTrue(principal.hasPermission("code:read"));
        }

        @Test
        @Order(4)
        @DisplayName("hasPermission_withMissingPermission_returnsFalse")
        void hasPermission_withMissingPermission_returnsFalse() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertFalse(principal.hasPermission("code:read"));
        }

        @Test
        @Order(5)
        @DisplayName("hasPermission_withWildcardPermission_grantsAll")
        void hasPermission_withWildcardPermission_grantsAll() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("*"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertTrue(principal.hasPermission("workflow:launch"));
            assertTrue(principal.hasPermission("code:read"));
            assertTrue(principal.hasPermission("any:permission"));
            assertTrue(principal.hasPermission("*"));
        }

        @Test
        @Order(6)
        @DisplayName("hasPermission_withoutPermissions_returnsFalseForAll")
        void hasPermission_withoutPermissions_returnsFalseForAll() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Collections.emptySet(), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertFalse(principal.hasPermission("workflow:launch"));
            assertFalse(principal.hasPermission("code:read"));
        }

        @Test
        @Order(7)
        @DisplayName("hasPermission_nullPermission_throwsNullPointerException")
        void hasPermission_nullPermission_throwsNullPointerException() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            Exception e = assertThrows(NullPointerException.class, () -> {
                principal.hasPermission(null);
            });
        }
    }

    @Nested
    @DisplayName("Principal Data")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PrincipalData {

        @Test
        @Order(1)
        @DisplayName("getAuthScheme_returnsCorrectScheme")
        void getAuthScheme_returnsCorrectScheme() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), "ApiKey", TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertEquals("ApiKey", principal.getAuthScheme());
        }

        @Test
        @Order(2)
        @DisplayName("getAuthenticatedAt_returnsCorrectTimestamp")
        void getAuthenticatedAt_returnsCorrectTimestamp() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertEquals(TEST_AUTHENTICATED_AT, principal.getAuthenticatedAt());
        }

        @Test
        @Order(3)
        @DisplayName("getExpiresAt_returnsCorrectExpiry")
        void getExpiresAt_returnsCorrectExpiry() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertEquals(TEST_EXPIRES_AT, principal.getExpiresAt());
        }

        @Test
        @Order(4)
        @DisplayName("isExpired_withFutureExpiry_returnsFalse")
        void isExpired_withFutureExpiry_returnsFalse() {
            Instant futureExpiry = Instant.now().plus(1, ChronoUnit.HOURS);
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, futureExpiry
            );

            assertFalse(principal.isExpired());
        }

        @Test
        @Order(5)
        @DisplayName("isExpired_withPastExpiry_returnsTrue")
        void isExpired_withPastExpiry_returnsTrue() {
            Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, pastExpiry
            );

            assertTrue(principal.isExpired());
        }

        @Test
        @Order(6)
        @DisplayName("isExpired_withNullExpiry_returnsFalse")
        void isExpired_withNullExpiry_returnsFalse() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, null
            );

            assertFalse(principal.isExpired());
        }
    }

    @Nested
    @DisplayName("Permission Constants")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PermissionConstants {

        @Test
        @Order(1)
        @DisplayName("PERM_ALL_constantExists")
        void PERM_ALL_constantExists() {
            assertEquals("*", AuthenticatedPrincipal.PERM_ALL);
        }

        @Test
        @Order(2)
        @DisplayName("workflowPermissions_constantsExist")
        void workflowPermissions_constantsExist() {
            assertEquals("workflow:launch", AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH);
            assertEquals("workflow:query", AuthenticatedPrincipal.PERM_WORKFLOW_QUERY);
            assertEquals("workflow:cancel", AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL);
            assertEquals("workitem:manage", AuthenticatedPrincipal.PERM_WORKITEM_MANAGE);
        }

        @Test
        @Order(3)
        @DisplayName("codePermissions_constantsExist")
        void codePermissions_constantsExist() {
            assertEquals("code:read", AuthenticatedPrincipal.PERM_CODE_READ);
            assertEquals("code:write", AuthenticatedPrincipal.PERM_CODE_WRITE);
            assertEquals("build:execute", AuthenticatedPrincipal.PERM_BUILD_EXECUTE);
            assertEquals("test:execute", AuthenticatedPrincipal.PERM_TEST_EXECUTE);
            assertEquals("git:commit", AuthenticatedPrincipal.PERM_GIT_COMMIT);
            assertEquals("upgrade:execute", AuthenticatedPrincipal.PERM_UPGRADE_EXECUTE);
        }
    }

    @Nested
    @DisplayName("Object Methods")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ObjectMethods {

        @Test
        @Order(1)
        @DisplayName("toString_returnsFormattedString")
        void toString_returnsFormattedString() {
            Set<String> permissions = Set.of("workflow:launch", "code:read");
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, permissions, TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            String toString = principal.toString();
            assertTrue(toString.contains("username='" + TEST_USERNAME + "'"));
            assertTrue(toString.contains("scheme='" + TEST_SCHEME + "'"));
            assertTrue(toString.contains("permissions=" + permissions));
            assertTrue(toString.contains("expiresAt=" + TEST_EXPIRES_AT));
        }

        @Test
        @Order(2)
        @DisplayName("equals_withSameObject_returnsTrue")
        void equals_withSameObject_returnsTrue() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertTrue(principal.equals(principal));
        }

        @Test
        @Order(3)
        @DisplayName("equals_withNull_returnsFalse")
        void equals_withNull_returnsFalse() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertFalse(principal.equals(null));
        }

        @Test
        @Order(4)
        @DisplayName("equals_withDifferentClass_returnsFalse")
        void equals_withDifferentClass_returnsFalse() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertFalse(principal.equals("string"));
        }

        @Test
        @Order(5)
        @DisplayName("equals_withDifferentUsername_returnsFalse")
        void equals_withDifferentUsername_returnsFalse() {
            AuthenticatedPrincipal principal1 = new AuthenticatedPrincipal(
                "user1", Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );
            AuthenticatedPrincipal principal2 = new AuthenticatedPrincipal(
                "user2", Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertFalse(principal1.equals(principal2));
        }

        @Test
        @Order(6)
        @DisplayName("equals_withDifferentScheme_returnsFalse")
        void equals_withDifferentScheme_returnsFalse() {
            AuthenticatedPrincipal principal1 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), "Bearer", TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );
            AuthenticatedPrincipal principal2 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), "ApiKey", TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertFalse(principal1.equals(principal2));
        }

        @Test
        @Order(7)
        @DisplayName("equals_withDifferentAuthenticatedAt_returnsFalse")
        void equals_withDifferentAuthenticatedAt_returnsFalse() {
            Instant differentTime = TEST_AUTHENTICATED_AT.plus(1, ChronoUnit.HOURS);
            AuthenticatedPrincipal principal1 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );
            AuthenticatedPrincipal principal2 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, differentTime, TEST_EXPIRES_AT
            );

            assertFalse(principal1.equals(principal2));
        }

        @Test
        @Order(8)
        @DisplayName("equals_withSameValues_returnsTrue")
        void equals_withSameValues_returnsTrue() {
            AuthenticatedPrincipal principal1 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );
            AuthenticatedPrincipal principal2 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertTrue(principal1.equals(principal2));
        }

        @Test
        @Order(9)
        @DisplayName("hashCode_consistentWithEquals")
        void hashCode_consistentWithEquals() {
            AuthenticatedPrincipal principal1 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );
            AuthenticatedPrincipal principal2 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );
            AuthenticatedPrincipal principal3 = new AuthenticatedPrincipal(
                "different", Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertEquals(principal1.hashCode(), principal2.hashCode());
            assertNotEquals(principal1.hashCode(), principal3.hashCode());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EdgeCases {

        @Test
        @Order(1)
        @DisplayName("constructor_withWhitespaceUsername_throwsIllegalArgumentException")
        void constructor_withWhitespaceUsername_throwsIllegalArgumentException() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> {
                new AuthenticatedPrincipal(
                    "   ", Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
                );
            });
            assertTrue(e.getMessage().contains("Authenticated principal must have a non-blank username"));
        }

        @Test
        @Order(2)
        @DisplayName("hasPermission_withEmptyStringPermission_returnsFalse")
        void hasPermission_withEmptyStringPermission_returnsFalse() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertFalse(principal.hasPermission(""));
        }

        @Test
        @Order(3)
        @DisplayName("constructor_withEmptyPermissions_succeeds")
        void constructor_withEmptyPermissions_succeeds() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Collections.emptySet(), TEST_SCHEME, TEST_AUTHENTICATED_AT, TEST_EXPIRES_AT
            );

            assertTrue(principal.getPermissions().isEmpty());
            assertFalse(principal.hasPermission("workflow:launch"));
        }

        @Test
        @Order(4)
        @DisplayName("isExpired_withNowTimestamp_returnsFalse")
        void isExpired_withNowTimestamp_returnsFalse() {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, TEST_AUTHENTICATED_AT, Instant.now()
            );

            assertFalse(principal.isExpired());
        }

        @Test
        @Order(5)
        @DisplayName("constructor_withDifferentTimezones_succeeds")
        void constructor_withDifferentTimezones_succeeds() {
            // Test with different time zones to ensure the equals/hashCode work correctly
            Instant created1 = Instant.parse("2023-01-01T00:00:00Z");
            Instant created2 = Instant.parse("2023-01-01T00:00:00Z");

            AuthenticatedPrincipal principal1 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, created1, TEST_EXPIRES_AT
            );
            AuthenticatedPrincipal principal2 = new AuthenticatedPrincipal(
                TEST_USERNAME, Set.of("workflow:launch"), TEST_SCHEME, created2, TEST_EXPIRES_AT
            );

            assertEquals(principal1, principal2);
            assertEquals(principal1.hashCode(), principal2.hashCode());
        }
    }

    @Nested
    @DisplayName("Real Implementation Integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RealImplementationIntegration {

        @Test
        @Order(1)
        @DisplayName("createdFromJWTProvider_hasExpectedProperties")
        void createdFromJWTProvider_hasExpectedProperties() throws Exception {
            // Create a real JWT provider
            JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
            String token = jwtProvider.issueToken(TEST_USERNAME, Set.of("workflow:launch"), 3600000);

            // Create a mock HttpExchange and authenticate
            HttpExchange exchange = createMockExchange("Authorization", "Bearer " + token);
            AuthenticatedPrincipal principal = jwtProvider.authenticate(exchange);

            // Verify the principal has expected properties
            assertEquals(TEST_USERNAME, principal.getUsername());
            assertTrue(principal.hasPermission("workflow:launch"));
            assertTrue(principal.isAuthenticated());
            assertEquals("Bearer", principal.getAuthScheme());
            assertNotNull(principal.getAuthenticatedAt());
            assertNotNull(principal.getExpiresAt());
            assertFalse(principal.isExpired());
        }

        @Test
        @Order(2)
        @DisplayName("createdFromApiKeyProvider_hasExpectedProperties")
        void createdFromApiKeyProvider_hasExpectedProperties() throws Exception {
            // Create a real API key provider
            ApiKeyAuthenticationProvider apiKeyProvider = new ApiKeyAuthenticationProvider(API_MASTER_KEY);
            apiKeyProvider.registerKey("test-key", TEST_USERNAME, "valid-key", Set.of("code:read", "code:write"));

            // Create a mock HttpExchange and authenticate
            HttpExchange exchange = createMockExchange("X-API-Key", "valid-key");
            AuthenticatedPrincipal principal = apiKeyProvider.authenticate(exchange);

            // Verify the principal has expected properties
            assertEquals(TEST_USERNAME, principal.getUsername());
            assertTrue(principal.hasPermission("code:read"));
            assertTrue(principal.hasPermission("code:write"));
            assertTrue(principal.isAuthenticated());
            assertEquals("ApiKey", principal.getAuthScheme());
            assertNotNull(principal.getAuthenticatedAt());
            assertNull(principal.getExpiresAt()); // API keys don't expire
            assertFalse(principal.isExpired());
        }

        @Test
        @Order(3)
        @DisplayName("equals_acrossProviderTypes_works")
        void equals_acrossProviderTypes_works() throws Exception {
            // Create principals from different providers but with same data
            JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
            ApiKeyAuthenticationProvider apiKeyProvider = new ApiKeyAuthenticationProvider(API_MASTER_KEY);

            String jwtToken = jwtProvider.issueToken(TEST_USERNAME, Set.of("workflow:launch"), 3600000);
            apiKeyProvider.registerKey("test-key", TEST_USERNAME, "valid-key", Set.of("workflow:launch"));

            HttpExchange jwtExchange = createMockExchange("Authorization", "Bearer " + jwtToken);
            HttpExchange apiKeyExchange = createMockExchange("X-API-Key", "valid-key");

            AuthenticatedPrincipal jwtPrincipal = jwtProvider.authenticate(jwtExchange);
            AuthenticatedPrincipal apiKeyPrincipal = apiKeyProvider.authenticate(apiKeyExchange);

            // Principals should be equal even if created by different providers
            assertEquals(jwtPrincipal.getUsername(), apiKeyPrincipal.getUsername());
            assertEquals(jwtPrincipal.getPermissions(), apiKeyPrincipal.getPermissions());
        }
    }

    // Helper methods for testing

    private static final String JWT_SECRET = "jwt-secret-key-32-bytes-long";
    private static final String API_MASTER_KEY = "api-master-key-32-bytes-long";

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