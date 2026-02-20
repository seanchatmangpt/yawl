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

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servlet filter that enforces per-IP rate limiting on authentication endpoints
 * to prevent brute-force and credential stuffing attacks.
 *
 * SOC2 HIGH finding: authentication endpoints (POST /login, POST /auth, Interface A/B
 * connect requests) were missing rate limiting, allowing unlimited password attempts.
 *
 * Algorithm: token bucket pattern (via Resilience4j RateLimiter) per client IP address.
 *   - Each IP gets a fixed quota of permits per time window (default: 10 per minute).
 *   - Once the quota is exhausted, all subsequent requests within the window receive
 *     HTTP 429 Too Many Requests with a Retry-After header.
 *   - Resilience4j automatically manages permit refresh and cleanup.
 *
 * Configuration via web.xml init-params (all optional):
 *   maxRequests      - max requests per window per IP (default: 10)
 *   windowSeconds    - window duration in seconds (default: 60)
 *
 * Thread Safety: Uses ReentrantLock instead of synchronized blocks to avoid
 * pinning virtual threads. Resilience4j RateLimiter is fully thread-safe.
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class RateLimitFilter implements Filter {

    private static final Logger _logger = LogManager.getLogger(RateLimitFilter.class);

    /** Default: max 10 authentication attempts per minute per IP. */
    private static final int DEFAULT_MAX_REQUESTS = 10;
    private static final int DEFAULT_WINDOW_SECONDS = 60;

    private int maxRequests;
    private int windowSeconds;

    /** Per-IP rate limiters using Resilience4j. Thread-safe via RateLimiter. */
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    /** Lock for creating new limiters (only held briefly). */
    private final ReentrantLock limiterCreationLock = new ReentrantLock();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        maxRequests = parseIntParam(filterConfig, "maxRequests", DEFAULT_MAX_REQUESTS);
        windowSeconds = parseIntParam(filterConfig, "windowSeconds", DEFAULT_WINDOW_SECONDS);

        _logger.info("RateLimitFilter initialized: max {} requests per {} seconds per IP",
                maxRequests, windowSeconds);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = resolveClientIp(httpRequest);
        RateLimiter limiter = getOrCreateLimiter(clientIp);

        if (!limiter.acquirePermission()) {
            long retryAfterSeconds = computeRetryAfterSeconds(limiter);
            String requestUri = httpRequest.getRequestURI();
            _logger.warn("Rate limit exceeded for IP {} on {} {}: {} requests per {} seconds",
                    clientIp, httpRequest.getMethod(), requestUri, maxRequests, windowSeconds);
            SecurityAuditLogger.rateLimitExceeded(clientIp, requestUri, maxRequests);

            httpResponse.setStatus(429);
            httpResponse.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            httpResponse.setHeader("X-RateLimit-Remaining", "0");
            httpResponse.setHeader("Content-Type", "application/json");
            httpResponse.getWriter().write(
                    "{\"error\":\"Too many requests\",\"retryAfterSeconds\":" +
                    retryAfterSeconds + "}");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        limiters.clear();
        _logger.info("RateLimitFilter destroyed");
    }

    /**
     * Resolves the real client IP, handling X-Forwarded-For for reverse proxy deployments.
     * Uses only the first (leftmost) address in the X-Forwarded-For chain as the trusted
     * client address; subsequent entries may be set by intermediate proxies.
     *
     * @param request the HTTP request
     * @return the resolved client IP string
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Retrieves or creates a Resilience4j RateLimiter for the given client IP.
     * Uses double-checked locking with ReentrantLock to minimize contention.
     *
     * @param clientIp the client IP address
     * @return a RateLimiter for this IP
     */
    private RateLimiter getOrCreateLimiter(String clientIp) {
        RateLimiter limiter = limiters.get(clientIp);
        if (limiter != null) {
            return limiter;
        }

        // Brief lock only for creation; concurrent lookups proceed without blocking
        limiterCreationLock.lock();
        try {
            // Double-check pattern: another thread may have created while waiting
            limiter = limiters.get(clientIp);
            if (limiter == null) {
                limiter = createLimiter(clientIp);
                limiters.put(clientIp, limiter);
            }
            return limiter;
        } finally {
            limiterCreationLock.unlock();
        }
    }

    /**
     * Creates a new Resilience4j RateLimiter with token bucket configuration.
     *
     * @param clientIp the client IP address
     * @return a new RateLimiter instance
     */
    private RateLimiter createLimiter(String clientIp) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(windowSeconds))
                .limitForPeriod(maxRequests)
                .timeoutDuration(Duration.ZERO)  // Fail immediately on exceeded limit
                .build();

        RateLimiter limiter = RateLimiter.of(clientIp, config);
        _logger.debug("Created rate limiter for IP {}: {} permits per {} seconds",
                clientIp, maxRequests, windowSeconds);
        return limiter;
    }

    /**
     * Computes Retry-After header value (seconds until next permit available).
     *
     * @param limiter the RateLimiter for this IP
     * @return seconds to wait (minimum 1)
     */
    private long computeRetryAfterSeconds(RateLimiter limiter) {
        // Resilience4j doesn't expose exact wait time, so use window duration
        return Math.max(1L, windowSeconds);
    }

    private int parseIntParam(FilterConfig cfg, String name, int defaultValue) {
        String value = cfg.getInitParameter(name);
        if (value != null && !value.trim().isEmpty()) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                _logger.warn("Invalid integer for init-param '{}': '{}', using default {}",
                        name, value, defaultValue);
            }
        }
        return defaultValue;
    }
}
