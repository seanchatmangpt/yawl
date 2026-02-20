/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.resilience.observability.FallbackObservability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * P1 CRITICAL - Vault Integration Optimization: TTL-based credential cache.
 *
 * <p>Eliminates repeated environment variable lookups and future Vault HTTP calls
 * by caching credential values with a configurable TTL. On Vault secret rotation
 * (detected via a forced invalidation call), all cached entries are evicted so the
 * next access re-fetches from the source.</p>
 *
 * <p>Performance targets:</p>
 * <ul>
 *   <li>Cache hit: &lt;1ms (ConcurrentHashMap lookup, no I/O)</li>
 *   <li>Cache miss / first-load: same as uncached (~200ms for Vault, ~1ms for env-var)</li>
 *   <li>Target for cached credential access: &lt;50ms (well below the 200ms baseline)</li>
 * </ul>
 *
 * <p>Thread safety: reads use a shared read-lock; writes use an exclusive write-lock via
 * {@link ReentrantReadWriteLock}. The lock is only held for the brief map mutation, not
 * while the supplier fetches credentials.</p>
 *
 * <p>Batch pre-warm: call {@link #prewarm(List)} at engine startup to load all known
 * credential keys in a single pass before request traffic begins.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class VaultCredentialCache {

    private static final Logger _logger = LogManager.getLogger(VaultCredentialCache.class);

    /** Default TTL: 5 minutes. Covers typical Vault lease windows. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /** Eviction sweep interval: run once per TTL period. */
    private static final long SWEEP_PERIOD_SECONDS = DEFAULT_TTL.getSeconds();

    private static volatile VaultCredentialCache _instance;
    private static final Object _instanceLock = new Object();

    // ------------------------------------------------------------------ state

    /** ConcurrentHashMap for lock-free reads on the common (cache-hit) path. */
    private final ConcurrentHashMap<String, CachedEntry> _cache;

    /** Per-key suppliers registered at startup or on first access. */
    private final ConcurrentHashMap<String, Supplier<String>> _suppliers;

    private final Duration _ttl;
    private final ReentrantReadWriteLock _rwLock;
    private final ScheduledExecutorService _sweeper;

    // ------------------------------------------------------------------ metrics (nanoseconds)

    private volatile long _totalHits;
    private volatile long _totalMisses;
    private volatile long _totalHitNanos;
    private volatile long _totalMissNanos;
    private volatile long _invalidations;

    // ------------------------------------------------------------------ inner types

    /** Immutable cache entry holding the credential value and its expiry instant. */
    private static final class CachedEntry {
        final String value;
        final Instant expiresAt;

        CachedEntry(String value, Duration ttl) {
            this.value = value;
            this.expiresAt = Instant.now().plus(ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    // ------------------------------------------------------------------ construction

    private VaultCredentialCache(Duration ttl) {
        this._ttl = ttl;
        this._cache = new ConcurrentHashMap<>(64);
        this._suppliers = new ConcurrentHashMap<>(64);
        this._rwLock = new ReentrantReadWriteLock();

        // Daemon sweeper removes stale entries so memory does not grow unbounded.
        this._sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vault-credential-cache-sweeper");
            t.setDaemon(true);
            return t;
        });
        _sweeper.scheduleAtFixedRate(this::evictExpired,
                SWEEP_PERIOD_SECONDS, SWEEP_PERIOD_SECONDS, TimeUnit.SECONDS);

        _logger.info("VaultCredentialCache initialized with TTL={}s", ttl.getSeconds());
    }

    /**
     * Returns the singleton cache instance with the default TTL of 5 minutes.
     *
     * @return the singleton instance
     */
    public static VaultCredentialCache getInstance() {
        if (_instance == null) {
            synchronized (_instanceLock) {
                if (_instance == null) {
                    _instance = new VaultCredentialCache(DEFAULT_TTL);
                }
            }
        }
        return _instance;
    }

    /**
     * Returns a singleton instance with a custom TTL.  Intended for testing.
     *
     * @param ttl the desired time-to-live; must be positive
     * @return a cache instance with the given TTL
     */
    public static VaultCredentialCache withTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive: " + ttl);
        }
        return new VaultCredentialCache(ttl);
    }

    // ------------------------------------------------------------------ public API

    /**
     * Registers a supplier for a credential key.  The supplier is called only on
     * cache misses; on hits the cached string is returned in &lt;1ms.
     *
     * @param key      the credential key (e.g. {@code "YAWL_PASSWORD"})
     * @param supplier the function that retrieves the real credential value
     */
    public void register(String key, Supplier<String> supplier) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Credential key must not be null or blank");
        }
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier must not be null for key: " + key);
        }
        _suppliers.put(key, supplier);
        _logger.debug("Registered supplier for credential key '{}'", key);
    }

    /**
     * Retrieves a credential, serving from cache when the entry is present and not expired.
     *
     * <p>On a cache miss the registered supplier is invoked and the result is stored.
     * If no supplier is registered, {@link IllegalStateException} is thrown.</p>
     *
     * @param key the credential key
     * @return the credential value; never null
     * @throws IllegalStateException if no supplier is registered for {@code key}
     * @throws RuntimeException      if the supplier fails
     */
    public String get(String key) {
        long start = System.nanoTime();

        // Fast path: check cache without locking (ConcurrentHashMap is thread-safe for reads)
        CachedEntry entry = _cache.get(key);
        if (entry != null && !entry.isExpired()) {
            long elapsed = System.nanoTime() - start;
            _totalHits++;
            _totalHitNanos += elapsed;
            _logger.trace("Cache HIT  key='{}' elapsed={}ns", key, elapsed);
            return entry.value;
        }

        // Slow path: load from supplier and cache result
        long elapsed = fetchAndCache(key, start);
        _totalMissNanos += elapsed;
        return _cache.get(key).value;
    }

    /**
     * Pre-warms the cache for all supplied keys by invoking each registered supplier now.
     * Call this at engine startup to eliminate cold-start latency under request traffic.
     *
     * @param keys the credential keys to pre-warm
     */
    public void prewarm(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        _logger.info("Pre-warming credential cache for {} key(s)", keys.size());
        long start = System.nanoTime();
        int loaded = 0;
        for (String key : keys) {
            try {
                get(key);
                loaded++;
            } catch (Exception e) {
                _logger.warn("Prewarm failed for key '{}': {}", key, e.getMessage());
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        _logger.info("Credential cache pre-warm complete: {}/{} keys loaded in {}ms",
                loaded, keys.size(), elapsedMs);
    }

    /**
     * Retrieves a credential with fallback and staleness tracking.
     *
     * <p>If the cache entry is expired or missing, records this as a fallback operation
     * with FallbackObservability. This enables P1 Andon alerts when stale credentials
     * are used.</p>
     *
     * @param key the credential key
     * @param fallbackValue fallback value if cache miss and supplier fails (can be null)
     * @return CredentialResult with value and staleness information
     */
    public CredentialResult getWithFallback(String key, String fallbackValue) {
        long start = System.nanoTime();
        CachedEntry entry = _cache.get(key);

        if (entry != null && !entry.isExpired()) {
            // Cache hit - fresh data
            long elapsed = System.nanoTime() - start;
            _totalHits++;
            _totalHitNanos += elapsed;
            _logger.trace("Cache HIT  key='{}' elapsed={}ns", key, elapsed);
            return new CredentialResult(entry.value, false, Duration.between(entry.expiresAt.minus(_ttl), Instant.now()));
        }

        // Cache miss or expired - attempt fetch
        Supplier<String> supplier = _suppliers.get(key);
        if (supplier == null) {
            // No supplier - use fallback value with observability
            if (fallbackValue != null) {
                FallbackObservability fallbackObs = FallbackObservability.getInstance();
                FallbackObservability.FallbackResult result = fallbackObs.recordDefaultFallback(
                    "vault-credential-cache",
                    "get:" + key,
                    FallbackObservability.FallbackReason.SERVICE_UNAVAILABLE,
                    fallbackValue
                );

                _logger.warn("Using fallback value for key '{}' (no supplier registered)", key);
                return new CredentialResult(fallbackValue, true, null);
            }
            throw new IllegalStateException(
                    "No supplier registered for credential key '" + key +
                    "'. Call register() before get().");
        }

        // Entry expired - record as stale data fallback
        if (entry != null && entry.isExpired()) {
            Instant entryCreated = entry.expiresAt.minus(_ttl);
            FallbackObservability fallbackObs = FallbackObservability.getInstance();

            // First try to get fresh value
            try {
                String freshValue = supplier.get();
                if (freshValue != null) {
                    // Update cache with fresh value
                    _rwLock.writeLock().lock();
                    try {
                        _cache.put(key, new CachedEntry(freshValue, _ttl));
                    } finally {
                        _rwLock.writeLock().unlock();
                    }
                    long elapsed = System.nanoTime() - start;
                    _totalMisses++;
                    _totalMissNanos += elapsed;
                    _logger.debug("Refreshed expired credential for key '{}'", key);
                    return new CredentialResult(freshValue, false, Duration.between(entryCreated, Instant.now()));
                }
            } catch (Exception e) {
                _logger.warn("Failed to refresh expired credential for key '{}': {}", key, e.getMessage());
            }

            // Use expired value as stale fallback
            FallbackObservability.FallbackResult result = fallbackObs.recordCacheFallback(
                "vault-credential-cache",
                "get:" + key,
                () -> entry.value,
                entryCreated
            );

            _logger.warn("Using expired (stale) credential for key '{}' (age={}ms)",
                key, result.getDataAgeMs());
            return new CredentialResult(entry.value, true, Duration.between(entryCreated, Instant.now()));
        }

        // Complete cache miss - fetch fresh
        long elapsed = fetchAndCache(key, start);
        _totalMissNanos += elapsed;
        String value = _cache.get(key).value;
        return new CredentialResult(value, false, Duration.ZERO);
    }

    /**
     * Result of a credential retrieval with staleness information.
     */
    public static final class CredentialResult {
        private final String value;
        private final boolean isStale;
        private final Duration age;

        private CredentialResult(String value, boolean isStale, Duration age) {
            this.value = value;
            this.isStale = isStale;
            this.age = age;
        }

        public String getValue() { return value; }
        public boolean isStale() { return isStale; }
        public Duration getAge() { return age; }
    }

    /**
     * Invalidates a single credential entry, forcing a fresh fetch on next access.
     * Use this when notified of a Vault secret rotation for a specific key.
     *
     * @param key the credential key to invalidate
     */
    public void invalidate(String key) {
        _cache.remove(key);
        _invalidations++;
        _logger.info("Credential cache invalidated for key '{}'", key);
    }

    /**
     * Invalidates all cached credentials, forcing a complete re-fetch on next access.
     * Use this when notified of a global Vault secret rotation event.
     */
    public void invalidateAll() {
        int size = _cache.size();
        _cache.clear();
        _invalidations += size;
        _logger.info("Credential cache fully invalidated ({} entries evicted)", size);
    }

    /**
     * Returns a snapshot of cache performance metrics.
     *
     * @return a {@link CacheMetrics} instance with hit/miss statistics
     */
    public CacheMetrics getMetrics() {
        long hits = _totalHits;
        long misses = _totalMisses;
        long hitNanos = _totalHitNanos;
        long missNanos = _totalMissNanos;

        double avgHitMs  = hits > 0  ? (hitNanos  / (double) hits  / 1_000_000.0) : 0.0;
        double avgMissMs = misses > 0 ? (missNanos / (double) misses / 1_000_000.0) : 0.0;

        return new CacheMetrics(hits, misses, avgHitMs, avgMissMs,
                _cache.size(), _invalidations);
    }

    /**
     * Shuts down the background sweeper thread.  Call on engine shutdown.
     */
    public void shutdown() {
        _sweeper.shutdownNow();
        _logger.info("VaultCredentialCache sweeper stopped");
    }

    // ------------------------------------------------------------------ private helpers

    /**
     * Fetches a credential from its supplier and stores it in the cache.
     * Acquisition of the write-lock is brief (only for the map put), not for the I/O.
     */
    private long fetchAndCache(String key, long startNs) {
        Supplier<String> supplier = _suppliers.get(key);
        if (supplier == null) {
            throw new IllegalStateException(
                    "No supplier registered for credential key '" + key +
                    "'. Call register() before get().");
        }

        // Invoke supplier outside any lock to avoid holding the lock during I/O
        String value;
        try {
            value = supplier.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve credential for key '" + key + "'", e);
        }

        if (value == null) {
            throw new IllegalStateException(
                    "Supplier for key '" + key + "' returned null - credentials must never be null");
        }

        // Write lock only for the ConcurrentHashMap.put (microseconds)
        _rwLock.writeLock().lock();
        try {
            // Double-check: another thread may have populated the entry while we fetched
            CachedEntry existing = _cache.get(key);
            if (existing != null && !existing.isExpired()) {
                long elapsed = System.nanoTime() - startNs;
                _totalMisses++;
                return elapsed;
            }
            _cache.put(key, new CachedEntry(value, _ttl));
        } finally {
            _rwLock.writeLock().unlock();
        }

        long elapsed = System.nanoTime() - startNs;
        _totalMisses++;
        _logger.debug("Cache MISS key='{}' elapsed={}ms", key, elapsed / 1_000_000);
        return elapsed;
    }

    /** Evicts all expired entries. Called by the daemon sweeper. */
    private void evictExpired() {
        int before = _cache.size();
        _cache.entrySet().removeIf(e -> e.getValue().isExpired());
        int evicted = before - _cache.size();
        if (evicted > 0) {
            _logger.debug("VaultCredentialCache sweeper evicted {} expired entries", evicted);
        }
    }

    // ------------------------------------------------------------------ inner types (public)

    /**
     * Snapshot of cache performance statistics.
     *
     * @param hits          total cache hits
     * @param misses        total cache misses
     * @param avgHitMs      average hit latency in milliseconds
     * @param avgMissMs     average miss latency in milliseconds
     * @param currentSize   number of entries currently in the cache
     * @param invalidations total invalidations performed
     */
    public record CacheMetrics(
            long hits,
            long misses,
            double avgHitMs,
            double avgMissMs,
            int currentSize,
            long invalidations) {

        /** Hit ratio in the range [0.0, 1.0]. Returns 0 if no requests yet. */
        public double hitRatio() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheMetrics{hits=%d, misses=%d, hitRatio=%.1f%%, avgHitMs=%.3f, " +
                "avgMissMs=%.3f, size=%d, invalidations=%d}",
                hits, misses, hitRatio() * 100, avgHitMs, avgMissMs,
                currentSize, invalidations);
        }
    }
}
