# YAWL v6.0.0-Beta Optimization Recommendations

**Version:** 6.0.0-Beta  
**Date:** 2026-02-18  
**Priority:** Performance optimization for production deployment

---

## Executive Summary

This document provides actionable optimization recommendations based on comprehensive performance analysis of YAWL v6.0.0-Beta. Recommendations are prioritized by impact and implementation effort.

### Priority Matrix

| Priority | Count | Estimated Impact |
|----------|-------|------------------|
| CRITICAL | 2 | Runtime correctness |
| HIGH | 4 | 20-50% latency reduction |
| MEDIUM | 6 | 10-20% latency reduction |
| LOW | 3 | 5-10% latency reduction |

---

## 1. CRITICAL Priority (Blocking)

### 1.1 Migrate YPersistenceManager to Hibernate 6 APIs

**File:** `src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`  
**Lines:** 247, 263, 323

**Issue:** Hibernate 6 removed deprecated Session methods:
- `session.save()` -> `session.persist()`
- `session.delete()` -> `session.remove()`
- `session.saveOrUpdate()` -> `session.merge()`

**Current Code:**
```java
// Line 247 - REMOVED in Hibernate 6
getSession().delete(obj);

// Line 263 - REMOVED in Hibernate 6
getSession().saveOrUpdate(obj);

// Line 323 - REMOVED in Hibernate 6
getSession().save(obj);
```

**Fix:**
```java
// Line 247
getSession().remove(obj);

// Line 263
getSession().merge(obj);

// Line 323
getSession().persist(obj);
```

**Impact:** Runtime failure without fix. Under compatibility shim: 10-20% overhead per call.

### 1.2 Fix L2 Cache Factory Class

**File:** `build/properties/hibernate.properties`  
**Line:** 481

**Issue:** EHCache region factory class incorrect for Hibernate 6.

**Current:**
```properties
hibernate.cache.region.factory_class org.hibernate.cache.ehcache.EhCacheRegionFactory
```

**Fix:**
```properties
hibernate.cache.region.factory_class org.hibernate.cache.jcache.JCacheCacheRegionFactory
hibernate.javax.cache.provider org.ehcache.jsr107.EhcacheCachingProvider
hibernate.javax.cache.uri ehcache.xml
```

**Impact:** +15-40% latency on entity reads without L2 cache.

---

## 2. HIGH Priority

### 2.1 Enable JDBC Batching

**File:** `build/properties/hibernate.properties`  
**Line:** 413

**Current:**
```properties
hibernate.jdbc.batch_size 0
```

**Fix:**
```properties
hibernate.jdbc.batch_size 20
hibernate.order_inserts true
hibernate.order_updates true
hibernate.batch_versioned_data true
```

**Impact:** 5-9 JDBC round trips per work item completion -> 2-3 batched calls. Estimated -50% persistence latency.

### 2.2 Replace Hashtable with ConcurrentHashMap

**File:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java`  
**Line:** 1145

**Current:**
```java
_timerStates = new Hashtable<String, String>();
```

**Fix:**
```java
_timerStates = new ConcurrentHashMap<String, String>();
```

**Impact:** Eliminates global lock on every timer state update. ~5-10% improvement in hot path.

### 2.3 Remove Duplicate Logger

**File:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java`  
**Lines:** 59, 62

**Current:**
```java
private static final Logger logger  = LogManager.getLogger(YNetRunner.class);  // line 59 - unused
private static final Logger _logger = LogManager.getLogger(YNetRunner.class);  // line 62 - used
```

**Fix:**
```java
// Delete line 59
private static final Logger _logger = LogManager.getLogger(YNetRunner.class);
```

**Impact:** Minor memory reduction, code clarity.

### 2.4 Remove XML Round-Trip in getFlowsIntoTaskID

**File:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java`  
**Line:** 1134

**Current:**
```java
Element eTask = JDOMUtil.stringToElement(atomicTask.toXML());
return eTask.getChild("flowsInto").getChild("nextElementRef").getAttributeValue("id");
```

**Fix:**
```java
// Use direct API access
YTask nextTask = atomicTask.getNextTask();
return nextTask != null ? nextTask.getID() : null;
```

**Impact:** Eliminates XML serialization overhead. ~1-5ms per timer-driven workflow call.

---

## 3. MEDIUM Priority

### 3.1 Investigate Double kick() Call

**File:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java`  
**Lines:** 849, 857

**Issue:** `completeTask()` calls both `continueIfPossible()` and `kick()`, where `kick()` also calls `continueIfPossible()`.

```java
continueIfPossible(pmgr);    // line 849 - already advances net
_busyTasks.remove(atomicTask);
...
kick(pmgr);                  // line 857 - calls continueIfPossible() again
```

**Recommendation:** Profile to confirm redundancy, then remove one call.

**Impact:** 5-15ms per transition on nets with 20+ tasks.

### 3.2 Add Status-Indexed Map to YWorkItemRepository

**File:** `src/org/yawlfoundation/yawl/engine/YWorkItemRepository.java`

**Current:** `getWorkItems(YWorkItemStatus status)` performs O(N) linear scan.

**Fix:**
```java
// Add secondary index
private final Map<YWorkItemStatus, Set<YWorkItem>> _statusIndex = new ConcurrentHashMap<>();

public Set<YWorkItem> getWorkItems(YWorkItemStatus status) {
    return new HashSet<>(_statusIndex.getOrDefault(status, Collections.emptySet()));
}

// Update on add/remove
private void addToIndex(YWorkItem item) {
    _statusIndex.computeIfAbsent(item.getStatus(), k -> ConcurrentHashMap.newKeySet()).add(item);
}
```

**Impact:** O(N) -> O(k) for status queries. <10ms at 10k items.

### 3.3 Gate cleanseRepository() Behind Dirty Flag

**File:** `src/org/yawlfoundation/yawl/engine/YWorkItemRepository.java`

**Issue:** `cleanseRepository()` called on every `getWorkItems()` invocation - O(N*M) operation.

**Fix:**
```java
private volatile boolean _dirty = false;

public void markDirty() { _dirty = true; }

public Set<YWorkItem> getWorkItems() {
    if (_dirty) {
        cleanseRepository();
        _dirty = false;
    }
    return _itemMap.values();
}
```

**Impact:** Eliminate unnecessary scans. Up to 50ms saved per API call under load.

### 3.4 Add WHERE Filter to Restore Queries

**File:** `src/org/yawlfoundation/yawl/engine/YEngineRestorer.java`

**Current:**
```java
pmgr.getObjectsForClass("YWorkItem")  // SELECT * FROM work_item
```

**Fix:**
```java
pmgr.getObjectsForClass("YWorkItem", "status in ('enabled', 'executing')")
// SELECT * FROM work_item WHERE status IN ('enabled', 'executing')
```

**Impact:** Significantly faster engine restore for large history tables.

### 3.5 Replace synchronized with ReentrantLock (Virtual Thread Compatibility)

**File:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java`

**Issue:** `synchronized` methods cause virtual thread pinning when calling blocking I/O.

**Current:**
```java
public synchronized void startWorkItemInTask(...) { ... }
public synchronized void completeWorkItemInTask(...) { ... }
public synchronized void continueIfPossible(...) { ... }
```

**Fix:**
```java
private final ReentrantLock _lock = new ReentrantLock();

public void startWorkItemInTask(...) {
    _lock.lock();
    try {
        // method body
    } finally {
        _lock.unlock();
    }
}
```

**Impact:** Enables virtual thread benefits for I/O-bound operations. 2-10x throughput improvement potential.

### 3.6 Remove Redundant Name Sets

**File:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java`

**Current:**
```java
private Set<YTask>   _enabledTasks;      // objects
private Set<String>  _enabledTaskNames;  // redundant
```

**Fix:** Use task object identity directly for persistence, eliminate string sets.

**Impact:** ~50% memory reduction for task tracking data.

---

## 4. LOW Priority

### 4.1 Enable String Deduplication

**JVM Flag:** Already enabled in `scripts/start-engine-java25-tuned.sh`

```bash
-XX:+UseStringDeduplication
```

**Impact:** 5-15% heap reduction for string-heavy workloads.

### 4.2 Use AOT Compilation Cache

**JVM Flags:**
```bash
-XX:+UseAOTCache
```

**Impact:** 20-40% reduction in JIT warmup time.

### 4.3 Use CDS (Class Data Sharing)

**Steps:**
```bash
# Create archive
java -share:off -XX:ArchiveClassesAtExit=app.jsa -jar yawl-engine.war

# Use archive
java -share:on -XX:SharedArchiveFile=app.jsa -jar yawl-engine.war
```

**Impact:** 10-20% faster startup.

---

## 5. Configuration Optimizations

### 5.1 JVM Settings (8GB Server)

```bash
# Heap
-Xms2g
-Xmx4g

# GC - G1GC (recommended for latency)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45
-XX:G1MixedGCLiveThresholdPercent=85
-XX:+G1UseAdaptiveIHOP

# Metaspace
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# Optimizations
-XX:+UseStringDeduplication
-XX:+OptimizeStringConcat
-XX:+UseCompactObjectHeaders

# Virtual thread scheduler
-Djdk.virtualThreadScheduler.parallelism=8
-Djdk.virtualThreadScheduler.maxPoolSize=256

# GC logging
-Xlog:gc*:file=logs/gc.log:time,uptime:filecount=5,filesize=20m
```

### 5.2 HikariCP Connection Pool

```properties
hibernate.hikari.maximumPoolSize=20
hibernate.hikari.minimumIdle=5
hibernate.hikari.connectionTimeout=30000
hibernate.hikari.idleTimeout=600000
hibernate.hikari.maxLifetime=1800000
hibernate.hikari.keepaliveTime=120000
hibernate.hikari.leakDetectionThreshold=60000
```

### 5.3 Hibernate Optimizations

```properties
# Batching
hibernate.jdbc.batch_size=20
hibernate.order_inserts=true
hibernate.order_updates=true

# Query cache (optional, for frequently-run queries)
hibernate.cache.use_query_cache=true

# Statistics (production monitoring)
hibernate.generate_statistics=true
```

---

## 6. Performance Targets

### 6.1 Latency Targets

| Operation | Target (p95) | Target (p99) |
|-----------|--------------|--------------|
| Case launch | < 500ms | < 800ms |
| Work item checkout | < 200ms | < 300ms |
| Work item checkin | < 300ms | < 500ms |
| Task transition | < 100ms | < 200ms |
| Database query | < 50ms | < 100ms |

### 6.2 Throughput Targets

| Metric | Target |
|--------|--------|
| Concurrent cases | > 100 cases/sec |
| Work item ops | > 1000 ops/sec |
| Max concurrent cases | ~500 per engine |

### 6.3 Resource Targets

| Metric | Target |
|--------|--------|
| GC time | < 5% CPU |
| Full GC frequency | < 10/hour |
| Memory (1000 cases) | < 512MB |
| Startup time | < 60s |

---

## 7. Implementation Roadmap

### Phase 1: Critical Fixes (Week 1)
- [ ] Migrate YPersistenceManager to Hibernate 6 APIs
- [ ] Fix L2 cache factory class

### Phase 2: High Priority (Week 2-3)
- [ ] Enable JDBC batching
- [ ] Replace Hashtable with ConcurrentHashMap
- [ ] Remove duplicate logger
- [ ] Fix XML round-trip

### Phase 3: Medium Priority (Week 4-6)
- [ ] Add status index to YWorkItemRepository
- [ ] Gate cleanseRepository()
- [ ] Add WHERE filters to restore queries
- [ ] Investigate double kick()

### Phase 4: Virtual Thread Migration (Week 7-8)
- [ ] Replace synchronized with ReentrantLock
- [ ] Test virtual thread performance
- [ ] Update documentation

---

## 8. Validation Checklist

After implementing optimizations, verify:

- [ ] All existing tests pass
- [ ] EnginePerformanceBaseline targets met
- [ ] GC pause times < 200ms (p99)
- [ ] Memory usage < 512MB for 1000 cases
- [ ] Startup time < 60s
- [ ] Load tests pass (> 99% success rate)

---

*Generated: 2026-02-18 | YAWL Performance Optimization Specialist*
