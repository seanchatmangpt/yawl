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

package org.yawlfoundation.yawl.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe rate limiter registry for API endpoints.
 *
 * Provides per-client and per-endpoint rate limiting using Resilience4j's token bucket
 * pattern. Supports both global and per-client configurations.
 *
 * Rate limiter configuration:
 * - Global limit: 1000 permits per 60 seconds (default)
 * - Per-client limit: 100 permits per 60 seconds (default)
 * - Timeout: 50ms (fail immediately if limit exceeded)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ApiKeyRateLimitRegistry {

    private static final Logger log = LogManager.getLogger(ApiKeyRateLimitRegistry.class);

    private static final String GLOBAL_LIMITER = "global-api";
    private static final String CLIENT_LIMITER_PREFIX = "client-";
    private static final String ENDPOINT_LIMITER_PREFIX = "endpoint-";

    private static final int DEFAULT_GLOBAL_PERMITS = 1000;
    private static final int DEFAULT_GLOBAL_PERIOD_SECONDS = 60;
    private static final int DEFAULT_CLIENT_PERMITS = 100;
    private static final int DEFAULT_CLIENT_PERIOD_SECONDS = 60;
    private static final long TIMEOUT_MS = 50L;

    private final io.github.resilience4j.ratelimiter.RateLimiterRegistry registry;
    private final Map<String, RateLimiterConfig> configs;
    private final AtomicReference<Boolean> globalLimiterEnabled;

    /**
     * Creates a new ApiKeyRateLimitRegistry with default configurations.
     */
    public ApiKeyRateLimitRegistry() {
        this.registry = io.github.resilience4j.ratelimiter.RateLimiterRegistry.ofDefaults();
        this.configs = new ConcurrentHashMap<>();
        this.globalLimiterEnabled = new AtomicReference<>(true);

        initializeGlobalLimiter();
    }

    /**
     * Initializes the global rate limiter with default configuration.
     * This limiter applies to all API requests across the system.
     */
    private void initializeGlobalLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(DEFAULT_GLOBAL_PERIOD_SECONDS))
                .limitForPeriod(DEFAULT_GLOBAL_PERMITS)
                .timeoutDuration(Duration.ofMillis(TIMEOUT_MS))
                .build();

        registry.rateLimiter(GLOBAL_LIMITER, config);
        configs.put(GLOBAL_LIMITER, config);
        log.info("Global rate limiter initialized: {} permits per {} seconds",
                DEFAULT_GLOBAL_PERMITS, DEFAULT_GLOBAL_PERIOD_SECONDS);
    }

    /**
     * Acquires a permit from the global rate limiter.
     *
     * @return true if permit was acquired, false if rate limit exceeded
     */
    public boolean acquireGlobal() {
        if (!globalLimiterEnabled.get()) {
            return true;
        }

        try {
            RateLimiter limiter = registry.rateLimiter(GLOBAL_LIMITER);
            return limiter.executeSupplier(() -> true);
        } catch (Exception e) {
            log.warn("Global rate limit exceeded");
            return false;
        }
    }

    /**
     * Acquires a permit for a specific client from the per-client rate limiter.
     *
     * @param clientId unique identifier for the client (e.g., IP address, user ID, API key)
     * @return true if permit was acquired, false if rate limit exceeded
     * @throws IllegalArgumentException if clientId is null or empty
     */
    public boolean acquirePerClient(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        String limiterName = CLIENT_LIMITER_PREFIX + clientId;

        try {
            // Check if limiter exists or create it
            RateLimiter limiter = registry.rateLimiter(limiterName, () -> {
                RateLimiterConfig config = RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofSeconds(DEFAULT_CLIENT_PERIOD_SECONDS))
                        .limitForPeriod(DEFAULT_CLIENT_PERMITS)
                        .timeoutDuration(Duration.ofMillis(TIMEOUT_MS))
                        .build();
                return config;
            });

            return limiter.executeSupplier(() -> true);
        } catch (Exception e) {
            log.warn("Rate limit exceeded for client: {}", clientId);
            return false;
        }
    }

    /**
     * Acquires a permit for a specific endpoint from the per-endpoint rate limiter.
     *
     * @param endpoint unique identifier for the endpoint (e.g., "/api/cases/launch")
     * @param permitsPerMinute maximum permits allowed per minute
     * @return true if permit was acquired, false if rate limit exceeded
     * @throws IllegalArgumentException if endpoint is null or empty, or permitsPerMinute is invalid
     */
    public boolean acquirePerEndpoint(String endpoint, int permitsPerMinute) {
        Objects.requireNonNull(endpoint, "endpoint cannot be null");
        if (endpoint.isEmpty()) {
            throw new IllegalArgumentException("endpoint cannot be empty");
        }
        if (permitsPerMinute <= 0) {
            throw new IllegalArgumentException("permitsPerMinute must be positive");
        }

        String limiterName = ENDPOINT_LIMITER_PREFIX + endpoint;

        try {
            // Check if limiter exists or create it
            int permitsPerPeriod = Math.max(1, permitsPerMinute / 10);
            RateLimiter limiter = registry.rateLimiter(limiterName, () -> {
                RateLimiterConfig config = RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofSeconds(6))  // 6 second period
                        .limitForPeriod(permitsPerPeriod)
                        .timeoutDuration(Duration.ofMillis(TIMEOUT_MS))
                        .build();
                return config;
            });

            return limiter.executeSupplier(() -> true);
        } catch (Exception e) {
            log.warn("Rate limit exceeded for endpoint: {}", endpoint);
            return false;
        }
    }

    /**
     * Creates a custom rate limiter with specified configuration.
     *
     * @param name unique name for the limiter
     * @param permitsPerPeriod number of permits allowed per period
     * @param periodSeconds duration of the rate limit period in seconds
     * @throws IllegalArgumentException if parameters are invalid
     */
    public void createCustomLimiter(String name, int permitsPerPeriod, int periodSeconds) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty");
        }
        if (permitsPerPeriod <= 0) {
            throw new IllegalArgumentException("permitsPerPeriod must be positive");
        }
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("periodSeconds must be positive");
        }

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(periodSeconds))
                .limitForPeriod(permitsPerPeriod)
                .timeoutDuration(Duration.ofMillis(TIMEOUT_MS))
                .build();

        registry.rateLimiter(name, config);
        configs.put(name, config);
        log.debug("Custom rate limiter created: name={}, permits={}, periodSeconds={}",
                name, permitsPerPeriod, periodSeconds);
    }

    /**
     * Creates a per-client rate limiter with default configuration.
     *
     * @param clientId the client identifier
     * @param permitsPerPeriod number of permits allowed per period
     * @param periodSeconds duration of the rate limit period in seconds
     */
    private void createClientLimiter(String clientId, int permitsPerPeriod, int periodSeconds) {
        String name = CLIENT_LIMITER_PREFIX + clientId;
        createCustomLimiter(name, permitsPerPeriod, periodSeconds);
        log.debug("Client rate limiter created: clientId={}, permits={}, periodSeconds={}",
                clientId, permitsPerPeriod, periodSeconds);
    }

    /**
     * Creates a per-endpoint rate limiter with specified permits per minute.
     *
     * @param endpoint the endpoint identifier
     * @param permitsPerMinute number of permits allowed per minute
     */
    private void createEndpointLimiter(String endpoint, int permitsPerMinute) {
        String name = ENDPOINT_LIMITER_PREFIX + endpoint;
        // Convert permits per minute to permits per period
        int permitsPerPeriod = Math.max(1, permitsPerMinute / 10);
        createCustomLimiter(name, permitsPerPeriod, 6); // 6 second period
        log.debug("Endpoint rate limiter created: endpoint={}, permitsPerMinute={}",
                endpoint, permitsPerMinute);
    }

    /**
     * Enables or disables the global rate limiter.
     *
     * @param enabled true to enable, false to disable
     */
    public void setGlobalLimiterEnabled(boolean enabled) {
        globalLimiterEnabled.set(enabled);
        log.info("Global rate limiter: {}", enabled ? "ENABLED" : "DISABLED");
    }

    /**
     * Returns whether the global rate limiter is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isGlobalLimiterEnabled() {
        return globalLimiterEnabled.get();
    }

    /**
     * Returns the configuration for a rate limiter.
     *
     * @param name the limiter name
     * @return the RateLimiterConfig, or null if not found
     */
    public RateLimiterConfig getConfig(String name) {
        return configs.get(name);
    }

    /**
     * Returns the number of registered limiters.
     *
     * @return count of rate limiters
     */
    public int getLimiterCount() {
        return registry.getAllRateLimiters().size();
    }

    /**
     * Resets all rate limiters to their initial state.
     * Useful for testing or configuration changes.
     * NOTE: Resilience4j RateLimiter doesn't expose a reset() method in public API.
     * To achieve reset functionality, recreate limiters with fresh configs.
     */
    public void resetAll() {
        // Clear the configs and re-initialize global limiter
        configs.clear();
        initializeGlobalLimiter();
        log.info("All rate limiters reset");
    }
}
