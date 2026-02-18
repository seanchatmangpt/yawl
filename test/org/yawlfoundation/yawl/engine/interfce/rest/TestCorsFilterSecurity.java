package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorsFilter SOC2 security fixes.
 *
 * Verifies that:
 *   - Wildcard origin is rejected at init time
 *   - Only whitelisted origins receive CORS headers
 *   - Non-whitelisted origins are rejected (no headers set)
 *   - Credentials flag reflects configuration
 *   - Preflight OPTIONS is handled correctly
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class TestCorsFilterSecurity {

    private CorsFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private Map<String, String> responseHeaders;

    @BeforeEach
    void setUp() {
        filter = new CorsFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        responseHeaders = new HashMap<>();

        // Capture headers set on response
        doAnswer(inv -> {
            responseHeaders.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(response).setHeader(anyString(), anyString());

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
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
            FilterConfig cfg = buildConfig("allowedOrigins", "*");
            assertThrows(ServletException.class, () -> filter.init(cfg),
                    "Wildcard '*' must be rejected at init time (SOC2 CRITICAL)");
        }

        @Test
        @DisplayName("Wildcard in comma-separated list throws ServletException")
        void wildcardInList_throwsServletException() {
            FilterConfig cfg = buildConfig("allowedOrigins",
                    "https://app.example.com, *, https://admin.example.com");
            assertThrows(ServletException.class, () -> filter.init(cfg),
                    "Wildcard embedded in list must be rejected");
        }

        @Test
        @DisplayName("Valid origin list initializes without error")
        void validOriginList_initializesSuccessfully() {
            FilterConfig cfg = buildConfig("allowedOrigins",
                    "https://app.example.com,https://admin.example.com");
            assertDoesNotThrow(() -> filter.init(cfg),
                    "Valid explicit origins must initialize successfully");
        }

        @Test
        @DisplayName("Empty allowedOrigins initializes without error (deny-all)")
        void emptyOrigins_initializesDenyAll() {
            FilterConfig cfg = buildConfig("allowedOrigins", null);
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
            FilterConfig cfg = buildConfig("allowedOrigins",
                    "https://app.example.com,https://admin.example.com");
            filter.init(cfg);
        }

        @Test
        @DisplayName("Whitelisted origin receives Access-Control-Allow-Origin header")
        void whitelistedOrigin_receivesHeader() throws Exception {
            when(request.getHeader("Origin")).thenReturn("https://app.example.com");

            filter.doFilter(request, response, chain);

            assertEquals("https://app.example.com",
                    responseHeaders.get("Access-Control-Allow-Origin"),
                    "Whitelisted origin must be reflected exactly in ACAO header");
        }

        @Test
        @DisplayName("Whitelisted origin receives Vary: Origin header")
        void whitelistedOrigin_receivesVaryOrigin() throws Exception {
            when(request.getHeader("Origin")).thenReturn("https://app.example.com");

            filter.doFilter(request, response, chain);

            assertEquals("Origin", responseHeaders.get("Vary"),
                    "Vary: Origin must be set for CORS responses to prevent caching across origins");
        }

        @Test
        @DisplayName("Non-whitelisted origin does NOT receive CORS headers")
        void nonWhitelistedOrigin_noHeaders() throws Exception {
            when(request.getHeader("Origin")).thenReturn("https://evil.example.com");

            filter.doFilter(request, response, chain);

            assertNull(responseHeaders.get("Access-Control-Allow-Origin"),
                    "Non-whitelisted origin must not receive ACAO header");
        }

        @Test
        @DisplayName("Request without Origin header proceeds without CORS headers")
        void noOriginHeader_noCorsHeaders() throws Exception {
            when(request.getHeader("Origin")).thenReturn(null);

            filter.doFilter(request, response, chain);

            assertNull(responseHeaders.get("Access-Control-Allow-Origin"),
                    "Same-origin request (no Origin header) must not get CORS headers");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("Access-Control-Allow-Credentials is not set when allowCredentials=false")
        void credentialsFalse_headerNotSet() throws Exception {
            when(request.getHeader("Origin")).thenReturn("https://app.example.com");

            filter.doFilter(request, response, chain);

            assertNull(responseHeaders.get("Access-Control-Allow-Credentials"),
                    "ACAC header must not be set when allowCredentials=false");
        }

        @Test
        @DisplayName("Access-Control-Allow-Credentials is 'true' when configured")
        void credentialsTrue_headerIsTrue() throws Exception {
            FilterConfig cfg = buildConfig(
                    "allowedOrigins", "https://app.example.com",
                    "allowCredentials", "true");
            filter.init(cfg);

            when(request.getHeader("Origin")).thenReturn("https://app.example.com");

            filter.doFilter(request, response, chain);

            assertEquals("true", responseHeaders.get("Access-Control-Allow-Credentials"),
                    "ACAC header must be 'true' when configured and origin is allowed");
        }

        @Test
        @DisplayName("Wildcard is never set as Access-Control-Allow-Origin value")
        void aCAOisNeverWildcard() throws Exception {
            when(request.getHeader("Origin")).thenReturn("https://app.example.com");

            filter.doFilter(request, response, chain);

            String acao = responseHeaders.get("Access-Control-Allow-Origin");
            assertNotEquals("*", acao,
                    "Wildcard must never appear as Access-Control-Allow-Origin (SOC2 CRITICAL)");
        }

        @Test
        @DisplayName("OPTIONS preflight returns 200 without invoking chain")
        void optionsPreflight_returns200() throws Exception {
            when(request.getHeader("Origin")).thenReturn("https://app.example.com");
            when(request.getMethod()).thenReturn("OPTIONS");

            filter.doFilter(request, response, chain);

            verify(response).setStatus(HttpServletResponse.SC_OK);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Filter chain is invoked for non-OPTIONS requests with valid origin")
        void validOriginNonOptions_chainInvoked() throws Exception {
            when(request.getHeader("Origin")).thenReturn("https://app.example.com");
            when(request.getMethod()).thenReturn("GET");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private FilterConfig buildConfig(String... keyValues) {
        FilterConfig cfg = mock(FilterConfig.class);
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] != null) {
                params.put(keyValues[i], keyValues[i + 1]);
            }
        }
        when(cfg.getInitParameter(anyString())).thenAnswer(inv ->
                params.get(inv.getArgument(0).toString()));
        return cfg;
    }
}
