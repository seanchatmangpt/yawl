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

package org.yawlfoundation.yawl.soc2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.authentication.CsrfProtectionFilter;
import org.yawlfoundation.yawl.authentication.CsrfTokenManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SOC2 CC6.1 / CC6.6 - CSRF Protection Filter Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC6.1 - Logical access security measures restrict access to information assets.
 * CC6.6 - The entity implements logical access security measures to protect against
 *         threats from sources outside its system boundaries (CSRF attacks).
 *
 * <p>Covers:
 * <ul>
 *   <li>POST requests blocked without valid CSRF token (403)</li>
 *   <li>PUT requests blocked without valid CSRF token (403)</li>
 *   <li>DELETE requests blocked without valid CSRF token (403)</li>
 *   <li>PATCH requests blocked without valid CSRF token (403)</li>
 *   <li>GET requests bypass CSRF check unconditionally</li>
 *   <li>HEAD requests bypass CSRF check unconditionally</li>
 *   <li>OPTIONS requests bypass CSRF check unconditionally</li>
 *   <li>TRACE requests bypass CSRF check unconditionally</li>
 *   <li>Valid CSRF token in parameter is accepted</li>
 *   <li>Valid CSRF token in X-CSRF-Token header is accepted</li>
 *   <li>Invalid/wrong token produces 403</li>
 *   <li>Missing token produces 403 with JSON error body</li>
 *   <li>Token invalidation causes subsequent request to be rejected</li>
 *   <li>Excluded paths bypass CSRF check (/health, /metrics, /api/)</li>
 *   <li>Path normalization prevents bypass via double slashes</li>
 *   <li>Path normalization prevents bypass via /./ sequences</li>
 *   <li>Case-insensitive path exclusion matching</li>
 *   <li>Concurrent token validation is thread-safe</li>
 *   <li>Filter init with no excludedPaths param works correctly</li>
 *   <li>Filter init with empty excludedPaths param works correctly</li>
 * </ul>
 *
 * <p>Chicago TDD: real CsrfProtectionFilter with real HttpServletRequest
 * proxies and real CsrfTokenManager. No mocks.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class CsrfProtectionFilterTest extends TestCase {

    public CsrfProtectionFilterTest(String name) {
        super(name);
    }

    // =========================================================================
    // Test infrastructure: proxy builders
    // =========================================================================

    /**
     * In-memory HttpSession backed by a HashMap.
     */
    private static HttpSession newSession() {
        Map<String, Object> attrs = new HashMap<>();
        return (HttpSession) Proxy.newProxyInstance(
            CsrfProtectionFilterTest.class.getClassLoader(),
            new Class<?>[]{ HttpSession.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getAttribute"    -> { return attrs.get((String) args[0]); }
                    case "setAttribute"    -> { attrs.put((String) args[0], args[1]); return null; }
                    case "removeAttribute" -> { attrs.remove((String) args[0]); return null; }
                    default                -> { return null; }
                }
            }
        );
    }

    /**
     * Builds a real HttpServletRequest proxy for state-changing method tests.
     *
     * @param method      HTTP method (POST, PUT, DELETE, etc.)
     * @param uri         request URI
     * @param session     the session (may be null to simulate no session)
     * @param paramToken  value for "_csrf" request parameter (null = absent)
     * @param headerToken value for "X-CSRF-Token" header (null = absent)
     */
    private static HttpServletRequest newRequest(String method, String uri,
                                                  HttpSession session,
                                                  String paramToken,
                                                  String headerToken) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            CsrfProtectionFilterTest.class.getClassLoader(),
            new Class<?>[]{ HttpServletRequest.class },
            (proxy, m, args) -> switch (m.getName()) {
                case "getMethod"     -> method;
                case "getRequestURI" -> uri;
                case "getSession"    -> session;
                case "getParameter"  -> {
                    String name = (String) args[0];
                    yield "_csrf".equals(name) ? paramToken : null;
                }
                case "getHeader"     -> {
                    String name = (String) args[0];
                    yield "X-CSRF-Token".equals(name) ? headerToken : null;
                }
                default -> null;
            }
        );
    }

    /**
     * Captured response: tracks status code and body written.
     */
    private static final class CapturedResponse {
        int status = 200;
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);
    }

    /**
     * Builds an HttpServletResponse proxy that captures status + body.
     */
    private static HttpServletResponse newResponse(CapturedResponse captured) {
        return (HttpServletResponse) Proxy.newProxyInstance(
            CsrfProtectionFilterTest.class.getClassLoader(),
            new Class<?>[]{ HttpServletResponse.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setStatus"      -> { captured.status = (int) args[0]; return null; }
                    case "setContentType" -> { return null; }
                    case "getWriter"      -> { return captured.writer; }
                    default               -> { return null; }
                }
            }
        );
    }

    /** Records whether the filter chain was invoked. */
    private static final class ChainCapture implements FilterChain {
        boolean invoked = false;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req,
                             jakarta.servlet.ServletResponse res)
                throws IOException, ServletException {
            invoked = true;
        }
    }

    /**
     * Builds a FilterConfig proxy with a configurable excludedPaths init parameter.
     *
     * @param excludedPaths comma-separated excluded paths, or null for none
     */
    private static FilterConfig newFilterConfig(String excludedPaths) {
        return (FilterConfig) Proxy.newProxyInstance(
            CsrfProtectionFilterTest.class.getClassLoader(),
            new Class<?>[]{ FilterConfig.class },
            (proxy, method, args) -> {
                if ("getInitParameter".equals(method.getName())) {
                    String paramName = (String) args[0];
                    if ("excludedPaths".equals(paramName)) {
                        return excludedPaths;
                    }
                }
                return null;
            }
        );
    }

    /**
     * Creates a CsrfProtectionFilter initialised with the given excluded paths.
     *
     * @param excludedPaths comma-separated paths, or null for none
     */
    private static CsrfProtectionFilter newFilter(String excludedPaths)
            throws ServletException {
        CsrfProtectionFilter filter = new CsrfProtectionFilter();
        filter.init(newFilterConfig(excludedPaths));
        return filter;
    }

    // =========================================================================
    // CC6.6 - State-changing methods blocked without token
    // =========================================================================

    /**
     * SOC2 CC6.6: POST to unprotected URI without CSRF token must return 403.
     * CSRF is the primary vector for state-changing attacks on browser clients.
     */
    public void testPostWithoutCsrfTokenReturns403() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);  // Token in session but NOT in request

        HttpServletRequest request = newRequest("POST", "/workflow/launch", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        HttpServletResponse response = newResponse(captured);
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, response, chain);

        assertEquals("POST without CSRF token must return 403", 403, captured.status);
        assertFalse("Filter chain must not be invoked on rejected POST", chain.invoked);
    }

    /**
     * SOC2 CC6.6: PUT requests without CSRF token must return 403.
     */
    public void testPutWithoutCsrfTokenReturns403() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("PUT", "/workflow/123", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        HttpServletResponse response = newResponse(captured);
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, response, chain);

        assertEquals("PUT without CSRF token must return 403", 403, captured.status);
        assertFalse("Filter chain must not be invoked on rejected PUT", chain.invoked);
    }

    /**
     * SOC2 CC6.6: DELETE requests without CSRF token must return 403.
     */
    public void testDeleteWithoutCsrfTokenReturns403() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("DELETE", "/workflow/123", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        HttpServletResponse response = newResponse(captured);
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, response, chain);

        assertEquals("DELETE without CSRF token must return 403", 403, captured.status);
        assertFalse("Filter chain must not be invoked on rejected DELETE", chain.invoked);
    }

    /**
     * SOC2 CC6.6: PATCH requests without CSRF token must return 403.
     */
    public void testPatchWithoutCsrfTokenReturns403() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("PATCH", "/workflow/123", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        HttpServletResponse response = newResponse(captured);
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, response, chain);

        assertEquals("PATCH without CSRF token must return 403", 403, captured.status);
        assertFalse("Filter chain must not be invoked on rejected PATCH", chain.invoked);
    }

    // =========================================================================
    // CC6.6 - Safe methods bypass CSRF check
    // =========================================================================

    /**
     * SOC2 CC6.6: GET requests must bypass CSRF protection entirely.
     * Read-only methods are not subject to CSRF vulnerability.
     */
    public void testGetRequestBypassesCsrfCheck() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        // No session, no token - GET should still pass
        HttpServletRequest request = newRequest("GET", "/workflow/list", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        HttpServletResponse response = newResponse(captured);
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, response, chain);

        assertTrue("GET request must reach filter chain", chain.invoked);
        assertEquals("GET must not set error status", 200, captured.status);
    }

    /**
     * SOC2 CC6.6: HEAD requests must bypass CSRF protection.
     */
    public void testHeadRequestBypassesCsrfCheck() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpServletRequest request = newRequest("HEAD", "/workflow/status", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("HEAD request must reach filter chain", chain.invoked);
    }

    /**
     * SOC2 CC6.6: OPTIONS requests must bypass CSRF protection.
     * OPTIONS is used for CORS preflight which must not be blocked.
     */
    public void testOptionsRequestBypassesCsrfCheck() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpServletRequest request = newRequest("OPTIONS", "/workflow/launch", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("OPTIONS request must reach filter chain (CORS preflight)", chain.invoked);
    }

    /**
     * SOC2 CC6.6: TRACE requests must bypass CSRF protection.
     */
    public void testTraceRequestBypassesCsrfCheck() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpServletRequest request = newRequest("TRACE", "/workflow/status", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("TRACE request must reach filter chain", chain.invoked);
    }

    // =========================================================================
    // CC6.1 - Valid tokens are accepted
    // =========================================================================

    /**
     * SOC2 CC6.1: POST with valid CSRF token in "_csrf" parameter must succeed.
     */
    public void testPostWithValidParameterTokenIsAccepted() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        String token = CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("POST", "/workflow/launch", session, token, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("POST with valid parameter token must reach filter chain", chain.invoked);
        assertEquals("Valid token must not set error status", 200, captured.status);
    }

    /**
     * SOC2 CC6.1: POST with valid CSRF token in "X-CSRF-Token" header must succeed.
     */
    public void testPostWithValidHeaderTokenIsAccepted() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        String token = CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("POST", "/workflow/launch", session, null, token);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("POST with valid X-CSRF-Token header must reach filter chain", chain.invoked);
    }

    /**
     * SOC2 CC6.6: Wrong token value in POST must return 403.
     * Prevents CSRF attacks with fabricated or stolen tokens.
     */
    public void testPostWithWrongTokenReturns403() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);  // store a real token in session

        HttpServletRequest request = newRequest("POST", "/workflow/launch",
                session, "attacker-forged-token", null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertEquals("Wrong CSRF token must return 403", 403, captured.status);
        assertFalse("Filter chain must not be invoked for wrong token", chain.invoked);
    }

    /**
     * SOC2 CC6.6: 403 response must include a JSON error body for API clients.
     * Machine-readable response body helps API clients distinguish CSRF failures.
     */
    public void testRejectedRequestReturnsJsonErrorBody() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("POST", "/workflow/launch", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        String body = captured.body.toString();
        assertNotNull("Response body must not be null", body);
        assertFalse("Response body must not be empty", body.isEmpty());
        assertTrue("Response body must contain 'error' key for JSON",
                body.contains("error"));
        assertTrue("Response body must contain CSRF-related error text",
                body.toLowerCase().contains("csrf") || body.toLowerCase().contains("token"));
    }

    /**
     * SOC2 CC6.6: Request with no session at all must return 403.
     * No session means no stored token = CSRF validation cannot succeed.
     */
    public void testPostWithNoSessionReturns403() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);

        // No session passed - CsrfTokenManager.validateToken will return false
        HttpServletRequest request = newRequest("POST", "/workflow/launch",
                null, "any-token", null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertEquals("POST with no session must return 403", 403, captured.status);
    }

    // =========================================================================
    // CC6.1 - Token invalidation
    // =========================================================================

    /**
     * SOC2 CC6.1: After token invalidation, subsequent POST must be rejected.
     * Used on logout to prevent token reuse attacks.
     */
    public void testTokenInvalidationCausesSubsequentPostToBeRejected() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        HttpSession session = newSession();
        String token = CsrfTokenManager.generateToken(session);

        // First request with valid token - must succeed
        HttpServletRequest req1 = newRequest("POST", "/workflow/launch", session, token, null);
        ChainCapture chain1 = new ChainCapture();
        filter.doFilter(req1, newResponse(new CapturedResponse()), chain1);
        assertTrue("First POST with valid token must reach chain", chain1.invoked);

        // Invalidate the token (e.g., on logout)
        CsrfTokenManager.invalidateToken(session);

        // Second request with same token - must be rejected
        HttpServletRequest req2 = newRequest("POST", "/workflow/launch", session, token, null);
        CapturedResponse captured2 = new CapturedResponse();
        ChainCapture chain2 = new ChainCapture();
        filter.doFilter(req2, newResponse(captured2), chain2);

        assertEquals("POST with invalidated token must return 403", 403, captured2.status);
        assertFalse("Filter chain must not be invoked after token invalidation", chain2.invoked);
    }

    // =========================================================================
    // CC6.6 - Excluded paths (safe-list)
    // =========================================================================

    /**
     * SOC2 CC6.6: Health check endpoint must bypass CSRF to enable monitoring.
     * External health checks cannot provide CSRF tokens.
     */
    public void testHealthCheckPathBypassesCsrfCheck() throws Exception {
        CsrfProtectionFilter filter = newFilter("/health,/metrics");

        // POST to /health with no session/token - should be allowed
        HttpServletRequest request = newRequest("POST", "/health", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("POST to /health must bypass CSRF (excluded path)", chain.invoked);
    }

    /**
     * SOC2 CC6.6: Metrics endpoint POST must bypass CSRF when excluded.
     */
    public void testMetricsPathBypassesCsrfCheck() throws Exception {
        CsrfProtectionFilter filter = newFilter("/health,/metrics");

        HttpServletRequest request = newRequest("POST", "/metrics", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("POST to /metrics must bypass CSRF (excluded path)", chain.invoked);
    }

    /**
     * SOC2 CC6.6: API prefix exclusion must cover all sub-paths.
     */
    public void testApiPrefixExclusionCoversSubPaths() throws Exception {
        CsrfProtectionFilter filter = newFilter("/api/");

        HttpServletRequest request = newRequest("POST", "/api/workflow/launch", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertTrue("POST to /api/workflow/launch must bypass CSRF (prefix excluded)", chain.invoked);
    }

    /**
     * SOC2 CC6.6: Non-excluded path must still require CSRF token even when
     * other paths are excluded.
     */
    public void testNonExcludedPathStillRequiresCsrfToken() throws Exception {
        CsrfProtectionFilter filter = newFilter("/health,/metrics");

        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        // /workflow/launch is NOT excluded - should require token
        HttpServletRequest request = newRequest("POST", "/workflow/launch", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertEquals("POST to non-excluded path must return 403 without token",
                403, captured.status);
        assertFalse("Chain must not be invoked for unexcluded path without token", chain.invoked);
    }

    // =========================================================================
    // CC6.6 - FIX #5: Path normalization prevents bypass
    // =========================================================================

    /**
     * SOC2 CC6.6 FIX #5: Double-slash path must still match the excluded path.
     * Attackers cannot bypass exclusion via //health to a non-normalised filter.
     */
    public void testDoubleSlashPathNormalizationMatchesExclusion() throws Exception {
        CsrfProtectionFilter filter = newFilter("/health");

        // Attacker attempts //health to bypass a naive filter
        HttpServletRequest request = newRequest("POST", "//health", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        // After normalization //health -> /health, which IS excluded
        assertTrue("Double-slash path must normalize and match exclusion", chain.invoked);
    }

    /**
     * SOC2 CC6.6 FIX #5: Dot-segment path must still match the excluded path.
     * Attackers cannot bypass exclusion via /./health.
     */
    public void testDotSegmentPathNormalizationMatchesExclusion() throws Exception {
        CsrfProtectionFilter filter = newFilter("/health");

        // Attacker attempts /./health to bypass a naive filter
        HttpServletRequest request = newRequest("POST", "/./health", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        // After normalization /./health -> /health, which IS excluded
        assertTrue("Dot-segment path must normalize and match exclusion", chain.invoked);
    }

    /**
     * SOC2 CC6.6 FIX #5: Case-insensitive path matching prevents bypass via /HEALTH.
     */
    public void testCaseInsensitivePathNormalizationMatchesExclusion() throws Exception {
        CsrfProtectionFilter filter = newFilter("/health");

        // Attacker attempts /HEALTH to bypass a case-sensitive filter
        HttpServletRequest request = newRequest("POST", "/HEALTH", null, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        // After lower-casing /HEALTH -> /health, which IS excluded
        assertTrue("Uppercase path must match lowercase exclusion (case-insensitive)", chain.invoked);
    }

    // =========================================================================
    // CC6.1 - Filter init variants
    // =========================================================================

    /**
     * SOC2 CC6.1: Filter with null excludedPaths init parameter initializes without error.
     * Default state: all state-changing methods require CSRF tokens.
     */
    public void testInitWithNullExcludedPathsSucceeds() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);

        // POST still blocked (no token) - proves filter is protecting normally
        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("POST", "/test", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertEquals("Filter with null excludedPaths must still block invalid POST",
                403, captured.status);
    }

    /**
     * SOC2 CC6.1: Filter with empty excludedPaths string initializes without error.
     */
    public void testInitWithEmptyExcludedPathsSucceeds() throws Exception {
        CsrfProtectionFilter filter = newFilter("");

        HttpSession session = newSession();
        CsrfTokenManager.generateToken(session);

        HttpServletRequest request = newRequest("POST", "/test", session, null, null);
        CapturedResponse captured = new CapturedResponse();
        ChainCapture chain = new ChainCapture();

        filter.doFilter(request, newResponse(captured), chain);

        assertEquals("Filter with empty excludedPaths must still block invalid POST",
                403, captured.status);
    }

    /**
     * SOC2 CC6.1: Filter.destroy() must not throw any exception.
     */
    public void testDestroyDoesNotThrow() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        try {
            filter.destroy();
        } catch (Exception e) {
            fail("destroy() must not throw: " + e.getMessage());
        }
    }

    // =========================================================================
    // CC6.6 - Concurrent token validation (thread safety)
    // =========================================================================

    /**
     * SOC2 CC6.6: CSRF filter must be thread-safe for concurrent requests.
     * Multiple concurrent state-changing requests with valid tokens must all succeed.
     */
    public void testConcurrentTokenValidationIsThreadSafe() throws Exception {
        CsrfProtectionFilter filter = newFilter(null);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    HttpSession session = newSession();
                    String token = CsrfTokenManager.generateToken(session);

                    HttpServletRequest request = newRequest("POST", "/workflow/launch",
                            session, token, null);
                    CapturedResponse captured = new CapturedResponse();
                    ChainCapture chain = new ChainCapture();
                    filter.doFilter(request, newResponse(captured), chain);

                    if (chain.invoked) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Concurrent test must complete within 5 seconds",
                doneLatch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals("All concurrent requests with valid tokens must succeed",
                threadCount, successCount.get());
        assertEquals("No concurrent request must fail with valid token", 0, failCount.get());
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(CsrfProtectionFilterTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
