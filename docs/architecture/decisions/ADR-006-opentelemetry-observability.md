# ADR-006: OpenTelemetry Observability

## Status
**ACCEPTED**

## Context

YAWL requires comprehensive observability for production monitoring, troubleshooting, and performance analysis. The system must provide:

### Business Drivers

1. **Operational Visibility**
   - Real-time workflow execution monitoring
   - Performance bottleneck identification
   - Error rate tracking and alerting
   - Resource utilization metrics

2. **Distributed Tracing**
   - Cross-service request tracing (engine, resource service, worklet service)
   - External service call tracking
   - Case lifecycle visibility
   - Work item processing latency

3. **Compliance Requirements**
   - Audit trail for workflow decisions
   - Performance SLA monitoring
   - Resource accountability
   - Incident post-mortem analysis

### Technical Constraints

1. **Observability Standards**
   - Need vendor-neutral instrumentation
   - Support for multiple backends (Jaeger, Prometheus, Grafana)
   - Low-overhead data collection
   - Standard semantic conventions

2. **Integration Points**
   - Spring Boot 3.4+ native OTel support
   - Hibernate 6.5 SQL tracing
   - Tomcat request tracking
   - Custom YAWL metrics

## Decision

**We will implement OpenTelemetry (OTel) as the unified observability framework for YAWL v6.0+.**

### Architecture

```
+------------------------------------------------------------------+
|                     YAWL Services                                 |
|  +-------------+  +-------------+  +-------------+               |
|  |   Engine    |  |  Resource   |  |  Worklet    |               |
|  |   Service   |  |   Service   |  |   Service   |               |
|  +------+------+  +------+------+  +------+------+               |
|         |                |                |                      |
|         +--------+-------+--------+-------+                      |
|                  |                |                              |
|          +-------v-------+  +-----v-----+                        |
|          | OTel SDK      |  | OTel SDK  |                        |
|          | (Instrument)  |  | (Export)  |                        |
|          +-------+-------+  +-----+-----+                        |
|                  |                |                              |
+------------------+----------------+------------------------------+
                   |                |
          +--------v--------+ +----v----+
          | OTLP Collector  | |  OTLP   |
          | (otel-collector)| | Protocol|
          +--------+--------+ +----+----+
                   |               |
      +------------+------+--------+--------+
      |            |      |        |        |
  +---v---+   +----v---+ +-v--+  +-v--+  +-v--+
  | Jaeger|   |Prometheus|Grafana|Loki|Tempo|
  +-------+   +---------+------+----+----+----+
```

### OTLP Export Configuration

#### application.yml

```yaml
management:
  opentelemetry:
    enabled: true
    resource:
      attributes:
        service.name: ${spring.application.name}
        service.version: ${yawl.version:6.0.0}
        deployment.environment: ${DEPLOY_ENV:production}
    tracing:
      enabled: true
      export:
        otlp:
          endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector:4317}
          timeout: 10s
          compression: gzip
    metrics:
      enabled: true
      export:
        otlp:
          endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector:4317}
          interval: 60s

  tracing:
    sampling:
      probability: ${OTEL_TRACES_SAMPLER_ARG:0.1}  # 10% sampling by default

  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

#### Environment Variables

```bash
# OTLP Endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

# Service Identity
OTEL_SERVICE_NAME=yawl-engine
OTEL_RESOURCE_ATTRIBUTES=service.version=6.0.0,deployment.environment=production

# Sampling
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1

# Metrics
OTEL_METRIC_EXPORT_INTERVAL=60000
OTEL_METRIC_EXPORT_TIMEOUT=30000

# Logging
OTEL_LOGS_EXPORTER=otlp
```

### YAWL-Specific Metrics

#### Workflow Metrics

```java
// Case metrics
public static final Counter CASES_STARTED = Counter.builder("yawl_cases_started")
    .description("Total number of workflow cases started")
    .tag("spec_id", "specIdentifier")
    .register(meterProvider);

public static final Counter CASES_COMPLETED = Counter.builder("yawl_cases_completed")
    .description("Total number of workflow cases completed")
    .tag("spec_id", "specIdentifier")
    .tag("status", "completed|cancelled|failed")
    .register(meterProvider);

public static final Gauge ACTIVE_CASES = Gauge.builder("yawl_active_cases")
    .description("Number of currently active workflow cases")
    .register(meterProvider);

// Work item metrics
public static final Histogram WORK_ITEM_DURATION = Histogram.builder("yawl_work_item_duration")
    .description("Time to complete a work item")
    .baseUnit("milliseconds")
    .tag("task_id", "taskIdentifier")
    .register(meterProvider);

public static final Counter WORK_ITEMS_CHECKED_OUT = Counter.builder("yawl_work_items_checked_out")
    .description("Total work items checked out")
    .tag("task_id", "taskIdentifier")
    .register(meterProvider);
```

#### Engine Metrics

```java
// Engine health
public static final Gauge ENGINE_UPTIME = Gauge.builder("yawl_engine_uptime_seconds")
    .description("Engine uptime in seconds")
    .register(meterProvider);

public static final Gauge SPECIFICATIONS_LOADED = Gauge.builder("yawl_specifications_loaded")
    .description("Number of loaded specifications")
    .register(meterProvider);

// Performance
public static final Histogram CASE_LAUNCH_LATENCY = Histogram.builder("yawl_case_launch_latency")
    .description("Time to launch a new case")
    .baseUnit("milliseconds")
    .register(meterProvider);
```

### Distributed Tracing Integration

#### Span Attributes (Semantic Conventions)

```java
// Case spans
Span caseSpan = tracer.spanBuilder("yawl.case.execute")
    .setAttribute("yawl.case.id", caseId)
    .setAttribute("yawl.spec.id", specId)
    .setAttribute("yawl.spec.version", version)
    .startSpan();

// Work item spans
Span workItemSpan = tracer.spanBuilder("yawl.workitem.process")
    .setAttribute("yawl.workitem.id", workItemId)
    .setAttribute("yawl.case.id", caseId)
    .setAttribute("yawl.task.id", taskId)
    .startSpan();
```

#### Automatic Instrumentation

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <version>2.10.0-alpha</version>
</dependency>
```

### Backend Configuration

#### Jaeger (Distributed Tracing)

```yaml
# docker-compose.yml
jaeger:
  image: jaegertracing/all-in-one:1.62
  environment:
    - COLLECTOR_OTLP_ENABLED=true
  ports:
    - "16686:16686"  # UI
    - "4317:4317"    # OTLP gRPC
    - "4318:4318"    # OTLP HTTP
```

#### Prometheus (Metrics)

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'yawl-engine'
    static_configs:
      - targets: ['yawl-engine:8080']
    metrics_path: '/actuator/prometheus'
```

#### Grafana Dashboard

```json
{
  "dashboard": {
    "title": "YAWL Engine Overview",
    "panels": [
      {
        "title": "Active Cases",
        "type": "gauge",
        "targets": [{"expr": "yawl_active_cases"}]
      },
      {
        "title": "Case Throughput",
        "type": "graph",
        "targets": [{"expr": "rate(yawl_cases_completed_total[5m])"}]
      },
      {
        "title": "Work Item Latency P99",
        "type": "stat",
        "targets": [{"expr": "histogram_quantile(0.99, yawl_work_item_duration_bucket)"}]
      }
    ]
  }
}
```

## Consequences

### Positive

1. **Vendor Neutrality**
   - Switch backends without code changes
   - Standard protocol (OTLP) support
   - Multi-backend support (traces + metrics + logs)

2. **Comprehensive Visibility**
   - End-to-end request tracing
   - Performance metrics collection
   - Error tracking and alerting

3. **Low Overhead**
   - Sampling reduces data volume
   - Async export non-blocking
   - Efficient binary protocol (gRPC)

4. **Spring Boot Integration**
   - Native OTel support in Spring Boot 3.4+
   - Auto-configuration
   - Actuator endpoints

### Negative

1. **Infrastructure Requirements**
   - OTLP Collector deployment
   - Storage backends (Jaeger, Prometheus)
   - Grafana for visualization

2. **Learning Curve**
   - OTel concepts (spans, metrics, logs)
   - Semantic conventions
   - Backend configuration

3. **Performance Impact**
   - Minimal but measurable overhead (1-3%)
   - Memory for trace buffering
   - Network bandwidth for export

## Implementation Roadmap

### Phase 1: Core Instrumentation (Week 1-2)
- Add OTel dependencies
- Configure OTLP export
- Instrument case lifecycle
- Instrument work item processing

### Phase 2: Metrics Collection (Week 3-4)
- Define YAWL-specific metrics
- Implement metric collection
- Configure Prometheus scrape
- Create Grafana dashboards

### Phase 3: Distributed Tracing (Week 5-6)
- Add span instrumentation
- Configure context propagation
- Set up Jaeger backend
- Create trace analysis queries

### Phase 4: Production Deployment (Week 7-8)
- Deploy OTLP Collector
- Configure sampling strategies
- Set up alerting rules
- Document runbooks

## Related ADRs

- ADR-007: Repository Pattern Caching (cache metrics)
- ADR-008: Resilience4j Circuit Breaking (circuit breaker metrics)
- ADR-004: Spring Boot 3.4 + Java 25 (OTel native support)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-18
**Implementation Status:** IN PROGRESS
**Review Date:** 2026-05-01 (3 months)

---

**Revision History:**
- 2026-02-18: Initial version approved
