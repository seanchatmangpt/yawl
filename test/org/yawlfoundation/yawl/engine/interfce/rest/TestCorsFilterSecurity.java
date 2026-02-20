package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 security-focused tests for CorsFilter SOC2 security fixes.
 *
 * <p>Uses real concrete servlet stub objects (no mocking framework).
 * Covers JUnit 5 nested test structure over the same CorsFilter as
 * {@link TestCorsFilterValidation} but emphasises edge cases and
 * security invariants with @Nested/@DisplayName annotation style.
 *
 * <p>Chicago TDD: every request and response object is a concrete
 * in-process implementation that accurately captures filter behaviour.
 * UnsupportedOperationException is thrown for any method CorsFilter
 * does not call, making unexpected calls immediately visible.
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class TestCorsFilterSecurity {

    private CorsFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorsFilter();
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
            RecordingHttpResponse response = new RecordingHttpResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new PassThroughFilterChain());

            assertEquals("https://app.example.com",
                    response.getHeader("Access-Control-Allow-Origin"),
                    "Whitelisted origin must be reflected exactly in ACAO header");
        }

        @Test
        @DisplayName("Whitelisted origin receives Vary: Origin header")
        void whitelistedOrigin_receivesVaryOrigin() throws Exception {
            RecordingHttpResponse response = new RecordingHttpResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new PassThroughFilterChain());

            assertEquals("Origin", response.getHeader("Vary"),
                    "Vary: Origin must be set for CORS responses to prevent caching across origins");
        }

        @Test
        @DisplayName("Non-whitelisted origin does NOT receive CORS headers")
        void nonWhitelistedOrigin_noHeaders() throws Exception {
            RecordingHttpResponse response = new RecordingHttpResponse();
            filter.doFilter(buildGetRequest("https://evil.example.com"), response,
                    new PassThroughFilterChain());

            assertNull(response.getHeader("Access-Control-Allow-Origin"),
                    "Non-whitelisted origin must not receive ACAO header");
        }

        @Test
        @DisplayName("Request without Origin header proceeds without CORS headers")
        void noOriginHeader_noCorsHeaders() throws Exception {
            RecordingHttpResponse response = new RecordingHttpResponse();
            PassThroughFilterChain chain = new PassThroughFilterChain();
            filter.doFilter(buildGetRequest(null), response, chain);

            assertNull(response.getHeader("Access-Control-Allow-Origin"),
                    "Same-origin request (no Origin header) must not get CORS headers");
            assertTrue(chain.wasInvoked(), "Filter chain must be invoked for same-origin requests");
        }

        @Test
        @DisplayName("Access-Control-Allow-Credentials is not set when allowCredentials=false")
        void credentialsFalse_headerNotSet() throws Exception {
            RecordingHttpResponse response = new RecordingHttpResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new PassThroughFilterChain());

            assertNull(response.getHeader("Access-Control-Allow-Credentials"),
                    "ACAC header must not be set when allowCredentials=false");
        }

        @Test
        @DisplayName("Access-Control-Allow-Credentials is 'true' when configured")
        void credentialsTrue_headerIsTrue() throws Exception {
            filter.destroy();
            filter = new CorsFilter();
            FilterConfig cfg = buildConfig(
                    "allowedOrigins", "https://app.example.com",
                    "allowCredentials", "true");
            filter.init(cfg);

            RecordingHttpResponse response = new RecordingHttpResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new PassThroughFilterChain());

            assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"),
                    "ACAC header must be 'true' when configured and origin is allowed");
        }

        @Test
        @DisplayName("Wildcard is never set as Access-Control-Allow-Origin value")
        void aCAOisNeverWildcard() throws Exception {
            RecordingHttpResponse response = new RecordingHttpResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new PassThroughFilterChain());

            String acao = response.getHeader("Access-Control-Allow-Origin");
            assertNotEquals("*", acao,
                    "Wildcard must never appear as Access-Control-Allow-Origin (SOC2 CRITICAL)");
        }

        @Test
        @DisplayName("OPTIONS preflight returns 200 without invoking chain")
        void optionsPreflight_returns200() throws Exception {
            RecordingHttpResponse response = new RecordingHttpResponse();
            PassThroughFilterChain chain = new PassThroughFilterChain();
            filter.doFilter(buildOptionsRequest("https://app.example.com"), response, chain);

            assertEquals(HttpServletResponse.SC_OK, response.getStatus(),
                    "OPTIONS preflight must return HTTP 200");
            assertFalse(chain.wasInvoked(),
                    "Filter chain must NOT be invoked for OPTIONS preflight");
        }

        @Test
        @DisplayName("Filter chain is invoked for non-OPTIONS requests with valid origin")
        void validOriginNonOptions_chainInvoked() throws Exception {
            RecordingHttpResponse response = new RecordingHttpResponse();
            PassThroughFilterChain chain = new PassThroughFilterChain();
            filter.doFilter(buildGetRequest("https://app.example.com"), response, chain);

            assertTrue(chain.wasInvoked(),
                    "Filter chain must be invoked for GET with whitelisted origin");
        }
    }

    // =========================================================================
    // Real (no-mock) servlet helpers
    // =========================================================================

    /**
     * Builds a concrete in-process FilterConfig. No mocking framework.
     * Odd-indexed keyValues are parameter names; even-indexed are values.
     * A null value skips the entry (parameter is absent).
     */
    private FilterConfig buildConfig(String... keyValues) {
        final Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] != null && keyValues[i + 1] != null) {
                params.put(keyValues[i], keyValues[i + 1]);
            }
        }
        return new FilterConfig() {
            @Override public String getFilterName() { return "CorsFilter"; }
            @Override public String getInitParameter(String name) { return params.get(name); }
            @Override public Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(params.keySet());
            }
            @Override public jakarta.servlet.ServletContext getServletContext() {
                return new TestCorsFilterValidation.TestOnlyServletContext();
            }
        };
    }

    private TestCorsFilterValidation.MinimalHttpServletRequest buildGetRequest(String origin) {
        return new TestCorsFilterValidation.MinimalHttpServletRequest("GET", "/api/v1/cases", origin);
    }

    private TestCorsFilterValidation.MinimalHttpServletRequest buildOptionsRequest(String origin) {
        return new TestCorsFilterValidation.MinimalHttpServletRequest("OPTIONS", "/api/v1/cases", origin);
    }

    /**
     * Records headers and status set by CorsFilter using only real Java data structures.
     * No mocking framework involved.
     */
    static final class RecordingHttpResponse extends TestCorsFilterValidation.RecordingHttpResponse {
        // Inherits all real implementations from TestCorsFilterValidation.RecordingHttpResponse
    }

    /**
     * Concrete FilterChain that records invocation and passes through.
     */
    static final class PassThroughFilterChain implements FilterChain {
        private boolean invoked = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            this.invoked = true;
        }

        public boolean wasInvoked() { return invoked; }
    }
}
