---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/observability/**"
  - "*/src/main/java/org/yawlfoundation/yawl/monitoring/**"
  - "**/yawl-monitoring/**"
  - "monitoring/**"
  - "scripts/observatory/**"
---

# Observability & Monitoring Rules

## Observatory (Codebase Intelligence)
- Facts in `docs/v6/latest/facts/` — always read before exploring codebase
- Refresh: `bash scripts/observatory/observatory.sh`
- Facts-only (fast): `bash scripts/observatory/observatory.sh --facts`
- 1 fact file ~ 50 tokens vs 5000 tokens for grep (100x compression)
- Staleness: check `receipts/observatory.json` SHA256 hashes

## OpenTelemetry
- Traces: Instrument workflow case execution (case start → task transitions → completion)
- Metrics: Case throughput, work item latency, queue depths, engine health
- Logs: Structured JSON logging with correlation IDs (case ID, work item ID)
- Context propagation via ScopedValue (not ThreadLocal)

## Prometheus Metrics
- Endpoint: `/actuator/prometheus` (Spring Boot Actuator)
- Key metrics: `yawl_cases_active`, `yawl_workitems_completed_total`, `yawl_task_duration_seconds`
- Histogram buckets for latency: 10ms, 50ms, 100ms, 200ms, 500ms, 1s, 5s

## Health Checks
- `/actuator/health` — Full health (engine + DB + integrations)
- `/actuator/health/liveness` — JVM alive (Kubernetes liveness)
- `/actuator/health/readiness` — Ready to serve (Kubernetes readiness)
- Custom indicators for: database connectivity, MCP server status, A2A agent status
