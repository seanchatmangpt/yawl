package org.yawlfoundation.yawl.dspy.performance;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Optimized LRU cache for frequently accessed data structures.
 * 
 * <p>Provides thread-safe caching with configurable eviction policies,
 * memory monitoring, and performance tracking. Optimized for low-latency
 * access patterns typical in DSPy optimization workflows.</p>
 * 
 * <h3>Performance Optimizations</h3>
 * <ul>
 *   <li>Concurrent access with read-write locks</li>
 *   <li>Memory monitoring with soft size limits</li>
 *   <li>Hit/miss statistics for tuning</li>
 *   <li>Eviction callbacks for resource cleanup</li>
 *   <li>Batch operations for reduced lock contention</li>
 * </ul>
 * 
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class OptimizedCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(OptimizedCache.class);

    // Configuration
    private final int maxSize;
    private final long maxMemoryBytes;
    private final EvictionPolicy evictionPolicy;
    
    // Statistics
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);
    private final AtomicLong currentMemoryBytes = new AtomicLong(0);
    
    // Cache storage with LRU support
    private final ConcurrentHashMap<K, CacheEntry<V>> cacheMap;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Eviction listener
    private final EvictionListener<K, V> evictionListener;
    
    /**
     * Eviction policies.
     */
    public enum EvictionPolicy {
        LRU, // Least Recently Used
        LFU, // Least Frequently Used
        FIFO // First In First Out
    }
    
    /**
     * Interface for eviction callbacks.
     */
    @FunctionalInterface
    public interface EvictionListener<K, V> {
        void onEviction(K key, V value);
    }
    
    /**
     * Cache entry with metadata.
     */
    private static class CacheEntry<V> {
        final V value;
        final long sizeBytes;
        final long createdAt;
        long lastAccessed;
        int accessCount;
        
        CacheEntry(V value, long sizeBytes) {
            this.value = value;
            this.sizeBytes = sizeBytes;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessed = this.createdAt;
            this.accessCount = 1;
        }
        
        void recordAccess() {
            this.lastAccessed = System.currentTimeMillis();
            this.accessCount++;
        }
    }
    
    /**
     * Creates a new optimized cache with default settings.
     */
    public OptimizedCache() {
        this(1000, 100 * 1024 * 1024); // 1000 items, 100MB
    }
    
    /**
     * Creates a new optimized cache with custom settings.
     */
    public OptimizedCache(int maxSize, long maxMemoryBytes) {
        this(maxSize, maxMemoryBytes, EvictionPolicy.LRU, null);
    }
    
    /**
     * Creates a new optimized cache with all settings.
     */
    public OptimizedCache(int maxSize, long maxMemoryBytes, 
                         EvictionPolicy evictionPolicy,
                         EvictionListener<K, V> evictionListener) {
        this.maxSize = maxSize;
        this.maxMemoryBytes = maxMemoryBytes;
        this.evictionPolicy = evictionPolicy;
        this.evictionListener = evictionListener;
        this.cacheMap = new ConcurrentHashMap<>(maxSize);
    }
    
    /**
     * Retrieves a value from the cache with hit tracking.
     */
    public @Nullable V get(K key) {
        if (key == null) return null;
        
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = cacheMap.get(key);
            if (entry != null) {
                entry.recordAccess();
                totalHits.incrementAndGet();
                return entry.value;
            }
            totalMisses.incrementAndGet();
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Stores a value in the cache with memory tracking.
     */
    public void put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Key and value must not be null");
        }
        
        lock.writeLock().lock();
        try {
            // Check if already exists
            CacheEntry<V> existing = cacheMap.get(key);
            if (existing != null) {
                // Update existing entry
                currentMemoryBytes.addAndGet(-existing.sizeBytes);
                cacheMap.remove(key);
            }
            
            // Create new entry with size estimation
            long estimatedSize = estimateSize(value);
            CacheEntry<V> newEntry = new CacheEntry<>(value, estimatedSize);
            
            // Check eviction conditions
            checkAndEvict(estimatedSize);
            
            // Add to cache
            cacheMap.put(key, newEntry);
            currentMemoryBytes.addAndGet(estimatedSize);
            
            log.debug("Cache put: key={}, size={}B, total={}B", 
                    key, estimatedSize, currentMemoryBytes.get());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Checks if a key exists in the cache.
     */
    public boolean contains(K key) {
        if (key == null) return false;
        
        lock.readLock().lock();
        try {
            return cacheMap.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Removes a specific key from the cache.
     */
    public boolean remove(K key) {
        if (key == null) return false;
        
        lock.writeLock().lock();
        try {
            CacheEntry<V> entry = cacheMap.remove(key);
            if (entry != null) {
                currentMemoryBytes.addAndGet(-entry.sizeBytes);
                if (evictionListener != null) {
                    evictionListener.onEviction(key, entry.value);
                }
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Returns the current cache size.
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cacheMap.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Returns cache statistics.
     */
    public Map<String, Object> getStats() {
        lock.readLock().lock();
        try {
            long hits = totalHits.get();
            long misses = totalMisses.get();
            double hitRate = hits > 0 ? (double) hits / (hits + misses) : 0.0;
            
            Map<String, Object> stats = new ConcurrentHashMap<>();
            stats.put("size", cacheMap.size());
            stats.put("maxSize", maxSize);
            stats.put("memoryBytes", currentMemoryBytes.get());
            stats.put("maxMemoryBytes", maxMemoryBytes);
            stats.put("hits", hits);
            stats.put("misses", misses);
            stats.put("hitRate", hitRate);
            stats.put("evictions", totalEvictions.get());
            stats.put("evictionPolicy", evictionPolicy);
            
            return Map.copyOf(stats);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            int size = cacheMap.size();
            cacheMap.clear();
            currentMemoryBytes.set(0);
            totalHits.set(0);
            totalMisses.set(0);
            totalEvictions.set(0);
            log.info("Cache cleared: {} entries removed", size);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Checks and performs eviction if necessary.
     */
    private void checkAndEvict(long newSize) {
        while ((cacheMap.size() >= maxSize || 
               currentMemoryBytes.get() + newSize > maxMemoryBytes) &&
               !cacheMap.isEmpty()) {
            
            K keyToEvict = selectEvictionCandidate();
            if (keyToEvict != null) {
                CacheEntry<V> entry = cacheMap.remove(keyToEvict);
                if (entry != null) {
                    currentMemoryBytes.addAndGet(-entry.sizeBytes);
                    totalEvictions.incrementAndGet();
                    
                    if (evictionListener != null) {
                        evictionListener.onEviction(keyToEvict, entry.value);
                    }
                }
            }
        }
    }
    
    /**
     * Selects the next candidate for eviction based on policy.
     */
    private K selectEvictionCandidate() {
        switch (evictionPolicy) {
            case LRU:
                return findLeastRecentlyUsed();
            case LFU:
                return findLeastFrequentlyUsed();
            case FIFO:
                return findFirstInFirstOut();
            default:
                return cacheMap.keySet().iterator().next();
        }
    }
    
    /**
     * Finds the least recently used key.
     */
    private K findLeastRecentlyUsed() {
        K lruKey = null;
        long minAccessTime = Long.MAX_VALUE;
        
        for (Map.Entry<K, CacheEntry<V>> entry : cacheMap.entrySet()) {
            if (entry.getValue().lastAccessed < minAccessTime) {
                minAccessTime = entry.getValue().lastAccessed;
                lruKey = entry.getKey();
            }
        }
        return lruKey;
    }
    
    /**
     * Finds the least frequently used key.
     */
    private K findLeastFrequentlyUsed() {
        K lfuKey = null;
        int minAccessCount = Integer.MAX_VALUE;
        
        for (Map.Entry<K, CacheEntry<V>> entry : cacheMap.entrySet()) {
            if (entry.getValue().accessCount < minAccessCount) {
                minAccessCount = entry.getValue().accessCount;
                lfuKey = entry.getKey();
            }
        }
        return lfuKey;
    }
    
    /**
     * Finds the first in first out key (oldest).
     */
    private K findFirstInFirstOut() {
        K fifoKey = null;
        long minCreateTime = Long.MAX_VALUE;
        
        for (Map.Entry<K, CacheEntry<V>> entry : cacheMap.entrySet()) {
            if (entry.getValue().createdAt < minCreateTime) {
                minCreateTime = entry.getValue().createdAt;
                fifoKey = entry.getKey();
            }
        }
        return fifoKey;
    }
    
    /**
     * Estimates the size of a value in bytes.
     * Override for more accurate size estimation.
     */
    protected long estimateSize(V value) {
        // Simple estimation: 100 bytes per object
        // Override in subclass for more accurate estimation
        return 100;
    }
    
    /**
     * Returns a snapshot of the current cache contents.
     */
    public Map<K, V> snapshot() {
        lock.readLock().lock();
        try {
            Map<K, V> snapshot = new ConcurrentHashMap<>(cacheMap.size());
            for (Map.Entry<K, CacheEntry<V>> entry : cacheMap.entrySet()) {
                snapshot.put(entry.getKey(), entry.getValue().value);
            }
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }
}
