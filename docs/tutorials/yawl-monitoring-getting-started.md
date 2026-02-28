# Getting Started with YAWL Monitoring

Learn how to monitor workflow execution, collect metrics, and trace cases through your system.

## What You'll Learn

In this tutorial, you'll:
1. Enable OpenTelemetry tracing to Jaeger
2. Configure Prometheus metrics collection
3. Parse JSON-structured logs for debugging
4. Set up Grafana to visualize workflow metrics
5. Create alerts for critical conditions

**Time to complete:** 30 minutes
**Prerequisites:** YAWL v6.0+ running, Docker for Jaeger/Prometheus (optional)

---

## Part 1: OpenTelemetry Distributed Tracing

### Step 1: Start Jaeger Locally

```bash
# Run Jaeger all-in-one container
docker run -d \
  --name jaeger \
  -p 16686:16686 \
  -p 14268:14268 \
  jaegertracing/all-in-one:latest
```

Jaeger UI is now available at `http://localhost:16686`

### Step 2: Configure YAWL to Export Traces

Add to `application.yaml`:

```yaml
yawl:
  observability:
    tracing:
      enabled: true
      exporter: jaeger  # or 'otlp'
      jaeger:
        agent-host: localhost
        agent-port: 6831  # Thrift UDP
      service-name: yawl-engine
      sampling-ratio: 1.0  # 100% sampling for tutorial
```

Or use environment variables:

```bash
export YAWL_OBSERVABILITY_TRACING_ENABLED=true
export YAWL_OBSERVABILITY_TRACING_EXPORTER=jaeger
export YAWL_OBSERVABILITY_JAEGER_AGENT_HOST=localhost
export YAWL_OBSERVABILITY_JAEGER_AGENT_PORT=6831
export OTEL_SERVICE_NAME=yawl-engine
export OTEL_TRACES_SAMPLER_ARG=1.0  # 100% sampling
```

### Step 3: Execute a Case and View Traces

```bash
# Start a case
curl -X POST http://localhost:8080/yawl/api/v1/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "Invoice2024",
    "data": {"invoiceAmount": 1500.00}
  }' | jq -r '.caseId'

# Note the caseId, e.g., "i42bf8c7d2dd4fac23"
```

### Step 4: View Trace in Jaeger

1. Open `http://localhost:16686`
2. Select service `yawl-engine` from dropdown
3. Click **Find Traces**
4. Click on a trace to see:
   - Case lifecycle: Case created → Tasks enabled → Tasks completed → Case completed
   - Span durations: How long each task took
   - Custom attributes: `yawl.case.id`, `yawl.task.name`, `yawl.work_item.status`

### Trace Example

```
SPAN: case:create
├── SPAN: case:load_specification
├── SPAN: task:enable (ProcessInvoice)
│   └── SPAN: work_item:create
├── SPAN: task:enable (ApproveInvoice)
│   └── SPAN: work_item:create
└── SPAN: case:complete
```

---

## Part 2: Prometheus Metrics

### Step 1: Start Prometheus

```bash
# Create prometheus.yml
cat > /tmp/prometheus.yml <<'EOF'
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'yawl'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
EOF

# Run Prometheus
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v /tmp/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

Prometheus UI is at `http://localhost:9090`

### Step 2: Configure YAWL Metrics

Add to `application.yaml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health
  metrics:
    export:
      prometheus:
        enabled: true

yawl:
  observability:
    metrics:
      enabled: true
      case-metrics:
        enabled: true
      work-item-metrics:
        enabled: true
      engine-metrics:
        enabled: true
```

### Step 3: Query Metrics in Prometheus

Open `http://localhost:9090` and try these queries:

```promql
# Active cases
yawl_cases_active

# Cases completed today
increase(yawl_cases_completed_total[24h])

# Average task duration
histogram_quantile(0.95, rate(yawl_task_duration_seconds_bucket[5m]))

# Work items by status
yawl_work_items{status=~"ENABLED|EXECUTING|SUSPENDED"}
```

---

## Part 3: Structured Logging

### Step 1: View JSON Logs

YAWL emits JSON-structured logs that include trace context:

```bash
# Run YAWL and capture logs
java -jar yawl-engine-6.0.0-GA.jar 2>&1 | head -20
```

You'll see logs like:

```json
{
  "timestamp": "2026-02-28T14:30:45.123Z",
  "level": "INFO",
  "thread": "virtual-123",
  "logger": "org.yawlfoundation.yawl.engine.YNetRunner",
  "message": "Case created",
  "trace_id": "3fa414eac033eb87359425b53a1d3b8c",
  "span_id": "9a8b4628668dd298",
  "context": {
    "caseId": "i42bf8c7d2dd4fac23",
    "specId": "Invoice2024",
    "userId": "admin"
  }
}
```

### Step 2: Parse Logs with jq

Extract useful information from structured logs:

```bash
# Get all case creation events
cat yawl.log | jq 'select(.message == "Case created") | {timestamp, caseId: .context.caseId, specId: .context.specId}'

# Find errors related to a specific case
cat yawl.log | jq 'select(.context.caseId == "i42bf8c7d2dd4fac23" and .level == "ERROR")'

# Correlate logs with trace IDs
cat yawl.log | jq 'select(.trace_id == "3fa414eac033eb87359425b53a1d3b8c")'
```

### Step 3: Analyze Performance from Logs

```bash
# Get average task execution time
cat yawl.log | jq -r 'select(.event == "task:complete") | .duration_ms' | \
  awk '{sum += $1; count++} END {print "Average:", sum/count, "ms"}'
```

---

## Part 4: Grafana Dashboard

### Step 1: Start Grafana

```bash
docker run -d \
  --name grafana \
  -p 3000:3000 \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  grafana/grafana
```

Access Grafana at `http://localhost:3000` (admin/admin)

### Step 2: Add Prometheus Data Source

1. Go to **Configuration → Data Sources**
2. Click **Add data source**
3. Select **Prometheus**
4. URL: `http://prometheus:9090`
5. Click **Save & Test**

### Step 3: Create a Dashboard

Create a new dashboard with these panels:

#### Panel 1: Active Cases

```
Name: Active Cases
Query: yawl_cases_active
Visualization: Stat
```

#### Panel 2: Cases Completed (24h)

```
Name: Cases Completed Today
Query: increase(yawl_cases_completed_total[24h])
Visualization: Stat
```

#### Panel 3: Task Duration Distribution

```
Name: P95 Task Duration
Query: histogram_quantile(0.95, rate(yawl_task_duration_seconds_bucket[5m]))
Visualization: Graph
```

#### Panel 4: Work Items by Status

```
Name: Work Items by Status
Query: yawl_work_items
Visualization: Pie Chart
Group by: status
```

---

## Part 5: Alerting

### Step 1: Create Alert Rules

Create `yawl-alerts.yaml`:

```yaml
groups:
  - name: yawl_workflow_alerts
    interval: 30s
    rules:
      - alert: CaseStuck
        expr: |
          (time() - yawl_case_start_time_seconds{status="EXECUTING"}) > 3600
        for: 1h
        annotations:
          summary: "Case {{ $labels.caseId }} stuck for > 1 hour"
          severity: "warning"

      - alert: HighTaskFailureRate
        expr: |
          rate(yawl_task_failures_total[5m]) > 0.1
        annotations:
          summary: "High task failure rate: {{ $value | humanizePercentage }}"
          severity: "critical"

      - alert: WorkItemQueueDepth
        expr: |
          yawl_work_items{status="ENABLED"} > 100
        annotations:
          summary: "{{ $value }} work items pending"
          severity: "warning"
```

### Step 2: Configure Prometheus Alert Manager

Add to `prometheus.yml`:

```yaml
alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - localhost:9093  # Alertmanager

rule_files:
  - '/etc/prometheus/yawl-alerts.yaml'
```

### Step 3: Restart and Test

```bash
# Restart Prometheus with new config
docker restart prometheus

# View active alerts at http://localhost:9090/alerts
```

---

## Complete Example: Health Check Endpoint

Use the built-in health check endpoint to monitor YAWL:

```bash
curl http://localhost:8080/actuator/health | jq .
```

Response:

```json
{
  "status": "UP",
  "components": {
    "yawlEngine": {
      "status": "UP",
      "details": {
        "activeCases": 42,
        "activeWorkItems": 127,
        "uptime": "2h 15m"
      }
    },
    "database": {
      "status": "UP",
      "details": {
        "database": "H2",
        "validationQuery": "OK"
      }
    }
  }
}
```

---

## Troubleshooting

### Traces not appearing in Jaeger

**Problem:** Jaeger UI shows no traces

**Solution:**
1. Verify Jaeger is running: `curl http://localhost:14268/api/traces`
2. Check YAWL logs for tracer initialization: `grep OpenTelemetryInitializer`
3. Verify sampling rate is > 0: `echo $OTEL_TRACES_SAMPLER_ARG`

### Metrics not scraped by Prometheus

**Problem:** Prometheus shows "No data"

**Solution:**
1. Check scrape endpoint: `curl http://localhost:8080/actuator/prometheus`
2. Verify endpoint enabled: check Spring Boot `management.endpoints.web.exposure.include`
3. Check firewall: `docker logs prometheus` for connection errors

### JSON logs not structured

**Problem:** Logs are plain text instead of JSON

**Solution:** Verify Log4j2 JSON layout configured:
```bash
grep -r "JsonLayout" /path/to/log4j2.xml
```

---

## What's Next?

- **[Monitoring Configuration Reference](../reference/yawl-monitoring-config.md)** — All observability options
- **[How-To: Set Up Distributed Tracing](../how-to/yawl-monitoring-tracing.md)** — Advanced tracing
- **[How-To: Configure Alerts](../how-to/yawl-monitoring-alerts.md)** — Alert management
- **[Architecture: Observability Design](../explanation/yawl-monitoring-architecture.md)** — Deep dive

---

## Quick Reference

| Component | Port | URL |
|-----------|------|-----|
| Jaeger UI | 16686 | http://localhost:16686 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 |
| YAWL Health | 8080 | http://localhost:8080/actuator/health |
| YAWL Metrics | 8080 | http://localhost:8080/actuator/prometheus |

---

**Next:** [How-To: Set Up Grafana Dashboards](../how-to/yawl-monitoring-grafana.md)
