# YAWL OpenTelemetry Integration

Comprehensive observability for YAWL workflow engine using OpenTelemetry, providing production-grade traces, metrics, and logs across all cloud platforms.

## Overview

This integration provides **two complementary approaches** to observability:

1. **Zero-Code Instrumentation** (Java Agent) - Primary approach, works out-of-the-box
2. **Manual Instrumentation** (Spring Boot Starter) - Optional, for deeper customization

### Why Two Approaches?

- **Java Agent**: Automatic instrumentation of JVM, HTTP, JDBC, Hibernate, etc. No code changes required.
- **Spring Boot Starter**: Enables custom YAWL-specific metrics and traces for workflow operations.

Both approaches work together seamlessly and export to the same backends.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ YAWL Engine Application                                     │
│                                                              │
│  ┌──────────────────────┐  ┌─────────────────────────────┐ │
│  │ OpenTelemetry Agent  │  │ YAWLTelemetry (Manual)      │ │
│  │ (Zero-Code)          │  │ - YEngine metrics           │ │
│  │ - HTTP/Servlet       │  │ - YNetRunner traces         │ │
│  │ - JDBC/Hibernate     │  │ - Case/WorkItem metrics     │ │
│  │ - Log4j              │  │ - Custom attributes         │ │
│  └──────────┬───────────┘  └─────────────┬───────────────┘ │
│             │                             │                 │
│             └─────────────┬───────────────┘                 │
│                           ▼                                 │
│              ┌────────────────────────┐                     │
│              │ OpenTelemetry SDK      │                     │
│              │ - Batching             │                     │
│              │ - Sampling             │                     │
│              │ - Context propagation  │                     │
│              └────────────┬───────────┘                     │
└──────────────────────────┼──────────────────────────────────┘
                           │
                           ▼
            ┌──────────────────────────┐
            │ OpenTelemetry Collector  │
            │ - Receives OTLP          │
            │ - Processes/Filters      │
            │ - Multi-backend export   │
            └──────────┬───────────────┘
                       │
      ┌────────────────┼────────────────┐
      ▼                ▼                ▼
┌──────────┐   ┌──────────┐    ┌───────────┐
│ Jaeger   │   │Prometheus│    │ Cloud     │
│ (Traces) │   │(Metrics) │    │ Backends  │
└──────────┘   └──────────┘    └───────────┘
      │                ▼                │
      └────────────►Grafana◄────────────┘
                 (Dashboards)
```

## Quick Start

### 1. Zero-Code Instrumentation (Recommended for Getting Started)

No code changes required. Just run YAWL with the OpenTelemetry Java agent:

```bash
# Build and download the agent
mvn clean package

# Run YAWL with OpenTelemetry (production mode)
./scripts/run-with-otel.sh

# Or development mode (outputs to logs)
./scripts/run-with-otel-dev.sh
```

The agent automatically instruments:
- HTTP requests (Servlet, JAX-RS)
- Database queries (JDBC, Hibernate)
- Async operations (ExecutorService, Thread pools)
- Logging (Log4j2)

### 2. Manual Instrumentation (Optional)

For deeper YAWL-specific observability, enable the Spring Boot starter:

**application.properties:**
```properties
# Enable YAWL observability features
yawl.observability.enabled=true

# Configure exporters
yawl.observability.exporter.type=otlp
yawl.observability.otlp.endpoint=http://otel-collector:4317
```

This adds YAWL-specific metrics:
- `yawl.case.started` - Cases started counter
- `yawl.case.completed` - Cases completed counter
- `yawl.case.duration` - Case execution duration histogram
- `yawl.workitem.created` - Work items created counter
- `yawl.workitem.duration` - Work item execution duration
- `yawl.cases.active` - Active cases gauge
- `yawl.workitems.active` - Active work items gauge
- `yawl.tasks.enabled` - Enabled tasks gauge
- `yawl.tasks.busy` - Busy tasks gauge

## Configuration

### Java Agent Configuration

**Production Configuration** (`agent-config.properties`):
```properties
otel.service.name=yawl-engine
otel.service.version=5.2
otel.exporter.otlp.endpoint=http://otel-collector:4318
otel.traces.sampler=parentbased_traceidratio
otel.traces.sampler.arg=0.1
otel.metrics.exporter=otlp,prometheus
```

**Development Configuration** (`agent-config-dev.properties`):
```properties
otel.service.name=yawl-engine-dev
otel.traces.exporter=logging
otel.metrics.exporter=logging,prometheus
otel.traces.sampler=always_on
otel.javaagent.debug=true
```

### Environment Variables

Override configuration via environment variables:

```bash
# Service identification
export OTEL_SERVICE_NAME=yawl-engine
export OTEL_SERVICE_VERSION=5.2
export OTEL_RESOURCE_ATTRIBUTES="deployment.environment=production"

# Exporter configuration
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
export OTEL_TRACES_SAMPLER=parentbased_traceidratio
export OTEL_TRACES_SAMPLER_ARG=0.1

# Prometheus metrics
export OTEL_EXPORTER_PROMETHEUS_PORT=9464
```

### Spring Boot Configuration

Copy the appropriate configuration file:

```bash
# Production
cp observability/opentelemetry/application-otel.properties \
   src/main/resources/application-otel.properties

# Development
cp observability/opentelemetry/application-otel-dev.properties \
   src/main/resources/application-otel-dev.properties
```

Activate the profile:
```bash
java -jar yawl.war --spring.profiles.active=otel
```

## Deployment

### Kubernetes Deployment

The Java agent is automatically injected via init container:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
spec:
  template:
    spec:
      initContainers:
      - name: otel-agent-downloader
        image: curlimages/curl:latest
        command:
        - sh
        - -c
        - |
          curl -L -o /otel-agent/opentelemetry-javaagent.jar \
          https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.2.0/opentelemetry-javaagent.jar
        volumeMounts:
        - name: otel-agent
          mountPath: /otel-agent

      containers:
      - name: yawl-engine
        image: yawl-engine:5.2
        env:
        - name: JAVA_TOOL_OPTIONS
          value: "-javaagent:/otel-agent/opentelemetry-javaagent.jar"
        - name: OTEL_SERVICE_NAME
          value: "yawl-engine"
        - name: OTEL_EXPORTER_OTLP_ENDPOINT
          value: "http://otel-collector:4317"
        - name: OTEL_RESOURCE_ATTRIBUTES
          value: "deployment.environment=production"
        volumeMounts:
        - name: otel-agent
          mountPath: /otel-agent

      volumes:
      - name: otel-agent
        emptyDir: {}
```

### Docker Deployment

```dockerfile
FROM tomcat:10-jdk21

# Download OpenTelemetry agent
RUN curl -L -o /opt/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.2.0/opentelemetry-javaagent.jar

# Copy YAWL application
COPY yawl.war /usr/local/tomcat/webapps/

# Configure OpenTelemetry
ENV JAVA_OPTS="-javaagent:/opt/opentelemetry-javaagent.jar"
ENV OTEL_SERVICE_NAME=yawl-engine
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

EXPOSE 8080 9464
```

### Docker Compose

```yaml
version: '3.8'

services:
  yawl-engine:
    image: yawl-engine:5.2
    environment:
      JAVA_OPTS: "-javaagent:/opt/opentelemetry-javaagent.jar"
      OTEL_SERVICE_NAME: yawl-engine
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
      OTEL_RESOURCE_ATTRIBUTES: deployment.environment=production
    ports:
      - "8080:8080"
      - "9464:9464"
    depends_on:
      - otel-collector

  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./observability/tracing/opentelemetry/collector.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4317:4317"  # OTLP gRPC
      - "4318:4318"  # OTLP HTTP
      - "8888:8888"  # Prometheus metrics (collector self-monitoring)

  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"  # UI
      - "14250:14250"  # gRPC

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./observability/monitoring/prometheus/values.yaml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    volumes:
      - ./observability/dashboards:/etc/grafana/provisioning/dashboards
    ports:
      - "3000:3000"
```

## Cloud Platform Integration

### Google Cloud Platform

```properties
# Use Google Cloud Trace exporter
otel.traces.exporter=google_cloud_trace
otel.exporter.google.cloud.trace.project-id=${GCP_PROJECT_ID}

# Use Google Cloud Monitoring for metrics
otel.metrics.exporter=google_cloud_monitoring
```

### Amazon Web Services

```properties
# Use AWS X-Ray exporter
otel.traces.exporter=xray
otel.exporter.xray.region=us-east-1

# Use CloudWatch for metrics
otel.metrics.exporter=cloudwatch
```

### Microsoft Azure

```properties
# Use Azure Monitor exporter
otel.traces.exporter=azuremonitor
otel.exporter.azuremonitor.connection.string=${AZURE_CONNECTION_STRING}
```

## Observability Features

### Traces

Automatically captured:
- HTTP request/response traces
- Database query traces (JDBC/Hibernate)
- Async operation traces
- YAWL-specific operation traces (with manual instrumentation)

Custom YAWL traces:
- `ExecuteCase` - Full case execution trace
- `StartWorkItem` - Work item start operation
- `CompleteWorkItem` - Work item completion
- `CancelCase` - Case cancellation
- `NetRunner.continueExecutionOnNet` - Net execution cycle

### Metrics

Automatically collected:
- JVM metrics (heap, GC, threads)
- HTTP metrics (request rate, duration, errors)
- Database connection pool metrics
- Thread pool metrics

YAWL-specific metrics:
- Case lifecycle metrics (started, completed, failed, cancelled)
- Work item metrics (created, started, completed, failed)
- Case/work item duration histograms
- Active cases/work items gauges
- Task state metrics (enabled, busy)
- Engine operation duration

### Logs

Automatic log correlation:
- Trace ID and Span ID automatically injected into logs
- Structured logging with MDC context
- Log export to OpenTelemetry backends

## Monitoring and Alerting

### Dashboards

Import the pre-built Grafana dashboard:
```bash
# Import YAWL OpenTelemetry dashboard
curl -X POST http://grafana:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @observability/dashboards/yawl-otel-dashboard.json
```

### Alerts

Deploy Prometheus alerting rules:
```bash
kubectl apply -f observability/monitoring/prometheus/alerting-rules.yaml
```

See [QUERIES.md](./QUERIES.md) for comprehensive query examples.

## Performance Tuning

### Sampling Configuration

**Production (10% sampling):**
```properties
otel.traces.sampler=parentbased_traceidratio
otel.traces.sampler.arg=0.1
```

**High-volume production (1% sampling):**
```properties
otel.traces.sampler=parentbased_traceidratio
otel.traces.sampler.arg=0.01
```

**Development (100% sampling):**
```properties
otel.traces.sampler=always_on
```

### Batch Processing

```properties
# Batch span processor configuration
otel.bsp.schedule.delay=5000          # 5 seconds
otel.bsp.max.queue.size=8192
otel.bsp.max.export.batch.size=512
otel.bsp.export.timeout=30000         # 30 seconds
```

### Resource Limits

```properties
# Memory limiter (in agent config)
otel.javaagent.extensions.memory.limit=512
```

## Troubleshooting

### Enable Debug Logging

```bash
export OTEL_JAVAAGENT_DEBUG=true
export OTEL_JAVAAGENT_LOGGING=application
java -javaagent:opentelemetry-javaagent.jar -jar yawl.war
```

### Verify Instrumentation

```bash
# Check if agent loaded successfully
curl http://localhost:9464/metrics | grep otel

# Check OpenTelemetry collector
curl http://localhost:13133/health

# Check Prometheus metrics
curl http://localhost:9464/metrics | grep yawl
```

### Common Issues

**Agent not loading:**
- Verify `JAVA_TOOL_OPTIONS` or `-javaagent` flag is set
- Check agent JAR file exists and is readable
- Enable debug logging to see agent initialization

**No traces appearing:**
- Verify OTLP endpoint is reachable
- Check sampling configuration (may be sampling out all traces)
- Verify collector is running and receiving data

**High overhead:**
- Reduce sampling ratio
- Disable specific instrumentations if not needed
- Increase batch delays

## Best Practices

### Production Deployment

1. **Use tail-based sampling** in the collector for intelligent trace retention
2. **Set appropriate sampling ratios** (5-10% for most workloads)
3. **Monitor the collector** itself using its Prometheus endpoint
4. **Use TLS** for production OTLP endpoints
5. **Set resource limits** to prevent memory issues

### Development

1. **Use logging exporters** to avoid external dependencies
2. **Sample all traces** for complete visibility
3. **Enable debug logging** to understand instrumentation behavior

### Monitoring

1. **Create SLOs** for critical YAWL operations
2. **Set up alerts** for high failure rates and slow operations
3. **Correlate metrics with traces** to diagnose issues
4. **Use exemplars** to link metrics to traces

## Native Image Compatibility

For GraalVM native-image deployments:

```properties
# Use programmatic configuration instead of agent
yawl.observability.enabled=true
otel.sdk.disabled=false
```

The Spring Boot starter is native-image compatible. The Java agent cannot be used with native images.

## Security Considerations

### Authentication

```properties
# OTLP with authentication
otel.exporter.otlp.headers=Authorization=Bearer ${OTEL_AUTH_TOKEN}
```

### TLS/mTLS

```properties
# Enable TLS
otel.exporter.otlp.endpoint=https://otel-collector:4317

# Mutual TLS
otel.exporter.otlp.certificate=/path/to/client-cert.pem
otel.exporter.otlp.client.key=/path/to/client-key.pem
otel.exporter.otlp.client.certificate=/path/to/ca-cert.pem
```

## References

- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [OpenTelemetry Java Agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)
- [YAWL Queries](./QUERIES.md)

## License

This integration follows the same license as YAWL (LGPL v3).
