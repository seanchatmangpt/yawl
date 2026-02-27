# Why YAWL Stores Evicted YNetRunner Snapshots Off-Heap

YAWL's architecture for 1M concurrent cases uses off-heap memory via the Foreign Memory API to store evicted `YNetRunner` snapshots. This document explains the heap problem it solves, how off-heap memory works, and when database or cache alternatives are appropriate.

---

## The Heap Problem: 30 GB for 1M Cases

A single `YNetRunner` object in memory consumes approximately 30 KB (code state, variable bindings, execution stack, pending work items, timer state). Multiplying by 1M cases:

```
1,000,000 cases × 30 KB/case = 30 GB heap memory
```

A 30 GB Java heap creates unsustainable problems:

1. **Full garbage collection**: The JVM's garbage collector must scan every object, move live objects, and compact the heap. At 30 GB, a full GC pause takes 5–10 seconds. During that pause, the entire application is unresponsive.

2. **GC overhead**: Garbage collection consumes 30–50% of CPU time (tracking allocation, marking objects, compacting). User code runs only 50–70% of the time.

3. **Generational collection failure**: Young-generation collections run frequently (case execution creates temporary objects), pushing work into old-generation. Old-generation collections are expensive.

4. **Cost and resource limits**: Kubernetes pods have memory limits (typically 2–4 GB). A single 30 GB heap requires 8–15 pods just for memory allocation, before CPU and network scaling.

The solution: store only the most frequently accessed cases (the "hot set") on heap, and evict the rest off-heap.

---

## The LRU Hot/Cold Split

YAWL's case storage uses a Least-Recently-Used (LRU) strategy with a two-tier layout:

```
┌─────────────────────────────────────────┐
│          JVM Heap (2 GB)                │
├─────────────────────────────────────────┤
│  Hot Cases:  50K active                 │
│  in YNetRunnerRepository cache          │
│  ~1.5 GB (30 KB × 50K)                  │
├─────────────────────────────────────────┤
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│     Off-Heap Memory (30 GB via FMA)     │
├─────────────────────────────────────────┤
│  Cold Cases: 950K sleeping              │
│  Serialized YNetRunner snapshots        │
│  ~28.5 GB (30 KB × 950K)                │
│  GC-invisible, deterministic lifecycle  │
└─────────────────────────────────────────┘
```

**Hot set**: Cases accessed in the last 60 seconds (running tasks, pending timers, waiting for decisions) live on the heap in the YNetRunnerRepository cache.

**Cold set**: Cases dormant for >60 seconds are serialized via `YCaseExporter` and written to off-heap memory blocks. On access, the case is deserialized back onto the heap, and least-recently-used hot cases are evicted to cold storage.

---

## Foreign Memory API (JEP 454): How Off-Heap Works

Java 21+ provides `java.lang.foreign.Arena` and `MemorySegment` (JEP 454, Foreign Memory API) to allocate native memory outside the GC heap:

```java
// Allocate 30 GB off-heap
Arena offHeapArena = Arena.ofShared();

// Allocate a 30 KB block
MemorySegment segment = offHeapArena.allocate(
    30_000,  // 30 KB
    8        // alignment
);

// Write serialized case to the segment
byte[] serialized = caseExporter.export(ynetRunner);
segment.copyFrom(MemorySegment.ofArray(serialized));

// Later, when case is needed:
byte[] restored = new byte[30_000];
MemorySegment.copy(segment, 0, MemorySegment.ofArray(restored), 0, 30_000);
YNetRunner ynetRunner = caseExporter.importFrom(restored);
```

### Key Properties

**GC-invisible**: The JVM's garbage collector never scans off-heap memory. Full GC pauses are proportional to heap size (1.5 GB), not total case storage (30 GB). GC time drops from 5–10 seconds to 50–100ms.

**Deterministic lifecycle**: Unlike heap objects, off-heap blocks have explicit lifetimes. An `Arena` allocates blocks and releases them all when `close()` is called. No "surprise" GC finalization, no cleanup delays.

**Address-based indexing**: Off-heap cases are indexed by their `MemorySegment` address. A `ConcurrentHashMap<String, MemorySegment>` maps case ID → address. Lookups are O(1) with no object dereferencing.

**Serialization format**: Cases are serialized to a compact binary format (YAWL's existing `YCaseExporter` protocol or Java serialization). At 30 KB per case, a 1M-case system fits in ~30 GB off-heap, which is economical on modern servers (64 GB total RAM, 30 GB off-heap, 4 GB heap).

---

## Integration Architecture (Phase 2)

YAWL provides two SPI interfaces for case storage:

```java
public interface LocalCaseRegistry {
    void register(String caseId, String tenantId);
    String lookupTenant(String caseId);
    void deregister(String caseId);
    long size();
}

public interface GlobalCaseRegistry {
    // Same methods, for multi-node deployments
}
```

And a future off-heap adapter (Phase 2):

```java
public interface OffHeapRunnerStore {
    void store(String caseId, YNetRunner runner);
    YNetRunner retrieve(String caseId);
    void evict(String caseId);
    void close(); // Releases Arena
}
```

The wiring in `YNetRunnerRepository` delegates to the SPI:

```java
public class YNetRunnerRepository {
    private final LocalCaseRegistry registry;
    private final LRUCache<String, YNetRunner> hotCache;  // 50K
    private final OffHeapRunnerStore offHeapStore;        // 950K

    public YNetRunner get(String caseId) {
        // Check hot cache first
        YNetRunner runner = hotCache.get(caseId);
        if (runner != null) {
            return runner;
        }

        // Check off-heap
        runner = offHeapStore.retrieve(caseId);
        if (runner != null) {
            // Promote to hot cache (may evict cold case)
            hotCache.put(caseId, runner);
        }

        return runner;
    }
}
```

**Phase 2 status**: The off-heap store SPI is designed; wiring into the repository is pending. The default in-memory implementation (hot set only) works for deployments up to 50K–100K cases.

---

## Trade-Offs: Off-Heap vs Database vs Cache

### Off-Heap (JEP 454)

**Advantages**:
- Zero GC pressure (GC pauses stay ~50ms even at 1M cases)
- Microsecond latency (in-process memory access)
- No network overhead
- No external infrastructure
- Deterministic memory management

**Limitations**:
- Single-pod only (off-heap memory is local to the JVM process)
- Pod restart loses state (case snapshots are not persisted)
- Native memory fragmentation (over time, `Arena` may become inefficient)

**When to use**:
- Single-node YAWL deployments (stateful monolith)
- Scenarios where case loss is acceptable (non-critical workflows)
- Cost-sensitive environments (no infrastructure beyond the pod)

### PostgreSQL Database

**Advantages**:
- Persistent (survives pod restarts)
- Multi-pod visibility (any pod can query the database)
- Standard operational tools (backup, replication, monitoring)
- Horizontal querying (e.g., "all cases for tenant X")

**Limitations**:
- Latency: 1–5ms per case (disk I/O, network round-trip)
- Throughput: ~10K case operations/sec per database
- Cost: ~$100–500/month for managed database
- Bottleneck: database connection pool limits concurrent access

**When to use**:
- Stateless deployments where pods are ephemeral
- Multi-pod deployments where different pods handle cases for the same tenant
- Compliance scenarios where persistent audit trails are required
- Cases that run for hours or days (infrequent access, database polling is acceptable)

### Redis (In-Memory Cache)

**Advantages**:
- Low latency (~1ms, 10× faster than PostgreSQL)
- High throughput (50K+ operations/sec)
- Cross-pod visibility (multiple pods share a single Redis instance)
- Minimal GC pressure (cases live in Redis, not in JVM heap)

**Limitations**:
- Not persistent by default (Redis is in-memory)
- Cost: $50–200/month for managed Redis
- Dependency on external service (operational overhead)
- Eviction policy (Redis has limited memory; old cases are dropped)

**When to use**:
- Multi-pod deployments where case state must be shared
- High-throughput scenarios (stateless pods scaling up/down frequently)
- Caches for case metadata (tenant lookups, case status)

---

## Capacity Model: Off-Heap at 1M Cases

Assuming off-heap storage with 50K hot + 950K cold:

| Metric | Value |
|--------|-------|
| **Hot heap usage** | 1.5 GB (50K × 30 KB) |
| **Cold off-heap** | 28.5 GB (950K × 30 KB) |
| **Total memory** | 30 GB |
| **GC pause** | ~50ms (heap-only, ignores off-heap) |
| **Hot hit rate** | ~80% (80% of accesses are to the 50K hot cases) |
| **Off-heap miss** | ~20% (20% need deserialization from cold storage) |
| **Deserialization latency** | ~1ms (30 KB case, in-process) |
| **p95 case access latency** | ~500µs (if in hot cache) / ~1ms (if off-heap) |

---

## Why Not Always Use the Database?

At 1M cases, querying the database for every case access would create a bottleneck:

- PostgreSQL throughput: ~10K queries/sec
- Case access rate: 40K/sec (10K new cases/sec × 4 state transitions/case)
- Queue depth: 3 seconds (40K/10K)

The database cannot keep up. Off-heap solves this by caching locally, with the database as a persistence layer (backup/restore only).

For stateless deployments (pods are ephemeral), off-heap becomes a liability (state lost on pod restart). Use the database or Redis instead.

---

## Next Steps

For YAWL 6.0+ deployments targeting 1M cases:

- **Single-node stateful deployment**: Off-heap storage (Phase 2, when available)
- **Stateless K8s with persistent database**: PostgreSQL adapter (Phase 3)
- **Multi-pod with shared cache**: Redis adapter (Phase 3)
- **Compliance/audit requirements**: PostgreSQL with event log (Phase 3, MappedEventLog)

See `docs/explanation/decisions/ADR-015-persistence-layer-v6.md` for the full persistence architecture.
