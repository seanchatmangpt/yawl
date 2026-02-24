# ADR-007: Repository Pattern Caching

## Status
**ACCEPTED**

## Context

YAWL's repository layer performs frequent database queries for specifications, user data, and workflow state. These queries create significant database load and add latency to workflow operations.

### Business Drivers

1. **Performance Requirements**
   - Sub-100ms response time for specification lookups
   - Support 1000+ concurrent workflow cases
   - Handle burst traffic without database overload
   - Reduce infrastructure costs (database instances)

2. **Read-Heavy Workloads**
   - Specification lookups: 95% reads, 5% writes
   - User/role lookups: 90% reads, 10% writes
   - Resource distribution rules: 80% reads, 20% writes
   - Configuration data: 99% reads, 1% writes

3. **Consistency Requirements**
   - Eventual consistency acceptable for most cached data
   - Specification changes must propagate within 5 seconds
   - User permission changes must be immediate (invalidate)

### Technical Constraints

1. **Cache Requirements**
   - In-memory caching for low latency
   - Distributed cache for multi-instance deployments
   - Automatic expiration and eviction
   - Statistics and monitoring

2. **Integration Points**
   - Spring Cache abstraction
   - Hibernate second-level cache (optional)
   - Repository pattern implementation
   - Transaction boundaries

## Decision

**We will implement Caffeine as the primary local cache with read-through patterns and configurable invalidation strategies.**

### Architecture

```
+------------------------------------------------------------------+
|                     Application Layer                             |
|  +-------------+  +-------------+  +-------------+               |
|  |   Service   |  |   Service   |  |   Service   |               |
|  +------+------+  +------+------+  +------+------+               |
|         |                |                |                      |
+---------+----------------+----------------+----------------------+
          |
+---------v--------------------------------------------------------+
|                     Repository Layer                              |
|  +------------------------------------------------------------+  |
|  |              @Cacheable Methods                             |  |
|  |  +-------------+  +-------------+  +-------------+         |  |
|  |  | findById()  |  | findByName()|  | findAll()   |         |  |
|  |  +------+------+  +------+------+  +------+------+         |  |
|  +---------+----------------+----------------+----------------+  |
|            |                |                |                   |
+------------+----------------+----------------+-------------------+
             |
+------------v----------------------------------------------------+
|                     Cache Layer (Caffeine)                        |
|  +------------------------------------------------------------+  |
|  |  Cache Manager                                             |  |
|  |  +-------------+  +-------------+  +-------------+         |  |
|  |  |specifications| | users_cache | | roles_cache |         |  |
|  |  |    _cache    |  |             |  |             |         |  |
|  |  +-------------+  +-------------+  +-------------+         |  |
|  |                                                            |  |
|  |  Configuration:                                            |  |
|  |  - Maximum size: 1000 entries per cache                    |  |
|  |  - Expire after write: 10 minutes                          |  |
|  |  - Expire after access: 30 minutes                         |  |
|  |  - Statistics: enabled                                     |  |
|  +------------------------------------------------------------+  |
+------------------------------------------------------------------+
             |
+------------v----------------------------------------------------+
|                     Database (PostgreSQL/H2)                      |
+------------------------------------------------------------------+
```

### Caffeine Configuration

#### application.yml

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m,recordStats
    cache-names:
      - specifications
      - users
      - roles
      - work-items
      - resource-distributions

yawl:
  cache:
    specifications:
      max-size: 500
      expire-after-write: 30m
      expire-after-access: 1h
    users:
      max-size: 1000
      expire-after-write: 5m
      expire-after-access: 30m
    roles:
      max-size: 100
      expire-after-write: 10m
      expire-after-access: 1h
```

#### Programmatic Configuration

```java
@Configuration
@EnableCaching
public class CacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }

    @Bean
    public Cache<String, YSpecification> specificationCache() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .refreshAfterWrite(10, TimeUnit.MINUTES)
            .recordStats()
            .build(this::loadSpecification);
    }
}
```

### Read-Through Pattern

```java
@Repository
public class SpecificationRepositoryImpl implements SpecificationRepository {

    private final SpecificationJpaRepository jpaRepository;
    private final Cache<String, YSpecification> cache;

    @Override
    @Cacheable(value = "specifications", key = "#identifier + ':' + #version")
    public YSpecification findByIdentifier(String identifier, String version) {
        return jpaRepository.findByIdentifierAndVersion(identifier, version)
            .orElseThrow(() -> new SpecificationNotFoundException(identifier, version));
    }

    @Override
    @Cacheable(value = "specifications", key = "#identifier")
    public List<YSpecification> findByIdentifier(String identifier) {
        return jpaRepository.findByIdentifier(identifier);
    }

    @Override
    @CachePut(value = "specifications", key = "#result.identifier + ':' + #result.version")
    public YSpecification save(YSpecification specification) {
        return jpaRepository.save(specification);
    }

    @Override
    @CacheEvict(value = "specifications", key = "#identifier + ':' + #version")
    public void deleteByIdentifier(String identifier, String version) {
        jpaRepository.deleteByIdentifierAndVersion(identifier, version);
    }

    @Override
    @CacheEvict(value = "specifications", allEntries = true)
    public void evictAll() {
        // Clear all cached specifications
    }
}
```

### Cache Invalidation Strategies

#### Time-Based Expiration

```yaml
yawl:
  cache:
    specifications:
      expire-after-write: 30m    # Maximum cache lifetime
      expire-after-access: 1h    # Evict if not accessed
      refresh-after-write: 10m   # Async refresh before expiry
```

#### Event-Based Invalidation

```java
@Component
public class CacheInvalidationListener {

    private final CacheManager cacheManager;

    @EventListener
    public void onSpecificationUpdated(SpecificationUpdatedEvent event) {
        // Invalidate specific specification
        String cacheKey = event.getIdentifier() + ":" + event.getVersion();
        cacheManager.getCache("specifications").evict(cacheKey);
    }

    @EventListener
    public void onUserPermissionChanged(UserPermissionChangedEvent event) {
        // Immediate invalidation for security-sensitive data
        cacheManager.getCache("users").evict(event.getUserId());
        cacheManager.getCache("roles").evict(event.getRoleId());
    }

    @EventListener
    public void onEngineShutdown(EngineShutdownEvent event) {
        // Clear all caches on shutdown
        cacheManager.getCacheNames()
            .forEach(name -> cacheManager.getCache(name).clear());
    }
}
```

#### Manual Invalidation

```java
@Service
public class CacheManagementService {

    private final CacheManager cacheManager;

    public void invalidateSpecification(String identifier, String version) {
        String key = identifier + ":" + version;
        cacheManager.getCache("specifications").evict(key);
    }

    public void invalidateUser(String userId) {
        cacheManager.getCache("users").evict(userId);
    }

    public void clearAllCaches() {
        cacheManager.getCacheNames()
            .forEach(name -> cacheManager.getCache(name).clear());
    }

    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache caffeineCache) {
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                    caffeineCache.getNativeCache();
                stats.put(cacheName, Map.of(
                    "size", nativeCache.estimatedSize(),
                    "hitRate", nativeCache.stats().hitRate(),
                    "missRate", nativeCache.stats().missRate(),
                    "evictions", nativeCache.stats().evictionCount()
                ));
            }
        }
        return stats;
    }
}
```

### Monitoring and Metrics

```java
@Component
public class CacheMetrics {

    private final MeterRegistry meterRegistry;
    private final CacheManager cacheManager;

    @Scheduled(fixedRate = 60000)
    public void recordCacheMetrics() {
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache caffeineCache) {
                var stats = caffeineCache.getNativeCache().stats();

                Gauge.builder("yawl.cache.size", caffeineCache.getNativeCache(), c -> c.estimatedSize())
                    .tag("cache", cacheName)
                    .register(meterRegistry);

                Gauge.builder("yawl.cache.hit.rate", stats, CacheStats::hitRate)
                    .tag("cache", cacheName)
                    .register(meterRegistry);

                Gauge.builder("yawl.cache.miss.rate", stats, CacheStats::missRate)
                    .tag("cache", cacheName)
                    .register(meterRegistry);

                Counter.builder("yawl.cache.evictions")
                    .tag("cache", cacheName)
                    .increment(stats.evictionCount());
            }
        }
    }
}
```

## Consequences

### Positive

1. **Performance Improvement**
   - 90%+ reduction in database queries for cached entities
   - Sub-millisecond cache hits vs 10-50ms database queries
   - Reduced database load and connection pool pressure

2. **Scalability**
   - Handle higher throughput with same database
   - Reduced infrastructure costs
   - Better response time consistency

3. **Simplicity**
   - Spring Cache abstraction requires minimal code
   - Caffeine is well-tested and performant
   - No external cache infrastructure required

### Negative

1. **Memory Usage**
   - Additional heap memory required (configurable)
   - Risk of OOM if caches too large
   - Need to tune for available memory

2. **Consistency Delays**
   - Eventual consistency for cached data
   - Stale data possible until expiry
   - Need careful invalidation for security data

3. **Complexity**
   - Cache configuration and tuning
   - Invalidation logic adds complexity
   - Debugging cache issues harder than database

## Alternatives Considered

### Redis (Distributed Cache)
**Rejected for Phase 1**: Added complexity, network latency, infrastructure dependency.

**Pros:**
- Distributed cache for multi-instance
- Persistence across restarts
- Rich data structures

**Cons:**
- Network latency (1-5ms)
- Additional infrastructure
- Serialization overhead

### Hibernate Second-Level Cache
**Rejected**: Less control, tightly coupled to ORM.

**Pros:**
- Automatic entity caching
- Query cache support

**Cons:**
- Less flexible than application cache
- Complex configuration
- ORM-specific

## Implementation Notes

### When to Cache

| Entity Type | Cache? | Reason |
|-------------|--------|--------|
| Specifications | Yes | Read-heavy, rarely changes |
| Users/Roles | Yes | Read-heavy, invalidation on change |
| Work Items | No | Frequently updated |
| Case State | No | Always fresh data required |
| Configuration | Yes | Rarely changes |

### Cache Key Design

```java
// Good: Composite key with all identifying information
@Cacheable(value = "specifications", key = "#identifier + ':' + #version")

// Good: Simple key for single identifier
@Cacheable(value = "users", key = "#userId")

// Avoid: Complex objects as keys
// @Cacheable(value = "cache", key = "#complexObject") // Bad
```

## Related ADRs

- ADR-006: OpenTelemetry Observability (cache metrics)
- ADR-008: Resilience4j Circuit Breaking (cache fallback)
- ADR-001: Dual Engine Architecture (caching for both engines)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-18
**Implementation Status:** IN PROGRESS
**Review Date:** 2026-05-01 (3 months)

---

**Revision History:**
- 2026-02-18: Initial version approved
