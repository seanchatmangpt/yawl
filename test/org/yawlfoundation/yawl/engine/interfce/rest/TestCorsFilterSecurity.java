package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 security-focused tests for CorsFilter SOC2 security fixes.
 *
 * <p>Covers the same invariants as {@link TestCorsFilterValidation} but in
 * JUnit 5 {@code @Nested}/{@code @DisplayName} style. Uses real concrete
 * in-process implementations of Jakarta Servlet interfaces — no mocking
 * framework. All unneeded interface methods throw
 * {@link UnsupportedOperationException} so any unexpected call surfaces
 * immediately as a test failure.
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
            FilterConfig cfg = buildConfig();
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
            RecordingResponse response = new RecordingResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new RecordingChain());

            assertEquals("https://app.example.com",
                    response.getHeader("Access-Control-Allow-Origin"),
                    "Whitelisted origin must be reflected exactly in ACAO header");
        }

        @Test
        @DisplayName("Whitelisted origin receives Vary: Origin header")
        void whitelistedOrigin_receivesVaryOrigin() throws Exception {
            RecordingResponse response = new RecordingResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new RecordingChain());

            assertEquals("Origin", response.getHeader("Vary"),
                    "Vary: Origin must be set for CORS responses to prevent caching across origins");
        }

        @Test
        @DisplayName("Non-whitelisted origin does NOT receive CORS headers")
        void nonWhitelistedOrigin_noHeaders() throws Exception {
            RecordingResponse response = new RecordingResponse();
            filter.doFilter(buildGetRequest("https://evil.example.com"), response,
                    new RecordingChain());

            assertNull(response.getHeader("Access-Control-Allow-Origin"),
                    "Non-whitelisted origin must not receive ACAO header");
        }

        @Test
        @DisplayName("Request without Origin header proceeds without CORS headers")
        void noOriginHeader_noCorsHeaders() throws Exception {
            RecordingResponse response = new RecordingResponse();
            RecordingChain chain = new RecordingChain();
            filter.doFilter(buildGetRequest(null), response, chain);

            assertNull(response.getHeader("Access-Control-Allow-Origin"),
                    "Same-origin request (no Origin header) must not get CORS headers");
            assertTrue(chain.wasInvoked(), "Filter chain must be invoked for same-origin requests");
        }

        @Test
        @DisplayName("Access-Control-Allow-Credentials is not set when allowCredentials=false")
        void credentialsFalse_headerNotSet() throws Exception {
            RecordingResponse response = new RecordingResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new RecordingChain());

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

            RecordingResponse response = new RecordingResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new RecordingChain());

            assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"),
                    "ACAC header must be 'true' when configured and origin is allowed");
        }

        @Test
        @DisplayName("Wildcard is never set as Access-Control-Allow-Origin value")
        void aCAOisNeverWildcard() throws Exception {
            RecordingResponse response = new RecordingResponse();
            filter.doFilter(buildGetRequest("https://app.example.com"), response,
                    new RecordingChain());

            String acao = response.getHeader("Access-Control-Allow-Origin");
            assertNotEquals("*", acao,
                    "Wildcard must never appear as Access-Control-Allow-Origin (SOC2 CRITICAL)");
        }

        @Test
        @DisplayName("OPTIONS preflight returns 200 without invoking chain")
        void optionsPreflight_returns200() throws Exception {
            RecordingResponse response = new RecordingResponse();
            RecordingChain chain = new RecordingChain();
            filter.doFilter(buildOptionsRequest("https://app.example.com"), response, chain);

            assertEquals(HttpServletResponse.SC_OK, response.getStatus(),
                    "OPTIONS preflight must return HTTP 200");
            assertFalse(chain.wasInvoked(),
                    "Filter chain must NOT be invoked for OPTIONS preflight");
        }

        @Test
        @DisplayName("Filter chain is invoked for non-OPTIONS requests with valid origin")
        void validOriginNonOptions_chainInvoked() throws Exception {
            RecordingResponse response = new RecordingResponse();
            RecordingChain chain = new RecordingChain();
            filter.doFilter(buildGetRequest("https://app.example.com"), response, chain);

            assertTrue(chain.wasInvoked(),
                    "Filter chain must be invoked for GET with whitelisted origin");
        }
    }

    // =========================================================================
    // Real (no-mock) servlet helpers
    // =========================================================================

    /**
     * Builds a concrete in-process FilterConfig backed by a plain Map.
     * Accepts vararg key-value pairs; a null value skips the entry.
     * No mocking framework is used.
     */
    private static FilterConfig buildConfig(String... keyValues) {
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
            @Override public ServletContext getServletContext() {
                return new MinimalServletContext();
            }
        };
    }

    private static CorsTestRequest buildGetRequest(String origin) {
        return new CorsTestRequest("GET", "/api/v1/cases", origin);
    }

    private static CorsTestRequest buildOptionsRequest(String origin) {
        return new CorsTestRequest("OPTIONS", "/api/v1/cases", origin);
    }

    // =========================================================================
    // Concrete in-process implementations (no mocking framework)
    // =========================================================================

    /**
     * Concrete in-process HttpServletRequest for CorsFilter testing.
     * Implements exactly the three methods CorsFilter calls: getHeader("Origin"),
     * getMethod(), and getRequestURI(). All other methods throw
     * UnsupportedOperationException so any unexpected call is immediately visible.
     */
    private static final class CorsTestRequest implements HttpServletRequest {

        private final String method;
        private final String requestUri;
        private final String origin;

        CorsTestRequest(String method, String requestUri, String origin) {
            this.method = method;
            this.requestUri = requestUri;
            this.origin = origin;
        }

        @Override public String getMethod() { return method; }
        @Override public String getRequestURI() { return requestUri; }
        @Override public String getHeader(String name) {
            if ("Origin".equalsIgnoreCase(name)) return origin;
            return null;
        }

        // All other methods CorsFilter does not call:
        @Override public String getAuthType() { throw new UnsupportedOperationException(); }
        @Override public Cookie[] getCookies() { throw new UnsupportedOperationException(); }
        @Override public long getDateHeader(String n) { throw new UnsupportedOperationException(); }
        @Override public Enumeration<String> getHeaders(String n) { throw new UnsupportedOperationException(); }
        @Override public Enumeration<String> getHeaderNames() { throw new UnsupportedOperationException(); }
        @Override public int getIntHeader(String n) { throw new UnsupportedOperationException(); }
        @Override public String getPathInfo() { throw new UnsupportedOperationException(); }
        @Override public String getPathTranslated() { throw new UnsupportedOperationException(); }
        @Override public String getContextPath() { return "/"; }
        @Override public String getQueryString() { throw new UnsupportedOperationException(); }
        @Override public String getRemoteUser() { throw new UnsupportedOperationException(); }
        @Override public boolean isUserInRole(String r) { throw new UnsupportedOperationException(); }
        @Override public Principal getUserPrincipal() { throw new UnsupportedOperationException(); }
        @Override public String getRequestedSessionId() { throw new UnsupportedOperationException(); }
        @Override public StringBuffer getRequestURL() { return new StringBuffer("http://localhost:8080" + requestUri); }
        @Override public String getServletPath() { return requestUri; }
        @Override public HttpSession getSession(boolean c) { throw new UnsupportedOperationException(); }
        @Override public HttpSession getSession() { throw new UnsupportedOperationException(); }
        @Override public String changeSessionId() { throw new UnsupportedOperationException(); }
        @Override public boolean isRequestedSessionIdValid() { throw new UnsupportedOperationException(); }
        @Override public boolean isRequestedSessionIdFromCookie() { throw new UnsupportedOperationException(); }
        @Override public boolean isRequestedSessionIdFromURL() { throw new UnsupportedOperationException(); }
        @Override public boolean authenticate(HttpServletResponse r) { throw new UnsupportedOperationException(); }
        @Override public void login(String u, String p) { throw new UnsupportedOperationException(); }
        @Override public void logout() { throw new UnsupportedOperationException(); }
        @Override public Collection<Part> getParts() { throw new UnsupportedOperationException(); }
        @Override public Part getPart(String n) { throw new UnsupportedOperationException(); }
        @Override public <T extends HttpUpgradeHandler> T upgrade(Class<T> c) { throw new UnsupportedOperationException(); }
        @Override public Object getAttribute(String n) { throw new UnsupportedOperationException(); }
        @Override public Enumeration<String> getAttributeNames() { throw new UnsupportedOperationException(); }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public void setCharacterEncoding(String e) { throw new UnsupportedOperationException(); }
        @Override public int getContentLength() { throw new UnsupportedOperationException(); }
        @Override public long getContentLengthLong() { throw new UnsupportedOperationException(); }
        @Override public String getContentType() { throw new UnsupportedOperationException(); }
        @Override public ServletInputStream getInputStream() { throw new UnsupportedOperationException(); }
        @Override public String getParameter(String n) { throw new UnsupportedOperationException(); }
        @Override public Enumeration<String> getParameterNames() { throw new UnsupportedOperationException(); }
        @Override public String[] getParameterValues(String n) { throw new UnsupportedOperationException(); }
        @Override public Map<String, String[]> getParameterMap() { throw new UnsupportedOperationException(); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public String getScheme() { return "http"; }
        @Override public String getServerName() { return "localhost"; }
        @Override public int getServerPort() { return 8080; }
        @Override public BufferedReader getReader() { throw new UnsupportedOperationException(); }
        @Override public String getRemoteAddr() { return "127.0.0.1"; }
        @Override public String getRemoteHost() { return "localhost"; }
        @Override public void setAttribute(String n, Object o) { throw new UnsupportedOperationException(); }
        @Override public void removeAttribute(String n) { throw new UnsupportedOperationException(); }
        @Override public Locale getLocale() { throw new UnsupportedOperationException(); }
        @Override public Enumeration<Locale> getLocales() { throw new UnsupportedOperationException(); }
        @Override public boolean isSecure() { return false; }
        @Override public RequestDispatcher getRequestDispatcher(String p) { throw new UnsupportedOperationException(); }
        @Override public int getRemotePort() { return 0; }
        @Override public String getLocalName() { return "localhost"; }
        @Override public String getLocalAddr() { return "127.0.0.1"; }
        @Override public int getLocalPort() { return 8080; }
        @Override public ServletContext getServletContext() { throw new UnsupportedOperationException(); }
        @Override public AsyncContext startAsync() { throw new UnsupportedOperationException(); }
        @Override public AsyncContext startAsync(ServletRequest q, ServletResponse s) { throw new UnsupportedOperationException(); }
        @Override public boolean isAsyncStarted() { return false; }
        @Override public boolean isAsyncSupported() { return false; }
        @Override public AsyncContext getAsyncContext() { throw new UnsupportedOperationException(); }
        @Override public DispatcherType getDispatcherType() { return DispatcherType.REQUEST; }
        @Override public String getRequestId() { return "test-request-cors-security"; }
        @Override public String getProtocolRequestId() { throw new UnsupportedOperationException(); }
        @Override public ServletConnection getServletConnection() { throw new UnsupportedOperationException(); }
    }

    /**
     * Records headers and status code set by CorsFilter. Only methods called
     * by CorsFilter have functional implementations; all others throw to surface
     * unexpected calls immediately.
     */
    static final class RecordingResponse implements HttpServletResponse {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private int status = SC_OK;

        public String getHeader(String name) { return headers.get(name); }
        public int getStatus() { return status; }

        @Override public void setHeader(String name, String value) { headers.put(name, value); }
        @Override public void setStatus(int sc) { this.status = sc; }
        @Override public void addHeader(String name, String value) {
            headers.merge(name, value, (a, b) -> a + ", " + b);
        }
        @Override public boolean containsHeader(String name) { return headers.containsKey(name); }
        @Override public Collection<String> getHeaders(String name) {
            String v = headers.get(name);
            return v != null ? Collections.singletonList(v) : Collections.emptyList();
        }
        @Override public Collection<String> getHeaderNames() { return headers.keySet(); }
        @Override public void setIntHeader(String name, int value) { headers.put(name, String.valueOf(value)); }
        @Override public void addIntHeader(String name, int value) {
            headers.merge(name, String.valueOf(value), (a, b) -> a + ", " + b);
        }
        @Override public void addCookie(Cookie c) { throw new UnsupportedOperationException(); }
        @Override public String encodeURL(String url) { throw new UnsupportedOperationException(); }
        @Override public String encodeRedirectURL(String url) { throw new UnsupportedOperationException(); }
        @Override public void sendError(int sc, String msg) { throw new UnsupportedOperationException(); }
        @Override public void sendError(int sc) { throw new UnsupportedOperationException(); }
        @Override public void sendRedirect(String loc) { throw new UnsupportedOperationException(); }
        @Override public void setDateHeader(String name, long date) { throw new UnsupportedOperationException(); }
        @Override public void addDateHeader(String name, long date) { throw new UnsupportedOperationException(); }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public String getContentType() { return "text/plain"; }
        @Override public ServletOutputStream getOutputStream() { throw new UnsupportedOperationException(); }
        @Override public PrintWriter getWriter() { throw new UnsupportedOperationException(); }
        @Override public void setCharacterEncoding(String charset) { throw new UnsupportedOperationException(); }
        @Override public void setContentLength(int len) { throw new UnsupportedOperationException(); }
        @Override public void setContentLengthLong(long len) { throw new UnsupportedOperationException(); }
        @Override public void setContentType(String type) { throw new UnsupportedOperationException(); }
        @Override public void setBufferSize(int size) { throw new UnsupportedOperationException(); }
        @Override public int getBufferSize() { throw new UnsupportedOperationException(); }
        @Override public void flushBuffer() { throw new UnsupportedOperationException(); }
        @Override public void resetBuffer() { throw new UnsupportedOperationException(); }
        @Override public boolean isCommitted() { return false; }
        @Override public void reset() { headers.clear(); status = SC_OK; }
        @Override public void setLocale(Locale loc) { throw new UnsupportedOperationException(); }
        @Override public Locale getLocale() { throw new UnsupportedOperationException(); }
    }

    /**
     * Concrete FilterChain that records whether doFilter was invoked.
     */
    static final class RecordingChain implements FilterChain {
        private boolean invoked = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            this.invoked = true;
        }

        public boolean wasInvoked() { return invoked; }
    }

    /**
     * Minimal ServletContext for FilterConfig. Only methods CorsFilter calls during
     * init (logging) have functional implementations.
     */
    private static final class MinimalServletContext implements ServletContext {
        @Override public String getContextPath() { return "/"; }
        @Override public int getMajorVersion() { return 6; }
        @Override public int getMinorVersion() { return 1; }
        @Override public int getEffectiveMajorVersion() { return 6; }
        @Override public int getEffectiveMinorVersion() { return 1; }
        @Override public String getServerInfo() { return "YawlTestServer/1.0"; }
        @Override public String getServletContextName() { return "yawl-test"; }
        @Override public void log(String msg) { /* intentional no-op */ }
        @Override public void log(String message, Throwable throwable) { /* intentional no-op */ }
        @Override public String getInitParameter(String name) { return null; }
        @Override public Enumeration<String> getInitParameterNames() { return Collections.emptyEnumeration(); }
        @Override public boolean setInitParameter(String name, String value) { return false; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public Enumeration<String> getAttributeNames() { return Collections.emptyEnumeration(); }
        @Override public void setAttribute(String name, Object object) { throw new UnsupportedOperationException(); }
        @Override public void removeAttribute(String name) { throw new UnsupportedOperationException(); }
        @Override public ClassLoader getClassLoader() { return Thread.currentThread().getContextClassLoader(); }
        @Override public String getVirtualServerName() { return "localhost"; }
        @Override public ServletContext getContext(String path) { throw new UnsupportedOperationException(); }
        @Override public String getMimeType(String file) { throw new UnsupportedOperationException(); }
        @Override public Set<String> getResourcePaths(String path) { throw new UnsupportedOperationException(); }
        @Override public java.net.URL getResource(String path) { throw new UnsupportedOperationException(); }
        @Override public InputStream getResourceAsStream(String path) { throw new UnsupportedOperationException(); }
        @Override public RequestDispatcher getRequestDispatcher(String path) { throw new UnsupportedOperationException(); }
        @Override public RequestDispatcher getNamedDispatcher(String name) { throw new UnsupportedOperationException(); }
        @Override public String getRealPath(String path) { throw new UnsupportedOperationException(); }
        @Override public ServletRegistration.Dynamic addServlet(String n, String c) { throw new UnsupportedOperationException(); }
        @Override public ServletRegistration.Dynamic addServlet(String n, Servlet s) { throw new UnsupportedOperationException(); }
        @Override public ServletRegistration.Dynamic addServlet(String n, Class<? extends Servlet> c) { throw new UnsupportedOperationException(); }
        @Override public ServletRegistration.Dynamic addJspFile(String n, String f) { throw new UnsupportedOperationException(); }
        @Override public <T extends Servlet> T createServlet(Class<T> c) { throw new UnsupportedOperationException(); }
        @Override public ServletRegistration getServletRegistration(String n) { throw new UnsupportedOperationException(); }
        @Override public Map<String, ? extends ServletRegistration> getServletRegistrations() { throw new UnsupportedOperationException(); }
        @Override public FilterRegistration.Dynamic addFilter(String n, String c) { throw new UnsupportedOperationException(); }
        @Override public FilterRegistration.Dynamic addFilter(String n, Filter f) { throw new UnsupportedOperationException(); }
        @Override public FilterRegistration.Dynamic addFilter(String n, Class<? extends Filter> c) { throw new UnsupportedOperationException(); }
        @Override public <T extends Filter> T createFilter(Class<T> c) { throw new UnsupportedOperationException(); }
        @Override public FilterRegistration getFilterRegistration(String n) { throw new UnsupportedOperationException(); }
        @Override public Map<String, ? extends FilterRegistration> getFilterRegistrations() { throw new UnsupportedOperationException(); }
        @Override public SessionCookieConfig getSessionCookieConfig() { throw new UnsupportedOperationException(); }
        @Override public void setSessionTrackingModes(Set<SessionTrackingMode> m) { throw new UnsupportedOperationException(); }
        @Override public Set<SessionTrackingMode> getDefaultSessionTrackingModes() { throw new UnsupportedOperationException(); }
        @Override public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() { throw new UnsupportedOperationException(); }
        @Override public void addListener(String c) { throw new UnsupportedOperationException(); }
        @Override public <T extends EventListener> void addListener(T t) { throw new UnsupportedOperationException(); }
        @Override public void addListener(Class<? extends EventListener> c) { throw new UnsupportedOperationException(); }
        @Override public <T extends EventListener> T createListener(Class<T> c) { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.descriptor.JspConfigDescriptor getJspConfigDescriptor() { throw new UnsupportedOperationException(); }
        @Override public void declareRoles(String... roles) { throw new UnsupportedOperationException(); }
        @Override public int getSessionTimeout() { throw new UnsupportedOperationException(); }
        @Override public void setSessionTimeout(int t) { throw new UnsupportedOperationException(); }
        @Override public String getRequestCharacterEncoding() { throw new UnsupportedOperationException(); }
        @Override public void setRequestCharacterEncoding(String e) { throw new UnsupportedOperationException(); }
        @Override public String getResponseCharacterEncoding() { throw new UnsupportedOperationException(); }
        @Override public void setResponseCharacterEncoding(String e) { throw new UnsupportedOperationException(); }
    }
}
