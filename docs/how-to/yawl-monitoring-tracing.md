# How-To: Set Up Distributed Tracing

Configure OpenTelemetry to trace cases through your YAWL system for debugging and performance analysis.

## Prerequisites

- YAWL v6.0+ running
- Jaeger or OTLP-compatible backend (Grafana Loki, Honeycomb, etc.)
- Docker (optional, for running Jaeger locally)

---

## Task 1: Choose Your Trace Exporter

### Option A: Jaeger (Recommended for Getting Started)

Jaeger is self-contained and includes a UI. Best for development and testing.

**Advantages:**
- UI included
- No additional backend needed
- Easy local setup

**Disadvantages:**
- Single-node only
- Limited retention

### Option B: OTLP (Recommended for Production)

OTLP is the OpenTelemetry standard protocol. Supports multiple backends.

**Supports:**
- Grafana Loki (logging)
- Datadog
- New Relic
- Honeycomb
- AWS X-Ray
- GCP Cloud Trace

---

## Task 2: Set Up Jaeger Locally

### Start Jaeger Container

```bash
docker run -d \
  --name jaeger \
  -p 16686:16686 \
  -p 14268:14268 \
  -p 6831:6831/udp \
  jaegertracing/all-in-one:latest
```

**Port mapping:**
- 16686: Jaeger UI (http://localhost:16686)
- 14268: HTTP collector
- 6831: Thrift UDP agent (used by YAWL)

### Verify Jaeger is Running

```bash
curl http://localhost:14268/api/traces
# Should return: {"data":[],"total":0,"limit":0,"offset":0,"errors":null}
```

---

## Task 3: Configure YAWL to Export to Jaeger

### Using Environment Variables

```bash
export OTEL_TRACES_SAMPLER=parentbased_always_on
export OTEL_SERVICE_NAME=yawl-engine
export OTEL_EXPORTER_JAEGER_AGENT_HOST=localhost
export OTEL_EXPORTER_JAEGER_AGENT_PORT=6831
export OTEL_EXPORTER_JAEGER_ENABLED=true
```

### Using application.yaml

```yaml
otel:
  enabled: true
  service-name: yawl-engine

otel.exporter.jaeger:
  enabled: true
  agent-host: localhost
  agent-port: 6831

otel.sdk.trace:
  enabled: true
  sample-rate: 1.0  # 100% sampling (use 0.1 for 10% in production)
```

### Using system properties

```bash
java -Dotel.service.name=yawl-engine \
     -Dotel.exporter.jaeger.enabled=true \
     -Dotel.exporter.jaeger.agent.host=localhost \
     -Dotel.exporter.jaeger.agent.port=6831 \
     -Dotel.traces.sampler=parentbased_always_on \
     -jar yawl-engine-6.0.0-GA.jar
```

---

## Task 4: Create and Execute a Case

```bash
# Start a case
CASE_ID=$(curl -s -X POST http://localhost:8080/yawl/api/v1/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "Invoice2024",
    "data": {
      "invoiceAmount": 5000.00,
      "vendor": "Acme Corp"
    }
  }' | jq -r '.caseId')

echo "Started case: $CASE_ID"
```

---

## Task 5: View Traces in Jaeger UI

### Open Jaeger UI

Navigate to http://localhost:16686

### Find Your Traces

1. Select **Service** dropdown → choose `yawl-engine`
2. Click **Find Traces**
3. You should see traces from your case execution

### Examine a Trace

Click on a trace to see the span waterfall:

```
SPAN: case.create (100ms)
├── SPAN: specification.load (15ms)
├── SPAN: net.initialize (20ms)
├── SPAN: task.enable[ProcessInvoice] (10ms)
│   └── SPAN: work_item.create (5ms)
└── SPAN: task.enable[ReviewAndApprove] (10ms)
    └── SPAN: work_item.create (5ms)

SPAN: task.complete[ProcessInvoice] (50ms)
├── SPAN: work_item.update (10ms)
└── SPAN: delegation.evaluate (20ms)

SPAN: case.complete (30ms)
└── SPAN: case.persist (15ms)
```

### View Span Details

Click on a span to see:
- **Duration:** How long the operation took
- **Attributes:** Custom data (caseId, taskName, status, etc.)
- **Events:** State transitions and milestones
- **Logs:** Error messages or debug info

---

## Task 6: Set Up OTLP Export to Grafana Loki

For production, use OTLP to export to Grafana Loki for long-term storage and analysis.

### Start Grafana Stack

```bash
docker run -d \
  --name loki \
  -p 3100:3100 \
  grafana/loki:latest

docker run -d \
  --name prometheus \
  -p 9090:9090 \
  prom/prometheus

docker run -d \
  --name grafana \
  -p 3000:3000 \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  grafana/grafana:latest
```

### Configure YAWL for OTLP

```yaml
otel:
  enabled: true
  service-name: yawl-engine

otel.exporter.otlp:
  enabled: true
  endpoint: http://localhost:4317  # OTLP gRPC endpoint
  # Alternative: http://localhost:4318  # OTLP HTTP endpoint
  protocol: grpc  # or 'http/protobuf'
  timeout: 30000  # milliseconds
  headers:
    Authorization: "Bearer $LOKI_API_TOKEN"  # If Loki requires auth

otel.sdk.trace:
  enabled: true
  sampler: parentbased_always_on
  batch-size: 512
  scheduled-delay: 5000  # 5 second batches
```

---

## Task 7: Configure Sampling

Production systems should not sample 100% of traces (uses too much memory/bandwidth).

### Set Sampling Rate

```yaml
otel.sdk.trace:
  sample-rate: 0.1  # Sample 10% of traces
```

Or use environment variable:

```bash
export OTEL_TRACES_SAMPLER=parentbased_traceidratio
export OTEL_TRACES_SAMPLER_ARG=0.1  # 10%
```

### Sampling Strategy

| Scenario | Rate | Rationale |
|----------|------|-----------|
| Development | 1.0 (100%) | Need full visibility |
| Staging | 0.1-0.5 (10-50%) | Balance detail vs. cost |
| Production | 0.01-0.1 (1-10%) | Minimize overhead |
| High-throughput | 0.001 (0.1%) | Only sample 1 in 1000 |

---

## Task 8: Add Custom Attributes

Enrich spans with business context:

### Add Case Attributes

```java
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;

public class InvoiceProcessor {
    private Tracer tracer = ...;

    public void processInvoice(String caseId, InvoiceData invoice) {
        Span span = tracer.spanBuilder("invoice.process")
            .setAttribute("yawl.case.id", caseId)
            .setAttribute("yawl.spec.id", "Invoice2024")
            .setAttribute("invoice.amount", invoice.getAmount())
            .setAttribute("invoice.vendor", invoice.getVendor())
            .setAttribute("invoice.currency", "USD")
            .startSpan();

        try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
            // Process invoice
        } finally {
            span.end();
        }
    }
}
```

### Predefined Attributes

YAWL automatically adds these attributes:

| Attribute | Example | Description |
|-----------|---------|-------------|
| `yawl.case.id` | `i42bf8c7d2dd4fac23` | Unique case identifier |
| `yawl.spec.id` | `Invoice2024` | Specification name |
| `yawl.spec.version` | `1` | Specification version |
| `yawl.net.id` | `InvoiceNet` | Net/subprocess name |
| `yawl.task.id` | `ReviewAndApprove` | Task name |
| `yawl.task.type` | `ATOMIC\|COMPOSITE` | Task type |
| `yawl.work_item.id` | `wi_abc123` | Work item identifier |
| `yawl.work_item.status` | `ENABLED\|EXECUTING\|SUSPENDED\|COMPLETED` | Work item status |
| `yawl.user.id` | `admin` | Current user |
| `yawl.resource.id` | `Participant_1` | Resource ID |

---

## Task 9: Trace Complex Operations

### Trace Case Lifecycle

```
TRACE: case:i42bf8c7d2dd4fac23
├── SPAN: case.create (100ms)
│   ├── SPAN: spec.load (15ms)
│   │   └── SPAN: spec.parse (10ms)
│   └── SPAN: net.initialize (20ms)
│
├── SPAN: task.enable[ProcessInvoice] (50ms)
│   ├── SPAN: work_item.create (20ms)
│   ├── SPAN: resource.allocate (15ms)
│   └── SPAN: notification.send (10ms)
│
├── SPAN: work_item.complete[ProcessInvoice] (150ms)
│   ├── SPAN: data.persist (50ms)
│   ├── SPAN: task.execute (70ms)
│   └── SPAN: audit.log (10ms)
│
└── SPAN: case.complete (30ms)
    ├── SPAN: case.persist (15ms)
    └── SPAN: notification.send (10ms)
```

### Query Traces in Jaeger

Find cases by attribute:

```
caseId="i42bf8c7d2dd4fac23"
```

Find slow operations:

```
minDuration=1000ms  # Traces slower than 1 second
```

Find errors:

```
error=true
```

---

## Task 10: Monitor Trace Performance

### Check Trace Ingest Rate

```bash
# Get metrics from Jaeger
curl http://localhost:14269/metrics | grep jaeger_agent

# Example output:
# jaeger_agent_processor_spans_processed_total{handler="zipkin_thrift",format="...
# jaeger_agent_processor_zipkin_spans_received_total 1234
```

### Correlate Traces with Logs

YAWL includes `trace_id` and `span_id` in structured logs:

```json
{
  "timestamp": "2026-02-28T14:35:45.123Z",
  "level": "INFO",
  "message": "Case created",
  "trace_id": "3fa414eac033eb87359425b53a1d3b8c",
  "span_id": "9a8b4628668dd298",
  "context": {
    "caseId": "i42bf8c7d2dd4fac23"
  }
}
```

Search logs by trace ID:

```bash
# Using grep
grep "3fa414eac033eb87359425b53a1d3b8c" yawl.log

# Using Loki query
{trace_id="3fa414eac033eb87359425b53a1d3b8c"}
```

---

## Troubleshooting

### Traces Not Appearing in Jaeger

**Check 1: Verify Jaeger is running**
```bash
curl http://localhost:14268/api/traces
```

**Check 2: Verify YAWL is exporting**
```bash
# Look for startup logs
grep -i "opentelemetry\|jaeger" yawl.log | head -10
```

**Check 3: Verify network connectivity**
```bash
# From YAWL container/host
telnet localhost 6831
```

**Check 4: Enable debug logging**
```yaml
logging:
  level:
    io.opentelemetry: DEBUG
```

### High Memory Usage from Tracing

**Solution 1: Reduce sampling rate**
```bash
export OTEL_TRACES_SAMPLER_ARG=0.01  # 1% instead of 100%
```

**Solution 2: Reduce batch size**
```yaml
otel.sdk.trace:
  batch-size: 256  # Instead of 512
```

**Solution 3: Reduce export timeout**
```yaml
otel.sdk.trace:
  scheduled-delay: 2000  # 2 seconds instead of 5
```

### Trace Data Not Correlated with Logs

**Solution:** Ensure Log4j2 JSON layout includes trace context:

```xml
<JsonLayout>
  <KeyValuePair key="trace_id" value="${otel:traceId}" />
  <KeyValuePair key="span_id" value="${otel:spanId}" />
  <KeyValuePair key="trace_flags" value="${otel:traceFlags}" />
</JsonLayout>
```

---

## What's Next?

- **[Monitoring Configuration Reference](../reference/yawl-monitoring-config.md)** — All tracing options
- **[How-To: Grafana Dashboards](../how-to/yawl-monitoring-grafana.md)** — Visualize traces
- **[Architecture: Observability Design](../explanation/yawl-monitoring-architecture.md)** — Deep dive

---

**Return to:** [Tutorial: Getting Started](../tutorials/yawl-monitoring-getting-started.md)
