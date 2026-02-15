# YAWL OpenTelemetry Quick Start Guide

Get YAWL observability up and running in 5 minutes.

## Prerequisites

- Docker and Docker Compose installed
- Maven 3.6+ (for building YAWL)
- Java 21+
- 8GB RAM minimum (for full stack)

## Option 1: Zero-Code Instrumentation (Recommended)

The fastest way to get observability - no code changes required!

### Step 1: Build YAWL with OpenTelemetry Agent

```bash
cd /home/user/yawl

# Build YAWL and download OpenTelemetry agent
mvn clean package

# Verify agent downloaded
ls -lh target/agents/opentelemetry-javaagent.jar
```

### Step 2: Start the Observability Stack

```bash
# Start OpenTelemetry Collector, Jaeger, Prometheus, Grafana
cd observability/opentelemetry
docker-compose -f docker-compose-otel.yml up -d

# Wait for services to be ready (30-60 seconds)
docker-compose -f docker-compose-otel.yml ps
```

### Step 3: Run YAWL with OpenTelemetry

```bash
cd /home/user/yawl

# Development mode (outputs traces to console)
./scripts/run-with-otel-dev.sh

# OR production mode (sends to collector)
./scripts/run-with-otel.sh
```

### Step 4: Access Observability UIs

Open your browser:

- **YAWL Application**: http://localhost:8080/yawl
- **Grafana Dashboards**: http://localhost:3000 (admin/admin)
- **Jaeger Traces**: http://localhost:16686
- **Prometheus Metrics**: http://localhost:9090
- **Prometheus (YAWL Metrics)**: http://localhost:9464/metrics

### Step 5: Generate Some Traffic

Execute a few workflows to generate telemetry data:

```bash
# Upload a specification
curl -X POST http://localhost:8080/yawl/ia \
  -F file=@exampleSpecs/OrderFulfillment.yawl

# Launch a case
curl -X POST http://localhost:8080/yawl/ib/launchCase \
  -d specID=OrderFulfillment \
  -d caseParams='<data/>'
```

### Step 6: View Your Observability Data

**Grafana Dashboard:**
1. Go to http://localhost:3000
2. Login: admin/admin
3. Navigate to Dashboards → YAWL Workflow Engine
4. See real-time metrics and traces

**Jaeger Traces:**
1. Go to http://localhost:16686
2. Select Service: `yawl-engine`
3. Click "Find Traces"
4. View detailed trace timelines

**Prometheus Metrics:**
1. Go to http://localhost:9090
2. Try queries:
   - `yawl_cases_active` - Active workflow cases
   - `rate(yawl_case_started_total[5m])` - Case start rate
   - `histogram_quantile(0.95, rate(yawl_case_duration_bucket[5m]))` - p95 case duration

Done! Your YAWL instance now has full observability.

---

## Option 2: Manual Instrumentation (Advanced)

For deeper YAWL-specific metrics and custom traces.

### Step 1: Build YAWL with Dependencies

```bash
cd /home/user/yawl
mvn clean package
```

### Step 2: Configure Spring Boot

Create `src/main/resources/application.properties`:

```properties
# Copy from observability/opentelemetry/application-otel.properties
yawl.observability.enabled=true
yawl.observability.exporter.type=otlp
yawl.observability.otlp.endpoint=http://localhost:4318
```

### Step 3: Start Observability Stack

```bash
cd observability/opentelemetry
docker-compose -f docker-compose-otel.yml up -d
```

### Step 4: Run YAWL with Spring Boot Profile

```bash
java -jar target/yawl-5.2.jar --spring.profiles.active=otel
```

### Step 5: Verify Custom Metrics

```bash
# Check YAWL-specific metrics
curl http://localhost:9464/metrics | grep yawl

# You should see:
# - yawl_case_started_total
# - yawl_case_completed_total
# - yawl_workitem_duration_bucket
# - yawl_cases_active
# ... and more
```

---

## Development Mode (No External Dependencies)

For local development without Docker:

```bash
# Use logging exporters
export OTEL_TRACES_EXPORTER=logging
export OTEL_METRICS_EXPORTER=logging

# Run with dev configuration
./scripts/run-with-otel-dev.sh

# Traces and metrics will be logged to console
# Prometheus metrics still available at http://localhost:9464/metrics
```

---

## Kubernetes Quick Start

### Step 1: Create Namespace

```bash
kubectl create namespace yawl-observability
```

### Step 2: Deploy OpenTelemetry Collector

```bash
kubectl apply -f k8s/observability/otel-collector.yaml -n yawl-observability
```

### Step 3: Deploy YAWL with Auto-Instrumentation

```bash
# Deploy YAWL with OpenTelemetry agent injection
kubectl apply -f k8s/yawl-deployment-otel.yaml -n yawl-observability
```

### Step 4: Access Services

```bash
# Port-forward Grafana
kubectl port-forward -n yawl-observability svc/grafana 3000:3000

# Port-forward Jaeger
kubectl port-forward -n yawl-observability svc/jaeger-query 16686:16686

# Access YAWL
kubectl port-forward -n yawl-observability svc/yawl-engine 8080:8080
```

---

## Cloud Provider Quick Start

### Google Cloud Platform (GCP)

```bash
# Set GCP project
export GCP_PROJECT_ID=my-yawl-project

# Use Cloud Trace and Cloud Monitoring
export OTEL_TRACES_EXPORTER=google_cloud_trace
export OTEL_METRICS_EXPORTER=google_cloud_monitoring
export OTEL_EXPORTER_GCP_PROJECT_ID=$GCP_PROJECT_ID

# Run YAWL
./scripts/run-with-otel.sh
```

View traces in GCP Console → Trace
View metrics in GCP Console → Monitoring

### Amazon Web Services (AWS)

```bash
# Use AWS X-Ray and CloudWatch
export OTEL_TRACES_EXPORTER=xray
export OTEL_METRICS_EXPORTER=cloudwatch
export OTEL_EXPORTER_XRAY_REGION=us-east-1

# Run YAWL
./scripts/run-with-otel.sh
```

View traces in AWS Console → X-Ray
View metrics in AWS Console → CloudWatch

### Microsoft Azure

```bash
# Use Azure Monitor
export OTEL_TRACES_EXPORTER=azuremonitor
export OTEL_METRICS_EXPORTER=azuremonitor
export AZURE_CONNECTION_STRING="InstrumentationKey=..."

# Run YAWL
./scripts/run-with-otel.sh
```

View telemetry in Azure Portal → Application Insights

---

## Verify Everything is Working

### Check 1: Metrics Endpoint

```bash
curl http://localhost:9464/metrics | grep -E "yawl_|otelcol_"

# Should see metrics like:
# yawl_cases_active
# yawl_case_started_total
# otelcol_process_uptime
```

### Check 2: OpenTelemetry Collector Health

```bash
curl http://localhost:13133/health

# Should return: {"status":"Server available"}
```

### Check 3: Traces in Jaeger

```bash
# Query Jaeger API
curl "http://localhost:16686/api/traces?service=yawl-engine&limit=10"

# Should return JSON with trace data
```

### Check 4: Prometheus Targets

```bash
# Check if Prometheus is scraping YAWL
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job == "yawl-engine")'
```

---

## Troubleshooting

### Problem: No traces appearing in Jaeger

**Solution:**
```bash
# Check if collector is receiving traces
curl http://localhost:8888/metrics | grep otelcol_receiver_accepted_spans

# Enable debug logging
export OTEL_JAVAAGENT_DEBUG=true
./scripts/run-with-otel-dev.sh

# Check sampling (may be filtering out traces)
export OTEL_TRACES_SAMPLER=always_on
```

### Problem: High CPU/Memory usage

**Solution:**
```bash
# Reduce sampling
export OTEL_TRACES_SAMPLER_ARG=0.01  # 1% sampling

# Increase batch delays
export OTEL_BSP_SCHEDULE_DELAY=10000  # 10 seconds

# Reduce max queue size
export OTEL_BSP_MAX_QUEUE_SIZE=2048
```

### Problem: Collector not starting

**Solution:**
```bash
# Check collector logs
docker logs otel-collector

# Verify configuration
docker exec otel-collector otelcol validate --config=/etc/otel-collector-config.yaml

# Check port conflicts
netstat -tuln | grep -E "4317|4318|8888"
```

---

## Next Steps

1. **Import Dashboards**: Load YAWL-specific Grafana dashboards from `observability/dashboards/`
2. **Set Up Alerts**: Configure Prometheus alerts from `observability/monitoring/prometheus/alerting-rules.yaml`
3. **Explore Queries**: Try example queries from `observability/opentelemetry/QUERIES.md`
4. **Customize Instrumentation**: Add YAWL-specific spans and metrics using `YAWLTelemetry` API
5. **Production Tuning**: Adjust sampling, batching, and resource limits for your workload

## Resources

- Full Documentation: `observability/opentelemetry/README.md`
- Query Examples: `observability/opentelemetry/QUERIES.md`
- OpenTelemetry Docs: https://opentelemetry.io/docs/
- Grafana Tutorials: https://grafana.com/tutorials/

---

**Questions or Issues?**
- Check the logs: `docker-compose -f docker-compose-otel.yml logs`
- Enable debug mode: `export OTEL_JAVAAGENT_DEBUG=true`
- Review collector config: `observability/tracing/opentelemetry/collector.yaml`
