# YAWL Monitoring Architecture

Understanding distributed tracing, metrics collection, and observability stack.

---

## Observability Pyramid

```
        ┌─────────────────────┐
        │  Custom Dashboards  │  <-- Grafana, Kibana
        ├─────────────────────┤
        │  Alerts & Rules     │  <-- Prometheus AlertManager
        ├─────────────────────┤
        │  Metrics            │  <-- Micrometer → Prometheus
        ├─────────────────────┤
        │  Logs               │  <-- JSON logs → Loki
        ├─────────────────────┤
        │  Traces             │  <-- OpenTelemetry → Jaeger
        └─────────────────────┘
```

---

## Distributed Tracing (OpenTelemetry)

### Trace Anatomy

A **trace** follows a case through the system:

```
Trace ID: 3fa414eac033eb87359425b53a1d3b8c

  SPAN: case:create [duration: 100ms]
  ├─ Event: created_at=2026-02-28T14:30:45.123Z
  ├─ Attribute: yawl.case.id=i42bf8c7d2dd4fac23
  ├─ Attribute: yawl.spec.id=Invoice2024
  │
  ├─ SPAN: spec:load [duration: 15ms]
  │  └─ Event: spec_parsed_at=2026-02-28T14:30:45.138Z
  │
  ├─ SPAN: net:initialize [duration: 20ms]
  │  └─ Event: net_ready_at=2026-02-28T14:30:45.158Z
  │
  └─ SPAN: task:enable[ProcessInvoice] [duration: 50ms]
     ├─ Attribute: yawl.task.id=ProcessInvoice
     ├─ Attribute: yawl.task.type=ATOMIC
     │
     └─ SPAN: work_item:create [duration: 20ms]
        └─ Attribute: yawl.work_item.id=wi_abc123
```

### Span Attributes

Enriched metadata on each span:

```json
{
  "traceId": "3fa414eac033eb87359425b53a1d3b8c",
  "spanId": "9a8b4628668dd298",
  "parentSpanId": "a1b2c3d4e5f6g7h8",
  "name": "task:enable",
  "startTime": "2026-02-28T14:30:45.158Z",
  "endTime": "2026-02-28T14:30:45.208Z",
  "durationMs": 50,
  "attributes": {
    "yawl.task.id": "ProcessInvoice",
    "yawl.task.type": "ATOMIC",
    "yawl.case.id": "i42bf8c7d2dd4fac23",
    "yawl.spec.id": "Invoice2024",
    "user.id": "admin"
  },
  "events": [
    {
      "name": "task_enabled",
      "timestamp": "2026-02-28T14:30:45.208Z"
    }
  ]
}
```

### Trace Export

```
YAWL Engine
    │
    ├─ Jaeger Exporter (UDP Thrift)
    │  └─ localhost:6831
    │     └─ Jaeger Collector
    │        └─ Jaeger Storage (Elasticsearch)
    │           └─ Jaeger UI (http://localhost:16686)
    │
    ├─ OTLP Exporter (gRPC)
    │  └─ localhost:4317
    │     └─ OTel Collector
    │        ├─ Grafana Loki (logs)
    │        ├─ Prometheus (metrics)
    │        └─ Jaeger (traces)
    │
    └─ OpenMetrics Exporter
       └─ localhost:8080/metrics
          └─ Prometheus Scrape Endpoint
```

---

## Metrics Collection (Micrometer + Prometheus)

### Metric Types

**Counter**: Monotonically increasing (never decreases)
```
yawl_cases_created_total: 5432
yawl_work_items_completed_total: 28450
yawl_task_failures_total: 12
```

**Gauge**: Current value (up or down)
```
yawl_cases_active: 42
yawl_work_items_pending: 127
yawl_engine_memory_bytes: 1073741824
```

**Histogram**: Distribution of values with percentiles
```
yawl_task_duration_seconds_bucket{le="0.1"}: 450
yawl_task_duration_seconds_bucket{le="1.0"}: 890
yawl_task_duration_seconds_bucket{le="10.0"}: 1000
```

**Timer**: Counter + Histogram (duration)
```
yawl_case_duration_seconds{quantile="0.5"}: 15.2
yawl_case_duration_seconds{quantile="0.95"}: 45.7
yawl_case_duration_seconds{quantile="0.99"}: 120.3
```

### Scrape Process

```
Time: T+0
Prometheus Config:
  scrape_interval: 15s
  static_configs:
    - targets: ['localhost:8080']
      metrics_path: '/actuator/prometheus'
    │
    ├─ HTTP GET /actuator/prometheus
    │  Endpoint returns all metrics in Prometheus text format
    │
    ├─ Parse response
    │  yawl_cases_active 42
    │  yawl_work_items_pending 127
    │  ...
    │
    └─ Store in TSDB
       Prometheus time-series database

Time: T+15s
  Scrape again, update metrics
```

---

## Structured Logging

### JSON Log Format

```json
{
  "timestamp": "2026-02-28T14:35:45.123Z",
  "level": "INFO",
  "thread": "virtual-123",
  "logger": "org.yawlfoundation.yawl.engine.YNetRunner",
  "message": "Work item enabled",
  "trace_id": "3fa414eac033eb87359425b53a1d3b8c",
  "span_id": "9a8b4628668dd298",
  "context": {
    "caseId": "i42bf8c7d2dd4fac23",
    "taskId": "ProcessInvoice",
    "workItemId": "wi_abc123",
    "userId": "admin",
    "duration_ms": 50
  }
}
```

### Log Pipeline

```
YAWL Application
    │ Log4j 2 Appender
    ├─ Console: JSON + timestamps
    ├─ File: Rotated logs (compressed)
    └─ Async: Queue-based, non-blocking

Collected by:
    ├─ Prometheus: Via scrape endpoint
    ├─ Grafana Loki: Via LogQL queries
    └─ ELK Stack: Via Filebeat shipper
```

---

## Sampling Strategy

### Problem: Too Much Data

100% sampling at 10K cases/sec:
- 36 billion traces per day
- Storage: 50 TB/month (unacceptable)

### Solution: Intelligent Sampling

```
Sampler Strategy: parentbased_traceidratio

Rule 1: Parent sampled?
  └─ Yes → child sampled (propagate sampling)
  └─ No → probability-based (10% chance)

Result:
  Consistent traces (all spans in trace sampled/not sampled)
  Storage: 5 TB/month (10% data)
  Still captures issues (errors always sampled)
```

### Sampling Configuration

```yaml
otel.sdk.trace:
  sampler: parentbased_traceidratio
  sample-rate: 0.1        # 10% of new traces
  sampler-arg: 0.1        # 10% sampling ratio
```

**Tracing cost:**
- 100% sampling: High cost, full visibility
- 10% sampling: 10× cheaper, 90% detail loss
- 1% sampling: 100× cheaper, 99% detail loss

**Decision**: Start with 10%, reduce if costs are high.

---

## Alerting

### Alert Rule Example

```yaml
alert: CaseStuck
expr: |
  (time() - yawl_case_start_time_seconds{status="EXECUTING"}) > 3600
for: 1h
annotations:
  summary: "Case {{ $labels.caseId }} stuck for > 1 hour"
  severity: "warning"
  dashboard: "https://grafana.example.com/d/yawl-cases"
```

When triggered:
1. Send to AlertManager
2. Route to Slack/Email/PagerDuty
3. Create incident
4. Auto-remediation (optional)

---

## Architecture Decisions

### Why OpenTelemetry?

**Alternative 1: Custom logging**
```
Cons: No standard, hard to correlate across services
```

**Alternative 2: Vendor-specific (Datadog, New Relic)**
```
Cons: Lock-in, hard to switch later
```

**Choice: OpenTelemetry (CNCF standard)**
```
Pros: Multi-backend support, standard protocol, vendor-agnostic
```

### Why JSON Logs?

**Alternative 1: Plain text logs**
```
2026-02-28 14:35:45 [thread-1] INFO: Case created
Cons: Hard to parse, no structure for analysis
```

**Alternative 2: Structured JSON**
```
{"timestamp": "...", "level": "INFO", "context": {...}}
Pros: Queryable, machine-readable, easy to grep
```

---

## Performance Impact

| Feature | CPU Overhead | Memory | Network |
|---------|-------------|--------|---------|
| 100% tracing | +15% | +100 MB | +5 Mbps |
| 10% tracing | +2% | +50 MB | +500 Kbps |
| 1% tracing | +0.5% | +20 MB | +100 Kbps |
| Metrics only | +1% | +10 MB | +50 Kbps |
| Logs only | +2% | +20 MB | +100 Kbps |

**Recommendation**: Start with 10% tracing + metrics + logs.

---

## Related Architecture

- **[Authentication](yawl-authentication-architecture.md)** — Trace auth events
- **[Scheduling](yawl-scheduling-architecture.md)** — Monitor schedule execution
- **[Monitoring](yawl-monitoring-architecture.md)** ← (you are here)
- **[Worklets](yawl-worklet-architecture.md)** — Trace RDR evaluation

---

## See Also

- [How-To: Set Up Distributed Tracing](../how-to/yawl-monitoring-tracing.md)
- [Reference: Configuration Options](../reference/yawl-monitoring-config.md)
- [Tutorial: Getting Started](../tutorials/yawl-monitoring-getting-started.md)
