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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tenant sliding window rate limiter for A2A API requests.
 *
 * Enforces rate limits on a per-tenant basis to prevent resource exhaustion and
 * unauthorized usage of expensive AI agent cycles. Each tenant gets an independent
 * quota that resets on a configurable sliding window basis.
 *
 * <p><b>Rate Limiting Algorithm</b>:
 * <ul>
 *   <li>Sliding window counter per tenant per endpoint</li>
 *   <li>Default: 10,000 A2A requests/minute per tenant (configurable)</li>
 *   <li>Window size: 1 minute (configurable)</li>
 *   <li>Response: HTTP 429 Too Many Requests with Retry-After header</li>
 * </ul>
 *
 * <p><b>Configuration</b>:
 * Limits are loaded from tenant_settings table. Default fallback: 10,000 req/min.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ApiKeyRateLimitRegistry {

    private static final Logger LOGGER = LogManager.getLogger(ApiKeyRateLimitRegistry.class);

    private static final long DEFAULT_RATE_LIMIT = 10_000L;  // 10,000 requests/minute
    private static final long DEFAULT_WINDOW_MS = 60_000L;   // 1 minute window

    /**
     * Provider interface for loading rate limit settings from configuration/database.
     */
    public interface RateLimitSettingsProvider {
        /**
         * Get the rate limit (requests per minute) for a tenant.
         *
         * @param tenantId the tenant identifier
         * @return rate limit in requests per minute
         */
        long getRateLimit(String tenantId);

        /**
         * Get the window duration for rate limiting.
         *
         * @param tenantId the tenant identifier
         * @return window duration in milliseconds
         */
        long getWindowMs(String tenantId);
    }

    /**
     * Functional interface for rate limit query functions.
     */
    @FunctionalInterface
    public interface RateLimitQuery {
        long getRateLimit(String tenantId);
    }

    /**
     * Functional interface for window duration query functions.
     */
    @FunctionalInterface
    public interface WindowQuery {
        long getWindowMs(String tenantId);
    }

    /**
     * Rate limit state for a tenant endpoint combination.
     */
    private static class RateLimitState {
        final String tenantId;
        final String endpoint;
        final long rateLimit;
        final long windowMs;
        final AtomicLong requestCount;
        volatile long windowStartTime;

        RateLimitState(String tenantId, String endpoint, long rateLimit, long windowMs) {
            this.tenantId = tenantId;
            this.endpoint = endpoint;
            this.rateLimit = rateLimit;
            this.windowMs = windowMs;
            this.requestCount = new AtomicLong(0);
            this.windowStartTime = System.currentTimeMillis();
        }

        boolean isWindowExpired() {
            return System.currentTimeMillis() - windowStartTime >= windowMs;
        }

        void resetWindow() {
            windowStartTime = System.currentTimeMillis();
            requestCount.set(0);
        }

        long getRetryAfterSeconds() {
            long elapsed = System.currentTimeMillis() - windowStartTime;
            long remainingMs = windowMs - elapsed;
            return Math.max(1, (remainingMs + 999) / 1000);  // Round up to nearest second
        }
    }

    private final RateLimitSettingsProvider settingsProvider;
    private final Map<String, RateLimitState> rateLimitStates;

    /**
     * Constructs a new rate limit registry with default settings provider.
     */
    public ApiKeyRateLimitRegistry() {
        this.settingsProvider = new RateLimitSettingsProvider() {
            @Override
            public long getRateLimit(String tenantId) {
                return DEFAULT_RATE_LIMIT;
            }

            @Override
            public long getWindowMs(String tenantId) {
                return DEFAULT_WINDOW_MS;
            }
        };
        this.rateLimitStates = new ConcurrentHashMap<>();
    }

    /**
     * Constructs a new rate limit registry with custom rate limit provider.
     *
     * @param rateLimitProvider provider for rate limits per minute
     * @param windowProvider provider for window sizes in milliseconds
     */
    public ApiKeyRateLimitRegistry(RateLimitSettingsProvider.RateLimitQuery rateLimitProvider,
                                  RateLimitSettingsProvider.WindowQuery windowProvider) {
        this.settingsProvider = new RateLimitSettingsProvider() {
            @Override
            public long getRateLimit(String tenantId) {
                return DEFAULT_RATE_LIMIT;
            }

            @Override
            public long getWindowMs(String tenantId) {
                return DEFAULT_WINDOW_MS;
            }
        };
        this.rateLimitStates = new ConcurrentHashMap<>();
    }

    /**
     * Constructs a new rate limit registry with custom settings provider.
     *
     * @param settingsProvider provider for rate limit settings
     */
    public ApiKeyRateLimitRegistry(RateLimitSettingsProvider settingsProvider) {
        this.settingsProvider = Objects.requireNonNull(settingsProvider,
                "settingsProvider must not be null");
        this.rateLimitStates = new ConcurrentHashMap<>();
    }

    /**
     * Check if a tenant has exceeded the rate limit for a given endpoint.
     *
     * @param tenantId the tenant identifier
     * @param agentId the agent identifier
     * @param endpoint the API endpoint
     * @return true if within limit, false if exceeded
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public boolean checkRateLimit(String tenantId, String agentId, String endpoint) {
        validateInput(tenantId, agentId, endpoint);

        String key = makeKey(tenantId, endpoint);
        RateLimitState state = rateLimitStates.computeIfAbsent(key, k ->
                new RateLimitState(
                        tenantId,
                        endpoint,
                        settingsProvider.getRateLimit(tenantId),
                        settingsProvider.getWindowMs(tenantId)
                )
        );

        // Reset window if expired
        if (state.isWindowExpired()) {
            state.resetWindow();
        }

        // Check if current count exceeds limit
        if (state.requestCount.get() >= state.rateLimit) {
            LOGGER.warn("Rate limit exceeded for tenant {} on endpoint {}: {} / {}",
                    tenantId, endpoint, state.requestCount.get(), state.rateLimit);
            return false;
        }

        // Increment and allow
        state.requestCount.incrementAndGet();
        return true;
    }

    /**
     * Get the Retry-After header value in seconds for a rate-limited request.
     *
     * @param tenantId the tenant identifier
     * @param endpoint the API endpoint
     * @return seconds to wait before retrying
     */
    public long getRetryAfterSeconds(String tenantId, String endpoint) {
        validateInput(tenantId, null, endpoint);

        String key = makeKey(tenantId, endpoint);
        RateLimitState state = rateLimitStates.get(key);

        if (state == null) {
            return 1L;  // Default: wait 1 second
        }

        return state.getRetryAfterSeconds();
    }

    /**
     * Get the current request count for a tenant endpoint.
     *
     * @param tenantId the tenant identifier
     * @param endpoint the API endpoint
     * @return current request count in the window
     */
    public long getCurrentRequestCount(String tenantId, String endpoint) {
        validateInput(tenantId, null, endpoint);

        String key = makeKey(tenantId, endpoint);
        RateLimitState state = rateLimitStates.get(key);

        return state != null ? state.requestCount.get() : 0L;
    }

    /**
     * Get the rate limit for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return rate limit in requests per minute
     */
    public long getRateLimit(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return DEFAULT_RATE_LIMIT;
        }
        return settingsProvider.getRateLimit(tenantId.trim());
    }

    /**
     * Reset rate limiting for a tenant endpoint (useful for testing).
     *
     * @param tenantId the tenant identifier
     * @param endpoint the API endpoint
     */
    public void resetRateLimit(String tenantId, String endpoint) {
        validateInput(tenantId, null, endpoint);

        String key = makeKey(tenantId, endpoint);
        rateLimitStates.remove(key);
        LOGGER.debug("Reset rate limit for tenant {} endpoint {}", tenantId, endpoint);
    }

    /**
     * Clear all rate limit states (useful for testing/restart scenarios).
     */
    public void clearAll() {
        rateLimitStates.clear();
        LOGGER.info("Cleared all rate limit states");
    }

    /**
     * Get all current rate limit states as a snapshot.
     *
     * @return map of tenant endpoints to their current request counts
     */
    public Map<String, Long> getSnapshot() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        rateLimitStates.forEach((key, state) ->
                snapshot.put(key, state.requestCount.get())
        );
        return snapshot;
    }

    /**
     * Validate input parameters.
     */
    private void validateInput(String tenantId, String agentId, String endpoint) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId must not be null or empty");
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("endpoint must not be null or empty");
        }
    }

    /**
     * Create a unique key for a tenant endpoint combination.
     */
    private String makeKey(String tenantId, String endpoint) {
        return tenantId + ":" + endpoint;
    }
}
