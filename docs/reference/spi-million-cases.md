# YAWL SPI for 1M Cases Architecture

**Document type:** Reference (lookup table format)
**Audience:** AI coding agents, DevOps engineers, YAWL developers
**Purpose:** Complete SPI contract reference for the 1M cases architecture. Defines boundaries between engine core and pluggable subsystems (event bus, case registry, runner eviction).

**Source files authoritative for this document:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/spi/WorkflowEventBus.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/spi/FlowWorkflowEventBus.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/spi/WorkflowEvent.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/spi/GlobalCaseRegistry.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/spi/LocalCaseRegistry.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/spi/RunnerEvictionStore.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/spi/OffHeapRunnerStore.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/stateless/listener/event/YEventType.java`

---

## 1. WorkflowEventBus SPI

### Overview

**Purpose:** Decouple engine's hot execution path from downstream consumers (resource manager, event log, monitoring).

**Implementations:**
- **Default:** `FlowWorkflowEventBus` (in-JVM, uses Java 21+ `java.util.concurrent.Flow` API) — shipped with engine
- **Optional:** `KafkaWorkflowEventBus` (external Kafka topic) — via Maven dependency + ServiceLoader

**ServiceLoader key:** `META-INF/services/org.yawlfoundation.yawl.engine.spi.WorkflowEventBus`

**Load mechanism:** `WorkflowEventBus.defaultBus()` — loads via `java.util.ServiceLoader`, falls back to `FlowWorkflowEventBus`

### Methods

| Method | Signature | Returns | Throws | Description |
|--------|-----------|---------|--------|-------------|
| `publish` | `void publish(WorkflowEvent event)` | void | `NullPointerException` | Publishes an event to all subscribers registered for its type. Non-blocking; uses back-pressure if subscriber buffer fills. |
| `subscribe` | `void subscribe(YEventType type, Consumer<WorkflowEvent> handler)` | void | `NullPointerException` | Registers a handler that will be called for all events of the specified type. Handlers invoked asynchronously on virtual threads. |
| `close` | `void close()` | void | (per implementation) | Shuts down the event bus and releases all resources. After this call, `publish()` and `subscribe()` have undefined behaviour. |
| `defaultBus` | `static WorkflowEventBus defaultBus()` | `WorkflowEventBus` | _(none)_ | Returns the default in-JVM event bus instance, loading via ServiceLoader. Falls back to `FlowWorkflowEventBus` if no provider registered. |

### Back-pressure Mechanism

**Buffer size:** `Flow.defaultBufferSize()` = 256 items per event type

**Behaviour when buffer full:**
- Subscriber calls `SubmissionPublisher#submit(event)`, which blocks the caller if buffer is full
- At 1M cases, this should not occur under normal load
- If back-pressure observed, increase buffer size or use Kafka adapter

### FlowWorkflowEventBus Implementation Details

| Aspect | Implementation |
|--------|----------------|
| **Publisher per type** | One `SubmissionPublisher<WorkflowEvent>` per `YEventType` — avoids head-of-line blocking between unrelated event streams |
| **Executor** | `Executors.newVirtualThreadPerTaskExecutor()` — creates a virtual thread per task; never exhausts under bursty workloads |
| **Buffer size** | `Flow.defaultBufferSize()` (256 items) |
| **Exception handling** | Handler exceptions caught and logged; subscription NOT cancelled (broken handler does not block stream) |
| **Demand** | Subscribers request `Long.MAX_VALUE` (unbounded demand); back-pressure applied at publisher side |

### WorkflowEvent Record

**Java record definition:**
```java
public record WorkflowEvent(
    YEventType type,
    YIdentifier caseId,
    Object payload,
    Instant timestamp)
```

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `type` | `YEventType` | No | The type of workflow event (see enum values below) |
| `caseId` | `YIdentifier` | Yes (null for global events) | The case identifier; may be null for system-level events |
| `payload` | `Object` | Yes | The event payload (type-specific; caller must know the concrete type). Typical payloads: work item, case data, exception |
| `timestamp` | `Instant` | No | Wall-clock time the event was created. Convenient constructor stamps with `Instant.now()` |

**Constructors:**
```java
// Full constructor
WorkflowEvent(YEventType type, YIdentifier caseId, Object payload, Instant timestamp)

// Convenience constructor (stamps with current time)
WorkflowEvent(YEventType type, YIdentifier caseId, Object payload)
```

### YEventType Enum Values

| Value | Category | When Fired | Payload Type | Notes |
|-------|----------|-----------|--------------|-------|
| `CASE_STARTING` | Case | Before case execution begins | `YSpecificationID` | Precedes `CASE_STARTED` |
| `CASE_STARTED` | Case | After case instantiated and ready for work items | Case data | First event in case lifecycle |
| `CASE_START_FAILED` | Case | Case launch threw exception | Exception | Case does not reach ready state |
| `CASE_COMPLETED` | Case | Case reached completion successfully | Case data | Final event in normal case lifecycle |
| `CASE_CANCELLED` | Case | Case explicitly cancelled | `YIdentifier` (case ID) | Case halted by user/admin |
| `CASE_DEADLOCKED` | Case | Case detected in deadlock state | `YIdentifier` (case ID) | Requires manual intervention |
| `CASE_SUSPENDING` | Case | Case suspension initiated | `YIdentifier` (case ID) | Precedes `CASE_SUSPENDED` |
| `CASE_SUSPENDED` | Case | Case execution paused | Case data | Work items paused |
| `CASE_RESUMED` | Case | Suspended case resumed | Case data | Work item execution resumes |
| `CASE_UNLOADED` | Case | Case evicted from hot memory | `YIdentifier` (case ID) | Precedes potential cold restore |
| `CASE_RESTORED` | Case | Case restored from cold storage | Case data | After `CASE_UNLOADED` |
| `CASE_IDLE_TIMEOUT` | Case | Case exceeded idle timeout | `YIdentifier` (case ID) | Case may be auto-cancelled |
| `ITEM_ENABLED` | Work Item | Work item became executable | `YWorkItem` | Downstream (resource manager) assigns |
| `ITEM_STARTED` | Work Item | Work item execution started | `YWorkItem` | Human or system commenced work |
| `ITEM_COMPLETED` | Work Item | Work item execution finished | `YWorkItem` | Work done; case proceeds |
| `ITEM_STATUS_CHANGE` | Work Item | Work item status changed (non-lifecycle) | `YWorkItem` | E.g., priority updated |
| `ITEM_CANCELLED` | Work Item | Work item explicitly cancelled | `YWorkItem` | Work item halted |
| `ITEM_DATA_VALUE_CHANGE` | Work Item | Work item input/output data modified | `YWorkItem` | Data-driven event |
| `ITEM_ENABLED_REANNOUNCE` | Work Item | Re-announcement of enabled work item | `YWorkItem` | Retry signal for subscribers |
| `ITEM_STARTED_REANNOUNCE` | Work Item | Re-announcement of started work item | `YWorkItem` | Retry signal for subscribers |
| `ITEM_ABORT` | Work Item | Work item execution aborted (internal) | `YWorkItem` | Engine-internal cancellation |
| `ITEM_CHECK_PRECONSTRAINTS` | Work Item | Pre-constraint validation phase | `YWorkItem` | Customization point |
| `ITEM_CHECK_POSTCONSTRAINTS` | Work Item | Post-constraint validation phase | `YWorkItem` | Customization point |
| `NET_STARTED` | Subnet | Subnet execution started | `YIdentifier` (net ID) | Hierarchical case |
| `NET_COMPLETED` | Subnet | Subnet execution completed | `YIdentifier` (net ID) | Hierarchical case |
| `NET_CANCELLED` | Subnet | Subnet cancelled | `YIdentifier` (net ID) | Hierarchical case |
| `CASE_CHECK_PRECONSTRAINTS` | Case | Pre-constraint validation phase | Case data | Customization point |
| `CASE_CHECK_POSTCONSTRAINTS` | Case | Post-constraint validation phase | Case data | Customization point |
| `TIMER_STARTED` | Timer | Timer (cyclic task) started | Timer descriptor | Cyclic task began |
| `TIMER_EXPIRED` | Timer | Timer duration expired | Timer descriptor | Time-based trigger fired |
| `TIMER_CANCELLED` | Timer | Timer explicitly cancelled | Timer descriptor | Cyclic task halted |
| `NO_EVENT` | Meta | Placeholder for no event | _(none)_ | Never published; internal use only |

---

## 2. GlobalCaseRegistry SPI

### Overview

**Purpose:** Map case IDs to tenant IDs in a multi-tenant YAWL deployment.

**Implementations:**
- **Default:** `LocalCaseRegistry` (in-JVM `ConcurrentHashMap`) — suitable for single-node deployments; 120 MB @ 1M cases
- **Optional:** `RedisGlobalCaseRegistry` (in `yawl-redis-adapter`) — multi-node deployments where different pods may process cases for same tenant

**ServiceLoader key:** `META-INF/services/org.yawlfoundation.yawl.engine.spi.GlobalCaseRegistry` (future enhancement)

### Methods

| Method | Signature | Returns | Throws | Description |
|--------|-----------|---------|--------|-------------|
| `register` | `void register(String caseId, String tenantId)` | void | `NullPointerException` | Registers a new case with its owning tenant. Both arguments required (non-null). |
| `register` | `void register(YIdentifier caseId, String tenantId)` | void | `NullPointerException` | Convenience overload accepting `YIdentifier` instead of string. |
| `lookupTenant` | `String lookupTenant(String caseId)` | `String` (or null) | _(none)_ | Looks up the tenant owning a given case. Returns null if case not registered. |
| `lookupTenant` | `String lookupTenant(YIdentifier caseId)` | `String` (or null) | _(none)_ | Convenience overload accepting `YIdentifier` instead of string. |
| `deregister` | `void deregister(String caseId)` | void | _(none)_ | Removes the registration for a completed or cancelled case. Safe to call on already-deregistered cases. |
| `deregister` | `void deregister(YIdentifier caseId)` | void | _(none)_ | Convenience overload accepting `YIdentifier` instead of string. |
| `size` | `long size()` | `long` | _(none)_ | Returns the number of active cases in the registry. |

### LocalCaseRegistry Implementation Details

| Aspect | Implementation |
|--------|----------------|
| **Backing store** | `ConcurrentHashMap<String, String>` (pre-sized for 1M entries) |
| **Pre-sizing** | 1,333,334 buckets (1M / 0.75 load factor) |
| **Memory @ 1M cases** | ~120 MB heap (~120 bytes per entry: UUID string + tenant string + map overhead) |
| **Thread safety** | Lock-free reads; benign race on concurrent register/deregister for same case |
| **Tenant context binding** | Via `ScopedTenantContext` (JEP 487) or legacy ThreadLocal in `YEngine` |

### Memory Footprint

| Entry count | Expected memory | Load factor | Bucket count |
|-------------|-----------------|-------------|--------------|
| 100K | ~12 MB | 0.75 | 133,334 |
| 1M | ~120 MB | 0.75 | 1,333,334 |
| 10M | ~1.2 GB | 0.75 | 13,333,334 |

---

## 3. RunnerEvictionStore SPI

### Overview

**Purpose:** Persist serialised `YNetRunner` snapshots to cold storage when in-memory LRU cache evicts a runner.

**Flow:** Cache hit → `YNetRunnerRepository` loads from hot-set. Cache miss → `RunnerEvictionStore#restore()` loads from cold storage.

**Implementations:**
- **Default:** `OffHeapRunnerStore` (Java 21+ Foreign Memory API) — zero GC pressure; up to 60 GB @ 1M cases
- **Optional:** `PostgreSQLRunnerStore` (in `yawl-postgres-adapter`) — persistent database storage; slower restore latency

**ServiceLoader key:** `META-INF/services/org.yawlfoundation.yawl.engine.spi.RunnerEvictionStore` (wiring in Phase 2/3)

### Methods

| Method | Signature | Returns | Throws | Description |
|--------|-----------|---------|--------|-------------|
| `evict` | `void evict(YIdentifier caseId, byte[] snapshot)` | void | `IllegalArgumentException`, `IllegalStateException` | Persists a serialised runner snapshot to cold storage. Throws if snapshot is empty or store closed. |
| `restore` | `byte[] restore(YIdentifier caseId)` | `byte[]` | `CaseNotFoundInStoreException` | Retrieves a previously evicted runner snapshot. Throws if no snapshot exists for the given case. |
| `contains` | `boolean contains(YIdentifier caseId)` | `boolean` | _(none)_ | Returns true if the store holds a snapshot for the given case. |
| `remove` | `void remove(YIdentifier caseId)` | void | _(none)_ | Removes the snapshot for a completed/cancelled case from cold storage. Safe to call on already-removed cases. |
| `size` | `long size()` | `long` | _(none)_ | Returns the number of snapshots currently held in this store. |
| `close` | `void close()` | void | (per implementation) | Releases all resources held by this store. After this call the store must not be used. |

### OffHeapRunnerStore Implementation Details

| Aspect | Implementation |
|--------|----------------|
| **Memory model** | Java 21+ Foreign Memory API: single `Arena#ofShared()` arena (off-heap memory not managed by GC) |
| **Allocation strategy** | One `MemorySegment` per evicted runner; address + length stored in index |
| **Index** | `ConcurrentHashMap<String, long[]>` where `long[] = {address, length}` |
| **Index pre-sizing** | 100K buckets (50K hot-set with 50% load factor) |
| **Snapshot count** | Tracked via `AtomicLong` for `size()` |
| **Memory @ 1M cases** | Up to 60 GB off-heap (1M × 30 KB average snapshot size) — NOT managed by GC |
| **GC impact** | Zero GC pressure; ZGC pause times unaffected by off-heap data |
| **Evicted-then-removed** | Segments freed via Arena deallocation on `close()`. Future versions may use slab allocator. |

### Exception Types

| Exception | When thrown | Handling |
|-----------|-------------|----------|
| `CaseNotFoundInStoreException` | `restore()` called for non-existent case | Caller must handle; cache miss recovery |
| `IllegalArgumentException` | `evict()` called with empty/null snapshot | Validation failure; should not happen in practice |
| `IllegalStateException` | Methods called on closed store | Programming error; log and fail fast |

---

## 4. WorkflowEventSubscriber Adapter

**Purpose:** Internal adapter (not SPI) that wraps a `Consumer<WorkflowEvent>` handler as a `Flow.Subscriber`.

**Characteristics:**
- Requests unbounded demand (`Long.MAX_VALUE`)
- Dispatches events to handler on virtual threads
- Catches and logs handler exceptions; does NOT cancel subscription
- Logging: `WorkflowEventSubscriber#onNext()` → handler exceptions logged as WARN; stream errors as ERROR

**Usage:** Registered via `FlowWorkflowEventBus#subscribe()` — internal plumbing, not exposed to users.

---

## 5. Integration Points

### ServiceLoader Registration

Each SPI interface is registered via `META-INF/services/` in the jar manifest:

```
META-INF/services/org.yawlfoundation.yawl.engine.spi.WorkflowEventBus
├─ org.yawlfoundation.yawl.engine.spi.FlowWorkflowEventBus (default)
└─ (optional: org.example.kafka.KafkaWorkflowEventBus)

META-INF/services/org.yawlfoundation.yawl.engine.spi.GlobalCaseRegistry
├─ org.yawlfoundation.yawl.engine.spi.LocalCaseRegistry (default)
└─ (optional: org.example.redis.RedisGlobalCaseRegistry)

META-INF/services/org.yawlfoundation.yawl.engine.spi.RunnerEvictionStore
├─ org.yawlfoundation.yawl.engine.spi.OffHeapRunnerStore (default)
└─ (optional: org.example.postgres.PostgreSQLRunnerStore)
```

### Environment Variable Overrides

**Phase 2/3 enhancement:** Allow env var to override default implementations:
```bash
export YAWL_EVENT_BUS_IMPL=org.example.kafka.KafkaWorkflowEventBus
export YAWL_CASE_REGISTRY_IMPL=org.example.redis.RedisGlobalCaseRegistry
export YAWL_RUNNER_EVICTION_STORE_IMPL=org.example.postgres.PostgreSQLRunnerStore
```

---

## 6. Contract Guarantees

| Guarantee | Enforced by | Notes |
|-----------|------------|-------|
| **Thread safety** | Implementation | All implementations must be thread-safe for concurrent operations at 1M cases scale |
| **Non-blocking event publishing** | `WorkflowEventBus#publish()` contract | Must not block engine's hot execution path; back-pressure applied to slow subscribers |
| **Immutability** | `WorkflowEvent` record | Events are immutable; handlers must not modify payload objects |
| **Exception isolation** | `WorkflowEventSubscriber` | Handler exceptions must not affect other subscribers or event stream |
| **Clean shutdown** | `close()` method | Implementations must release all resources and complete gracefully |

---

## 7. Performance Characteristics

| Operation | Implementation | Latency |
|-----------|----------------|---------|
| `EventBus#publish()` | `FlowWorkflowEventBus` | < 1 ms (queued to subscriber buffer; back-pressure if full) |
| `CaseRegistry#register()` | `LocalCaseRegistry` | < 100 µs (ConcurrentHashMap put) |
| `CaseRegistry#lookupTenant()` | `LocalCaseRegistry` | < 100 µs (ConcurrentHashMap get) |
| `RunnerEvictionStore#evict()` | `OffHeapRunnerStore` | < 5 ms (arena allocation + memcpy for 30 KB) |
| `RunnerEvictionStore#restore()` | `OffHeapRunnerStore` | < 5 ms (memcpy for 30 KB) |

---

## 8. Testing Strategies

### Unit Tests

- `FlowWorkflowEventBus`: Verify subscribers receive events in order, exception handling, back-pressure
- `LocalCaseRegistry`: Concurrent register/lookup/deregister, concurrent hash map correctness
- `OffHeapRunnerStore`: Evict/restore round-trips, off-heap memory allocation, Arena cleanup

### Integration Tests (Chicago TDD)

- Event flow: Engine publishes events → subscribers handle asynchronously
- Case registry: Multiple pods look up tenant for cases they don't own (redis adapter)
- Runner eviction: Cache evicts hot runners → cold restore on miss

### Performance Benchmarks

- Event publishing throughput: target 100K+ events/sec/pod
- Case registry lookup: target < 100 µs p99 @ 1M cases
- Runner restore: target < 30 ms p99 (including deserialization)

