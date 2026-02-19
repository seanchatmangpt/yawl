# YAWL Spring Boot Actuator - Kubernetes Integration Guide

## Overview

YAWL v6.0.0 includes production-ready Spring Boot Actuator endpoints for cloud-native deployments on Kubernetes (GKE, EKS, AKS) and Google Cloud Run.

## Health Endpoints

### Liveness Probe
**Endpoint:** `/actuator/health/liveness`

**Purpose:** Determines if the YAWL engine process is alive and not deadlocked.

**Kubernetes Configuration:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

**Response Codes:**
- `200 OK` - Engine is alive
- `503 Service Unavailable` - Engine is deadlocked or terminated (restart required)

**Sample Response (UP):**
```json
{
  "status": "UP",
  "details": {
    "status": "running",
    "alive": true,
    "uptime": "2h 15m 30s"
  }
}
```

### Readiness Probe
**Endpoint:** `/actuator/health/readiness`

**Purpose:** Determines if the YAWL engine is ready to accept traffic.

**Kubernetes Configuration:**
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 2
```

**Response Codes:**
- `200 OK` - Engine is ready to accept requests
- `503 Service Unavailable` - Engine is initializing, overloaded, or database unavailable (remove from load balancer)

**Sample Response (UP):**
```json
{
  "status": "UP",
  "details": {
    "status": "running",
    "ready": true,
    "activeCases": 150,
    "load": "1.50%"
  }
}
```

### Overall Health
**Endpoint:** `/actuator/health`

**Purpose:** Comprehensive health check including all subsystems.

**Kubernetes Configuration:**
```yaml
# Optional startup probe for slow-starting containers
startupProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 10
  failureThreshold: 30
```

**Sample Response:**
```json
{
  "status": "UP",
  "components": {
    "yEngineHealthIndicator": {
      "status": "UP",
      "details": {
        "status": "running",
        "activeCases": 150,
        "workItems": 342,
        "loadedSpecifications": 12,
        "overallLoad": "3.42%"
      }
    },
    "yDatabaseHealthIndicator": {
      "status": "UP",
      "details": {
        "persistence": "enabled",
        "connection": "available",
        "database": "PostgreSQL",
        "version": "14.5",
        "queryTime": "8ms"
      }
    },
    "yExternalServicesHealthIndicator": {
      "status": "UP",
      "details": {
        "totalServices": 5,
        "healthyServices": 5,
        "unhealthyServices": 0
      }
    }
  }
}
```

## Metrics Endpoints

### Prometheus Metrics
**Endpoint:** `/actuator/prometheus`

**Purpose:** Exposes metrics in Prometheus format for scraping.

**Kubernetes ServiceMonitor:**
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: yawl-engine
  labels:
    app: yawl-engine
spec:
  selector:
    matchLabels:
      app: yawl-engine
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

**Available Metrics:**
- `yawl_cases_launched_total` - Total workflow cases launched
- `yawl_cases_completed_total` - Total workflow cases completed
- `yawl_cases_active` - Currently active workflow cases
- `yawl_workitems_completed_total` - Total work items completed
- `yawl_workitems_active` - Currently active work items
- `yawl_case_execution_time_seconds` - Case execution time histogram
- `yawl_workitem_execution_time_seconds` - Work item execution time histogram
- `yawl_agent_invocations_total` - Agent invocations by agent name
- `yawl_agent_response_time_seconds` - Agent response time histogram
- `yawl_jvm_memory_heap_used_bytes` - JVM heap memory usage
- `yawl_jvm_threads_live` - Number of live threads

### Metrics JSON
**Endpoint:** `/actuator/metrics`

**Purpose:** JSON format metrics for monitoring tools.

**Sample Response:**
```json
{
  "names": [
    "yawl.cases.active",
    "yawl.cases.launched",
    "yawl.cases.completed",
    "yawl.workitems.active",
    "yawl.agent.invocations.total",
    "jvm.memory.used",
    "jvm.threads.live"
  ]
}
```

**Detailed Metric:**
`GET /actuator/metrics/yawl.cases.active`
```json
{
  "name": "yawl.cases.active",
  "description": "Number of currently active workflow cases",
  "baseUnit": "cases",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 150.0
    }
  ],
  "availableTags": [
    {
      "tag": "application",
      "values": ["yawl-engine"]
    }
  ]
}
```

## Complete Kubernetes Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  labels:
    app: yawl-engine
    version: v5.2
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
  template:
    metadata:
      labels:
        app: yawl-engine
        version: v5.2
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
      - name: yawl-engine
        image: yawl/yawl-engine:5.2
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        env:
        - name: ENVIRONMENT
          value: production
        - name: JAVA_OPTS
          value: "-Xmx2g -Xms1g"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: http
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: http
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
        startupProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: 10
          periodSeconds: 10
          failureThreshold: 30
---
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine
  labels:
    app: yawl-engine
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: http
    protocol: TCP
    name: http
  selector:
    app: yawl-engine
```

## Google Cloud Run Configuration

**service.yaml:**
```yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: yawl-engine
spec:
  template:
    metadata:
      annotations:
        autoscaling.knative.dev/maxScale: '10'
        autoscaling.knative.dev/minScale: '1'
    spec:
      containers:
      - image: gcr.io/project-id/yawl-engine:5.2
        ports:
        - containerPort: 8080
        env:
        - name: ENVIRONMENT
          value: production
        resources:
          limits:
            memory: 2Gi
            cpu: '2'
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
          initialDelaySeconds: 60
          periodSeconds: 10
        startupProbe:
          httpGet:
            path: /actuator/health/readiness
          initialDelaySeconds: 30
          periodSeconds: 5
          failureThreshold: 10
```

**Deploy:**
```bash
gcloud run services replace service.yaml --region=us-central1
```

## Monitoring and Alerting

### Prometheus Alert Rules

```yaml
groups:
- name: yawl_alerts
  rules:
  - alert: YAWLEngineDown
    expr: up{job="yawl-engine"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "YAWL Engine is down"
      description: "YAWL Engine has been down for more than 1 minute."

  - alert: YAWLHighLoad
    expr: yawl_cases_active > 8000
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "YAWL Engine high load"
      description: "YAWL Engine has {{ $value }} active cases (threshold: 8000)"

  - alert: YAWLDatabaseDown
    expr: yawl_database_health_status == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "YAWL Database is down"
      description: "YAWL cannot connect to database"

  - alert: YAWLAgentFailureRate
    expr: rate(yawl_agent_failure_total[5m]) > 0.1
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High agent failure rate"
      description: "Agent failure rate is {{ $value }} per second"
```

### Grafana Dashboard

Import the YAWL dashboard template from `/docs/actuator/grafana-dashboard.json`

**Key Panels:**
- Active Cases (gauge)
- Case Completion Rate (graph)
- Work Item Throughput (graph)
- Agent Response Times (heatmap)
- JVM Memory Usage (graph)
- Database Query Times (graph)

## Environment Variables

```bash
# Enable actuator endpoints
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus,info

# Set environment tag for metrics
ENVIRONMENT=production

# Configure application name
SPRING_APPLICATION_NAME=yawl-engine

# Health check settings
MANAGEMENT_HEALTH_PROBES_ENABLED=true
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
```

## Security Considerations

### Restrict Actuator Endpoints

In production, restrict actuator access to internal networks only:

```yaml
management:
  server:
    port: 9090  # Different port for actuator
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
      base-path: /actuator
```

**Network Policy:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: yawl-actuator-access
spec:
  podSelector:
    matchLabels:
      app: yawl-engine
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: monitoring
    ports:
    - protocol: TCP
      port: 9090
```

## Troubleshooting

### Health Check Fails During Startup

**Symptom:** Readiness probe fails with 503

**Solution:** Increase `initialDelaySeconds` to allow engine initialization:
```yaml
readinessProbe:
  initialDelaySeconds: 60  # Increase from 30
```

### Liveness Probe Causes Restart Loop

**Symptom:** Container restarts repeatedly

**Solution:** Increase `failureThreshold` and check deadlock detection:
```yaml
livenessProbe:
  failureThreshold: 5  # Increase from 3
  timeoutSeconds: 10   # Increase timeout
```

### Metrics Not Appearing in Prometheus

**Symptom:** No metrics scraped

**Solution:** Verify ServiceMonitor selector and port:
```bash
kubectl get servicemonitor -n monitoring
kubectl logs -n monitoring prometheus-0 | grep yawl
```

## Best Practices

1. **Always configure both liveness and readiness probes** - They serve different purposes
2. **Use startup probes for slow-starting engines** - Prevents premature liveness failures
3. **Monitor agent health separately** - External service failures shouldn't affect readiness
4. **Set appropriate resource limits** - Prevents OOM kills that trigger liveness failures
5. **Use Prometheus for metrics** - Better than polling /actuator/metrics endpoint
6. **Configure graceful shutdown** - Allows in-flight workflows to complete

## References

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Kubernetes Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Prometheus Metrics](https://prometheus.io/docs/concepts/metric_types/)
- [Cloud Run Health Checks](https://cloud.google.com/run/docs/configuring/healthchecks)
