package org.yawlfoundation.yawl.authentication;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsrfTokenManager.
 *
 * Tests cover:
 * - Token generation and storage in a real in-memory session
 * - Token retrieval and auto-generation
 * - Constant-time validation (FIX #1)
 * - Token invalidation
 * - Null safety
 *
 * Chicago TDD: all tests use real objects (InMemorySession, InMemoryRequest)
 * backed by real Java HashMap state. No mocks, no Mockito.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestCsrfTokenManager {

    private InMemorySession session;
    private InMemoryRequest request;

    @BeforeEach
    public void setUp() {
        session = new InMemorySession();
        request = new InMemoryRequest(session);
    }

    @Test
    public void testGenerateTokenReturnsNonNullNonEmptyString() {
        String token = CsrfTokenManager.generateToken(session);
        assertNotNull(token, "Generated token must not be null");
        assertFalse(token.isEmpty(), "Generated token must not be empty");
    }

    @Test
    public void testGenerateTokenStoresTokenInSession() {
        String token = CsrfTokenManager.generateToken(session);
        Object stored = session.getAttribute("_csrf_token");
        assertEquals(token, stored,
                "Generated token must be stored in session under '_csrf_token'");
    }

    @Test
    public void testGenerateTokenProducesBase64UrlEncoding() {
        String token = CsrfTokenManager.generateToken(session);
        // Base64URL chars: A-Z a-z 0-9 - _  (no + / =)
        assertTrue(token.matches("[A-Za-z0-9_-]+"),
                "Token must be Base64URL-encoded (no +, /, or = characters)");
    }

    @Test
    public void testGenerateTokenProducesUniqueTokens() {
        String token1 = CsrfTokenManager.generateToken(session);
        String token2 = CsrfTokenManager.generateToken(new InMemorySession());
        assertNotEquals(token1, token2,
                "Each generateToken() call must produce a unique token (SecureRandom)");
    }

    @Test
    public void testGenerateTokenNullSessionThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> CsrfTokenManager.generateToken(null),
                "generateToken(null) must throw IllegalArgumentException");
    }

    @Test
    public void testGetTokenReturnsExistingSessionToken() {
        String existingToken = "pre-existing-csrf-token-value";
        session.setAttribute("_csrf_token", existingToken);

        String token = CsrfTokenManager.getToken(session);
        assertEquals(existingToken, token,
                "getToken() must return the existing token from the session");
    }

    @Test
    public void testGetTokenAutoGeneratesWhenNoTokenInSession() {
        assertNull(session.getAttribute("_csrf_token"),
                "Session must have no token before auto-generation");

        String token = CsrfTokenManager.getToken(session);
        assertNotNull(token,
                "getToken() must auto-generate a token when none exists in session");
        assertFalse(token.isEmpty(), "Auto-generated token must not be empty");
    }

    @Test
    public void testGetTokenAutoGenerationPersistsInSession() {
        CsrfTokenManager.getToken(session);
        assertNotNull(session.getAttribute("_csrf_token"),
                "Auto-generated token must be stored in session");
    }

    @Test
    public void testGetTokenNullSessionThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> CsrfTokenManager.getToken(null),
                "getToken(null) must throw IllegalArgumentException");
    }

    @Test
    public void testValidateTokenAcceptsMatchingParameterToken() {
        String token = CsrfTokenManager.generateToken(session);
        request.setParameter("_csrf", token);

        assertTrue(CsrfTokenManager.validateToken(request),
                "validateToken() must return true when '_csrf' parameter matches session token");
    }

    @Test
    public void testValidateTokenAcceptsMatchingHeaderToken() {
        String token = CsrfTokenManager.generateToken(session);
        // No parameter set — only the header carries the token
        request.setHeader("X-CSRF-Token", token);

        assertTrue(CsrfTokenManager.validateToken(request),
                "validateToken() must return true when 'X-CSRF-Token' header matches session token");
    }

    @Test
    public void testValidateTokenParameterTakesPrecedenceOverHeader() {
        String sessionToken = CsrfTokenManager.generateToken(session);
        // Parameter matches; header deliberately wrong
        request.setParameter("_csrf", sessionToken);
        request.setHeader("X-CSRF-Token", "wrong-header-value");

        assertTrue(CsrfTokenManager.validateToken(request),
                "validateToken() must accept matching parameter even when header is wrong");
    }

    @Test
    public void testValidateTokenRejectsMismatchedToken() {
        CsrfTokenManager.generateToken(session);
        request.setParameter("_csrf", "completely-wrong-token");

        assertFalse(CsrfTokenManager.validateToken(request),
                "validateToken() must return false when submitted token does not match session token");
    }

    @Test
    public void testValidateTokenRejectsRequestWithNoSession() {
        InMemoryRequest requestWithoutSession = new InMemoryRequest(null);
        requestWithoutSession.setParameter("_csrf", "some-token");

        assertFalse(CsrfTokenManager.validateToken(requestWithoutSession),
                "validateToken() must return false when request has no session");
    }

    @Test
    public void testValidateTokenRejectsWhenNoTokenInSession() {
        // Session exists but has no CSRF token stored
        request.setParameter("_csrf", "any-token-value");

        assertFalse(CsrfTokenManager.validateToken(request),
                "validateToken() must return false when session has no stored token");
    }

    @Test
    public void testValidateTokenRejectsWhenNoTokenSubmitted() {
        CsrfTokenManager.generateToken(session);
        // No parameter and no header submitted

        assertFalse(CsrfTokenManager.validateToken(request),
                "validateToken() must return false when request submits no token");
    }

    @Test
    public void testValidateTokenConstantTimeComparison() {
        // Verify correct / incorrect behavior for constant-time validation (FIX #1)
        String token = CsrfTokenManager.generateToken(session);

        request.setParameter("_csrf", token);
        assertTrue(CsrfTokenManager.validateToken(request),
                "Correct token must validate (constant-time comparison)");

        // One character off must fail
        String offByOne = token.substring(0, token.length() - 1) + "X";
        request.setParameter("_csrf", offByOne);
        assertFalse(CsrfTokenManager.validateToken(request),
                "Off-by-one token must be rejected (constant-time comparison)");
    }

    @Test
    public void testInvalidateTokenRemovesAttributeFromSession() {
        session.setAttribute("_csrf_token", "some-token");
        CsrfTokenManager.invalidateToken(session);
        assertNull(session.getAttribute("_csrf_token"),
                "invalidateToken() must remove '_csrf_token' from the session");
    }

    @Test
    public void testInvalidateTokenWithNullSessionDoesNotThrow() {
        assertDoesNotThrow(() -> CsrfTokenManager.invalidateToken(null),
                "invalidateToken(null) must not throw any exception");
    }

    @Test
    public void testTokenLengthSufficientForSecurity() {
        String token = CsrfTokenManager.generateToken(session);
        // 32 bytes Base64URL-encoded = 43 chars (without padding)
        // Minimum acceptable length for security
        assertTrue(token.length() >= 40,
                "Token must be at least 40 characters (>= 32 bytes of entropy)");
    }

    // =========================================================================
    //  Real in-memory implementations of Jakarta Servlet interfaces
    //  Backed by real Java HashMap state — no mocking framework used.
    // =========================================================================

    /**
     * Real in-memory HttpSession backed by a HashMap.
     * Attributes are stored and retrieved via actual Java collections,
     * exactly as a real servlet container would manage them.
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
            return "in-memory-session-id";
        }

        @Override
        public long getLastAccessedTime() {
            return System.currentTimeMillis();
        }

        @Override
        public ServletContext getServletContext() {
            throw new UnsupportedOperationException(
                    "InMemorySession.getServletContext: not needed for CSRF token tests");
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            throw new UnsupportedOperationException(
                    "InMemorySession.setMaxInactiveInterval: not needed for CSRF token tests");
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
     * Real in-memory HttpServletRequest backed by HashMaps for parameters and headers.
     * Parameters and headers are stored and retrieved via actual Java collections.
     * Methods not required by CsrfTokenManager throw UnsupportedOperationException.
     */
    static final class InMemoryRequest implements HttpServletRequest {
        private final InMemorySession session;
        private final Map<String, String> parameters = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();

        InMemoryRequest(InMemorySession session) {
            this.session = session;
        }

        void setParameter(String name, String value) {
            parameters.put(name, value);
        }

        void setHeader(String name, String value) {
            headers.put(name, value);
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
        public String getAuthType() {
            return null;
        }

        @Override
        public String getMethod() {
            return "POST";
        }

        @Override
        public String getRequestURI() {
            return "/test/csrf";
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer("http://localhost/test/csrf");
        }

        @Override
        public String getContextPath() {
            return "/yawl";
        }

        @Override
        public String getServletPath() {
            return "/test/csrf";
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
                    "InMemoryRequest.authenticate: not needed for CSRF token tests");
        }

        @Override
        public void login(String username, String password) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.login: not needed for CSRF token tests");
        }

        @Override
        public void logout() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.logout: not needed for CSRF token tests");
        }

        @Override
        public Collection<Part> getParts() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getParts: not needed for CSRF token tests");
        }

        @Override
        public Part getPart(String name) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getPart: not needed for CSRF token tests");
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.upgrade: not needed for CSRF token tests");
        }

        @Override
        public HttpServletMapping getHttpServletMapping() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getHttpServletMapping: not needed for CSRF token tests");
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
                    "InMemoryRequest.setCharacterEncoding: not needed for CSRF token tests");
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
                    "InMemoryRequest.getInputStream: not needed for CSRF token tests");
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
                    "InMemoryRequest.getReader: not needed for CSRF token tests");
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
                    "InMemoryRequest.setAttribute: not needed for CSRF token tests");
        }

        @Override
        public void removeAttribute(String name) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.removeAttribute: not needed for CSRF token tests");
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
                    "InMemoryRequest.getRequestDispatcher: not needed for CSRF token tests");
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
                    "InMemoryRequest.getServletContext: not needed for CSRF token tests");
        }

        @Override
        public AsyncContext startAsync() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.startAsync: not needed for CSRF token tests");
        }

        @Override
        public AsyncContext startAsync(ServletRequest req, ServletResponse resp) {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.startAsync(req,resp): not needed for CSRF token tests");
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
                    "InMemoryRequest.getAsyncContext: not needed for CSRF token tests");
        }

        @Override
        public DispatcherType getDispatcherType() {
            return DispatcherType.REQUEST;
        }

        @Override
        public String getRequestId() {
            return "in-memory-request-id";
        }

        @Override
        public String getProtocolRequestId() {
            return "in-memory-protocol-request-id";
        }

        @Override
        public ServletConnection getServletConnection() {
            throw new UnsupportedOperationException(
                    "InMemoryRequest.getServletConnection: not needed for CSRF token tests");
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
