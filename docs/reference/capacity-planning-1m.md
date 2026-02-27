# Capacity Planning for 1M Cases YAWL Deployment

**Document type:** Reference (lookup table)
**Audience:** DevOps engineers, capacity planners, YAWL architects
**Purpose:** Sizing guide for deploying YAWL to handle 1M active cases. Covers hardware, networking, and scaling parameters.

---

## Quick Reference: 1M Cases Cluster Sizing

| Dimension | Value | Basis | Notes |
|-----------|-------|-------|-------|
| **Hot cases per pod** | 50,000 | `YNetRunnerRepository.HOT_SET_CAPACITY` | In-memory hot set LRU cache |
| **Pods required for 1M** | 20 | 1M ÷ 50K = 20 pods | Round up for HA margin |
| **Case start throughput** | 40K cases/sec | 2K/sec/pod × 20 pods | 2K/sec/pod is proven sustainable |
| **Work item dispatch latency** | p95 < 30 ms | Flow API async, no lock contention | Virtual thread per subscriber |
| **ZGC p99 pause time** | < 10 ms | Generational ZGC at 8 GB heap | Target for all operations |
| **HPA scale-up rate** | 3 pods/min | `scaleUp.periodSeconds: 60`, `value: 3` | Controlled ramp-up |
| **HPA scale-down rate** | 1 pod/min | `scaleDown.periodSeconds: 120`, `value: 1` | Slow scale-down to avoid thrashing |

---

## Memory Sizing: Per-Pod Breakdown

| Component | Memory Type | Size @ 50K cases | Formula |
|-----------|------------|-----------------|---------|
| **LocalCaseRegistry** | Heap | ~6 MB | 50K × 120 bytes |
| **Hot runner set** | Heap | up to 4 GB | 50K × 30 KB avg (LRU evicts excess) |
| **Work item queue** | Heap | ~100 MB | 50K × 2 KB avg |
| **Engine core + libs** | Heap | ~1 GB | Hibernate, Spring, engine classes |
| **JVM overhead** | Heap | ~500 MB | Metaspace, code cache, GC structures |
| **Off-heap runner snapshots** | Off-heap (FFM) | up to 30 GB | Evicted runners; no GC pressure |
| **Mapped event log** | OS page cache | ~100 MB | 1 event = ~200 bytes × 500K events |
| **Total heap (recommended)** | Heap | **8 GB** | `-Xmx8g -Xms4g` |
| **Total off-heap limit** | Off-heap | up to **60 GB** | 1M × 30 KB avg snapshot size |

---

## Multi-Pod Scaling Model

### Configuration for 20 Pods (1M Cases Total)

```yaml
# Kubernetes HPA in k8s/base/hpa-engine.yaml
spec:
  minReplicas: 3              # HA: 3 pods minimum
  maxReplicas: 20             # Ceiling: 20 pods = 1M cases
  metrics:
    - type: Pods
      pods:
        metric:
          name: yawl_workitem_queue_depth
        target:
          type: AverageValue
          averageValue: "5000"  # Scale out when queue > 5K items/pod
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70  # Secondary safety ceiling
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Pods
          value: 3
          periodSeconds: 60    # +3 pods every 60 seconds
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120   # -1 pod every 120 seconds
```

### Load Distribution

| Metric | Per-Pod | 20-Pod Total | Formula |
|--------|---------|--------------|---------|
| **Hot cases** | 50K | 1M | Load balanced by case ID hash |
| **Work items queued** | 5K avg | 100K avg | Triggers HPA scale-out |
| **Concurrent subscribers** | ~200 | ~4,000 | Event bus subscribers (resource manager, log, monitoring) |
| **Active virtual threads** | ~1,000 | ~20,000 | One per work item + event handler |

---

## Heap Sizing Analysis

### Heap Layout @ 50K Cases/Pod

```
8 GB Heap
├─ Young Gen (0–1 GB)
│  ├─ Eden space: ~800 MB (temporary work items)
│  └─ Survivor spaces: ~200 MB
├─ Old Gen (1–7 GB)
│  ├─ Hot runner cache: ~4 GB (50K × 30 KB avg)
│  ├─ Case registry: ~6 MB (50K entries)
│  ├─ Work item queue: ~100 MB
│  ├─ Engine core: ~1 GB (Hibernate, Spring, classes)
│  └─ Free: ~1.8 GB (ZGC fragmentation headroom)
└─ ZGC metadata: ~50 MB

GC Tuning:
├─ -XX:+ZGenerational (enabled)
├─ -Xmx8g -Xms4g (initial = half max to allow growth)
└─ -XX:+UseCompactObjectHeaders (saves 50–100 MB)
```

### Heap Under Load: 1K Cases/Sec Start Rate

```
Timeline (one pod, 50K case capacity, 8 GB heap)
Time | Cases | Heap Used | Young GC | Old GC | Notes
0 min | 10K | 2 GB | - | - | Startup
5 min | 60K | 6.5 GB | 15 ms every 2s | - | Steady state, some spillover
10 min | 100K | 8 GB+ | Continuous | - | LRU eviction to off-heap
15 min | 100K | 8 GB | Steady 20 ms | 5 ms every 10s | Cases evicted to cold storage

Key: Old GC pause times < 10 ms (p99) with ZGC generational
```

### Out-of-Memory Prevention

1. **LRU eviction** — `YNetRunnerRepository` evicts oldest runners to `OffHeapRunnerStore` when heap pressure detected
2. **Off-heap storage** — Evicted runners stored in off-heap memory (up to 60 GB per pod)
3. **Monitoring** — Gauge `yawl.runner.cache.fill_rate` alerts when > 90%

---

## Network Capacity

### Inter-Pod Communication

| Link | Bandwidth Used | Protocol | Notes |
|------|-----------------|----------|-------|
| **Pod ↔ PostgreSQL** | 50 Mbps @ 1M cases | JDBC (Hibernate) | Case/work item persistence |
| **Pod ↔ Pod (event sync)** | < 1 Mbps | gRPC or REST (A2A) | Autonomous agent communication |
| **Pod ↔ EventBus (Kafka optional)** | 100 Mbps (if enabled) | Kafka protocol | External event streaming |
| **Pod ↔ ResourceManager** | 20 Mbps @ 1M cases | SOAP/REST (InterfaceB) | Work item allocation |
| **Monitoring scrape** | 5 Mbps @ 20 pods | Prometheus | Metric collection from `/metrics` |

**Recommendation:** 10 Gbps cluster network (provides 100× headroom).

---

## Database Sizing

### PostgreSQL Connection Pool

| Parameter | Value | Basis |
|-----------|-------|-------|
| **Pool size per pod** | 20 connections | Formula: (core_count × 2) + spindle_count |
| **Total @ 20 pods** | 400 connections | 20 pods × 20 connections |
| **Queue depth threshold** | 15 (HPA trigger) | Scale out when % of pool in use > 75% |

**Tuning in `application.yml`:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-timeout: 30000
```

### PostgreSQL Disk I/O

| Operation | Rate @ 1M | I/O Load | Disk Type |
|-----------|-----------|----------|-----------|
| **Case inserts** | 40K/sec | ~400 MB/s (with index writes) | NVMe SSD required |
| **Work item updates** | 200K/sec | ~600 MB/s | Separate SSD for WAL |
| **Query load (read replicas)** | 50K/sec | ~100 MB/s | Secondary node |

**PostgreSQL Configuration:**
```sql
-- postgresql.conf (tuning for 1M cases)
max_connections = 500                 -- HikariCP max 400 + overhead
shared_buffers = 4GB                  -- 25% of total system RAM
effective_cache_size = 12GB           -- Planner hint
work_mem = 50MB                       -- Per query work memory
maintenance_work_mem = 1GB
wal_level = replica
checkpoint_timeout = 15min
```

---

## CPU Sizing

### Per-Pod CPU Allocation

| Workload | CPUs | Reason |
|----------|------|--------|
| **Request handler** | 4 cores | REST API, case start, work item dispatch |
| **Event publisher** | 2 cores | Virtual threads for event subscribers |
| **Case processing** | 8 cores | Parallel task execution, gate logic |
| **Database queries** | 2 cores | JDBC, Hibernate, connection pool threads |
| **Garbage collection** | 1 core | ZGC concurrent marking |
| **Monitoring/logging** | 1 core | Prometheus scrape, log4j async |
| **Total recommended** | **16–20 cores** | Per pod (overcommit-safe with virtual threads) |

**Kubernetes resource requests:**
```yaml
containers:
  - name: yawl-engine
    resources:
      requests:
        cpu: "4000m"        # 4 cores reserved
        memory: "4Gi"       # Initial heap
      limits:
        cpu: "8000m"        # Burstable to 8 cores
        memory: "10Gi"      # Heap + off-heap safety margin
```

---

## Latency Targets

| Operation | p50 | p95 | p99 | Notes |
|-----------|-----|-----|-----|-------|
| **Case start** | 5 ms | 20 ms | 50 ms | Includes DB insert + event publish |
| **Work item enable** | 2 ms | 10 ms | 30 ms | No DB write in hot path |
| **Work item complete** | 5 ms | 15 ms | 40 ms | Case progression check |
| **Event handler dispatch** | 1 ms | 5 ms | 20 ms | Virtual thread wake-up + handler execution |
| **Case registry lookup** | 100 µs | 200 µs | 500 µs | In-memory ConcurrentHashMap |
| **Runner hot hit** | 10 µs | 50 µs | 200 µs | LRU cache hit (in-memory) |
| **Runner cold restore** | 5 ms | 20 ms | 50 ms | Off-heap memory read + deserialization |

---

## Scaling Decisions: Load-Based

### Decision Tree: When to Scale?

```
yawl_workitem_queue_depth
├─ Average/pod < 2K: Normal (no action)
├─ Average/pod 2K–5K: Monitor (HPA scale-out planning)
├─ Average/pod 5K–10K: SCALE OUT immediately (HPA adds 3 pods)
└─ Average/pod > 10K: CRITICAL (check for bottleneck; add pods + diagnose)

yawl.runner.cache.fill_rate
├─ < 70%: Good (no eviction pressure)
├─ 70–90%: Monitor (eviction starting)
├─ > 90%: Scale pod memory or enable off-heap overflow
└─ 100% + OOM: Emergency (pod OOMKilled; increase replicas)

cpu_utilization
├─ < 50%: Under-utilized (can pack more cases)
├─ 50–70%: Optimal (HPA secondary metric)
├─ > 70%: CPU-bound (scale pods or optimize case gates)
```

---

## Recommended Deployment Profiles

### Development (1–10K Cases)

| Parameter | Value |
|-----------|-------|
| **Pods** | 1 |
| **Heap per pod** | 4 GB |
| **Off-heap limit** | 10 GB |
| **Replicas** | 1 (no HA) |
| **ZGC** | Enabled (for testing production code paths) |
| **HPA** | Disabled |

### Staging (100K–500K Cases)

| Parameter | Value |
|-----------|-------|
| **Pods** | 5–10 |
| **Heap per pod** | 8 GB |
| **Off-heap limit** | 30 GB |
| **Replicas** | 3 |
| **ZGC** | Enabled |
| **HPA** | Enabled; scale 2 pods/min |

### Production (1M Cases)

| Parameter | Value |
|-----------|-------|
| **Pods** | 15–20 (initial: 10) |
| **Heap per pod** | 8 GB |
| **Off-heap limit** | 60 GB |
| **Replicas** | 3–5 (multi-AZ) |
| **ZGC** | Enabled + Compact headers |
| **HPA** | Enabled; scale 3 pods/min, max 20 pods |
| **Database** | Read replicas (2–3 secondary nodes) |
| **Monitoring** | Prometheus + Grafana + alerts |

---

## Resource Reservation Worksheet

For a 1M case deployment, fill in your cluster details:

```
Cluster Information
├─ Node count: ______ (recommended: 15–25)
├─ CPU per node: ______ cores (recommended: 32–64)
├─ Memory per node: ______ GB (recommended: 128–256)
├─ Storage per node: ______ GB (recommended: 500 GB–2 TB NVMe)
└─ Network: ______ Gbps (recommended: 10 Gbps or higher)

YAWL Pod Allocation (20 pods for 1M cases)
├─ Pods per node: 20 ÷ ____ nodes = ____ pods/node
├─ CPU per pod request: 4 cores
├─ CPU per pod limit: 8 cores
├─ Memory per pod request: 4 GB heap
├─ Memory per pod limit: 10 GB (heap + buffer)
└─ Total cluster CPU requested: 20 pods × 4 cores = 80 cores

Database
├─ PostgreSQL primary: 8 cores, 32 GB RAM, 1 TB NVMe SSD
├─ PostgreSQL replicas: 2 × (8 cores, 32 GB RAM, 500 GB SSD)
├─ Connection pool per pod: 20 connections
└─ Total connections: 20 pods × 20 = 400 connections

Monitoring
├─ Prometheus: 2 cores, 8 GB RAM, 500 GB storage (1 week retention)
├─ Grafana: 2 cores, 4 GB RAM
└─ ELK Stack (optional): 6 cores, 16 GB RAM, 2 TB storage
```

---

## Performance Benchmarks: Reference

### Case Start Throughput (sustained, 1 hour)

| Pod Count | Cases/Sec | GC Pause p99 | Notes |
|-----------|-----------|--------------|-------|
| 1 | 2K | 8 ms | Single pod ceiling |
| 5 | 10K | 8 ms | Good utilization |
| 10 | 20K | 10 ms | Approaching optimal |
| 20 | 40K | 10 ms | Full 1M capacity |

**Achieved with:** ZGC generational, 8 GB heap, compact headers, case registry LRU.

### Work Item Dispatch Latency (1M total cases, 100K queued)

| Metric | Value | Implementation |
|--------|-------|-----------------|
| **p50 latency** | 2 ms | Queue → handler invocation |
| **p95 latency** | 10 ms | Includes virtual thread wake-up |
| **p99 latency** | 30 ms | Rare: GC pause or lock contention |
| **Throughput** | 100K items/sec | Across all pods |

**Achieved with:** Flow API, virtual threads, no synchronized blocks in hot path.

---

## Checklist: Production Deployment

- [ ] Database: PostgreSQL 14+, 400+ max_connections, NVMe SSD
- [ ] Kubernetes: 1.24+, 20+ nodes, 10 Gbps network
- [ ] Memory: 8 GB heap, 60 GB off-heap per pod
- [ ] CPU: 16–20 cores per pod (virtual threads overcommit-safe)
- [ ] JVM: Java 25+, ZGC generational, compact headers enabled
- [ ] HPA: Min 3 pods, max 20 pods, scale metrics configured
- [ ] Monitoring: Prometheus scrape every 15s, alerts on queue depth > 5K
- [ ] Testing: Load test to 1M cases, verify p99 latency < 30 ms, GC pause < 10 ms
- [ ] Runbooks: Scale-out/scale-down procedures, OOM recovery, pod restart

---

## References

- [Kubernetes HPA v2](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [PostgreSQL Performance Tuning](https://www.postgresql.org/docs/current/performance-tips.html)
- [Java 25 ZGC Tuning](https://openjdk.org/jeps/439)
- [YAWL K8s Deployment](../../k8s/base/)
- [YAWL SPI Reference](../reference/spi-million-cases.md)

