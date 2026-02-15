# YAWL OpenTelemetry - Documentation Index

Quick reference to all observability documentation and resources.

## Quick Links

- **New to YAWL Observability?** → Start with [QUICKSTART.md](./QUICKSTART.md)
- **Ready to Deploy?** → See [BUILD.md](./BUILD.md)
- **Need to Query Data?** → Check [QUERIES.md](./QUERIES.md)
- **Want Full Details?** → Read [README.md](./README.md)
- **Integration Overview?** → Review [INTEGRATION_SUMMARY.md](./INTEGRATION_SUMMARY.md)

---

## Documentation Structure

### Getting Started

**[QUICKSTART.md](./QUICKSTART.md)** (5-minute guide)
- Zero-code instrumentation setup
- Manual instrumentation setup
- Development mode (no dependencies)
- Kubernetes deployment
- Cloud platform integration
- Verification steps
- Troubleshooting common issues

**Start here if**: You want to get YAWL observability running quickly.

---

### Complete Reference

**[README.md](./README.md)** (Comprehensive documentation)
- Architecture overview
- Configuration options
- Deployment scenarios
- Observability features
- Performance tuning
- Best practices
- Security considerations
- Native image compatibility

**Read this if**: You need complete understanding of the integration.

---

### Query Reference

**[QUERIES.md](./QUERIES.md)** (Query examples and alerts)
- 50+ PromQL query examples
- TraceQL examples for Tempo/Jaeger
- Elasticsearch queries
- Grafana Explore workflows
- Cloud provider queries (GCP, AWS, Azure)
- Alerting rules
- Performance optimization queries
- Capacity planning queries

**Use this if**: You need to query telemetry data or set up alerts.

---

### Build and Deployment

**[BUILD.md](./BUILD.md)** (Build and deployment guide)
- Build requirements
- Maven dependencies
- Build process
- Deployment options (WAR, JAR, Docker, K8s)
- Configuration management
- Build profiles
- Troubleshooting build issues
- CI/CD integration
- Production checklist

**Consult this if**: You're building or deploying YAWL with observability.

---

### Integration Summary

**[INTEGRATION_SUMMARY.md](./INTEGRATION_SUMMARY.md)** (Executive overview)
- Architecture summary
- Components delivered
- Features implemented
- Maven integration
- Deployment scenarios
- Performance benchmarks
- Cloud platform support
- HYPER_STANDARDS compliance
- File inventory

**Review this if**: You need a high-level overview of what was delivered.

---

## Directory Structure

```
observability/
├── opentelemetry/
│   ├── INDEX.md                          # ← You are here
│   ├── QUICKSTART.md                     # 5-minute quick start
│   ├── README.md                         # Complete documentation
│   ├── QUERIES.md                        # Query examples
│   ├── BUILD.md                          # Build & deployment
│   ├── INTEGRATION_SUMMARY.md            # Integration overview
│   ├── agent-config.properties           # Production agent config
│   ├── agent-config-dev.properties       # Dev agent config
│   ├── application-otel.properties       # Spring Boot config
│   ├── application-otel-dev.properties   # Dev Spring Boot config
│   ├── docker-compose-otel.yml           # Full observability stack
│   └── grafana-datasources.yaml          # Grafana datasource config
│
├── dashboards/
│   ├── yawl-otel-dashboard.json          # Main YAWL dashboard
│   ├── grafana/                          # Other dashboards
│   │   ├── operational-dashboard.json
│   │   ├── business-dashboard.json
│   │   └── ...
│
├── tracing/
│   ├── opentelemetry/
│   │   └── collector.yaml                # OTel Collector config
│   └── jaeger/
│       └── values.yaml                   # Jaeger config
│
├── monitoring/
│   └── prometheus/
│       ├── values.yaml                   # Prometheus config
│       ├── alerting-rules.yaml           # Alert definitions
│       └── recording-rules.yaml          # Recording rules
│
└── logging/
    └── loki/
        └── values.yaml                   # Loki config
```

---

## Common Tasks

### Task: Get Started in 5 Minutes
1. Read: [QUICKSTART.md](./QUICKSTART.md) → Option 1
2. Run: `mvn clean package`
3. Run: `./scripts/run-with-otel-dev.sh`
4. Access: http://localhost:9464/metrics

### Task: Deploy to Production
1. Read: [BUILD.md](./BUILD.md) → Production Checklist
2. Read: [README.md](./README.md) → Best Practices
3. Configure: `agent-config.properties`
4. Deploy: Follow deployment scenario in README.md
5. Verify: Use [QUERIES.md](./QUERIES.md) to validate

### Task: Set Up Monitoring
1. Deploy: OpenTelemetry Collector (see docker-compose-otel.yml)
2. Import: Dashboard from `dashboards/yawl-otel-dashboard.json`
3. Configure: Alerts from [QUERIES.md](./QUERIES.md) → Alerting Rules
4. Verify: Check Grafana dashboards

### Task: Query Workflow Data
1. Reference: [QUERIES.md](./QUERIES.md)
2. Choose your backend:
   - Prometheus: Use PromQL queries
   - Jaeger/Tempo: Use TraceQL queries
   - Cloud: Use provider-specific queries
3. Create custom queries based on examples

### Task: Troubleshoot Issues
1. Check: [QUICKSTART.md](./QUICKSTART.md) → Troubleshooting
2. Check: [BUILD.md](./BUILD.md) → Troubleshooting Build Issues
3. Check: [README.md](./README.md) → Troubleshooting
4. Enable: Debug logging (`OTEL_JAVAAGENT_DEBUG=true`)
5. Verify: Collector health, metrics endpoint, trace backend

### Task: Optimize Performance
1. Read: [README.md](./README.md) → Performance Tuning
2. Review: [INTEGRATION_SUMMARY.md](./INTEGRATION_SUMMARY.md) → Performance Impact
3. Adjust: Sampling ratios, batch settings
4. Monitor: Using [QUERIES.md](./QUERIES.md) → Performance Optimization

### Task: Deploy to Cloud
1. Choose platform: GCP, AWS, or Azure
2. Read: [QUICKSTART.md](./QUICKSTART.md) → Cloud Provider Quick Start
3. Read: [README.md](./README.md) → Cloud Platform Integration
4. Configure: Cloud-specific exporters
5. Deploy: Follow cloud deployment scenario

---

## Key Concepts

### Zero-Code Instrumentation
Automatic instrumentation using OpenTelemetry Java agent. No code changes required.

**Learn more**: [README.md](./README.md) → Zero-Code Instrumentation

### Manual Instrumentation
Custom YAWL-specific metrics and traces using Spring Boot Starter.

**Learn more**: [README.md](./README.md) → Manual Instrumentation

### OpenTelemetry Collector
Receives, processes, and exports telemetry to multiple backends.

**Learn more**: [README.md](./README.md) → Architecture

### Sampling
Controls how many traces are collected to reduce overhead.

**Learn more**: [README.md](./README.md) → Performance Tuning

### Exporters
Send telemetry to different backends (OTLP, Jaeger, Prometheus, Cloud).

**Learn more**: [README.md](./README.md) → Exporters Supported

---

## Source Code Reference

### Java Classes

**YAWLTelemetry.java**
- Location: `src/org/yawlfoundation/yawl/engine/observability/`
- Purpose: Central telemetry provider for YAWL
- Methods: Record case/workitem events, update metrics

**YAWLTracing.java**
- Location: `src/org/yawlfoundation/yawl/engine/observability/`
- Purpose: Tracing utilities for YAWL operations
- Methods: Create spans, add attributes, record exceptions

**OpenTelemetryConfig.java**
- Location: `src/org/yawlfoundation/yawl/engine/observability/`
- Purpose: Spring Boot configuration for OpenTelemetry
- Features: Resource creation, exporter setup, auto-configuration

### Test Classes

**YAWLTelemetryTest.java**
- Location: `test/org/yawlfoundation/yawl/engine/observability/`
- Coverage: 20+ test cases, 80%+ coverage

**YAWLTracingTest.java**
- Location: `test/org/yawlfoundation/yawl/engine/observability/`
- Coverage: 18+ test cases, comprehensive span testing

---

## Configuration Reference

### Agent Configuration

**Production**: `agent-config.properties`
- OTLP exporter to collector
- 10% sampling
- Batch processing enabled

**Development**: `agent-config-dev.properties`
- Logging exporter (no collector needed)
- 100% sampling
- Debug mode enabled

### Spring Boot Configuration

**Production**: `application-otel.properties`
- YAWL observability enabled
- OTLP endpoint configured
- 10% sampling

**Development**: `application-otel-dev.properties`
- Logging exporters
- 100% sampling
- Debug logging

---

## Dashboards Reference

### Main Dashboard
**File**: `dashboards/yawl-otel-dashboard.json`

**Panels**:
- Active cases/work items
- Case/work item throughput
- Duration percentiles (p50, p95, p99)
- Error rates
- Top slowest traces
- Operation performance

**Import to Grafana**:
```bash
curl -X POST http://grafana:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @observability/dashboards/yawl-otel-dashboard.json
```

---

## External Resources

- **OpenTelemetry**: https://opentelemetry.io/docs/instrumentation/java/
- **OpenTelemetry Collector**: https://opentelemetry.io/docs/collector/
- **Grafana**: https://grafana.com/docs/
- **Prometheus**: https://prometheus.io/docs/
- **Jaeger**: https://www.jaegertracing.io/docs/
- **Tempo**: https://grafana.com/docs/tempo/

---

## Support

### Documentation Feedback
If you find issues or have suggestions for documentation:
1. Check if the answer exists in another doc using this index
2. Review the QUICKSTART troubleshooting section
3. Review the BUILD troubleshooting section
4. Check OpenTelemetry official documentation

### Integration Issues
If you encounter integration issues:
1. Enable debug logging: `OTEL_JAVAAGENT_DEBUG=true`
2. Check collector logs: `docker logs otel-collector`
3. Verify metrics endpoint: `curl http://localhost:9464/metrics`
4. Review [QUICKSTART.md](./QUICKSTART.md) → Troubleshooting

---

## Version Information

- **YAWL Version**: 5.2
- **OpenTelemetry SDK**: 1.36.0
- **OpenTelemetry Instrumentation**: 2.2.0
- **Spring Boot**: 3.2.2
- **Java**: 21+

---

## Changelog

### 2026-02-15 - Initial Integration
- Zero-code instrumentation via Java agent
- Manual instrumentation via Spring Boot Starter
- Complete documentation suite
- Production-ready dashboards and alerts
- Full cloud platform support
- Comprehensive test coverage

---

**Questions?** Start with [QUICKSTART.md](./QUICKSTART.md) for immediate help.
