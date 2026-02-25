/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.cache;

import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model.TherapyRecommendations;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for therapy recommendations to improve response times and reduce API calls.
 *
 * @since 6.0.0
 */
public class TherapyRecommendationCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public TherapyRecommendationCache() {
        this(3600000L); // 1 hour default TTL
    }

    public TherapyRecommendationCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Generates a cache key from patient profile and query.
     */
    public String generateKey(String patientId, String condition, String query) {
        return patientId + ":" + condition + ":" + query.hashCode();
    }

    /**
     * Gets a cached recommendation if available and not expired.
     */
    public Optional<TherapyRecommendations> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired(ttlMillis)) {
            return Optional.of(entry.recommendations());
        }
        cache.remove(key);
        return Optional.empty();
    }

    /**
     * Stores a recommendation in the cache.
     */
    public void put(String key, TherapyRecommendations recommendations) {
        cache.put(key, new CacheEntry(recommendations, Instant.now()));
    }

    /**
     * Clears expired entries from the cache.
     */
    public void evictStaleEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(ttlMillis));
    }

    /**
     * Gets the current cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
    }

    private record CacheEntry(TherapyRecommendations recommendations, Instant timestamp) {
        boolean isExpired(long ttlMillis) {
            return Instant.now().toEpochMilli() - timestamp.toEpochMilli() > ttlMillis;
        }
    }
}
