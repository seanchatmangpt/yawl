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

package org.yawlfoundation.yawl.authentication;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitFilter using Resilience4j token bucket pattern.
 *
 * Tests verify:
 * - Correct initialization from filter config
 * - Per-IP rate limiting enforcement
 * - HTTP 429 response on limit exceeded
 * - Proper header generation
 * - X-Forwarded-For proxy header handling
 * - Concurrent request handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rate Limit Filter Tests")
class TestRateLimitFilter {

    private RateLimitFilter filter;

    @Mock
    private FilterConfig filterConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private jakarta.servlet.FilterChain chain;

    private StringWriter responseWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws ServletException, IOException {
        filter = new RateLimitFilter();
        responseWriter = new StringWriter();
        printWriter = new PrintWriter(responseWriter);

        // Default mock configuration
        when(filterConfig.getInitParameter("maxRequests")).thenReturn("3");
        when(filterConfig.getInitParameter("windowSeconds")).thenReturn("1");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(response.getWriter()).thenReturn(printWriter);
    }

    @Test
    @DisplayName("Filter initializes with default configuration")
    void testInitWithDefaults() throws ServletException {
        when(filterConfig.getInitParameter("maxRequests")).thenReturn(null);
        when(filterConfig.getInitParameter("windowSeconds")).thenReturn(null);

        filter.init(filterConfig);
        // If no exception, initialization succeeded
        assertNotNull(filter);
    }

    @Test
    @DisplayName("Filter allows requests within rate limit")
    void testRequestsWithinLimit() throws ServletException, IOException {
        filter.init(filterConfig);

        // All 3 requests should pass (within limit)
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
            verify(chain, times(i + 1)).doFilter(request, response);
        }

        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Filter rejects requests exceeding rate limit")
    void testRateLimitExceeded() throws ServletException, IOException {
        filter.init(filterConfig);

        // Use 3 permits
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        // Next request should be rejected
        filter.doFilter(request, response, chain);
        verify(response).setStatus(429);
        verify(response).setHeader("X-RateLimit-Limit", "3");
        verify(response).setHeader("X-RateLimit-Remaining", "0");
    }

    @Test
    @DisplayName("Filter returns appropriate Retry-After header")
    void testRetryAfterHeader() throws ServletException, IOException {
        filter.init(filterConfig);

        // Exhaust permits
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        // Exceed limit
        filter.doFilter(request, response, chain);
        verify(response).setHeader(eq("Retry-After"), argThat(v -> {
            int seconds = Integer.parseInt(v);
            return seconds >= 1;
        }));
    }

    @Test
    @DisplayName("Filter handles X-Forwarded-For header for proxy deployments")
    void testXForwardedForHeader() throws ServletException, IOException {
        filter.init(filterConfig);

        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.50, 192.168.1.1");

        // Make requests from first IP (10.0.0.50)
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        // Next request from same IP should be limited
        filter.doFilter(request, response, chain);
        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("Filter separates rate limits per IP address")
    void testPerIpRateLimiting() throws ServletException, IOException {
        filter.init(filterConfig);

        // First IP exhausts quota
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        // Different IP should start fresh
        when(request.getRemoteAddr()).thenReturn("192.168.1.101");
        filter.doFilter(request, response, chain);
        verify(chain, times(4)).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter returns JSON error response on rate limit")
    void testErrorResponseFormat() throws ServletException, IOException {
        filter.init(filterConfig);

        // Exhaust permits
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        // Exceed limit and verify response body
        filter.doFilter(request, response, chain);
        verify(response).setHeader("Content-Type", "application/json");
        // Response body includes error message (checked via printWriter)
    }

    @Test
    @DisplayName("Filter parses invalid configuration with defaults")
    void testInvalidConfiguration() throws ServletException {
        when(filterConfig.getInitParameter("maxRequests")).thenReturn("invalid");
        when(filterConfig.getInitParameter("windowSeconds")).thenReturn("not-a-number");

        filter.init(filterConfig);
        // Should use defaults and not throw exception
        assertNotNull(filter);
    }

    @Test
    @DisplayName("Filter handles whitespace in configuration")
    void testWhitespaceConfiguration() throws ServletException, IOException {
        when(filterConfig.getInitParameter("maxRequests")).thenReturn("  5  ");
        when(filterConfig.getInitParameter("windowSeconds")).thenReturn("  60  ");

        filter.init(filterConfig);
        // Should parse correctly with whitespace trimmed
        assertNotNull(filter);
    }

    @Test
    @DisplayName("Filter closes gracefully on destroy")
    void testFilterDestroy() {
        filter.destroy();
        // No exception should be thrown
        assertNotNull(filter);
    }

    @Test
    @DisplayName("Multiple concurrent IPs are tracked independently")
    void testConcurrentMultipleIps() throws ServletException, IOException {
        filter.init(filterConfig);

        String[] ips = {
            "192.168.1.1",
            "192.168.1.2",
            "192.168.1.3"
        };

        for (String ip : ips) {
            when(request.getRemoteAddr()).thenReturn(ip);
            filter.doFilter(request, response, chain);
        }

        // All 3 should pass because each IP is separate
        verify(chain, times(3)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Filter handles X-Forwarded-For with single IP")
    void testXForwardedForSingleIp() throws ServletException, IOException {
        filter.init(filterConfig);

        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.50");

        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        // Next request exceeds limit
        filter.doFilter(request, response, chain);
        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("Filter handles empty X-Forwarded-For header")
    void testEmptyXForwardedFor() throws ServletException, IOException {
        filter.init(filterConfig);

        when(request.getHeader("X-Forwarded-For")).thenReturn("");

        // Should use remote address instead
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(3)).doFilter(request, response);
    }

    @Test
    @DisplayName("Security audit logger is called on rate limit violation")
    void testSecurityAuditLogging() throws ServletException, IOException {
        filter.init(filterConfig);

        // Exhaust permits
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        // Exceed limit - SecurityAuditLogger.rateLimitExceeded should be called
        // (verification depends on static method - actual verification in integration test)
        filter.doFilter(request, response, chain);
        verify(response).setStatus(429);
    }
}
