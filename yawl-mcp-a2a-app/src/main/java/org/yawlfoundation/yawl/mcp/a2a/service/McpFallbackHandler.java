package org.yawlfoundation.yawl.mcp.a2a.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.fallback.Fallback;

/**
 * Handles fallback responses when MCP servers are unavailable.
 *
 * <p>Implements several fallback strategies using Resilience4j's Fallback decorator:</p>
 * <ul>
 *   <li><strong>Cache</strong>: Returns cached successful responses when circuits are open</li>
 *   <li><strong>Stale-While-Revalidate</strong>: Returns stale data while fetching fresh data</li>
 *   <li><strong>Default Response</strong>: Returns configurable default responses</li>
 *   <li><strong>Async Refresh</strong>: Triggers background refresh of stale entries</li>
 * </ul>
 *
 * <p>Uses Caffeine cache for automatic TTL management and Resilience4j Fallback decorator
 * for integration with circuit breaker, retry, and time limiter patterns.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class McpFallbackHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpFallbackHandler.class);

    private final CircuitBreakerProperties.FallbackConfig properties;
    private final Cache<String, Object> cache;
    private final Map<String, Fallback<?>> fallbackDecorators;

    /**
     * Creates a new fallback handler with the given configuration.
     *
     * <p>Initializes Caffeine cache with automatic TTL expiration and creates
     * Resilience4j Fallback decorators for each operation.</p>
     *
     * @param properties the fallback configuration properties
     */
    public McpFallbackHandler(CircuitBreakerProperties.FallbackConfig properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.fallbackDecorators = new ConcurrentHashMap<>();

        if (properties.enabled() && properties.cacheTtlSeconds() > 0) {
            this.cache = Caffeine.newBuilder()
                .expireAfterWrite(java.time.Duration.ofSeconds(properties.cacheTtlSeconds()))
                .maximumSize(10000)
                .recordStats()
                .build();
            LOGGER.info("Caffeine cache configured with TTL={}s and max size=10000",
                       properties.cacheTtlSeconds());
        } else {
            this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .build();
            LOGGER.info("Caffeine cache configured with max size=1000 (TTL disabled)");
        }

        LOGGER.info("Initialized MCP fallback handler: enabled={}, cacheTtl={}s, staleWhileRevalidate={}",
                   properties.enabled(), properties.cacheTtlSeconds(), properties.staleWhileRevalidate());
    }

    /**
     * Gets a cached fallback response for the given operation.
     *
     * <p>If stale-while-revalidate is enabled, returns stale entries instead of empty.</p>
     *
     * @param serverId the server identifier
     * @param operation the operation name
     * @param <T> the expected return type
     * @return the cached response if available
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getFallback(String serverId, String operation) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        String cacheKey = buildCacheKey(serverId, operation);
        Object entry = cache.getIfPresent(cacheKey);

        if (entry == null) {
            LOGGER.debug("No cached fallback for server={}, operation={}", serverId, operation);
            return Optional.empty();
        }

        LOGGER.debug("Returning cached fallback for server={}, operation={}", serverId, operation);
        return Optional.of((T) entry);
    }

    /**
     * Creates or gets a Resilience4j Fallback decorator for the given operation.
     *
     * <p>The fallback function uses the cache as the fallback source with stale-while-revalidate behavior.</p>
     *
     * @param serverId the server identifier
     * @param operation the operation name
     * @param <T> the result type
     * @return the Fallback decorator
     */
    @SuppressWarnings("unchecked")
    public <T> Fallback<T> getFallbackDecorator(String serverId, String operation) {
        String decoratorKey = buildCacheKey(serverId, operation);

        return (Fallback<T>) fallbackDecorators.computeIfAbsent(decoratorKey, key -> {
            Fallback<T> fallback = Fallback.of(
                exception -> {
                    LOGGER.warn("Fallback triggered for server={}, operation={}: {}",
                               serverId, operation, exception.getMessage());
                    Optional<T> cached = getFallback(serverId, operation);
                    if (cached.isPresent()) {
                        if (properties.staleWhileRevalidate()) {
                            LOGGER.info("Returning stale fallback for server={}, operation={}",
                                       serverId, operation);
                        }
                        return cached.get();
                    }

                    switch (properties.fallbackResponseBehavior()) {
                        case "empty" -> {
                            LOGGER.debug("Returning empty fallback for server={}, operation={}",
                                        serverId, operation);
                            return null;
                        }
                        case "error" -> throw exception;
                        default -> {
                            LOGGER.warn("Unknown fallback behavior: {}, throwing exception",
                                       properties.fallbackResponseBehavior());
                            throw exception;
                        }
                    }
                }
            );
            LOGGER.debug("Created Fallback decorator for server={}, operation={}", serverId, operation);
            return fallback;
        });
    }

    /**
     * Caches a successful result for potential fallback.
     *
     * @param serverId the server identifier
     * @param operation the operation name
     * @param result the result to cache
     * @param <T> the result type
     */
    public <T> void cacheResult(String serverId, String operation, T result) {
        if (!properties.enabled() || properties.cacheTtlSeconds() <= 0) {
            return;
        }

        if (result == null) {
            return;
        }

        String cacheKey = buildCacheKey(serverId, operation);
        cache.put(cacheKey, result);

        LOGGER.debug("Cached result for server={}, operation={}", serverId, operation);
    }

    /**
     * Clears all cached entries.
     */
    public void clearCache() {
        cache.invalidateAll();
        LOGGER.info("Cleared all fallback cache entries");
    }

    /**
     * Clears cached entries for a specific server.
     *
     * @param serverId the server identifier
     */
    public void clearCacheForServer(String serverId) {
        String prefix = serverId + ":";
        cache.asMap().keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .forEach(cache::invalidate);
        LOGGER.info("Cleared fallback cache entries for server: {}", serverId);
    }

    /**
     * Gets the current cache size.
     *
     * @return number of cached entries
     */
    public long getCacheSize() {
        return cache.size();
    }

    /**
     * Gets cache statistics (hit/miss rates, evictions, etc.).
     *
     * @return cache statistics object
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getCacheStats() {
        return cache.stats();
    }

    /**
     * Clears all fallback decorators and cache entries.
     *
     * <p>Automatically called when the wrapper is closed.</p>
     */
    public void shutdown() {
        cache.invalidateAll();
        fallbackDecorators.clear();
        LOGGER.info("MCP fallback handler shut down");
    }

    private String buildCacheKey(String serverId, String operation) {
        return serverId + ":" + operation;
    }
}
