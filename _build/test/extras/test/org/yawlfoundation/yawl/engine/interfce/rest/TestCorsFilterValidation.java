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

package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.*;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * Integration tests for CorsFilter security policy enforcement (SOC2 CC6.6).
 *
 * <p>Maps to SOC2 control: CC6.6 - Logical Access Controls / Network Boundaries.
 * CORS misconfiguration (wildcard with credentials) is an OWASP Top-10 finding that
 * allows any malicious website to make authenticated cross-origin requests.
 *
 * <p>Tests are written against the real {@link CorsFilter} implementation using
 * real concrete implementations of Jakarta Servlet interfaces. All request and
 * response objects are concrete in-process implementations that accurately capture
 * the filter's header-setting behaviour. No mocking framework is used.
 *
 * <p>SOC2 invariants verified:
 * <ul>
 *   <li>Wildcard origin '*' is always rejected at init time (prevents CORS bypass)</li>
 *   <li>Wildcard embedded in a list is also rejected</li>
 *   <li>Only explicitly whitelisted origins receive CORS headers</li>
 *   <li>Non-whitelisted origins receive no CORS headers (browser blocks them)</li>
 *   <li>Access-Control-Allow-Origin is never set to '*' in any response</li>
 *   <li>allowCredentials=true is only honoured when an explicit origin list is present</li>
 * </ul>
 *
 * <p>Chicago TDD: all tests use the real CorsFilter implementation.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class TestCorsFilterValidation extends TestCase {

    private CorsFilter filter;

    public TestCorsFilterValidation(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        filter = new CorsFilter();
    }

    @Override
    protected void tearDown() throws Exception {
        filter.destroy();
        super.tearDown();
    }

    // =========================================================================
    // CC6.6 - Init-time wildcard rejection
    // =========================================================================

    /**
     * CC6.6: CorsFilter.init() must throw ServletException when allowedOrigins='*'.
     * A wildcard CORS policy combined with credentials allows any site to make
     * authenticated requests on behalf of YAWL users (CSRF escalation).
     */
    public void testInit_WildcardOriginAlone_ThrowsServletException() {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "*");
        try {
            filter.init(cfg);
            fail("CC6.6 FAIL: CorsFilter must throw ServletException for wildcard origin '*'");
        } catch (ServletException e) {
            assertNotNull("Exception message must describe the security violation", e.getMessage());
            assertTrue("Exception message must reference SOC2 or security policy",
                    e.getMessage().contains("SOC2") || e.getMessage().contains("security") ||
                    e.getMessage().contains("wildcard") || e.getMessage().contains("prohibited"));
        }
    }

    /**
     * CC6.6: CorsFilter.init() must throw ServletException when '*' appears anywhere
     * in a comma-separated origin list, not just as the sole value.
     */
    public void testInit_WildcardEmbeddedInList_ThrowsServletException() {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com, *, https://admin.example.com");
        try {
            filter.init(cfg);
            fail("CC6.6 FAIL: CorsFilter must throw ServletException when '*' is in list");
        } catch (ServletException e) {
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: CorsFilter.init() with wildcard origin and allowCredentials=true must
     * throw ServletException. This combination is the highest-severity CORS misconfiguration.
     */
    public void testInit_WildcardOriginWithAllowCredentials_ThrowsServletException() {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "*",
                "allowCredentials", "true");
        try {
            filter.init(cfg);
            fail("CC6.6 FAIL: Wildcard + allowCredentials=true must be rejected at init");
        } catch (ServletException e) {
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: CorsFilter.init() with an empty allowedOrigins configures deny-all mode.
     * No origins are allowed, so no CORS headers will be set in any response.
     * This must not throw an exception - it is a valid (restrictive) configuration.
     */
    public void testInit_EmptyAllowedOrigins_InitializesInDenyAllMode() {
        FilterConfig cfg = buildFilterConfig(); // no allowedOrigins param
        try {
            filter.init(cfg);
            // No exception: deny-all is a valid secure configuration
        } catch (ServletException e) {
            fail("CC6.6 FAIL: Empty allowedOrigins must not throw - it configures deny-all mode: "
                    + e.getMessage());
        }
    }

    /**
     * CC6.6: CorsFilter.init() with a valid comma-separated list of explicit origins
     * must initialize successfully without throwing.
     */
    public void testInit_ValidExplicitOriginList_InitializesSuccessfully() {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com,https://admin.example.com");
        try {
            filter.init(cfg);
        } catch (ServletException e) {
            fail("CC6.6 FAIL: Valid explicit origin list must not throw ServletException: "
                    + e.getMessage());
        }
    }

    // =========================================================================
    // CC6.6 - Request processing: CORS header enforcement
    // =========================================================================

    /**
     * CC6.6: A whitelisted origin must receive the Access-Control-Allow-Origin header
     * set to the exact origin value (not '*').
     */
    public void testDoFilter_WhitelistedOrigin_ReceivesExactOriginHeader() throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com,https://admin.example.com");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        PassThroughFilterChain chain = new PassThroughFilterChain();
        filter.doFilter(buildGetRequest("https://app.example.com"), response, chain);

        assertEquals("CC6.6: Whitelisted origin must be reflected exactly in ACAO header",
                "https://app.example.com",
                response.getHeader("Access-Control-Allow-Origin"));
    }

    /**
     * CC6.6: The ACAO header must never be set to '*', even for whitelisted origins.
     * Wildcard would bypass same-origin policy for all browsers.
     */
    public void testDoFilter_WhitelistedOrigin_AcaoHeaderIsNeverWildcard() throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        filter.doFilter(buildGetRequest("https://app.example.com"),
                response, new PassThroughFilterChain());

        String acao = response.getHeader("Access-Control-Allow-Origin");
        assertNotNull("CC6.6: ACAO header must be set for whitelisted origin", acao);
        assertFalse("CC6.6: ACAO header must NEVER be '*' (wildcard bypass)",
                "*".equals(acao));
    }

    /**
     * CC6.6: A whitelisted origin must also receive the Vary: Origin header.
     * This prevents intermediate caches from serving a CORS response for origin A
     * to a request from origin B.
     */
    public void testDoFilter_WhitelistedOrigin_ReceivesVaryOriginHeader() throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        filter.doFilter(buildGetRequest("https://app.example.com"),
                response, new PassThroughFilterChain());

        assertEquals("CC6.6: Vary: Origin must be set for whitelisted CORS response",
                "Origin", response.getHeader("Vary"));
    }

    /**
     * CC6.6: A non-whitelisted origin must NOT receive any CORS headers.
     * The browser will then block the cross-origin response.
     */
    public void testDoFilter_NonWhitelistedOrigin_ReceivesNoCorsHeaders() throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com,https://admin.example.com");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        filter.doFilter(buildGetRequest("https://evil.example.com"),
                response, new PassThroughFilterChain());

        assertNull("CC6.6: Non-whitelisted origin must NOT receive ACAO header",
                response.getHeader("Access-Control-Allow-Origin"));
        assertNull("CC6.6: Non-whitelisted origin must NOT receive Vary: Origin header",
                response.getHeader("Vary"));
    }

    /**
     * CC6.6: When no Origin header is present (same-origin request), no CORS headers
     * must be added. Same-origin requests do not need CORS handling.
     */
    public void testDoFilter_NoOriginHeader_NoCorsHeadersAdded() throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        PassThroughFilterChain chain = new PassThroughFilterChain();
        filter.doFilter(buildGetRequestNoOrigin(), response, chain);

        assertNull("CC6.6: Same-origin request must not get ACAO header",
                response.getHeader("Access-Control-Allow-Origin"));
        assertTrue("CC6.6: Filter chain must be invoked for same-origin requests",
                chain.wasInvoked());
    }

    /**
     * CC6.6: When allowCredentials=false (default), the Access-Control-Allow-Credentials
     * header must not be set. Not setting it is the safer default.
     */
    public void testDoFilter_CredentialsFalseByDefault_AcacHeaderAbsent() throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com");
        filter.init(cfg); // allowCredentials defaults to false

        RecordingHttpResponse response = new RecordingHttpResponse();
        filter.doFilter(buildGetRequest("https://app.example.com"),
                response, new PassThroughFilterChain());

        String acac = response.getHeader("Access-Control-Allow-Credentials");
        assertNull("CC6.6: ACAC header must not be set when allowCredentials=false", acac);
    }

    /**
     * CC6.6: When allowCredentials=true and origin is whitelisted, the
     * Access-Control-Allow-Credentials header must be set to "true".
     */
    public void testDoFilter_CredentialsTrueWithWhitelistedOrigin_AcacHeaderIsTrue()
            throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com",
                "allowCredentials", "true");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        filter.doFilter(buildGetRequest("https://app.example.com"),
                response, new PassThroughFilterChain());

        assertEquals("CC6.6: ACAC header must be 'true' when configured and origin allowed",
                "true", response.getHeader("Access-Control-Allow-Credentials"));
    }

    /**
     * CC6.6: When allowCredentials=true but origin is NOT whitelisted, the
     * ACAC header must still not be set (no CORS headers at all for rejected origin).
     */
    public void testDoFilter_CredentialsTrueWithNonWhitelistedOrigin_NoCorsHeaders()
            throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com",
                "allowCredentials", "true");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        filter.doFilter(buildGetRequest("https://evil.attacker.com"),
                response, new PassThroughFilterChain());

        assertNull("CC6.6: Non-whitelisted origin must not receive ACAO header",
                response.getHeader("Access-Control-Allow-Origin"));
        assertNull("CC6.6: Non-whitelisted origin must not receive ACAC header",
                response.getHeader("Access-Control-Allow-Credentials"));
    }

    /**
     * CC6.6: OPTIONS preflight requests for whitelisted origins must return HTTP 200
     * without passing to the downstream filter chain.
     */
    public void testDoFilter_OptionsPreflightForWhitelistedOrigin_Returns200AndSkipsChain()
            throws Exception {
        FilterConfig cfg = buildFilterConfig(
                "allowedOrigins", "https://app.example.com");
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        PassThroughFilterChain chain = new PassThroughFilterChain();
        filter.doFilter(buildOptionsRequest("https://app.example.com"), response, chain);

        assertEquals("CC6.6: OPTIONS preflight must return HTTP 200",
                HttpServletResponse.SC_OK, response.getStatus());
        assertFalse("CC6.6: OPTIONS preflight must NOT invoke the downstream filter chain",
                chain.wasInvoked());
    }

    /**
     * CC6.6: When deny-all is configured (empty allowedOrigins), no origin receives
     * CORS headers, even for requests that appear legitimate.
     */
    public void testDoFilter_DenyAllMode_NoOriginReceivesCorsHeaders() throws Exception {
        FilterConfig cfg = buildFilterConfig(); // empty allowedOrigins = deny all
        filter.init(cfg);

        RecordingHttpResponse response = new RecordingHttpResponse();
        filter.doFilter(buildGetRequest("https://app.example.com"),
                response, new PassThroughFilterChain());

        assertNull("CC6.6: Deny-all mode must not set ACAO for any origin",
                response.getHeader("Access-Control-Allow-Origin"));
    }

    // =========================================================================
    // Helpers: real FilterConfig, HttpServletRequest, HttpServletResponse
    // =========================================================================

    /**
     * Builds a real FilterConfig with the given key-value pairs as init parameters.
     * This is a concrete implementation, not a mock.
     */
    private FilterConfig buildFilterConfig(String... keyValues) {
        final Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            params.put(keyValues[i], keyValues[i + 1]);
        }
        return new FilterConfig() {
            @Override public String getFilterName() { return "CorsFilter"; }
            @Override public String getInitParameter(String name) { return params.get(name); }
            @Override public Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(params.keySet());
            }
            @Override public ServletContext getServletContext() {
                return new TestOnlyServletContext();
            }
        };
    }

    /**
     * Builds a minimal real GET HttpServletRequest with the given Origin header value.
     */
    private HttpServletRequest buildGetRequest(String origin) {
        return new MinimalHttpServletRequest("GET", "/api/v1/cases", origin);
    }

    /**
     * Builds a minimal real GET HttpServletRequest with no Origin header (same-origin).
     */
    private HttpServletRequest buildGetRequestNoOrigin() {
        return new MinimalHttpServletRequest("GET", "/api/v1/cases", null);
    }

    /**
     * Builds a minimal real OPTIONS HttpServletRequest with the given Origin header value.
     */
    private HttpServletRequest buildOptionsRequest(String origin) {
        return new MinimalHttpServletRequest("OPTIONS", "/api/v1/cases", origin);
    }

    // =========================================================================
    // Real request/response implementations (no mocks)
    // =========================================================================

    /**
     * A real, minimal implementation of HttpServletRequest for CORS filter testing.
     * Only the three methods actually called by CorsFilter are implemented with
     * meaningful behaviour: getHeader("Origin"), getMethod(), getRequestURI().
     * All other interface methods throw UnsupportedOperationException so that any
     * unexpected call is immediately visible as a test failure.
     */
    private static final class MinimalHttpServletRequest implements HttpServletRequest {

        private static final String ROOT_CONTEXT_PATH = "/";
        private static final String HTTP_1_1_PROTOCOL = "HTTP/1.1";

        private final String method;
        private final String requestUri;
        private final String origin;

        MinimalHttpServletRequest(String method, String requestUri, String origin) {
            this.method = method;
            this.requestUri = requestUri;
            this.origin = origin;
        }

        // --- Methods called by CorsFilter ---
        @Override public String getMethod() { return method; }
        @Override public String getRequestURI() { return requestUri; }
        @Override public String getHeader(String name) {
            if ("Origin".equalsIgnoreCase(name)) return origin;
            return null;
        }

        // --- Methods NOT called by CorsFilter: throw to surface unexpected calls ---
        @Override public String getAuthType() {
            throw new UnsupportedOperationException("CorsFilter does not call getAuthType()");
        }
        @Override public Cookie[] getCookies() {
            throw new UnsupportedOperationException("CorsFilter does not call getCookies()");
        }
        @Override public long getDateHeader(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getDateHeader()");
        }
        @Override public Enumeration<String> getHeaders(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getHeaders(String)");
        }
        @Override public Enumeration<String> getHeaderNames() {
            throw new UnsupportedOperationException("CorsFilter does not call getHeaderNames()");
        }
        @Override public int getIntHeader(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getIntHeader()");
        }
        @Override public String getPathInfo() {
            throw new UnsupportedOperationException("CorsFilter does not call getPathInfo()");
        }
        @Override public String getPathTranslated() {
            throw new UnsupportedOperationException("CorsFilter does not call getPathTranslated()");
        }
        @Override public String getContextPath() { return ROOT_CONTEXT_PATH; }
        @Override public String getQueryString() {
            throw new UnsupportedOperationException("CorsFilter does not call getQueryString()");
        }
        @Override public String getRemoteUser() {
            throw new UnsupportedOperationException("CorsFilter does not call getRemoteUser()");
        }
        @Override public boolean isUserInRole(String role) {
            throw new UnsupportedOperationException("CorsFilter does not call isUserInRole()");
        }
        @Override public Principal getUserPrincipal() {
            throw new UnsupportedOperationException("CorsFilter does not call getUserPrincipal()");
        }
        @Override public String getRequestedSessionId() {
            throw new UnsupportedOperationException("CorsFilter does not call getRequestedSessionId()");
        }
        @Override public StringBuffer getRequestURL() {
            return new StringBuffer("http://localhost:8080" + requestUri);
        }
        @Override public String getServletPath() { return requestUri; }
        @Override public HttpSession getSession(boolean create) {
            throw new UnsupportedOperationException("CorsFilter does not call getSession(boolean)");
        }
        @Override public HttpSession getSession() {
            throw new UnsupportedOperationException("CorsFilter does not call getSession()");
        }
        @Override public String changeSessionId() {
            throw new UnsupportedOperationException("CorsFilter does not call changeSessionId()");
        }
        @Override public boolean isRequestedSessionIdValid() {
            throw new UnsupportedOperationException("CorsFilter does not call isRequestedSessionIdValid()");
        }
        @Override public boolean isRequestedSessionIdFromCookie() {
            throw new UnsupportedOperationException("CorsFilter does not call isRequestedSessionIdFromCookie()");
        }
        @Override public boolean isRequestedSessionIdFromURL() {
            throw new UnsupportedOperationException("CorsFilter does not call isRequestedSessionIdFromURL()");
        }
        @Override public boolean authenticate(HttpServletResponse response) {
            throw new UnsupportedOperationException("CorsFilter does not call authenticate()");
        }
        @Override public void login(String username, String password) {
            throw new UnsupportedOperationException("CorsFilter does not call login()");
        }
        @Override public void logout() {
            throw new UnsupportedOperationException("CorsFilter does not call logout()");
        }
        @Override public Collection<Part> getParts() {
            throw new UnsupportedOperationException("CorsFilter does not call getParts()");
        }
        @Override public Part getPart(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getPart()");
        }
        @Override public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
            throw new UnsupportedOperationException("CorsFilter does not call upgrade()");
        }
        @Override public Object getAttribute(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getAttribute()");
        }
        @Override public Enumeration<String> getAttributeNames() {
            throw new UnsupportedOperationException("CorsFilter does not call getAttributeNames()");
        }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public void setCharacterEncoding(String env) {
            throw new UnsupportedOperationException("CorsFilter does not call setCharacterEncoding()");
        }
        @Override public int getContentLength() {
            throw new UnsupportedOperationException("CorsFilter does not call getContentLength()");
        }
        @Override public long getContentLengthLong() {
            throw new UnsupportedOperationException("CorsFilter does not call getContentLengthLong()");
        }
        @Override public String getContentType() {
            throw new UnsupportedOperationException("CorsFilter does not call getContentType()");
        }
        @Override public ServletInputStream getInputStream() {
            throw new UnsupportedOperationException("CorsFilter does not call getInputStream()");
        }
        @Override public String getParameter(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getParameter()");
        }
        @Override public Enumeration<String> getParameterNames() {
            throw new UnsupportedOperationException("CorsFilter does not call getParameterNames()");
        }
        @Override public String[] getParameterValues(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getParameterValues()");
        }
        @Override public Map<String, String[]> getParameterMap() {
            throw new UnsupportedOperationException("CorsFilter does not call getParameterMap()");
        }
        @Override public String getProtocol() { return HTTP_1_1_PROTOCOL; }
        @Override public String getScheme() { return "http"; }
        @Override public String getServerName() { return "localhost"; }
        @Override public int getServerPort() { return 8080; }
        @Override public BufferedReader getReader() {
            throw new UnsupportedOperationException("CorsFilter does not call getReader()");
        }
        @Override public String getRemoteAddr() { return "127.0.0.1"; }
        @Override public String getRemoteHost() { return "localhost"; }
        @Override public void setAttribute(String name, Object o) {
            throw new UnsupportedOperationException("CorsFilter does not call setAttribute()");
        }
        @Override public void removeAttribute(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call removeAttribute()");
        }
        @Override public Locale getLocale() {
            throw new UnsupportedOperationException("CorsFilter does not call getLocale()");
        }
        @Override public Enumeration<Locale> getLocales() {
            throw new UnsupportedOperationException("CorsFilter does not call getLocales()");
        }
        @Override public boolean isSecure() { return false; }
        @Override public RequestDispatcher getRequestDispatcher(String path) {
            throw new UnsupportedOperationException("CorsFilter does not call getRequestDispatcher()");
        }
        @Override public int getRemotePort() { return 0; }
        @Override public String getLocalName() { return "localhost"; }
        @Override public String getLocalAddr() { return "127.0.0.1"; }
        @Override public int getLocalPort() { return 8080; }
        @Override public ServletContext getServletContext() {
            throw new UnsupportedOperationException("CorsFilter does not call getServletContext()");
        }
        @Override public AsyncContext startAsync() {
            throw new UnsupportedOperationException("CorsFilter does not call startAsync()");
        }
        @Override public AsyncContext startAsync(ServletRequest req, ServletResponse res) {
            throw new UnsupportedOperationException("CorsFilter does not call startAsync(req, res)");
        }
        @Override public boolean isAsyncStarted() { return false; }
        @Override public boolean isAsyncSupported() { return false; }
        @Override public AsyncContext getAsyncContext() {
            throw new UnsupportedOperationException("CorsFilter does not call getAsyncContext()");
        }
        @Override public DispatcherType getDispatcherType() { return DispatcherType.REQUEST; }
        @Override public String getRequestId() { return "test-request-cors-validation"; }
        @Override public String getProtocolRequestId() {
            throw new UnsupportedOperationException("CorsFilter does not call getProtocolRequestId()");
        }
        @Override public ServletConnection getServletConnection() {
            throw new UnsupportedOperationException("CorsFilter does not call getServletConnection()");
        }
    }

    /**
     * A real, recording implementation of HttpServletResponse that captures all headers
     * set by the CorsFilter so tests can make assertions without any mocking framework.
     * Only methods that CorsFilter actually calls have functional implementations.
     */
    static final class RecordingHttpResponse implements HttpServletResponse {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private int status = SC_OK;

        public String getHeader(String name) { return headers.get(name); }
        public int getStatus() { return status; }

        @Override public void setHeader(String name, String value) { headers.put(name, value); }
        @Override public void setStatus(int sc) { this.status = sc; }
        @Override public void addHeader(String name, String value) {
            headers.merge(name, value, (a, b) -> a + ", " + b);
        }
        @Override public void addCookie(Cookie cookie) {
            throw new UnsupportedOperationException("CorsFilter does not call addCookie()");
        }
        @Override public boolean containsHeader(String name) { return headers.containsKey(name); }
        @Override public String encodeURL(String url) {
            throw new UnsupportedOperationException("CorsFilter does not call encodeURL()");
        }
        @Override public String encodeRedirectURL(String url) {
            throw new UnsupportedOperationException("CorsFilter does not call encodeRedirectURL()");
        }
        @Override public void sendError(int sc, String msg) {
            throw new UnsupportedOperationException("CorsFilter does not call sendError(int, String)");
        }
        @Override public void sendError(int sc) {
            throw new UnsupportedOperationException("CorsFilter does not call sendError(int)");
        }
        @Override public void sendRedirect(String location) {
            throw new UnsupportedOperationException("CorsFilter does not call sendRedirect()");
        }
        @Override public void setDateHeader(String name, long date) {
            throw new UnsupportedOperationException("CorsFilter does not call setDateHeader()");
        }
        @Override public void addDateHeader(String name, long date) {
            throw new UnsupportedOperationException("CorsFilter does not call addDateHeader()");
        }
        @Override public void setIntHeader(String name, int value) {
            headers.put(name, String.valueOf(value));
        }
        @Override public void addIntHeader(String name, int value) {
            headers.merge(name, String.valueOf(value), (a, b) -> a + ", " + b);
        }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public String getContentType() { return "text/plain"; }
        @Override public ServletOutputStream getOutputStream() {
            throw new UnsupportedOperationException("CorsFilter does not call getOutputStream()");
        }
        @Override public PrintWriter getWriter() {
            throw new UnsupportedOperationException("CorsFilter does not call getWriter()");
        }
        @Override public void setCharacterEncoding(String charset) {
            throw new UnsupportedOperationException("CorsFilter does not call setCharacterEncoding()");
        }
        @Override public void setContentLength(int len) {
            throw new UnsupportedOperationException("CorsFilter does not call setContentLength()");
        }
        @Override public void setContentLengthLong(long len) {
            throw new UnsupportedOperationException("CorsFilter does not call setContentLengthLong()");
        }
        @Override public void setContentType(String type) {
            throw new UnsupportedOperationException("CorsFilter does not call setContentType()");
        }
        @Override public void setBufferSize(int size) {
            throw new UnsupportedOperationException("CorsFilter does not call setBufferSize()");
        }
        @Override public int getBufferSize() {
            throw new UnsupportedOperationException("CorsFilter does not call getBufferSize()");
        }
        @Override public void flushBuffer() {
            throw new UnsupportedOperationException("CorsFilter does not call flushBuffer()");
        }
        @Override public void resetBuffer() {
            throw new UnsupportedOperationException("CorsFilter does not call resetBuffer()");
        }
        @Override public boolean isCommitted() { return false; }
        @Override public void reset() { headers.clear(); status = SC_OK; }
        @Override public void setLocale(Locale loc) {
            throw new UnsupportedOperationException("CorsFilter does not call setLocale()");
        }
        @Override public Locale getLocale() {
            throw new UnsupportedOperationException("CorsFilter does not call getLocale()");
        }
        @Override public Collection<String> getHeaders(String name) {
            String v = headers.get(name);
            return v != null ? Collections.singletonList(v) : Collections.emptyList();
        }
        @Override public Collection<String> getHeaderNames() { return headers.keySet(); }
    }

    /**
     * A real FilterChain that records whether it was invoked and passes control through.
     * Used to verify that the CorsFilter correctly delegates to or skips the downstream chain.
     */
    private static final class PassThroughFilterChain implements FilterChain {
        private boolean invoked = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            this.invoked = true;
        }

        public boolean wasInvoked() { return invoked; }
    }

    /**
     * A minimal ServletContext for use in the test-scoped FilterConfig.
     * Only the logging methods are functional (used by CorsFilter's init logger).
     * All servlet registration and management methods throw UnsupportedOperationException
     * because CorsFilter never calls them.
     */
    private static final class TestOnlyServletContext implements ServletContext {
        private static final String CONTEXT_NAME = "yawl-test";

        @Override public String getContextPath() { return ROOT_CONTEXT; }
        @Override public int getMajorVersion() { return 6; }
        @Override public int getMinorVersion() { return 1; }
        @Override public int getEffectiveMajorVersion() { return 6; }
        @Override public int getEffectiveMinorVersion() { return 1; }
        @Override public String getServerInfo() { return "YawlTestServer/1.0"; }
        @Override public String getServletContextName() { return CONTEXT_NAME; }
        @Override public void log(String msg) { /* intentional: routes to stdout during tests */ }
        @Override public void log(String message, Throwable throwable) {
            /* intentional: routes to stdout during tests */
        }
        @Override public String getInitParameter(String name) { return null; }
        @Override public Enumeration<String> getInitParameterNames() {
            return Collections.emptyEnumeration();
        }
        @Override public boolean setInitParameter(String name, String value) { return false; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public Enumeration<String> getAttributeNames() {
            return Collections.emptyEnumeration();
        }
        @Override public void setAttribute(String name, Object object) {
            throw new UnsupportedOperationException("CorsFilter does not call ServletContext.setAttribute()");
        }
        @Override public void removeAttribute(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call removeAttribute()");
        }
        @Override public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }
        @Override public String getVirtualServerName() { return "localhost"; }

        // --- Registration/management methods never called by CorsFilter ---
        @Override public ServletContext getContext(String uripath) {
            throw new UnsupportedOperationException("CorsFilter does not call getContext()");
        }
        @Override public String getMimeType(String file) {
            throw new UnsupportedOperationException("CorsFilter does not call getMimeType()");
        }
        @Override public Set<String> getResourcePaths(String path) {
            throw new UnsupportedOperationException("CorsFilter does not call getResourcePaths()");
        }
        @Override public java.net.URL getResource(String path) {
            throw new UnsupportedOperationException("CorsFilter does not call getResource()");
        }
        @Override public InputStream getResourceAsStream(String path) {
            throw new UnsupportedOperationException("CorsFilter does not call getResourceAsStream()");
        }
        @Override public RequestDispatcher getRequestDispatcher(String path) {
            throw new UnsupportedOperationException("CorsFilter does not call getRequestDispatcher()");
        }
        @Override public RequestDispatcher getNamedDispatcher(String name) {
            throw new UnsupportedOperationException("CorsFilter does not call getNamedDispatcher()");
        }
        @Override public String getRealPath(String path) {
            throw new UnsupportedOperationException("CorsFilter does not call getRealPath()");
        }
        @Override public ServletRegistration.Dynamic addServlet(String servletName, String className) {
            throw new UnsupportedOperationException("CorsFilter does not call addServlet()");
        }
        @Override public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
            throw new UnsupportedOperationException("CorsFilter does not call addServlet()");
        }
        @Override public ServletRegistration.Dynamic addServlet(String servletName,
                Class<? extends Servlet> servletClass) {
            throw new UnsupportedOperationException("CorsFilter does not call addServlet()");
        }
        @Override public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) {
            throw new UnsupportedOperationException("CorsFilter does not call addJspFile()");
        }
        @Override public <T extends Servlet> T createServlet(Class<T> clazz) {
            throw new UnsupportedOperationException("CorsFilter does not call createServlet()");
        }
        @Override public ServletRegistration getServletRegistration(String servletName) {
            throw new UnsupportedOperationException("CorsFilter does not call getServletRegistration()");
        }
        @Override public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            throw new UnsupportedOperationException("CorsFilter does not call getServletRegistrations()");
        }
        @Override public FilterRegistration.Dynamic addFilter(String filterName, String className) {
            throw new UnsupportedOperationException("CorsFilter does not call addFilter()");
        }
        @Override public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
            throw new UnsupportedOperationException("CorsFilter does not call addFilter()");
        }
        @Override public FilterRegistration.Dynamic addFilter(String filterName,
                Class<? extends Filter> filterClass) {
            throw new UnsupportedOperationException("CorsFilter does not call addFilter()");
        }
        @Override public <T extends Filter> T createFilter(Class<T> clazz) {
            throw new UnsupportedOperationException("CorsFilter does not call createFilter()");
        }
        @Override public FilterRegistration getFilterRegistration(String filterName) {
            throw new UnsupportedOperationException("CorsFilter does not call getFilterRegistration()");
        }
        @Override public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            throw new UnsupportedOperationException("CorsFilter does not call getFilterRegistrations()");
        }
        @Override public SessionCookieConfig getSessionCookieConfig() {
            throw new UnsupportedOperationException("CorsFilter does not call getSessionCookieConfig()");
        }
        @Override public void setSessionTrackingModes(Set<SessionTrackingMode> modes) {
            throw new UnsupportedOperationException("CorsFilter does not call setSessionTrackingModes()");
        }
        @Override public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            throw new UnsupportedOperationException("CorsFilter does not call getDefaultSessionTrackingModes()");
        }
        @Override public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            throw new UnsupportedOperationException("CorsFilter does not call getEffectiveSessionTrackingModes()");
        }
        @Override public void addListener(String className) {
            throw new UnsupportedOperationException("CorsFilter does not call addListener()");
        }
        @Override public <T extends EventListener> void addListener(T t) {
            throw new UnsupportedOperationException("CorsFilter does not call addListener()");
        }
        @Override public void addListener(Class<? extends EventListener> listenerClass) {
            throw new UnsupportedOperationException("CorsFilter does not call addListener()");
        }
        @Override public <T extends EventListener> T createListener(Class<T> clazz) {
            throw new UnsupportedOperationException("CorsFilter does not call createListener()");
        }
        @Override public JspConfigDescriptor getJspConfigDescriptor() {
            throw new UnsupportedOperationException("CorsFilter does not call getJspConfigDescriptor()");
        }
        @Override public void declareRoles(String... roleNames) {
            throw new UnsupportedOperationException("CorsFilter does not call declareRoles()");
        }
        @Override public int getSessionTimeout() {
            throw new UnsupportedOperationException("CorsFilter does not call getSessionTimeout()");
        }
        @Override public void setSessionTimeout(int sessionTimeout) {
            throw new UnsupportedOperationException("CorsFilter does not call setSessionTimeout()");
        }
        @Override public String getRequestCharacterEncoding() {
            throw new UnsupportedOperationException("CorsFilter does not call getRequestCharacterEncoding()");
        }
        @Override public void setRequestCharacterEncoding(String encoding) {
            throw new UnsupportedOperationException("CorsFilter does not call setRequestCharacterEncoding()");
        }
        @Override public String getResponseCharacterEncoding() {
            throw new UnsupportedOperationException("CorsFilter does not call getResponseCharacterEncoding()");
        }
        @Override public void setResponseCharacterEncoding(String encoding) {
            throw new UnsupportedOperationException("CorsFilter does not call setResponseCharacterEncoding()");
        }

        private static final String ROOT_CONTEXT = "/";
    }

    // =========================================================================
    // Test Suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("CORS Filter Validation Tests (SOC2 CC6.6)");
        suite.addTestSuite(TestCorsFilterValidation.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
