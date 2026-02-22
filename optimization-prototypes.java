// YAWL Engine Performance Optimization Prototypes
// These are code examples demonstrating the recommended optimizations

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Prototype 1: Fine-Grained Locking Strategy for YNetRunner
 * 
 * This demonstrates how to split the coarse-grained write lock
 * into separate read and write operations for better concurrency.
 */
public class YNetRunnerOptimized {
    
    // Current implementation (problematic)
    private final ReentrantReadWriteLock _executionLock = new ReentrantReadWriteLock(true);
    private final ReentrantReadWriteLock.ReadLock _readLock = _executionLock.readLock();
    private final ReentrantReadWriteLock.WriteLock _writeLock = _executionLock.writeLock();
    
    /**
     * Current implementation - entire operation under write lock
     */
    public void kick_current(YPersistenceManager pmgr) {
        _writeLock.lock();
        try {
            // 50-200ms of work under exclusive lock
            if (!continueIfPossible(pmgr)) {
                // Handle completion...
            }
        } finally {
            _writeLock.unlock();
        }
    }
    
    /**
     * Optimized implementation - split into read and write phases
     */
    public void kick_optimized(YPersistenceManager pmgr) {
        // Phase 1: Read-only checks under read lock
        boolean shouldContinue;
        _readLock.lock();
        try {
            shouldContinue = checkEnabledTasks();  // Read-only operation
        } finally {
            _readLock.unlock();
        }
        
        // Phase 2: State updates under write lock (only if needed)
        if (shouldContinue) {
            _writeLock.lock();
            try {
                if (!continueIfPossible(pmgr)) {
                    // Handle completion...
                }
            } finally {
                _writeLock.unlock();
            }
        }
    }
    
    private boolean checkEnabledTasks() {
        // Lightweight check - doesn't modify state
        return !_enabledTasks.isEmpty();
    }
}

/**
 * Prototype 2: Virtual Thread Integration
 * 
 * Demonstrates migrating from platform threads to virtual threads
 * for better scalability with I/O-bound workflow operations.
 */
public class VirtualThreadOptimization {
    
    // Current implementation
    private final ExecutorService platformPool = 
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    
    /**
     * Current implementation - platform threads
     */
    public void executeWithPlatformThreads(List<String> caseIds) {
        List<Future<?>> futures = new ArrayList<>();
        
        for (String caseId : caseIds) {
            futures.add(platformPool.submit(() -> {
                executeCase(caseId);  // Each case on a platform thread
            }));
        }
        
        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Handle failure
            }
        }
    }
    
    /**
     * Optimized implementation - virtual threads
     */
    private final ExecutorService virtualPool = 
        Executors.newVirtualThreadPerTaskExecutor();
    
    public void executeWithVirtualThreads(List<String> caseId) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String caseId : caseIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> executeCase(caseId), 
                virtualPool
            );
            futures.add(future);
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join();
    }
}

/**
 * Prototype 3: WorkItem Repository with Caching
 * 
 * Demonstrates adding lightweight caching and batch operations
 * to reduce database load.
 */
public class WorkItemRepositoryOptimized {
    
    private final YWorkItemRepository _repository;
    private final Cache<String, YWorkItem> _lightweightCache;
    private final Map<String, List<String>> _caseTaskCache;
    
    public WorkItemRepositoryOptimized() {
        this._repository = new YWorkItemRepository();
        // Cache for lightweight work items (10 minute TTL, 1000 max size)
        this._lightweightCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();
        
        // Cache for case-to-task relationships (1 hour TTL)
        this._caseTaskCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Current implementation - full object load
     */
    public YWorkItem get(String caseID, String taskID) {
        return _repository.get(caseID, taskID);  // Loads entire object graph
    }
    
    /**
     * Optimized implementation - lightweight version
     */
    public YWorkItem getLightweight(String caseID, String taskID) {
        String key = caseID + ":" + taskID;
        
        // Try cache first
        YWorkItem cached = _lightweightCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        
        // Load and cache
        YWorkItem item = _repository.getLightweight(caseID, taskID);
        if (item != null) {
            _lightweightCache.put(key, item);
        }
        return item;
    }
    
    /**
     * Batch operation for bulk queries
     */
    public Map<String, YWorkItem> getLightweightBatch(Collection<String> keys) {
        Map<String, YWorkItem> result = new HashMap<>();
        List<String> missingKeys = new ArrayList<>();
        
        // Check cache first
        for (String key : keys) {
            YWorkItem cached = _lightweightCache.getIfPresent(key);
            if (cached != null) {
                result.put(key, cached);
            } else {
                missingKeys.add(key);
            }
        }
        
        // Batch load missing items
        if (!missingKeys.isEmpty()) {
            Map<String, YWorkItem> loaded = _repository.getLightweightBatch(missingKeys);
            result.putAll(loaded);
            
            // Update cache
            loaded.forEach((key, item) -> {
                if (item != null) {
                    _lightweightCache.put(key, item);
                }
            });
        }
        
        return result;
    }
}

/**
 * Prototype 4: WorkItem Object Pooling
 * 
 * Demonstrates object pooling to reduce allocation pressure
 * and GC overhead for frequently created/destroyed work items.
 */
public class WorkItemPool {
    
    private final ConcurrentMap<String, YWorkItem> _pool = new ConcurrentHashMap<>();
    private final AtomicInteger _poolHits = new AtomicInteger();
    private final AtomicInteger _poolMisses = new AtomicInteger();
    private final int _maxPoolSize = 1000;
    private final AtomicInteger _currentSize = new AtomicInteger();
    
    /**
     * Get or create a work item from pool
     */
    public YWorkItem acquire(String caseID, String taskID) {
        String key = caseID + ":" + taskID;
        
        // Try to get from pool
        YWorkItem item = _pool.get(key);
        if (item != null) {
            _poolHits.incrementAndGet();
            return item;
        }
        
        // Pool miss - create new
        _poolMisses.incrementAndGet();
        
        // Check pool size limit
        if (_currentSize.get() >= _maxPoolSize) {
            // Pool full - create without caching
            return createWorkItem(caseID, taskID);
        }
        
        // Create and cache
        item = createWorkItem(caseID, taskID);
        _currentSize.incrementAndGet();
        _pool.put(key, item);
        
        return item;
    }
    
    /**
     * Return work item to pool
     */
    public void release(YWorkItem item) {
        if (item != null) {
            // Reset state for reuse
            item.resetForReuse();
            
            // Return to pool if under limit
            String key = item.getCaseID() + ":" + item.getTaskID();
            if (_currentSize.get() < _maxPoolSize) {
                _pool.put(key, item);
            } else {
                _currentSize.decrementAndGet();
            }
        }
    }
    
    /**
     * Get pool statistics
     */
    public PoolStats getStats() {
        int hits = _poolHits.get();
        int misses = _poolMisses.get();
        double hitRate = hits / (double) (hits + misses);
        
        return new PoolStats(
            hits, misses, hitRate,
            _currentSize.get(), _maxPoolSize,
            _pool.size()
        );
    }
    
    private YWorkItem createWorkItem(String caseID, String taskID) {
        // Factory method for creating new work items
        return new YWorkItem(caseID, taskID);
    }
    
    public record PoolStats(
        int hits, int misses, double hitRate,
        int currentSize, int maxSize,
        int uniqueItems
    ) {}
}

/**
 * Prototype 5: Asynchronous WorkItem Processing
 * 
 * Demonstrates using CompletableFuture for non-blocking
 * work item processing and completion.
 */
public class AsyncWorkItemProcessor {
    
    private final ExecutorService _virtualExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();
    private final YWorkItemRepository _repository;
    
    public AsyncWorkItemProcessor(YWorkItemRepository repository) {
        this._repository = repository;
    }
    
    /**
     * Current implementation - blocking
     */
    public void completeWorkItemBlocking(YWorkItem item) {
        // Blocking database operations
        _repository.updateWorkItem(item);
        
        // Blocking logging
        logCompletion(item);
        
        // Blocking notification
        notifyListeners(item);
    }
    
    /**
     * Optimized implementation - non-blocking
     */
    public CompletableFuture<Void> completeWorkItemAsync(YWorkItem item) {
        // Chain async operations
        return CompletableFuture.supplyAsync(() -> {
                // Async database update
                return _repository.updateWorkItemAsync(item);
            }, _virtualExecutor)
            .thenApplyAsync(updatedItem -> {
                // Async logging
                logCompletionAsync(updatedItem);
                return updatedItem;
            }, _virtualExecutor)
            .thenAcceptAsync(updatedItem -> {
                // Async notification
                notifyListenersAsync(updatedItem);
            }, _virtualExecutor)
            .exceptionally(ex -> {
                // Handle any failure in the chain
                logError("Failed to complete work item", ex);
                return null;
            });
    }
    
    // Async helper methods
    private CompletableFuture<YWorkItem> updateWorkItemAsync(YWorkItem item) {
        return CompletableFuture.supplyAsync(() -> {
            _repository.updateWorkItem(item);
            return item;
        }, _virtualExecutor);
    }
    
    private void logCompletionAsync(YWorkItem item) {
        CompletableFuture.runAsync(() -> {
            logCompletion(item);
        }, _virtualExecutor);
    }
    
    private void notifyListenersAsync(YWorkItem item) {
        CompletableFuture.runAsync(() -> {
            notifyListeners(item);
        }, _virtualExecutor);
    }
}

/**
 * Prototype 6: State Partitioning for Scalability
 * 
 * Demonstrates partitioning work items across multiple
 * shards to reduce contention and improve scalability.
 */
public class PartitionedWorkItemRepository {
    
    private final List<ConcurrentMap<String, YWorkItem>> _partitions;
    private final int _partitionCount;
    private final HashFunction _hashFunction;
    
    public PartitionedWorkItemRepository(int partitionCount) {
        this._partitionCount = partitionCount;
        this._partitions = new ArrayList<>(partitionCount);
        this._hashFunction = Hashing.murmur3_32();
        
        // Create partitions
        for (int i = 0; i < partitionCount; i++) {
            _partitions.add(new ConcurrentHashMap<>());
        }
    }
    
    /**
     * Get partition key for a case ID
     */
    private int getPartition(String caseID) {
        int hash = _hashFunction.hashUnencodedChars(caseID);
        return (hash & Integer.MAX_VALUE) % _partitionCount;
    }
    
    /**
     * Current implementation - single map
     */
    public YWorkItem get_single(String caseID, String taskID) {
        return _repository.get(caseID, taskID);
    }
    
    /**
     * Optimized implementation - partitioned access
     */
    public YWorkItem get_partitioned(String caseID, String taskID) {
        int partition = getPartition(caseID);
        ConcurrentMap<String, YWorkItem> partitionMap = _partitions.get(partition);
        return partitionMap.get(caseID + ":" + taskID);
    }
    
    /**
     * Get all work items for a case (cross-partition)
     */
    public List<YWorkItem> getWorkItemsForCase(String caseID) {
        List<YWorkItem> result = new ArrayList<>();
        String caseKey = caseID + ":";
        
        // Search all partitions
        for (ConcurrentMap<String, YWorkItem> partition : _partitions) {
            partition.keySet().stream()
                .filter(key -> key.startsWith(caseKey))
                .forEach(key -> {
                    YWorkItem item = partition.get(key);
                    if (item != null) {
                        result.add(item);
                    }
                });
        }
        
        return result;
    }
    
    /**
     * Get partition statistics for load balancing
     */
    public PartitionStats[] getPartitionStats() {
        PartitionStats[] stats = new PartitionStats[_partitionCount];
        
        for (int i = 0; i < _partitionCount; i++) {
            ConcurrentMap<String, YWorkItem> partition = _partitions.get(i);
            stats[i] = new PartitionStats(
                i, 
                partition.size(),
                partition.size() * 0.75,  // Estimated memory usage
                getLoadFactor(partition)
            );
        }
        
        return stats;
    }
    
    private double getLoadFactor(ConcurrentMap<String, YWorkItem> partition) {
        // Simple load factor based on size
        double targetSize = 1000.0;  // Target partition size
        return partition.size() / targetSize;
    }
    
    public record PartitionStats(
        int partitionId,
        int itemCount,
        long memoryUsage,
        double loadFactor
    ) {}
}

// Helper classes and interfaces
interface YPersistenceManager {
    // Persistence interface
}

class YWorkItem {
    private String _caseID;
    private String _taskID;
    private Object _data;
    
    public YWorkItem(String caseID, String taskID) {
        this._caseID = caseID;
        this._taskID = taskID;
    }
    
    public String getCaseID() { return _caseID; }
    public String getTaskID() { return _taskID; }
    
    public void resetForReuse() {
        // Reset state for pooling
        this._data = null;
    }
}

class YWorkItemRepository {
    public YWorkItem get(String caseID, String taskID) {
        // Full object loading
        return new YWorkItem(caseID, taskID);
    }
    
    public YWorkItem getLightweight(String caseID, String taskID) {
        // Lightweight loading
        return new YWorkItem(caseID, taskID);
    }
    
    public Map<String, YWorkItem> getLightweightBatch(Collection<String> keys) {
        // Batch loading
        Map<String, YWorkItem> result = new HashMap<>();
        for (String key : keys) {
            String[] parts = key.split(":");
            result.put(key, new YWorkItem(parts[0], parts[1]));
        }
        return result;
    }
    
    public void updateWorkItem(YWorkItem item) {
        // Update operation
    }
}
