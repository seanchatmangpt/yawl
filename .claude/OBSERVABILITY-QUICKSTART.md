# YAWL OpenTelemetry Quick Start

30-second setup guide for production-ready observability.

## Environment Variables

```bash
# Enable OpenTelemetry (default: true)
export OTEL_ENABLED=true

# OTLP endpoint (default: http://localhost:4317)
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

# Service name for trace attribution
export OTEL_SERVICE_NAME=yawl-engine

# Sampling rate (0.0-1.0, default: 1.0)
# Use 0.1 for production, 1.0 for development
export OTEL_TRACES_SAMPLER_ARG=0.1

# Metrics export interval in ms (default: 60000)
export OTEL_METRIC_EXPORT_INTERVAL=60000
```

## Docker Compose Setup

```yaml
# docker-compose.yml
services:
  yawl-engine:
    image: yawl:6.0.0
    environment:
      - OTEL_ENABLED=true
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
      - OTEL_SERVICE_NAME=yawl-engine
    ports:
      - "8080:8080"
    depends_on:
      - otel-collector

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.91.0
    volumes:
      - ./otel-config.yaml:/etc/otelcol/config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
      - "8888:8888"   # Prometheus metrics
    depends_on:
      - jaeger
      - prometheus

  jaeger:
    image: jaegertracing/all-in-one:1.52
    ports:
      - "16686:16686"  # UI
      - "14250:14250"

  prometheus:
    image: prom/prometheus:v2.48.0
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"
```

## Collector Configuration

```yaml
# otel-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 1s
    send_batch_size: 1024

exporters:
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true
  prometheus:
    endpoint: 0.0.0.0:8888

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlp/jaeger]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
```

## Code Integration

```java
// At application startup (before any workflow operations)
OpenTelemetryInitializer.init();

// Create workflow spans
Span caseSpan = WorkflowSpanBuilder.forCase(caseId)
    .withSpecId(specId)
    .withParentContext(parentContext)  // For distributed tracing
    .start();
try (Scope scope = caseSpan.makeCurrent()) {
    // ... workflow execution ...
} finally {
    caseSpan.end();
}

// Record metrics
YawlMetrics.recordCaseLaunched(specId);
YawlMetrics.recordCaseCompleted(specId, "success", durationMs);
YawlMetrics.recordWorkItemCompleted(taskName, durationMs);

// Structured logging
StructuredLogger.info("Case launched")
    .with("caseId", caseId)
    .with("specId", specId)
    .with("launchedBy", userId)
    .log();
```

## Verification

```bash
# Check health endpoint
curl http://localhost:8080/yawl/health

# Expected response
{
  "status": "UP",
  "components": {
    "otel": "UP",
    "database": "UP",
    "engine": "UP"
  }
}

# Access Jaeger UI for traces
open http://localhost:16686

# Access Prometheus for metrics
open http://localhost:9090
```

## Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `yawl.case.launched` | Counter | Cases started (by spec) |
| `yawl.case.completed` | Counter | Cases finished (by spec, outcome) |
| `yawl.case.duration` | Histogram | Case execution time (ms) |
| `yawl.workitem.created` | Counter | Work items created (by task) |
| `yawl.workitem.completed` | Counter | Work items finished (by task) |
| `yawl.workitem.duration` | Histogram | Work item processing time |
| `yawl.engine.active_cases` | Gauge | Currently running cases |
| `yawl.engine.active_workitems` | Gauge | Pending work items |

## Trace Attributes

All workflow spans include:

- `yawl.case.id` - Case identifier
- `yawl.spec.id` - Specification ID
- `yawl.spec.version` - Specification version
- `yawl.task.name` - Task/condition name
- `yawl.workitem.id` - Work item identifier
- `yawl.workitem.status` - Current status

## Troubleshooting

**No traces appearing in Jaeger:**
```bash
# Verify collector is receiving data
curl http://localhost:8888/metrics | grep otlp_receiver

# Check YAWL logs for OTEL errors
docker logs yawl-engine 2>&1 | grep -i otel
```

**High memory usage:**
```bash
# Reduce sampling in production
export OTEL_TRACES_SAMPLER_ARG=0.05

# Increase batch timeout
# (in otel-config.yaml)
processors:
  batch:
    timeout: 5s
```

**Missing metrics:**
```bash
# Verify Prometheus is scraping
curl http://localhost:9090/api/v1/targets

# Check metric endpoint
curl http://localhost:8888/metrics | grep yawl
```
