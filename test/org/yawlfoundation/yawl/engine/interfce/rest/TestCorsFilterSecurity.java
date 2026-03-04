package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CorsFilter SOC2 security fixes using Chicago TDD.
 *
 * Verifies that:
 *   - Wildcard origin is rejected at init time
 *   - Only whitelisted origins receive CORS headers
 *   - Non-whitelisted origins are rejected (no headers set)
 *   - Credentials flag reflects configuration
 *   - Preflight OPTIONS is handled correctly
 *
 * Uses real test implementations instead of mocks for Chicago TDD compliance.
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class TestCorsFilterSecurity {

    private CorsFilter filter;
    private TestHttpServletRequest request;
    private TestHttpServletResponse response;
    private TestFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorsFilter();
        request = new TestHttpServletRequest();
        response = new TestHttpServletResponse();
        chain = new TestFilterChain();

        // Set default request properties
        request.setMethod("GET");
        request.setRequestURI("/api/v1/cases");
    }

    // =========================================================================
    // Init-time validation
    // =========================================================================

    @Nested
    @DisplayName("Init: wildcard rejection")
    class InitTests {

          @Test
        @DisplayName("Wildcard '*' in allowedOrigins throws ServletException")
        void wildcardOrigin_throwsServletException() {
            TestFilterConfig cfg = buildConfig("allowedOrigins", "*");
            assertThrows(ServletException.class, () -> filter.init(cfg),
                    "Wildcard '*' must be rejected at init time (SOC2 CRITICAL)");
        }

        @Test
        @DisplayName("Wildcard in comma-separated list throws ServletException")
        void wildcardInList_throwsServletException() {
            TestFilterConfig cfg = buildConfig("allowedOrigins",
                    "https://app.example.com, *, https://admin.example.com");
            assertThrows(ServletException.class, () -> filter.init(cfg),
                    "Wildcard embedded in list must be rejected");
        }

        @Test
        @DisplayName("Valid origin list initializes without error")
        void validOriginList_initializesSuccessfully() {
            TestFilterConfig cfg = buildConfig("allowedOrigins",
                    "https://app.example.com,https://admin.example.com");
            assertDoesNotThrow(() -> filter.init(cfg),
                    "Valid explicit origins must initialize successfully");
        }

        @Test
        @DisplayName("Empty allowedOrigins initializes without error (deny-all)")
        void emptyOrigins_initializesDenyAll() {
            TestFilterConfig cfg = buildConfig("allowedOrigins", null);
            assertDoesNotThrow(() -> filter.init(cfg),
                    "No allowedOrigins configured should initialize in deny-all mode");
        }
    }

    // =========================================================================
    // Request processing
    // =========================================================================

    @Nested
    @DisplayName("Request: CORS header enforcement")
    class RequestTests {

        @BeforeEach
        void initFilter() throws ServletException {
            TestFilterConfig cfg = buildConfig("allowedOrigins",
                    "https://app.example.com,https://admin.example.com");
            filter.init(cfg);
        }

        @Test
        @DisplayName("Whitelisted origin receives Access-Control-Allow-Origin header")
        void whitelistedOrigin_receivesHeader() throws Exception {
            request.setHeader("Origin", "https://app.example.com");

            filter.doFilter(request, response, chain);

            assertEquals("https://app.example.com",
                    response.getHeader("Access-Control-Allow-Origin"),
                    "Whitelisted origin must be reflected exactly in ACAO header");
        }

        @Test
        @DisplayName("Whitelisted origin receives Vary: Origin header")
        void whitelistedOrigin_receivesVaryOrigin() throws Exception {
            request.setHeader("Origin", "https://app.example.com");

            filter.doFilter(request, response, chain);

            assertEquals("Origin", response.getHeader("Vary"),
                    "Vary: Origin must be set for CORS responses to prevent caching across origins");
        }

        @Test
        @DisplayName("Non-whitelisted origin does NOT receive CORS headers")
        void nonWhitelistedOrigin_noHeaders() throws Exception {
            request.setHeader("Origin", "https://evil.example.com");

            filter.doFilter(request, response, chain);

            assertNull(response.getHeader("Access-Control-Allow-Origin"),
                    "Non-whitelisted origin must not receive ACAO header");
        }

        @Test
        @DisplayName("Request without Origin header proceeds without CORS headers")
        void noOriginHeader_noCorsHeaders() throws Exception {
            // Don't set Origin header

            filter.doFilter(request, response, chain);

            assertNull(response.getHeader("Access-Control-Allow-Origin"),
                    "Same-origin request (no Origin header) must not get CORS headers");
            assertTrue(chain.wasInvoked(), "Filter chain should be invoked for non-CORS requests");
        }

        @Test
        @DisplayName("Access-Control-Allow-Credentials is not set when allowCredentials=false")
        void credentialsFalse_headerNotSet() throws Exception {
            request.setHeader("Origin", "https://app.example.com");

            filter.doFilter(request, response, chain);

            assertNull(response.getHeader("Access-Control-Allow-Credentials"),
                    "ACAC header must not be set when allowCredentials=false");
        }

        @Test
        @DisplayName("Access-Control-Allow-Credentials is 'true' when configured")
        void credentialsTrue_headerIsTrue() throws Exception {
            TestFilterConfig cfg = buildConfig(
                    "allowedOrigins", "https://app.example.com",
                    "allowCredentials", "true");
            filter.init(cfg);

            request.setHeader("Origin", "https://app.example.com");

            filter.doFilter(request, response, chain);

            assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"),
                    "ACAC header must be 'true' when configured and origin is allowed");
        }

        @Test
        @DisplayName("Wildcard is never set as Access-Control-Allow-Origin value")
        void aCAOisNeverWildcard() throws Exception {
            request.setHeader("Origin", "https://app.example.com");

            filter.doFilter(request, response, chain);

            String acao = response.getHeader("Access-Control-Allow-Origin");
            assertNotEquals("*", acao,
                    "Wildcard must never appear as Access-Control-Allow-Origin (SOC2 CRITICAL)");
        }

        @Test
        @DisplayName("OPTIONS preflight returns 200 without invoking chain")
        void optionsPreflight_returns200() throws Exception {
            request.setHeader("Origin", "https://app.example.com");
            request.setMethod("OPTIONS");

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), "OPTIONS preflight should return 200 OK");
            assertFalse(chain.wasInvoked(), "Filter chain should not be invoked for OPTIONS preflight");
        }

        @Test
        @DisplayName("Filter chain is invoked for non-OPTIONS requests with valid origin")
        void validOriginNonOptions_chainInvoked() throws Exception {
            request.setHeader("Origin", "https://app.example.com");
            request.setMethod("GET");

            filter.doFilter(request, response, chain);

            assertTrue(chain.wasInvoked(), "Filter chain should be invoked for GET requests");
            assertEquals(request, chain.getLastRequest(), "Request should be passed to chain");
            assertEquals(response, chain.getLastResponse(), "Response should be passed to chain");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TestFilterConfig buildConfig(String... keyValues) {
        TestFilterConfig cfg = new TestFilterConfig("CorsFilter");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] != null && keyValues[i + 1] != null) {
                cfg.setInitParameter(keyValues[i], keyValues[i + 1]);
            }
        }
        return cfg;
    }
}
