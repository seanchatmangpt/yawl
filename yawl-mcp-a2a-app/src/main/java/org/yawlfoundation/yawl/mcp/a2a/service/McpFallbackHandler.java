package org.yawlfoundation.yawl.mcp.a2a.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles fallback responses when MCP servers are unavailable.
 *
 * <p>Implements several fallback strategies:</p>
 * <ul>
 *   <li><strong>Cache</strong>: Returns cached successful responses when circuits are open</li>
 *   <li><strong>Stale-While-Revalidate</strong>: Returns stale data while fetching fresh data</li>
 *   <li><strong>Default Response</strong>: Returns configurable default responses</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class McpFallbackHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpFallbackHandler.class);

    private final CircuitBreakerProperties.FallbackConfig properties;
    private final Map<String, CacheEntry<?>> cache;
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a new fallback handler with the given configuration.
     *
     * @param properties the fallback configuration properties
     */
    public McpFallbackHandler(CircuitBreakerProperties.FallbackConfig properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.cache = new ConcurrentHashMap<>();

        if (properties.enabled() && properties.cacheTtlSeconds() > 0) {
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mcp-fallback-cache-cleanup");
                t.setDaemon(true);
                return t;
            });
            this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredEntries,
                properties.cacheTtlSeconds(),
                properties.cacheTtlSeconds(),
                TimeUnit.SECONDS);
        } else {
            this.cleanupExecutor = null;
        }

        LOGGER.info("Initialized MCP fallback handler: enabled={}, cacheTtl={}s, staleWhileRevalidate={}",
                   properties.enabled(), properties.cacheTtlSeconds(), properties.staleWhileRevalidate());
    }

    /**
     * Gets a cached fallback response for the given operation.
     *
     * @param serverId the server identifier
     * @param operation the operation name
     * @param <T> the expected return type
     * @return the cached response if available and not expired
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getFallback(String serverId, String operation) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        String cacheKey = buildCacheKey(serverId, operation);
        CacheEntry<?> entry = cache.get(cacheKey);

        if (entry == null) {
            LOGGER.debug("No cached fallback for server={}, operation={}", serverId, operation);
            return Optional.empty();
        }

        if (isExpired(entry)) {
            if (properties.staleWhileRevalidate()) {
                LOGGER.info("Returning stale cached response for server={}, operation={}",
                           serverId, operation);
                return Optional.of((T) entry.value());
            }
            cache.remove(cacheKey);
            return Optional.empty();
        }

        LOGGER.debug("Returning cached fallback for server={}, operation={}", serverId, operation);
        return Optional.of((T) entry.value());
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
        Instant expiresAt = Instant.now().plusSeconds(properties.cacheTtlSeconds());

        cache.put(cacheKey, new CacheEntry<>(result, expiresAt, operation));
        LOGGER.debug("Cached result for server={}, operation={}, expiresAt={}",
                    serverId, operation, expiresAt);
    }

    /**
     * Clears all cached entries.
     */
    public void clearCache() {
        cache.clear();
        LOGGER.info("Cleared all fallback cache entries");
    }

    /**
     * Clears cached entries for a specific server.
     *
     * @param serverId the server identifier
     */
    public void clearCacheForServer(String serverId) {
        cache.keySet().removeIf(key -> key.startsWith(serverId + ":"));
        LOGGER.info("Cleared fallback cache entries for server: {}", serverId);
    }

    /**
     * Gets the current cache size.
     *
     * @return number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Shuts down the cleanup executor.
     */
    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("MCP fallback handler shut down");
    }

    private String buildCacheKey(String serverId, String operation) {
        return serverId + ":" + operation;
    }

    private boolean isExpired(CacheEntry<?> entry) {
        return Instant.now().isAfter(entry.expiresAt());
    }

    private void cleanupExpiredEntries() {
        int removed = 0;
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (isExpired(entry.getValue())) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.debug("Cleaned up {} expired fallback cache entries", removed);
        }
    }

    /**
     * Represents a cached entry with expiration time.
     */
    private record CacheEntry<T>(
        T value,
        Instant expiresAt,
        String operation
    ) {
        @Override
        public String toString() {
            return "CacheEntry{operation='" + operation + "', expiresAt=" + expiresAt + "}";
        }
    }
}
