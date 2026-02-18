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

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servlet filter that enforces per-IP rate limiting on authentication endpoints
 * to prevent brute-force and credential stuffing attacks.
 *
 * SOC2 HIGH finding: authentication endpoints (POST /login, POST /auth, Interface A/B
 * connect requests) were missing rate limiting, allowing unlimited password attempts.
 *
 * Algorithm: sliding window counter per client IP address.
 *   - Each IP gets a fixed quota of requests per time window (default: 10 per minute).
 *   - Once the quota is exhausted, all subsequent requests within the window receive
 *     HTTP 429 Too Many Requests with a Retry-After header.
 *   - Windows reset automatically after the configured duration.
 *   - Expired entries are evicted periodically to prevent unbounded memory growth.
 *
 * Configuration via web.xml init-params (all optional):
 *   maxRequests      - max requests per window per IP (default: 10)
 *   windowSeconds    - window duration in seconds (default: 60)
 *   cleanupIntervalS - how often expired entries are purged in seconds (default: 300)
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class RateLimitFilter implements Filter {

    private static final Logger _logger = LogManager.getLogger(RateLimitFilter.class);

    /** Default: max 10 authentication attempts per minute per IP. */
    private static final int DEFAULT_MAX_REQUESTS = 10;
    private static final long DEFAULT_WINDOW_SECONDS = 60L;
    private static final long DEFAULT_CLEANUP_INTERVAL_SECONDS = 300L;

    private int maxRequests;
    private long windowMs;
    private long cleanupIntervalMs;

    /** Per-IP request counters keyed by remote address string. */
    private final ConcurrentHashMap<String, RateLimitEntry> counters = new ConcurrentHashMap<>();

    /** Timestamp of the last cleanup pass. */
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        maxRequests = parseIntParam(filterConfig, "maxRequests", DEFAULT_MAX_REQUESTS);
        long windowSeconds = parseLongParam(filterConfig, "windowSeconds", DEFAULT_WINDOW_SECONDS);
        long cleanupSeconds = parseLongParam(filterConfig, "cleanupIntervalS",
                DEFAULT_CLEANUP_INTERVAL_SECONDS);
        windowMs = windowSeconds * 1000L;
        cleanupIntervalMs = cleanupSeconds * 1000L;

        _logger.info("RateLimitFilter initialized: max {} requests per {} seconds per IP",
                maxRequests, windowSeconds);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = resolveClientIp(httpRequest);

        evictExpiredEntriesIfDue();

        RateLimitEntry entry = counters.computeIfAbsent(clientIp,
                k -> new RateLimitEntry(System.currentTimeMillis(), windowMs));

        if (!entry.tryAcquire(maxRequests, windowMs)) {
            long retryAfterSeconds = entry.retryAfterSeconds(windowMs);
            String requestUri = httpRequest.getRequestURI();
            _logger.warn("Rate limit exceeded for IP {} on {} {}: {} requests in {}ms window",
                    clientIp, httpRequest.getMethod(), requestUri, maxRequests, windowMs);
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
        counters.clear();
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
     * Removes entries whose window has expired, but only if the cleanup interval has elapsed.
     * This prevents O(n) scan on every request while still bounding memory growth.
     */
    private void evictExpiredEntriesIfDue() {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();
        if (now - last >= cleanupIntervalMs && lastCleanup.compareAndSet(last, now)) {
            counters.entrySet().removeIf(e -> e.getValue().isExpired(now, windowMs));
            _logger.debug("RateLimitFilter: evicted expired entries, remaining: {}",
                    counters.size());
        }
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

    private long parseLongParam(FilterConfig cfg, String name, long defaultValue) {
        String value = cfg.getInitParameter(name);
        if (value != null && !value.trim().isEmpty()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                _logger.warn("Invalid long for init-param '{}': '{}', using default {}",
                        name, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * Tracks request count and window start time for a single client IP.
     * Thread-safe via atomic operations.
     */
    private static final class RateLimitEntry {

        private final AtomicLong windowStart;
        private final AtomicInteger count;

        RateLimitEntry(long windowStartMs, long windowMs) {
            this.windowStart = new AtomicLong(windowStartMs);
            this.count = new AtomicInteger(0);
        }

        /**
         * Attempts to record a request. Returns true if the request is within the
         * rate limit, false if the limit is exceeded.
         *
         * @param maxRequests maximum allowed requests per window
         * @param windowMs    window duration in milliseconds
         * @return true if the request is permitted, false if rate limited
         */
        boolean tryAcquire(int maxRequests, long windowMs) {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start >= windowMs) {
                // Window has expired: reset counter atomically
                if (windowStart.compareAndSet(start, now)) {
                    count.set(1);
                    return true;
                }
                // Another thread reset the window; fall through to count check
            }
            return count.incrementAndGet() <= maxRequests;
        }

        /**
         * Returns the number of seconds until the current window expires.
         *
         * @param windowMs window duration in milliseconds
         * @return seconds until retry is allowed (minimum 1)
         */
        long retryAfterSeconds(long windowMs) {
            long elapsed = System.currentTimeMillis() - windowStart.get();
            long remainingMs = windowMs - elapsed;
            return Math.max(1L, (remainingMs + 999) / 1000);
        }

        /**
         * Returns true if this entry's window has expired and can be evicted.
         *
         * @param now      current epoch milliseconds
         * @param windowMs window duration in milliseconds
         * @return true if the entry is stale
         */
        boolean isExpired(long now, long windowMs) {
            return now - windowStart.get() >= windowMs * 2;
        }
    }
}
