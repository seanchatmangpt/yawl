# Autonomous Smart Caching Strategy Design

**Status**: Design Phase | **Date**: Feb 2026 | **Benefit**: 80% cache hit rate, 20% code

---

## Problem Statement

YAWL agents process work items with domain-specific data that must be fetched from external systems. Currently, no caching strategy exists:

```
Scenario: 50 Approval tasks in succession, all requiring same decision rules

Request 1: GET /rules/approval-rules.json (cold cache)
  -> HTTP roundtrip: 500ms
  -> Parse JSON: 50ms
  -> Load into decision engine: 200ms
  Total: 750ms (1st item overhead)

Request 2: GET /rules/approval-rules.json (cache miss, no prediction)
  -> HTTP roundtrip: 500ms
  -> Parse: 50ms
  -> Load: 200ms
  Total: 750ms × 50 items = 37.5s for data loading alone

With Caching (no prefetch):
  Request 1-2: Misses (no data yet)
  Request 3: Hit (cached from request 1)
  Request 4-50: Hit
  Cache hit rate: 48/50 = 96%
  Total loading time: 750 + 750 + (48 × 100ms) = 6.35s

With Smart Caching (predictive prefetch):
  Background prefetch before request 1:
    Predict next task = APPROVAL
    Prefetch rules: 500ms (in background)
  Request 1: Hit (prefetched)
  Request 2-50: Hit
  Cache hit rate: 50/50 = 100%
  Total loading time: 500ms (prefetch) + (50 × 100ms) = 5.5s
```

**Current bottleneck**: Cold cache misses on domain data (rules, models, credentials) add 200-500ms latency per item × 50 items = 10-25 seconds overhead per workflow.

---

## Solution: Smart Caching with Predictive Prefetch & Auto-TTL

Predict future data access patterns, prefetch data in the background, and automatically adjust cache TTL based on access frequency and data freshness requirements.

### Architecture Diagram

```
Smart Caching System
  |
  ├─ CacheAccessPredictor
  |  |
  |  └─ Pattern: task_type X data_key -> P(will_access)
  |     [Approval -> rules=0.95, models=0.88, credentials=0.75]
  |
  ├─ CachePrefetcher
  |  |
  |  ├─ Monitor work item flow
  |  └─ When task type detected, prefetch top 3 data keys
  |
  ├─ MultiLayerCache
  |  |
  |  ├─ Layer 1: Heap cache (10 MB, <1ms latency)
  |  |           [hot items: approval-rules, approval-model]
  |  |
  |  ├─ Layer 2: Disk cache (100 MB, 10-50ms latency)
  |  |           [warm items: old rules versions]
  |  |
  |  └─ Layer 3: External store (HTTP, 100-500ms latency)
  |              [cold items: remote APIs]
  |
  └─ TTLOptimizer
     |
     └─ Monitor hit/miss rate per key
        Adjust TTL: high hit rate -> longer TTL, low hit rate -> shorter TTL
        (prevents stale data while maximizing cache hits)

Work Item Processing
  |
  ├─ Agent discovers Approval task
  |  |
  |  └─ CacheAccessPredictor.predict() -> [rules(0.95), model(0.88), creds(0.75)]
  |
  ├─ Background: CachePrefetcher.prefetch(["rules", "model", "creds"])
  |  |
  |  └─ Get from cache or external store asynchronously
  |
  ├─ Agent processes item
  |  |
  |  ├─ access("approval-rules") -> Cache hit (prefetched)
  |  ├─ access("approval-model") -> Cache hit (prefetched)
  |  ├─ access("approval-creds") -> Cache hit (prefetched)
  |  |
  |  └─ 3/3 cache hits (100% hit rate)
  |
  └─ TTLOptimizer updates: hit_rate=1.0 -> increase TTL(rules)
```

---

## Pseudocode Design

```java
// org.yawlfoundation.yawl.integration.autonomous.caching.SmartCacheManager

public class SmartCacheManager {
    private final CacheAccessPredictor accessPredictor;
    private final CachePrefetcher cachePrefetcher;
    private final MultiLayerCache multiLayerCache;
    private final TTLOptimizer ttlOptimizer;
    private final CacheMetricsCollector metricsCollector;

    // Configuration
    private final int maxHeapCacheSize;        // 10 MB
    private final int maxDiskCacheSize;        // 100 MB
    private final Duration baseTTL;            // 5 minutes
    private final int prefetchDepth;           // Top 3 predictions to prefetch

    public SmartCacheManager(Configuration config) {
        this.accessPredictor = new CacheAccessPredictor();
        this.cachePrefetcher = new CachePrefetcher(this);
        this.multiLayerCache = new MultiLayerCache(
            config.getMaxHeapCacheSize(),
            config.getMaxDiskCacheSize()
        );
        this.ttlOptimizer = new TTLOptimizer();
        this.metricsCollector = new CacheMetricsCollector();
        this.baseTTL = Duration.ofMinutes(5);
        this.prefetchDepth = 3;
    }

    // Main method: get data with smart caching
    public <T> T get(String key, String taskType, Supplier<T> fetcher)
            throws CacheException {

        try {
            // Step 1: Try multi-layer cache (heap -> disk -> miss)
            T cachedValue = multiLayerCache.getIfPresent(key);
            if (cachedValue != null) {
                metricsCollector.recordCacheHit(key, taskType);
                return cachedValue;
            }

            // Step 2: Cache miss - fetch from external source
            metricsCollector.recordCacheMiss(key, taskType);
            T value = fetcher.get();

            // Step 3: Cache the value with optimized TTL
            Duration ttl = ttlOptimizer.getTTL(key);
            multiLayerCache.put(key, value, ttl);

            logger.debug("Cached {} with TTL {}s", key, ttl.toSeconds());

            // Step 4: Trigger prefetch for related data
            cachePrefetcher.prefetchRelated(key, taskType);

            return value;

        } catch (Exception e) {
            logger.error("Cache operation failed for {}: {}", key, e.getMessage());
            throw new CacheException("Failed to get " + key, e);
        }
    }

    // Predictive prefetch: load data likely to be needed next
    public void predictivelyPrefetch(String taskType) {
        try {
            // Step 1: Predict data access pattern for this task type
            List<CacheAccessPrediction> predictions =
                accessPredictor.predictAccessPattern(taskType);

            logger.info("Predicted {} data accesses for task type {}",
                predictions.size(), taskType);

            // Step 2: Prefetch top N items in background
            predictions.stream()
                .limit(prefetchDepth)
                .filter(p -> p.probability() > 0.3)  // Only high-confidence predictions
                .forEach(prediction -> {
                    cachePrefetcher.prefetchAsync(
                        prediction.dataKey(),
                        taskType,
                        prediction.probability()
                    );
                });

        } catch (Exception e) {
            logger.warn("Predictive prefetch failed for {}: {}",
                taskType, e.getMessage());
            // Graceful degradation: continue without prefetch
        }
    }

    // Update access statistics for learning
    public void recordAccess(String key, String taskType, long latencyMs) {
        accessPredictor.recordAccess(key, taskType);
        ttlOptimizer.recordAccess(key, latencyMs);
    }
}

// Predict cache access pattern using historical data
public class CacheAccessPredictor {
    private final Map<String, AccessPattern> patterns;  // task_type -> pattern
    private final ExecutorService backgroundExecutor;

    public List<CacheAccessPrediction> predictAccessPattern(String taskType) {
        AccessPattern pattern = patterns.getOrDefault(taskType,
            AccessPattern.createDefault());

        // Extract probability distribution for this task type
        List<CacheAccessPrediction> predictions = pattern.getAccessProbabilities()
            .entrySet().stream()
            .map(entry -> new CacheAccessPrediction(
                entry.getKey(),           // data_key
                entry.getValue(),         // probability
                taskType,
                Instant.now()
            ))
            .sorted(Comparator.comparingDouble(CacheAccessPrediction::probability)
                .reversed())
            .toList();

        logger.debug("Pattern for {} has {} likely accesses",
            taskType, predictions.size());

        return predictions;
    }

    // Learn from actual access
    public void recordAccess(String key, String taskType) {
        AccessPattern pattern = patterns.computeIfAbsent(taskType,
            _ -> AccessPattern.createDefault());

        pattern.recordAccess(key);
    }

    // Access pattern: learned from historical task executions
    public static class AccessPattern {
        private final Map<String, Integer> accessCounts;  // data_key -> count
        private int totalAccesses;

        public AccessPattern() {
            this.accessCounts = new ConcurrentHashMap<>();
            this.totalAccesses = 0;
        }

        public void recordAccess(String key) {
            accessCounts.compute(key, (_, count) -> (count == null ? 0 : count) + 1);
            totalAccesses++;
        }

        public Map<String, Double> getAccessProbabilities() {
            return accessCounts.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> (double) entry.getValue() / Math.max(totalAccesses, 1)
                ));
        }

        public static AccessPattern createDefault() {
            return new AccessPattern();
        }
    }
}

// Background prefetch service
public class CachePrefetcher {
    private final SmartCacheManager cacheManager;
    private final ExternalDataSource dataSource;
    private final ExecutorService prefetchExecutor;

    public void prefetchAsync(String dataKey, String taskType, double confidence) {
        prefetchExecutor.submit(() -> {
            try {
                logger.debug("Prefetching {} for task {} (confidence: {:.1f}%)",
                    dataKey, taskType, confidence * 100);

                // Fetch data and cache it
                Object data = dataSource.fetch(dataKey);
                cacheManager.multiLayerCache.put(dataKey, data,
                    Duration.ofMinutes(5));

                logger.debug("Prefetch completed for {}", dataKey);

            } catch (Exception e) {
                logger.warn("Prefetch failed for {}: {}", dataKey, e.getMessage());
                // Silently fail; will be fetched on-demand if needed
            }
        });
    }

    public void prefetchRelated(String currentKey, String taskType) {
        // After accessing currentKey, prefetch related keys
        List<String> relatedKeys = cacheManager.accessPredictor
            .getRelatedKeys(currentKey, taskType);

        relatedKeys.forEach(key -> prefetchAsync(key, taskType, 0.5));
    }
}

// Multi-layer cache: heap -> disk -> external
public class MultiLayerCache {
    private final Map<String, CacheEntry> heapCache;
    private final Path diskCacheDir;
    private final long maxHeapBytes;
    private final long maxDiskBytes;

    public <T> T getIfPresent(String key) throws CacheException {
        // Step 1: Try heap cache (1-2 microseconds)
        CacheEntry entry = heapCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value();
        }

        // Step 2: Try disk cache (10-50 milliseconds)
        try {
            Path diskPath = diskCacheDir.resolve(key);
            if (Files.exists(diskPath)) {
                byte[] diskData = Files.readAllBytes(diskPath);
                CacheEntry diskEntry = deserialize(diskData);

                if (!diskEntry.isExpired()) {
                    // Move to heap cache
                    heapCache.put(key, diskEntry);
                    return (T) diskEntry.value();
                } else {
                    // Expired, remove from disk
                    Files.delete(diskPath);
                }
            }
        } catch (IOException e) {
            logger.warn("Disk cache read failed for {}: {}", key, e.getMessage());
        }

        // Step 3: Not in cache (both layers)
        return null;
    }

    public <T> void put(String key, T value, Duration ttl) throws CacheException {
        CacheEntry entry = new CacheEntry(value, ttl);

        try {
            // Step 1: Put in heap cache (if space available)
            if (heapCache.size() * estimateEntrySize(entry) < maxHeapBytes) {
                heapCache.put(key, entry);
            }

            // Step 2: Also persist to disk cache (if space available)
            Path diskPath = diskCacheDir.resolve(key);
            if (getDiskCacheUsage() + estimateEntrySize(entry) < maxDiskBytes) {
                byte[] data = serialize(entry);
                Files.write(diskPath, data);
            }

        } catch (Exception e) {
            logger.warn("Cache write failed for {}: {}", key, e.getMessage());
            throw new CacheException("Failed to cache " + key, e);
        }
    }

    private record CacheEntry(Object value, Instant expiresAt) {
        public CacheEntry(Object value, Duration ttl) {
            this(value, Instant.now().plus(ttl));
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}

// Automatically optimize TTL based on hit/miss patterns
public class TTLOptimizer {
    private final Map<String, TTLMetrics> metrics;  // key -> hit/miss stats
    private final Duration minTTL;                  // 30 seconds
    private final Duration maxTTL;                  // 24 hours
    private final Duration baseTTL;                 // 5 minutes

    public Duration getTTL(String key) {
        TTLMetrics m = metrics.getOrDefault(key, TTLMetrics.createDefault());

        double hitRate = m.getHitRate();
        int accessCount = m.getTotalAccesses();

        // Step 1: Compute suggested TTL based on hit rate
        Duration suggestedTTL;
        if (hitRate >= 0.9) {
            // Very high hit rate: increase TTL (data is popular and stable)
            suggestedTTL = baseTTL.multipliedBy(4);  // 20 minutes
        } else if (hitRate >= 0.7) {
            // High hit rate: keep TTL steady
            suggestedTTL = baseTTL;  // 5 minutes
        } else if (hitRate >= 0.5) {
            // Medium hit rate: reduce TTL (data changing more often)
            suggestedTTL = baseTTL.dividedBy(2);  // 2.5 minutes
        } else {
            // Low hit rate: short TTL (data volatile or rarely accessed)
            suggestedTTL = Duration.ofSeconds(30);
        }

        // Step 2: Enforce bounds
        if (suggestedTTL.compareTo(minTTL) < 0) {
            suggestedTTL = minTTL;
        }
        if (suggestedTTL.compareTo(maxTTL) > 0) {
            suggestedTTL = maxTTL;
        }

        logger.debug("TTL for {}: hit_rate={:.1f}%, access_count={}, TTL={}s",
            key, hitRate * 100, accessCount, suggestedTTL.toSeconds());

        return suggestedTTL;
    }

    public void recordAccess(String key, long latencyMs) {
        TTLMetrics m = metrics.computeIfAbsent(key, _ -> TTLMetrics.createDefault());
        m.recordAccess(latencyMs);
    }

    private static class TTLMetrics {
        private int hits;
        private int misses;
        private final Deque<Long> latencies;  // Last 100 accesses

        public TTLMetrics() {
            this.hits = 0;
            this.misses = 0;
            this.latencies = new ConcurrentLinkedDeque<>();
        }

        public void recordAccess(long latencyMs) {
            if (latencyMs < 10) {  // <10ms = cache hit
                hits++;
            } else {
                misses++;
            }

            latencies.addLast(latencyMs);
            if (latencies.size() > 100) {
                latencies.removeFirst();
            }
        }

        public double getHitRate() {
            int total = hits + misses;
            return total == 0 ? 0 : (double) hits / total;
        }

        public int getTotalAccesses() {
            return hits + misses;
        }

        public static TTLMetrics createDefault() {
            return new TTLMetrics();
        }
    }
}

// Integration with work item processing
public class PollingDiscoveryStrategy implements DiscoveryStrategy {
    private final SmartCacheManager cacheManager;

    @Override
    public List<WorkItemResult> discoverAndProcess() {
        List<YWorkItem> discoveredItems = engine.getAvailableWorkItems();

        List<WorkItemResult> results = new ArrayList<>();
        for (YWorkItem item : discoveredItems) {
            try {
                // Step 1: Predict what data will be needed for this task
                cacheManager.predictivelyPrefetch(item.getTaskID());

                // Step 2: Process item (all data requests go through smart cache)
                WorkItemResult result = processWorkItem(item);
                results.add(result);

            } catch (Exception e) {
                logger.error("Failed to process item {}: {}", item.getID(), e);
                results.add(WorkItemResult.failed(item.getID(), "processing_error"));
            }
        }

        return results;
    }

    private WorkItemResult processWorkItem(YWorkItem item) {
        String taskType = item.getTaskID();

        // All data access goes through SmartCacheManager.get()
        // which applies prefetch + caching + TTL optimization

        // Example:
        Map<String, Object> rules = cacheManager.get(
            "approval-rules",
            taskType,
            () -> externalDataSource.fetchRules("approval")
        );

        Map<String, Object> model = cacheManager.get(
            "approval-model",
            taskType,
            () -> externalDataSource.fetchModel("approval")
        );

        // Record access for learning
        cacheManager.recordAccess("approval-rules", taskType, 1);  // 1ms (cache hit)
        cacheManager.recordAccess("approval-model", taskType, 1);

        // Process with cached data
        WorkflowDecision decision = reasoner.reason(item, rules, model);

        return WorkItemResult.completed(item.getID(), decision);
    }
}

// Immutable prediction record
public record CacheAccessPrediction(
    String dataKey,
    double probability,     // 0.0 to 1.0
    String taskType,
    Instant predictedAt
) {
    public boolean isHighConfidence() {
        return probability >= 0.7;
    }
}
```

---

## Cache Prefetch Strategy

### Tier 1: Domain Rules (Highest Priority)

```
Task Type: Approval
  -> approval-rules.json (2 MB, 300ms HTTP)
  -> Accessed 95% of the time
  -> Prefetch probability: 0.95

Strategy:
  1. Detect task type = Approval
  2. Background: GET approval-rules.json
  3. Cache in heap (stays fresh for 5 min)
  4. Next 50 Approval tasks: 100% cache hit
```

### Tier 2: Decision Models (Medium Priority)

```
Task Type: PaymentAuth
  -> payment-auth-model.pkl (8 MB, 500ms HTTP)
  -> Accessed 65% of the time
  -> Prefetch probability: 0.65

Strategy:
  1. Only prefetch if high confidence (>0.5)
  2. If prefetch completes in time, use it
  3. If not, fetch on-demand during processing
```

### Tier 3: External Service Warmup (Low Priority)

```
Task Type: DocumentProcessing
  -> AWS Textract credentials (100ms)
  -> Azure Cognitive Services token (100ms)
  -> Accessed 40% of the time
  -> Prefetch probability: 0.40

Strategy:
  1. Only prefetch if very high confidence (>0.7)
  2. Fail gracefully if prefetch times out
  3. Cold connection established during task processing
```

---

## Performance Model

### Cache Hit Rates by Prediction Quality

```
Perfect Prediction (P=1.0):
  Task sequence: [Approval(50x), PaymentAuth(30x), Approval(20x)]

  Prefetch before Approval #1: rules
    Hit rate: 50/50 = 100%
  Prefetch before PaymentAuth #1: model
    Hit rate: 30/30 = 100%
  Prefetch before Approval #51: rules (cached, no prefetch needed)
    Hit rate: 20/20 = 100%

  Total: 100/100 = 100% cache hit rate

Good Prediction (P=0.8):
  Accuracy: 80% of predictions are correct
  Hit rate: 0.8 × 100% + 0.2 × 50% = 90% cache hit rate

Poor Prediction (P=0.5):
  Accuracy: 50% of predictions are correct
  Hit rate: 0.5 × 100% + 0.5 × 50% = 75% cache hit rate
```

### Latency Savings

```
Without Smart Cache:
  Item 1: Fetch rules (300ms) + parse (50ms) + process (4s) = 4.35s
  Item 2-50: Same = 4.35s × 50 = 217.5s

With Smart Cache (100% hit rate):
  Prefetch: 300ms (background, before Item 1)
  Item 1-50: Cache hit (1ms) + process (4s) = 4.01s × 50 = 200.5s

  Savings: 217.5s - 200.5s = 17s per 50 items = 340ms per item
```

---

## Configuration

```yaml
cache:
  smart-cache:
    enabled: true

    # Cache layers
    heap-cache-size-mb: 10
    disk-cache-size-mb: 100
    disk-cache-dir: "/var/cache/yawl"

    # TTL optimization
    base-ttl-seconds: 300              # 5 minutes
    min-ttl-seconds: 30                # 30 seconds (volatile data)
    max-ttl-seconds: 86400             # 24 hours (stable data)

    # Prefetching
    prefetch-enabled: true
    prefetch-depth: 3                  # Top 3 predictions
    prefetch-confidence-threshold: 0.3 # Only P >= 0.3
    prefetch-timeout-ms: 1000          # Max 1s to prefetch

    # Learning
    access-pattern-window-size: 1000   # Last 1000 accesses
    ttl-optimization-interval-seconds: 60

  metrics:
    enabled: true
    publish-interval-seconds: 30
```

---

## Metrics & Observability

```
Counters:
  cache.hits.total                    // Total cache hits
  cache.misses.total                  // Total cache misses
  cache.prefetch.success              // Successful prefetches
  cache.prefetch.timeout              // Timed-out prefetches

Histograms:
  cache.hit_rate                      // Distribution of hit rates per key
  cache.latency.ms                    // Latency distribution (hit vs miss)
  cache.ttl.seconds                   // Distribution of TTLs
  cache.prefetch.latency.ms           // Background prefetch latency

Gauges:
  cache.heap.usage.bytes              // Current heap cache size
  cache.disk.usage.bytes              // Current disk cache size
  cache.heap.hit_rate                 // Heap layer hit rate
  cache.disk.hit_rate                 // Disk layer hit rate
  cache.prediction.accuracy           // % of correct predictions
```

---

## Implementation Roadmap

1. **Phase 1 (Days 1-2)**: Create `MultiLayerCache` (heap + disk layers)
2. **Phase 2 (Days 3)**: Implement `CacheAccessPredictor` with pattern learning
3. **Phase 3 (Days 4)**: Create `CachePrefetcher` with background execution
4. **Phase 4 (Days 5)**: Implement `TTLOptimizer` with adaptive TTL
5. **Phase 5 (Days 6)**: Integrate with `PollingDiscoveryStrategy`
6. **Phase 6 (Days 7)**: Add metrics, configuration, tests
7. **Validation (Days 8)**: Performance test with 100+ item workflows

---

## Files to Create/Update

- **Create**: `org/yawlfoundation/yawl/integration/autonomous/caching/SmartCacheManager.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/caching/CacheAccessPredictor.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/caching/CachePrefetcher.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/caching/MultiLayerCache.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/caching/TTLOptimizer.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/model/CacheAccessPrediction.java` (record)
- **Update**: `org/yawlfoundation/yawl/integration/autonomous/strategies/PollingDiscoveryStrategy.java`
- **Update**: `application.yml` (configuration)

---

## Related Designs

- **PREDICTIVE-WORK-ROUTING.md**: Complements smart caching (both use prediction)
- **AUTONOMOUS-TASK-PARALLELIZATION.md**: Parallel processing benefits from pre-warmed cache

---

## Testing Strategy

### Unit Test: Prediction Accuracy

```java
@Test
void testAccessPatternLearning() {
    // Arrange: Process 100 Approval tasks
    for (int i = 0; i < 100; i++) {
        predictor.recordAccess("approval-rules", "Approval");
        predictor.recordAccess("approval-model", "Approval");
        if (i % 2 == 0) {
            predictor.recordAccess("approval-credentials", "Approval");
        }
    }

    // Act: Predict next access
    List<CacheAccessPrediction> predictions = predictor.predictAccessPattern("Approval");

    // Assert
    assertEquals("approval-rules", predictions.get(0).dataKey());
    assertTrue(predictions.get(0).probability() > 0.95,
        "Rules should be nearly certain (100 times accessed)");
    assertTrue(predictions.get(1).probability() > 0.95,
        "Model should be nearly certain");
    assertTrue(predictions.get(2).probability() > 0.40,
        "Credentials should be 50% (accessed every other time)");
}
```

### Integration Test: Cache Hit Rate

```java
@Test
void testCacheHitRateImproves() {
    // Arrange: 50 Approval tasks, smart cache enabled
    List<YWorkItem> items = createApprovalItems(50);

    // Act: Process all items
    var hitRates = new ArrayList<Double>();
    for (YWorkItem item : items) {
        cacheManager.predictivelyPrefetch(item.getTaskID());
        double hitRate = metricsCollector.getHitRate();
        hitRates.add(hitRate);
    }

    // Assert: Hit rate improves over time
    assertTrue(hitRates.get(0) < 0.5, "First access should be mostly misses");
    assertTrue(hitRates.get(49) > 0.95, "Last access should be ~100% hits");
}
```

### Performance Test: Latency Improvement

```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
void testLatencyReduction_WithoutSmartCache() {
    // 50 items × 350ms per item = 17.5s data loading overhead
    Instant start = Instant.now();
    processItemsWithoutCache();
    Duration elapsed = Duration.between(start, Instant.now());

    assertTrue(elapsed.toMillis() > 15000, "Should take >15s (cold starts)");
}

@Test
@Timeout(value = 20, unit = TimeUnit.SECONDS)
void testLatencyReduction_WithSmartCache() {
    // Prefetch: 300ms, then 50 items × ~1ms per item = 0.3s data loading
    Instant start = Instant.now();
    processItemsWithSmartCache();
    Duration elapsed = Duration.between(start, Instant.now());

    assertTrue(elapsed.toMillis() < 15000, "Should be faster with smart cache");
}
```

---

## Backward Compatibility

- **Interface B**: No changes
- **Existing code**: Cache is optional; non-smart code continues to work
- **Configuration**: Default `smart-cache.enabled: false` for safe rollout
- **Fallback**: If cache fails, on-demand fetching applies automatically

---

## References

- Multi-Tier Caching: https://redis.io/docs/management/caching-guide/
- TTL Optimization: https://www.cloudflare.com/learning/cdn/glossary/time-to-live-ttl/
- Predictive Prefetching: https://arxiv.org/abs/2105.03291
- Cache Coherence: https://en.wikipedia.org/wiki/Cache_coherence
