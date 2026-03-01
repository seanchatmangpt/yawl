# YAWL v6.0.0 Comprehensive Observability & Monitoring Infrastructure

## Overview

This directory contains production-grade observability infrastructure for YAWL v6.0.0, providing comprehensive distributed tracing, metrics collection, structured logging, health checks, and **Actor System Monitoring** for actor-based workflow systems.

## What's Included

### Core Components

1. **OpenTelemetry Instrumentation** (`OpenTelemetryInitializer.java`, `WorkflowSpanBuilder.java`)
   - Multi-exporter support (Jaeger, Tempo, OTLP/gRPC)
   - Workflow-specific span hierarchy
   - Automatic resource attributes
   - Sampling strategies

2. **Prometheus Metrics** (`YawlMetrics.java`)
   - Case lifecycle metrics (created, completed, failed)
   - Task execution metrics
   - Engine queue and threadpool metrics
   - Custom histogram percentiles (P50, P95, P99)
   - Micrometer integration

3. **Actor Monitoring System** (`src/org/yawlfoundation/yawl/observability/actor/`)
   - **ActorHealthMetrics**: Real-time actor health and performance tracking
   - **ActorTracer**: Distributed tracing for message flows between actors
   - **ActorAlertManager**: Configurable alerting with thresholds and notifications
   - **ActorDashboardData**: Real-time data for monitoring dashboards
   - **ActorAnomalyDetector**: Advanced anomaly detection algorithms
   - **ActorObservabilityService**: Main orchestration service for actor monitoring

4. **Structured Logging** (`StructuredLogger.java`)
   - JSON log format for ELK/Loki aggregation
   - Correlation ID propagation via MDC
   - Log level routing (ERROR → PagerDuty, DEBUG → local)
   - Automatic exception serialization

5. **Health Checks** (`HealthCheckEndpoint.java`)
   - Kubernetes-compatible probes (liveness, readiness, startup)
   - Subsystem status monitoring
   - JSON health responses
   - HTTP status code mapping

### Configuration Files

- **prometheus-alerts.yml** - 20+ alert rules for SLO violations and operational issues
- **alertmanager-config.yml** - Intelligent alert routing (PagerDuty, Slack)
- **prometheus-scrape-config.yml** - Multi-target scrape configuration
- **loki-config.yaml** - Log aggregation with retention policies
- **grafana-dashboard-yawl-overview.json** - Production dashboard template
- **actor-monitoring-config.yaml** - Actor monitoring configuration
- **actor-alerting-rules.yml** - Actor-specific alert rules
- **actor-dashboard.json** - Actor monitoring dashboard
- **actor-docker-compose.yml** - Complete monitoring stack for actors
- **otel-collector-config.yaml** - OpenTelemetry collector configuration

### Documentation

- **OBSERVABILITY_DESIGN.md** - Complete architectural design (9+ sections)
- **IMPLEMENTATION_GUIDE.md** - Step-by-step integration guide with code examples
- **This README** - Quick reference and overview

### Tests

- **ObservabilityTest.java** - Comprehensive test suite validating all components
- **Actor*Test.java** - Actor monitoring system tests
- **Mock data generator** - Test data generation for actor scenarios

## Quick Start

### 1. Add Dependency

In your module's `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-monitoring</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. Initialize Observability

```java
// At application startup
OpenTelemetryInitializer.initialize();

// Initialize metrics
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
YawlMetrics.initialize(registry);

// Initialize Actor Monitoring System
ActorObservabilityService.initialize(registry);

// Set up health checks
HealthCheckEndpoint health = new HealthCheckEndpoint(delegate);
```

### 3. Add Tracing

```java
Tracer tracer = OpenTelemetryInitializer.getTracer("yawl.engine");

Span span = WorkflowSpanBuilder.create(tracer, "case.execute")
    .withCaseId("case-123")
    .withSpecificationId("spec-456")
    .start();

try (Scope scope = span.makeCurrent()) {
    // Case execution logic
} finally {
    span.end();
}
```

### 4. Record Actor Metrics

```java
// Basic actor lifecycle tracking
ActorObservabilityService service = ActorObservabilityService.getInstance();
service.recordActorCreated("worker-1", "worker");
service.recordMessageProcessing("worker-1", "task", 1000000L, 1024L);
service.updateQueueDepth("worker-1", 25);
service.updateMemoryUsage("worker-1", 2048L);

// Advanced metrics recording
Map<String, Double> metrics = Map.of(
    "processing_time", 100.0,
    "error_rate", 0.05,
    "memory_usage", 2048.0
);
service.recordMetricsForAnomalyDetection("worker-1", metrics);

// Distributed tracing
ActorTracer.ActorSpan span = service.startMessageFlow(
    "flow-123", "worker-1", "worker-2", "task", Map.of()
);
// ... process message ...
service.finishMessageFlow(span.getSpanId(), Duration.ofMillis(100));
```

### 5. Get Monitoring Data

```java
// Get dashboard data
ActorDashboardData.DashboardOverview overview = service.getDashboardOverview();
ActorDashboardData.PerformanceDashboard perfDashboard = service.getPerformanceDashboard();

// Get real-time metrics
Map<String, Object> realtime = service.getRealTimeMetrics();

// Get anomaly detection results
ActorAnomalyDetector.AnomalyStatistics anomalyStats = service.getAnomalyStatistics();
```

### 5. Add Structured Logging

```java
StructuredLogger log = StructuredLogger.getLogger(MyClass.class);

Map<String, Object> fields = new HashMap<>();
fields.put("case_id", "case-123");
fields.put("activity", "review");
log.info("Activity started", fields);
```

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                   YAWL v6.0.0 Application               │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌────────────────┐  ┌─────────────┐  ┌──────────────┐  │
│  │   YEngine      │  │  YNetRunner │  │  YActivity   │  │
│  │                │  │             │  │              │  │
│  │ • incrementCase│  │ • spanBuilde│  │ • spanBuilde│  │
│  │ • recordMetric │  │ • metrics   │  │ • logging    │  │
│  │ • structured   │  │ • logging   │  │              │  │
│  │   logging      │  │             │  │              │  │
│  └────────────────┘  └─────────────┘  └──────────────┘  │
│         │                   │                   │         │
│         └───────────────────┼───────────────────┘         │
│                             │                             │
│  ┌──────────────────────────┼──────────────────────────┐  │
│  │            Observability Infrastructure              │  │
│  ├──────────────────────────┼──────────────────────────┤  │
│  │                          │                           │  │
│  │  ┌──────────────┐  ┌────▼────────┐  ┌────────────┐ │  │
│  │  │ OpenTelemetry│  │  Micrometer  │  │  SLF4J     │ │  │
│  │  │              │  │  Prometheus  │  │  Structured│ │  │
│  │  │ • Tracing    │  │              │  │  Logging   │ │  │
│  │  │ • Spans      │  │ • Counters   │  │            │ │  │
│  │  │ • Exporters  │  │ • Gauges     │  │ • JSON     │ │  │
│  │  │              │  │ • Histograms │  │ • MDC      │ │  │
│  │  └──────────────┘  └──────────────┘  └────────────┘ │  │
│  │         │                  │                  │       │  │
│  │  ┌──────▼────────────────────▼────────────────▼────┐  │  │
│  │  │         Health Check Endpoint                    │  │  │
│  │  │  • /health/live   (liveness probe)             │  │  │
│  │  │  • /health/ready  (readiness probe)            │  │  │
│  │  │  • /health/startup (startup probe)             │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                           │
└─────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
    ┌────────┐          ┌────────┐         ┌────────┐
    │ Jaeger │          │Prometheus       │ Loki   │
    │  UI    │          │Grafana          │Kibana  │
    │        │          │AlertManager     │        │
    └────────┘          └────────┘         └────────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                        ┌───▼───┐
                        │ Slack │
                        │ Pager  │
                        │ Duty   │
                        └────────┘
```

## Key Metrics

### Case Execution
- **yawl_case_created_total** - Cases created
- **yawl_case_completed_total** - Cases completed
- **yawl_case_failed_total** - Cases failed
- **yawl_case_duration_seconds** - Case execution time (P50/P95/P99)
- **yawl_case_active** - Currently active cases

### Task Execution
- **yawl_task_executed_total** - Tasks executed
- **yawl_task_failed_total** - Task failures
- **yawl_task_duration_seconds** - Task time (P50/P95/P99)
- **yawl_task_pending** - Pending tasks in queue

### Engine Health
- **yawl_engine_queue_depth** - Work queue depth
- **yawl_engine_threadpool_active_workers** - Active threads
- **yawl_engine_threadpool_queue_size** - Thread pool queue
- **yawl_engine_request_latency_seconds** - Request latency
- **yawl_engine_memory_usage_bytes** - Memory consumption

### Database
- **yawl_db_connection_pool_active** - Active connections
- **yawl_db_connection_pool_idle** - Idle connections
- **yawl_db_query_duration_seconds** - Query latency
- **yawl_db_transaction_duration_seconds** - Transaction time

## Service Level Objectives (SLOs)

| SLO | Target | Alert | Duration |
|-----|--------|-------|----------|
| Case Availability | 99.9% | < 99% | 5 min |
| Case Latency P95 | < 5s | > 7s | 5 min |
| Case Latency P99 | < 10s | > 15s | 5 min |
| Task Throughput | > 100/min | < 80/min | 5 min |
| Engine Health | 99.5% | < 99% | 2 min |

## Alert Severity Levels

- **CRITICAL** (5 min response) → PagerDuty page
- **WARNING** (15 min response) → Slack #yawl-alerts
- **INFO** (N/A) → Dashboard only

### Critical Alerts
- Queue depth > 5000 items
- No active workers
- Database pool exhausted
- Pod OOMKilled
- CrashLoopBackOff

### Warning Alerts
- Case latency P95 > 7s
- Task throughput < 80/min
- Memory usage > 85%
- CPU usage > 80%
- Error rate > 5%

## Configuration

### System Properties

```bash
# OpenTelemetry
-Dotel.exporter.otlp.endpoint=http://localhost:4317
-Dotel.exporter.jaeger.endpoint=http://localhost:14268/api/traces
-Dotel.service.name=yawl-engine
-Dotel.service.version=6.0.0
-Dotel.resource.attributes=environment=production,region=us-west-2

# Logging
-Dlog.level=INFO
```

### Environment Variables

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
export OTEL_SERVICE_NAME=yawl-engine-prod
export OTEL_RESOURCE_ATTRIBUTES=environment=production
```

### Spring Boot (application.properties)

```properties
otel.exporter.otlp.endpoint=http://otel-collector:4317
otel.service.name=yawl-engine

management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.metrics.export.prometheus.enabled=true

logging.level.root=INFO
logging.level.org.yawlfoundation.yawl=DEBUG
```

## Health Probes

### Kubernetes Configuration

```yaml
startupProbe:
  httpGet:
    path: /health/startup
    port: 8080
  failureThreshold: 30
  periodSeconds: 1

readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
  failureThreshold: 3
  periodSeconds: 5

livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
  failureThreshold: 3
  periodSeconds: 10
```

### Health Endpoint Response

```json
{
  "status": "UP",
  "timestamp": "2026-02-17T12:34:56Z",
  "uptime": 3600000,
  "details": {
    "database_healthy": true,
    "queue_healthy": true,
    "active_workers": 8,
    "queue_depth": 23,
    "jvm_memory_available": 523456789
  }
}
```

## Deployment Options

### Docker Compose (Dev/Staging)

```bash
docker-compose -f observability/docker-compose.yml up
```

Provides:
- YAWL Engine + Postgres
- OpenTelemetry Collector
- Jaeger (tracing UI)
- Prometheus + Grafana
- Loki
- AlertManager

### Kubernetes (Production)

See `observability/kubernetes-deployment.yaml` for:
- YAWL Deployment with probes
- OpenTelemetry Collector
- Prometheus Operator
- Grafana with dashboards
- Loki StatefulSet
- AlertManager

### Helm Chart

```bash
helm install yawl ./yawl-monitoring-helm \
  --set otel.collector.endpoint=otel-collector:4317 \
  --set prometheus.retention=30d \
  --set grafana.adminPassword=<secret>
```

## Monitoring Dashboards

### YAWL Overview
- Case throughput (created/completed/failed)
- Case latency percentiles (P50/P95/P99)
- Queue depth and utilization
- Active cases and workers
- Error rate

### Case Execution Flow
- Cases created vs completed
- State distribution timeline
- Top failing activities
- Resource allocation

### Performance & Resources
- CPU and memory usage
- GC pause duration
- Database query latency
- Network I/O

### SLO Compliance
- Availability tracking
- Latency compliance
- Error rate trends
- Alert state

## Testing

### Run Test Suite

```bash
mvn clean test -Dtest=ObservabilityTest
```

### Manual Testing

```bash
# Check health endpoints
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
curl http://localhost:8080/health/startup

# Scrape metrics
curl http://localhost:8080/actuator/prometheus | grep yawl_

# Query Prometheus
curl 'http://localhost:9090/api/v1/query?query=yawl_case_created_total'

# Check traces in Jaeger
curl http://localhost:16686/api/traces?service=yawl-engine

# Query logs in Loki
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={job="yawl-engine"}'
```

## Performance Impact

- **OpenTelemetry overhead**: 2-5% with sampling
- **Metrics collection**: < 1% overhead
- **Structured logging**: Minimal (async by default)
- **Health checks**: < 1ms latency

## Troubleshooting

See **OBSERVABILITY_DESIGN.md** section 9 (Production Runbooks) for:
- High queue depth
- Case latency spikes
- No active workers
- Memory pressure
- Database performance degradation

## References

### Documentation
- [OBSERVABILITY_DESIGN.md](./OBSERVABILITY_DESIGN.md) - Complete architecture
- [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) - Integration examples

### External Resources
- [OpenTelemetry Java Instrumentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Prometheus Operator](https://prometheus-operator.dev/)
- [Grafana Loki](https://grafana.com/docs/loki/)
- [Google SRE Book - SLOs](https://sre.google/books/)

## Support

For issues or questions:
1. Check IMPLEMENTATION_GUIDE.md troubleshooting section
2. Review OBSERVABILITY_DESIGN.md runbooks
3. Check component test cases in ObservabilityTest.java
4. File issue in YAWL repository

## License

Licensed under the same terms as YAWL Foundation.
