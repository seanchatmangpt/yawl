# YAWL Actuator - Quick Reference Card

## Endpoints at a Glance

| Endpoint | Purpose | When to Use |
|----------|---------|-------------|
| `/actuator/health` | Overall health | Startup probe, manual checks |
| `/actuator/health/liveness` | Process alive | Kubernetes liveness probe |
| `/actuator/health/readiness` | Ready for traffic | Kubernetes readiness probe |
| `/actuator/metrics` | All metrics (JSON) | Debugging, ad-hoc monitoring |
| `/actuator/prometheus` | Prometheus format | Prometheus scraping |

## Health Status Codes

- **200 OK** = Healthy (UP)
- **503 Service Unavailable** = Unhealthy (DOWN)

## Common Curl Commands

```bash
# Check overall health
curl http://localhost:8080/actuator/health | jq .

# Check liveness
curl http://localhost:8080/actuator/health/liveness

# Check readiness
curl http://localhost:8080/actuator/health/readiness

# List all metrics
curl http://localhost:8080/actuator/metrics | jq .names

# Get specific metric
curl http://localhost:8080/actuator/metrics/yawl.cases.active | jq .

# Get Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep yawl
```

## Kubernetes Probe Configuration

```yaml
# Minimal working configuration
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
```

## Key Metrics

### Workflow Metrics
- `yawl_cases_active` - Active workflow cases
- `yawl_cases_launched_total` - Total cases launched
- `yawl_cases_completed_total` - Total cases completed
- `yawl_workitems_active` - Active work items

### Agent Metrics
- `yawl_agent_invocations_total` - Total agent calls
- `yawl_agent_success_total` - Successful agent calls
- `yawl_agent_response_time_seconds` - Agent response times

### Resource Metrics
- `yawl_jvm_memory_heap_used_bytes` - Heap memory used
- `yawl_jvm_threads_live` - Number of threads

## Recording Metrics in Code

```java
// Inject metrics
@Autowired
private YWorkflowMetrics workflowMetrics;

@Autowired
private YAgentPerformanceMetrics agentMetrics;

// Record case events
workflowMetrics.recordCaseLaunched();
workflowMetrics.recordCaseCompleted(executionTimeMs);
workflowMetrics.recordCaseCancelled();
workflowMetrics.recordCaseFailed();

// Record work item events
workflowMetrics.recordWorkItemEnabled();
workflowMetrics.recordWorkItemStarted();
workflowMetrics.recordWorkItemCompleted(executionTimeMs);
workflowMetrics.recordWorkItemFailed();

// Record agent events
agentMetrics.recordA2AAgentInvocation("agent-name");
agentMetrics.recordA2AAgentSuccess("agent-name", responseTimeMs);
agentMetrics.recordA2AAgentFailure("agent-name", "TimeoutException");

agentMetrics.recordMCPAgentInvocation("mcp-service");
agentMetrics.recordMCPAgentSuccess("mcp-service", responseTimeMs);
agentMetrics.recordMCPAgentFailure("mcp-service", "ConnectionException");
```

## Health Indicator Behavior

### Engine Health
- **UP**: Engine running, load < 90%
- **WARNING**: Load 75-90%
- **DOWN**: Engine dormant/terminating, or load > 90%

### Database Health
- **UP**: Connected, query < 1000ms
- **WARNING**: Query 1000-5000ms
- **DOWN**: Not connected or query > 5000ms

### External Services Health
- **UP**: All services responding
- **WARNING**: Some services down (< 50%)
- **DOWN**: All services down or > 50% down

### Liveness
- **UP**: Process alive, no deadlock
- **DOWN**: Process deadlocked or terminated

### Readiness
- **UP**: Initialized, not overloaded
- **DOWN**: Initializing, shutting down, or overloaded

## Prometheus Alerts (Copy-Paste Ready)

```yaml
groups:
- name: yawl_critical
  rules:
  - alert: YAWLDown
    expr: up{job="yawl-engine"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "YAWL Engine is down"

  - alert: YAWLDatabaseDown
    expr: yawl_database_health_status == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "YAWL Database is unavailable"

  - alert: YAWLHighLoad
    expr: yawl_cases_active > 8000
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "YAWL Engine high load: {{ $value }} cases"
```

## Docker Compose (Quick Deploy)

```yaml
version: '3.8'
services:
  yawl:
    image: yawl-engine:5.2
    ports:
      - "8080:8080"
    environment:
      - ENVIRONMENT=production
      - JAVA_OPTS=-Xmx2g
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/liveness"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
```

## Cloud Run (gcloud command)

```bash
gcloud run deploy yawl-engine \
  --image gcr.io/project/yawl-engine:5.2 \
  --platform managed \
  --region us-central1 \
  --port 8080 \
  --memory 2Gi \
  --cpu 2
```

## Troubleshooting

### Health Check Returns 404
→ Actuator not enabled. Check `management.endpoints.web.exposure.include` in application.yml

### Health Always Shows DOWN
→ Check specific components in `/actuator/health` response for error details

### Metrics Not in Prometheus
→ Verify Prometheus scrape config and check `/actuator/prometheus` endpoint directly

### Container Restart Loop
→ Increase liveness probe `initialDelaySeconds` and `failureThreshold`

### Readiness Never Ready
→ Check database connectivity and engine initialization logs

## Environment Variables

```bash
# Enable all actuator endpoints
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus,info

# Set environment tag
ENVIRONMENT=production

# Show health details
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always

# Enable probes
MANAGEMENT_HEALTH_PROBES_ENABLED=true
```

## Configuration Snippet (application.yml)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  metrics:
    tags:
      application: yawl-engine
      environment: ${ENVIRONMENT:development}
```

## Quick Test Script

```bash
#!/bin/bash
# test-actuator.sh

BASE_URL="http://localhost:8080"

echo "Testing YAWL Actuator Endpoints..."

echo -e "\n1. Overall Health:"
curl -s $BASE_URL/actuator/health | jq .status

echo -e "\n2. Liveness:"
curl -s $BASE_URL/actuator/health/liveness | jq .status

echo -e "\n3. Readiness:"
curl -s $BASE_URL/actuator/health/readiness | jq .status

echo -e "\n4. Active Cases:"
curl -s $BASE_URL/actuator/metrics/yawl.cases.active | jq .measurements[0].value

echo -e "\n5. Metrics Count:"
curl -s $BASE_URL/actuator/metrics | jq '.names | length'

echo -e "\nAll tests complete!"
```

## Further Reading

- Full Documentation: `/docs/actuator/README.md`
- Kubernetes Guide: `/docs/actuator/KUBERNETES_INTEGRATION.md`
- Implementation Details: `/docs/actuator/IMPLEMENTATION_SUMMARY.md`

---
**YAWL v5.2** | Cloud-Native Workflow Engine
