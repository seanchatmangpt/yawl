# YAWL v6.0.0 Comprehensive Observability & Monitoring Design

## Architecture Overview

This document describes the production-grade observability infrastructure for YAWL workflow engine, supporting distributed tracing, metrics collection, structured logging, and health checks across stateful and stateless deployments.

### Three Pillars

```
┌─────────────────────────────────────────────────────────────┐
│                    YAWL OBSERVABILITY                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────┐   ┌──────────────────┐   ┌──────────┐   │
│  │  TRACING        │   │  METRICS         │   │  LOGGING │   │
│  │ (OpenTelemetry) │   │ (Prometheus)     │   │  (ELK)   │   │
│  └─────────────────┘   └──────────────────┘   └──────────┘   │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐   │
│  │       HEALTH CHECKS & SERVICE LEVEL OBJECTIVES       │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. Distributed Tracing (OpenTelemetry)

### Design Goals

- Track workflow execution across engine components (YEngine → YNetRunner → Activities)
- Correlate requests through cross-service calls (engine → stateless converters)
- Support multiple exporters (Jaeger, Tempo, Datadog) without code changes
- Minimal performance overhead through sampling strategies

### Span Hierarchy

```
Trace: "case-execution-trace-id"
├─ Span: "case.create" (case_id=case-123, spec_id=spec-456)
│  └─ Span: "specification.validate" (xsd_size_bytes=102400)
├─ Span: "case.execute" (case_id=case-123)
│  ├─ Span: "net.run" (net_id=root_net, instance_count=1)
│  │  ├─ Span: "activity.execute" (activity_name=review, work_item_id=wi-1)
│  │  │  └─ Span: "resource.allocate" (resource_id=user-bob)
│  │  ├─ Span: "transition.fire" (transition_id=t1)
│  │  └─ Span: "activity.execute" (activity_name=approve, work_item_id=wi-2)
│  └─ Span: "case.complete" (completion_status=SUCCESS)
└─ Span: "case.archive" (archive_location=s3://yawl-archive)
```

### Implementation: OpenTelemetryInitializer

```java
// Initialize at application startup
OpenTelemetryInitializer.initialize();
Tracer tracer = OpenTelemetryInitializer.getTracer("yawl.engine");

// Create workflow spans
WorkflowSpanBuilder.create(tracer, "case.execute")
    .withCaseId("case-123")
    .withSpecificationId("spec-456")
    .setAttribute("initiator", "user@example.com")
    .start();
```

### Configuration

System properties control exporter selection:

```bash
# OTLP/gRPC exporter (Tempo, OpenTelemetry Collector)
-Dotel.exporter.otlp.endpoint=http://localhost:4317

# Jaeger Thrift exporter
-Dotel.exporter.jaeger.endpoint=http://localhost:14268/api/traces

# Resource attributes
-Dotel.service.name=yawl-engine-prod-us-west-2
-Dotel.service.version=6.0.0
-Dotel.resource.attributes=environment=production,region=us-west-2,zone=az-1
```

### Sampling Strategy

For high-throughput production:

```
- Head sampling: 10% of all traces
- Tail sampling: Always sample traces with errors
- Sampling rates per span type:
  - Case execution: 10%
  - Activity execution: 5%
  - Resource allocation: 1%
  - Archive operations: 100% (low volume)
```

---

## 2. Metrics & Monitoring (Prometheus)

### Key Metrics

#### Case Metrics

| Metric | Type | Labels | Purpose |
|--------|------|--------|---------|
| `yawl_case_created_total` | Counter | specification_id, initiator | Case creation rate |
| `yawl_case_completed_total` | Counter | specification_id, completion_status | Case success rate |
| `yawl_case_failed_total` | Counter | specification_id, error_type | Case failure rate |
| `yawl_case_duration_seconds` | Histogram | specification_id | Case execution time |
| `yawl_case_active` | Gauge | specification_id | Currently active cases |

#### Task Metrics

| Metric | Type | Labels | Purpose |
|--------|------|--------|---------|
| `yawl_task_executed_total` | Counter | activity_name, completion_type | Task execution rate |
| `yawl_task_failed_total` | Counter | activity_name, error_type | Task failure rate |
| `yawl_task_duration_seconds` | Histogram | activity_name | Task execution time |
| `yawl_task_pending` | Gauge | activity_name | Pending tasks in workqueue |

#### Engine Metrics

| Metric | Type | Labels | Purpose |
|--------|------|--------|---------|
| `yawl_engine_queue_depth` | Gauge | engine_instance | Work queue size |
| `yawl_engine_threadpool_active_workers` | Gauge | engine_instance | Active worker threads |
| `yawl_engine_threadpool_queue_size` | Gauge | engine_instance | Thread pool queue size |
| `yawl_engine_request_latency_seconds` | Histogram | operation | Request latency |
| `yawl_engine_memory_usage_bytes` | Gauge | memory_type (heap/non-heap) | Memory consumption |

#### Database Metrics

| Metric | Type | Labels | Purpose |
|--------|------|--------|---------|
| `yawl_db_connection_pool_active` | Gauge | pool_name | Active connections |
| `yawl_db_connection_pool_idle` | Gauge | pool_name | Idle connections |
| `yawl_db_query_duration_seconds` | Histogram | query_type (select/insert/update) | Query latency |
| `yawl_db_transaction_duration_seconds` | Histogram | transaction_type | Transaction latency |

### Implementation: YawlMetrics

```java
// Initialize Micrometer registry
MeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
YawlMetrics.initialize(meterRegistry);
YawlMetrics metrics = YawlMetrics.getInstance();

// Record case metrics
metrics.incrementCaseCreated();
metrics.recordCaseDuration(durationMs);
metrics.setActiveCaseCount(caseCount);

// Record engine state
metrics.setQueueDepth(engine.getQueueSize());
metrics.setActiveThreads(threadPool.getActiveCount());
```

### Prometheus Scrape Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'yawl-engine'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s

  - job_name: 'yawl-stateless'
    static_configs:
      - targets: ['localhost:8081']
    metrics_path: '/metrics'

  - job_name: 'kubernetes-nodes'
    kubernetes_sd_configs:
      - role: node
    scheme: https
    tls_config:
      ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
    bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
```

---

## 3. Structured Logging (ELK/Loki Stack)

### JSON Log Format

Each log entry is a structured JSON object:

```json
{
  "timestamp": "2026-02-17T12:34:56.789Z",
  "level": "INFO",
  "logger": "org.yawlfoundation.yawl.engine.YNetRunner",
  "message": "Net execution started",
  "correlation_id": "trace-123-456",
  "trace_id": "0af7651916cd43dd8448eb211c80319c",
  "span_id": "b7ad6b7169203331",
  "case_id": "case-789",
  "activity_name": "approve",
  "work_item_id": "wi-456",
  "execution_time_ms": 234,
  "custom_fields": {
    "resource_allocated": "bob@company.com",
    "queue_depth_before": 45,
    "queue_depth_after": 44
  }
}
```

### Log Routing Rules

```
ERROR logs (Severity: CRITICAL)
  ↓
  ├─ PagerDuty integration (immediate alert)
  ├─ Elasticsearch (retention: 30 days)
  └─ Archive to S3 (long-term compliance)

WARN logs (Severity: WARNING)
  ↓
  ├─ Loki aggregation (retention: 7 days)
  └─ Grafana alerts (threshold-based)

DEBUG logs (Retention: LOCAL ONLY)
  ↓
  └─ Local file (debug.log, rotation: 1GB)
```

### Implementation: StructuredLogger

```java
StructuredLogger log = StructuredLogger.getLogger(YEngine.class);

// Set correlation context
StructuredLogger.setCorrelationId("trace-123-456");
StructuredLogger.setTraceId("0af7651916cd43dd8448eb211c80319c");

// Log with structured fields
Map<String, Object> fields = new HashMap<>();
fields.put("case_id", "case-789");
fields.put("activity_name", "review");
fields.put("execution_time_ms", 234);
log.info("Case execution completed", fields);

// Clean up context
StructuredLogger.clearContext();
```

### Log4j 2 Configuration (log4j2-spring.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration packages="org.springframework.boot.logging.log4j2">
  <Properties>
    <Property name="LOG_PATTERN">%d{ISO8601} %-5p %c{2} [%X{correlation_id}] %m%n</Property>
    <Property name="LOG_FILE">logs/yawl.log</Property>
  </Properties>

  <Appenders>
    <!-- JSON stdout for ELK/Loki -->
    <Console name="JsonConsole" target="SYSTEM_OUT">
      <JsonLayout compact="true" eventEol="true">
        <KeyValuePair key="timestamp" value="$${date:ISO8601}" />
        <KeyValuePair key="level" value="$${level}" />
        <KeyValuePair key="logger" value="$${logger}" />
        <KeyValuePair key="message" value="$${message}" />
        <KeyValuePair key="correlation_id" value="$${ctx:correlation_id}" />
        <KeyValuePair key="trace_id" value="$${ctx:trace_id}" />
      </JsonLayout>
    </Console>

    <!-- Rolling file appender -->
    <RollingFile name="RollingFile" fileName="${LOG_FILE}"
                 filePattern="logs/yawl-%d{yyyy-MM-dd}-%i.log.gz">
      <PatternLayout pattern="${LOG_PATTERN}" />
      <Policies>
        <TimeBasedTriggeringPolicy interval="1" modulate="true" />
        <SizeBasedTriggeringPolicy size="100MB" />
      </Policies>
      <DefaultRolloverStrategy max="30" />
    </RollingFile>

    <!-- Error appender for ERROR logs -->
    <RollingFile name="ErrorFile" fileName="logs/error.log"
                 filePattern="logs/error-%d{yyyy-MM-dd}-%i.log.gz">
      <PatternLayout pattern="${LOG_PATTERN}" />
      <Filters>
        <ThresholdFilter level="ERROR" onMatch="ACCEPT" onMismatch="DENY" />
      </Filters>
      <Policies>
        <TimeBasedTriggeringPolicy interval="1" modulate="true" />
        <SizeBasedTriggeringPolicy size="100MB" />
      </Policies>
      <DefaultRolloverStrategy max="90" />
    </RollingFile>
  </Appenders>

  <Loggers>
    <Logger name="org.yawlfoundation.yawl" level="INFO" />
    <Root level="INFO">
      <AppenderRef ref="JsonConsole" />
      <AppenderRef ref="RollingFile" />
      <AppenderRef ref="ErrorFile" />
    </Root>
  </Loggers>
</Configuration>
```

### Loki Configuration (loki-config.yaml)

```yaml
auth_enabled: false

ingester:
  chunk_idle_period: 3m
  max_chunk_age: 1h
  max_streams_per_user: 10000
  max_global_streams_per_user: 10000
  chunk_retain_period: 1m

limits_config:
  enforce_metric_name: false
  reject_old_samples: true
  reject_old_samples_max_age: 168h

schema_config:
  configs:
  - from: 2026-01-01
    store: boltdb-shipper
    object_store: filesystem
    schema: v11
    index:
      prefix: index_
      period: 24h

server:
  http_listen_port: 3100
  log_level: info

storage_config:
  boltdb_shipper:
    active_index_directory: /loki/boltdb-shipper-active
    shared_store: filesystem
  filesystem:
    directory: /loki/chunks

chunk_store_config:
  max_look_back_period: 0s

table_manager:
  retention_deletes_enabled: false
  retention_period: 0s
```

---

## 4. Health Checks & Service Level Objectives

### Kubernetes Health Probes

#### Startup Probe (30s max, 1s period)

Waits for:
- YAWL schema validation (XSD loading)
- Database connectivity
- Initial case storage scan
- Warm-up metrics collection

```yaml
startupProbe:
  httpGet:
    path: /health/startup
    port: 8080
  initialDelaySeconds: 0
  periodSeconds: 1
  timeoutSeconds: 3
  failureThreshold: 30
```

#### Readiness Probe (10s max, 5s period)

Checks:
- Database pool > 0 available connections
- Work queue capacity < 90%
- Active worker threads > 0
- No critical errors in last 5 minutes

```yaml
readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

#### Liveness Probe (30s max, 10s period)

Checks:
- JVM heap available > 10 MB
- GC not stuck (last GC < 1 minute)
- Not deadlocked

```yaml
livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3
```

### Health Check Response

```json
{
  "status": "UP",
  "timestamp": "2026-02-17T12:34:56Z",
  "uptime": 3600000,
  "details": {
    "database_healthy": true,
    "queue_healthy": true,
    "active_workers": 8,
    "max_workers": 16,
    "queue_depth": 23,
    "queue_capacity": 1000,
    "jvm_memory_available": 523456789
  }
}
```

### Service Level Objectives (SLOs)

| SLO | Target | SLI Metric | Alert Threshold |
|-----|--------|-----------|-----------------|
| Case Availability | 99.9% | (successful_cases / total_cases) * 100 | < 99% in 5min window |
| Case Latency P95 | < 5s | histogram_quantile(0.95, case_duration_seconds) | > 7s for 5min |
| Case Latency P99 | < 10s | histogram_quantile(0.99, case_duration_seconds) | > 15s for 5min |
| Task Throughput | > 100/min | rate(task_executed_total[1m]) | < 80/min for 5min |
| Engine Health | 99.5% | (up_instances / total_instances) * 100 | < 99% for 2min |

### SLO Alerting Rules (Prometheus)

```yaml
groups:
  - name: yawl_slo_alerts
    interval: 1m
    rules:
      # Case availability alert
      - alert: YawlCaseAvailabilityLow
        expr: |
          (sum(rate(yawl_case_completed_total[5m])) /
           sum(rate(yawl_case_created_total[5m]))) < 0.99
        for: 5m
        labels:
          severity: critical
          service: yawl
        annotations:
          summary: "YAWL case availability below 99% ({{ $value | humanizePercentage }})"
          dashboard: "http://grafana:3000/d/yawl-overview"

      # Case latency alert (P95)
      - alert: YawlCaseLatencyP95High
        expr: |
          histogram_quantile(0.95, sum(rate(yawl_case_duration_seconds_bucket[5m])) by (le))
          > 7
        for: 5m
        labels:
          severity: warning
          service: yawl
        annotations:
          summary: "YAWL case latency P95 > 7s ({{ $value | humanizeDuration }})"

      # Task throughput alert
      - alert: YawlTaskThroughputLow
        expr: |
          sum(rate(yawl_task_executed_total[1m])) < 80
        for: 5m
        labels:
          severity: warning
          service: yawl
        annotations:
          summary: "YAWL task throughput below 80/min ({{ $value | humanize }}/min)"

      # Engine health alert
      - alert: YawlEngineHealthDegraded
        expr: |
          (count(up{job="yawl-engine"}) / 10) < 0.99
        for: 2m
        labels:
          severity: critical
          service: yawl
        annotations:
          summary: "YAWL engine health below 99% ({{ $value | humanizePercentage }})"
```

---

## 5. Alert Rules & Escalation

### Alert Severity Levels

| Severity | Definition | Response Time | Escalation |
|----------|-----------|----------------|------------|
| CRITICAL | Service down or data loss risk | 5 minutes | Page on-call |
| WARNING | SLO violation or degradation | 15 minutes | Slack #yawl-alerts |
| INFO | Informational only | N/A | Dashboard only |

### Alert Routing Rules (AlertManager)

```yaml
route:
  receiver: 'default'
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 12h

  routes:
    # Critical alerts → PagerDuty
    - match:
        severity: critical
      receiver: pagerduty
      continue: true

    # YAWL engine alerts → Slack
    - match:
        service: yawl
      receiver: slack-yawl
      group_interval: 1m
      repeat_interval: 1h

    # Database alerts → Database team
    - match:
        service: database
      receiver: slack-database

receivers:
  - name: 'default'
    slack_configs:
      - api_url: ${SLACK_WEBHOOK_URL}
        channel: '#alerts'

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: ${PAGERDUTY_SERVICE_KEY}
        description: '{{ .GroupLabels.alertname }}'
        details:
          firing: '{{ template "pagerduty.default.instances" .Alerts.Firing }}'

  - name: 'slack-yawl'
    slack_configs:
      - api_url: ${SLACK_WEBHOOK_YAWL}
        channel: '#yawl-alerts'
        title: 'YAWL Alert: {{ .GroupLabels.alertname }}'
        text: |
          {{ range .Alerts }}
            *{{ .Labels.severity | toUpper }}*: {{ .Annotations.summary }}
            Dashboard: {{ .Annotations.dashboard }}
          {{ end }}
```

### Alert Rules (prometheus-alerts.yml)

```yaml
groups:
  - name: yawl_alerts
    interval: 30s
    rules:
      # Engine CPU pressure
      - alert: YawlEngineCpuHigh
        expr: |
          rate(process_cpu_seconds_total[5m]) * 100 > 80
        for: 5m
        labels:
          severity: warning
          service: yawl
        annotations:
          summary: "YAWL engine CPU > 80% ({{ $value | humanize }}%)"
          runbook: "https://wiki.company.com/yawl/cpu-high"

      # Queue depth critical
      - alert: YawlQueueDepthCritical
        expr: |
          yawl_engine_queue_depth > 5000
        for: 2m
        labels:
          severity: critical
          service: yawl
        annotations:
          summary: "YAWL queue depth critical: {{ $value | humanize }} items"
          runbook: "https://wiki.company.com/yawl/queue-backlog"

      # No active workers
      - alert: YawlNoActiveWorkers
        expr: |
          yawl_engine_threadpool_active_workers == 0
        for: 1m
        labels:
          severity: critical
          service: yawl
        annotations:
          summary: "YAWL has no active worker threads"
          runbook: "https://wiki.company.com/yawl/no-workers"

      # High error rate
      - alert: YawlHighErrorRate
        expr: |
          (sum(rate(yawl_case_failed_total[5m])) /
           sum(rate(yawl_case_created_total[5m]))) > 0.05
        for: 5m
        labels:
          severity: warning
          service: yawl
        annotations:
          summary: "YAWL error rate > 5% ({{ $value | humanizePercentage }})"

      # Database connection pool exhausted
      - alert: YawlDbPoolExhausted
        expr: |
          yawl_db_connection_pool_idle == 0
        for: 2m
        labels:
          severity: critical
          service: yawl
        annotations:
          summary: "YAWL database connection pool exhausted"
          runbook: "https://wiki.company.com/yawl/db-pool-exhausted"

      # Memory pressure
      - alert: YawlMemoryPressure
        expr: |
          (process_resident_memory_bytes / process_virtual_memory_max_bytes) > 0.85
        for: 5m
        labels:
          severity: warning
          service: yawl
        annotations:
          summary: "YAWL memory usage > 85% ({{ $value | humanizePercentage }})"
```

### On-Call Escalation Policy

```
Time         Action              Team
────────────────────────────────────────────────
0-5 min      Alert firing        Automated PagerDuty
5-15 min     Primary on-call     Immediate page
15-30 min    Secondary escalate  Auto-page secondary
30+ min      Manager escalate    Escalate to manager
```

---

## 6. Grafana Dashboard Templates

### YAWL Engine Overview Dashboard

Key panels:
- Case throughput (cases/sec)
- Case latency (P50, P95, P99)
- Active cases gauge
- Error rate gauge
- Engine queue depth
- Worker thread utilization
- Database pool utilization

### Case Execution Flow Dashboard

Tracks:
- Cases created vs completed
- Case state distribution
- Time in each state
- Top failing activities
- Resource allocation timeline

### Performance & Resource Dashboard

Shows:
- CPU usage by component
- Memory heap/non-heap
- GC pause duration
- Database query latency
- Network I/O

---

## 7. Deployment Configuration

### Docker Compose (dev/staging)

```yaml
version: '3.8'
services:
  yawl-engine:
    image: yawlfoundation/yawl:6.0.0
    environment:
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
      OTEL_SERVICE_NAME: yawl-engine-dev
      LOG_LEVEL: DEBUG
    ports:
      - "8080:8080"
      - "8888:8888"  # Prometheus metrics
    depends_on:
      - postgres
      - otel-collector

  otel-collector:
    image: otel/opentelemetry-collector:latest
    ports:
      - "4317:4317"  # OTLP gRPC
      - "4318:4318"  # OTLP HTTP
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    command: ["--config=/etc/otel-collector-config.yaml"]

  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"  # UI
      - "14268:14268"  # Thrift HTTP

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - ./prometheus-alerts.yml:/etc/prometheus/alerts.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    ports:
      - "3000:3000"
    volumes:
      - ./grafana-dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana-datasources.yml:/etc/grafana/provisioning/datasources/datasources.yml

  loki:
    image: grafana/loki:latest
    ports:
      - "3100:3100"
    volumes:
      - ./loki-config.yaml:/etc/loki/local-config.yaml

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_PASSWORD: yawl-dev
```

### Kubernetes Deployment

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-observability-config
data:
  otel-collector-config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318

    processors:
      batch:
        send_batch_size: 1024
        timeout: 5s
      memory_limiter:
        check_interval: 1s
        limit_mib: 512
        spike_limit_mib: 128

    exporters:
      tempo:
        endpoint: tempo:4317
        tls:
          insecure: true
      prometheus:
        endpoint: 0.0.0.0:8889

    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [memory_limiter, batch]
          exporters: [tempo]
        metrics:
          receivers: [otlp]
          processors: [batch]
          exporters: [prometheus]
```

---

## 8. Quick Start Guide

### 1. Initialize Observability

```java
// At application startup (ServletContextListener or Spring ApplicationListener)
OpenTelemetryInitializer.initialize();

// Initialize metrics
MeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
YawlMetrics.initialize(meterRegistry);

// Initialize health checks
HealthCheckEndpoint healthCheck = new HealthCheckEndpoint(new MyHealthCheckDelegate());
```

### 2. Add Tracing to Case Execution

```java
public class YNetRunner {
    private final Tracer tracer = OpenTelemetryInitializer.getTracer("yawl.engine");

    public void execute(YCase yCase) {
        Span span = WorkflowSpanBuilder.create(tracer, "net.run")
            .withCaseId(yCase.getCaseID())
            .withSpecificationId(yCase.getSpecificationID())
            .start();

        try (Scope scope = span.makeCurrent()) {
            // Execution logic
        } finally {
            span.end();
        }
    }
}
```

### 3. Record Metrics

```java
public class YEngine {
    public YCase createCase(String specificationID) {
        YawlMetrics metrics = YawlMetrics.getInstance();
        metrics.incrementCaseCreated();
        // ... create case ...
    }

    public void executeCase(YCase yCase) {
        YawlMetrics metrics = YawlMetrics.getInstance();
        Timer.Sample sample = metrics.startCaseExecutionTimer();
        try {
            // ... execute case ...
        } finally {
            metrics.recordCaseExecutionTime(sample);
        }
    }
}
```

### 4. Use Structured Logging

```java
public class YActivity {
    private static final StructuredLogger log = StructuredLogger.getLogger(YActivity.class);

    public void executeActivity() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", caseId);
        fields.put("activity_name", name);
        fields.put("resource_id", resource);
        log.info("Activity execution started", fields);
    }
}
```

---

## 9. Production Runbooks

### High Queue Depth

**Symptoms**: `yawl_engine_queue_depth > 5000`

**Actions**:
1. Check worker thread count: `kubectl top pods -l app=yawl-engine`
2. Scale up engine instances: `kubectl scale deployment yawl-engine --replicas=4`
3. Review slow activities: Query Grafana "Top Slow Activities" dashboard
4. Consider task timeout tuning

### Case Latency Spike

**Symptoms**: `yawl_case_duration_seconds{quantile="0.95"} > 7`

**Actions**:
1. Check database query latency: `SELECT * FROM (SELECT quantile(0.95, duration) FROM yawl_db_queries WHERE timestamp > now()-5m)`
2. Review query plans: Check slow query log
3. Analyze activity distribution: Which activities are slow?
4. Consider indexing improvements

### No Active Workers

**Symptoms**: `yawl_engine_threadpool_active_workers == 0`

**Actions**:
1. Check thread dumps: `jstack <pid> | grep yawl`
2. Verify database connectivity
3. Check for deadlocks
4. Restart engine pod if necessary

---

## References

- [OpenTelemetry Java SDK Docs](https://opentelemetry.io/docs/instrumentation/java/)
- [Prometheus Operator for Kubernetes](https://prometheus-operator.dev/)
- [Grafana Loki Documentation](https://grafana.com/docs/loki/)
- [SLO Best Practices](https://sre.google/books/)
