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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe store for tracking idempotent request keys and their cached responses.
 *
 * Implements the idempotency pattern by tracking request keys and returning cached
 * responses for duplicate requests. Entries are automatically expired after a
 * configurable TTL (default: 24 hours).
 *
 * Use cases:
 * - POST /cases/launch with Idempotency-Key header → returns same case ID for retries
 * - PUT /cases/{id}/complete with Idempotency-Key header → idempotent completion
 * - POST /tasks/{id}/assign with Idempotency-Key header → prevents duplicate assignments
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class IdempotencyKeyStore {

    private static final Logger log = LogManager.getLogger(IdempotencyKeyStore.class);

    private static final long DEFAULT_TTL_HOURS = 24;
    private static final long CLEANUP_INTERVAL_MINUTES = 60;

    private final Map<String, CachedResponse> responseCache;
    private final long ttlMillis;
    private volatile long lastCleanup;

    /**
     * Record holding a cached response for an idempotent request.
     *
     * @param key the idempotency key
     * @param response the cached response body
     * @param statusCode HTTP status code of the original response
     * @param timestamp when this entry was created
     */
    public record CachedResponse(
            String key,
            String response,
            int statusCode,
            Instant timestamp
    ) {
        public CachedResponse {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(response, "response cannot be null");
            Objects.requireNonNull(timestamp, "timestamp cannot be null");
            if (statusCode < 100 || statusCode >= 600) {
                throw new IllegalArgumentException("Invalid HTTP status code: " + statusCode);
            }
        }

        /**
         * Returns true if this cached response has expired based on TTL.
         *
         * @param ttlMillis the time-to-live in milliseconds
         * @return true if expired, false otherwise
         */
        public boolean isExpired(long ttlMillis) {
            long ageMillis = System.currentTimeMillis() - timestamp.toEpochMilli();
            return ageMillis > ttlMillis;
        }
    }

    /**
     * Creates a new IdempotencyKeyStore with default TTL (24 hours).
     */
    public IdempotencyKeyStore() {
        this(DEFAULT_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Creates a new IdempotencyKeyStore with custom TTL.
     *
     * @param ttl the time-to-live value
     * @param unit the time unit for TTL
     * @throws IllegalArgumentException if TTL is invalid
     */
    public IdempotencyKeyStore(long ttl, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");
        if (ttl <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }

        this.responseCache = new ConcurrentHashMap<>();
        this.ttlMillis = unit.toMillis(ttl);
        this.lastCleanup = System.currentTimeMillis();

        log.info("IdempotencyKeyStore initialized with TTL: {} {}", ttl, unit);
    }

    /**
     * Stores a cached response for an idempotency key.
     * Overwrites any existing response for the same key.
     *
     * @param key the idempotency key (must be unique per request)
     * @param response the response body to cache
     * @param statusCode the HTTP status code
     * @throws IllegalArgumentException if key is null, empty, or too long
     */
    public void store(String key, String response, int statusCode) {
        Objects.requireNonNull(key, "key cannot be null");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }
        if (key.length() > 512) {
            throw new IllegalArgumentException("key too long (max 512 chars): " + key.length());
        }
        Objects.requireNonNull(response, "response cannot be null");

        CachedResponse cached = new CachedResponse(key, response, statusCode, Instant.now());
        responseCache.put(key, cached);

        log.debug("Cached idempotent response: key={}, statusCode={}, responseLength={}",
                key, statusCode, response.length());

        // Trigger cleanup if interval elapsed
        if (shouldCleanup()) {
            performCleanup();
        }
    }

    /**
     * Retrieves a cached response if one exists and has not expired.
     *
     * @param key the idempotency key
     * @return Optional containing the cached response, or empty if not found or expired
     * @throws IllegalArgumentException if key is null or empty
     */
    public Optional<CachedResponse> retrieve(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }

        CachedResponse cached = responseCache.get(key);
        if (cached == null) {
            return Optional.empty();
        }

        if (cached.isExpired(ttlMillis)) {
            responseCache.remove(key);
            log.debug("Cached response expired: key={}", key);
            return Optional.empty();
        }

        log.debug("Retrieved cached idempotent response: key={}, age={}ms",
                key, System.currentTimeMillis() - cached.timestamp().toEpochMilli());
        return Optional.of(cached);
    }

    /**
     * Checks if a request key has been processed before (idempotent duplicate detection).
     *
     * @param key the idempotency key
     * @return true if the key exists and has not expired, false otherwise
     */
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        CachedResponse cached = responseCache.get(key);
        if (cached == null) {
            return false;
        }

        if (cached.isExpired(ttlMillis)) {
            responseCache.remove(key);
            return false;
        }

        return true;
    }

    /**
     * Removes an idempotency key from the store.
     *
     * @param key the key to remove
     * @return true if removed, false if not found
     */
    public boolean remove(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return responseCache.remove(key) != null;
    }

    /**
     * Returns the number of cached responses currently stored.
     *
     * @return count of cached entries
     */
    public int size() {
        return responseCache.size();
    }

    /**
     * Clears all cached responses.
     */
    public void clear() {
        int oldSize = responseCache.size();
        responseCache.clear();
        log.info("IdempotencyKeyStore cleared: {} entries removed", oldSize);
    }

    /**
     * Removes expired entries from the cache.
     * Called automatically at configurable intervals.
     */
    private void performCleanup() {
        int beforeSize = responseCache.size();

        responseCache.entrySet().removeIf(entry -> entry.getValue().isExpired(ttlMillis));

        int removed = beforeSize - responseCache.size();
        if (removed > 0) {
            log.info("IdempotencyKeyStore cleanup: {} expired entries removed", removed);
        }

        lastCleanup = System.currentTimeMillis();
    }

    /**
     * Returns true if cleanup should be performed based on elapsed time.
     *
     * @return true if cleanup interval has elapsed
     */
    private boolean shouldCleanup() {
        long elapsedMillis = System.currentTimeMillis() - lastCleanup;
        long cleanupIntervalMillis = TimeUnit.MINUTES.toMillis(CLEANUP_INTERVAL_MINUTES);
        return elapsedMillis > cleanupIntervalMillis;
    }

    /**
     * Returns the TTL for this store in milliseconds.
     *
     * @return TTL in milliseconds
     */
    public long getTtlMillis() {
        return ttlMillis;
    }

    /**
     * Returns the TTL for this store in minutes (for human readability).
     *
     * @return TTL in minutes
     */
    public long getTtlMinutes() {
        return TimeUnit.MILLISECONDS.toMinutes(ttlMillis);
    }
}
