# YAWL Metrics Reference

**Document type:** Reference (lookup table)
**Audience:** DevOps engineers, SRE teams, YAWL developers
**Purpose:** Complete reference for all Micrometer metrics exposed by YAWL 6.0.0. Used for monitoring, alerting, and HPA scaling decisions.

**Source files authoritative for this document:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/observability/YawlMetrics.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/actuator/metrics/YWorkflowMetrics.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/actuator/metrics/YAgentPerformanceMetrics.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunnerLockMetrics.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/metrics/InterfaceMetrics.java`

---

## Metric Endpoint

**URL:** `GET /actuator/prometheus`
**Format:** Prometheus text format (OpenMetrics)
**Refresh interval:** Configurable via `management.metrics.export.prometheus.step` (default 1 minute)

---

## Core Workflow Metrics

### Case Lifecycle Counters

| Metric | Type | Tags | Description | When Incremented |
|--------|------|------|-------------|------------------|
| `yawl.case.created` | Counter | `application`, `environment`, `version` | Total cases created | Case instantiation (before gate logic) |
| `yawl.case.completed` | Counter | `application`, `environment`, `version` | Total cases completed successfully | Case reaches final node with no exception |
| `yawl.case.failed` | Counter | `application`, `environment`, `version` | Total cases failed (exception during execution) | Case throws exception in engine core |
| `yawl.case.cancelled` | Counter | `application`, `environment`, `version` | Total cases cancelled by user/admin | `cancelCase()` API called |

### Work Item Counters

| Metric | Type | Tags | Description | When Incremented |
|--------|------|------|-------------|------------------|
| `yawl.workitem.enabled` | Counter | `application`, `taskName` | Total work items enabled | Work item becomes executable (resource manager notified) |
| `yawl.workitem.started` | Counter | `application`, `taskName` | Total work items started | Work item execution begins |
| `yawl.workitem.completed` | Counter | `application`, `taskName` | Total work items completed | Work item finish confirmed (with output data) |
| `yawl.workitem.failed` | Counter | `application`, `taskName` | Total work items failed | Work item execution threw exception |
| `yawl.workitem.cancelled` | Counter | `application`, `taskName` | Total work items cancelled | Work item explicitly cancelled |

### Case Gauge Metrics

| Metric | Type | Tags | Description | Value |
|--------|------|------|-------------|-------|
| `yawl.case.active` | Gauge | `application`, `environment` | Number of active (running) cases | Current count of cases not yet completed/cancelled |
| `yawl_workitem_queue_depth` | Gauge | Pod label | Work items queued per pod (HPA metric) | Immediate count of items pending dispatch |
| `yawl.case.idle_timeout_count` | Gauge | `application` | Cases exceeded idle timeout threshold | Count of cases waiting for user action > max idle time |

---

## 1M Cases Metrics (Phase 5/6)

New metrics for multi-million-case deployments, optimized for scaling and resource management.

### Work Item Queue Depth (HPA Driver)

| Metric | Type | Tags | Description | When Used |
|--------|------|------|-------------|-----------|
| `yawl_workitem_queue_depth` | Gauge (Kubernetes Pod annotation) | Pod name, node | Immediate count of work items queued per pod | HPA scale-out trigger when average > 5,000 items/pod |

**Calculation:**
```
yawl_workitem_queue_depth = work_item_enabled_count - work_item_dispatch_count (cumulative)
```

**HPA Scale-out Threshold:** Average across all pods > 5,000 → add 3 pods per scale-up window

**Example metric output:**
```
yawl_workitem_queue_depth{pod="yawl-engine-0",node="node-1"} 4250
yawl_workitem_queue_depth{pod="yawl-engine-1",node="node-2"} 5100  # triggers scale-out
yawl_workitem_queue_depth{pod="yawl-engine-19",node="node-20"} 4800
# Average: 4800 > 5000 threshold → HPA initiates scale-up
```

### Runner Cache Metrics

| Metric | Type | Tags | Description | When Used |
|--------|------|------|-------------|-----------|
| `yawl.runner.cache.hit_rate` | Gauge | `pod_name`, `node` | Percentage of cache hits vs misses (hot-set LRU) | Indicates memory pressure and eviction rate |
| `yawl.runner.cache.size` | Gauge | `pod_name` | Current number of runners in hot-set cache | Monotonic; capped at 50K per pod |
| `yawl.runner.cache.eviction_count` | Counter | `pod_name` | Cumulative runners evicted to cold storage | Tracks off-heap utilization over time |
| `yawl.runner.cache.fill_rate` | Gauge | `pod_name` | Percentage of cache capacity in use (0–100%) | Alert when > 90% (eviction pressure imminent) |

**Example values @ 1M cases:**
```
yawl.runner.cache.hit_rate{pod="yawl-engine-0"} 0.85        # 85% cache hit rate (healthy)
yawl.runner.cache.hit_rate{pod="yawl-engine-5"} 0.62        # 62% cache hit rate (memory pressure)
yawl.runner.cache.size{pod="yawl-engine-0"} 45000          # 45K runners in-memory
yawl.runner.cache.fill_rate{pod="yawl-engine-0"} 0.90      # 90% full → eviction active
yawl.runner.cache.eviction_count{pod="yawl-engine-0"} 100000 # 100K runners evicted lifetime
```

**Alerting:**
- Alert if `yawl.runner.cache.hit_rate` < 70% (excessive cold restores)
- Alert if `yawl.runner.cache.fill_rate` > 95% (OOM risk imminent)

### Runner Restore Latency

| Metric | Type | Tags | Description | Typical Value |
|--------|------|------|-------------|---------------|
| `yawl.runner.restore_duration_ms` | Histogram | `pod_name`, `percentile` | Cold restore time per case (off-heap → heap) | p50: 5 ms, p95: 20 ms, p99: 50 ms |

**Percentiles exposed:**
- `.p50` = 50th percentile (median)
- `.p95` = 95th percentile
- `.p99` = 99th percentile
- `.max` = maximum observed

**Example metric output:**
```
yawl.runner.restore_duration_ms{pod="yawl-engine-0",quantile="0.5"} 5.2
yawl.runner.restore_duration_ms{pod="yawl-engine-0",quantile="0.95"} 18.5
yawl.runner.restore_duration_ms{pod="yawl-engine-0",quantile="0.99"} 48.3
yawl_runner_restore_duration_ms_count{pod="yawl-engine-0"} 42000  # 42K cold restores in scrape interval
```

**Alerting:**
- Alert if `p99 > 100 ms` (I/O subsystem degradation)
- Alert if `count > 1000/min` (sustained high eviction rate)

### Lock Contention (JEP 491 Benefit)

| Metric | Type | Tags | Description | Typical Value |
|--------|------|------|-------------|---------------|
| `yawl.runner.lock.wait_ms` | Histogram | `pod_name`, `percentile` | Virtual thread wait time on DB locks (JEP 491) | p50: < 100 µs, p99: < 5 ms |

**How JEP 491 improves this:** Without synchronized pinning, virtual threads waiting on DB locks do NOT pin carrier threads.

**Example metric output:**
```
yawl.runner.lock.wait_ms{pod="yawl-engine-0",quantile="0.5"} 0.08   # 80 microseconds
yawl.runner.lock.wait_ms{pod="yawl-engine-0",quantile="0.99"} 4.2   # 4.2 milliseconds
yawl_runner_lock_wait_ms_count 500000                              # 500K lock acquisitions
```

**Alerting:**
- Alert if `p99 > 10 ms` (potential deadlock or connection pool saturation)
- Alert if `count/min` increasing while throughput flat (lock contention bottleneck)

---

## Timers (Latency Histograms)

### Case Execution Time

| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `yawl.case.duration` | Timer (Histogram + Counter) | End-to-end time from case start to completion | `application`, `specName`, `version` |

**Value interpretation:**
```
yawl_case_duration_seconds_bucket{application="yawl-engine",specName="invoice-process",le="0.1"} 42000
# 42,000 cases completed in < 100 ms
yawl_case_duration_seconds_bucket{...,le="1.0"} 56000
# 56,000 cases completed in < 1 second
yawl_case_duration_seconds_bucket{...,le="+Inf"} 60000
# 60,000 total cases
```

### Work Item Execution Time

| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `yawl.workitem.duration` | Timer | Time from work item enabled to completion | `application`, `taskName` |

**Example:** `yawl_workitem_duration_seconds{taskName="review-invoice"}` [histogram buckets]

### Engine Request Latency

| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `yawl.engine.latency` | Timer | HTTP request latency for engine REST endpoints | `application`, `endpoint` (e.g., `/startCase`) |

---

## Interface Metrics

### InterfaceB (Resource Manager Integration)

| Metric | Type | Description |
|--------|------|-------------|
| `yawl.ib.request.count` | Counter | HTTP requests received from resource service |
| `yawl.ib.request.duration` | Timer | Request handling time |
| `yawl.ib.error.count` | Counter | Failed requests (HTTP 4xx/5xx) |
| `yawl.ib.connection.active` | Gauge | Active concurrent connections |

### InterfaceX (External Services)

| Metric | Type | Description |
|--------|------|-------------|
| `yawl.ix.service.call.count` | Counter | Outbound service calls (e.g., to external APIs) |
| `yawl.ix.service.call.duration` | Timer | Service call latency |
| `yawl.ix.service.error.count` | Counter | Failed service calls |

---

## Database & Connection Pool Metrics

### HikariCP Connection Pool

| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `hikaricp.connections.active` | Gauge | Active connections in use | `pool` (e.g., `YAWLConnectionPool`) |
| `hikaricp.connections.idle` | Gauge | Idle connections waiting for requests | `pool` |
| `hikaricp.connections.pending` | Gauge | Threads waiting for a connection | `pool` |
| `hikaricp.connections.timeout` | Counter | Failed connection acquisitions (timeout) | `pool` |

**Alerting:**
- Alert if `hikaricp.connections.pending` > 5 (connection pool saturation)
- Alert if `hikaricp.connections.timeout` increasing (slow queries blocking pool)

### Hibernate Query Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `hibernate.sessions.open` | Gauge | Open Hibernate sessions |
| `hibernate.sessions.closed` | Counter | Total closed sessions |
| `hibernate.flush.count` | Counter | Total flushes (batch writes) |

---

## JVM & GC Metrics (Standard Micrometer)

### Memory

| Metric | Type | Description |
|--------|------|-------------|
| `jvm.memory.used` | Gauge | Bytes of memory currently in use | `area` = `heap` or `nonheap` |
| `jvm.memory.max` | Gauge | Maximum allocatable memory | `area` = `heap` or `nonheap` |
| `jvm.memory.committed` | Gauge | Memory committed by JVM | `area` |

### Garbage Collection

| Metric | Type | Description | Typical Value (ZGC) |
|--------|------|-------------|-------------------|
| `jvm.gc.pause` | Timer (Histogram) | GC pause duration per collection | p99 < 10 ms with `-XX:+ZGenerational` |
| `jvm.gc.collection.count` | Counter | Number of GC collections | Increases over time; watch rate of change |
| `jvm.gc.max.data.size` | Gauge | Maximum heap size | 8 GB per pod |

**Example metric output:**
```
jvm_gc_pause_seconds_bucket{gc="ZGC",quantile="0.99"} 0.008      # 8 ms p99 pause
jvm_gc_pause_seconds_bucket{gc="ZGC",quantile="0.999"} 0.010     # 10 ms p99.9 pause
jvm_gc_collection_count{gc="ZGC"} 15000                          # 15K collections over uptime
```

---

## System Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `process.cpu.usage` | Gauge | CPU usage as fraction (0–1) |
| `process.files.open` | Gauge | Open file descriptors |
| `process.files.max` | Gauge | Max file descriptor limit |
| `system.load.average.1m` | Gauge | 1-minute system load average |
| `system.cpu.count` | Gauge | Number of CPU processors |

---

## Prometheus Query Examples

### Alert: High Work Item Queue Depth

```promql
# Fire alert when average queue depth > 5K items/pod
avg(yawl_workitem_queue_depth) > 5000
```

**Action:** HPA should auto-scale. If not firing, check HPA configuration.

### Alert: Excessive Cache Eviction

```promql
# Fire alert when cache hit rate drops below 70% (cold restore pressure)
rate(yawl.runner.cache.eviction_count[5m]) /
  (rate(yawl.runner.cache.eviction_count[5m]) +
   rate(yawl.runner.cache.hit_rate[5m])) < 0.7
```

**Action:** Monitor memory pressure; consider increasing pod count or heap.

### Alert: GC Pause Exceeded Target

```promql
# Fire alert when p99 GC pause > 10 ms
histogram_quantile(0.99, jvm_gc_pause_seconds_bucket{gc="ZGC"}) > 0.010
```

**Action:** Check heap usage; verify ZGC generational enabled.

### Dashboard: Throughput (Cases/Sec)

```promql
# Calculate sustained case start rate (moving average)
rate(yawl.case.created[1m])
```

**Expected @ 1M:** 40K cases/sec across 20 pods = 2K cases/sec/pod

---

## Metric Retention & Aggregation

| Time Window | Prometheus Retention | Sampling | Use Case |
|-------------|---------------------|----------|----------|
| 1 minute | Always | Raw data | Immediate anomaly detection |
| 15 minutes | Always | 15s samples | HPA scaling decisions |
| 1 hour | Always | 1m samples | Trend analysis |
| 7 days | Aggregated | 5m samples | Weekly reports |
| 30+ days | Long-term storage (e.g., S3) | 1h samples | SLA compliance, capacity planning |

---

## Custom Metrics: How to Add

Custom business metrics can be registered in Spring components:

```java
@Component
public class CustomWorkflowMetrics {
    private final MeterRegistry meterRegistry;

    public CustomWorkflowMetrics(MeterRegistry registry) {
        this.meterRegistry = registry;

        // Counter: total invoices processed
        Counter.builder("invoice.processed")
            .description("Total invoices processed")
            .register(registry);
    }

    public void recordInvoiceProcessed() {
        meterRegistry.counter("invoice.processed").increment();
    }
}
```

All custom metrics appear in `/actuator/prometheus` automatically.

---

## References

- [Micrometer Metrics](https://micrometer.io/)
- [Prometheus Documentation](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Grafana Dashboard Examples](https://grafana.com/grafana/dashboards/)
- [YAWL Capacity Planning: 1M Cases](../reference/capacity-planning-1m.md)
- [YAWL Configuration: Observability](../reference/configuration.md#7-observability-and-logging)

