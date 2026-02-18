# yawl-monitoring

**Artifact:** `org.yawlfoundation:yawl-monitoring:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Comprehensive observability and monitoring infrastructure (`org.yawlfoundation.yawl.observability`):

- **Distributed tracing** — OpenTelemetry SDK with Jaeger (Thrift) and OTLP exporters
- **Metrics** — Micrometer registry with Prometheus scrape endpoint
- **Structured logging** — JSON-formatted logs via Log4j 2 + Jackson
- **Semantic conventions** — OTel semconv (stable + incubating) for workflow-specific attributes
- **Spring Boot integration** — optional Actuator health and metrics endpoints

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-engine` | engine metric sources (case counts, work item states, latency) |

## Key Third-Party Dependencies

| Artifact | Version | Purpose |
|----------|---------|---------|
| `opentelemetry-api` + `opentelemetry-sdk` | `1.52.0` | OTel SDK core |
| `opentelemetry-sdk-trace-core` | `1.52.0` | Trace SDK |
| `opentelemetry-instrumentation-api` + `-api-semconv` | `2.18.1` | Instrumentation API |
| `opentelemetry-exporter-jaeger-thrift` | `1.52.0` | Jaeger trace export |
| `opentelemetry-exporter-otlp` | `1.52.0` | OTLP export (Grafana, Honeycomb, etc.) |
| `opentelemetry-semconv` | `1.39.0` | Stable semantic conventions |
| `opentelemetry-semconv-incubating` | `1.39.0-alpha` | Incubating semconv attributes |
| `micrometer-core` | `1.16.3` | Metrics abstraction |
| `micrometer-registry-prometheus` | `1.16.3` | Prometheus metrics endpoint |
| `opentelemetry-exporter-prometheus` | `1.52.0-alpha` | OTel → Prometheus bridge |
| `log4j-api` + `log4j-core` + `log4j-slf4j2-impl` | `2.25.3` | Full Log4j 2 stack |
| `jackson-databind` + `jackson-datatype-jsr310` | `2.19.4` | JSON structured log output |
| `spring-boot-starter-actuator` | `3.5.10` | Optional Spring Boot health/metrics (optional dependency) |

> **Note:** This is the only YAWL module that declares `log4j-slf4j2-impl`, which bridges
> SLF4J calls into Log4j 2 core. Including it in multiple modules would cause bridge conflicts.

Test dependencies: JUnit 4, JUnit 5 Jupiter, Hamcrest.

## Build Configuration Notes

- **Source directory:** `../src/org/yawlfoundation/yawl/observability` (scoped to observability package)
- **Test directory:** `../test/org/yawlfoundation/yawl/observability`
- The Spring Boot Actuator dependency is declared `<optional>true</optional>` — only activated
  in Spring Boot deployments

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-monitoring clean package
```
