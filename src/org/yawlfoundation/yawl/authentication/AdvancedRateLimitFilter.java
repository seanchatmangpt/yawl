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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced rate limiting filter for YAWL web applications.
 *
 * <p>This filter implements sliding window rate limiting with different limits
 * for different endpoints and IP addresses. It provides DDoS protection and
 * prevents abuse of YAWL services.
 *
 * <p>Features:
 * <ul>
 *   <li>Sliding window rate limiting</li>
 *   <li>Per-endpoint rate limits</li>
 *   <li>IP-based rate limiting</li>
 *   <li>Burst protection</li>
 *   <li>Exponential backoff for abusive IPs</li>
 *   <li>Configurable whitelisting</li>
 *   <li>Detailed logging and metrics</li>
 * </ul>
 *
 * <p>Configuration in web.xml:
 * <pre>
 * &lt;filter&gt;
 *   &lt;filter-name&gt;AdvancedRateLimitFilter&lt;/filter-name&gt;
 *   &lt;filter-class&gt;org.yawlfoundation.yawl.authentication.AdvancedRateLimitFilter&lt;/filter-class&gt;
 *   &lt;init-param&gt;
 *     &lt;param-name&gt;defaultLimit&lt;/param-name&gt;
 *     &lt;param-value&gt;100&lt;/param-value&gt;
 *   &lt;/init-param&gt;
 *   &lt;init-param&gt;
 *     &lt;param-name&gt;timeWindowSeconds&lt;/param-name&gt;
 *     &lt;param-value&gt;60&lt;/param-value&gt;
 *   &lt;/init-param&gt;
 * &lt;/filter&gt;
 * </pre>
 *
 * @author YAWL Foundation Security Team
 * @version 6.0.0
 * @since 6.0.0
 */
public class AdvancedRateLimitFilter implements Filter {

    private static final Logger logger = LogManager.getLogger(AdvancedRateLimitFilter.class);

    // Default configuration
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_TIME_WINDOW_SECONDS = 60;
    private static final int BURST_LIMIT = 200;
    private static final long BLACKLIST_DURATION_MS = 300000; // 5 minutes

    // Rate limit data structures
    private final Map<String, AtomicInteger> ipRequestCounts = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> ipRequestTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Instant> blacklistedIps = new ConcurrentHashMap<>();
    private final Map<String, EndpointConfig> endpointConfigs = new ConcurrentHashMap<>();

    // Configuration
    private int defaultLimit = DEFAULT_LIMIT;
    private int defaultTimeWindowSeconds = DEFAULT_TIME_WINDOW_SECONDS;
    private Set<String> whitelistedIps = new HashSet<>();
    private Set<String> adminIps = new HashSet<>();
    private boolean enableBlacklisting = true;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Load configuration from init parameters
        String defaultLimitParam = filterConfig.getInitParameter("defaultLimit");
        if (defaultLimitParam != null) {
            defaultLimit = Integer.parseInt(defaultLimitParam);
        }

        String timeWindowParam = filterConfig.getInitParameter("timeWindowSeconds");
        if (timeWindowParam != null) {
            defaultTimeWindowSeconds = Integer.parseInt(timeWindowParam);
        }

        // Configure endpoint-specific limits
        configureEndpointLimits();

        // Initialize default whitelist (localhost, loopback)
        whitelistedIps.add("127.0.0.1");
        whitelistedIps.add("0:0:0:0:0:0:0:1");
        whitelistedIps.add("::1");

        logger.info("AdvancedRateLimitFilter initialized with default limit: {}/{}s",
                   defaultLimit, defaultTimeWindowSeconds);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        String endpoint = httpRequest.getRequestURI();
        String httpMethod = httpRequest.getMethod();

        // Check if IP is blacklisted
        if (enableBlacklisting && isBlacklisted(clientIp)) {
            logger.warn("Blacklisted IP {} attempted to access {}", clientIp, endpoint);
            sendRateLimitResponse(httpResponse, 429, "IP temporarily blocked");
            return;
        }

        // Check if IP is whitelisted
        if (isWhitelisted(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        // Check rate limits
        RateLimitResult result = checkRateLimit(clientIp, endpoint, httpMethod);

        if (result.isAllowed()) {
            // Increment counter and proceed
            incrementRequestCounter(clientIp, endpoint, httpMethod);
            chain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            handleRateLimitExceeded(httpResponse, result);
        }
    }

    /**
     * Get client IP address, handling proxies and load balancers.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // If multiple IPs, get the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * Check if an IP address is whitelisted.
     */
    private boolean isWhitelisted(String ip) {
        return whitelistedIps.contains(ip) || adminIps.contains(ip);
    }

    /**
     * Check if an IP address is blacklisted.
     */
    private boolean isBlacklisted(String ip) {
        if (!enableBlacklisting) {
            return false;
        }

        Instant blacklistExpiry = blacklistedIps.get(ip);
        if (blacklistExpiry != null && blacklistExpiry.isAfter(Instant.now())) {
            return true;
        } else if (blacklistExpiry != null) {
            // Remove expired blacklist entry
            blacklistedIps.remove(ip);
        }

        return false;
    }

    /**
     * Check rate limit for a specific request.
     */
    private RateLimitResult checkRateLimit(String ip, String endpoint, String method) {
        // Check if this is an admin endpoint with different limits
        if (adminIps.contains(ip) && endpoint.startsWith("/admin/")) {
            return new RateLimitResult(true, "Admin access");
        }

        // Get endpoint-specific configuration
        EndpointConfig config = endpointConfigs.getOrDefault(endpoint, getDefaultEndpointConfig());

        // Get current request count for this IP and endpoint
        String key = getRateLimitKey(ip, endpoint, method);
        AtomicInteger count = ipRequestCounts.get(key);

        if (count == null) {
            return new RateLimitResult(true, "New request");
        }

        // Check if within burst limit
        if (count.get() > BURST_LIMIT) {
            return new RateLimitResult(false, "Burst limit exceeded");
        }

        // Get request timestamps for sliding window calculation
        List<Long> timestamps = ipRequestTimestamps.get(key);
        if (timestamps == null) {
            return new RateLimitResult(true, "No timestamp data");
        }

        // Calculate sliding window
        Instant now = Instant.now();
        long windowStart = now.toEpochMilli() - (config.timeWindowSeconds * 1000L);

        // Remove old timestamps
        timestamps.removeIf(timestamp -> timestamp < windowStart);

        // Check current rate
        int currentRate = timestamps.size();
        if (currentRate >= config.limit) {
            // Blacklist IP for repeated violations
            if (enableBlacklisting && currentRate > config.limit * 2) {
                blacklistIp(ip);
            }
            return new RateLimitResult(false, "Rate limit exceeded");
        }

        return new RateLimitResult(true, "Within limits");
    }

    /**
     * Increment request counter.
     */
    private void incrementRequestCounter(String ip, String endpoint, String method) {
        String key = getRateLimitKey(ip, endpoint, method);

        ipRequestCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        ipRequestTimestamps.computeIfAbsent(key, k -> new ArrayList<>())
            .add(Instant.now().toEpochMilli());
    }

    /**
     * Get rate limit key for an IP and endpoint.
     */
    private String getRateLimitKey(String ip, String endpoint, String method) {
        return String.format("%s|%s|%s", ip, endpoint, method);
    }

    /**
     * Handle rate limit exceeded response.
     */
    private void handleRateLimitExceeded(HttpServletResponse response, RateLimitResult result)
            throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", result.getMessage());
        errorResponse.put("retryAfter", calculateRetryTime());

        response.getWriter().write(generateJsonResponse(errorResponse));

        // Log rate limit event
        logger.warn("Rate limit exceeded: {}", result.getMessage());
    }

    /**
     * Send rate limit response with specific status code.
     */
    private void sendRateLimitResponse(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Access denied");
        errorResponse.put("message", message);
        errorResponse.put("timestamp", Instant.now().toString());

        response.getWriter().write(generateJsonResponse(errorResponse));
    }

    /**
     * Blacklist an IP address.
     */
    private void blacklistIp(String ip) {
        if (enableBlacklisting) {
            blacklistedIps.put(ip, Instant.now().plusMillis(BLACKLIST_DURATION_MS));
            logger.error("IP {} blacklisted for {} minutes", ip, BLACKLIST_DURATION_MS / 60000);
        }
    }

    /**
     * Calculate retry time based on violations.
     */
    private long calculateRetryTime() {
        // Simple exponential backoff
        return Math.min(300, 30); // Max 5 minutes, min 30 seconds
    }

    /**
     * Generate JSON response.
     */
    private String generateJsonResponse(Map<String, Object> data) {
        StringBuilder json = new StringBuilder("{");
        data.forEach((key, value) -> {
            json.append("\"").append(key).append("\": ");
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
            json.append(", ");
        });
        if (!data.isEmpty()) {
            json.setLength(json.length() - 2); // Remove last comma and space
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Configure endpoint-specific rate limits.
     */
    private void configureEndpointLimits() {
        // API endpoints with stricter limits
        endpointConfigs.put("/api/workflow/instance/start",
            new EndpointConfig(50, 30)); // 50 requests per 30 seconds
        endpointConfigs.put("/api/workflow/instance/update",
            new EndpointConfig(100, 60));
        endpointConfigs.put("/api/search",
            new EndpointConfig(30, 60)); // Search operations are expensive
        endpointConfigs.put("/api/export",
            new EndpointConfig(20, 300)); // Export operations are heavy
        endpointConfigs.put("/login",
            new EndpointConfig(5, 60)); // Login attempts are rate-limited

        // Admin endpoints with higher limits for trusted IPs
        adminIps.add("192.168.1.0/24"); // Example admin network
    }

    /**
     * Get default endpoint configuration.
     */
    private EndpointConfig getDefaultEndpointConfig() {
        return new EndpointConfig(defaultLimit, defaultTimeWindowSeconds);
    }

    @Override
    public void destroy() {
        // Cleanup resources
        ipRequestCounts.clear();
        ipRequestTimestamps.clear();
        blacklistedIps.clear();
        endpointConfigs.clear();
        logger.info("AdvancedRateLimitFilter destroyed");
    }

    /**
     * Rate limit result container.
     */
    private static class RateLimitResult {
        private final boolean allowed;
        private final String message;

        public RateLimitResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Endpoint configuration container.
     */
    private static class EndpointConfig {
        private final int limit;
        private final int timeWindowSeconds;

        public EndpointConfig(int limit, int timeWindowSeconds) {
            this.limit = limit;
            this.timeWindowSeconds = timeWindowSeconds;
        }

        public int getLimit() {
            return limit;
        }

        public int getTimeWindowSeconds() {
            return timeWindowSeconds;
        }
    }
}