package org.yawlfoundation.yawl.authentication;

import jakarta.servlet.*;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsrfProtectionFilter.
 *
 * Tests cover:
 * - Safe method pass-through (GET, HEAD, OPTIONS, TRACE)
 * - POST/PUT/DELETE/PATCH methods require valid CSRF token
 * - Path normalization to prevent bypass (FIX #5): double-slash, dot-slash, case
 * - Configurable path exclusions (FIX #6)
 * - HTTP 403 responses for invalid or missing tokens
 * - Valid token allows request through the filter chain
 *
 * Chicago TDD: all tests use real objects backed by actual Java state.
 * No mocks, no Mockito, no empty/placeholder implementations.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@Tag("unit")
public class TestCsrfProtectionFilter {

    private CsrfProtectionFilter filter;
    private RecordingFilterConfig filterConfig;

    @BeforeEach
    public void setUp() throws ServletException {
        filter = new CsrfProtectionFilter();
        filterConfig = new RecordingFilterConfig();
        filter.init(filterConfig);
    }

    // =========================================================================
    //  Safe-method pass-through tests
    // =========================================================================

    @Test
    public void testGetMethodBypassesFilterWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemorySession session = new InMemorySession();
        InMemoryRequest request = new InMemoryRequest(session, "GET", "/some/resource");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "GET must bypass CSRF check and continue through the filter chain");
        assertEquals(0, response.getRecordedStatus(),
                "GET must not trigger a 403 response");
    }

    @Test
    public void testHeadMethodBypassesFilter() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemorySession session = new InMemorySession();
        InMemoryRequest request = new InMemoryRequest(session, "HEAD", "/some/resource");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "HEAD must bypass CSRF check and continue through the filter chain");
    }

    @Test
    public void testOptionsMethodBypassesFilter() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemorySession session = new InMemorySession();
        InMemoryRequest request = new InMemoryRequest(session, "OPTIONS", "/some/resource");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "OPTIONS must bypass CSRF check and continue through the filter chain");
    }

    @Test
    public void testTraceMethodBypassesFilter() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemorySession session = new InMemorySession();
        InMemoryRequest request = new InMemoryRequest(session, "TRACE", "/some/resource");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "TRACE must bypass CSRF check and continue through the filter chain");
    }

    // =========================================================================
    //  POST with valid / invalid / missing token
    // =========================================================================

    @Test
    public void testPostWithValidTokenAllowsRequest() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemorySession session = new InMemorySession();
        String token = CsrfTokenManager.generateToken(session);

        InMemoryRequest request = new InMemoryRequest(session, "POST", "/user/update");
        request.setParameter("_csrf", token);
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "POST with valid CSRF token must continue through the filter chain");
        assertEquals(0, response.getRecordedStatus(),
                "POST with valid CSRF token must not trigger a 403");
    }

    @Test
    public void testPostWithInvalidTokenReturns403() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemorySession session = new InMemorySession();
        CsrfTokenManager.generateToken(session);

        InMemoryRequest request = new InMemoryRequest(session, "POST", "/user/update");
        request.setParameter("_csrf", "wrong-token-value");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertFalse(chain.wasInvoked(),
                "POST with invalid CSRF token must NOT continue through the filter chain");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getRecordedStatus(),
                "POST with invalid CSRF token must return HTTP 403 Forbidden");
    }

    @Test
    public void testPostWithNoTokenAndNoSessionReturns403() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "POST", "/user/update");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertFalse(chain.wasInvoked(),
                "POST without any session must NOT continue through the filter chain");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getRecordedStatus(),
                "POST without any session must return HTTP 403 Forbidden");
    }

    @Test
    public void testPostWithValidHeaderTokenAllowsRequest() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemorySession session = new InMemorySession();
        String token = CsrfTokenManager.generateToken(session);

        InMemoryRequest request = new InMemoryRequest(session, "POST", "/user/update");
        request.setHeader("X-CSRF-Token", token);
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "POST with valid X-CSRF-Token header must continue through the filter chain");
    }

    // =========================================================================
    //  Other state-changing HTTP methods require CSRF token
    // =========================================================================

    @Test
    public void testPutWithoutTokenReturns403() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "PUT", "/user/123");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertFalse(chain.wasInvoked(), "PUT without token must be blocked");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getRecordedStatus(),
                "PUT without token must return HTTP 403 Forbidden");
    }

    @Test
    public void testDeleteWithoutTokenReturns403() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "DELETE", "/user/123");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertFalse(chain.wasInvoked(), "DELETE without token must be blocked");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getRecordedStatus(),
                "DELETE without token must return HTTP 403 Forbidden");
    }

    @Test
    public void testPatchWithoutTokenReturns403() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "PATCH", "/user/123");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertFalse(chain.wasInvoked(), "PATCH without token must be blocked");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getRecordedStatus(),
                "PATCH without token must return HTTP 403 Forbidden");
    }

    // =========================================================================
    //  Excluded-path configuration tests (FIX #6, FIX #5 path normalisation)
    // =========================================================================

    @Test
    public void testExcludedPathAllowsPostWithoutToken() throws Exception {
        // Re-initialise with an excluded path
        RecordingFilterConfig configWithExcluded = new RecordingFilterConfig("/api/");
        filter.init(configWithExcluded);

        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "POST", "/api/endpoint");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "POST to an excluded path must bypass CSRF check");
    }

    @Test
    public void testNonExcludedPathStillRequiresToken() throws Exception {
        RecordingFilterConfig configWithExcluded = new RecordingFilterConfig("/api/");
        filter.init(configWithExcluded);

        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "POST", "/user/update");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertFalse(chain.wasInvoked(),
                "POST to a non-excluded path must still require CSRF token");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getRecordedStatus(),
                "Non-excluded path without token must return HTTP 403");
    }

    @Test
    public void testDoubleSlashPathNormalisationAllowsExcluded() throws Exception {
        // FIX #5: double-slash path must be normalised so the exclusion matches
        RecordingFilterConfig configWithExcluded = new RecordingFilterConfig("/api/");
        filter.init(configWithExcluded);

        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "POST", "/api//endpoint");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "Double-slash path /api//endpoint must be normalised to /api/endpoint and match exclusion");
    }

    @Test
    public void testDotSlashPathNormalisationAllowsExcluded() throws Exception {
        // FIX #5: /api/./endpoint must normalise to /api/endpoint
        RecordingFilterConfig configWithExcluded = new RecordingFilterConfig("/api/");
        filter.init(configWithExcluded);

        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "POST", "/api/./endpoint");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "Dot-slash path /api/./endpoint must be normalised to /api/endpoint and match exclusion");
    }

    @Test
    public void testCaseInsensitivePathNormalisationAllowsExcluded() throws Exception {
        // FIX #5: upper-case path must normalise to lower-case for comparison
        RecordingFilterConfig configWithExcluded = new RecordingFilterConfig("/api/");
        filter.init(configWithExcluded);

        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "POST", "/API/endpoint");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        assertTrue(chain.wasInvoked(),
                "/API/endpoint (upper-case) must normalise to /api/endpoint and match exclusion");
    }

    @Test
    public void testMultipleExcludedPathsAreAllRespected() throws Exception {
        RecordingFilterConfig multiConfig = new RecordingFilterConfig("/api/,/health,/metrics");
        filter.init(multiConfig);

        for (String path : new String[]{"/api/data", "/health", "/metrics/jvm"}) {
            RecordingFilterChain chain = new RecordingFilterChain();
            InMemoryRequest request = new InMemoryRequest(null, "POST", path);
            RecordingResponse response = new RecordingResponse();

            filter.doFilter(request, response, chain);

            assertTrue(chain.wasInvoked(),
                    "POST to excluded path '" + path + "' must bypass CSRF check");
        }
    }

    @Test
    public void testInitWithNoExcludedPathsDoesNotThrow() {
        CsrfProtectionFilter freshFilter = new CsrfProtectionFilter();
        assertDoesNotThrow(() -> freshFilter.init(new RecordingFilterConfig()),
                "init() with no excludedPaths parameter must not throw");
    }

    @Test
    public void testInitWithMalformedExcludedPathsDoesNotThrow() {
        CsrfProtectionFilter freshFilter = new CsrfProtectionFilter();
        assertDoesNotThrow(() -> freshFilter.init(new RecordingFilterConfig("/api/,,,/health,  , /metrics")),
                "init() with malformed excludedPaths must not throw");
    }

    @Test
    public void testForbiddenResponseBodyContainsErrorJson() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        InMemoryRequest request = new InMemoryRequest(null, "POST", "/user/update");
        RecordingResponse response = new RecordingResponse();

        filter.doFilter(request, response, chain);

        String body = response.getRecordedBody();
        assertTrue(body.contains("error"),
                "403 response body must contain 'error' field (JSON)");
        assertEquals("application/json", response.getRecordedContentType(),
                "403 response content-type must be application/json");
    }

    @Test
    public void testDestroyDoesNotThrow() {
        assertDoesNotThrow(() -> filter.destroy(),
                "destroy() must not throw any exception");
    }

    // =========================================================================
    //  Real in-memory implementations of Jakarta Servlet interfaces
    //  Backed by actual Java state — no mocking framework used.
    // =========================================================================

    /**
     * Real FilterConfig implementation that returns configurable init parameters
     * and a no-operation ServletContext.
     */
    static final class RecordingFilterConfig implements FilterConfig {
        private final String excludedPaths;

        RecordingFilterConfig() {
            this.excludedPaths = null;
        }

        RecordingFilterConfig(String excludedPaths) {
            this.excludedPaths = excludedPaths;
        }

        @Override
        public String getFilterName() {
            return "CsrfProtectionFilter";
        }

        @Override
        public String getInitParameter(String name) {
            if ("excludedPaths".equals(name)) {
                return excludedPaths;
            }
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            if (excludedPaths != null) {
                return Collections.enumeration(Collections.singletonList("excludedPaths"));
            }
            return Collections.emptyEnumeration();
        }

        @Override
        public ServletContext getServletContext() {
            throw new UnsupportedOperationException(
                    "RecordingFilterConfig.getServletContext: not needed for CSRF filter tests");
        }
    }

    /**
     * Real FilterChain that records whether doFilter() was called.
     * When invoked it simply records the call — it does not delegate further,
     * because CSRF filter tests only need to verify pass/block decisions.
     */
    static final class RecordingFilterChain implements FilterChain {
        private boolean invoked = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            invoked = true;
        }

        boolean wasInvoked() {
            return invoked;
        }
    }

    /**
     * Real in-memory HttpServletResponse that records the HTTP status code,
     * content-type header, and body written by the filter.
     * All state is held in actual Java fields — no mocking used.
     */
    static final class RecordingResponse implements HttpServletResponse {
        private int status = 0;
        private String contentType;
        private final StringWriter bodyWriter = new StringWriter();
        private final PrintWriter printWriter = new PrintWriter(bodyWriter);
        private final Map<String, String> headers = new HashMap<>();

        int getRecordedStatus() {
            return status;
        }

        String getRecordedContentType() {
            return contentType;
        }

        String getRecordedBody() {
            printWriter.flush();
            return bodyWriter.toString();
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
        }

        @Override
        public void setContentType(String type) {
            this.contentType = type;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            return printWriter;
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public Collection<String> getHeaders(String name) {
            String v = headers.get(name);
            return v == null ? Collections.emptyList() : Collections.singletonList(v);
        }

        @Override
        public Collection<String> getHeaderNames() {
            return headers.keySet();
        }

        @Override
        public boolean containsHeader(String name) {
            return headers.containsKey(name);
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void setCharacterEncoding(String charset) {
            throw new UnsupportedOperationException(
                    "RecordingResponse.setCharacterEncoding: not needed for CSRF filter tests");
        }

        @Override
        public void setContentLength(int len) {
            throw new UnsupportedOperationException(
                    "RecordingResponse.setContentLength: not needed for CSRF filter tests");
        }

        @Override
        public void setContentLengthLong(long len) {
            throw new UnsupportedOperationException(
                    "RecordingResponse.setContentLengthLong: not needed for CSRF filter tests");
        }

        @Override
        public void setBufferSize(int size) {
            throw new UnsupportedOperationException(
                    "RecordingResponse.setBufferSize: not needed for CSRF filter tests");
        }

        @Override
        public int getBufferSize() {
            return 8192;
        }

        @Override
        public void flushBuffer() throws IOException {
            printWriter.flush();
        }

        @Override
        public void resetBuffer() {
            throw new UnsupportedOperationException(
                    "RecordingResponse.resetBuffer: not needed for CSRF filter tests");
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException(
                    "RecordingResponse.reset: not needed for CSRF filter tests");
        }

        @Override
        public void setLocale(Locale loc) {
            throw new UnsupportedOperationException(
                    "RecordingResponse.setLocale: not needed for CSRF filter tests");
        }

        @Override
        public Locale getLocale() {
            return Locale.getDefault();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            throw new UnsupportedOperationException(
                    "RecordingResponse.getOutputStream: not needed for CSRF filter tests");
        }

        @Override
        public void addCookie(Cookie cookie) {
            throw new UnsupportedOperationException(
                    "RecordingResponse.addCookie: not needed for CSRF filter tests");
        }

        @Override
        public String encodeURL(String url) {
            return url;
        }

        @Override
        public String encodeRedirectURL(String url) {
            return url;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            throw new UnsupportedOperationException(
                    "RecordingResponse.sendRedirect: not needed for CSRF filter tests");
        }

        @Override
        public void sendRedirect(String location, int sc, boolean clearBuffer)
                throws IOException {
            throw new UnsupportedOperationException(
                    "RecordingResponse.sendRedirect(String, int, boolean): not needed for CSRF filter tests");
        }

        @Override
        public void setDateHeader(String name, long date) {
            headers.put(name, String.valueOf(date));
        }

        @Override
        public void addDateHeader(String name, long date) {
            headers.put(name, String.valueOf(date));
        }

        @Override
        public void setIntHeader(String name, int value) {
            headers.put(name, String.valueOf(value));
        }

        @Override
        public void addIntHeader(String name, int value) {
            headers.put(name, String.valueOf(value));
        }

        @Override
        public int getStatus() {
            return status;
        }
    }

    /**
     * Real in-memory HttpSession backed by a HashMap.
     * Attributes are stored and retrieved via actual Java collections.
     */
    static final class InMemorySession implements HttpSession {
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public long getCreationTime() {
            return System.currentTimeMillis();
        }

        @Override
        public String getId() {
            return "csrf-filter-test-session-id";
        }

        @Override
        public long getLastAccessedTime() {
            return System.currentTimeMillis();
        }

        @Override
        public ServletContext getServletContext() {
            throw new UnsupportedOperationException(
                    "InMemorySession.getServletContext: not needed for CSRF filter tests");
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            throw new UnsupportedOperationException(
                    "InMemorySession.setMaxInactiveInterval: not needed for CSRF filter tests");
        }

        @Override
        public int getMaxInactiveInterval() {
            return 1800;
        }

        @Override
        public void invalidate() {
            attributes.clear();
        }

        @Override
        public boolean isNew() {
            return false;
        }
    }

    /**
     * Real in-memory HttpServletRequest.
     * Method, URI, parameters, headers, and the associated session are
     * held in actual Java fields and collections — no mocking used.
     */
    static final class InMemoryRequest implements HttpServletRequest {
        private final InMemorySession session;
        private final String method;
        private final String requestUri;
        private final Map<String, String> parameters = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();

        InMemoryRequest(InMemorySession session, String method, String requestUri) {
            this.session = session;
            this.method = method;
            this.requestUri = requestUri;
        }

        void setParameter(String name, String value) {
            parameters.put(name, value);
        }

        void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public String getAuthType() {
            return null;
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public String getParameter(String name) {
            return parameters.get(name);
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public HttpSession getSession(boolean create) {
            return session;
        }

        @Override
        public HttpSession getSession() {
            return session;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer("http://localhost" + requestUri);
        }

        @Override
        public String getContextPath() {
            return "/yawl";
        }

        @Override
        public String getServletPath() {
            return requestUri;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return true;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse response) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.authenticate: not needed for CSRF filter tests");
        }

        @Override
        public void login(String username, String password) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.login: not needed for CSRF filter tests");
        }

        @Override
        public void logout() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.logout: not needed for CSRF filter tests");
        }

        @Override
        public Collection<Part> getParts() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getParts: not needed for CSRF filter tests");
        }

        @Override
        public Part getPart(String name) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getPart: not needed for CSRF filter tests");
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.upgrade: not needed for CSRF filter tests");
        }

        @Override
        public HttpServletMapping getHttpServletMapping() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getHttpServletMapping: not needed for CSRF filter tests");
        }

        @Override
        public String changeSessionId() {
            return session != null ? session.getId() : null;
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public void setCharacterEncoding(String env) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.setCharacterEncoding: not needed for CSRF filter tests");
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public long getContentLengthLong() {
            return 0L;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getInputStream: not needed for CSRF filter tests");
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            String v = parameters.get(name);
            return v == null ? null : new String[]{v};
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> map = new HashMap<>();
            parameters.forEach((k, v) -> map.put(k, new String[]{v}));
            return map;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 8080;
        }

        @Override
        public BufferedReader getReader() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getReader: not needed for CSRF filter tests");
        }

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        @Override
        public String getRemoteHost() {
            return "localhost";
        }

        @Override
        public void setAttribute(String name, Object o) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.setAttribute: not needed for CSRF filter tests");
        }

        @Override
        public void removeAttribute(String name) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.removeAttribute: not needed for CSRF filter tests");
        }

        @Override
        public Locale getLocale() {
            return Locale.getDefault();
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(Collections.singletonList(Locale.getDefault()));
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getRequestDispatcher: not needed for CSRF filter tests");
        }

        @Override
        public int getRemotePort() {
            return 12345;
        }

        @Override
        public String getLocalName() {
            return "localhost";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 8080;
        }

        @Override
        public ServletContext getServletContext() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getServletContext: not needed for CSRF filter tests");
        }

        @Override
        public AsyncContext startAsync() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.startAsync: not needed for CSRF filter tests");
        }

        @Override
        public AsyncContext startAsync(ServletRequest req, ServletResponse resp) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.startAsync(req,resp): not needed for CSRF filter tests");
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getAsyncContext: not needed for CSRF filter tests");
        }

        @Override
        public DispatcherType getDispatcherType() {
            return DispatcherType.REQUEST;
        }

        @Override
        public String getRequestId() {
            return "csrf-filter-test-request-id";
        }

        @Override
        public String getProtocolRequestId() {
            return "csrf-filter-test-protocol-request-id";
        }

        @Override
        public ServletConnection getServletConnection() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getServletConnection: not needed for CSRF filter tests");
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String v = headers.get(name);
            return v == null
                    ? Collections.emptyEnumeration()
                    : Collections.enumeration(Collections.singletonList(v));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public int getIntHeader(String name) {
            String v = headers.get(name);
            return v == null ? -1 : Integer.parseInt(v);
        }

        @Override
        public long getDateHeader(String name) {
            return -1L;
        }

        @Override
        public Cookie[] getCookies() {
            return new Cookie[0];
        }
    }
}
